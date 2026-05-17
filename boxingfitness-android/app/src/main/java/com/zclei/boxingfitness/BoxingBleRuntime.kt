package com.zclei.boxingfitness

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object BoxingBleRuntime {
    fun interface HitListener {
        fun onHit(packet: BoxingBleManager.BoxingPacket)
    }

    fun interface ImpactPreviewListener {
        fun onImpactPreview(packet: BoxingBleManager.BoxingPacket)
    }

    @Volatile
    private var manager: BoxingBleManager? = null

    @Volatile
    var latestPacket: BoxingBleManager.BoxingPacket? = null
        private set

    private val hitListeners = CopyOnWriteArraySet<HitListener>()
    private val impactPreviewListeners = CopyOnWriteArraySet<ImpactPreviewListener>()
    private val bleListeners = CopyOnWriteArraySet<BoxingBleManager.Listener>()
    private val impactPreviewStates = ConcurrentHashMap<String, ImpactPreviewState>()

    fun manager(context: Context): BoxingBleManager {
        val existing = manager
        if (existing != null) return existing
        return synchronized(this) {
            manager
                ?: BoxingBleManager(
                    context = context.applicationContext,
                    listener = runtimeBleListener,
                ).also { manager = it }
        }
    }

    fun addBleListener(listener: BoxingBleManager.Listener) {
        bleListeners.add(listener)
    }

    fun removeBleListener(listener: BoxingBleManager.Listener) {
        bleListeners.remove(listener)
    }

    fun enableGyro(): Boolean = manager?.enableGyro() == true

    fun disableGyro(): Boolean = manager?.disableGyro() == true

    fun readPunchThresholdSensitivity(): Boolean = manager?.readPunchThresholdSensitivity() == true

    fun writePunchThresholdSensitivity(level: Int): Boolean = manager?.writePunchThresholdSensitivity(level) == true

    fun addHitListener(listener: HitListener) {
        hitListeners.add(listener)
    }

    fun removeHitListener(listener: HitListener) {
        hitListeners.remove(listener)
    }

    fun addImpactPreviewListener(listener: ImpactPreviewListener) {
        impactPreviewListeners.add(listener)
    }

    fun removeImpactPreviewListener(listener: ImpactPreviewListener) {
        impactPreviewListeners.remove(listener)
    }

    private val runtimeBleListener =
        object : BoxingBleManager.Listener {
            override fun onStateChanged(state: BoxingBleManager.State) {
                bleListeners.forEach { listener -> listener.onStateChanged(state) }
            }

            override fun onDeviceListChanged(devices: List<BoxingBleManager.DeviceCandidate>) {
                bleListeners.forEach { listener -> listener.onDeviceListChanged(devices) }
            }

            override fun onPacket(packet: BoxingBleManager.BoxingPacket) {
                latestPacket = packet
                bleListeners.forEach { listener -> listener.onPacket(packet) }
            }

            override fun onHit(packet: BoxingBleManager.BoxingPacket) {
                latestPacket = packet
                markOfficialHit(packet)
                hitListeners.forEach { listener -> listener.onHit(packet) }
                bleListeners.forEach { listener -> listener.onHit(packet) }
            }

            override fun onPunchThresholdSensitivity(level: Int) {
                bleListeners.forEach { listener -> listener.onPunchThresholdSensitivity(level) }
            }

            override fun onLog(message: String) {
                bleListeners.forEach { listener -> listener.onLog(message) }
            }
        }

    private fun maybeEmitImpactPreview(packet: BoxingBleManager.BoxingPacket) {
        val key = packet.deviceAddress ?: packet.deviceName ?: return
        val state =
            impactPreviewStates.getOrPut(key) {
                ImpactPreviewState(packet)
            }
        val now = SystemClock.elapsedRealtime()
        val punchIncreased = packet.punches > state.lastPunches
        val pressureTriggered = packet.sample.pressure >= IMPACT_PREVIEW_PRESSURE_THRESHOLD
        val accelDelta =
            abs(packet.sample.ax - state.lastAx) +
                abs(packet.sample.ay - state.lastAy) +
                abs(packet.sample.az - state.lastAz)
        val accelTriggered = accelDelta >= IMPACT_PREVIEW_ACCEL_DELTA_THRESHOLD
        val enoughGap = now - state.lastPreviewAtMs >= IMPACT_PREVIEW_DEBOUNCE_MS
        val afterOfficialGap = now - state.lastOfficialHitAtMs >= IMPACT_PREVIEW_AFTER_HIT_SUPPRESS_MS
        if (!punchIncreased && enoughGap && afterOfficialGap && (pressureTriggered || accelTriggered)) {
            state.lastPreviewAtMs = now
            impactPreviewListeners.forEach { listener -> listener.onImpactPreview(packet) }
        }
        state.update(packet)
    }

    private fun markOfficialHit(packet: BoxingBleManager.BoxingPacket) {
        val key = packet.deviceAddress ?: packet.deviceName ?: return
        val state =
            impactPreviewStates.getOrPut(key) {
            ImpactPreviewState(packet)
        }
        state.lastOfficialHitAtMs = SystemClock.elapsedRealtime()
        state.update(packet)
    }

    private class ImpactPreviewState(packet: BoxingBleManager.BoxingPacket) {
        var lastPunches = packet.punches
        var lastAx = packet.sample.ax
        var lastAy = packet.sample.ay
        var lastAz = packet.sample.az
        var lastPreviewAtMs = 0L
        var lastOfficialHitAtMs = Long.MIN_VALUE / 2

        fun update(packet: BoxingBleManager.BoxingPacket) {
            lastPunches = packet.punches
            lastAx = packet.sample.ax
            lastAy = packet.sample.ay
            lastAz = packet.sample.az
        }
    }

    private const val IMPACT_PREVIEW_PRESSURE_THRESHOLD = 1_400
    private const val IMPACT_PREVIEW_ACCEL_DELTA_THRESHOLD = 220
    private const val IMPACT_PREVIEW_DEBOUNCE_MS = 240L
    private const val IMPACT_PREVIEW_AFTER_HIT_SUPPRESS_MS = 150L
}
