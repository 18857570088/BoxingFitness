package com.zclei.boxingfitness

import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.zclei.boxingfitness.cloud.CloudTrainingUploader
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

class FreeBoxingActivity : AppCompatActivity() {
    private enum class TrainingDurationPreset(
        val seconds: Int,
        val zhLabel: String,
        val enLabel: String,
    ) {
        Seconds30(30, "30 \u79D2", "30s"),
        Seconds60(60, "60 \u79D2", "60s"),
        Seconds120(120, "120 \u79D2", "120s"),
        ;

        val durationMs: Long
            get() = seconds * 1_000L

        fun label(languageCode: String): String =
            if (languageCode.startsWith("zh")) zhLabel else enLabel
    }

    private lateinit var rootContainer: FrameLayout
    private lateinit var gameView: FreeBoxingGameView
    private lateinit var selectionControlsView: View
    private lateinit var stateTitleView: TextView
    private lateinit var stateBodyView: TextView
    private lateinit var timerValueView: TextView
    private lateinit var progressFillView: View
    private lateinit var hitsValueView: TextView
    private lateinit var comboValueView: TextView
    private lateinit var missValueView: TextView
    private lateinit var countdownValueView: TextView
    private lateinit var paceValueView: TextView
    private lateinit var reliefFillView: View
    private lateinit var coachHintView: TextView
    private lateinit var startButtonView: TextView
    private lateinit var endButtonView: TextView
    private lateinit var actionControlsView: View
    private lateinit var settingsButtonView: View
    private lateinit var settingsPageView: View
    private lateinit var combatMetricsView: View
    private lateinit var launchCountdownView: TextView
    private lateinit var evaluationPopupView: View
    private lateinit var evaluationTitleView: TextView
    private lateinit var evaluationBodyView: TextView
    private lateinit var stressFillView: View
    private lateinit var calmFillView: View

    private var detectorJob: Job? = null
    private var bleHitListener: BoxingBleRuntime.HitListener? = null
    private var detectorRecoveryJob: Job? = null
    private var countdownJob: Job? = null
    private var launchCountdownJob: Job? = null
    private var evaluationPopupJob: Job? = null
    private var speechEngine: TextToSpeech? = null
    private var speechReady = false
    private var lastMotivationSpeechElapsedMs = 0L
    private var safeModeActive = false
    private var trainingActive = false
    private var launchCountdownActive = false
    private var pendingStartAfterPermission = false
    private var countdownTargetElapsedMs = 0L
    private var trainingRunId = 0L
    private var detectorRecoveryAttempts = 0
    private var pendingBleHitCount = 0
    private var bleHitDrainScheduled = false
    private var debugBleWindowStartedAtMs = 0L
    private var debugBleHitEvents = 0
    private var debugBleHitDelta = 0
    private var debugBleDrainCount = 0
    private var debugBleMaxDrain = 0
    private var debugBleMaxPending = 0
    private var selectedPersonaIndex = 0
    private var selectedSpeed = FreeBoxingGameView.SpeedPreset.Medium
    private var selectedDensity = FreeBoxingGameView.DensityPreset.Duo
    private var selectedDuration = TrainingDurationPreset.Seconds30
    private var remainingTrainingMs = TrainingDurationPreset.Seconds30.durationMs
    private var lastRoundHits = 0
    private var lastRoundMisses = 0
    private var lastRoundDurationSeconds = 0
    private var dailyStressLevel = DAILY_STRESS_START
    private var dailyCalmLevel = DAILY_CALM_START
    private var dailyEmotionDayKey = ""
    private var lastAppliedRoundHits = 0
    private var currentDetectorDebugLogFile: File? = null
    private var currentDetectorAudioFile: File? = null
    private val speedButtons = LinkedHashMap<FreeBoxingGameView.SpeedPreset, TextView>()
    private val densityButtons = LinkedHashMap<FreeBoxingGameView.DensityPreset, TextView>()
    private val durationButtons = LinkedHashMap<TrainingDurationPreset, TextView>()
    private val personaCards = LinkedHashMap<Int, View>()
    private val dailyEmotionPreferences: SharedPreferences by lazy(LazyThreadSafetyMode.NONE) {
        getSharedPreferences(DAILY_EMOTION_PREFS, MODE_PRIVATE)
    }

    private val sensitivityLevel by lazy {
        intent.getIntExtra(EXTRA_SENSITIVITY_LEVEL, 50)
    }
    private val languageCode by lazy {
        intent.getStringExtra(EXTRA_LANGUAGE).orEmpty().ifBlank { "zh" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initSpeechEngine()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        hideSystemBars()
        runCatching {
            setContentView(buildContentView())
            refreshDailyEmotionState(resetRoundHits = true)
            renderIdleState()
        }.onFailure { throwable ->
            reportFatalError("onCreate", throwable)
            renderFatalFallback(
                freeText("\u81EA\u7531\u62F3\u51FB\u542F\u52A8\u5931\u8D25\uFF0C\u5DF2\u5207\u6362\u5230\u5B89\u5168\u6A21\u5F0F\u3002", "Free Boxing failed to start and switched to safe mode."),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        refreshDailyEmotionState()
        if (!safeModeActive && trainingActive) {
            resumeTrainingIfNeeded()
        }
    }

    override fun onPause() {
        cancelLaunchCountdown(showMessage = false)
        pauseTrainingCountdown()
        stopDetectorSession()
        clearDetectorRecoveryState()
        hideTrainingSummaryPopup()
        super.onPause()
    }

    override fun onDestroy() {
        cancelLaunchCountdown(showMessage = false)
        pauseTrainingCountdown()
        stopDetectorSession()
        clearDetectorRecoveryState()
        hideTrainingSummaryPopup()
        speechEngine?.stop()
        speechEngine?.shutdown()
        speechEngine = null
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::settingsPageView.isInitialized && settingsPageView.visibility == View.VISIBLE) {
            hideSettingsPage()
            return
        }
        super.onBackPressed()
    }

    private fun buildContentView(): View {
        safeModeActive = false
        rootContainer =
            FrameLayout(this).apply {
                setBackgroundColor(Color.parseColor("#B9E5FF"))
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }

        gameView =
            FreeBoxingGameView(this).apply {
                setLanguageCode(languageCode)
                setFatalErrorReporter { throwable ->
                    runOnUiThread {
                        reportFatalError("gameView", throwable)
                        renderFatalFallback(
                            freeText("\u81EA\u7531\u62F3\u51FB\u6E32\u67D3\u5931\u8D25\uFF0C\u5DF2\u5207\u6362\u5230\u5B89\u5168\u6A21\u5F0F\u3002", "Free Boxing rendering failed and switched to safe mode."),
                        )
                    }
                }
            }

        rootContainer.addView(
            gameView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        rootContainer.addView(buildOverlay())
        gameView.bind(
            object : FreeBoxingGameView.Listener {
                override fun onOverlaySnapshot(snapshot: FreeBoxingGameView.OverlaySnapshot) {
                    runOnUiThread {
                        lastRoundHits = snapshot.hits
                        lastRoundMisses = snapshot.misses
                        if (::hitsValueView.isInitialized) {
                            hitsValueView.text = snapshot.hits.toString()
                        }
                        if (::comboValueView.isInitialized) {
                            comboValueView.text = "x${snapshot.combo}"
                        }
                        if (::missValueView.isInitialized) {
                            missValueView.text = snapshot.misses.toString()
                        }
                        if (::paceValueView.isInitialized) {
                            paceValueView.text = snapshot.paceLabel
                        }
                        if (::reliefFillView.isInitialized) {
                            reliefFillView.scaleX = (snapshot.relief / 100f).coerceIn(0f, 1f)
                        }
                        syncDailyEmotionState(snapshot.hits)
                    }
                }

                override fun onHintChanged(hint: String) {
                    runOnUiThread {
                        if (::coachHintView.isInitialized) {
                            coachHintView.text = hint
                        }
                    }
                }

                override fun onMotivationCue(message: String) {
                    runOnUiThread {
                        speakMotivationCue(message)
                    }
                }
            },
        )
        gameView.setSpeedPreset(selectedSpeed)
        gameView.setDensityPreset(selectedDensity)
        gameView.setPersona(selectedPersonaIndex)
        updateSpeedButtons(selectedSpeed)
        updateDensityButtons(selectedDensity)
        updateDurationButtons(selectedDuration)
        updateTrainingUi()
        return rootContainer
    }

    private fun buildOverlay(): View =
        FrameLayout(this).apply {
            layoutParams =
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            addView(buildTopBar())
            addView(buildEmotionMeters())
            addView(actionControlsCard())
            addView(buildCombatMetrics())
            addView(buildSettingsPage())
            addView(buildEvaluationPopup())
            addView(buildLaunchCountdownOverlay())
        }

    private fun buildTopBar(): View =
        FrameLayout(this).apply {
            layoutParams =
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(72),
                    Gravity.TOP,
                )
            addView(
                backButton(),
                FrameLayout.LayoutParams(dp(56), dp(56), Gravity.START or Gravity.TOP).apply {
                    leftMargin = dp(16)
                    topMargin = dp(14)
                },
            )
            addView(
                settingsButton(),
                FrameLayout.LayoutParams(dp(56), dp(56), Gravity.END or Gravity.TOP).apply {
                    topMargin = dp(14)
                    rightMargin = dp(16)
                },
            )
        }

    private fun buildEmotionMeters(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams =
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.END or Gravity.TOP,
                ).apply {
                    topMargin = dp(84)
                    rightMargin = dp(6)
                    bottomMargin = dp(18)
                }
            addView(meterScaleColumn(textColor = Color.parseColor("#9B2424"), tickColor = Color.parseColor("#B85454"), alignEnd = true))
            addView(horizontalSpace(dp(4)))
            addView(emotionThermometerBody())
            addView(horizontalSpace(dp(4)))
            addView(meterScaleColumn(textColor = Color.parseColor("#145C35"), tickColor = Color.parseColor("#3D9963"), alignEnd = false))
        }

    private fun meterScaleColumn(
        textColor: Int,
        tickColor: Int,
        alignEnd: Boolean,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT)
            for (value in 100 downTo 0 step 10) {
                addView(meterScaleItem(value, textColor, tickColor, alignEnd))
            }
        }

    private fun meterScaleItem(
        value: Int,
        textColor: Int,
        tickColor: Int,
        alignEnd: Boolean,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (alignEnd) Gravity.CENTER_VERTICAL or Gravity.END else Gravity.CENTER_VERTICAL or Gravity.START
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            val labelView =
                TextView(this@FreeBoxingActivity).apply {
                    text = value.toString()
                    gravity = if (alignEnd) Gravity.END else Gravity.START
                    setTextColor(textColor)
                    textSize = 10f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    alpha = if (value % 20 == 0) 0.98f else 0.72f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
            val tickView =
                View(this@FreeBoxingActivity).apply {
                    setBackgroundColor(tickColor)
                    alpha = if (value % 20 == 0) 0.8f else 0.5f
                    layoutParams = LinearLayout.LayoutParams(dp(7), dp(1))
                }
            if (alignEnd) {
                addView(labelView)
                addView(tickView)
            } else {
                addView(tickView)
                addView(labelView)
            }
        }

    private fun emotionThermometerBody(): View =
        FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(60), ViewGroup.LayoutParams.MATCH_PARENT)
            addView(
                View(this@FreeBoxingActivity).apply {
                    background =
                        topRoundedGradient(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(
                                Color.parseColor("#58F8FFFF"),
                                Color.parseColor("#2EDCEEFF"),
                                Color.parseColor("#1A112231"),
                            ),
                            24,
                        ).apply {
                            setStroke(dp(1), Color.parseColor("#74FFFFFF"))
                        }
                },
                FrameLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = dp(20)
                },
            )
            addView(
                LinearLayout(this@FreeBoxingActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(dp(6), dp(10), dp(6), dp(12))
                    layoutParams =
                        FrameLayout.LayoutParams(
                            dp(48),
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                        ).apply {
                            bottomMargin = dp(20)
                        }
                    addView(
                        thermometerChannel(
                            accentColor = Color.parseColor("#8F1E1E"),
                            glowColor = Color.parseColor("#C84B4B"),
                            initialRatio = DAILY_STRESS_START / DAILY_METER_MAX,
                            fillRef = { stressFillView = it },
                        ),
                    )
                    addView(horizontalSpace(dp(4)))
                    addView(
                        thermometerChannel(
                            accentColor = Color.parseColor("#145A32"),
                            glowColor = Color.parseColor("#2E8B57"),
                            initialRatio = DAILY_CALM_START / DAILY_METER_MAX,
                            fillRef = { calmFillView = it },
                        ),
                    )
                },
            )
            addView(
                View(this@FreeBoxingActivity).apply {
                    background =
                        topRoundedGradient(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(Color.parseColor("#66FFFFFF"), Color.parseColor("#10FFFFFF")),
                            20,
                        )
                    alpha = 0.7f
                },
                FrameLayout.LayoutParams(dp(10), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.TOP).apply {
                    leftMargin = dp(9)
                    topMargin = dp(14)
                    bottomMargin = dp(38)
                },
            )
            addView(
                View(this@FreeBoxingActivity).apply {
                    background =
                        GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(
                                Color.parseColor("#72F7FFFF"),
                                Color.parseColor("#2FC6E0F2"),
                                Color.parseColor("#240C2135"),
                            ),
                        ).apply {
                            cornerRadius = dp(16).toFloat()
                            setStroke(dp(1), Color.parseColor("#7EFFFFFF"))
                        }
                    alpha = 0.92f
                },
                FrameLayout.LayoutParams(dp(58), dp(18), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = dp(4)
                },
            )
            addView(
                View(this@FreeBoxingActivity).apply {
                    background =
                        roundedGradient(
                            intArrayOf(Color.parseColor("#84FFFFFF"), Color.parseColor("#12FFFFFF")),
                            999,
                        )
                    alpha = 0.78f
                },
                FrameLayout.LayoutParams(dp(30), dp(4), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = dp(15)
                },
            )
        }

    private fun thermometerChannel(
        accentColor: Int,
        glowColor: Int,
        initialRatio: Float,
        fillRef: (View) -> Unit,
    ): View =
        FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            background =
                topRoundedGradient(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(
                        Color.parseColor("#2CFFFFFF"),
                        Color.parseColor("#140E1A22"),
                    ),
                    16,
                ).apply {
                    setStroke(dp(1), Color.parseColor("#42FFFFFF"))
                }
            clipToOutline = true
            addView(
                View(this@FreeBoxingActivity).apply {
                    background =
                        topRoundedGradient(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(Color.parseColor("#10FFFFFF"), Color.parseColor("#06000000")),
                            16,
                        )
                    alpha = 0.9f
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                View(this@FreeBoxingActivity).apply {
                    background =
                        topRoundedGradient(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(Color.parseColor("#62FFFFFF"), Color.parseColor("#00FFFFFF")),
                            16,
                        )
                    alpha = 0.68f
                },
                FrameLayout.LayoutParams(
                    dp(5),
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.START or Gravity.TOP,
                ).apply {
                    topMargin = dp(6)
                }
            )
            val fillView =
                View(this@FreeBoxingActivity).apply {
                    background =
                        topRoundedGradient(
                            GradientDrawable.Orientation.BOTTOM_TOP,
                            intArrayOf(accentColor, glowColor),
                            16,
                        )
                    scaleY = initialRatio.coerceIn(0f, 1f)
                }
            addView(
                fillView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.BOTTOM,
                ),
            )
            addView(
                View(this@FreeBoxingActivity).apply {
                    background =
                        topRoundedGradient(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(Color.parseColor("#54FFFFFF"), Color.parseColor("#00FFFFFF")),
                            14,
                        )
                    alpha = 0.62f
                },
                FrameLayout.LayoutParams(
                    dp(4),
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.START or Gravity.TOP,
                ).apply {
                    leftMargin = dp(1)
                    topMargin = dp(8)
                    bottomMargin = dp(6)
                },
            )
            fillRef(fillView)
        }

    private fun buildLeftHud(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams =
                FrameLayout.LayoutParams(
                    min(dp(300), (resources.displayMetrics.widthPixels * 0.28f).toInt()),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.START or Gravity.TOP,
                ).apply {
                    leftMargin = dp(16)
                    topMargin = dp(112)
                }
            addView(metricCard())
            addView(verticalSpace(dp(10)))
            addView(reliefCard())
        }

    private fun buildCombatMetrics(): View =
        LinearLayout(this).apply {
            combatMetricsView = this
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            background = glassPanel(16)
            setPadding(dp(7), dp(6), dp(7), dp(6))
            val metricsWidth = min(dp(548), (resources.displayMetrics.widthPixels * 0.64f).toInt())
            layoutParams =
                FrameLayout.LayoutParams(
                    metricsWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                ).apply {
                    topMargin = dp(10)
                }
            addView(combatMetricChip(freeText("\u51FB\u4E2D", "Hits")) { hitsValueView = it })
            addView(horizontalSpace(dp(5)))
            addView(combatMetricChip(freeText("\u8FDE\u58F0", "Combo"), isCombo = true) { comboValueView = it })
            addView(horizontalSpace(dp(5)))
            addView(combatMetricChip(freeText("\u6F0F\u51FB", "Misses")) { missValueView = it })
            addView(horizontalSpace(dp(5)))
            addView(combatMetricChip(freeText("\u5012\u8BA1\u65F6", "Time")) { countdownValueView = it })
        }

    private fun combatMetricChip(
        label: String,
        isCombo: Boolean = false,
        targetRef: (TextView) -> Unit,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedFill("#18FFFFFF", 14).apply { setStroke(dp(1), Color.parseColor("#2EFFFFFF")) }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#EAF5FF"))
                    textSize = 10.5f
                },
            )
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    text = if (isCombo) "x0" else "0"
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 20f
                    setPadding(0, dp(3), 0, 0)
                    targetRef(this)
                },
            )
        }

    private fun buildCoachHint(): View =
        TextView(this).apply {
            coachHintView = this
            text = freeText("\u6C14\u6CE1\u51FA\u73B0\u540E 0.5 - 1.0 \u79D2\u51FA\u62F3\uFF0C\u7206\u788E\u53CD\u9988\u6700\u5F3A\u3002", "Punch 0.5 - 1.0 seconds after a bubble appears for the cleanest burst.")
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#F4FBFF"))
            textSize = 15f
            background = glassPanel(20)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            layoutParams =
                FrameLayout.LayoutParams(
                    min(dp(620), resources.displayMetrics.widthPixels - dp(48)),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                ).apply {
                    bottomMargin = dp(18)
                }
        }

    private fun buildEvaluationPopup(): View =
        LinearLayout(this).apply {
            evaluationPopupView = this
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            isClickable = true
            elevation = dp(10).toFloat()
            background = glassPanel(24).apply { setStroke(dp(1), Color.parseColor("#55FFFFFF")) }
            setPadding(dp(28), dp(22), dp(28), dp(22))
            layoutParams =
                FrameLayout.LayoutParams(
                    min(dp(520), resources.displayMetrics.widthPixels - dp(96)).coerceAtLeast(dp(300)),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                )
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    evaluationTitleView = this
                    gravity = Gravity.CENTER
                    text = freeText("\u672C\u8F6E\u8BAD\u7EC3\u8BC4\u4EF7", "Round Summary")
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 24f
                },
            )
            addView(verticalSpace(dp(12)))
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    evaluationBodyView = this
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#ECF8FF"))
                    textSize = 18f
                    setLineSpacing(dp(2).toFloat(), 1.08f)
                },
            )
        }

    private fun backButton(): View =
        TextView(this).apply {
            text = "<"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 24f
            setTypeface(Typeface.DEFAULT_BOLD)
            background = glassPanel(18)
            layoutParams =
                LinearLayout.LayoutParams(dp(56), dp(56))
            setOnClickListener { finish() }
        }

    private fun settingsButton(): View =
        TextView(this).apply {
            settingsButtonView = this
            text = "\u2699"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 25f
            setTypeface(Typeface.DEFAULT_BOLD)
            background = glassPanel(18)
            setOnClickListener {
                if (!trainingActive) {
                    showSettingsPage()
                }
            }
        }

    private fun statusCard(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = glassPanel(18)
            layoutParams =
                LinearLayout.LayoutParams(dp(340), ViewGroup.LayoutParams.WRAP_CONTENT)
            stateTitleView =
                TextView(this@FreeBoxingActivity).apply {
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 18f
                }
            stateBodyView =
                TextView(this@FreeBoxingActivity).apply {
                    setTextColor(Color.parseColor("#EBF6FF"))
                    alpha = 0.9f
                    textSize = 13f
                    setLineSpacing(0f, 1.12f)
                    setPadding(0, dp(6), 0, 0)
                }
            timerValueView =
                TextView(this@FreeBoxingActivity).apply {
                    setTextColor(Color.parseColor("#F7FCFF"))
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 26f
                    setPadding(0, dp(10), 0, 0)
                }
            val track =
                FrameLayout(this@FreeBoxingActivity).apply {
                    background = roundedFill("#26FFFFFF", 999)
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(6),
                        ).apply {
                            topMargin = dp(8)
                        }
                    progressFillView =
                        View(this@FreeBoxingActivity).apply {
                            background = roundedGradient(intArrayOf(Color.parseColor("#62D3FF"), Color.parseColor("#9BE7FF")), 999)
                            scaleX = 0f
                            pivotX = 0f
                        }
                    addView(
                        progressFillView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            addView(stateTitleView)
            addView(stateBodyView)
            addView(timerValueView)
            addView(track)
        }

    private fun buildSettingsPage(): View =
        FrameLayout(this).apply {
            settingsPageView = this
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#CC071524"))
            layoutParams =
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            addView(settingsPanel())
        }

    private fun settingsPanel(): View =
        ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams =
                FrameLayout.LayoutParams(
                    min(dp(920), resources.displayMetrics.widthPixels - dp(64)),
                    min(dp(620), resources.displayMetrics.heightPixels - dp(64)),
                    Gravity.CENTER,
                )
            addView(
                LinearLayout(this@FreeBoxingActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    background = glassPanel(24)
                    setPadding(dp(22), dp(18), dp(22), dp(22))
                    addView(settingsHeader())
                    addView(verticalSpace(dp(16)))
                    addView(settingsChoiceSection(freeText("\u8BAD\u7EC3\u65F6\u95F4", "Duration"), TrainingDurationPreset.values().toList()))
                    addView(verticalSpace(dp(14)))
                    addView(settingsChoiceSection(freeText("\u6C14\u6CE1\u901F\u5EA6", "Bubble Speed"), FreeBoxingGameView.SpeedPreset.values().toList()))
                    addView(verticalSpace(dp(14)))
                    addView(settingsChoiceSection(freeText("\u6C14\u6CE1\u6570\u91CF", "Bubble Count"), FreeBoxingGameView.DensityPreset.values().toList()))
                    addView(verticalSpace(dp(18)))
                    addView(settingsDeviceSection())
                },
            )
        }

    private fun settingsHeader(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    text = freeText("\u81EA\u7531\u62F3\u51FB\u8BBE\u7F6E", "Free Boxing Settings")
                    gravity = Gravity.START
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 20f
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                },
            )
            addView(verticalSpace(dp(12)))
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    text = freeText("\u8BBE\u7F6E\u786E\u8BA4", "Confirm")
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#071524"))
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 16f
                    background = confirmButtonBackground()
                    elevation = dp(7).toFloat()
                    translationZ = dp(2).toFloat()
                    setIncludeFontPadding(false)
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setPadding(dp(3), dp(9), dp(3), dp(9))
                    setOnClickListener { hideSettingsPage() }
                },
            )
        }

    private fun settingsDeviceSection(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    text = freeText("\u8BBE\u5907\u529F\u80FD", "Device Features")
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 15f
                },
            )
            addView(verticalSpace(dp(12)))
            addView(settingsPersonaSection())
        }

    private fun settingsPersonaSection(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    text = freeText("\u89D2\u8272\u9009\u62E9", "Character")
                    setTextColor(Color.parseColor("#E3F1FF"))
                    textSize = 13f
                },
            )
            val rows =
                LinearLayout(this@FreeBoxingActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(8), 0, 0)
                }
            FreeBoxingPersonaCatalog.options.chunked(2).forEachIndexed { rowIndex, rowOptions ->
                rows.addView(
                    LinearLayout(this@FreeBoxingActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        rowOptions.forEachIndexed { columnIndex, option ->
                            val index = rowIndex * 4 + columnIndex
                            addView(personaCard(index, option))
                            if (columnIndex < rowOptions.lastIndex) {
                                addView(horizontalSpace(dp(8)))
                            }
                        }
                    },
                )
                if (rowIndex < FreeBoxingPersonaCatalog.options.chunked(2).lastIndex) {
                    rows.addView(verticalSpace(dp(8)))
                }
            }
            addView(rows)
        }

    private fun personaCard(
        index: Int,
        option: FreeBoxingPersonaOption,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(9))
            minimumWidth = dp(104)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            personaCards[index] = this
            addView(
                ImageView(this@FreeBoxingActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    background = personaBackground(active = false)
                    loadPersonaThumbnail(index, dp(72))?.let { setImageBitmap(it) }
                    layoutParams = LinearLayout.LayoutParams(dp(74), dp(74))
                },
            )
            addView(verticalSpace(dp(6)))
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    text = if (languageCode.startsWith("zh")) option.zhName else option.enName
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 12f
                    maxLines = 1
                },
            )
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    text = if (languageCode.startsWith("zh")) option.zhRole else option.enRole
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#BFD9EA"))
                    textSize = 10f
                    maxLines = 1
                },
            )
            setOnClickListener {
                selectedPersonaIndex = index
                gameView.setPersona(index)
                updatePersonaCards(index)
            }
        }

    private fun loadPersonaThumbnail(
        index: Int,
        sizePx: Int,
    ): Bitmap? =
        FreeBoxingPersonaCatalog.loadSprite(this)?.let { sprite ->
            FreeBoxingPersonaCatalog.thumbnail(sprite, index, sizePx)
        }

    private fun updatePersonaCards(selectedIndex: Int) {
        personaCards.forEach { (index, view) ->
            view.background = personaBackground(active = index == selectedIndex)
            if (view is LinearLayout) {
                (view.getChildAt(0) as? ImageView)?.background = personaBackground(active = index == selectedIndex)
            }
        }
    }

    private fun personaBackground(active: Boolean): GradientDrawable =
        if (active) {
            roundedGradient(intArrayOf(Color.parseColor("#D8F8FF"), Color.parseColor("#74D8FF")), 18).apply {
                setStroke(dp(2), Color.WHITE)
            }
        } else {
            roundedFill("#18FFFFFF", 18).apply {
                setStroke(dp(1), Color.parseColor("#3CFFFFFF"))
            }
        }

    private fun settingsChoiceSection(
        title: String,
        values: List<Any>,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    text = title
                    setTextColor(Color.parseColor("#E3F1FF"))
                    textSize = 13f
                },
            )
            addView(
                LinearLayout(this@FreeBoxingActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(8), 0, 0)
                    values.forEachIndexed { index, value ->
                        addView(segmentedButton(value))
                        if (index < values.lastIndex) {
                            addView(horizontalSpace(dp(8)))
                        }
                    }
                },
            )
        }

    private fun showSettingsPage() {
        if (::settingsPageView.isInitialized && !trainingActive) {
            hideTrainingSummaryPopup()
            updatePersonaCards(selectedPersonaIndex)
            updateSpeedButtons(selectedSpeed)
            updateDensityButtons(selectedDensity)
            updateDurationButtons(selectedDuration)
            settingsPageView.visibility = View.VISIBLE
            settingsPageView.bringToFront()
            updateSettingsPageVisibility(isVisible = true)
        }
    }

    private fun hideSettingsPage() {
        if (::settingsPageView.isInitialized) {
            settingsPageView.visibility = View.GONE
            updateSettingsPageVisibility(isVisible = false)
        }
    }

    private fun actionControlsCard(): View =
        LinearLayout(this).apply {
            actionControlsView = this
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = glassPanel(18)
            setPadding(dp(8), dp(7), dp(8), dp(7))
            layoutParams =
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START,
                ).apply {
                    leftMargin = dp(32)
                    bottomMargin = dp(28)
                }
            startButtonView =
                actionControlButton(freeText("\u5F00\u59CB", "Start"), active = true) {
                    requestTrainingStart()
                }
            endButtonView =
                actionControlButton(freeText("\u7ED3\u675F", "End"), active = false) {
                    if (launchCountdownActive) {
                        cancelLaunchCountdown(showMessage = true)
                    } else {
                        finishTrainingSession(manual = true)
                    }
                }
            addView(startButtonView)
            addView(horizontalSpace(dp(8)))
            addView(endButtonView)
        }

    private fun actionControlButton(
        label: String,
        active: Boolean,
        onClick: () -> Unit,
    ): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT_BOLD)
            textSize = 16f
            minWidth = dp(66)
            minHeight = dp(42)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = segmentedBackground(active)
            setTextColor(if (active) Color.parseColor("#071524") else Color.parseColor("#EBF7FF"))
            setOnClickListener { onClick() }
        }

    private fun buildLaunchCountdownOverlay(): View =
        TextView(this).apply {
            launchCountdownView = this
            visibility = View.GONE
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
            textSize = 104f
            background = roundedFill("#18000000", 28)
            setShadowLayer(dp(10).toFloat(), 0f, dp(4).toFloat(), Color.parseColor("#80000000"))
            layoutParams =
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
        }

    private fun segmentedCard(
        title: String,
        values: List<Any>,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassPanel(18)
            setPadding(dp(12), dp(10), dp(12), dp(12))
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    text = title
                    setTextColor(Color.parseColor("#E3F1FF"))
                    textSize = 12f
                },
            )
            addView(
                LinearLayout(this@FreeBoxingActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(8), 0, 0)
                    values.forEachIndexed { index, value ->
                        addView(segmentedButton(value))
                        if (index < values.lastIndex) {
                            addView(horizontalSpace(dp(8)))
                        }
                    }
                },
            )
        }

    private fun segmentedButton(value: Any): TextView {
        val textView =
            TextView(this).apply {
                gravity = Gravity.CENTER
                setTypeface(Typeface.DEFAULT_BOLD)
                textSize = 14f
                minWidth = dp(54)
                setPadding(dp(14), dp(11), dp(14), dp(11))
            }
        when (value) {
            is FreeBoxingGameView.SpeedPreset -> {
                speedButtons[value] = textView
                textView.text = value.label(languageCode)
                textView.setOnClickListener {
                    selectedSpeed = value
                    gameView.setSpeedPreset(value)
                    updateSpeedButtons(value)
                }
                if (value == FreeBoxingGameView.SpeedPreset.Medium) {
                    updateSpeedButtons(value)
                }
            }

            is FreeBoxingGameView.DensityPreset -> {
                densityButtons[value] = textView
                textView.text =
                    when (value) {
                        FreeBoxingGameView.DensityPreset.Solo -> freeText("\u5C11", "Low")
                        FreeBoxingGameView.DensityPreset.Duo -> freeText("\u4E2D", "Medium")
                        FreeBoxingGameView.DensityPreset.Storm -> freeText("\u591A", "High")
                    }
                textView.setOnClickListener {
                    selectedDensity = value
                    gameView.setDensityPreset(value)
                    updateDensityButtons(value)
                }
                if (value == FreeBoxingGameView.DensityPreset.Duo) {
                    updateDensityButtons(value)
                }
            }

            is TrainingDurationPreset -> {
                durationButtons[value] = textView
                textView.text = value.label(languageCode)
                textView.setOnClickListener {
                    if (trainingActive) {
                        return@setOnClickListener
                    }
                    selectedDuration = value
                    remainingTrainingMs = value.durationMs
                    updateDurationButtons(value)
                    updateTimerDisplay()
                }
            }
        }
        return textView
    }

    private fun metricCard(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassPanel(18)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(metricRow())
        }

    private fun metricRow(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(metricPair(freeText("\u51FB\u788E", "Shattered"), freeText("\u8FDE\u51FB", "Combo"), { hitsValueView = it }, { comboValueView = it }))
            addView(verticalSpace(dp(10)))
            addView(metricPair(freeText("\u6F0F\u51FB", "Misses"), freeText("\u901F\u5EA6", "Pace"), { missValueView = it }, { paceValueView = it }))
        }

    private fun reliefCard(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassPanel(18)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    text = freeText("\u91CA\u653E\u611F", "Relief")
                    setTextColor(Color.parseColor("#EAF3FB"))
                    textSize = 12f
                },
            )
            addView(
                FrameLayout(this@FreeBoxingActivity).apply {
                    background = roundedFill("#24FFFFFF", 999)
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(12),
                        ).apply {
                            topMargin = dp(8)
                        }
                    reliefFillView =
                        View(this@FreeBoxingActivity).apply {
                            background =
                                roundedGradient(
                                    intArrayOf(
                                        Color.parseColor("#FFCB66"),
                                        Color.parseColor("#FF7B7B"),
                                        Color.parseColor("#D05BFF"),
                                    ),
                                    999,
                                )
                            scaleX = 0.08f
                            pivotX = 0f
                        }
                    addView(reliefFillView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                },
            )
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    text = freeText("\u6BCF\u51FB\u788E\u4E00\u4E2A\u6C14\u6CE1\uFF0C\u5C31\u628A\u4E00\u70B9\u538B\u529B\u91CA\u653E\u51FA\u53BB\u3002", "Every shattered bubble lets a little pressure go.")
                    setTextColor(Color.parseColor("#E8F4FF"))
                    alpha = 0.88f
                    textSize = 12f
                    setLineSpacing(0f, 1.12f)
                    setPadding(0, dp(8), 0, 0)
                },
            )
        }

    private fun metricPair(
        leftLabel: String,
        rightLabel: String,
        leftRef: (TextView) -> Unit,
        rightRef: (TextView) -> Unit,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(metricItem(leftLabel, leftRef))
            addView(horizontalSpace(dp(10)))
            addView(metricItem(rightLabel, rightRef))
        }

    private fun metricItem(
        label: String,
        targetRef: (TextView) -> Unit,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill("#14FFFFFF", 14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    text = label
                    setTextColor(Color.parseColor("#E1F0FC"))
                    alpha = 0.84f
                    textSize = 12f
                },
            )
            addView(
                TextView(this@FreeBoxingActivity).apply {
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 30f
                    text = if (label == freeText("\u901F\u5EA6", "Pace")) FreeBoxingGameView.SpeedPreset.Medium.label(languageCode) else "0"
                    setPadding(0, dp(6), 0, 0)
                    targetRef(this)
                },
            )
        }

    private fun initSpeechEngine() {
        speechEngine =
            TextToSpeech(applicationContext) { status ->
                speechReady = status == TextToSpeech.SUCCESS
                if (speechReady) {
                    speechEngine?.language =
                        if (languageCode.startsWith("zh")) {
                            Locale.CHINA
                        } else {
                            Locale.US
                        }
                    speechEngine?.setSpeechRate(1.05f)
                    speechEngine?.setPitch(1.05f)
                }
            }
    }

    private fun showLaunchCountdownStep(step: String) {
        val displayText = if (step == "GO") freeText("开始！", "GO!") else step
        if (::launchCountdownView.isInitialized) {
            launchCountdownView.animate().cancel()
            launchCountdownView.text = displayText
            launchCountdownView.visibility = View.VISIBLE
            launchCountdownView.alpha = 0.96f
            launchCountdownView.scaleX = 0.62f
            launchCountdownView.scaleY = 0.62f
            launchCountdownView.animate()
                .scaleX(if (step == "GO") 1.42f else 1.22f)
                .scaleY(if (step == "GO") 1.42f else 1.22f)
                .alpha(1f)
                .setDuration(210L)
                .start()
        }
        if (::countdownValueView.isInitialized) {
            countdownValueView.text = displayText
        }
    }

    private fun hideLaunchCountdown() {
        if (::launchCountdownView.isInitialized) {
            launchCountdownView.animate().cancel()
            launchCountdownView.visibility = View.GONE
            launchCountdownView.scaleX = 1f
            launchCountdownView.scaleY = 1f
            launchCountdownView.alpha = 1f
        }
    }

    private fun speakLaunchCountdownStep(step: String) {
        val speechText =
            if (languageCode.startsWith("zh")) {
                when (step) {
                    "3" -> "\u4E09"
                    "2" -> "\u4E8C"
                    "1" -> "\u4E00"
                    else -> "\u5F00\u59CB"
                }
            } else {
                if (step == "GO") "Go" else step
            }
        if (speechReady) {
            speechEngine?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "free_boxing_countdown_$step")
        }
    }

    private fun speakMotivationCue(
        message: String,
        force: Boolean = false,
    ) {
        if (!speechReady || message.isBlank()) {
            return
        }
        if (!force && !trainingActive) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastMotivationSpeechElapsedMs < 1_200L) {
            return
        }
        lastMotivationSpeechElapsedMs = now
        speechEngine?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "free_boxing_motivation_$now")
    }

    private fun requestTrainingStart() {
        if (trainingActive || launchCountdownActive) {
            return
        }
        startLaunchCountdown()
    }

    private fun startLaunchCountdown() {
        if (safeModeActive || trainingActive || launchCountdownActive) {
            return
        }
        BoxingBleRuntime.enableGyro()
        hideTrainingSummaryPopup()
        clearDetectorRecoveryState()
        pendingStartAfterPermission = false
        launchCountdownActive = true
        trainingRunId += 1L
        remainingTrainingMs = selectedDuration.durationMs
        countdownTargetElapsedMs = 0L
        gameView.beginTraining()
        hideSettingsPage()
        updateTrainingUi()
        updateTimerDisplay()
        dispatchDetectorState(
            type = "loading",
            message = freeText("\u51C6\u5907\u51FA\u62F3\uFF1A3\u30012\u30011\u3001\u5F00\u59CB\uFF01", "Get ready: 3, 2, 1, GO!"),
        )
        val runId = trainingRunId
        launchCountdownJob?.cancel()
        launchCountdownJob =
            lifecycleScope.launch {
                val steps = listOf("3", "2", "1", "GO")
                for (step in steps) {
                    if (!launchCountdownActive || runId != trainingRunId) {
                        return@launch
                    }
                    showLaunchCountdownStep(step)
                    speakLaunchCountdownStep(step)
                    gameView.playCountdownPunch()
                    delay(if (step == "GO") 680L else 880L)
                }
                if (launchCountdownActive && runId == trainingRunId) {
                    launchCountdownActive = false
                    hideLaunchCountdown()
                    startTrainingSession()
                }
            }
    }

    private fun startTrainingSession() {
        if (safeModeActive || trainingActive) {
            return
        }
        BoxingBleRuntime.enableGyro()
        hideTrainingSummaryPopup()
        clearDetectorRecoveryState()
        launchCountdownActive = false
        hideLaunchCountdown()
        pendingStartAfterPermission = false
        trainingActive = true
        trainingRunId += 1L
        remainingTrainingMs = selectedDuration.durationMs
        lastRoundDurationSeconds = 0
        countdownTargetElapsedMs = 0L
        gameView.beginTraining()
        updateTrainingUi()
        updateTimerDisplay()
        dispatchDetectorState(
            type = "loading",
            message = freeText("\u8BAD\u7EC3\u5F00\u59CB\uFF0C\u6B63\u5728\u51C6\u5907\u58F0\u97F3\u8BC6\u522B\u3002", "Round started. Preparing hit detection."),
        )
        startDetectorSession()
        startTrainingCountdown()
    }

    private fun cancelLaunchCountdown(showMessage: Boolean) {
        if (!launchCountdownActive) {
            return
        }
        BoxingBleRuntime.disableGyro()
        launchCountdownActive = false
        pendingStartAfterPermission = false
        trainingRunId += 1L
        countdownTargetElapsedMs = 0L
        launchCountdownJob?.cancel()
        launchCountdownJob = null
        hideLaunchCountdown()
        if (::gameView.isInitialized) {
            gameView.endTraining()
        }
        clearDetectorRecoveryState()
        remainingTrainingMs = selectedDuration.durationMs
        updateTrainingUi()
        updateTimerDisplay()
        dispatchDetectorState(
            type = if (showMessage) "finished" else "idle",
            message =
                if (showMessage) {
                    freeText("\u672C\u8F6E\u5DF2\u53D6\u6D88\uFF0C\u53EF\u4EE5\u91CD\u65B0\u70B9\u51FB\u5F00\u59CB\u3002", "Round cancelled. Press Start again when ready.")
                } else {
                    ""
                },
        )
    }

    private fun finishTrainingSession(manual: Boolean) {
        if (!trainingActive) {
            return
        }
        BoxingBleRuntime.disableGyro()
        lastRoundDurationSeconds = elapsedRoundDurationSeconds().coerceAtLeast(0)
        trainingActive = false
        pendingStartAfterPermission = false
        trainingRunId += 1L
        countdownTargetElapsedMs = 0L
        pauseTrainingCountdown()
        stopDetectorSession()
        clearDetectorRecoveryState()
        gameView.endTraining()
        remainingTrainingMs = selectedDuration.durationMs
        updateTrainingUi()
        updateTimerDisplay()
        dispatchDetectorState(
            type = "finished",
            progress = 0f,
            message =
                if (manual) {
                    freeText("\u672C\u8F6E\u5DF2\u7ED3\u675F\uFF0C\u9009\u9879\u5DF2\u6062\u590D\uFF0C\u53EF\u4EE5\u91CD\u65B0\u5F00\u59CB\u3002", "Round ended. Options are back for the next run.")
                } else {
                    freeText("\u8BAD\u7EC3\u65F6\u95F4\u5230\uFF0C\u9009\u9879\u5DF2\u6062\u590D\uFF0C\u53EF\u4EE5\u91CD\u65B0\u5F00\u59CB\u3002", "Time is up. Options are back for the next run.")
                },
        )
        showTrainingSummaryPopup()
    }

    private fun showTrainingSummaryPopup() {
        if (!::evaluationPopupView.isInitialized || !::evaluationTitleView.isInitialized || !::evaluationBodyView.isInitialized) {
            return
        }
        val hits = lastRoundHits.coerceAtLeast(0)
        val durationSeconds = lastRoundDurationSeconds.coerceAtLeast(0)
        addDailyDurationSeconds(durationSeconds)
        appendSessionHistory(hits, durationSeconds)
        maybeUploadRoundToCloud(hits, durationSeconds)
        val misses = lastRoundMisses.coerceAtLeast(0)
        val total = hits + misses
        val hitRate = if (total > 0) ((hits * 100f) / total).toInt() else 0
        val stressValue = dailyStressLevel.roundToInt().coerceIn(0, DAILY_METER_MAX.roundToInt())
        val calmValue = dailyCalmLevel.roundToInt().coerceIn(0, DAILY_METER_MAX.roundToInt())
        val encouragement =
            when {
                hitRate >= 85 -> freeText("\u975E\u5E38\u68D2\uFF01\u8FD9\u8F6E\u51FA\u62F3\u5E72\u8106\u3001\u547D\u4E2D\u7A33\uFF0C\u7EE7\u7EED\u4FDD\u6301\u8FD9\u4E2A\u8282\u594F\u3002", "Excellent round. Your timing was sharp and steady.")
                hitRate >= 60 -> freeText("\u5F88\u597D\uFF01\u4F60\u5DF2\u7ECF\u8FDB\u5165\u72B6\u6001\uFF0C\u4E0B\u4E00\u8F6E\u628A\u62F3\u901F\u548C\u8282\u594F\u518D\u63D0\u4E00\u70B9\u3002", "Nice work. You are in rhythm. Push the pace a little next round.")
                total > 0 -> freeText("\u5DF2\u7ECF\u5B8C\u6210\u8FD9\u8F6E\uFF01\u653E\u677E\u547C\u5438\uFF0C\u770B\u51C6\u6C14\u6CE1\u518D\u51FA\u62F3\uFF0C\u4E0B\u4E00\u8F6E\u4F1A\u66F4\u7A33\u3002", "Round complete. Breathe, wait for the bubble, then punch. The next one will be steadier.")
                else -> freeText("\u672C\u8F6E\u5DF2\u7ED3\u675F\u3002\u51C6\u5907\u597D\u540E\u518D\u6765\u4E00\u8F6E\uFF0C\u8BA9\u8EAB\u4F53\u6162\u6162\u70ED\u8D77\u6765\u3002", "Round ended. Start again when ready and ease into the rhythm.")
            }
        evaluationTitleView.text = freeText("\u672C\u8F6E\u8BAD\u7EC3\u8BC4\u4EF7", "Round Summary")
        evaluationBodyView.text =
            freeText(
                "\u603B\u51FB\u4E2D\u6570\uFF1A$hits\n\u6F0F\u51FB\u6570\uFF1A$misses\n\u51FB\u4E2D\u7387\uFF1A$hitRate%\n\u538B\u529B\u503C\u4E0B\u964D\u5230\uFF1A$stressValue\n\u5E73\u9759\u503C\u4E0A\u5347\u5230\uFF1A$calmValue\n\n$encouragement",
                "Hits: $hits\nMisses: $misses\nHit rate: $hitRate%\nStress down to: $stressValue\nCalm up to: $calmValue\n\n$encouragement",
            )
        evaluationPopupJob?.cancel()
        evaluationPopupView.animate().cancel()
        evaluationPopupView.alpha = 0f
        evaluationPopupView.scaleX = 0.94f
        evaluationPopupView.scaleY = 0.94f
        evaluationPopupView.visibility = View.VISIBLE
        evaluationPopupView.bringToFront()
        evaluationPopupView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180L)
            .start()
        evaluationPopupJob =
            lifecycleScope.launch {
                delay(10_000L)
                hideTrainingSummaryPopup()
            }
        speakMotivationCue(
            freeText(
                "\u672C\u8F6E\u5B8C\u6210\uFF0C\u538B\u529B\u503C\u4E0B\u964D\u5230 $stressValue\uFF0C\u5E73\u9759\u503C\u4E0A\u5347\u5230 $calmValue\u3002\u4F60\u771F\u68D2\uFF01",
                "Round complete. Stress is down to $stressValue, calm is up to $calmValue. Good work!",
            ),
            force = true,
        )
    }

    private fun hideTrainingSummaryPopup() {
        evaluationPopupJob?.cancel()
        evaluationPopupJob = null
        if (!::evaluationPopupView.isInitialized) {
            return
        }
        evaluationPopupView.animate().cancel()
        evaluationPopupView.visibility = View.GONE
        evaluationPopupView.alpha = 1f
        evaluationPopupView.scaleX = 1f
        evaluationPopupView.scaleY = 1f
    }

    private fun maybeUploadRoundToCloud(
        hits: Int,
        durationSeconds: Int,
    ) {
        if (hits <= 0 || durationSeconds <= 0) {
            return
        }
        val averageFrequency = hits / durationSeconds.toFloat().coerceAtLeast(1f)
        CloudTrainingUploader.uploadIfAvailable(
            context = this,
            scope = lifecycleScope,
            report =
                CloudTrainingUploader.buildReport(
                    totalHits = hits,
                    durationSeconds = durationSeconds,
                    averageFrequency = averageFrequency,
                    bestBurstCount = 0,
                    preferredModeSeconds = if (selectedDuration.durationMs >= 60_000L) 60 else 30,
                ),
        )
    }

    private fun startTrainingCountdown() {
        pauseTrainingCountdown()
        val runId = trainingRunId
        countdownTargetElapsedMs = SystemClock.elapsedRealtime() + remainingTrainingMs
        countdownJob =
            lifecycleScope.launch {
                while (trainingActive && runId == trainingRunId) {
                    remainingTrainingMs = kotlin.math.max(0L, countdownTargetElapsedMs - SystemClock.elapsedRealtime())
                    updateTimerDisplay()
                    if (remainingTrainingMs <= 0L) {
                        if (runId == trainingRunId) {
                            finishTrainingSession(manual = false)
                        }
                        break
                    }
                    delay(100L)
                }
            }
    }

    private fun pauseTrainingCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        if (trainingActive && countdownTargetElapsedMs > 0L) {
            remainingTrainingMs = kotlin.math.max(0L, countdownTargetElapsedMs - SystemClock.elapsedRealtime())
        }
    }

    private fun resumeTrainingIfNeeded() {
        if (!trainingActive) {
            return
        }
        if (remainingTrainingMs <= 0L) {
            finishTrainingSession(manual = false)
            return
        }
        startDetectorSession()
        startTrainingCountdown()
    }

    private fun startBleHitRecognitionAndStart() {
        requestTrainingStart()
    }

    private fun startDetectorSession() {
        if (!trainingActive) {
            return
        }
        stopDetectorSession()
        val listener = BoxingBleRuntime.HitListener { packet ->
            enqueueBleHits(packet.hitDelta)
        }
        bleHitListener = listener
        BoxingBleRuntime.addHitListener(listener)
        dispatchBleDetectorReady()
    }
    private fun stopDetectorSession() {
        bleHitListener?.let { BoxingBleRuntime.removeHitListener(it) }
        bleHitListener = null
        pendingBleHitCount = 0
        bleHitDrainScheduled = false
        detectorJob?.cancel()
        detectorJob = null
    }

    private fun clearDetectorRecoveryState() {
        detectorRecoveryJob?.cancel()
        detectorRecoveryJob = null
        detectorRecoveryAttempts = 0
    }

    private fun scheduleDetectorRecovery(throwable: Throwable) {
        if (!trainingActive || safeModeActive || isFinishing) {
            dispatchDetectorState(
                type = "error",
                message = throwable.message ?: freeText("\u5F53\u524D\u65E0\u6CD5\u4F7F\u7528\u9EA6\u514B\u98CE\uFF0C\u8BF7\u68C0\u67E5\u5F55\u97F3\u6743\u9650\u6216\u8BBE\u5907\u5360\u7528\u3002", "Bluetooth hit detection is unavailable right now."),
            )
            return
        }
        if (detectorRecoveryAttempts >= MAX_DETECTOR_RECOVERY_ATTEMPTS) {
            dispatchDetectorState(
                type = "error",
                message =
                    throwable.message
                        ?: freeText(
                            "\u58F0\u97F3\u8BC6\u522B\u591A\u6B21\u4E2D\u65AD\uFF0C\u8BF7\u7A0D\u540E\u91CD\u65B0\u5F00\u59CB\u6216\u68C0\u67E5\u9EA6\u514B\u98CE\u3002",
                            "Hit detection disconnected several times. Please restart the round or check the Bluetooth device.",
                        ),
            )
            return
        }
        detectorRecoveryAttempts += 1
        val recoveryAttempt = detectorRecoveryAttempts
        val recoveryRunId = trainingRunId
        detectorRecoveryJob?.cancel()
        detectorRecoveryJob =
            lifecycleScope.launch {
                dispatchDetectorState(
                    type = "loading",
                    message =
                        freeText(
                            "\u58F0\u97F3\u8BC6\u522B\u77ED\u6682\u4E2D\u65AD\uFF0C\u6B63\u5728\u91CD\u8FDE\uFF08$recoveryAttempt/$MAX_DETECTOR_RECOVERY_ATTEMPTS\uFF09\u3002",
                            "Hit detection paused briefly. Reconnecting ($recoveryAttempt/$MAX_DETECTOR_RECOVERY_ATTEMPTS).",
                        ),
                )
                delay(DETECTOR_RECOVERY_DELAY_MS)
                if (!trainingActive || safeModeActive || isFinishing || recoveryRunId != trainingRunId) {
                    return@launch
                }
                startDetectorSession()
            }
    }

    private fun enqueueBleHits(count: Int) {
        val hitCount = count.coerceIn(1, 12)
        val action = {
            debugBleHitEvents += 1
            debugBleHitDelta += hitCount
            pendingBleHitCount = (pendingBleHitCount + hitCount).coerceAtMost(48)
            debugBleMaxPending = maxOf(debugBleMaxPending, pendingBleHitCount)
            if (!bleHitDrainScheduled) {
                bleHitDrainScheduled = true
                if (::gameView.isInitialized) {
                    gameView.postOnAnimation { drainBleHits() }
                } else {
                    drainBleHits()
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            runOnUiThread(action)
        }
    }

    private fun drainBleHits() {
        bleHitDrainScheduled = false
        val count = pendingBleHitCount
        pendingBleHitCount = 0
        if (count <= 0) return
        debugBleDrainCount += 1
        debugBleMaxDrain = maxOf(debugBleMaxDrain, count)
        dispatchPunchBatch(count)
        logBleHitStats()
    }

    private fun dispatchPunchBatch(count: Int) {
        val action = {
            if (!isFinishing && !safeModeActive && ::gameView.isInitialized) {
                runCatching {
                    gameView.registerBleHits(count, intensity = 1f)
                }.onFailure { throwable ->
                    reportFatalError("dispatchPunch", throwable)
                    renderFatalFallback(
                        freeText("Free Boxing hit feedback failed and switched to safe mode.", "Free Boxing hit feedback failed and switched to safe mode."),
                    )
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            runOnUiThread(action)
        }
    }

    private fun logBleHitStats() {
        val now = SystemClock.elapsedRealtime()
        if (debugBleWindowStartedAtMs == 0L) {
            debugBleWindowStartedAtMs = now
            return
        }
        val elapsed = now - debugBleWindowStartedAtMs
        if (elapsed < 1_000L) return
        val effectStats =
            if (::gameView.isInitialized) {
                gameView.debugEffectStats()
            } else {
                "gameView=notReady"
            }
        Log.d(
            "FreeBoxingBleStats",
            "events=${debugBleHitEvents} hitDelta=${debugBleHitDelta} drains=${debugBleDrainCount} " +
                "maxDrain=${debugBleMaxDrain} maxPending=${debugBleMaxPending} elapsed=${elapsed}ms $effectStats",
        )
        debugBleWindowStartedAtMs = now
        debugBleHitEvents = 0
        debugBleHitDelta = 0
        debugBleDrainCount = 0
        debugBleMaxDrain = 0
        debugBleMaxPending = 0
    }

    private fun dispatchBleDetectorReady() {
        dispatchDetectorState(type = "ready", progress = 1f, message = freeText("蓝牙击中识别已就绪。", "Bluetooth hit detection is ready."))
    }
    private fun dispatchDetectorState(
        type: String,
        progress: Float = 0f,
        message: String = "",
    ) {
        runOnUiThread {
            if (isFinishing || safeModeActive) {
                return@runOnUiThread
            }
            if (!trainingActive && (type == "loading" || type == "calibrating" || type == "ready")) {
                return@runOnUiThread
            }
            if (::stateTitleView.isInitialized && ::stateBodyView.isInitialized && ::progressFillView.isInitialized) {
                stateTitleView.text =
                    when (type) {
                        "idle" -> freeText("\u81EA\u7531\u62F3\u51FB", "Free Boxing")
                        "loading" -> freeText("\u51C6\u5907\u5F00\u59CB", "Preparing")
                        "calibrating" -> freeText("\u6821\u51C6\u73AF\u5883", "Calibrating Room")
                        "ready" -> freeText("\u53EF\u4EE5\u51FA\u62F3", "Punch Now")
                        "finished" -> freeText("\u672C\u8F6E\u7ED3\u675F", "Round Finished")
                        "permission_denied" -> freeText("需要蓝牙连接", "Bluetooth Needed")
                        "error" -> freeText("\u58F0\u97F3\u8BC6\u522B\u5F02\u5E38", "Detector Error")
                        else -> freeText("\u81EA\u7531\u62F3\u51FB", "Free Boxing")
                    }
                stateBodyView.text = message
                if (!trainingActive || type == "calibrating") {
                    progressFillView.scaleX = progress.coerceIn(0f, 1f)
                } else {
                    updateTimerDisplay()
                }
                stateBodyView.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
            }
            if (::gameView.isInitialized) {
                gameView.updateDetectorState(type)
            }
        }
    }

    private fun updateSpeedButtons(selected: FreeBoxingGameView.SpeedPreset) {
        speedButtons.forEach { (preset, view) ->
            val active = preset == selected
            view.background = segmentedBackground(active)
            view.setTextColor(if (active) Color.parseColor("#071524") else Color.parseColor("#EBF7FF"))
            view.isEnabled = !trainingActive
        }
        if (::paceValueView.isInitialized) {
            paceValueView.text = selected.label(languageCode)
        }
    }

    private fun updateDensityButtons(selected: FreeBoxingGameView.DensityPreset) {
        densityButtons.forEach { (preset, view) ->
            val active = preset == selected
            view.background = segmentedBackground(active)
            view.setTextColor(if (active) Color.parseColor("#071524") else Color.parseColor("#EBF7FF"))
            view.isEnabled = !trainingActive
        }
    }

    private fun updateDurationButtons(selected: TrainingDurationPreset) {
        durationButtons.forEach { (preset, view) ->
            val active = preset == selected
            view.background = segmentedBackground(active)
            view.setTextColor(if (active) Color.parseColor("#071524") else Color.parseColor("#EBF7FF"))
            view.isEnabled = !trainingActive
        }
    }

    private fun updateTrainingUi() {
        val activeRound = trainingActive || launchCountdownActive
        if (activeRound) {
            hideSettingsPage()
        }
        val settingsVisible = ::settingsPageView.isInitialized && settingsPageView.visibility == View.VISIBLE
        if (::selectionControlsView.isInitialized) {
            selectionControlsView.visibility = if (activeRound) View.GONE else View.VISIBLE
        }
        if (::actionControlsView.isInitialized) {
            actionControlsView.visibility = if (settingsVisible) View.GONE else View.VISIBLE
        }
        if (::coachHintView.isInitialized) {
            coachHintView.visibility = if (settingsVisible) View.GONE else View.VISIBLE
        }
        if (::settingsButtonView.isInitialized) {
            settingsButtonView.visibility = if (activeRound || settingsVisible) View.GONE else View.VISIBLE
            settingsButtonView.isEnabled = !activeRound
        }
        if (::combatMetricsView.isInitialized) {
            combatMetricsView.visibility = if (activeRound) View.VISIBLE else View.GONE
        }
        if (::startButtonView.isInitialized) {
            startButtonView.visibility = if (activeRound) View.GONE else View.VISIBLE
            startButtonView.isEnabled = !activeRound
        }
        if (::endButtonView.isInitialized) {
            endButtonView.visibility = if (activeRound) View.VISIBLE else View.GONE
            endButtonView.isEnabled = activeRound
        }
        updateDurationButtons(selectedDuration)
    }

    private fun updateSettingsPageVisibility(isVisible: Boolean) {
        if (::actionControlsView.isInitialized) {
            actionControlsView.visibility = if (isVisible) View.GONE else View.VISIBLE
        }
        if (::coachHintView.isInitialized) {
            coachHintView.visibility = if (isVisible) View.GONE else View.VISIBLE
        }
        if (::settingsButtonView.isInitialized) {
            settingsButtonView.visibility = if (isVisible || trainingActive || launchCountdownActive) View.GONE else View.VISIBLE
        }
    }

    private fun updateTimerDisplay() {
        val displayText =
            if (trainingActive) {
                formatRemainingTime(remainingTrainingMs)
            } else if (launchCountdownActive && ::countdownValueView.isInitialized && countdownValueView.text.isNotBlank()) {
                countdownValueView.text.toString()
            } else {
                selectedDuration.label(languageCode)
            }
        if (::countdownValueView.isInitialized && (!launchCountdownActive || trainingActive)) {
            countdownValueView.text = displayText
        }
        if (!::timerValueView.isInitialized || !::progressFillView.isInitialized) {
            return
        }
        timerValueView.text =
            if (trainingActive) {
                freeText("\u5269\u4F59 $displayText", "Left $displayText")
            } else {
                freeText("\u65F6\u957F ${selectedDuration.label(languageCode)}", "Duration ${selectedDuration.label(languageCode)}")
            }
        val ratio =
            if (trainingActive && selectedDuration.durationMs > 0L) {
                (remainingTrainingMs.toFloat() / selectedDuration.durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
        }
        progressFillView.scaleX = ratio
    }

    private fun refreshDailyEmotionState(resetRoundHits: Boolean = false) {
        ensureDailyEmotionState()
        if (resetRoundHits) {
            lastAppliedRoundHits = 0
        }
        updateDailyEmotionViews()
    }

    private fun syncDailyEmotionState(roundHits: Int) {
        val safeHits = roundHits.coerceAtLeast(0)
        val resetToday = ensureDailyEmotionState()
        if (resetToday) {
            lastAppliedRoundHits = safeHits
            updateDailyEmotionViews()
            return
        }
        if (safeHits < lastAppliedRoundHits) {
            lastAppliedRoundHits = safeHits
            updateDailyEmotionViews()
            return
        }
        val hitDelta = safeHits - lastAppliedRoundHits
        if (hitDelta > 0) {
            dailyStressLevel = (dailyStressLevel - hitDelta * DAILY_STRESS_DELTA).coerceIn(0f, DAILY_METER_MAX)
            dailyCalmLevel = (dailyCalmLevel + hitDelta * DAILY_CALM_DELTA).coerceIn(0f, DAILY_METER_MAX)
            persistDailyEmotionState()
        }
        lastAppliedRoundHits = safeHits
        updateDailyEmotionViews()
    }

    private fun ensureDailyEmotionState(): Boolean {
        val today = todayKey()
        if (dailyEmotionDayKey == today) {
            return false
        }
        val storedDay = dailyEmotionPreferences.getString(DAILY_EMOTION_DAY_KEY, null)
        return if (storedDay == today) {
            dailyEmotionDayKey = today
            dailyStressLevel =
                dailyEmotionPreferences.getFloat(DAILY_STRESS_VALUE_KEY, DAILY_STRESS_START).coerceIn(0f, DAILY_METER_MAX)
            dailyCalmLevel =
                dailyEmotionPreferences.getFloat(DAILY_CALM_VALUE_KEY, DAILY_CALM_START).coerceIn(0f, DAILY_METER_MAX)
            false
        } else {
            dailyEmotionDayKey = today
            dailyStressLevel = DAILY_STRESS_START
            dailyCalmLevel = DAILY_CALM_START
            persistDailyEmotionState()
            true
        }
    }

    private fun persistDailyEmotionState() {
        dailyEmotionPreferences
            .edit()
            .putString(DAILY_EMOTION_DAY_KEY, dailyEmotionDayKey)
            .putFloat(DAILY_STRESS_VALUE_KEY, dailyStressLevel)
            .putFloat(DAILY_CALM_VALUE_KEY, dailyCalmLevel)
            .apply()
    }

    private fun addDailyDurationSeconds(durationSeconds: Int) {
        val safeSeconds = durationSeconds.coerceAtLeast(0)
        if (safeSeconds <= 0) {
            return
        }
        ensureDailyEmotionState()
        val currentDuration = dailyEmotionPreferences.getInt(DAILY_DURATION_SECONDS_KEY, 0)
        dailyEmotionPreferences
            .edit()
            .putString(DAILY_EMOTION_DAY_KEY, dailyEmotionDayKey)
            .putInt(DAILY_DURATION_SECONDS_KEY, currentDuration + safeSeconds)
            .apply()
    }

    private fun appendSessionHistory(
        hits: Int,
        durationSeconds: Int,
    ) {
        val safeHits = hits.coerceAtLeast(0)
        val safeSeconds = durationSeconds.coerceAtLeast(0)
        if (safeHits <= 0 && safeSeconds <= 0) {
            return
        }
        ensureDailyEmotionState()
        val raw = dailyEmotionPreferences.getString(HISTORY_JSON_KEY, null).orEmpty()
        val history =
            runCatching {
                if (raw.isBlank()) JSONArray() else JSONArray(raw)
            }.getOrElse { JSONArray() }
        history.put(
            JSONObject()
                .put("timestampMs", System.currentTimeMillis())
                .put("hits", safeHits)
                .put("durationSec", safeSeconds),
        )
        dailyEmotionPreferences
            .edit()
            .putString(HISTORY_JSON_KEY, history.toString())
            .apply()
    }

    private fun elapsedRoundDurationSeconds(): Int =
        (((selectedDuration.durationMs - remainingTrainingMs).coerceAtLeast(0L)) / 1000L).toInt()

    private fun updateDailyEmotionViews() {
        if (::stressFillView.isInitialized) {
            applyVerticalMeterLevel(stressFillView, dailyStressLevel / DAILY_METER_MAX)
        }
        if (::calmFillView.isInitialized) {
            applyVerticalMeterLevel(calmFillView, dailyCalmLevel / DAILY_METER_MAX)
        }
    }

    private fun applyVerticalMeterLevel(
        view: View,
        ratio: Float,
    ) {
        val safeRatio = ratio.coerceIn(0f, 1f)
        if (view.height > 0) {
            view.pivotY = view.height.toFloat()
            view.scaleY = safeRatio
        } else {
            view.post {
                view.pivotY = view.height.toFloat()
                view.scaleY = safeRatio
            }
        }
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun formatRemainingTime(ms: Long): String {
        val totalSeconds = ((ms + 999L) / 1_000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%d:%02d".format(minutes, seconds)
    }

    private fun renderIdleState() {
        trainingActive = false
        pendingStartAfterPermission = false
        clearDetectorRecoveryState()
        remainingTrainingMs = selectedDuration.durationMs
        if (::gameView.isInitialized) {
            gameView.endTraining()
        }
        updateTrainingUi()
        updateTimerDisplay()
        dispatchDetectorState(
            type = "idle",
            progress = 0f,
            message = "",
        )
    }
    private fun hideSystemBars() {
        val decorView = window.peekDecorView() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decorView.windowInsetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility =
                (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    private fun calibrationMessage(): String =
        freeText("\u6B63\u5728\u6821\u51C6\u73AF\u5883\uFF0C\u8BF7\u4FDD\u6301\u4E00\u79D2\u5B89\u9759\u3002", "Calibrating the room. Hold still for a second.")

    private fun renderFatalFallback(message: String) {
        safeModeActive = true
        trainingActive = false
        pauseTrainingCountdown()
        stopDetectorSession()
        val host =
            if (::rootContainer.isInitialized) {
                rootContainer.apply {
                    removeAllViews()
                    setBackgroundColor(Color.parseColor("#B9E5FF"))
                }
            } else {
                FrameLayout(this).apply {
                    setBackgroundColor(Color.parseColor("#B9E5FF"))
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                }.also {
                    rootContainer = it
                    setContentView(it)
                }
            }
        host.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(28), dp(24), dp(28), dp(24))
                background = glassPanel(24)
                layoutParams =
                    FrameLayout.LayoutParams(
                        min(dp(620), resources.displayMetrics.widthPixels - dp(48)),
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    )
                addView(
                    TextView(this@FreeBoxingActivity).apply {
                        text = freeText("自由拳击安全模式", "Free Boxing Safe Mode")
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        setTypeface(Typeface.DEFAULT_BOLD)
                        textSize = 22f
                    },
                )
                addView(
                    TextView(this@FreeBoxingActivity).apply {
                        text = message
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#D7E9F7"))
                        textSize = 15f
                        setLineSpacing(0f, 1.15f)
                        setPadding(0, dp(12), 0, dp(10))
                    },
                )
                addView(
                    TextView(this@FreeBoxingActivity).apply {
                        text = freeText("The error was written to a local app log for follow-up debugging.", "The error was written to a local app log for follow-up debugging.")
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#BFE4FF"))
                        textSize = 13f
                    },
                )
                addView(verticalSpace(dp(18)))
                addView(
                    TextView(this@FreeBoxingActivity).apply {
                        text = freeText("Back", "Back")
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        setTypeface(Typeface.DEFAULT_BOLD)
                        textSize = 15f
                        background = segmentedBackground(true)
                        setPadding(dp(18), dp(12), dp(18), dp(12))
                        setOnClickListener { finish() }
                    },
                )
            },
        )
    }

    private fun reportFatalError(
        source: String,
        throwable: Throwable,
    ) {
        val errorText =
            buildString {
                appendLine("source=$source")
                appendLine("time=${System.currentTimeMillis()}")
                appendLine("message=${throwable.message}")
                val writer = StringWriter()
                throwable.printStackTrace(PrintWriter(writer))
                append(writer.toString())
            }
        runCatching {
            File(filesDir, "free_boxing_last_error.txt").writeText(errorText, Charsets.UTF_8)
        }
    }

    private fun glassPanel(cornerDp: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#8F08294A"), Color.parseColor("#48081D39")),
        ).apply {
            cornerRadius = dp(cornerDp).toFloat()
            setStroke(dp(1), Color.parseColor("#3DFFFFFF"))
        }

    private fun roundedFill(
        fillColor: String,
        cornerDp: Int,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerDp).toFloat()
            setColor(Color.parseColor(fillColor))
        }

    private fun topRoundedFill(
        fillColor: String,
        topCornerDp: Int,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii =
                floatArrayOf(
                    dp(topCornerDp).toFloat(),
                    dp(topCornerDp).toFloat(),
                    dp(topCornerDp).toFloat(),
                    dp(topCornerDp).toFloat(),
                    0f,
                    0f,
                    0f,
                    0f,
                )
            setColor(Color.parseColor(fillColor))
        }

    private fun roundedGradient(
        colors: IntArray,
        cornerDp: Int,
    ): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = dp(cornerDp).toFloat()
        }

    private fun topRoundedGradient(
        orientation: GradientDrawable.Orientation,
        colors: IntArray,
        topCornerDp: Int,
    ): GradientDrawable =
        GradientDrawable(orientation, colors).apply {
            cornerRadii =
                floatArrayOf(
                    dp(topCornerDp).toFloat(),
                    dp(topCornerDp).toFloat(),
                    dp(topCornerDp).toFloat(),
                    dp(topCornerDp).toFloat(),
                    0f,
                    0f,
                    0f,
                    0f,
                )
        }

    private fun segmentedBackground(active: Boolean): GradientDrawable =
        if (active) {
            roundedGradient(intArrayOf(Color.parseColor("#88E1FF"), Color.parseColor("#379EF5")), 14)
        } else {
            roundedFill("#14FFFFFF", 14).apply { setStroke(dp(1), Color.parseColor("#30FFFFFF")) }
        }

    private fun confirmButtonBackground(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#BFF3FF"),
                Color.parseColor("#5CC4FF"),
                Color.parseColor("#247FD8"),
            ),
        ).apply {
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.parseColor("#E5FAFF"))
        }

    private fun freeText(
        zh: String,
        en: String,
    ): String = if (languageCode.startsWith("zh")) zh else en

    private fun horizontalSpace(width: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(width, 1)
        }

    private fun verticalSpace(height: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SENSITIVITY_LEVEL = "free_boxing_sensitivity"
        const val EXTRA_LANGUAGE = "free_boxing_language"
        private const val MAX_DETECTOR_RECOVERY_ATTEMPTS = 3
        private const val DETECTOR_RECOVERY_DELAY_MS = 320L
        private const val DAILY_EMOTION_PREFS = "free_boxing_daily_emotion"
        private const val DAILY_EMOTION_DAY_KEY = "day_key"
        private const val DAILY_DURATION_SECONDS_KEY = "duration_seconds"
        private const val HISTORY_JSON_KEY = "history_json"
        private const val DAILY_STRESS_VALUE_KEY = "stress_value"
        private const val DAILY_CALM_VALUE_KEY = "calm_value"
        private const val DAILY_STRESS_START = 100f
        private const val DAILY_CALM_START = 50f
        private const val DAILY_METER_MAX = 100f
        private const val DAILY_STRESS_DELTA = 0.2f
        private const val DAILY_CALM_DELTA = 0.2f
    }
}











