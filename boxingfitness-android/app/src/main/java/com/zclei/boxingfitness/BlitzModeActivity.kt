package com.zclei.boxingfitness

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import com.zclei.boxingfitness.cloud.CloudTrainingUploader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class BlitzModeActivity : AppCompatActivity() {
    private enum class TrainingDurationPreset(
        val seconds: Int,
        val zhLabel: String,
        val enLabel: String,
        val frLabel: String,
        val thLabel: String,
    ) {
        Seconds30(30, "30 秒", "30s", "30 s", "30 วินาที"),
        Seconds60(60, "60 秒", "60s", "60 s", "60 วินาที"),
        Seconds120(120, "120 秒", "120s", "120 s", "120 วินาที"),
        ;

        val durationMs: Long
            get() = seconds * 1_000L

        fun label(languageCode: String): String =
            when {
                languageCode.startsWith("zh") -> zhLabel
                languageCode.startsWith("fr") -> frLabel
                languageCode.startsWith("th") -> thLabel
                else -> enLabel
            }
    }

    private data class AggregateValue(
        val calories: Float,
        val fatBurnGrams: Float,
        val hits: Int = 0,
    )

    private lateinit var rootContainer: FrameLayout
    private lateinit var gameView: BlitzModeGameView
    private lateinit var backButtonView: View
    private lateinit var selectionControlsView: View
    private lateinit var statusBarView: View
    private lateinit var energyPanelView: View
    private lateinit var hintView: TextView
    private lateinit var hitsValueView: TextView
    private lateinit var comboValueView: TextView
    private lateinit var ppsValueView: TextView
    private lateinit var timerValueView: TextView
    private lateinit var caloriesValueView: TextView
    private lateinit var fatBurnValueView: TextView
    private lateinit var intensityValueView: TextView
    private lateinit var todaySummaryValueView: TextView
    private lateinit var weekSummaryValueView: TextView
    private lateinit var monthSummaryValueView: TextView
    private lateinit var startButtonView: TextView
    private lateinit var endButtonView: TextView
    private lateinit var actionControlsView: View
    private lateinit var launchCountdownView: TextView
    private lateinit var evaluationPopupView: View
    private lateinit var evaluationTitleView: TextView
    private lateinit var evaluationBodyView: TextView

    private var detectorJob: Job? = null
    private var bleHitListener: BoxingBleRuntime.HitListener? = null
    private var bleImpactPreviewListener: BoxingBleRuntime.ImpactPreviewListener? = null
    private var detectorRecoveryJob: Job? = null
    private var countdownJob: Job? = null
    private var launchCountdownJob: Job? = null
    private var evaluationPopupJob: Job? = null
    private var speechEngine: TextToSpeech? = null
    private var speechReady = false
    private var trainingActive = false
    private var launchCountdownActive = false
    private var pendingStartAfterPermission = false
    private var countdownTargetElapsedMs = 0L
    private var remainingTrainingMs = TrainingDurationPreset.Seconds30.durationMs
    private var trainingRunId = 0L
    private var detectorRecoveryAttempts = 0
    private var selectedDuration = TrainingDurationPreset.Seconds30
    private var selectedDifficulty = BlitzModeGameView.DifficultyPreset.Advanced
    private var lastSnapshot = BlitzModeGameView.OverlaySnapshot(0, 0, 0, 0f, 0f, 0f)
    private var lastSessionDurationSeconds = 0
    private val durationButtons = LinkedHashMap<TrainingDurationPreset, TextView>()
    private val difficultyButtons = LinkedHashMap<BlitzModeGameView.DifficultyPreset, TextView>()
    private val aggregatePrefs by lazy {
        getSharedPreferences("blitz_mode_aggregate", MODE_PRIVATE)
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
        runCatching {
            setContentView(buildContentView())
            hideSystemBars()
            renderIdleState()
        }.onFailure {
            setContentView(buildFatalFallback())
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (trainingActive) {
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

    private fun buildContentView(): View {
        rootContainer =
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }

        gameView =
            BlitzModeGameView(this).apply {
                setLanguageCode(languageCode)
                setDifficultyPreset(selectedDifficulty)
            }
        rootContainer.addView(
            gameView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val overlay =
            FrameLayout(this).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                setPadding(dp(16), dp(14), dp(16), dp(16))
            }

        backButtonView = buildBackButton()
        overlay.addView(
            backButtonView,
            FrameLayout.LayoutParams(
                dp(46),
                dp(46),
                Gravity.TOP or Gravity.START,
            ).apply {
                leftMargin = dp(6)
                topMargin = dp(6)
            },
        )

        val setupStack =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }

        selectionControlsView = buildSelectionPanel()
        setupStack.addView(
            selectionControlsView,
            LinearLayout.LayoutParams(
                dp(338),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        energyPanelView = buildAggregatePanel()
        setupStack.addView(
            energyPanelView,
            LinearLayout.LayoutParams(
                dp(258),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = dp(12)
            },
        )

        overlay.addView(
            setupStack,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply {
                topMargin = dp(18)
            },
        )
        setupStack.post { alignTopPanels() }

        statusBarView = buildStatusBar()
        overlay.addView(
            statusBarView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply {
                topMargin = dp(10)
            },
        )

        hintView =
            TextView(this).apply {
                text = freeText("选择时长和强度，准备开启极速燃脂。", "Choose a duration and difficulty, then start Rapid Fat Burn.", "Choisissez une durée et une intensité, puis lancez Brûle-graisse express.", "เลือกเวลาและความเข้มข้น แล้วเริ่มโหมดเผาผลาญเร่งด่วน")
                setTextColor(Color.parseColor("#E8FAFF"))
                textSize = 15f
                gravity = Gravity.CENTER
                background = roundedFill("#73101927", "#4E55D8FF", 20)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                visibility = View.GONE
            }
        overlay.addView(
            hintView,
            FrameLayout.LayoutParams(
                dp(520),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                bottomMargin = dp(24)
            },
        )

        actionControlsView = buildActionControls()
        overlay.addView(
            actionControlsView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START,
            ).apply {
                bottomMargin = dp(18)
                leftMargin = dp(8)
            },
        )

        launchCountdownView =
            TextView(this).apply {
                visibility = View.GONE
                setTextColor(Color.WHITE)
                setTypeface(Typeface.DEFAULT_BOLD)
                textSize = 68f
                gravity = Gravity.CENTER
                setShadowLayer(dp(18).toFloat(), 0f, dp(4).toFloat(), Color.parseColor("#CC0CC9FF"))
            }
        overlay.addView(
            launchCountdownView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        evaluationPopupView = buildEvaluationPopup()
        overlay.addView(
            evaluationPopupView,
            FrameLayout.LayoutParams(
                dp(440),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        rootContainer.addView(overlay)

        gameView.bind(
            object : BlitzModeGameView.Listener {
                override fun onOverlaySnapshot(snapshot: BlitzModeGameView.OverlaySnapshot) {
                    lastSnapshot = snapshot
                    runOnUiThread {
                        updateOverlay(snapshot)
                    }
                }

                override fun onHintChanged(hint: String) {
                    runOnUiThread {
                        if (::hintView.isInitialized && hint.isNotBlank() && (trainingActive || launchCountdownActive)) {
                            hintView.text = hint
                            hintView.visibility = View.VISIBLE
                        }
                    }
                }
            },
        )

        return rootContainer
    }

    private fun buildFatalFallback(): View =
        FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#06111B"))
            addView(
                TextView(this@BlitzModeActivity).apply {
                    text = freeText("极速燃脂启动失败，请返回锻炼中心后重试。", "Rapid Fat Burn failed to start. Please return and try again.", "Échec du démarrage de Brûle-graisse express. Veuillez revenir et réessayer.", "เริ่มโหมดเผาผลาญเร่งด่วนไม่สำเร็จ โปรดย้อนกลับและลองใหม่")
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    gravity = Gravity.CENTER
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

    private fun buildBackButton(): View =
        TextView(this).apply {
            text = "←"
            contentDescription = freeText("返回", "Back", "Retour", "กลับ")
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.WHITE)
            textSize = 24f
            background = roundedFill("#8C0A1320", "#5A64DFFF", 18)
            elevation = dp(6).toFloat()
            setOnClickListener { finish() }
        }

    private fun buildSelectionPanel(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill("#98101A28", "#6257D8FF", 24)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            addView(
                TextView(this@BlitzModeActivity).apply {
                    text = freeText("极速燃脂", "Rapid Fat Burn", "Brûle-graisse express", "เผาผลาญเร่งด่วน")
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 24f
                },
            )
            addView(
                TextView(this@BlitzModeActivity).apply {
                    text = freeText("HIIT 燃脂模式，强调节奏、速度与耐力。", "HIIT fat-burn mode focused on cadence, pace, and endurance.", "Mode HIIT axé sur la cadence, le rythme et l'endurance.", "โหมด HIIT เผาผลาญไขมัน เน้นจังหวะ ความเร็ว และความอึด")
                    setTextColor(Color.parseColor("#CDEBFF"))
                    textSize = 11f
                    setPadding(0, dp(4), 0, 0)
                },
            )
            addView(sectionTitle(freeText("训练时间", "Duration", "Durée", "เวลา")))
            addView(optionRow(durationButtons) { addDurationButtons(this) })
            addView(sectionTitle(freeText("训练强度", "Difficulty", "Intensité", "ความเข้มข้น")))
            addView(optionRow(difficultyButtons) { addDifficultyButtons(this) })
            addView(
                TextView(this@BlitzModeActivity).apply {
                        text =
                            freeText(
                            "左右靶位独立随机出现，系统按有效击打次数、节奏频率和强度估算卡路里与燃脂量。",
                            "Targets appear independently at random. Calories and fat burn are estimated from valid hits, pace, and intensity.",
                            "Les cibles apparaissent aléatoirement et indépendamment. Les calories et la graisse brûlée sont estimées selon les coups valides, le rythme et l'intensité.",
                            "เป้าซ้ายขวาจะสุ่มแยกกันอย่างอิสระ ระบบจะประเมินแคลอรีและการเผาผลาญไขมันจากจำนวนครั้งที่ชก จังหวะ และความแรง",
                        )
                    setTextColor(Color.parseColor("#A9DFFF"))
                    textSize = 10f
                    setPadding(0, dp(8), 0, 0)
                },
            )
        }

    private fun buildStatusBar(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            background = roundedFill("#7A08131D", "#4E67D8FF", 18)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            addView(statusMetric(freeText("击中", "Hits", "Coups", "ชกเข้า")) { hitsValueView = it })
            addView(horizontalSpace(dp(6)))
            addView(statusMetric(freeText("连击", "Combo", "Combo", "คอมโบ")) { comboValueView = it })
            addView(horizontalSpace(dp(6)))
            addView(statusMetric("PPS") { ppsValueView = it })
            addView(horizontalSpace(dp(6)))
            addView(statusMetric(freeText("时间", "Time", "Temps", "เวลา")) { timerValueView = it })
        }

    private fun buildEnergyPanel(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill("#7C0B1521", "#544FE0FF", 20)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(
                TextView(this@BlitzModeActivity).apply {
                    text = freeText("燃脂估算", "Burn Estimate", "Estimation de dépense", "ประเมินการเผาผลาญ")
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 15f
                },
            )
            addView(energyMetric(freeText("消耗卡路里", "Calories", "Calories", "แคลอรี"), "kcal") { caloriesValueView = it })
            addView(verticalSpace(dp(6)))
            addView(energyMetric(freeText("燃脂量", "Fat Burn", "Graisse brûlée", "ไขมันที่เผาผลาญ"), "g") { fatBurnValueView = it })
            addView(verticalSpace(dp(10)))
            intensityValueView =
                TextView(this@BlitzModeActivity).apply {
                    setTextColor(Color.parseColor("#C4F0FF"))
                    textSize = 13f
                    text = difficultyText()
                }
            addView(intensityValueView)
        }

    private fun buildAggregatePanel(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill("#7C0B1521", "#544FE0FF", 20)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            addView(
                TextView(this@BlitzModeActivity).apply {
                    text = freeText("燃脂估算", "Burn Estimate")
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 16f
                },
            )
            addView(
                TextView(this@BlitzModeActivity).apply {
                    text = "LOCAL"
                    setTextColor(Color.parseColor("#062437"))
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 9f
                    gravity = Gravity.CENTER
                    background = roundedFill("#79E8FFFF", "#B8FFFFFF", 10)
                    setPadding(dp(8), dp(3), dp(8), dp(3))
                },
            )
            addView(
                TextView(this@BlitzModeActivity).apply {
                    text = freeText("本地累计 / 今日 / 本周 / 本月", "Local totals: Today / Week / Month", "Totaux locaux : Jour / Semaine / Mois", "สะสมในเครื่อง / วันนี้ / สัปดาห์ / เดือน")
                    setTextColor(Color.parseColor("#BEEAFF"))
                    textSize = 11f
                    setPadding(0, dp(4), 0, dp(8))
                },
            )
            addView(aggregateMetricRow(freeText("今日", "Today", "Aujourd'hui", "วันนี้")) { todaySummaryValueView = it })
            addView(verticalSpace(dp(6)))
            addView(aggregateMetricRow(freeText("本周", "This Week", "Cette semaine", "สัปดาห์นี้")) { weekSummaryValueView = it })
            addView(verticalSpace(dp(6)))
            addView(aggregateMetricRow(freeText("本月", "This Month", "Ce mois", "เดือนนี้")) { monthSummaryValueView = it })
            addView(
                TextView(this@BlitzModeActivity).apply {
                    text = freeText("每轮训练都会累计到本机统计。", "Each round is added to your device totals.", "Chaque tour est ajouté aux totaux locaux.", "แต่ละรอบจะถูกสะสมลงในสถิติของเครื่อง")
                    setTextColor(Color.parseColor("#8FD7FF"))
                    textSize = 10f
                    setPadding(0, dp(8), 0, 0)
                },
            )
        }

    private fun buildActionControls(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            startButtonView =
                commandButton(freeText("开始", "Start", "Commencer", "เริ่ม"), "#15D6A2", true).apply {
                    minWidth = dp(90)
                    textSize = 17f
                    setPadding(dp(18), dp(11), dp(18), dp(11))
                    setOnClickListener { requestTrainingStart() }
                }
            addView(startButtonView)
            addView(horizontalSpace(dp(10)))
            endButtonView =
                commandButton(freeText("结束", "End", "Terminer", "จบ"), "#FF8658", false).apply {
                    minWidth = dp(90)
                    textSize = 17f
                    setPadding(dp(18), dp(11), dp(18), dp(11))
                    setOnClickListener {
                        if (launchCountdownActive) {
                            cancelLaunchCountdown(showMessage = true)
                        } else {
                            finishTrainingSession(manual = true)
                        }
                    }
                }
            addView(endButtonView)
        }

    private fun buildEvaluationPopup(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            background = roundedFill("#EE0A1420", "#6A59E6FF", 24)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            evaluationTitleView =
                TextView(this@BlitzModeActivity).apply {
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 24f
                }
            addView(evaluationTitleView)
            evaluationBodyView =
                TextView(this@BlitzModeActivity).apply {
                    setTextColor(Color.parseColor("#D8F3FF"))
                    textSize = 16f
                    setLineSpacing(0f, 1.18f)
                    setPadding(0, dp(12), 0, 0)
                }
            addView(evaluationBodyView)
        }

    private fun sectionTitle(text: String): View =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
            textSize = 13f
            setPadding(0, dp(10), 0, dp(5))
        }

    private fun optionRow(
        buttons: LinkedHashMap<*, TextView>,
        builder: LinearLayout.() -> Unit,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            builder()
        }

    private fun addDurationButtons(container: LinearLayout) {
        TrainingDurationPreset.entries.forEachIndexed { index, preset ->
            val chip =
                optionChip(preset.label(languageCode), preset == selectedDuration).apply {
                    setOnClickListener {
                        selectedDuration = preset
                        updateDurationButtons()
                        updateTimerDisplay()
                    }
                }
            durationButtons[preset] = chip
            container.addView(chip)
            if (index < TrainingDurationPreset.entries.lastIndex) {
                container.addView(horizontalSpace(dp(8)))
            }
        }
    }

    private fun addDifficultyButtons(container: LinearLayout) {
        BlitzModeGameView.DifficultyPreset.entries.forEachIndexed { index, preset ->
            val chip =
                optionChip(preset.label(languageCode), preset == selectedDifficulty).apply {
                    setOnClickListener {
                        selectedDifficulty = preset
                        gameView.setDifficultyPreset(preset)
                        updateDifficultyButtons()
                    }
                }
            difficultyButtons[preset] = chip
            container.addView(chip)
            if (index < BlitzModeGameView.DifficultyPreset.entries.lastIndex) {
                container.addView(horizontalSpace(dp(8)))
            }
        }
    }

    private fun optionChip(
        text: String,
        selected: Boolean,
    ): TextView =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT_BOLD)
            textSize = 12f
            minWidth = dp(78)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = selectionChipBackground(selected)
            setTextColor(if (selected) Color.parseColor("#061520") else Color.parseColor("#CFEAFF"))
        }

    private fun statusMetric(
        label: String,
        ref: (TextView) -> Unit,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedFill("#4A0D1A28", "#3754A9CC", 16)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(
                TextView(this@BlitzModeActivity).apply {
                    text = label
                    setTextColor(Color.parseColor("#BFE8FF"))
                    textSize = 10f
                    gravity = Gravity.CENTER
                },
            )
            addView(
                TextView(this@BlitzModeActivity).apply {
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 18f
                    gravity = Gravity.CENTER
                    text = "--"
                    ref(this)
                },
            )
        }

    private fun energyMetric(
        label: String,
        unit: String,
        ref: (TextView) -> Unit,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill("#321B2B3A", "#2D6EDCFF", 16)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(
                TextView(this@BlitzModeActivity).apply {
                    text = label
                    setTextColor(Color.parseColor("#BDEBFF"))
                    textSize = 12f
                },
            )
            addView(
                LinearLayout(this@BlitzModeActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.BOTTOM
                    addView(
                        TextView(this@BlitzModeActivity).apply {
                            setTextColor(Color.WHITE)
                            setTypeface(Typeface.DEFAULT_BOLD)
                            textSize = 24f
                            text = "0.0"
                            ref(this)
                        },
                    )
                    addView(
                        TextView(this@BlitzModeActivity).apply {
                            text = " $unit"
                            setTextColor(Color.parseColor("#A7DBFF"))
                            textSize = 13f
                            setPadding(dp(4), 0, 0, dp(3))
                        },
                    )
                },
            )
        }

    private fun aggregateMetricRow(
        label: String,
        ref: (TextView) -> Unit,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedFill("#3A172838", "#4684E8FF", 16)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            addView(
                View(this@BlitzModeActivity).apply {
                    background = roundedFill("#72BAF6FF", "#C4EFFFFF", 3)
                    layoutParams = LinearLayout.LayoutParams(dp(4), dp(28))
                },
            )
            addView(horizontalSpace(dp(10)))
            addView(
                TextView(this@BlitzModeActivity).apply {
                    text = label
                    setTextColor(Color.parseColor("#D6F3FF"))
                    textSize = 11f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    gravity = Gravity.CENTER
                    background = roundedFill("#35213C52", "#4678C6FF", 10)
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                },
            )
            addView(horizontalSpace(dp(8)))
            addView(
                TextView(this@BlitzModeActivity).apply {
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    textSize = 14f
                    gravity = Gravity.START
                    text = formatAggregateValue(AggregateValue(0f, 0f))
                    ref(this)
                },
            )
        }

    private fun commandButton(
        text: String,
        accentColor: String,
        primary: Boolean,
    ): TextView =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.WHITE)
            includeFontPadding = false
            textSize = 22f
            minWidth = dp(118)
            setPadding(dp(24), dp(16), dp(24), dp(16))
            minLines = 1
            maxLines = 2
            setLineSpacing(0f, 1.05f)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 12, 22, 1, TypedValue.COMPLEX_UNIT_SP)
            background =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(22).toFloat()
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                    colors =
                        if (primary) {
                            intArrayOf(Color.parseColor(accentColor), Color.parseColor("#119981"))
                        } else {
                            intArrayOf(Color.parseColor(accentColor), Color.parseColor("#B84E31"))
                        }
                    setStroke(dp(1), Color.parseColor("#66FFFFFF"))
                }
            elevation = dp(8).toFloat()
            alpha = if (primary) 1f else 0.92f
        }

    private fun initSpeechEngine() {
        speechEngine =
            TextToSpeech(applicationContext) { status ->
                speechReady = status == TextToSpeech.SUCCESS
                if (speechReady) {
                    speechEngine?.language = ttsLocaleForLanguage()
                    speechEngine?.setSpeechRate(1.02f)
                    speechEngine?.setPitch(1.0f)
                }
            }
    }

    private fun updateDurationButtons() {
        durationButtons.forEach { (preset, button) ->
            val selected = preset == selectedDuration
            button.background = selectionChipBackground(selected)
            button.setTextColor(if (selected) Color.parseColor("#05141F") else Color.parseColor("#CFEAFF"))
        }
    }

    private fun updateDifficultyButtons() {
        difficultyButtons.forEach { (preset, button) ->
            val selected = preset == selectedDifficulty
            button.background = selectionChipBackground(selected)
            button.setTextColor(if (selected) Color.parseColor("#05141F") else Color.parseColor("#CFEAFF"))
        }
    }

    private fun selectionChipBackground(selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            if (selected) {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(Color.parseColor("#5EF1FF"), Color.parseColor("#B4F7FF"))
                setStroke(dp(1), Color.parseColor("#DAFFFFFF"))
            } else {
                setColor(Color.parseColor("#2C102132"))
                setStroke(dp(1), Color.parseColor("#4D69CAFF"))
            }
        }

    private fun requestTrainingStart() {
        if (trainingActive || launchCountdownActive) {
            return
        }
        startLaunchCountdown()
    }

    private fun startLaunchCountdown() {
        if (trainingActive || launchCountdownActive) {
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
        gameView.endTraining()
        updateTrainingUi()
        updateTimerDisplay()
        dispatchDetectorState(type = "loading", message = freeText("准备冲刺：3、2、1、开始！", "Get ready to sprint: 3, 2, 1, GO!"))
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
                    gameView.playCountdownPulse()
                    delay(if (step == "GO") 620L else 860L)
                }
                if (launchCountdownActive && runId == trainingRunId) {
                    launchCountdownActive = false
                    hideLaunchCountdown()
                    startTrainingSession()
                }
            }
    }

    private fun showLaunchCountdownStep(step: String) {
        launchCountdownView.animate().cancel()
        launchCountdownView.text = if (step == "GO") freeText("开始!", "GO!", "Partez !", "เริ่ม!") else step
        launchCountdownView.visibility = View.VISIBLE
        launchCountdownView.alpha = 0.96f
        launchCountdownView.scaleX = 0.62f
        launchCountdownView.scaleY = 0.62f
        launchCountdownView.animate()
            .scaleX(if (step == "GO") 1.42f else 1.20f)
            .scaleY(if (step == "GO") 1.42f else 1.20f)
            .alpha(1f)
            .setDuration(220L)
            .start()
    }

    private fun hideLaunchCountdown() {
        launchCountdownView.animate().cancel()
        launchCountdownView.visibility = View.GONE
        launchCountdownView.scaleX = 1f
        launchCountdownView.scaleY = 1f
        launchCountdownView.alpha = 1f
    }

    private fun speakLaunchCountdownStep(step: String) {
        if (!speechReady) {
            return
        }
        val speechText =
            when {
                step != "GO" -> step
                languageCode.startsWith("zh") -> "开始"
                languageCode.startsWith("fr") -> "Partez"
                languageCode.startsWith("th") -> "เริ่ม"
                else -> "Go"
            }
        speechEngine?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "blitz_countdown_$step")
    }

    private fun startTrainingSession() {
        if (trainingActive) {
            return
        }
        BoxingBleRuntime.enableGyro()
        launchCountdownActive = false
        pendingStartAfterPermission = false
        trainingActive = true
        trainingRunId += 1L
        remainingTrainingMs = selectedDuration.durationMs
        countdownTargetElapsedMs = 0L
        lastSnapshot = BlitzModeGameView.OverlaySnapshot(0, 0, 0, 0f, 0f, 0f)
        gameView.setDifficultyPreset(selectedDifficulty)
        gameView.beginTraining()
        updateOverlay(lastSnapshot)
        updateTrainingUi()
        updateTimerDisplay()
        dispatchDetectorState(type = "loading", message = freeText("训练开始，正在准备蓝牙击中识别。", "Round started. Preparing Bluetooth hit detection.", "La séance démarre, préparation de la détection Bluetooth.", "เริ่มการฝึกแล้ว กำลังเตรียมระบบตรวจจับผ่านบลูทูธ"))
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
        clearDetectorRecoveryState()
        remainingTrainingMs = selectedDuration.durationMs
        updateTrainingUi()
        updateTimerDisplay()
        if (showMessage) {
            dispatchDetectorState(type = "finished", message = freeText("本轮已取消，可以重新开始。", "Round cancelled. You can start again.", "Le tour a été annulé. Vous pouvez recommencer.", "ยกเลิกรอบนี้แล้ว สามารถเริ่มใหม่ได้"))
        } else {
            dispatchDetectorState(type = "idle", message = "")
        }
    }

    private fun finishTrainingSession(manual: Boolean) {
        if (!trainingActive) {
            return
        }
        BoxingBleRuntime.disableGyro()
        lastSessionDurationSeconds = currentSessionDurationSeconds().coerceAtLeast(0)
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
            message =
                if (manual) {
                    freeText("极速燃脂已结束，本轮数据已结算。", "Rapid Fat Burn stopped. Your round has been scored.", "Brûle-graisse express terminé. Votre tour a été comptabilisé.", "จบโหมดเผาผลาญเร่งด่วนแล้ว ระบบได้สรุปรอบนี้เรียบร้อย")
                } else {
                    freeText("时间到！本轮极速燃脂结算完成。", "Time up! Your Rapid Fat Burn round has been scored.", "Temps écoulé ! Votre tour Brûle-graisse express a été comptabilisé.", "หมดเวลา! ระบบได้สรุปรอบเผาผลาญเร่งด่วนนี้เรียบร้อย")
                },
        )
        persistAggregateTotals(lastSnapshot, lastSessionDurationSeconds)
        maybeUploadRoundToCloud(lastSnapshot, lastSessionDurationSeconds)
        refreshAggregatePanel()
        showTrainingSummaryPopup(manual)
    }

    private fun showTrainingSummaryPopup(manual: Boolean) {
        val snapshot = lastSnapshot
        val encouragement =
            when {
                snapshot.pps >= 2.6f -> freeText(
                    "这轮爆发很强，已经接近真正的 HIIT 冲刺节奏。",
                    "That was explosive. You were very close to a real HIIT sprint rhythm.",
                    "C'était explosif. Vous étiez tout près d'un vrai rythme de sprint HIIT.",
                    "รอบนี้ระเบิดพลังได้ดีมาก ใกล้เคียงจังหวะสปรินต์ HIIT จริงแล้ว",
                )
                snapshot.pps >= 1.7f -> freeText(
                    "节奏已经很稳，下一轮再把转换提得更快会更顺。",
                    "The rhythm was solid. Switch even faster next round and it will feel smoother.",
                    "Le rythme était solide. Accélérez encore les transitions au prochain tour.",
                    "จังหวะเริ่มนิ่งแล้ว รอบหน้าถ้าเปลี่ยนจังหวะให้เร็วขึ้นอีกจะลื่นมากขึ้น",
                )
                snapshot.hits > 0 -> freeText(
                    "已经建立了基础速度，下一轮前 10 秒可以再狠一点。",
                    "You established the base pace. Push the first 10 seconds even harder next round.",
                    "Vous avez posé une bonne base. Accélérez encore les 10 premières secondes au prochain tour.",
                    "รอบนี้ตั้งจังหวะพื้นฐานได้แล้ว รอบหน้าลองเร่ง 10 วินาทีแรกให้แรงขึ้นอีก",
                )
                else -> freeText(
                    "这一轮还没有完全进入节奏，准备好时可以马上再来。",
                    "This round never locked into rhythm. You can jump right back in whenever you are ready.",
                    "Le rythme n'a pas encore pris. Vous pouvez relancer un tour dès que vous êtes prêt.",
                    "รอบนี้ยังไม่เข้าจังหวะเต็มที่ พร้อมเมื่อไรก็เริ่มใหม่ได้ทันที",
                )
            }
        val title =
            if (manual) {
                freeText("本轮结束", "Round Ended", "Tour terminé", "จบรอบ")
            } else {
                freeText("时间到", "TIME UP", "TEMPS ÉCOULÉ", "หมดเวลา")
            }
        evaluationTitleView.text = title
        evaluationBodyView.text =
            freeText(
                buildString {
                    appendLine("有效击中：${snapshot.hits}")
                    appendLine("平均频率：${formatDecimal(snapshot.pps, 2)} 次/秒")
                    appendLine("最佳连击：${snapshot.bestCombo}")
                    appendLine("消耗卡路里：${formatDecimal(snapshot.calories, 1)} kcal")
                    appendLine("燃脂量：${formatDecimal(snapshot.fatBurnGrams, 1)} g")
                    appendLine()
                    append(encouragement)
                },
                buildString {
                    appendLine("Valid hits: ${snapshot.hits}")
                    appendLine("Average pace: ${formatDecimal(snapshot.pps, 2)} hits/s")
                    appendLine("Best combo: ${snapshot.bestCombo}")
                    appendLine("Calories: ${formatDecimal(snapshot.calories, 1)} kcal")
                    appendLine("Fat burn: ${formatDecimal(snapshot.fatBurnGrams, 1)} g")
                    appendLine()
                    append(encouragement)
                },
                buildString {
                    appendLine("Coups valides : ${snapshot.hits}")
                    appendLine("Rythme moyen : ${formatDecimal(snapshot.pps, 2)} coups/s")
                    appendLine("Meilleur combo : ${snapshot.bestCombo}")
                    appendLine("Calories : ${formatDecimal(snapshot.calories, 1)} kcal")
                    appendLine("Graisse brûlée : ${formatDecimal(snapshot.fatBurnGrams, 1)} g")
                    appendLine()
                    append(encouragement)
                },
                buildString {
                    appendLine("ชกเข้า有效: ${snapshot.hits}")
                    appendLine("ความถี่เฉลี่ย: ${formatDecimal(snapshot.pps, 2)} ครั้ง/วินาที")
                    appendLine("คอมโบสูงสุด: ${snapshot.bestCombo}")
                    appendLine("แคลอรีที่ใช้: ${formatDecimal(snapshot.calories, 1)} kcal")
                    appendLine("การเผาผลาญไขมัน: ${formatDecimal(snapshot.fatBurnGrams, 1)} g")
                    appendLine()
                    append(encouragement)
                },
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
        speakSummary(snapshot)
    }

    private fun speakSummary(snapshot: BlitzModeGameView.OverlaySnapshot) {
        if (!speechReady) {
            return
        }
        val message =
            freeText(
                "本轮极速燃脂完成，有效击中 ${snapshot.hits} 次，平均频率 ${formatDecimal(snapshot.pps, 2)} 次每秒，消耗 ${formatDecimal(snapshot.calories, 1)} 大卡，燃脂约 ${formatDecimal(snapshot.fatBurnGrams, 1)} 克。",
                "Rapid Fat Burn complete. ${snapshot.hits} valid hits, ${formatDecimal(snapshot.pps, 2)} hits per second, ${formatDecimal(snapshot.calories, 1)} calories, and about ${formatDecimal(snapshot.fatBurnGrams, 1)} grams of fat burn.",
                "Brûle-graisse express terminé. ${snapshot.hits} coups valides, ${formatDecimal(snapshot.pps, 2)} coups par seconde, ${formatDecimal(snapshot.calories, 1)} calories et environ ${formatDecimal(snapshot.fatBurnGrams, 1)} g de graisse brûlée.",
                "จบโหมดเผาผลาญเร่งด่วนแล้ว ชกเข้า有效 ${snapshot.hits} ครั้ง ความถี่เฉลี่ย ${formatDecimal(snapshot.pps, 2)} ครั้งต่อวินาที ใช้พลังงาน ${formatDecimal(snapshot.calories, 1)} kcal และเผาผลาญไขมันประมาณ ${formatDecimal(snapshot.fatBurnGrams, 1)} g",
            )
        speechEngine?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "blitz_summary")
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

    private fun startTrainingCountdown() {
        pauseTrainingCountdown()
        val runId = trainingRunId
        countdownTargetElapsedMs = SystemClock.elapsedRealtime() + remainingTrainingMs
        countdownJob =
            lifecycleScope.launch {
                while (trainingActive && runId == trainingRunId) {
                    remainingTrainingMs = max(0L, countdownTargetElapsedMs - SystemClock.elapsedRealtime())
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
            remainingTrainingMs = max(0L, countdownTargetElapsedMs - SystemClock.elapsedRealtime())
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

    private fun startDetectorSession() {
        if (!trainingActive) {
            return
        }
        stopDetectorSession()
        val listener = BoxingBleRuntime.HitListener { packet ->
            runOnUiThread {
                if (trainingActive) {
                    repeat(packet.hitDelta.coerceIn(1, 12)) {
                        gameView.registerPunch(1f, packet.hand)
                    }
                }
            }
        }
        val previewListener = BoxingBleRuntime.ImpactPreviewListener { packet ->
            runOnUiThread {
                if (trainingActive) {
                    gameView.previewPunch(packet.hand)
                }
            }
        }
        bleHitListener = listener
        bleImpactPreviewListener = previewListener
        BoxingBleRuntime.addHitListener(listener)
        BoxingBleRuntime.addImpactPreviewListener(previewListener)
        dispatchBleDetectorReady()
    }
    private fun stopDetectorSession() {
        bleHitListener?.let { BoxingBleRuntime.removeHitListener(it) }
        bleHitListener = null
        bleImpactPreviewListener?.let { BoxingBleRuntime.removeImpactPreviewListener(it) }
        bleImpactPreviewListener = null
        detectorJob?.cancel()
        detectorJob = null
    }

    private fun clearDetectorRecoveryState() {
        detectorRecoveryJob?.cancel()
        detectorRecoveryJob = null
        detectorRecoveryAttempts = 0
    }

    private fun scheduleDetectorRecovery(throwable: Throwable) {
        if (!trainingActive || isFinishing) {
            dispatchDetectorState(
                type = "error",
                message = throwable.message ?: freeText("蓝牙击中识别暂时不可用。", "Bluetooth hit detection is unavailable right now.", "La détection Bluetooth est momentanément indisponible.", "ระบบตรวจจับผ่านบลูทูธใช้งานไม่ได้ชั่วคราว"),
            )
            return
        }
        if (detectorRecoveryAttempts >= MAX_DETECTOR_RECOVERY_ATTEMPTS) {
            dispatchDetectorState(
                type = "error",
                message =
                    throwable.message
                        ?: freeText("蓝牙击中识别连续中断，请稍后重新开始训练。", "Bluetooth hit detection was interrupted repeatedly. Please restart the round.", "La détection Bluetooth a été interrompue à plusieurs reprises. Veuillez relancer la séance.", "การตรวจจับผ่านบลูทูธขาดตอนหลายครั้ง โปรดเริ่มการฝึกใหม่อีกครั้ง"),
            )
            return
        }
        detectorRecoveryAttempts += 1
        detectorRecoveryJob?.cancel()
        dispatchDetectorState(
            type = "error",
            message = freeText("蓝牙击中识别短暂中断，正在恢复。", "Bluetooth hit detection paused briefly. Recovering.", "La détection Bluetooth s'est brièvement arrêtée. Récupération.", "การตรวจจับผ่านบลูทูธหยุดชั่วคราว กำลังกู้คืน"),
        )
        val runId = trainingRunId
        detectorRecoveryJob =
            lifecycleScope.launch {
                delay(DETECTOR_RECOVERY_DELAY_MS)
                if (trainingActive && runId == trainingRunId) {
                    startDetectorSession()
                }
            }
    }

    private fun dispatchBleDetectorReady() {
        dispatchDetectorState(type = "ready", message = freeText("蓝牙击中识别已就绪。", "Bluetooth hit detection is ready."))
    }
    private fun dispatchDetectorState(
        type: String,
        message: String,
    ) {
        runOnUiThread {
            if (::gameView.isInitialized) {
                gameView.updateDetectorState(type)
            }
            if (::hintView.isInitialized && message.isNotBlank()) {
                hintView.text = message
                hintView.visibility =
                    if (trainingActive || launchCountdownActive || type == "permission_denied" || type == "error") {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
            }
        }
    }

    private fun renderIdleState() {
        updateDurationButtons()
        updateDifficultyButtons()
        updateTrainingUi()
        updateOverlay(lastSnapshot)
        refreshAggregatePanel()
        rootContainer.post { alignTopPanels() }
        updateTimerDisplay()
        if (::hintView.isInitialized) {
            hintView.text = freeText("选择时长和强度，听到“开始”后进入极速节奏。", "Choose a duration and difficulty. After GO, go all-in on cadence.", "Choisissez une durée et une intensité. Après le départ, entrez dans le rythme maximal.", "เลือกเวลาและความเข้มข้น หลังได้ยินคำว่าเริ่มให้เข้าสู่จังหวะเร่งเต็มที่")
            hintView.visibility = View.GONE
        }
    }

    private fun updateTrainingUi() {
        val showSelection = !trainingActive && !launchCountdownActive
        selectionControlsView.visibility = if (showSelection) View.VISIBLE else View.GONE
        energyPanelView.visibility = if (showSelection) View.VISIBLE else View.GONE
        statusBarView.visibility = if (trainingActive) View.VISIBLE else View.GONE
        startButtonView.visibility = if (showSelection) View.VISIBLE else View.GONE
        endButtonView.visibility = if (trainingActive || launchCountdownActive) View.VISIBLE else View.GONE
        hintView.visibility = if (trainingActive || launchCountdownActive) View.VISIBLE else View.GONE
    }

    private fun updateOverlay(snapshot: BlitzModeGameView.OverlaySnapshot) {
        if (!::hitsValueView.isInitialized) {
            return
        }
        hitsValueView.text = snapshot.hits.toString()
        comboValueView.text = snapshot.combo.toString()
        ppsValueView.text = formatDecimal(snapshot.pps, 1)
    }

    private fun refreshAggregatePanel() {
        if (!::todaySummaryValueView.isInitialized) {
            return
        }
        val now = System.currentTimeMillis()
        todaySummaryValueView.text = formatAggregateValue(loadAggregateValue(dayKey(now)))
        weekSummaryValueView.text = formatAggregateValue(loadAggregateValue(weekKey(now)))
        monthSummaryValueView.text = formatAggregateValue(loadAggregateValue(monthKey(now)))
    }

    private fun persistAggregateTotals(
        snapshot: BlitzModeGameView.OverlaySnapshot,
        durationSeconds: Int,
    ) {
        if (snapshot.hits <= 0 && snapshot.calories <= 0f && snapshot.fatBurnGrams <= 0f) {
            return
        }
        val now = System.currentTimeMillis()
        addAggregateValue(dayKey(now), snapshot.hits, snapshot.calories, snapshot.fatBurnGrams, durationSeconds)
        addAggregateValue(weekKey(now), snapshot.hits, snapshot.calories, snapshot.fatBurnGrams)
        addAggregateValue(monthKey(now), snapshot.hits, snapshot.calories, snapshot.fatBurnGrams)
    }

    private fun maybeUploadRoundToCloud(
        snapshot: BlitzModeGameView.OverlaySnapshot,
        durationSeconds: Int,
    ) {
        if (snapshot.hits <= 0 || durationSeconds <= 0) {
            return
        }
        CloudTrainingUploader.uploadIfAvailable(
            context = this,
            scope = lifecycleScope,
            report =
                CloudTrainingUploader.buildReport(
                    totalHits = snapshot.hits,
                    durationSeconds = durationSeconds,
                    averageFrequency = snapshot.pps,
                    bestBurstCount = snapshot.bestCombo,
                    preferredModeSeconds = (selectedDuration.durationMs / 1000L).toInt(),
                ),
        )
    }

    private fun addAggregateValue(
        key: String,
        hits: Int,
        calories: Float,
        fatBurnGrams: Float,
        durationSeconds: Int = 0,
    ) {
        val hitKey = "${key}_hits"
        val calorieKey = "${key}_calories"
        val fatKey = "${key}_fat"
        val durationKey = "${key}_duration_sec"
        val currentHits = aggregatePrefs.getInt(hitKey, 0)
        val currentCalories = aggregatePrefs.getFloat(calorieKey, 0f)
        val currentFat = aggregatePrefs.getFloat(fatKey, 0f)
        aggregatePrefs.edit()
            .putInt(hitKey, currentHits + hits.coerceAtLeast(0))
            .putFloat(calorieKey, currentCalories + calories)
            .putFloat(fatKey, currentFat + fatBurnGrams)
            .putInt(durationKey, aggregatePrefs.getInt(durationKey, 0) + durationSeconds.coerceAtLeast(0))
            .apply()
    }

    private fun currentSessionDurationSeconds(): Int =
        (((selectedDuration.durationMs - remainingTrainingMs).coerceAtLeast(0L)) / 1000L).toInt()

    private fun loadAggregateValue(key: String): AggregateValue =
        AggregateValue(
            calories = aggregatePrefs.getFloat("${key}_calories", 0f),
            fatBurnGrams = aggregatePrefs.getFloat("${key}_fat", 0f),
            hits = aggregatePrefs.getInt("${key}_hits", 0),
        )

    private fun formatAggregateValue(value: AggregateValue): CharSequence {
        val caloriesText = "${formatDecimal(value.calories, 1)} kcal"
        val fatText = "${formatDecimal(value.fatBurnGrams, 1)} g"
        val fullText = "$caloriesText  ·  $fatText"
        return SpannableString(fullText).apply {
            val fatStart = fullText.indexOf(fatText)
            if (fatStart >= 0) {
                setSpan(
                    ForegroundColorSpan(Color.parseColor("#FF5A5A")),
                    fatStart,
                    fatStart + fatText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    fatStart,
                    fatStart + fatText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    private fun alignTopPanels() {
        if (!::selectionControlsView.isInitialized || !::energyPanelView.isInitialized) {
            return
        }
        val alignedHeight = max(selectionControlsView.height, energyPanelView.height)
        if (alignedHeight <= 0) {
            return
        }
        selectionControlsView.minimumHeight = alignedHeight
        energyPanelView.minimumHeight = alignedHeight
        selectionControlsView.requestLayout()
        energyPanelView.requestLayout()
    }

    private fun dayKey(timestampMs: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestampMs }
        return String.format(
            Locale.US,
            "day_%04d%02d%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun weekKey(timestampMs: Long): String {
        val calendar =
            Calendar.getInstance().apply {
                timeInMillis = timestampMs
                firstDayOfWeek = Calendar.MONDAY
                minimalDaysInFirstWeek = 4
            }
        return String.format(
            Locale.US,
            "week_%04d_%02d",
            calendar.getWeekYear(),
            calendar.get(Calendar.WEEK_OF_YEAR),
        )
    }

    private fun monthKey(timestampMs: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestampMs }
        return String.format(
            Locale.US,
            "month_%04d%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
        )
    }

    private fun updateTimerDisplay() {
        if (!::timerValueView.isInitialized) {
            return
        }
        val remaining =
            if (trainingActive || launchCountdownActive) {
                remainingTrainingMs
            } else {
                selectedDuration.durationMs
            }
        val seconds = max(0L, remaining / 1_000L)
        timerValueView.text = String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L)
    }

    private fun difficultyText(): String =
        freeText(
            "训练强度：${selectedDifficulty.label(languageCode)}",
            "Difficulty: ${selectedDifficulty.label(languageCode)}",
            "Intensité : ${selectedDifficulty.label(languageCode)}",
            "ความเข้มข้น: ${selectedDifficulty.label(languageCode)}",
        )

    private fun formatDecimal(
        value: Float,
        decimals: Int,
    ): String =
        String.format(Locale.US, "%.${decimals}f", value)

    private fun freeText(
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

    private fun ttsLocaleForLanguage(): Locale =
        when {
            languageCode.startsWith("zh") -> Locale.CHINA
            languageCode.startsWith("fr") -> Locale.FRANCE
            languageCode.startsWith("th") -> Locale("th", "TH")
            else -> Locale.US
        }

    private fun roundedFill(
        fillColor: String,
        strokeColor: String,
        radiusDp: Int,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(fillColor))
            setStroke(dp(1), Color.parseColor(strokeColor))
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
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    private fun horizontalSpace(width: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(width, 1)
        }

    private fun verticalSpace(height: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, height)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SENSITIVITY_LEVEL = "blitz_mode_sensitivity"
        const val EXTRA_LANGUAGE = "blitz_mode_language"
        private const val MAX_DETECTOR_RECOVERY_ATTEMPTS = 3
        private const val DETECTOR_RECOVERY_DELAY_MS = 320L
    }
}











