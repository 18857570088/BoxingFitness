package com.zclei.boxingfitness

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class ColorGraffitiPersonaOption(
    val zhName: String,
    val enName: String,
    val zhRole: String,
    val enRole: String,
)

internal object ColorGraffitiPersonaCatalog {
    const val CELL_WIDTH = 352
    const val CELL_HEIGHT = 384
    private var cachedSourceRects: List<Rect>? = null

    val options =
        listOf(
            ColorGraffitiPersonaOption("\u7537\u5B69", "Boy", "\u513F\u7AE5", "Child"),
            ColorGraffitiPersonaOption("\u5973\u5B69", "Girl", "\u513F\u7AE5", "Child"),
            ColorGraffitiPersonaOption("\u9752\u5E74\u7537", "Young Man", "\u9752\u5E74", "Youth"),
            ColorGraffitiPersonaOption("\u9752\u5E74\u5973", "Young Woman", "\u9752\u5E74", "Youth"),
            ColorGraffitiPersonaOption("\u804C\u4E1A\u7537", "Professional Man", "\u804C\u4E1A\u4EBA\u7269", "Professional"),
            ColorGraffitiPersonaOption("\u804C\u4E1A\u5973", "Professional Woman", "\u804C\u4E1A\u4EBA\u7269", "Professional"),
            ColorGraffitiPersonaOption("\u4E2D\u5E74\u7537", "Middle-aged Man", "\u4E2D\u5E74", "Midlife"),
            ColorGraffitiPersonaOption("\u4E2D\u5E74\u5973", "Middle-aged Woman", "\u4E2D\u5E74", "Midlife"),
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

internal class ColorGraffitiGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    enum class SpeedPreset(
        val velocity: Float,
        val gapMs: Float,
        val zhLabel: String,
        val enLabel: String,
    ) {
        Slow(0.28f, 840f, "\u6162\u901F", "Slow"),
        Medium(0.42f, 610f, "\u4E2D\u901F", "Mid"),
        Fast(0.58f, 430f, "\u5FEB\u901F", "Fast"),
        ;

        fun label(languageCode: String): String =
            if (languageCode.startsWith("zh")) zhLabel else enLabel
    }

    enum class DensityPreset(
        val count: Int,
        val gapScale: Float,
    ) {
        Solo(1, 1.65f),
        Duo(1, 1.25f),
        Storm(2, 1.05f),
        ;
    }

    enum class VisualMode {
        DarkNight,
        WhiteCanvas,
        RainbowLand,
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
        val bornAtMs: Long,
        val hitStartMs: Long,
        val hitEndMs: Long,
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

    private data class GlowSpot(
        val x: Float,
        val y: Float,
        val radius: Float,
        val color: Int,
    )

    private val bubblePalettes =
        listOf(
            BubblePalette(Color.parseColor("#3DBDE4FF"), Color.parseColor("#8AD6FF"), Color.parseColor("#4D6DD6FF")),
            BubblePalette(Color.parseColor("#38FFCDF3"), Color.parseColor("#FF7BC0"), Color.parseColor("#54FF7BC0")),
            BubblePalette(Color.parseColor("#38C3FFDC"), Color.parseColor("#62E0A3"), Color.parseColor("#4C62E0A3")),
            BubblePalette(Color.parseColor("#3DFFE3B0"), Color.parseColor("#FFC15E"), Color.parseColor("#54FFC15E")),
            BubblePalette(Color.parseColor("#38DDCDFF"), Color.parseColor("#A98BFF"), Color.parseColor("#4CA98BFF")),
        )
    private val neonPaintPalettes =
        listOf(
            BubblePalette(Color.parseColor("#D8FF2D78"), Color.parseColor("#FFFF2D78"), Color.parseColor("#99FF6BA8")),
            BubblePalette(Color.parseColor("#D8FF6B00"), Color.parseColor("#FFFFAA44"), Color.parseColor("#99FFAA44")),
            BubblePalette(Color.parseColor("#D8FFE600"), Color.parseColor("#FFFFFF8A"), Color.parseColor("#99FFE600")),
            BubblePalette(Color.parseColor("#D800FFB2"), Color.parseColor("#FF80FFD8"), Color.parseColor("#9900FFB2")),
            BubblePalette(Color.parseColor("#D800C8FF"), Color.parseColor("#FF80E8FF"), Color.parseColor("#9900C8FF")),
            BubblePalette(Color.parseColor("#D87B2FFF"), Color.parseColor("#FFB47FFF"), Color.parseColor("#997B2FFF")),
            BubblePalette(Color.parseColor("#D8FF2FD8"), Color.parseColor("#FFFF80EE"), Color.parseColor("#99FF2FD8")),
            BubblePalette(Color.parseColor("#D8AAFF00"), Color.parseColor("#FFD4FF70"), Color.parseColor("#99AAFF00")),
        )
    private val rainbowArcColors =
        intArrayOf(
            Color.parseColor("#FF4F85"),
            Color.parseColor("#FF8A38"),
            Color.parseColor("#FFD84A"),
            Color.parseColor("#66E06A"),
            Color.parseColor("#48D6FF"),
            Color.parseColor("#4B74FF"),
            Color.parseColor("#A95CFF"),
        )

    private val spriteSheet = ColorGraffitiPersonaCatalog.loadSprite(context)
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

    private var listener: Listener? = null
    private var languageCode = "zh"
    private var speedPreset = SpeedPreset.Medium
    private var densityPreset = DensityPreset.Duo
    private var personaIndex = 2
    private var hits = 0
    private var combo = 0
    private var misses = 0
    private var relief = 8f
    private var spawnCountdownMs = 220f
    private var avatarPunchAmount = 0f
    private var lastHandDirection = 1f
    private var slowMotionFactor = 1f
    private var detectorReady = false
    private var currentHint = readyHint()
    private var lastFrameMs = 0L
    private var running = false
    private var sessionActive = false
    private var fatalError: Throwable? = null
    private var fatalErrorReporter: ((Throwable) -> Unit)? = null
    private var visualMode = VisualMode.DarkNight
    private var artworkBitmap: Bitmap? = null
    private var artworkCanvas: Canvas? = null
    private var staticBackgroundBitmap: Bitmap? = null
    private var staticBackgroundMode: VisualMode? = null
    private var artworkSeed = 0.173f
    private var lastHintPublishMs = 0L
    private var lastMotivationPublishMs = 0L
    private var lastHapticMs = 0L
    private var lastToneMs = 0L

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

    fun setVisualMode(value: Int) {
        visualMode = VisualMode.entries.getOrElse(value) { VisualMode.DarkNight }
        if (width > 0 && height > 0) {
            clearStaticBackground()
            rebuildClouds()
        }
        publishHint(if (detectorReady) readyHint() else currentHint.ifBlank { readyHint() })
        invalidate()
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
            relief = 8f
            spawnCountdownMs = 220f
            avatarPunchAmount = 0f
            slowMotionFactor = 1f
            bubbles.clear()
            particles.clear()
            rings.clear()
            floatingTexts.clear()
            artworkSeed = 0.173f
            clearArtwork()
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
            avatarPunchAmount = 0f
            slowMotionFactor = 1f
            invalidate()
        }
    }

    fun setPersona(index: Int) {
        runSafely {
            personaIndex = index.coerceIn(0, ColorGraffitiPersonaCatalog.options.lastIndex)
            avatarPunchAmount = max(avatarPunchAmount, 0.35f)
            invalidate()
        }
    }

    fun playCountdownPunch() {
        runSafely {
            lastHandDirection *= -1f
            avatarPunchAmount = 1.25f
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
        clearStaticBackground()
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
            clearStaticBackground()
            rebuildArtworkSurface(w, h)
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

        slowMotionFactor += (1f - slowMotionFactor) * 0.075f
        val timeScale = slowMotionFactor

        if (visualMode == VisualMode.RainbowLand) {
            clouds.forEach { cloud ->
                cloud.x += cloud.velocity * deltaMs
                if (cloud.x - cloud.width > width + 32f) {
                    cloud.x = -cloud.width
                }
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
        drawPaintBackground(canvas, safeWidth, safeHeight)
        if (visualMode == VisualMode.RainbowLand) {
            clouds.forEach { drawCloud(canvas, it) }
        }
        artworkBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        rings.forEach { drawRing(canvas, it) }
        particles.forEach { drawParticle(canvas, it) }
        if (visualMode != VisualMode.DarkNight) {
            floatingTexts.forEach { drawFloatingText(canvas, it) }
        }
    }

    private fun drawPaintBackground(
        canvas: Canvas,
        safeWidth: Float,
        safeHeight: Float,
    ) {
        val cached = staticBackgroundBitmap
        if (
            cached != null &&
            !cached.isRecycled &&
            cached.width == width &&
            cached.height == height &&
            staticBackgroundMode == visualMode
        ) {
            canvas.drawBitmap(cached, 0f, 0f, null)
            return
        }
        rebuildStaticBackground()
        staticBackgroundBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
            ?: drawPaintBackgroundUncached(canvas, safeWidth, safeHeight)
    }

    private fun rebuildStaticBackground() {
        if (width <= 0 || height <= 0) {
            return
        }
        clearStaticBackground()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        staticBackgroundBitmap = bitmap
        staticBackgroundMode = visualMode
        drawPaintBackgroundUncached(Canvas(bitmap), width.toFloat(), height.toFloat())
    }

    private fun clearStaticBackground() {
        staticBackgroundBitmap?.recycle()
        staticBackgroundBitmap = null
        staticBackgroundMode = null
    }

    private fun drawPaintBackgroundUncached(
        canvas: Canvas,
        safeWidth: Float,
        safeHeight: Float,
    ) {
        when (visualMode) {
            VisualMode.DarkNight -> {
                canvas.drawColor(Color.parseColor("#0A0A0F"))
                val spots =
                    listOf(
                        GlowSpot(safeWidth * 0.2f, safeHeight * 0.3f, min(safeWidth, safeHeight) * 0.18f, Color.parseColor("#181E00C8")),
                        GlowSpot(safeWidth * 0.8f, safeHeight * 0.52f, min(safeWidth, safeHeight) * 0.22f, Color.parseColor("#1400B478")),
                        GlowSpot(safeWidth * 0.5f, safeHeight * 0.8f, min(safeWidth, safeHeight) * 0.2f, Color.parseColor("#18C85000")),
                    )
                spots.forEach { spot ->
                    bubbleFillPaint.shader =
                        RadialGradient(
                            spot.x,
                            spot.y,
                            spot.radius,
                            intArrayOf(spot.color, Color.TRANSPARENT),
                            floatArrayOf(0f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                    canvas.drawRect(0f, 0f, safeWidth, safeHeight, bubbleFillPaint)
                    bubbleFillPaint.shader = null
                }
                val gridStep = dp(24f).coerceAtLeast(18f)
                bubbleStrokePaint.shader = null
                bubbleStrokePaint.strokeWidth = 1f
                bubbleStrokePaint.color = Color.parseColor("#12FFFFFF")
                var x = 0f
                while (x <= safeWidth) {
                    canvas.drawLine(x, 0f, x, safeHeight, bubbleStrokePaint)
                    x += gridStep * 2f
                }
                var y = 0f
                while (y <= safeHeight) {
                    canvas.drawLine(0f, y, safeWidth, y, bubbleStrokePaint)
                    y += gridStep * 2f
                }
            }

            VisualMode.WhiteCanvas -> {
                canvas.drawColor(Color.parseColor("#FFFDFC"))
                bubbleFillPaint.shader =
                    LinearGradient(
                        0f,
                        0f,
                        0f,
                        safeHeight,
                        intArrayOf(Color.parseColor("#FFFDFC"), Color.parseColor("#FAF5EE"), Color.parseColor("#F4EEE4")),
                        floatArrayOf(0f, 0.52f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                canvas.drawRect(0f, 0f, safeWidth, safeHeight, bubbleFillPaint)
                bubbleFillPaint.shader =
                    RadialGradient(
                        safeWidth * 0.18f,
                        safeHeight * 0.16f,
                        min(safeWidth, safeHeight) * 0.34f,
                        intArrayOf(Color.parseColor("#18FFFFFF"), Color.TRANSPARENT),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                canvas.drawRect(0f, 0f, safeWidth, safeHeight, bubbleFillPaint)
                bubbleFillPaint.shader =
                    RadialGradient(
                        safeWidth * 0.82f,
                        safeHeight * 0.86f,
                        min(safeWidth, safeHeight) * 0.30f,
                        intArrayOf(Color.parseColor("#14E0D2C2"), Color.TRANSPARENT),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                canvas.drawRect(0f, 0f, safeWidth, safeHeight, bubbleFillPaint)
                bubbleFillPaint.shader = null
                bubbleStrokePaint.shader = null
                bubbleStrokePaint.strokeWidth = 1f
                bubbleStrokePaint.color = Color.parseColor("#09000000")
                var x = 0f
                while (x <= safeWidth) {
                    canvas.drawLine(x, 0f, x, safeHeight, bubbleStrokePaint)
                    x += dp(32f)
                }
                var y = 0f
                while (y <= safeHeight) {
                    canvas.drawLine(0f, y, safeWidth, y, bubbleStrokePaint)
                    y += dp(32f)
                }
                bubbleStrokePaint.color = Color.parseColor("#12B1A18C")
                bubbleStrokePaint.strokeWidth = dp(0.8f)
                var fiberRow = 0
                var fiberY = dp(12f)
                while (fiberY < safeHeight) {
                    var fiberX = if (fiberRow % 2 == 0) dp(10f) else dp(28f)
                    while (fiberX < safeWidth) {
                        val fiberLength = dp(5.5f + (fiberRow % 3))
                        val fiberTilt = if ((fiberRow + (fiberX / dp(44f)).toInt()) % 2 == 0) dp(0.9f) else -dp(0.7f)
                        canvas.drawLine(fiberX, fiberY, fiberX + fiberLength, fiberY + fiberTilt, bubbleStrokePaint)
                        fiberX += dp(44f)
                    }
                    fiberY += dp(26f)
                    fiberRow += 1
                }
                particlePaint.color = Color.parseColor("#10CABBA6")
                var dotRow = 0
                var dotY = dp(18f)
                while (dotY < safeHeight) {
                    var dotX = if (dotRow % 2 == 0) dp(20f) else dp(40f)
                    while (dotX < safeWidth) {
                        canvas.drawCircle(dotX, dotY, dp(0.6f), particlePaint)
                        dotX += dp(64f)
                    }
                    dotY += dp(36f)
                    dotRow += 1
                }
                bubbleStrokePaint.color = Color.parseColor("#18B4A691")
                bubbleStrokePaint.strokeWidth = dp(1f)
                canvas.drawRect(dp(6f), dp(6f), safeWidth - dp(6f), safeHeight - dp(6f), bubbleStrokePaint)
            }

            VisualMode.RainbowLand -> {
                canvas.drawColor(Color.parseColor("#DFF3FF"))
                bubbleFillPaint.alpha = 255
                bubbleFillPaint.shader =
                    LinearGradient(
                        0f,
                        0f,
                        0f,
                        safeHeight,
                        intArrayOf(Color.parseColor("#C8E8FA"), Color.parseColor("#DDF0FB"), Color.parseColor("#8BC48A")),
                        floatArrayOf(0f, 0.6f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                canvas.drawRect(0f, 0f, safeWidth, safeHeight, bubbleFillPaint)
                bubbleFillPaint.shader = null

                shadowPaint.shader = null
                shadowPaint.alpha = 255
                shadowPaint.color = Color.parseColor("#72FFFFFF")
                canvas.drawCircle(safeWidth * 0.82f, safeHeight * 0.18f, min(safeWidth, safeHeight) * 0.12f, shadowPaint)
                shadowPaint.color = Color.parseColor("#30FFFFFF")
                canvas.drawCircle(safeWidth * 0.82f, safeHeight * 0.18f, min(safeWidth, safeHeight) * 0.17f, shadowPaint)
                bubbleFillPaint.alpha = 255
                bubbleFillPaint.shader =
                    LinearGradient(
                        0f,
                        safeHeight * 0.58f,
                        0f,
                        safeHeight,
                        intArrayOf(Color.parseColor("#056CC3BA"), Color.parseColor("#5C429E88")),
                        null,
                        Shader.TileMode.CLAMP,
                    )
                canvas.drawRect(0f, safeHeight * 0.58f, safeWidth, safeHeight, bubbleFillPaint)
                bubbleFillPaint.shader = null
            }
        }
    }

    private fun rebuildArtworkSurface(
        widthPx: Int,
        heightPx: Int,
    ) {
        if (widthPx <= 0 || heightPx <= 0) {
            return
        }
        artworkBitmap?.recycle()
        artworkBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        artworkCanvas = Canvas(artworkBitmap!!)
        clearArtwork()
    }

    private fun clearArtwork() {
        artworkBitmap?.eraseColor(Color.TRANSPARENT)
    }

    private fun isWhiteCanvasMode(): Boolean = visualMode == VisualMode.WhiteCanvas

    private fun isRainbowMode(): Boolean = visualMode == VisualMode.RainbowLand

    private fun nextPaintPoint(
        marginX: Float = min(dp(18f), width * 0.03f),
        marginY: Float = min(dp(18f), height * 0.03f),
    ): Pair<Float, Float> {
        val safeMarginX = marginX.coerceAtMost(width * 0.36f)
        val safeMarginY = marginY.coerceAtMost(height * 0.28f)
        val x = safeMarginX + random.nextFloat() * max(1f, width - safeMarginX * 2f)
        val y = safeMarginY + random.nextFloat() * max(1f, height - safeMarginY * 2f)
        return x to y
    }

    private fun paintSplash(
        centerX: Float,
        centerY: Float,
        baseRadius: Float,
        palette: BubblePalette,
        power: Float,
    ) {
        val canvas = artworkCanvas ?: return
        val transparentStroke = palette.strokeColor and 0x00FFFFFF
        val transparentGlow = palette.glowColor and 0x00FFFFFF

        bubbleFillPaint.shader =
            RadialGradient(
                centerX,
                centerY,
                baseRadius,
                intArrayOf(palette.strokeColor, palette.fillColor, transparentGlow),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawCircle(centerX, centerY, baseRadius, bubbleFillPaint)
        bubbleFillPaint.shader = null

        val splashCount = 5 + (power * 2f).toInt()
        repeat(splashCount) {
            val angle = random.nextFloat() * (Math.PI.toFloat() * 2f)
            val distance = baseRadius * (0.35f + random.nextFloat() * 1.15f)
            val splashX = centerX + kotlin.math.cos(angle) * distance
            val splashY = centerY + kotlin.math.sin(angle) * distance
            val radius = dp(3.2f) + random.nextFloat() * baseRadius * 0.38f
            bubbleFillPaint.shader =
                RadialGradient(
                    splashX,
                    splashY,
                    radius,
                    intArrayOf(palette.strokeColor, transparentStroke),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            canvas.drawCircle(splashX, splashY, radius, bubbleFillPaint)
            bubbleFillPaint.shader = null
            if (random.nextFloat() > 0.42f) {
                val trailX = splashX + kotlin.math.cos(angle) * (dp(12f) + random.nextFloat() * baseRadius * 0.65f)
                val trailY = splashY + kotlin.math.sin(angle) * (dp(12f) + random.nextFloat() * baseRadius * 0.65f)
                bubbleStrokePaint.color = palette.strokeColor
                bubbleStrokePaint.alpha = 112
                bubbleStrokePaint.strokeCap = Paint.Cap.ROUND
                bubbleStrokePaint.strokeWidth = dp(1.2f) + random.nextFloat() * dp(2.2f)
                canvas.drawLine(splashX, splashY, trailX, trailY, bubbleStrokePaint)
            }
        }

        bubbleFillPaint.color = Color.WHITE
        bubbleFillPaint.alpha = (70 + power * 34f).toInt().coerceIn(0, 170)
        canvas.drawOval(
            RectF(
                centerX - baseRadius * 0.34f,
                centerY - baseRadius * 0.48f,
                centerX - baseRadius * 0.02f,
                centerY - baseRadius * 0.12f,
            ),
            bubbleFillPaint,
        )
        canvas.drawOval(
            RectF(
                centerX + baseRadius * 0.08f,
                centerY - baseRadius * 0.02f,
                centerX + baseRadius * 0.24f,
                centerY + baseRadius * 0.18f,
            ),
            bubbleFillPaint,
        )
    }

    private fun paintRainbowBurst(
        centerX: Float,
        centerY: Float,
        baseRadius: Float,
        power: Float,
    ) {
        val canvas = artworkCanvas ?: return
        val rainbowDiameter = randomRainbowDiameterPx(power)
        val rainbowRadius = rainbowDiameter * 0.5f
        val arcCenterX = centerX.coerceIn(rainbowRadius + dp(8f), width - rainbowRadius - dp(8f))
        val arcCenterY = centerY.coerceIn(rainbowRadius + dp(10f), height - dp(18f))
        val strokeBase = dp(5.4f) + power * dp(1.25f)
        val bandStep = strokeBase * 0.70f
        val sweepAngle = 130f + random.nextFloat() * 28f
        val startAngle = 198f + random.nextFloat() * 12f
        val signedSweep = sweepAngle

        bubbleFillPaint.alpha = 255
        bubbleFillPaint.shader =
            RadialGradient(
                arcCenterX,
                arcCenterY - rainbowRadius * 0.28f,
                rainbowRadius * 1.24f,
                intArrayOf(Color.parseColor("#28FFFFFF"), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawCircle(arcCenterX, arcCenterY - rainbowRadius * 0.28f, rainbowRadius * 1.24f, bubbleFillPaint)
        bubbleFillPaint.shader = null

        bubbleStrokePaint.strokeCap = Paint.Cap.ROUND
        bubbleStrokePaint.maskFilter = BlurMaskFilter(max(dp(3.2f), strokeBase * 0.62f), BlurMaskFilter.Blur.NORMAL)
        bubbleStrokePaint.color = Color.WHITE
        bubbleStrokePaint.alpha = 18
        bubbleStrokePaint.strokeWidth = strokeBase * 2.6f
        canvas.drawArc(
            RectF(
                arcCenterX - rainbowRadius * 0.98f,
                arcCenterY - rainbowRadius * 0.98f,
                arcCenterX + rainbowRadius * 0.98f,
                arcCenterY + rainbowRadius * 0.98f,
            ),
            startAngle,
            signedSweep,
            false,
            bubbleStrokePaint,
        )

        rainbowArcColors.forEachIndexed { index, color ->
            val radius = rainbowRadius - index * bandStep
            if (radius <= strokeBase) {
                return@forEachIndexed
            }
            val rect = RectF(arcCenterX - radius, arcCenterY - radius, arcCenterX + radius, arcCenterY + radius)
            bubbleStrokePaint.color = color
            bubbleStrokePaint.alpha = 48
            bubbleStrokePaint.strokeWidth = strokeBase * 2.1f
            bubbleStrokePaint.maskFilter = BlurMaskFilter(max(dp(2.0f), strokeBase * 0.38f), BlurMaskFilter.Blur.NORMAL)
            canvas.drawArc(rect, startAngle, signedSweep, false, bubbleStrokePaint)

            bubbleStrokePaint.color = color
            bubbleStrokePaint.alpha = 164
            bubbleStrokePaint.strokeWidth = strokeBase * 1.08f
            bubbleStrokePaint.maskFilter = BlurMaskFilter(max(dp(0.9f), strokeBase * 0.16f), BlurMaskFilter.Blur.NORMAL)
            canvas.drawArc(rect, startAngle, signedSweep, false, bubbleStrokePaint)

            bubbleStrokePaint.color = Color.WHITE
            bubbleStrokePaint.alpha = 22
            bubbleStrokePaint.strokeWidth = max(dp(0.8f), strokeBase * 0.24f)
            bubbleStrokePaint.maskFilter = BlurMaskFilter(dp(0.8f), BlurMaskFilter.Blur.NORMAL)
            canvas.drawArc(rect, startAngle, signedSweep, false, bubbleStrokePaint)
        }
        bubbleStrokePaint.maskFilter = null

        repeat(2 + (power * 1.5f).toInt()) {
            val angle = Math.toRadians((startAngle + signedSweep * (0.18f + random.nextFloat() * 0.64f)).toDouble())
            val radius = rainbowRadius * (0.50f + random.nextFloat() * 0.32f)
            val dripX = arcCenterX + kotlin.math.cos(angle).toFloat() * radius
            val dripY = arcCenterY + kotlin.math.sin(angle).toFloat() * radius
            val color = rainbowArcColors[random.nextInt(rainbowArcColors.size)]
            val mistRadius = dp(5.0f) + random.nextFloat() * max(dp(5f), strokeBase * 1.2f)
            bubbleFillPaint.color = color
            bubbleFillPaint.alpha = 34
            canvas.drawCircle(dripX, dripY, mistRadius, bubbleFillPaint)
        }

        repeat(4 + power.toInt()) {
            val angle = Math.toRadians((startAngle + signedSweep * random.nextFloat()).toDouble())
            val radius = rainbowRadius * (0.38f + random.nextFloat() * 0.82f)
            val splashX = arcCenterX + kotlin.math.cos(angle).toFloat() * radius
            val splashY = arcCenterY + kotlin.math.sin(angle).toFloat() * radius
            val dotRadius = dp(4.0f) + random.nextFloat() * dp(8.0f)
            val color = rainbowArcColors[random.nextInt(rainbowArcColors.size)]
            bubbleFillPaint.shader =
                RadialGradient(
                    splashX,
                    splashY,
                    dotRadius,
                    intArrayOf((color and 0x00FFFFFF) or (0x3A shl 24), color and 0x00FFFFFF),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            canvas.drawCircle(splashX, splashY, dotRadius, bubbleFillPaint)
            bubbleFillPaint.shader = null
        }

        bubbleFillPaint.color = Color.WHITE
        bubbleFillPaint.alpha = (24 + power * 10f).toInt().coerceIn(0, 70)
        canvas.drawOval(
            RectF(
                arcCenterX - rainbowRadius * 0.28f,
                arcCenterY - rainbowRadius * 0.44f,
                arcCenterX - rainbowRadius * 0.02f,
                arcCenterY - rainbowRadius * 0.12f,
            ),
            bubbleFillPaint,
        )
    }

    private fun randomRainbowDiameterPx(power: Float): Float {
        val minDiameter = cmToPx(1.0f)
        val maxDiameter = cmToPx(5.0f).coerceAtMost(min(width, height) * 0.82f)
        val largeBias = sqrt(random.nextFloat())
        val powerBoost = ((power.coerceIn(0.7f, 1.9f) - 0.7f) / 1.2f) * 0.16f
        val t = (largeBias + powerBoost).coerceIn(0f, 1f)
        return minDiameter + (maxDiameter - minDiameter).coerceAtLeast(0f) * t
    }

    private fun cmToPx(value: Float): Float {
        val xdpi = resources.displayMetrics.xdpi.takeIf { it > 0f } ?: resources.displayMetrics.densityDpi.toFloat()
        return xdpi * value / 2.54f
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

    private fun drawAvatar(
        canvas: Canvas,
        nowMs: Float,
    ) {
        val drawHeight = min(height * 0.59f, width * 0.345f)
        val sourceRect = ColorGraffitiPersonaCatalog.sourceRect(personaIndex, spriteSheet)
        val drawWidth = drawHeight * ColorGraffitiPersonaCatalog.aspectRatio(personaIndex, spriteSheet)
        val handDirection = lastHandDirection
        val activeMotion = if (sessionActive) 1f else 0.35f
        val fightingSway = sin(nowMs * 0.0065f) * activeMotion
        val rhythmicPunch = if (sessionActive) max(0f, sin(nowMs * 0.0085f)) * 0.34f else 0f
        val motionAmount = max(avatarPunchAmount, rhythmicPunch)
        val centerX = width * 0.5f + handDirection * (motionAmount * 58f + fightingSway * 7f)
        val centerY = height * 0.595f + sin(nowMs * 0.0026f) * 6f - motionAmount * 15f

        avatarShadowPaint.color = Color.parseColor("#66123452")
        canvas.drawOval(
            RectF(
                centerX - drawWidth * 0.42f,
                centerY + drawHeight * 0.34f,
                centerX + drawWidth * 0.42f,
                centerY + drawHeight * 0.50f,
            ),
            avatarShadowPaint,
        )

        val bitmap = spriteSheet
        if (bitmap != null) {
            val destination =
                RectF(
                    -drawWidth * 0.5f,
                    -drawHeight * 0.69f,
                    drawWidth * 0.5f,
                    drawHeight * 0.35f,
            )
            canvas.save()
            canvas.translate(centerX, centerY)
            canvas.rotate(handDirection * (motionAmount * 13f + fightingSway * 1.8f))
            canvas.scale(1f + motionAmount * 0.05f, 1.015f - motionAmount * 0.02f)
            canvas.drawBitmap(bitmap, sourceRect, destination, null)
            canvas.restore()
        } else {
            fallbackAvatarPaint.color = Color.parseColor("#4DFFFFFF")
            canvas.drawCircle(centerX, centerY - drawHeight * 0.24f, drawHeight * 0.13f, fallbackAvatarPaint)
            canvas.drawRoundRect(
                RectF(
                    centerX - drawWidth * 0.18f,
                    centerY - drawHeight * 0.05f,
                    centerX + drawWidth * 0.18f,
                    centerY + drawHeight * 0.28f,
                ),
                drawWidth * 0.08f,
                drawWidth * 0.08f,
                fallbackAvatarPaint,
            )
        }

        if (motionAmount > 0.08f) {
            bubbleStrokePaint.color = if (handDirection < 0f) Color.parseColor("#8C88FFE6") else Color.parseColor("#8CFFC75E")
            bubbleStrokePaint.strokeWidth = 9f
            bubbleStrokePaint.strokeCap = Paint.Cap.ROUND
            val baseX = centerX + handDirection * drawWidth * 0.16f
            val baseY = centerY - drawHeight * 0.235f
            for (index in 0 until 3) {
                bubbleStrokePaint.alpha = (82 + index * 42).coerceAtMost(255)
                canvas.drawLine(
                    baseX - handDirection * index * 16f,
                    baseY + index * 12f,
                    baseX + handDirection * (52f + motionAmount * 76f + index * 18f),
                    baseY - 10f + index * 14f,
                    bubbleStrokePaint,
                )
            }
        }

        avatarPunchAmount *= 0.88f
    }

    private fun drawBubble(
        canvas: Canvas,
        bubble: Bubble,
        nowMs: Long,
    ) {
        val centerX = bubble.x + sin(bubble.wobble.toDouble()).toFloat() * 10f
        val centerY = bubble.y
        val active = nowMs in bubble.hitStartMs..bubble.hitEndMs
        val alpha = if (bubble.hit) (1f - bubble.hitAgeMs / 300f).coerceIn(0f, 1f) else 1f

        shadowPaint.color = bubble.palette.glowColor
        shadowPaint.alpha = (alpha * if (active) 110 else 72).toInt().coerceIn(0, 255)
        canvas.drawCircle(centerX, centerY, bubble.radius + if (active) 14f else 8f, shadowPaint)

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

        bubbleFillPaint.color = Color.WHITE
        bubbleFillPaint.alpha = (alpha * 220f).toInt().coerceIn(0, 255)
        canvas.drawOval(
            RectF(
                -bubble.radius * 0.40f,
                -bubble.radius * 0.52f,
                -bubble.radius * 0.04f,
                -bubble.radius * 0.04f,
            ),
            bubbleFillPaint,
        )
        canvas.drawOval(
            RectF(
                bubble.radius * 0.10f,
                bubble.radius * 0.02f,
                bubble.radius * 0.26f,
                bubble.radius * 0.26f,
            ),
            bubbleFillPaint,
        )
        canvas.restore()

        if (active && !bubble.hit) {
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

    private fun spawnBurst(nowMs: Long) {
        repeat(densityPreset.count) { offset ->
            spawnBubble(nowMs + offset * 20L)
        }
    }

    private fun spawnBubble(nowMs: Long) {
        val side = if (random.nextBoolean()) -1 else 1
        val radius = bubbleRadiusPx()
        val centerGap = radius * 0.88f
        val edgePadding = radius + dp(16f)
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
                width - edgePadding
            }
        val laneBase =
            if (laneMax > laneMin) {
                laneMin + random.nextFloat() * (laneMax - laneMin)
            } else {
                width * 0.5f + side * (radius * 1.9f)
            }
        bubbles +=
            Bubble(
                side = side,
                x = laneBase.coerceIn(edgePadding, width - edgePadding),
                y = bubbleStartY(radius),
                radius = radius,
                velocityY = speedPreset.velocity + random.nextFloat() * 0.08f,
                drift = (random.nextFloat() - 0.5f) * 0.08f,
                wobble = random.nextFloat() * (Math.PI.toFloat() * 2f),
                wobbleSpeed = 0.0025f + random.nextFloat() * 0.0016f,
                squash = 1f,
                bornAtMs = nowMs,
                hitStartMs = nowMs + 500L,
                hitEndMs = nowMs + 1_000L,
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

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    private fun handlePunch(
        nowMs: Long,
        intensity: Float,
    ) {
        if (!detectorReady) {
            publishHintThrottled(calibratingHint(), nowMs)
            return
        }
        avatarPunchAmount = max(avatarPunchAmount, 0.55f)
        combo = 0
        hits += 1
        relief = min(100f, relief + 1.8f + intensity * 1.15f)
        slowMotionFactor = min(slowMotionFactor, 0.46f)
        publishOverlaySnapshot()
        publishHintThrottled(paintingHint(), nowMs)
        publishMotivationThrottled(motivationHint(), nowMs)

        val power = intensity.coerceIn(0.7f, 1.9f)
        val normalizedPower = ((power - 0.7f) / 1.2f).coerceIn(0f, 1f)
        val palette = neonPaintPalettes[(hits - 1).mod(neonPaintPalettes.size)]
        val impactRadius =
            if (isRainbowMode()) {
                bubbleRadiusPx() * (0.72f + normalizedPower * 1.38f)
            } else {
                bubbleRadiusPx() * (0.92f + power * 0.66f)
            }
        val sizeVariance = if (isRainbowMode()) 0.84f + random.nextFloat() * 0.22f else 1f
        val variedRadius = impactRadius * sizeVariance
        val (impactX, impactY) =
            if (isRainbowMode()) {
                nextPaintPoint(variedRadius * 1.15f, variedRadius * 0.95f)
            } else {
                nextPaintPoint()
            }
        if (isRainbowMode()) {
            paintRainbowBurst(impactX, impactY, variedRadius, power)
        } else {
            paintSplash(impactX, impactY, impactRadius, palette, power)
        }

        trimEffectsForRapidHits()
        val shardCount =
            if (isRainbowMode()) {
                18 + (power * 10f).toInt()
            } else {
                18 + (power * 10f).toInt()
            }
        repeat(shardCount) { index ->
            val angle = ((Math.PI * 2.0 * index) / shardCount.coerceAtLeast(12)) + (random.nextFloat() - 0.5f) * 0.9f
            val speed = 3.4f + random.nextFloat() * 18.5f * power
            particles +=
                Particle(
                    x = impactX,
                    y = impactY,
                    velocityX = kotlin.math.cos(angle).toFloat() * speed,
                    velocityY = kotlin.math.sin(angle).toFloat() * speed - 1.2f,
                    radius = 2.8f + random.nextFloat() * 7.5f,
                    maxLifeMs = 360f + random.nextFloat() * 360f,
                    color =
                        if (isRainbowMode()) {
                            rainbowArcColors[random.nextInt(rainbowArcColors.size)]
                        } else if (random.nextBoolean()) {
                            palette.strokeColor
                        } else {
                            palette.glowColor
                        },
                )
        }
        repeat(4) { index ->
            rings +=
                Ring(
                    x = impactX,
                    y = impactY,
                    radius = (if (isRainbowMode()) variedRadius else impactRadius) * (0.78f + index * 0.42f),
                    alpha = 0.76f - index * 0.11f,
                    color = if (isRainbowMode()) rainbowArcColors[index.mod(rainbowArcColors.size)] else palette.strokeColor,
                )
        }
        if (HAPTIC_FEEDBACK_ENABLED && nowMs - lastHapticMs >= HAPTIC_MIN_GAP_MS) {
            lastHapticMs = nowMs
            Haptics.tap(context)
        }
        if (TONE_FEEDBACK_ENABLED && nowMs - lastToneMs >= TONE_MIN_GAP_MS) {
            lastToneMs = nowMs
            playPaintHitTone(power)
        }
    }

    private fun playPaintHitTone(power: Float) {
        val primaryDuration = 42 + (power.coerceIn(0.7f, 1.9f) * 18f).toInt()
        val sparkleDuration = 36 + (power.coerceIn(0.7f, 1.9f) * 14f).toInt()
        runCatching { toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, primaryDuration) }
        postDelayed(
            { runCatching { toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, sparkleDuration) } },
            46L,
        )
    }

    private fun trimEffectsForRapidHits() {
        while (particles.size > MAX_PARTICLES_BEFORE_HIT) {
            particles.removeAt(0)
        }
        while (rings.size > MAX_RINGS_BEFORE_HIT) {
            rings.removeAt(0)
        }
        while (floatingTexts.size > MAX_FLOATING_TEXT_BEFORE_HIT) {
            floatingTexts.removeAt(0)
        }
    }

    private fun registerMiss() {
        combo = 0
        misses += 1
        publishOverlaySnapshot()
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

    private fun publishHintThrottled(
        value: String,
        nowMs: Long,
    ) {
        if (value == currentHint || nowMs - lastHintPublishMs >= HINT_MIN_GAP_MS) {
            lastHintPublishMs = nowMs
            publishHint(value)
        }
    }

    private fun publishMotivation(value: String) {
        listener?.onMotivationCue(value)
    }

    private fun publishMotivationThrottled(
        value: String,
        nowMs: Long,
    ) {
        if (nowMs - lastMotivationPublishMs < MOTIVATION_MIN_GAP_MS) {
            return
        }
        lastMotivationPublishMs = nowMs
        publishMotivation(value)
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
        canvas.drawColor(Color.parseColor("#0A0A0F"))
        bubbleFillPaint.shader =
            RadialGradient(
                width * 0.34f,
                height * 0.38f,
                min(width, height) * 0.24f,
                intArrayOf(Color.parseColor("#26FF2D78"), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bubbleFillPaint)
        bubbleFillPaint.shader =
            RadialGradient(
                width * 0.72f,
                height * 0.55f,
                min(width, height) * 0.28f,
                intArrayOf(Color.parseColor("#2400C8FF"), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bubbleFillPaint)
        bubbleFillPaint.shader = null
        bubbleStrokePaint.color = Color.parseColor("#18FFFFFF")
        bubbleStrokePaint.strokeWidth = 1f
        val step = dp(24f).coerceAtLeast(18f) * 2f
        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), bubbleStrokePaint)
            x += step
        }
        var y = 0f
        while (y <= height) {
            canvas.drawLine(0f, y, width.toFloat(), y, bubbleStrokePaint)
            y += step
        }
        textPaint.alpha = 255
        textPaint.textSize = 34f
        canvas.drawText(
            if (languageCode.startsWith("zh")) "色彩涂鸦安全模式" else "Color Graffiti Safe Mode",
            width * 0.5f,
            height * 0.54f,
            textPaint,
        )
    }

    private fun rebuildClouds() {
        clouds.clear()
        val rainbowMode = visualMode == VisualMode.RainbowLand
        val cloudCount = if (rainbowMode) 6 else 7
        repeat(cloudCount) { index ->
            clouds +=
                Cloud(
                    x = random.nextFloat() * width,
                    y = if (rainbowMode) 28f + index * 62f + random.nextFloat() * 26f else 36f + index * 54f + random.nextFloat() * 28f,
                    width = if (rainbowMode) 170f + random.nextFloat() * 210f else 120f + random.nextFloat() * 120f,
                    height = if (rainbowMode) 44f + random.nextFloat() * 34f else 34f + random.nextFloat() * 24f,
                    velocity = if (rainbowMode) 0.018f + random.nextFloat() * 0.032f else 0.04f + random.nextFloat() * 0.08f,
                    alpha = if (rainbowMode) 0.24f + random.nextFloat() * 0.14f else 0.16f + random.nextFloat() * 0.18f,
                )
        }
    }

    private fun readyHint(): String =
        when {
            languageCode.startsWith("zh") && isWhiteCanvasMode() -> "识别已就绪，尽情出拳，让霓虹颜料在纯白画布上尽情绽开。"
            languageCode.startsWith("zh") && isRainbowMode() -> "识别已就绪，尽情出拳，让绚丽彩虹在乐园背景上瞬间绽开。"
            languageCode.startsWith("zh") -> "识别已就绪，尽情出拳，让霓虹颜料在画布上炸开。"
            isWhiteCanvasMode() -> "Detection is ready. Punch freely and let neon paint bloom across the white canvas."
            isRainbowMode() -> "Detection is ready. Punch freely and let rainbow arcs bloom across the park backdrop."
            else -> "Detection is ready. Punch freely and let the neon paint explode across the canvas."
        }

    private fun calibratingHint(): String =
        when {
            languageCode.startsWith("zh") && isWhiteCanvasMode() -> "正在校准环境，请先安静一秒，纯白画布马上准备迎接第一抹颜色。"
            languageCode.startsWith("zh") && isRainbowMode() -> "正在校准环境，请先安静一秒，彩虹乐园马上准备接住第一道彩虹。"
            languageCode.startsWith("zh") -> "正在校准环境，请先安静一秒，马上开始自由涂鸦。"
            isWhiteCanvasMode() -> "Calibrating the room. Hold still for a second. The white canvas is almost ready."
            isRainbowMode() -> "Calibrating the room. Hold still for a second. Rainbow Park is almost ready."
            else -> "Calibrating the room. Hold still for a second before the paint starts."
        }

    private fun permissionHint(): String =
        if (languageCode.startsWith("zh")) {
            "需要蓝牙设备权限，色彩涂鸦才能识别出拳并炸开彩色油漆。"
        } else {
            "Bluetooth connection is required for Color Graffiti punch detection."
        }

    private fun errorHint(): String =
        if (languageCode.startsWith("zh")) {
            "蓝牙击中识别暂时不可用，请稍后再试，画布会等你回来。"
        } else {
            "Punch detection is unavailable right now. Please try again in a moment."
        }

    private fun missHint(): String =
        if (languageCode.startsWith("zh")) {
            "\u518D\u7B49\u534A\u62CD\uFF0C\u7B49\u6C14\u6CE1\u8FDB\u5165\u6700\u4F73\u51FB\u4E2D\u7A97\u3002"
        } else {
            "Wait half a beat for the best hit window."
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
            if (languageCode.startsWith("zh") && isWhiteCanvasMode()) {
                listOf(
                    "真棒！",
                    "漂亮！",
                    "继续！",
                    "上色！",
                    "好拳！",
                    "不错！",
                )
            } else if (languageCode.startsWith("zh") && isRainbowMode()) {
                listOf(
                    "彩虹！",
                    "漂亮！",
                    "真亮！",
                    "继续！",
                    "好拳！",
                    "真棒！",
                )
            } else if (languageCode.startsWith("zh")) {
                listOf(
                    "真棒！",
                    "漂亮！",
                    "太棒了！",
                    "好拳！",
                    "继续！",
                    "不错！",
                )
            } else if (isRainbowMode()) {
                listOf(
                    "Rainbow!",
                    "Bright!",
                    "Beautiful!",
                    "Great!",
                    "Nice!",
                    "Go!",
                )
            } else if (isWhiteCanvasMode()) {
                listOf(
                    "Nice!",
                    "Great!",
                    "Beautiful!",
                    "Keep going!",
                    "Splash!",
                    "Good!",
                )
            } else {
                listOf(
                    "Nice!",
                    "Great!",
                    "Good!",
                    "Keep going!",
                    "Punch!",
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
        when {
            languageCode.startsWith("zh") && isWhiteCanvasMode() -> "点击开始后自由出拳，每次有效击打都会在纯白画布上炸开一团霓虹色彩。"
            languageCode.startsWith("zh") && isRainbowMode() -> "点击开始后自由出拳，每次有效击打都会在对应位置炸开一弧绚丽彩虹。"
            languageCode.startsWith("zh") -> "点击开始后自由出拳，每次有效击打都会在画布上炸开一团霓虹色彩。"
            isWhiteCanvasMode() -> "Press Start, then punch freely. Every valid hit will burst neon paint across the white canvas."
            isRainbowMode() -> "Press Start, then punch freely. Every valid hit will burst a vivid rainbow arc at the impact point."
            else -> "Press Start, then punch freely. Every valid hit will burst neon paint across the canvas."
        }

    private fun idleHint(): String =
        if (languageCode.startsWith("zh")) {
            "右上角选择时长和模式后直接开始，随意挥拳，没有失败惩罚。"
        } else {
            "Pick a duration and mode, then start. Punch freely with no failure penalty."
        }

    private fun finishedHint(): String =
        when {
            languageCode.startsWith("zh") && isWhiteCanvasMode() -> "这一轮纯白画布已经留下你的抽象画作，可以重新开始下一轮。"
            languageCode.startsWith("zh") && isRainbowMode() -> "这一轮彩虹乐园已经留下你的彩虹抽象画作，可以重新开始下一轮。"
            languageCode.startsWith("zh") -> "这一轮色彩涂鸦已经完成，你的抽象画作已留在画布上，可以重新开始下一轮。"
            isWhiteCanvasMode() -> "White Canvas is complete. Your abstract artwork is ready, and you can start another round any time."
            isRainbowMode() -> "Rainbow Park is complete. Your rainbow artwork is ready, and you can start another round any time."
            else -> "This color round is complete. Your abstract artwork is ready, and you can start another round any time."
        }

    private fun paintingHint(): String =
        when {
            languageCode.startsWith("zh") && isWhiteCanvasMode() -> "继续出拳，让纯白画布叠出更丰富的色彩层次。"
            languageCode.startsWith("zh") && isRainbowMode() -> "继续出拳，让更多彩虹弧线在乐园背景上层层绽开。"
            languageCode.startsWith("zh") -> "继续出拳，让更多霓虹油漆在画布上层层绽开。"
            isWhiteCanvasMode() -> "Keep punching and build richer layers of color across the white canvas."
            isRainbowMode() -> "Keep punching and layer more rainbow arcs across the park backdrop."
            else -> "Keep punching and layer more neon paint across the canvas."
        }

    private companion object {
        const val HINT_MIN_GAP_MS = 160L
        const val MOTIVATION_MIN_GAP_MS = 5_000L
        const val HAPTIC_MIN_GAP_MS = 650L
        const val HAPTIC_FEEDBACK_ENABLED = false
        const val TONE_MIN_GAP_MS = 80L
        const val TONE_FEEDBACK_ENABLED = true
        const val MAX_PARTICLES_BEFORE_HIT = 110
        const val MAX_RINGS_BEFORE_HIT = 18
        const val MAX_FLOATING_TEXT_BEFORE_HIT = 8
    }
}



