package com.zclei.boxingfitness

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TabStopSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.zclei.boxingfitness.auth.ActivationApiResult
import com.zclei.boxingfitness.auth.ActivationService
import com.zclei.boxingfitness.auth.ActivationState
import com.zclei.boxingfitness.cloud.CloudBootstrapResult
import com.zclei.boxingfitness.cloud.CloudAchievementItem
import com.zclei.boxingfitness.cloud.CloudLeaderboardEntry
import com.zclei.boxingfitness.cloud.CloudLeaderboardResult
import com.zclei.boxingfitness.cloud.CloudSyncService
import com.zclei.boxingfitness.cloud.CloudTrainingUploader
import com.zclei.boxingfitness.cloud.CloudTierProgress
import com.zclei.boxingfitness.cloud.CloudTrainingHistoryItem
import com.zclei.boxingfitness.cloud.CloudUserProfile
import com.zclei.boxingfitness.cloud.CloudUserStatistics
import com.zclei.boxingfitness.model.AppLanguage
import com.zclei.boxingfitness.model.TrainingMode
import com.zclei.boxingfitness.model.TrainingReport
import com.zclei.boxingfitness.ui.Haptics
import com.zclei.boxingfitness.ui.HistoryItemAdapter
import com.zclei.boxingfitness.ui.LeaderboardRowAdapter
import com.zclei.boxingfitness.ui.VerticalSpacingDecoration
import com.zclei.boxingfitness.ui.applyRippleOverlay
import java.text.SimpleDateFormat
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Date
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private val avatarPalette =
        listOf("#CC4400", "#E07010", "#FFAA40", "#FFD060", "#A63A10", "#7A1400", "#8B5E3C", "#C06014")
    private enum class HomePage {
        TrainingCenter,
        TrainingAchievements,
        Leaderboard,
        Profile,
    }

    private enum class LeaderboardBoard(val apiKey: String) {
        DailyBestHits("daily_best_hits"),
        TotalHits("total_hits"),
        TotalDuration("total_duration"),
        TotalCalories("total_calories"),
        TotalFatBurn("total_fat_burn"),
    }

    private var selectedMode: TrainingMode = TrainingMode.Seconds30
    private var selectedPlayMode: TrainingPlayMode = TrainingPlayMode.Classic30
    private var lastCoachMessage: String? = null
    private var lastCoachOutcome: TrainingCoachOutcome? = null
    private var selectedLanguage: AppLanguage = defaultLanguage()
    private var sensitivityLevel: Int = DEFAULT_SENSITIVITY
    private var selectedHomePage: HomePage = HomePage.TrainingCenter
    private var lastBackPressedAtMs: Long = 0L
    private var trainingJob: Job? = null
    private var activationJob: Job? = null
    private var currentEngine: ReflexBallTrainingEngine? = null
    private var boxingBleManager: BoxingBleManager? = null
    private var selectedBleDevice: BoxingBleManager.DeviceCandidate? = null
    private var latestBlePacket: BoxingBleManager.BoxingPacket? = null
    private var boxingBleUiListener: BoxingBleManager.Listener? = null
    private var boxingBleConnected = false
    private var leftBleBatteryPercent: Int? = null
    private var rightBleBatteryPercent: Int? = null
    private var pendingBoxingBleAutoConnectAfterPermission = false
    private var boxingBleAutoConnectAttempted = false
    private var boxingBleAutoConnectTimeoutRunnable: Runnable? = null
    private var bleTrainingHitCount = 0
    private var bleTrainingStartedAtMs = 0L
    private val bleTrainingHitTimes = mutableListOf<Float>()
    private val mainTrainingBleHitListener =
        BoxingBleRuntime.HitListener { packet ->
            runOnUiThread {
                repeat(packet.hitDelta.coerceIn(1, 12)) {
                    handleBleTrainingHit()
                }
            }
        }
    private var lastDisplayedCount = 0
    private var lastSpokenCountdown: Int? = null
    private var goSpoken = false
    private var tts: TextToSpeech? = null
    private var ttsInitialized = false
    private var ttsReady = false
    private var ttsLocaleInUse: Locale? = null
    private val ttsCompletionCallbacks = ConcurrentHashMap<String, () -> Unit>()
    private var latestReport: TrainingReport? = null
    private var calibrationRetrySuggested = false
    private var activationState: ActivationState? = null
    private var installId: String = ""
    private var deviceHash: String = ""
    private var authStatusMessageKey: String? = null
    private var authStatusFallbackMessage: String? = null
    private var authStatusColor: Int = Color.parseColor("#FFD060")
    private var cloudJob: Job? = null
    private var leaderboardJob: Job? = null
    private var cloudProfile: CloudUserProfile? = null
    private var cloudStatistics: CloudUserStatistics? = null
    private var cloudHistory: List<CloudTrainingHistoryItem> = emptyList()
    private var cloudAchievements: List<CloudAchievementItem> = emptyList()
    private var cloudTier: CloudTierProgress? = null
    private var leaderboardResult: CloudLeaderboardResult? = null
    private var leaderboardBoard: LeaderboardBoard = LeaderboardBoard.DailyBestHits
    private var cloudStatusMessageKey: String? = null
    private var cloudStatusFallbackMessage: String? = null
    private var cloudStatusColor: Int = Color.parseColor("#FFD060")
    private var pendingAvatarSelection: ((Uri?) -> Unit)? = null
    private var autoRestoreAttempted = false
    private var celebrationShowing = false
    private var dismissingCelebrationForTraining = false
    private var activeCelebrationDialog: AlertDialog? = null
    private val celebrationQueue: ArrayDeque<() -> Unit> = ArrayDeque()

    private val activationService = ActivationService()
    private val cloudSyncService = CloudSyncService()

    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var promotionBannerView: TextView
    private lateinit var trainingHeroCard: LinearLayout
    private lateinit var trainingHeroBadgeView: TextView
    private lateinit var trainingHeroHeadlineView: TextView
    private lateinit var trainingHeroSummaryView: TextView
    private lateinit var trainingHeroInsightView: TextView
    private lateinit var trainingHeroProgressView: TextView
    private lateinit var shareTrainingButton: Button
    private lateinit var modeTitleView: TextView
    private lateinit var emotionModesTitleView: TextView
    private lateinit var fitnessModesTitleView: TextView
    private lateinit var reportTitleView: TextView
    private lateinit var profileTitleView: TextView
    private lateinit var profileSubtitleView: TextView
    private lateinit var profileCard: LinearLayout
    private lateinit var profileAvatarShell: FrameLayout
    private lateinit var profileAvatarImageView: ImageView
    private lateinit var profileAvatarFallbackView: TextView
    private lateinit var profileHeroTagView: TextView
    private lateinit var profileHeroBadgeView: TextView
    private lateinit var profileSummaryView: TextView
    private lateinit var profileMetaView: TextView
    private lateinit var profileTierView: TextView
    private lateinit var profileStatsView: TextView
    private lateinit var profileBadgesView: TextView
    private lateinit var cloudStatusView: TextView
    private lateinit var editProfileButton: Button
    private lateinit var refreshCloudButton: Button
    private lateinit var debugLogExportButton: Button
    private lateinit var developerInfoButton: Button
    private lateinit var historyTitleView: TextView
    private lateinit var historySubtitleView: TextView
    private lateinit var historyCard: LinearLayout
    private lateinit var historyListRecycler: RecyclerView
    private lateinit var historyEmptyView: LinearLayout
    private lateinit var historyItemAdapter: HistoryItemAdapter
    private lateinit var historyView: TextView
    private lateinit var leaderboardTitleView: TextView
    private lateinit var leaderboardSubtitleView: TextView
    private lateinit var leaderboardCard: LinearLayout
    private lateinit var leaderboardPodiumContainer: LinearLayout
    private lateinit var leaderboardListRecycler: RecyclerView
    private lateinit var leaderboardRowAdapter: LeaderboardRowAdapter
    private lateinit var leaderboardMeCard: LinearLayout
    private lateinit var leaderboardMeTitleView: TextView
    private lateinit var leaderboardMeView: TextView
    private lateinit var shareLeaderboardButton: Button
    private lateinit var leaderboardModeGroup: RadioGroup
    private lateinit var leaderboard30Button: RadioButton
    private lateinit var leaderboard60Button: RadioButton
    private lateinit var leaderboardTotalHitsButton: RadioButton
    private lateinit var leaderboardStreakButton: RadioButton
    private lateinit var leaderboardFatBurnButton: RadioButton
    private lateinit var refreshLeaderboardButton: Button
    private lateinit var leaderboardView: TextView
    private lateinit var achievementsTitleView: TextView
    private lateinit var achievementsSubtitleView: TextView
    private lateinit var achievementsCard: LinearLayout
    private lateinit var achievementsGridContainer: LinearLayout
    private lateinit var achievementsSummaryView: TextView
    private lateinit var shareAchievementsButton: Button
    private lateinit var trainingLiveCard: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var countdownView: TextView
    private lateinit var countView: TextView
    private lateinit var remainingView: TextView
    private lateinit var reportView: LinearLayout
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var headerBleStatusView: BluetoothStatusIndicatorView
    private lateinit var headerBleLeftBatteryView: BatteryStatusIndicatorView
    private lateinit var headerBleRightBatteryView: BatteryStatusIndicatorView
    private lateinit var settingsButton: ImageButton
    private lateinit var quietIconView: ImageView
    private lateinit var modeGroup: RadioGroup
    private lateinit var mode30Button: RadioButton
    private lateinit var mode60Button: RadioButton
    private lateinit var modeBurst10Button: RadioButton
    private lateinit var modeBurst15Button: RadioButton
    private lateinit var modeLevelButton: RadioButton
    private lateinit var modeDailyButton: RadioButton
    private lateinit var trainingPlayCard: LinearLayout
    private lateinit var trainingPlayTitleView: TextView
    private lateinit var trainingPlayBodyView: TextView
    private lateinit var trainingPlayProgressView: TextView
    private lateinit var boxingBleCard: LinearLayout
    private lateinit var boxingBleStatusView: TextView
    private lateinit var boxingBleDeviceView: TextView
    private lateinit var boxingBleDeviceListView: RadioGroup
    private lateinit var boxingBleMetricView: TextView
    private lateinit var boxingBleScanButton: Button
    private lateinit var boxingBleConnectButton: Button
    private lateinit var boxingBleDisconnectButton: Button
    private lateinit var boxingBleGyroOnButton: Button
    private lateinit var boxingBleGyroOffButton: Button
    private var sensitivitySeekBar: SeekBar? = null
    private var sensitivityValueView: TextView? = null
    private var sensitivityDeviceStatusView: TextView? = null

    private lateinit var activationCard: LinearLayout
    private lateinit var activationTitleView: TextView
    private lateinit var activationHintView: TextView
    private lateinit var serialInput: EditText
    private lateinit var codeInput: EditText
    private lateinit var serialInputErrorView: TextView
    private lateinit var codeInputErrorView: TextView
    private var activationInputsValid: Boolean = false
    private lateinit var activateButton: Button
    private lateinit var authStatusView: TextView
    private lateinit var activationDetailsView: TextView
    private lateinit var pageTabsCard: LinearLayout
    private lateinit var pageTrainingButton: TextView
    private lateinit var pageAchievementsButton: TextView
    private lateinit var pageLeaderboardButton: TextView
    private lateinit var pageProfileButton: TextView
    private lateinit var pageHost: FrameLayout
    private lateinit var contentRootView: LinearLayout
    private lateinit var trainingWatermarkPage: FrameLayout
    private lateinit var trainingSwipe: SwipeRefreshLayout
    private lateinit var achievementsSwipe: SwipeRefreshLayout
    private lateinit var leaderboardSwipe: SwipeRefreshLayout
    private lateinit var profileSwipe: SwipeRefreshLayout
    private lateinit var pageTrainingContainer: LinearLayout
    private lateinit var pageAchievementsContainer: LinearLayout
    private lateinit var pageLeaderboardContainer: LinearLayout
    private lateinit var pageProfileContainer: LinearLayout
    private val hideActivationCardRunnable =
        Runnable {
            if (isActivated() && trainingJob?.isActive != true) {
                setActivationVisible(false)
                clearAuthStatusMessage()
            }
        }

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private val boxingBlePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val manager = boxingBleManager ?: return@registerForActivityResult
            if (manager.requiredPermissions().all { grants[it] == true || ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
                if (pendingBoxingBleAutoConnectAfterPermission) {
                    pendingBoxingBleAutoConnectAfterPermission = false
                    attemptBoxingBleAutoConnect()
                } else {
                    manager.startScan()
                }
            } else {
                pendingBoxingBleAutoConnectAfterPermission = false
                updateBoxingBleStatus("蓝牙权限未授予，无法扫描设备")
            }
        }

    private val avatarPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val callback = pendingAvatarSelection
            pendingAvatarSelection = null
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                }
            }
            callback?.invoke(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        loadSettings()
        ensureInstallIdentity()
        loadActivationState()
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
        setupBoxingBleManager()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleMainBackPressed()
                }
            },
        )
        if (handleDebugAutomationRoute(intent)) {
            return
        }
        initTextToSpeech()
        renderIdle()
        verifyActivationInBackground()
        showFirstLaunchBlePromptIfNeeded()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleMainBackPressed()
    }

    private fun handleMainBackPressed() {
        if (selectedHomePage != HomePage.TrainingCenter) {
            selectHomePage(HomePage.TrainingCenter)
            setCloudStatusMessage(
                "#FFD060",
                fallback =
                    localText(
                        "已返回锻炼中心，再按一次退出 APP。",
                        "Returned to Training Center. Press back again to exit.",
                        "Retour au centre d'entraînement. Appuyez encore pour quitter.",
                        "กลับสู่ศูนย์ฝึกแล้ว กดกลับอีกครั้งเพื่อออกจากแอป",
                    ),
            )
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastBackPressedAtMs <= 1800L) {
            trainingJob?.cancel()
            disconnectBoxingBlePairForAppExit()
            moveTaskToBack(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask()
            } else {
                finish()
            }
            return
        }
        lastBackPressedAtMs = now
        setCloudStatusMessage(
            "#FFD060",
            fallback =
                localText(
                    "再按一次退出 APP。",
                    "Press back again to exit the app.",
                    "Appuyez encore une fois pour quitter l'application.",
                    "กดกลับอีกครั้งเพื่อออกจากแอป",
                ),
        )
    }

    private fun showFirstLaunchBlePromptIfNeeded() {
        if (prefs.getBoolean(KEY_FIRST_LAUNCH_BLE_PROMPT_SHOWN, false)) {
            return
        }
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_BLE_PROMPT_SHOWN, true).apply()
        contentRootView.post {
            if (isFinishing || isDestroyed) {
                return@post
            }
            AlertDialog.Builder(this)
                .setMessage(firstLaunchBlePromptMessage())
                .setPositiveButton(goToSettingsLabel()) { _, _ -> showFormalSettingsDialog() }
                .setNegativeButton(laterLabel(), null)
                .show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugAutomationRoute(intent)
    }

    override fun onResume() {
        super.onResume()
        boxingBleUiListener?.let {
            BoxingBleRuntime.removeBleListener(it)
            BoxingBleRuntime.addBleListener(it)
        }
        if (::reportView.isInitialized) {
            renderLatestReportPage()
        }
        if (::achievementsGridContainer.isInitialized) {
            renderAchievements()
        }
        if (isActivated() && activationState != null) {
            CloudTrainingUploader.flushPendingIfAvailable(this, lifecycleScope) { flushedCount ->
                if (flushedCount > 0) {
                    refreshCloudData(forceLeaderboard = true)
                }
            }
        }
    }

    override fun onPause() {
        boxingBleUiListener?.let { BoxingBleRuntime.removeBleListener(it) }
        super.onPause()
    }

    override fun onDestroy() {
        if (trainingJob?.isActive == true) {
            BoxingBleRuntime.disableGyro()
        }
        if (isFinishing && !isChangingConfigurations) {
            disconnectBoxingBlePairForAppExit()
        }
        currentEngine?.cancel()
        trainingJob?.cancel()
        BoxingBleRuntime.removeHitListener(mainTrainingBleHitListener)
        boxingBleUiListener?.let { BoxingBleRuntime.removeBleListener(it) }
        boxingBleUiListener = null
        cancelBoxingBleAutoConnectTimeout()
        activationJob?.cancel()
        cloudJob?.cancel()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun buildContentView(): View {
        val root =
            FrameLayout(this).apply {
                setBackgroundColor(Color.parseColor("#140800"))
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }
        val contentRoot =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }
        contentRootView = contentRoot
        val topContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(24), dp(20), dp(0))
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            }
        pageHost =
            FrameLayout(this).apply {
                setBackgroundColor(Color.parseColor("#140800"))
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f,
                    )
            }

        val headerRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        val headerTextColumn =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.0f,
                    )
            }

        titleView =
            titleText("", 26f).apply {
                gravity = Gravity.START
            }
        subtitleView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#E9D2A2"))
                setPadding(0, dp(8), dp(12), 0)
            }
        headerTextColumn.addView(titleView)

        val headerBlePanel =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(dp(6), dp(4), dp(6), dp(4))
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(4)
                    }
            }
        headerBleStatusView =
            BluetoothStatusIndicatorView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                contentDescription = bleDisconnectedLabel()
                setConnected(false)
            }
        headerBleLeftBatteryView =
            BatteryStatusIndicatorView(this, bleLeftShortLabel()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(30)).apply {
                    leftMargin = dp(5)
                }
                setBattery(null)
            }
        headerBleRightBatteryView =
            BatteryStatusIndicatorView(this, bleRightShortLabel()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(30)).apply {
                    leftMargin = dp(4)
                }
                setBattery(null)
        }
        headerBlePanel.addView(headerBleStatusView)
        headerBlePanel.addView(headerBleLeftBatteryView)
        headerBlePanel.addView(headerBleRightBatteryView)
        headerTextColumn.addView(headerBlePanel)
        headerTextColumn.addView(subtitleView)
        headerRow.addView(headerTextColumn)

        settingsButton =
            ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_manage)
                setBackgroundColor(Color.TRANSPARENT)
                setColorFilter(Color.parseColor("#FFF5E6"))
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener {
                    if (trainingJob?.isActive != true) {
                        showFormalSettingsDialog()
                    }
                }
            }
        headerRow.addView(settingsButton)
        topContainer.addView(headerRow)

        promotionBannerView =
            bodyText("").apply {
                visibility = View.GONE
                setTextColor(Color.WHITE)
                setTypeface(Typeface.DEFAULT_BOLD)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = roundedBackground("#E07010", "#FFB347", 22)
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(12)
                        bottomMargin = dp(8)
                    }
                alpha = 0f
                translationY = -dp(12).toFloat()
            }
        topContainer.addView(promotionBannerView)

        activationCard =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = surfaceCardBackground()
                setPadding(dp(16), dp(16), dp(16), dp(16))
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(12)
                        bottomMargin = dp(12)
                    }
            }
        activationTitleView = sectionLabel("")
        activationHintView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#E9D2A2"))
                setPadding(0, 0, 0, dp(10))
            }
        serialInput =
            activationInput("").apply {
                filters = arrayOf(InputFilter.LengthFilter(11))
            }
        serialInputErrorView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#FFAA40"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(2), dp(4), 0, 0)
                visibility = View.GONE
            }
        codeInput =
            activationInput("").apply {
                filters = arrayOf(InputFilter.LengthFilter(8))
            }
        codeInputErrorView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#FFAA40"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(2), dp(4), 0, 0)
                visibility = View.GONE
            }
        activateButton =
            actionButton("", "#CC4400").apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                setOnClickListener { activateDevice() }
            }
        serialInput.doAfterTextChanged {
            updateActivationInputState()
        }
        codeInput.doAfterTextChanged {
            updateActivationInputState()
        }
        authStatusView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#FFD060"))
                setPadding(0, dp(10), 0, 0)
            }
        activationDetailsView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#F2D8A7"))
                setPadding(0, dp(12), 0, 0)
                visibility = View.GONE
            }
        activationCard.addView(activationTitleView)
        activationCard.addView(activationHintView)
        activationCard.addView(serialInput)
        activationCard.addView(serialInputErrorView)
        activationCard.addView(spacer(dp(8)))
        activationCard.addView(codeInput)
        activationCard.addView(codeInputErrorView)
        activationCard.addView(spacer(dp(12)))
        activationCard.addView(activateButton)
        activationCard.addView(authStatusView)
        activationCard.addView(activationDetailsView)
        topContainer.addView(activationCard)
        updateActivationInputState()

        pageTabsCard =
            surfaceCard().apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(4)
                    }
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = bottomNavBackground()
                elevation = dp(6).toFloat()
            }
        pageTrainingButton = homePageButton { selectHomePage(HomePage.TrainingCenter) }
        pageAchievementsButton = homePageButton { selectHomePage(HomePage.TrainingAchievements) }
        pageLeaderboardButton = homePageButton { selectHomePage(HomePage.Leaderboard) }
        pageProfileButton = homePageButton { selectHomePage(HomePage.Profile) }
        pageTabsCard.addView(pageTrainingButton)
        pageTabsCard.addView(horizontalSpace(dp(8)))
        pageTabsCard.addView(pageAchievementsButton)
        pageTabsCard.addView(horizontalSpace(dp(8)))
        pageTabsCard.addView(pageLeaderboardButton)
        pageTabsCard.addView(horizontalSpace(dp(8)))
        pageTabsCard.addView(pageProfileButton)
        pageTrainingContainer = pageContentContainer().apply {
            setPadding(dp(20), dp(2), dp(20), dp(24))
        }
        trainingSwipe = wrapInSwipeRefresh(pageTrainingContainer, enabled = false)
        trainingWatermarkPage = buildTrainingWatermarkPage(trainingSwipe)
        pageHost.addView(trainingWatermarkPage)

        pageAchievementsContainer = pageContentContainer().apply {
            setPadding(dp(20), dp(8), dp(20), dp(24))
        }
        achievementsSwipe = wrapInSwipeRefresh(pageAchievementsContainer, enabled = true) {
            refreshCloudData(forceLeaderboard = false)
        }
        pageHost.addView(achievementsSwipe)

        pageLeaderboardContainer = pageContentContainer().apply {
            setPadding(dp(20), dp(8), dp(20), dp(24))
        }
        leaderboardSwipe = wrapInSwipeRefresh(pageLeaderboardContainer, enabled = true) {
            refreshLeaderboardOnly()
        }
        pageHost.addView(leaderboardSwipe)

        pageProfileContainer = pageContentContainer().apply {
            setPadding(dp(20), dp(8), dp(20), dp(24))
        }
        profileSwipe = wrapInSwipeRefresh(pageProfileContainer, enabled = true) {
            refreshCloudData(forceLeaderboard = true)
        }
        pageHost.addView(profileSwipe)

        trainingHeroCard =
            detailCard(fillColor = "#102735", strokeColor = "#2D627E", cornerDp = 26).apply {
                background = heroBackground("#CC4400")
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = 0
                        bottomMargin = dp(6)
                    }
            }
        trainingHeroBadgeView =
            badgeText(
                text = "",
                textColor = "#140800",
                fillColor = "#FFD060",
            ).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            }
        trainingHeroHeadlineView =
            titleText("", 28f).apply {
                gravity = Gravity.START
                setTextColor(Color.parseColor("#FFF8E8"))
                setPadding(0, dp(14), 0, 0)
            }
        trainingHeroSummaryView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#F2D8A7"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(8), 0, 0)
            }
        trainingHeroInsightView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#FFF3D3"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(10), 0, 0)
            }
        trainingHeroProgressView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#FFD060"))
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(12), 0, 0)
            }
        shareTrainingButton =
            compactActionButton("", "#16384A").apply {
                setOnClickListener { shareTrainingSummary() }
            }
        trainingHeroCard.addView(trainingHeroBadgeView)
        trainingHeroCard.addView(trainingHeroHeadlineView)
        trainingHeroCard.addView(trainingHeroSummaryView)
        trainingHeroCard.addView(trainingHeroInsightView)
        trainingHeroCard.addView(trainingHeroProgressView)

        val trainingControlShell =
            FrameLayout(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                background = surfaceCardBackground()
                elevation = dp(3).toFloat()
                outlineProvider =
                    object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
                        }
                    }
                clipToOutline = true
                addView(
                    ImageView(this@MainActivity).apply {
                        setImageResource(R.drawable.training_center_watermark)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        alpha = 0.56f
                        layoutParams =
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                    },
                )
                addView(
                    View(this@MainActivity).apply {
                        background =
                            GradientDrawable(
                                GradientDrawable.Orientation.TL_BR,
                                intArrayOf(
                                    Color.argb(204, 7, 15, 23),
                                    Color.argb(156, 11, 29, 40),
                                    Color.argb(196, 6, 13, 20),
                                ),
                            ).apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dp(24).toFloat()
                            }
                        layoutParams =
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                    },
                )
            }
        val trainingControlCard =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(12), dp(18), dp(8))
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            }

        modeTitleView = sectionTitle("").apply { visibility = View.GONE }
        emotionModesTitleView =
            bodyText("").apply {
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.parseColor("#88E7F4"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(0, dp(4), 0, dp(10))
            }
        fitnessModesTitleView =
            bodyText("").apply {
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.parseColor("#FFC778"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(0, dp(14), 0, dp(10))
            }

        modeGroup =
            RadioGroup(this).apply {
                orientation = RadioGroup.VERTICAL
                gravity = Gravity.START
                setPadding(0, 0, 0, dp(8))
            }
        mode30Button =
            RadioButton(this).apply {
                id = View.generateViewId()
                isChecked = true
                setTextColor(Color.WHITE)
                minHeight = dp(48)
                minWidth = dp(48)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
        mode60Button =
            RadioButton(this).apply {
                id = View.generateViewId()
                setTextColor(Color.WHITE)
                minHeight = dp(48)
                minWidth = dp(48)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
        modeBurst10Button =
            RadioButton(this).apply {
                id = View.generateViewId()
                setTextColor(Color.WHITE)
                minHeight = dp(48)
                minWidth = dp(48)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
        modeBurst15Button =
            RadioButton(this).apply {
                id = View.generateViewId()
                setTextColor(Color.WHITE)
                minHeight = dp(48)
                minWidth = dp(48)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
        modeLevelButton =
            RadioButton(this).apply {
                id = View.generateViewId()
                setTextColor(Color.WHITE)
                minHeight = dp(48)
                minWidth = dp(48)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
        modeDailyButton =
            RadioButton(this).apply {
                id = View.generateViewId()
                setTextColor(Color.WHITE)
                minHeight = dp(48)
                minWidth = dp(48)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
        configureModeButton(mode30Button)
        configureModeButton(mode60Button)
        configureModeButton(modeBurst10Button)
        configureModeButton(modeBurst15Button)
        configureModeButton(modeLevelButton)
        configureModeButton(modeDailyButton)
        modeGroup.addView(emotionModesTitleView)
        modeGroup.addView(mode60Button)
        modeGroup.addView(modeBurst10Button)
        modeGroup.addView(mode30Button)
        modeGroup.addView(fitnessModesTitleView)
        modeGroup.addView(modeBurst15Button)
        modeGroup.addView(modeLevelButton)
        modeGroup.addView(modeDailyButton)
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedPlayMode = trainingPlayModeForCheckedId(checkedId)
            selectedMode = modeForPlayMode(selectedPlayMode)
            prefs.edit().putString(KEY_SELECTED_PLAY_MODE, selectedPlayMode.name).apply()
            if (trainingJob?.isActive != true) {
                remainingView.text = displayRemaining(selectedMode.durationSeconds * 1_000L)
            }
            refreshModeButtonStyles()
            renderTrainingPlayStatus()
        }
        mode30Button.setOnClickListener {
            launchFreeBoxingMode()
        }
        mode60Button.setOnClickListener {
            launchColorGraffitiMode()
        }
        modeBurst10Button.setOnClickListener {
            launchEmotionChampMode()
        }
        modeBurst15Button.setOnClickListener {
            launchBlitzMode()
        }
        modeLevelButton.setOnClickListener {
            launchFatBurnChallengeMode()
        }
        modeDailyButton.setOnClickListener {
            launchFatBurnCoachMode()
        }

        trainingPlayCard =
            detailCard(fillColor = "#0B1B27", strokeColor = "#2E5E78", cornerDp = 22).apply {
                background = metallicBackground("#142F42", "#08131C", "#FF9A30", 22)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(4)
                        bottomMargin = 0
                    }
            }
        trainingPlayTitleView =
            titleText("", 18f).apply {
                gravity = Gravity.START
                setTextColor(Color.parseColor("#FFF8E8"))
            }
        trainingPlayBodyView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#E5C98A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setPadding(0, dp(8), 0, 0)
            }
        trainingPlayProgressView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#FFF3D3"))
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(10), 0, 0)
            }
        trainingPlayCard.addView(trainingPlayTitleView)
        trainingPlayCard.addView(trainingPlayBodyView)
        trainingPlayCard.addView(trainingPlayProgressView)

        quietIconView =
            ImageView(this).apply {
                setImageResource(android.R.drawable.ic_lock_silent_mode)
                setColorFilter(Color.parseColor("#FFD060"))
                layoutParams =
                    LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                visibility = View.GONE
                alpha = 0.95f
            }
        statusView =
            bodyText("").apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(6), 0, dp(6))
            }

        countdownView =
            titleText("3", 40f).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#FFD060"))
                setPadding(0, dp(6), 0, dp(2))
            }

        countView =
            titleText("0", 72f).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(0, dp(6), 0, dp(2))
            }

        remainingView =
            bodyText("").apply {
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#FFB347"))
                setPadding(0, 0, 0, dp(12))
            }

        startButton =
            actionButton("", "#E07010").apply {
                setOnClickListener { ensurePermissionAndStart() }
            }
        stopButton =
            actionButton("", "#A73A54").apply {
                isEnabled = false
                alpha = 0.5f
                setOnClickListener { stopTraining(showStoppedState = true) }
            }
        trainingLiveCard =
            surfaceCard().apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding(dp(18), dp(16), dp(18), dp(16))
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(12)
                    }
            }
        trainingLiveCard.addView(quietIconView)
        trainingLiveCard.addView(statusView)
        trainingLiveCard.addView(countdownView)
        trainingLiveCard.addView(countView)
        trainingLiveCard.addView(remainingView)
        trainingLiveCard.addView(
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(
                    stopButton.apply {
                        layoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                    },
                )
            },
        )
        trainingControlCard.addView(trainingLiveCard)
        trainingControlCard.addView(modeGroup)
        trainingControlShell.addView(trainingControlCard)
        pageTrainingContainer.addView(trainingControlShell)

        reportTitleView = sectionTitle("")
        pageTrainingContainer.addView(reportTitleView)
        reportView =
            surfaceCard().apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(6)
                    }
            }
        pageTrainingContainer.addView(reportView)

        profileTitleView = sectionTitle("")
        pageProfileContainer.addView(profileTitleView)
        profileSubtitleView = sectionSubtitle("")
        pageProfileContainer.addView(profileSubtitleView)
        profileCard =
            surfaceCard().apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(6)
                        bottomMargin = dp(8)
                    }
            }
        val profileHeroShell =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = metallicBackground("#2C5B76", "#1A0C00", "#D9B870", 28)
                setPadding(dp(20), dp(20), dp(20), dp(20))
            }
        val profileHeroRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, 0)
            }
        profileAvatarShell =
            FrameLayout(this).apply {
                background = avatarBackground("#CC4400")
                clipToOutline = true
                elevation = dp(4).toFloat()
                layoutParams =
                    LinearLayout.LayoutParams(dp(74), dp(74)).apply {
                        rightMargin = dp(16)
                    }
            }
        profileAvatarImageView =
            ImageView(this).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                visibility = View.GONE
            }
        profileAvatarFallbackView =
            TextView(this).apply {
                text = "R"
                gravity = Gravity.CENTER
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }
        profileAvatarShell.addView(profileAvatarImageView)
        profileAvatarShell.addView(profileAvatarFallbackView)
        val profileHeadlineColumn =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.0f,
                    )
            }
        profileHeroBadgeView =
            badgeText(
                text = "",
                textColor = "#FFF5E6",
                fillColor = "#153244",
            ).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            }
        profileSummaryView =
            titleText("", 24f).apply {
                gravity = Gravity.START
                setTextColor(Color.parseColor("#FFF5E6"))
            }
        profileMetaView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#B88A54"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(4), 0, 0)
            }
        profileTierView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#FFD060"))
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(10), 0, 0)
            }
        profileStatsView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#F7E8C5"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                setLineSpacing(0f, 1.22f)
                background = metallicBackground("#173345", "#0B151D", "#305067", 22)
                setPadding(dp(16), dp(15), dp(16), dp(15))
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(14)
                    }
            }
        profileBadgesView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#E7C896"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setLineSpacing(0f, 1.18f)
                background = metallicBackground("#342514", "#140A04", "#7C5B2A", 22)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(10)
                    }
            }
        cloudStatusView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#FFD060"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(Typeface.DEFAULT_BOLD)
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
        profileHeadlineColumn.addView(profileHeroBadgeView)
        profileHeadlineColumn.addView(profileSummaryView)
        profileHeadlineColumn.addView(profileMetaView)
        profileHeadlineColumn.addView(profileTierView)
        profileHeroTagView =
            TextView(this).apply {
                text = profileHeroTagText()
                setTextColor(Color.parseColor("#140800"))
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                background = metallicBackground("#FFE8A8", "#C7932B", "#FFF2CD", 999)
                setPadding(dp(12), dp(6), dp(12), dp(6))
            }
        profileHeroShell.addView(profileHeroTagView)
        profileHeroRow.addView(profileAvatarShell)
        profileHeroRow.addView(profileHeadlineColumn)
        profileHeroShell.addView(profileHeroRow)
        val profileActionRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                setPadding(0, dp(16), 0, 0)
            }
        editProfileButton =
            compactActionButton("", "#16384A").apply {
                setOnClickListener { showEditProfileDialog() }
            }
        refreshCloudButton =
            compactActionButton("", "#E07010").apply {
                setOnClickListener {
                    profileSwipe.isRefreshing = true
                    refreshCloudData(forceLeaderboard = true)
                }
            }
        profileActionRow.addView(editProfileButton)
        profileActionRow.addView(horizontalSpace(dp(12)))
        profileActionRow.addView(refreshCloudButton)
        debugLogExportButton =
            compactActionButton("", "#A76F18").apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(10)
                    }
                setOnClickListener { shareDebugLogs() }
            }
        developerInfoButton =
            compactActionButton("", "#16384A").apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(12)
                    }
                setOnClickListener { showDeveloperInfoDialog() }
            }
        profileCard.addView(profileHeroShell)
        profileCard.addView(profileStatsView)
        profileCard.addView(profileBadgesView)
        profileCard.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                setPadding(0, dp(14), 0, 0)
                addView(cloudStatusView)
            },
        )
        profileCard.addView(profileActionRow)
        profileCard.addView(debugLogExportButton)
        profileCard.addView(developerInfoButton)
        pageProfileContainer.addView(profileCard)

        achievementsTitleView = sectionTitle("")
        pageAchievementsContainer.addView(achievementsTitleView)
        achievementsSubtitleView = sectionSubtitle("")
        pageAchievementsContainer.addView(achievementsSubtitleView)
        achievementsCard =
            surfaceCard().apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(6)
                        bottomMargin = dp(8)
                    }
            }
        achievementsSummaryView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#F2D8A7"))
            }
        shareAchievementsButton =
            compactActionButton("", "#16384A").apply {
                setOnClickListener { shareAchievementsSummary() }
            }
        val achievementsHeaderRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        achievementsSummaryView.layoutParams =
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f,
            )
        achievementsHeaderRow.addView(achievementsSummaryView)
        achievementsHeaderRow.addView(shareAchievementsButton)
        achievementsGridContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(14), 0, 0)
            }
        achievementsCard.addView(achievementsHeaderRow)
        achievementsCard.addView(achievementsGridContainer)
        pageAchievementsContainer.addView(achievementsCard)

        historyTitleView = sectionTitle("")
        pageAchievementsContainer.addView(historyTitleView)
        historySubtitleView = sectionSubtitle("")
        pageAchievementsContainer.addView(historySubtitleView)
        historyCard = surfaceCard().apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(6)
                    bottomMargin = dp(8)
                    }
        }
        historyItemAdapter = HistoryItemAdapter { item -> historySessionCard(item) }
        historyListRecycler =
            RecyclerView(this).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = historyItemAdapter
                isNestedScrollingEnabled = false
                addItemDecoration(VerticalSpacingDecoration(dp(10)))
            }
        historyEmptyView =
            emptyStateCard(
                badge = historyEmptyBadgeText(),
                title = historyEmptyTitleText(),
                message = tr("no_history"),
                accentColor = "#FFB347",
            ).apply { visibility = View.GONE }
        historyView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#F2D8A7"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
        historyCard.addView(historyListRecycler)
        historyCard.addView(historyEmptyView)
        historyCard.addView(historyView)
        pageAchievementsContainer.addView(historyCard)

        leaderboardTitleView = sectionTitle("")
        pageLeaderboardContainer.addView(leaderboardTitleView)
        leaderboardSubtitleView = sectionSubtitle("")
        pageLeaderboardContainer.addView(leaderboardSubtitleView)
        leaderboardModeGroup =
            RadioGroup(this).apply {
                orientation = RadioGroup.VERTICAL
                gravity = Gravity.START
                setPadding(0, dp(4), 0, dp(10))
            }
        leaderboard30Button =
            RadioButton(this).apply {
                id = View.generateViewId()
                setTextColor(Color.WHITE)
                isChecked = true
            }
        leaderboard60Button =
            RadioButton(this).apply {
                id = View.generateViewId()
                setTextColor(Color.WHITE)
            }
        leaderboardTotalHitsButton =
            RadioButton(this).apply {
                id = View.generateViewId()
                setTextColor(Color.WHITE)
            }
        leaderboardStreakButton =
            RadioButton(this).apply {
                id = View.generateViewId()
                setTextColor(Color.WHITE)
            }
        leaderboardFatBurnButton =
            RadioButton(this).apply {
                id = View.generateViewId()
                setTextColor(Color.WHITE)
            }
        leaderboardModeGroup.addView(leaderboard30Button)
        leaderboardModeGroup.addView(leaderboard60Button)
        leaderboardModeGroup.addView(leaderboardTotalHitsButton)
        leaderboardModeGroup.addView(leaderboardStreakButton)
        leaderboardModeGroup.addView(leaderboardFatBurnButton)
        leaderboardModeGroup.setOnCheckedChangeListener { _, checkedId ->
            leaderboardBoard =
                when (checkedId) {
                    leaderboard60Button.id -> LeaderboardBoard.TotalHits
                    leaderboardTotalHitsButton.id -> LeaderboardBoard.TotalDuration
                    leaderboardStreakButton.id -> LeaderboardBoard.TotalCalories
                    leaderboardFatBurnButton.id -> LeaderboardBoard.TotalFatBurn
                    else -> LeaderboardBoard.DailyBestHits
                }
            leaderboardSubtitleView.text = leaderboardBoardSubtitle(leaderboardBoard)
            refreshLeaderboardOnly()
        }
        pageLeaderboardContainer.addView(leaderboardModeGroup)
        refreshLeaderboardButton =
            compactActionButton("", "#16384A").apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        bottomMargin = dp(10)
                    }
                setOnClickListener {
                    leaderboardSwipe.isRefreshing = true
                    refreshLeaderboardOnly()
                }
            }
        pageLeaderboardContainer.addView(refreshLeaderboardButton)
        leaderboardCard =
            surfaceCard().apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(2)
                    }
            }
        leaderboardPodiumContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
            }
        leaderboardRowAdapter = LeaderboardRowAdapter { entry -> leaderboardRowCardPremium(entry) }
        leaderboardListRecycler =
            RecyclerView(this).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = leaderboardRowAdapter
                isNestedScrollingEnabled = false
                setPadding(0, dp(12), 0, 0)
                clipToPadding = false
                addItemDecoration(VerticalSpacingDecoration(dp(10)))
            }
        leaderboardMeCard =
            detailCard(fillColor = "#0B1721", strokeColor = "#2A5C7B", cornerDp = 20).apply {
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
        leaderboardMeTitleView =
            bodyText("").apply {
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.parseColor("#FFB347"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
        leaderboardMeView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#FFF5E6"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, dp(8), 0, 0)
            }
        shareLeaderboardButton =
            compactActionButton("", "#16384A").apply {
                setOnClickListener { shareLeaderboardSummary() }
            }
        val leaderboardMeHeaderRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        leaderboardMeTitleView.layoutParams =
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f,
            )
        leaderboardMeHeaderRow.addView(leaderboardMeTitleView)
        leaderboardMeHeaderRow.addView(shareLeaderboardButton)
        leaderboardView =
            bodyText("").apply {
                setTextColor(Color.parseColor("#FFF5E6"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
        leaderboardCard.addView(leaderboardPodiumContainer)
        leaderboardCard.addView(leaderboardListRecycler)
        leaderboardMeCard.addView(leaderboardMeHeaderRow)
        leaderboardMeCard.addView(leaderboardMeView)
        leaderboardCard.addView(leaderboardMeCard)
        leaderboardCard.addView(leaderboardView)
        pageLeaderboardContainer.addView(leaderboardCard)

        applyStaticTexts()
        contentRoot.addView(topContainer)
        contentRoot.addView(pageHost)
        contentRoot.addView(
            pageTabsCard.apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        leftMargin = dp(14)
                        rightMargin = dp(14)
                        bottomMargin = dp(14)
                    }
            },
        )
        root.addView(contentRoot)
        return root
    }

    private fun buildBoxingBleCard(): LinearLayout =
        surfaceCard().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(6)
                    bottomMargin = dp(10)
                }
            addView(sectionTitle(bleDeviceSectionTitle()))
            boxingBleStatusView =
                bodyText(bleDisconnectedLabel()).apply {
                    setTextColor(Color.parseColor("#FFD060"))
                    setPadding(0, dp(4), 0, dp(6))
                }
            addView(boxingBleStatusView)
            boxingBleDeviceView =
                bodyText(bleNoDeviceSelectedLabel()).apply {
                    setTextColor(Color.parseColor("#D6E9F8"))
                    setPadding(0, 0, 0, dp(6))
                }
            addView(boxingBleDeviceView)
            boxingBleDeviceListView =
                RadioGroup(this@MainActivity).apply {
                    orientation = RadioGroup.VERTICAL
                    setPadding(0, 0, 0, dp(6))
                }
            addView(boxingBleDeviceListView)
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(10), 0, 0)
                    boxingBleScanButton = compactActionButton(bleScanLabel(), "#174154")
                    boxingBleConnectButton = compactActionButton(bleConnectLabel(), "#E07010")
                    boxingBleDisconnectButton = compactActionButton(bleDisconnectLabel(), "#A73A54")
                    addView(boxingBleScanButton, compactWeightedParams(1f, right = 6))
                    addView(boxingBleConnectButton, compactWeightedParams(1f, right = 6))
                    addView(boxingBleDisconnectButton, compactWeightedParams(1f))
                },
            )
            boxingBleScanButton.setOnClickListener { ensureBoxingBlePermissionAndScan() }
            boxingBleConnectButton.setOnClickListener {
                val selected = selectedBleDevice
                val manager = boxingBleManager
                if (selected != null && manager != null) {
                    manager.connect(selected)
                } else {
                    updateBoxingBleStatus(bleScanFirstMessage())
                }
            }
            boxingBleDisconnectButton.setOnClickListener { disconnectBoxingBlePairFromSettings() }
            updateBoxingBleButtons(connected = false)
            refreshBoxingBleCardFromRuntime()
        }

    private fun disconnectBoxingBlePairFromSettings() {
        val manager = boxingBleManager ?: return
        manager.disconnect()
        leftBleBatteryPercent = null
        rightBleBatteryPercent = null
        refreshHeaderBleStatus()
        updateBoxingBleButtons(connected = false)
    }

    private fun disconnectBoxingBlePairForAppExit() {
        boxingBleManager?.disconnect(updateState = false)
        leftBleBatteryPercent = null
        rightBleBatteryPercent = null
        boxingBleConnected = false
    }

    private fun setupBoxingBleManager() {
        boxingBleManager = BoxingBleRuntime.manager(this)
        boxingBleUiListener?.let { BoxingBleRuntime.removeBleListener(it) }
        boxingBleUiListener =
            object : BoxingBleManager.Listener {
                override fun onStateChanged(state: BoxingBleManager.State) {
                    updateBoxingBleStatus(state.message)
                    updateHeaderBleState(state)
                    if (hasDualBoxingBleConnection(state)) {
                        saveLastBoxingBlePair(state.readyDevices)
                        cancelBoxingBleAutoConnectTimeout()
                    }
                    updateBoxingBleButtons(
                        connected = isBoxingBleConnectedState(state.connectionState),
                    )
                }

                override fun onDeviceListChanged(devices: List<BoxingBleManager.DeviceCandidate>) {
                    val first = devices.firstOrNull()
                    if (selectedBleDevice == null || devices.none { it.address == selectedBleDevice?.address }) {
                        selectedBleDevice = first
                    }
                    renderBoxingBleDeviceSelection(devices)
                    updateBoxingBleButtons(connected = false)
                }

                override fun onPacket(packet: BoxingBleManager.BoxingPacket) {
                    latestBlePacket = packet
                    updateHeaderBleBattery(packet)
                    renderBoxingBlePacket(packet)
                }

                override fun onHit(packet: BoxingBleManager.BoxingPacket) {
                    latestBlePacket = packet
                    updateHeaderBleBattery(packet)
                    renderBoxingBlePacket(packet, hit = true)
                    Haptics.tap(this@MainActivity)
                }

                override fun onPunchThresholdSensitivity(level: Int) {
                    val normalized = level.coerceIn(0, 100)
                    sensitivityLevel = normalized
                    saveSettings()
                    sensitivitySeekBar?.progress = normalized
                    sensitivityValueView?.text = formatSensitivityValue(normalized)
                    sensitivityDeviceStatusView?.text = punchThresholdStatusText(normalized, fromDevice = true)
                }
            }
        boxingBleUiListener?.let { BoxingBleRuntime.addBleListener(it) }
        attemptBoxingBleAutoConnect()
    }

    private fun refreshBoxingBleCardFromRuntime() {
        val manager = boxingBleManager ?: return
        val state = manager.currentState
        val connected = isBoxingBleConnectedState(state.connectionState)
        selectedBleDevice = state.connectedDevice ?: selectedBleDevice ?: manager.devices.firstOrNull()
        updateBoxingBleStatus(state.message)
        updateHeaderBleState(state)
        renderBoxingBleDeviceSelection(manager.devices, state.connectedDevices)
        BoxingBleRuntime.latestPacket?.let { packet ->
            latestBlePacket = packet
            updateHeaderBleBattery(packet)
            renderBoxingBlePacket(packet)
        }
        updateBoxingBleButtons(connected = connected)
    }

    private fun renderBoxingBleDeviceSelection(
        devices: List<BoxingBleManager.DeviceCandidate>,
        connectedDevices: List<BoxingBleManager.DeviceCandidate> = emptyList(),
    ) {
        if (!::boxingBleDeviceView.isInitialized) return
        val selected = selectedBleDevice
        val connectedAddresses = connectedDevices.map { it.address }.toSet()
        boxingBleDeviceView.text =
            when {
                connectedDevices.isNotEmpty() ->
                    bleConnectedDevicesText(connectedDevices.size) + "\n" +
                        connectedDevices.joinToString("\n") { formatBoxingBleDevice(it, showAddress = false) }
                selected != null ->
                    "${bleSelectedDevicePrefix()}${formatBoxingBleDevice(selected, showAddress = false)}"
                else -> bleNoDeviceSelectedLabel()
            }
        if (!::boxingBleDeviceListView.isInitialized) return
        boxingBleDeviceListView.removeAllViews()
        devices.forEach { device ->
            val checked = selected?.address == device.address || connectedAddresses.contains(device.address)
            val row =
                RadioButton(this).apply {
                    text = formatBoxingBleDevice(device, showAddress = true)
                    setTextColor(Color.parseColor("#D6E9F8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD060"))
                    isChecked = checked
                    setPadding(0, dp(4), 0, dp(4))
                    setOnClickListener {
                        selectedBleDevice = device
                        renderBoxingBleDeviceSelection(devices, connectedDevices)
                        updateBoxingBleButtons(connected = connectedDevices.isNotEmpty())
                    }
                }
            boxingBleDeviceListView.addView(row)
        }
    }

    private fun formatBoxingBleDevice(
        device: BoxingBleManager.DeviceCandidate,
        showAddress: Boolean,
    ): String {
        val hand =
            when (device.hand) {
                BoxingBleManager.BoxingHand.Right -> bleRightHandLabel()
                BoxingBleManager.BoxingHand.Left -> bleLeftHandLabel()
                null -> bleUnknownLabel()
            }
        val pair = device.pairId?.let { "${blePairIdLabel()} $it" } ?: bleUnrecognizedPairLabel()
        val base = "$pair  $hand  ${device.name}  RSSI ${device.rssi} dBm"
        return if (showAddress) "$base\n${device.address}" else base
    }

    private fun isBoxingBleConnectedState(state: BoxingBleManager.ConnectionState): Boolean =
        state == BoxingBleManager.ConnectionState.Connected ||
            state == BoxingBleManager.ConnectionState.ServicesReady ||
            state == BoxingBleManager.ConnectionState.NotifyReady

    private fun hasDualBoxingBleConnection(state: BoxingBleManager.State = boxingBleManager?.currentState ?: BoxingBleManager.State()): Boolean {
        if (state.connectionState != BoxingBleManager.ConnectionState.NotifyReady) return false
        val devices = state.readyDevices
        return isCompleteDistinctBoxingBlePair(devices)
    }

    private fun requireDualBoxingBleConnection(): Boolean {
        if (hasDualBoxingBleConnection()) return true
        val message = boxingBlePairErrorMessage()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        updateBoxingBleStatus(message)
        return false
    }

    private fun saveLastBoxingBlePair(devices: List<BoxingBleManager.DeviceCandidate>) {
        val left = devices.firstOrNull { it.hand == BoxingBleManager.BoxingHand.Left }
        val right = devices.firstOrNull { it.hand == BoxingBleManager.BoxingHand.Right }
        if (left == null || right == null) return
        if (!isCompleteDistinctBoxingBlePair(listOf(left, right))) return
        prefs.edit()
            .putString(KEY_LAST_BLE_LEFT_ADDRESS, left.address)
            .putString(KEY_LAST_BLE_LEFT_NAME, left.name)
            .putString(KEY_LAST_BLE_RIGHT_ADDRESS, right.address)
            .putString(KEY_LAST_BLE_RIGHT_NAME, right.name)
            .apply()
    }

    private fun attemptBoxingBleAutoConnect() {
        if (boxingBleAutoConnectAttempted || hasDualBoxingBleConnection()) return
        val manager = boxingBleManager ?: return
        val leftAddress = prefs.getString(KEY_LAST_BLE_LEFT_ADDRESS, null)
        val leftName = prefs.getString(KEY_LAST_BLE_LEFT_NAME, null)
        val rightAddress = prefs.getString(KEY_LAST_BLE_RIGHT_ADDRESS, null)
        val rightName = prefs.getString(KEY_LAST_BLE_RIGHT_NAME, null)
        if (leftAddress.isNullOrBlank() || leftName.isNullOrBlank() || rightAddress.isNullOrBlank() || rightName.isNullOrBlank()) {
            return
        }
        if (leftAddress.equals(rightAddress, ignoreCase = true)) {
            clearLastBoxingBlePair()
            updateBoxingBleStatus(boxingBlePairErrorMessage())
            return
        }
        val missing =
            manager.requiredPermissions().filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
        if (missing.isNotEmpty()) {
            pendingBoxingBleAutoConnectAfterPermission = true
            boxingBlePermissionLauncher.launch(missing.toTypedArray())
            return
        }
        boxingBleAutoConnectAttempted = true
        updateBoxingBleStatus(bleAutoConnectingMessage())
        if (manager.connectRememberedPair(leftAddress, leftName, rightAddress, rightName)) {
            scheduleBoxingBleAutoConnectTimeout()
        }
    }

    private fun isCompleteDistinctBoxingBlePair(devices: List<BoxingBleManager.DeviceCandidate>): Boolean {
        val left = devices.firstOrNull { it.hand == BoxingBleManager.BoxingHand.Left }
        val right = devices.firstOrNull { it.hand == BoxingBleManager.BoxingHand.Right }
        if (left == null || right == null) return false
        if (left.address.equals(right.address, ignoreCase = true)) return false
        val leftPairId = left.pairId
        val rightPairId = right.pairId
        return leftPairId != null && leftPairId == rightPairId
    }

    private fun clearLastBoxingBlePair() {
        prefs.edit()
            .remove(KEY_LAST_BLE_LEFT_ADDRESS)
            .remove(KEY_LAST_BLE_LEFT_NAME)
            .remove(KEY_LAST_BLE_RIGHT_ADDRESS)
            .remove(KEY_LAST_BLE_RIGHT_NAME)
            .apply()
    }

    private fun scheduleBoxingBleAutoConnectTimeout() {
        cancelBoxingBleAutoConnectTimeout()
        val runnable =
            Runnable {
                boxingBleAutoConnectTimeoutRunnable = null
                if (!hasDualBoxingBleConnection()) {
                    val message = boxingBlePairErrorMessage()
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    updateBoxingBleStatus(message)
                }
            }
        boxingBleAutoConnectTimeoutRunnable = runnable
        contentRootView.postDelayed(runnable, BOXING_BLE_AUTO_CONNECT_TIMEOUT_MS)
    }

    private fun cancelBoxingBleAutoConnectTimeout() {
        boxingBleAutoConnectTimeoutRunnable?.let { runnable ->
            if (::contentRootView.isInitialized) {
                contentRootView.removeCallbacks(runnable)
            }
        }
        boxingBleAutoConnectTimeoutRunnable = null
    }

    private fun updateHeaderBleState(state: BoxingBleManager.State) {
        val connected = state.connectedDevices.isNotEmpty() || isBoxingBleConnectedState(state.connectionState)
        boxingBleConnected = connected
        if (!connected) {
            leftBleBatteryPercent = null
            rightBleBatteryPercent = null
        }
        refreshHeaderBleStatus()
    }

    private fun updateHeaderBleBattery(packet: BoxingBleManager.BoxingPacket) {
        val power = packet.powerState.coerceIn(0, 100)
        when (packet.hand) {
            BoxingBleManager.BoxingHand.Left -> leftBleBatteryPercent = power
            BoxingBleManager.BoxingHand.Right -> rightBleBatteryPercent = power
            null -> Unit
        }
        refreshHeaderBleStatus()
    }

    private fun refreshHeaderBleStatus() {
        if (!::headerBleStatusView.isInitialized) return
        headerBleStatusView.setConnected(boxingBleConnected)
        headerBleStatusView.contentDescription = if (boxingBleConnected) bleConnectedLabel() else bleDisconnectedLabel()
        headerBleLeftBatteryView.setHandLabel(bleLeftShortLabel())
        headerBleRightBatteryView.setHandLabel(bleRightShortLabel())
        headerBleLeftBatteryView.setBattery(leftBleBatteryPercent)
        headerBleRightBatteryView.setBattery(rightBleBatteryPercent)
    }

    private fun ensureBoxingBlePermissionAndScan() {
        val manager = boxingBleManager ?: return
        val missing =
            manager.requiredPermissions().filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
        if (missing.isEmpty()) {
            manager.startScan()
        } else {
            boxingBlePermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun updateBoxingBleStatus(message: String) {
        if (!::boxingBleStatusView.isInitialized) return
        boxingBleStatusView.text = localizedBleStatusMessage(message)
    }

    private fun localizedBleStatusMessage(message: String): String =
        when {
            message == "未连接" -> bleDisconnectedLabel()
            message == "已断开" -> bleDisconnectedDoneMessage()
            message == "手机蓝牙未开启" -> blePhoneOffMessage()
            message == "请先扫描 BOXING 设备" -> bleScanFirstMessage()
            message == "未发现 BOXING 设备" -> bleNoBoxingDeviceMessage()
            message == "蓝牙权限未授予，无法扫描设备" -> blePermissionDeniedMessage()
            message == "设备蓝牙连接有误，请到设置界面扫描后连接" -> boxingBlePairErrorMessage()
            message == "正在自动连接上次蓝牙设备" -> bleAutoConnectingMessage()
            message.startsWith("正在扫描 BOXING 设备") -> bleScanningMessage()
            message.startsWith("发现 ") && message.contains(" 个 BOXING 设备") ->
                bleFoundDevicesMessage(message.substringAfter("发现 ").substringBefore(" 个").toIntOrNull() ?: 0)
            message.startsWith("扫描完成，发现 ") && message.contains(" 个设备") ->
                bleScanCompleteMessage(message.substringAfter("扫描完成，发现 ").substringBefore(" 个").toIntOrNull() ?: 0)
            message.startsWith("正在连接 ") -> "${bleConnectingPrefix()}${message.removePrefix("正在连接 ")}"
            message.contains(" 已连接，正在发现服务") ->
                message.replace(" 已连接，正在发现服务", " ${bleConnectedDiscoveringSuffix()}")
            message.contains(" 服务已发现，正在打开通知") ->
                message.replace(" 服务已发现，正在打开通知", " ${bleServicesReadySuffix()}")
            message.contains(" 通知已打开，陀螺仪保持关闭") ->
                message.replace(" 通知已打开，陀螺仪保持关闭", " ${bleNotifyReadyGyroOffSuffix()}")
            message.contains(" 已断开 status=") ->
                message.replace(" 已断开 status=", " ${bleDisconnectedStatusSuffix()}")
            message.startsWith("蓝牙断开，正在自动重连") ->
                message.replace("蓝牙断开，正在自动重连", bleReconnectingPrefix())
            message == "10分钟未检测到拳击动作，已断开蓝牙" -> bleIdleDisconnectedMessage()
            else -> message
        }

    private fun updateBoxingBleButtons(connected: Boolean) {
        if (!::boxingBleScanButton.isInitialized) return
        boxingBleConnectButton.isEnabled = selectedBleDevice != null && !connected
        boxingBleDisconnectButton.isEnabled = connected
        val disabledAlpha = 0.45f
        boxingBleConnectButton.alpha = if (boxingBleConnectButton.isEnabled) 1f else disabledAlpha
        boxingBleDisconnectButton.alpha = if (connected) 1f else disabledAlpha
    }

    private fun renderBoxingBlePacket(
        packet: BoxingBleManager.BoxingPacket,
        hit: Boolean = false,
    ) {
        if (!::boxingBleMetricView.isInitialized) return
        val hand =
            when (packet.hand) {
                BoxingBleManager.BoxingHand.Right -> "右手"
                BoxingBleManager.BoxingHand.Left -> "左手"
                null -> packet.deviceName ?: "设备"
        }
        boxingBleMetricView.text =
            "$hand  陀螺仪拳击次数 ${packet.punches}\n" +
                "陀螺仪拳击力度 ${packet.punchForce}    电量 ${packet.powerState.coerceIn(0, 102)}"
        if (hit) {
            boxingBleMetricView.background = roundedBackground("#A71920", "#FFB3B3", 18)
            boxingBleMetricView.postDelayed({
                if (::boxingBleMetricView.isInitialized) {
                    boxingBleMetricView.background = roundedBackground("#181818", "#3A2C20", 18)
                }
            }, 160L)
        }
    }

    private fun compactWeightedParams(
        weight: Float,
        right: Int = 0,
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
            rightMargin = dp(right)
        }

    private fun homePageButton(onClick: () -> Unit): TextView =
        bodyText("").apply {
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(12), dp(15), dp(12), dp(15))
            compoundDrawablePadding = dp(6)
            isAllCaps = false
            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1.0f,
                )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            applyRippleOverlay()
        }

    private fun pageContentContainer(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
        }

    private fun wrapInSwipeRefresh(
        content: View,
        enabled: Boolean,
        onRefresh: (() -> Unit)? = null,
    ): SwipeRefreshLayout {
        val scroll =
            ScrollView(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }
        scroll.addView(content)
        return SwipeRefreshLayout(this).apply {
            layoutParams =
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            isEnabled = enabled
            setColorSchemeColors(Color.parseColor("#E07010"), Color.parseColor("#FFB347"))
            setProgressBackgroundColorSchemeColor(Color.parseColor("#1A0C00"))
            if (onRefresh != null) {
                setOnRefreshListener { onRefresh() }
            }
            addView(scroll)
        }
    }

    private fun buildTrainingWatermarkPage(content: View): FrameLayout =
        FrameLayout(this).apply {
            layoutParams =
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            setBackgroundColor(Color.parseColor("#140800"))
            addView(
                ImageView(this@MainActivity).apply {
                        setImageResource(R.drawable.training_center_watermark)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        alpha = 0.28f
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                },
            )
            addView(
                View(this@MainActivity).apply {
                    background =
                        GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(
                                Color.parseColor("#F008111A"),
                                Color.parseColor("#B808111A"),
                                Color.parseColor("#F808111A"),
                            ),
                        )
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                },
            )
            addView(content)
        }

    private fun bottomNavBackground(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(28).toFloat()
            colors =
                intArrayOf(
                    Color.parseColor("#0D1822"),
                    Color.parseColor("#2D1400"),
                )
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(dp(1), Color.parseColor("#8A3A00"))
        }

    private fun homePageIconRes(page: HomePage): Int =
        when (page) {
            HomePage.TrainingCenter -> android.R.drawable.ic_media_play
            HomePage.TrainingAchievements -> android.R.drawable.star_big_on
            HomePage.Leaderboard -> android.R.drawable.ic_menu_sort_by_size
            HomePage.Profile -> android.R.drawable.ic_menu_myplaces
        }

    private fun selectHomePage(page: HomePage) {
        if (!isActivated() && page != HomePage.TrainingCenter) {
            renderActivationRequired(tr("activation_required"))
            return
        }
        val previousPage = selectedHomePage
        selectedHomePage = page
        refreshHeaderSubtitle()
        refreshHomePageVisibility(previousPage)
    }

    private fun refreshHomePageVisibility(previousPage: HomePage? = null) {
        val activated = isActivated()
        if (!activated && selectedHomePage != HomePage.TrainingCenter) {
            selectedHomePage = HomePage.TrainingCenter
        }
        refreshHeaderSubtitle()
        pageTabsCard.visibility = View.VISIBLE
        trainingSwipe.visibility = if (selectedHomePage == HomePage.TrainingCenter) View.VISIBLE else View.GONE
        achievementsSwipe.visibility =
            if (activated && selectedHomePage == HomePage.TrainingAchievements) View.VISIBLE else View.GONE
        leaderboardSwipe.visibility =
            if (activated && selectedHomePage == HomePage.Leaderboard) View.VISIBLE else View.GONE
        profileSwipe.visibility = if (activated && selectedHomePage == HomePage.Profile) View.VISIBLE else View.GONE
        updateHomePageTabs()
        if (previousPage != null && previousPage != selectedHomePage) {
            when (selectedHomePage) {
                HomePage.Leaderboard -> animatePageEntrance(leaderboardSwipe)
                HomePage.Profile -> animatePageEntrance(profileSwipe)
                else -> {}
            }
        }
    }

    private fun refreshHeaderSubtitle() {
        if (!::subtitleView.isInitialized) {
            return
        }
        val text = headerSubtitleText()
        subtitleView.text = text
        subtitleView.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
    }

    private fun updateHomePageTabs() {
        val activated = isActivated()
        applyHomeTabStyle(pageTrainingButton, HomePage.TrainingCenter, selectedHomePage == HomePage.TrainingCenter, true)
        applyHomeTabStyle(pageAchievementsButton, HomePage.TrainingAchievements, selectedHomePage == HomePage.TrainingAchievements, activated)
        applyHomeTabStyle(pageLeaderboardButton, HomePage.Leaderboard, selectedHomePage == HomePage.Leaderboard, activated)
        applyHomeTabStyle(pageProfileButton, HomePage.Profile, selectedHomePage == HomePage.Profile, activated)
    }

    private fun applyHomeTabStyle(
        button: TextView,
        page: HomePage,
        selected: Boolean,
        enabled: Boolean,
    ) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1.0f else 0.45f
        button.translationY = if (selected) -dp(2).toFloat() else 0f
        button.scaleX = if (selected) 1.03f else 1.0f
        button.scaleY = if (selected) 1.03f else 1.0f
        val iconTint =
            if (!enabled) {
                Color.parseColor("#5B7284")
            } else if (selected) {
                Color.parseColor("#140800")
            } else {
                Color.parseColor("#D4B98A")
            }
        val icon =
            ContextCompat.getDrawable(this, homePageIconRes(page))?.mutate()?.apply {
                setTint(iconTint)
            }
        button.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null)
        if (selected) {
            button.setTextColor(Color.parseColor("#140800"))
            button.background = roundedBackground("#9FE6DA", "#E5FFF9", 20)
            button.elevation = dp(8).toFloat()
        } else {
            button.setTextColor(Color.parseColor("#D6B882"))
            button.background = roundedBackground("#0F1B27", "#244458", 20)
            button.elevation = 0f
        }
    }

    private fun animatePageEntrance(view: View) {
        view.alpha = 0f
        view.translationY = dp(12).toFloat()
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .start()
    }

    private fun setInlineTrainingPanelVisible(visible: Boolean) {
        if (!::trainingLiveCard.isInitialized) {
            return
        }
        trainingLiveCard.visibility = if (visible) View.VISIBLE else View.GONE
        if (::modeGroup.isInitialized) {
            modeGroup.visibility = if (visible) View.GONE else View.VISIBLE
        }
        if (visible) {
            trainingLiveCard.requestFocus()
            trainingLiveCard.post {
                trainingLiveCard.parent
                trainingLiveCard.requestRectangleOnScreen(Rect(0, 0, trainingLiveCard.width, trainingLiveCard.height), true)
            }
        }
    }

    private fun ensurePermissionAndStart() {
        if (trainingJob?.isActive == true) {
            return
        }
        if (selectedPlayMode == TrainingPlayMode.Classic30) {
            launchFreeBoxingMode()
            return
        }
        if (selectedPlayMode == TrainingPlayMode.Classic60) {
            launchColorGraffitiMode()
            return
        }
        if (selectedPlayMode == TrainingPlayMode.Burst10) {
            launchEmotionChampMode()
            return
        }
        if (selectedPlayMode == TrainingPlayMode.Burst15) {
            launchBlitzMode()
            return
        }
        if (selectedPlayMode == TrainingPlayMode.LevelChallenge) {
            launchFatBurnChallengeMode()
            return
        }
        if (selectedPlayMode == TrainingPlayMode.DailyChallenge) {
            launchFatBurnCoachMode()
            return
        }
        if (!isActivated()) {
            renderActivationRequired(tr("activation_required"))
            return
        }
        startTraining()
    }

    private fun launchFreeBoxingMode() {
        if (!isActivated()) {
            renderActivationRequired(tr("activation_required"))
            return
        }
        if (!requireDualBoxingBleConnection()) {
            return
        }
        if (trainingJob?.isActive == true) {
        }
        startActivity(
            Intent(this, FreeBoxingActivity::class.java)
                .putExtra(FreeBoxingActivity.EXTRA_SENSITIVITY_LEVEL, sensitivityLevel)
                .putExtra(FreeBoxingActivity.EXTRA_LANGUAGE, selectedLanguage.storageValue),
        )
    }

    private fun launchColorGraffitiMode() {
        if (!isActivated()) {
            renderActivationRequired(tr("activation_required"))
            return
        }
        if (!requireDualBoxingBleConnection()) {
            return
        }
        if (trainingJob?.isActive == true) {
        }
        startActivity(
            Intent(this, ColorGraffitiActivity::class.java)
                .putExtra(ColorGraffitiActivity.EXTRA_SENSITIVITY_LEVEL, sensitivityLevel)
                .putExtra(ColorGraffitiActivity.EXTRA_LANGUAGE, selectedLanguage.storageValue),
        )
    }
    private fun launchEmotionChampMode() {
        if (!isActivated()) {
            renderActivationRequired(tr("activation_required"))
            return
        }
        if (!requireDualBoxingBleConnection()) {
            return
        }
        if (trainingJob?.isActive == true) {
        }
        startActivity(
            Intent(this, EmotionChampActivity::class.java)
                .putExtra(EmotionChampActivity.EXTRA_SENSITIVITY_LEVEL, sensitivityLevel)
                .putExtra(EmotionChampActivity.EXTRA_LANGUAGE, selectedLanguage.storageValue),
        )
    }

    private fun launchBlitzMode() {
        if (!isActivated()) {
            renderActivationRequired(tr("activation_required"))
            return
        }
        if (!requireDualBoxingBleConnection()) {
            return
        }
        if (trainingJob?.isActive == true) {
        }
        startActivity(
            Intent(this, BlitzModeActivity::class.java)
                .putExtra(BlitzModeActivity.EXTRA_SENSITIVITY_LEVEL, sensitivityLevel)
                .putExtra(BlitzModeActivity.EXTRA_LANGUAGE, selectedLanguage.storageValue),
        )
    }

    private fun launchFatBurnChallengeMode() {
        if (!isActivated()) {
            renderActivationRequired(tr("activation_required"))
            return
        }
        if (!requireDualBoxingBleConnection()) {
            return
        }
        if (trainingJob?.isActive == true) {
        }
        startActivity(
            Intent(this, FatBurnChallengeActivity::class.java)
                .putExtra(FatBurnChallengeActivity.EXTRA_SENSITIVITY_LEVEL, sensitivityLevel)
                .putExtra(FatBurnChallengeActivity.EXTRA_AUTH_SERIAL, prefs.getString(KEY_AUTH_SERIAL, null).orEmpty())
                .putExtra(FatBurnChallengeActivity.EXTRA_LANGUAGE, selectedLanguage.storageValue),
        )
    }

    private fun launchFatBurnCoachMode() {
        if (!isActivated()) {
            renderActivationRequired(tr("activation_required"))
            return
        }
        if (!requireDualBoxingBleConnection()) {
            return
        }
        startActivity(
            Intent(this, FatBurnCoachActivity::class.java)
                .putExtra(FatBurnCoachActivity.EXTRA_SENSITIVITY_LEVEL, sensitivityLevel)
                .putExtra(FatBurnCoachActivity.EXTRA_LANGUAGE, selectedLanguage.storageValue),
        )
    }

    private fun handleDebugAutomationRoute(sourceIntent: Intent?): Boolean {
        if (sourceIntent == null) {
            return false
        }
        return when (sourceIntent.getStringExtra(EXTRA_DEBUG_OPEN_MODULE)) {
            DEBUG_MODULE_FAT_BURN_COACH -> {
                val coachIntent =
                    Intent(this, FatBurnCoachActivity::class.java)
                sourceIntent.extras?.let { extras ->
                    Bundle(extras).apply {
                        remove(EXTRA_DEBUG_OPEN_MODULE)
                    }.also { forwardedExtras ->
                        coachIntent.putExtras(forwardedExtras)
                    }
                }
                coachIntent
                    .putExtra(
                        FatBurnCoachActivity.EXTRA_SENSITIVITY_LEVEL,
                        sourceIntent.getIntExtra(FatBurnCoachActivity.EXTRA_SENSITIVITY_LEVEL, sensitivityLevel),
                    ).putExtra(
                        FatBurnCoachActivity.EXTRA_LANGUAGE,
                        sourceIntent.getStringExtra(FatBurnCoachActivity.EXTRA_LANGUAGE) ?: selectedLanguage.storageValue,
                    )
                if (sourceIntent.hasExtra(FatBurnCoachActivity.EXTRA_AUTO_ACCEPT_PLAN)) {
                    coachIntent.putExtra(
                        FatBurnCoachActivity.EXTRA_AUTO_ACCEPT_PLAN,
                        sourceIntent.getBooleanExtra(FatBurnCoachActivity.EXTRA_AUTO_ACCEPT_PLAN, false),
                    )
                }
                if (sourceIntent.hasExtra(FatBurnCoachActivity.EXTRA_AUTO_START_SESSION)) {
                    coachIntent.putExtra(
                        FatBurnCoachActivity.EXTRA_AUTO_START_SESSION,
                        sourceIntent.getBooleanExtra(FatBurnCoachActivity.EXTRA_AUTO_START_SESSION, false),
                    )
                }
                if (sourceIntent.hasExtra(FatBurnCoachActivity.EXTRA_AUTO_SIMULATE_PUNCHES)) {
                    coachIntent.putExtra(
                        FatBurnCoachActivity.EXTRA_AUTO_SIMULATE_PUNCHES,
                        sourceIntent.getBooleanExtra(FatBurnCoachActivity.EXTRA_AUTO_SIMULATE_PUNCHES, false),
                    )
                }
                if (sourceIntent.hasExtra(FatBurnCoachActivity.EXTRA_AUTO_FINISH_AFTER_MS)) {
                    coachIntent.putExtra(
                        FatBurnCoachActivity.EXTRA_AUTO_FINISH_AFTER_MS,
                        sourceIntent.getLongExtra(FatBurnCoachActivity.EXTRA_AUTO_FINISH_AFTER_MS, 0L),
                    )
                }
                if (sourceIntent.hasExtra(FatBurnCoachActivity.EXTRA_AUTO_SEED_WEIGHT_KG)) {
                    coachIntent.putExtra(
                        FatBurnCoachActivity.EXTRA_AUTO_SEED_WEIGHT_KG,
                        sourceIntent.getFloatExtra(FatBurnCoachActivity.EXTRA_AUTO_SEED_WEIGHT_KG, -1f),
                    )
                }
                if (sourceIntent.hasExtra(FatBurnCoachActivity.EXTRA_AUTO_SEED_WAIST_CM)) {
                    coachIntent.putExtra(
                        FatBurnCoachActivity.EXTRA_AUTO_SEED_WAIST_CM,
                        sourceIntent.getFloatExtra(FatBurnCoachActivity.EXTRA_AUTO_SEED_WAIST_CM, -1f),
                    )
                }
                if (sourceIntent.hasExtra(FatBurnCoachActivity.EXTRA_AUTO_OPEN_TAB)) {
                    coachIntent.putExtra(
                        FatBurnCoachActivity.EXTRA_AUTO_OPEN_TAB,
                        sourceIntent.getStringExtra(FatBurnCoachActivity.EXTRA_AUTO_OPEN_TAB),
                    )
                }
                startActivity(coachIntent)
                finish()
                true
            }
            DEBUG_MODULE_FAT_BURN_CHALLENGE -> {
                val challengeIntent =
                    Intent(this, FatBurnChallengeActivity::class.java)
                sourceIntent.extras?.let { extras ->
                    Bundle(extras).apply {
                        remove(EXTRA_DEBUG_OPEN_MODULE)
                    }.also { forwardedExtras ->
                        challengeIntent.putExtras(forwardedExtras)
                    }
                }
                challengeIntent
                    .putExtra(
                        FatBurnChallengeActivity.EXTRA_SENSITIVITY_LEVEL,
                        sourceIntent.getIntExtra(FatBurnChallengeActivity.EXTRA_SENSITIVITY_LEVEL, sensitivityLevel),
                    ).putExtra(
                        FatBurnChallengeActivity.EXTRA_AUTH_SERIAL,
                        sourceIntent.getStringExtra(FatBurnChallengeActivity.EXTRA_AUTH_SERIAL)
                            ?: prefs.getString(KEY_AUTH_SERIAL, null).orEmpty(),
                    ).putExtra(
                        FatBurnChallengeActivity.EXTRA_LANGUAGE,
                        sourceIntent.getStringExtra(FatBurnChallengeActivity.EXTRA_LANGUAGE) ?: selectedLanguage.storageValue,
                    )
                startActivity(challengeIntent)
                finish()
                true
            }
            else -> false
        }
    }

    private fun canUseTrainingDebugLogs(): Boolean = false

    private fun debugLogDirectory(create: Boolean = false): File =
        File(cacheDir, "training_debug_logs").apply {
            if (create) {
                mkdirs()
            }
        }

    private fun createTrainingDebugLogFile(): File? =
        if (!canUseTrainingDebugLogs()) {
            null
        } else {
            runCatching {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(debugLogDirectory(create = true), "reflex_debug_${selectedMode.durationSeconds}s_$timestamp.csv")
                pruneTrainingDebugLogs()
                file
            }.getOrNull()
        }

    private fun pruneTrainingDebugLogs(maxFiles: Int = 16) {
        val logs =
            debugLogDirectory()
                .listFiles { file -> file.isFile && file.name.startsWith("reflex_debug_") && file.extension.equals("csv", true) }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
        logs.drop(maxFiles).forEach { file -> runCatching { file.delete() } }
    }

    private fun clearTrainingDebugLogsForRegularUser() {
        if (canUseTrainingDebugLogs()) {
            return
        }
        runCatching {
            debugLogDirectory()
                .listFiles { file ->
                    file.isFile && file.name.startsWith("reflex_debug_") && file.extension.equals("csv", true)
                }
                ?.forEach { file -> runCatching { file.delete() } }
        }
    }

    private fun shareDebugLogs() {
        if (!canUseTrainingDebugLogs()) {
                return
        }
        val logs =
            debugLogDirectory()
                .listFiles { file -> file.isFile && file.name.startsWith("reflex_debug_") && file.extension.equals("csv", true) }
                ?.sortedByDescending { it.lastModified() }
                ?.take(8)
                .orEmpty()
        if (logs.isEmpty()) {
            setCloudStatusMessage("#FFD060", fallback = debugLogExportEmptyText())
            return
        }
        showDebugLogSelectionDialog(logs)
    }

    private fun showDebugLogSelectionDialog(logs: List<File>) {
        val checkedItems = BooleanArray(logs.size) { index -> index == 0 }
        val displayNames =
            logs.map { file ->
                val sizeKb = (file.length() / 1024L).coerceAtLeast(1L)
                "${file.name}  ${sizeKb}KB"
            }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(debugLogExportSelectTitle())
            .setMultiChoiceItems(displayNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setNegativeButton(debugLogExportCancelLabel(), null)
            .setPositiveButton(debugLogExportShareLabel()) { _, _ ->
                val selectedLogs = logs.filterIndexed { index, _ -> checkedItems[index] }
                if (selectedLogs.isEmpty()) {
                    setCloudStatusMessage("#FFD060", fallback = debugLogExportNoSelectionText())
                    return@setPositiveButton
                }
                shareSelectedDebugLogs(selectedLogs)
            }
            .show()
    }

    private fun shareSelectedDebugLogs(logs: List<File>) {
        runCatching {
            val shareDir = File(cacheDir, "shared").apply { mkdirs() }
            shareDir
                .listFiles { file ->
                    file.isFile && file.name.startsWith("reflex_debug_") && file.extension.equals("csv", true)
                }
                ?.forEach { file -> runCatching { file.delete() } }
            val uris =
                ArrayList<Uri>(
                    logs.map { source ->
                        val target = File(shareDir, source.name)
                        source.copyTo(target, overwrite = true)
                        FileProvider.getUriForFile(this, "${packageName}.fileprovider", target)
                    },
                )
            val shareIntent =
                Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "text/csv"
                    if (uris.size == 1) {
                        putExtra(Intent.EXTRA_STREAM, uris.first())
                    } else {
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    putExtra(Intent.EXTRA_TEXT, debugLogExportTitle())
                    clipData =
                        ClipData.newUri(contentResolver, debugLogExportTitle(), uris.first()).apply {
                            uris.drop(1).forEach { uri ->
                                addItem(ClipData.Item(uri))
                            }
                        }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            startActivity(Intent.createChooser(shareIntent, debugLogExportTitle()))
            logs.forEach { file -> runCatching { file.delete() } }
            setCloudStatusMessage("#FFB347", fallback = debugLogExportDoneText(logs.size))
        }.onFailure {
            setCloudStatusMessage("#FFAA40", fallback = debugLogExportFailedText())
        }
    }

    private fun startTraining() {
        if (trainingJob?.isActive == true) {
            return
        }
        if (!isActivated()) {
            renderActivationRequired(tr("activation_required"))
            return
        }
        dismissCelebrationBeforeTraining()
        val sessionMode = selectedMode
        val sessionPlayMode = selectedPlayMode
        calibrationRetrySuggested = false
        lastDisplayedCount = 0
        bleTrainingHitCount = 0
        bleTrainingStartedAtMs = 0L
        bleTrainingHitTimes.clear()
        lastSpokenCountdown = null
        goSpoken = false
        countView.text = "0"
        countdownView.text = tr("prepare_short")
        statusView.text = tr("keep_quiet")
        statusView.setTextColor(Color.parseColor("#F2D8A7"))
        remainingView.text = displayRemaining(sessionMode.durationSeconds * 1_000L)
        quietIconView.visibility = View.VISIBLE
        setTrainingBusyUi(true)
        setActivationVisible(false)
        setInlineTrainingPanelVisible(true)
        applyStaticTexts()
        BoxingBleRuntime.addHitListener(mainTrainingBleHitListener)
        BoxingBleRuntime.enableGyro()

        trainingJob =
            lifecycleScope.launch {
                try {
                    for (value in 3 downTo 1) {
                        applySessionUpdate(TrainingSessionUpdate.Countdown(value))
                        kotlinx.coroutines.delay(1_000L)
                    }
                    applySessionUpdate(TrainingSessionUpdate.StartCue)
                    val startedAt = System.currentTimeMillis()
                    bleTrainingStartedAtMs = startedAt
                    while (isActive) {
                        val elapsed = System.currentTimeMillis() - startedAt
                        val remaining = (sessionMode.durationSeconds * 1_000L - elapsed).coerceAtLeast(0L)
                        applySessionUpdate(TrainingSessionUpdate.Running(count = bleTrainingHitCount, remainingMillis = remaining))
                        if (remaining <= 0L) break
                        kotlinx.coroutines.delay(50L)
                    }
                    val bleReport =
                        TrainingReport(
                            mode = sessionMode,
                            totalHits = bleTrainingHitCount.coerceAtLeast(0),
                            averageFrequency = bleTrainingHitCount / sessionMode.durationSeconds.toFloat().coerceAtLeast(1f),
                            bestBurstCount =
                                bleTrainingHitTimes
                                    .map { start -> bleTrainingHitTimes.count { it >= start && it <= start + 3f } }
                                    .maxOrNull()
                                    ?: 0,
                            bestBurstStartSec = bleTrainingHitTimes.firstOrNull() ?: 0f,
                            endedAtEpochMs = System.currentTimeMillis(),
                            sessionDurationSeconds = sessionMode.durationSeconds,
                        )
                        lastCoachOutcome = updateTrainingGameAfterReport(bleReport, sessionPlayMode)
                        lastCoachMessage = null
                        latestReport = bleReport
                        renderReport(bleReport)
                        renderTrainingPlayStatus()
                        renderTrainingHero()
                        syncTrainingReport(bleReport)
                        setTrainingBusyUi(false)
                        quietIconView.visibility = View.GONE
                        countdownView.text = tr("done_short")
                        statusView.text = tr("training_complete")
                        statusView.setTextColor(Color.parseColor("#FFB347"))
                        remainingView.text = displayRemaining(0L)
                        setInlineTrainingPanelVisible(false)
                        lastCoachOutcome?.let { outcome ->
                            countdownView.postDelayed({
                                if (trainingJob == null) {
                                    maybeShowTrainingOutcomeCelebration(bleReport, outcome)
                                }
                            }, 350L)
                        }
                } catch (_: CancellationException) {
                    if (!isDestroyed && !isFinishing && statusView.text != tr("training_stopped")) {
                        renderIdle()
                    }
                } catch (t: Throwable) {
                    renderError(t.message ?: tr("training_failed"))
                } finally {
                    BoxingBleRuntime.removeHitListener(mainTrainingBleHitListener)
                    BoxingBleRuntime.disableGyro()
                    currentEngine = null
                    trainingJob = null
                }
            }
    }

    private fun stopTraining(showStoppedState: Boolean) {
        currentEngine?.cancel()
        trainingJob?.cancel()
        BoxingBleRuntime.removeHitListener(mainTrainingBleHitListener)
        BoxingBleRuntime.disableGyro()
        tts?.stop()
        currentEngine = null
        trainingJob = null
        lastSpokenCountdown = null
        goSpoken = false
        if (showStoppedState) {
            calibrationRetrySuggested = false
            setTrainingBusyUi(false)
            setInlineTrainingPanelVisible(false)
            statusView.text = tr("training_stopped")
            statusView.setTextColor(Color.parseColor("#FFD060"))
            countdownView.text = "--"
            quietIconView.visibility = View.GONE
            applyStaticTexts()
        }
    }

    private fun debugLogExportLabel(): String = ""

    private fun debugLogExportEmptyText(): String = ""

    private fun debugLogExportSelectTitle(): String = ""

    private fun debugLogExportCancelLabel(): String = ""

    private fun debugLogExportShareLabel(): String = ""

    private fun debugLogExportNoSelectionText(): String = ""

    private fun debugLogExportTitle(): String = ""

    private fun debugLogExportDoneText(count: Int = 0): String = ""

    private fun debugLogExportFailedText(): String = ""

    private fun handleBleTrainingHit() {
        if (trainingJob?.isActive != true) return
        if (bleTrainingStartedAtMs <= 0L) {
            bleTrainingStartedAtMs = System.currentTimeMillis()
        }
        bleTrainingHitCount += 1
        bleTrainingHitTimes += ((System.currentTimeMillis() - bleTrainingStartedAtMs).coerceAtLeast(0L) / 1000f)
        if (bleTrainingHitCount != lastDisplayedCount) {
            countView.text = bleTrainingHitCount.toString()
            pulseCount()
            Haptics.tap(this)
            lastDisplayedCount = bleTrainingHitCount
        }
    }

    private fun TrainingReport.withBleHits(): TrainingReport {
        val duration = sessionDurationSeconds.coerceAtLeast(1)
        val hits = bleTrainingHitCount.coerceAtLeast(0)
        val bestBurst =
            bleTrainingHitTimes
                .map { start -> bleTrainingHitTimes.count { it >= start && it <= start + 3f } }
                .maxOrNull()
                ?: 0
        return copy(
            totalHits = hits,
            averageFrequency = hits / duration.toFloat(),
            bestBurstCount = bestBurst,
            bestBurstStartSec = bleTrainingHitTimes.firstOrNull() ?: 0f,
        )
    }

    private fun applySessionUpdate(update: TrainingSessionUpdate, onStartCueDone: (() -> Unit)? = null) {
        when (update) {
            is TrainingSessionUpdate.Countdown -> {
                quietIconView.visibility = View.GONE
                countdownView.text = update.value.toString()
                statusView.text = displayCountdownStatus(update.value)
                statusView.setTextColor(Color.parseColor("#FFD060"))
                if (lastSpokenCountdown != update.value) {
                    speakCue(update.value.toString())
                    lastSpokenCountdown = update.value
                    countdownView.announceForAccessibility(update.value.toString())
                }
            }

            is TrainingSessionUpdate.StartCue -> {
                quietIconView.visibility = View.GONE
                countdownView.text = displayGoLabel()
                statusView.text = tr("training_live")
                statusView.setTextColor(Color.parseColor("#FFB347"))
                bleTrainingStartedAtMs = System.currentTimeMillis()
                if (!goSpoken) {
                    speakCue(displayGoCue(), onDone = onStartCueDone)
                    goSpoken = true
                } else {
                    onStartCueDone?.invoke()
                }
            }

            is TrainingSessionUpdate.Running -> {
                quietIconView.visibility = View.GONE
                countdownView.text = displayGoLabel()
                statusView.text = tr("training_live")
                statusView.setTextColor(Color.parseColor("#FFB347"))
                if (!goSpoken) {
                    goSpoken = true
                }
                countView.text = bleTrainingHitCount.toString()
                remainingView.text = displayRemaining(update.remainingMillis)
            }
        }
    }

    private fun renderIdle(authMessageKey: String? = null) {
        if (!isActivated()) {
            renderActivationRequired()
            return
        }
        calibrationRetrySuggested = false
        setTrainingBusyUi(false)
        setInlineTrainingPanelVisible(false)
        setActivationVisible(authMessageKey != null)
        statusView.text = tr("ready")
        statusView.setTextColor(Color.parseColor("#F2D8A7"))
        countdownView.text = "3"
        countView.text = "0"
        remainingView.text = displayRemaining(selectedMode.durationSeconds * 1_000L)
        quietIconView.visibility = View.GONE
        lastSpokenCountdown = null
        goSpoken = false
        if (authMessageKey != null) {
            setAuthStatusMessage("#FFB347", key = authMessageKey)
        } else {
            clearAuthStatusMessage()
        }
        applyStaticTexts()
        renderLatestReportPage()
        if (authMessageKey == "activation_success_ready") {
            activationCard.removeCallbacks(hideActivationCardRunnable)
            activationCard.postDelayed(hideActivationCardRunnable, 3_000L)
        }
    }

    private fun renderActivationRequired(message: String? = null) {
        calibrationRetrySuggested = false
        setTrainingBusyUi(false)
        setActivationBusy(false)
        setInlineTrainingPanelVisible(false)
        setActivationVisible(true)
        statusView.text = tr("activation_required")
        statusView.setTextColor(Color.parseColor("#FFAA40"))
        countdownView.text = tr("lock_short")
        countView.text = "0"
        remainingView.text = displayRemaining(selectedMode.durationSeconds * 1_000L)
        quietIconView.visibility = View.GONE
        setAuthStatusMessage(
            colorHex = "#FFD060",
            key = if (message == null) "activation_hint" else null,
            fallback = message,
        )
        lastSpokenCountdown = null
        goSpoken = false
        applyStaticTexts()
    }

    private fun renderCalibrationFailure(message: String) {
        calibrationRetrySuggested = true
        setTrainingBusyUi(false)
        setInlineTrainingPanelVisible(false)
        setActivationVisible(false)
        statusView.text = message
        statusView.setTextColor(Color.parseColor("#FFAA40"))
        countdownView.text = tr("retry_short")
        quietIconView.visibility = View.VISIBLE
        lastSpokenCountdown = null
        goSpoken = false
        applyStaticTexts()
    }

    private fun renderError(message: String) {
        calibrationRetrySuggested = false
        setTrainingBusyUi(false)
        setInlineTrainingPanelVisible(false)
        setActivationVisible(!isActivated())
        statusView.text = message
        statusView.setTextColor(Color.parseColor("#FF8A80"))
        countdownView.text = tr("error_short")
        remainingView.text = displayRemaining(selectedMode.durationSeconds * 1_000L)
        quietIconView.visibility = View.GONE
        lastSpokenCountdown = null
        goSpoken = false
        applyStaticTexts()
    }

    private fun renderLatestReportPage() {
        val aggregateReport = buildTodayAggregateReport()
        if (aggregateReport.hasData()) {
            renderDailyAggregateReport(aggregateReport)
        } else {
            renderEmptyReport()
        }
    }

    private fun renderDailyAggregateReport(report: DailyAggregateReport) {
        reportView.removeAllViews()
        val headerRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        val overviewChip =
            badgeText(
                localText("今日综合", "TODAY OVERVIEW", "VUE DU JOUR", "ภาพรวมวันนี้"),
                fillColor = "#17354A",
            )
        val moduleChip = badgeText("${report.activeModules}/6", fillColor = "#E07010")
        val spacer =
            View(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        1,
                        1f,
                    )
            }
        headerRow.addView(overviewChip)
        headerRow.addView(spacer)
        headerRow.addView(moduleChip)

        val heroTitle =
            titleText(
                localText(
                    "今日综合战报",
                    "Today's Combined Report",
                    "Rapport global du jour",
                    "สรุปการฝึกวันนี้",
                ),
                22f,
            ).apply {
                setTextColor(Color.parseColor("#FFF8E8"))
                setPadding(0, dp(10), 0, 0)
            }
        val summaryLine =
            bodyText(
                localText(
                    "综合 6 个模块今天的拳击数、压力变化、平静变化、卡路里和燃脂数据",
                    "Combined across today's six modules: hits, stress relief, calm gain, calories, and fat burn",
                    "Vue combinée des six modules du jour : coups, baisse du stress, calme gagné, calories et graisse brûlée.",
                    "รวมข้อมูลจาก 6 โมดูลวันนี้: จำนวนหมัด ความเครียดที่ลดลง ความสงบที่เพิ่มขึ้น แคลอรี และการเผาผลาญไขมัน",
                ),
            ).apply {
                setTextColor(Color.parseColor("#C9A46A"))
                setPadding(0, dp(4), 0, 0)
            }
        val totalHitsCard =
            reportMetricCard(
                label = localText("今日总拳击数", "Today's Total Hits", "Total des coups du jour", "หมัดรวมวันนี้"),
                value = "${report.totalHits} ${localText("次", "hits", "coups", "ครั้ง")}",
                accentColor = "#FF9A30",
                alignLeft = true,
            ).apply {
                applyAggregateCardTone(
                    topColor = "#3C2508",
                    bottomColor = "#160900",
                    accentColor = "#FFB347",
                    prominent = true,
                )
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(12)
                    }
            }
        totalHitsCard.layoutParams =
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.1f,
            ).apply {
                topMargin = dp(12)
                rightMargin = dp(10)
            }
        val totalDurationCard =
            reportMetricCard(
                label = localText("今日总训练时长", "Today's Training Time", "Temps d'entraînement du jour", "เวลาฝึกวันนี้"),
                value = "${String.format(Locale.US, "%.1f", report.totalDurationMinutes)} ${localText("分钟", "min", "min", "นาที")}",
                accentColor = "#6CCBFF",
                alignLeft = true,
            ).apply {
                applyAggregateCardTone(
                    topColor = "#102133",
                    bottomColor = "#07101A",
                    accentColor = "#6CCBFF",
                )
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    ).apply {
                        topMargin = dp(12)
                    }
            }
        val totalsRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
                addView(totalHitsCard)
                addView(totalDurationCard)
            }
        (totalHitsCard.getChildAt(0) as? TextView)?.text =
            localText("今日总拳击数", "Today's Total Hits", "Total des coups du jour", "หมัดรวมวันนี้")
        (totalHitsCard.getChildAt(1) as? TextView)?.text =
            buildAggregateMetricValueText(
                report.totalHits.toString(),
                localText("次", "hits", "coups", "ครั้ง"),
            )
        (totalDurationCard.getChildAt(0) as? TextView)?.text =
            localText("今日总训练时长", "Today's Training Time", "Temps d'entraînement du jour", "เวลาฝึกวันนี้")
        (totalDurationCard.getChildAt(1) as? TextView)?.text =
            buildAggregateMetricValueText(
                String.format(Locale.US, "%.1f", report.totalDurationMinutes),
                localText("分钟", "min", "min", "นาที"),
            )
        totalsRow.post {
            val alignedHeight = maxOf(totalHitsCard.height, totalDurationCard.height)
            if (alignedHeight > 0) {
                totalHitsCard.minimumHeight = alignedHeight
                totalDurationCard.minimumHeight = alignedHeight
                totalHitsCard.requestLayout()
                totalDurationCard.requestLayout()
            }
        }
        val metricsGrid =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, 0)
            }
        val topRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
        topRow.addView(
            reportMetricCard(
                label = localText("降低的压力值", "Stress Reduced", "Stress réduit", "ความเครียดที่ลดลง"),
                value = formatDailyAggregateValue(report.stressReduction),
                accentColor = "#FF7A7A",
                alignLeft = true,
            ).apply {
                applyAggregateCardTone(
                    topColor = "#241216",
                    bottomColor = "#0E0709",
                    accentColor = "#FF7A7A",
                )
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    ).apply {
                        rightMargin = dp(10)
                    }
            },
        )
        topRow.addView(
            reportMetricCard(
                label = localText("提高的平静值", "Calm Increased", "Calme accru", "ความสงบที่เพิ่มขึ้น"),
                value = formatDailyAggregateValue(report.calmIncrease),
                accentColor = "#7DFFAF",
                alignLeft = true,
            ).apply {
                applyAggregateCardTone(
                    topColor = "#10241A",
                    bottomColor = "#08110C",
                    accentColor = "#7DFFAF",
                )
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
            },
        )
        val bottomRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }
        bottomRow.addView(
            reportMetricCard(
                label = localText("今日总消耗", "Today's Calories", "Calories du jour", "แคลอรีรวมวันนี้"),
                value = "${String.format(Locale.US, "%.1f", report.totalCalories)} kcal",
                accentColor = "#FFC85A",
                alignLeft = true,
            ).apply {
                applyAggregateCardTone(
                    topColor = "#2A2110",
                    bottomColor = "#120D07",
                    accentColor = "#FFC85A",
                )
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    ).apply {
                        rightMargin = dp(10)
                    }
            },
        )
        bottomRow.addView(
            reportMetricCard(
                label = localText("今日总燃脂", "Today's Fat Burn", "Graisse brûlée du jour", "ไขมันที่เผาผลาญวันนี้"),
                value = "${String.format(Locale.US, "%.1f", report.totalFatBurnGrams)} g",
                accentColor = "#FF5A5A",
                alignLeft = true,
            ).apply {
                applyAggregateCardTone(
                    topColor = "#2A1212",
                    bottomColor = "#120708",
                    accentColor = "#FF5A5A",
                )
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
            },
        )
        metricsGrid.addView(topRow)
        metricsGrid.addView(bottomRow)

        reportView.addView(headerRow)
        reportView.addView(heroTitle)
        reportView.addView(summaryLine)
        reportView.addView(totalsRow)
        reportView.addView(metricsGrid)
        reportView.addView(
            detailCard(fillColor = "#2A1000", strokeColor = "#FFD060", cornerDp = 18).apply {
                background = metallicBackground("#2D2813", "#1A0C00", "#FFD060", 18)
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(12)
                    }
                addView(
                    bodyText(
                        localText(
                            "已汇总色彩涂鸦、情绪拳王、自由拳击、极速燃脂、燃脂挑战、燃脂陪练今天的训练结果。",
                            "Includes today's results from Color Graffiti, Emotion Champ, Free Boxing, Rapid Fat Burn, Fat Burn Challenge, and Fat Burn Coach.",
                        ),
                    ).apply {
                        setTextColor(Color.parseColor("#FFF3D3"))
                        setTypeface(Typeface.DEFAULT_BOLD)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    },
                )
            },
        )
    }

    private fun renderReport(report: TrainingReport) {
        latestReport = report
        renderLatestReportPage()
        return
        reportView.removeAllViews()
        val headerRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        val modeChip = badgeText(displayModeLabel(report.mode), fillColor = "#17354A")
        val hitsChip = badgeText("${report.totalHits} ${tr("hits")}", fillColor = "#E07010")
        val spacer =
            View(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        1,
                        1f,
                    )
            }
        headerRow.addView(modeChip)
        headerRow.addView(spacer)
        headerRow.addView(hitsChip)

        val heroTitle =
            titleText(
                localText("本次训练摘要", "Session Summary", "Résumé de session", "สรุปการฝึกครั้งนี้"),
                22f,
            ).apply {
                setTextColor(Color.parseColor("#FFF8E8"))
                setPadding(0, dp(14), 0, 0)
            }
        val summaryLine =
            bodyText(
                localText(
                    "${tr("sensitivity")} $sensitivityLevel · ${displayModeLabel(report.mode)}",
                    "Sensitivity $sensitivityLevel · ${displayModeLabel(report.mode)}",
                    "Sensibilité $sensitivityLevel · ${displayModeLabel(report.mode)}",
                    "ความไว $sensitivityLevel · ${displayModeLabel(report.mode)}",
                ),
            ).apply {
                setTextColor(Color.parseColor("#C9A46A"))
                setPadding(0, dp(6), 0, 0)
            }
        val metricsGrid =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(16), 0, 0)
            }
        val topRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
        topRow.addView(
            reportMetricCard(
                label = tr("average_frequency"),
                value = String.format(Locale.US, "%.2f %s", report.averageFrequency, tr("hits_per_second")),
                accentColor = "#FF9A30",
            ).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    ).apply {
                        rightMargin = dp(10)
                    }
            },
        )
        topRow.addView(
            reportMetricCard(
                label = tr("best_burst"),
                value = "${report.bestBurstCount} ${tr("hits")}",
                accentColor = "#FFD060",
            ).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
            },
        )
        val bottomRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
            }
        bottomRow.addView(
            reportMetricCard(
                label = tr("burst_start"),
                value = String.format(Locale.US, "%.1fs", report.bestBurstStartSec),
                accentColor = "#C084FC",
            ).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    ).apply {
                        rightMargin = dp(10)
                    }
            },
        )
        bottomRow.addView(
            reportMetricCard(
                label = localText("完成时间", "Finished", "Terminé", "เสร็จสิ้น"),
                value = formatReportEndedTime(report.endedAtEpochMs),
                accentColor = "#FFB347",
            ).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
            },
        )
        metricsGrid.addView(topRow)
        metricsGrid.addView(bottomRow)

        reportView.addView(headerRow)
        reportView.addView(heroTitle)
        reportView.addView(summaryLine)
        reportView.addView(metricsGrid)
        coachMessageForReport(report)?.takeIf { it.isNotBlank() }?.let { message ->
            reportView.addView(
                detailCard(fillColor = "#2A1000", strokeColor = "#FFD060", cornerDp = 18).apply {
                    background = metallicBackground("#2D2813", "#1A0C00", "#FFD060", 18)
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = dp(14)
                        }
                    addView(
                        bodyText(message).apply {
                            setTextColor(Color.parseColor("#FFF3D3"))
                            setTypeface(Typeface.DEFAULT_BOLD)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        },
                    )
                },
            )
        }
        addShareTrainingButtonToReport()
    }

    private fun addShareTrainingButtonToReport() {
        (shareTrainingButton.parent as? ViewGroup)?.removeView(shareTrainingButton)
        val shareRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(16)
                    }
                addView(
                    shareTrainingButton.apply {
                        layoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                    },
                )
            }
        reportView.addView(shareRow)
    }

    private fun activateDevice() {
        val serial = normalizeDigits(serialInput.text?.toString()).take(11)
        val code = normalizeDigits(codeInput.text?.toString()).take(8)
        if (serial.length != 11) {
            setAuthStatusMessage("#FFAA40", key = "serial_invalid")
            return
        }
        if (code.length != 8) {
            setAuthStatusMessage("#FFAA40", key = "code_invalid")
            return
        }

        activationJob?.cancel()
        setActivationBusy(true)
        setAuthStatusMessage("#FFD060", key = "activation_loading")
        activationJob =
            lifecycleScope.launch(Dispatchers.IO) {
                val result =
                    activationService.activate(
                        serial = serial,
                        code = code,
                        installId = installId,
                        deviceHash = deviceHash,
                        appVersion = BuildConfig.VERSION_NAME,
                    )
                withContext(Dispatchers.Main) {
                    setActivationBusy(false)
                    handleActivationResult(serial, result)
                }
            }
    }

    private fun handleActivationResult(
        serial: String,
        result: ActivationApiResult,
    ) {
        if (result.success && !result.activationToken.isNullOrBlank()) {
            persistActivationState(result.serial ?: serial, result.activationToken)
            codeInput.setText("")
            renderIdle(authMessageKey = "activation_success_ready")
            refreshCloudData(forceLeaderboard = true)
            return
        }

        setAuthStatusFailure(result.reason, result.message)
    }

    private fun attemptAutoRestoreActivation(force: Boolean = false) {
        if (isActivated()) {
            return
        }
        if (autoRestoreAttempted && !force) {
            return
        }
        autoRestoreAttempted = true
        activationJob?.cancel()
        setActivationBusy(true)
        setAuthStatusMessage("#FFD060", fallback = activationRestoreLoadingMessage())
        activationJob =
            lifecycleScope.launch(Dispatchers.IO) {
                val result =
                    activationService.reactivateByDevice(
                        installId = installId,
                        deviceHash = deviceHash,
                        appVersion = BuildConfig.VERSION_NAME,
                    )
                withContext(Dispatchers.Main) {
                    setActivationBusy(false)
                    if (result.success && !result.activationToken.isNullOrBlank() && !result.serial.isNullOrBlank()) {
                        persistActivationState(result.serial, result.activationToken)
                        clearAuthStatusMessage()
                        renderIdle()
                        refreshCloudData(forceLeaderboard = true)
                    } else if (result.reason == ActivationService.NETWORK_REASON) {
                        setAuthStatusMessage("#FFD060", fallback = activationRestoreNetworkMessage())
                    } else {
                        clearAuthStatusMessage()
                        renderActivationRequired()
                    }
                }
            }
    }

    private fun verifyActivationInBackground() {
        markActivationCheckedNow()
        refreshCloudData(forceLeaderboard = true)
        if (trainingJob?.isActive != true) {
            clearAuthStatusMessage()
            applyStaticTexts()
        }
    }

    private fun ensureInstallIdentity() {
        installId = prefs.getString(KEY_INSTALL_ID, null).orEmpty()
        if (installId.isBlank()) {
            installId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALL_ID, installId).apply()
        }
        deviceHash = computeDeviceHash()
    }

    private fun loadActivationState() {
        val serial = prefs.getString(KEY_AUTH_SERIAL, null).orEmpty()
        val token = prefs.getString(KEY_AUTH_TOKEN, null).orEmpty()
        if (serial.isBlank() || token.isBlank()) {
            persistActivationState(
                serial = freeUseSerial(),
                activationToken = FREE_USE_AUTH_TOKEN,
            )
            return
        }
        activationState =
            ActivationState(
                serial = serial,
                activationToken = token,
                installId = prefs.getString(KEY_AUTH_INSTALL_ID, installId).orEmpty().ifBlank { installId },
                deviceHash = prefs.getString(KEY_AUTH_DEVICE_HASH, deviceHash).orEmpty().ifBlank { deviceHash },
                activatedAtEpochMs = prefs.getLong(KEY_AUTH_ACTIVATED_AT, System.currentTimeMillis()),
                lastCheckAtEpochMs = prefs.getLong(KEY_AUTH_LAST_CHECK_AT, 0L),
            )
    }

    private fun persistActivationState(
        serial: String,
        activationToken: String,
    ) {
        val now = System.currentTimeMillis()
        activationState =
            ActivationState(
                serial = serial,
                activationToken = activationToken,
                installId = installId,
                deviceHash = deviceHash,
                activatedAtEpochMs = now,
                lastCheckAtEpochMs = now,
            )
        prefs.edit()
            .putString(KEY_AUTH_SERIAL, serial)
            .putString(KEY_AUTH_TOKEN, activationToken)
            .putString(KEY_AUTH_INSTALL_ID, installId)
            .putString(KEY_AUTH_DEVICE_HASH, deviceHash)
            .putLong(KEY_AUTH_ACTIVATED_AT, now)
            .putLong(KEY_AUTH_LAST_CHECK_AT, now)
            .apply()
    }

    private fun clearActivationState() {
        persistActivationState(
            serial = freeUseSerial(),
            activationToken = FREE_USE_AUTH_TOKEN,
        )
    }

    private fun markActivationCheckedNow() {
        val state = activationState ?: return
        val now = System.currentTimeMillis()
        activationState = state.copy(lastCheckAtEpochMs = now)
        prefs.edit().putLong(KEY_AUTH_LAST_CHECK_AT, now).apply()
    }

    private fun isActivated(): Boolean = true

    private fun freeUseSerial(): String {
        val seed = sha256Hex("$installId:$deviceHash")
        val digits =
            seed
                .map { char -> ('0'.code + (char.code % 10)).toChar() }
                .joinToString("")
                .take(9)
                .padEnd(9, '0')
        return "26$digits"
    }

    private fun authFailureMessageKey(reason: String?): String? =
        when (reason) {
            "serial_not_found" -> "activation_serial_not_found"
            "invalid_code" -> "activation_invalid_code"
            "already_bound" -> "activation_already_bound"
            "not_activated" -> "activation_not_activated"
            ActivationService.NETWORK_REASON -> "activation_network_error"
            else -> null
        }

    private fun setAuthStatusFailure(
        reason: String?,
        fallbackMessage: String,
    ) {
        val key = authFailureMessageKey(reason)
        setAuthStatusMessage(
            colorHex = if (reason == ActivationService.NETWORK_REASON) "#FFD060" else "#FFAA40",
            key = key,
            fallback = if (key == null) fallbackMessage.ifBlank { tr("activation_failed") } else null,
        )
    }

    private fun setAuthStatusMessage(
        colorHex: String,
        key: String? = null,
        fallback: String? = null,
    ) {
        authStatusMessageKey = key
        authStatusFallbackMessage = fallback
        authStatusColor = Color.parseColor(colorHex)
        applyAuthStatusView()
    }

    private fun currentAuthStatusMessage(): String =
        authStatusMessageKey?.let(::tr) ?: authStatusFallbackMessage.orEmpty()

    private fun clearAuthStatusMessage() {
        authStatusMessageKey = null
        authStatusFallbackMessage = null
        applyAuthStatusView()
    }

    private fun applyAuthStatusView() {
        val message = currentAuthStatusMessage()
        authStatusView.text = message
        if (message.isBlank()) {
            authStatusView.visibility = View.GONE
            authStatusView.background = null
            return
        }
        authStatusView.visibility = View.VISIBLE
        authStatusView.setTextColor(authStatusColor)
        authStatusView.background = chipBackground(authStatusColor)
        authStatusView.setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun refreshActivationCardState() {
        val activated = isActivated()
        activationCard.background = if (activated) heroBackground("#0E4057") else surfaceCardBackground()
        activationTitleView.text =
            if (activated) tr("activation_ready_title") else tr("activation_title")
        activationHintView.text =
            if (activated) tr("activation_ready_subtitle") else tr("activation_subtitle")
        serialInput.hint = tr("serial_hint")
        codeInput.hint = tr("code_hint")
        activateButton.text = tr("activate")

        serialInput.visibility = if (activated) View.GONE else View.VISIBLE
        codeInput.visibility = if (activated) View.GONE else View.VISIBLE
        if (activated) {
            serialInputErrorView.visibility = View.GONE
            codeInputErrorView.visibility = View.GONE
        }
        activateButton.visibility = if (activated) View.GONE else View.VISIBLE
        activationDetailsView.visibility = if (activated) View.VISIBLE else View.GONE

        if (activated) {
            activationDetailsView.text = buildActivationDetailsText()
            applyAuthStatusView()
        } else {
            activationDetailsView.text = ""
            if (authStatusMessageKey == null && authStatusFallbackMessage.isNullOrBlank()) {
                setAuthStatusMessage("#FFD060", key = "activation_hint")
            } else {
                applyAuthStatusView()
            }
        }
    }

    private fun buildActivationDetailsText(): String {
        val state = activationState ?: return ""
        val serialText = if (state.serial.length <= 4) state.serial else "*******" + state.serial.takeLast(4)
        val checkedAt = formatActivationCheckTime(state.lastCheckAtEpochMs)
        return buildString {
            append(tr("activation_serial_label"))
            append(": ")
            append(serialText)
            append('\n')
            append(tr("activation_status_label"))
            append(": ")
            append(tr("activation_status_verified"))
            append('\n')
            append(tr("activation_last_check_label"))
            append(": ")
            append(checkedAt)
        }
    }

    private fun maskSerial(serial: String): String =
        if (serial.length <= 4) {
            serial
        } else {
            "*******" + serial.takeLast(4)
        }

    private fun formatActivationCheckTime(epochMs: Long): String {
        if (epochMs <= 0L) {
            return tr("activation_just_now")
        }
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
    }

    private fun normalizeDigits(value: String?): String = value.orEmpty().filter { it.isDigit() }

    private fun activationRestoreLoadingMessage(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "正在检查本机历史使用状态..."
            AppLanguage.English -> "Checking this device for a previous activation..."
            AppLanguage.French -> "Vérification de l'état précédent de cet appareil..."
            AppLanguage.Thai -> "กำลังตรวจสอบสถานะการใช้งานเดิมของอุปกรณ์นี้..."
        }

    private fun activationRestoreNetworkMessage(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "暂时无法自动恢复激活，请联网后重试，或手动重新激活。"
            AppLanguage.English -> "Unable to restore activation right now. Please connect to the internet or activate manually."
            AppLanguage.French -> "Impossible de restaurer l'activation pour le moment. Connectez-vous a Internet ou activez manuellement."
            AppLanguage.Thai -> "ไม่สามารถกู้คืนการเปิดใช้งานอัตโนมัติได้ในขณะนี้ กรุณาเชื่อมต่ออินเทอร์เน็ตแล้วลองใหม่ หรือเปิดใช้งานด้วยตนเอง"
        }

    private fun computeDeviceHash(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        return sha256Hex(if (androidId.isBlank()) "unknown-device" else androidId)
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun refreshCloudData(forceLeaderboard: Boolean = true) {
        val state = activationState ?: return
        cloudJob?.cancel()
        setCloudStatusMessage("#FFD060", key = "cloud_sync_loading")
        cloudJob =
            lifecycleScope.launch(Dispatchers.IO) {
                val bootstrap =
                    cloudSyncService.bootstrap(
                        state = state,
                        language = selectedLanguage,
                        appVersion = BuildConfig.VERSION_NAME,
                    )
                withContext(Dispatchers.Main) {
                    applyCloudBootstrap(bootstrap)
                    if (forceLeaderboard) {
                        refreshLeaderboardOnly()
                    }
                }
            }
    }

    private fun refreshLeaderboardOnly() {
        val state = activationState
        if (state == null || !isActivated()) {
            renderLeaderboard()
            stopSwipeRefreshSpinners()
            return
        }
        leaderboardJob?.cancel()
        setCloudStatusMessage(
            "#FFD060",
            fallback =
                localText(
                    "正在刷新榜单...",
                    "Refreshing leaderboard...",
                    "Actualisation du classement...",
                    "กำลังรีเฟรชอันดับ...",
                ),
        )
        leaderboardJob =
            lifecycleScope.launch(Dispatchers.IO) {
                val result =
                    cloudSyncService.fetchLeaderboard(
                        state = state,
                        boardKey = leaderboardBoard.apiKey,
                        appVersion = BuildConfig.VERSION_NAME,
                        window = "all",
                        limit = 20,
                    )
                withContext(Dispatchers.Main) {
                    applyLeaderboardResult(result)
                }
            }
    }

    private fun syncTrainingReport(report: TrainingReport) {
        val previousUnlockedKeys = cloudAchievements.filter { it.unlocked }.map { it.key }.toSet()
        val previousTierLevel = cloudTier?.level ?: cloudProfile?.currentTier ?: prefs.getInt(KEY_LAST_SEEN_TIER, 0)
        CloudTrainingUploader.uploadIfAvailable(
            context = this,
            scope = lifecycleScope,
            report = report,
        ) { upload ->
            if (upload.success) {
                val newlyUnlocked = computeNewlyUnlockedAchievements(previousUnlockedKeys, upload.achievements)
                val promotedTier =
                    upload.tier?.takeIf {
                        shouldCelebrateTier(
                            tier = it,
                            promotedHint = upload.promoted,
                            previousLevel = previousTierLevel,
                        )
                    }
                cloudProfile = upload.profile ?: cloudProfile
                cloudStatistics = upload.statistics ?: cloudStatistics
                cloudHistory = if (upload.history.isNotEmpty()) upload.history else cloudHistory
                if (upload.achievements.isNotEmpty()) {
                    cloudAchievements = upload.achievements
                }
                cloudTier = upload.tier ?: cloudTier
                syncSeenTier(upload.tier)
                setCloudStatusMessage("#FFB347", key = "cloud_sync_ready")
                refreshCloudViews()
                refreshLeaderboardOnly()
                maybeShowPostTrainingCelebrations(newlyUnlocked, promotedTier)
            } else {
                setCloudStatusMessage(
                    colorHex = "#FFD060",
                    key =
                        if (upload.queuedLocally || upload.reason == CloudSyncService.NETWORK_REASON) {
                            "cloud_sync_network"
                        } else {
                            null
                        },
                    fallback = upload.message,
                )
                refreshCloudViews()
            }
        }
    }

    private fun applyCloudBootstrap(result: CloudBootstrapResult) {
        if (result.success) {
            cloudProfile = result.profile ?: cloudProfile
            cloudStatistics = result.statistics ?: cloudStatistics
            if (result.history.isNotEmpty()) {
                cloudHistory = result.history
            }
            if (result.achievements.isNotEmpty()) {
                cloudAchievements = result.achievements
            }
            cloudTier = result.tier ?: cloudTier
            syncSeenTier(result.tier)
            setCloudStatusMessage("#FFB347", key = "cloud_sync_ready")
            CloudTrainingUploader.flushPendingIfAvailable(this, lifecycleScope) { flushedCount ->
                if (flushedCount > 0) {
                    refreshCloudData(forceLeaderboard = true)
                }
            }
        } else {
            setCloudStatusMessage(
                colorHex = "#FFD060",
                key = if (result.reason == CloudSyncService.NETWORK_REASON) "cloud_sync_network" else null,
                fallback = result.message,
            )
        }
        refreshCloudViews()
    }

    private fun applyLeaderboardResult(result: CloudLeaderboardResult) {
        leaderboardResult = result
        if (result.success) {
            setCloudStatusMessage("#FFB347", key = "leaderboard_ready")
        } else {
            setCloudStatusMessage(
                colorHex = "#FFD060",
                key = if (result.reason == CloudSyncService.NETWORK_REASON) "cloud_sync_network" else null,
                fallback = result.message,
            )
        }
        refreshCloudViews()
    }

    private fun showEditProfileDialog() {
        val currentProfile = cloudProfile ?: return
        val dialogRoot =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(12), dp(20), dp(4))
            }
        var selectedAvatarColor = sanitizeAvatarColor(currentProfile.avatarColor)
        var selectedAvatarUri = currentAvatarImageUri()
        val avatarSwatches = mutableListOf<View>()
        val nicknameInput =
            EditText(this).apply {
                setText(currentProfile.nickname)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#8F6A44"))
                setBackgroundColor(Color.parseColor("#2A1000"))
                setPadding(dp(12), dp(12), dp(12), dp(12))
                filters = arrayOf(InputFilter.LengthFilter(64))
            }
        val avatarPreviewShell =
            FrameLayout(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(dp(68), dp(68)).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        bottomMargin = dp(12)
                    }
                background = avatarBackground(selectedAvatarColor)
                clipToOutline = true
            }
        val avatarPreviewImageView =
            ImageView(this).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                visibility = View.GONE
            }
        val avatarPreviewFallbackView =
            TextView(this).apply {
                gravity = Gravity.CENTER
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }
        avatarPreviewShell.addView(avatarPreviewImageView)
        avatarPreviewShell.addView(avatarPreviewFallbackView)
        val avatarPaletteRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
        avatarPalette.forEachIndexed { index, color ->
            val swatchVisual =
                View(this).apply {
                    layoutParams =
                        FrameLayout.LayoutParams(dp(24), dp(24)).apply {
                            gravity = Gravity.CENTER
                        }
                    background = roundedBackground(color, if (color == selectedAvatarColor) "#FFFFFF" else color, 999)
                }
            val swatchTouch =
                FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                    isClickable = true
                    isFocusable = true
                    contentDescription =
                        String.format(Locale.US, tr("cd_avatar_swatch"), index + 1)
                    addView(swatchVisual)
                    setOnClickListener {
                        selectedAvatarColor = color
                        bindAvatarPresentation(
                            container = avatarPreviewShell,
                            imageView = avatarPreviewImageView,
                            fallbackView = avatarPreviewFallbackView,
                            seedText = nicknameInput.text?.toString(),
                            colorHex = selectedAvatarColor,
                            imageUri = selectedAvatarUri,
                        )
                        avatarSwatches.forEachIndexed { idx, child ->
                            val paletteColor = avatarPalette[idx]
                            child.background =
                                roundedBackground(
                                    paletteColor,
                                    if (paletteColor == selectedAvatarColor) "#FFFFFF" else paletteColor,
                                    999,
                                )
                        }
                    }
                }
            avatarSwatches += swatchVisual
            avatarPaletteRow.addView(swatchTouch)
        }
        val avatarPaletteScroll =
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                addView(
                    avatarPaletteRow,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                    },
                )
            }
        val avatarActionRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
        val chooseAvatarButton =
            compactActionButton(avatarChooseButtonLabel(), "#16384A").apply {
                setOnClickListener {
                    pendingAvatarSelection = { uri ->
                        if (uri != null) {
                            selectedAvatarUri = uri
                            bindAvatarPresentation(
                                container = avatarPreviewShell,
                                imageView = avatarPreviewImageView,
                                fallbackView = avatarPreviewFallbackView,
                                seedText = nicknameInput.text?.toString(),
                                colorHex = selectedAvatarColor,
                                imageUri = selectedAvatarUri,
                            )
                        }
                    }
                    avatarPickerLauncher.launch(arrayOf("image/*"))
                }
            }
        val clearAvatarButton =
            compactActionButton(avatarClearButtonLabel(), "#5C3D99").apply {
                setOnClickListener {
                    selectedAvatarUri = null
                    bindAvatarPresentation(
                        container = avatarPreviewShell,
                        imageView = avatarPreviewImageView,
                        fallbackView = avatarPreviewFallbackView,
                        seedText = nicknameInput.text?.toString(),
                        colorHex = selectedAvatarColor,
                        imageUri = selectedAvatarUri,
                    )
                }
            }
        avatarActionRow.addView(chooseAvatarButton)
        avatarActionRow.addView(horizontalSpace(dp(12)))
        avatarActionRow.addView(clearAvatarButton)
        dialogRoot.addView(sectionLabel(tr("profile_nickname")))
        dialogRoot.addView(nicknameInput)
        dialogRoot.addView(spacer(dp(12)))
        dialogRoot.addView(sectionLabel(tr("profile_avatar")))
        dialogRoot.addView(avatarPreviewShell)
        dialogRoot.addView(avatarActionRow)
        dialogRoot.addView(
            bodyText(avatarImageHintText()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setTextColor(Color.parseColor("#B88A54"))
                setPadding(0, dp(8), 0, dp(8))
            },
        )
        dialogRoot.addView(avatarPaletteScroll)
        bindAvatarPresentation(
            container = avatarPreviewShell,
            imageView = avatarPreviewImageView,
            fallbackView = avatarPreviewFallbackView,
            seedText = nicknameInput.text?.toString(),
            colorHex = selectedAvatarColor,
            imageUri = selectedAvatarUri,
        )
        nicknameInput.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    bindAvatarPresentation(
                        container = avatarPreviewShell,
                        imageView = avatarPreviewImageView,
                        fallbackView = avatarPreviewFallbackView,
                        seedText = s?.toString(),
                        colorHex = selectedAvatarColor,
                        imageUri = selectedAvatarUri,
                    )
                }

                override fun afterTextChanged(s: android.text.Editable?) = Unit
            },
        )

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(tr("profile_edit"))
                .setView(dialogRoot)
                .setNegativeButton(tr("cancel"), null)
                .setPositiveButton(tr("save"), null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val state = activationState ?: return@setOnClickListener
                val nickname = nicknameInput.text?.toString()?.trim().orEmpty()
                if (nickname.isBlank()) {
                    setCloudStatusMessage("#FFAA40", key = "profile_save_failed")
                    refreshCloudViews()
                    return@setOnClickListener
                }
                storeAvatarImageUri(selectedAvatarUri)
                refreshProfileAvatar()
                setCloudStatusMessage("#FFD060", key = "cloud_sync_loading")
                refreshCloudViews()
                lifecycleScope.launch(Dispatchers.IO) {
                    val result =
                        cloudSyncService.updateProfile(
                            state = state,
                            nickname = nickname,
                            language = selectedLanguage,
                            avatarColor = selectedAvatarColor,
                            appVersion = BuildConfig.VERSION_NAME,
                        )
                    withContext(Dispatchers.Main) {
                        applyCloudBootstrap(result)
                        if (result.success) {
                            setCloudStatusMessage("#FFB347", key = "profile_saved")
                            dialog.dismiss()
                        } else if (result.reason != CloudSyncService.NETWORK_REASON) {
                            setCloudStatusMessage("#FFAA40", key = "profile_save_failed", fallback = result.message)
                        }
                        refreshCloudViews()
                    }
                }
            }
        }
        dialog.show()
        dialog.window?.decorView?.setBackgroundColor(Color.parseColor("#1A0C00"))
    }

    private fun showDeveloperInfoDialog() {
        val scrollView =
            ScrollView(this).apply {
                isFillViewport = true
            }
        val dialogRoot =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(12))
            }
        scrollView.addView(
            dialogRoot,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        dialogRoot.addView(
            bodyText(developerInfoPageSubtitle()).apply {
                setTextColor(Color.parseColor("#FFD88A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, 0, 0, dp(12))
            },
        )

        dialogRoot.addView(sectionLabel(developerCompanySectionTitle()))
        dialogRoot.addView(
            detailCard(fillColor = "#0B1721", strokeColor = "#20384A").apply {
                addView(
                    titleText(developerCompanyName(), 18f).apply {
                        setTextColor(Color.parseColor("#FFF8E8"))
                    },
                )
                addView(
                    bodyText(developerCompanyDescription()).apply {
                        setTextColor(Color.parseColor("#FFD88A"))
                        setPadding(0, dp(8), 0, 0)
                    },
                )
            },
        )

        dialogRoot.addView(spacer(dp(12)))
        dialogRoot.addView(sectionLabel(developerContactSectionTitle()))
        dialogRoot.addView(
            detailCard(fillColor = "#0B1721", strokeColor = "#20384A").apply {
                addView(
                    bodyText(developerEmailLabel()).apply {
                        setTextColor(Color.parseColor("#B88A54"))
                        setTypeface(Typeface.DEFAULT_BOLD)
                    },
                )
                addView(
                    titleText(DEVELOPER_EMAIL, 18f).apply {
                        setTextColor(Color.parseColor("#FFB347"))
                        setPadding(0, dp(8), 0, 0)
                    },
                )
                addView(
                    compactActionButton(developerEmailActionLabel(), "#E07010").apply {
                        layoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                topMargin = dp(12)
                            }
                        setOnClickListener { openDeveloperEmail() }
                    },
                )
            },
        )

        dialogRoot.addView(spacer(dp(12)))
        dialogRoot.addView(sectionLabel(developerExtrasSectionTitle()))
        dialogRoot.addView(
            detailCard(fillColor = "#0B1721", strokeColor = "#20384A").apply {
                addView(
                    bodyText("${developerVersionLabel()}: ${displayAppVersion()}").apply {
                        setTextColor(Color.parseColor("#F2D8A7"))
                        setTypeface(Typeface.DEFAULT_BOLD)
                    },
                )
                addView(
                    bodyText(developerDocumentHint()).apply {
                        setTextColor(Color.parseColor("#B88A54"))
                        setPadding(0, dp(10), 0, 0)
                    },
                )
                addView(
                    compactActionButton(privacyPolicyEntryLabel(), "#16384A").apply {
                        layoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                topMargin = dp(12)
                            }
                        setOnClickListener {
                            showDeveloperDocumentDialog(
                                title = privacyPolicyEntryLabel(),
                                assetFile = developerPrivacyPolicyAssetFile(),
                            )
                        }
                    },
                )
                addView(
                    compactActionButton(userAgreementEntryLabel(), "#1F3B52").apply {
                        layoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                topMargin = dp(10)
                            }
                        setOnClickListener {
                            showDeveloperDocumentDialog(
                                title = userAgreementEntryLabel(),
                                assetFile = developerUserAgreementAssetFile(),
                            )
                        }
                    },
                )
            },
        )

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(developerInfoPageTitle())
                .setView(scrollView)
                .setPositiveButton(closeLabel(), null)
                .create()
        dialog.show()
        dialog.window?.decorView?.setBackgroundColor(Color.parseColor("#1A0C00"))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (resources.displayMetrics.heightPixels * 0.88f).toInt(),
        )
    }

    private fun showDeveloperDocumentDialog(
        title: String,
        assetFile: String,
    ) {
        val content = loadAssetText(assetFile).ifBlank { developerDocumentUnavailableText() }
        val scrollView =
            ScrollView(this).apply {
                isFillViewport = true
            }
        val body =
            bodyText(content).apply {
                setTextColor(Color.parseColor("#F2D8A7"))
                setLineSpacing(dp(4).toFloat(), 1.15f)
                setPadding(dp(20), dp(18), dp(20), dp(12))
            }
        scrollView.addView(
            body,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton(closeLabel(), null)
                .create()
        dialog.show()
        dialog.window?.decorView?.setBackgroundColor(Color.parseColor("#1A0C00"))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (resources.displayMetrics.heightPixels * 0.88f).toInt(),
        )
    }

    private fun refreshCloudViews() {
        val activated = isActivated()
        refreshHomePageVisibility()
        renderTrainingHero()
        if (!activated) {
            return
        }

        profileSummaryView.text = buildProfileSummaryText()
        profileMetaView.text = buildProfileMetaSummary()
        profileTierView.text = buildProfileTierSummary()
        profileStatsView.text = buildProfileStatsOverview()
        profileBadgesView.text = buildRecentBadgeSummary()
        refreshProfileAvatar()
        cloudStatusView.setTextColor(cloudStatusColor)
        cloudStatusView.text = currentCloudStatusMessage().ifBlank { tr("cloud_sync_idle") }
        cloudStatusView.background = chipBackground(cloudStatusColor)
        renderAchievements()
        renderHistoryCards()
        renderLeaderboard()
        refreshCloudListLocaleBindings()
        stopSwipeRefreshSpinners()
    }

    private fun refreshCloudListLocaleBindings() {
        if (::historyItemAdapter.isInitialized && historyItemAdapter.currentList.isNotEmpty()) {
            historyItemAdapter.notifyDataSetChanged()
        }
        if (::leaderboardRowAdapter.isInitialized && leaderboardRowAdapter.currentList.isNotEmpty()) {
            leaderboardRowAdapter.notifyDataSetChanged()
        }
    }

    private fun stopSwipeRefreshSpinners() {
        if (::trainingSwipe.isInitialized) trainingSwipe.isRefreshing = false
        if (::achievementsSwipe.isInitialized) achievementsSwipe.isRefreshing = false
        if (::leaderboardSwipe.isInitialized) leaderboardSwipe.isRefreshing = false
        if (::profileSwipe.isInitialized) profileSwipe.isRefreshing = false
    }

    private fun renderTrainingHero() {
        val activated = isActivated()
        val stats = cloudStatistics
        val tier = cloudTier
        trainingHeroBadgeView.text =
            when (selectedLanguage) {
                AppLanguage.Chinese -> "训练中心"
                AppLanguage.English -> "TRAINING CENTER"
                AppLanguage.French -> "CENTRE D'ENTRAINEMENT"
                AppLanguage.Thai -> "ศูนย์ฝึก"
            }
        trainingHeroHeadlineView.text =
            when {
                activated && tier != null -> tierLabelForKey(tier.key)
                else -> tr("title")
            }
        trainingHeroSummaryView.text =
            when {
                !activated ->
                    localText(
                        "完成设备激活后，即可解锁训练进度、成就与榜单挑战。",
                        "Activate your device to unlock training progress, achievements, and rank challenges.",
                        "Activez l'appareil pour débloquer la progression, les succès et les défis de classement.",
                        "เปิดใช้งานอุปกรณ์เพื่อปลดล็อกความคืบหน้า ความสำเร็จ และการท้าทายอันดับ",
                    )
                stats != null ->
                    localText(
                        "最佳 30 秒 ${stats.best30Hits} 击 · 最佳 60 秒 ${stats.best60Hits} 击 · 累计 ${stats.totalHits} 击",
                        "Best 30s ${stats.best30Hits} · Best 60s ${stats.best60Hits} · ${stats.totalHits} total hits",
                        "Meilleur 30 s ${stats.best30Hits} · Meilleur 60 s ${stats.best60Hits} · ${stats.totalHits} frappes",
                        "ดีที่สุด 30 วินาที ${stats.best30Hits} · ดีที่สุด 60 วินาที ${stats.best60Hits} · รวม ${stats.totalHits} ครั้ง",
                    )
                else ->
                    localText(
                        "云端训练数据正在同步，稍后即可看到你的成绩、段位与成长进度。",
                        "Cloud data is syncing. Your scores, rank, and progress will appear soon.",
                        "Les données cloud se synchronisent. Vos scores, rangs et progrès apparaîtront bientôt.",
                        "กำลังซิงก์ข้อมูลบนคลาวด์ คะแนน อันดับ และความคืบหน้าจะปรากฏในไม่ช้า",
                    )
            }
        trainingHeroInsightView.text =
            latestReport?.let { report ->
                localText(
                    "最新战报：${displayModeLabel(report.mode)} · ${report.totalHits} 击 · ${tr("best_burst")} ${report.bestBurstCount}",
                    "Latest report: ${displayModeLabel(report.mode)} · ${report.totalHits} hits · ${tr("best_burst")} ${report.bestBurstCount}",
                    "Dernier rapport : ${displayModeLabel(report.mode)} · ${report.totalHits} coups · ${tr("best_burst")} ${report.bestBurstCount}",
                    "รายงานล่าสุด: ${displayModeLabel(report.mode)} · ${report.totalHits} ครั้ง · ${tr("best_burst")} ${report.bestBurstCount}",
                )
            } ?: localText(
                "暂无最新战报，完成一轮训练后这里会显示你的核心成绩。",
                "No report yet. Finish a session and your key stats will appear here.",
                "Aucun rapport pour le moment. Terminez une séance pour afficher vos statistiques clés.",
                "ยังไม่มีรายงานล่าสุด เมื่อจบหนึ่งรอบจะมีการแสดงผลสถิติหลักที่นี่",
            )
        trainingHeroProgressView.text =
            when {
                !activated ->
                    localText(
                        "激活后可同步训练记录、成就、榜单与个人成长数据。",
                        "Activation unlocks cloud records, achievements, leaderboards, and profile progress.",
                        "L'activation débloque l'historique cloud, les succès, les classements et la progression.",
                        "หลังเปิดใช้งานจะสามารถซิงก์ประวัติ ความสำเร็จ อันดับ และความก้าวหน้าส่วนตัวได้",
                    )
                tier != null && tier.nextHits != null && tier.nextKey != null -> {
                    val best30 = stats?.best30Hits ?: tier.bestHits
                    val totalHits = stats?.totalHits ?: tier.totalHits
                    val remainingBest30 = (tier.nextHits - best30).coerceAtLeast(0)
                    val remainingTotal = ((tier.nextTotalHits ?: 0) - totalHits).coerceAtLeast(0)
                    localText(
                        "距离 ${tierLabelForKey(tier.nextKey)}：30秒最佳还差 $remainingBest30 击 · 累计拳击数还差 $remainingTotal 击",
                        "To ${tierLabelForKey(tier.nextKey)}: $remainingBest30 more best-30 hits and $remainingTotal total hits",
                        "Pour ${tierLabelForKey(tier.nextKey)} : encore $remainingBest30 coups en 30 s et $remainingTotal coups cumulés",
                        "สู่ ${tierLabelForKey(tier.nextKey)}: ขาดอีก $remainingBest30 ครั้งใน 30 วินาที และ $remainingTotal ครั้งสะสม",
                    )
                }
                tier != null ->
                    localText(
                        "已达到当前最高段位，继续保持你的拳击节奏。",
                        "Top tier reached. Keep pushing your boxing pace.",
                        "Rang maximal atteint. Continuez à garder votre rythme.",
                        "ถึงระดับสูงสุดแล้ว รักษาจังหวะการชกนี้ไว้ต่อไป",
                    )
                else -> currentCloudStatusMessage().ifBlank { tr("cloud_sync_idle") }
            }
        trainingHeroCard.background = if (activated) heroBackground("#CC4400") else heroBackground("#1C3140")
        shareTrainingButton.alpha = if (latestReport != null) 1.0f else 0.72f
        shareTrainingButton.isEnabled = latestReport != null
    }

    private fun trainingPlayModeForCheckedId(checkedId: Int): TrainingPlayMode =
        when (checkedId) {
            mode60Button.id -> TrainingPlayMode.Classic60
            modeBurst10Button.id -> TrainingPlayMode.Burst10
            modeBurst15Button.id -> TrainingPlayMode.Burst15
            modeLevelButton.id -> TrainingPlayMode.LevelChallenge
            modeDailyButton.id -> TrainingPlayMode.DailyChallenge
            else -> TrainingPlayMode.Classic30
        }

    private fun configureModeButton(button: RadioButton) {
        button.buttonDrawable = null
        button.includeFontPadding = false
        button.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        button.minHeight = dp(92)
        button.minimumHeight = dp(92)
        button.minWidth = dp(76)
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        button.setTypeface(Typeface.DEFAULT_BOLD)
        button.letterSpacing = 0.01f
        button.setLineSpacing(0f, 1.08f)
        button.setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), Color.parseColor("#55000000"))
        button.setPadding(dp(28), dp(16), dp(28), dp(16))
        button.minLines = 1
        button.maxLines = 2
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(button, 12, 17, 1, TypedValue.COMPLEX_UNIT_SP)
        button.layoutParams =
            RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(12)
            }
    }

    private fun spacedModeLabel(rawLabel: String): String {
        if (rawLabel.isBlank()) return rawLabel
        val iconLength = Character.charCount(rawLabel.codePointAt(0))
        val icon = rawLabel.substring(0, iconLength)
        val title = rawLabel.substring(iconLength).trimStart()
        return if (title.isBlank()) rawLabel else "$icon\u2003\u2003$title"
    }

    private fun trainingCenterModeIcon(playMode: TrainingPlayMode): String =
        when (playMode) {
            TrainingPlayMode.Classic30 -> "🥊"
            TrainingPlayMode.Classic60 -> "🎨"
            TrainingPlayMode.Burst10 -> "👑"
            TrainingPlayMode.Burst15 -> "⚡"
            TrainingPlayMode.LevelChallenge -> "🔥"
            TrainingPlayMode.DailyChallenge -> "🤖"
        }

    private fun trainingCenterModeTitle(playMode: TrainingPlayMode): String =
        when (playMode) {
            TrainingPlayMode.Classic30 ->
                localText("自由拳击", "Free Boxing", "Boxe libre", "ชกอิสระ")
            TrainingPlayMode.Classic60 ->
                localText("色彩涂鸦", "Color Graffiti", "Graffiti couleur", "สีสันกราฟฟิตี")
            TrainingPlayMode.Burst10 ->
                localText("情绪拳王", "Emotion Champ", "Champion émotion", "ราชันอารมณ์")
            TrainingPlayMode.Burst15 ->
                localText("极速燃脂", "Rapid Fat Burn", "Brûle-graisse express", "เผาผลาญเร่งด่วน")
            TrainingPlayMode.LevelChallenge ->
                localText("燃脂挑战", "Fat-Burn Challenge", "Défi brûle-graisse", "ท้าทายเผาผลาญ")
            TrainingPlayMode.DailyChallenge ->
                localText("燃脂陪练", "Fat Burn Sparring", "Sparring brûle-graisse", "ซ้อมเผาผลาญไขมัน")
        }

    private fun trainingCenterModeSubtitle(playMode: TrainingPlayMode): String =
        when (playMode) {
            TrainingPlayMode.Classic30 ->
                localText("轻松释放", "Release stress", "Relâcher", "ปล่อยแรงกดดัน")
            TrainingPlayMode.Classic60 ->
                localText("挥拳创作", "Paint with hits", "Peindre en frappant", "ต่อยแต้มสี")
            TrainingPlayMode.Burst10 ->
                localText("击碎情绪", "Break emotions", "Briser l'émotion", "ปล่อยอารมณ์")
            TrainingPlayMode.Burst15 ->
                localText("高频冲刺", "Fast sprint", "Sprint rapide", "เร่งสปีด")
            TrainingPlayMode.LevelChallenge ->
                localText("计划闯关", "30-day plan", "Plan 30 jours", "แผน 30 วัน")
            TrainingPlayMode.DailyChallenge ->
                localText("智能陪练", "Coach guidance", "Coach intelligent", "โค้ชอัจฉริยะ")
        }

    private fun modeButtonPalette(playMode: TrainingPlayMode): ModeButtonPalette =
        when (playMode) {
            TrainingPlayMode.Classic60 ->
                ModeButtonPalette(
                    accent = "#66F0FF",
                    activeHighlight = "#2D7F96",
                    activeBase = "#132D38",
                    activeStroke = "#8EF8FF",
                    inactiveHighlight = "#183644",
                    inactiveBase = "#0A171E",
                    inactiveStroke = "#2E5A68",
                    activeText = "#F1FEFF",
                    inactiveText = "#D4F8FF",
                )

            TrainingPlayMode.Burst10 ->
                ModeButtonPalette(
                    accent = "#A8FF8E",
                    activeHighlight = "#467D44",
                    activeBase = "#172A1A",
                    activeStroke = "#C8FFB5",
                    inactiveHighlight = "#213827",
                    inactiveBase = "#0D1710",
                    inactiveStroke = "#406449",
                    activeText = "#F5FFF1",
                    inactiveText = "#E1F8D8",
                )

            TrainingPlayMode.Classic30 ->
                ModeButtonPalette(
                    accent = "#8ED6FF",
                    activeHighlight = "#346E92",
                    activeBase = "#132535",
                    activeStroke = "#B6E8FF",
                    inactiveHighlight = "#1B3243",
                    inactiveBase = "#0B141C",
                    inactiveStroke = "#39556C",
                    activeText = "#F3FBFF",
                    inactiveText = "#DDEFFF",
                )

            TrainingPlayMode.Burst15 ->
                ModeButtonPalette(
                    accent = "#FFD85A",
                    activeHighlight = "#A36A12",
                    activeBase = "#2E1605",
                    activeStroke = "#FFF0A8",
                    inactiveHighlight = "#49311A",
                    inactiveBase = "#140B05",
                    inactiveStroke = "#8F5A20",
                    activeText = "#FFF9E9",
                    inactiveText = "#F8E0B0",
                )

            TrainingPlayMode.LevelChallenge ->
                ModeButtonPalette(
                    accent = "#FF7E4F",
                    activeHighlight = "#9C361D",
                    activeBase = "#301008",
                    activeStroke = "#FFC0A4",
                    inactiveHighlight = "#4B2217",
                    inactiveBase = "#180907",
                    inactiveStroke = "#8B3C29",
                    activeText = "#FFF3EE",
                    inactiveText = "#FFD9CA",
                )

            TrainingPlayMode.DailyChallenge ->
                ModeButtonPalette(
                    accent = "#FF5B73",
                    activeHighlight = "#98314B",
                    activeBase = "#2D0C17",
                    activeStroke = "#FFB5C2",
                    inactiveHighlight = "#48202B",
                    inactiveBase = "#17080D",
                    inactiveStroke = "#8D3E50",
                    activeText = "#FFF1F4",
                    inactiveText = "#FFD6DE",
                )
        }

    private fun refreshModeButtonStyles() {
        if (!::mode30Button.isInitialized || !::modeDailyButton.isInitialized) {
            return
        }
        val items =
            listOf(
                mode30Button to TrainingPlayMode.Classic30,
                mode60Button to TrainingPlayMode.Classic60,
                modeBurst10Button to TrainingPlayMode.Burst10,
                modeBurst15Button to TrainingPlayMode.Burst15,
                modeLevelButton to TrainingPlayMode.LevelChallenge,
                modeDailyButton to TrainingPlayMode.DailyChallenge,
            )
        val stackSubtitle =
            if (selectedLanguage == AppLanguage.Chinese) {
                items.any { (_, playMode) -> shouldStackModeSubtitle(playMode) }
            } else {
                true
            }
        items.forEach { (button, playMode) ->
            val selected = selectedPlayMode == playMode
            val palette = modeButtonPalette(playMode)
            button.setTextColor(Color.parseColor(if (selected) palette.activeText else palette.inactiveText))
            button.text = coloredModeLabel(playMode, palette.accent, stackSubtitle)
            if (selectedLanguage == AppLanguage.Chinese) {
                button.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                button.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            } else {
                button.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                button.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            button.setTypeface(Typeface.DEFAULT_BOLD)
            button.background =
                if (selected) {
                    metallicBackground(
                        highlight = palette.activeHighlight,
                        base = palette.activeBase,
                        stroke = palette.activeStroke,
                        cornerDp = 26,
                    )
                } else {
                    metallicBackground(
                        highlight = palette.inactiveHighlight,
                        base = palette.inactiveBase,
                        stroke = palette.inactiveStroke,
                        cornerDp = 24,
                    )
                }
            button.elevation = dp(if (selected) 8 else 5).toFloat()
            button.translationY = if (selected) -dp(1).toFloat() else 0f
            button.alpha = if (trainingJob?.isActive == true) 0.62f else 1.0f
        }
    }

    private fun coloredModeLabel(
        playMode: TrainingPlayMode,
        accentColor: String,
        stackSubtitle: Boolean,
    ): SpannableString {
        val icon = trainingCenterModeIcon(playMode)
        val title = trainingCenterModeTitle(playMode)
        val subtitle = trainingCenterModeSubtitle(playMode)
        val useAlignedSubtitle = stackSubtitle && selectedLanguage != AppLanguage.Chinese
        val label = SpannableStringBuilder().apply {
            append(icon)
            append('\t')
            append(title)
            if (useAlignedSubtitle) {
                append('\n')
                append('\t')
                append(subtitle)
            } else if (stackSubtitle) {
                append('\n')
                append(subtitle)
            } else {
                val separator = if (selectedLanguage == AppLanguage.Chinese) "\u2002·\u2002" else "  ·  "
                append(separator)
                append(subtitle)
            }
        }
        return SpannableString(label).apply {
            val iconLength = icon.length
            val titleStart = iconLength + 1
            val titleEnd = titleStart + title.length
            val subtitleStart =
                if (useAlignedSubtitle) {
                    titleEnd + 2
                } else if (stackSubtitle) {
                    titleEnd + 1
                } else {
                    val separator = if (selectedLanguage == AppLanguage.Chinese) "\u2002·\u2002" else "  ·  "
                    titleEnd + separator.length
                }
            setSpan(ForegroundColorSpan(Color.parseColor(accentColor)), 0, iconLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(1.62f), 0, iconLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(1.04f), titleStart, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(Color.parseColor("#546977")), subtitleStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(if (stackSubtitle) 0.68f else 0.61f), subtitleStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (useAlignedSubtitle) {
                setSpan(
                    TabStopSpan.Standard(dp(58)),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    private fun shouldStackModeSubtitle(playMode: TrainingPlayMode): Boolean {
        val title = trainingCenterModeTitle(playMode)
        val subtitle = trainingCenterModeSubtitle(playMode)
        val visualWidth = approximateVisualWidth(title) + approximateVisualWidth(subtitle)
        return visualWidth > 18.5f
    }

    private fun approximateVisualWidth(text: String): Float {
        var total = 0f
        text.forEach { ch ->
            total +=
                when {
                    Character.isWhitespace(ch) -> 0.35f
                    ch.code in 0x4E00..0x9FFF -> 1.0f
                    Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.THAI -> 0.9f
                    else -> 0.58f
                }
        }
        return total
    }

    private fun modeForPlayMode(playMode: TrainingPlayMode): TrainingMode =
        when (playMode) {
            TrainingPlayMode.Classic30,
            TrainingPlayMode.LevelChallenge,
            TrainingPlayMode.DailyChallenge,
            -> TrainingMode.Seconds30

            TrainingPlayMode.Classic60 -> TrainingMode.Seconds60
            TrainingPlayMode.Burst10 -> TrainingMode.Burst10
            TrainingPlayMode.Burst15 -> TrainingMode.Burst15
        }

    private fun playModeLabel(playMode: TrainingPlayMode): String =
        when (playMode) {
            TrainingPlayMode.Classic30 ->
                localText("🥊 自由拳击", "🥊 Free Boxing", "🥊 Boxe libre", "🥊 ชกอิสระ")

            TrainingPlayMode.Classic60 ->
                localText("🎨 色彩涂鸦", "🎨 Color Graffiti", "🎨 Graffiti couleur", "🎨 สีสันกราฟฟิตี")

            TrainingPlayMode.Burst10 ->
                localText("👑 情绪拳王", "👑 Emotion Champ", "👑 Champion émotion", "👑 ราชันอารมณ์")

            TrainingPlayMode.Burst15 ->
                localText("⚡ 极速燃脂", "⚡ Rapid Fat Burn", "⚡ Brûle-graisse express", "⚡ เผาผลาญเร่งด่วน")

            TrainingPlayMode.LevelChallenge ->
                localText("🔥 燃脂挑战", "🔥 Fat-Burn Challenge", "🔥 Défi brûle-graisse", "🔥 ท้าทายเผาผลาญ")

            TrainingPlayMode.DailyChallenge ->
                localText("🤖 燃脂陪练", "🤖 Fat Burn Sparring", "🤖 Sparring brule-graisse", "🤖 ซ้อมเผาผลาญไขมัน")
        }

    private fun renderTrainingPlayStatus() {
        if (!::trainingPlayTitleView.isInitialized) {
            return
        }
        val goal = currentTrainingGoalPresentation()
        val title =
            if (selectedPlayMode == TrainingPlayMode.Burst15) {
                localText("极速燃脂", "Rapid Fat Burn", "Brûle-graisse express", "เผาผลาญเร่งด่วน")
            } else {
                goal.title
            }
        val body =
            if (selectedPlayMode == TrainingPlayMode.Burst15) {
                localText(
                    "15 秒极速燃脂冲刺，目标 38 击；用更高频率拉升卡路里消耗与燃脂效率。",
                    "A 15-second rapid fat-burn sprint. Target 38 hits while driving calories and burn efficiency with higher pace.",
                    "Sprint brûle-graisse de 15 s. Objectif 38 coups pour brûler plus avec un rythme élevé.",
                    "สปรินต์เผาผลาญ 15 วินาที เป้าหมาย 38 ครั้ง เพื่อเร่งแคลอรีและประสิทธิภาพการเผาผลาญ",
                )
            } else {
                goal.body
            }
        trainingPlayTitleView.text = title
        trainingPlayBodyView.text = body
        trainingPlayProgressView.text = buildTrainingProgressLine(goal.targetHits)
        trainingPlayCard.background = metallicBackground("#142F42", "#08131C", goal.accentColor, 22)
    }

    private fun currentTrainingGoalPresentation(): TrainingGoalPresentation {
        return trainingGoalPresentationFor(selectedPlayMode)
    }

    private fun trainingGoalPresentationFor(playMode: TrainingPlayMode): TrainingGoalPresentation {
        val level = currentTrainingLevelDefinition()
        val dailyTarget = dailyChallengeTargetHits()
        return when (playMode) {
            TrainingPlayMode.Classic30 ->
                TrainingGoalPresentation(
                    title = localText("自由拳击", "Free Boxing", "Boxe libre", "ชกอิสระ"),
                    body = localText(
                        "30 秒自由出拳，适合热身、找节奏与刷新个人最好成绩。",
                        "A 30-second free boxing session for warm-up, rhythm checks, and best-score attempts.",
                        "Session libre de 30 s pour l'échauffement, le rythme et le record personnel.",
                        "เซสชันชกอิสระ 30 วินาที สำหรับวอร์มอัพ จับจังหวะ และทำสถิติใหม่",
                    ),
                    accentColor = "#FF9A30",
                )

            TrainingPlayMode.Classic60 ->
                TrainingGoalPresentation(
                    title = localText("色彩涂鸦", "Color Graffiti", "Graffiti couleur", "สีสันกราฟฟิตี"),
                    body = localText(
                        "60 秒连续输出，把每一次击中都变成更鲜明的节奏轨迹。",
                        "A 60-second flow that turns steady hits into a vivid rhythm trail.",
                        "Un flow de 60 s pour transformer les coups réguliers en trace rythmée.",
                        "การเล่นต่อเนื่อง 60 วินาที เปลี่ยนทุกการชกให้เป็นร่องรอยจังหวะที่ชัดขึ้น",
                    ),
                    accentColor = "#FF5A7A",
                )

            TrainingPlayMode.Burst10 ->
                TrainingGoalPresentation(
                    title = localText("情绪拳王", "Emotion Champ", "Champion émotion", "ราชันอารมณ์"),
                    body = localText(
                        "10 秒情绪释放冲刺，目标 25 击，把注意力集中在启动速度和爆发节奏。",
                        "A 10-second release sprint. Target 25 hits with fast launch speed and sharp rhythm.",
                        "Sprint émotionnel de 10 s. Objectif 25 coups avec départ rapide et rythme net.",
                        "สปรินต์ระบายอารมณ์ 10 วินาที เป้าหมาย 25 ครั้ง เน้นออกตัวเร็วและจังหวะระเบิดพลัง",
                    ),
                    accentColor = "#FFD060",
                    targetHits = 25,
                )

            TrainingPlayMode.Burst15 ->
                TrainingGoalPresentation(
                    title = localText("极速燃脂", "Rapid Fat Burn", "Brûle-graisse express", "เผาผลาญเร่งด่วน"),
                    body = localText(
                        "15 秒极速燃脂冲刺，目标 38 击；用更高频率拉升卡路里消耗与燃脂效率。",
                        "A 15-second rapid fat-burn sprint. Target 38 hits while driving calories and burn efficiency with higher pace.",
                        "Sprint brûle-graisse de 15 s. Objectif 38 coups pour brûler plus avec un rythme élevé.",
                        "สปรินต์เผาผลาญ 15 วินาที เป้าหมาย 38 ครั้ง เพื่อเร่งแคลอรีและประสิทธิภาพการเผาผลาญ",
                    ),
                    accentColor = "#FFE45E",
                    targetHits = 38,
                )

            TrainingPlayMode.LevelChallenge ->
                TrainingGoalPresentation(
                    title = localText(
                        "燃脂挑战 · 目标 ${level.targetHits} 击",
                        "Fat-Burn Challenge · ${level.targetHits} hits",
                        "Défi brûle-graisse · ${level.targetHits} coups",
                        "ท้าทายเผาผลาญ · เป้าหมาย ${level.targetHits} ครั้ง",
                    ),
                    body = localText(
                        "完成本轮燃脂目标即可解锁下一档强度，让训练持续进阶。",
                        "Clear the target to unlock the next intensity tier and keep progression moving.",
                        "Atteignez l'objectif pour débloquer le palier suivant et continuer à progresser.",
                        "ทำเป้าหมายรอบนี้ให้สำเร็จเพื่อปลดล็อกระดับความเข้มข้นถัดไป",
                    ),
                    accentColor = "#FF6B35",
                    targetHits = level.targetHits,
                )

            TrainingPlayMode.DailyChallenge ->
                TrainingGoalPresentation(
                    title = localText(
                        "燃脂陪练 · 目标 $dailyTarget 击",
                        "Fat Burn Sparring · $dailyTarget hits",
                        "Sparring brule-graisse · $dailyTarget coups",
                        "ซ้อมเสมือน · เป้าหมาย $dailyTarget ครั้ง",
                    ),
                    body = localText(
                        "每天一个轻量目标，完成后记录任务奖励，适合养成持续训练习惯。",
                        "A lightweight sparring target that rewards consistency and helps build a daily habit.",
                        "Un objectif léger de sparring pour récompenser la régularité et créer l'habitude.",
                        "เป้าหมายเบา ๆ ในแต่ละวัน เพื่อสร้างนิสัยการฝึกอย่างต่อเนื่อง",
                    ),
                    accentColor = "#FFB347",
                    targetHits = dailyTarget,
                )
        }
    }

    private fun updateTrainingGameAfterReport(
        report: TrainingReport,
        playMode: TrainingPlayMode,
    ): TrainingCoachOutcome {
        val today = todayKey()
        ensureDailyTaskDate(today)
        saveLocalSessionSummary(report)
        val streak = updateTrainingStreak(today)
        prefs.edit().putBoolean(KEY_DAILY_TASK_TRAINED, true).apply()

        var xpGain = 10
        val levelBefore = currentTrainingLevelDefinition().level
        var levelAfter = levelBefore
        val currentGoal = trainingGoalPresentationFor(playMode)
        val goalMet = currentGoal.targetHits?.let { report.totalHits >= it } == true
        if (goalMet) {
            xpGain += 10
            prefs.edit().putBoolean(KEY_DAILY_TASK_TARGET_DONE, true).apply()
        }

        when (playMode) {
            TrainingPlayMode.LevelChallenge -> {
                val level = currentTrainingLevelDefinition()
                if (report.totalHits >= level.targetHits) {
                    val nextLevel = (level.level + 1).coerceAtMost(trainingLevelDefinitions().size)
                    prefs.edit().putInt(KEY_TRAINING_LEVEL, nextLevel).apply()
                    levelAfter = nextLevel
                    xpGain += 25
                }
            }

            TrainingPlayMode.DailyChallenge -> {
                val target = dailyChallengeTargetHits()
                if (report.totalHits >= target) {
                    xpGain += 20
                }
            }

            TrainingPlayMode.Burst10,
            TrainingPlayMode.Burst15,
            -> {
                if (goalMet) {
                    xpGain += 15
                }
            }

            TrainingPlayMode.Classic30,
            TrainingPlayMode.Classic60,
            -> Unit
        }

        addTrainingXp(xpGain)
        return TrainingCoachOutcome(
            playMode = playMode,
            goalMet = goalMet,
            levelBefore = levelBefore,
            levelAfter = levelAfter,
            targetHits = currentGoal.targetHits,
            streak = streak,
            xpGain = xpGain,
        )
    }

    private fun coachMessageForReport(report: TrainingReport): String? =
        lastCoachOutcome?.let { buildCoachMessage(report, it) } ?: lastCoachMessage

    private fun buildCoachMessage(
        report: TrainingReport,
        outcome: TrainingCoachOutcome,
    ): String {
        val challengeMessage = challengeMessageForOutcome(report, outcome)
        val trend = sevenDayTrendText()
        val paceLine =
            when {
                report.averageFrequency >= 3.0f ->
                    localText(
                        "爆发节奏很高，继续保持动作质量。",
                        "High burst rhythm. Keep the movement quality clean.",
                    )
                report.averageFrequency >= 2.0f ->
                    localText(
                        "节奏很稳，已经可以挑战更高目标。",
                        "Solid rhythm. You are ready to chase a higher target.",
                    )
                else ->
                    localText(
                        "先把命中质量稳住，再逐步提速。",
                        "Lock in clean hits first, then build speed gradually.",
                    )
            }
        return localText(
            "$challengeMessage $paceLine 连续训练 ${outcome.streak} 天，XP +${outcome.xpGain}。$trend",
            "$challengeMessage $paceLine Streak ${outcome.streak} day(s), XP +${outcome.xpGain}. $trend",
        )
    }

    private fun challengeMessageForOutcome(
        report: TrainingReport,
        outcome: TrainingCoachOutcome,
    ): String =
        when (outcome.playMode) {
            TrainingPlayMode.LevelChallenge -> {
                val target = outcome.targetHits ?: 0
                if (outcome.goalMet) {
                    if (outcome.levelAfter > outcome.levelBefore) {
                        localText(
                            "燃脂挑战达成，已解锁第 ${outcome.levelAfter} 档强度。",
                            "Fat-burn challenge cleared. Intensity tier ${outcome.levelAfter} unlocked.",
                        )
                    } else {
                        localText(
                            "已完成最高燃脂目标，继续刷新极限。",
                            "Top fat-burn target cleared. Keep pushing your limit.",
                        )
                    }
                } else {
                    val remaining = (target - report.totalHits).coerceAtLeast(0)
                    localText(
                        "燃脂挑战还差 $remaining 击，下次优先稳住节奏。",
                        "$remaining hits to clear the fat-burn target. Keep the rhythm steady next time.",
                    )
                }
            }

            TrainingPlayMode.DailyChallenge -> {
                val target = outcome.targetHits ?: dailyChallengeTargetHits()
                if (outcome.goalMet) {
                    localText(
                        "燃脂陪练目标完成，已记录任务奖励。",
                        "Fat burn sparring target completed and rewarded.",
                    )
                } else {
                    val remaining = (target - report.totalHits).coerceAtLeast(0)
                    localText(
                        "燃脂陪练目标还差 $remaining 击，再来一轮就有机会完成。",
                        "$remaining hits short of the fat burn sparring target. One more run can do it.",
                    )
                }
            }

            TrainingPlayMode.Burst10 ->
                if (outcome.goalMet) {
                    localText(
                        "情绪拳王目标达成，释放得很漂亮。",
                        "Emotion Champ target reached. Your release rhythm looks sharp.",
                    )
                } else {
                    localText(
                        "情绪拳王训练完成，下轮可以把前 3 秒打得更主动。",
                        "Emotion Champ session complete. Try attacking the first 3 seconds harder next round.",
                    )
                }

            TrainingPlayMode.Burst15 ->
                if (outcome.goalMet) {
                    localText(
                        "极速燃脂目标达成，节奏和燃脂效率都很亮眼。",
                        "Rapid Fat Burn target reached. Your pace and burn efficiency look strong.",
                    )
                } else {
                    localText(
                        "极速燃脂训练完成，下轮继续把频率再推高一点。",
                        "Rapid Fat Burn session complete. Push the pace even higher next round.",
                    )
                }

            TrainingPlayMode.Classic30 ->
                localText(
                    "自由拳击已记录，今天的节奏又往前推进了一步。",
                    "Free Boxing recorded. Today's rhythm moved one step forward.",
                    "Boxe libre enregistrée. Le rythme d'aujourd'hui avance encore.",
                    "บันทึกชกอิสระแล้ว จังหวะวันนี้ก้าวหน้าอีกขั้น",
                )

            TrainingPlayMode.Classic60 ->
                localText(
                    "色彩涂鸦已记录，连续输出越来越稳定。",
                    "Color Graffiti recorded. Your sustained rhythm is getting steadier.",
                    "Graffiti couleur enregistré. Votre rythme devient plus stable.",
                    "บันทึกสีสันกราฟฟิตีแล้ว จังหวะต่อเนื่องนิ่งขึ้น",
                )
        }

    private fun buildTrainingProgressLine(targetHits: Int?): String {
        ensureDailyTaskDate()
        val targetText =
            targetHits?.let {
                localText("本轮目标 $it 击", "Target $it hits", "Objectif $it coups", "เป้าหมายรอบนี้ $it ครั้ง")
            } ?: localText("自由训练", "Free training", "Entrainement libre", "ฝึกอิสระ")
        return localText(
            "$targetText · ${dailyTaskSummaryText()} · ${sevenDayTrendText()}",
            "$targetText · ${dailyTaskSummaryText()} · ${sevenDayTrendText()}",
        )
    }

    private fun dailyTaskSummaryText(): String {
        ensureDailyTaskDate()
        val doneCount =
            listOf(
                prefs.getBoolean(KEY_DAILY_TASK_TRAINED, false),
                prefs.getBoolean(KEY_DAILY_TASK_TARGET_DONE, false),
                prefs.getBoolean(KEY_DAILY_TASK_SHARED, false),
            ).count { it }
        val streak = prefs.getInt(KEY_TRAINING_STREAK, 0)
        val xp = prefs.getInt(KEY_TRAINING_XP, 0)
        return localText(
            "今日任务 $doneCount/3 · 连续 $streak 天 · XP $xp",
            "Daily tasks $doneCount/3 · Streak $streak · XP $xp",
            "Tâches du jour $doneCount/3 · Série $streak j · XP $xp",
            "ภารกิจวันนี้ $doneCount/3 · ต่อเนื่อง $streak วัน · XP $xp",
        )
    }

    private fun markTrainingSharedForDailyTask() {
        ensureDailyTaskDate()
        prefs.edit().putBoolean(KEY_DAILY_TASK_SHARED, true).apply()
        renderTrainingPlayStatus()
    }

    private fun trainingLevelDefinitions(): List<TrainingLevelDefinition> =
        listOf(
            TrainingLevelDefinition(1, 12),
            TrainingLevelDefinition(2, 18),
            TrainingLevelDefinition(3, 25),
            TrainingLevelDefinition(4, 32),
            TrainingLevelDefinition(5, 40),
            TrainingLevelDefinition(6, 50),
            TrainingLevelDefinition(7, 60),
            TrainingLevelDefinition(8, 75),
            TrainingLevelDefinition(9, 90),
        )

    private fun currentTrainingLevelDefinition(): TrainingLevelDefinition {
        val levels = trainingLevelDefinitions()
        val level = prefs.getInt(KEY_TRAINING_LEVEL, 1).coerceIn(1, levels.size)
        return levels.firstOrNull { it.level == level } ?: levels.first()
    }

    private fun dailyChallengeTargetHits(): Int {
        val best30 = max(cloudStatistics?.best30Hits ?: 0, bestLocalThirtySecondHits())
        return if (best30 <= 0) {
            20
        } else {
            max(18, (best30 * 0.82f).toInt()).coerceIn(18, 120)
        }
    }

    private fun bestLocalThirtySecondHits(): Int =
        loadLocalSessionSummaries()
            .filter { it.durationSeconds == 30 }
            .map { it.hits }
            .maxOrNull() ?: 0

    private fun addTrainingXp(value: Int) {
        val current = prefs.getInt(KEY_TRAINING_XP, 0)
        prefs.edit().putInt(KEY_TRAINING_XP, (current + value).coerceAtMost(999_999)).apply()
    }

    private fun updateTrainingStreak(today: String): Int {
        val previousDate = prefs.getString(KEY_TRAINING_LAST_DATE, null)
        val current = prefs.getInt(KEY_TRAINING_STREAK, 0)
        val next =
            when {
                previousDate == today -> current.coerceAtLeast(1)
                previousDate == dayKey(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)) -> current + 1
                else -> 1
            }
        val best = max(prefs.getInt(KEY_BEST_TRAINING_STREAK, 0), next)
        prefs.edit()
            .putString(KEY_TRAINING_LAST_DATE, today)
            .putInt(KEY_TRAINING_STREAK, next)
            .putInt(KEY_BEST_TRAINING_STREAK, best)
            .apply()
        return next
    }

    private fun ensureDailyTaskDate(today: String = todayKey()) {
        if (prefs.getString(KEY_DAILY_TASK_DATE, null) == today) {
            return
        }
        prefs.edit()
            .putString(KEY_DAILY_TASK_DATE, today)
            .putBoolean(KEY_DAILY_TASK_TRAINED, false)
            .putBoolean(KEY_DAILY_TASK_TARGET_DONE, false)
            .putBoolean(KEY_DAILY_TASK_SHARED, false)
            .apply()
    }

    private fun saveLocalSessionSummary(report: TrainingReport) {
        val summaries = loadLocalSessionSummaries().toMutableList()
        summaries.add(
            LocalSessionSummary(
                dateKey = dayKey(report.endedAtEpochMs),
                endedAtMs = report.endedAtEpochMs,
                durationSeconds = report.mode.durationSeconds,
                hits = report.totalHits,
                playMode = selectedPlayMode.name,
            ),
        )
        val array = JSONArray()
        summaries.sortedByDescending { it.endedAtMs }.take(60).forEach { item ->
            array.put(
                JSONObject()
                    .put("date", item.dateKey)
                    .put("endedAt", item.endedAtMs)
                    .put("duration", item.durationSeconds)
                    .put("hits", item.hits)
                    .put("playMode", item.playMode),
            )
        }
        prefs.edit().putString(KEY_LOCAL_TRAINING_SESSIONS, array.toString()).apply()
    }

    private fun loadLocalSessionSummaries(): List<LocalSessionSummary> {
        val raw = prefs.getString(KEY_LOCAL_TRAINING_SESSIONS, null).orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        LocalSessionSummary(
                            dateKey = item.optString("date"),
                            endedAtMs = item.optLong("endedAt"),
                            durationSeconds = item.optInt("duration"),
                            hits = item.optInt("hits"),
                            playMode = item.optString("playMode"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun buildTodayAggregateReport(): DailyAggregateReport {
        val moduleTotals =
            listOf(
                loadEmotionModuleTotals("free_boxing_daily_emotion"),
                loadEmotionModuleTotals("color_graffiti_daily_emotion"),
                loadEmotionModuleTotals("emotion_champ_daily_emotion"),
                loadBlitzTodayTotals(),
                loadFatBurnTodayTotals("fat_burn_challenge"),
                loadFatBurnTodayTotals("fat_burn_coach"),
            )
        return DailyAggregateReport(
            totalHits = moduleTotals.sumOf { it.hits },
            stressReduction = moduleTotals.sumOf { it.stressReduction.toDouble() }.toFloat(),
            calmIncrease = moduleTotals.sumOf { it.calmIncrease.toDouble() }.toFloat(),
            totalCalories = moduleTotals.sumOf { it.calories.toDouble() }.toFloat(),
            totalFatBurnGrams = moduleTotals.sumOf { it.fatBurnGrams.toDouble() }.toFloat(),
            totalDurationMinutes = moduleTotals.sumOf { it.durationSeconds.toDouble() }.toFloat() / 60f,
            activeModules = moduleTotals.count { it.hasData() },
        )
    }

    private fun buildWorkoutResultsDashboard(): WorkoutResultsDashboard {
        val now = System.currentTimeMillis()
        val endMsExclusive = now + 1L
        val registeredAt = aggregateRegisteredStartMs()
        return WorkoutResultsDashboard(
            today = buildWorkoutTotalsForWindow(aggregateStartOfDay(now), endMsExclusive),
            week = buildWorkoutTotalsForWindow(aggregateStartOfWeek(now), endMsExclusive),
            month = buildWorkoutTotalsForWindow(aggregateStartOfMonth(now), endMsExclusive),
            year = buildWorkoutTotalsForWindow(aggregateStartOfYear(now), endMsExclusive),
            sinceRegistered = buildWorkoutTotalsForWindow(registeredAt, endMsExclusive),
            registeredAtEpochMs = registeredAt,
        )
    }

    private fun buildWorkoutTotalsForWindow(
        startMs: Long,
        endMsExclusive: Long,
    ): WorkoutMetricTotals =
        listOf(
            loadEmotionWindowTotals("free_boxing_daily_emotion", startMs, endMsExclusive),
            loadEmotionWindowTotals("color_graffiti_daily_emotion", startMs, endMsExclusive),
            loadEmotionWindowTotals("emotion_champ_daily_emotion", startMs, endMsExclusive),
            loadBlitzWindowTotals(startMs, endMsExclusive),
            loadFatBurnWindowTotals("fat_burn_challenge", startMs, endMsExclusive),
            loadFatBurnWindowTotals("fat_burn_coach", startMs, endMsExclusive),
        ).fold(WorkoutMetricTotals()) { acc, item -> acc + item }

    private fun loadEmotionModuleTotals(prefsName: String): ModuleTodayTotals {
        val emotionPrefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        if (emotionPrefs.getString("day_key", null) != aggregateTodayKey()) {
            return ModuleTodayTotals()
        }
        val stressReduction = (100f - emotionPrefs.getFloat("stress_value", 100f)).coerceAtLeast(0f)
        val calmIncrease = (emotionPrefs.getFloat("calm_value", 50f) - 50f).coerceAtLeast(0f)
        val inferredHits =
            max(
                ((stressReduction / 0.2f) + 0.5f).toInt(),
                ((calmIncrease / 0.2f) + 0.5f).toInt(),
            )
        val estimatedCalories = estimateEmotionCalories(inferredHits)
        return ModuleTodayTotals(
            hits = inferredHits,
            stressReduction = stressReduction,
            calmIncrease = calmIncrease,
            calories = estimatedCalories,
            fatBurnGrams = estimateEmotionFatBurn(estimatedCalories),
            durationSeconds = emotionPrefs.getInt("duration_seconds", 0).coerceAtLeast(0),
        )
    }

    private fun loadEmotionWindowTotals(
        prefsName: String,
        startMs: Long,
        endMsExclusive: Long,
    ): WorkoutMetricTotals {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        var totals = WorkoutMetricTotals()
        var todayAlreadyCaptured = false
        val rawHistory = prefs.getString("history_json", null).orEmpty()
        if (rawHistory.isNotBlank()) {
            runCatching {
                val array = JSONArray(rawHistory)
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val timestampMs = item.optLong("timestampMs", 0L)
                    if (timestampMs < startMs || timestampMs >= endMsExclusive) {
                        continue
                    }
                    val sessionHits = item.optInt("hits", 0).coerceAtLeast(0)
                    val sessionCalories = estimateEmotionCalories(sessionHits)
                    totals +=
                        WorkoutMetricTotals(
                            hits = sessionHits,
                            durationSeconds = item.optInt("durationSec", 0).coerceAtLeast(0),
                            calories = sessionCalories,
                            fatBurnGrams = estimateEmotionFatBurn(sessionCalories),
                        )
                    if (aggregateTodayKey(timestampMs) == aggregateTodayKey()) {
                        todayAlreadyCaptured = true
                    }
                }
            }
        }
        val now = System.currentTimeMillis()
        if (now in startMs until endMsExclusive && !todayAlreadyCaptured && prefs.getString("day_key", null) == aggregateTodayKey()) {
            val stressReduction = (100f - prefs.getFloat("stress_value", 100f)).coerceAtLeast(0f)
            val calmIncrease = (prefs.getFloat("calm_value", 50f) - 50f).coerceAtLeast(0f)
            val currentHits =
                max(
                    ((stressReduction / 0.2f) + 0.5f).toInt(),
                    ((calmIncrease / 0.2f) + 0.5f).toInt(),
                ).coerceAtLeast(0)
            val currentCalories = estimateEmotionCalories(currentHits)
            totals +=
                WorkoutMetricTotals(
                    hits = currentHits,
                    durationSeconds = prefs.getInt("duration_seconds", 0).coerceAtLeast(0),
                    calories = currentCalories,
                    fatBurnGrams = estimateEmotionFatBurn(currentCalories),
                )
        }
        return totals
    }

    private fun estimateEmotionCalories(hits: Int): Float =
        (hits.coerceAtLeast(0) * EMOTION_MODE_HIT_CALORIES).coerceAtLeast(0f)

    private fun estimateEmotionFatBurn(calories: Float): Float =
        (calories.coerceAtLeast(0f) * EMOTION_MODE_FAT_BURN_RATIO).coerceAtLeast(0f)

    private fun loadBlitzTodayTotals(): ModuleTodayTotals {
        val aggregatePrefs = getSharedPreferences("blitz_mode_aggregate", MODE_PRIVATE)
        val dayKey = blitzAggregateDayKey(System.currentTimeMillis())
        return ModuleTodayTotals(
            hits = aggregatePrefs.getInt("${dayKey}_hits", 0).coerceAtLeast(0),
            calories = aggregatePrefs.getFloat("${dayKey}_calories", 0f).coerceAtLeast(0f),
            fatBurnGrams = aggregatePrefs.getFloat("${dayKey}_fat", 0f).coerceAtLeast(0f),
            durationSeconds = aggregatePrefs.getInt("${dayKey}_duration_sec", 0).coerceAtLeast(0),
        )
    }

    private fun loadBlitzWindowTotals(
        startMs: Long,
        endMsExclusive: Long,
    ): WorkoutMetricTotals {
        val aggregatePrefs = getSharedPreferences("blitz_mode_aggregate", MODE_PRIVATE)
        val dayPrefixes =
            aggregatePrefs.all.keys
                .filter { it.startsWith("day_") && it.endsWith("_hits") && it.length == 17 }
                .map { it.removeSuffix("_hits") }
                .toSet()
        var totals = WorkoutMetricTotals()
        dayPrefixes.forEach { prefix ->
            val dayTimestamp = parseBlitzAggregateDayStart(prefix.removePrefix("day_")) ?: return@forEach
            if (dayTimestamp < startMs || dayTimestamp >= endMsExclusive) {
                return@forEach
            }
            totals +=
                WorkoutMetricTotals(
                    hits = aggregatePrefs.getInt("${prefix}_hits", 0).coerceAtLeast(0),
                    durationSeconds = aggregatePrefs.getInt("${prefix}_duration_sec", 0).coerceAtLeast(0),
                    calories = aggregatePrefs.getFloat("${prefix}_calories", 0f).coerceAtLeast(0f),
                    fatBurnGrams = aggregatePrefs.getFloat("${prefix}_fat", 0f).coerceAtLeast(0f),
                )
        }
        return totals
    }

    private fun loadFatBurnTodayTotals(prefsName: String): ModuleTodayTotals {
        val modulePrefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val raw = modulePrefs.getString("daily_reports_json", null).orEmpty()
        if (raw.isBlank()) {
            return ModuleTodayTotals()
        }
        return runCatching {
            val array = JSONArray(raw)
            var hits = 0
            var calories = 0f
            var fatBurn = 0f
            var durationSeconds = 0
            val today = aggregateTodayKey()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (aggregateTodayKey(item.optLong("timestampMs", 0L)) != today) {
                    continue
                }
                hits += item.optInt("totalHits", 0).coerceAtLeast(0)
                calories += item.optDouble("calories", 0.0).toFloat().coerceAtLeast(0f)
                fatBurn += item.optDouble("fatBurnGrams", 0.0).toFloat().coerceAtLeast(0f)
                durationSeconds += item.optInt("durationSec", 0).coerceAtLeast(0)
            }
            ModuleTodayTotals(hits = hits, calories = calories, fatBurnGrams = fatBurn, durationSeconds = durationSeconds)
        }.getOrDefault(ModuleTodayTotals())
    }

    private fun loadFatBurnWindowTotals(
        prefsName: String,
        startMs: Long,
        endMsExclusive: Long,
    ): WorkoutMetricTotals {
        val modulePrefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val raw = modulePrefs.getString("daily_reports_json", null).orEmpty()
        if (raw.isBlank()) {
            return WorkoutMetricTotals()
        }
        return runCatching {
            val array = JSONArray(raw)
            var totals = WorkoutMetricTotals()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val timestampMs = item.optLong("timestampMs", 0L)
                if (timestampMs < startMs || timestampMs >= endMsExclusive) {
                    continue
                }
                totals +=
                    WorkoutMetricTotals(
                        hits = item.optInt("totalHits", 0).coerceAtLeast(0),
                        durationSeconds = item.optInt("durationSec", 0).coerceAtLeast(0),
                        calories = item.optDouble("calories", 0.0).toFloat().coerceAtLeast(0f),
                        fatBurnGrams = item.optDouble("fatBurnGrams", 0.0).toFloat().coerceAtLeast(0f),
                    )
            }
            totals
        }.getOrDefault(WorkoutMetricTotals())
    }

    private fun aggregateTodayKey(timestampMs: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(timestampMs))

    private fun blitzAggregateDayKey(timestampMs: Long): String =
        SimpleDateFormat("'day_'yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(timestampMs))

    private fun parseBlitzAggregateDayStart(dayKey: String): Long? =
        runCatching {
            SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }.parse(dayKey)?.time
        }.getOrNull()

    private fun aggregateRegisteredStartMs(): Long {
        val activatedAt = activationState?.activatedAtEpochMs?.takeIf { it > 0L }
        val installedAt =
            runCatching {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).firstInstallTime
            }.getOrDefault(System.currentTimeMillis())
        return aggregateStartOfDay(activatedAt ?: installedAt)
    }

    private fun aggregateStartOfDay(timestampMs: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timestampMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun aggregateStartOfWeek(timestampMs: Long): Long {
        val calendar =
            Calendar.getInstance().apply {
                timeInMillis = timestampMs
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val delta = (7 + (calendar.get(Calendar.DAY_OF_WEEK) - calendar.firstDayOfWeek)) % 7
        calendar.add(Calendar.DAY_OF_MONTH, -delta)
        return calendar.timeInMillis
    }

    private fun aggregateStartOfMonth(timestampMs: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timestampMs
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun aggregateStartOfYear(timestampMs: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timestampMs
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun formatDailyAggregateValue(value: Float): String = String.format(Locale.US, "%.1f", value)

    private fun buildAggregateMetricValueText(
        primary: String,
        unit: String,
    ): CharSequence {
        val textValue = "$primary $unit".trim()
        val unitStart = textValue.lastIndexOf(' ')
        return if (unitStart > 0 && unitStart < textValue.length - 1) {
            SpannableStringBuilder(textValue).apply {
                setSpan(
                    RelativeSizeSpan(1.18f),
                    0,
                    unitStart,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    ForegroundColorSpan(Color.parseColor("#A9B9C8")),
                    unitStart + 1,
                    textValue.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    RelativeSizeSpan(0.72f),
                    unitStart + 1,
                    textValue.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        } else {
            SpannableStringBuilder(textValue).apply {
                setSpan(
                    RelativeSizeSpan(1.12f),
                    0,
                    textValue.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    private fun sevenDayTrendText(): String {
        val sessions = loadLocalSessionSummaries().sortedBy { it.endedAtMs }.takeLast(12)
        if (sessions.size < 2) {
            return localText("7天趋势：等待更多数据", "7-day trend: collecting data", "Tendance 7 j : collecte de données", "แนวโน้ม 7 วัน: กำลังเก็บข้อมูล")
        }
        val splitIndex = (sessions.size / 2).coerceAtLeast(1)
        val early = sessions.take(splitIndex)
        val recent = sessions.drop(splitIndex).ifEmpty { sessions.takeLast(1) }
        val earlyAverage = early.map { it.hits }.average()
        val recentAverage = recent.map { it.hits }.average()
        val diff = recentAverage - earlyAverage
        return when {
            diff >= 2.0 ->
                localText(
                    "7天趋势：提升 +${String.format(Locale.US, "%.1f", diff)}",
                    "7-day trend: +${String.format(Locale.US, "%.1f", diff)}",
                    "Tendance 7 j : +${String.format(Locale.US, "%.1f", diff)}",
                    "แนวโน้ม 7 วัน: +${String.format(Locale.US, "%.1f", diff)}",
                )

            diff <= -2.0 ->
                localText(
                    "7天趋势：回落 ${String.format(Locale.US, "%.1f", diff)}",
                    "7-day trend: ${String.format(Locale.US, "%.1f", diff)}",
                    "Tendance 7 j : ${String.format(Locale.US, "%.1f", diff)}",
                    "แนวโน้ม 7 วัน: ${String.format(Locale.US, "%.1f", diff)}",
                )

            else ->
                localText("7天趋势：稳定", "7-day trend: stable", "Tendance 7 j : stable", "แนวโน้ม 7 วัน: คงที่")
        }
    }

    private fun todayKey(): String = dayKey(System.currentTimeMillis())

    private fun dayKey(epochMs: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(epochMs))

    private fun localText(
        chinese: String,
        english: String,
        french: String = english,
        thai: String = english,
    ): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> chinese
            AppLanguage.English -> english
            AppLanguage.French -> french
            AppLanguage.Thai -> thai
        }

    private fun firstLaunchBlePromptMessage(): String =
        localText(
            "请到设置界面连接蓝牙设备",
            "Please connect your Bluetooth device in Settings.",
            "Veuillez connecter l'appareil Bluetooth dans les réglages.",
            "โปรดเชื่อมต่ออุปกรณ์บลูทูธในหน้าตั้งค่า",
        )

    private fun goToSettingsLabel(): String =
        localText("去设置", "Settings", "Réglages", "ไปที่ตั้งค่า")

    private fun laterLabel(): String =
        localText("稍后", "Later", "Plus tard", "ภายหลัง")

    private fun bleDeviceSectionTitle(): String =
        localText("蓝牙设备", "Bluetooth Devices", "Appareils Bluetooth", "อุปกรณ์บลูทูธ")

    private fun deviceFeaturesSectionTitle(): String =
        localText("设备功能", "Device Features", "Fonctions de l'appareil", "ฟังก์ชันอุปกรณ์")

    private fun bleScanLabel(): String =
        localText("扫描", "Scan", "Scanner", "สแกน")

    private fun bleConnectLabel(): String =
        localText("连接", "Connect", "Connecter", "เชื่อมต่อ")

    private fun bleDisconnectLabel(): String =
        localText("断开", "Disconnect", "Déconnecter", "ตัดการเชื่อมต่อ")

    private fun bleConnectedLabel(): String =
        localText("蓝牙已连接", "Bluetooth connected", "Bluetooth connecté", "เชื่อมต่อบลูทูธแล้ว")

    private fun bleDisconnectedLabel(): String =
        localText("蓝牙未连接", "Bluetooth disconnected", "Bluetooth déconnecté", "ยังไม่ได้เชื่อมต่อบลูทูธ")

    private fun bleDisconnectedDoneMessage(): String =
        localText("已断开", "Disconnected", "Déconnecté", "ตัดการเชื่อมต่อแล้ว")

    private fun blePhoneOffMessage(): String =
        localText("手机蓝牙未开启", "Phone Bluetooth is off", "Le Bluetooth du téléphone est désactivé", "บลูทูธของโทรศัพท์ปิดอยู่")

    private fun bleNoDeviceSelectedLabel(): String =
        localText("未选择设备", "No device selected", "Aucun appareil sélectionné", "ยังไม่ได้เลือกอุปกรณ์")

    private fun bleSelectedDevicePrefix(): String =
        localText("已选择：", "Selected: ", "Sélectionné : ", "เลือกแล้ว: ")

    private fun bleConnectedDevicesText(count: Int): String =
        localText("已连接 $count 个设备：", "$count devices connected:", "$count appareil(s) connecté(s) :", "เชื่อมต่อแล้ว $count อุปกรณ์:")

    private fun bleLeftHandLabel(): String =
        localText("左手", "Left", "Gauche", "ซ้าย")

    private fun bleRightHandLabel(): String =
        localText("右手", "Right", "Droite", "ขวา")

    private fun bleLeftShortLabel(): String =
        localText("左", "L", "G", "ซ")

    private fun bleRightShortLabel(): String =
        localText("右", "R", "D", "ข")

    private fun bleUnknownLabel(): String =
        localText("未知", "Unknown", "Inconnu", "ไม่ทราบ")

    private fun blePairIdLabel(): String =
        localText("编号", "ID", "ID", "รหัส")

    private fun bleUnrecognizedPairLabel(): String =
        localText("未识别编号", "Unknown ID", "ID inconnu", "ไม่ทราบรหัส")

    private fun bleScanFirstMessage(): String =
        localText(
            "请先扫描 BOXING 设备",
            "Please scan for BOXING devices first.",
            "Veuillez d'abord scanner les appareils BOXING.",
            "โปรดสแกนอุปกรณ์ BOXING ก่อน",
        )

    private fun blePermissionDeniedMessage(): String =
        localText(
            "蓝牙权限未授予，无法扫描设备",
            "Bluetooth permission was not granted. Cannot scan devices.",
            "Autorisation Bluetooth refusée. Impossible de scanner.",
            "ไม่ได้รับอนุญาตบลูทูธ จึงไม่สามารถสแกนอุปกรณ์ได้",
        )

    private fun bleScanningMessage(): String =
        localText("正在扫描 BOXING 设备", "Scanning for BOXING devices", "Recherche des appareils BOXING", "กำลังสแกนอุปกรณ์ BOXING")

    private fun bleNoBoxingDeviceMessage(): String =
        localText("未发现 BOXING 设备", "No BOXING device found", "Aucun appareil BOXING trouvé", "ไม่พบอุปกรณ์ BOXING")

    private fun bleFoundDevicesMessage(count: Int): String =
        localText("发现 $count 个 BOXING 设备", "Found $count BOXING devices", "$count appareil(s) BOXING trouvé(s)", "พบอุปกรณ์ BOXING $count เครื่อง")

    private fun bleScanCompleteMessage(count: Int): String =
        localText("扫描完成，发现 $count 个设备", "Scan complete. Found $count devices.", "Scan terminé. $count appareil(s) trouvé(s).", "สแกนเสร็จแล้ว พบ $count อุปกรณ์")

    private fun bleConnectingPrefix(): String =
        localText("正在连接 ", "Connecting ", "Connexion ", "กำลังเชื่อมต่อ ")

    private fun bleConnectedDiscoveringSuffix(): String =
        localText("已连接，正在发现服务", "connected, discovering services", "connecté, recherche des services", "เชื่อมต่อแล้ว กำลังค้นหาบริการ")

    private fun bleServicesReadySuffix(): String =
        localText("服务已发现，正在打开通知", "services found, enabling notifications", "services trouvés, activation des notifications", "พบบริการแล้ว กำลังเปิดการแจ้งเตือน")

    private fun bleNotifyReadyGyroOffSuffix(): String =
        localText("通知已打开，陀螺仪保持关闭", "notifications enabled, gyroscope stays off", "notifications activées, gyroscope désactivé", "เปิดการแจ้งเตือนแล้ว ไจโรยังปิดอยู่")

    private fun bleDisconnectedStatusSuffix(): String =
        localText("已断开 status=", "disconnected status=", "déconnecté status=", "ตัดการเชื่อมต่อ status=")

    private fun bleReconnectingPrefix(): String =
        localText("蓝牙断开，正在自动重连", "Bluetooth disconnected, auto reconnecting", "Bluetooth déconnecté, reconnexion automatique", "บลูทูธหลุด กำลังเชื่อมต่อใหม่อัตโนมัติ")

    private fun bleAutoConnectingMessage(): String =
        localText(
            "正在自动连接上次蓝牙设备",
            "Auto-connecting the last Bluetooth devices",
            "Connexion automatique aux derniers appareils Bluetooth",
            "กำลังเชื่อมต่ออุปกรณ์บลูทูธล่าสุดอัตโนมัติ",
        )

    private fun boxingBlePairErrorMessage(): String =
        localText(
            "设备蓝牙连接有误，请到设置界面扫描后连接",
            "Bluetooth device connection error. Please scan and connect in Settings.",
            "Erreur de connexion Bluetooth. Scannez et connectez l'appareil dans les réglages.",
            "การเชื่อมต่อบลูทูธของอุปกรณ์ผิดพลาด โปรดสแกนและเชื่อมต่อในหน้าตั้งค่า",
        )

    private fun bleIdleDisconnectedMessage(): String =
        localText(
            "10分钟未检测到拳击动作，已断开蓝牙",
            "No punch detected for 10 minutes. Bluetooth disconnected.",
            "Aucun coup détecté pendant 10 minutes. Bluetooth déconnecté.",
            "ไม่พบการชกเป็นเวลา 10 นาที จึงตัดการเชื่อมต่อบลูทูธแล้ว",
        )

    private fun sensitivityReadingDeviceText(): String =
        localText(
            "正在读取设备灵敏度...",
            "Reading device sensitivity...",
            "Lecture de la sensibilité de l'appareil...",
            "กำลังอ่านค่าความไวของอุปกรณ์...",
        )

    private fun sensitivityDeviceNotConnectedText(): String =
        localText(
            "蓝牙未连接，进入设置时未读取设备灵敏度",
            "Bluetooth is not connected. Device sensitivity was not read.",
            "Bluetooth non connecté. La sensibilité de l'appareil n'a pas été lue.",
            "ยังไม่ได้เชื่อมต่อบลูทูธ จึงไม่ได้อ่านค่าความไวของอุปกรณ์",
        )

    private fun buildProfileSummaryText(): String {
        val profile = cloudProfile
        if (profile == null) {
            return localText(
                "等待你的拳击档案",
                "Waiting for your fighter profile",
                "En attente de votre profil de boxeur",
                "กำลังรอโปรไฟล์นักชกของคุณ",
            )
        }
        return profile.nickname
    }

    private fun headerSubtitleText(): String = ""

    private fun buildProfileMetaSummary(): String {
        val profile = cloudProfile ?: return ""
        val primaryLine =
            mutableListOf(
                "${tr("activation_serial_label")}: ${profile.serialMasked}",
                "${tr("profile_language")}: ${languageDisplayName(AppLanguage.fromStorage(profile.languageCode))}",
            )
        val countryCode = normalizedCountryCode(profile.countryCode)
        return buildString {
            append(primaryLine.joinToString("  ·  "))
            if (countryCode != null) {
                append('\n')
                append("${tr("profile_country")}: $countryCode")
            }
        }
    }

    private fun buildProfileStatsOverview(): String {
        val stats = cloudStatistics
        val dashboard = buildWorkoutResultsDashboard()
        val lifetimeTotals = dashboard.sinceRegistered
        val bestDayHits =
            buildDailyWorkoutTotalsMap(aggregateRegisteredStartMs(), System.currentTimeMillis() + 1L)
                .values
                .maxOfOrNull { it.hits }
                ?.coerceAtLeast(0)
                ?: 0
        if (!lifetimeTotals.hasData() && stats == null) {
            return localText(
                "完成首次训练并同步云端后，这里会显示你的累计锻炼成果与榜单统计口径。",
                "Finish and sync your first session to unlock lifetime workout results and ranking metrics here.",
                "Terminez et synchronisez votre première séance pour afficher ici les résultats cumulés et les classements.",
                "ฝึกครั้งแรกและซิงก์คลาวด์ให้เสร็จ แล้วระบบจะแสดงผลสะสมและสถิติอันดับที่นี่",
            )
        }
        return buildString {
            append(
                localText(
                    "历史累计拳击数：${lifetimeTotals.hits}",
                    "Lifetime punch count: ${lifetimeTotals.hits}",
                    "Coups cumulés : ${lifetimeTotals.hits}",
                    "หมัดสะสมทั้งหมด: ${lifetimeTotals.hits}",
                ),
            )
            append('\n')
            append(
                localText(
                    "单日最高拳击数：$bestDayHits",
                    "Best single-day punches: $bestDayHits",
                    "Meilleur total quotidien : $bestDayHits",
                    "สถิติหมัดสูงสุดต่อวัน: $bestDayHits",
                ),
            )
            append('\n')
            append(
                localText(
                    "历史累计训练时长：${formatWorkoutDurationMinutes(lifetimeTotals.durationSeconds)} 分钟",
                    "Lifetime training time: ${formatWorkoutDurationMinutes(lifetimeTotals.durationSeconds)} min",
                    "Temps cumulé : ${formatWorkoutDurationMinutes(lifetimeTotals.durationSeconds)} min",
                    "เวลาฝึกสะสม: ${formatWorkoutDurationMinutes(lifetimeTotals.durationSeconds)} นาที",
                ),
            )
            append('\n')
            append(
                localText(
                    "累计消耗卡路里：${String.format(Locale.US, "%.1f", lifetimeTotals.calories)} kcal",
                    "Lifetime calories: ${String.format(Locale.US, "%.1f", lifetimeTotals.calories)} kcal",
                    "Calories cumulées : ${String.format(Locale.US, "%.1f", lifetimeTotals.calories)} kcal",
                    "แคลอรีสะสม: ${String.format(Locale.US, "%.1f", lifetimeTotals.calories)} kcal",
                ),
            )
            append('\n')
            append(
                localText(
                    "累计燃脂量：${String.format(Locale.US, "%.1f", lifetimeTotals.fatBurnGrams)} g",
                    "Lifetime fat burn: ${String.format(Locale.US, "%.1f", lifetimeTotals.fatBurnGrams)} g",
                    "Graisse brûlée cumulée : ${String.format(Locale.US, "%.1f", lifetimeTotals.fatBurnGrams)} g",
                    "ไขมันที่เผาผลาญสะสม: ${String.format(Locale.US, "%.1f", lifetimeTotals.fatBurnGrams)} g",
                ),
            )
            append('\n')
            append(
                localText(
                    "${activeDaysLabel()}：${stats?.activeDays ?: 0}",
                    "${activeDaysLabel()}: ${stats?.activeDays ?: 0}",
                    "${activeDaysLabel()} : ${stats?.activeDays ?: 0}",
                    "${activeDaysLabel()}: ${stats?.activeDays ?: 0}",
                ),
            )
            append('\n')
            append(
                localText(
                    "榜单维度：单日最高拳击数 / 累计拳击数 / 训练时长 / 消耗卡路里 / 燃脂量",
                    "Ranking metrics: best single-day punches / lifetime punches / training time / calories / fat burn",
                    "Classements : meilleur jour / coups cumulés / durée / calories / graisse brûlée",
                    "เกณฑ์จัดอันดับ: สูงสุดต่อวัน / หมัดสะสม / เวลา / แคลอรี / การเผาผลาญไขมัน",
                ),
            )
        }
    }

    private fun buildProfileTierSummary(): String {
        val tier = cloudTier ?: return tierLabelForLevel(cloudProfile?.currentTier ?: 1)
        val tierName = tierLabelForKey(tier.key)
        return if (tier.nextHits != null && tier.nextKey != null) {
            "$tierName  Lv.${tier.level}  |  ${nextTierLabel()}: ${tierLabelForKey(tier.nextKey)} (30秒 ${tier.bestHits}/${tier.nextHits} · 累计 ${tier.totalHits}/${tier.nextTotalHits ?: 0})"
        } else {
            "$tierName  Lv.${tier.level}  |  ${championLabel()}"
        }
    }

    private fun buildRecentBadgeSummary(): String {
        val unlocked = cloudAchievements.filter { it.unlocked }.sortedByDescending { it.unlockedAt.orEmpty() }.take(3)
        if (unlocked.isEmpty()) {
            return localText(
                "最近亮点：继续训练并同步云端，这里会显示最近解锁的徽章与成长进展。",
                "Highlights: keep training and syncing to reveal your latest unlocked badges and progress.",
                "Temps forts : continuez à vous entraîner et à synchroniser pour afficher vos badges récents.",
                "ไฮไลต์ล่าสุด: ฝึกต่อและซิงก์คลาวด์เพื่อแสดงเหรียญล่าสุดและความก้าวหน้า",
            )
        }
        val names = unlocked.joinToString(" · ") { achievementDisplayName(it.key) }
        return localText(
            "最近亮点：$names",
            "Highlights: $names",
            "Temps forts : $names",
            "ไฮไลต์ล่าสุด: $names",
        )
    }

    private fun refreshProfileAvatar() {
        val profile = cloudProfile
        bindAvatarPresentation(
            container = profileAvatarShell,
            imageView = profileAvatarImageView,
            fallbackView = profileAvatarFallbackView,
            seedText = profile?.nickname,
            colorHex = profile?.avatarColor ?: "#CC4400",
            imageUri = currentAvatarImageUri(),
        )
        profileHeroBadgeView.text = tierLabelForLevel(profile?.currentTier ?: cloudTier?.level ?: 1)
        profileHeroBadgeView.background = metallicBackground("#FFE8A8", "#B68026", "#FFF3D2", 999)
        profileHeroBadgeView.setTextColor(Color.parseColor("#140800"))
    }

    private fun currentAvatarImageUri(): Uri? =
        prefs.getString(KEY_PROFILE_AVATAR_URI, null)
            ?.takeIf { it.isNotBlank() }
            ?.let {
                try {
                    Uri.parse(it)
                } catch (_: Throwable) {
                    null
                }
            }

    private fun storeAvatarImageUri(uri: Uri?) {
        prefs.edit().putString(KEY_PROFILE_AVATAR_URI, uri?.toString()).apply()
    }

    private fun bindAvatarPresentation(
        container: FrameLayout,
        imageView: ImageView,
        fallbackView: TextView,
        seedText: String?,
        colorHex: String,
        imageUri: Uri?,
    ) {
        container.background = avatarBackground(sanitizeAvatarColor(colorHex))
        fallbackView.text = avatarInitial(seedText)
        if (imageUri != null && loadAvatarImage(imageView, imageUri)) {
            imageView.visibility = View.VISIBLE
            fallbackView.visibility = View.GONE
        } else {
            imageView.setImageDrawable(null)
            imageView.visibility = View.GONE
            fallbackView.visibility = View.VISIBLE
        }
    }

    private fun loadAvatarImage(
        imageView: ImageView,
        uri: Uri,
    ): Boolean =
        try {
            imageView.setImageURI(null)
            imageView.setImageURI(uri)
            imageView.drawable != null
        } catch (_: Throwable) {
            false
        }

    private fun avatarInitial(seedText: String?): String {
        val normalized = seedText?.trim().orEmpty().ifBlank { "R" }
        return normalized.first().uppercaseChar().toString()
    }

    private fun renderAchievements() {
        val dashboard = buildWorkoutResultsDashboard()
        achievementsTitleView.text = localText("锻炼成果", "Workout Results", "Résultats d'entraînement", "ผลการฝึก")
        achievementsSubtitleView.text =
            localText(
                "按当日、周、月、年和注册至今累计拳击数、训练时长、消耗卡路里与燃脂量。",
                "Cumulative punch count, training time, calories burned, and fat burn by day, week, month, year, and since registration.",
                "Cumul quotidien, hebdomadaire, mensuel, annuel et depuis l'inscription.",
                "ยอดรวมรายวัน รายสัปดาห์ รายเดือน รายปี และตั้งแต่สมัครใช้งาน",
            )
        achievementsSubtitleView.visibility = View.VISIBLE
        achievementsCard.visibility = View.VISIBLE
        historyTitleView.visibility = View.GONE
        historySubtitleView.visibility = View.GONE
        historyCard.visibility = View.GONE
        if (::achievementsSwipe.isInitialized) {
            achievementsSwipe.isEnabled = false
        }
        achievementsSummaryView.text =
            localText(
                "统计周期：当日 / 本周 / 本月 / 本年 / 注册至今（自 ${formatWorkoutResultsDate(dashboard.registeredAtEpochMs)}）",
                "Ranges: today / week / month / year / since ${formatWorkoutResultsDate(dashboard.registeredAtEpochMs)}",
                "Périodes : aujourd'hui / semaine / mois / année / depuis ${formatWorkoutResultsDate(dashboard.registeredAtEpochMs)}",
                "ช่วงเวลา: วันนี้ / สัปดาห์ / เดือน / ปี / ตั้งแต่ ${formatWorkoutResultsDate(dashboard.registeredAtEpochMs)}",
            )
        achievementsGridContainer.removeAllViews()
        shareAchievementsButton.alpha = if (dashboard.hasData()) 1.0f else 0.72f
        shareAchievementsButton.isEnabled = dashboard.hasData()
        if (!dashboard.hasData()) {
            achievementsGridContainer.addView(
                emptyStateCard(
                    badge = localText("成果", "RESULTS", "RÉSULTATS", "ผลลัพธ์"),
                    title = localText("等待首批锻炼成果", "Waiting for your first workout results", "En attente de vos premiers résultats", "กำลังรอผลการฝึกครั้งแรก"),
                    message =
                        localText(
                            "完成任意训练后，这里会按当日、周、月、年和注册至今累计显示拳击数、训练时长、消耗卡路里与燃脂量。",
                            "After you complete any training, this page will accumulate punch count, training time, calories burned, and fat burn by day, week, month, year, and since registration.",
                            "Après chaque séance, cette page cumule coups, durée, calories et graisse brûlée par jour, semaine, mois, année et depuis l'inscription.",
                            "หลังจากฝึกเสร็จ หน้านี้จะสะสมจำนวนหมัด เวลาฝึก แคลอรี และการเผาผลาญไขมัน แยกตามวัน สัปดาห์ เดือน ปี และตั้งแต่สมัคร",
                        ),
                    accentColor = "#FF9A30",
                ),
            )
            return
        }
        achievementsGridContainer.addView(
            workoutResultsPeriodCard(
                title = localText("当日累计", "Today", "Aujourd'hui", "วันนี้"),
                subtitle = localText("今日训练总览", "Today's totals", "Totaux du jour", "สรุปวันนี้"),
                totals = dashboard.today,
                accentColor = "#FF9A30",
                topColor = "#3B220C",
                bottomColor = "#180A03",
            ),
        )
        achievementsGridContainer.addView(spacer(dp(12)))
        achievementsGridContainer.addView(
            workoutResultsPeriodCard(
                title = localText("本周累计", "This Week", "Cette semaine", "สัปดาห์นี้"),
                subtitle = localText("本周训练总览", "Weekly totals", "Totaux hebdomadaires", "สรุปสัปดาห์นี้"),
                totals = dashboard.week,
                accentColor = "#7EDBFF",
                topColor = "#142C38",
                bottomColor = "#091119",
            ),
        )
        achievementsGridContainer.addView(spacer(dp(12)))
        achievementsGridContainer.addView(
            workoutResultsPeriodCard(
                title = localText("本月累计", "This Month", "Ce mois-ci", "เดือนนี้"),
                subtitle = localText("本月训练总览", "Monthly totals", "Totaux mensuels", "สรุปเดือนนี้"),
                totals = dashboard.month,
                accentColor = "#7DFFAF",
                topColor = "#122A20",
                bottomColor = "#08110C",
            ),
        )
        achievementsGridContainer.addView(spacer(dp(12)))
        achievementsGridContainer.addView(
            workoutResultsPeriodCard(
                title = localText("本年累计", "This Year", "Cette année", "ปีนี้"),
                subtitle = localText("本年训练总览", "Yearly totals", "Totaux annuels", "สรุปปีนี้"),
                totals = dashboard.year,
                accentColor = "#FFC85A",
                topColor = "#302611",
                bottomColor = "#120D07",
            ),
        )
        achievementsGridContainer.addView(spacer(dp(12)))
        achievementsGridContainer.addView(
            workoutResultsPeriodCard(
                title = localText("注册至今累计", "Since Registration", "Depuis l'inscription", "ตั้งแต่สมัครใช้งาน"),
                subtitle = localText(
                    "累计起点 ${formatWorkoutResultsDate(dashboard.registeredAtEpochMs)}",
                    "Since ${formatWorkoutResultsDate(dashboard.registeredAtEpochMs)}",
                    "Depuis le ${formatWorkoutResultsDate(dashboard.registeredAtEpochMs)}",
                    "ตั้งแต่ ${formatWorkoutResultsDate(dashboard.registeredAtEpochMs)}",
                ),
                totals = dashboard.sinceRegistered,
                accentColor = "#FF8D8D",
                topColor = "#2A1717",
                bottomColor = "#110708",
            ),
        )
        return
        achievementsTitleView.text = localText("锻炼成果", "Workout Results", "Résultats d'entraînement", "Workout Results")
        achievementsSubtitleView.text =
            localText(
                "按周、月、年和注册至今累计拳击数、训练时长、消耗卡路里与燃脂量。",
                "Cumulative punch count, training time, calories burned, and fat burn by week, month, year, and since registration.",
                "Cumul hebdomadaire, mensuel, annuel et depuis l'inscription.",
                "Weekly, monthly, yearly, and since-registration totals.",
            )
        achievementsSubtitleView.visibility = View.VISIBLE
        achievementsCard.visibility = View.VISIBLE
        historyTitleView.visibility = View.GONE
        historySubtitleView.visibility = View.GONE
        historyCard.visibility = View.GONE
        if (::achievementsSwipe.isInitialized) {
            achievementsSwipe.isEnabled = false
        }
        achievementsSummaryView.text =
            localText(
                "统计周期：本周 / 本月 / 本年 / 注册至今（自 ${formatWorkoutResultsDate(dashboard.registeredAtEpochMs)}）",
                "Ranges: week / month / year / since ${formatWorkoutResultsDate(dashboard.registeredAtEpochMs)}",
                "Périodes : semaine / mois / année / depuis ${formatWorkoutResultsDate(dashboard.registeredAtEpochMs)}",
                "Ranges: week / month / year / since ${formatWorkoutResultsDate(dashboard.registeredAtEpochMs)}",
            )
        achievementsGridContainer.removeAllViews()
        shareAchievementsButton.alpha = if (dashboard.hasData()) 1.0f else 0.72f
        shareAchievementsButton.isEnabled = dashboard.hasData()
        if (!dashboard.hasData()) {
            achievementsGridContainer.addView(
                emptyStateCard(
                    badge = localText("成果", "RESULTS", "RÉSULTATS", "RESULTS"),
                    title = localText("等待首批锻炼成果", "Waiting for your first workout results"),
                    message =
                        localText(
                            "完成任意训练后，这里会按周、月、年和注册至今累计显示拳击数、训练时长、消耗卡路里与燃脂量。",
                            "After you complete any training, this page will accumulate punch count, training time, calories burned, and fat burn by week, month, year, and since registration.",
                        ),
                    accentColor = "#FF9A30",
                ),
            )
            return
        }
        achievementsGridContainer.addView(
            workoutResultsPeriodCard(
                title = localText("本周累计", "This Week"),
                subtitle = localText("本周训练总览", "Weekly totals"),
                totals = dashboard.week,
                accentColor = "#7EDBFF",
                topColor = "#142C38",
                bottomColor = "#091119",
            ),
        )
        achievementsGridContainer.addView(spacer(dp(12)))
        achievementsGridContainer.addView(
            workoutResultsPeriodCard(
                title = localText("本月累计", "This Month"),
                subtitle = localText("本月训练总览", "Monthly totals"),
                totals = dashboard.month,
                accentColor = "#7DFFAF",
                topColor = "#122A20",
                bottomColor = "#08110C",
            ),
        )
        achievementsGridContainer.addView(spacer(dp(12)))
        achievementsGridContainer.addView(
            workoutResultsPeriodCard(
                title = localText("本年累计", "This Year"),
                subtitle = localText("本年训练总览", "Yearly totals"),
                totals = dashboard.year,
                accentColor = "#FFC85A",
                topColor = "#302611",
                bottomColor = "#120D07",
            ),
        )
        achievementsGridContainer.addView(spacer(dp(12)))
        achievementsGridContainer.addView(
            workoutResultsPeriodCard(
                title = localText("注册至今累计", "Since Registration"),
                subtitle = localText(
                    "累计起点 ${formatWorkoutResultsDate(dashboard.registeredAtEpochMs)}",
                    "Since ${formatWorkoutResultsDate(dashboard.registeredAtEpochMs)}",
                ),
                totals = dashboard.sinceRegistered,
                accentColor = "#FF8D8D",
                topColor = "#2A1717",
                bottomColor = "#110708",
            ),
        )
        return
        achievementsGridContainer.removeAllViews()
        val items = cloudAchievements.sortedBy { it.sortOrder }
        if (items.isEmpty()) {
            achievementsSummaryView.text = achievementsSubtitleText(0, 0)
            achievementsGridContainer.addView(
                emptyStateCard(
                    badge = localText("徽章", "HONOR", "HONNEUR", "เกียรติยศ"),
                    title =
                        localText(
                            "荣誉馆等待点亮",
                            "Your honor hall is waiting",
                            "Votre galerie d'honneur attend",
                            "หอเกียรติยศกำลังรอคุณ",
                        ),
                    message =
                        localText(
                            "完成训练并同步云端后，这里会展示你的段位与徽章成长。",
                            "Finish and sync a session to light up your tier and badge collection here.",
                            "Terminez et synchronisez une séance pour afficher votre rang et vos badges.",
                            "ฝึกให้เสร็จและซิงก์ข้อมูล เพื่อแสดงระดับและเหรียญของคุณที่นี่",
                        ),
                    accentColor = "#FFD060",
                ),
            )
            shareAchievementsButton.alpha = 0.72f
            shareAchievementsButton.isEnabled = false
            return
        }
        shareAchievementsButton.alpha = 1.0f
        shareAchievementsButton.isEnabled = true
        val unlockedCount = items.count { it.unlocked }
        achievementsSummaryView.text = achievementsSubtitleText(unlockedCount, items.size)
        cloudTier?.let { tier ->
            achievementsGridContainer.addView(achievementTierHeroCardPremium(tier, unlockedCount, items.size))
            achievementsGridContainer.addView(spacer(dp(14)))
        }

        val recentUnlocked = items.filter { it.unlocked }.sortedByDescending { it.unlockedAt.orEmpty() }.take(3)
        if (recentUnlocked.isNotEmpty()) {
            achievementsGridContainer.addView(sectionLabel(achievementsRecentUnlockedTitle()))
            achievementsGridContainer.addView(spacer(dp(8)))
            val recentRow =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                }
            recentUnlocked.forEachIndexed { index, item ->
                recentRow.addView(
                    badgeText(
                        text = achievementDisplayName(item.key),
                        textColor = "#FFF5E6",
                        fillColor = "#16384A",
                    ).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                if (index > 0) {
                                    leftMargin = dp(8)
                                }
                            }
                    },
                )
            }
            achievementsGridContainer.addView(recentRow)
            achievementsGridContainer.addView(spacer(dp(14)))
        }

        val itemMap = items.associateBy { it.key }
        achievementGroupSpecs().forEachIndexed { groupIndex, group ->
            val groupItems = group.second.mapNotNull(itemMap::get)
            val unlockedInGroup = groupItems.count { it.unlocked }
            val groupCard = detailCard(fillColor = "#0D1822", strokeColor = "#264558", cornerDp = 22)
            val groupHeader =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
            val headerTitle =
                sectionLabel(group.first).apply {
                    setPadding(0, 0, 0, 0)
                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1.0f,
                        )
                }
            val headerBadge =
                badgeText(
                    text = "$unlockedInGroup/${groupItems.size}",
                    textColor = "#FFF8E8",
                    fillColor = "#173649",
                ).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                }
            groupHeader.addView(headerTitle)
            groupHeader.addView(headerBadge)
            groupCard.addView(groupHeader)
            groupCard.addView(spacer(dp(10)))
            groupItems.chunked(2).forEachIndexed { rowIndex, rowItems ->
                val row =
                    LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.START
                    }
                    rowItems.forEachIndexed { index, item ->
                        row.addView(
                            achievementBadgeCardPremium(item).apply {
                                layoutParams =
                                    LinearLayout.LayoutParams(
                                        0,
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        1.0f,
                                ).apply {
                                    if (index > 0) {
                                        leftMargin = dp(10)
                                    }
                                }
                        },
                    )
                }
                repeat(2 - rowItems.size) {
                    row.addView(horizontalSpace(0).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1.0f) })
                }
                groupCard.addView(row)
                if (rowIndex < (groupItems.size - 1) / 2) {
                    groupCard.addView(spacer(dp(10)))
                }
            }
            achievementsGridContainer.addView(groupCard)
            if (groupIndex < achievementGroupSpecs().lastIndex) {
                achievementsGridContainer.addView(spacer(dp(14)))
            }
        }
    }

    private fun workoutResultsPeriodCard(
        title: String,
        subtitle: String,
        totals: WorkoutMetricTotals,
        accentColor: String,
        topColor: String,
        bottomColor: String,
    ): LinearLayout =
        detailCard(fillColor = bottomColor, strokeColor = accentColor, cornerDp = 22).apply {
            background = metallicBackground(topColor, bottomColor, accentColor, 22)
            val headerRow =
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
            val titleView =
                titleText(title, 18f).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f,
                        )
                    setTextColor(Color.parseColor("#FFF8E8"))
                    setPadding(0, 0, 0, 0)
                }
            val subtitleBadge = badgeText(subtitle, fillColor = accentColor, textColor = "#140800")
            headerRow.addView(titleView)
            headerRow.addView(subtitleBadge)
            addView(headerRow)
            addView(
                bodyText(
                    localText(
                        "累计拳击数、训练时长、消耗卡路里与燃脂量",
                        "Punch count, training time, calories burned, and fat burn",
                        "Coups, durée d'entraînement, calories brûlées et graisse brûlée",
                        "จำนวนหมัด เวลาฝึก แคลอรี และการเผาผลาญไขมัน",
                    ),
                ).apply {
                    setTextColor(Color.parseColor("#B7CFE0"))
                    setPadding(0, dp(6), 0, 0)
                },
            )

            val firstRow =
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(12), 0, 0)
                }
            firstRow.addView(
                workoutResultsMetricCard(
                    label = localText("拳击数", "Punch Count", "Coups", "จำนวนหมัด"),
                    primary = totals.hits.toString(),
                    unit = localText("次", "hits", "coups", "ครั้ง"),
                    accentColor = "#FFB347",
                    topColor = "#3C2508",
                    bottomColor = "#160900",
                ).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f,
                        ).apply {
                            rightMargin = dp(10)
                        }
                },
            )
            firstRow.addView(
                workoutResultsMetricCard(
                    label = localText("训练时长", "Training Time", "Temps d'entraînement", "เวลาฝึก"),
                    primary = formatWorkoutDurationMinutes(totals.durationSeconds),
                    unit = localText("分钟", "min", "min", "นาที"),
                    accentColor = "#6CCBFF",
                    topColor = "#102133",
                    bottomColor = "#07101A",
                ).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f,
                        )
                },
            )

            val secondRow =
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(10), 0, 0)
                }
            secondRow.addView(
                workoutResultsMetricCard(
                    label = localText("消耗卡路里", "Calories Burned", "Calories brûlées", "แคลอรีที่เผาผลาญ"),
                    primary = String.format(Locale.US, "%.1f", totals.calories),
                    unit = "kcal",
                    accentColor = "#FFC85A",
                    topColor = "#2A2110",
                    bottomColor = "#120D07",
                ).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f,
                        ).apply {
                            rightMargin = dp(10)
                        }
                },
            )
            secondRow.addView(
                workoutResultsMetricCard(
                    label = localText("燃脂量", "Fat Burn", "Graisse brûlée", "การเผาผลาญไขมัน"),
                    primary = String.format(Locale.US, "%.1f", totals.fatBurnGrams),
                    unit = "g",
                    accentColor = "#FF6E6E",
                    topColor = "#2A1212",
                    bottomColor = "#120708",
                ).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f,
                        )
                },
            )
            addView(firstRow)
            addView(secondRow)
        }

    private fun workoutResultsMetricCard(
        label: String,
        primary: String,
        unit: String,
        accentColor: String,
        topColor: String,
        bottomColor: String,
    ): LinearLayout =
        reportMetricCard(
            label = label,
            value = "$primary $unit",
            accentColor = accentColor,
            alignLeft = true,
        ).apply {
            applyAggregateCardTone(
                topColor = topColor,
                bottomColor = bottomColor,
                accentColor = accentColor,
            )
        }

    private fun formatWorkoutDurationMinutes(durationSeconds: Int): String =
        String.format(Locale.US, "%.1f", durationSeconds.coerceAtLeast(0) / 60f)

    private fun formatWorkoutResultsDate(timestampMs: Long): String {
        val pattern =
            when (selectedLanguage) {
                AppLanguage.Chinese -> "yyyy-MM-dd"
                AppLanguage.French -> "dd/MM/yyyy"
                AppLanguage.Thai -> "dd/MM/yyyy"
                else -> "yyyy-MM-dd"
            }
        return SimpleDateFormat(pattern, localeForLanguage()).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(timestampMs))
    }

    private fun achievementGroupSpecs(): List<Pair<String, List<String>>> =
        listOf(
            achievementGroupTitle("milestone") to listOf("first_training", "sessions_5", "sessions_15", "sessions_30"),
            achievementGroupTitle("total_hits") to listOf("hits_100", "hits_500", "hits_1000", "hits_5000"),
            achievementGroupTitle("best30") to listOf("best_30_40", "best_30_60", "best_30_80", "best_30_100"),
            achievementGroupTitle("best60") to listOf("best_60_90", "best_60_120", "best_60_150", "best_60_180"),
            achievementGroupTitle("burst") to listOf("burst_6", "burst_10", "burst_12", "burst_15"),
            achievementGroupTitle("streak") to listOf("streak_3", "streak_7", "streak_14", "streak_30"),
        )

    private fun achievementGroupTitle(key: String): String =
        when (selectedLanguage) {
            AppLanguage.Chinese ->
                when (key) {
                    "milestone" -> "训练里程碑"
                    "total_hits" -> "累计击打"
                    "best30" -> "30 秒成绩徽章"
                    "best60" -> "60 秒成绩徽章"
                    "burst" -> "爆发能力"
                    else -> "坚持打卡"
                }
            AppLanguage.French ->
                when (key) {
                    "milestone" -> "Étapes d'entraînement"
                    "total_hits" -> "Coups cumulés"
                    "best30" -> "Badges 30 s"
                    "best60" -> "Badges 60 s"
                    "burst" -> "Puissance explosive"
                    else -> "Série d'entraînement"
                }
            AppLanguage.Thai ->
                when (key) {
                    "milestone" -> "เป้าหมายการฝึก"
                    "total_hits" -> "หมัดสะสม"
                    "best30" -> "เหรียญ 30 วินาที"
                    "best60" -> "เหรียญ 60 วินาที"
                    "burst" -> "พลังระเบิด"
                    else -> "ฝึกต่อเนื่อง"
                }
            else ->
                when (key) {
                    "milestone" -> "Training Milestones"
                    "total_hits" -> "Total Hits"
                    "best30" -> "30s Badges"
                    "best60" -> "60s Badges"
                    "burst" -> "Burst Power"
                    else -> "Streak Badges"
                }
        }

    private fun achievementsRecentUnlockedTitle(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "最近解锁"
            AppLanguage.French -> "Déblocages récents"
            AppLanguage.Thai -> "ปลดล็อกล่าสุด"
            else -> "Recently unlocked"
        }

    private fun achievementTierHeroCard(
        tier: CloudTierProgress,
        unlockedCount: Int,
        totalCount: Int,
    ): LinearLayout =
        detailCard(fillColor = "#102533", strokeColor = "#2B5870", cornerDp = 22).apply {
            addView(
                badgeText(
                    text = localText("当前段位", "Current Tier", "Rang actuel", "ระดับปัจจุบัน"),
                    textColor = "#140800",
                    fillColor = "#FFD060",
                ),
            )
            addView(
                titleText(tierLabelForKey(tier.key), 22f).apply {
                    setPadding(0, dp(12), 0, 0)
                    setTextColor(Color.parseColor("#FFF8E8"))
                },
            )
            addView(
                bodyText(achievementsSubtitleText(unlockedCount, totalCount)).apply {
                    setTextColor(Color.parseColor("#F2D8A7"))
                    setPadding(0, dp(6), 0, 0)
                },
            )
            addView(
                bodyText(tierHeroProgressText(tier)).apply {
                    setTextColor(Color.parseColor("#B88A54"))
                    setPadding(0, dp(10), 0, 0)
                },
            )
        }

    private fun tierHeroProgressText(tier: CloudTierProgress): String {
        val best30 = cloudStatistics?.best30Hits ?: tier.bestHits
        val totalHits = cloudStatistics?.totalHits ?: tier.totalHits
        return if (tier.nextHits != null && tier.nextKey != null) {
            val remainingBest30 = (tier.nextHits - best30).coerceAtLeast(0)
            val remainingTotal = ((tier.nextTotalHits ?: 0) - totalHits).coerceAtLeast(0)
            localText(
                "综合升段：30秒最佳 $best30 击 · 累计拳击 $totalHits 击 | 距离 ${tierLabelForKey(tier.nextKey)} 还差 30秒 $remainingBest30 击、累计 $remainingTotal 击",
                "Tier progress: Best 30s $best30 · Total hits $totalHits | To ${tierLabelForKey(tier.nextKey)} need $remainingBest30 best-30 hits and $remainingTotal total hits",
                "Progression : 30 s $best30 · Total $totalHits | Pour ${tierLabelForKey(tier.nextKey)} il faut encore $remainingBest30 coups en 30 s et $remainingTotal coups cumulés",
                "ความก้าวหน้า: 30 วินาที $best30 · สะสม $totalHits | ไปถึง ${tierLabelForKey(tier.nextKey)} ต้องเพิ่มอีก $remainingBest30 ครั้งใน 30 วินาที และ $remainingTotal ครั้งสะสม",
            )
        } else {
            localText(
                "综合升段：30秒最佳 $best30 击 · 累计拳击 $totalHits 击 | 已达到最高段位",
                "Tier progress: Best 30s $best30 · Total hits $totalHits | Top tier reached",
                "Progression : 30 s $best30 · Total $totalHits | Rang maximum atteint",
                "ความก้าวหน้า: 30 วินาที $best30 · สะสม $totalHits | ถึงระดับสูงสุดแล้ว",
            )
        }
    }

    private fun renderHistoryCards() {
        val items = cloudHistory.take(6)
        historyView.visibility = View.GONE
        if (items.isEmpty()) {
            historyListRecycler.visibility = View.GONE
            historyEmptyView.visibility = View.VISIBLE
            historyItemAdapter.submitList(emptyList())
            return
        }
        historyEmptyView.visibility = View.GONE
        historyListRecycler.visibility = View.VISIBLE
        historyItemAdapter.submitList(items)
    }

    private fun renderLeaderboard() {
        val result = leaderboardResult
        val entries = result?.takeIf { it.success }?.top.orEmpty()
        val boardAccent = leaderboardAccentColor(leaderboardBoard)
        leaderboardTitleView.text = tr("leaderboard_title")
        leaderboardSubtitleView.text = leaderboardBoardSubtitle(leaderboardBoard)
        leaderboardSubtitleView.visibility = View.VISIBLE
        leaderboardModeGroup.visibility = View.VISIBLE
        refreshLeaderboardButton.visibility = View.VISIBLE
        leaderboardCard.visibility = View.VISIBLE
        leaderboardSwipe.isEnabled = true
        leaderboardCard.background = metallicBackground("#132534", leaderboardAccentFill(leaderboardBoard), boardAccent, 24)

        leaderboard30Button.text = leaderboardBoardLabel(LeaderboardBoard.DailyBestHits)
        leaderboard60Button.text = leaderboardBoardLabel(LeaderboardBoard.TotalHits)
        leaderboardTotalHitsButton.text = leaderboardBoardLabel(LeaderboardBoard.TotalDuration)
        leaderboardStreakButton.text = leaderboardBoardLabel(LeaderboardBoard.TotalCalories)
        leaderboardFatBurnButton.text = leaderboardBoardLabel(LeaderboardBoard.TotalFatBurn)
        when (leaderboardBoard) {
            LeaderboardBoard.DailyBestHits -> leaderboard30Button.isChecked = true
            LeaderboardBoard.TotalHits -> leaderboard60Button.isChecked = true
            LeaderboardBoard.TotalDuration -> leaderboardTotalHitsButton.isChecked = true
            LeaderboardBoard.TotalCalories -> leaderboardStreakButton.isChecked = true
            LeaderboardBoard.TotalFatBurn -> leaderboardFatBurnButton.isChecked = true
        }

        leaderboardPodiumContainer.removeAllViews()
        leaderboardView.visibility = View.GONE
        shareLeaderboardButton.visibility =
            if (result?.success == true && (result.me != null || entries.isNotEmpty())) View.VISIBLE else View.GONE
        shareLeaderboardButton.isEnabled = shareLeaderboardButton.visibility == View.VISIBLE

        if (entries.isEmpty()) {
            leaderboardPodiumContainer.visibility = View.GONE
            leaderboardListRecycler.visibility = View.GONE
            leaderboardRowAdapter.submitList(emptyList())
            leaderboardMeCard.visibility = View.VISIBLE
            leaderboardMeCard.background = metallicBackground("#163246", "#0A141C", boardAccent, 22)
            leaderboardMeCard.removeAllViews()
            leaderboardMeCard.addView(
                emptyStateCard(
                    badge = localText("榜单", "RANK", "RANG", "อันดับ"),
                    title =
                        localText(
                            "等待更多用户数据",
                            "Waiting for more user results",
                            "En attente de plus de résultats utilisateurs",
                            "กำลังรอข้อมูลจากผู้ใช้เพิ่มเติม",
                        ),
                    message =
                        result?.message?.takeIf { it.isNotBlank() }
                            ?: localText(
                                "用户完成训练并同步后，这里会按不同序列号用户生成训练排名。",
                                "Once users sync workouts, this board will rank different serial-number users.",
                                "Après synchronisation, ce classement comparera les utilisateurs par numéro de série.",
                                "เมื่อผู้ใช้ซิงก์การฝึกแล้ว หน้านี้จะจัดอันดับผู้ใช้ตามหมายเลขซีเรียล",
                            ),
                    accentColor = boardAccent,
                ),
            )
            return
        }

        val topThree = entries.take(3)
        leaderboardPodiumContainer.visibility = if (topThree.isNotEmpty()) View.VISIBLE else View.GONE
        topThree.forEachIndexed { index, entry ->
            leaderboardPodiumContainer.addView(
                podiumCardPremium(
                    entry = entry,
                    accentColor = podiumAccentForRank(entry.rank),
                    elevated = entry.rank == 1,
                    leftMargin = if (index == 0) 0 else dp(10),
                ),
            )
        }

        val others = entries.drop(3)
        leaderboardListRecycler.visibility = if (others.isNotEmpty()) View.VISIBLE else View.GONE
        leaderboardRowAdapter.submitList(others)

        leaderboardMeCard.visibility = View.VISIBLE
        leaderboardMeCard.background = metallicBackground("#21485F", leaderboardAccentFill(leaderboardBoard), boardAccent, 22)
        leaderboardMeCard.removeAllViews()
        leaderboardMeTitleView.text = localText("我的排名", "MY RANK", "MON RANG", "อันดับของฉัน")
        leaderboardMeTitleView.setTextColor(Color.parseColor(boardAccent))
        leaderboardMeTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        leaderboardMeTitleView.setTypeface(Typeface.DEFAULT_BOLD)
        leaderboardMeView.text =
            result?.me?.let { entry ->
                localText(
                    "当前排名 ${rankLabel(entry.rank)} · ${leaderboardDisplayName(entry)}\n${leaderboardSerialBadgeText(entry)} · ${leaderboardPrimaryValueText(entry)}",
                    "Current rank ${rankLabel(entry.rank)} · ${leaderboardDisplayName(entry)}\n${leaderboardSerialBadgeText(entry)} · ${leaderboardPrimaryValueText(entry)}",
                    "Rang actuel ${rankLabel(entry.rank)} · ${leaderboardDisplayName(entry)}\n${leaderboardSerialBadgeText(entry)} · ${leaderboardPrimaryValueText(entry)}",
                    "อันดับปัจจุบัน ${rankLabel(entry.rank)} · ${leaderboardDisplayName(entry)}\n${leaderboardSerialBadgeText(entry)} · ${leaderboardPrimaryValueText(entry)}",
                )
            } ?: localText(
                "当前序列号暂未上榜，继续训练并同步后再来看看。",
                "This serial is not on the board yet. Keep training and sync again.",
                "Ce numéro de série n'est pas encore classé. Continuez puis synchronisez.",
                "ซีเรียลนี้ยังไม่ติดอันดับ ฝึกต่อและซิงก์อีกครั้ง",
            )
        leaderboardMeView.gravity = Gravity.START
        leaderboardMeView.setTextColor(Color.parseColor("#FFF5E6"))
        leaderboardMeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        detachFromParent(leaderboardMeTitleView)
        detachFromParent(leaderboardMeView)
        leaderboardMeCard.addView(leaderboardMeTitleView)
        leaderboardMeCard.addView(
            leaderboardMeView.apply {
                setPadding(0, dp(8), 0, 0)
            },
        )
    }

    private fun buildLocalLeaderboardEntries(board: LeaderboardBoard): List<LocalLeaderboardEntry> =
        when (board) {
            LeaderboardBoard.DailyBestHits -> buildDailyBestHitEntries()
            LeaderboardBoard.TotalHits -> buildModuleAggregateEntries(board)
            LeaderboardBoard.TotalDuration -> buildModuleAggregateEntries(board)
            LeaderboardBoard.TotalCalories -> buildModuleAggregateEntries(board)
            LeaderboardBoard.TotalFatBurn -> buildModuleAggregateEntries(board)
        }

    private fun buildDailyBestHitEntries(): List<LocalLeaderboardEntry> {
        val dayTotals = buildDailyWorkoutTotalsMap(aggregateRegisteredStartMs(), System.currentTimeMillis() + 1L)
        return dayTotals.entries
            .filter { it.value.hits > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, WorkoutMetricTotals>> { it.value.hits }.thenByDescending { it.key })
            .take(10)
            .mapIndexed { index, item ->
                LocalLeaderboardEntry(
                    rank = index + 1,
                    title = formatLeaderboardDayLabel(item.key),
                    badge = localText("单日历史", "DAILY BEST", "MEILLEUR JOUR", "สถิติรายวัน"),
                    tertiaryBadge =
                        "${formatWorkoutDurationMinutes(item.value.durationSeconds)} ${
                            localText("分钟", "min", "min", "นาที")
                        }",
                    primaryValue =
                        localText(
                            "${item.value.hits} 拳",
                            "${item.value.hits} hits",
                            "${item.value.hits} coups",
                            "${item.value.hits} หมัด",
                        ),
                    secondaryValue =
                        localText(
                            "消耗卡路里 ${String.format(Locale.US, "%.1f", item.value.calories)} kcal · 燃脂量 ${String.format(Locale.US, "%.1f", item.value.fatBurnGrams)} g",
                            "Calories ${String.format(Locale.US, "%.1f", item.value.calories)} kcal · Fat ${String.format(Locale.US, "%.1f", item.value.fatBurnGrams)} g",
                            "Calories ${String.format(Locale.US, "%.1f", item.value.calories)} kcal · Graisse ${String.format(Locale.US, "%.1f", item.value.fatBurnGrams)} g",
                            "แคลอรี ${String.format(Locale.US, "%.1f", item.value.calories)} kcal · ไขมัน ${String.format(Locale.US, "%.1f", item.value.fatBurnGrams)} g",
                        ),
                )
            }
    }

    private fun buildModuleAggregateEntries(board: LeaderboardBoard): List<LocalLeaderboardEntry> =
        buildModuleAggregateSnapshots()
            .sortedByBoard(board)
            .mapIndexed { index, item ->
                LocalLeaderboardEntry(
                    rank = index + 1,
                    title = item.title,
                    badge = item.category,
                    tertiaryBadge = localText("历史累计", "LIFETIME", "CUMUL", "สะสมทั้งหมด"),
                    primaryValue = leaderboardModulePrimaryText(board, item.totals),
                    secondaryValue = leaderboardModuleSecondaryText(board, item.totals),
                )
            }

    private fun buildModuleAggregateSnapshots(): List<ModuleAggregateSnapshot> {
        val startMs = aggregateRegisteredStartMs()
        val endMsExclusive = System.currentTimeMillis() + 1L
        val emotionLabel = localText("情绪减压", "EMOTION", "ÉMOTION", "คลายอารมณ์")
        val fitnessLabel = localText("健身燃脂", "FAT-BURN", "BRÛLE-GRAISSE", "เผาผลาญ")
        return listOf(
            ModuleAggregateSnapshot(localText("色彩涂鸦", "Color Graffiti", "Graffiti couleur", "สีสันกราฟฟิตี"), emotionLabel, loadEmotionWindowTotals("color_graffiti_daily_emotion", startMs, endMsExclusive)),
            ModuleAggregateSnapshot(localText("情绪拳王", "Emotion Champ", "Champion émotion", "ราชันอารมณ์"), emotionLabel, loadEmotionWindowTotals("emotion_champ_daily_emotion", startMs, endMsExclusive)),
            ModuleAggregateSnapshot(localText("自由拳击", "Free Boxing", "Boxe libre", "ชกอิสระ"), emotionLabel, loadEmotionWindowTotals("free_boxing_daily_emotion", startMs, endMsExclusive)),
            ModuleAggregateSnapshot(localText("极速燃脂", "Rapid Fat Burn", "Brûle-graisse express", "เผาผลาญเร่งด่วน"), fitnessLabel, loadBlitzWindowTotals(startMs, endMsExclusive)),
            ModuleAggregateSnapshot(localText("燃脂挑战", "Fat-Burn Challenge", "Défi brûle-graisse", "ท้าทายเผาผลาญ"), fitnessLabel, loadFatBurnWindowTotals("fat_burn_challenge", startMs, endMsExclusive)),
            ModuleAggregateSnapshot(localText("燃脂陪练", "Fat-Burn Coach", "Coach brûle-graisse", "โค้ชเผาผลาญ"), fitnessLabel, loadFatBurnWindowTotals("fat_burn_coach", startMs, endMsExclusive)),
        )
    }

    private fun List<ModuleAggregateSnapshot>.sortedByBoard(board: LeaderboardBoard): List<ModuleAggregateSnapshot> =
        when (board) {
            LeaderboardBoard.TotalHits ->
                sortedWith(compareByDescending<ModuleAggregateSnapshot> { it.totals.hits }.thenByDescending { it.totals.durationSeconds })
            LeaderboardBoard.TotalDuration ->
                sortedWith(compareByDescending<ModuleAggregateSnapshot> { it.totals.durationSeconds }.thenByDescending { it.totals.hits })
            LeaderboardBoard.TotalCalories ->
                sortedWith(compareByDescending<ModuleAggregateSnapshot> { it.totals.calories }.thenByDescending { it.totals.hits })
            LeaderboardBoard.TotalFatBurn ->
                sortedWith(compareByDescending<ModuleAggregateSnapshot> { it.totals.fatBurnGrams }.thenByDescending { it.totals.hits })
            LeaderboardBoard.DailyBestHits -> this
        }

    private fun leaderboardModulePrimaryText(
        board: LeaderboardBoard,
        totals: WorkoutMetricTotals,
    ): String =
        when (board) {
            LeaderboardBoard.TotalHits ->
                localText("${totals.hits} 拳", "${totals.hits} hits", "${totals.hits} coups", "${totals.hits} หมัด")
            LeaderboardBoard.TotalDuration ->
                localText(
                    "${formatWorkoutDurationMinutes(totals.durationSeconds)} 分钟",
                    "${formatWorkoutDurationMinutes(totals.durationSeconds)} min",
                    "${formatWorkoutDurationMinutes(totals.durationSeconds)} min",
                    "${formatWorkoutDurationMinutes(totals.durationSeconds)} นาที",
                )
            LeaderboardBoard.TotalCalories ->
                "${String.format(Locale.US, "%.1f", totals.calories)} kcal"
            LeaderboardBoard.TotalFatBurn ->
                "${String.format(Locale.US, "%.1f", totals.fatBurnGrams)} g"
            LeaderboardBoard.DailyBestHits -> ""
        }

    private fun leaderboardModuleSecondaryText(
        board: LeaderboardBoard,
        totals: WorkoutMetricTotals,
    ): String =
        when (board) {
            LeaderboardBoard.TotalHits ->
                localText(
                    "训练时长 ${formatWorkoutDurationMinutes(totals.durationSeconds)} 分钟 · 消耗卡路里 ${String.format(Locale.US, "%.1f", totals.calories)} kcal",
                    "Duration ${formatWorkoutDurationMinutes(totals.durationSeconds)} min · Calories ${String.format(Locale.US, "%.1f", totals.calories)} kcal",
                    "Durée ${formatWorkoutDurationMinutes(totals.durationSeconds)} min · Calories ${String.format(Locale.US, "%.1f", totals.calories)} kcal",
                    "เวลา ${formatWorkoutDurationMinutes(totals.durationSeconds)} นาที · แคลอรี ${String.format(Locale.US, "%.1f", totals.calories)} kcal",
                )
            LeaderboardBoard.TotalDuration ->
                localText(
                    "拳击数 ${totals.hits} · 燃脂量 ${String.format(Locale.US, "%.1f", totals.fatBurnGrams)} g",
                    "Hits ${totals.hits} · Fat ${String.format(Locale.US, "%.1f", totals.fatBurnGrams)} g",
                    "Coups ${totals.hits} · Graisse ${String.format(Locale.US, "%.1f", totals.fatBurnGrams)} g",
                    "หมัด ${totals.hits} · ไขมัน ${String.format(Locale.US, "%.1f", totals.fatBurnGrams)} g",
                )
            LeaderboardBoard.TotalCalories ->
                localText(
                    "拳击数 ${totals.hits} · 训练时长 ${formatWorkoutDurationMinutes(totals.durationSeconds)} 分钟",
                    "Hits ${totals.hits} · Duration ${formatWorkoutDurationMinutes(totals.durationSeconds)} min",
                    "Coups ${totals.hits} · Durée ${formatWorkoutDurationMinutes(totals.durationSeconds)} min",
                    "หมัด ${totals.hits} · เวลา ${formatWorkoutDurationMinutes(totals.durationSeconds)} นาที",
                )
            LeaderboardBoard.TotalFatBurn ->
                localText(
                    "消耗卡路里 ${String.format(Locale.US, "%.1f", totals.calories)} kcal · 拳击数 ${totals.hits}",
                    "Calories ${String.format(Locale.US, "%.1f", totals.calories)} kcal · Hits ${totals.hits}",
                    "Calories ${String.format(Locale.US, "%.1f", totals.calories)} kcal · Coups ${totals.hits}",
                    "แคลอรี ${String.format(Locale.US, "%.1f", totals.calories)} kcal · หมัด ${totals.hits}",
                )
            LeaderboardBoard.DailyBestHits -> ""
        }

    private fun buildDailyWorkoutTotalsMap(
        startMs: Long,
        endMsExclusive: Long,
    ): Map<String, WorkoutMetricTotals> {
        val map = linkedMapOf<String, WorkoutMetricTotals>()
        accumulateEmotionDailyTotals("color_graffiti_daily_emotion", startMs, endMsExclusive, map)
        accumulateEmotionDailyTotals("emotion_champ_daily_emotion", startMs, endMsExclusive, map)
        accumulateEmotionDailyTotals("free_boxing_daily_emotion", startMs, endMsExclusive, map)
        accumulateBlitzDailyTotals(startMs, endMsExclusive, map)
        accumulateFatBurnDailyTotals("fat_burn_challenge", startMs, endMsExclusive, map)
        accumulateFatBurnDailyTotals("fat_burn_coach", startMs, endMsExclusive, map)
        return map
    }

    private fun accumulateEmotionDailyTotals(
        prefsName: String,
        startMs: Long,
        endMsExclusive: Long,
        destination: MutableMap<String, WorkoutMetricTotals>,
    ) {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        var todayAlreadyCaptured = false
        val rawHistory = prefs.getString("history_json", null).orEmpty()
        if (rawHistory.isNotBlank()) {
            runCatching {
                val array = JSONArray(rawHistory)
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val timestampMs = item.optLong("timestampMs", 0L)
                    if (timestampMs < startMs || timestampMs >= endMsExclusive) {
                        continue
                    }
                    val hits = item.optInt("hits", 0).coerceAtLeast(0)
                    val calories = estimateEmotionCalories(hits)
                    val dayKey = aggregateTodayKey(timestampMs)
                    destination.accumulateDayTotals(
                        dayKey,
                        WorkoutMetricTotals(
                            hits = hits,
                            durationSeconds = item.optInt("durationSec", 0).coerceAtLeast(0),
                            calories = calories,
                            fatBurnGrams = estimateEmotionFatBurn(calories),
                        ),
                    )
                    if (dayKey == aggregateTodayKey()) {
                        todayAlreadyCaptured = true
                    }
                }
            }
        }
        val now = System.currentTimeMillis()
        if (now in startMs until endMsExclusive && !todayAlreadyCaptured && prefs.getString("day_key", null) == aggregateTodayKey()) {
            val stressReduction = (100f - prefs.getFloat("stress_value", 100f)).coerceAtLeast(0f)
            val calmIncrease = (prefs.getFloat("calm_value", 50f) - 50f).coerceAtLeast(0f)
            val hits =
                max(
                    ((stressReduction / 0.2f) + 0.5f).toInt(),
                    ((calmIncrease / 0.2f) + 0.5f).toInt(),
                ).coerceAtLeast(0)
            val calories = estimateEmotionCalories(hits)
            destination.accumulateDayTotals(
                aggregateTodayKey(),
                WorkoutMetricTotals(
                    hits = hits,
                    durationSeconds = prefs.getInt("duration_seconds", 0).coerceAtLeast(0),
                    calories = calories,
                    fatBurnGrams = estimateEmotionFatBurn(calories),
                ),
            )
        }
    }

    private fun accumulateBlitzDailyTotals(
        startMs: Long,
        endMsExclusive: Long,
        destination: MutableMap<String, WorkoutMetricTotals>,
    ) {
        val prefs = getSharedPreferences("blitz_mode_aggregate", MODE_PRIVATE)
        val prefixes =
            prefs.all.keys
                .filter { it.startsWith("day_") && it.endsWith("_hits") && it.length == 17 }
                .map { it.removeSuffix("_hits") }
                .toSet()
        prefixes.forEach { prefix ->
            val dayStart = parseBlitzAggregateDayStart(prefix.removePrefix("day_")) ?: return@forEach
            if (dayStart < startMs || dayStart >= endMsExclusive) {
                return@forEach
            }
            destination.accumulateDayTotals(
                aggregateTodayKey(dayStart),
                WorkoutMetricTotals(
                    hits = prefs.getInt("${prefix}_hits", 0).coerceAtLeast(0),
                    durationSeconds = prefs.getInt("${prefix}_duration_sec", 0).coerceAtLeast(0),
                    calories = prefs.getFloat("${prefix}_calories", 0f).coerceAtLeast(0f),
                    fatBurnGrams = prefs.getFloat("${prefix}_fat", 0f).coerceAtLeast(0f),
                ),
            )
        }
    }

    private fun accumulateFatBurnDailyTotals(
        prefsName: String,
        startMs: Long,
        endMsExclusive: Long,
        destination: MutableMap<String, WorkoutMetricTotals>,
    ) {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val raw = prefs.getString("daily_reports_json", null).orEmpty()
        if (raw.isBlank()) {
            return
        }
        runCatching {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val timestampMs = item.optLong("timestampMs", 0L)
                if (timestampMs < startMs || timestampMs >= endMsExclusive) {
                    continue
                }
                destination.accumulateDayTotals(
                    aggregateTodayKey(timestampMs),
                    WorkoutMetricTotals(
                        hits = item.optInt("totalHits", 0).coerceAtLeast(0),
                        durationSeconds = item.optInt("durationSec", 0).coerceAtLeast(0),
                        calories = item.optDouble("calories", 0.0).toFloat().coerceAtLeast(0f),
                        fatBurnGrams = item.optDouble("fatBurnGrams", 0.0).toFloat().coerceAtLeast(0f),
                    ),
                )
            }
        }
    }

    private fun MutableMap<String, WorkoutMetricTotals>.accumulateDayTotals(
        dayKey: String,
        addition: WorkoutMetricTotals,
    ) {
        this[dayKey] = (this[dayKey] ?: WorkoutMetricTotals()) + addition
    }

    private fun formatLeaderboardDayLabel(dayKey: String): String =
        runCatching {
            val source = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getDefault() }
            val target =
                when (selectedLanguage) {
                    AppLanguage.Chinese -> SimpleDateFormat("MM月dd日 EEE", localeForLanguage())
                    else -> SimpleDateFormat("MMM dd, EEE", localeForLanguage())
                }.apply { timeZone = TimeZone.getDefault() }
            target.format(source.parse(dayKey) ?: Date())
        }.getOrDefault(dayKey)

    private fun localLeaderboardSummaryText(
        board: LeaderboardBoard,
        topEntry: LocalLeaderboardEntry?,
    ): String {
        val base =
            when (board) {
                LeaderboardBoard.DailyBestHits ->
                    localText(
                        "按不同用户的历史单日最高拳击数进行排序。",
                        "Ranked by each user's best single training day.",
                        "Classé par le meilleur total quotidien de chaque utilisateur.",
                        "จัดอันดับตามจำนวนหมัดสูงสุดในหนึ่งวันของผู้ใช้แต่ละคน",
                    )
                LeaderboardBoard.TotalHits ->
                    localText(
                        "按不同用户注册至今的累计拳击数进行排序。",
                        "Ranked by each user's lifetime punch count.",
                        "Classé par le total cumulé de coups par utilisateur.",
                        "จัดอันดับตามจำนวนหมัดสะสมทั้งหมดของผู้ใช้",
                    )
                LeaderboardBoard.TotalDuration ->
                    localText(
                        "按不同用户的历史累计训练时长进行排序。",
                        "Ranked by each user's lifetime training duration.",
                        "Classé par durée d'entraînement cumulée par utilisateur.",
                        "จัดอันดับตามระยะเวลาฝึกสะสมทั้งหมดของผู้ใช้",
                    )
                LeaderboardBoard.TotalCalories ->
                    localText(
                        "按不同用户的历史累计消耗卡路里进行排序。",
                        "Ranked by each user's lifetime calories burned.",
                        "Classé par calories cumulées par utilisateur.",
                        "จัดอันดับตามแคลอรีสะสมทั้งหมดของผู้ใช้",
                    )
                LeaderboardBoard.TotalFatBurn ->
                    localText(
                        "按不同用户的历史累计燃脂量进行排序。",
                        "Ranked by each user's lifetime fat burn.",
                        "Classé par graisse brûlée cumulée par utilisateur.",
                        "จัดอันดับตามปริมาณการเผาผลาญไขมันสะสมของผู้ใช้",
                    )
            }
        val topper =
            topEntry?.let {
                localText(
                    "\n当前榜首：${it.title} · ${it.primaryValue}",
                    "\nCurrent leader: ${it.title} · ${it.primaryValue}",
                    "\nLeader actuel : ${it.title} · ${it.primaryValue}",
                    "\nผู้นำปัจจุบัน: ${it.title} · ${it.primaryValue}",
                )
            }.orEmpty()
        return base + topper
    }

    private fun renderAchievementsPlaceholder() {
        achievementsTitleView.text = localText("锻炼成果", "Workout Results", "Résultats d'entraînement", "ผลการฝึก")
        achievementsSubtitleView.text = ""
        achievementsSubtitleView.visibility = View.GONE
        achievementsCard.visibility = View.GONE
        historyTitleView.visibility = View.GONE
        historySubtitleView.visibility = View.GONE
        historyCard.visibility = View.GONE
        if (::achievementsSwipe.isInitialized) {
            achievementsSwipe.isEnabled = false
        }
    }

    private fun renderLeaderboardPlaceholder() {
        leaderboardTitleView.text = tr("leaderboard_title")
        leaderboardSubtitleView.text = ""
        leaderboardSubtitleView.visibility = View.GONE
        leaderboardModeGroup.visibility = View.GONE
        refreshLeaderboardButton.visibility = View.GONE
        leaderboardCard.visibility = View.GONE
        if (::leaderboardSwipe.isInitialized) {
            leaderboardSwipe.isEnabled = false
        }
    }

    private fun detachFromParent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun buildProfileMetaText(): String {
        val profile = cloudProfile ?: return ""
        return buildString {
            append(tr("activation_serial_label"))
            append(": ")
            append(profile.serialMasked)
            append("   ·   ")
            append(tr("profile_language"))
            append(": ")
            append(languageDisplayName(AppLanguage.fromStorage(profile.languageCode)))
            val countryCode = normalizedCountryCode(profile.countryCode)
            if (countryCode != null) {
                append('\n')
                append(tr("profile_country"))
                append(": ")
                append(countryCode)
            }
        }
    }

    private fun normalizedCountryCode(value: String?): String? {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) {
            return null
        }
        return if (normalized.equals("null", ignoreCase = true)) null else normalized
    }

    private fun buildProfileStatsText(): String {
        val stats = cloudStatistics ?: return tr("cloud_sync_idle")
        return buildString {
            append(tr("total_sessions"))
            append(": ")
            append(stats.totalSessions)
            append("   ·   ")
            append(tr("total_hits"))
            append(": ")
            append(stats.totalHits)
            append('\n')
            append(tr("best_30_hits"))
            append(": ")
            append(stats.best30Hits)
            append("   ·   ")
            append(tr("best_60_hits"))
            append(": ")
            append(stats.best60Hits)
            append('\n')
            append(tr("average_frequency"))
            append(": ")
            append(String.format(Locale.US, "%.2f %s", maxOf(stats.average30Frequency, stats.average60Frequency), tr("hits_per_second")))
        }
    }

    private fun buildHistoryText(): String {
        if (cloudHistory.isEmpty()) {
            return tr("no_history")
        }
        return cloudHistory.take(6).joinToString("\n\n") { item ->
            buildString {
                append(displayModeLabel(secondsToMode(item.modeSeconds)))
                append("  ·  ")
                append(item.totalHits)
                append(" ")
                append(tr("hits"))
                append('\n')
                append(String.format(Locale.US, "%.2f %s", item.averageFrequency, tr("hits_per_second")))
                append("  ·  ")
                append(tr("best_burst"))
                append(": ")
                append(item.bestBurstCount)
                append('\n')
                append(formatHistoryTime(item.endedAt))
            }
        }
    }

    private fun buildLeaderboardText(): String {
        val result = leaderboardResult
        if (result == null || !result.success || result.top.isEmpty()) {
            return tr("leaderboard_empty")
        }
        val lines =
            result.top.joinToString("\n") { entry ->
                "${rankLabel(entry.rank)} ${leaderboardDisplayName(entry)}\n${leaderboardPrimaryValueText(entry)}\n${leaderboardSerialBadgeText(entry)}"
            }
        val meLine =
            result.me?.let { entry ->
                "\n\n${tr("leaderboard_me")}: ${rankLabel(entry.rank)}  ${leaderboardDisplayName(entry)}  |  ${leaderboardPrimaryValueText(entry)}"
            } ?: "\n\n${tr("leaderboard_no_rank")}"
        return lines + meLine
    }

    private fun leaderboardBoardLabel(board: LeaderboardBoard): String =
        when (selectedLanguage) {
            AppLanguage.Chinese ->
                when (board) {
                    LeaderboardBoard.DailyBestHits -> "单日最高拳击数"
                    LeaderboardBoard.TotalHits -> "历史累计拳击数"
                    LeaderboardBoard.TotalDuration -> "历史累计训练时长"
                    LeaderboardBoard.TotalCalories -> "历史累计消耗卡路里"
                    LeaderboardBoard.TotalFatBurn -> "历史累计燃脂量"
                }
            AppLanguage.English ->
                when (board) {
                    LeaderboardBoard.DailyBestHits -> "Best Day Hits"
                    LeaderboardBoard.TotalHits -> "Lifetime Hits"
                    LeaderboardBoard.TotalDuration -> "Lifetime Duration"
                    LeaderboardBoard.TotalCalories -> "Lifetime Calories"
                    LeaderboardBoard.TotalFatBurn -> "Lifetime Fat Burn"
                }
            AppLanguage.French ->
                when (board) {
                    LeaderboardBoard.DailyBestHits -> "Meilleur total du jour"
                    LeaderboardBoard.TotalHits -> "Coups cumulés"
                    LeaderboardBoard.TotalDuration -> "Durée cumulée"
                    LeaderboardBoard.TotalCalories -> "Calories cumulées"
                    LeaderboardBoard.TotalFatBurn -> "Graisse brûlée cumulée"
                }
            AppLanguage.Thai ->
                when (board) {
                    LeaderboardBoard.DailyBestHits -> "หมัดสูงสุดต่อวัน"
                    LeaderboardBoard.TotalHits -> "หมัดสะสมทั้งหมด"
                    LeaderboardBoard.TotalDuration -> "เวลาฝึกสะสม"
                    LeaderboardBoard.TotalCalories -> "แคลอรีสะสม"
                    LeaderboardBoard.TotalFatBurn -> "การเผาผลาญไขมันสะสม"
                }
        }

    private fun leaderboardBoardSubtitle(board: LeaderboardBoard): String =
        when (selectedLanguage) {
            AppLanguage.Chinese ->
                when (board) {
                    LeaderboardBoard.DailyBestHits -> "按历史单日最高拳击数排名"
                    LeaderboardBoard.TotalHits -> "按不同用户的历史累计拳击数排名"
                    LeaderboardBoard.TotalDuration -> "按不同用户的历史累计训练时长排名"
                    LeaderboardBoard.TotalCalories -> "按不同用户的历史累计消耗卡路里排名"
                    LeaderboardBoard.TotalFatBurn -> "按不同用户的历史累计燃脂量排名"
                }
            AppLanguage.English ->
                when (board) {
                    LeaderboardBoard.DailyBestHits -> "Ranked by each user's best single-day punch count"
                    LeaderboardBoard.TotalHits -> "Ranked by each user's lifetime punch count"
                    LeaderboardBoard.TotalDuration -> "Ranked by each user's lifetime training duration"
                    LeaderboardBoard.TotalCalories -> "Ranked by each user's lifetime calories burned"
                    LeaderboardBoard.TotalFatBurn -> "Ranked by each user's lifetime fat burn"
                }
            AppLanguage.French ->
                when (board) {
                    LeaderboardBoard.DailyBestHits -> "Classement selon le meilleur nombre de coups sur une journée"
                    LeaderboardBoard.TotalHits -> "Classement selon le total de coups cumulés"
                    LeaderboardBoard.TotalDuration -> "Classement selon la durée totale d'entraînement"
                    LeaderboardBoard.TotalCalories -> "Classement selon les calories brûlées cumulées"
                    LeaderboardBoard.TotalFatBurn -> "Classement selon la graisse brûlée cumulée"
                }
            AppLanguage.Thai ->
                when (board) {
                    LeaderboardBoard.DailyBestHits -> "จัดอันดับตามจำนวนหมัดสูงสุดของผู้ใช้ในหนึ่งวัน"
                    LeaderboardBoard.TotalHits -> "จัดอันดับตามจำนวนหมัดสะสมทั้งหมดของผู้ใช้"
                    LeaderboardBoard.TotalDuration -> "จัดอันดับตามเวลาฝึกสะสมทั้งหมดของผู้ใช้"
                    LeaderboardBoard.TotalCalories -> "จัดอันดับตามแคลอรีสะสมที่ผู้ใช้เผาผลาญ"
                    LeaderboardBoard.TotalFatBurn -> "จัดอันดับตามการเผาผลาญไขมันสะสมของผู้ใช้"
                }
        }

    private fun leaderboardBoardFromKey(key: String?): LeaderboardBoard =
        when (key) {
            LeaderboardBoard.TotalHits.apiKey -> LeaderboardBoard.TotalHits
            LeaderboardBoard.TotalDuration.apiKey -> LeaderboardBoard.TotalDuration
            LeaderboardBoard.TotalCalories.apiKey -> LeaderboardBoard.TotalCalories
            LeaderboardBoard.TotalFatBurn.apiKey -> LeaderboardBoard.TotalFatBurn
            LeaderboardBoard.DailyBestHits.apiKey -> LeaderboardBoard.DailyBestHits
            else -> LeaderboardBoard.DailyBestHits
        }

    private fun leaderboardPrimaryValueText(entry: CloudLeaderboardEntry): String =
        when (leaderboardBoard) {
            LeaderboardBoard.DailyBestHits ->
                localText("${entry.bestHits} 拳", "${entry.bestHits} hits", "${entry.bestHits} coups", "${entry.bestHits} หมัด")
            LeaderboardBoard.TotalHits ->
                localText("${entry.bestHits} 拳", "${entry.bestHits} hits", "${entry.bestHits} coups", "${entry.bestHits} หมัด")
            LeaderboardBoard.TotalDuration ->
                localText("${entry.bestHits} 分钟", "${entry.bestHits} min", "${entry.bestHits} min", "${entry.bestHits} นาที")
            LeaderboardBoard.TotalCalories -> "${entry.bestHits} kcal"
            LeaderboardBoard.TotalFatBurn -> "${entry.bestHits} g"
        }

    private fun leaderboardSecondaryValueText(entry: CloudLeaderboardEntry): String =
        when (leaderboardBoard) {
            LeaderboardBoard.DailyBestHits ->
                entry.endedAt?.takeIf { it.isNotBlank() }?.let {
                    if (it.length >= 10) {
                        formatLeaderboardDayLabel(it.take(10))
                    } else {
                        it
                    }
                } ?: localText("历史日榜", "Daily board", "Classement du jour", "อันดับรายวัน")
            LeaderboardBoard.TotalHits ->
                localText("历史累计", "Lifetime total", "Cumul total", "สะสมทั้งหมด")
            LeaderboardBoard.TotalDuration ->
                localText("历史累计", "Lifetime duration", "Durée cumulée", "เวลาสะสม")
            LeaderboardBoard.TotalCalories ->
                localText("历史累计", "Lifetime calories", "Calories cumulées", "แคลอรีสะสม")
            LeaderboardBoard.TotalFatBurn ->
                localText("历史累计", "Lifetime fat burn", "Graisse brûlée cumulée", "เผาผลาญไขมันสะสม")
        }

    private fun leaderboardDisplayName(entry: CloudLeaderboardEntry): String {
        val nickname = entry.nickname.trim()
        if (nickname.isBlank()) {
            val suffix = entry.serialMasked.takeLast(4)
            return localText("用户 $suffix", "Player $suffix", "Joueur $suffix", "ผู้เล่น $suffix")
        }
        val maskedSuffix = entry.serialMasked.takeLast(4)
        val genericNickname = "Player-$maskedSuffix"
        return if (nickname.equals(genericNickname, ignoreCase = true)) {
            localText("用户 $maskedSuffix", "Player $maskedSuffix", "Joueur $maskedSuffix", "ผู้เล่น $maskedSuffix")
        } else {
            nickname
        }
    }

    private fun leaderboardSerialBadgeText(entry: CloudLeaderboardEntry): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "序列号 ${entry.serialMasked}"
            AppLanguage.English -> "Serial ${entry.serialMasked}"
            AppLanguage.French -> "Série ${entry.serialMasked}"
            AppLanguage.Thai -> "ซีเรียล ${entry.serialMasked}"
        }

    private fun leaderboardPodiumTitle(rank: Int): String =
        when (selectedLanguage) {
            AppLanguage.Chinese ->
                when (rank) {
                    1 -> "冠军"
                    2 -> "亚军"
                    3 -> "季军"
                    else -> "TOP $rank"
                }
            else ->
                when (rank) {
                    1 -> "Champion"
                    2 -> "Runner-up"
                    3 -> "Third Place"
                    else -> "TOP $rank"
                }
        }

    private fun formatHistoryTime(value: String?): String {
        if (value.isNullOrBlank()) {
            return tr("activation_just_now")
        }
        val parsed = parseCloudDate(value)
        if (parsed == null) {
            return value.replace('T', ' ').replace("Z", "").replace(".000", "")
        }
        val pattern =
            when (selectedLanguage) {
                AppLanguage.Chinese -> "MM-dd HH:mm"
                AppLanguage.English -> "MMM dd, HH:mm"
                AppLanguage.French -> "dd MMM HH:mm"
                AppLanguage.Thai -> "dd/MM HH:mm"
            }
        return SimpleDateFormat(pattern, localeForLanguage()).apply {
            timeZone = TimeZone.getDefault()
        }.format(parsed)
    }

    private fun reportCardText(report: TrainingReport): String {
        val frequency = String.format(Locale.US, "%.2f", report.averageFrequency)
        val bestStart = String.format(Locale.US, "%.1f", report.bestBurstStartSec)
        val burstUnit = localText("秒", "s", "s", "วินาที")
        return buildString {
            append("${tr("mode")}: ${displayModeLabel(report.mode)}")
            append('\n')
            append("${tr("sensitivity")}: $sensitivityLevel")
            append('\n')
            append("${tr("total_hits")}: ${report.totalHits}")
            append('\n')
            append("${tr("average_frequency")}: $frequency ${tr("hits_per_second")}")
            append('\n')
            append("${tr("best_burst")}: ${report.bestBurstCount} ${tr("hits")}")
            append('\n')
            append("${tr("burst_start")}: $bestStart$burstUnit")
        }
    }

    private fun emptyStateCard(
        badge: String,
        title: String,
        message: String,
        accentColor: String = "#FF9A30",
    ): LinearLayout =
        detailCard(fillColor = "#0D1822", strokeColor = accentColor, cornerDp = 22).apply {
            background = metallicBackground("#163246", "#0A141C", accentColor, 22)
            gravity = Gravity.CENTER_HORIZONTAL
            addView(
                TextView(this@MainActivity).apply {
                    text = badge
                    gravity = Gravity.CENTER
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(Color.parseColor("#140800"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    background = metallicBackground("#BCEEFF", accentColor, "#E8FBFF", 999)
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                },
            )
            addView(
                titleText(title, 20f).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(14), 0, 0)
                },
            )
            addView(
                bodyText(message).apply {
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#B7CFE0"))
                    setPadding(dp(4), dp(8), dp(4), dp(4))
                },
            )
        }

    private fun renderEmptyReport() {
        reportView.removeAllViews()
        reportView.addView(
            emptyStateCard(
                badge = localText("今日战报", "TODAY REPORT", "RAPPORT", "รายงานวันนี้"),
                title =
                    localText(
                        "等待今日综合战报",
                        "Waiting for today's combined report",
                        "En attente du rapport du jour",
                        "กำลังรอรายงานสรุปวันนี้",
                    ),
                message =
                    localText(
                        "完成任一训练模块后，这里会自动汇总今天的拳击数、压力值、平静值、卡路里和燃脂数据。",
                        "After you finish any training module, today's hits, stress relief, calm gain, calories, and fat burn will appear here automatically.",
                        "Après n'importe quel module, cette page affichera automatiquement les coups, le stress réduit, le calme gagné, les calories et la graisse brûlée du jour.",
                        "หลังจบการฝึกในโมดูลใดก็ได้ หน้านี้จะสรุปจำนวนหมัด ความเครียดที่ลดลง ความสงบที่เพิ่มขึ้น แคลอรี และการเผาผลาญไขมันของวันนี้โดยอัตโนมัติ",
                    ),
                accentColor = "#FF9A30",
            ),
        )
        return
        reportView.removeAllViews()
        reportView.addView(
            emptyStateCard(
                badge = localText("战报", "REPORT", "RAPPORT", "รายงาน"),
                title =
                    localText(
                        "等待首份训练战报",
                        "Waiting for your first report",
                        "En attente de votre premier rapport",
                        "กำลังรอรายงานการฝึกครั้งแรก",
                    ),
                message = tr("no_report"),
                accentColor = "#FF9A30",
            ),
        )
    }

    private fun reportMetricCard(
        label: String,
        value: String,
        accentColor: String,
        alignLeft: Boolean = false,
    ): LinearLayout =
        detailCard(fillColor = "#0B1721", strokeColor = accentColor, cornerDp = 18).apply {
            setPadding(dp(14), if (alignLeft) dp(10) else dp(12), dp(14), if (alignLeft) dp(10) else dp(12))
            addView(
                bodyText(label).apply {
                    setTextColor(Color.parseColor("#B88A54"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (alignLeft) 11.5f else 12f)
                    if (alignLeft) {
                        gravity = Gravity.START
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    }
                },
            )
            addView(
                titleText(value, if (alignLeft) 20f else 18f).apply {
                    setTextColor(Color.parseColor("#FFF8E8"))
                    setPadding(0, if (alignLeft) dp(6) else dp(8), 0, 0)
                    if (alignLeft) {
                        gravity = Gravity.START
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                        val textValue = value.trim()
                        val unitStart = textValue.lastIndexOf(' ')
                        text =
                            if (unitStart > 0 && unitStart < textValue.length - 1) {
                                SpannableStringBuilder(textValue).apply {
                                    setSpan(
                                        RelativeSizeSpan(1.18f),
                                        0,
                                        unitStart,
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                                    )
                                    setSpan(
                                        ForegroundColorSpan(Color.parseColor("#A9B9C8")),
                                        unitStart + 1,
                                        textValue.length,
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                                    )
                                    setSpan(
                                        RelativeSizeSpan(0.72f),
                                        unitStart + 1,
                                        textValue.length,
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                                    )
                                }
                            } else {
                                SpannableStringBuilder(textValue).apply {
                                    setSpan(
                                        RelativeSizeSpan(1.12f),
                                        0,
                                        textValue.length,
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                                    )
                                }
                            }
                    }
                },
            )
        }

    private fun LinearLayout.applyAggregateCardTone(
        topColor: String,
        bottomColor: String,
        accentColor: String,
        prominent: Boolean = false,
    ) {
        background = metallicBackground(topColor, bottomColor, accentColor, if (prominent) 20 else 18)
        elevation = if (prominent) dp(7).toFloat() else dp(5).toFloat()
        (getChildAt(0) as? TextView)?.apply {
            setTextColor(Color.parseColor(if (prominent) "#FFD8A3" else "#C9B08B"))
            if (prominent) {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            }
        }
        (getChildAt(1) as? TextView)?.apply {
            setTextColor(Color.parseColor("#FFF8EF"))
            if (prominent) {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            }
        }
    }

    private fun formatReportEndedTime(epochMs: Long): String {
        val pattern =
            when (selectedLanguage) {
                AppLanguage.Chinese -> "MM-dd HH:mm"
                AppLanguage.English -> "MMM dd, HH:mm"
                AppLanguage.French -> "dd MMM HH:mm"
                AppLanguage.Thai -> "dd/MM HH:mm"
            }
        return SimpleDateFormat(pattern, localeForLanguage()).format(Date(epochMs))
    }

    private fun setCloudStatusMessage(
        colorHex: String,
        key: String? = null,
        fallback: String? = null,
    ) {
        cloudStatusMessageKey = key
        cloudStatusFallbackMessage = sanitizeCloudStatusFallback(fallback)
        cloudStatusColor = Color.parseColor(colorHex)
        if (::cloudStatusView.isInitialized) {
            cloudStatusView.setTextColor(cloudStatusColor)
            cloudStatusView.text = currentCloudStatusMessage()
            cloudStatusView.background = chipBackground(cloudStatusColor)
        }
    }

    private fun sanitizeCloudStatusFallback(message: String?): String? {
        val normalized = message?.trim().orEmpty()
        if (normalized.isBlank()) {
            return null
        }
        val lower = normalized.lowercase(Locale.US)
        return when {
            lower.contains("serial number not found") -> null
            normalized.contains("序列号不存在") -> null
            else -> normalized
        }
    }

    private fun currentCloudStatusMessage(): String =
        cloudStatusMessageKey?.let(::tr) ?: cloudStatusFallbackMessage.orEmpty()

    private fun secondsToMode(seconds: Int): TrainingMode =
        when {
            seconds >= 60 -> TrainingMode.Seconds60
            seconds >= 30 -> TrainingMode.Seconds30
            seconds >= 15 -> TrainingMode.Burst15
            else -> TrainingMode.Burst10
        }

    private fun setTrainingBusyUi(isBusy: Boolean) {
        val activated = isActivated()
        startButton.isEnabled = !isBusy && activated
        startButton.alpha = if (startButton.isEnabled) 1.0f else 0.5f
        stopButton.isEnabled = isBusy
        stopButton.alpha = if (isBusy) 1.0f else 0.5f
        settingsButton.isEnabled = !isBusy
        settingsButton.alpha = if (isBusy) 0.5f else 1.0f
        activateButton.isEnabled = !isBusy && !activated && activationInputsValid
        activateButton.alpha = if (activateButton.isEnabled) 1.0f else 0.6f
        serialInput.isEnabled = !isBusy && !activated
        codeInput.isEnabled = !isBusy && !activated
        for (index in 0 until modeGroup.childCount) {
            modeGroup.getChildAt(index).isEnabled = !isBusy
            modeGroup.getChildAt(index).alpha = if (isBusy) 0.6f else 1.0f
        }
        refreshModeButtonStyles()
    }

    private fun setActivationVisible(visible: Boolean) {
        if (!visible && ::activationCard.isInitialized) {
            activationCard.removeCallbacks(hideActivationCardRunnable)
        }
        activationCard.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun setActivationBusy(isBusy: Boolean) {
        val allowInput = !isBusy && !isActivated()
        activateButton.isEnabled = allowInput && activationInputsValid
        activateButton.alpha = if (activateButton.isEnabled) 1.0f else 0.6f
        serialInput.isEnabled = allowInput
        codeInput.isEnabled = allowInput
    }

    private fun updateActivationInputState() {
        val serialDigits = normalizeDigits(serialInput.text?.toString())
        val codeDigits = normalizeDigits(codeInput.text?.toString())
        val serialRaw = serialInput.text?.toString().orEmpty()
        val codeRaw = codeInput.text?.toString().orEmpty()
        val serialOk = serialDigits.length == 11
        val codeOk = codeDigits.length == 8

        if (serialRaw.isEmpty() || serialOk) {
            serialInputErrorView.text = ""
            serialInputErrorView.visibility = View.GONE
        } else {
            serialInputErrorView.text = tr("serial_invalid")
            serialInputErrorView.visibility = View.VISIBLE
        }
        if (codeRaw.isEmpty() || codeOk) {
            codeInputErrorView.text = ""
            codeInputErrorView.visibility = View.GONE
        } else {
            codeInputErrorView.text = tr("code_invalid")
            codeInputErrorView.visibility = View.VISIBLE
        }

        activationInputsValid = serialOk && codeOk
        val activated = isActivated()
        val busy = activationJob?.isActive == true || trainingJob?.isActive == true
        val enabled = !activated && !busy && activationInputsValid
        activateButton.isEnabled = enabled
        activateButton.alpha = if (enabled) 1.0f else 0.6f
    }

    private fun showFormalSettingsDialog() {
        val dialogRoot =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(12), dp(20), dp(4))
            }

        dialogRoot.addView(
            bodyText(tr("settings_subtitle")).apply {
                setTextColor(Color.parseColor("#E9D2A2"))
                setPadding(0, 0, 0, dp(14))
            },
        )
        dialogRoot.addView(sectionLabel(deviceFeaturesSectionTitle()))
        boxingBleCard = buildBoxingBleCard()
        dialogRoot.addView(boxingBleCard)
        dialogRoot.addView(sectionLabel(tr("language")))
        dialogRoot.addView(
            bodyText(tr("language_helper")).apply {
                setTextColor(Color.parseColor("#E9D2A2"))
                setPadding(0, 0, 0, dp(8))
            },
        )
        val languageGroup =
            RadioGroup(this).apply {
                orientation = RadioGroup.VERTICAL
                setPadding(0, dp(4), 0, dp(16))
            }
        val zhOption =
            RadioButton(this).apply {
                id = View.generateViewId()
                text = tr("language_chinese")
                isChecked = selectedLanguage == AppLanguage.Chinese
                setTextColor(Color.WHITE)
            }
        val enOption =
            RadioButton(this).apply {
                id = View.generateViewId()
                text = tr("language_english")
                isChecked = selectedLanguage == AppLanguage.English
                setTextColor(Color.WHITE)
            }
        val frOption =
            RadioButton(this).apply {
                id = View.generateViewId()
                text = tr("language_french")
                isChecked = selectedLanguage == AppLanguage.French
                setTextColor(Color.WHITE)
            }
        val thOption =
            RadioButton(this).apply {
                id = View.generateViewId()
                text = tr("language_thai")
                isChecked = selectedLanguage == AppLanguage.Thai
                setTextColor(Color.WHITE)
            }
        languageGroup.addView(zhOption)
        languageGroup.addView(enOption)
        languageGroup.addView(frOption)
        languageGroup.addView(thOption)
        dialogRoot.addView(languageGroup)

        dialogRoot.addView(sectionLabel(tr("sensitivity")))
        dialogRoot.addView(
            bodyText(tr("sensitivity_helper")).apply {
                setTextColor(Color.parseColor("#E9D2A2"))
                setPadding(0, 0, 0, dp(8))
            },
        )
        val sensitivityValueView =
            titleText(formatSensitivityValue(sensitivityLevel), 22f).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setTextColor(Color.parseColor("#FFB347"))
                setPadding(0, 0, 0, dp(4))
            }
        this.sensitivityValueView = sensitivityValueView
        dialogRoot.addView(sensitivityValueView)
        sensitivityDeviceStatusView =
            bodyText(punchThresholdStatusText(sensitivityLevel, fromDevice = false)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setTextColor(Color.parseColor("#C9A46A"))
                setPadding(0, 0, 0, dp(8))
            }
        dialogRoot.addView(sensitivityDeviceStatusView)
        val sensitivitySeek =
            SeekBar(this).apply {
                max = 100
                progress = sensitivityLevel
                setPadding(0, 0, 0, dp(6))
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            sensitivityValueView.text = formatSensitivityValue(progress)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                    },
                )
            }
        sensitivitySeekBar = sensitivitySeek
        dialogRoot.addView(sensitivitySeek)
        dialogRoot.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    bodyText(tr("sensitivity_stable")).apply {
                        setTextColor(Color.parseColor("#8F6A44"))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    },
                )
                addView(
                    bodyText(tr("sensitivity_balanced")).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        setTextColor(Color.parseColor("#8F6A44"))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    },
                )
                addView(
                    bodyText(tr("sensitivity_sensitive")).apply {
                        gravity = Gravity.END
                        setTextColor(Color.parseColor("#8F6A44"))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    },
                )
            },
        )

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(tr("settings"))
                .setView(
                    ScrollView(this).apply {
                        isFillViewport = false
                        addView(dialogRoot)
                    },
                )
                .setNeutralButton(tr("restore_default"), null)
                .setNegativeButton(tr("cancel"), null)
                .setPositiveButton(tr("save"), null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                sensitivitySeek.progress = DEFAULT_SENSITIVITY
                sensitivityValueView.text = formatSensitivityValue(DEFAULT_SENSITIVITY)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                applyLanguageAndSensitivitySettings(
                    language =
                        when (languageGroup.checkedRadioButtonId) {
                            enOption.id -> AppLanguage.English
                            frOption.id -> AppLanguage.French
                            thOption.id -> AppLanguage.Thai
                            else -> AppLanguage.Chinese
                        },
                    sensitivity = sensitivitySeek.progress,
                    refreshCloud = true,
                )
                dialog.dismiss()
            }
            if (isBoxingBleConnectedState(boxingBleManager?.currentState?.connectionState ?: BoxingBleManager.ConnectionState.Idle)) {
                boxingBleManager?.readPunchThresholdSensitivity()
                sensitivityDeviceStatusView?.text = sensitivityReadingDeviceText()
            } else {
                sensitivityDeviceStatusView?.text = sensitivityDeviceNotConnectedText()
            }
        }
        dialog.setOnDismissListener {
            this@MainActivity.sensitivitySeekBar = null
            this@MainActivity.sensitivityValueView = null
            this@MainActivity.sensitivityDeviceStatusView = null
        }
        dialog.show()
        dialog.window?.decorView?.setBackgroundColor(Color.parseColor("#1A0C00"))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (resources.displayMetrics.heightPixels * 0.88f).toInt(),
        )
    }

    private fun showSettingsDialog() {
        showFormalSettingsDialog()
    }

    private fun applyLanguageAndSensitivitySettings(
        language: AppLanguage,
        sensitivity: Int,
        refreshCloud: Boolean,
    ) {
        selectedLanguage = language
        sensitivityLevel = sensitivity.coerceIn(0, 100)
        saveSettings()
        writePunchThresholdSensitivityToDevice(sensitivityLevel)
        updateTtsLanguage()
        clearLanguageSensitiveFallbackMessages()
        if (isActivated()) {
            renderIdle()
            renderLatestReportPage()
            renderTrainingPlayStatus()
            refreshModeButtonStyles()
            refreshCloudViews()
            if (refreshCloud) {
                refreshCloudData(forceLeaderboard = false)
            }
        } else {
            renderActivationRequired()
        }
    }

    private fun clearLanguageSensitiveFallbackMessages() {
        if (cloudStatusMessageKey == null) {
            cloudStatusFallbackMessage = null
        }
        if (authStatusMessageKey == null) {
            authStatusFallbackMessage = null
        }
        lastCoachMessage = null
    }

    private fun profileHeroTagText(): String =
        localText("拳击训练档案", "FIGHTER PROFILE", "DOSSIER DU BOXEUR", "โปรไฟล์นักชก")

    private fun historyEmptyBadgeText(): String =
        localText("记录", "HISTORY", "HISTORIQUE", "ประวัติ")

    private fun historyEmptyTitleText(): String =
        localText(
            "训练记录尚未生成",
            "No training history yet",
            "Aucun historique pour le moment",
            "ยังไม่มีประวัติการฝึก",
        )

    private fun updateEmptyStateCardText(
        card: LinearLayout,
        badge: String,
        title: String,
        message: String,
    ) {
        (card.getChildAt(0) as? TextView)?.text = badge
        (card.getChildAt(1) as? TextView)?.text = title
        (card.getChildAt(2) as? TextView)?.text = message
    }

    private fun applyStaticTexts() {
        titleView.text = tr("title")
        subtitleView.text = headerSubtitleText()
        subtitleView.visibility = if (headerSubtitleText().isBlank()) View.GONE else View.VISIBLE
        modeTitleView.text = ""
        modeTitleView.visibility = View.GONE
        emotionModesTitleView.text =
            localText(
                "情绪减压",
                "Emotional Relief",
                "Apaisement émotionnel",
                "ผ่อนคลายอารมณ์",
            )
        fitnessModesTitleView.text =
            localText(
                "健身燃脂",
                "Fat-Burn Fitness",
                "Fitness brûle-graisse",
                "ฟิตเนสเผาผลาญไขมัน",
            )
        mode30Button.text = playModeLabel(TrainingPlayMode.Classic30)
        mode60Button.text = playModeLabel(TrainingPlayMode.Classic60)
        modeBurst10Button.text = playModeLabel(TrainingPlayMode.Burst10)
        modeBurst15Button.text = playModeLabel(TrainingPlayMode.Burst15)
        modeBurst15Button.text = localText("⚡ 极速燃脂", "⚡ Rapid Fat Burn", "⚡ Brûle-graisse express", "⚡ เผาผลาญเร่งด่วน")
        modeLevelButton.text = playModeLabel(TrainingPlayMode.LevelChallenge)
        modeDailyButton.text = playModeLabel(TrainingPlayMode.DailyChallenge)
        trainingPlayCard.visibility = View.GONE
        trainingHeroCard.visibility = View.GONE
        when (selectedPlayMode) {
            TrainingPlayMode.Classic30 -> mode30Button.isChecked = true
            TrainingPlayMode.Classic60 -> mode60Button.isChecked = true
            TrainingPlayMode.Burst10 -> modeBurst10Button.isChecked = true
            TrainingPlayMode.Burst15 -> modeBurst15Button.isChecked = true
            TrainingPlayMode.LevelChallenge -> modeLevelButton.isChecked = true
            TrainingPlayMode.DailyChallenge -> modeDailyButton.isChecked = true
        }
        refreshModeButtonStyles()
        renderTrainingPlayStatus()
        startButton.text = if (calibrationRetrySuggested) tr("retry_calibration") else tr("start")
        stopButton.text = tr("stop")
        pageTrainingButton.text = tr("page_training_center")
        pageAchievementsButton.text = localText("锻炼成果", "Workout Results", "Résultats", "ผลการฝึก")
        pageLeaderboardButton.text = tr("page_leaderboard")
        pageProfileButton.text = tr("page_profile")
        reportTitleView.text = tr("latest_report")
        profileTitleView.text = tr("profile_title")
        profileSubtitleView.text = profilePageSubtitle()
        profileSubtitleView.visibility = View.VISIBLE
        if (::profileHeroTagView.isInitialized) {
            profileHeroTagView.text = profileHeroTagText()
        }
        achievementsTitleView.text = localText("锻炼成果", "Workout Results", "Résultats", "ผลการฝึก")
        achievementsSubtitleView.text = ""
        achievementsSubtitleView.visibility = View.GONE
        historyTitleView.visibility = View.GONE
        historySubtitleView.visibility = View.GONE
        leaderboardTitleView.text = tr("leaderboard_title")
        leaderboardSubtitleView.text = ""
        leaderboardSubtitleView.visibility = View.GONE
        leaderboardModeGroup.visibility = View.GONE
        editProfileButton.text = tr("profile_edit")
        refreshCloudButton.text = tr("cloud_refresh")
        debugLogExportButton.text = debugLogExportLabel()
        debugLogExportButton.visibility = if (canUseTrainingDebugLogs()) View.VISIBLE else View.GONE
        developerInfoButton.text = developerInfoButtonLabel()
        refreshLeaderboardButton.text = tr("leaderboard_refresh")
        refreshLeaderboardButton.visibility = View.GONE
        shareTrainingButton.text = shareTrainingLabel()
        shareAchievementsButton.text = shareAchievementsLabel()
        shareLeaderboardButton.text = shareLeaderboardLabel()
        settingsButton.contentDescription = tr("settings")
        quietIconView.contentDescription = tr("keep_quiet")
        refreshActivationCardState()
        if (::serialInput.isInitialized && ::codeInput.isInitialized) {
            updateActivationInputState()
        }
        refreshHomePageVisibility()
        renderLatestReportPage()
        refreshCloudViews()
    }
    private fun shareTrainingLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "分享战报"
            AppLanguage.French -> "Partager"
            AppLanguage.Thai -> "แชร์รายงาน"
            else -> "Share Report"
        }

    private fun shareAchievementsLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "分享成果"
            AppLanguage.French -> "Partager"
            AppLanguage.Thai -> "แชร์ผลการฝึก"
            else -> "Share Results"
        }

    private fun shareLeaderboardLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "分享排名"
            AppLanguage.French -> "Partager"
            AppLanguage.Thai -> "แชร์อันดับ"
            else -> "Share Rank"
        }

    private fun currentTierShareLabel(): String =
        cloudTier?.let { tierLabelForKey(it.key) } ?: tierLabelForLevel(cloudProfile?.currentTier ?: 1)

    private fun posterRoot(
        accentColor: String,
        secondaryAccent: String = "#17384B",
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(32), dp(42), dp(32), dp(42))
            background =
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.parseColor("#120600"),
                        Color.parseColor("#241000"),
                        Color.parseColor("#071019"),
                    ),
                ).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(34).toFloat()
                    setStroke(dp(2), Color.parseColor(accentColor))
                }
            addView(
                TextView(this@MainActivity).apply {
                    text = "BoxingFitness"
                    setTextColor(Color.parseColor("#FFF0BF"))
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    letterSpacing = 0.08f
                },
            )
            addView(
                View(this@MainActivity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(1),
                        ).apply {
                            topMargin = dp(16)
                            bottomMargin = dp(20)
                        }
                    background = roundedBackground(secondaryAccent, secondaryAccent, 999)
                    alpha = 0.55f
                },
            )
        }

    private fun posterSectionCard(
        accentColor: String,
        fillColor: String = "#0D1822",
        strokeColor: String = accentColor,
    ): LinearLayout =
        detailCard(fillColor = fillColor, strokeColor = strokeColor, cornerDp = 26).apply {
            background = metallicBackground("#152B39", fillColor, strokeColor, 26)
        }

    private fun posterMetricCard(
        label: String,
        value: String,
        accentColor: String,
    ): LinearLayout =
        detailCard(fillColor = "#0B1720", strokeColor = accentColor, cornerDp = 18).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(
                bodyText(label).apply {
                    setTextColor(Color.parseColor("#B88A54"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                },
            )
            addView(
                titleText(value, 19f).apply {
                    gravity = Gravity.START
                    setTextColor(Color.parseColor("#FFF8E8"))
                    setPadding(0, dp(8), 0, 0)
                },
            )
        }

    private fun posterIdentityCard(
        nickname: String,
        subline: String,
        accentColor: String,
    ): LinearLayout =
        posterSectionCard(accentColor = accentColor, fillColor = "#0A141C", strokeColor = "#294558").apply {
            val row =
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
            val avatarShell =
                FrameLayout(this@MainActivity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(dp(68), dp(68)).apply {
                            rightMargin = dp(16)
                        }
                }
            val avatarImage =
                ImageView(this@MainActivity).apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                }
            val avatarFallback =
                TextView(this@MainActivity).apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    gravity = Gravity.CENTER
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                }
            bindAvatarPresentation(
                container = avatarShell,
                imageView = avatarImage,
                fallbackView = avatarFallback,
                seedText = nickname,
                colorHex = cloudProfile?.avatarColor ?: "#2A5C7B",
                imageUri = currentAvatarImageUri(),
            )
            avatarShell.addView(avatarImage)
            avatarShell.addView(avatarFallback)

            val textColumn =
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f,
                        )
                }
            textColumn.addView(
                titleText(nickname, 20f).apply {
                    gravity = Gravity.START
                    setTextColor(Color.parseColor("#FFF8E8"))
                },
            )
            textColumn.addView(
                bodyText(subline).apply {
                    setTextColor(Color.parseColor("#CAA26A"))
                    setPadding(0, dp(6), 0, 0)
                },
            )
            row.addView(avatarShell)
            row.addView(textColumn)
            addView(row)
        }

    private fun renderPosterBitmap(root: View): Bitmap {
        val widthPx = 1080
        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return Bitmap.createBitmap(root.measuredWidth, root.measuredHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            root.draw(canvas)
        }
    }

    private fun sharePosterBitmap(
        bitmap: Bitmap,
        filePrefix: String,
        chooserTitle: String,
        shareText: String,
    ) {
        val shareDir = File(cacheDir, "shared").apply { mkdirs() }
        val outputFile = File(shareDir, "${filePrefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(outputFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", outputFile)
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }

    private fun shareTextPlain(
        text: String,
        chooserTitle: String = shareTrainingLabel(),
    ) {
        if (text.isBlank()) {
            return
        }
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }

    private fun buildTrainingPosterBitmap(report: TrainingReport): Bitmap {
        val accentColor = "#FF9A30"
        val root = posterRoot(accentColor)
        root.addView(
            TextView(this).apply {
                text = localText("训练战报", "TRAINING REPORT", "RAPPORT D'ENTRAINEMENT", "รายงานการฝึก")
                setTextColor(Color.parseColor("#140800"))
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = metallicBackground("#BCEEFF", accentColor, "#FFF0C9", 999)
                setPadding(dp(14), dp(7), dp(14), dp(7))
            },
        )
        root.addView(
            posterSectionCard(accentColor = accentColor, fillColor = "#1A0C00", strokeColor = accentColor).apply {
                val heroRow =
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                    }
                val leftColumn =
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        minimumWidth = dp(280)
                        layoutParams =
                            LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1f,
                            ).apply {
                                rightMargin = dp(22)
                            }
                    }
                leftColumn.addView(
                    bodyText(localText("本次训练成绩", "SESSION RESULT", "RESULTAT DE SESSION", "ผลการฝึกครั้งนี้")).apply {
                        setTextColor(Color.parseColor("#B88A54"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    },
                )
                leftColumn.addView(
                    titleText(localText("成绩快照", "Performance Snapshot", "Apercu des performances", "ภาพรวมผลงาน"), 30f).apply {
                        gravity = Gravity.START
                        setTextColor(Color.parseColor("#FFF8E8"))
                        setPadding(0, dp(10), 0, 0)
                    },
                )
                leftColumn.addView(
                    bodyText(displayModeLabel(report.mode)).apply {
                        setTextColor(Color.parseColor("#FFE49A"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        setPadding(0, dp(8), 0, 0)
                    },
                )
                leftColumn.addView(
                    bodyText(formatReportEndedTime(report.endedAtEpochMs)).apply {
                        setTextColor(Color.parseColor("#B88A54"))
                        setPadding(0, dp(12), 0, 0)
                    },
                )
                val scoreOrb =
                    FrameLayout(this@MainActivity).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(dp(196), dp(196)).apply {
                                gravity = Gravity.CENTER_VERTICAL
                            }
                        background = metallicBackground("#214159", "#0A131B", "#D9F2FF", 999)
                        addView(
                            FrameLayout(this@MainActivity).apply {
                                layoutParams =
                                    FrameLayout.LayoutParams(dp(166), dp(166), Gravity.CENTER)
                                background = metallicBackground("#FF9A30", "#1A0C00", "#E7FBFF", 999)
                                addView(
                                    LinearLayout(this@MainActivity).apply {
                                        orientation = LinearLayout.VERTICAL
                                        gravity = Gravity.CENTER
                                        layoutParams =
                                            FrameLayout.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                            )
                                        addView(
                                            bodyText("TOTAL").apply {
                                                gravity = Gravity.CENTER
                                                setTextColor(Color.parseColor("#FFF0BF"))
                                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                                            },
                                        )
                                        addView(
                                            titleText(report.totalHits.toString(), 40f).apply {
                                                gravity = Gravity.CENTER
                                                setTextColor(Color.parseColor("#FFFFFF"))
                                                setPadding(0, dp(4), 0, 0)
                                            },
                                        )
                                        addView(
                                            bodyText(tr("hits")).apply {
                                                gravity = Gravity.CENTER
                                                setTextColor(Color.parseColor("#FFF0C9"))
                                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    }
                heroRow.addView(leftColumn)
                heroRow.addView(scoreOrb)
                addView(heroRow)
                val statusRow =
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.START
                        layoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply { topMargin = dp(18) }
                    }
                statusRow.addView(
                    badgeText(
                        text = currentTierShareLabel(),
                        textColor = "#FFF8E8",
                        fillColor = "#16384A",
                    ).apply {
                        setPadding(dp(12), dp(6), dp(12), dp(6))
                    },
                )
                statusRow.addView(
                    badgeText(
                        text =
                            localText(
                                "最佳爆发 ${report.bestBurstCount}",
                                "Burst ${report.bestBurstCount}",
                                "Rafale ${report.bestBurstCount}",
                                "ระเบิดพลัง ${report.bestBurstCount}",
                            ),
                        textColor = "#140800",
                        fillColor = "#F0B94B",
                    ).apply {
                        (layoutParams as? LinearLayout.LayoutParams)?.leftMargin = dp(10)
                        setPadding(dp(12), dp(6), dp(12), dp(6))
                    },
                )
                addView(statusRow)
            }.apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(8) }
            },
        )
        val metricsRow1 =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(18) }
            }
        metricsRow1.addView(
            posterMetricCard(
                tr("average_frequency"),
                "${String.format(Locale.US, "%.2f", report.averageFrequency)} ${tr("hits_per_second")}",
                "#4FB6FF",
            ).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(10) }
            },
        )
        metricsRow1.addView(
            posterMetricCard(tr("best_burst"), "${report.bestBurstCount} ${tr("hits")}", "#F0B94B").apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        root.addView(metricsRow1)
        val metricsRow2 =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(10) }
            }
        metricsRow2.addView(
            posterMetricCard(tr("burst_start"), "${String.format(Locale.US, "%.1f", report.bestBurstStartSec)}s", "#FFB347").apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(10) }
            },
        )
        metricsRow2.addView(
            posterMetricCard(tr("tier"), currentTierShareLabel(), "#D8B76A").apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        root.addView(metricsRow2)
        root.addView(
            posterIdentityCard(
                nickname = cloudProfile?.nickname.orEmpty().ifBlank { "Fighter" },
                subline =
                    localText(
                        "继续冲击更高段位",
                        "Keep climbing to the next tier",
                        "Continuez vers le rang supérieur",
                        "ไต่ระดับต่อไป",
                    ),
                accentColor = "#244458",
            ).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(22) }
            },
        )
        return renderPosterBitmap(root)
    }

    private fun buildAchievementsPosterBitmap(): Bitmap {
        val recent = cloudAchievements.filter { it.unlocked }.sortedByDescending { it.unlockedAt.orEmpty() }.take(1).firstOrNull()
        val nextLocked = cloudAchievements.filterNot { it.unlocked }.sortedBy { it.sortOrder }.firstOrNull()
        val badgeName = recent?.let { achievementDisplayName(it.key) } ?: currentTierShareLabel()
        val badgeCode = recent?.let { achievementBadgeCode(it.key) } ?: "TIER"
        val unlockedCount = cloudAchievements.count { it.unlocked }
        val accentColor = recent?.let { achievementAccentColor(it.key) } ?: "#D8B76A"
        val root = posterRoot(accentColor, "#224357")
        root.addView(
            TextView(this).apply {
                text = localText("新徽章解锁", "NEW HONOR", "NOUVEL HONNEUR", "เกียรติยศใหม่")
                setTextColor(Color.parseColor("#140800"))
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = metallicBackground("#FFE8A8", accentColor, "#FFF5D8", 999)
                setPadding(dp(14), dp(7), dp(14), dp(7))
            },
        )
        root.addView(
            posterSectionCard(accentColor = accentColor, fillColor = "#0F1820", strokeColor = accentColor).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        layoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply { topMargin = dp(4) }
                        val ribbonRow =
                            LinearLayout(this@MainActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER
                            }
                        ribbonRow.addView(
                            View(this@MainActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(dp(40), dp(92)).apply { rightMargin = dp(14) }
                                background = metallicBackground("#466A89", "#1A2F40", "#8FD8FF", 16)
                            },
                        )
                        ribbonRow.addView(
                            FrameLayout(this@MainActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(dp(188), dp(188))
                                background = metallicBackground("#2A3A49", "#0B1318", accentColor, 999)
                                addView(
                                    FrameLayout(this@MainActivity).apply {
                                        layoutParams =
                                            FrameLayout.LayoutParams(dp(152), dp(152), Gravity.CENTER)
                                        background = metallicBackground("#FFE8A8", accentColor, "#FFF5D8", 999)
                                        addView(
                                            LinearLayout(this@MainActivity).apply {
                                                orientation = LinearLayout.VERTICAL
                                                gravity = Gravity.CENTER
                                                layoutParams =
                                                    FrameLayout.LayoutParams(
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                    )
                                                addView(
                                                    bodyText("BADGE").apply {
                                                        gravity = Gravity.CENTER
                                                        setTextColor(Color.parseColor("#7A5A1F"))
                                                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                                                    },
                                                )
                                                addView(
                                                    titleText(badgeCode, 30f).apply {
                                                        gravity = Gravity.CENTER
                                                        setTextColor(Color.parseColor("#4A3510"))
                                                        setPadding(0, dp(6), 0, 0)
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                        ribbonRow.addView(
                            View(this@MainActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(dp(40), dp(92)).apply { leftMargin = dp(14) }
                                background = metallicBackground("#466A89", "#1A2F40", "#8FD8FF", 16)
                            },
                        )
                        addView(ribbonRow)
                        addView(
                            badgeText(
                                text = localText("荣耀珍藏", "HONOR VAULT", "COFFRE D'HONNEUR", "คลังเกียรติยศ"),
                                textColor = "#140800",
                                fillColor = "#D8B76A",
                            ).apply {
                                (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(16)
                                setPadding(dp(12), dp(6), dp(12), dp(6))
                            },
                        )
                    },
                )
                addView(
                    titleText(badgeName, 28f).apply {
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#FFF8E7"))
                        setPadding(0, dp(18), 0, 0)
                    },
                )
                addView(
                    bodyText(
                        localText(
                            "当前段位：${currentTierShareLabel()}",
                            "Current tier: ${currentTierShareLabel()}",
                            "Rang actuel : ${currentTierShareLabel()}",
                            "ระดับปัจจุบัน: ${currentTierShareLabel()}",
                        ),
                    ).apply {
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#F2D8A7"))
                        setPadding(0, dp(10), 0, 0)
                    },
                )
                addView(
                    bodyText(
                        localText(
                            "已解锁徽章：$unlockedCount / ${cloudAchievements.size}",
                            "Unlocked badges: $unlockedCount / ${cloudAchievements.size}",
                            "Badges debloques : $unlockedCount / ${cloudAchievements.size}",
                            "เหรียญที่ปลดล็อก: $unlockedCount / ${cloudAchievements.size}",
                        ),
                    ).apply {
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#FFD88A"))
                        setPadding(0, dp(8), 0, 0)
                    },
                )
                if (nextLocked != null) {
                    addView(
                        detailCard(fillColor = "#12202B", strokeColor = "#38546B", cornerDp = 18).apply {
                            setPadding(dp(16), dp(14), dp(16), dp(14))
                            layoutParams =
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                ).apply { topMargin = dp(16) }
                            addView(
                                bodyText(localText("下一枚目标", "NEXT TARGET", "PROCHAIN OBJECTIF", "เป้าหมายถัดไป")).apply {
                                    setTextColor(Color.parseColor("#B88A54"))
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                                },
                            )
                            addView(
                                titleText(achievementDisplayName(nextLocked.key), 18f).apply {
                                    gravity = Gravity.START
                                    setTextColor(Color.parseColor("#FFF8E8"))
                                    setPadding(0, dp(8), 0, 0)
                                },
                            )
                            addView(
                                bodyText("${nextLocked.progress}/${nextLocked.goal}").apply {
                                    setTextColor(Color.parseColor("#FFD88A"))
                                    setPadding(0, dp(8), 0, 0)
                                },
                            )
                        },
                    )
                }
            }.apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(10) }
            },
        )
        root.addView(
            posterIdentityCard(
                nickname = cloudProfile?.nickname.orEmpty().ifBlank { "Fighter" },
                subline = localText(
                    "每一次训练都在积累成长",
                    "Every session adds to your growth",
                    "Chaque seance renforce vos progres",
                    "ทุกครั้งที่ฝึกคือการเติบโต",
                ),
                accentColor = accentColor,
            ).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(20) }
            },
        )
        return renderPosterBitmap(root)
    }

    private fun buildLeaderboardPosterBitmap(): Bitmap {
        val me = leaderboardResult?.me
        val topThree = leaderboardResult?.top?.take(3).orEmpty()
        val accentColor = leaderboardAccentColor(leaderboardBoard)
        val root = posterRoot(accentColor, leaderboardAccentFill(leaderboardBoard))
        root.addView(
            TextView(this).apply {
                text = leaderboardBoardLabel(leaderboardBoard)
                setTextColor(Color.parseColor("#140800"))
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = metallicBackground("#BCEEFF", accentColor, "#FFF0C9", 999)
                setPadding(dp(14), dp(7), dp(14), dp(7))
            },
        )
        root.addView(
            posterSectionCard(accentColor = accentColor, fillColor = "#0E1821", strokeColor = accentColor).apply {
                val heroRow =
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                val rankBlock =
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams =
                            LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1f,
                            ).apply { rightMargin = dp(14) }
                    }
                rankBlock.addView(
                    bodyText(localText("当前排名", "CURRENT RANK", "RANG ACTUEL", "อันดับปัจจุบัน")).apply {
                        setTextColor(Color.parseColor("#B88A54"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    },
                )
                rankBlock.addView(
                    titleText(me?.let { "NO.${it.rank}" } ?: "NO.--", 42f).apply {
                        gravity = Gravity.START
                        setTextColor(Color.parseColor("#FFF8E8"))
                        setPadding(0, dp(10), 0, 0)
                    },
                )
                rankBlock.addView(
                    bodyText(
                        me?.let { leaderboardPrimaryValueText(it) }
                            ?: localText("准备冲榜", "Ready to climb", "Prêt à grimper", "พร้อมไต่อันดับ"),
                    ).apply {
                        setTextColor(Color.parseColor("#FFF0BF"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        setPadding(0, dp(8), 0, 0)
                    },
                )
                rankBlock.addView(
                    bodyText(currentTierShareLabel()).apply {
                        setTextColor(Color.parseColor("#D8B76A"))
                        setPadding(0, dp(10), 0, 0)
                    },
                )
                val trophySeal =
                    FrameLayout(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(174), dp(174))
                        background = metallicBackground("#284455", "#0D1821", accentColor, 999)
                        addView(
                            LinearLayout(this@MainActivity).apply {
                                orientation = LinearLayout.VERTICAL
                                gravity = Gravity.CENTER
                                layoutParams =
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                addView(
                                    bodyText("RANK").apply {
                                        gravity = Gravity.CENTER
                                        setTextColor(Color.parseColor("#FFD88A"))
                                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                                    },
                                )
                                addView(
                                    titleText(me?.rank?.toString() ?: "--", 38f).apply {
                                        gravity = Gravity.CENTER
                                        setTextColor(Color.parseColor("#FFFFFF"))
                                        setPadding(0, dp(6), 0, 0)
                                    },
                                )
                                addView(
                                    bodyText(leaderboardBoardLabel(leaderboardBoard)).apply {
                                        gravity = Gravity.CENTER
                                        setTextColor(Color.parseColor("#F2D8A7"))
                                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                                    },
                                )
                            },
                        )
                    }
                heroRow.addView(rankBlock)
                heroRow.addView(trophySeal)
                addView(heroRow)
            }.apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(10) }
            },
        )
        if (topThree.isNotEmpty()) {
            val podiumRow =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.BOTTOM
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dp(18) }
                }
            topThree.forEachIndexed { index, entry ->
                val podiumAccent =
                    when (entry.rank) {
                        1 -> "#F2C14E"
                        2 -> "#F2D8A7"
                        else -> "#D39A6A"
                    }
                podiumRow.addView(
                    detailCard(fillColor = "#0D1924", strokeColor = podiumAccent, cornerDp = 22).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1f,
                            ).apply { if (index > 0) leftMargin = dp(10) }
                        background =
                            metallicBackground(
                                when (entry.rank) {
                                    1 -> "#4A3A16"
                                    2 -> "#35414B"
                                    else -> "#4B3428"
                                },
                                "#0D1924",
                                podiumAccent,
                                22,
                            )
                        minimumHeight =
                            when (entry.rank) {
                                1 -> dp(226)
                                2 -> dp(192)
                                else -> dp(178)
                            }
                        gravity = Gravity.CENTER_HORIZONTAL
                        addView(
                            badgeText("TOP ${entry.rank}", textColor = "#140800", fillColor = podiumAccent).apply {
                                setPadding(dp(12), dp(6), dp(12), dp(6))
                            },
                        )
                        addView(
                            titleText(entry.nickname, if (entry.rank == 1) 22f else 18f).apply {
                                gravity = Gravity.CENTER
                                setPadding(0, dp(16), 0, 0)
                                setTextColor(Color.parseColor("#FFF8E8"))
                            },
                        )
                        addView(
                            badgeText(tierLabelForKey(entry.tierKey), textColor = "#FFF5E6", fillColor = "#17384B").apply {
                                (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(8)
                                setPadding(dp(10), dp(5), dp(10), dp(5))
                            },
                        )
                        addView(
                            titleText(entry.bestHits.toString(), if (entry.rank == 1) 32f else 26f).apply {
                                gravity = Gravity.CENTER
                                setTextColor(Color.parseColor(podiumAccent))
                                setPadding(0, dp(14), 0, 0)
                            },
                        )
                        addView(
                            bodyText(leaderboardBoardLabel(leaderboardBoard)).apply {
                                gravity = Gravity.CENTER
                                setTextColor(Color.parseColor("#CAA26A"))
                                setPadding(0, dp(4), 0, 0)
                            },
                        )
                        addView(
                            bodyText(leaderboardSecondaryValueText(entry)).apply {
                                gravity = Gravity.CENTER
                                setTextColor(Color.parseColor("#B88A54"))
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                                setPadding(0, dp(10), 0, 0)
                            },
                        )
                    },
                )
            }
            root.addView(podiumRow)
        }
        root.addView(
            posterIdentityCard(
                nickname = cloudProfile?.nickname.orEmpty().ifBlank { "Fighter" },
                subline = localText(
                    "来挑战我的成绩",
                    "Come challenge my score",
                    "Venez defier mon score",
                    "มาท้าทายคะแนนของฉัน",
                ),
                accentColor = accentColor,
            ).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(20) }
            },
        )
        return renderPosterBitmap(root)
    }

    private fun shareTrainingSummary() {
        val report = latestReport
        val shareText =
            if (report != null) {
                localText(
                    "我刚完成一轮 ${displayModeLabel(report.mode)} 训练，击打 ${report.totalHits} 次，平均 ${String.format(Locale.US, "%.2f", report.averageFrequency)} 次/秒。当前段位：${currentTierShareLabel()}。",
                    "I just finished a ${displayModeLabel(report.mode)} session with ${report.totalHits} hits at ${String.format(Locale.US, "%.2f", report.averageFrequency)} hits/s. Current tier: ${currentTierShareLabel()}.",
                    "Je viens de terminer une seance ${displayModeLabel(report.mode)} avec ${report.totalHits} coups a ${String.format(Locale.US, "%.2f", report.averageFrequency)} coups/s. Rang actuel : ${currentTierShareLabel()}.",
                    "ฉันเพิ่งฝึก ${displayModeLabel(report.mode)} เสร็จ ทำได้ ${report.totalHits} ครั้ง เฉลี่ย ${String.format(Locale.US, "%.2f", report.averageFrequency)} ครั้ง/วินาที ระดับปัจจุบัน: ${currentTierShareLabel()}",
                )
            } else {
                localText(
                    "我的 BoxingFitness 训练已经开始，欢迎来挑战我的成绩。",
                    "My BoxingFitness training is on. Come challenge my score.",
                    "Mon entrainement BoxingFitness a commence. Venez defier mon score.",
                    "ฉันเริ่มฝึก BoxingFitness แล้ว มาท้าทายคะแนนของฉันได้เลย",
                )
            }
        if (report == null) {
            shareTextPlain(shareText, shareTrainingLabel())
            return
        }
        runCatching {
            sharePosterBitmap(
                bitmap = buildTrainingPosterBitmap(report),
                filePrefix = "training_report",
                chooserTitle = shareTrainingLabel(),
                shareText = shareText,
            )
        }.getOrElse {
            shareTextPlain(shareText, shareTrainingLabel())
        }
        markTrainingSharedForDailyTask()
    }

    private fun buildWorkoutResultsShareText(dashboard: WorkoutResultsDashboard): String {
        fun line(
            zhLabel: String,
            enLabel: String,
            frLabel: String,
            thLabel: String,
            totals: WorkoutMetricTotals,
        ): String =
            localText(
                "$zhLabel：拳击数 ${totals.hits} 次 · 训练时长 ${formatWorkoutDurationMinutes(totals.durationSeconds)} 分钟 · 消耗卡路里 ${String.format(Locale.US, "%.1f", totals.calories)} kcal · 燃脂量 ${String.format(Locale.US, "%.1f", totals.fatBurnGrams)} g",
                "$enLabel: Punch Count ${totals.hits} · Training Time ${formatWorkoutDurationMinutes(totals.durationSeconds)} min · Calories Burned ${String.format(Locale.US, "%.1f", totals.calories)} kcal · Fat Burn ${String.format(Locale.US, "%.1f", totals.fatBurnGrams)} g",
                "$frLabel : Coups ${totals.hits} · Temps d'entrainement ${formatWorkoutDurationMinutes(totals.durationSeconds)} min · Calories brulees ${String.format(Locale.US, "%.1f", totals.calories)} kcal · Graisse brulee ${String.format(Locale.US, "%.1f", totals.fatBurnGrams)} g",
                "$thLabel: จำนวนหมัด ${totals.hits} · เวลาฝึก ${formatWorkoutDurationMinutes(totals.durationSeconds)} นาที · แคลอรีที่เผาผลาญ ${String.format(Locale.US, "%.1f", totals.calories)} kcal · ไขมันที่เผาผลาญ ${String.format(Locale.US, "%.1f", totals.fatBurnGrams)} g",
            )

        return buildString {
            append(localText("BoxingFitness 锻炼成果", "BoxingFitness Workout Results", "Resultats BoxingFitness", "ผลการฝึก BoxingFitness"))
            append('\n')
            append(line("当日", "Today", "Aujourd'hui", "วันนี้", dashboard.today))
            append('\n')
            append(line("本周", "Week", "Semaine", "สัปดาห์นี้", dashboard.week))
            append('\n')
            append(line("本月", "Month", "Mois", "เดือนนี้", dashboard.month))
            append('\n')
            append(line("本年", "Year", "Annee", "ปีนี้", dashboard.year))
            append('\n')
            append(line("注册至今", "Since registration", "Depuis l'inscription", "ตั้งแต่สมัครใช้งาน", dashboard.sinceRegistered))
        }
    }

    private fun shareAchievementsSummary() {
        val dashboard = buildWorkoutResultsDashboard()
        val shareText =
            buildWorkoutResultsShareText(dashboard)
        shareTextPlain(shareText, shareAchievementsLabel())
    }

    private fun shareLeaderboardSummary() {
        val me = leaderboardResult?.me
        val shareText =
            if (me != null) {
                localText(
                    "我当前在 ${leaderboardBoardLabel(leaderboardBoard)} 中排名 ${me.rank}，成绩 ${leaderboardPrimaryValueText(me)}。来挑战我的成绩。",
                    "I am ranked ${me.rank} on the ${leaderboardBoardLabel(leaderboardBoard)} with ${leaderboardPrimaryValueText(me)}. Come challenge my score.",
                    "Je suis classe ${me.rank} dans ${leaderboardBoardLabel(leaderboardBoard)} avec ${leaderboardPrimaryValueText(me)}. Venez defier mon score.",
                    "ฉันอยู่อันดับ ${me.rank} ใน ${leaderboardBoardLabel(leaderboardBoard)} ด้วยผลงาน ${leaderboardPrimaryValueText(me)} มาท้าทายคะแนนของฉัน",
                )
            } else {
                localText(
                    "我正在冲击 ${leaderboardBoardLabel(leaderboardBoard)}，欢迎来挑战。",
                    "I am climbing the ${leaderboardBoardLabel(leaderboardBoard)}. Come challenge me.",
                    "Je progresse dans ${leaderboardBoardLabel(leaderboardBoard)}. Venez me defier.",
                    "ฉันกำลังไต่อันดับ ${leaderboardBoardLabel(leaderboardBoard)} มาท้าทายกันได้เลย",
                )
            }
        runCatching {
            sharePosterBitmap(
                bitmap = buildLeaderboardPosterBitmap(),
                filePrefix = "leaderboard_rank",
                chooserTitle = shareLeaderboardLabel(),
                shareText = shareText,
            )
        }.getOrElse {
            shareTextPlain(shareText, shareLeaderboardLabel())
        }
    }

    private fun formatSensitivityValue(value: Int): String = "${value.coerceIn(0, 100)} / 100"

    private fun sensitivityToPunchThreshold(value: Int): Int = 6000 - value.coerceIn(0, 100) * 40

    private fun punchThresholdStatusText(
        value: Int,
        fromDevice: Boolean,
    ): String {
        val level = value.coerceIn(0, 100)
        val source =
            if (fromDevice) {
                localText("设备返回", "Device returned", "Retour appareil", "อุปกรณ์ส่งกลับ")
            } else {
                localText("当前设置", "Current setting", "Réglage actuel", "ค่าปัจจุบัน")
            }
        return localText(
            "$source：灵敏度 $level",
            "$source: sensitivity $level",
            "$source : sensibilité $level",
            "$source: ความไว $level",
        )
    }

    private fun writePunchThresholdSensitivityToDevice(level: Int) {
        val normalized = level.coerceIn(0, 100)
        val sent = boxingBleManager?.writePunchThresholdSensitivity(normalized) == true
        sensitivityDeviceStatusView?.text =
            if (sent) {
                localText(
                    "已随保存写入设备：灵敏度 $normalized",
                    "Saved to device: sensitivity $normalized",
                    "Enregistré sur l'appareil : sensibilité $normalized",
                    "บันทึกลงอุปกรณ์แล้ว: ความไว $normalized",
                )
            } else {
                val message =
                    localText(
                        "蓝牙未连接，灵敏度已保存到本地，暂未写入设备",
                        "Bluetooth is not connected. Sensitivity was saved locally only.",
                        "Bluetooth non connecté. La sensibilité est enregistrée localement seulement.",
                        "ยังไม่ได้เชื่อมต่อบลูทูธ บันทึกค่าความไวไว้ในเครื่องเท่านั้น",
                    )
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                message
            }
    }

    private fun displayCountdownStatus(value: Int): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "${value} 秒后开始..."
            AppLanguage.English -> "Starting in $value..."
            AppLanguage.French -> "Depart dans $value..."
            AppLanguage.Thai -> "เริ่มในอีก $value..."
        }

    private fun displayGoCue(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "开始"
            AppLanguage.English -> "Go"
            AppLanguage.French -> "Go"
            AppLanguage.Thai -> "เริ่ม"
        }

    private fun displayGoLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "开始"
            AppLanguage.English -> "GO"
            AppLanguage.French -> "GO"
            AppLanguage.Thai -> "เริ่ม!"
        }

    private fun displayModeLabel(mode: TrainingMode): String =
        when (selectedLanguage) {
            AppLanguage.Chinese ->
                when (mode) {
                    TrainingMode.Seconds30 -> "30 秒"
                    TrainingMode.Seconds60 -> "60 秒"
                    TrainingMode.Burst10 -> "10 秒爆发"
                    TrainingMode.Burst15 -> "极速燃脂"
                }
            AppLanguage.English ->
                when (mode) {
                    TrainingMode.Seconds30 -> "30 sec"
                    TrainingMode.Seconds60 -> "60 sec"
                    TrainingMode.Burst10 -> "10 sec burst"
                    TrainingMode.Burst15 -> "Rapid Fat Burn"
                }
            AppLanguage.French ->
                when (mode) {
                    TrainingMode.Seconds30 -> "30 s"
                    TrainingMode.Seconds60 -> "60 s"
                    TrainingMode.Burst10 -> "Explosif 10 s"
                    TrainingMode.Burst15 -> "Brule-graisse express"
                }
            AppLanguage.Thai ->
                when (mode) {
                    TrainingMode.Seconds30 -> "30 วินาที"
                    TrainingMode.Seconds60 -> "60 วินาที"
                    TrainingMode.Burst10 -> "เร่งสปีด 10 วิ"
                    TrainingMode.Burst15 -> "เผาผลาญเร่งด่วน"
                }
        }

    private fun displayRemaining(remainingMillis: Long): String {
        val seconds = remainingMillis.coerceAtLeast(0L) / 100L / 10.0f
        return when (selectedLanguage) {
            AppLanguage.Chinese -> String.format(Locale.US, "剩余 %.1f 秒", seconds)
            AppLanguage.English -> String.format(Locale.US, "%.1fs left", seconds)
            AppLanguage.French -> String.format(Locale.US, "%.1fs restantes", seconds)
            AppLanguage.Thai -> String.format(Locale.US, "เหลือ %.1f วินาที", seconds)
        }
    }

    private fun loadSettings() {
        selectedLanguage = AppLanguage.fromStorage(prefs.getString(KEY_LANGUAGE, defaultLanguage().storageValue))
        sensitivityLevel = prefs.getInt(KEY_SENSITIVITY, DEFAULT_SENSITIVITY).coerceIn(0, 100)
        if (!prefs.getBoolean(KEY_SENSITIVITY_DEFAULT_MIGRATED, false) && sensitivityLevel == LEGACY_DEFAULT_SENSITIVITY) {
            sensitivityLevel = DEFAULT_SENSITIVITY
            prefs.edit()
                .putInt(KEY_SENSITIVITY, sensitivityLevel)
                .putBoolean(KEY_SENSITIVITY_DEFAULT_MIGRATED, true)
                .apply()
        } else if (!prefs.getBoolean(KEY_SENSITIVITY_DEFAULT_MIGRATED, false)) {
            prefs.edit().putBoolean(KEY_SENSITIVITY_DEFAULT_MIGRATED, true).apply()
        }
        selectedPlayMode =
            runCatching {
                TrainingPlayMode.valueOf(prefs.getString(KEY_SELECTED_PLAY_MODE, TrainingPlayMode.Classic30.name).orEmpty())
            }.getOrDefault(TrainingPlayMode.Classic30)
        selectedMode = modeForPlayMode(selectedPlayMode)
    }

    private fun saveSettings() {
        prefs.edit()
            .putString(KEY_LANGUAGE, selectedLanguage.storageValue)
            .putInt(KEY_SENSITIVITY, sensitivityLevel)
            .apply()
    }

    private fun initTextToSpeech() {
        tts =
            TextToSpeech(applicationContext) { status ->
                val speaker = tts
                ttsInitialized = true
                if (status != TextToSpeech.SUCCESS || speaker == null) {
                    ttsReady = false
                    ttsLocaleInUse = null
                } else {
                    ttsReady = true
                    updateTtsLanguage()
                    speaker.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit

                            override fun onDone(utteranceId: String?) {
                                completeTtsCue(utteranceId)
                            }

                            @Deprecated("Deprecated in Android framework")
                            override fun onError(utteranceId: String?) {
                                completeTtsCue(utteranceId)
                            }

                            override fun onError(utteranceId: String?, errorCode: Int) {
                                completeTtsCue(utteranceId)
                            }
                        },
                    )
                    speaker.setSpeechRate(1.0f)
                }
            }
    }

    private fun preferredTtsLocales(): LinkedHashSet<Locale> =
        when (selectedLanguage) {
            AppLanguage.Chinese ->
                linkedSetOf(
                    Locale.CHINA,
                    Locale.SIMPLIFIED_CHINESE,
                    Locale.CHINESE,
                    Locale.US,
                    Locale.ENGLISH,
                )

            AppLanguage.English ->
                linkedSetOf(
                    Locale.US,
                    Locale.UK,
                    Locale.ENGLISH,
                )

            AppLanguage.French ->
                linkedSetOf(
                    Locale.FRANCE,
                    Locale.FRENCH,
                    Locale.CANADA_FRENCH,
                    Locale.US,
                    Locale.ENGLISH,
                )

            AppLanguage.Thai ->
                linkedSetOf(
                    Locale("th", "TH"),
                    Locale("th"),
                    Locale.US,
                    Locale.ENGLISH,
                )
        }

    private fun selectedSpeechLanguageCode(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> Locale.CHINESE.language
            AppLanguage.English -> Locale.ENGLISH.language
            AppLanguage.French -> Locale.FRENCH.language
            AppLanguage.Thai -> "th"
        }

    private fun usesEnglishSpeechFallback(): Boolean {
        val currentLanguage = ttsLocaleInUse?.language ?: return false
        return selectedLanguage != AppLanguage.English &&
            !currentLanguage.equals(selectedSpeechLanguageCode(), ignoreCase = true) &&
            currentLanguage.equals(Locale.ENGLISH.language, ignoreCase = true)
    }

    private fun updateTtsLanguage() {
        if (!ttsInitialized) {
            return
        }
        val speaker = tts ?: return
        val preferredLocale = preferredTtsLocales().first()
        val cached = ttsLocaleInUse
        if (cached != null && cached.language == preferredLocale.language) {
            // Already serving the right language family; skip the slow setLanguage round-trip.
            return
        }
        val candidates = preferredTtsLocales()
        var appliedLocale: Locale? = null
        candidates.forEach { locale ->
            if (appliedLocale != null) {
                return@forEach
            }
            val result = speaker.setLanguage(locale)
            if (result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                appliedLocale = locale
            }
        }
        ttsLocaleInUse = appliedLocale
        ttsReady = appliedLocale != null
    }

    private fun speakCue(text: String, onDone: (() -> Unit)? = null) {
        if (!ttsReady) {
            onDone?.invoke()
            return
        }
        val cueText = spokenCueText(text)
        if (cueText.isBlank()) {
            onDone?.invoke()
            return
        }
        val utteranceId = "cue-${UUID.randomUUID()}"
        if (onDone != null) {
            ttsCompletionCallbacks[utteranceId] = onDone
        }
        val result = tts?.speak(cueText, TextToSpeech.QUEUE_FLUSH, null, utteranceId) ?: TextToSpeech.ERROR
        if (result == TextToSpeech.ERROR) {
            ttsCompletionCallbacks.remove(utteranceId)
            onDone?.invoke()
        }
    }

    private fun completeTtsCue(utteranceId: String?) {
        if (utteranceId.isNullOrBlank()) {
            return
        }
        ttsCompletionCallbacks.remove(utteranceId)?.invoke()
    }

    private fun spokenCueText(text: String): String =
        when {
            usesEnglishSpeechFallback() ->
                when (text) {
                    displayGoCue(), displayGoLabel(), "开始", "GO", "Go" -> "Go"
                    else -> text
                }

            selectedLanguage == AppLanguage.French ->
                when (text) {
                    displayGoCue(), displayGoLabel() -> "Partez"
                    else -> text
                }

            selectedLanguage == AppLanguage.Thai ->
                when (text) {
                    displayGoCue(), displayGoLabel() -> "Go"
                    else -> text
                }

            else ->
                when (text) {
                    displayGoCue(), displayGoLabel() -> if (selectedLanguage == AppLanguage.Chinese) "开始" else "Go"
                    else -> text
                }
        }

    private var countPulseAnimator: AnimatorSet? = null

    private fun pulseCount() {
        countPulseAnimator?.cancel()
        countView.scaleX = 1.0f
        countView.scaleY = 1.0f
        val growX = ObjectAnimator.ofFloat(countView, View.SCALE_X, 1.0f, 1.12f)
        val growY = ObjectAnimator.ofFloat(countView, View.SCALE_Y, 1.0f, 1.12f)
        val shrinkX = ObjectAnimator.ofFloat(countView, View.SCALE_X, 1.12f, 1.0f)
        val shrinkY = ObjectAnimator.ofFloat(countView, View.SCALE_Y, 1.12f, 1.0f)
        countPulseAnimator = AnimatorSet().apply {
            play(growX).with(growY)
            play(shrinkX).with(shrinkY).after(growX)
            duration = 110L
            start()
        }
    }

    /*
    private fun tr(key: String): String =
        when (selectedLanguage) {
            AppLanguage.Chinese ->
                when (key) {
                    "settings_subtitle" -> "语言和灵敏度会保存在当前设备，下次打开 APP 时自动生效。"
                    "language_helper" -> "界面和训练提示会按这里设置的语言显示。"
                    "language_chinese" -> "简体中文"
                    "language_english" -> "English"
                    "sensitivity_stable" -> "高手"
                    "sensitivity_balanced" -> "标准"
                    "sensitivity_sensitive" -> "新手"
                    "restore_default" -> "恢复默认"
                    "activation_success_ready" -> "激活成功，本机已完成验证，现在可以开始训练。"
                    "activation_verified_status" -> "设备已验证，可直接开始训练。"
                    "activation_ready_title" -> "设备已激活"
                    "activation_ready_subtitle" -> "当前设备已通过认证。你可以直接开始训练，也可以在设置里调整语言和灵敏度。"
                    "activation_serial_label" -> "序列号"
                    "activation_status_label" -> "状态"
                    "activation_status_verified" -> "已验证"
                    "activation_last_check_label" -> "最近校验"
                    "activation_just_now" -> "刚刚"
                    "title" -> "BoxingFitness"
                    "subtitle" -> "训练前会先做背景声校正，再进行 3-2-1-Go 倒计时并实时计数。"
                    "mode" -> "模式"
                    "latest_report" -> "最新报告"
                    "no_report" -> "暂无训练报告。"
                    "start" -> "开始"
                    "stop" -> "停止"
                    "settings" -> "设置"
                    "permission_required" -> "需要蓝牙设备权限。"
                    "ready" -> "准备开始训练。"
                    "keep_quiet" -> "请保持安静"
                    "analyzing_mic" -> "正在分析手机蓝牙设备..."
                    "adapting_noise" -> "正在适应当前环境噪声..."
                    "calibrating_keep_quiet" -> "正在校准背景声，请保持安静。"
                    "calibration_fail_unstable" -> "环境噪声波动过大，校准不合格，请在安静环境下重试。"
                    "calibration_fail_retry" -> "稳定背景声音样本不足，请保持安静后重新校准。"
                    "training_complete" -> "训练完成。"
                    "training_stopped" -> "训练已停止。"
                    "training_live" -> "正在训练"
                    "training_failed" -> "训练失败。"
                    "prepare_short" -> "安静"
                    "calibration_short" -> "校准"
                    "done_short" -> "完成"
                    "error_short" -> "错误"
                    "retry_short" -> "重试"
                    "retry_calibration" -> "重新校准"
                    "save" -> "保存"
                    "cancel" -> "取消"
                    "language" -> "APP 语言"
                    "sensitivity" -> "灵敏度"
                    "sensitivity_helper" -> "范围 0-100，数值越大，灵敏度越高。"
                    "total_hits" -> "总击打次数"
                    "average_frequency" -> "平均频率"
                    "hits_per_second" -> "次/秒"
                    "best_burst" -> "最佳 3 秒爆发"
                    "burst_start" -> "爆发起点"
                    "hits" -> "次"
                    "activation_title" -> "设备激活"
                    "activation_subtitle" -> "请输入包装内的 11 位序列号和 8 位激活码。"
                    "activation_hint" -> "完成激活后，才能开始训练。"
                    "activation_required" -> "当前 APP 尚未激活"
                    "activation_loading" -> "正在验证序列号和激活码..."
                    "activation_success" -> "激活成功，现在可以开始使用。"
                    "activation_failed" -> "激活失败，请稍后重试。"
                    "activation_serial_not_found" -> "账号数据暂不可用，请稍后重试。"
                    "activation_invalid_code" -> "激活码错误，请重新输入。"
                    "activation_already_bound" -> "该产品序列号已在另一台设备激活。"
                    "activation_not_activated" -> "当前设备尚未完成激活。"
                    "activation_network_error" -> "无法连接认证服务器，请检查网络后重试。"
                    "serial_hint" -> "请输入 11 位序列号"
                    "code_hint" -> "请输入 8 位激活码"
                    "serial_invalid" -> "请输入正确的 11 位序列号。"
                    "code_invalid" -> "请输入正确的 8 位激活码。"
                    "activate" -> "立即激活"
                    "lock_short" -> "激活"
                    else -> key
                }

            AppLanguage.English ->
                when (key) {
                    "settings_subtitle" -> "Language and sensitivity are saved on this device and applied automatically next time."
                    "language_helper" -> "UI text and training prompts follow this language setting."
                    "language_chinese" -> "Simplified Chinese"
                    "language_english" -> "English"
                    "sensitivity_stable" -> "Stable"
                    "sensitivity_balanced" -> "Balanced"
                    "sensitivity_sensitive" -> "Sensitive"
                    "restore_default" -> "Restore Default"
                    "activation_success_ready" -> "Activation complete. This device is verified and ready to train."
                    "activation_verified_status" -> "This device is verified and ready."
                    "activation_ready_title" -> "Device Verified"
                    "activation_ready_subtitle" -> "This device has already been activated. You can start training right away or fine-tune the settings below."
                    "activation_serial_label" -> "Serial"
                    "activation_status_label" -> "Status"
                    "activation_status_verified" -> "Verified"
                    "activation_last_check_label" -> "Last check"
                    "activation_just_now" -> "Just now"
                    "title" -> "BoxingFitness"
                    "subtitle" -> "The app calibrates background sound first, then speaks 3-2-1-Go and counts in real time."
                    "mode" -> "Mode"
                    "latest_report" -> "Latest Report"
                    "no_report" -> "No training session yet."
                    "start" -> "Start"
                    "stop" -> "Stop"
                    "settings" -> "Settings"
                    "permission_required" -> "Bluetooth connection is required."
                    "ready" -> "Ready to train."
                    "keep_quiet" -> "Please keep quiet."
                    "analyzing_mic" -> "Analyzing this phone mic..."
                    "adapting_noise" -> "Adapting to current phone noise..."
                    "calibrating_keep_quiet" -> "Calibrating background sound. Please keep quiet."
                    "calibration_fail_unstable" -> "Noise changed too much during calibration. Please retry in a quieter environment."
                    "calibration_fail_retry" -> "Not enough stable background sound was collected. Please retry."
                    "training_complete" -> "Training complete."
                    "training_stopped" -> "Training stopped."
                    "training_live" -> "Training live"
                    "training_failed" -> "Training failed."
                    "prepare_short" -> "QUIET"
                    "calibration_short" -> "CAL"
                    "done_short" -> "DONE"
                    "error_short" -> "ERR"
                    "retry_short" -> "RETRY"
                    "retry_calibration" -> "Retry Calibration"
                    "save" -> "Save"
                    "cancel" -> "Cancel"
                    "language" -> "App Language"
                    "sensitivity" -> "Sensitivity"
                    "sensitivity_helper" -> "Range 0-100. Higher values mean higher sensitivity."
                    "total_hits" -> "Total hits"
                    "average_frequency" -> "Average frequency"
                    "hits_per_second" -> "hits/s"
                    "best_burst" -> "Best 3-second burst"
                    "burst_start" -> "Burst start"
                    "hits" -> "hits"
                    "activation_title" -> "Device Activation"
                    "activation_subtitle" -> "Enter the 11-digit serial number and 8-digit activation code from the package."
                    "activation_hint" -> "Activate this app before starting training."
                    "activation_required" -> "This app is not activated yet."
                    "activation_loading" -> "Verifying serial number and activation code..."
                    "activation_success" -> "Activation successful. You can start training now."
                    "activation_failed" -> "Activation failed. Please try again."
                    "activation_serial_not_found" -> "Account data is unavailable. Please try again later."
                    "activation_invalid_code" -> "Activation code is invalid."
                    "activation_already_bound" -> "This serial number is already activated on another device."
                    "activation_not_activated" -> "This device is not activated."
                    "activation_network_error" -> "Unable to reach the activation server. Please check your network."
                    "serial_hint" -> "Enter 11-digit serial number"
                    "code_hint" -> "Enter 8-digit activation code"
                    "serial_invalid" -> "Please enter a valid 11-digit serial number."
                    "code_invalid" -> "Please enter a valid 8-digit activation code."
                    "activate" -> "Activate"
                    "lock_short" -> "LOCK"
                    else -> key
                }
        }

    */

    private fun tr(key: String): String = UiStrings.get(selectedLanguage, key)

    private fun languageDisplayName(language: AppLanguage): String =
        when (language) {
            AppLanguage.Chinese -> tr("language_chinese")
            AppLanguage.English -> tr("language_english")
            AppLanguage.French -> tr("language_french")
            AppLanguage.Thai -> tr("language_thai")
        }

    private fun countdownStatus(value: Int): String = displayCountdownStatus(value)

    private fun goCue(): String = displayGoCue()

    private fun goLabel(): String = displayGoLabel()

    private fun modeLabel(mode: TrainingMode): String = displayModeLabel(mode)

    private fun formatRemaining(remainingMillis: Long): String = displayRemaining(remainingMillis)

    private fun defaultLanguage(): AppLanguage =
        when (Locale.getDefault().language.lowercase(Locale.US)) {
            "zh" -> AppLanguage.Chinese
            "fr" -> AppLanguage.French
            "th" -> AppLanguage.Thai
            else -> AppLanguage.English
        }

    private fun profileSubtitleText(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "账号状态、语言与训练概览"
            AppLanguage.English -> "Account status, language, and training overview"
            AppLanguage.French -> "Statut du compte, langue et resume d'entrainement"
            AppLanguage.Thai -> "สถานะบัญชี ภาษา และภาพรวมการฝึก"
        }

    private fun historySubtitleText(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "最近训练结果会自动同步到云端"
            AppLanguage.English -> "Recent sessions are synced to the cloud automatically"
            AppLanguage.French -> "Les dernieres seances sont synchronisees automatiquement"
            AppLanguage.Thai -> "ผลการฝึกล่าสุดจะซิงก์ไปยังคลาวด์โดยอัตโนมัติ"
        }

    private fun leaderboardSubtitleText(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "查看单日最高拳击数与历史累计训练排名"
            AppLanguage.English -> "See daily-best and lifetime training rankings"
            AppLanguage.French -> "Consultez les classements quotidiens et cumulés"
            AppLanguage.Thai -> "ดูอันดับสถิติรายวันและอันดับสะสมทั้งหมด"
        }

    private fun avatarChooseButtonLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "选择图片"
            AppLanguage.English -> "Choose Photo"
            AppLanguage.French -> "Choisir une photo"
            AppLanguage.Thai -> "เลือกรูปภาพ"
        }

    private fun avatarClearButtonLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "移除图片"
            AppLanguage.English -> "Remove Photo"
            AppLanguage.French -> "Supprimer la photo"
            AppLanguage.Thai -> "ลบรูปภาพ"
        }

    private fun avatarImageHintText(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "可从手机相册选择头像图片，未选择时使用颜色头像。"
            AppLanguage.English -> "Choose an avatar photo from this phone, or keep the color avatar."
            AppLanguage.French -> "Choisissez une photo sur ce telephone ou gardez l'avatar colore."
            AppLanguage.Thai -> "เลือกรูปโปรไฟล์จากโทรศัพท์ หรือใช้รูปโปรไฟล์สีเดิมก็ได้"
        }

    private fun developerInfoButtonLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "联系我们"
            AppLanguage.English -> "Contact Us"
            AppLanguage.French -> "Nous contacter"
            AppLanguage.Thai -> "ติดต่อเรา"
        }

    private fun developerInfoPageTitle(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "关于我们"
            AppLanguage.English -> "About Us"
            AppLanguage.French -> "A propos"
            AppLanguage.Thai -> "เกี่ยวกับเรา"
        }

    private fun developerInfoPageSubtitle(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "查看开发者信息、联系邮箱、用户协议与隐私政策"
            AppLanguage.English -> "Developer details, contact email and policy links"
            AppLanguage.French -> "Informations developpeur, e-mail et acces aux politiques"
            AppLanguage.Thai -> "ข้อมูลผู้พัฒนา อีเมลติดต่อ และลิงก์นโยบาย"
        }

    private fun developerCompanySectionTitle(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "公司信息"
            AppLanguage.English -> "Company"
            AppLanguage.French -> "Entreprise"
            AppLanguage.Thai -> "ข้อมูลบริษัท"
        }

    private fun developerContactSectionTitle(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "联系方式"
            AppLanguage.English -> "Contact"
            AppLanguage.French -> "Contact"
            AppLanguage.Thai -> "ช่องทางติดต่อ"
        }

    private fun developerExtrasSectionTitle(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "附加信息"
            AppLanguage.English -> "More Info"
            AppLanguage.French -> "Informations"
            AppLanguage.Thai -> "ข้อมูลเพิ่มเติม"
        }

    private fun developerCompanyName(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> DEVELOPER_COMPANY_NAME_ZH
            AppLanguage.English -> DEVELOPER_COMPANY_NAME_EN
            AppLanguage.French -> DEVELOPER_COMPANY_NAME_FR
            AppLanguage.Thai -> DEVELOPER_COMPANY_NAME_TH
        }

    private fun developerCompanyDescription(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "专注于智能拳击产品与运动数据体验"
            AppLanguage.English -> "Focused on BoxingFitness products and sports data experiences"
            AppLanguage.French -> "Specialisee dans les produits de boxe intelligents et l'experience des donnees sportives"
            AppLanguage.Thai -> "มุ่งเน้นผลิตภัณฑ์มวยอัจฉริยะและประสบการณ์ข้อมูลการออกกำลังกาย"
        }

    private fun developerEmailLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "联系邮箱"
            AppLanguage.English -> "Email"
            AppLanguage.French -> "E-mail"
            AppLanguage.Thai -> "อีเมล"
        }

    private fun developerEmailActionLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "发送邮件"
            AppLanguage.English -> "Send Email"
            AppLanguage.French -> "Envoyer un e-mail"
            AppLanguage.Thai -> "ส่งอีเมล"
        }

    private fun developerVersionLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "当前 APP 版本号"
            AppLanguage.English -> "App Version"
            AppLanguage.French -> "Version de l'application"
            AppLanguage.Thai -> "เวอร์ชันแอป"
        }

    private fun privacyPolicyEntryLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "隐私政策"
            AppLanguage.English -> "Privacy Policy"
            AppLanguage.French -> "Politique de confidentialite"
            AppLanguage.Thai -> "นโยบายความเป็นส่วนตัว"
        }

    private fun userAgreementEntryLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "用户协议"
            AppLanguage.English -> "User Agreement"
            AppLanguage.French -> "Accord utilisateur"
            AppLanguage.Thai -> "ข้อตกลงผู้ใช้"
        }

    private fun developerDocumentHint(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "可查看本应用当前版本对应的隐私政策与用户协议。"
            AppLanguage.English -> "Review the privacy policy and user agreement for the current app version."
            AppLanguage.French -> "Consultez la politique de confidentialite et l'accord utilisateur de cette version."
            AppLanguage.Thai -> "ดูนโยบายความเป็นส่วนตัวและข้อตกลงผู้ใช้ของแอปเวอร์ชันปัจจุบัน"
        }

    private fun developerPrivacyPolicyAssetFile(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "privacy_policy_zh.txt"
            AppLanguage.English -> "privacy_policy_en.txt"
            AppLanguage.French -> "privacy_policy_fr.txt"
            AppLanguage.Thai -> "privacy_policy_th.txt"
        }

    private fun developerUserAgreementAssetFile(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "user_agreement_zh.txt"
            AppLanguage.English -> "user_agreement_en.txt"
            AppLanguage.French -> "user_agreement_fr.txt"
            AppLanguage.Thai -> "user_agreement_th.txt"
        }

    private fun developerDocumentUnavailableText(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "当前文档暂不可用，请稍后重试。"
            AppLanguage.English -> "This document is currently unavailable. Please try again later."
            AppLanguage.French -> "Ce document n'est pas disponible pour le moment."
            AppLanguage.Thai -> "เอกสารนี้ยังไม่พร้อมใช้งานในขณะนี้ โปรดลองอีกครั้งภายหลัง"
        }

    private fun closeLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "关闭"
            AppLanguage.English -> "Close"
            AppLanguage.French -> "Fermer"
            AppLanguage.Thai -> "ปิด"
        }

    private fun achievementsTitleText(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "成就徽章"
            AppLanguage.English -> "Achievements"
            AppLanguage.French -> "Succès"
            AppLanguage.Thai -> "ความสำเร็จ"
        }

    private fun achievementsSectionHint(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "持续训练，逐步解锁更高层级的徽章。"
            AppLanguage.English -> "Keep training to unlock higher-tier badges."
            AppLanguage.French -> "Continuez pour débloquer des badges de niveau supérieur."
            AppLanguage.Thai -> "ฝึกต่อเนื่องเพื่อปลดล็อกเหรียญระดับที่สูงขึ้น"
        }

    private fun profilePageSubtitle(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "查看账号资料、云端同步状态、训练统计与项目文档。"
            AppLanguage.English -> "Review your account, tier, and training performance."
            AppLanguage.French -> "Consultez votre compte, votre rang et vos statistiques d'entraînement."
            AppLanguage.Thai -> "ดูบัญชี ระดับ และสถิติการฝึกของคุณ"
        }

    private fun historySectionSubtitle(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "每次训练结束后，战报会自动保存在这里。"
            AppLanguage.English -> "Each completed session is saved here automatically."
            AppLanguage.French -> "Chaque séance terminée est enregistrée ici automatiquement."
            AppLanguage.Thai -> "ทุกครั้งที่ฝึกเสร็จ ระบบจะบันทึกผลไว้ที่นี่โดยอัตโนมัติ"
        }

    private fun achievementsSubtitleText(unlockedCount: Int, totalCount: Int): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "已解锁 $unlockedCount / $totalCount 枚徽章"
            AppLanguage.English -> "Unlocked $unlockedCount / $totalCount badges"
            AppLanguage.French -> "$unlockedCount / $totalCount badges débloqués"
            AppLanguage.Thai -> "ปลดล็อกแล้ว $unlockedCount / $totalCount เหรียญ"
        }

    private fun profileBestScoreLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "最佳成绩"
            AppLanguage.English -> "Best Score"
            AppLanguage.French -> "Meilleur score"
            AppLanguage.Thai -> "ผลงานดีที่สุด"
        }

    private fun streakLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "连续训练"
            AppLanguage.English -> "Streak"
            AppLanguage.French -> "Série"
            AppLanguage.Thai -> "ฝึกต่อเนื่อง"
        }

    private fun activeDaysLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "活跃天数"
            AppLanguage.English -> "Active Days"
            AppLanguage.French -> "Jours actifs"
            AppLanguage.Thai -> "วันที่แอ็กทีฟ"
        }

    private fun nextTierLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "下一段位"
            AppLanguage.English -> "Next Tier"
            AppLanguage.French -> "Rang suivant"
            AppLanguage.Thai -> "ระดับถัดไป"
        }

    private fun displayAppVersion(): String {
        val parts = BuildConfig.VERSION_NAME.split('.')
        return if (parts.size >= 2) {
            "V${parts[0]}.${parts[1]}"
        } else {
            "V${BuildConfig.VERSION_NAME}"
        }
    }

    private fun openDeveloperEmail() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DEVELOPER_EMAIL"))
        intent.putExtra(Intent.EXTRA_SUBJECT, DEVELOPER_EMAIL_SUBJECT)
        try {
            startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    private fun loadAssetText(assetFile: String): String =
        try {
            assets.open(assetFile).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Throwable) {
            ""
        }

    private fun championLabel(): String =
        when (selectedLanguage) {
            AppLanguage.Chinese -> "已达最高段位"
            AppLanguage.English -> "Top tier reached"
            AppLanguage.French -> "Rang maximal atteint"
            AppLanguage.Thai -> "ถึงระดับสูงสุดแล้ว"
        }

    private fun tierLabelForLevel(level: Int): String = tierLabelForKey(tierKeyForLevel(level))

    private fun tierKeyForLevel(level: Int): String =
        when (level.coerceIn(1, 9)) {
            1 -> "beginner"
            2 -> "prospect"
            3 -> "contender"
            4 -> "striker"
            5 -> "challenger"
            6 -> "elite"
            7 -> "master"
            8 -> "legend"
            else -> "champion"
        }

    private fun tierLabelForKey(key: String?): String =
        when (key) {
            "beginner" -> localText("拳坛新丁", "New Blood", "Débutant", "นักชกหน้าใหม่")
            "prospect" -> localText("热血新秀", "Rising Rookie", "Espoir montant", "ดาวรุ่ง")
            "contender" -> localText("擂台争锋者", "Arena Contender", "Compétiteur", "ผู้ท้าชิง")
            "striker" -> localText("铁拳出击手", "Iron Fist Striker", "Frappeur de fer", "หมัดเหล็ก")
            "challenger" -> localText("风暴挑战者", "Storm Challenger", "Défi tempête", "ผู้ท้าทายพายุ")
            "elite" -> localText("荣耀精英", "Glory Elite", "Élite glorieuse", "ยอดฝีมือ")
            "master" -> localText("宗师", "Grand Master", "Grand maître", "ปรมาจารย์")
            "legend" -> localText("不朽传奇", "Immortal Legend", "Légende", "ตำนาน")
            "champion" -> localText("至尊拳王", "Supreme Champion", "Champion suprême", "แชมป์สูงสุด")
            else -> localText("拳坛新丁", "New Blood", "Débutant", "นักชกหน้าใหม่")
        }

    private fun achievementDisplayName(key: String): String =
        when (key) {
            "first_training" -> localText("初次登台", "Debut", "Début", "เปิดตัว")
            "sessions_5" -> localText("持续热身", "Warmup Run", "Échauffement", "วอร์มต่อเนื่อง")
            "sessions_15" -> localText("训练常客", "Regular Fighter", "Habitué", "นักชกประจำ")
            "sessions_30" -> localText("擂台老兵", "Ring Veteran", "Vétéran", "นักชกเก๋า")
            "hits_100" -> localText("百拳试锋", "100-Hit Trial", "100 coups", "ทดลอง 100 หมัด")
            "hits_500" -> localText("五百重击", "500 Heavy Hits", "500 coups", "500 หมัดหนัก")
            "hits_1000" -> localText("千拳风暴", "1K Punch Storm", "Tempête 1K", "พายุพันหมัด")
            "hits_5000" -> localText("万击宗匠", "5K Master", "Maître 5K", "มาสเตอร์ 5K")
            "best_30_40" -> localText("30 秒 40 击", "30s - 40 Hits", "30 s - 40 coups", "30 วิ - 40 ครั้ง")
            "best_30_60" -> localText("30 秒 60 击", "30s - 60 Hits", "30 s - 60 coups", "30 วิ - 60 ครั้ง")
            "best_30_80" -> localText("30 秒 80 击", "30s - 80 Hits", "30 s - 80 coups", "30 วิ - 80 ครั้ง")
            "best_30_100" -> localText("30 秒 100 击", "30s - 100 Hits", "30 s - 100 coups", "30 วิ - 100 ครั้ง")
            "best_60_90" -> localText("60 秒 90 击", "60s - 90 Hits", "60 s - 90 coups", "60 วิ - 90 ครั้ง")
            "best_60_120" -> localText("60 秒 120 击", "60s - 120 Hits", "60 s - 120 coups", "60 วิ - 120 ครั้ง")
            "best_60_150" -> localText("60 秒 150 击", "60s - 150 Hits", "60 s - 150 coups", "60 วิ - 150 ครั้ง")
            "best_60_180" -> localText("60 秒 180 击", "60s - 180 Hits", "60 s - 180 coups", "60 วิ - 180 ครั้ง")
            "burst_6" -> localText("爆发新星", "Burst Rookie", "Explosif débutant", "ดาวรุ่งระเบิดพลัง")
            "burst_10" -> localText("爆发高手", "Burst Expert", "Expert explosif", "ผู้เชี่ยวชาญระเบิดพลัง")
            "burst_12" -> localText("爆发大师", "Burst Master", "Maître explosif", "มาสเตอร์ระเบิดพลัง")
            "burst_15" -> localText("爆发之王", "Burst King", "Roi explosif", "ราชาระเบิดพลัง")
            "streak_3" -> localText("连练 3 天", "3-Day Streak", "3 jours de suite", "ต่อเนื่อง 3 วัน")
            "streak_7" -> localText("连练 7 天", "7-Day Streak", "7 jours de suite", "ต่อเนื่อง 7 วัน")
            "streak_14" -> localText("连练 14 天", "14-Day Streak", "14 jours de suite", "ต่อเนื่อง 14 วัน")
            "streak_30" -> localText("连练 30 天", "30-Day Streak", "30 jours de suite", "ต่อเนื่อง 30 วัน")
            else -> key
        }

    private fun achievementBadgeCode(key: String): String =
        when (key) {
            "first_training" -> "1ST"
            "sessions_5" -> "S5"
            "sessions_15" -> "S15"
            "sessions_30" -> "S30"
            "hits_100" -> "H100"
            "hits_500" -> "H500"
            "hits_1000" -> "1K"
            "hits_5000" -> "5K"
            "best_30_40" -> "30/40"
            "best_30_60" -> "30/60"
            "best_30_80" -> "30/80"
            "best_30_100" -> "30/100"
            "best_60_90" -> "60/90"
            "best_60_120" -> "60/120"
            "best_60_150" -> "60/150"
            "best_60_180" -> "60/180"
            "burst_6" -> "B6"
            "burst_10" -> "B10"
            "burst_12" -> "B12"
            "burst_15" -> "B15"

            "streak_3" -> "D3"
            "streak_7" -> "D7"
            "streak_14" -> "D14"
            "streak_30" -> "D30"
            else -> "BADGE"
        }

    private fun achievementBadgeImageRes(key: String): Int? =
        when (key) {
            "first_training" -> R.drawable.achievement_milestone_01
            "sessions_5" -> R.drawable.achievement_milestone_02
            "sessions_15" -> R.drawable.achievement_milestone_03
            "sessions_30" -> R.drawable.achievement_milestone_04
            "hits_100" -> R.drawable.achievement_hits_01
            "hits_500" -> R.drawable.achievement_hits_02
            "hits_1000" -> R.drawable.achievement_hits_03
            "hits_5000" -> R.drawable.achievement_hits_04
            "best_30_40" -> R.drawable.achievement_best30_05
            "best_30_60" -> R.drawable.achievement_best30_06
            "best_30_80" -> R.drawable.achievement_best30_07
            "best_30_100" -> R.drawable.achievement_best30_08
            "best_60_90" -> R.drawable.achievement_best60_09
            "best_60_120" -> R.drawable.achievement_best60_10
            "best_60_150" -> R.drawable.achievement_best60_11
            "best_60_180" -> R.drawable.achievement_best60_12
            "burst_6" -> R.drawable.achievement_burst_13
            "burst_10" -> R.drawable.achievement_burst_14
            "burst_12" -> R.drawable.achievement_burst_15
            "burst_15" -> R.drawable.achievement_burst_16

            "streak_3" -> R.drawable.achievement_streak_17
            "streak_7" -> R.drawable.achievement_streak_18
            "streak_14" -> R.drawable.achievement_streak_19
            "streak_30" -> R.drawable.achievement_streak_20
            else -> null
        }

    private fun achievementAccentColor(key: String): String =
        when {
            key.startsWith("best_30") -> "#FF9A30"
            key.startsWith("best_60") -> "#FFD060"
            key.startsWith("hits_") -> "#FFB347"
            key.startsWith("burst_") -> "#FF8A65"
            key.startsWith("streak_") -> "#C084FC"
            else -> "#FFB347"
        }

    private data class MetallicPalette(
        val highlight: String,
        val base: String,
        val stroke: String,
        val text: String,
    )

    private data class ModeButtonPalette(
        val accent: String,
        val activeHighlight: String,
        val activeBase: String,
        val activeStroke: String,
        val inactiveHighlight: String,
        val inactiveBase: String,
        val inactiveStroke: String,
        val activeText: String,
        val inactiveText: String,
    )

    private fun metallicBackground(
        highlight: String,
        base: String,
        stroke: String,
        cornerDp: Int = 18,
    ): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor(highlight),
                Color.parseColor(base),
            ),
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerDp).toFloat()
            setStroke(dp(1), Color.parseColor(stroke))
        }

    private fun achievementMetalPalette(
        key: String,
        unlocked: Boolean,
    ): MetallicPalette =
        when {
            key.startsWith("best_30") ->
                if (unlocked) MetallicPalette("#FFE7A2", "#C8942C", "#FBE2A0", "#FFF8E6") else MetallicPalette("#32404B", "#1A232A", "#465B69", "#B88A54")
            key.startsWith("best_60") ->
                if (unlocked) MetallicPalette("#FFD3A2", "#B86F2E", "#F1C18B", "#FFF2E6") else MetallicPalette("#383B43", "#1D2026", "#4B5361", "#B88A54")
            key.startsWith("hits_") ->
                if (unlocked) MetallicPalette("#A6F3E5", "#23927C", "#CAFCEF", "#EFFFFB") else MetallicPalette("#2E4044", "#162328", "#425761", "#B88A54")
            key.startsWith("burst_") ->
                if (unlocked) MetallicPalette("#FFD1BE", "#B7653E", "#F8B999", "#FFF3EC") else MetallicPalette("#413631", "#211A19", "#5B4A44", "#B88A54")
            key.startsWith("streak_") ->
                if (unlocked) MetallicPalette("#E0D0FF", "#6D4BC7", "#CDBBFF", "#F7F2FF") else MetallicPalette("#383645", "#1E1E27", "#55556A", "#B88A54")
            else ->
                if (unlocked) MetallicPalette("#D2F4F0", "#2C8476", "#C7F9F2", "#F2FFFD") else MetallicPalette("#324049", "#1A2329", "#465963", "#B88A54")
        }

    private fun achievementBadgeCard(item: CloudAchievementItem): LinearLayout {
        val unlocked = item.unlocked
        val accentColor = achievementAccentColor(item.key)
        val fillColor = if (unlocked) "#11242F" else "#0C1822"
        val strokeColor = if (unlocked) accentColor else "#233A4B"
        val progressFraction = if (item.goal > 0) item.progress.toFloat() / item.goal.toFloat() else 0f
        return detailCard(fillColor = fillColor, strokeColor = strokeColor, cornerDp = 18).apply {
            val codeView =
                TextView(this@MainActivity).apply {
                    text = achievementBadgeCode(item.key)
                    gravity = Gravity.CENTER
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(if (unlocked) Color.parseColor("#FFF8E8") else Color.parseColor("#B88A54"))
                    background = roundedBackground(if (unlocked) accentColor else "#12222E", strokeColor, 999)
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                }
            val titleView =
                bodyText(achievementDisplayName(item.key)).apply {
                    setTextColor(Color.parseColor("#FFF5E6"))
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setPadding(0, dp(10), 0, 0)
                }
            val progressView =
                bodyText("${item.progress}/${item.goal}").apply {
                    setTextColor(if (unlocked) Color.parseColor(accentColor) else Color.parseColor("#C9A46A"))
                    setPadding(0, dp(6), 0, 0)
                }
            val progressBar =
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    minimumHeight = dp(7)
                    background = roundedBackground("#10212E", "#1B3446", 999)
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(7),
                        ).apply {
                            topMargin = dp(10)
                        }
                    val safeProgress = progressFraction.coerceIn(0f, 1f)
                    if (safeProgress > 0f) {
                        addView(
                            View(this@MainActivity).apply {
                                background = roundedBackground(accentColor, accentColor, 999)
                                layoutParams =
                                    LinearLayout.LayoutParams(
                                        0,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        safeProgress,
                                    )
                            },
                        )
                    }
                    if (safeProgress < 1f) {
                        addView(
                            View(this@MainActivity).apply {
                                layoutParams =
                                    LinearLayout.LayoutParams(
                                        0,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        (1f - safeProgress).coerceAtLeast(0.0001f),
                                    )
                            },
                        )
                    }
                }
            addView(codeView)
            addView(titleView)
            addView(progressBar)
            addView(progressView)
        }
    }

    private fun shouldCelebrateTier(
        tier: CloudTierProgress?,
        promotedHint: Boolean,
        previousLevel: Int,
    ): Boolean {
        if (tier == null || previousLevel <= 0) {
            return false
        }
        return (promotedHint || tier.level > previousLevel) && tier.level > previousLevel
    }

    private fun syncSeenTier(tier: CloudTierProgress?) {
        if (tier == null) {
            return
        }
        val previousLevel = prefs.getInt(KEY_LAST_SEEN_TIER, 0)
        if (tier.level != previousLevel) {
            prefs.edit().putInt(KEY_LAST_SEEN_TIER, tier.level).apply()
        }
    }

    private fun computeNewlyUnlockedAchievements(
        previousUnlockedKeys: Set<String>,
        incoming: List<CloudAchievementItem>,
    ): List<CloudAchievementItem> =
        incoming
            .filter { it.unlocked && !previousUnlockedKeys.contains(it.key) }
            .sortedBy { it.sortOrder }

    private fun dismissCelebrationBeforeTraining() {
        dismissingCelebrationForTraining = true
        activeCelebrationDialog?.dismiss()
        activeCelebrationDialog = null
        celebrationShowing = false
        dismissingCelebrationForTraining = false
        tts?.stop()
        resetCelebrationVoice()
    }

    private fun maybeShowTrainingOutcomeCelebration(
        report: TrainingReport,
        outcome: TrainingCoachOutcome,
    ) {
        enqueueCelebration { showTrainingOutcomeDialog(report, outcome) }
    }

    private fun showTrainingOutcomeDialog(
        report: TrainingReport,
        outcome: TrainingCoachOutcome,
    ) {
        val title =
            when (outcome.playMode) {
                TrainingPlayMode.LevelChallenge ->
                    if (outcome.goalMet) {
                        localText("燃脂挑战完成", "Fat-Burn Complete", "Défi terminé", "ท้าทายสำเร็จ")
                    } else {
                        localText("燃脂挑战继续", "Keep Burning", "Continuez", "เผาผลาญต่อ")
                    }

                TrainingPlayMode.DailyChallenge ->
                    if (outcome.goalMet) {
                        localText("燃脂陪练完成", "Fat Burn Sparring Complete", "Sparring terminé", "ซ้อมเผาผลาญสำเร็จ")
                    } else {
                        localText("燃脂陪练进行中", "Fat Burn Sparring Progress", "Sparring en cours", "ซ้อมเผาผลาญต่อ")
                    }

                TrainingPlayMode.Burst10 ->
                    if (outcome.goalMet) {
                        localText("情绪拳王达成", "Emotion Champ Hit", "Objectif atteint", "ทำเป้าราชันอารมณ์สำเร็จ")
                    } else {
                        localText("情绪拳王完成", "Emotion Champ Complete", "Emotion Champ terminé", "จบราชันอารมณ์")
                    }

                TrainingPlayMode.Burst15 ->
                    if (outcome.goalMet) {
                        localText("极速燃脂达成", "Rapid Fat Burn Hit", "Objectif atteint", "ทำเป้าเผาผลาญเร่งด่วนสำเร็จ")
                    } else {
                        localText("极速燃脂完成", "Rapid Fat Burn Complete", "Brûle-graisse terminé", "จบเผาผลาญเร่งด่วน")
                    }

                TrainingPlayMode.Classic30 -> localText("自由拳击完成", "Free Boxing Complete", "Boxe libre terminée", "จบชกอิสระ")

                TrainingPlayMode.Classic60 -> localText("色彩涂鸦完成", "Color Graffiti Complete", "Graffiti terminé", "จบสีสันกราฟฟิตี")
            }
        val chips =
            buildList {
                add("XP +${outcome.xpGain}" to "#FFD060")
                add(
                    localText(
                        "连练 ${outcome.streak} 天",
                        "${outcome.streak}-day streak",
                        "${outcome.streak} jours de suite",
                        "ต่อเนื่อง ${outcome.streak} วัน",
                    ) to "#FFB347",
                )
                add(playModeLabel(outcome.playMode) to "#FF9A30")
                if (outcome.goalMet) {
                    add(localText("任务完成", "Task done", "Objectif atteint", "งานสำเร็จ") to "#E07010")
                }
            }
        showCelebrationDialog(
            accentColor = if (outcome.goalMet) "#FFD060" else "#FF9A30",
            eyebrow = if (outcome.goalMet) "VICTORY" else "GOOD WORK",
            title = title,
            body = buildCoachMessage(report, outcome),
            chips = chips,
        )
    }

    private fun maybeShowPostTrainingCelebrations(
        unlockedAchievements: List<CloudAchievementItem>,
        promotedTier: CloudTierProgress?,
    ) {
        if (unlockedAchievements.isNotEmpty()) {
            enqueueCelebration { showAchievementUnlockDialog(unlockedAchievements) }
        }
        promotedTier?.let { tier ->
            enqueueCelebration { showTierPromotionDialog(tier) }
        }
    }

    private fun enqueueCelebration(action: () -> Unit) {
        if (currentEngine != null) {
            celebrationQueue.addLast(action)
            return
        }
        if (celebrationShowing) {
            celebrationQueue.addLast(action)
        } else {
            celebrationShowing = true
            action()
        }
    }

    private fun onCelebrationDismissed() {
        activeCelebrationDialog = null
        resetCelebrationVoice()
        if (dismissingCelebrationForTraining || currentEngine != null) {
            celebrationShowing = false
            return
        }
        celebrationShowing = false
        showNextCelebrationIfIdle()
    }

    private fun showNextCelebrationIfIdle() {
        if (celebrationShowing || currentEngine != null || celebrationQueue.isEmpty()) {
            return
        }
        celebrationShowing = true
        celebrationQueue.removeFirst().invoke()
    }

    private fun showTierPromotionBanner(tier: CloudTierProgress) {
        val message =
            localText(
                "段位升级：${tierLabelForKey(tier.key)} Lv.${tier.level}",
                "Rank Up! ${tierLabelForKey(tier.key)} Lv.${tier.level}",
                "Rang supérieur ! ${tierLabelForKey(tier.key)} Lv.${tier.level}",
                "เลื่อนระดับ! ${tierLabelForKey(tier.key)} Lv.${tier.level}",
            )
        promotionBannerView.text = message
        promotionBannerView.visibility = View.VISIBLE
        promotionBannerView.alpha = 0f
        promotionBannerView.translationY = -dp(12).toFloat()
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(promotionBannerView, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(promotionBannerView, View.TRANSLATION_Y, -dp(12).toFloat(), 0f),
            )
            duration = 320L
            start()
        }
        promotionBannerView.postDelayed({
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(promotionBannerView, View.ALPHA, 1f, 0f),
                    ObjectAnimator.ofFloat(promotionBannerView, View.TRANSLATION_Y, 0f, -dp(8).toFloat()),
                )
                duration = 320L
                start()
            }
            promotionBannerView.postDelayed({ promotionBannerView.visibility = View.GONE }, 340L)
        }, 2200L)
    }

    private fun showAchievementUnlockDialog(items: List<CloudAchievementItem>) {
        val chips =
            items.take(3).map { achievementDisplayName(it.key) to achievementAccentColor(it.key) }
        val title =
            localText("新徽章解锁", "New badges unlocked", "Nouveaux badges", "ปลดล็อกเหรียญใหม่")
        val body =
            localText(
                "本次训练解锁 ${items.size} 枚徽章，继续保持节奏，冲击更高段位。",
                "You unlocked ${items.size} new badges in this session. Keep pushing for the next tier.",
                "Vous avez débloqué ${items.size} nouveaux badges. Continuez vers le rang suivant.",
                "คุณปลดล็อกเหรียญใหม่ ${items.size} เหรียญในการฝึกครั้งนี้ ไปต่อสู่ระดับถัดไป",
            )
        showCelebrationDialog(
            accentColor = "#FFB347",
            eyebrow = "NEW BADGES",
            title = title,
            body = body,
            chips = chips,
        )
    }

    private fun showTierPromotionDialog(tier: CloudTierProgress) {
        val title =
            localText(
                "段位升级：${tierLabelForKey(tier.key)}",
                "Rank Up: ${tierLabelForKey(tier.key)}",
                "Rang supérieur : ${tierLabelForKey(tier.key)}",
                "เลื่อนระดับ: ${tierLabelForKey(tier.key)}",
            )
        val body =
            localText(
                "你的综合升段表现已达到 ${tierLabelForKey(tier.key)}：30秒最佳 ${tier.bestHits} 击，累计拳击 ${tier.totalHits} 击。",
                "Your blended tier progress reached ${tierLabelForKey(tier.key)}: best 30s ${tier.bestHits}, total hits ${tier.totalHits}.",
                "Votre progression atteint ${tierLabelForKey(tier.key)} : meilleur 30 s ${tier.bestHits}, total ${tier.totalHits} coups.",
                "ความก้าวหน้าของคุณถึง ${tierLabelForKey(tier.key)}: ดีที่สุด 30 วิ ${tier.bestHits} ครั้ง รวม ${tier.totalHits} ครั้ง",
            )
        showCelebrationDialog(
            accentColor = "#FFD060",
            eyebrow = "RANK UP",
            title = title,
            body = body,
            chips = listOf("Lv.${tier.level}" to "#FFD060"),
        )
    }

    private fun showCelebrationDialog(
        accentColor: String,
        eyebrow: String,
        title: String,
        body: String,
        chips: List<Pair<String, String>>,
    ) {
        val overlay =
            FrameLayout(this).apply {
                setPadding(dp(24), dp(24), dp(24), dp(24))
                setBackgroundColor(Color.argb(155, 4, 10, 18))
            }
        val card =
            detailCard(fillColor = "#0F2130", strokeColor = accentColor, cornerDp = 26).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    )
                minimumHeight = dp(220)
            }
        card.addView(
            badgeText(
                text = eyebrow,
                textColor = "#140800",
                fillColor = accentColor,
            ),
        )
        card.addView(
            titleText(title, 24f).apply {
                setTextColor(Color.parseColor("#FFF8E8"))
                setPadding(0, dp(16), 0, 0)
            },
        )
        card.addView(
            bodyText(body).apply {
                setTextColor(Color.parseColor("#C7D6E4"))
                setPadding(0, dp(10), 0, 0)
            },
        )
        if (chips.isNotEmpty()) {
            val chipRow =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                    setPadding(0, dp(16), 0, 0)
                }
            chips.forEachIndexed { index, chip ->
                chipRow.addView(
                    badgeText(
                        text = chip.first,
                        textColor = "#FFF8E8",
                        fillColor = chip.second,
                    ).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                if (index > 0) {
                                    leftMargin = dp(8)
                                }
                            }
                    },
                )
            }
            card.addView(chipRow)
        }
        card.addView(
            bodyText(
                localText(
                    "点击任意位置继续，或 10 秒后自动关闭",
                    "Tap anywhere to continue, or it closes in 10 seconds",
                    "Touchez n'importe où pour continuer, ou fermeture dans 10 secondes",
                    "แตะที่ใดก็ได้เพื่อดำเนินการต่อ หรือปิดอัตโนมัติใน 10 วินาที",
                ),
            ).apply {
                setTextColor(Color.parseColor("#7F97AA"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(18), 0, 0)
            },
        )
        overlay.addView(card)

        val dialog =
            AlertDialog.Builder(this)
                .setView(overlay)
                .create()
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { onCelebrationDismissed() }
        overlay.setOnClickListener { dialog.dismiss() }
        dialog.show()
        activeCelebrationDialog = dialog
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        speakCelebration(celebrationVoiceText(title))

        card.alpha = 0f
        card.scaleX = 0.9f
        card.scaleY = 0.9f
        card.translationY = dp(20).toFloat()
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(card, View.SCALE_X, 0.9f, 1f),
                ObjectAnimator.ofFloat(card, View.SCALE_Y, 0.9f, 1f),
                ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, dp(20).toFloat(), 0f),
            )
            duration = 280L
            start()
        }
        overlay.postDelayed({
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }, 10_000L)
    }

    private fun celebrationVoiceText(title: String): String =
        if (usesEnglishSpeechFallback()) {
            "Amazing! $title! Keep going strong!"
        } else {
            when (selectedLanguage) {
                AppLanguage.Chinese -> "太棒了！$title！继续保持，向更强进发！"
                AppLanguage.English -> "Amazing! $title! Keep going strong!"
                AppLanguage.French -> "Magnifique ! $title ! Continuez comme ca !"
                AppLanguage.Thai -> "ยอดเยี่ยมมาก! $title! สู้ต่อไปนะ!"
            }
        }

    private fun speakCelebration(text: String) {
        val speaker = tts ?: return
        if (!ttsReady || text.isBlank()) {
            return
        }
        runCatching {
            speaker.setPitch(1.35f)
            speaker.setSpeechRate(1.08f)
            speaker.speak(spokenCueText(text), TextToSpeech.QUEUE_ADD, null, "celebration-${UUID.randomUUID()}")
            promotionBannerView.postDelayed({
                resetCelebrationVoice()
            }, 2_800L)
        }
    }

    private fun resetCelebrationVoice() {
        runCatching {
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(1.0f)
        }
    }

    private fun rankLabel(rank: Int): String =
        when (rank) {
            1 -> "#1"
            2 -> "#2"
            3 -> "#3"
            else -> "#$rank"
        }

    private fun buildPodiumEntries(topThree: List<CloudLeaderboardEntry>): List<CloudLeaderboardEntry> =
        when (topThree.size) {
            0 -> emptyList()
            1 -> topThree
            2 -> listOf(topThree[0], topThree[1])
            else -> listOf(topThree[1], topThree[0], topThree[2])
        }

    private fun podiumAccentForRank(rank: Int): String =
        when (rank) {
            1 -> "#FFD060"
            2 -> "#A9C6D8"
            3 -> "#E3A36B"
            else -> "#FFB347"
        }

    private fun leaderboardAccentColor(board: LeaderboardBoard = leaderboardBoard): String =
        when (board) {
            LeaderboardBoard.DailyBestHits -> "#FF9A30"
            LeaderboardBoard.TotalHits -> "#FFB347"
            LeaderboardBoard.TotalDuration -> "#66D6FF"
            LeaderboardBoard.TotalCalories -> "#FFD060"
            LeaderboardBoard.TotalFatBurn -> "#FF6B6B"
        }

    private fun leaderboardAccentFill(board: LeaderboardBoard = leaderboardBoard): String =
        when (board) {
            LeaderboardBoard.DailyBestHits -> "#11283A"
            LeaderboardBoard.TotalHits -> "#112B2C"
            LeaderboardBoard.TotalDuration -> "#102A34"
            LeaderboardBoard.TotalCalories -> "#2A2414"
            LeaderboardBoard.TotalFatBurn -> "#32171B"
        }

    private fun sanitizeAvatarColor(colorHex: String?): String {
        val normalized = colorHex?.trim().orEmpty()
        return if (normalized.matches(Regex("^#[0-9A-Fa-f]{6}$"))) normalized.uppercase(Locale.US) else "#CC4400"
    }

    private fun podiumLocalCardPremium(
        entry: LocalLeaderboardEntry,
        accentColor: String,
        elevated: Boolean,
        leftMargin: Int,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply { this.leftMargin = leftMargin }
            addView(
                detailCard(fillColor = "#0D1822", strokeColor = accentColor, cornerDp = 24).apply {
                    minimumHeight = if (elevated) dp(238) else dp(204)
                    gravity = Gravity.CENTER_HORIZONTAL
                    background =
                        metallicBackground(
                            if (elevated) "#2A2414" else "#162733",
                            "#0D1822",
                            accentColor,
                            24,
                        )
                    setPadding(dp(16), dp(18), dp(16), dp(18))
                    addView(
                        badgeText(rankLabel(entry.rank), textColor = "#140800", fillColor = accentColor).apply {
                            setPadding(dp(12), dp(6), dp(12), dp(6))
                        },
                    )
                    addView(
                        titleText(entry.title, if (elevated) 20f else 18f).apply {
                            gravity = Gravity.CENTER
                            setPadding(0, dp(14), 0, 0)
                            setTextColor(Color.parseColor("#FFF8E8"))
                        },
                    )
                    addView(
                        badgeText(entry.badge, textColor = "#F7E8C8", fillColor = "#17384B").apply {
                            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(8)
                            setPadding(dp(10), dp(5), dp(10), dp(5))
                        },
                    )
                    if (entry.tertiaryBadge.isNotBlank()) {
                        addView(
                            bodyText(entry.tertiaryBadge).apply {
                                gravity = Gravity.CENTER
                                setTextColor(Color.parseColor("#D6C4A0"))
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                                setPadding(0, dp(10), 0, 0)
                            },
                        )
                    }
                    addView(
                        titleText(entry.primaryValue, if (elevated) 24f else 20f).apply {
                            gravity = Gravity.CENTER
                            setTextColor(Color.parseColor(accentColor))
                            setPadding(0, dp(14), 0, 0)
                        },
                    )
                    addView(
                        bodyText(entry.secondaryValue).apply {
                            gravity = Gravity.CENTER
                            setTextColor(Color.parseColor("#CAA26A"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                            setPadding(0, dp(8), 0, 0)
                        },
                    )
                },
            )
        }

    private fun leaderboardRowLocalCardPremium(entry: LocalLeaderboardEntry): LinearLayout {
        val accentColor = leaderboardAccentColor(leaderboardBoard)
        val card = detailCard(fillColor = "#0C1822", strokeColor = "#27485B", cornerDp = 20)
        card.background = metallicBackground("#162733", "#0B1720", "#244458", 20)
        val row =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        val accentBar =
            View(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(dp(4), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                        rightMargin = dp(14)
                    }
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(999).toFloat()
                        setColor(Color.parseColor(accentColor))
                    }
            }
        val rankView =
            bodyText(rankLabel(entry.rank)).apply {
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.parseColor(accentColor))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        rightMargin = dp(14)
                    }
            }
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
            }
        content.addView(
            titleText(entry.title, 18f).apply {
                gravity = Gravity.START
                setTextColor(Color.parseColor("#FFF8E8"))
            },
        )
        val badgeRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
            }
        badgeRow.addView(
            badgeText(entry.badge, textColor = "#F2D8A7", fillColor = leaderboardAccentFill(leaderboardBoard)).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            },
        )
        if (entry.tertiaryBadge.isNotBlank()) {
            badgeRow.addView(
                badgeText(entry.tertiaryBadge, textColor = "#DCEFFF", fillColor = "#132635").apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    (layoutParams as? LinearLayout.LayoutParams)?.leftMargin = dp(8)
                },
            )
        }
        content.addView(badgeRow)
        content.addView(
            bodyText(entry.primaryValue).apply {
                setTextColor(Color.parseColor(accentColor))
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setPadding(0, dp(8), 0, 0)
            },
        )
        content.addView(
            bodyText(entry.secondaryValue).apply {
                setTextColor(Color.parseColor("#B88A54"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(4), 0, 0)
            },
        )
        row.addView(accentBar)
        row.addView(rankView)
        row.addView(content)
        card.addView(row)
        return card
    }

    private fun avatarBackground(colorHex: String): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(sanitizeAvatarColor(colorHex)))
            setStroke(dp(2), Color.parseColor("#FFF5E6"))
        }

    private fun heroBackground(primaryColor: String): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor(primaryColor),
                Color.parseColor("#173446"),
                Color.parseColor("#0D1E2A"),
            ),
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), Color.parseColor("#336780"))
        }

    private fun detailCard(
        fillColor: String = "#0C1822",
        strokeColor: String = "#1C3344",
        cornerDp: Int = 18,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(cornerDp).toFloat()
                    setColor(Color.parseColor(fillColor))
                    setStroke(dp(1), Color.parseColor(strokeColor))
                }
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
        }

    private fun badgeText(
        text: String,
        textColor: String = "#FFF5E6",
        fillColor: String = "#16384A",
    ): TextView =
        bodyText(text).apply {
            setTextColor(Color.parseColor(textColor))
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = roundedBackground(fillColor, fillColor, 999)
        }

    private fun roundedBackground(
        fillColor: String,
        strokeColor: String = fillColor,
        cornerDp: Int = 14,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerDp).toFloat()
            setColor(Color.parseColor(fillColor))
            setStroke(dp(1), Color.parseColor(strokeColor))
        }

    private fun historySessionCard(item: CloudTrainingHistoryItem): LinearLayout {
        val card = detailCard(fillColor = "#0B1721", strokeColor = "#20384A")
        val header =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        val modeChip = badgeText(displayModeLabel(secondsToMode(item.modeSeconds)), fillColor = "#17354A")
        val hitsChip = badgeText("${item.totalHits} ${tr("hits")}", fillColor = "#E07010")
        val headerSpacer =
            View(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        1,
                        1.0f,
                    )
            }
        header.addView(modeChip)
        header.addView(headerSpacer)
        header.addView(hitsChip)

        val titleLine =
            bodyText(formatHistoryTime(item.endedAt ?: item.startedAt)).apply {
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.parseColor("#FFF8E8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(0, dp(12), 0, 0)
            }
        val metricsRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                setPadding(0, dp(12), 0, 0)
            }
        val avgChip =
            badgeText(
                text = String.format(Locale.US, "%.2f %s", item.averageFrequency, tr("hits_per_second")),
                textColor = "#F2D8A7",
                fillColor = "#123246",
            )
        val burstChip =
            badgeText(
                text = "${tr("best_burst")}: ${item.bestBurstCount}",
                textColor = "#FFD060",
                fillColor = "#2B2412",
            ).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        leftMargin = dp(10)
                    }
            }
        metricsRow.addView(avgChip)
        metricsRow.addView(burstChip)
        val detailLine =
            bodyText(
                "${tr("burst_start")}: ${String.format(Locale.US, "%.1f", item.bestBurstStartSec)}s",
            ).apply {
                setTextColor(Color.parseColor("#F2D8A7"))
                setTextColor(Color.parseColor("#B88A54"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(8), 0, 0)
            }
        card.addView(header)
        card.addView(titleLine)
        card.addView(metricsRow)
        card.addView(detailLine)
        return card
    }

    private fun podiumCard(
        entry: CloudLeaderboardEntry,
        accentColor: String,
        elevated: Boolean,
        leftMargin: Int,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1.0f,
                ).apply {
                    this.leftMargin = leftMargin
                    topMargin = if (elevated) 0 else dp(18)
                }
            addView(
                detailCard(fillColor = "#0D1924", strokeColor = accentColor, cornerDp = 22).apply {
                    minimumHeight = if (elevated) dp(176) else dp(148)
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(
                        badgeText(
                            text = "TOP ${entry.rank}",
                            textColor = "#140800",
                            fillColor = accentColor,
                        ),
                    )
                    addView(
                        bodyText(rankLabel(entry.rank)).apply {
                            gravity = Gravity.CENTER
                            setTypeface(Typeface.DEFAULT_BOLD)
                            setTextColor(Color.parseColor(accentColor))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (elevated) 28f else 22f)
                            setPadding(0, dp(14), 0, 0)
                        },
                    )
                    addView(
                        titleText(entry.nickname, if (elevated) 20f else 18f).apply {
                            gravity = Gravity.CENTER
                            setPadding(0, dp(6), 0, 0)
                            setTextColor(Color.parseColor("#FFF8E8"))
                        },
                    )
                    addView(
                        badgeText(
                            text = tierLabelForKey(entry.tierKey),
                            textColor = "#FFF5E6",
                            fillColor = "#16384A",
                        ).apply {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                            setPadding(dp(10), dp(5), dp(10), dp(5))
                        },
                    )
                    addView(
                        badgeText(
                            text = leaderboardBoardLabel(leaderboardBoard),
                            textColor = "#F2D8A7",
                            fillColor = "#102738",
                        ).apply {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                            setPadding(dp(8), dp(4), dp(8), dp(4))
                        },
                    )
                    addView(
                        bodyText(leaderboardPrimaryValueText(entry)).apply {
                            gravity = Gravity.CENTER
                            setTypeface(Typeface.DEFAULT_BOLD)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (elevated) 20f else 18f)
                            setTextColor(Color.parseColor(accentColor))
                            setPadding(0, dp(10), 0, 0)
                        },
                    )
                    addView(
                        bodyText(leaderboardSecondaryValueText(entry)).apply {
                            gravity = Gravity.CENTER
                            setTextColor(Color.parseColor("#CAA26A"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                            setPadding(0, dp(8), 0, 0)
                        },
                    )
                },
            )
            addView(
                View(this@MainActivity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            when (entry.rank) {
                                1 -> dp(52)
                                2 -> dp(36)
                                else -> dp(28)
                            },
                        ).apply {
                            topMargin = dp(10)
                        }
                    background = roundedBackground(fillColor = accentColor, strokeColor = accentColor, cornerDp = 18)
                },
            )
        }

    private fun leaderboardRowCard(entry: CloudLeaderboardEntry): LinearLayout {
        val accentColor = leaderboardAccentColor(leaderboardBoard)
        val card = detailCard(fillColor = "#0C1822", strokeColor = "#1C3344")
        val row =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        val rankView =
            bodyText(rankLabel(entry.rank)).apply {
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.parseColor(accentColor))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        rightMargin = dp(12)
                    }
            }
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.0f,
                    )
            }
        content.addView(
            bodyText(entry.nickname).apply {
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.parseColor("#FFF5E6"))
            },
        )
        content.addView(
            badgeText(tierLabelForKey(entry.tierKey), textColor = "#F2D8A7", fillColor = leaderboardAccentFill(leaderboardBoard)).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            },
        )
        content.addView(
            bodyText(
                "${leaderboardPrimaryValueText(entry)} | ${leaderboardSecondaryValueText(entry)}",
            ).apply {
                setTextColor(Color.parseColor("#B88A54"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(4), 0, 0)
            },
        )
        val serialBadge =
            badgeText(entry.serialMasked, textColor = "#F2D8A7", fillColor = "#132635").apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            }
        row.addView(rankView)
        row.addView(content)
        row.addView(serialBadge)
        card.addView(row)
        return card
    }

    private fun achievementTierHeroCardPremium(
        tier: CloudTierProgress,
        unlockedCount: Int,
        totalCount: Int,
    ): LinearLayout =
        detailCard(fillColor = "#0F1820", strokeColor = "#D4B16B", cornerDp = 24).apply {
            background = metallicBackground("#224B63", "#0D1A23", "#D8B97A", 24)
            addView(
                TextView(this@MainActivity).apply {
                    text = localText("荣耀段位", "Honor Tier", "Rang d'honneur", "ระดับเกียรติยศ")
                    setTextColor(Color.parseColor("#140800"))
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    background = metallicBackground("#FFE8A8", "#C7932B", "#FFF2CD", 999)
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                },
            )
            addView(
                titleText(tierLabelForKey(tier.key), 24f).apply {
                    gravity = Gravity.START
                    setPadding(0, dp(14), 0, 0)
                    setTextColor(Color.parseColor("#FFF8E7"))
                },
            )
            addView(
                bodyText(achievementsSubtitleText(unlockedCount, totalCount)).apply {
                    setTextColor(Color.parseColor("#F2D8A7"))
                    setPadding(0, dp(6), 0, 0)
                },
            )
            addView(
                bodyText(tierHeroProgressText(tier)).apply {
                    setTextColor(Color.parseColor("#A7C8DD"))
                    setPadding(0, dp(10), 0, 0)
                },
            )
        }

    private fun achievementBadgeCardPremium(item: CloudAchievementItem): LinearLayout {
        val unlocked = item.unlocked
        val accentColor = achievementAccentColor(item.key)
        val palette = achievementMetalPalette(item.key, unlocked)
        val badgeImageRes = achievementBadgeImageRes(item.key)
        val progressFraction = if (item.goal > 0) item.progress.toFloat() / item.goal.toFloat() else 0f
        return detailCard(fillColor = "#0C1822", strokeColor = if (unlocked) palette.stroke else "#233A4B", cornerDp = 20).apply {
            background =
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.parseColor(if (unlocked) "#13202A" else "#0D1822"),
                        Color.parseColor(if (unlocked) "#0A1218" else "#140800"),
                    ),
                ).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), Color.parseColor(if (unlocked) palette.stroke else "#24384A"))
                }
            val topRow =
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
            val medal =
                FrameLayout(this@MainActivity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                            rightMargin = dp(12)
                        }
                    background =
                        if (badgeImageRes == null) {
                            metallicBackground(
                                if (unlocked) palette.highlight else "#243441",
                                if (unlocked) palette.base else "#121D26",
                                if (unlocked) palette.stroke else "#314755",
                                999,
                            )
                        } else {
                            roundedBackground(
                                if (unlocked) "#09131C" else "#101B24",
                                if (unlocked) palette.stroke else "#314755",
                                999,
                            )
                        }
                    setPadding(dp(2), dp(2), dp(2), dp(2))
                    elevation = dp(2).toFloat()
                    if (badgeImageRes != null) {
                        addView(
                            ImageView(this@MainActivity).apply {
                                setImageResource(badgeImageRes)
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                alpha = if (unlocked) 1f else 0.42f
                                contentDescription = achievementDisplayName(item.key)
                                outlineProvider =
                                    object : ViewOutlineProvider() {
                                        override fun getOutline(view: View, outline: Outline) {
                                            outline.setOval(0, 0, view.width, view.height)
                                        }
                                    }
                                clipToOutline = true
                                layoutParams =
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                            },
                        )
                    } else {
                        addView(
                            TextView(this@MainActivity).apply {
                                text = achievementBadgeCode(item.key)
                                gravity = Gravity.CENTER
                                setTypeface(Typeface.DEFAULT_BOLD)
                                setTextColor(Color.parseColor(if (unlocked) palette.text else "#B88A54"))
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                                layoutParams =
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                            },
                        )
                    }
                }
            val titleColumn =
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1.0f,
                        )
                }
            titleColumn.addView(
                bodyText(achievementDisplayName(item.key)).apply {
                    setTextColor(Color.parseColor("#FFF5E6"))
                    setTypeface(Typeface.DEFAULT_BOLD)
                },
            )
            titleColumn.addView(
                bodyText(
                    if (unlocked) {
                        localText("已解锁", "Unlocked", "Débloqué", "ปลดล็อกแล้ว")
                    } else {
                        localText("成长中", "In Progress", "En cours", "กำลังพัฒนา")
                    },
                ).apply {
                    setTextColor(Color.parseColor(if (unlocked) palette.stroke else "#B88A54"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, dp(4), 0, 0)
                },
            )
            topRow.addView(medal)
            topRow.addView(titleColumn)

            val progressBar =
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    minimumHeight = dp(8)
                    background = roundedBackground("#0F1C27", "#1B3446", 999)
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(8),
                        ).apply {
                            topMargin = dp(12)
                        }
                    val safeProgress = progressFraction.coerceIn(0f, 1f)
                    if (safeProgress > 0f) {
                        addView(
                            View(this@MainActivity).apply {
                                background =
                                    metallicBackground(
                                        if (unlocked) palette.highlight else accentColor,
                                        if (unlocked) palette.base else "#203545",
                                        if (unlocked) palette.stroke else accentColor,
                                        999,
                                    )
                                layoutParams =
                                    LinearLayout.LayoutParams(
                                        0,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        safeProgress,
                                    )
                            },
                        )
                    }
                    if (safeProgress < 1f) {
                        addView(
                            View(this@MainActivity).apply {
                                layoutParams =
                                    LinearLayout.LayoutParams(
                                        0,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        (1f - safeProgress).coerceAtLeast(0.0001f),
                                    )
                            },
                        )
                    }
                }
            addView(topRow)
            addView(progressBar)
            addView(
                bodyText("${item.progress}/${item.goal}").apply {
                    setTextColor(if (unlocked) Color.parseColor(accentColor) else Color.parseColor("#C9A46A"))
                    setPadding(0, dp(8), 0, 0)
                },
            )
        }
    }

    private fun podiumCardPremium(
        entry: CloudLeaderboardEntry,
        accentColor: String,
        elevated: Boolean,
        leftMargin: Int,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1.0f,
                ).apply {
                    this.leftMargin = leftMargin
                    topMargin = if (elevated) 0 else dp(18)
                }
            addView(
                detailCard(fillColor = "#0D1924", strokeColor = accentColor, cornerDp = 24).apply {
                    background =
                        metallicBackground(
                            when (entry.rank) {
                                1 -> "#4A3A16"
                                2 -> "#35414B"
                                else -> "#4B3428"
                            },
                            "#0D1924",
                            accentColor,
                            24,
                        )
                    minimumHeight = if (elevated) dp(186) else dp(156)
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(
                        TextView(this@MainActivity).apply {
                            text = leaderboardPodiumTitle(entry.rank)
                            setTextColor(Color.parseColor("#140800"))
                            setTypeface(Typeface.DEFAULT_BOLD)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                            background =
                                metallicBackground(
                                    when (entry.rank) {
                                        1 -> "#FFE7A1"
                                        2 -> "#E3EBF1"
                                        else -> "#F1C19A"
                                    },
                                    accentColor,
                                    "#FFF5DA",
                                    999,
                                )
                            setPadding(dp(12), dp(6), dp(12), dp(6))
                        },
                    )
                    addView(
                        bodyText(rankLabel(entry.rank)).apply {
                            gravity = Gravity.CENTER
                            setTypeface(Typeface.DEFAULT_BOLD)
                            setTextColor(Color.parseColor(accentColor))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (elevated) 28f else 22f)
                            setPadding(0, dp(16), 0, 0)
                        },
                    )
                    addView(
                        titleText(leaderboardDisplayName(entry), if (elevated) 20f else 18f).apply {
                            gravity = Gravity.CENTER
                            setPadding(0, dp(8), 0, 0)
                            setTextColor(Color.parseColor("#FFF8E8"))
                        },
                    )
                    addView(
                        badgeText(
                            text = leaderboardSerialBadgeText(entry),
                            textColor = "#E7C998",
                            fillColor = "#112432",
                        ).apply {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                            setPadding(dp(10), dp(5), dp(10), dp(5))
                            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(8)
                        },
                    )
                    addView(
                        badgeText(
                            text = tierLabelForKey(entry.tierKey),
                            textColor = "#FFF5E6",
                            fillColor = "#17384B",
                        ).apply {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                            setPadding(dp(10), dp(5), dp(10), dp(5))
                        },
                    )
                    addView(
                        bodyText(leaderboardPrimaryValueText(entry)).apply {
                            gravity = Gravity.CENTER
                            setTypeface(Typeface.DEFAULT_BOLD)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (elevated) 21f else 18f)
                            setTextColor(Color.parseColor(accentColor))
                            setPadding(0, dp(12), 0, 0)
                        },
                    )
                    addView(
                        bodyText(leaderboardSecondaryValueText(entry)).apply {
                            gravity = Gravity.CENTER
                            setTextColor(Color.parseColor("#CAA26A"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                            setPadding(0, dp(8), 0, 0)
                        },
                    )
                },
            )
            addView(
                View(this@MainActivity).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            when (entry.rank) {
                                1 -> dp(54)
                                2 -> dp(38)
                                else -> dp(30)
                            },
                        ).apply {
                            topMargin = dp(10)
                        }
                    background = metallicBackground("#2A4B5E", accentColor, accentColor, 18)
                },
            )
        }

    private fun leaderboardRowCardPremium(entry: CloudLeaderboardEntry): LinearLayout {
        val accentColor = leaderboardAccentColor(leaderboardBoard)
        val card = detailCard(fillColor = "#0C1822", strokeColor = "#27485B", cornerDp = 20)
        card.background = metallicBackground("#162733", "#0B1720", "#244458", 20)
        val row =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        val accentBar =
            View(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(dp(6), dp(54)).apply {
                        rightMargin = dp(12)
                    }
                background = metallicBackground(accentColor, "#17384B", accentColor, 999)
            }
        val rankView =
            bodyText(rankLabel(entry.rank)).apply {
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.parseColor(accentColor))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        rightMargin = dp(12)
                    }
            }
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.0f,
                    )
            }
        content.addView(
            bodyText(leaderboardDisplayName(entry)).apply {
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.parseColor("#FFF5E6"))
            },
        )
        content.addView(
            bodyText("${leaderboardPrimaryValueText(entry)} | ${leaderboardSecondaryValueText(entry)}").apply {
                setTextColor(Color.parseColor("#B88A54"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(4), 0, 0)
            },
        )
        val sideColumn =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
        sideColumn.addView(
            badgeText(tierLabelForKey(entry.tierKey), textColor = "#F2D8A7", fillColor = leaderboardAccentFill(leaderboardBoard)).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            },
        )
        sideColumn.addView(
            badgeText(leaderboardSerialBadgeText(entry), textColor = "#F2D8A7", fillColor = "#132635").apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(6)
            },
        )
        row.addView(accentBar)
        row.addView(rankView)
        row.addView(content)
        row.addView(sideColumn)
        card.addView(row)
        return card
    }

    private fun localeForLanguage(): Locale =
        when (selectedLanguage) {
            AppLanguage.Chinese -> Locale.SIMPLIFIED_CHINESE
            AppLanguage.English -> Locale.US
            AppLanguage.French -> Locale.FRANCE
            AppLanguage.Thai -> Locale("th", "TH")
        }

    private fun parseCloudDate(value: String): Date? {
        val patterns =
            listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
            )
        patterns.forEach { pattern ->
            runCatching {
                val parser = SimpleDateFormat(pattern, Locale.US)
                if (pattern.contains("'Z'") || pattern == "yyyy-MM-dd'T'HH:mm:ss.SSS" || pattern == "yyyy-MM-dd'T'HH:mm:ss" || pattern == "yyyy-MM-dd HH:mm:ss") {
                    parser.timeZone = TimeZone.getTimeZone("UTC")
                }
                return parser.parse(value)
            }
        }
        return null
    }

    private fun titleText(
        text: String,
        sizeSp: Float,
    ): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            letterSpacing = 0.01f
        }

    private fun sectionTitle(text: String): TextView =
        bodyText(text).apply {
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.parseColor("#FFF6E2"))
            setPadding(0, dp(10), 0, dp(8))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            letterSpacing = 0.01f
        }

    private fun sectionSubtitle(text: String): TextView =
        bodyText(text).apply {
            setTextColor(Color.parseColor("#8EA6B9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setPadding(0, 0, 0, dp(12))
        }

    private fun sectionLabel(text: String): TextView =
        bodyText(text).apply {
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.parseColor("#FFF6E2"))
            setPadding(0, 0, 0, dp(6))
        }

    private fun activationInput(hint: String): EditText =
        EditText(this).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8F6A44"))
            setBackgroundColor(Color.parseColor("#2A1000"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
        }

    private fun bodyText(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#EAF3FB"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            setLineSpacing(0f, 1.18f)
        }

    private fun headerBatteryText(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#FFF5E6"))
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            includeFontPadding = false
            maxLines = 1
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
        }

    private fun surfaceCard(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = surfaceCardBackground()
            setPadding(dp(20), dp(20), dp(20), dp(20))
            elevation = dp(3).toFloat()
        }

    private fun surfaceCardBackground(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(24).toFloat()
            colors =
                intArrayOf(
                    Color.parseColor("#112230"),
                    Color.parseColor("#1A0C00"),
                )
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(dp(1), Color.parseColor("#27475B"))
        }

    private fun chipBackground(accentColor: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(999).toFloat()
            setColor(Color.argb(38, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)))
            setStroke(dp(1), accentColor)
        }

    private fun actionButton(
        text: String,
        color: String,
    ): Button =
        Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            background = roundedBackground(color, "#D6F6FF", 22)
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            textSize = 15f
            isAllCaps = false
            elevation = dp(3).toFloat()
            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1.0f,
                )
            applyRippleOverlay()
        }

    private fun compactActionButton(
        text: String,
        color: String,
    ): Button =
        Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            background = roundedBackground(color, "#D6F6FF", 20)
            setTypeface(Typeface.DEFAULT_BOLD)
            minWidth = 0
            minimumWidth = 0
            textSize = 13f
            isAllCaps = false
            setPadding(dp(16), dp(11), dp(16), dp(11))
            elevation = dp(2).toFloat()
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            applyRippleOverlay()
        }

    private fun horizontalSpace(width: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(width, 1)
        }

    private fun spacer(height: Int): View =
        View(this).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    height,
                )
        }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()

    private companion object {
        const val EXTRA_DEBUG_OPEN_MODULE = "debug_open_module"
        const val DEBUG_MODULE_FAT_BURN_CHALLENGE = "fat_burn_challenge"
        const val DEBUG_MODULE_FAT_BURN_COACH = "fat_burn_coach"
        const val PREFS_NAME = "reflex_ball_settings"
        const val KEY_LANGUAGE = "language"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_SENSITIVITY_DEFAULT_MIGRATED = "sensitivity_default_migrated_3000"
        const val KEY_INSTALL_ID = "install_id"
        const val KEY_AUTH_SERIAL = "auth_serial"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_AUTH_INSTALL_ID = "auth_install_id"
        const val KEY_AUTH_DEVICE_HASH = "auth_device_hash"
        const val KEY_AUTH_ACTIVATED_AT = "auth_activated_at"
        const val KEY_AUTH_LAST_CHECK_AT = "auth_last_check_at"
        const val FREE_USE_AUTH_TOKEN = "free-use"
        const val KEY_LAST_SEEN_TIER = "last_seen_tier"
        const val KEY_PROFILE_AVATAR_URI = "profile_avatar_uri"
        const val KEY_LOCAL_BACKGROUND_PROFILES = "local_background_noise_profiles"
        const val KEY_SELECTED_PLAY_MODE = "selected_play_mode"
        const val KEY_FIRST_LAUNCH_BLE_PROMPT_SHOWN = "first_launch_ble_prompt_shown"
        const val KEY_LAST_BLE_LEFT_ADDRESS = "last_ble_left_address"
        const val KEY_LAST_BLE_LEFT_NAME = "last_ble_left_name"
        const val KEY_LAST_BLE_RIGHT_ADDRESS = "last_ble_right_address"
        const val KEY_LAST_BLE_RIGHT_NAME = "last_ble_right_name"
        const val KEY_TRAINING_LEVEL = "training_play_level"
        const val KEY_TRAINING_XP = "training_play_xp"
        const val KEY_TRAINING_LAST_DATE = "training_last_date"
        const val KEY_TRAINING_STREAK = "training_current_streak"
        const val KEY_BEST_TRAINING_STREAK = "training_best_streak"
        const val KEY_DAILY_TASK_DATE = "daily_task_date"
        const val KEY_DAILY_TASK_TRAINED = "daily_task_trained"
        const val KEY_DAILY_TASK_TARGET_DONE = "daily_task_target_done"
        const val KEY_DAILY_TASK_SHARED = "daily_task_shared"
        const val KEY_LOCAL_TRAINING_SESSIONS = "local_training_sessions"
        const val DEVELOPER_COMPANY_NAME_ZH = "绍兴维脉科技有限公司"
        const val DEVELOPER_COMPANY_NAME_EN = "Shaoxing Weimai Technology Co., Ltd."
        const val DEVELOPER_COMPANY_NAME_FR = "Shaoxing Weimai Technology Co., Ltd."
        const val DEVELOPER_COMPANY_NAME_TH = "Shaoxing Weimai Technology Co., Ltd."
        const val DEVELOPER_EMAIL = "zclei@vip.sina.com"
        const val DEVELOPER_EMAIL_SUBJECT = "BoxingFitness APP咨询"
        const val DEFAULT_SENSITIVITY = 75
        const val LEGACY_DEFAULT_SENSITIVITY = 50
        const val AUDIO_SAMPLE_DURATION_MS = 10_000
        const val MAX_LOCAL_BACKGROUND_PROFILES = 2
        const val EMOTION_MODE_HIT_CALORIES = 0.34f
        const val EMOTION_MODE_FAT_BURN_RATIO = 0.11f
        const val BOXING_BLE_AUTO_CONNECT_TIMEOUT_MS = 20_000L
        const val BOXING_BLE_PAIR_ERROR_MESSAGE = "设备蓝牙连接有误，请到设置界面扫描后连接"
        const val FIRST_LAUNCH_BLE_PROMPT_MESSAGE = "请到设置界面连接蓝牙设备"
        val SUPER_AUDIO_COLLECTOR_SERIALS =
            setOf(
                "02260400011",
                "01260400153",
                "01260400286",
                "01260400278",
                "01260400294",
            )
    }
}

private class BluetoothStatusIndicatorView(
    context: android.content.Context,
) : View(context) {
    private var connected: Boolean = false
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val path = Path()

    fun setConnected(value: Boolean) {
        if (connected == value) return
        connected = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val top = (height - size) / 2f + size * 0.17f
        val bottom = (height + size) / 2f - size * 0.17f
        val mid = height / 2f
        val left = cx - size * 0.22f
        val right = cx + size * 0.21f
        paint.color = if (connected) Color.parseColor("#28A8FF") else Color.parseColor("#FF4B55")
        paint.strokeWidth = max(2f, size * 0.08f)
        path.reset()
        path.moveTo(cx, top)
        path.lineTo(cx, bottom)
        path.moveTo(cx, top)
        path.lineTo(right, mid - size * 0.12f)
        path.lineTo(left, mid)
        path.lineTo(right, mid + size * 0.12f)
        path.lineTo(cx, bottom)
        canvas.drawPath(path, paint)
    }
}

private class BatteryStatusIndicatorView(
    context: android.content.Context,
    private var handLabel: String,
) : View(context) {
    private var percent: Int? = null
    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    private val bodyRect = RectF()
    private val fillRect = RectF()
    private val terminalRect = RectF()

    fun setHandLabel(value: String) {
        handLabel = value
    }

    fun setBattery(value: Int?) {
        percent = value?.coerceIn(0, 100)
        contentDescription = "$handLabel ${percent?.let { "$it%" } ?: "--"}"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bodyLeft = width * 0.05f
        val bodyTop = height * 0.18f
        val bodyRight = width * 0.88f
        val bodyBottom = height * 0.82f
        bodyRect.set(bodyLeft, bodyTop, bodyRight, bodyBottom)
        terminalRect.set(bodyRight + width * 0.015f, height * 0.36f, width * 0.98f, height * 0.64f)

        val level = percent
        val outlineColor =
            when {
                level == null -> Color.parseColor("#8A97A3")
                level <= 20 -> Color.parseColor("#FF4B55")
                level <= 45 -> Color.parseColor("#FFD060")
                else -> Color.parseColor("#28A8FF")
            }
        strokePaint.color = outlineColor
        fillPaint.color = Color.argb(44, Color.red(outlineColor), Color.green(outlineColor), Color.blue(outlineColor))
        canvas.drawRoundRect(bodyRect, height * 0.11f, height * 0.11f, fillPaint)
        canvas.drawRoundRect(bodyRect, height * 0.11f, height * 0.11f, strokePaint)
        fillPaint.color = outlineColor
        canvas.drawRoundRect(terminalRect, height * 0.04f, height * 0.04f, fillPaint)

        if (level != null) {
            val innerPadding = height * 0.14f
            val fillWidth = (bodyRect.width() - innerPadding * 2f) * (level / 100f)
            fillRect.set(
                bodyRect.left + innerPadding,
                bodyRect.top + innerPadding,
                bodyRect.left + innerPadding + fillWidth,
                bodyRect.bottom - innerPadding,
            )
            fillPaint.color = Color.argb(92, Color.red(outlineColor), Color.green(outlineColor), Color.blue(outlineColor))
            canvas.drawRoundRect(fillRect, height * 0.06f, height * 0.06f, fillPaint)
        }

        textPaint.textSize = height * 0.36f
        val numberText = level?.toString() ?: "--"
        val display = "$handLabel $numberText"
        val baseline = bodyRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(display, bodyRect.centerX(), baseline, textPaint)
    }
}
