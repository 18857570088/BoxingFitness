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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
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
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val fatBurnChallengeFrMap =
    mapOf(
        "Fat Burning Challenge" to "Défi Brûle-Graisse",
        "30-day structured HIT fat-burning boxing program" to "Programme de boxe HIT brûle-graisse structuré sur 30 jours",
        "TODAY'S COURSE" to "COURS DU JOUR",
        "PROGRESS" to "PROGRÈS",
        "Streak" to "Série",
        "This Week" to "Cette semaine",
        "This Month" to "Ce mois-ci",
        "30-Day Plan" to "Plan 30 jours",
        "Reports" to "Rapports",
        "Achievements" to "Succès",
        "Hits" to "Coups",
        "Combo" to "Combo",
        "Timer" to "Temps",
        "Open Report" to "Voir le rapport",
        "Back Home" to "Retour",
        "After GO, hit the moving targets fast." to "Après GO, frappez rapidement les cibles mobiles.",
        "Hit the moving targets quickly to raise pace and burn." to "Frappez rapidement les cibles mobiles pour augmenter le rythme et la dépense.",
        "Preparing hit detection. Stay ready." to "Préparation de la détection des coups. Restez prêt.",
        "Calibration complete. Punch whenever a target is on screen." to "Calibration terminée. Frappez dès qu'une cible apparaît à l'écran.",
        "30-Day Schedule" to "Programme 30 jours",
        "The plan ramps through adaptation, build, and consolidation phases." to "Le plan progresse par adaptation, renforcement et consolidation.",
        "Adapt 1-10" to "Adaptation 1-10",
        "Build 11-20" to "Renforcement 11-20",
        "Solidify 21-30" to "Consolidation 21-30",
        "Super-user plan testing is enabled. Tap any day to run the full flow." to "Le test complet du plan est activé pour le super-utilisateur. Touchez n'importe quel jour pour lancer le flux complet.",
        "Test" to "Test",
        "Done" to "Terminé",
        "Today" to "Aujourd'hui",
        "Catch-up" to "Rattrapage",
        "Planned" to "Prévu",
        "Start Another 30-Day Challenge" to "Lancer un nouveau défi de 30 jours",
        "Daily / Weekly / Monthly Summary" to "Bilan jour / semaine / mois",
        "Calories, fat burn, and weight trend are stored in local reports." to "Les calories, la combustion des graisses et l'évolution du poids sont enregistrées dans les rapports locaux.",
        "Recent 7-Day Trend" to "Tendance des 7 derniers jours",
        "Track recent completion, calories, and accuracy trends." to "Suivez la progression récente, les calories et la tendance d'exécution.",
        "Milestones" to "Jalons",
        "Turn long-term fat loss into visible small wins." to "Transformez la perte de graisse à long terme en petites victoires visibles.",
        "Completed" to "Jours réalisés",
        "Fat burn" to "Graisse brûlée",
        "Avg completion" to "Moy. réalisation",
        "Avg HPS" to "Moy. HPS",
        "Weight trend" to "Tendance du poids",
        "Rest" to "Repos",
        "Saved" to "Enregistré",
        "Unlocked" to "Déverrouillé",
        "Preview" to "Aperçu",
        "Course goal" to "Objectif du cours",
        "Replay Today's Class" to "Refaire le cours du jour",
        "Start Today's Class" to "Commencer le cours du jour",
        "Replay Test Day" to "Refaire le jour test",
        "Start Test Day" to "Commencer le jour test",
        "Start" to "Commencer",
        "Back" to "Retour",
        "Start Next Stage" to "Lancer l'étape suivante",
        "End Session" to "Terminer la séance",
        "Review today's class and start when ready." to "Consultez le cours du jour puis commencez quand vous êtes prêt.",
        "Adaptation" to "Adaptation",
        "Build" to "Renforcement",
        "Consolidation" to "Consolidation",
        "Rhythm Adapt" to "Adaptation au rythme",
        "Foundational Combo" to "Combo fondamental",
        "Short Burst" to "Sprint court",
        "Recovery Rhythm" to "Rythme de récupération",
        "Endurance Build" to "Construction d'endurance",
        "Beat Control" to "Contrôle du tempo",
        "Sustained Output" to "Sortie continue",
        "Recovery Technique" to "Technique de récupération",
        "Explosive Rhythm" to "Rythme explosif",
        "Stage Review" to "Bilan d'étape",
        "Power Endurance" to "Endurance de puissance",
        "Dual Burst" to "Double sprint",
        "High Frequency" to "Haute fréquence",
        "Load Boost" to "Hausse de charge",
        "Compressed Tempo" to "Tempo compressé",
        "Sustained Burst" to "Sprint continu",
        "Active Recovery" to "Récupération active",
        "Speed Consolidation" to "Consolidation de vitesse",
        "Final Review" to "Bilan final",
        "Consolidated Endurance" to "Endurance consolidée",
        "Speed Burst" to "Explosion de vitesse",
        "Steady Rhythm" to "Rythme stable",
        "Heavy Sprint" to "Sprint intensif",
        "Late Acceleration" to "Accélération finale",
        "Recovery Integration" to "Intégration de récupération",
        "Peak Challenge" to "Défi pic",
        "Fat Burn Session" to "Séance brûle-graisse",
        "Low Load" to "Charge légère",
        "Medium-High" to "Charge moyenne à élevée",
        "High Intensity" to "Haute intensité",
        "Reduce load and recover rhythm and breathing" to "Réduire la charge et récupérer le rythme et la respiration",
        "Measure endurance ceiling with a full-beat test" to "Mesurer le plafond d'endurance avec un test complet",
        "Build rhythm stability and basic tolerance" to "Construire une stabilité rythmique et une tolérance de base",
        "Raise sustained output and burst frequency" to "Augmenter la sortie continue et la fréquence de sprint",
        "Solidify endurance reserves and late-stage stability" to "Consolider l'endurance et la stabilité en fin de séance",
        "Stage 1 · Activate" to "Étape 1 · Activation",
        "Stage 2 · Build Pace" to "Étape 2 · Monter le rythme",
        "Stage 2 · Steady Burn" to "Étape 2 · Brûlage stable",
        "Stage 3 · Sprint Burn" to "Étape 3 · Sprint brûle-graisse",
        "Final · Hit Goal" to "Final · Atteindre l'objectif",
    )

private val fatBurnChallengeThMap =
    mapOf(
        "Fat Burning Challenge" to "ภารกิจเผาผลาญไขมัน",
        "30-day structured HIT fat-burning boxing program" to "โปรแกรมชกมวยเผาผลาญไขมันแบบ HIT 30 วัน",
        "TODAY'S COURSE" to "คอร์สวันนี้",
        "PROGRESS" to "ความคืบหน้า",
        "Streak" to "ต่อเนื่อง",
        "This Week" to "สัปดาห์นี้",
        "This Month" to "เดือนนี้",
        "30-Day Plan" to "แผน 30 วัน",
        "Reports" to "รายงาน",
        "Achievements" to "ความสำเร็จ",
        "Hits" to "ครั้งชก",
        "Combo" to "คอมโบ",
        "Timer" to "เวลา",
        "Open Report" to "ดูรายงาน",
        "Back Home" to "กลับหน้าแรก",
        "After GO, hit the moving targets fast." to "หลัง GO ให้ชกเป้าเคลื่อนที่อย่างรวดเร็ว",
        "Hit the moving targets quickly to raise pace and burn." to "ชกเป้าเคลื่อนที่ให้เร็วเพื่อเพิ่มจังหวะและการเผาผลาญ",
        "Preparing hit detection. Stay ready." to "กำลังเตรียมระบบตรวจจับหมัด โปรดเตรียมพร้อม",
        "Calibration complete. Punch whenever a target is on screen." to "ปรับเทียบเสร็จแล้ว ชกได้ทันทีเมื่อมีเป้าปรากฏบนหน้าจอ",
        "30-Day Schedule" to "ตาราง 30 วัน",
        "The plan ramps through adaptation, build, and consolidation phases." to "แผนจะค่อย ๆ เพิ่มจากช่วงปรับตัว เสริมสร้าง ไปจนถึงช่วงคงสภาพ",
        "Adapt 1-10" to "ปรับตัว 1-10",
        "Build 11-20" to "เสริมสร้าง 11-20",
        "Solidify 21-30" to "คงสภาพ 21-30",
        "Super-user plan testing is enabled. Tap any day to run the full flow." to "เปิดสิทธิ์ทดสอบทั้งแผนสำหรับผู้ใช้ระดับซูเปอร์แล้ว แตะวันใดก็ได้เพื่อทดสอบครบขั้นตอน",
        "Test" to "ทดสอบ",
        "Done" to "เสร็จแล้ว",
        "Today" to "วันนี้",
        "Catch-up" to "ชดเชย",
        "Planned" to "ตามแผน",
        "Start Another 30-Day Challenge" to "เริ่มภารกิจ 30 วันรอบใหม่",
        "Daily / Weekly / Monthly Summary" to "สรุปรายวัน / รายสัปดาห์ / รายเดือน",
        "Calories, fat burn, and weight trend are stored in local reports." to "แคลอรี การเผาผลาญไขมัน และแนวโน้มน้ำหนักจะถูกบันทึกไว้ในรายงานภายในเครื่อง",
        "Recent 7-Day Trend" to "แนวโน้ม 7 วันล่าสุด",
        "Track recent completion, calories, and accuracy trends." to "ติดตามความต่อเนื่อง แคลอรี และแนวโน้มการทำได้ในช่วงล่าสุด",
        "Milestones" to "หมุดหมาย",
        "Turn long-term fat loss into visible small wins." to "เปลี่ยนเป้าหมายลดไขมันระยะยาวให้เป็นชัยชนะเล็ก ๆ ที่มองเห็นได้",
        "Completed" to "วันที่ทำสำเร็จ",
        "Fat burn" to "เผาผลาญไขมัน",
        "Avg completion" to "ค่าเฉลี่ยการทำสำเร็จ",
        "Avg HPS" to "ค่าเฉลี่ย HPS",
        "Weight trend" to "แนวโน้มน้ำหนัก",
        "Rest" to "พัก",
        "Saved" to "บันทึกแล้ว",
        "Unlocked" to "ปลดล็อกแล้ว",
        "Preview" to "ตัวอย่าง",
        "Course goal" to "เป้าหมายคอร์ส",
        "Replay Today's Class" to "เล่นคอร์สวันนี้อีกครั้ง",
        "Start Today's Class" to "เริ่มคอร์สวันนี้",
        "Replay Test Day" to "เล่นวันทดสอบอีกครั้ง",
        "Start Test Day" to "เริ่มวันทดสอบ",
        "Start" to "เริ่ม",
        "Back" to "กลับ",
        "Start Next Stage" to "เริ่มช่วงถัดไป",
        "End Session" to "จบคอร์ส",
        "Review today's class and start when ready." to "ดูคอร์สวันนี้ก่อน แล้วเริ่มเมื่อพร้อม",
        "Adaptation" to "ปรับตัว",
        "Build" to "เสริมสร้าง",
        "Consolidation" to "คงสภาพ",
        "Rhythm Adapt" to "ปรับจังหวะ",
        "Foundational Combo" to "คอมโบพื้นฐาน",
        "Short Burst" to "เร่งสั้น",
        "Recovery Rhythm" to "ฟื้นฟูจังหวะ",
        "Endurance Build" to "เสริมความอึด",
        "Beat Control" to "คุมจังหวะ",
        "Sustained Output" to "ปล่อยหมัดต่อเนื่อง",
        "Recovery Technique" to "เทคนิคฟื้นฟู",
        "Explosive Rhythm" to "จังหวะระเบิดพลัง",
        "Stage Review" to "ประเมินช่วง",
        "Power Endurance" to "ความอึดเชิงพลัง",
        "Dual Burst" to "เร่งสองช่วง",
        "High Frequency" to "ความถี่สูง",
        "Load Boost" to "เพิ่มโหลด",
        "Compressed Tempo" to "จังหวะอัดแน่น",
        "Sustained Burst" to "เร่งต่อเนื่อง",
        "Active Recovery" to "ฟื้นฟูแบบแอคทีฟ",
        "Speed Consolidation" to "คงความเร็ว",
        "Final Review" to "ประเมินสุดท้าย",
        "Consolidated Endurance" to "ความอึดที่คงตัว",
        "Speed Burst" to "สปีดระเบิด",
        "Steady Rhythm" to "จังหวะนิ่ง",
        "Heavy Sprint" to "เร่งหนัก",
        "Late Acceleration" to "เร่งปลายทาง",
        "Recovery Integration" to "ฟื้นฟูผสาน",
        "Peak Challenge" to "ท้าทายสูงสุด",
        "Fat Burn Session" to "เซสชันเผาผลาญไขมัน",
        "Low Load" to "โหลดต่ำ",
        "Medium-High" to "โหลดกลางถึงสูง",
        "High Intensity" to "เข้มข้นสูง",
        "Reduce load and recover rhythm and breathing" to "ลดโหลดเพื่อฟื้นจังหวะและการหายใจ",
        "Measure endurance ceiling with a full-beat test" to "วัดขีดความอึดด้วยการทดสอบเต็มรูปแบบ",
        "Build rhythm stability and basic tolerance" to "สร้างความนิ่งของจังหวะและความทนทานพื้นฐาน",
        "Raise sustained output and burst frequency" to "เพิ่มการปล่อยหมัดต่อเนื่องและความถี่ของการเร่ง",
        "Solidify endurance reserves and late-stage stability" to "เสริมความอึดและความนิ่งช่วงท้าย",
        "Stage 1 · Activate" to "ช่วง 1 · ปลุกจังหวะ",
        "Stage 2 · Build Pace" to "ช่วง 2 · เพิ่มความเร็ว",
        "Stage 2 · Steady Burn" to "ช่วง 2 · เผาผลาญคงที่",
        "Stage 3 · Sprint Burn" to "ช่วง 3 · เร่งเผาผลาญ",
        "Final · Hit Goal" to "ช่วงสุดท้าย · ทำเป้าให้ครบ",
    )

private fun fatBurnChallengeTranslate(languageCode: String, en: String): String =
    when {
        languageCode.startsWith("fr") -> fatBurnChallengeFrMap[en] ?: en
        languageCode.startsWith("th") -> fatBurnChallengeThMap[en] ?: en
        else -> en
    }

private fun fatBurnChallengeText(
    languageCode: String,
    zh: String,
    en: String,
    fr: String? = null,
    th: String? = null,
): String =
    when {
        languageCode.startsWith("zh") -> zh
        languageCode.startsWith("fr") -> fr ?: fatBurnChallengeTranslate(languageCode, en)
        languageCode.startsWith("th") -> th ?: fatBurnChallengeTranslate(languageCode, en)
        else -> en
    }

private fun fatBurnChallengeLocale(languageCode: String): Locale =
    when {
        languageCode.startsWith("zh") -> Locale.CHINA
        languageCode.startsWith("fr") -> Locale.FRANCE
        languageCode.startsWith("th") -> Locale("th", "TH")
        else -> Locale.US
    }

class FatBurnChallengeActivity : AppCompatActivity(), BlitzModeGameView.Listener {
    private enum class DashboardTab {
        Plan,
        Report,
        Achievement,
    }

    private enum class ResultPopupMode {
        Preview,
        StageRest,
        Summary,
    }

    private data class ChallengeDayPlan(
        val dayIndex: Int,
        val phaseLabelZh: String,
        val phaseLabelEn: String,
        val titleZh: String,
        val titleEn: String,
        val focusZh: String,
        val focusEn: String,
        val intensityZh: String,
        val intensityEn: String,
        val warmupSec: Int,
        val roundCount: Int,
        val workSec: Int,
        val restSec: Int,
        val cooldownSec: Int,
        val bpm: Int,
        val loadScore: Int,
        val estimatedCalories: Float,
        val targetValidHits: Int,
    ) {
        val totalDurationSec: Int
            get() = warmupSec + cooldownSec + roundCount * workSec + (roundCount - 1).coerceAtLeast(0) * restSec

        fun phaseLabel(languageCode: String): String = fatBurnChallengeText(languageCode, phaseLabelZh, phaseLabelEn)

        fun title(languageCode: String): String = fatBurnChallengeText(languageCode, titleZh, titleEn)

        fun focus(languageCode: String): String = fatBurnChallengeText(languageCode, focusZh, focusEn)

        fun intensity(languageCode: String): String = fatBurnChallengeText(languageCode, intensityZh, intensityEn)

        fun targetHps(): Float = targetValidHits / totalDurationSec.toFloat().coerceAtLeast(1f)
    }

    private data class DayReport(
        val dayIndex: Int,
        val timestampMs: Long,
        val totalHits: Int,
        val validHits: Int,
        val missedBeats: Int,
        val accuracy: Float,
        val hps: Float,
        val calories: Float,
        val fatBurnGrams: Float,
        val durationSec: Int,
        val grade: String,
        val estimatedWeightKg: Float,
        val estimatedWaistDeltaCm: Float,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("dayIndex", dayIndex)
                .put("timestampMs", timestampMs)
                .put("totalHits", totalHits)
                .put("validHits", validHits)
                .put("missedBeats", missedBeats)
                .put("accuracy", accuracy.toDouble())
                .put("hps", hps.toDouble())
                .put("calories", calories.toDouble())
                .put("fatBurnGrams", fatBurnGrams.toDouble())
                .put("durationSec", durationSec)
                .put("grade", grade)
                .put("estimatedWeightKg", estimatedWeightKg.toDouble())
                .put("estimatedWaistDeltaCm", estimatedWaistDeltaCm.toDouble())

        companion object {
            fun fromJson(json: JSONObject): DayReport =
                DayReport(
                    dayIndex = json.optInt("dayIndex"),
                    timestampMs = json.optLong("timestampMs"),
                    totalHits = json.optInt("totalHits"),
                    validHits = json.optInt("validHits"),
                    missedBeats = json.optInt("missedBeats"),
                    accuracy = json.optDouble("accuracy").toFloat(),
                    hps = json.optDouble("hps").toFloat(),
                    calories = json.optDouble("calories").toFloat(),
                    fatBurnGrams = json.optDouble("fatBurnGrams").toFloat(),
                    durationSec = json.optInt("durationSec"),
                    grade = json.optString("grade", "B"),
                    estimatedWeightKg = json.optDouble("estimatedWeightKg", DEFAULT_BASE_WEIGHT_KG.toDouble()).toFloat(),
                    estimatedWaistDeltaCm = json.optDouble("estimatedWaistDeltaCm", 0.0).toFloat(),
                )
        }
    }

    private data class AggregateWindow(
        val completedDays: Int,
        val calories: Float,
        val fatBurnGrams: Float,
        val avgAccuracy: Float,
        val avgHps: Float,
        val estimatedWeightKg: Float,
        val estimatedWaistDeltaCm: Float,
    )

    private data class PlannedCourseCumulative(
        val calories: Float,
        val fatBurnGrams: Float,
    )

    private data class AchievementMilestone(
        val titleZh: String,
        val titleEn: String,
        val descriptionZh: String,
        val descriptionEn: String,
        val unlocked: Boolean,
        val progress: Float,
        val progressLabel: String,
    ) {
        fun title(languageCode: String): String = fatBurnChallengeText(languageCode, titleZh, titleEn)

        fun description(languageCode: String): String = fatBurnChallengeText(languageCode, descriptionZh, descriptionEn)
    }

    private lateinit var rootContainer: FrameLayout
    private lateinit var gameView: BlitzModeGameView
    private lateinit var dashboardScrollView: ScrollView
    private lateinit var dashboardColumn: LinearLayout
    private lateinit var contentContainer: LinearLayout
    private lateinit var statusBarView: View
    private lateinit var backButtonView: View
    private lateinit var hintView: TextView
    private lateinit var countdownView: TextView
    private lateinit var startButtonView: TextView
    private lateinit var endButtonView: TextView
    private lateinit var resultPopupView: View
    private lateinit var resultTitleView: TextView
    private lateinit var resultBodyView: TextView
    private lateinit var resultPrimaryButtonView: TextView
    private lateinit var resultSecondaryButtonView: TextView

    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var todayDayView: TextView
    private lateinit var todayThemeView: TextView
    private lateinit var todayMetaView: TextView
    private lateinit var todayLoadView: TextView
    private lateinit var todayCumulativeView: TextView
    private lateinit var streakValueView: TextView
    private lateinit var weekValueView: TextView
    private lateinit var monthValueView: TextView

    private lateinit var totalHitsValueView: TextView
    private lateinit var comboValueView: TextView
    private lateinit var ppsValueView: TextView
    private lateinit var timerValueView: TextView

    private val tabButtons = LinkedHashMap<DashboardTab, TextView>()

    private var selectedTab = DashboardTab.Plan
    private var trainingActive = false
    private var stageRestActive = false
    private var launchCountdownActive = false
    private var pendingStartAfterPermission = false
    private var remainingTrainingMs = 0L
    private var currentPlan = generatePlanForDay(1)
    private var selectedPlanDayIndex: Int? = null
    private var lastSnapshot = BlitzModeGameView.OverlaySnapshot(0, 0, 0, 0f, 0f, 0f)
    private var resultPopupMode = ResultPopupMode.Summary
    private var lastDetectorStateType = "loading"
    private var detectorRecoveryAttempts = 0
    private var trainingRunId = 0L
    private var speechEngine: TextToSpeech? = null
    private var speechReady = false
    private var courseStages: List<CourseStage> = emptyList()
    private var currentStageIndex = 0
    private var courseAccumulatedHits = 0
    private var courseAccumulatedCalories = 0f
    private var courseAccumulatedFatBurn = 0f
    private var courseBestCombo = 0
    private var courseAccumulatedTrainingMs = 0L
    private var currentStageStartedAtMs = 0L

    private var detectorJob: Job? = null
    private var bleHitListener: BoxingBleRuntime.HitListener? = null
    private var bleImpactPreviewListener: BoxingBleRuntime.ImpactPreviewListener? = null
    private var detectorRecoveryJob: Job? = null
    private var countdownJob: Job? = null
    private var launchCountdownJob: Job? = null
    private var stageTransitionJob: Job? = null

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private val sensitivityLevel by lazy {
        intent.getIntExtra(EXTRA_SENSITIVITY_LEVEL, 50)
    }
    private val languageCode by lazy {
        intent.getStringExtra(EXTRA_LANGUAGE).orEmpty().ifBlank { "zh" }
    }
    private val authSerial by lazy {
        intent.getStringExtra(EXTRA_AUTH_SERIAL).orEmpty().ifBlank { prefs.getString(KEY_AUTH_SERIAL, null).orEmpty() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (authSerial.isNotBlank()) {
            prefs.edit().putString(KEY_AUTH_SERIAL, authSerial).apply()
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContentView(buildContentView())
        hideSystemBars()
        initSpeechEngine()
        refreshDashboard()
        renderIdleState()
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
        super.onPause()
    }

    override fun onDestroy() {
        if (trainingActive || launchCountdownActive || stageRestActive) {
            BoxingBleRuntime.disableGyro()
        }
        cancelLaunchCountdown(showMessage = false)
        pauseTrainingCountdown()
        stopDetectorSession()
        clearDetectorRecoveryState()
        speechEngine?.stop()
        speechEngine?.shutdown()
        speechEngine = null
        speechReady = false
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onOverlaySnapshot(snapshot: BlitzModeGameView.OverlaySnapshot) {
        lastSnapshot = snapshot
        if (!::totalHitsValueView.isInitialized) {
            return
        }
        totalHitsValueView.text = (courseAccumulatedHits + snapshot.hits).toString()
        comboValueView.text = snapshot.combo.toString()
        ppsValueView.text = formatDecimal(snapshot.pps, 2)
        if (trainingActive) {
            updateTimerDisplay()
            if (isGoalStage() && currentGoalRemainingHits(snapshot) <= 0) {
                finishTrainingSession(manual = false)
            }
        }
    }

    override fun onHintChanged(hint: String) {
        hintView.text =
            when {
                launchCountdownActive -> text("倒计时结束后，快速击打移动拳靶。", "After GO, hit the moving targets fast.")
                trainingActive && lastDetectorStateType == "ready" -> text("快速击打移动拳靶，提高频率与燃脂效率。", "Hit the moving targets quickly to raise pace and burn.")
                trainingActive -> text("正在准备蓝牙击中识别，请保持出拳准备。", "Preparing hit detection. Stay ready.")
                else -> hint
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

        currentPlan = todayPlan()
        gameView =
            BlitzModeGameView(this).apply {
                setLanguageCode(languageCode)
                setDifficultyPreset(currentPlan.toBlitzDifficultyPreset())
                visibility = View.GONE
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
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }

        backButtonView = buildBackButton()
        overlay.addView(
            backButtonView,
            FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(6)
                topMargin = dp(6)
            },
        )

        dashboardScrollView =
            ScrollView(this).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
            }
        dashboardColumn =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(72), dp(18), dp(16), dp(16))
            }
        dashboardScrollView.addView(
            dashboardColumn,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        buildDashboardContents()
        overlay.addView(
            dashboardScrollView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        statusBarView = buildStatusBar()
        overlay.addView(
            statusBarView,
            FrameLayout.LayoutParams(
                dp(470),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply {
                topMargin = dp(18)
            },
        )

        endButtonView =
            commandButton(text("结束", "End"), fillColor = "#B93535", strokeColor = "#FF9186").apply {
                setOnClickListener { finishTrainingSession(manual = true) }
            }
        overlay.addView(
            endButtonView,
            FrameLayout.LayoutParams(
                dp(100),
                dp(44),
                Gravity.BOTTOM or Gravity.START,
            ).apply {
                leftMargin = dp(10)
                bottomMargin = dp(10)
            },
        )

        hintView =
            TextView(this).apply {
                setTextColor(Color.parseColor("#F2F7FB"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(10), dp(18), dp(10))
                background = roundedFill("#4D0C2232", "#4DAEE8FF", 18)
            }
        overlay.addView(
            hintView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                bottomMargin = dp(18)
            },
        )

        countdownView =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 54f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setShadowLayer(16f, 0f, 0f, Color.parseColor("#AAFF7A59"))
            }
        overlay.addView(
            countdownView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        resultPopupView = buildResultPopup()
        overlay.addView(
            resultPopupView,
            FrameLayout.LayoutParams(
                dp(520),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        overlay.bringChildToFront(backButtonView)

        rootContainer.addView(overlay)
        gameView.bind(this)
        return rootContainer
    }

    private fun buildDashboardContents() {
        titleView =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                text = text("燃脂挑战", "Fat Burning Challenge")
            }
        dashboardColumn.addView(titleView)

        subtitleView =
            TextView(this).apply {
                setTextColor(Color.parseColor("#D0E8F5"))
                textSize = 13.5f
                text = text("30天结构化HIT燃脂拳击锻炼计划", "30-day structured HIT fat-burning boxing program")
            }
        dashboardColumn.addView(subtitleView)
        dashboardColumn.addView(verticalSpace(12))

        val heroRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
        heroRow.addView(
            buildTodayCard(),
            LinearLayout.LayoutParams(dp(322), dp(190)),
        )
        heroRow.addView(horizontalSpace(12))
        heroRow.addView(
            buildSummaryCard(),
            LinearLayout.LayoutParams(dp(214), dp(190)),
        )
        dashboardColumn.addView(heroRow)
        dashboardColumn.addView(verticalSpace(12))

        val actionRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        startButtonView =
            commandButton(text("开始今日课程", "Start Today's Class"), fillColor = "#FF7A59", strokeColor = "#FFD79A").apply {
                setOnClickListener { requestTrainingStart() }
            }
        actionRow.addView(
            startButtonView,
            LinearLayout.LayoutParams(dp(214), dp(58)),
        )
        dashboardColumn.addView(actionRow)
        dashboardColumn.addView(verticalSpace(12))

        dashboardColumn.addView(buildTabBar())
        dashboardColumn.addView(verticalSpace(10))

        contentContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedFill("#CC0B1B27", "#345E7A", 22)
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
        dashboardColumn.addView(
            contentContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun buildTodayCard(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill("#D9122332", "#4E7996", 24)
            setPadding(dp(18), dp(16), dp(18), dp(16))

            addView(
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.parseColor("#9FE6FF"))
                    textSize = 12f
                    text = text("TODAY'S COURSE", "TODAY'S COURSE")
                },
            )
            todayDayView =
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.WHITE)
                    textSize = 24f
                    typeface = Typeface.DEFAULT_BOLD
                }
            addView(todayDayView)
            todayThemeView =
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.parseColor("#FFE6BF"))
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                }
            addView(todayThemeView)
            todayMetaView =
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.parseColor("#D9ECF7"))
                    textSize = 13f
                    setPadding(0, dp(8), 0, 0)
                }
            addView(todayMetaView)
            todayLoadView =
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.parseColor("#FFCE7A"))
                    textSize = 13f
                    setPadding(0, dp(8), 0, 0)
                }
            addView(todayLoadView)
            todayCumulativeView =
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.parseColor("#FF8D73"))
                    textSize = 12.5f
                    setPadding(0, dp(6), 0, 0)
                }
            addView(todayCumulativeView)
        }

    private fun buildSummaryCard(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill("#D90E1821", "#45667E", 24)
            setPadding(dp(16), dp(14), dp(16), dp(14))

            addView(
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.parseColor("#9FE6FF"))
                    textSize = 12f
                    text = text("PROGRESS", "PROGRESS")
                },
            )

            streakValueView = summaryMetric(text("连续打卡", "Streak"))
            addView(streakValueView)
            weekValueView = summaryMetric(text("本周消耗", "This Week"))
            addView(weekValueView)
            monthValueView = summaryMetric(text("本月燃脂", "This Month"))
            addView(monthValueView)
        }

    private fun summaryMetric(label: String): TextView =
        TextView(this).apply {
            setTextColor(Color.parseColor("#F5FBFF"))
            textSize = 13f
            setPadding(0, dp(10), 0, 0)
            text = "$label --"
        }

    private fun buildTabBar(): View =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(
                LinearLayout(this@FatBurnChallengeActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    listOf(
                        DashboardTab.Plan to text("30天计划", "30-Day Plan"),
                        DashboardTab.Report to text("报告汇总", "Reports"),
                        DashboardTab.Achievement to text("里程碑成就", "Achievements"),
                    ).forEachIndexed { index, (tab, label) ->
                        val chip =
                            TextView(this@FatBurnChallengeActivity).apply {
                                minWidth = dp(112)
                                gravity = Gravity.CENTER
                                text = label
                                textSize = 13f
                                setPadding(dp(16), dp(10), dp(16), dp(10))
                                setOnClickListener {
                                    selectedTab = tab
                                    refreshTabButtons()
                                    rebuildTabContent()
                                    resetDashboardScrollPosition()
                                }
                            }
                        tabButtons[tab] = chip
                        addView(chip)
                        if (index < 2) {
                            addView(horizontalSpace(8))
                        }
                    }
                },
            )
        }

    private fun buildStatusBar(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedFill("#D91B2A39", "#5FB7D8", 22)
            setPadding(dp(16), dp(9), dp(16), dp(9))

            val labelsRow =
                LinearLayout(this@FatBurnChallengeActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
            val valuesRow =
                LinearLayout(this@FatBurnChallengeActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, dp(3), 0, 0)
                }

            fun addMetric(label: String, bindValue: (TextView) -> Unit) {
                labelsRow.addView(
                    TextView(this@FatBurnChallengeActivity).apply {
                        setTextColor(Color.parseColor("#9AD5EA"))
                        textSize = 10.5f
                        gravity = Gravity.CENTER
                        text = label
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                valuesRow.addView(
                    TextView(this@FatBurnChallengeActivity).apply {
                        setTextColor(Color.WHITE)
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        text = "--"
                        bindValue(this)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
            }

            addMetric(text("击中", "Hits")) { totalHitsValueView = it }
            addMetric(text("连击", "Combo")) { comboValueView = it }
            addMetric(text("PPS", "PPS")) { ppsValueView = it }
            addMetric(text("倒计时", "Timer")) { timerValueView = it }

            addView(labelsRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(valuesRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun buildResultPopup(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            background = roundedFill("#F20E1A24", "#74D4FF", 24)
            setPadding(dp(22), dp(22), dp(22), dp(20))

            resultTitleView =
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.WHITE)
                    textSize = 24f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
            addView(resultTitleView)

            resultBodyView =
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.parseColor("#E6F4FF"))
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(14), 0, dp(16))
                }
            addView(resultBodyView)

            val buttons =
                LinearLayout(this@FatBurnChallengeActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
            resultPrimaryButtonView =
                commandButton(text("查看日报", "Open Report"), fillColor = "#FF6B4A", strokeColor = "#FFD67B").apply {
                    setOnClickListener {
                        hideTrainingSummaryPopup()
                        selectedTab = DashboardTab.Report
                        refreshTabButtons()
                        refreshDashboard()
                        resetDashboardScrollPosition()
                        renderIdleState()
                    }
                }
            buttons.addView(
                resultPrimaryButtonView,
                LinearLayout.LayoutParams(dp(156), dp(48)),
            )
            buttons.addView(horizontalSpace(10))
            resultSecondaryButtonView =
                commandButton(text("返回首页", "Back Home"), fillColor = "#0D2C3F", strokeColor = "#73CAF2").apply {
                    setOnClickListener {
                        hideTrainingSummaryPopup()
                        selectedTab = DashboardTab.Plan
                        refreshTabButtons()
                        refreshDashboard()
                        resetDashboardScrollPosition()
                        renderIdleState()
                    }
                }
            buttons.addView(
                resultSecondaryButtonView,
                LinearLayout.LayoutParams(dp(156), dp(48)),
            )
            addView(buttons)
        }

    private fun buildBackButton(): View =
        TextView(this).apply {
            text = "←"
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(Color.WHITE)
            background = roundedFill("#B3122432", "#66C8F5", 20)
            setOnClickListener { finish() }
        }

    private fun refreshDashboard() {
        currentPlan = todayPlan()
        gameView.setDifficultyPreset(currentPlan.toBlitzDifficultyPreset())
        refreshHomeCards()
        refreshTabButtons()
        rebuildTabContent()
        updateStartButton()
    }

    private fun refreshHomeCards() {
        val reports = loadReports()
        val weekAggregate = aggregateWindow(reports.filter { sameWeek(it.timestampMs, System.currentTimeMillis()) })
        val monthAggregate = aggregateWindow(reports.filter { sameMonth(it.timestampMs, System.currentTimeMillis()) })
        val streak = currentStreakDays(reports)
        val plannedCumulative = plannedCourseCumulative(currentPlan.dayIndex)

        titleView.text = text("燃脂挑战", "Fat Burning Challenge")
        subtitleView.text = text("30天结构化HIT燃脂拳击锻炼计划", "30-day structured HIT fat-burning boxing program")

        todayDayView.text =
            text(
                if (isTestingChallengeDay()) "测试第${currentPlan.dayIndex}天 / 30 · ${currentPlan.phaseLabel(languageCode)}" else "第${currentPlan.dayIndex}天 / 30 · ${currentPlan.phaseLabel(languageCode)}",
                if (isTestingChallengeDay()) "Test Day ${currentPlan.dayIndex} / 30 · ${currentPlan.phaseLabel(languageCode)}" else "Day ${currentPlan.dayIndex} / 30 · ${currentPlan.phaseLabel(languageCode)}",
                if (isTestingChallengeDay()) "Jour test ${currentPlan.dayIndex} / 30 · ${currentPlan.phaseLabel(languageCode)}" else "Jour ${currentPlan.dayIndex} / 30 · ${currentPlan.phaseLabel(languageCode)}",
                if (isTestingChallengeDay()) "วันทดสอบ ${currentPlan.dayIndex} / 30 · ${currentPlan.phaseLabel(languageCode)}" else "วันที่ ${currentPlan.dayIndex} / 30 · ${currentPlan.phaseLabel(languageCode)}",
            )
        todayThemeView.text = currentPlan.title(languageCode)
        todayMetaView.text =
            text(
                "${currentPlan.focus(languageCode)}\n${formatDuration(currentPlan.totalDurationSec)} · ${currentPlan.roundCount}轮 · ${currentPlan.workSec}秒冲刺 / ${currentPlan.restSec}秒恢复",
                "${currentPlan.focus(languageCode)}\n${formatDuration(currentPlan.totalDurationSec)} · ${currentPlan.roundCount} rounds · ${currentPlan.workSec}s work / ${currentPlan.restSec}s rest",
                "${currentPlan.focus(languageCode)}\n${formatDuration(currentPlan.totalDurationSec)} · ${currentPlan.roundCount} manches · ${currentPlan.workSec}s effort / ${currentPlan.restSec}s repos",
                "${currentPlan.focus(languageCode)}\n${formatDuration(currentPlan.totalDurationSec)} · ${currentPlan.roundCount} รอบ · เร่ง ${currentPlan.workSec} วินาที / พัก ${currentPlan.restSec} วินาที",
            )
        todayLoadView.text =
            text(
                "${currentPlan.intensity(languageCode)} · ${currentPlan.bpm} BPM · 目标 ${currentPlan.targetValidHits} 击 · ${formatDecimal(currentPlan.estimatedCalories, 0)} kcal",
                "${currentPlan.intensity(languageCode)} · ${currentPlan.bpm} BPM · Target ${currentPlan.targetValidHits} hits · ${formatDecimal(currentPlan.estimatedCalories, 0)} kcal",
                "${currentPlan.intensity(languageCode)} · ${currentPlan.bpm} BPM · Objectif ${currentPlan.targetValidHits} coups · ${formatDecimal(currentPlan.estimatedCalories, 0)} kcal",
                "${currentPlan.intensity(languageCode)} · ${currentPlan.bpm} BPM · เป้าหมาย ${currentPlan.targetValidHits} ครั้ง · ${formatDecimal(currentPlan.estimatedCalories, 0)} kcal",
            )
        todayCumulativeView.text =
            text(
                "计划累计 ${formatDecimal(plannedCumulative.calories, 0)} kcal · 累计燃脂 ${formatDecimal(plannedCumulative.fatBurnGrams, 1)} g",
                "Planned total ${formatDecimal(plannedCumulative.calories, 0)} kcal · ${formatDecimal(plannedCumulative.fatBurnGrams, 1)} g fat",
                "Cumul prévu ${formatDecimal(plannedCumulative.calories, 0)} kcal · ${formatDecimal(plannedCumulative.fatBurnGrams, 1)} g de graisse",
                "สะสมตามแผน ${formatDecimal(plannedCumulative.calories, 0)} kcal · เผาผลาญสะสม ${formatDecimal(plannedCumulative.fatBurnGrams, 1)} g",
            )

        streakValueView.text = text("连续打卡", "Streak", "Série", "ต่อเนื่อง") + " " + text("${streak}天", "${streak} days", "${streak} jours", "${streak} วัน")
        weekValueView.text = text("本周消耗", "This Week", "Cette semaine", "สัปดาห์นี้") + " ${formatDecimal(weekAggregate.calories, 0)} kcal"
        monthValueView.text = text("本月燃脂", "This Month", "Ce mois-ci", "เดือนนี้") + " ${formatDecimal(monthAggregate.fatBurnGrams, 1)} g"
    }

    private fun refreshTabButtons() {
        tabButtons.forEach { (tab, chip) ->
            val selected = selectedTab == tab
            chip.setTextColor(Color.parseColor(if (selected) "#081018" else "#D9EEF8"))
            chip.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            chip.background =
                if (selected) {
                    roundedFill("#FFD88E", "#FFF3D0", 18)
                } else {
                    roundedFill("#18273A", "#34536B", 18)
                }
        }
    }

    private fun rebuildTabContent() {
        contentContainer.removeAllViews()
        when (selectedTab) {
            DashboardTab.Plan -> buildPlanTab()
            DashboardTab.Report -> buildReportTab()
            DashboardTab.Achievement -> buildAchievementTab()
        }
    }

    private fun buildPlanTab() {
        contentContainer.addView(sectionHeader(text("30 天课程排期", "30-Day Schedule"), text("系统按适应期、强化期、巩固期科学推进训练负荷。", "The plan ramps through adaptation, build, and consolidation phases.")))
        contentContainer.addView(verticalSpace(10))

        val phaseRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        listOf(
            text("适应期 1-10", "Adapt 1-10") to "#6BD4FF",
            text("强化期 11-20", "Build 11-20") to "#FFC669",
            text("巩固期 21-30", "Solidify 21-30") to "#FF7E5E",
        ).forEachIndexed { index, (label, color) ->
            phaseRow.addView(metricPill(label, color))
            if (index < 2) {
                phaseRow.addView(horizontalSpace(8))
            }
        }
        contentContainer.addView(phaseRow)
        contentContainer.addView(verticalSpace(12))

        if (canTestAnyPlanDay()) {
            contentContainer.addView(
                TextView(this).apply {
                    setTextColor(Color.parseColor("#FFD58A"))
                    textSize = 12f
                    text = text("超级用户已开启全计划测试权限，可直接点任意天进入完整流程。", "Super-user plan testing is enabled. Tap any day to run the full flow.")
                },
            )
            contentContainer.addView(verticalSpace(10))
        }

        val todayIndex = currentDayIndex()
        val activeDayIndex = activePlanDayIndex()
        (1..30).forEach { day ->
            val plan = generatePlanForDay(day)
            val status =
                when {
                    canTestAnyPlanDay() && day == activeDayIndex && day != todayIndex -> text("测试课程", "Test")
                    day < todayIndex && loadReportForDay(day) != null -> text("已完成", "Done")
                    day == todayIndex -> text("今日课程", "Today")
                    day < todayIndex -> text("待补做", "Catch-up")
                    else -> text("计划中", "Planned")
                }
            val row = planRow(plan, status, day == activeDayIndex, plannedCourseCumulative(plan.dayIndex))
            if (canTestAnyPlanDay()) {
                row.isClickable = true
                row.isFocusable = true
                row.setOnClickListener {
                    selectedPlanDayIndex = if (day == todayIndex) null else day
                    refreshDashboard()
                    requestTrainingStart()
                }
            }
            contentContainer.addView(row)
            contentContainer.addView(verticalSpace(8))
        }
        contentContainer.addView(verticalSpace(10))
        contentContainer.addView(
            commandButton(
                text("再来一轮30天燃脂挑战", "Start Another 30-Day Challenge"),
                fillColor = "#FF7A59",
                strokeColor = "#FFD79A",
            ).apply {
                setOnClickListener { showRestartProgramDialog() }
            },
            LinearLayout.LayoutParams(dp(276), dp(58)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
    }

    private data class ChallengeSummary(
        val totalHits: Int,
        val validHits: Int,
        val missedBeats: Int,
        val accuracy: Float,
        val hps: Float,
    )

    private data class CourseStage(
        val titleZh: String,
        val titleEn: String,
        val durationSec: Int?,
        val restSecAfter: Int,
        val difficulty: BlitzModeGameView.DifficultyPreset,
    ) {
        fun title(languageCode: String): String = if (languageCode.startsWith("zh")) titleZh else titleEn
    }

    private fun buildReportTab() {
        val reports = loadReports()
        val todayReport = loadReportForDay(currentPlan.dayIndex)
        val dayAggregate = aggregateWindow(todayReport?.let { listOf(it) } ?: emptyList())
        val weekAggregate = aggregateWindow(reports.filter { sameWeek(it.timestampMs, System.currentTimeMillis()) })
        val monthAggregate = aggregateWindow(reports.filter { sameMonth(it.timestampMs, System.currentTimeMillis()) })

        contentContainer.addView(sectionHeader(text("日 / 周 / 月汇总", "Daily / Weekly / Monthly Summary"), text("卡路里、燃脂量与体重趋势都会沉淀到本地报告。", "Calories, fat burn, and weight trend are stored in local reports.")))
        contentContainer.addView(verticalSpace(12))

        contentContainer.addView(aggregateCard(text("今日", "Today"), dayAggregate, highlight = todayReport?.grade ?: "--"))
        contentContainer.addView(verticalSpace(10))
        contentContainer.addView(aggregateCard(text("本周", "This Week"), weekAggregate, highlight = "${reports.count { sameWeek(it.timestampMs, System.currentTimeMillis()) }} ${text("天", "days")}"))
        contentContainer.addView(verticalSpace(10))
        contentContainer.addView(aggregateCard(text("本月", "This Month"), monthAggregate, highlight = "${reports.count { sameMonth(it.timestampMs, System.currentTimeMillis()) }} / 30"))

        contentContainer.addView(verticalSpace(14))
        contentContainer.addView(sectionHeader(text("最近 7 天趋势", "Recent 7-Day Trend"), text("观察日课完成情况、卡路里和命中率的连续变化。", "Track recent completion, calories, and accuracy trends.")))
        contentContainer.addView(verticalSpace(10))
        buildRecentTrendRows(reports).forEachIndexed { index, view ->
            contentContainer.addView(view)
            if (index < 6) {
                contentContainer.addView(verticalSpace(8))
            }
        }
    }

    private fun buildAchievementTab() {
        val milestones = buildAchievements(loadReports())
        contentContainer.addView(sectionHeader(text("里程碑成就", "Milestones"), text("把减脂目标拆成一连串可感知的小胜利。", "Turn long-term fat loss into visible small wins.")))
        contentContainer.addView(verticalSpace(12))
        milestones.forEachIndexed { index, milestone ->
            contentContainer.addView(achievementCard(milestone))
            if (index < milestones.lastIndex) {
                contentContainer.addView(verticalSpace(10))
            }
        }
    }

    private fun sectionHeader(
        title: String,
        subtitle: String,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    text = title
                },
            )
            addView(
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.parseColor("#AFD0E0"))
                    textSize = 12.5f
                    setPadding(0, dp(4), 0, 0)
                    text = subtitle
                },
            )
        }

    private fun metricPill(
        label: String,
        color: String,
    ): View =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0B1218"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundedFill(color, color, 16)
        }

    private fun planRow(
        plan: ChallengeDayPlan,
        status: String,
        highlight: Boolean,
        cumulative: PlannedCourseCumulative,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill(if (highlight) "#1C3241" else "#131E28", if (highlight) "#8EE6FF" else "#304655", 18)
            setPadding(dp(14), dp(12), dp(14), dp(12))

            val topRow =
                LinearLayout(this@FatBurnChallengeActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        TextView(this@FatBurnChallengeActivity).apply {
                            setTextColor(Color.WHITE)
                            textSize = 15f
                            typeface = Typeface.DEFAULT_BOLD
                            text = text("第${plan.dayIndex}天 · ${plan.title(languageCode)}", "Day ${plan.dayIndex} · ${plan.title(languageCode)}")
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(metricPill(status, if (highlight) "#FFD68A" else "#66C7F2"))
                }
            addView(topRow)

            addView(
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.parseColor("#CAE8F8"))
                    textSize = 12.5f
                    setPadding(0, dp(8), 0, 0)
                        text =
                            text(
                                "${plan.phaseLabel(languageCode)} · ${plan.roundCount}轮 · ${plan.workSec}秒冲刺 / ${plan.restSec}秒恢复 · ${plan.bpm} BPM",
                                "${plan.phaseLabel(languageCode)} · ${plan.roundCount} rounds · ${plan.workSec}s work / ${plan.restSec}s recover · ${plan.bpm} BPM",
                            )
                },
            )
            addView(
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.parseColor("#F5C46D"))
                    textSize = 12f
                    setPadding(0, dp(6), 0, 0)
                        text =
                            text(
                                "${plan.focus(languageCode)} · ${formatDuration(plan.totalDurationSec)} · 负荷 ${plan.loadScore}/10 · ${formatDecimal(plan.estimatedCalories, 0)} kcal",
                                "${plan.focus(languageCode)} · ${formatDuration(plan.totalDurationSec)} · Load ${plan.loadScore}/10 · ${formatDecimal(plan.estimatedCalories, 0)} kcal",
                            )
                },
            )
            addView(
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.parseColor("#FF8D73"))
                    textSize = 12f
                    setPadding(0, dp(6), 0, 0)
                    text =
                        text(
                            "计划累计 ${formatDecimal(cumulative.calories, 0)} kcal · 累计燃脂 ${formatDecimal(cumulative.fatBurnGrams, 1)} g",
                            "Planned total ${formatDecimal(cumulative.calories, 0)} kcal · ${formatDecimal(cumulative.fatBurnGrams, 1)} g fat",
                        )
                },
            )
        }

    private fun aggregateCard(
        title: String,
        aggregate: AggregateWindow,
        highlight: String,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill("#122330", "#35556E", 20)
            setPadding(dp(14), dp(12), dp(14), dp(12))

            val topRow =
                LinearLayout(this@FatBurnChallengeActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        TextView(this@FatBurnChallengeActivity).apply {
                            setTextColor(Color.WHITE)
                            textSize = 16f
                            typeface = Typeface.DEFAULT_BOLD
                            text = title
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(metricPill(highlight, "#FFB35A"))
                }
            addView(topRow)
            addView(verticalSpace(8))
            addView(aggregateInfoRow(text("完成天数", "Completed"), "${aggregate.completedDays}"))
            addView(aggregateInfoRow("Calories", "${formatDecimal(aggregate.calories, 0)} kcal"))
            addView(aggregateInfoRow(text("燃脂", "Fat burn"), "${formatDecimal(aggregate.fatBurnGrams, 1)} g"))
            addView(aggregateInfoRow(text("平均达成率", "Avg completion"), "${(aggregate.avgAccuracy * 100f).roundToInt()}%"))
            addView(aggregateInfoRow(text("平均 HPS", "Avg HPS"), formatDecimal(aggregate.avgHps, 2)))
            addView(aggregateInfoRow(text("体重趋势", "Weight trend"), "${formatDecimal(aggregate.estimatedWeightKg, 1)} kg"))
        }

    private fun aggregateInfoRow(
        label: String,
        value: String,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            addView(
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.parseColor("#9DD2E8"))
                    textSize = 12f
                    text = label
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.WHITE)
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    text = value
                },
            )
        }

    private fun buildRecentTrendRows(reports: List<DayReport>): List<View> {
        val rows = ArrayList<View>()
        val today = Calendar.getInstance()
        repeat(7) { index ->
            val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(6 - index)) }
            val report = reports.firstOrNull { sameDay(it.timestampMs, day.timeInMillis) }
            rows +=
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = roundedFill("#10202B", "#274052", 16)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    addView(
                        TextView(this@FatBurnChallengeActivity).apply {
                            setTextColor(Color.parseColor("#D7ECF7"))
                            textSize = 12f
                            text = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(day.timeInMillis))
                        },
                        LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.WRAP_CONTENT),
                    )
                    addView(
                        View(this@FatBurnChallengeActivity).apply {
                            background = roundedFill("#2B465A", "#2B465A", 6)
                        },
                        LinearLayout.LayoutParams(dp(1), dp(22)),
                    )
                    addView(horizontalSpace(10))
                    addView(
                        TextView(this@FatBurnChallengeActivity).apply {
                            setTextColor(Color.WHITE)
                            textSize = 12f
                            text =
                                if (report == null) {
                                    text("未打卡", "Rest")
                                } else {
                                    "${report.validHits}${text(" 击", " hits")} · ${text("达成率", "Completion")} ${(report.accuracy * 100f).roundToInt()}% · ${formatDecimal(report.calories, 0)} kcal"
                                }
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(metricPill(if (sameDay(day.timeInMillis, today.timeInMillis)) text("今日", "Today") else text("记录", "Saved"), if (report == null) "#5D7587" else "#6BD4FF"))
                }
        }
        return rows
    }

    private fun achievementCard(milestone: AchievementMilestone): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill(if (milestone.unlocked) "#152B1E" else "#131E28", if (milestone.unlocked) "#6FE59A" else "#304655", 20)
            setPadding(dp(14), dp(12), dp(14), dp(12))

            val topRow =
                LinearLayout(this@FatBurnChallengeActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        TextView(this@FatBurnChallengeActivity).apply {
                            setTextColor(Color.WHITE)
                            textSize = 15f
                            typeface = Typeface.DEFAULT_BOLD
                            text = milestone.title(languageCode)
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(metricPill(if (milestone.unlocked) text("已解锁", "Unlocked") else milestone.progressLabel, if (milestone.unlocked) "#6FE59A" else "#FFC669"))
                }
            addView(topRow)
            addView(
                TextView(this@FatBurnChallengeActivity).apply {
                    setTextColor(Color.parseColor("#CAE8F8"))
                    textSize = 12.5f
                    setPadding(0, dp(8), 0, dp(10))
                    text = milestone.description(languageCode)
                },
            )
            addView(progressBar(milestone.progress))
        }

    private fun progressBar(progress: Float): View =
        FrameLayout(this).apply {
            background = roundedFill("#1E3342", "#1E3342", 8)
            addView(
                View(this@FatBurnChallengeActivity).apply {
                    background = roundedFill("#FF7A59", "#FFCA7A", 8)
                    layoutParams =
                        FrameLayout.LayoutParams(
                            (dp(260) * progress.coerceIn(0f, 1f)).roundToInt().coerceAtLeast(dp(8)),
                            dp(8),
                        )
                },
            )
            layoutParams =
                LinearLayout.LayoutParams(
                    dp(260),
                    dp(8),
                )
        }

    private fun updateStartButton() {
        val completed = loadReportForDay(currentPlan.dayIndex) != null
        startButtonView.text =
            when {
                isTestingChallengeDay() && completed -> text("重做测试课程", "Replay Test Day")
                isTestingChallengeDay() -> text("开始测试课程", "Start Test Day")
                completed -> text("重做今日课程", "Replay Today's Class")
                else -> text("开始今日课程", "Start Today's Class")
            }
    }

    private fun requestTrainingStart() {
        if (trainingActive || launchCountdownActive || stageRestActive) {
            return
        }
        openCoursePreview()
    }

    private fun showRestartProgramDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(text("开启下一轮挑战", "Start Next Challenge"))
            .setMessage(
                text(
                    "将从今天重新开始新一轮 30 天燃脂挑战，并清空当前这一轮的日课记录与进度。是否继续？",
                    "This will restart a new 30-day challenge from today and clear the current round records and progress. Continue?",
                ),
            ).setPositiveButton(text("开始下一轮", "Start New Round")) { _, _ ->
                restartProgramCycle()
            }.setNegativeButton(text("取消", "Cancel"), null)
            .show()
    }

    private fun restartProgramCycle() {
        val todayKey = dayKey(System.currentTimeMillis())
        prefs.edit()
            .putString(KEY_PROGRAM_START_DAY, todayKey)
            .remove(KEY_DAILY_REPORTS_JSON)
            .apply()
        selectedPlanDayIndex = null
        currentPlan = todayPlan()
        hideTrainingSummaryPopup()
        refreshDashboard()
        resetDashboardScrollPosition()
    }

    private fun requestCourseLaunch() {
        if (trainingActive || launchCountdownActive || stageRestActive) {
            return
        }
        hideTrainingSummaryPopup()
        startLaunchCountdown()
    }

    private fun startLaunchCountdown() {
        if (launchCountdownActive || trainingActive) {
            return
        }
        BoxingBleRuntime.enableGyro()
        pendingStartAfterPermission = false
        clearDetectorRecoveryState()
        launchCountdownActive = true
        hideTrainingSummaryPopup()
        lastDetectorStateType = "loading"
        dispatchDetectorState("loading", text("开始准备蓝牙击中识别。", "Preparing Bluetooth hit detection."))
        startDetectorSession()
        updateTrainingUi()

        val steps =
            listOf(
                text("校准", "CAL"),
                "3",
                "2",
                "1",
                "GO",
            )
        val delays = listOf(900L, 760L, 760L, 760L, 640L)
        launchCountdownJob?.cancel()
        launchCountdownJob =
            lifecycleScope.launch {
                countdownView.visibility = View.VISIBLE
                for (index in steps.indices) {
                    countdownView.text =
                        if (steps[index] == "GO") {
                            text("开始", "GO!", "Partez !", "เริ่ม!")
                        } else {
                            steps[index]
                        }
                    if (index > 0) {
                        speakLaunchCountdownStep(steps[index])
                    }
                    delay(delays[index])
                }
                countdownView.visibility = View.GONE
                startTrainingSession()
            }
    }

    private fun cancelLaunchCountdown(showMessage: Boolean) {
        if (launchCountdownActive) {
            BoxingBleRuntime.disableGyro()
        }
        launchCountdownJob?.cancel()
        launchCountdownJob = null
        launchCountdownActive = false
        countdownView.visibility = View.GONE
        if (showMessage) {
            dispatchDetectorState("finished", text("已取消开始倒计时。", "Launch countdown cancelled."))
        }
    }

    private fun startTrainingSession() {
        currentPlan = todayPlan()
        launchCountdownActive = false
        stageRestActive = false
        trainingRunId = SystemClock.elapsedRealtime()
        clearDetectorRecoveryState()
        courseStages = buildCourseStages(currentPlan)
        currentStageIndex = 0
        courseAccumulatedHits = 0
        courseAccumulatedCalories = 0f
        courseAccumulatedFatBurn = 0f
        courseBestCombo = 0
        courseAccumulatedTrainingMs = 0L
        startCurrentStage()
    }

    private fun finishTrainingSession(manual: Boolean) {
        if (!trainingActive && !launchCountdownActive && !stageRestActive) {
            return
        }
        BoxingBleRuntime.disableGyro()
        cancelLaunchCountdown(showMessage = false)
        stageTransitionJob?.cancel()
        stageTransitionJob = null
        val snapshot =
            if (trainingActive) {
                val liveSnapshot = gameView.currentSnapshot()
                accumulateStageSnapshot(liveSnapshot)
                buildCourseSnapshot()
            } else {
                buildCourseSnapshot()
            }
        val courseDurationSeconds = (courseAccumulatedTrainingMs.coerceAtLeast(0L) / 1000L).toInt().coerceAtLeast(1)
        trainingActive = false
        stageRestActive = false
        pauseTrainingCountdown()
        stopDetectorSession()
        clearDetectorRecoveryState()
        gameView.endTraining()
        gameView.visibility = View.GONE
        persistDailyReport(snapshot, courseDurationSeconds)
        maybeUploadCourseToCloud(snapshot, courseDurationSeconds)
        refreshDashboard()
        showTrainingSummaryPopup(snapshot, manual)
        updateTrainingUi()
    }

    private fun showTrainingSummaryPopup(
        snapshot: BlitzModeGameView.OverlaySnapshot,
        manual: Boolean,
    ) {
        resultPopupMode = ResultPopupMode.Summary
        val summary = snapshot.toChallengeSummary(currentPlan)
        val grade = evaluateGrade(summary, currentPlan)
        val previousReport =
            loadReports()
                .filter { it.dayIndex < currentPlan.dayIndex }
                .maxByOrNull { it.dayIndex }

        val deltaHits = summary.validHits - (previousReport?.validHits ?: 0)
        val deltaAccuracy = summary.accuracy - (previousReport?.accuracy ?: 0f)
        val streak = currentStreakDays(loadReports())
        val body =
            StringBuilder().apply {
                appendLine(
                    text(
                        "总击打 ${summary.totalHits} 次 · 最佳连击 ${snapshot.bestCombo} 次 · 达成率 ${(summary.accuracy * 100f).roundToInt()}%",
                        "Hits ${summary.totalHits} · Best combo ${snapshot.bestCombo} · Completion ${(summary.accuracy * 100f).roundToInt()}%",
                    ),
                )
                appendLine(
                    text(
                        "平均频率 PPS ${formatDecimal(summary.hps, 2)} · 消耗 ${formatDecimal(snapshot.calories, 0)} kcal · 预计燃脂 ${formatDecimal(snapshot.fatBurnGrams, 1)} g",
                        "Average PPS ${formatDecimal(summary.hps, 2)} · ${formatDecimal(snapshot.calories, 0)} kcal · ${formatDecimal(snapshot.fatBurnGrams, 1)} g fat burn",
                    ),
                )
                appendLine(
                    text(
                        "本轮等级 $grade · 连续打卡 ${streak} 天",
                        "Grade $grade · Streak $streak days",
                    ),
                )
                append(
                    text(
                        "较上一日 ${if (deltaHits >= 0) "+" else ""}$deltaHits 次 · ${if (deltaAccuracy >= 0f) "+" else ""}${(deltaAccuracy * 100f).roundToInt()}%",
                        "vs previous day ${if (deltaHits >= 0) "+" else ""}$deltaHits hits · ${if (deltaAccuracy >= 0f) "+" else ""}${(deltaAccuracy * 100f).roundToInt()}%",
                    ),
                )
                if (manual) {
                    appendLine()
                    append(text("本次为手动结束，训练报告已保存。", "This run ended manually and the report was saved."))
                }
            }.toString()

        resultTitleView.text =
            when (grade) {
                "S" -> text("燃脂先锋", "Fat-Burn Leader")
                "A" -> text("高效燃脂", "High-Efficiency Burn")
                "B" -> text("稳定完成", "Steady Finish")
                else -> text("继续加油", "Keep Going")
            }
        resultBodyView.text = body
        resultPrimaryButtonView.visibility = View.VISIBLE
        resultSecondaryButtonView.visibility = View.VISIBLE
        resultPrimaryButtonView.text = text("查看日报", "Open Report")
        resultSecondaryButtonView.text = text("返回首页", "Back Home")
        resultPrimaryButtonView.setOnClickListener {
            hideTrainingSummaryPopup()
            selectedTab = DashboardTab.Report
            refreshTabButtons()
            refreshDashboard()
            resetDashboardScrollPosition()
            renderIdleState()
        }
        resultSecondaryButtonView.setOnClickListener {
            hideTrainingSummaryPopup()
            selectedTab = DashboardTab.Plan
            refreshTabButtons()
            refreshDashboard()
            resetDashboardScrollPosition()
            renderIdleState()
        }
        resultPopupView.visibility = View.VISIBLE
    }

    private fun hideTrainingSummaryPopup() {
        stageTransitionJob?.cancel()
        stageTransitionJob = null
        resultPopupView.visibility = View.GONE
    }

    private fun startTrainingCountdown() {
        countdownJob?.cancel()
        val sessionId = trainingRunId
        val stage = currentStage()
        val totalMs = (stage?.durationSec ?: 0) * 1_000L
        if (stage?.durationSec == null) {
            remainingTrainingMs = 0L
            updateTimerDisplay()
            return
        }
        countdownJob =
            lifecycleScope.launch {
                val startElapsedMs = SystemClock.elapsedRealtime()
                while (trainingActive && sessionId == trainingRunId) {
                    val elapsed = SystemClock.elapsedRealtime() - startElapsedMs
                    remainingTrainingMs = (totalMs - elapsed).coerceAtLeast(0L)
                    updateTimerDisplay()
                    if (remainingTrainingMs <= 0L) {
                        completeTimedStage()
                        break
                    }
                    delay(100L)
                }
            }
    }

    private fun pauseTrainingCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun resumeTrainingIfNeeded() {
        if (!trainingActive) {
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

    private fun initSpeechEngine() {
        speechEngine =
            TextToSpeech(applicationContext) { status ->
                speechReady = status == TextToSpeech.SUCCESS
                if (speechReady) {
                    speechEngine?.language = fatBurnChallengeLocale(languageCode)
                    speechEngine?.setSpeechRate(1.04f)
                    speechEngine?.setPitch(1.0f)
                }
            }
    }

    private fun speakLaunchCountdownStep(step: String) {
        if (!speechReady) {
            return
        }
        val speechText =
            when {
                languageCode.startsWith("zh") ->
                    when (step) {
                        "校准" -> "校准"
                        "3" -> "三"
                        "2" -> "二"
                        "1" -> "一"
                        else -> "开始"
                    }
                languageCode.startsWith("fr") ->
                    when (step) {
                        "校准" -> "Calibrage"
                        "GO" -> "Partez"
                        else -> step
                    }
                languageCode.startsWith("th") ->
                    when (step) {
                        "校准" -> "ปรับเทียบ"
                        "GO" -> "เริ่ม"
                        else -> step
                    }
                else -> if (step == "GO") "Go" else step
            }
        speechEngine?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "fat_burn_countdown_$step")
    }

    private fun clearDetectorRecoveryState() {
        detectorRecoveryAttempts = 0
        detectorRecoveryJob?.cancel()
        detectorRecoveryJob = null
    }

    private fun openCoursePreview() {
        currentPlan = todayPlan()
        courseStages = buildCourseStages(currentPlan)
        resultPopupMode = ResultPopupMode.Preview
        resultTitleView.text = if (isTestingChallengeDay()) text("测试课程预览", "Test Course Preview") else text("今日课程预览", "Today's Course")
        val stageLines =
            courseStages.mapIndexed { index, stage ->
                val durationText =
                    stage.durationSec?.let { "${it}${text("秒", "s", "s", " วินาที")}" } ?:
                        text(
                            "直到完成目标击打",
                            "Until the hit target is complete",
                            "Jusqu'à l'objectif de coups",
                            "จนกว่าจะตีครบตามเป้า",
                        )
                "${index + 1}. ${stage.title(languageCode)} · $durationText"
            }
        resultBodyView.text =
            buildString {
                appendLine(currentPlan.title(languageCode))
                appendLine(
                    text("课程目标", "Course goal", "Objectif du cours", "เป้าหมายคอร์ส") +
                        " · " +
                        text(
                            "${currentPlan.targetValidHits}击",
                            "${currentPlan.targetValidHits} hits",
                            "${currentPlan.targetValidHits} coups",
                            "${currentPlan.targetValidHits} ครั้ง",
                        ),
                )
                appendLine()
                append(stageLines.joinToString("\n"))
            }
        resultPrimaryButtonView.visibility = View.VISIBLE
        resultSecondaryButtonView.visibility = View.VISIBLE
        resultPrimaryButtonView.text = text("开始", "Start")
        resultSecondaryButtonView.text = text("返回", "Back")
        resultPrimaryButtonView.setOnClickListener { requestCourseLaunch() }
        resultSecondaryButtonView.setOnClickListener { hideTrainingSummaryPopup() }
        resultPopupView.visibility = View.VISIBLE
    }

    private fun startCurrentStage() {
        val stage = currentStage() ?: return
        trainingActive = true
        stageRestActive = false
        trainingRunId = SystemClock.elapsedRealtime()
        currentStageStartedAtMs = SystemClock.elapsedRealtime()
        remainingTrainingMs = (stage.durationSec ?: 0) * 1_000L
        gameView.setDifficultyPreset(stage.difficulty)
        gameView.visibility = View.VISIBLE
        gameView.beginTraining()
        if (
            bleHitListener == null ||
            lastDetectorStateType == "finished" ||
            lastDetectorStateType == "error" ||
            lastDetectorStateType == "permission_denied"
        ) {
            lastDetectorStateType = "loading"
            dispatchDetectorState("loading", text("正在准备蓝牙击中识别。", "Preparing Bluetooth hit detection."))
            startDetectorSession()
        } else {
            gameView.updateDetectorState(lastDetectorStateType)
        }
        updateOverlay(gameView.currentSnapshot())
        updateTimerDisplay()
        updateTrainingUi()
        startTrainingCountdown()
    }

    private fun completeTimedStage() {
        if (!trainingActive) {
            return
        }
        val stage = currentStage() ?: return
        val stageSnapshot = gameView.currentSnapshot()
        accumulateStageSnapshot(stageSnapshot)
        trainingActive = false
        pauseTrainingCountdown()
        gameView.endTraining()
        if (currentStageIndex >= courseStages.lastIndex) {
            finishTrainingSession(manual = false)
            return
        }
        val nextStageIndex = currentStageIndex + 1
        val restSec = stage.restSecAfter.coerceAtLeast(0)
        if (restSec <= 0) {
            currentStageIndex = nextStageIndex
            startCurrentStage()
            return
        }
        stageRestActive = true
        updateTrainingUi()
        showStageRestPopup(stageSnapshot, restSec, nextStageIndex)
    }

    private fun showStageRestPopup(
        stageSnapshot: BlitzModeGameView.OverlaySnapshot,
        restSec: Int,
        nextStageIndex: Int,
    ) {
        resultPopupMode = ResultPopupMode.StageRest
        resultPrimaryButtonView.visibility = View.GONE
        resultSecondaryButtonView.visibility = View.GONE
        resultTitleView.text = text("${currentStage()?.title(languageCode).orEmpty()} 完成", "${currentStage()?.title(languageCode).orEmpty()} complete")
        stageTransitionJob?.cancel()
        stageTransitionJob =
            lifecycleScope.launch {
                var remainingRest = restSec
                while (remainingRest >= 0 && stageRestActive) {
                    val nextStage = courseStages.getOrNull(nextStageIndex)
                    resultBodyView.text =
                        text(
                            "本阶段消耗 ${formatDecimal(stageSnapshot.calories, 1)} kcal · 预计燃脂 ${formatDecimal(stageSnapshot.fatBurnGrams, 1)} g\n休息 ${remainingRest} 秒后进入 ${nextStage?.title(languageCode).orEmpty()}",
                            "This stage burned ${formatDecimal(stageSnapshot.calories, 1)} kcal and ${formatDecimal(stageSnapshot.fatBurnGrams, 1)} g fat.\nRest ${remainingRest}s, then enter ${nextStage?.title(languageCode).orEmpty()}",
                            "Cette étape a brûlé ${formatDecimal(stageSnapshot.calories, 1)} kcal et ${formatDecimal(stageSnapshot.fatBurnGrams, 1)} g de graisse.\nReposez-vous ${remainingRest}s puis passez à ${nextStage?.title(languageCode).orEmpty()}",
                            "ช่วงนี้เผาผลาญ ${formatDecimal(stageSnapshot.calories, 1)} kcal และไขมัน ${formatDecimal(stageSnapshot.fatBurnGrams, 1)} g\nพัก ${remainingRest} วินาที แล้วเข้าสู่ ${nextStage?.title(languageCode).orEmpty()}",
                        )
                    resultPopupView.visibility = View.VISIBLE
                    if (remainingRest == 0) {
                        break
                    }
                    delay(1_000L)
                    remainingRest -= 1
                }
                if (!stageRestActive) {
                    return@launch
                }
                val nextStage = courseStages.getOrNull(nextStageIndex)
                resultBodyView.text =
                    text(
                        "本阶段消耗 ${formatDecimal(stageSnapshot.calories, 1)} kcal · 预计燃脂 ${formatDecimal(stageSnapshot.fatBurnGrams, 1)} g\n休息已结束，请点击“开始”进入 ${nextStage?.title(languageCode).orEmpty()}。",
                        "This stage burned ${formatDecimal(stageSnapshot.calories, 1)} kcal and ${formatDecimal(stageSnapshot.fatBurnGrams, 1)} g fat.\nRest is complete. Tap Start to enter ${nextStage?.title(languageCode).orEmpty()}.",
                        "Cette étape a brûlé ${formatDecimal(stageSnapshot.calories, 1)} kcal et ${formatDecimal(stageSnapshot.fatBurnGrams, 1)} g de graisse.\nLe repos est terminé. Touchez Démarrer pour entrer dans ${nextStage?.title(languageCode).orEmpty()}.",
                        "ช่วงนี้เผาผลาญ ${formatDecimal(stageSnapshot.calories, 1)} kcal และไขมัน ${formatDecimal(stageSnapshot.fatBurnGrams, 1)} g\nพักเสร็จแล้ว แตะเริ่มเพื่อเข้าสู่ ${nextStage?.title(languageCode).orEmpty()}",
                    )
                resultPrimaryButtonView.visibility = View.VISIBLE
                resultSecondaryButtonView.visibility = View.VISIBLE
                resultPrimaryButtonView.text = text("开始下一阶段", "Start Next Stage")
                resultSecondaryButtonView.text = text("结束课程", "End Session")
                resultPrimaryButtonView.setOnClickListener {
                    stageTransitionJob?.cancel()
                    stageTransitionJob = null
                    resultPopupView.visibility = View.GONE
                    currentStageIndex = nextStageIndex
                    startCurrentStage()
                }
                resultSecondaryButtonView.setOnClickListener {
                    stageTransitionJob?.cancel()
                    stageTransitionJob = null
                    finishTrainingSession(manual = true)
                }
            }
    }

    private fun accumulateStageSnapshot(snapshot: BlitzModeGameView.OverlaySnapshot) {
        courseAccumulatedHits += snapshot.hits
        courseAccumulatedCalories += snapshot.calories
        courseAccumulatedFatBurn += snapshot.fatBurnGrams
        courseBestCombo = max(courseBestCombo, snapshot.bestCombo)
        courseAccumulatedTrainingMs += (SystemClock.elapsedRealtime() - currentStageStartedAtMs).coerceAtLeast(0L)
    }

    private fun buildCourseSnapshot(): BlitzModeGameView.OverlaySnapshot {
        val totalSeconds = (courseAccumulatedTrainingMs.coerceAtLeast(1L)) / 1000f
        return BlitzModeGameView.OverlaySnapshot(
            hits = courseAccumulatedHits,
            combo = 0,
            bestCombo = courseBestCombo,
            pps = if (totalSeconds <= 0f) 0f else courseAccumulatedHits / totalSeconds,
            calories = courseAccumulatedCalories,
            fatBurnGrams = courseAccumulatedFatBurn,
        )
    }

    private fun currentStage(): CourseStage? = courseStages.getOrNull(currentStageIndex)

    private fun isGoalStage(): Boolean = trainingActive && currentStage()?.durationSec == null

    private fun currentGoalRemainingHits(snapshot: BlitzModeGameView.OverlaySnapshot = gameView.currentSnapshot()): Int =
        (currentPlan.targetValidHits - (courseAccumulatedHits + snapshot.hits)).coerceAtLeast(0)

    private fun scheduleDetectorRecovery(throwable: Throwable) {
        if (!trainingActive && !launchCountdownActive) {
            return
        }
        if (detectorRecoveryAttempts >= MAX_DETECTOR_RECOVERY_ATTEMPTS) {
            dispatchDetectorState("error", text("蓝牙击中识别中断，请重新开始课程。", "Bluetooth hit detection stopped. Please restart the class."))
            return
        }
        detectorRecoveryAttempts += 1
        dispatchDetectorState("error", text("正在恢复蓝牙击中识别…", "Recovering Bluetooth hit detection..."))
        detectorRecoveryJob?.cancel()
        detectorRecoveryJob =
            lifecycleScope.launch {
                delay((450L + detectorRecoveryAttempts * 250L).coerceAtMost(1_600L))
                if (trainingActive || launchCountdownActive) {
                    startDetectorSession()
                }
            }
    }

    private fun dispatchBleDetectorReady() {
        dispatchDetectorState(type = "ready", message = text("蓝牙击中识别已就绪。", "Bluetooth hit detection is ready."))
    }
    private fun dispatchDetectorState(
        type: String,
        message: String,
    ) {
        lastDetectorStateType = type
        gameView.updateDetectorState(type)
        hintView.text = message
        hintView.visibility = if (trainingActive || launchCountdownActive) View.VISIBLE else View.GONE
    }

    private fun renderIdleState() {
        trainingActive = false
        stageRestActive = false
        launchCountdownActive = false
        updateTrainingUi()
        gameView.endTraining()
        gameView.visibility = View.GONE
        hintView.text = text("查看今日课程后即可开始。", "Review today's class and start when ready.")
        refreshDashboard()
        resetDashboardScrollPosition()
    }

    private fun updateTrainingUi() {
        val inTrainingState = trainingActive || launchCountdownActive || stageRestActive
        backButtonView.visibility = if (inTrainingState) View.GONE else View.VISIBLE
        dashboardScrollView.visibility = if (inTrainingState) View.GONE else View.VISIBLE
        statusBarView.visibility = if (trainingActive) View.VISIBLE else View.GONE
        endButtonView.visibility = if (trainingActive) View.VISIBLE else View.GONE
        hintView.visibility = if (trainingActive || launchCountdownActive) View.VISIBLE else View.GONE
        countdownView.visibility = if (launchCountdownActive) View.VISIBLE else View.GONE
        gameView.visibility = if (trainingActive) View.VISIBLE else View.GONE
        if (!inTrainingState && resultPopupView.visibility != View.VISIBLE) {
            hintView.visibility = View.GONE
        }
    }

    private fun resetDashboardScrollPosition() {
        dashboardScrollView.post {
            dashboardScrollView.scrollTo(0, 0)
        }
    }

    private fun updateOverlay(snapshot: BlitzModeGameView.OverlaySnapshot) {
        onOverlaySnapshot(snapshot)
    }

    private fun updateTimerDisplay() {
        timerValueView.text =
            if (isGoalStage()) {
                text(
                    "剩${currentGoalRemainingHits()}击",
                    "${currentGoalRemainingHits()} left",
                    "${currentGoalRemainingHits()} restants",
                    "เหลืออีก ${currentGoalRemainingHits()} ครั้ง",
                )
            } else {
                displayRemaining(remainingTrainingMs)
            }
    }

    private fun plannedCourseCumulative(dayIndex: Int): PlannedCourseCumulative {
        var totalCalories = 0f
        var totalFatBurn = 0f
        for (day in 1..dayIndex.coerceIn(1, 30)) {
            val plan = generatePlanForDay(day)
            totalCalories += plan.estimatedCalories
            totalFatBurn += plan.estimatedFatBurnGrams()
        }
        return PlannedCourseCumulative(totalCalories, totalFatBurn)
    }

    private fun ChallengeDayPlan.estimatedFatBurnGrams(): Float = estimatedCalories * toBlitzDifficultyPreset().fatBurnRatio

    private fun todayPlan(): ChallengeDayPlan = generatePlanForDay(activePlanDayIndex())

    private fun activePlanDayIndex(): Int = selectedPlanDayIndex?.takeIf { canTestAnyPlanDay() } ?: currentDayIndex()

    private fun canTestAnyPlanDay(): Boolean = SUPER_PLAN_TEST_SERIALS.contains(authSerial)

    private fun isTestingChallengeDay(): Boolean = canTestAnyPlanDay() && activePlanDayIndex() != currentDayIndex()

    private fun currentDayIndex(): Int {
        val startDayKey = prefs.getString(KEY_PROGRAM_START_DAY, null)
        val todayKey = dayKey(System.currentTimeMillis())
        if (startDayKey == null) {
            prefs.edit().putString(KEY_PROGRAM_START_DAY, todayKey).apply()
            return 1
        }
        val startTime = parseDayKey(startDayKey)
        val diffDays = ((dayStart(System.currentTimeMillis()) - startTime) / DAY_MS).toInt().coerceAtLeast(0)
        return (diffDays + 1).coerceAtMost(30)
    }

    private fun buildCourseStages(plan: ChallengeDayPlan): List<CourseStage> {
        val restBase = plan.restSec.coerceIn(15, 35)
        return when {
            plan.loadScore <= 3 ->
                listOf(
                    CourseStage("阶段1 · 激活节奏", "Stage 1 · Activate", 30, restBase, BlitzModeGameView.DifficultyPreset.Beginner),
                    CourseStage("阶段2 · 提升频率", "Stage 2 · Build Pace", 30, restBase + 5, BlitzModeGameView.DifficultyPreset.Advanced),
                    CourseStage("终段 · 达成目标", "Final · Hit Goal", null, 0, BlitzModeGameView.DifficultyPreset.Advanced),
                )
            plan.loadScore <= 6 ->
                listOf(
                    CourseStage("阶段1 · 激活节奏", "Stage 1 · Activate", 30, restBase, BlitzModeGameView.DifficultyPreset.Advanced),
                    CourseStage("阶段2 · 稳态燃脂", "Stage 2 · Steady Burn", 60, restBase + 5, BlitzModeGameView.DifficultyPreset.Advanced),
                    CourseStage("终段 · 达成目标", "Final · Hit Goal", null, 0, BlitzModeGameView.DifficultyPreset.Advanced),
                )
            else ->
                listOf(
                    CourseStage("阶段1 · 激活节奏", "Stage 1 · Activate", 30, restBase, BlitzModeGameView.DifficultyPreset.Advanced),
                    CourseStage("阶段2 · 稳态燃脂", "Stage 2 · Steady Burn", 60, restBase + 5, BlitzModeGameView.DifficultyPreset.Insane),
                    CourseStage("阶段3 · 冲刺燃脂", "Stage 3 · Sprint Burn", 60, restBase + 8, BlitzModeGameView.DifficultyPreset.Insane),
                    CourseStage("终段 · 达成目标", "Final · Hit Goal", null, 0, BlitzModeGameView.DifficultyPreset.Insane),
                )
        }
    }

    private fun generatePlanForDay(dayIndex: Int): ChallengeDayPlan {
        val safeDay = dayIndex.coerceIn(1, 30)
        val phase =
            when {
                safeDay <= 10 -> 0
                safeDay <= 20 -> 1
                else -> 2
            }
        val dayInPhase = ((safeDay - 1) % 10) + 1
        val recoveryDay = dayInPhase == 4 || dayInPhase == 8
        val assessmentDay = dayInPhase == 10

        val phaseLabelZh = listOf("适应期", "强化期", "巩固期")[phase]
        val phaseLabelEn = listOf("Adaptation", "Build", "Consolidation")[phase]
        val phaseThemesZh =
            listOf(
                listOf("节拍适应日", "基础连打日", "短时冲刺日", "恢复节奏日", "耐力推进日", "跟拍稳控日", "连续输出日", "恢复技术日", "爆发节奏日", "阶段评估日"),
                listOf("强化耐力日", "双段冲刺日", "高频跟拍日", "恢复节奏日", "负荷提升日", "节拍压缩日", "持续爆发日", "主动恢复日", "速度巩固日", "阶段评估日"),
                listOf("巩固耐力日", "速度爆发日", "节奏稳定日", "恢复节奏日", "高负荷冲刺日", "终段提速日", "连续输出日", "恢复整合日", "峰值挑战日", "终段评估日"),
            )
        val themeZh = phaseThemesZh[phase][dayInPhase - 1]
        val themeEn =
            when (themeZh) {
                "节拍适应日" -> "Rhythm Adapt"
                "基础连打日" -> "Foundational Combo"
                "短时冲刺日" -> "Short Burst"
                "恢复节奏日" -> "Recovery Rhythm"
                "耐力推进日" -> "Endurance Build"
                "跟拍稳控日" -> "Beat Control"
                "连续输出日" -> "Sustained Output"
                "恢复技术日" -> "Recovery Technique"
                "爆发节奏日" -> "Explosive Rhythm"
                "阶段评估日" -> "Stage Review"
                "强化耐力日" -> "Power Endurance"
                "双段冲刺日" -> "Dual Burst"
                "高频跟拍日" -> "High Frequency"
                "负荷提升日" -> "Load Boost"
                "节拍压缩日" -> "Compressed Tempo"
                "持续爆发日" -> "Sustained Burst"
                "主动恢复日" -> "Active Recovery"
                "速度巩固日" -> "Speed Consolidation"
                "终段评估日" -> "Final Review"
                "巩固耐力日" -> "Consolidated Endurance"
                "速度爆发日" -> "Speed Burst"
                "节奏稳定日" -> "Steady Rhythm"
                "高负荷冲刺日" -> "Heavy Sprint"
                "终段提速日" -> "Late Acceleration"
                "恢复整合日" -> "Recovery Integration"
                "峰值挑战日" -> "Peak Challenge"
                else -> "Fat Burn Session"
            }

        val baseWarmup = if (phase == 0) 45 else 55
        val baseRounds = when (phase) { 0 -> 4; 1 -> 5; else -> 6 }
        val baseWork = when (phase) { 0 -> 26; 1 -> 34; else -> 42 }
        val baseRest = when (phase) { 0 -> 22; 1 -> 18; else -> 14 }
        val baseCooldown = if (phase == 2) 50 else 45
        val baseBpm = when (phase) { 0 -> 92; 1 -> 108; else -> 120 }

        val rounds =
            when {
                recoveryDay -> (baseRounds - 1).coerceAtLeast(3)
                assessmentDay -> baseRounds + 1
                else -> baseRounds + (dayInPhase / 4)
            } * DAILY_TRAINING_VOLUME_MULTIPLIER
        val workSec =
            when {
                recoveryDay -> (baseWork - 4).coerceAtLeast(22)
                assessmentDay -> baseWork + 6
                else -> baseWork + (dayInPhase % 3) * 3
            }
        val restSec =
            when {
                recoveryDay -> baseRest + 6
                assessmentDay -> (baseRest - 2).coerceAtLeast(12)
                else -> (baseRest - dayInPhase / 5).coerceAtLeast(12)
            }
        val bpm =
            when {
                recoveryDay -> baseBpm - 10
                assessmentDay -> baseBpm + 8
                else -> baseBpm + (dayInPhase - 1) * 2
            }
        val loadScore = min(10, max(3, ((rounds * workSec) / 30) + phase * 2 - if (recoveryDay) 1 else 0))
        val intensityZh =
            when {
                loadScore <= 4 -> "低负荷"
                loadScore <= 7 -> "中高负荷"
                else -> "高强度"
            }
        val intensityEn =
            when {
                loadScore <= 4 -> "Low Load"
                loadScore <= 7 -> "Medium-High"
                else -> "High Intensity"
            }
        val totalDurationMin = (baseWarmup + baseCooldown + rounds * workSec + (rounds - 1) * restSec) / 60f
        val estimatedCalories = totalDurationMin * (5.3f + loadScore * 0.38f)
        val expectedValidBeats = rounds * ((workSec * bpm / 60f) * 0.84f)
        val targetValidHits = expectedValidBeats.roundToInt().coerceAtLeast(48)

        val focusZh =
            when {
                recoveryDay -> "降低负荷，稳住节奏与呼吸恢复"
                assessmentDay -> "用完整节拍测出当前耐力上限"
                phase == 0 -> "建立稳定跟拍与基础拳击耐受度"
                phase == 1 -> "提高持续输出、冲刺频率与节拍压缩能力"
                else -> "巩固耐力储备，拉高末段输出稳定性"
            }
        val focusEn =
            when {
                recoveryDay -> "Reduce load and recover rhythm and breathing"
                assessmentDay -> "Measure endurance ceiling with a full-beat test"
                phase == 0 -> "Build rhythm stability and basic tolerance"
                phase == 1 -> "Raise sustained output and burst frequency"
                else -> "Solidify endurance reserves and late-stage stability"
            }

        return ChallengeDayPlan(
            dayIndex = safeDay,
            phaseLabelZh = phaseLabelZh,
            phaseLabelEn = phaseLabelEn,
            titleZh = themeZh,
            titleEn = themeEn,
            focusZh = focusZh,
            focusEn = focusEn,
            intensityZh = intensityZh,
            intensityEn = intensityEn,
            warmupSec = baseWarmup,
            roundCount = rounds,
            workSec = workSec,
            restSec = restSec,
            cooldownSec = baseCooldown,
            bpm = bpm,
            loadScore = loadScore,
            estimatedCalories = estimatedCalories,
            targetValidHits = targetValidHits,
        )
    }

    private fun ChallengeDayPlan.toBlitzDifficultyPreset(): BlitzModeGameView.DifficultyPreset =
        when {
            loadScore >= 8 || workSec >= 30 -> BlitzModeGameView.DifficultyPreset.Insane
            loadScore >= 5 || roundCount >= 6 -> BlitzModeGameView.DifficultyPreset.Advanced
            else -> BlitzModeGameView.DifficultyPreset.Beginner
        }

    private fun persistDailyReport(
        snapshot: BlitzModeGameView.OverlaySnapshot,
        durationSec: Int,
    ) {
        val summary = snapshot.toChallengeSummary(currentPlan)
        val reports = loadReports().toMutableList()
        val currentTotalCalories = reports.sumOf { it.calories.toDouble() }.toFloat() + snapshot.calories
        val currentTotalFat = reports.sumOf { it.fatBurnGrams.toDouble() }.toFloat() + snapshot.fatBurnGrams
        val report =
            DayReport(
                dayIndex = currentPlan.dayIndex,
                timestampMs = System.currentTimeMillis(),
                totalHits = summary.totalHits,
                validHits = summary.validHits,
                missedBeats = summary.missedBeats,
                accuracy = summary.accuracy,
                hps = summary.hps,
                calories = snapshot.calories,
                fatBurnGrams = snapshot.fatBurnGrams,
                durationSec = durationSec.coerceAtLeast(1),
                grade = evaluateGrade(summary, currentPlan),
                estimatedWeightKg = DEFAULT_BASE_WEIGHT_KG - currentTotalCalories / 7_700f,
                estimatedWaistDeltaCm = currentTotalFat / 600f,
            )
        reports.removeAll { it.dayIndex == report.dayIndex }
        reports += report
        reports.sortBy { it.dayIndex }
        saveReports(reports)
    }

    private fun maybeUploadCourseToCloud(
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
                    preferredModeSeconds = if (durationSeconds >= 60) 60 else 30,
                ),
        )
    }

    private fun loadReports(): List<DayReport> {
        val raw = prefs.getString(KEY_DAILY_REPORTS_JSON, null).orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(DayReport.fromJson(array.getJSONObject(index)))
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun saveReports(reports: List<DayReport>) {
        val array = JSONArray()
        reports.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_DAILY_REPORTS_JSON, array.toString()).apply()
    }

    private fun loadReportForDay(dayIndex: Int): DayReport? = loadReports().firstOrNull { it.dayIndex == dayIndex }

    private fun aggregateWindow(reports: List<DayReport>): AggregateWindow {
        if (reports.isEmpty()) {
            return AggregateWindow(
                completedDays = 0,
                calories = 0f,
                fatBurnGrams = 0f,
                avgAccuracy = 0f,
                avgHps = 0f,
                estimatedWeightKg = DEFAULT_BASE_WEIGHT_KG,
                estimatedWaistDeltaCm = 0f,
            )
        }
        val calories = reports.sumOf { it.calories.toDouble() }.toFloat()
        val fatBurn = reports.sumOf { it.fatBurnGrams.toDouble() }.toFloat()
        val avgAccuracy = reports.map { it.accuracy }.average().toFloat()
        val avgHps = reports.map { it.hps }.average().toFloat()
        return AggregateWindow(
            completedDays = reports.size,
            calories = calories,
            fatBurnGrams = fatBurn,
            avgAccuracy = avgAccuracy,
            avgHps = avgHps,
            estimatedWeightKg = DEFAULT_BASE_WEIGHT_KG - calories / 7_700f,
            estimatedWaistDeltaCm = fatBurn / 600f,
        )
    }

    private fun buildAchievements(reports: List<DayReport>): List<AchievementMilestone> {
        val streak = currentStreakDays(reports)
        val totalCalories = reports.sumOf { it.calories.toDouble() }.toFloat()
        val monthAggregate = aggregateWindow(reports)
        val accuracyStreak = longestAccuracyStreak(reports, threshold = 0.85f)

        return listOf(
            AchievementMilestone("坚持 3 天", "3-Day Streak", "连续 3 天完成课程，建立运动节律。", "Complete 3 days in a row to build rhythm.", streak >= 3, streak / 3f, "$streak / 3"),
            AchievementMilestone("坚持 7 天", "7-Day Streak", "连续 7 天打卡，建立第一层习惯闭环。", "Train for 7 straight days to lock in habit momentum.", streak >= 7, streak / 7f, "$streak / 7"),
            AchievementMilestone("坚持 14 天", "14-Day Streak", "连续 14 天课程，耐力与执行力同步提升。", "Complete 14 days in a row for deeper endurance gains.", streak >= 14, streak / 14f, "$streak / 14"),
            AchievementMilestone("累计燃烧 500 kcal", "Burn 500 kcal", "累计完成 500 kcal 的课程消耗。", "Accumulate 500 kcal burned in challenge sessions.", totalCalories >= 500f, totalCalories / 500f, "${formatDecimal(totalCalories, 0)} / 500"),
            AchievementMilestone("累计燃烧 1000 kcal", "Burn 1000 kcal", "累计完成 1000 kcal 的课程消耗。", "Accumulate 1000 kcal burned in challenge sessions.", totalCalories >= 1_000f, totalCalories / 1_000f, "${formatDecimal(totalCalories, 0)} / 1000"),
            AchievementMilestone("腰围 -1cm", "Waist -1 cm", "根据累计燃脂量估算，腰围趋势下降 1 cm。", "Estimated waist trend drops by 1 cm from cumulative fat burn.", monthAggregate.estimatedWaistDeltaCm >= 1f, monthAggregate.estimatedWaistDeltaCm / 1f, "${formatDecimal(monthAggregate.estimatedWaistDeltaCm, 1)} / 1.0"),
            AchievementMilestone("连续 5 天达成率 > 85%", "5 Days > 85% Completion", "连续 5 天把课程目标达成率稳定在 85% 以上。", "Keep completion above 85% for five days straight.", accuracyStreak >= 5, accuracyStreak / 5f, "$accuracyStreak / 5"),
        )
    }

    private fun longestAccuracyStreak(
        reports: List<DayReport>,
        threshold: Float,
    ): Int {
        var best = 0
        var current = 0
        reports.sortedBy { it.dayIndex }.forEach { report ->
            if (report.accuracy >= threshold) {
                current += 1
                best = max(best, current)
            } else {
                current = 0
            }
        }
        return best
    }

    private fun currentStreakDays(reports: List<DayReport>): Int {
        if (reports.isEmpty()) {
            return 0
        }
        val dayKeys = reports.map { dayKey(it.timestampMs) }.toHashSet()
        var cursor = dayStart(System.currentTimeMillis())
        var streak = 0
        while (dayKeys.contains(dayKey(cursor))) {
            streak += 1
            cursor -= DAY_MS
        }
        return streak
    }

    private fun evaluateGrade(
        summary: ChallengeSummary,
        plan: ChallengeDayPlan,
    ): String {
        val targetHps = plan.targetHps()
        return when {
            summary.accuracy >= 0.88f && summary.hps >= targetHps * 0.98f -> "S"
            summary.accuracy >= 0.82f && summary.hps >= targetHps * 0.90f -> "A"
            summary.accuracy >= 0.72f -> "B"
            else -> "C"
        }
    }

    private fun BlitzModeGameView.OverlaySnapshot.toChallengeSummary(plan: ChallengeDayPlan): ChallengeSummary {
        val completionRate =
            if (plan.targetValidHits <= 0) {
                if (hits > 0) 1f else 0f
            } else {
                (hits.toFloat() / plan.targetValidHits.toFloat()).coerceIn(0f, 1f)
            }
        return ChallengeSummary(
            totalHits = hits,
            validHits = hits,
            missedBeats = 0,
            accuracy = completionRate,
            hps = pps,
        )
    }

    private fun text(
        zh: String,
        en: String,
        fr: String? = null,
        th: String? = null,
    ): String = fatBurnChallengeText(languageCode, zh, en, fr, th)

    private fun displayRemaining(remainingMs: Long): String {
        val safeMs = remainingMs.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes <= 0) {
            "${seconds}${text("秒", "s")}"
        } else {
            "${minutes}${text("分", "m")} ${seconds}${text("秒", "s")}"
        }
    }

    private fun formatDecimal(
        value: Float,
        digits: Int,
    ): String = String.format(Locale.US, "%.${digits}f", value)

    private fun dayKey(timestampMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestampMs))

    private fun parseDayKey(value: String): Long =
        runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)?.time ?: dayStart(System.currentTimeMillis())
        }.getOrElse { dayStart(System.currentTimeMillis()) }

    private fun dayStart(timestampMs: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timestampMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun sameDay(
        timestampA: Long,
        timestampB: Long,
    ): Boolean = dayKey(timestampA) == dayKey(timestampB)

    private fun sameWeek(
        timestampA: Long,
        timestampB: Long,
    ): Boolean {
        val calendarA =
            Calendar.getInstance().apply {
                timeInMillis = timestampA
                firstDayOfWeek = Calendar.MONDAY
            }
        val calendarB =
            Calendar.getInstance().apply {
                timeInMillis = timestampB
                firstDayOfWeek = Calendar.MONDAY
            }
        return calendarA.get(Calendar.YEAR) == calendarB.get(Calendar.YEAR) &&
            calendarA.get(Calendar.WEEK_OF_YEAR) == calendarB.get(Calendar.WEEK_OF_YEAR)
    }

    private fun sameMonth(
        timestampA: Long,
        timestampB: Long,
    ): Boolean {
        val calendarA = Calendar.getInstance().apply { timeInMillis = timestampA }
        val calendarB = Calendar.getInstance().apply { timeInMillis = timestampB }
        return calendarA.get(Calendar.YEAR) == calendarB.get(Calendar.YEAR) &&
            calendarA.get(Calendar.MONTH) == calendarB.get(Calendar.MONTH)
    }

    private fun commandButton(
        label: String,
        fillColor: String,
        strokeColor: String,
    ): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            includeFontPadding = false
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            minLines = 1
            maxLines = 2
            setLineSpacing(0f, 1.06f)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 11, 15, 1, TypedValue.COMPLEX_UNIT_SP)
            background = roundedFill(fillColor, strokeColor, 20)
            elevation = dp(2).toFloat()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val EXTRA_SENSITIVITY_LEVEL = "extra_sensitivity_level"
        const val EXTRA_LANGUAGE = "extra_language"
        const val EXTRA_AUTH_SERIAL = "extra_auth_serial"

        private const val PREFS_NAME = "fat_burn_challenge"
        private const val KEY_AUTH_SERIAL = "auth_serial"
        private const val KEY_PROGRAM_START_DAY = "program_start_day"
        private const val KEY_DAILY_REPORTS_JSON = "daily_reports_json"
        private const val MAX_DETECTOR_RECOVERY_ATTEMPTS = 8
        private const val DAY_MS = 86_400_000L
        private const val DEFAULT_BASE_WEIGHT_KG = 68f
        private const val DAILY_TRAINING_VOLUME_MULTIPLIER = 2
        private val SUPER_PLAN_TEST_SERIALS =
            setOf(
                "02260400011",
                "02260400029",
            )
    }
}












