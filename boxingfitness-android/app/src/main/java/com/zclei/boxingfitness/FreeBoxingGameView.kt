package com.zclei.boxingfitness

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class FreeBoxingPersonaOption(
    val zhName: String,
    val enName: String,
    val zhRole: String,
    val enRole: String,
)

internal object FreeBoxingPersonaCatalog {
    const val CELL_WIDTH = 352
    const val CELL_HEIGHT = 384
    private var cachedSourceRects: List<Rect>? = null

    val options =
        listOf(
            FreeBoxingPersonaOption("\u7537\u5B69", "Boy", "\u513F\u7AE5", "Child"),
            FreeBoxingPersonaOption("\u5973\u5B69", "Girl", "\u513F\u7AE5", "Child"),
            FreeBoxingPersonaOption("\u5C11\u5E74\u7537\u6027", "Young Man", "\u5C11\u5E74", "Youth"),
            FreeBoxingPersonaOption("\u5C11\u5E74\u5973\u6027", "Young Woman", "\u5C11\u5E74", "Youth"),
            FreeBoxingPersonaOption("\u804C\u4E1A\u7537", "Professional Man", "\u804C\u4E1A\u4EBA\u7269", "Professional"),
            FreeBoxingPersonaOption("\u804C\u4E1A\u5973", "Professional Woman", "\u804C\u4E1A\u4EBA\u7269", "Professional"),
            FreeBoxingPersonaOption("\u4E2D\u5E74\u7537", "Middle-aged Man", "\u4E2D\u5E74", "Midlife"),
            FreeBoxingPersonaOption("\u4E2D\u5E74\u5973", "Middle-aged Woman", "\u4E2D\u5E74", "Midlife"),
        )

    fun loadSprite(context: Context): Bitmap? =
        runCatching {
            context.assets.open("free_boxing_personas.png").use(BitmapFactory::decodeStream)
        }.getOrNull()?.let(::transparentizeCellBackgrounds)?.also { cachedSourceRects = computePersonaSourceRects(it) }

    fun thumbnail(
        spriteSheet: Bitmap?,
        index: Int,
        sizePx: Int,
    ): Bitmap? {
        val safeSheet = spriteSheet ?: return null
        val safeIndex = index.coerceIn(0, options.lastIndex)
        val source = sourceRect(safeIndex, safeSheet)
        return runCatching {
            val cropped = Bitmap.createBitmap(safeSheet, source.left, source.top, source.width(), source.height())
            val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val scale = min(sizePx.toFloat() / source.width().coerceAtLeast(1), sizePx.toFloat() / source.height().coerceAtLeast(1))
            val drawWidth = source.width() * scale
            val drawHeight = source.height() * scale
            val destination =
                RectF(
                    (sizePx - drawWidth) * 0.5f,
                    (sizePx - drawHeight) * 0.5f,
                    (sizePx + drawWidth) * 0.5f,
                    (sizePx + drawHeight) * 0.5f,
                )
            canvas.drawBitmap(cropped, null, destination, null)
            cropped.recycle()
            output
        }.getOrNull()
    }

    fun sourceRect(
        index: Int,
        spriteSheet: Bitmap?,
    ): Rect {
        val safeIndex = index.coerceIn(0, options.lastIndex)
        cachedSourceRects?.getOrNull(safeIndex)?.let { return Rect(it) }
        return fullCellRect(safeIndex, spriteSheet)
    }

    fun aspectRatio(
        index: Int,
        spriteSheet: Bitmap?,
    ): Float {
        val source = sourceRect(index, spriteSheet)
        return source.width().coerceAtLeast(1).toFloat() / source.height().coerceAtLeast(1).toFloat()
    }

    private fun transparentizeCellBackgrounds(source: Bitmap): Bitmap {
        val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return bitmap
        }
        val pixels = IntArray(width * height)
        val visited = BooleanArray(pixels.size)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (row in 0 until max(1, height / CELL_HEIGHT)) {
            for (column in 0 until max(1, width / CELL_WIDTH)) {
                val left = column * CELL_WIDTH
                val top = row * CELL_HEIGHT
                val right = min(width - 1, (column + 1) * CELL_WIDTH - 1)
                val bottom = min(height - 1, (row + 1) * CELL_HEIGHT - 1)
                val seedPoints =
                    intArrayOf(
                        encode(left + 2, top + 2, width),
                        encode(right - 2, top + 2, width),
                        encode(left + 2, bottom - 2, width),
                        encode(right - 2, bottom - 2, width),
                        encode((left + right) / 2, top + 2, width),
                        encode((left + right) / 2, bottom - 2, width),
                    )
                seedPoints.forEach { seed ->
                    floodTransparentBackground(seed, pixels, visited, width, left, top, right, bottom)
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun computePersonaSourceRects(bitmap: Bitmap): List<Rect> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return options.indices.map { index ->
            val cell = fullCellRect(index, bitmap)
            val cellWidth = cell.width().coerceAtLeast(1)
            val cellHeight = cell.height().coerceAtLeast(1)
            val visited = BooleanArray(cellWidth * cellHeight)
            var bestCount = 0
            var bestRect: Rect? = null

            for (y in cell.top until cell.bottom) {
                for (x in cell.left until cell.right) {
                    val localIndex = (y - cell.top) * cellWidth + (x - cell.left)
                    if (visited[localIndex]) {
                        continue
                    }
                    if (Color.alpha(pixels[y * width + x]) <= 28) {
                        visited[localIndex] = true
                        continue
                    }
                    val component = traceOpaqueComponent(x, y, pixels, width, cell, visited)
                    if (component.first > bestCount) {
                        bestCount = component.first
                        bestRect = component.second
                    }
                }
            }

            val safeRect = bestRect ?: cell
            val insetX = max(6, safeRect.width() / 12)
            val insetY = max(6, safeRect.height() / 14)
            Rect(
                max(cell.left, safeRect.left - insetX),
                max(cell.top, safeRect.top - insetY),
                min(cell.right, safeRect.right + insetX),
                min(cell.bottom, safeRect.bottom + insetY),
            )
        }
    }

    private fun traceOpaqueComponent(
        startX: Int,
        startY: Int,
        pixels: IntArray,
        bitmapWidth: Int,
        cell: Rect,
        visited: BooleanArray,
    ): Pair<Int, Rect> {
        val queue = ArrayDeque<Int>()
        val cellWidth = cell.width().coerceAtLeast(1)
        queue.add(encode(startX, startY, bitmapWidth))
        var count = 0
        var minX = cell.right
        var minY = cell.bottom
        var maxX = cell.left
        var maxY = cell.top

        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val x = index % bitmapWidth
            val y = index / bitmapWidth
            if (x !in cell.left until cell.right || y !in cell.top until cell.bottom) {
                continue
            }
            val localIndex = (y - cell.top) * cellWidth + (x - cell.left)
            if (visited[localIndex]) {
                continue
            }
            visited[localIndex] = true
            if (Color.alpha(pixels[y * bitmapWidth + x]) <= 28) {
                continue
            }
            count += 1
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
            for (offsetY in -1..1) {
                for (offsetX in -1..1) {
                    if (offsetX == 0 && offsetY == 0) {
                        continue
                    }
                    queue.add(encode(x + offsetX, y + offsetY, bitmapWidth))
                }
            }
        }

        val right = if (count > 0) maxX + 1 else cell.right
        val bottom = if (count > 0) maxY + 1 else cell.bottom
        return count to Rect(minX, minY, right, bottom)
    }

    private fun floodTransparentBackground(
        seed: Int,
        pixels: IntArray,
        visited: BooleanArray,
        width: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        if (seed !in pixels.indices || visited[seed]) {
            return
        }
        val seedColor = pixels[seed]
        val queue = ArrayDeque<Int>()
        queue.add(seed)
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            if (index !in pixels.indices || visited[index]) {
                continue
            }
            val x = index % width
            val y = index / width
            if (x !in left..right || y !in top..bottom || !isCellBackgroundColor(pixels[index], seedColor)) {
                continue
            }
            visited[index] = true
            pixels[index] = pixels[index] and 0x00FFFFFF
            if (x > left) queue.add(index - 1)
            if (x < right) queue.add(index + 1)
            if (y > top) queue.add(index - width)
            if (y < bottom) queue.add(index + width)
        }
    }

    private fun isCellBackgroundColor(
        color: Int,
        seedColor: Int,
    ): Boolean {
        if (Color.alpha(color) < 16) {
            return true
        }
        val dr = abs(Color.red(color) - Color.red(seedColor))
        val dg = abs(Color.green(color) - Color.green(seedColor))
        val db = abs(Color.blue(color) - Color.blue(seedColor))
        val maxDelta = max(dr, max(dg, db))
        val totalDelta = dr + dg + db
        val saturation = max(Color.red(color), max(Color.green(color), Color.blue(color))) - min(Color.red(color), min(Color.green(color), Color.blue(color)))
        return maxDelta <= 34 && totalDelta <= 74 && saturation <= 38
    }

    private fun encode(
        x: Int,
        y: Int,
        width: Int,
    ): Int = y.coerceAtLeast(0) * width + x.coerceAtLeast(0)

    private fun fullCellRect(
        index: Int,
        spriteSheet: Bitmap?,
    ): Rect {
        val column = index % 4
        val row = index / 4
        val sheetWidth = spriteSheet?.width ?: CELL_WIDTH * 4
        val sheetHeight = spriteSheet?.height ?: CELL_HEIGHT * 2
        val left = column * CELL_WIDTH
        val top = row * CELL_HEIGHT
        val right = min(sheetWidth, (column + 1) * CELL_WIDTH)
        val bottom = min(sheetHeight, (row + 1) * CELL_HEIGHT)
        return Rect(left, top, right, bottom)
    }
}

internal class FreeBoxingGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    enum class SpeedPreset(
        val velocity: Float,
        val gapMs: Float,
        val zhLabel: String,
        val enLabel: String,
    ) {
        Slow(0.27f, 890f, "\u6162\u901F", "Slow"),
        Medium(0.3185f, 540f, "\u4E2D\u901F", "Mid"),
        Fast(0.559f, 280f, "\u5FEB\u901F", "Fast"),
        ;

        fun label(languageCode: String): String =
            if (languageCode.startsWith("zh")) zhLabel else enLabel
    }

    enum class DensityPreset(
        val count: Int,
        val targetsPerSecond: Float,
    ) {
        Solo(1, 1.0f),
        Duo(1, 3.0f),
        Storm(1, 4.5f),
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

    private data class BubblePalette(
        val fillColor: Int,
        val strokeColor: Int,
        val glowColor: Int,
    )

    private data class Bubble(
        val side: Int,
        var x: Float,
        var y: Float,
        val radius: Float,
        val velocityY: Float,
        val drift: Float,
        var wobble: Float,
        val wobbleSpeed: Float,
        val squash: Float,
        val palette: BubblePalette,
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
    )

    private data class Ring(
        val x: Float,
        val y: Float,
        var radius: Float,
        var alpha: Float,
        val color: Int,
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

    private val bubblePalettes =
        listOf(
            BubblePalette(Color.parseColor("#3DBDE4FF"), Color.parseColor("#8AD6FF"), Color.parseColor("#4D6DD6FF")),
            BubblePalette(Color.parseColor("#38FFCDF3"), Color.parseColor("#FF7BC0"), Color.parseColor("#54FF7BC0")),
            BubblePalette(Color.parseColor("#38C3FFDC"), Color.parseColor("#62E0A3"), Color.parseColor("#4C62E0A3")),
            BubblePalette(Color.parseColor("#3DFFE3B0"), Color.parseColor("#FFC15E"), Color.parseColor("#54FFC15E")),
            BubblePalette(Color.parseColor("#38DDCDFF"), Color.parseColor("#A98BFF"), Color.parseColor("#4CA98BFF")),
        )

    private val bubbles = ArrayList<Bubble>()
    private val particles = ArrayList<Particle>()
    private val rings = ArrayList<Ring>()
    private val floatingTexts = ArrayList<FloatingText>()
    private val clouds = ArrayList<Cloud>()
    private val random = java.util.Random()

    private val bubbleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bubbleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val avatarShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fallbackAvatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
        }
    private val tempRect = RectF()
    private val avatarSourceRect = Rect()
    private val avatarDestRect = RectF()
    private val spriteSheet: Bitmap? = FreeBoxingPersonaCatalog.loadSprite(context)
    private var skyShader: LinearGradient? = null
    private var groundShader: LinearGradient? = null

    private var listener: Listener? = null
    private var languageCode = "zh"
    private var personaIndex = 0
    private var speedPreset = SpeedPreset.Medium
    private var densityPreset = DensityPreset.Duo
    private var hits = 0
    private var combo = 0
    private var misses = 0
    private var relief = 8f
    private var spawnCountdownMs = 220f
    private var slowMotionFactor = 1f
    private var detectorReady = false
    private var currentHint = readyHint()
    private var lastFrameMs = 0L
    private var running = false
    private var sessionActive = false
    private var fatalError: Throwable? = null
    private var fatalErrorReporter: ((Throwable) -> Unit)? = null
    private var lastToneAtMs = 0L
    private var lastPunchFeedbackAtMs = 0L
    private var avatarPunchAmount = 0f
    private var lastHandDirection = 1

    private val toneGenerator by lazy(LazyThreadSafetyMode.NONE) {
        ToneGenerator(AudioManager.STREAM_MUSIC, 90)
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

    fun setPersona(index: Int) {
        personaIndex = index.coerceIn(0, FreeBoxingPersonaCatalog.options.lastIndex)
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
            relief = 8f
            spawnCountdownMs = 220f
            slowMotionFactor = 1f
            avatarPunchAmount = 0f
            bubbles.clear()
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
            spawnCountdownMs = 220f
            bubbles.clear()
            particles.clear()
            rings.clear()
            floatingTexts.clear()
            slowMotionFactor = 1f
            invalidate()
        }
    }

    fun playCountdownPunch() {
        runSafely {
            lastHandDirection *= -1
            avatarPunchAmount = 1f
            invalidate()
        }
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
            handlePunch(SystemClock.elapsedRealtime(), intensity.coerceIn(0.35f, 1.9f))
        }
    }

    fun registerBleHit(intensity: Float = 1f) {
        registerBleHits(1, intensity)
    }

    fun registerBleHits(
        count: Int,
        intensity: Float = 1f,
    ) {
        runSafely {
            if (!sessionActive) {
                return@runSafely
            }
            val safeCount = count.coerceIn(1, MAX_BLE_HITS_PER_FRAME)
            repeat(safeCount) { index ->
                handleBleHit(
                    intensity = intensity.coerceIn(0.35f, 1.9f),
                    fullEffect = index < MAX_FULL_EFFECT_HITS_PER_FRAME,
                    publishEachHit = index == safeCount - 1,
                )
            }
        }
    }

    fun debugEffectStats(): String =
        "session=$sessionActive ready=$detectorReady bubbles=${bubbles.size} particles=${particles.size} " +
            "rings=${rings.size} texts=${floatingTexts.size} hits=$hits combo=$combo misses=$misses"

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
            rebuildBackgroundShaders(w.toFloat(), h.toFloat())
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

        spawnCountdownMs -= deltaMs
        if (sessionActive && detectorReady && spawnCountdownMs <= 0f) {
            spawnBurst(nowMs)
            spawnCountdownMs = nextSpawnGapMs()
        }

        slowMotionFactor += (1f - slowMotionFactor) * 0.075f
        val timeScale = slowMotionFactor
        avatarPunchAmount = max(0f, avatarPunchAmount - deltaMs / 180f)

        if (!sessionActive) {
            clouds.forEach { cloud ->
                cloud.x += cloud.velocity * deltaMs
                if (cloud.x - cloud.width > width + 32f) {
                    cloud.x = -cloud.width
                }
            }
        }

        val bubbleIterator = bubbles.iterator()
        while (bubbleIterator.hasNext()) {
            val bubble = bubbleIterator.next()
            bubble.wobble += bubble.wobbleSpeed * deltaMs
            bubble.y += bubble.velocityY * deltaMs * timeScale
            bubble.x += bubble.drift * deltaMs
            if (bubble.hit) {
                bubble.hitAgeMs += deltaMs
                if (bubble.hitAgeMs >= 300f) {
                    bubbleIterator.remove()
                }
                continue
            }
            if (bubble.y - bubble.radius > height + 28f) {
                bubbleIterator.remove()
                misses += 1
                combo = 0
                publishOverlaySnapshot()
                publishHint(missHint())
            }
        }

        val particleIterator = particles.iterator()
        while (particleIterator.hasNext()) {
            val particle = particleIterator.next()
            particle.x += particle.velocityX * deltaMs * 0.06f * timeScale
            particle.y += particle.velocityY * deltaMs * 0.06f * timeScale
            particle.velocityX *= 0.985f
            particle.velocityY = particle.velocityY * 0.986f + 0.03f
            particle.lifeMs += deltaMs
            if (particle.lifeMs >= particle.maxLifeMs) {
                particleIterator.remove()
            }
        }

        val ringIterator = rings.iterator()
        while (ringIterator.hasNext()) {
            val ring = ringIterator.next()
            ring.radius += deltaMs * 0.24f
            ring.alpha *= 0.972f
            if (ring.alpha <= 0.02f) {
                ringIterator.remove()
            }
        }

        val textIterator = floatingTexts.iterator()
        while (textIterator.hasNext()) {
            val floatingText = textIterator.next()
            floatingText.lifeMs += deltaMs
            floatingText.y -= deltaMs * 0.035f
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
        bubbleFillPaint.shader = skyShader
        canvas.drawRect(0f, 0f, safeWidth, safeHeight, bubbleFillPaint)
        bubbleFillPaint.shader = null

        if (!sessionActive) {
            shadowPaint.color = Color.parseColor("#72FFFFFF")
            canvas.drawCircle(safeWidth * 0.82f, safeHeight * 0.18f, min(safeWidth, safeHeight) * 0.12f, shadowPaint)
            clouds.forEach { drawCloud(canvas, it) }
        }

        bubbleFillPaint.shader = groundShader
        canvas.drawRect(0f, safeHeight * 0.58f, safeWidth, safeHeight, bubbleFillPaint)
        bubbleFillPaint.shader = null

        drawAvatar(canvas, safeWidth, safeHeight)
        bubbles.forEach { drawBubble(canvas, it, nowMs) }
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
        tempRect.set(
            cloud.x - cloud.width * 0.42f,
            cloud.y - cloud.height * 0.44f,
            cloud.x + cloud.width * 0.42f,
            cloud.y + cloud.height * 0.44f,
        )
        canvas.drawOval(
            tempRect,
            shadowPaint,
        )
        tempRect.set(
            cloud.x - cloud.width * 0.46f,
            cloud.y - cloud.height * 0.18f,
            cloud.x - cloud.width * 0.02f,
            cloud.y + cloud.height * 0.42f,
        )
        canvas.drawOval(
            tempRect,
            shadowPaint,
        )
        tempRect.set(
            cloud.x + cloud.width * 0.02f,
            cloud.y - cloud.height * 0.06f,
            cloud.x + cloud.width * 0.46f,
            cloud.y + cloud.height * 0.46f,
        )
        canvas.drawOval(
            tempRect,
            shadowPaint,
        )
    }

    private fun drawBubble(
        canvas: Canvas,
        bubble: Bubble,
        nowMs: Long,
    ) {
        val centerX = bubble.x + sin(bubble.wobble.toDouble()).toFloat() * 10f
        val centerY = bubble.y
        val active = !bubble.hit && isTargetHittable(bubble)
        val alpha = if (bubble.hit) (1f - bubble.hitAgeMs / 300f).coerceIn(0f, 1f) else 1f

        if (!sessionActive) {
            shadowPaint.color = bubble.palette.glowColor
            shadowPaint.alpha = (alpha * if (active) 110 else 72).toInt().coerceIn(0, 255)
            canvas.drawCircle(centerX, centerY, bubble.radius + if (active) 14f else 8f, shadowPaint)
        }

        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.scale(1f, bubble.squash)
        bubbleFillPaint.color = bubble.palette.fillColor
        bubbleFillPaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        bubbleStrokePaint.color = bubble.palette.strokeColor
        bubbleStrokePaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        bubbleStrokePaint.strokeWidth = if (active) 3f else 2f
        canvas.drawCircle(0f, 0f, bubble.radius, bubbleFillPaint)
        canvas.drawCircle(0f, 0f, bubble.radius, bubbleStrokePaint)

        if (!sessionActive) {
            bubbleFillPaint.color = Color.WHITE
            bubbleFillPaint.alpha = (alpha * 220f).toInt().coerceIn(0, 255)
            tempRect.set(
                -bubble.radius * 0.40f,
                -bubble.radius * 0.52f,
                -bubble.radius * 0.04f,
                -bubble.radius * 0.04f,
            )
            canvas.drawOval(tempRect, bubbleFillPaint)
            tempRect.set(
                bubble.radius * 0.10f,
                bubble.radius * 0.02f,
                bubble.radius * 0.26f,
                bubble.radius * 0.26f,
            )
            canvas.drawOval(tempRect, bubbleFillPaint)
        }
        canvas.restore()

        if (active && !bubble.hit && !sessionActive) {
            ringPaint.color = bubble.palette.strokeColor
            ringPaint.alpha = 96
            ringPaint.strokeWidth = 3f
            canvas.drawCircle(centerX, centerY, bubble.radius + 10f, ringPaint)
        }
    }

    private fun drawRing(
        canvas: Canvas,
        ring: Ring,
    ) {
        ringPaint.color = ring.color
        ringPaint.alpha = (ring.alpha * 255f).toInt().coerceIn(0, 255)
        ringPaint.strokeWidth = 5.8f
        canvas.drawCircle(ring.x, ring.y, ring.radius, ringPaint)
    }

    private fun drawParticle(
        canvas: Canvas,
        particle: Particle,
    ) {
        val alpha = (1f - particle.lifeMs / particle.maxLifeMs).coerceIn(0f, 1f)
        particlePaint.color = particle.color
        particlePaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(particle.x, particle.y, particle.radius, particlePaint)
    }

    private fun drawFloatingText(
        canvas: Canvas,
        floatingText: FloatingText,
    ) {
        val alpha = (1f - floatingText.lifeMs / floatingText.maxLifeMs).coerceIn(0f, 1f)
        textPaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        textPaint.textSize = 26f + min(18f, combo * 1.8f)
        canvas.drawText(floatingText.text, floatingText.x, floatingText.y, textPaint)
    }

    private fun rebuildBackgroundShaders(
        viewWidth: Float,
        viewHeight: Float,
    ) {
        skyShader =
            LinearGradient(
                0f,
                0f,
                0f,
                viewHeight,
                intArrayOf(
                    Color.parseColor("#B9E5FF"),
                    Color.parseColor("#DFF3FF"),
                    Color.parseColor("#83CDB8"),
                ),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP,
            )
        groundShader =
            LinearGradient(
                0f,
                viewHeight * 0.58f,
                0f,
                viewHeight,
                intArrayOf(Color.parseColor("#056CC3BA"), Color.parseColor("#5C429E88")),
                null,
                Shader.TileMode.CLAMP,
            )
    }

    private fun spawnBurst(nowMs: Long) {
        repeat(densityPreset.count) { offset ->
            spawnBubble(nowMs + offset * 20L)
        }
    }

    private fun nextSpawnGapMs(): Float {
        val baseGapMs = 1000f / densityPreset.targetsPerSecond
        val jitter =
            when (densityPreset) {
                DensityPreset.Solo -> 0.92f + random.nextFloat() * 0.16f
                DensityPreset.Duo -> 0.90f + random.nextFloat() * 0.18f
                DensityPreset.Storm -> 0.88f + random.nextFloat() * 0.20f
            }
        return baseGapMs * jitter
    }

    private fun spawnBubble(nowMs: Long) {
        val side = if (random.nextBoolean()) -1 else 1
        val radius = bubbleRadiusPx()
        val avatarWidth = avatarDisplayWidth()
        val centerGap = avatarWidth * 0.44f + radius * 0.45f
        val edgePadding = radius + dp(16f)
        val preferredReservedWidth = min(dp(144f), width.toFloat() * 0.24f)
        val fallbackReservedWidth = min(dp(92f), width.toFloat() * 0.15f)
        val laneMin =
            if (side < 0) {
                edgePadding
            } else {
                width * 0.5f + centerGap
            }
        val laneMax =
            if (side < 0) {
                width * 0.5f - centerGap
            } else {
                width - edgePadding - preferredReservedWidth
            }
        val resolvedLaneMax =
            if (side > 0 && laneMax <= laneMin + radius * 0.4f) {
                width - edgePadding - fallbackReservedWidth
            } else {
                laneMax
            }
        val spawnMaxX =
            if (side > 0) {
                max(edgePadding, width - edgePadding - fallbackReservedWidth)
            } else {
                width - edgePadding
            }
        val laneBase =
            if (resolvedLaneMax > laneMin) {
                laneMin + random.nextFloat() * (resolvedLaneMax - laneMin)
            } else {
                width * 0.5f + side * (avatarWidth * 0.92f)
            }
        bubbles +=
            Bubble(
                side = side,
                x = laneBase.coerceIn(edgePadding, spawnMaxX),
                y = bubbleStartY(radius),
                radius = radius,
                velocityY = speedPreset.velocity + random.nextFloat() * 0.08f,
                drift = (random.nextFloat() - 0.5f) * 0.08f,
                wobble = random.nextFloat() * (Math.PI.toFloat() * 2f),
                wobbleSpeed = 0.0025f + random.nextFloat() * 0.0016f,
                squash = 1f,
                palette = bubblePalettes[random.nextInt(bubblePalettes.size)],
            )
    }

    private fun bubbleRadiusPx(): Float {
        val densityDpi = resources.displayMetrics.densityDpi.coerceAtLeast(160).toFloat()
        val diameterPx = densityDpi * 18f / 25.4f
        return (diameterPx * 0.5f).coerceIn(dp(42f), min(width, height) * 0.13f)
    }

    private fun bubbleStartY(radius: Float): Float =
        dp(92f) + radius

    private fun targetVisualTop(bubble: Bubble): Float =
        bubble.y - bubble.radius * bubble.squash

    private fun targetVisualBottom(bubble: Bubble): Float =
        bubble.y + bubble.radius * bubble.squash

    private fun isTargetHittable(bubble: Bubble): Boolean {
        return !bubble.hit
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    private fun handlePunch(
        nowMs: Long,
        intensity: Float,
    ) {
        val hittableBubbles =
            bubbles.filter { bubble ->
                isTargetHittable(bubble)
            }
        if (hittableBubbles.isEmpty()) {
            publishHint(waitNextWaveHint())
            return
        }
        val screenCenterX = width * 0.5f
        val winner =
            hittableBubbles.minByOrNull { bubble ->
                abs((bubble.x + sin(bubble.wobble.toDouble()).toFloat() * 10f) - screenCenterX)
            } ?: return
        registerHit(winner, intensity)
    }

    private fun drawAvatar(
        canvas: Canvas,
        safeWidth: Float,
        safeHeight: Float,
    ) {
        val avatarHeight = avatarDisplayHeight()
        val avatarWidth = avatarDisplayWidth()
        val centerX = safeWidth * 0.5f
        val groundY = safeHeight * 0.91f
        val lean = lastHandDirection * avatarPunchAmount * dp(10f)
        val lift = avatarPunchAmount * dp(6f)
        val left = centerX - avatarWidth * 0.5f + lean
        val top = groundY - avatarHeight - lift
        val right = left + avatarWidth
        val bottom = groundY - lift

        avatarShadowPaint.color = Color.parseColor("#33000000")
        tempRect.set(centerX - avatarWidth * 0.42f, groundY - dp(12f), centerX + avatarWidth * 0.42f, groundY + dp(10f))
        canvas.drawOval(tempRect, avatarShadowPaint)

        val sheet = spriteSheet
        if (sheet != null) {
            avatarSourceRect.set(FreeBoxingPersonaCatalog.sourceRect(personaIndex, sheet))
            avatarDestRect.set(left, top, right, bottom)
            canvas.drawBitmap(sheet, avatarSourceRect, avatarDestRect, null)
        } else {
            fallbackAvatarPaint.color = Color.parseColor("#DDF6FF")
            tempRect.set(left + avatarWidth * 0.32f, top, right - avatarWidth * 0.32f, top + avatarHeight * 0.18f)
            canvas.drawOval(tempRect, fallbackAvatarPaint)
            tempRect.set(left + avatarWidth * 0.22f, top + avatarHeight * 0.18f, right - avatarWidth * 0.22f, bottom)
            canvas.drawRoundRect(tempRect, dp(26f), dp(26f), fallbackAvatarPaint)
        }
    }

    private fun avatarDisplayHeight(): Float =
        (height * 0.44f).coerceIn(dp(168f), height * 0.58f)

    private fun avatarDisplayWidth(): Float {
        val aspect = FreeBoxingPersonaCatalog.aspectRatio(personaIndex, spriteSheet).coerceIn(0.52f, 1.15f)
        return (avatarDisplayHeight() * aspect).coerceIn(dp(112f), width * 0.28f)
    }

    private fun handleBleHit(
        intensity: Float,
        fullEffect: Boolean = true,
        publishEachHit: Boolean = true,
    ) {
        val winner =
            bubbles
                .filter { bubble -> isTargetHittable(bubble) }
                .minByOrNull { bubble ->
                    val screenCenterX = width * 0.5f
                    abs((bubble.x + sin(bubble.wobble.toDouble()).toFloat() * 10f) - screenCenterX)
                }
        if (winner != null) {
            registerHit(winner, intensity, fullEffect, publishEachHit)
            return
        }
        hits += 1
        combo += 1
        relief = min(100f, relief + 2.6f + combo * 0.45f)
        if (publishEachHit) {
            publishOverlaySnapshot()
            val motivation = motivationHint()
            publishPunchFeedback(motivation)
            invalidate()
        }
    }

    private fun registerHit(
        bubble: Bubble,
        intensity: Float,
        fullEffect: Boolean = true,
        publishEachHit: Boolean = true,
    ) {
        bubble.hit = true
        lastHandDirection = bubble.side
        avatarPunchAmount = 1f
        hits += 1
        combo += 1
        relief = min(100f, relief + 2.6f + combo * 0.45f)
        slowMotionFactor = min(slowMotionFactor, 0.46f)
        if (publishEachHit) {
            publishOverlaySnapshot()
        }
        val motivation = motivationHint()
        if (publishEachHit) {
            publishPunchFeedback(motivation)
        }

        if (!fullEffect) {
            if (publishEachHit) {
                invalidate()
            }
            return
        }
        val impactX = bubble.x + sin(bubble.wobble.toDouble()).toFloat() * 10f
        val impactY = bubble.y
        val power = intensity.coerceIn(0.7f, 1.9f)
        trimEffectsForRapidHits()
        val shardCount = (14 + (bubble.radius * 0.16f).toInt()).coerceIn(18, 28)
        repeat(shardCount) { index ->
            val angle = ((Math.PI * 2.0 * index) / shardCount) + (random.nextFloat() - 0.5f) * 0.74f
            val speed = 4.8f + random.nextFloat() * 10.5f * power
            particles +=
                Particle(
                    x = impactX,
                    y = impactY,
                    velocityX = kotlin.math.cos(angle).toFloat() * speed,
                    velocityY = kotlin.math.sin(angle).toFloat() * speed - 2.4f,
                    radius = 2.6f + random.nextFloat() * 6.4f,
                    maxLifeMs = 320f + random.nextFloat() * 260f,
                    color = if (index % 3 == 0) Color.WHITE else bubble.palette.strokeColor,
                )
        }
        repeat(3) { index ->
            rings +=
                Ring(
                    x = impactX,
                    y = impactY,
                    radius = bubble.radius * (0.72f + index * 0.34f),
                    alpha = 0.78f - index * 0.18f,
                    color = if (index == 0) Color.WHITE else bubble.palette.strokeColor,
                )
        }
        floatingTexts +=
            FloatingText(
                text = impactText(motivation),
                x = impactX,
                y = impactY - bubble.radius * 0.42f,
                maxLifeMs = 620f,
            )
        trimEffectsForRapidHits()
        if (publishEachHit) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastToneAtMs >= MIN_HIT_TONE_GAP_MS) {
                lastToneAtMs = now
                runCatching { toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 48) }
            }
        }
    }

    private fun trimEffectsForRapidHits() {
        while (particles.size > 56) {
            particles.removeAt(0)
        }
        while (rings.size > 7) {
            rings.removeAt(0)
        }
        while (floatingTexts.size > 2) {
            floatingTexts.removeAt(0)
        }
    }

    private fun publishPunchFeedback(motivation: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPunchFeedbackAtMs < MIN_PUNCH_FEEDBACK_GAP_MS) return
        lastPunchFeedbackAtMs = now
        publishHint(motivation)
        publishMotivation(motivation)
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
        bubbleFillPaint.shader =
            LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat().coerceAtLeast(1f),
                intArrayOf(Color.parseColor("#B9E5FF"), Color.parseColor("#DFF3FF"), Color.parseColor("#83CDB8")),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bubbleFillPaint)
        bubbleFillPaint.shader = null
        shadowPaint.color = Color.parseColor("#66FFFFFF")
        canvas.drawCircle(width * 0.5f, height * 0.42f, min(width, height) * 0.14f, shadowPaint)
        textPaint.alpha = 255
        textPaint.textSize = 34f
        canvas.drawText(if (languageCode.startsWith("zh")) "自由拳击安全模式" else "Free Boxing Safe Mode", width * 0.5f, height * 0.54f, textPaint)
    }

    private fun rebuildClouds() {
        clouds.clear()
        repeat(7) { index ->
            clouds +=
                Cloud(
                    x = random.nextFloat() * width,
                    y = 36f + index * 54f + random.nextFloat() * 28f,
                    width = 120f + random.nextFloat() * 120f,
                    height = 34f + random.nextFloat() * 24f,
                    velocity = 0.04f + random.nextFloat() * 0.08f,
                    alpha = 0.16f + random.nextFloat() * 0.18f,
                )
        }
    }

    private fun readyHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u6C14\u6CE1\u8FDB\u5165\u5C4F\u5E55\u540E\u5373\u53EF\u51FA\u62F3\uFF0C\u4F18\u5148\u51FB\u4E2D\u4F4D\u7F6E\u66F4\u4F4E\u7684\u6C14\u6CE1\u3002"
        } else {
            "Punch once a bubble is on screen. The lowest visible bubble gets priority."
        }

    private fun calibratingHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u6B63\u5728\u6821\u51C6\u73AF\u5883\uFF0C\u8BF7\u5148\u4FDD\u6301\u4E00\u79D2\u5B89\u9759\u3002"
        } else {
            "Calibrating the room. Hold still for a second."
        }

    private fun permissionHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u9700\u8981\u9EA6\u514B\u98CE\u6743\u9650\uFF0C\u81EA\u7531\u62F3\u51FB\u624D\u80FD\u5224\u65AD\u51FB\u4E2D\u3002"
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
            "\u6C14\u6CE1\u6EA2\u51FA\u5C4F\u5E55\u4E86\uFF0C\u4E0B\u4E00\u62F3\u4F18\u5148\u51FB\u4E2D\u4F4D\u7F6E\u66F4\u4F4E\u7684\u6C14\u6CE1\u3002"
        } else {
            "A bubble slipped past. Next punch, prioritize the lowest visible bubble."
        }

    private fun waitNextWaveHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u7B49\u4E0B\u4E00\u7EC4\u6C14\u6CE1\u843D\u4E0B\u6765\uFF0C\u518D\u72E0\u72E0\u51FB\u788E\u5B83\u3002"
        } else {
            "Wait for the next wave, then smash it."
        }

    private fun successHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u5F88\u597D\uFF0C\u5C31\u662F\u8FD9\u79CD\u6253\u788E\u538B\u529B\u7684\u611F\u89C9\u3002"
        } else {
            "Nice. Keep releasing through the punch."
        }

    private fun motivationHint(): String {
        val options =
            if (languageCode.startsWith("zh")) {
                listOf(
                    "\u4F60\u771F\u68D2\uFF01",
                    "\u5E72\u5F97\u6F02\u4EAE\uFF01",
                    "\u592A\u68D2\u4E86\uFF01",
                    "\u6F02\u4EAE\u4E00\u51FB\uFF01",
                    "\u7EE7\u7EED\uFF01",
                    "干得真棒！",
                )
            } else {
                listOf(
                    "Good work!",
                    "Great hit!",
                    "Nice punch!",
                    "You are doing great!",
                    "Keep going!",
                )
            }
        return options[random.nextInt(options.size)]
    }

    private fun impactText(motivation: String): String =
        if (combo >= 4) {
            "${comboHint()}  $motivation"
        } else {
            motivation
        }

    private fun burstHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u7206\u5F00"
        } else {
            "Burst"
        }

    private fun comboHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u8FDE\u51FB x$combo"
        } else {
            "Combo x$combo"
        }

    private fun armingHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u9009\u597D\u8282\u594F\u540E\u70B9\u51FB\u5F00\u59CB\uFF0C\u7CFB\u7EDF\u4F1A\u5148\u6821\u51C6\u73AF\u5883\uFF0C\u518D\u5F00\u653E\u51FB\u6253\u5224\u5B9A\u3002"
        } else {
            "Press Start to arm the round. The detector will calibrate before hit detection opens."
        }

    private fun idleHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u70B9\u51FB\u53F3\u4E0A\u89D2\u9F7F\u8F6E\u8C03\u6574\u914D\u7F6E\uFF0C\u6216\u76F4\u63A5\u70B9\u51FB\u5F00\u59CB\u3002"
        } else {
            "Tap the gear to adjust setup, or press Start directly."
        }

    private fun finishedHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u8FD9\u4E00\u8F6E\u5DF2\u7ECF\u7ED3\u675F\uFF0C\u9009\u9879\u5DF2\u6062\u590D\uFF0C\u53EF\u4EE5\u91CD\u65B0\u5F00\u59CB\u4E0B\u4E00\u8F6E\u3002"
        } else {
            "This round has ended. Your setup options are back and ready for the next round."
        }

    private companion object {
        const val MAX_BLE_HITS_PER_FRAME = 12
        const val MAX_FULL_EFFECT_HITS_PER_FRAME = 1
        const val MIN_HIT_TONE_GAP_MS = 180L
        const val MIN_PUNCH_FEEDBACK_GAP_MS = 700L
    }
}


