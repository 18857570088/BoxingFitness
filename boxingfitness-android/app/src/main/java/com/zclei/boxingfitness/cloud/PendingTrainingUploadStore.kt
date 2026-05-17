package com.zclei.boxingfitness.cloud

import android.content.Context
import android.util.Log
import com.zclei.boxingfitness.model.TrainingMode
import com.zclei.boxingfitness.model.TrainingReport
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

private const val PENDING_UPLOAD_PREFS_NAME = "reflex_ball_settings"
private const val KEY_PENDING_TRAINING_UPLOADS = "cloud_pending_training_uploads"
private const val MAX_PENDING_TRAINING_UPLOADS = 200
private const val PENDING_QUEUE_TAG = "PendingTrainingQueue"

internal data class PendingTrainingUpload(
    val clientSessionKey: String,
    val queuedAtEpochMs: Long,
    val attemptCount: Int,
    val lastAttemptAtEpochMs: Long,
    val report: TrainingReport,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("client_session_key", clientSessionKey)
            .put("queued_at_epoch_ms", queuedAtEpochMs)
            .put("attempt_count", attemptCount)
            .put("last_attempt_at_epoch_ms", lastAttemptAtEpochMs)
            .put("mode_seconds", report.sessionDurationSeconds)
            .put("total_hits", report.totalHits)
            .put("average_frequency", report.averageFrequency.toDouble())
            .put("best_burst_count", report.bestBurstCount)
            .put("best_burst_start_sec", report.bestBurstStartSec.toDouble())
            .put("ended_at_epoch_ms", report.endedAtEpochMs)

    companion object {
        fun fromJson(json: JSONObject): PendingTrainingUpload {
            val modeSeconds = json.optInt("mode_seconds", 30)
            val mode =
                when {
                    modeSeconds >= 60 -> TrainingMode.Seconds60
                    else -> TrainingMode.Seconds30
                }
            return PendingTrainingUpload(
                clientSessionKey = json.optString("client_session_key"),
                queuedAtEpochMs = json.optLong("queued_at_epoch_ms"),
                attemptCount = json.optInt("attempt_count", 0),
                lastAttemptAtEpochMs = json.optLong("last_attempt_at_epoch_ms"),
                report =
                    TrainingReport(
                        mode = mode,
                        totalHits = json.optInt("total_hits", 0),
                        averageFrequency = json.optDouble("average_frequency", 0.0).toFloat(),
                        bestBurstCount = json.optInt("best_burst_count", 0),
                        bestBurstStartSec = json.optDouble("best_burst_start_sec", 0.0).toFloat(),
                        endedAtEpochMs = json.optLong("ended_at_epoch_ms"),
                        sessionDurationSeconds = modeSeconds.coerceAtLeast(1),
                    ),
            )
        }
    }
}

internal object PendingTrainingUploadStore {
    @Synchronized
    fun enqueue(
        context: Context,
        report: TrainingReport,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): PendingTrainingUpload {
        val uploads = loadAll(context).toMutableList()
        val clientSessionKey = buildClientSessionKey(report)
        val existingIndex = uploads.indexOfFirst { it.clientSessionKey == clientSessionKey }
        val nextAttemptCount = if (existingIndex >= 0) uploads[existingIndex].attemptCount + 1 else 1
        val queuedAtEpochMs = if (existingIndex >= 0) uploads[existingIndex].queuedAtEpochMs else nowEpochMs
        val item =
            PendingTrainingUpload(
                clientSessionKey = clientSessionKey,
                queuedAtEpochMs = queuedAtEpochMs,
                attemptCount = nextAttemptCount,
                lastAttemptAtEpochMs = nowEpochMs,
                report = report,
            )
        if (existingIndex >= 0) {
            uploads[existingIndex] = item
        } else {
            uploads += item
        }
        val trimmed =
            uploads
                .sortedBy { it.queuedAtEpochMs }
                .takeLast(MAX_PENDING_TRAINING_UPLOADS)
        saveAll(context, trimmed)
        Log.i(PENDING_QUEUE_TAG, "Queued training upload ${item.clientSessionKey}, pending=${trimmed.size}")
        return item
    }

    @Synchronized
    fun loadAll(context: Context): List<PendingTrainingUpload> {
        val raw =
            context
                .getSharedPreferences(PENDING_UPLOAD_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PENDING_TRAINING_UPLOADS, null)
                .orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (index in 0 until json.length()) {
                    add(PendingTrainingUpload.fromJson(json.getJSONObject(index)))
                }
            }
        }.getOrElse { throwable ->
            Log.w(PENDING_QUEUE_TAG, "Failed to parse pending training uploads", throwable)
            emptyList()
        }
    }

    @Synchronized
    fun replaceAll(
        context: Context,
        uploads: List<PendingTrainingUpload>,
    ) {
        saveAll(context, uploads.sortedBy { it.queuedAtEpochMs })
    }

    @Synchronized
    fun pendingCount(context: Context): Int = loadAll(context).size

    fun buildClientSessionKey(report: TrainingReport): String {
        val payload =
            listOf(
                report.sessionDurationSeconds.toString(),
                report.totalHits.toString(),
                report.averageFrequency.toString(),
                report.bestBurstCount.toString(),
                report.bestBurstStartSec.toString(),
                report.endedAtEpochMs.toString(),
            ).joinToString("|")
        return sha256(payload).take(40)
    }

    private fun saveAll(
        context: Context,
        uploads: List<PendingTrainingUpload>,
    ) {
        val array = JSONArray()
        uploads.forEach { array.put(it.toJson()) }
        context
            .getSharedPreferences(PENDING_UPLOAD_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_TRAINING_UPLOADS, array.toString())
            .apply()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

