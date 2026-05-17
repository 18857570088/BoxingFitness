package com.zclei.boxingfitness

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
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
import kotlin.math.sin

private data class EmotionMonsterSpec(
    val id: String,
    val zhName: String,
    val enName: String,
)

private data class EmotionMonsterAsset(
    val spec: EmotionMonsterSpec,
    val variants: List<Bitmap>,
) {
    fun label(languageCode: String): String =
        if (languageCode.startsWith("zh")) spec.zhName else spec.enName

    fun randomBitmap(random: java.util.Random): Bitmap? =
        if (variants.isEmpty()) {
            null
        } else {
            variants[random.nextInt(variants.size)]
        }
}

private object EmotionMonsterCatalog {
    private val specs =
        listOf(
            EmotionMonsterSpec("tuoyan", "\u62d6\u5ef6", "Procrastination"),
            EmotionMonsterSpec("fennu", "\u6124\u6012", "Anger"),
            EmotionMonsterSpec("jiaolv", "\u7126\u8651", "Anxiety"),
            EmotionMonsterSpec("yali", "\u538b\u529b", "Stress"),
            EmotionMonsterSpec("neihao", "\u5185\u8017", "Overthinking"),
            EmotionMonsterSpec("shekong", "\u793e\u6050", "Social Fear"),
            EmotionMonsterSpec("zibei", "\u81ea\u5351", "Insecurity"),
            EmotionMonsterSpec("shimian", "\u5931\u7720", "Insomnia"),
            EmotionMonsterSpec("kongxu", "\u7a7a\u865a", "Emptiness"),
            EmotionMonsterSpec("pibei", "\u75b2\u60eb", "Fatigue"),
            EmotionMonsterSpec("bijiao", "\u6bd4\u8f83", "Comparison"),
            EmotionMonsterSpec("houhui", "\u540e\u6094", "Regret"),
            EmotionMonsterSpec("youyu", "\u72b9\u8c6b", "Hesitation"),
            EmotionMonsterSpec("gudu", "\u5b64\u72ec", "Loneliness"),
            EmotionMonsterSpec("kongzhi", "\u63a7\u5236", "Control"),
            EmotionMonsterSpec("neijiu", "\u5185\u75da", "Guilt"),
            EmotionMonsterSpec("wanmei", "\u5b8c\u7f8e\u4e3b\u4e49", "Perfectionism"),
            EmotionMonsterSpec("jinrong", "\u91d1\u878d\u7126\u8651", "Money Anxiety"),
            EmotionMonsterSpec("shijian", "\u65f6\u95f4\u7126\u8651", "Time Anxiety"),
        )

    fun load(context: Context): List<EmotionMonsterAsset> =
        specs.map { spec ->
            val folder = "emotion_monsters/Images/${spec.id}"
            val fileNames =
                runCatching {
                    context.assets.list(folder)?.toList().orEmpty()
                }.getOrDefault(emptyList())
                    .filter { fileName ->
                        val lower = fileName.lowercase()
                        lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
                    }
                    .sorted()
            val bitmaps =
                fileNames.mapNotNull { fileName ->
                    decodeScaledAsset(context, "$folder/$fileName")
                }
            EmotionMonsterAsset(spec, bitmaps)
        }

    private fun decodeScaledAsset(
        context: Context,
        assetPath: String,
        maxDimension: Int = 420,
    ): Bitmap? {
        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        runCatching {
            context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
        }.getOrNull()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        return runCatching {
            context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        }.getOrNull()?.let(::removeNearWhiteBackground)
    }

    private fun removeNearWhiteBackground(source: Bitmap): Bitmap {
        val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return bitmap
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            val max = max(red, max(green, blue))
            val min = min(red, min(green, blue))
            val saturation = max - min
            if (max >= 236 && saturation <= 24) {
                val alpha =
                    when {
                        max >= 248 -> 0
                        max >= 242 -> 22
                        else -> 48
                    }
                pixels[index] = Color.argb(alpha, red, green, blue)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}

internal class EmotionChampGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    enum class SpeedPreset(
        val velocity: Float,
        val gapMs: Float,
        val zhLabel: String,
        val enLabel: String,
    ) {
        Slow(0.54f, 890f, "\u6162\u901f", "Slow"),
        Medium(0.637f, 540f, "\u4e2d\u901f", "Mid"),
        Fast(1.118f, 280f, "\u5feb\u901f", "Fast"),
        ;

        fun label(languageCode: String): String =
            if (languageCode.startsWith("zh")) zhLabel else enLabel
    }

    enum class DensityPreset(
        val count: Int,
        val gapScale: Float,
    ) {
        Solo(1, 1.60f),
        Duo(1, 0.613f),
        Storm(1, 0.507f),
        ;
    }

    data class OverlaySnapshot(
        val hits: Int,
        val combo: Int,
        val misses: Int,
        val relief: Float,
        val paceLabel: String,
    )

    interface Listener {
        fun onOverlaySnapshot(snapshot: OverlaySnapshot)

        fun onHintChanged(hint: String)

        fun onMotivationCue(message: String)
    }

    private data class MonsterPalette(
        val shellColor: Int,
        val strokeColor: Int,
        val glowColor: Int,
    )

    private data class MonsterTarget(
        val side: Int,
        var x: Float,
        var y: Float,
        val radius: Float,
        val velocityY: Float,
        val drift: Float,
        var wobble: Float,
        val wobbleSpeed: Float,
        val bornAtMs: Long,
        val palette: MonsterPalette,
        val monster: EmotionMonsterAsset,
        val bitmap: Bitmap?,
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
        val shard: Boolean = true,
    )

    private data class Ring(
        val x: Float,
        val y: Float,
        var radius: Float,
        var alpha: Float,
        val color: Int,
        val strokeWidth: Float,
    )

    private data class FloatingText(
        val text: String,
        val x: Float,
        var y: Float,
        val maxLifeMs: Float,
        var lifeMs: Float = 0f,
    )

    private data class Cloud(
        var x: Float,
        var y: Float,
        val width: Float,
        val height: Float,
        val velocity: Float,
        val alpha: Float,
    )

    private val monsterPalettes =
        listOf(
            MonsterPalette(Color.parseColor("#32D8F1FF"), Color.parseColor("#8DE5FF"), Color.parseColor("#4CA7E8FF")),
            MonsterPalette(Color.parseColor("#2FEFF6D8"), Color.parseColor("#98FFD0"), Color.parseColor("#54A8FFE0")),
            MonsterPalette(Color.parseColor("#34FFE2C7"), Color.parseColor("#FFCF87"), Color.parseColor("#58FFC868")),
            MonsterPalette(Color.parseColor("#30FFD3F5"), Color.parseColor("#F1A7FF"), Color.parseColor("#58C78BFF")),
            MonsterPalette(Color.parseColor("#36E8F9FF"), Color.parseColor("#A9F0FF"), Color.parseColor("#56FFFFFF")),
        )

    private val monsters = EmotionMonsterCatalog.load(context)
    private val activeTargets = ArrayList<MonsterTarget>()
    private val particles = ArrayList<Particle>()
    private val rings = ArrayList<Ring>()
    private val floatingTexts = ArrayList<FloatingText>()
    private val clouds = ArrayList<Cloud>()
    private val random = java.util.Random()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val nameTextPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
        }
    private val floatingTextPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
        }

    private var listener: Listener? = null
    private var languageCode = "zh"
    private var speedPreset = SpeedPreset.Medium
    private var densityPreset = DensityPreset.Duo
    private var hits = 0
    private var combo = 0
    private var misses = 0
    private var relief = 10f
    private var spawnCountdownMs = 180f
    private var slowMotionFactor = 1f
    private var impactFreezeMs = 0f
    private var detectorReady = false
    private var currentHint = readyHint()
    private var lastFrameMs = 0L
    private var running = false
    private var sessionActive = false
    private var fatalError: Throwable? = null
    private var fatalErrorReporter: ((Throwable) -> Unit)? = null

    private val toneGenerator by lazy(LazyThreadSafetyMode.NONE) {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    }

    fun setFatalErrorReporter(reporter: (Throwable) -> Unit) {
        fatalErrorReporter = reporter
    }

    fun bind(listener: Listener) {
        this.listener = listener
        publishOverlaySnapshot()
        publishHint(currentHint)
    }

    fun setLanguageCode(value: String) {
        languageCode = value.ifBlank { "zh" }
        publishOverlaySnapshot()
        publishHint(currentHint.ifBlank { readyHint() })
        invalidate()
    }

    fun setSpeedPreset(value: SpeedPreset) {
        if (speedPreset == value) {
            return
        }
        speedPreset = value
        publishOverlaySnapshot()
        invalidate()
    }

    fun setDensityPreset(value: DensityPreset) {
        densityPreset = value
    }

    fun beginTraining() {
        runSafely {
            sessionActive = true
            detectorReady = false
            hits = 0
            combo = 0
            misses = 0
            relief = 10f
            spawnCountdownMs = 180f
            slowMotionFactor = 1f
            impactFreezeMs = 0f
            activeTargets.clear()
            particles.clear()
            rings.clear()
            floatingTexts.clear()
            publishOverlaySnapshot()
            publishHint(armingHint())
            invalidate()
        }
    }

    fun endTraining() {
        runSafely {
            sessionActive = false
            detectorReady = false
            spawnCountdownMs = 540f
            slowMotionFactor = 1f
            impactFreezeMs = 0f
            activeTargets.clear()
            particles.clear()
            rings.clear()
            floatingTexts.clear()
            invalidate()
        }
    }

    fun setPersona(index: Int) {
        // Emotion Champ no longer uses boxer avatars.
    }

    fun playCountdownPunch() {
        rings +=
            Ring(
                x = width * 0.5f,
                y = height * 0.52f,
                radius = min(width, height) * 0.08f,
                alpha = 0.95f,
                color = Color.WHITE,
                strokeWidth = 6f,
            )
        invalidate()
    }

    fun updateDetectorState(type: String) {
        runSafely {
            detectorReady = sessionActive && type == "ready"
            when (type) {
                "ready" -> publishHint(readyHint())
                "loading" -> publishHint(armingHint())
                "calibrating" -> publishHint(calibratingHint())
                "permission_denied" -> publishHint(permissionHint())
                "error" -> publishHint(errorHint())
                "idle" -> publishHint(idleHint())
                "finished" -> publishHint(finishedHint())
            }
        }
    }

    fun registerPunch(intensity: Float) {
        runSafely {
            if (!sessionActive) {
                return@runSafely
            }
            handlePunch(SystemClock.elapsedRealtime(), intensity.coerceIn(0.35f, 1.95f))
        }
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

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            rebuildClouds()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (fatalError != null) {
            drawFallback(canvas)
            return
        }
        runSafely {
            val now = SystemClock.elapsedRealtime()
            val deltaMs =
                when {
                    lastFrameMs == 0L -> 16f
                    else -> min(34f, (now - lastFrameMs).toFloat())
                }
            lastFrameMs = now
            updateWorld(now, deltaMs)
            drawWorld(canvas, now)
        }
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

        slowMotionFactor += (1f - slowMotionFactor) * 0.1f
        var worldDeltaMs = deltaMs * slowMotionFactor
        if (impactFreezeMs > 0f) {
            impactFreezeMs = max(0f, impactFreezeMs - deltaMs)
            worldDeltaMs = 0f
        }

        spawnCountdownMs -= worldDeltaMs
        if (sessionActive && spawnCountdownMs <= 0f) {
            spawnBurst(nowMs)
            spawnCountdownMs = nextSpawnGapMs()
        }

        clouds.forEach { cloud ->
            cloud.x += cloud.velocity * deltaMs
            if (cloud.x - cloud.width > width + dp(24f)) {
                cloud.x = -cloud.width
            }
        }

        val targetIterator = activeTargets.iterator()
        while (targetIterator.hasNext()) {
            val target = targetIterator.next()
            target.wobble += target.wobbleSpeed * worldDeltaMs
            target.y += target.velocityY * worldDeltaMs
            target.x += target.drift * worldDeltaMs
            if (target.hit) {
                target.hitAgeMs += worldDeltaMs
                if (target.hitAgeMs >= 360f) {
                    targetIterator.remove()
                }
                continue
            }
            if (target.y - target.radius > height + dp(80f) + target.velocityY * HIT_SIGNAL_LATENCY_MS) {
                targetIterator.remove()
            }
        }

        val particleIterator = particles.iterator()
        while (particleIterator.hasNext()) {
            val particle = particleIterator.next()
            particle.x += particle.velocityX * worldDeltaMs * 0.065f
            particle.y += particle.velocityY * worldDeltaMs * 0.065f
            particle.velocityX *= 0.988f
            particle.velocityY = particle.velocityY * 0.988f + 0.022f
            particle.rotation += particle.spin * worldDeltaMs
            particle.lifeMs += worldDeltaMs
            if (particle.lifeMs >= particle.maxLifeMs) {
                particleIterator.remove()
            }
        }

        val ringIterator = rings.iterator()
        while (ringIterator.hasNext()) {
            val ring = ringIterator.next()
            ring.radius += worldDeltaMs * 0.27f
            ring.alpha *= 0.968f
            if (ring.alpha <= 0.02f) {
                ringIterator.remove()
            }
        }

        val textIterator = floatingTexts.iterator()
        while (textIterator.hasNext()) {
            val floatingText = textIterator.next()
            floatingText.lifeMs += worldDeltaMs
            floatingText.y -= worldDeltaMs * 0.042f
            if (floatingText.lifeMs >= floatingText.maxLifeMs) {
                textIterator.remove()
            }
        }
    }

    private fun drawWorld(
        canvas: Canvas,
        nowMs: Long,
    ) {
        val safeWidth = width.toFloat().coerceAtLeast(1f)
        val safeHeight = height.toFloat().coerceAtLeast(1f)
        val skyShader =
            LinearGradient(
                0f, 0f, 0f, safeHeight,
                intArrayOf(
                    Color.parseColor("#A9DBFF"),
                    Color.parseColor("#DDF2FF"),
                    Color.parseColor("#EAF8FF"),
                    Color.parseColor("#95D4BD"),
                ),
                floatArrayOf(0f, 0.52f, 0.74f, 1f),
                Shader.TileMode.CLAMP,
            )
        fillPaint.shader = skyShader
        canvas.drawRect(0f, 0f, safeWidth, safeHeight, fillPaint)
        fillPaint.shader = null

        shadowPaint.color = Color.parseColor("#7AFFFFFF")
        canvas.drawCircle(safeWidth * 0.80f, safeHeight * 0.17f, min(safeWidth, safeHeight) * 0.10f, shadowPaint)

        clouds.forEach { drawCloud(canvas, it) }

        val groundShader =
            LinearGradient(
                0f, safeHeight * 0.60f, 0f, safeHeight,
                intArrayOf(Color.parseColor("#1AA8DCA7"), Color.parseColor("#4E59B084")),
                null,
                Shader.TileMode.CLAMP,
            )
        fillPaint.shader = groundShader
        canvas.drawRect(0f, safeHeight * 0.60f, safeWidth, safeHeight, fillPaint)
        fillPaint.shader = null

        activeTargets.forEach { drawMonster(canvas, it, nowMs) }
        rings.forEach { drawRing(canvas, it) }
        particles.forEach { drawParticle(canvas, it) }
        floatingTexts.forEach { drawFloatingText(canvas, it) }
    }

    private fun drawCloud(
        canvas: Canvas,
        cloud: Cloud,
    ) {
        shadowPaint.color = Color.WHITE
        shadowPaint.alpha = (cloud.alpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawOval(
            RectF(
                cloud.x - cloud.width * 0.42f,
                cloud.y - cloud.height * 0.44f,
                cloud.x + cloud.width * 0.42f,
                cloud.y + cloud.height * 0.44f,
            ),
            shadowPaint,
        )
        canvas.drawOval(
            RectF(
                cloud.x - cloud.width * 0.46f,
                cloud.y - cloud.height * 0.18f,
                cloud.x - cloud.width * 0.02f,
                cloud.y + cloud.height * 0.42f,
            ),
            shadowPaint,
        )
        canvas.drawOval(
            RectF(
                cloud.x + cloud.width * 0.02f,
                cloud.y - cloud.height * 0.06f,
                cloud.x + cloud.width * 0.46f,
                cloud.y + cloud.height * 0.46f,
            ),
            shadowPaint,
        )
    }

    private fun drawMonster(
        canvas: Canvas,
        target: MonsterTarget,
        nowMs: Long,
    ) {
        val centerX = target.x + sin(target.wobble.toDouble()).toFloat() * 10f
        val centerY = target.y
        val radius = target.radius
        val active = !target.hit && isTargetHittable(target)
        val alpha = if (target.hit) (1f - target.hitAgeMs / 360f).coerceIn(0f, 1f) else 1f

        shadowPaint.color = target.palette.glowColor
        shadowPaint.alpha = (alpha * if (active) 128 else 94).toInt().coerceIn(0, 255)
        canvas.drawOval(
            RectF(
                centerX - radius * 0.74f,
                centerY - radius * 1.06f,
                centerX + radius * 0.74f,
                centerY + radius * 0.62f,
            ),
            shadowPaint,
        )

        val bitmap = target.bitmap
        val imageBottom: Float
        if (bitmap != null) {
            val availableWidth = radius * 1.92f
            val availableHeight = radius * 1.72f
            val aspect = bitmap.width.toFloat().coerceAtLeast(1f) / bitmap.height.toFloat().coerceAtLeast(1f)
            var drawWidth = availableWidth
            var drawHeight = drawWidth / aspect
            if (drawHeight > availableHeight) {
                drawHeight = availableHeight
                drawWidth = drawHeight * aspect
            }
            val destination =
                RectF(
                    centerX - drawWidth * 0.5f,
                    centerY - radius * 0.82f,
                    centerX + drawWidth * 0.5f,
                    centerY - radius * 0.82f + drawHeight,
                )
            imagePaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(bitmap, null, destination, imagePaint)
            imageBottom = destination.bottom
        } else {
            val fallbackRect =
                RectF(
                    centerX - radius * 0.72f,
                    centerY - radius * 0.82f,
                    centerX + radius * 0.72f,
                    centerY + radius * 0.56f,
                )
            fillPaint.color = target.palette.shellColor
            fillPaint.alpha = (alpha * 215f).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(fallbackRect, radius * 0.34f, radius * 0.34f, fillPaint)
            strokePaint.color = target.palette.strokeColor
            strokePaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            strokePaint.strokeWidth = if (active) dp(2.3f) else dp(1.7f)
            canvas.drawRoundRect(fallbackRect, radius * 0.34f, radius * 0.34f, strokePaint)
            imageBottom = fallbackRect.bottom
        }

        val name = target.monster.label(languageCode)
        nameTextPaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        nameTextPaint.textSize = radius.coerceIn(dp(28f), dp(54f)) * 0.24f
        val textWidth = nameTextPaint.measureText(name)
        val pillHeight = nameTextPaint.textSize + dp(14f)
        val pillRect =
            RectF(
                centerX - textWidth * 0.5f - dp(12f),
                imageBottom + dp(8f),
                centerX + textWidth * 0.5f + dp(12f),
                imageBottom + dp(8f) + pillHeight,
            )
        fillPaint.color = Color.parseColor("#A0101722")
        fillPaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(pillRect, pillHeight * 0.48f, pillHeight * 0.48f, fillPaint)
        strokePaint.color = Color.parseColor("#5CFFFFFF")
        strokePaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        strokePaint.strokeWidth = dp(1.1f)
        canvas.drawRoundRect(pillRect, pillHeight * 0.48f, pillHeight * 0.48f, strokePaint)
        canvas.drawText(name, centerX, pillRect.centerY() + nameTextPaint.textSize * 0.34f, nameTextPaint)

        if (active && !target.hit) {
            ringPaint.color = target.palette.strokeColor
            ringPaint.alpha = 96
            ringPaint.strokeWidth = dp(1.8f)
            canvas.drawRoundRect(
                RectF(
                    centerX - radius * 0.76f,
                    centerY - radius * 0.86f,
                    centerX + radius * 0.76f,
                    pillRect.bottom + dp(4f),
                ),
                dp(18f),
                dp(18f),
                ringPaint,
            )
        }
    }

    private fun drawRing(
        canvas: Canvas,
        ring: Ring,
    ) {
        ringPaint.color = ring.color
        ringPaint.alpha = (ring.alpha * 255f).toInt().coerceIn(0, 255)
        ringPaint.strokeWidth = ring.strokeWidth
        canvas.drawCircle(ring.x, ring.y, ring.radius, ringPaint)
    }

    private fun drawParticle(
        canvas: Canvas,
        particle: Particle,
    ) {
        val alpha = (1f - particle.lifeMs / particle.maxLifeMs).coerceIn(0f, 1f)
        particlePaint.color = particle.color
        particlePaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        if (particle.shard) {
            val halfWidth = particle.radius * (0.62f + particle.stretch * 0.14f)
            val halfHeight = particle.radius * (0.48f + particle.stretch * 0.22f)
            canvas.save()
            canvas.translate(particle.x, particle.y)
            canvas.rotate(particle.rotation)
            canvas.drawRoundRect(
                RectF(-halfWidth, -halfHeight, halfWidth, halfHeight),
                particle.radius * 0.18f,
                particle.radius * 0.18f,
                particlePaint,
            )
            canvas.restore()
        } else {
            canvas.drawCircle(particle.x, particle.y, particle.radius, particlePaint)
        }
    }

    private fun drawFloatingText(
        canvas: Canvas,
        floatingText: FloatingText,
    ) {
        val alpha = (1f - floatingText.lifeMs / floatingText.maxLifeMs).coerceIn(0f, 1f)
        floatingTextPaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        floatingTextPaint.textSize = dp(15f)
        floatingTextPaint.setShadowLayer(dp(5f), 0f, dp(2f), Color.parseColor("#7F000000"))
        canvas.drawText(floatingText.text, floatingText.x, floatingText.y, floatingTextPaint)
    }

    private fun spawnBurst(nowMs: Long) {
        repeat(densityPreset.count) { offset ->
            spawnMonster(nowMs + offset * 28L)
        }
    }

    private fun nextSpawnGapMs(): Float {
        val baseGapMs = speedPreset.gapMs * densityPreset.gapScale
        val jitter =
            when (densityPreset) {
                DensityPreset.Solo -> 0.78f + random.nextFloat() * 0.64f
                DensityPreset.Duo -> 0.74f + random.nextFloat() * 0.52f
                DensityPreset.Storm -> 0.84f + random.nextFloat() * 0.32f
            }
        return baseGapMs * jitter
    }

    private fun spawnMonster(nowMs: Long) {
        if (monsters.isEmpty()) {
            return
        }
        val monsterAsset = monsters[random.nextInt(monsters.size)]
        val radius = monsterRadiusPx()
        val edgePadding = radius + dp(18f)
        val side = if (random.nextBoolean()) -1 else 1
        val x = chooseSpawnX(radius, edgePadding)
        activeTargets +=
            MonsterTarget(
                side = side,
                x = x.coerceIn(edgePadding, width - edgePadding),
                y = monsterStartY(radius),
                radius = radius,
                velocityY = speedPreset.velocity + random.nextFloat() * 0.09f,
                drift = (random.nextFloat() - 0.5f) * 0.06f,
                wobble = random.nextFloat() * (Math.PI.toFloat() * 2f),
                wobbleSpeed = 0.0022f + random.nextFloat() * 0.0014f,
                bornAtMs = nowMs,
                palette = monsterPalettes[random.nextInt(monsterPalettes.size)],
                monster = monsterAsset,
                bitmap = monsterAsset.randomBitmap(random),
            )
    }

    private fun chooseSpawnX(
        radius: Float,
        edgePadding: Float,
    ): Float {
        val minX = edgePadding
        val preferredReservedWidth = min(dp(144f), width.toFloat() * 0.24f)
        val fallbackReservedWidth = min(dp(92f), width.toFloat() * 0.15f)
        var maxX = width.toFloat() - edgePadding - preferredReservedWidth
        if (maxX <= minX + radius * 1.8f) {
            maxX = width.toFloat() - edgePadding - fallbackReservedWidth
        }
        if (maxX <= minX) {
            return width * 0.5f
        }

        val visibleTargets =
            activeTargets.filter { target ->
                !target.hit && targetVisualBottom(target) > -target.radius && targetVisualTop(target) < height + dp(36f)
            }
        if (visibleTargets.isEmpty()) {
            return minX + random.nextFloat() * (maxX - minX)
        }

        val candidates = ArrayList<Float>()
        val laneCount =
            when {
                width >= dp(900f) -> 7
                width >= dp(720f) -> 6
                else -> 5
            }
        repeat(laneCount) { index ->
            val fraction =
                if (laneCount == 1) {
                    0.5f
                } else {
                    index.toFloat() / (laneCount - 1).toFloat()
                }
            candidates += minX + (maxX - minX) * fraction
        }
        repeat(10) {
            candidates += minX + random.nextFloat() * (maxX - minX)
        }

        var bestX = minX + random.nextFloat() * (maxX - minX)
        var bestScore = Float.NEGATIVE_INFINITY
        visibleTargets.forEach { target ->
            if (abs(target.x - bestX) < radius * 2.1f) {
                bestX = target.x
            }
        }

        candidates.forEach { candidate ->
            var minGap = Float.POSITIVE_INFINITY
            visibleTargets.forEach { target ->
                val spacing = abs(target.x - candidate) - (target.radius + radius) * 0.96f
                if (spacing < minGap) {
                    minGap = spacing
                }
            }
            val edgeGap = min(candidate - minX, maxX - candidate)
            val score = minGap + edgeGap * 0.08f
            if (score > bestScore) {
                bestScore = score
                bestX = candidate
            }
        }

        return bestX.coerceIn(minX, maxX)
    }

    private fun monsterRadiusPx(): Float {
        val densityDpi = resources.displayMetrics.densityDpi.coerceAtLeast(160).toFloat()
        val diameterPx = densityDpi * 23f / 25.4f
        return (diameterPx * 0.5f).coerceIn(dp(50f), min(width, height) * 0.145f)
    }

    private fun monsterStartY(radius: Float): Float =
        -radius * 0.35f

    private fun targetVisualTop(target: MonsterTarget): Float {
        val centerY = target.y
        val radius = target.radius
        val bitmap = target.bitmap
        if (bitmap != null) {
            val availableWidth = radius * 1.92f
            val availableHeight = radius * 1.72f
            val aspect = bitmap.width.toFloat().coerceAtLeast(1f) / bitmap.height.toFloat().coerceAtLeast(1f)
            var drawWidth = availableWidth
            var drawHeight = drawWidth / aspect
            if (drawHeight > availableHeight) {
                drawHeight = availableHeight
            }
            return centerY - radius * 0.82f
        }
        return centerY - radius * 0.82f
    }

    private fun targetVisualBottom(target: MonsterTarget): Float {
        val centerY = target.y
        val radius = target.radius
        val bitmap = target.bitmap
        if (bitmap != null) {
            val availableWidth = radius * 1.92f
            val availableHeight = radius * 1.72f
            val aspect = bitmap.width.toFloat().coerceAtLeast(1f) / bitmap.height.toFloat().coerceAtLeast(1f)
            var drawWidth = availableWidth
            var drawHeight = drawWidth / aspect
            if (drawHeight > availableHeight) {
                drawHeight = availableHeight
            }
            return centerY - radius * 0.82f + drawHeight
        }
        return centerY + radius * 0.56f
    }

    private fun isTargetHittable(target: MonsterTarget): Boolean {
        val top = targetVisualTop(target)
        return !target.hit && top > -target.radius && top < height.toFloat() + target.velocityY * HIT_SIGNAL_LATENCY_MS
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    private fun handlePunch(
        nowMs: Long,
        intensity: Float,
    ) {
        val hittableTargets =
            activeTargets.filter { target ->
                isTargetHittable(target)
            }
        if (hittableTargets.isEmpty()) {
            publishHint(waitNextWaveHint())
            return
        }
        val winner = hittableTargets.maxByOrNull { target -> targetVisualBottom(target) } ?: return
        registerHit(winner, intensity)
    }

    private fun registerHit(
        target: MonsterTarget,
        intensity: Float,
    ) {
        target.hit = true
        hits += 1
        relief = min(100f, relief + 2.5f)
        slowMotionFactor = min(slowMotionFactor, 0.18f)
        impactFreezeMs = max(impactFreezeMs, 90f + intensity.coerceIn(0.7f, 1.95f) * 40f)
        publishOverlaySnapshot()
        publishHint(successHint(target.monster.label(languageCode)))
        publishMotivation(motivationHint())

        val impactX = target.x + sin(target.wobble.toDouble()).toFloat() * 10f
        val impactY = target.y
        val power = intensity.coerceIn(0.7f, 1.95f)
        val burstColors = collectBurstColors(target)
        val shardCount = 156 + (power * 82f).toInt()
        repeat(shardCount) { index ->
            val angle = ((Math.PI * 2.0 * index) / shardCount) + (random.nextFloat() - 0.5f) * 0.55f
            val speed = 10.8f + random.nextFloat() * 32.0f * power
            val shardColor = burstColors[index % burstColors.size]
            particles +=
                Particle(
                    x = impactX,
                    y = impactY,
                    velocityX = kotlin.math.cos(angle).toFloat() * speed,
                    velocityY = kotlin.math.sin(angle).toFloat() * speed - 4.4f,
                    radius = 6.0f + random.nextFloat() * 14.5f,
                    maxLifeMs = 680f + random.nextFloat() * 900f,
                    color = shardColor,
                    rotation = random.nextFloat() * 360f,
                    spin = -0.86f + random.nextFloat() * 1.72f,
                    stretch = 0.9f + random.nextFloat() * 2.6f,
                    shard = true,
                )
        }
        repeat(40) { index ->
            val angle = random.nextFloat() * (Math.PI.toFloat() * 2f)
            val speed = 6.4f + random.nextFloat() * 18.0f * power
            particles +=
                Particle(
                    x = impactX,
                    y = impactY,
                    velocityX = kotlin.math.cos(angle.toDouble()).toFloat() * speed,
                    velocityY = kotlin.math.sin(angle.toDouble()).toFloat() * speed - 2.4f,
                    radius = 12.0f + random.nextFloat() * 20.0f,
                    maxLifeMs = 880f + random.nextFloat() * 980f,
                    color = burstColors[index % burstColors.size],
                    rotation = random.nextFloat() * 360f,
                    spin = -0.42f + random.nextFloat() * 0.84f,
                    stretch = 0.9f + random.nextFloat() * 1.4f,
                    shard = true,
                )
        }
        repeat(54) { index ->
            particles +=
                Particle(
                    x = impactX,
                    y = impactY,
                    velocityX = (random.nextFloat() - 0.5f) * 28f * power,
                    velocityY = (random.nextFloat() - 0.5f) * 28f * power - 1.1f,
                    radius = 4.0f + random.nextFloat() * 9.4f,
                    maxLifeMs = 320f + random.nextFloat() * 420f,
                    color = if (index % 5 == 0) Color.parseColor("#FFF9FEFF") else burstColors[index % burstColors.size],
                    rotation = random.nextFloat() * 360f,
                    spin = -0.92f + random.nextFloat() * 1.84f,
                    stretch = 0.8f + random.nextFloat() * 1.5f,
                    shard = true,
                )
        }
        floatingTexts +=
            FloatingText(
                text = target.monster.label(languageCode),
                x = impactX,
                y = impactY - target.radius * 0.24f,
                maxLifeMs = 860f,
            )
        Haptics.tap(context)
        playImpactSound()
    }

    private fun collectBurstColors(target: MonsterTarget): IntArray {
        val colors = ArrayList<Int>(18)
        val bitmap = target.bitmap
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            repeat(96) {
                val sampled = bitmap.getPixel(random.nextInt(bitmap.width), random.nextInt(bitmap.height))
                if (Color.alpha(sampled) >= 72) {
                    colors += sampled
                }
            }
        }
        if (colors.isEmpty()) {
            colors += target.palette.strokeColor
            colors += target.palette.glowColor
            colors += Color.parseColor("#F3FFFFFF")
            colors += Color.parseColor("#B5E9FFFF")
        }
        while (colors.size < 18) {
            colors += colors[random.nextInt(colors.size)]
        }
        return colors.toIntArray()
    }

    private fun playImpactSound() {
        runCatching { toneGenerator.startTone(ToneGenerator.TONE_CDMA_LOW_L, 220) }
        postDelayed({ runCatching { toneGenerator.startTone(ToneGenerator.TONE_CDMA_MED_L, 210) } }, 28L)
        postDelayed({ runCatching { toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 240) } }, 62L)
        postDelayed({ runCatching { toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 180) } }, 118L)
    }

    private fun registerSoftMiss() {
        publishHint(missHint())
    }

    private fun publishOverlaySnapshot() {
        listener?.onOverlaySnapshot(
            OverlaySnapshot(
                hits = hits,
                combo = combo,
                misses = misses,
                relief = relief,
                paceLabel = speedPreset.label(languageCode),
            ),
        )
    }

    private fun publishHint(value: String) {
        currentHint = value
        listener?.onHintChanged(value)
    }

    private fun publishMotivation(value: String) {
        listener?.onMotivationCue(value)
    }

    private fun runSafely(block: () -> Unit) {
        if (fatalError != null) {
            return
        }
        try {
            block()
        } catch (throwable: Throwable) {
            fatalError = throwable
            running = false
            fatalErrorReporter?.invoke(throwable)
            invalidate()
        }
    }

    private fun drawFallback(canvas: Canvas) {
        fillPaint.shader =
            LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat().coerceAtLeast(1f),
                intArrayOf(Color.parseColor("#A9DBFF"), Color.parseColor("#EAF8FF"), Color.parseColor("#95D4BD")),
                floatArrayOf(0f, 0.65f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
        fillPaint.shader = null
        shadowPaint.color = Color.parseColor("#66FFFFFF")
        canvas.drawCircle(width * 0.5f, height * 0.42f, min(width, height) * 0.14f, shadowPaint)
        floatingTextPaint.alpha = 255
        floatingTextPaint.textSize = dp(16f)
        canvas.drawText(if (languageCode.startsWith("zh")) "情绪拳王安全模式" else "Emotion Champ Safe Mode", width * 0.5f, height * 0.54f, floatingTextPaint)
    }

    private fun rebuildClouds() {
        clouds.clear()
        repeat(6) { index ->
            clouds +=
                Cloud(
                    x = random.nextFloat() * width,
                    y = dp(44f) + index * dp(48f) + random.nextFloat() * dp(22f),
                    width = dp(94f) + random.nextFloat() * dp(98f),
                    height = dp(26f) + random.nextFloat() * dp(20f),
                    velocity = 0.03f + random.nextFloat() * 0.05f,
                    alpha = 0.18f + random.nextFloat() * 0.14f,
                )
        }
    }

    private fun readyHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u60C5\u7EEA\u602A\u7269\u51FA\u73B0\u540E 0.5 - 1.0 \u79D2\u51FA\u62F3\uFF0C\u51FB\u788E\u53CD\u9988\u6700\u5F3A\u3002"
        } else {
            "Punch 0.5 - 1.0 seconds after a monster appears for the strongest shatter."
        }

    private fun calibratingHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u6B63\u5728\u6821\u51C6\u73AF\u5883\uFF0C\u8BF7\u5148\u4FDD\u6301\u4E00\u79D2\u5B89\u9759\u3002"
        } else {
            "Calibrating the room. Hold still for a second."
        }

    private fun permissionHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u9700\u8981\u9EA6\u514B\u98CE\u6743\u9650\uFF0C\u60C5\u7EEA\u62F3\u738B\u624D\u80FD\u5224\u65AD\u51FB\u4E2D\u3002"
        } else {
            "Bluetooth connection is required to detect hits."
        }

    private fun errorHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u58F0\u97F3\u8BC6\u522B\u6682\u65F6\u4E0D\u53EF\u7528\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5\u3002"
        } else {
            "The detector is unavailable right now. Please try again."
        }

    private fun missHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u7EE7\u7EED\u81EA\u7531\u51FA\u62F3\uFF0C\u547D\u4E2D\u4EFB\u610F\u4E0B\u843D\u7684\u60C5\u7EEA\u602A\u7269\u5C31\u4F1A\u7206\u5F00\u3002"
        } else {
            "Keep swinging freely. Any falling monster can burst on impact."
        }

    private fun waitNextWaveHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u7B49\u4E0B\u4E00\u53EA\u60C5\u7EEA\u602A\u7269\u4ECE\u9876\u90E8\u843D\u4E0B\u6765\uFF0C\u518D\u72E0\u72E0\u5E72\u788E\u5B83\u3002"
        } else {
            "Wait for the next monster to fall from the top, then smash it."
        }

    private fun successHint(name: String): String =
        if (languageCode.startsWith("zh")) {
            "\u5F88\u597D\uFF0C" + name + " \u5DF2\u7ECF\u88AB\u4F60\u51FB\u788E\u3002"
        } else {
            "$name just shattered. Nice hit."
        }

    private fun motivationHint(): String {
        val options =
            if (languageCode.startsWith("zh")) {
                listOf("\u771F\u68D2", "\u6F02\u4EAE", "\u7EE7\u7EED", "\u5F88\u597D", "\u518D\u6765")
            } else {
                listOf("Great", "Nice", "Keep going", "Good work", "Again")
            }
        return options[random.nextInt(options.size)]
    }

    private fun armingHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u70B9\u51FB\u5F00\u59CB\u540E\uFF0C\u7CFB\u7EDF\u4F1A\u5148\u6821\u51C6\u73AF\u5883\uFF0C\u518D\u653E\u51FA\u4E0B\u843D\u7684\u60C5\u7EEA\u602A\u7269\u3002"
        } else {
            "Press Start to arm the round. The detector will calibrate before the monsters begin to fall."
        }

    private fun idleHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u70B9\u53F3\u4E0A\u89D2\u8BBE\u7F6E\u8C03\u6574\u901F\u5EA6\u548C\u6570\u91CF\uFF0C\u6216\u8005\u76F4\u63A5\u70B9\u51FB\u5F00\u59CB\u3002"
        } else {
            "Tap the gear to adjust speed and count, or press Start directly."
        }

    private fun finishedHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u8FD9\u4E00\u8F6E\u5DF2\u7ECF\u7ED3\u675F\uFF0C\u8BBE\u7F6E\u9879\u5DF2\u6062\u590D\uFF0C\u53EF\u4EE5\u91CD\u65B0\u5F00\u59CB\u3002"
        } else {
            "This round has ended. Setup options are back for the next round."
        }

    private companion object {
        const val HIT_SIGNAL_LATENCY_MS = 700f
    }
}


