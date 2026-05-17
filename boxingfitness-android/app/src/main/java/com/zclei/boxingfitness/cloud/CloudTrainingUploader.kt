package com.zclei.boxingfitness.cloud

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import com.zclei.boxingfitness.BuildConfig
import com.zclei.boxingfitness.auth.ActivationState
import com.zclei.boxingfitness.model.TrainingMode
import com.zclei.boxingfitness.model.TrainingReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

private const val CLOUD_PREFS_NAME = "reflex_ball_settings"
private const val KEY_AUTH_SERIAL = "auth_serial"
private const val KEY_AUTH_TOKEN = "auth_token"
private const val KEY_AUTH_INSTALL_ID = "auth_install_id"
private const val KEY_AUTH_DEVICE_HASH = "auth_device_hash"
private const val KEY_AUTH_ACTIVATED_AT = "auth_activated_at"
private const val KEY_AUTH_LAST_CHECK_AT = "auth_last_check_at"
private const val FREE_USE_AUTH_TOKEN = "free-use"
private const val CLOUD_UPLOADER_TAG = "CloudTrainingUploader"
private const val QUEUED_REASON = "queued_local"

object CloudTrainingUploader {
    fun buildReport(
        totalHits: Int,
        durationSeconds: Int,
        averageFrequency: Float,
        bestBurstCount: Int,
        endedAtEpochMs: Long = System.currentTimeMillis(),
        preferredModeSeconds: Int = durationSeconds,
    ): TrainingReport {
        val safeDurationSeconds = durationSeconds.coerceAtLeast(1)
        val normalizedMode =
            when {
                preferredModeSeconds >= 60 -> TrainingMode.Seconds60
                else -> TrainingMode.Seconds30
            }
        return TrainingReport(
            mode = normalizedMode,
            totalHits = totalHits.coerceAtLeast(0),
            averageFrequency = averageFrequency.coerceAtLeast(0f),
            bestBurstCount = bestBurstCount.coerceAtLeast(0),
            bestBurstStartSec = 0f,
            endedAtEpochMs = endedAtEpochMs,
            sessionDurationSeconds = safeDurationSeconds,
        )
    }

    fun flushPendingIfAvailable(
        context: Context,
        scope: LifecycleCoroutineScope,
        onFlushed: ((Int) -> Unit)? = null,
    ) {
        val state = loadActivationState(context)
        if (state == null) {
            onFlushed?.let { callback ->
                scope.launch(Dispatchers.Main) {
                    callback(0)
                }
            }
            return
        }
        scope.launch(Dispatchers.IO) {
            val flushedCount = flushPendingNow(context, state)
            onFlushed?.let { callback ->
                scope.launch(Dispatchers.Main) {
                    callback(flushedCount)
                }
            }
        }
    }

    fun uploadIfAvailable(
        context: Context,
        scope: LifecycleCoroutineScope,
        report: TrainingReport,
        onUploaded: ((CloudSessionUploadResult) -> Unit)? = null,
    ) {
        val state = loadActivationState(context) ?: return
        scope.launch(Dispatchers.IO) {
            flushPendingNow(context, state)
            val result = uploadNow(state, report)
            val finalResult =
                if (result.success) {
                    result
                } else if (shouldQueueForRetry(result)) {
                    PendingTrainingUploadStore.enqueue(context, report)
                    CloudSessionUploadResult(
                        success = false,
                        message = "Cloud sync is temporarily unavailable. The training result has been saved locally and will upload automatically later.",
                        reason = QUEUED_REASON,
                        queuedLocally = true,
                        pendingQueueCount = PendingTrainingUploadStore.pendingCount(context),
                    )
                } else {
                    result
                }
            onUploaded?.let { callback ->
                scope.launch(Dispatchers.Main) {
                    callback(finalResult)
                }
            }
        }
    }

    private fun uploadNow(
        state: ActivationState,
        report: TrainingReport,
        clientSessionKey: String = PendingTrainingUploadStore.buildClientSessionKey(report),
    ): CloudSessionUploadResult =
        runCatching {
            CloudSyncService().uploadTrainingSession(
                state = state,
                report = report,
                appVersion = BuildConfig.VERSION_NAME,
                clientSessionKey = clientSessionKey,
            )
        }.getOrElse { throwable ->
            Log.w(CLOUD_UPLOADER_TAG, "Failed to upload training report", throwable)
            CloudSessionUploadResult(
                success = false,
                message = throwable.message ?: "Upload failed.",
                reason = "upload_error",
            )
        }

    private fun flushPendingNow(
        context: Context,
        state: ActivationState,
    ): Int {
        val pending = PendingTrainingUploadStore.loadAll(context)
        if (pending.isEmpty()) {
            return 0
        }
        val remaining = mutableListOf<PendingTrainingUpload>()
        var flushedCount = 0
        pending.forEachIndexed { index, entry ->
            val result = uploadNow(state, entry.report, entry.clientSessionKey)
            if (result.success) {
                flushedCount += 1
                Log.i(CLOUD_UPLOADER_TAG, "Flushed pending training upload ${entry.clientSessionKey}")
                return@forEachIndexed
            }
            if (shouldQueueForRetry(result)) {
                val now = System.currentTimeMillis()
                remaining +=
                    entry.copy(
                        attemptCount = entry.attemptCount + 1,
                        lastAttemptAtEpochMs = now,
                    )
                if (index + 1 < pending.size) {
                    remaining += pending.subList(index + 1, pending.size)
                }
                Log.w(
                    CLOUD_UPLOADER_TAG,
                    "Deferred pending training upload ${entry.clientSessionKey}, reason=${result.reason ?: "unknown"}",
                )
                PendingTrainingUploadStore.replaceAll(context, remaining)
                return flushedCount
            }
            Log.w(
                CLOUD_UPLOADER_TAG,
                "Dropped non-retriable pending upload ${entry.clientSessionKey}, reason=${result.reason ?: "unknown"}",
            )
        }
        PendingTrainingUploadStore.replaceAll(context, remaining)
        return flushedCount
    }

    private fun shouldQueueForRetry(result: CloudSessionUploadResult): Boolean =
        !result.success &&
            !result.queuedLocally &&
            when (result.reason) {
                null,
                "",
                CloudSyncService.NETWORK_REASON,
                "upload_error",
                "server_error",
                QUEUED_REASON,
                -> true
                else -> false
            }

    private fun loadActivationState(context: Context): ActivationState? {
        val prefs = context.getSharedPreferences(CLOUD_PREFS_NAME, Context.MODE_PRIVATE)
        var installId = prefs.getString(KEY_AUTH_INSTALL_ID, null).orEmpty()
        if (installId.isBlank()) {
            installId = UUID.randomUUID().toString()
        }
        val deviceHash = prefs.getString(KEY_AUTH_DEVICE_HASH, null).orEmpty().ifBlank { sha256Hex(installId) }
        val serial = prefs.getString(KEY_AUTH_SERIAL, null).orEmpty().ifBlank { freeUseSerial(installId, deviceHash) }
        val token = prefs.getString(KEY_AUTH_TOKEN, null).orEmpty().ifBlank { FREE_USE_AUTH_TOKEN }
        if (!prefs.contains(KEY_AUTH_SERIAL) || !prefs.contains(KEY_AUTH_TOKEN)) {
            val now = System.currentTimeMillis()
            prefs.edit()
                .putString(KEY_AUTH_SERIAL, serial)
                .putString(KEY_AUTH_TOKEN, token)
                .putString(KEY_AUTH_INSTALL_ID, installId)
                .putString(KEY_AUTH_DEVICE_HASH, deviceHash)
                .putLong(KEY_AUTH_ACTIVATED_AT, now)
                .putLong(KEY_AUTH_LAST_CHECK_AT, now)
                .apply()
        }
        return ActivationState(
            serial = serial,
            activationToken = token,
            installId = installId,
            deviceHash = deviceHash,
            activatedAtEpochMs = prefs.getLong(KEY_AUTH_ACTIVATED_AT, System.currentTimeMillis()),
            lastCheckAtEpochMs = prefs.getLong(KEY_AUTH_LAST_CHECK_AT, 0L),
        )
    }

    private fun freeUseSerial(
        installId: String,
        deviceHash: String,
    ): String {
        val digits =
            sha256Hex("$installId:$deviceHash")
                .map { char -> ('0'.code + (char.code % 10)).toChar() }
                .joinToString("")
                .take(9)
                .padEnd(9, '0')
        return "26$digits"
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

