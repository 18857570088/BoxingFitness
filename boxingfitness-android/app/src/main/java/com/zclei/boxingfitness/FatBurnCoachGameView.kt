package com.zclei.boxingfitness

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.zclei.boxingfitness.ui.Haptics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FatBurnCoachGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    data class PhaseConfig(
        val phaseKey: String,
        val zhLabel: String,
        val enLabel: String,
        val durationMs: Long,
        val bpm: Int,
        val hittable: Boolean,
        val roundIndex: Int,
        val roundTotal: Int,
        val cueStrength: Float,
    ) {
        fun label(languageCode: String): String = if (languageCode.startsWith("zh")) zhLabel else enLabel
    }

    data class SessionConfig(
        val dayIndex: Int,
        val zhTitle: String,
        val enTitle: String,
        val targetValidHits: Int,
        val estimatedCalories: Float,
        val phases: List<PhaseConfig>,
    ) {
        val totalDurationMs: Long
            get() = phases.sumOf { it.durationMs }

        fun title(languageCode: String): String = if (languageCode.startsWith("zh")) zhTitle else enTitle
    }

    data class OverlaySnapshot(
        val totalHits: Int,
        val validHits: Int,
        val missedBeats: Int,
        val accuracy: Float,
        val hps: Float,
        val combo: Int,
        val bestCombo: Int,
        val calories: Float,
        val fatBurnGrams: Float,
        val roundIndex: Int,
        val roundTotal: Int,
        val phaseLabel: String,
        val beatFitLabel: String,
    )

    interface Listener {
        fun onOverlaySnapshot(snapshot: OverlaySnapshot)

        fun onHintChanged(hint: String)
    }

    private data class PhaseWindow(
        val startMs: Long,
        val endMs: Long,
        val phase: PhaseConfig,
    )

    private data class BeatCue(
        val id: Long,
        val timeMs: Long,
        val side: Int,
        val hittable: Boolean,
        val phaseWindow: PhaseWindow,
        var judged: Boolean = false,
        var hit: Boolean = false,
        var hitOffsetMs: Float = 0f,
    )

    private data class Pulse(
        val side: Int,
        val bornAtMs: Long,
        val lifeMs: Float,
        val color: Int,
        val cueStrength: Float,
        var alpha: Float = 1f,
    )

    private data class Particle(
        var x: Float,
        var y: Float,
        var velocityX: Float,
        var velocityY: Float,
        val radius: Float,
        val color: Int,
        val maxLifeMs: Float,
        val stretch: Float,
        var lifeMs: Float = 0f,
    )

    private val random = java.util.Random()
    private val phaseWindows = ArrayList<PhaseWindow>()
    private val beatCues = ArrayList<BeatCue>()
    private val pulses = ArrayList<Pulse>()
    private val particles = ArrayList<Particle>()
    private val recentOffsets = ArrayList<Float>()
    private val debugAutoHitCueIds = HashSet<Long>()

    private var sessionConfig: SessionConfig? = null
    private var listener: Listener? = null
    private var languageCode: String = "zh"
    private var sessionActive = false
    private var detectorReady = false
    private var detectorPrimed = false
    private var debugAutoPunchEnabled = false
    private var running = false
    private var lastFrameMs = 0L
    private var sessionStartElapsedMs = 0L
    private var nextCueIndex = 0
    private var lastBeatSide = if (random.nextBoolean()) -1 else 1
    private var currentHint = idleHint()

    private var totalHits = 0
    private var validHits = 0
    private var missedBeats = 0
    private var combo = 0
    private var bestCombo = 0
    private var caloriesBurned = 0f
    private var fatBurnGrams = 0f
    private var impactFlashAlpha = 0f
    private var impactFlashX = 0f
    private var impactFlashY = 0f
    private var impactFlashRadius = 0f
    private var impactFlashColor = Color.WHITE
    private var phaseProgress = 0f

    private val toneGenerator by lazy(LazyThreadSafetyMode.NONE) {
        ToneGenerator(AudioManager.STREAM_MUSIC, 92)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val metricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C7EFFC")
        textAlign = Paint.Align.CENTER
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun bind(listener: Listener) {
        this.listener = listener
        publishOverlaySnapshot()
        publishHint(currentHint)
    }

    fun setLanguageCode(value: String) {
        languageCode = value.ifBlank { "zh" }
        publishOverlaySnapshot()
        publishHint(currentHint)
        invalidate()
    }

    fun setSessionConfig(config: SessionConfig) {
        sessionConfig = config
        rebuildTimeline(config)
        debugAutoHitCueIds.clear()
        publishOverlaySnapshot()
        invalidate()
    }

    fun setDebugAutoPunchEnabled(enabled: Boolean) {
        debugAutoPunchEnabled = enabled
        if (!enabled) {
            debugAutoHitCueIds.clear()
        }
    }

    fun currentSnapshot(): OverlaySnapshot =
        OverlaySnapshot(
            totalHits = totalHits,
            validHits = validHits,
            missedBeats = missedBeats,
            accuracy = currentAccuracy(),
            hps = currentHps(),
            combo = combo,
            bestCombo = bestCombo,
            calories = caloriesBurned,
            fatBurnGrams = fatBurnGrams,
            roundIndex = currentPhaseWindow()?.phase?.roundIndex ?: 0,
            roundTotal = currentPhaseWindow()?.phase?.roundTotal ?: 0,
            phaseLabel = currentPhaseWindow()?.phase?.label(languageCode).orEmpty(),
            beatFitLabel = beatFitLabel(),
        )

    fun beginTraining() {
        sessionActive = true
        detectorReady = false
        detectorPrimed = false
        sessionStartElapsedMs = SystemClock.elapsedRealtime()
        nextCueIndex = 0
        totalHits = 0
        validHits = 0
        missedBeats = 0
        combo = 0
        bestCombo = 0
        caloriesBurned = 0f
        fatBurnGrams = 0f
        pulses.clear()
        particles.clear()
        recentOffsets.clear()
        debugAutoHitCueIds.clear()
        impactFlashAlpha = 0f
        currentHint = armingHint()
        publishHint(currentHint)
        publishOverlaySnapshot()
        invalidate()
    }

    fun endTraining() {
        sessionActive = false
        detectorReady = false
        detectorPrimed = false
        nextCueIndex = 0
        pulses.clear()
        particles.clear()
        debugAutoHitCueIds.clear()
        impactFlashAlpha = 0f
        impactFlashRadius = 0f
        phaseProgress = 0f
        currentHint = finishedHint()
        publishHint(currentHint)
        invalidate()
    }

    fun updateDetectorState(type: String) {
        detectorReady =
            if (!sessionActive) {
                detectorPrimed = false
                false
            } else {
                when (type) {
                    "ready" -> {
                        detectorPrimed = true
                        true
                    }
                    "permission_denied", "finished" -> {
                        detectorPrimed = false
                        false
                    }
                    else -> detectorPrimed
                }
            }
        currentHint =
            when (type) {
                "ready" -> readyHint()
                "loading" -> armingHint()
                "calibrating" -> calibratingHint()
                "permission_denied" -> permissionHint()
                "error" -> errorHint()
                "finished" -> finishedHint()
                else -> if (sessionActive) waitingHint() else idleHint()
            }
        publishHint(currentHint)
    }

    fun registerPunch(
        intensity: Float,
        hand: BoxingBleManager.BoxingHand? = null,
    ) {
        if (!sessionActive) {
            return
        }
        val handSide = hand.toPunchSide() ?: return
        val scoringPhase = currentPhaseWindow()?.phase?.hittable == true
        if (!scoringPhase) {
            currentHint =
                if (currentPhaseWindow()?.phase?.phaseKey == "warmup") {
                    readyHint()
                } else {
                    recoverHint()
                }
            publishHint(currentHint)
            return
        }
        val elapsedMs = SystemClock.elapsedRealtime() - sessionStartElapsedMs
        val attempt =
            beatCues
                .asSequence()
                .filter {
                    if (!it.hittable || it.judged) {
                        false
                    } else {
                        val offsetMs = (elapsedMs - it.timeMs).toFloat()
                        it.side == handSide && offsetMs in -ATTEMPT_WINDOW_EARLY_MS..ATTEMPT_WINDOW_LATE_MS
                    }
                }
                .minByOrNull { abs((elapsedMs - it.timeMs).toFloat()) }
        val candidate =
            attempt?.takeIf {
                val offsetMs = (elapsedMs - it.timeMs).toFloat()
                offsetMs in -HIT_WINDOW_EARLY_MS..HIT_WINDOW_LATE_MS
            }

        if (attempt == null) {
            return
        }
        totalHits += 1

        if (candidate != null) {
            candidate.judged = true
            candidate.hit = true
            candidate.hitOffsetMs = (elapsedMs - candidate.timeMs).toFloat()
            recentOffsets += candidate.hitOffsetMs
            while (recentOffsets.size > 8) {
                recentOffsets.removeAt(0)
            }
            validHits += 1
            combo += 1
            bestCombo = max(bestCombo, combo)
            val phaseFactor = currentPhaseWindow()?.phase?.cueStrength ?: 1f
            caloriesBurned += (0.28f + intensity.coerceIn(0.35f, 1.8f) * 0.12f) * phaseFactor
            fatBurnGrams += (0.038f + intensity.coerceIn(0.35f, 1.8f) * 0.018f) * phaseFactor
            createHitBurst(candidate.side, intensity, hit = true)
            currentHint = successHint()
            publishHint(currentHint)
            Haptics.tap(context)
        } else {
            combo = 0
            createHitBurst(
                side = if (lastBeatSide >= 0) 1 else -1,
                intensity = intensity * 0.72f,
                hit = false,
            )
            currentHint = offBeatHint()
            publishHint(currentHint)
        }
        publishOverlaySnapshot()
    }

    fun previewPunch(hand: BoxingBleManager.BoxingHand? = null) {
        if (!sessionActive) {
            return
        }
        val handSide = hand.toPunchSide() ?: return
        val elapsedMs = SystemClock.elapsedRealtime() - sessionStartElapsedMs
        val hasCue =
            beatCues.any {
                if (!it.hittable || it.judged || it.side != handSide) {
                    false
                } else {
                    val offsetMs = (elapsedMs - it.timeMs).toFloat()
                    offsetMs in -ATTEMPT_WINDOW_EARLY_MS..ATTEMPT_WINDOW_LATE_MS
                }
            }
        if (!hasCue) {
            return
        }
        createHitBurst(side = handSide, intensity = 0.55f, hit = true)
        postInvalidateOnAnimation()
    }

    fun playCountdownPulse() {
        pulses +=
            Pulse(
                side = 0,
                bornAtMs = SystemClock.elapsedRealtime(),
                lifeMs = 520f,
                color = Color.WHITE,
                cueStrength = 0.8f,
            )
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastFrameMs = 0L
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        running = false
        lastFrameMs = 0L
        runCatching { toneGenerator.release() }
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.elapsedRealtime()
        val deltaMs =
            when {
                lastFrameMs == 0L -> 16f
                else -> min(34f, (now - lastFrameMs).toFloat())
            }
        lastFrameMs = now
        updateWorld(now, deltaMs)
        drawWorld(canvas, now)
        if (running) {
            postInvalidateOnAnimation()
        }
    }

    private fun rebuildTimeline(config: SessionConfig) {
        phaseWindows.clear()
        beatCues.clear()
        var cursorMs = 0L
        var cueId = 1L
        config.phases.forEach { phase ->
            val window =
                PhaseWindow(
                    startMs = cursorMs,
                    endMs = cursorMs + phase.durationMs,
                    phase = phase,
                )
            phaseWindows += window
            if (phase.bpm > 0) {
                val intervalMs = (60_000f / phase.bpm.toFloat()).toLong().coerceAtLeast(180L)
                var beatMs = cursorMs + intervalMs
                while (beatMs < window.endMs) {
                    val side =
                        if (phase.hittable) {
                            lastBeatSide *= -1
                            lastBeatSide
                        } else {
                            0
                        }
                    beatCues +=
                        BeatCue(
                            id = cueId++,
                            timeMs = beatMs,
                            side = side,
                            hittable = phase.hittable,
                            phaseWindow = window,
                        )
                    beatMs += intervalMs
                }
            }
            cursorMs = window.endMs
        }
    }

    private fun updateWorld(
        nowMs: Long,
        deltaMs: Float,
    ) {
        val config = sessionConfig ?: return
        val elapsedMs =
            when {
                sessionActive -> (nowMs - sessionStartElapsedMs).coerceAtLeast(0L)
                else -> 0L
            }

        while (sessionActive && nextCueIndex < beatCues.size && beatCues[nextCueIndex].timeMs <= elapsedMs) {
            val cue = beatCues[nextCueIndex++]
            spawnCuePulse(cue)
            playBeatTone(cue)
        }

        if (sessionActive) {
            beatCues.forEach { cue ->
                if (cue.hittable && !cue.judged && elapsedMs > cue.timeMs + HIT_WINDOW_LATE_MS.toLong()) {
                    cue.judged = true
                    missedBeats += 1
                    combo = 0
                    currentHint = missHint()
                    publishHint(currentHint)
                    publishOverlaySnapshot()
                }
            }
        }

        if (sessionActive && debugAutoPunchEnabled) {
            beatCues.forEach { cue ->
                if (
                    cue.hittable &&
                    !cue.judged &&
                    !debugAutoHitCueIds.contains(cue.id) &&
                    elapsedMs >= cue.timeMs + DEBUG_AUTO_PUNCH_DELAY_MS &&
                    elapsedMs <= cue.timeMs + HIT_WINDOW_LATE_MS.toLong() - 36L
                ) {
                    debugAutoHitCueIds += cue.id
                    registerPunch(1.08f)
                }
            }
        }

        val pulseIterator = pulses.iterator()
        while (pulseIterator.hasNext()) {
            val pulse = pulseIterator.next()
            val ageMs = (nowMs - pulse.bornAtMs).toFloat()
            pulse.alpha = (1f - ageMs / pulse.lifeMs).coerceIn(0f, 1f)
            if (pulse.alpha <= 0.02f) {
                pulseIterator.remove()
            }
        }

        val particleIterator = particles.iterator()
        while (particleIterator.hasNext()) {
            val particle = particleIterator.next()
            particle.x += particle.velocityX * deltaMs * 0.06f
            particle.y += particle.velocityY * deltaMs * 0.06f
            particle.velocityX *= 0.991f
            particle.velocityY = particle.velocityY * 0.988f + 0.018f
            particle.lifeMs += deltaMs
            if (particle.lifeMs >= particle.maxLifeMs) {
                particleIterator.remove()
            }
        }

        if (impactFlashAlpha > 0f) {
            impactFlashAlpha *= 0.915f
            impactFlashRadius += deltaMs * 0.48f
            if (impactFlashAlpha <= 0.02f) {
                impactFlashAlpha = 0f
            }
        }

        val currentWindow = currentPhaseWindow(elapsedMs)
        val currentPhase = currentWindow?.phase
        phaseProgress =
            when {
                currentWindow == null -> 0f
                currentWindow.endMs <= currentWindow.startMs -> 0f
                else -> ((elapsedMs - currentWindow.startMs).toFloat() / (currentWindow.endMs - currentWindow.startMs).toFloat()).coerceIn(0f, 1f)
            }

        if (!sessionActive) {
            currentHint = if (config.phases.isEmpty()) idleHint() else idleHint()
        } else if (!detectorReady && elapsedMs < 1_500L) {
            currentHint = armingHint()
        } else if (currentPhase != null && !currentPhase.hittable) {
            currentHint = recoverHint()
        }
    }

    private fun drawWorld(
        canvas: Canvas,
        nowMs: Long,
    ) {
        val safeWidth = width.toFloat().coerceAtLeast(1f)
        val safeHeight = height.toFloat().coerceAtLeast(1f)
        val currentElapsedMs =
            when {
                sessionActive -> (nowMs - sessionStartElapsedMs).coerceAtLeast(0L)
                else -> 0L
            }
        val currentWindow = currentPhaseWindow(currentElapsedMs)

        fillPaint.shader =
            LinearGradient(
                0f,
                0f,
                safeWidth,
                safeHeight,
                intArrayOf(
                    Color.parseColor("#100B11"),
                    Color.parseColor("#271118"),
                    Color.parseColor("#441515"),
                    Color.parseColor("#110A0E"),
                ),
                floatArrayOf(0f, 0.32f, 0.72f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(0f, 0f, safeWidth, safeHeight, fillPaint)
        fillPaint.shader = null

        drawBackgroundBands(canvas, safeWidth, safeHeight)
        val showTrainingScene = sessionActive || pulses.isNotEmpty() || particles.isNotEmpty() || impactFlashAlpha > 0.02f
        if (showTrainingScene) {
            drawBeatPads(canvas, safeWidth, safeHeight, currentWindow?.phase)
            drawBeatProgress(canvas, safeWidth, safeHeight)
            drawImpactOverlay(canvas)
            pulses.forEach { drawPulse(canvas, it, safeWidth, safeHeight) }
            particles.forEach { drawParticle(canvas, it) }
            drawCenterBeatOrb(canvas, safeWidth, safeHeight, currentWindow)
            if (!sessionActive) {
                drawCenterCopy(canvas, safeWidth, safeHeight, currentWindow)
            }
        }
    }

    private fun drawBackgroundBands(
        canvas: Canvas,
        safeWidth: Float,
        safeHeight: Float,
    ) {
        val bands =
            listOf(
                RectF(safeWidth * 0.08f, safeHeight * 0.18f, safeWidth * 0.92f, safeHeight * 0.23f),
                RectF(safeWidth * 0.04f, safeHeight * 0.48f, safeWidth * 0.96f, safeHeight * 0.53f),
                RectF(safeWidth * 0.12f, safeHeight * 0.78f, safeWidth * 0.88f, safeHeight * 0.83f),
            )
        bands.forEachIndexed { index, rect ->
            fillPaint.shader =
                LinearGradient(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                    intArrayOf(Color.TRANSPARENT, Color.parseColor(if (index % 2 == 0) "#1AFFF08C" else "#1AF97316"), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
            canvas.drawRoundRect(rect, dp(26f), dp(26f), fillPaint)
            fillPaint.shader = null
        }
    }

    private fun drawBeatPads(
        canvas: Canvas,
        safeWidth: Float,
        safeHeight: Float,
        currentPhase: PhaseConfig?,
    ) {
        val padWidth = safeWidth * 0.22f
        val padHeight = safeHeight * 0.24f
        val centerY = safeHeight * 0.56f
        val leftRect = RectF(safeWidth * 0.08f, centerY - padHeight * 0.5f, safeWidth * 0.08f + padWidth, centerY + padHeight * 0.5f)
        val rightRect = RectF(safeWidth * 0.70f, centerY - padHeight * 0.5f, safeWidth * 0.70f + padWidth, centerY + padHeight * 0.5f)

        drawPad(canvas, leftRect, active = isSideActive(-1), label = text("左拳", "LEFT"))
        drawPad(canvas, rightRect, active = isSideActive(1), label = text("右拳", "RIGHT"))

        if (currentPhase != null) {
            metricPaint.textSize = safeWidth * 0.016f
            metricPaint.color = Color.parseColor("#E7F6FF")
            canvas.drawText(currentPhase.label(languageCode), safeWidth * 0.5f, safeHeight * 0.14f, metricPaint)
        }
    }

    private fun drawPad(
        canvas: Canvas,
        rect: RectF,
        active: Boolean,
        label: String,
    ) {
        fillPaint.shader =
            LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    Color.parseColor(if (active) "#3DFFB84D" else "#1C5C3720"),
                    Color.parseColor(if (active) "#5CFF6B1A" else "#12372420"),
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRoundRect(rect, dp(24f), dp(24f), fillPaint)
        fillPaint.shader = null

        strokePaint.color = Color.parseColor(if (active) "#FFE992" else "#5AD4B392")
        strokePaint.strokeWidth = dp(if (active) 3f else 1.4f)
        canvas.drawRoundRect(rect, dp(24f), dp(24f), strokePaint)

        val inner = RectF(rect.left + dp(12f), rect.top + dp(12f), rect.right - dp(12f), rect.bottom - dp(12f))
        strokePaint.color = Color.parseColor(if (active) "#88FFF0B8" else "#296AE0C2")
        strokePaint.strokeWidth = dp(1.1f)
        canvas.drawRoundRect(inner, dp(16f), dp(16f), strokePaint)

        textPaint.textSize = rect.width() * 0.14f
        textPaint.color = Color.WHITE
        canvas.drawText(label, rect.centerX(), rect.centerY() + textPaint.textSize * 0.35f, textPaint)
    }

    private fun drawBeatProgress(
        canvas: Canvas,
        safeWidth: Float,
        safeHeight: Float,
    ) {
        val trackRect = RectF(safeWidth * 0.22f, safeHeight * 0.88f, safeWidth * 0.78f, safeHeight * 0.90f)
        fillPaint.color = Color.parseColor("#1AFFFFFF")
        canvas.drawRoundRect(trackRect, trackRect.height() * 0.5f, trackRect.height() * 0.5f, fillPaint)

        val config = sessionConfig
        val progress =
            if (config == null || !sessionActive || config.totalDurationMs <= 0L) {
                0f
            } else {
                ((SystemClock.elapsedRealtime() - sessionStartElapsedMs).toFloat() / config.totalDurationMs.toFloat()).coerceIn(0f, 1f)
            }

        val fillRect = RectF(trackRect.left, trackRect.top, lerp(trackRect.left, trackRect.right, progress), trackRect.bottom)
        fillPaint.shader =
            LinearGradient(
                fillRect.left,
                fillRect.top,
                fillRect.right,
                fillRect.bottom,
                intArrayOf(Color.parseColor("#FFB347"), Color.parseColor("#FF5A5F"), Color.parseColor("#FFCB52")),
                floatArrayOf(0f, 0.54f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRoundRect(fillRect, fillRect.height() * 0.5f, fillRect.height() * 0.5f, fillPaint)
        fillPaint.shader = null
    }

    private fun drawImpactOverlay(canvas: Canvas) {
        if (impactFlashAlpha <= 0f) {
            return
        }
        fillPaint.shader =
            RadialGradient(
                impactFlashX,
                impactFlashY,
                impactFlashRadius.coerceAtLeast(dp(26f)),
                intArrayOf(
                    withAlpha(impactFlashColor, (impactFlashAlpha * 220f).toInt()),
                    withAlpha(impactFlashColor, (impactFlashAlpha * 80f).toInt()),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
        fillPaint.shader = null
    }

    private fun drawPulse(
        canvas: Canvas,
        pulse: Pulse,
        safeWidth: Float,
        safeHeight: Float,
    ) {
        val ageMs = (SystemClock.elapsedRealtime() - pulse.bornAtMs).toFloat()
        val progress = (ageMs / pulse.lifeMs).coerceIn(0f, 1f)
        val centerX =
            when (pulse.side) {
                -1 -> safeWidth * 0.19f
                1 -> safeWidth * 0.81f
                else -> safeWidth * 0.5f
            }
        val centerY = if (pulse.side == 0) safeHeight * 0.44f else safeHeight * 0.56f
        val radius = lerp(dp(28f), dp(126f), progress * (0.78f + pulse.cueStrength * 0.22f))
        strokePaint.color = withAlpha(pulse.color, (pulse.alpha * 220f).toInt())
        strokePaint.strokeWidth = dp(4.2f - progress * 2.2f)
        canvas.drawCircle(centerX, centerY, radius, strokePaint)

        fillPaint.shader =
            RadialGradient(
                centerX,
                centerY,
                radius,
                intArrayOf(
                    withAlpha(pulse.color, (pulse.alpha * 120f).toInt()),
                    withAlpha(pulse.color, (pulse.alpha * 34f).toInt()),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.46f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        fillPaint.shader = null
    }

    private fun drawParticle(
        canvas: Canvas,
        particle: Particle,
    ) {
        val alpha = (1f - particle.lifeMs / particle.maxLifeMs).coerceIn(0f, 1f)
        particlePaint.color = withAlpha(particle.color, (alpha * 255f).toInt())
        canvas.save()
        canvas.translate(particle.x, particle.y)
        canvas.scale(particle.stretch, 1f / particle.stretch.coerceAtLeast(0.65f))
        canvas.drawCircle(0f, 0f, particle.radius, particlePaint)
        canvas.restore()
    }

    private fun drawCenterBeatOrb(
        canvas: Canvas,
        safeWidth: Float,
        safeHeight: Float,
        currentWindow: PhaseWindow?,
    ) {
        val centerX = safeWidth * 0.5f
        val centerY = safeHeight * 0.42f
        val baseRadius = safeWidth * 0.072f
        val dynamicRadius = baseRadius * (1f + 0.18f * currentBeatEnergy())
        fillPaint.shader =
            RadialGradient(
                centerX,
                centerY,
                dynamicRadius * 1.35f,
                intArrayOf(
                    Color.parseColor("#FFFFEE9A"),
                    Color.parseColor("#B3FF6B35"),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.36f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawCircle(centerX, centerY, dynamicRadius * 1.35f, fillPaint)
        fillPaint.shader = null

        fillPaint.color = Color.parseColor("#FFF7D05E")
        canvas.drawCircle(centerX, centerY, dynamicRadius, fillPaint)
        strokePaint.color = Color.parseColor("#FFFDF4CD")
        strokePaint.strokeWidth = dp(2.4f)
        canvas.drawCircle(centerX, centerY, dynamicRadius, strokePaint)

        metricPaint.textSize = safeWidth * 0.016f
        metricPaint.color = Color.parseColor("#3D2200")
        canvas.drawText(
            currentWindow?.phase?.roundIndex?.takeIf { it > 0 }?.let { text("第 $it 轮", "R$it") } ?: text("节拍", "BEAT"),
            centerX,
            centerY + metricPaint.textSize * 0.35f,
            metricPaint,
        )
    }

    private fun drawCenterCopy(
        canvas: Canvas,
        safeWidth: Float,
        safeHeight: Float,
        currentWindow: PhaseWindow?,
    ) {
        val titleY = safeHeight * 0.20f
        textPaint.color = Color.WHITE
        textPaint.textSize = safeWidth * 0.028f
        canvas.drawText(sessionConfig?.title(languageCode).orEmpty(), safeWidth * 0.5f, titleY, textPaint)

        metricPaint.color = Color.parseColor("#DBF3FF")
        metricPaint.textSize = safeWidth * 0.015f
        val phaseText =
            when {
                !sessionActive -> text("AI 教练陪练节拍课程", "AI coach-guided rhythm session")
                currentWindow == null -> currentHint
                else -> "${currentWindow.phase.label(languageCode)} · ${beatFitLabel()}"
            }
        canvas.drawText(phaseText, safeWidth * 0.5f, titleY + metricPaint.textSize * 1.9f, metricPaint)
    }

    private fun spawnCuePulse(cue: BeatCue) {
        val color =
            when {
                !cue.hittable -> Color.parseColor("#8AD8F0")
                cue.side < 0 -> Color.parseColor("#FFD060")
                else -> Color.parseColor("#FF7A59")
            }
        pulses +=
            Pulse(
                side = cue.side,
                bornAtMs = SystemClock.elapsedRealtime(),
                lifeMs = if (cue.hittable) 420f else 540f,
                color = color,
                cueStrength = cue.phaseWindow.phase.cueStrength,
            )
    }

    private fun createHitBurst(
        side: Int,
        intensity: Float,
        hit: Boolean,
    ) {
        val safeWidth = width.toFloat().coerceAtLeast(1f)
        val safeHeight = height.toFloat().coerceAtLeast(1f)
        val centerX =
            when (side) {
                -1 -> safeWidth * 0.19f
                1 -> safeWidth * 0.81f
                else -> safeWidth * 0.5f
            }
        val centerY = if (side == 0) safeHeight * 0.44f else safeHeight * 0.56f
        impactFlashX = centerX
        impactFlashY = centerY
        impactFlashRadius = safeWidth * 0.05f
        impactFlashAlpha = if (hit) 0.92f else 0.44f
        impactFlashColor = if (hit) Color.parseColor("#FFF8B0") else Color.parseColor("#7CE3FF")

        val burstCount = if (hit) 14 else 6
        repeat(burstCount) { index ->
            val angle = (Math.PI * 2.0 * index / burstCount.toDouble()) + random.nextDouble() * 0.55
            val speed = (0.85f + random.nextFloat() * 0.95f) * intensity.coerceIn(0.35f, 1.75f)
            particles +=
                Particle(
                    x = centerX,
                    y = centerY,
                    velocityX = kotlin.math.cos(angle).toFloat() * speed * 14f,
                    velocityY = kotlin.math.sin(angle).toFloat() * speed * 14f,
                    radius = dp((if (hit) 2.8f else 2.1f) + random.nextFloat() * 3.8f),
                    color =
                        if (hit) {
                            if (index % 2 == 0) Color.parseColor("#FFF2A2") else Color.parseColor("#FF6B4A")
                        } else {
                            Color.parseColor("#8AE9FF")
                        },
                    maxLifeMs = if (hit) 460f else 320f,
                    stretch = 0.8f + random.nextFloat() * 1.8f,
                )
        }

        playImpactTone(hit = hit, intensity = intensity)
    }

    private fun playBeatTone(cue: BeatCue) {
        if (!sessionActive) {
            return
        }
        if (cue.hittable) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 48)
        } else {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 32)
        }
    }

    private fun playImpactTone(
        hit: Boolean,
        intensity: Float,
    ) {
        if (hit) {
            val baseDuration = (42 + intensity.coerceIn(0.35f, 1.8f) * 24f).toInt()
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, baseDuration)
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, (baseDuration * 0.82f).toInt())
        } else {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 42)
        }
    }

    private fun currentPhaseWindow(
        elapsedMs: Long = if (sessionActive) SystemClock.elapsedRealtime() - sessionStartElapsedMs else 0L,
    ): PhaseWindow? = phaseWindows.firstOrNull { elapsedMs in it.startMs until it.endMs } ?: phaseWindows.lastOrNull()

    private fun isSideActive(side: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        return pulses.any { it.side == side && now - it.bornAtMs < it.lifeMs * 0.9f }
    }

    private fun BoxingBleManager.BoxingHand?.toPunchSide(): Int? =
        when (this) {
            BoxingBleManager.BoxingHand.Left -> -1
            BoxingBleManager.BoxingHand.Right -> 1
            null -> null
        }

    private fun currentAccuracy(): Float {
        return if (totalHits <= 0) 0f else validHits.toFloat() / totalHits.toFloat()
    }

    private fun currentHps(): Float {
        if (!sessionActive) {
            val config = sessionConfig ?: return 0f
            val totalSeconds = config.totalDurationMs / 1_000f
            return if (totalSeconds <= 0f) 0f else totalHits / totalSeconds
        }
        val elapsedSec = ((SystemClock.elapsedRealtime() - sessionStartElapsedMs).coerceAtLeast(1L)) / 1_000f
        return totalHits / elapsedSec
    }

    private fun currentBeatEnergy(): Float {
        val now = SystemClock.elapsedRealtime()
        val pulse = pulses.maxByOrNull { now - it.bornAtMs } ?: return 0f
        val age = (now - pulse.bornAtMs).toFloat()
        return (1f - age / pulse.lifeMs).coerceIn(0f, 1f)
    }

    private fun beatFitLabel(): String {
        if (recentOffsets.isEmpty()) {
            return text("等待节拍", "Build the rhythm")
        }
        val average = recentOffsets.average().toFloat()
        val meanAbs = recentOffsets.map { abs(it) }.average().toFloat()
        return when {
            meanAbs < 62f -> text("跟拍稳定", "On beat")
            average > 92f -> text("略慢半拍", "Slightly late")
            average < -92f -> text("略快半拍", "Slightly early")
            else -> text("节奏可控", "Rhythm steady")
        }
    }

    private fun publishOverlaySnapshot() {
        listener?.onOverlaySnapshot(currentSnapshot())
    }

    private fun publishHint(value: String) {
        listener?.onHintChanged(value)
    }

    private fun text(
        zh: String,
        en: String,
    ): String = if (languageCode.startsWith("zh")) zh else en
    private fun idleHint(): String = text("等待采纳今日陪练建议", "Waiting for today's coached plan")
    private fun armingHint(): String = text("正在校准蓝牙设备与陪练节拍", "Preparing Bluetooth hit detection")
    private fun calibratingHint(): String = text("环境校准中", "Calibrating environment")
    private fun readyHint(): String = text("跟着教练鼓点稳定出拳", "Follow the coach beat with steady punches")
    private fun waitingHint(): String = text("等待下一拍", "Wait for the next beat")
    private fun successHint(): String = text("命中节拍", "Beat matched")
    private fun missHint(): String = text("下一拍再压稳", "Lock into the next beat")
    private fun recoverHint(): String = text("恢复呼吸，准备下一轮", "Recover and prepare for the next round")
    private fun offBeatHint(): String = text("保持节拍，不要抢拍", "Keep the rhythm, don't rush")
    private fun permissionHint(): String = text("需要蓝牙设备权限才能开始陪练", "Bluetooth connection is required")
    private fun errorHint(): String = text("陪练识别暂时中断，正在恢复", "Bluetooth hit detection interrupted, recovering")
    private fun finishedHint(): String = text("今日陪练完成", "Today's coached session is complete")

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun lerp(
        start: Float,
        end: Float,
        progress: Float,
    ): Float = start + (end - start) * progress

    private fun withAlpha(
        color: Int,
        alpha: Int,
    ): Int = Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val HIT_WINDOW_EARLY_MS = 620f
        const val HIT_WINDOW_LATE_MS = 1_250f
        const val ATTEMPT_WINDOW_EARLY_MS = 760f
        const val ATTEMPT_WINDOW_LATE_MS = 1_650f
        const val DEBUG_AUTO_PUNCH_DELAY_MS = 72L
    }
}



