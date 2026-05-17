package com.zclei.boxingfitness

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
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
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class BlitzModeGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    enum class DifficultyPreset(
        val zhLabel: String,
        val enLabel: String,
        val frLabel: String,
        val thLabel: String,
        val minSpawnGapMs: Float,
        val maxSpawnGapMs: Float,
        val minHitSpawnGapMs: Float,
        val maxHitSpawnGapMs: Float,
        val targetLifeMs: Float,
        val travelFraction: Float,
        val hitCalories: Float,
        val fatBurnRatio: Float,
    ) {
        Beginner("慢速", "Beginner", "Débutant", "ช้า", 200f, 280f, 120f, 160f, 750f, 0.12f, 0.34f, 0.110f),
        Advanced("中速", "Advanced", "Intermédiaire", "กลาง", 130f, 160f, 70f, 100f, 600f, 0.18f, 0.40f, 0.116f),
        Insane("快速", "Insane", "Extrême", "เร็ว", 100f, 120f, 40f, 60f, 450f, 0.29f, 0.46f, 0.123f),
        ;

        fun label(languageCode: String): String =
            when {
                languageCode.startsWith("zh") -> zhLabel
                languageCode.startsWith("fr") -> frLabel
                languageCode.startsWith("th") -> thLabel
                else -> enLabel
            }
    }

    data class OverlaySnapshot(
        val hits: Int,
        val combo: Int,
        val bestCombo: Int,
        val pps: Float,
        val calories: Float,
        val fatBurnGrams: Float,
    )

    interface Listener {
        fun onOverlaySnapshot(snapshot: OverlaySnapshot)

        fun onHintChanged(hint: String)
    }

    private data class Target(
        val id: Long,
        val side: Int,
        val y: Float,
        val width: Float,
        val height: Float,
        val startX: Float,
        val endX: Float,
        val bornAtMs: Long,
        val liveMs: Float,
        val accentColor: Int,
        val coreColor: Int,
        val pulseOffset: Float,
        var ageMs: Float = 0f,
        var hit: Boolean = false,
        var hitAgeMs: Float = 0f,
    )

    private data class Particle(
        var x: Float,
        var y: Float,
        var velocityX: Float,
        var velocityY: Float,
        val radius: Float,
        val maxLifeMs: Float,
        val color: Int,
        var lifeMs: Float = 0f,
        var rotation: Float = 0f,
        val spin: Float = 0f,
        val stretch: Float = 1f,
    )

    private data class Ring(
        val x: Float,
        val y: Float,
        var radius: Float,
        var alpha: Float,
        val color: Int,
        val strokeWidth: Float,
    )

    private data class Streak(
        val x: Float,
        var y: Float,
        val width: Float,
        val height: Float,
        val alpha: Float,
        val velocity: Float,
    )

    private val random = java.util.Random()
    private val activeTargets = ArrayList<Target>()
    private val particles = ArrayList<Particle>()
    private val rings = ArrayList<Ring>()
    private val backgroundStreaks = ArrayList<Streak>()
    private val queuedSpawnSides = ArrayDeque<Int>()

    private var listener: Listener? = null
    private var languageCode: String = "zh"
    private var difficultyPreset = DifficultyPreset.Advanced
    private var sessionActive = false
    private var detectorReady = false
    private var hits = 0
    private var combo = 0
    private var bestCombo = 0
    private var caloriesBurned = 0f
    private var fatBurnGrams = 0f
    private var spawnCountdownMs = 260f
    private var lastSpawnSide = 0
    private var lastQueuedSpawnSide = 0
    private var currentSpawnRunSide = 0
    private var remainingSpawnRunCount = 0
    private var running = false
    private var lastFrameMs = 0L
    private var roundStartMs = 0L
    private var lastHitElapsedMs = 0L
    private var currentHint = idleHint()
    private var impactFlashAlpha = 0f
    private var impactFlashX = 0f
    private var impactFlashY = 0f
    private var impactFlashRadius = 0f
    private var impactFlashColor = Color.WHITE
    private var sidePulseAlpha = 0f
    private var sidePulseSide = 0

    private val toneGenerator by lazy(LazyThreadSafetyMode.NONE) {
        ToneGenerator(AudioManager.STREAM_MUSIC, 95)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DFF6FF")
        textAlign = Paint.Align.CENTER
    }
    private val targetStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(dp(18f))
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(dp(8f))
    }

    fun bind(listener: Listener) {
        this.listener = listener
        publishOverlaySnapshot()
        publishHint(currentHint)
    }

    fun setLanguageCode(value: String) {
        languageCode = value.ifBlank { "zh" }
        publishOverlaySnapshot()
        publishHint(currentHint.ifBlank { idleHint() })
        invalidate()
    }

    fun setDifficultyPreset(value: DifficultyPreset) {
        difficultyPreset = value
        if (!sessionActive) {
            currentHint = readyHint()
            publishHint(currentHint)
        }
        invalidate()
    }

    fun currentSnapshot(): OverlaySnapshot =
        OverlaySnapshot(
            hits = hits,
            combo = combo,
            bestCombo = bestCombo,
            pps = currentPps(),
            calories = caloriesBurned,
            fatBurnGrams = fatBurnGrams,
        )

    fun beginTraining() {
        sessionActive = true
        detectorReady = false
        hits = 0
        combo = 0
        bestCombo = 0
        caloriesBurned = 0f
        fatBurnGrams = 0f
        roundStartMs = SystemClock.elapsedRealtime()
        lastHitElapsedMs = 0L
        spawnCountdownMs = nextInitialSpawnGapMs()
        lastSpawnSide = 0
        lastQueuedSpawnSide = 0
        currentSpawnRunSide = 0
        remainingSpawnRunCount = 0
        queuedSpawnSides.clear()
        ensureSpawnQueue()
        activeTargets.clear()
        particles.clear()
        rings.clear()
        impactFlashAlpha = 0f
        sidePulseAlpha = 0f
        currentHint = armingHint()
        publishOverlaySnapshot()
        publishHint(currentHint)
        invalidate()
    }

    fun endTraining() {
        sessionActive = false
        detectorReady = false
        spawnCountdownMs = 260f
        lastSpawnSide = 0
        lastQueuedSpawnSide = 0
        currentSpawnRunSide = 0
        remainingSpawnRunCount = 0
        queuedSpawnSides.clear()
        activeTargets.clear()
        particles.clear()
        rings.clear()
        impactFlashAlpha = 0f
        sidePulseAlpha = 0f
        currentHint = finishedHint()
        publishHint(currentHint)
        invalidate()
    }

    fun updateDetectorState(type: String) {
        detectorReady = sessionActive && type == "ready"
        currentHint =
            when (type) {
                "ready" -> readyHint()
                "loading" -> armingHint()
                "calibrating" -> calibratingHint()
                "permission_denied" -> permissionHint()
                "error" -> errorHint()
                "finished" -> finishedHint()
                else -> idleHint()
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
        val target = activeTargets.firstOrNull { !it.hit && isTargetHittable(it) && it.matchesHand(hand) }
        if (target == null) {
            publishHint(waitingHint())
            return
        }
        handleHit(target, intensity.coerceIn(0.35f, 1.85f))
    }

    fun previewPunch(hand: BoxingBleManager.BoxingHand? = null) {
        if (!sessionActive || !detectorReady) {
            return
        }
        val target = activeTargets.firstOrNull { !it.hit && isTargetHittable(it) && it.matchesHand(hand) } ?: return
        val progress = (target.ageMs / target.liveMs).coerceIn(0f, 1f)
        val eased = 1f - (1f - progress) * (1f - progress)
        val impactX = lerp(target.startX, target.endX, eased)
        val impactY = target.y
        createImpactBurst(impactX, impactY, target, 0.55f)
        triggerImpactFlash(impactX, impactY, target.accentColor, target.side, 0.55f, combo)
        postInvalidateOnAnimation()
    }

    fun playCountdownPulse() {
        rings +=
            Ring(
                x = width * 0.5f,
                y = height * 0.5f,
                radius = min(width, height) * 0.10f,
                alpha = 0.92f,
                color = Color.WHITE,
                strokeWidth = dp(5.2f),
            )
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastFrameMs = 0L
        ensureBackgroundStreaks()
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        running = false
        lastFrameMs = 0L
        runCatching { toneGenerator.release() }
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        ensureBackgroundStreaks()
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

    private fun updateWorld(
        nowMs: Long,
        deltaMs: Float,
    ) {
        if (width <= 0 || height <= 0) {
            return
        }

        backgroundStreaks.forEach { streak ->
            streak.y += streak.velocity * deltaMs
            if (streak.y - streak.height > height + dp(40f)) {
                streak.y = -streak.height
            }
        }

        updateRandomSpawnCountdown(nowMs, deltaMs)

        val targetIterator = activeTargets.iterator()
        while (targetIterator.hasNext()) {
            val target = targetIterator.next()
            if (target.hit) {
                target.hitAgeMs += deltaMs
                if (target.hitAgeMs >= 360f) {
                    targetIterator.remove()
                    tightenNextSpawn(nextSpawnGapMs(shorter = true))
                }
                continue
            }

            target.ageMs += deltaMs
            if (target.ageMs >= target.liveMs + TARGET_SIGNAL_LATENCY_MS) {
                targetIterator.remove()
                combo = 0
                publishOverlaySnapshot()
                tightenNextSpawn(nextSpawnGapMs(shorter = false))
            }
        }

        val particleIterator = particles.iterator()
        while (particleIterator.hasNext()) {
            val particle = particleIterator.next()
            particle.x += particle.velocityX * deltaMs * 0.065f
            particle.y += particle.velocityY * deltaMs * 0.065f
            particle.velocityX *= 0.989f
            particle.velocityY = particle.velocityY * 0.986f + 0.028f
            particle.rotation += particle.spin * deltaMs
            particle.lifeMs += deltaMs
            if (particle.lifeMs >= particle.maxLifeMs) {
                particleIterator.remove()
            }
        }

        val ringIterator = rings.iterator()
        while (ringIterator.hasNext()) {
            val ring = ringIterator.next()
            ring.radius += deltaMs * 0.24f
            ring.alpha *= 0.965f
            if (ring.alpha <= 0.018f) {
                ringIterator.remove()
            }
        }

        if (impactFlashAlpha > 0f) {
            impactFlashAlpha *= 0.905f
            impactFlashRadius += deltaMs * 0.42f
            if (impactFlashAlpha <= 0.02f) {
                impactFlashAlpha = 0f
            }
        }

        if (sidePulseAlpha > 0f) {
            sidePulseAlpha *= 0.91f
            if (sidePulseAlpha <= 0.02f) {
                sidePulseAlpha = 0f
            }
        }
    }

    private fun drawWorld(
        canvas: Canvas,
        nowMs: Long,
    ) {
        val safeWidth = width.toFloat().coerceAtLeast(1f)
        val safeHeight = height.toFloat().coerceAtLeast(1f)

        val backgroundShader =
            LinearGradient(
                0f,
                0f,
                safeWidth,
                safeHeight,
                intArrayOf(
                    Color.parseColor("#07131F"),
                    Color.parseColor("#0E2236"),
                    Color.parseColor("#101A2A"),
                    Color.parseColor("#060C16"),
                ),
                floatArrayOf(0f, 0.35f, 0.72f, 1f),
                Shader.TileMode.CLAMP,
            )
        fillPaint.shader = backgroundShader
        canvas.drawRect(0f, 0f, safeWidth, safeHeight, fillPaint)
        fillPaint.shader = null

        drawBackgroundGrid(canvas, safeWidth, safeHeight)
        drawBackgroundStreaks(canvas)
        drawImpactOverlay(canvas, safeWidth, safeHeight)
        activeTargets.forEach { target -> drawTarget(canvas, target, nowMs) }
        rings.forEach { ring -> drawRing(canvas, ring) }
        particles.forEach { particle -> drawParticle(canvas, particle) }

    }

    private fun drawBackgroundGrid(
        canvas: Canvas,
        safeWidth: Float,
        safeHeight: Float,
    ) {
        strokePaint.color = Color.parseColor("#1A86D9FF")
        strokePaint.strokeWidth = dp(1f)
        var y = safeHeight * 0.14f
        while (y < safeHeight) {
            canvas.drawLine(0f, y, safeWidth, y, strokePaint)
            y += safeHeight * 0.11f
        }
        var x = safeWidth * 0.08f
        while (x < safeWidth) {
            canvas.drawLine(x, safeHeight * 0.12f, x, safeHeight, strokePaint)
            x += safeWidth * 0.12f
        }
    }

    private fun drawBackgroundStreaks(canvas: Canvas) {
        backgroundStreaks.forEach { streak ->
            val shader =
                LinearGradient(
                    streak.x,
                    streak.y,
                    streak.x,
                    streak.y + streak.height,
                    intArrayOf(Color.TRANSPARENT, Color.parseColor("#3FF6FBFF"), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP,
                )
            fillPaint.shader = shader
            fillPaint.alpha = (streak.alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(
                RectF(streak.x - streak.width * 0.5f, streak.y, streak.x + streak.width * 0.5f, streak.y + streak.height),
                dp(18f),
                dp(18f),
                fillPaint,
            )
            fillPaint.shader = null
        }
        fillPaint.alpha = 255
    }

    private fun drawImpactOverlay(
        canvas: Canvas,
        safeWidth: Float,
        safeHeight: Float,
    ) {
        if (sidePulseAlpha > 0f && sidePulseSide != 0) {
            val edgeWidth = safeWidth * 0.34f
            val fromX = if (sidePulseSide < 0) 0f else safeWidth
            val toX = if (sidePulseSide < 0) edgeWidth else safeWidth - edgeWidth
            fillPaint.shader =
                LinearGradient(
                    fromX,
                    0f,
                    toX,
                    0f,
                    intArrayOf(
                        withAlpha(impactFlashColor, (sidePulseAlpha * 180f).toInt()),
                        withAlpha(impactFlashColor, (sidePulseAlpha * 40f).toInt()),
                        Color.TRANSPARENT,
                    ),
                    if (sidePulseSide < 0) floatArrayOf(0f, 0.48f, 1f) else floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP,
                )
            canvas.drawRect(0f, 0f, safeWidth, safeHeight, fillPaint)
            fillPaint.shader = null
        }

        if (impactFlashAlpha > 0f) {
            fillPaint.shader =
                RadialGradient(
                    impactFlashX,
                    impactFlashY,
                    impactFlashRadius.coerceAtLeast(dp(30f)),
                    intArrayOf(
                        withAlpha(Color.WHITE, (impactFlashAlpha * 210f).toInt()),
                        withAlpha(impactFlashColor, (impactFlashAlpha * 116f).toInt()),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 0.32f, 1f),
                    Shader.TileMode.CLAMP,
                )
            canvas.drawCircle(impactFlashX, impactFlashY, impactFlashRadius.coerceAtLeast(dp(30f)), fillPaint)
            fillPaint.shader = null
        }
    }

    private fun drawTarget(
        canvas: Canvas,
        target: Target,
        nowMs: Long,
    ) {
        val progress = (target.ageMs / target.liveMs).coerceIn(0f, 1f)
        val eased = 1f - (1f - progress) * (1f - progress)
        val centerX = lerp(target.startX, target.endX, eased)
        val pulse = 1f + sin((nowMs * 0.010 + target.pulseOffset).toDouble()).toFloat() * 0.05f
        val alpha = if (target.hit) (1f - target.hitAgeMs / 360f).coerceIn(0f, 1f) else 1f
        val targetRect =
            RectF(
                centerX - target.width * 0.5f * pulse,
                target.y - target.height * 0.5f * pulse,
                centerX + target.width * 0.5f * pulse,
                target.y + target.height * 0.5f * pulse,
            )

        drawTargetTrail(canvas, target, centerX, targetRect, alpha)

        shadowPaint.shader =
            RadialGradient(
                centerX,
                target.y,
                max(target.width, target.height) * 0.82f,
                intArrayOf(withAlpha(target.accentColor, 130), withAlpha(target.accentColor, 26), Color.TRANSPARENT),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRoundRect(
            RectF(
                targetRect.left - dp(12f),
                targetRect.top - dp(12f),
                targetRect.right + dp(12f),
                targetRect.bottom + dp(12f),
            ),
            dp(28f),
            dp(28f),
            shadowPaint,
        )
        shadowPaint.shader = null

        val targetShader =
            LinearGradient(
                targetRect.left,
                targetRect.top,
                targetRect.right,
                targetRect.bottom,
                intArrayOf(
                    Color.parseColor("#11263B"),
                    target.coreColor,
                    target.accentColor,
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        fillPaint.shader = targetShader
        fillPaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(targetRect, dp(26f), dp(26f), fillPaint)
        fillPaint.shader = null

        strokePaint.color = withAlpha(Color.WHITE, (alpha * 160f).toInt())
        strokePaint.strokeWidth = dp(1.8f)
        canvas.drawRoundRect(targetRect, dp(26f), dp(26f), strokePaint)

        strokePaint.color = withAlpha(target.accentColor, (alpha * 255f).toInt())
        strokePaint.strokeWidth = dp(if (detectorReady) 4.2f else 2.8f)
        canvas.drawRoundRect(
            RectF(
                targetRect.left + dp(6f),
                targetRect.top + dp(6f),
                targetRect.right - dp(6f),
                targetRect.bottom - dp(6f),
            ),
            dp(20f),
            dp(20f),
            strokePaint,
        )

        drawTargetCore(canvas, targetRect, target.accentColor, alpha)
        drawTargetBolt(canvas, targetRect, target.accentColor, alpha)
    }

    private fun drawTargetTrail(
        canvas: Canvas,
        target: Target,
        centerX: Float,
        targetRect: RectF,
        alpha: Float,
    ) {
        val trailLength = target.width * 0.92f
        val direction = if (target.side < 0) -1f else 1f
        repeat(4) { index ->
            val segmentAlpha = (alpha * (0.42f - index * 0.08f)).coerceAtLeast(0f)
            if (segmentAlpha <= 0f) {
                return@repeat
            }
            val offset = (index + 1) * target.width * 0.15f * direction
            fillPaint.color = withAlpha(target.accentColor, (segmentAlpha * 255f).toInt())
            canvas.drawRoundRect(
                RectF(
                    centerX - target.width * 0.18f + offset - trailLength * 0.30f,
                    targetRect.centerY() - target.height * 0.13f,
                    centerX + target.width * 0.18f + offset,
                    targetRect.centerY() + target.height * 0.13f,
                ),
                dp(18f),
                dp(18f),
                fillPaint,
            )
        }
    }

    private fun drawTargetCore(
        canvas: Canvas,
        targetRect: RectF,
        accentColor: Int,
        alpha: Float,
    ) {
        val centerX = targetRect.centerX()
        val centerY = targetRect.centerY()
        fillPaint.color = withAlpha(Color.WHITE, (alpha * 108f).toInt())
        canvas.drawCircle(centerX, centerY, targetRect.width() * 0.10f, fillPaint)
        strokePaint.color = withAlpha(Color.parseColor("#D8FCFF"), (alpha * 200f).toInt())
        strokePaint.strokeWidth = dp(3f)
        canvas.drawCircle(centerX, centerY, targetRect.width() * 0.18f, strokePaint)
        strokePaint.color = withAlpha(accentColor, (alpha * 255f).toInt())
        strokePaint.strokeWidth = dp(2f)
        canvas.drawCircle(centerX, centerY, targetRect.width() * 0.28f, strokePaint)
        canvas.drawLine(targetRect.left + dp(18f), centerY, targetRect.right - dp(18f), centerY, strokePaint)
        canvas.drawLine(centerX, targetRect.top + dp(18f), centerX, targetRect.bottom - dp(18f), strokePaint)
    }

    private fun drawTargetBolt(
        canvas: Canvas,
        targetRect: RectF,
        accentColor: Int,
        alpha: Float,
    ) {
        val boltPath =
            Path().apply {
                moveTo(targetRect.centerX() - targetRect.width() * 0.10f, targetRect.top + targetRect.height() * 0.18f)
                lineTo(targetRect.centerX() + targetRect.width() * 0.02f, targetRect.top + targetRect.height() * 0.18f)
                lineTo(targetRect.centerX() - targetRect.width() * 0.03f, targetRect.centerY())
                lineTo(targetRect.centerX() + targetRect.width() * 0.13f, targetRect.centerY())
                lineTo(targetRect.centerX() - targetRect.width() * 0.04f, targetRect.bottom - targetRect.height() * 0.16f)
                lineTo(targetRect.centerX() + targetRect.width() * 0.02f, targetRect.centerY() + targetRect.height() * 0.08f)
                lineTo(targetRect.centerX() - targetRect.width() * 0.12f, targetRect.centerY() + targetRect.height() * 0.08f)
                close()
            }
        fillPaint.color = withAlpha(accentColor, (alpha * 220f).toInt())
        canvas.drawPath(boltPath, fillPaint)
        strokePaint.color = withAlpha(Color.WHITE, (alpha * 210f).toInt())
        strokePaint.strokeWidth = dp(1.6f)
        canvas.drawPath(boltPath, strokePaint)
    }

    private fun drawRing(
        canvas: Canvas,
        ring: Ring,
    ) {
        strokePaint.color = withAlpha(ring.color, (ring.alpha * 255f).toInt())
        strokePaint.strokeWidth = ring.strokeWidth
        canvas.drawCircle(ring.x, ring.y, ring.radius, strokePaint)
    }

    private fun drawParticle(
        canvas: Canvas,
        particle: Particle,
    ) {
        val alpha = (1f - particle.lifeMs / particle.maxLifeMs).coerceIn(0f, 1f)
        particlePaint.color = withAlpha(particle.color, (alpha * 255f).toInt())
        canvas.save()
        canvas.translate(particle.x, particle.y)
        canvas.rotate(particle.rotation)
        canvas.drawRoundRect(
            RectF(
                -particle.radius * 0.55f * particle.stretch,
                -particle.radius * 0.38f,
                particle.radius * 0.55f * particle.stretch,
                particle.radius * 0.38f,
            ),
            particle.radius * 0.18f,
            particle.radius * 0.18f,
            particlePaint,
        )
        canvas.restore()
    }

    private fun drawIdlePrompt(
        canvas: Canvas,
        safeWidth: Float,
        safeHeight: Float,
    ) {
        val title =
            localText(
                "极速燃脂",
                "Rapid Fat Burn",
                "Brûle-graisse express",
                "เผาผลาญเร่งด่วน",
            )
        val body =
            localText(
                "左右拳靶独立随机出现。\n提高拳击频率可提升消耗与燃脂。",
                "Targets appear independently at random.\nPush your punch rate to raise calories and fat burn.",
                "Les cibles apparaissent aléatoirement et indépendamment.\nAccélérez pour brûler plus de calories et de graisse.",
                "เป้าซ้ายขวาจะสุ่มแยกกันอย่างอิสระ\nยิ่งชกถี่ ยิ่งช่วยเพิ่มการใช้พลังงานและการเผาผลาญไขมัน",
            )
        textPaint.textSize = min(safeWidth, safeHeight) * 0.064f
        textPaint.setShadowLayer(dp(12f), 0f, dp(4f), Color.parseColor("#AA00B7FF"))
        canvas.drawText(title, safeWidth * 0.5f, safeHeight * 0.22f, textPaint)
        smallTextPaint.textSize = min(safeWidth, safeHeight) * 0.026f
        smallTextPaint.alpha = 215
        canvas.drawText(body.lines().first(), safeWidth * 0.5f, safeHeight * 0.29f, smallTextPaint)
        canvas.drawText(body.lines().last(), safeWidth * 0.5f, safeHeight * 0.33f, smallTextPaint)
    }

    private fun updateRandomSpawnCountdown(
        nowMs: Long,
        deltaMs: Float,
    ) {
        if (!sessionActive || activeTargets.count { !it.hit } >= MAX_ACTIVE_TARGETS) {
            return
        }
        val nextCountdown = spawnCountdownMs - deltaMs
        if (nextCountdown <= 0f) {
            val side = chooseNextSpawnSide()
            if (side != 0) {
                spawnTarget(nowMs, side)
            }
            spawnCountdownMs = nextSpawnGapMs(shorter = false)
        } else {
            spawnCountdownMs = nextCountdown
        }
    }

    private fun chooseNextSpawnSide(): Int {
        val leftCount = activeTargets.count { !it.hit && it.side < 0 }
        val rightCount = activeTargets.count { !it.hit && it.side > 0 }
        val leftAvailable = leftCount < MAX_ACTIVE_TARGETS_PER_SIDE
        val rightAvailable = rightCount < MAX_ACTIVE_TARGETS_PER_SIDE
        if (!leftAvailable && !rightAvailable) {
            return 0
        }
        ensureSpawnQueue()
        val preferred = queuedSpawnSides.pollFirst() ?: randomSide()
        ensureSpawnQueue()
        val side =
            when {
                preferred < 0 && leftAvailable -> -1
                preferred > 0 && rightAvailable -> 1
                leftAvailable && !rightAvailable -> -1
                rightAvailable && !leftAvailable -> 1
                random.nextBoolean() -> -1
                else -> 1
            }
        lastSpawnSide = side
        return side
    }

    private fun ensureSpawnQueue() {
        while (queuedSpawnSides.size < SPAWN_QUEUE_PREVIEW_COUNT) {
            queuedSpawnSides.add(nextQueuedSpawnSide())
        }
    }

    private fun nextQueuedSpawnSide(): Int {
        if (remainingSpawnRunCount <= 0 || currentSpawnRunSide == 0) {
            startNextSpawnRun()
        }
        remainingSpawnRunCount -= 1
        lastQueuedSpawnSide = currentSpawnRunSide
        return currentSpawnRunSide
    }

    private fun startNextSpawnRun() {
        val singleMode = random.nextFloat() < SINGLE_SPAWN_RUN_PROBABILITY
        val anchorSide = if (lastQueuedSpawnSide != 0) lastQueuedSpawnSide else lastSpawnSide
        currentSpawnRunSide =
            if (anchorSide == 0) {
                randomSide()
            } else if (singleMode) {
                -anchorSide
            } else if (random.nextFloat() < MULTI_RUN_SWITCH_SIDE_PROBABILITY) {
                -anchorSide
            } else {
                anchorSide
            }
        remainingSpawnRunCount =
            if (singleMode) {
                1
            } else {
                2 + random.nextInt(MAX_MULTI_SPAWN_RUN_LENGTH - 1)
            }
    }

    private fun randomSide(): Int = if (random.nextBoolean()) -1 else 1

    private fun tightenNextSpawn(gapMs: Float) {
        spawnCountdownMs = min(spawnCountdownMs, gapMs)
    }

    private fun spawnTarget(
        nowMs: Long,
        side: Int,
    ) {
        if (width <= 0 || height <= 0) {
            return
        }
        val targetWidth = min(width, height) * 0.17f
        val targetHeight = targetWidth * 1.08f
        val laneCenter =
            when (random.nextInt(3)) {
                0 -> 0.40f
                1 -> 0.48f
                else -> 0.56f
            }
        val y = height * (laneCenter + (random.nextFloat() - 0.5f) * 0.05f)
        val travel = width * difficultyPreset.travelFraction
        val sideInset = width * 0.17f
        val startX =
            if (side < 0) {
                sideInset
            } else {
                width - sideInset
            }
        val endX =
            if (side < 0) {
                startX + travel
            } else {
                startX - travel
            }
        val accentColor =
            if (side < 0) {
                Color.parseColor("#41E6FF")
            } else {
                Color.parseColor("#FFB035")
            }
        val coreColor =
            if (side < 0) {
                Color.parseColor("#1568AE")
            } else {
                Color.parseColor("#A24D0E")
            }
        activeTargets +=
            Target(
                id = nowMs,
                side = side,
                y = y,
                width = targetWidth,
                height = targetHeight,
                startX = startX,
                endX = endX,
                bornAtMs = nowMs,
                liveMs = difficultyPreset.targetLifeMs,
                accentColor = accentColor,
                coreColor = coreColor,
                pulseOffset = random.nextFloat() * (Math.PI.toFloat() * 2f),
            )
        publishHint(readyHint())
    }

    private fun nextInitialSpawnGapMs(): Float {
        val base = nextSpawnGapMs(shorter = false)
        return (base * (0.35f + random.nextFloat() * 1.75f)).coerceAtLeast(60f)
    }

    private fun nextSpawnGapMs(shorter: Boolean): Float {
        val minGap = if (shorter) difficultyPreset.minHitSpawnGapMs else difficultyPreset.minSpawnGapMs
        val maxGap = if (shorter) difficultyPreset.maxHitSpawnGapMs else difficultyPreset.maxSpawnGapMs
        val span = maxGap - minGap
        val base = minGap + random.nextFloat() * span
        val independentJitter = 0.55f + random.nextFloat() * 1.85f
        val randomHold =
            when {
                shorter && random.nextFloat() < 0.18f -> 1.35f + random.nextFloat() * 1.10f
                !shorter && random.nextFloat() < 0.30f -> 1.55f + random.nextFloat() * 1.85f
                else -> 1f
            }
        return (base * independentJitter * randomHold).coerceAtLeast(40f)
    }

    private fun isTargetHittable(target: Target): Boolean =
        sessionActive && detectorReady && !target.hit && target.ageMs <= target.liveMs + TARGET_SIGNAL_LATENCY_MS

    private fun Target.matchesHand(hand: BoxingBleManager.BoxingHand?): Boolean =
        when (hand) {
            BoxingBleManager.BoxingHand.Left -> side < 0
            BoxingBleManager.BoxingHand.Right -> side > 0
            null -> false
        }

    private fun handleHit(
        target: Target,
        intensity: Float,
    ) {
        target.hit = true
        val now = SystemClock.elapsedRealtime()
        val sinceLastHit = if (lastHitElapsedMs == 0L) Long.MAX_VALUE else now - lastHitElapsedMs
        combo = if (sinceLastHit <= 950L) combo + 1 else 1
        bestCombo = max(bestCombo, combo)
        hits += 1
        lastHitElapsedMs = now

        val pps = currentPps().coerceAtLeast(0.1f)
        val calorieGain =
            difficultyPreset.hitCalories *
                (0.84f + intensity * 0.34f + min(pps, 4.8f) * 0.08f + min(combo.toFloat(), 12f) * 0.012f)
        caloriesBurned += calorieGain
        fatBurnGrams = caloriesBurned * difficultyPreset.fatBurnRatio
        publishOverlaySnapshot()
        publishHint(successHint())

        val progress = (target.ageMs / target.liveMs).coerceIn(0f, 1f)
        val eased = 1f - (1f - progress) * (1f - progress)
        val impactX = lerp(target.startX, target.endX, eased)
        val impactY = target.y
        createImpactBurst(impactX, impactY, target, intensity)
        triggerImpactFlash(impactX, impactY, target.accentColor, target.side, intensity, combo)

        Haptics.tap(context)
        playImpactTone(intensity, combo)
    }

    private fun triggerImpactFlash(
        impactX: Float,
        impactY: Float,
        accentColor: Int,
        side: Int,
        intensity: Float,
        comboCount: Int,
    ) {
        val power = intensity.coerceIn(0.35f, 1.85f)
        impactFlashX = impactX
        impactFlashY = impactY
        impactFlashColor = accentColor
        impactFlashRadius = dp(54f) + power * dp(24f) + min(comboCount.toFloat(), 8f) * dp(3f)
        impactFlashAlpha = (0.68f + power * 0.14f + min(comboCount.toFloat(), 6f) * 0.02f).coerceAtMost(1f)
        sidePulseSide = side
        sidePulseAlpha = (0.26f + power * 0.18f).coerceAtMost(0.72f)
    }

    private fun playImpactTone(
        intensity: Float,
        comboCount: Int,
    ) {
        val power = intensity.coerceIn(0.35f, 1.85f)
        val primaryDuration = (92f + power * 42f).toInt()
        val accentDuration = (60f + power * 24f).toInt()
        val finishDuration = (48f + power * 18f).toInt()
        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, primaryDuration)
        postDelayed(
            {
                runCatching {
                    toneGenerator.startTone(
                        if (comboCount >= 6) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_BEEP2,
                        accentDuration,
                    )
                }
            },
            22L,
        )
        if (power >= 1.0f || comboCount >= 4) {
            postDelayed(
                {
                    runCatching {
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, finishDuration)
                    }
                },
                58L,
            )
        }
        if (comboCount >= 8) {
            postDelayed(
                {
                    runCatching {
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 36)
                    }
                },
                104L,
            )
        }
    }

    private fun createImpactBurst(
        impactX: Float,
        impactY: Float,
        target: Target,
        intensity: Float,
    ) {
        val power = intensity.coerceIn(0.45f, 1.85f)
        val shardCount = 38 + (power * 30f).toInt()
        repeat(shardCount) { index ->
            val angle = (Math.PI * 2.0 * index / shardCount) + (random.nextFloat() - 0.5f) * 0.52f
            val speed = 8.0f + random.nextFloat() * 28f * power
            particles +=
                Particle(
                    x = impactX,
                    y = impactY,
                    velocityX = cos(angle).toFloat() * speed,
                    velocityY = sin(angle).toFloat() * speed - 3.2f,
                    radius = 5.2f + random.nextFloat() * 9.6f,
                    maxLifeMs = 360f + random.nextFloat() * 420f,
                    color = if (index % 5 == 0) Color.WHITE else target.accentColor,
                    rotation = random.nextFloat() * 360f,
                    spin = -0.82f + random.nextFloat() * 1.64f,
                    stretch = 1.0f + random.nextFloat() * 2.1f,
                )
        }
        repeat(24) {
            val angle = random.nextFloat() * (Math.PI.toFloat() * 2f)
            val speed = 3.4f + random.nextFloat() * 14.5f * power
            particles +=
                Particle(
                    x = impactX,
                    y = impactY,
                    velocityX = cos(angle.toDouble()).toFloat() * speed,
                    velocityY = sin(angle.toDouble()).toFloat() * speed - 1.8f,
                    radius = 9.8f + random.nextFloat() * 16f,
                    maxLifeMs = 420f + random.nextFloat() * 380f,
                    color = Color.parseColor("#D5F8FF"),
                    rotation = random.nextFloat() * 360f,
                    spin = -0.44f + random.nextFloat() * 0.88f,
                    stretch = 0.9f + random.nextFloat() * 1.1f,
                )
        }
        rings +=
            Ring(
                x = impactX,
                y = impactY,
                radius = dp(24f),
                alpha = 0.92f,
                color = target.accentColor,
                strokeWidth = dp(4.5f),
            )
        rings +=
            Ring(
                x = impactX,
                y = impactY,
                radius = dp(10f),
                alpha = 0.88f,
                color = Color.WHITE,
                strokeWidth = dp(3f),
            )
        rings +=
            Ring(
                x = impactX,
                y = impactY,
                radius = dp(36f),
                alpha = 0.62f,
                color = Color.parseColor("#DFF8FF"),
                strokeWidth = dp(2.4f),
            )
    }

    private fun currentPps(): Float {
        if (roundStartMs == 0L || hits <= 0) {
            return 0f
        }
        val elapsedSeconds = ((SystemClock.elapsedRealtime() - roundStartMs).coerceAtLeast(1L)) / 1000f
        return hits / elapsedSeconds
    }

    private fun ensureBackgroundStreaks() {
        if (width <= 0 || height <= 0) {
            return
        }
        backgroundStreaks.clear()
        repeat(8) { index ->
            backgroundStreaks +=
                Streak(
                    x = width * (0.12f + index * 0.11f + random.nextFloat() * 0.05f),
                    y = -height * random.nextFloat(),
                    width = dp(6f) + random.nextFloat() * dp(10f),
                    height = height * (0.18f + random.nextFloat() * 0.22f),
                    alpha = 0.08f + random.nextFloat() * 0.14f,
                    velocity = 0.020f + random.nextFloat() * 0.020f,
                )
        }
    }

    private fun publishOverlaySnapshot() {
        listener?.onOverlaySnapshot(currentSnapshot())
    }

    private fun publishHint(value: String) {
        currentHint = value
        listener?.onHintChanged(value)
    }

    private fun idleHint(): String =
        localText(
            "选择时长和强度，然后开始极速燃脂。",
            "Choose a duration and difficulty, then start Rapid Fat Burn.",
            "Choisissez une durée et une intensité, puis lancez Brûle-graisse express.",
            "เลือกเวลาและความเข้มข้น แล้วเริ่มโหมดเผาผลาญเร่งด่วน",
        )

    private fun armingHint(): String =
        localText(
            "蓝牙击中识别正在就绪，听到开始后全力冲刺。",
            "Hit detection is arming. Explode into pace after GO.",
            "La détection se prépare. Accélérez dès le signal de départ.",
            "ระบบกำลังเตรียมพร้อมตรวจจับเสียง เมื่อได้ยินเริ่มให้เร่งเต็มที่",
        )

    private fun calibratingHint(): String =
        localText(
            "正在校准环境声音，请保持准备。",
            "Preparing Bluetooth hit detection. Stay ready.",
            "Calibration audio en cours. Restez prêt.",
            "กำลังปรับเทียบเสียงรอบข้าง โปรดเตรียมพร้อม",
        )

    private fun readyHint(): String =
        localText(
            "左右靶位将独立随机出现，节奏越快，消耗越高。",
            "Targets appear independently at random. Higher pace means brighter calorie burn.",
            "Les cibles apparaissent aléatoirement et indépendamment. Plus le rythme est élevé, plus vous brûlez.",
            "เป้าจะสุ่มซ้ายขวาแยกกัน ยิ่งจังหวะเร็ว การเผาผลาญยิ่งสูง",
        )

    private fun waitingHint(): String =
        localText(
            "下一个拳靶尚未到位，请保持节奏。",
            "The next target is not ready yet. Hold your rhythm.",
            "La prochaine cible n'est pas encore prête. Gardez le rythme.",
            "เป้าถัดไปยังไม่พร้อม โปรดรักษาจังหวะไว้",
        )

    private fun successHint(): String =
        localText(
            "击中漂亮，继续提速。",
            "Clean hit. Keep accelerating.",
            "Impact propre. Continuez à accélérer.",
            "ชกเข้าอย่างสวยงาม เร่งต่อได้เลย",
        )

    private fun permissionHint(): String =
        localText(
            "极速燃脂需要蓝牙设备权限。",
            "Bluetooth connection is required for Rapid Fat Burn.",
            "La connexion Bluetooth est requise pour Brûle-graisse express.",
            "โหมดเผาผลาญเร่งด่วนต้องใช้สิทธิ์ไมโครโฟน",
        )

    private fun errorHint(): String =
        localText(
            "蓝牙击中识别暂时中断，正在恢复。",
            "Bluetooth hit detection paused for a moment. Recovering now.",
            "La détection audio est brièvement interrompue. Récupération en cours.",
            "การตรวจจับเสียงหยุดชั่วคราว กำลังกู้คืน",
        )

    private fun finishedHint(): String =
        localText(
            "本轮结束，HIIT 结果已生成。",
            "Round finished. Your HIIT results are ready.",
            "Le tour est terminé. Vos résultats HIIT sont prêts.",
            "จบรอบแล้ว ผลลัพธ์ HIIT พร้อมแล้ว",
        )

    private fun localText(
        zh: String,
        en: String,
        fr: String = en,
        th: String = en,
    ): String =
        when {
            languageCode.startsWith("zh") -> zh
            languageCode.startsWith("fr") -> fr
            languageCode.startsWith("th") -> th
            else -> en
        }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    private fun lerp(
        start: Float,
        end: Float,
        progress: Float,
    ): Float = start + (end - start) * progress

    private fun withAlpha(
        color: Int,
        alpha: Int,
    ): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val TARGET_SIGNAL_LATENCY_MS = 250f
        const val MAX_ACTIVE_TARGETS = 1
        const val MAX_ACTIVE_TARGETS_PER_SIDE = 1
        const val SPAWN_QUEUE_PREVIEW_COUNT = 4
        const val SINGLE_SPAWN_RUN_PROBABILITY = 0.50f
        const val MULTI_RUN_SWITCH_SIDE_PROBABILITY = 0.72f
        const val MAX_MULTI_SPAWN_RUN_LENGTH = 4
    }
}


