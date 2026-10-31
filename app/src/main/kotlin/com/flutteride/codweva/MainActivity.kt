package com.flutteride.codweva

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flutteride.codweva.java.JavaBuildWorkflow
import com.flutteride.codweva.java.JavaProjectCreator
import com.flutteride.codweva.java.KotlinBuildWorkflow
import com.flutteride.codweva.java.KotlinProjectCreator
import com.flutteride.codweva.reactnative.ReactNativeBuildWorkflow
import com.flutteride.codweva.reactnative.ReactNativeProjectCreator
import com.flutteride.codweva.ui.ProjectTypeSelectorDialog
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashMap
import java.util.HashSet
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * MainActivity — الشاشة الرئيسية.
 *
 * v15.2 — إصلاح اختفاء شريط الأقواس فوق الكيبورد:
 *   - applyEdgeToEdgeInsets() بقت تتعامل مع ime (لوحة المفاتيح) كـ bottom padding
 *   - updateKeyboardVisibility() بتتنفذ مباشرة من الـ insets listener
 *   - setupKeyboardVisibilityListener() بقت فارغة (لم نعد بحاجتها)
 *   - codeToolbar بيظهر فوق الكيبورد بشكل صحيح
 *
 * v15.1 — إصلاح اختفاء لوحة السجل وشريط الأقواس بعد Edge-to-Edge:
 *   - applyEditorInsets() لم تعد تطبق bottom padding على logPanel
 *     (لأن rootLayout بياخد bottom inset بالفعل)
 *   - editorTopInset و editorBottomInset = 0 (insets تم استهلاكها من parent)
 *   - logPanel و codeToolbar يرجعوا يظهروا بشكل طبيعي
 *
 * v15.0 — إصلاح Edge-to-Edge على Android 15/16:
 *   - WindowCompat.setDecorFitsSystemWindows(window, false)
 *   - layoutInDisplayCutoutMode = SHORT_EDGES (دعم النوتش)
 *   - تطبيق WindowInsets على rootLayout
 *   - دعم displayCutout في الحسابات
 *   - أيقونات شريط الحالة بيضاء على الخلفية الداكنة
 *
 * v14.1 — إصلاحات واجهة لوحة السجل:
 *   - إضافة HorizontalScrollView لشريط الأزرار العلوي
 *   - اليسار (السجل + الوقت + الأخطاء) ثابت
 *   - اليمين (GitHub + مسح + نسخ) قابل للتمرير أفقياً
 *   - تصغير حجم الأزرار أفقياً (padding/textSize/height)
 *
 * v14.0 — Dialog اختيار طريقة الرفع:
 *   - عند الضغط على "بناء APK" يظهر Dialog يخيّر بين GraphQL / دفعات
 *   - خيار "تذكّر اختياري" محفوظ في SharedPreferences
 *   - زر "تغيير طريقة الرفع" في Drawer لإعادة الاختيار
 *   - Dialog بديل عند فشل GraphQL (HTTP 499) لتجربة الدفعات
 */
class MainActivity : AppCompatActivity() {

    // ============================================================
    // الألوان
    // ============================================================
    private val COLOR_BACKGROUND = 0xFF0D1117.toInt()
    private val COLOR_SURFACE = 0xFF161B22.toInt()
    private val COLOR_SURFACE_ALT = 0xFF21262D.toInt()
    private val COLOR_PRIMARY = 0xFF1F6FEB.toInt()
    private val COLOR_PRIMARY_LIGHT = 0xFF58A6FF.toInt()
    private val COLOR_TEXT_PRIMARY = 0xFFE6EDF3.toInt()
    private val COLOR_TEXT_SECONDARY = 0xFF8B949E.toInt()
    private val COLOR_TEXT_BLUE = COLOR_TEXT_PRIMARY
    private val COLOR_ARROW = 0xFF8B949E.toInt()
    private val COLOR_BORDER = 0xFF30363D.toInt()
    private val COLOR_DRAG_HANDLE = 0xFF21262D.toInt()
    private val COLOR_ERROR = 0xFFCF222E.toInt()
    private val COLOR_WARNING = 0xFFD29922.toInt()
    private val COLOR_SUCCESS = 0xFF2EA043.toInt()

    private val STEP_COLOR_SUCCESS = 0xFF2EA043.toInt()
    private val STEP_COLOR_FAILURE = 0xFFCF222E.toInt()
    private val STEP_COLOR_RUNNING = 0xFFD29922.toInt()
    private val STEP_COLOR_QUEUED = 0xFF6E7681.toInt()

    private val LOG_COLOR_ERROR = 0xFFCF222E.toInt()
    private val LOG_COLOR_NORMAL = 0xFFE6EDF3.toInt()
    private val LOG_COLOR_CONTEXT = 0xFF8B949E.toInt()
    private val LOG_COLOR_LINK = 0xFF58A6FF.toInt()
    private val LOG_COLOR_SUCCESS = 0xFF3FB950.toInt()
    private val LOG_COLOR_WARNING = 0xFFD29922.toInt()

    // ============================================================
    // ثوابت
    // ============================================================
    private val DEFAULT_VISIBLE_LOGS = 100
    private val LOGS_BATCH_SIZE = 100

    private val ANIMATION_DURATION = 150L
    private val SLIDE_OFFSET_FRACTION = 0.25f

    private val FLUTTER_VERSIONS = arrayOf(
        "3.47.2", "3.44.7", "3.41.0", "3.38.0", "3.35.0",
        "3.29.0", "3.24.0", "3.19.0", "3.16.0"
    )

    private val FLUTTER_VERSION_LABELS = arrayOf(
        "3.47.2 (الأحدث)", "3.44.7", "3.41.0", "3.38.0", "3.35.0",
        "3.29.0", "3.24.0", "3.19.0", "3.16.0 (الأقدم المدعوم)"
    )

    private val SCREEN_PROJECTS_LIST = 0
    private val SCREEN_CREATE_PROJECT = 1
    private val SCREEN_EDITOR = 2
    private val SCREEN_LIBRARY = 3
    private val SCREEN_AI = 4
    private val SCREEN_SETTINGS = 5

    private val REQUEST_IMPORT_FILE = 100
    private val REQUEST_IMPORT_PROJECT_ZIP = 101
    private val REQUEST_PICK_ICON = 200
    private val REQUEST_PICK_ICON_CREATE = 201
    private val REQUEST_NOTIFICATION_PERMISSION = 8100

    private val IMPORT_TYPE_ID_BASE = 1000

    // ============================================================
    // ⭐ v15.0: متغيرات insets
    // ============================================================
    private var systemBarTopInset: Int = 0
    private var systemBarBottomInset: Int = 0
    private var systemBarLeftInset: Int = 0
    private var systemBarRightInset: Int = 0

    // ============================================================
    // الحالة
    // ============================================================
    private var currentScreen = SCREEN_PROJECTS_LIST
    private var previousScreen = SCREEN_PROJECTS_LIST

    private var currentCreateType: ProjectType = ProjectType.FLUTTER

    private lateinit var prefs: SharedPreferences
    private lateinit var projectsBaseDir: File
    private var currentProjectDir: File? = null
    private var currentOpenFile: File? = null
    private var reloadFileAfterAi = false
    private lateinit var editorStateStore: EditorStateStore

    private val openTabs = mutableListOf<EditorTabState>()
    private var activeTabIndex = -1
    private var tabStrip: LinearLayout? = null
    private var tabScrollView: HorizontalScrollView? = null
    private var btnFavoriteCurrent: Button? = null
    private var favoritePaths: Set<String> = HashSet()

    private var selectedIconUri: Uri? = null
    private var ivIconPreview: ImageView? = null
    private var tvIconSelected: TextView? = null
    private var btnSelectIcon: Button? = null

    private var btnToolbarSave: Button? = null

    private var repoTypeGroup: RadioGroup? = null

    private lateinit var rootLayout: LinearLayout
    private lateinit var contentContainer: FrameLayout
    private var bottomNav: LinearLayout? = null
    private var navProjects: LinearLayout? = null
    private var navLibrary: LinearLayout? = null
    private var navAi: LinearLayout? = null
    private var navSettings: LinearLayout? = null
    private var currentNavIndex = 0

    private lateinit var layoutProjectsList: LinearLayout
    private lateinit var lvProjects: ListView
    private val projectsList = mutableListOf<File>()

    private lateinit var layoutCreateProject: LinearLayout
    private var etAppName: EditText? = null
    private var etPackageName: EditText? = null
    private var spinnerApi: Spinner? = null
    private var spinnerFlutterVersion: Spinner? = null
    private var tvFlutterVersionLabel: TextView? = null

    private var drawerLayout: DrawerLayout? = null
    private var layoutEditor: LinearLayout? = null
    private var toolbar: Toolbar? = null
    private var codeEditorView: CodeEditorView? = null
    private var codeToolbar: CodeToolbar? = null
    private var findReplaceBar: FindReplaceBar? = null
    private var settingsDialog: ProjectSettingsDialog? = null
    private var tvProjectInfo: TextView? = null
    private var rvFileTree: RecyclerView? = null
    private var fileTreeAdapter: FileTreeAdapter? = null
    private var fileTreeLayoutManager: LinearLayoutManager? = null
    private var treeScrollPosition = 0
    private var treeScrollOffset = 0
    private var drawerContent: LinearLayout? = null
    private var tvCurrentFilePath: TextView? = null

    private var btnChangeUploadMethodInDrawer: Button? = null

    private var editorTopInset: Int = 0
    private var editorBottomInset: Int = 0

    private var accountCardView: LinearLayout? = null
    private var accountNameView: TextView? = null
    private var accountUserView: TextView? = null
    private var accountIconView: TextView? = null

    private var viewerContainer: FrameLayout? = null
    private var activeViewer: View? = null
    private var activeViewerKind: FileKind? = null
    private var activeImageViewer: ImageViewer? = null
    private var activeVideoViewer: VideoViewer? = null
    private var activeAudioViewer: AudioViewer? = null
    private var activeUnsupportedViewer: UnsupportedViewer? = null

    // ============================================================
    // لوحة السجل
    // ============================================================
    private var logPanel: LinearLayout? = null
    private var tvLogs: TextView? = null
    private var rvSteps: RecyclerView? = null
    private var stepsAdapter: StepsAdapter? = null
    private val uiSteps = mutableListOf<UiStep>()
    private var tvBuildTimer: TextView? = null
    private var tvUploadCounter: TextView? = null
    private var uploadCounterBox: LinearLayout? = null
    private var tvErrorSummary: TextView? = null
    private val buildErrors = mutableListOf<BuildError>()
    private var logDragHandle: View? = null
    private var dragIcon: TextView? = null
    private var isLogPanelExpanded = false
    private var logPanelCollapsedHeight = 0
    private var logPanelExpandedHeight = 0
    private var startY = 0f
    private var startHeight = 0
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null

    private var logHeaderRef: LinearLayout? = null

    private var waitingIndicator: TextView? = null

    private var buildStartedAtMs = 0L
    private var buildInProgress = false

    private var downloadProgressDialog: AlertDialog? = null
    private var downloadProgressBar: ProgressBar? = null
    private var downloadProgressText: TextView? = null
    private var downloadProgressPercent: TextView? = null

    private var isEditorVisible = false
    private val expandedDirs = mutableListOf<File>()
    private lateinit var buildApi: FlutterBuildApi
    private var selectedFileForDialog: File? = null

    private val projectIconCache = HashMap<String, Bitmap>()

    private var lastKeyboardVisible = false
    private var isAnimating = false

    private var backgroundExecutor: ExecutorService? = null

    @Volatile
    private var loadingTreeForProject: File? = null

    private var shouldReloadProjectsList = false
    private var lastAppliedEditorThemeId: String? = null

    private var buildReceiver: BroadcastReceiver? = null
    private var buildReceiverRegistered = false

    @Volatile
    private var isPolling = false

    private class BuildError(
        val rawMessage: String,
        val path: String,
        val line: Int,
        val column: Int,
        val warning: Boolean
    )

    private class UiStep(
        var name: String,
        var status: String,
        var conclusion: String,
        val startedAtMs: Long,
        var durationMs: Long,
        var jobId: Long
    ) {
        val allLogs = mutableListOf<FlutterBuildApi.BuildLogLine>()
        val errorLogs = mutableListOf<FlutterBuildApi.BuildLogLine>()
        val annotations = mutableListOf<FlutterBuildApi.BuildLogLine>()
        var logsExpanded = false
        var visibleCount = 100
        var logsFetched = false
        var showFromEnd = isFailure()

        fun isRunning(): Boolean = status.equals("in_progress", ignoreCase = true)
        fun isSuccess(): Boolean =
            status.equals("completed", ignoreCase = true) &&
                    conclusion.equals("success", ignoreCase = true)

        fun isFailure(): Boolean =
            status.equals("completed", ignoreCase = true) &&
                    (conclusion.equals("failure", ignoreCase = true) ||
                            conclusion.equals("cancelled", ignoreCase = true))

        fun isQueued(): Boolean = !isRunning() && !isSuccess() && !isFailure()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthManager.getInstance(this).isLoggedIn) {
            goToLoginActivity()
            return
        }

        // ============================================================
        // ⭐ v15.0: إعداد النافذة لـ Edge-to-Edge بشكل كامل
        // ============================================================
        setupWindowEdgeToEdge()

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        backgroundExecutor = Executors.newFixedThreadPool(3)

        prefs = getSharedPreferences("flutter_ide_prefs", Context.MODE_PRIVATE)
        editorStateStore = EditorStateStore(this)
        projectsBaseDir = File(filesDir, "projects").apply {
            if (!exists()) mkdirs()
        }

        buildApi = FlutterBuildApi(this)
        val savedLogMode = prefs.getInt("log_mode", 0)
        buildApi.logMode = savedLogMode

        timerHandler = Handler(Looper.getMainLooper())

        buildAllScreens()
        setupKeyboardVisibilityListener()
        setupRootLayout()

        shouldReloadProjectsList = false
        prefs.edit().putBoolean(KEY_PROJECTS_DIRTY, false).apply()

        registerBuildReceiver()
        requestNotificationPermissionIfNeeded()
        BuildForegroundService.ensureChannel(this)

        showScreen(SCREEN_PROJECTS_LIST, false)
        loadProjectsList()
    }

    /**
     * ⭐ v15.0 — إعداد النافذة لـ Edge-to-Edge.
     */
    private fun setupWindowEdgeToEdge() {
        // 1) Edge-to-Edge إجباري
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 2) دعم Display Cutout (النوتش) — من API 28
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // 3) ألوان شريط الحالة والتنقل
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = COLOR_BACKGROUND
            window.navigationBarColor = COLOR_BACKGROUND
            window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(COLOR_BACKGROUND)
            )
        }

        // 4) أيقونات شريط الحالة بيضاء
        try {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        } catch (_: Exception) {
        }
    }

    private fun setupRootLayout() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BACKGROUND)
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        contentContainer = FrameLayout(this).apply {
            setBackgroundColor(COLOR_BACKGROUND)
        }
        rootLayout.addView(
            contentContainer,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        bottomNav = buildBottomNav()
        rootLayout.addView(bottomNav, LinearLayout.LayoutParams(-1, -2))

        // ============================================================
        // ⭐ v15.0: تطبيق WindowInsets على rootLayout
        // ============================================================
        applyEdgeToEdgeInsets()

        setContentView(rootLayout)

        // ⭐ أعد تطبيق insets بعد setContentView
        ViewCompat.requestApplyInsets(rootLayout)
    }

    /**
     * ⭐ v15.2 — تطبيق WindowInsets مع دعم IME (لوحة المفاتيح).
     *
     * التغييرات:
     *   - إضافة ime.bottom إلى الـ bottom padding
     *     (ضروري لظهور codeToolbar فوق الكيبورد)
     *   - استدعاء updateKeyboardVisibility() مباشرة
     *     (بدل الاعتماد على listener منفصل على decorView)
     */
    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            } else {
                androidx.core.graphics.Insets.NONE
            }

            // ⭐ v15.2: ime inset (لوحة المفاتيح)
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val left = maxOf(systemBars.left, cutout.left)
            val top = maxOf(systemBars.top, cutout.top)
            val right = maxOf(systemBars.right, cutout.right)
            val bottom = maxOf(
                maxOf(systemBars.bottom, cutout.bottom),
                ime.bottom
            )

            // نحفظ القيم للاستخدام في applyEditorInsets
            systemBarTopInset = top
            systemBarBottomInset = bottom
            systemBarLeftInset = left
            systemBarRightInset = right

            // نطبق الـ padding على rootLayout (يحتوي contentContainer + bottomNav)
            view.setPadding(left, top, right, bottom)

            // ⭐ v15.2: تحديث keyboard visibility مباشرة
            val keyboardVisible = ime.bottom > 0
            updateKeyboardVisibility(keyboardVisible)

            insets
        }
    }

    private fun buildBottomNav(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val divider = View(this).apply {
            setBackgroundColor(COLOR_BORDER)
        }
        container.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(COLOR_SURFACE)
        }

        navProjects = buildBottomNavItem("مشاريع", true) {
            showScreen(SCREEN_PROJECTS_LIST, true)
        }
        navLibrary = buildBottomNavItem("المكتبة", false) {
            startActivity(Intent(this, BuiltAppsActivity::class.java))
        }
        navAi = buildBottomNavItem("AI", false) {
            startActivity(Intent(this, AiSettingsActivity::class.java))
        }
        navSettings = buildBottomNavItem("إعدادات", false) {
            showSettingsDialog()
        }

        nav.addView(navProjects, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(navLibrary, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(navAi, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(navSettings, LinearLayout.LayoutParams(0, -2, 1f))

        container.addView(nav)
        return container
    }

    private fun buildBottomNavItem(
        label: String,
        active: Boolean,
        onClick: View.OnClickListener
    ): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener(onClick)
        }

        val indicator = View(this).apply {
            setBackgroundColor(if (active) COLOR_PRIMARY else 0x00000000)
        }
        val ip = LinearLayout.LayoutParams(dp(28), dp(3)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(6)
        }
        item.addView(indicator, ip)

        val text = TextView(this).apply {
            this.text = label
            setTextSize(13f)
            setTextColor(if (active) COLOR_PRIMARY else COLOR_TEXT_SECONDARY)
            setTypeface(
                Typeface.DEFAULT,
                if (active) Typeface.BOLD else Typeface.NORMAL
            )
            gravity = Gravity.CENTER
            tag = "nav_label"
        }
        item.addView(text)

        item.tag = indicator
        return item
    }

    private fun updateNavActive(index: Int) {
        currentNavIndex = index
        updateNavItem(navProjects, index == 0)
        updateNavItem(navLibrary, index == 1)
        updateNavItem(navAi, index == 2)
        updateNavItem(navSettings, index == 3)
    }

    private fun updateNavItem(item: LinearLayout?, active: Boolean) {
        if (item == null) return
        val tag = item.tag
        if (tag is View) {
            tag.setBackgroundColor(if (active) COLOR_PRIMARY else 0x00000000)
        }
        val text = item.findViewWithTag<TextView>("nav_label")
        if (text != null) {
            text.setTextColor(if (active) COLOR_PRIMARY else COLOR_TEXT_SECONDARY)
            text.setTypeface(
                Typeface.DEFAULT,
                if (active) Typeface.BOLD else Typeface.NORMAL
            )
        }
    }

    private fun showScreen(screen: Int) {
        showScreen(screen, true)
    }

    private fun showScreen(screen: Int, animate: Boolean) {
        if (isAnimating) return

        currentScreen = screen

        val newScreenView = getScreenView(screen) ?: return

        val showBottomNav = shouldShowBottomNav(screen)
        bottomNav?.visibility = if (showBottomNav) View.VISIBLE else View.GONE

        if (showBottomNav) {
            updateNavActiveForScreen(screen)
        }

        if (contentContainer.childCount > 0) {
            val oldView = contentContainer.getChildAt(0)
            if (oldView === newScreenView) {
                onScreenShown(screen)
                return
            }

            (newScreenView.parent as? ViewGroup)?.removeView(newScreenView)

            if (animate && oldView.width > 0) {
                performScreenTransition(oldView, newScreenView)
                return
            } else {
                contentContainer.removeAllViews()
            }
        }

        (newScreenView.parent as? ViewGroup)?.removeView(newScreenView)

        contentContainer.addView(newScreenView, FrameLayout.LayoutParams(-1, -1))
        onScreenShown(screen)
    }

    private fun getScreenView(screen: Int): View? {
        return when (screen) {
            SCREEN_PROJECTS_LIST, SCREEN_LIBRARY, SCREEN_AI -> layoutProjectsList
            SCREEN_CREATE_PROJECT -> layoutCreateProject
            SCREEN_EDITOR -> drawerLayout
            else -> null
        }
    }

    private fun shouldShowBottomNav(screen: Int): Boolean {
        return screen == SCREEN_PROJECTS_LIST ||
                screen == SCREEN_LIBRARY ||
                screen == SCREEN_AI
    }

    private fun updateNavActiveForScreen(screen: Int) {
        when (screen) {
            SCREEN_PROJECTS_LIST -> updateNavActive(0)
            SCREEN_LIBRARY -> updateNavActive(1)
            SCREEN_AI -> updateNavActive(2)
        }
    }

    private fun onScreenShown(screen: Int) {
        if (screen == SCREEN_PROJECTS_LIST) {
            restartBrandAnimationIfPossible()

            if (shouldReloadProjectsList) {
                shouldReloadProjectsList = false
                loadProjectsList()
            }
        } else if (screen == SCREEN_EDITOR) {
            isEditorVisible = true
        }
    }

    private fun performScreenTransition(oldView: View, newView: View) {
        isAnimating = true

        var containerWidth = contentContainer.width
        if (containerWidth <= 0) {
            containerWidth = resources.displayMetrics.widthPixels
        }

        val slideDistance = containerWidth * SLIDE_OFFSET_FRACTION

        (newView.parent as? ViewGroup)?.removeView(newView)
        contentContainer.addView(newView, FrameLayout.LayoutParams(-1, -1))

        newView.alpha = 0f
        newView.translationX = slideDistance
        oldView.alpha = 1f
        oldView.translationX = 0f

        val oldAlpha = ObjectAnimator.ofFloat(oldView, "alpha", 1f, 0f)
        val oldTranslate = ObjectAnimator.ofFloat(oldView, "translationX", 0f, -slideDistance)
        val newAlpha = ObjectAnimator.ofFloat(newView, "alpha", 0f, 1f)
        val newTranslate = ObjectAnimator.ofFloat(newView, "translationX", slideDistance, 0f)

        val set = AnimatorSet()
        set.playTogether(oldAlpha, oldTranslate, newAlpha, newTranslate)
        set.duration = ANIMATION_DURATION
        set.interpolator = DecelerateInterpolator()

        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                contentContainer.removeView(oldView)
                oldView.alpha = 1f
                oldView.translationX = 0f
                newView.alpha = 1f
                newView.translationX = 0f
                isAnimating = false
                onScreenShown(currentScreen)
            }
        })

        set.start()
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterBuildReceiver()

        try {
            buildApi.stopPolling()
        } catch (_: Exception) {
        }

        stopTimerUpdates()
        releaseCurrentViewer()
        hideDownloadProgressDialog()

        backgroundExecutor?.shutdownNow()
        backgroundExecutor = null
        loadingTreeForProject = null

        for (bmp in projectIconCache.values) {
            if (!bmp.isRecycled) bmp.recycle()
        }
        projectIconCache.clear()
    }

    private fun goToLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * ⭐ v15.2: لم نعد بحاجة لـ listener منفصل على decorView
     * لأن applyEdgeToEdgeInsets() بقت بتتعامل مع ime inset مباشرة
     * وتستدعي updateKeyboardVisibility() بنفسها.
     */
    private fun setupKeyboardVisibilityListener() {
        // لا شيء — التحديث يتم من applyEdgeToEdgeInsets()
    }

    private fun updateKeyboardVisibility(keyboardVisible: Boolean) {
        if (keyboardVisible == lastKeyboardVisible) return
        lastKeyboardVisible = keyboardVisible

        val isTextEditor = activeViewerKind == FileKind.TEXT ||
                activeViewerKind == FileKind.UNKNOWN ||
                activeViewerKind == null

        if (keyboardVisible) {
            codeToolbar?.visibility = if (isTextEditor) View.VISIBLE else View.GONE
            logPanel?.visibility = View.GONE
        } else {
            codeToolbar?.visibility = View.GONE
            logPanel?.let { panel ->
                panel.visibility = View.VISIBLE

                val params = panel.layoutParams
                params.height = if (isLogPanelExpanded)
                    logPanelExpandedHeight
                else
                    logPanelCollapsedHeight
                panel.layoutParams = params

                updateDragIcon()
                updateLogPanelContentVisibility(params.height)
            }
        }
    }

    private fun releaseCurrentViewer() {
        try {
            activeVideoViewer?.release()
            activeVideoViewer = null
            activeAudioViewer?.release()
            activeAudioViewer = null
            activeImageViewer?.release()
            activeImageViewer = null
            activeUnsupportedViewer = null
            activeViewer = null
            activeViewerKind = null
        } catch (_: Exception) {
        }
    }

    private fun showFileViewer(file: File) {
        if (!file.isFile) return
        val container = viewerContainer ?: return

        releaseCurrentViewer()

        val kind = FileKind.detect(file.name)
        var newViewer: View? = null

        try {
            when (kind) {
                FileKind.IMAGE -> {
                    activeImageViewer = ImageViewer(this).apply {
                        loadImage(file.absolutePath)
                    }
                    newViewer = activeImageViewer
                }
                FileKind.VIDEO -> {
                    activeVideoViewer = VideoViewer(this).apply {
                        loadVideo(file.absolutePath)
                    }
                    newViewer = activeVideoViewer
                }
                FileKind.AUDIO -> {
                    activeAudioViewer = AudioViewer(this).apply {
                        loadAudio(file.absolutePath)
                    }
                    newViewer = activeAudioViewer
                }
                FileKind.PDF, FileKind.APK, FileKind.ARCHIVE, FileKind.BINARY -> {
                    activeUnsupportedViewer = UnsupportedViewer(this).apply {
                        loadFile(file.absolutePath, kind)
                    }
                    newViewer = activeUnsupportedViewer
                }
                FileKind.TEXT, FileKind.UNKNOWN -> {
                    if (codeEditorView == null) {
                        codeEditorView = CodeEditorView(this)
                    }
                    newViewer = codeEditorView
                }
            }
        } catch (e: Exception) {
            addLog("تعذر عرض الملف: ${e.message}")
            activeUnsupportedViewer = UnsupportedViewer(this).apply {
                loadFile(file.absolutePath, kind)
            }
            newViewer = activeUnsupportedViewer
        }

        if (newViewer == null) return

        container.removeAllViews()
        container.addView(newViewer, FrameLayout.LayoutParams(-1, -1))

        activeViewer = newViewer
        activeViewerKind = kind

        val isText = (kind == FileKind.TEXT || kind == FileKind.UNKNOWN)
        codeToolbar?.let { tb ->
            tb.visibility = if (isText) {
                if (lastKeyboardVisible) View.VISIBLE else View.GONE
            } else {
                View.GONE
            }
            if (isText && codeEditorView != null) {
                tb.setEditor(codeEditorView)
            }
        }

        if (!isText && findReplaceBar?.visibility == View.VISIBLE) {
            findReplaceBar?.hide()
        }
    }

    private fun findProjectIcon(projectDir: File?): File? {
        if (projectDir == null || !projectDir.isDirectory) return null

        val rnIcon = File(projectDir, "android/app/src/main/res/drawable/ic_launcher.png")
        if (rnIcon.isFile) return rnIcon

        val javaIcon = File(projectDir, "app/src/main/res/drawable/app_icon.png")
        if (javaIcon.isFile) return javaIcon

        val drawable = File(projectDir, "android/app/src/main/res/drawable/ic_launcher.png")
        if (drawable.isFile) return drawable

        val xmlIcon = File(projectDir, "android/app/src/main/res/drawable/ic_launcher.xml")
        if (xmlIcon.isFile) return xmlIcon

        val fallbacks = arrayOf(
            "android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
            "android/app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
            "android/app/src/main/res/mipmap-xhdpi/ic_launcher.png",
            "android/app/src/main/res/mipmap-hdpi/ic_launcher.png",
            "android/app/src/main/res/mipmap-mdpi/ic_launcher.png",
            "android/app/src/main/res/mipmap/ic_launcher.png"
        )
        for (path in fallbacks) {
            val f = File(projectDir, path)
            if (f.isFile) return f
        }
        return null
    }

    private fun getProjectIconCached(projectDir: File?): Bitmap? {
        if (projectDir == null) return null
        val key = projectDir.absolutePath

        if (projectIconCache.containsKey(key)) {
            val cached = projectIconCache[key]
            if (cached != null && !cached.isRecycled) return cached
            return null
        }

        val iconFile = findProjectIcon(projectDir)
        if (iconFile == null || !iconFile.exists()) {
            projectIconCache.remove(key)
            return null
        }

        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(iconFile.absolutePath, opts)

            val targetPx = dp(96)
            var sample = 1
            val maxDim = maxOf(opts.outWidth, opts.outHeight)
            while (maxDim / (sample * 2) >= targetPx) sample *= 2

            opts.inJustDecodeBounds = false
            opts.inSampleSize = sample
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888

            val bmp = BitmapFactory.decodeFile(iconFile.absolutePath, opts)
            if (bmp != null) projectIconCache[key] = bmp
            bmp
        } catch (_: Exception) {
            projectIconCache.remove(key)
            null
        }
    }

    private fun invalidateProjectIconCache(projectDir: File?) {
        if (projectDir == null) return
        val key = projectDir.absolutePath
        projectIconCache.remove(key)?.let {
            if (!it.isRecycled) it.recycle()
        }
    }

    private fun loadProjectIconAsync(projectDir: File, container: FrameLayout) {
        Thread({
            val bmp = getProjectIconCached(projectDir)
            if (bmp == null || bmp.isRecycled) return@Thread

            container.post {
                if (isFinishing || bmp.isRecycled) return@post
                if (container.childCount == 0) return@post

                container.removeAllViews()
                val iconView = ImageView(this).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(-1, -1)
                    clipToOutline = true
                    outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(
                            view: View,
                            outline: android.graphics.Outline
                        ) {
                            outline.setOval(0, 0, view.width, view.height)
                        }
                    }
                }
                container.addView(iconView)
            }
        }, "project-icon-loader").start()
    }

    fun addLog(message: String?) {
        runOnUiThread {
            try {
                if (message == null) return@runOnUiThread
                val parsedError = parseBuildError(message)
                if (parsedError != null) registerBuildError(parsedError)

                val timestamp = SimpleDateFormat(
                    "HH:mm:ss", Locale.getDefault()
                ).format(Date())
                val logEntry = "[$timestamp] $message\n"

                tvLogs?.let { logsView ->
                    if (parsedError == null) {
                        logsView.append(logEntry)
                    } else {
                        val clickableEntry = SpannableStringBuilder(logEntry)
                        clickableEntry.setSpan(
                            ForegroundColorSpan(COLOR_TEXT_PRIMARY),
                            0, clickableEntry.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        clickableEntry.setSpan(
                            UnderlineSpan(), 0, clickableEntry.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        clickableEntry.setSpan(
                            object : ClickableSpan() {
                                override fun onClick(widget: View) {
                                    openBuildError(parsedError)
                                }

                                override fun updateDrawState(ds: android.text.TextPaint) {
                                    ds.color = COLOR_TEXT_PRIMARY
                                    ds.isUnderlineText = true
                                }
                            },
                            0, clickableEntry.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        logsView.append(clickableEntry)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun parseBuildError(message: String?): BuildError? {
        if (message.isNullOrBlank()) return null
        val lower = message.lowercase(Locale.US)
        val warning = lower.contains("warning") ||
                lower.contains("warn:") ||
                lower.contains("تحذير")

        val matcher = BUILD_LOCATION_PATTERN.matcher(message)
        if (!matcher.find()) return null

        val path = cleanBuildPath(matcher.group(1))
        val line: Int
        val column: Int

        try {
            line = matcher.group(2).toInt()
            column = matcher.group(3)?.toIntOrNull() ?: 0
        } catch (_: NumberFormatException) {
            return null
        }

        return BuildError(
            message,
            path,
            maxOf(1, line),
            maxOf(0, column),
            warning
        )
    }

    private fun cleanBuildPath(rawPath: String?): String {
        var path = rawPath?.trim()?.replace('\\', '/') ?: ""
        if (path.startsWith("file://")) path = path.substring(7)
        while (path.startsWith("./")) path = path.substring(2)

        val projectRoots = arrayOf(
            "lib/", "test/", "android/", "ios/", "web/",
            "linux/", "macos/", "windows/", "app/src/"
        )

        var best = -1
        for (root in projectRoots) {
            val index = path.lastIndexOf(root)
            if (index > best) best = index
        }
        if (best > 0 && !File(path).exists()) path = path.substring(best)
        return path
    }

    private fun registerBuildError(error: BuildError) {
        for (existing in buildErrors) {
            if (existing.path == error.path &&
                existing.line == error.line &&
                existing.column == error.column &&
                existing.warning == error.warning
            ) {
                refreshExternalErrorMarkersForCurrentFile()
                return
            }
        }
        buildErrors.add(error)
        tvErrorSummary?.text = "أخطاء: ${buildErrors.size}"
        refreshExternalErrorMarkersForCurrentFile()
    }

    private fun clearBuildErrors() {
        buildErrors.clear()
        tvErrorSummary?.text = "أخطاء: 0"
        refreshExternalErrorMarkersForCurrentFile()
    }

    private fun resolveBuildFile(rawPath: String?): File? {
        val projDir = currentProjectDir ?: return null
        if (rawPath == null) return null
        var path = rawPath.replace('\\', '/')
        if (path.startsWith("file://")) path = path.substring(7)
        while (path.startsWith("./")) path = path.substring(2)

        val direct = File(path)
        if (direct.isAbsolute && direct.exists()) return direct

        val stripped = stripGitHubRunnerPrefix(path)
        if (stripped != path) {
            val f = File(projDir, stripped)
            if (f.exists()) return f
        }

        val projectPrefix = projDir.name + "/"
        if (path.startsWith(projectPrefix)) {
            path = path.substring(projectPrefix.length)
        }

        val candidate = File(projDir, path)
        if (candidate.exists()) return candidate

        val tail = extractAfterLastKnownRoot(path)
        if (tail != null && tail != path) {
            val tailCandidate = File(projDir, tail)
            if (tailCandidate.exists()) return tailCandidate
        }

        return findByKnownRoot(path)
    }

    private fun stripGitHubRunnerPrefix(path: String?): String {
        if (path == null) return ""
        val marker = "/home/runner/work/"
        val idx = path.indexOf(marker)
        if (idx >= 0) {
            val after = path.substring(idx + marker.length)
            val parts = after.split("/", limit = 3)
            if (parts.size >= 3) return parts[2]
        }
        return path
    }

    private fun extractAfterLastKnownRoot(path: String?): String? {
        if (path == null) return null
        val roots = arrayOf(
            "/lib/", "/android/", "/test/", "/web/", "/ios/",
            "/macos/", "/windows/", "/linux/", "/pubspec.yaml",
            "/app/src/"
        )
        var bestIdx = -1
        for (r in roots) {
            val i = path.indexOf(r)
            if (i > bestIdx) bestIdx = i
        }
        return if (bestIdx >= 0) path.substring(bestIdx + 1) else null
    }

    private fun findByKnownRoot(path: String?): File? {
        val projDir = currentProjectDir ?: return null
        if (path == null) return null

        val knownRoots = arrayOf(
            "lib/", "android/app/src/main/", "android/app/", "android/",
            "test/", "web/", "ios/Runner/", "ios/",
            "pubspec.yaml", "analysis_options.yaml",
            "app/src/main/", "app/src/"
        )
        for (root in knownRoots) {
            val idx = path.indexOf(root)
            if (idx >= 0) {
                val sub = path.substring(idx)
                val f = File(projDir, sub)
                if (f.exists()) return f
            }
        }
        return null
    }

    private fun findNearbySourceFile(path: String?): File? {
        val projDir = currentProjectDir ?: return null
        if (path == null) return null
        val lower = path.lowercase(Locale.US)

        when {
            lower.endsWith("app.tsx") -> {
                val f = File(projDir, "App.tsx")
                if (f.exists()) return f
            }
            lower.endsWith("app.ts") -> {
                val f = File(projDir, "App.ts")
                if (f.exists()) return f
            }
            lower.endsWith("app.jsx") -> {
                val f = File(projDir, "App.jsx")
                if (f.exists()) return f
            }
            lower.endsWith("app.js") -> {
                val f = File(projDir, "App.js")
                if (f.exists()) return f
            }
            lower.endsWith("index.js") || lower.endsWith("index.ts") -> {
                val f = File(projDir, "index.js")
                if (f.exists()) return f
                val f2 = File(projDir, "index.ts")
                if (f2.exists()) return f2
            }
            lower.endsWith("package.json") -> {
                val f = File(projDir, "package.json")
                if (f.exists()) return f
            }
            lower.endsWith("app.json") -> {
                val f = File(projDir, "app.json")
                if (f.exists()) return f
            }
            lower.endsWith("tsconfig.json") -> {
                val f = File(projDir, "tsconfig.json")
                if (f.exists()) return f
            }
            lower.endsWith("androidmanifest.xml") -> {
                val f = File(projDir, "android/app/src/main/AndroidManifest.xml")
                if (f.exists()) return f
                val f2 = File(projDir, "app/src/main/AndroidManifest.xml")
                if (f2.exists()) return f2
            }
            lower.endsWith("build.gradle") || lower.endsWith("build.gradle.kts") -> {
                val f1 = File(projDir, "android/app/build.gradle")
                if (f1.exists()) return f1
                val f2 = File(projDir, "android/build.gradle")
                if (f2.exists()) return f2
                val f3 = File(projDir, "app/build.gradle")
                if (f3.exists()) return f3
            }
            lower.endsWith("styles.xml") || lower.endsWith("colors.xml") ||
                    lower.endsWith("strings.xml") || lower.endsWith("themes.xml") -> {
                val fileName = path.substring(path.lastIndexOf('/') + 1)
                val f = File(projDir, "android/app/src/main/res/values/$fileName")
                if (f.exists()) return f
                val f2 = File(projDir, "app/src/main/res/values/$fileName")
                if (f2.exists()) return f2
            }
            lower.contains("ic_launcher") || lower.contains("drawable") -> {
                val resDrawable = File(projDir, "android/app/src/main/res/drawable")
                if (resDrawable.isDirectory) {
                    val children = resDrawable.listFiles()
                    if (children != null && children.isNotEmpty()) return resDrawable
                }
                val resDrawable2 = File(projDir, "app/src/main/res/drawable")
                if (resDrawable2.isDirectory) {
                    val children = resDrawable2.listFiles()
                    if (children != null && children.isNotEmpty()) return resDrawable2
                }
            }
            lower.endsWith("pubspec.yaml") -> {
                val f = File(projDir, "pubspec.yaml")
                if (f.exists()) return f
            }
            lower.endsWith("module.toml") -> {
                val f = File(projDir, "app/module.toml")
                if (f.exists()) return f
            }
            lower.endsWith("main.dart") -> {
                val f = File(projDir, "lib/main.dart")
                if (f.exists()) return f
            }
            lower.endsWith("mainactivity.java") -> {
                val f = findFileRecursive(projDir, "MainActivity.java")
                if (f != null) return f
            }
            lower.endsWith("mainactivity.kt") -> {
                val f = findFileRecursive(projDir, "MainActivity.kt")
                if (f != null) return f
            }
            lower.endsWith("mainapplication.kt") -> {
                val f = findFileRecursive(projDir, "MainApplication.kt")
                if (f != null) return f
            }
            lower.endsWith("mainapplication.java") -> {
                val f = findFileRecursive(projDir, "MainApplication.java")
                if (f != null) return f
            }
        }
        return null
    }

    private fun findFileRecursive(dir: File, fileName: String): File? {
        if (!dir.isDirectory) return null
        val children = dir.listFiles() ?: return null
        for (child in children) {
            if (child.isFile && child.name == fileName) return child
            if (child.isDirectory) {
                val found = findFileRecursive(child, fileName)
                if (found != null) return found
            }
        }
        return null
    }

    private fun refreshExternalErrorMarkersForCurrentFile() {
        val editor = codeEditorView ?: return
        if (activeViewerKind != FileKind.TEXT && activeViewerKind != FileKind.UNKNOWN) return

        val lines = HashSet<Int>()
        val openFile = currentOpenFile
        if (openFile != null) {
            for (error in buildErrors) {
                val errorFile = resolveBuildFile(error.path)
                if (sameFile(errorFile, openFile)) lines.add(error.line)
            }
        }
        editor.setExternalErrorLines(lines)
    }

    private fun sameFile(first: File?, second: File?): Boolean {
        if (first == null || second == null) return false
        return try {
            first.canonicalFile == second.canonicalFile
        } catch (_: IOException) {
            first.absolutePath == second.absolutePath
        }
    }

    private fun openBuildError(error: BuildError) {
        var file = resolveBuildFile(error.path)
        if (file == null) file = findNearbySourceFile(error.path)

        if (file == null) {
            val fileName = error.path.substringAfterLast('/').ifEmpty { error.path }
            val msg = StringBuilder()
            msg.append("الملف غير موجود في مشروعك\n\n")
            msg.append("الاسم: ").append(fileName).append("\n")
            msg.append("السطر: ").append(error.line)
            if (error.column > 0) msg.append(":").append(error.column)
            msg.append("\n\nقد يكون ملفاً مؤقتاً أثناء البناء على GitHub")

            AlertDialog.Builder(this)
                .setTitle("تعذّر فتح الملف")
                .setMessage(msg.toString())
                .setPositiveButton("نسخ المسار") { _, _ ->
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    cm.setPrimaryClip(
                        android.content.ClipData.newPlainText("path", error.path)
                    )
                    Toast.makeText(this, "تم نسخ المسار", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("إغلاق", null)
                .show()
            return
        }

        openFileInEditor(file)

        if (file.isDirectory) {
            Toast.makeText(
                this,
                "تم فتح المجلد: ${file.name}",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (activeViewerKind == FileKind.TEXT || activeViewerKind == FileKind.UNKNOWN) {
            val targetFile = file
            codeEditorView?.post {
                codeEditorView?.goToPosition(error.line, error.column)
            }

            val note = if (targetFile.name.equals(
                    error.path.substringAfterLast('/'),
                    ignoreCase = true
                )
            ) {
                "السطر ${error.line}" +
                        if (error.column > 0) ":${error.column}" else ""
            } else {
                "تم فتح ملف قريب: ${targetFile.name}"
            }
            Toast.makeText(this, note, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildAllScreens() {
        buildProjectsListScreen()
        buildCreateProjectScreen(ProjectType.FLUTTER)
        buildEditorScreen()
    }

    // ============================================================
    // Projects List Screen
    // ============================================================
    private fun buildProjectsListScreen() {
        layoutProjectsList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BACKGROUND)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val appBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val brand = TextView(this).apply {
            text = "KlencodIDE"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(24f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.02f
            alpha = 0f
            translationY = -40f
            scaleX = 0.85f
            scaleY = 0.85f
        }
        appBar.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        layoutProjectsList.addView(appBar, LinearLayout.LayoutParams(-1, -2))

        brand.post { playBrandAnimation(brand) }

        val quickActions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(12))
        }

        val actionsRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val newProjectBtn = UiStyle.primaryButton(this, "+ مشروع جديد").apply {
            setOnClickListener {
                ProjectTypeSelectorDialog.show(this@MainActivity) { type ->
                    showCreateProjectScreen(type)
                }
            }
        }
        val npParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            marginEnd = dp(6)
        }
        actionsRow1.addView(newProjectBtn, npParams)

        val templatesBtn = UiStyle.ghostButton(this, "القوالب الجاهزة").apply {
            setOnClickListener {
                startActivity(Intent(this@MainActivity, TemplateStoreActivity::class.java))
            }
        }
        val tpParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            marginStart = dp(6)
        }
        actionsRow1.addView(templatesBtn, tpParams)

        quickActions.addView(actionsRow1, LinearLayout.LayoutParams(-1, -2))

        val actionsRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val row2Params = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        }
        actionsRow2.layoutParams = row2Params

        val importZipBtn = UiStyle.ghostButton(this, "استيراد مشروع من ZIP").apply {
            setOnClickListener { openProjectZipImport() }
        }
        actionsRow2.addView(importZipBtn, LinearLayout.LayoutParams(-1, dp(46)))

        quickActions.addView(actionsRow2)

        layoutProjectsList.addView(quickActions, LinearLayout.LayoutParams(-1, -2))

        val sectionHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(8))
        }

        val sectionTitle = TextView(this).apply {
            text = "مشاريعك"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(15f)
            typeface = Typeface.DEFAULT_BOLD
        }
        sectionHeader.addView(sectionTitle, LinearLayout.LayoutParams(0, -2, 1f))

        val projectCount = TextView(this).apply {
            text = ""
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(12f)
            tag = "project_count"
        }
        sectionHeader.addView(projectCount)

        layoutProjectsList.addView(sectionHeader, LinearLayout.LayoutParams(-1, -2))

        val listContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        layoutProjectsList.addView(listContainer, listContainer.layoutParams)

        val emptyState = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(40), dp(24), dp(40))
            visibility = View.GONE
        }

        val emptyShape = TextView(this).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(COLOR_SURFACE_ALT)
                setStroke(dp(1), COLOR_BORDER)
            }
        }
        val eshParams = LinearLayout.LayoutParams(dp(96), dp(96)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        emptyState.addView(emptyShape, eshParams)

        val emptyTitle = TextView(this).apply {
            text = "لا توجد مشاريع بعد"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(16f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val etParams = LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = dp(16)
        }
        emptyState.addView(emptyTitle, etParams)

        val emptySub = TextView(this).apply {
            text = "ابدأ بإنشاء مشروعك الأول"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(13f)
            gravity = Gravity.CENTER
        }
        val esParams = LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = dp(6)
        }
        emptyState.addView(emptySub, esParams)

        val emptyNewBtn = UiStyle.primaryButton(this, "+ إنشاء مشروع").apply {
            setOnClickListener {
                ProjectTypeSelectorDialog.show(this@MainActivity) { type ->
                    showCreateProjectScreen(type)
                }
            }
        }
        val enParams = LinearLayout.LayoutParams(-2, dp(44)).apply {
            topMargin = dp(24)
            gravity = Gravity.CENTER
        }
        emptyState.addView(emptyNewBtn, enParams)

        listContainer.addView(emptyState, FrameLayout.LayoutParams(-1, -1))

        lvProjects = ListView(this).apply {
            setBackgroundColor(COLOR_BACKGROUND)
            divider = null
            dividerHeight = dp(8)
            isVerticalScrollBarEnabled = false
            setPadding(dp(12), dp(4), dp(12), dp(8))
            clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(-1, -1)

            onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                openProject(projectsList[position])
            }

            onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
                showProjectOptionsDialog(projectsList[position])
                true
            }

            emptyView = emptyState
        }

        listContainer.addView(lvProjects)
    }

    private fun playBrandAnimation(brand: TextView) {
        val fadeIn = ObjectAnimator.ofFloat(brand, "alpha", 0f, 1f).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
        }

        val slideDown = ObjectAnimator.ofFloat(brand, "translationY", -40f, 0f).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
        }

        val scaleUpX = ObjectAnimator.ofFloat(brand, "scaleX", 0.85f, 1f).apply {
            duration = 600
            interpolator = OvershootInterpolator(1.4f)
        }
        val scaleUpY = ObjectAnimator.ofFloat(brand, "scaleY", 0.85f, 1f).apply {
            duration = 600
            interpolator = OvershootInterpolator(1.4f)
        }

        val phase1 = AnimatorSet().apply {
            playTogether(fadeIn, slideDown, scaleUpX, scaleUpY)
            startDelay = 80
        }

        val pulseX = ObjectAnimator.ofFloat(brand, "scaleX", 1f, 1.04f, 1f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
        }
        val pulseY = ObjectAnimator.ofFloat(brand, "scaleY", 1f, 1.04f, 1f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
        }

        val phase2 = AnimatorSet().apply {
            playTogether(pulseX, pulseY)
        }

        AnimatorSet().apply {
            playSequentially(phase1, phase2)
            start()
        }
    }

    // ============================================================
    // Create Project Screen
    // ============================================================
    private fun showCreateProjectScreen(type: ProjectType) {
        currentCreateType = type
        buildCreateProjectScreen(type)
        showScreen(SCREEN_CREATE_PROJECT, true)
    }

    private fun buildCreateProjectScreen(type: ProjectType = ProjectType.FLUTTER) {
        currentCreateType = type

        layoutCreateProject = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(COLOR_BACKGROUND)
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(24))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val tvCreateTitle = TextView(this).apply {
            text = "إنشاء مشروع ${type.displayName} جديد"
            setTextSize(22f)
            setTextColor(COLOR_TEXT_PRIMARY)
            setPadding(0, 0, 0, 8)
        }
        content.addView(tvCreateTitle)

        val tvTypeDesc = TextView(this).apply {
            text = type.description
            setTextSize(12f)
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(0, 0, 0, 24)
        }
        content.addView(tvTypeDesc)

        content.addView(TextView(this).apply {
            text = "اسم التطبيق:"
            setTextSize(14f)
            setTextColor(COLOR_TEXT_PRIMARY)
            setPadding(0, 0, 0, 8)
        })

        etAppName = EditText(this).apply {
            hint = "مثلاً: My App"
            setHintTextColor(COLOR_TEXT_SECONDARY)
            setTextColor(COLOR_TEXT_PRIMARY)
            background = UiStyle.rounded(
                this@MainActivity, COLOR_SURFACE, 12, COLOR_BORDER, 1
            )
            setPadding(16, 14, 16, 14)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        content.addView(etAppName)

        content.addView(TextView(this).apply {
            text = "ملاحظة: سيُستخدم اسم التطبيق كاسم المستودع على GitHub. " +
                    "إذا تكرر، سيُضاف رقم تلقائياً."
            setTextSize(11f)
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(0, dp(6), 0, 0)
            setLineSpacing(0f, 1.1f)
        })

        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 16)
        })

        content.addView(TextView(this).apply {
            text = "اسم الحزمة:"
            setTextSize(14f)
            setTextColor(COLOR_TEXT_PRIMARY)
            setPadding(0, 0, 0, 8)
        })

        etPackageName = EditText(this).apply {
            hint = "مثلاً: com.example.myapp"
            setHintTextColor(COLOR_TEXT_SECONDARY)
            setTextColor(COLOR_TEXT_PRIMARY)
            background = UiStyle.rounded(
                this@MainActivity, COLOR_SURFACE, 12, COLOR_BORDER, 1
            )
            setPadding(16, 14, 16, 14)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        content.addView(etPackageName)

        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 16)
        })

        content.addView(TextView(this).apply {
            text = "Target API:"
            setTextSize(14f)
            setTextColor(COLOR_TEXT_PRIMARY)
            setPadding(0, 0, 0, 8)
        })

        val apiLevels = arrayOf(
            "21 (Android 5.0)", "24 (Android 7.0)", "28 (Android 9)",
            "31 (Android 12)", "33 (Android 13)", "34 (Android 14)",
            "35 (Android 15)", "36 (Android 16)"
        )

        spinnerApi = Spinner(this).apply {
            adapter = UiStyle.spinnerAdapter(this@MainActivity, apiLevels)
            setSelection(5)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            setPadding(16, 12, 16, 12)
        }
        UiStyle.styleSpinner(spinnerApi)
        content.addView(spinnerApi)

        if (type.requiresFlutterVersion) {
            content.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, 16)
            })

            tvFlutterVersionLabel = TextView(this).apply {
                text = "إصدار Flutter:"
                setTextSize(14f)
                setTextColor(COLOR_TEXT_PRIMARY)
                setPadding(0, 0, 0, 8)
            }
            content.addView(tvFlutterVersionLabel)

            spinnerFlutterVersion = Spinner(this).apply {
                adapter = UiStyle.spinnerAdapter(
                    this@MainActivity, FLUTTER_VERSION_LABELS
                )
                setSelection(0)
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                setPadding(16, 12, 16, 12)
            }
            UiStyle.styleSpinner(spinnerFlutterVersion)
            content.addView(spinnerFlutterVersion)
        } else {
            spinnerFlutterVersion = null
            tvFlutterVersionLabel = null
        }

        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 16)
        })

        content.addView(TextView(this).apply {
            text = "نوع المستودع على GitHub:"
            setTextSize(14f)
            setTextColor(COLOR_TEXT_PRIMARY)
            setPadding(0, 0, 0, 8)
        })

        repoTypeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val rbPublic = RadioButton(this).apply {
            text = "عام (Public) - موصى به"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            id = android.R.id.button1
        }
        repoTypeGroup!!.addView(rbPublic)

        repoTypeGroup!!.addView(TextView(this).apply {
            text = "بناء مجاني غير محدود\nمناسب للمشاريع التعليمية\nالكود مرئي للجميع"
            setTextSize(11f)
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(dp(30), 0, 0, dp(10))
            setLineSpacing(0f, 1.15f)
        })

        val rbPrivate = RadioButton(this).apply {
            text = "خاص (Private)"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            id = android.R.id.button2
        }
        repoTypeGroup!!.addView(rbPrivate)

        repoTypeGroup!!.addView(TextView(this).apply {
            text = "كودك خاص تماماً\n2,000 دقيقة بناء مجاناً/شهر\nبعدها قد تحتاج دفع"
            setTextSize(11f)
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(dp(30), 0, 0, dp(10))
            setLineSpacing(0f, 1.15f)
        })

        repoTypeGroup!!.check(android.R.id.button1)
        content.addView(repoTypeGroup, LinearLayout.LayoutParams(-1, -2))

        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 16)
        })

        content.addView(TextView(this).apply {
            text = "أيقونة التطبيق:"
            setTextSize(14f)
            setTextColor(COLOR_TEXT_PRIMARY)
            setPadding(0, 0, 0, 8)
        })

        val iconRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        btnSelectIcon = Button(this).apply {
            text = "اختيار أيقونة"
            background = UiStyle.rounded(
                this@MainActivity, COLOR_SURFACE, 12, COLOR_BORDER, 1
            )
            minHeight = dp(48)
            setTextColor(COLOR_TEXT_PRIMARY)
            isAllCaps = false
            setPadding(16, 12, 16, 12)
            setOnClickListener { pickIconForCreateProject() }
        }
        iconRow.addView(btnSelectIcon)

        ivIconPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(60, 60)
            background = UiStyle.rounded(
                this@MainActivity, COLOR_SURFACE, 12, COLOR_BORDER, 1
            )
            setPadding(4, 4, 4, 4)
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        iconRow.addView(ivIconPreview)

        tvIconSelected = TextView(this).apply {
            text = "لم يتم اختيار أيقونة (سيتم استخدام الافتراضية)"
            setTextSize(11f)
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(16, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        iconRow.addView(tvIconSelected)

        content.addView(iconRow)

        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 32)
        })

        val createButtonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val btnCancel = Button(this).apply {
            text = "إلغاء"
            background = UiStyle.rounded(
                this@MainActivity, COLOR_SURFACE_ALT, 12, COLOR_BORDER, 1
            )
            minHeight = dp(48)
            setTextColor(COLOR_TEXT_PRIMARY)
            isAllCaps = false
            setPadding(16, 14, 16, 14)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            setOnClickListener {
                showScreen(SCREEN_PROJECTS_LIST, true)
                resetIconSelection()
            }
        }
        createButtonRow.addView(btnCancel)

        val btnCreate = Button(this).apply {
            text = "إنشاء"
            background = UiStyle.rounded(
                this@MainActivity, COLOR_PRIMARY, 12, COLOR_PRIMARY_LIGHT, 1
            )
            minHeight = dp(48)
            setTextColor(COLOR_TEXT_PRIMARY)
            isAllCaps = false
            setPadding(16, 14, 16, 14)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
            setOnClickListener { createProject() }
        }
        createButtonRow.addView(btnCreate)

        content.addView(createButtonRow)

        scrollView.addView(content, FrameLayout.LayoutParams(-1, -2))
        layoutCreateProject.addView(scrollView, LinearLayout.LayoutParams(-1, -1))
    }

    private fun resetIconSelection() {
        selectedIconUri = null
        ivIconPreview?.visibility = View.GONE
        ivIconPreview?.setImageURI(null)
        tvIconSelected?.text = "لم يتم اختيار أيقونة (سيتم استخدام الافتراضية)"
        tvIconSelected?.setTextColor(COLOR_TEXT_SECONDARY)
    }

    private fun pickIconForCreateProject() {
        val intent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        ).apply {
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_PICK_ICON_CREATE)
    }

    private fun cropImageToSquare(imageUri: Uri): Uri? {
        return try {
            val inputStream = contentResolver.openInputStream(imageUri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val width = originalBitmap.width
            val height = originalBitmap.height
            val size = minOf(width, height)
            val left = (width - size) / 2
            val top = (height - size) / 2
            val squaredBitmap = Bitmap.createBitmap(originalBitmap, left, top, size, size)
            val resizedBitmap = Bitmap.createScaledBitmap(squaredBitmap, 512, 512, true)

            val tempFile = File(cacheDir, "temp_icon_${System.currentTimeMillis()}.png")
            FileOutputStream(tempFile).use { fos ->
                resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }

            if (squaredBitmap != originalBitmap) squaredBitmap.recycle()
            if (resizedBitmap != squaredBitmap) resizedBitmap.recycle()
            originalBitmap.recycle()

            Uri.fromFile(tempFile)
        } catch (e: Exception) {
            addLog("خطأ في قص الصورة: ${e.message}")
            imageUri
        }
    }

    // ============================================================
    // Tab Strip
    // ============================================================
    private fun buildTabStrip() {
        val editorLayout = layoutEditor ?: return

        tabScrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(COLOR_BACKGROUND)
            setPadding(dp(4), dp(2), dp(4), dp(2))
            layoutParams = LinearLayout.LayoutParams(-1, dp(36))
        }

        tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        tabScrollView!!.addView(
            tabStrip,
            FrameLayout.LayoutParams(-2, -1)
        )
        editorLayout.addView(tabScrollView)
    }

    private fun renderTabStrip() {
        val strip = tabStrip ?: return

        if (strip.childCount != openTabs.size) {
            strip.removeAllViews()
            for (i in openTabs.indices) {
                val tab = createTabView(i)
                strip.addView(
                    tab,
                    LinearLayout.LayoutParams(dp(120), -1)
                )
            }
        } else {
            for (i in openTabs.indices) {
                updateTabView(strip.getChildAt(i), i)
            }
        }

        updateFavoriteButton()
        updateSaveButtonVisibility()

        val scroll = tabScrollView
        if (scroll != null && activeTabIndex in 0 until strip.childCount) {
            val idx = activeTabIndex
            scroll.post {
                if (idx < strip.childCount) {
                    scroll.smoothScrollTo(strip.getChildAt(idx).left, 0)
                }
            }
        }
    }

    private fun createTabView(index: Int): LinearLayout {
        val state = openTabs[index]
        val file = File(state.absolutePath)

        val tab = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(2), 0)
            tag = index
        }

        val label = TextView(this).apply {
            tag = "tab_label"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(11f)
            isSingleLine = true
            maxWidth = dp(90)
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setPadding(0, 0, dp(4), 0)
        }
        tab.addView(label, LinearLayout.LayoutParams(dp(90), -1))

        val close = Button(this).apply {
            tag = index
            text = "×"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(14f)
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { v ->
                val idx = v.tag as? Int
                if (idx != null) closeTab(idx)
            }
        }
        tab.addView(close, LinearLayout.LayoutParams(dp(24), -1))

        tab.setOnClickListener { v ->
            val idx = v.tag as? Int
            if (idx != null) switchToTab(idx)
        }

        tab.setOnLongClickListener { v ->
            val idx = v.tag as? Int
            if (idx != null && idx < openTabs.size) {
                toggleFavorite(File(openTabs[idx].absolutePath))
            }
            true
        }

        updateTabView(tab, index)
        return tab
    }

    private fun updateTabView(tab: View, index: Int) {
        if (tab !is LinearLayout) return
        if (index !in openTabs.indices) return

        val state = openTabs[index]
        val file = File(state.absolutePath)

        tab.tag = index
        tab.findViewWithTag<TextView>("tab_label")?.text =
            file.name + if (state.dirty) " •" else ""

        var closeView: View? = null
        for (i in 0 until tab.childCount) {
            val c = tab.getChildAt(i)
            if (c is Button) { closeView = c; break }
        }
        closeView?.tag = index

        val isActive = (index == activeTabIndex)
        tab.background = UiStyle.rounded(
            this,
            if (isActive) COLOR_PRIMARY else COLOR_SURFACE_ALT,
            8, COLOR_BORDER, 1
        )
        closeView?.background = UiStyle.rounded(
            this,
            if (isActive) COLOR_PRIMARY else COLOR_SURFACE_ALT,
            6, COLOR_BORDER, 0
        )
    }

    private fun showOpenFileDialog() {
        val projDir = currentProjectDir ?: return
        try {
            val files = ProjectFileService(projDir).listFiles()
            if (files.isEmpty()) {
                Toast.makeText(this, "لا توجد ملفات قابلة للفتح", Toast.LENGTH_SHORT).show()
                return
            }
            val dialog = AlertDialog.Builder(this)
                .setTitle("فتح ملف في تبويب")
                .setAdapter(
                    UiStyle.dialogListAdapter(this, files.toTypedArray())
                ) { _, which ->
                    openFileInEditor(File(projDir, files[which]))
                }
                .setNegativeButton("إلغاء", null)
                .create()
            dialog.show()
            styleDialog(dialog)
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر قراءة ملفات المشروع", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showGoToLineDialog() {
        if (codeEditorView == null || currentOpenFile == null) return
        if (activeViewerKind != FileKind.TEXT && activeViewerKind != FileKind.UNKNOWN) {
            Toast.makeText(
                this,
                "الانتقال إلى سطر متاح للملفات النصية فقط",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val input = EditText(this).apply {
            hint = "رقم السطر"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(COLOR_TEXT_PRIMARY)
            setHintTextColor(COLOR_TEXT_SECONDARY)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("الانتقال إلى رقم سطر")
            .setView(input)
            .setPositiveButton("انتقال", null)
            .setNegativeButton("إلغاء", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val line = input.text.toString().trim().toInt()
                    if (line < 1) throw NumberFormatException()
                    codeEditorView?.goToLine(line)
                    dialog.dismiss()
                } catch (_: NumberFormatException) {
                    input.error = "أدخل رقم سطر صحيح"
                }
            }
        }
        dialog.show()
        styleDialog(dialog)
    }

    private fun showFavoritesDialog() {
        val projDir = currentProjectDir ?: return
        val favorites = mutableListOf<String>()
        for (path in favoritePaths) {
            if (File(projDir, path).isFile) favorites.add(path)
        }
        favorites.sort()

        if (favorites.isEmpty()) {
            Toast.makeText(this, "لا توجد ملفات مفضلة", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("الملفات المفضلة")
            .setAdapter(
                UiStyle.dialogListAdapter(this, favorites.toTypedArray())
            ) { _, which ->
                openFileInEditor(File(projDir, favorites[which]))
            }
            .setNegativeButton("إغلاق", null)
            .create()
        dialog.show()
        styleDialog(dialog)
    }

    private fun toggleFavoriteCurrent() {
        currentOpenFile?.let { toggleFavorite(it) }
    }

    private fun toggleFavorite(file: File) {
        if (currentProjectDir == null) return
        val path = getRelativePath(file)
        if (favoritePaths.contains(path)) {
            favoritePaths = favoritePaths - path
            Toast.makeText(this, "أزيل من المفضلة", Toast.LENGTH_SHORT).show()
        } else {
            favoritePaths = favoritePaths + path
            Toast.makeText(this, "أضيف إلى المفضلة", Toast.LENGTH_SHORT).show()
        }
        saveEditorProjectState()
        updateFavoriteButton()
        renderTabStrip()
    }

    private fun updateFavoriteButton() {
        val fav = btnFavoriteCurrent ?: return
        val isFav = currentOpenFile?.let {
            favoritePaths.contains(getRelativePath(it))
        } ?: false
        fav.text = if (isFav) "★ مفضل" else "☆ مفضل"
    }

    private fun formatCurrentDart() {
        val openFile = currentOpenFile
        if (openFile == null ||
            !openFile.name.lowercase(Locale.US).endsWith(".dart")
        ) {
            Toast.makeText(this, "التنسيق متاح لملفات Dart فقط", Toast.LENGTH_SHORT).show()
            return
        }
        if (activeViewerKind != FileKind.TEXT && activeViewerKind != FileKind.UNKNOWN) return

        val editor = codeEditorView ?: return
        val formatted = formatDartSource(editor.getText())
        editor.setText(formatted)
        updateActiveTabFromEditor()
        saveCurrentFile()
        Toast.makeText(this, "تم تنسيق ملف Dart", Toast.LENGTH_SHORT).show()
    }

    private fun formatDartSource(source: String?): String {
        if (source.isNullOrEmpty()) return ""
        val lines = source.replace("\r", "").split("\n")
        val result = StringBuilder()
        var indent = 0

        for (original in lines) {
            val line = original.trim()
            if (line.isEmpty()) {
                result.append('\n')
                continue
            }
            if (line.startsWith("}") || line.startsWith(")") || line.startsWith("]")) {
                indent = maxOf(0, indent - 1)
            }
            repeat(indent) { result.append("  ") }
            result.append(line).append('\n')

            val opens = countBracesOutsideStrings(line, '{')
            val closes = countBracesOutsideStrings(line, '}')
            indent = maxOf(0, indent + opens - closes)
        }

        if (result.isNotEmpty()) result.setLength(result.length - 1)
        return result.toString()
    }

    private fun countBracesOutsideStrings(line: String, target: Char): Int {
        var single = false
        var dbl = false
        var escaped = false
        var count = 0

        for (c in line) {
            if (escaped) { escaped = false; continue }
            if (c == '\\' && (single || dbl)) { escaped = true; continue }
            if (c == '\'' && !dbl) single = !single
            else if (c == '"' && !single) dbl = !dbl
            else if (!single && !dbl && c == target) count++
        }
        return count
    }

    private fun showProjectSearchDialog() {
        val projDir = currentProjectDir ?: return
        if (!projDir.isDirectory) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val query = EditText(this).apply {
            hint = "النص المطلوب البحث عنه"
            setTextColor(COLOR_TEXT_PRIMARY)
            setHintTextColor(COLOR_TEXT_SECONDARY)
        }
        root.addView(query, LinearLayout.LayoutParams(-1, dp(48)))

        val replacement = EditText(this).apply {
            hint = "النص البديل (اختياري)"
            setTextColor(COLOR_TEXT_PRIMARY)
            setHintTextColor(COLOR_TEXT_SECONDARY)
        }
        root.addView(replacement, LinearLayout.LayoutParams(-1, dp(48)))

        val resultCount = TextView(this).apply {
            setTextColor(COLOR_TEXT_BLUE)
            setPadding(0, dp(6), 0, dp(6))
        }
        root.addView(resultCount)

        val results = ListView(this).apply {
            setBackgroundColor(COLOR_SURFACE)
            divider = null
        }
        root.addView(results, LinearLayout.LayoutParams(-1, dp(220)))

        val resultPaths = mutableListOf<String>()
        val resultLines = mutableListOf<Int>()

        val dialog = AlertDialog.Builder(this)
            .setTitle("بحث واستبدال في المشروع")
            .setView(root)
            .setNegativeButton("إغلاق", null)
            .create()

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val searchBtn = addDialogButton("بحث")
        val replaceBtn = addDialogButton("استبدال الكل")

        buttons.addView(searchBtn, LinearLayout.LayoutParams(0, dp(42), 1f))
        buttons.addView(replaceBtn, LinearLayout.LayoutParams(0, dp(42), 1f))
        root.addView(buttons)

        searchBtn.setOnClickListener {
            runProjectSearch(
                query.text.toString(), resultCount, results,
                resultPaths, resultLines
            )
        }

        replaceBtn.setOnClickListener {
            val find = query.text.toString()
            if (find.trim().isEmpty()) {
                query.error = "أدخل نص البحث أولاً"
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("تأكيد الاستبدال")
                .setMessage("سيتم استبدال النص في كل ملفات المشروع المطابقة. هل تريد المتابعة؟")
                .setPositiveButton("استبدال") { _, _ ->
                    replaceInProject(
                        find, replacement.text.toString(),
                        resultCount, results, resultPaths, resultLines
                    )
                }
                .setNegativeButton("إلغاء", null)
                .create()
                .also { confirm ->
                    confirm.show()
                    styleDialog(confirm)
                }
        }

        results.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (position < resultPaths.size) {
                openFileInEditor(File(projDir, resultPaths[position]))
                if (activeViewerKind == FileKind.TEXT ||
                    activeViewerKind == FileKind.UNKNOWN
                ) {
                    codeEditorView?.goToLine(resultLines[position])
                }
                dialog.dismiss()
            }
        }

        dialog.show()
        styleDialog(dialog)
    }

    private fun addDialogButton(title: String): Button {
        return Button(this).apply {
            text = title
            setTextColor(COLOR_TEXT_BLUE)
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            setBackgroundColor(COLOR_SURFACE)
        }
    }

    private fun runProjectSearch(
        query: String?,
        resultCount: TextView,
        results: ListView,
        resultPaths: MutableList<String>,
        resultLines: MutableList<Int>
    ) {
        val projDir = currentProjectDir ?: return
        resultPaths.clear()
        resultLines.clear()
        val labels = mutableListOf<String>()

        if (query.isNullOrEmpty()) {
            resultCount.text = "أدخل نصًا للبحث"
            results.adapter = null
            return
        }

        try {
            val service = ProjectFileService(projDir)
            val pattern = Pattern.compile(
                Pattern.quote(query),
                Pattern.CASE_INSENSITIVE
            )

            for (relative in service.listFiles()) {
                if (!FileKind.isTextLike(relative)) continue

                val content = try {
                    service.readFile(relative)
                } catch (_: Exception) {
                    continue
                }

                val lines = content.replace("\r", "").split("\n")
                for (lineIndex in lines.indices) {
                    val matcher = pattern.matcher(lines[lineIndex])
                    if (matcher.find()) {
                        resultPaths.add(relative)
                        resultLines.add(lineIndex + 1)
                        labels.add(
                            "$relative:${lineIndex + 1}  ${lines[lineIndex].trim()}"
                        )
                    }
                }
            }

            val adapter = object : ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, labels
            ) {
                override fun getView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup
                ): View {
                    val text = super.getView(position, convertView, parent) as TextView
                    text.setTextColor(COLOR_TEXT_PRIMARY)
                    text.setTextSize(11f)
                    text.setPadding(dp(8), dp(8), dp(8), dp(8))
                    return text
                }
            }
            results.adapter = adapter
            resultCount.text = "${labels.size} نتيجة في المشروع"
        } catch (e: Exception) {
            resultCount.text = "تعذر البحث: ${e.message}"
            results.adapter = null
        }
    }

    private fun replaceInProject(
        query: String,
        replacement: String,
        resultCount: TextView,
        results: ListView,
        resultPaths: MutableList<String>,
        resultLines: MutableList<Int>
    ) {
        val projDir = currentProjectDir ?: return
        var changedFiles = 0
        var replacements = 0

        try {
            val service = ProjectFileService(projDir)
            val pattern = Pattern.compile(
                Pattern.quote(query),
                Pattern.CASE_INSENSITIVE
            )

            for (relative in service.listFiles()) {
                if (!FileKind.isTextLike(relative)) continue

                val content = try {
                    service.readFile(relative)
                } catch (_: Exception) {
                    continue
                }

                val matcher = pattern.matcher(content)
                var count = 0
                while (matcher.find()) count++

                if (count > 0) {
                    service.writeFile(
                        relative,
                        pattern.matcher(content).replaceAll(
                            java.util.regex.Matcher.quoteReplacement(replacement)
                        )
                    )
                    changedFiles++
                    replacements += count
                    syncOpenTabFromDisk(File(projDir, relative))
                }
            }

            resultCount.text = "تم استبدال $replacements تطابق في $changedFiles ملف"
            runProjectSearch(query, resultCount, results, resultPaths, resultLines)
        } catch (e: Exception) {
            resultCount.text = "تعذر الاستبدال: ${e.message}"
        }
    }

    private fun syncOpenTabFromDisk(file: File) {
        val projDir = currentProjectDir ?: return
        val state = findTab(file.absolutePath) ?: return
        try {
            val content = ProjectFileService(projDir).readFile(getRelativePath(file))
            state.content = content
            state.dirty = false
            state.loaded = true

            if (currentOpenFile?.absolutePath == file.absolutePath) {
                if (activeViewerKind == FileKind.TEXT ||
                    activeViewerKind == FileKind.UNKNOWN
                ) {
                    codeEditorView?.setTextImmediate(content, file.absolutePath)
                }
            }
            renderTabStrip()
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // Editor Screen
    // ============================================================
    private fun buildEditorScreen() {
        drawerLayout = DrawerLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            setBackgroundColor(COLOR_BACKGROUND)
        }

        layoutEditor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            setBackgroundColor(COLOR_BACKGROUND)
        }

        toolbar = Toolbar(this).apply {
            setBackgroundColor(COLOR_SURFACE)
            elevation = dp(3).toFloat()
            setTitleTextColor(COLOR_TEXT_PRIMARY)
            title = "Klencod IDE"
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setTitleMargin(0, 0, 0, 0)
            setContentInsetsRelative(dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(-1, dp(48))
        }

        btnToolbarSave = Button(this).apply {
            text = "حفظ"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setPadding(dp(16), 0, dp(16), 0)
            background = UiStyle.rounded(
                this@MainActivity, COLOR_PRIMARY, 10, COLOR_PRIMARY_LIGHT, 1
            )
            visibility = View.GONE
            setOnClickListener {
                saveAllOpenTabs()
                Toast.makeText(
                    this@MainActivity,
                    "تم حفظ جميع التعديلات",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val saveParams = Toolbar.LayoutParams(
            Toolbar.LayoutParams.WRAP_CONTENT,
            dp(36)
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            rightMargin = dp(8)
        }
        toolbar!!.addView(btnToolbarSave, saveParams)

        layoutEditor!!.addView(toolbar)

        tvProjectInfo = TextView(this).apply {
            setTextSize(11f)
            setTextColor(COLOR_TEXT_BLUE)
            setBackgroundColor(COLOR_SURFACE_ALT)
            setPadding(dp(12), dp(3), dp(12), dp(3))
            visibility = View.GONE
        }

        tvCurrentFilePath = TextView(this).apply {
            setTextSize(11f)
            setTextColor(COLOR_TEXT_SECONDARY)
            setBackgroundColor(COLOR_SURFACE_ALT)
            setPadding(dp(12), dp(2), dp(12), dp(2))
            visibility = View.GONE
        }

        buildTabStrip()

        viewerContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            setBackgroundColor(COLOR_BACKGROUND)
        }
        layoutEditor!!.addView(viewerContainer)

        codeEditorView = CodeEditorView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        codeToolbar = CodeToolbar(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            visibility = View.GONE
        }
        layoutEditor!!.addView(codeToolbar)

        codeToolbar?.setEditor(codeEditorView)

        mainContent.addView(layoutEditor)

        buildLogPanel()

        val logParams = LinearLayout.LayoutParams(-1, logPanelCollapsedHeight)
        logParams.setMargins(0, 0, 0, 0)
        mainContent.addView(logPanel, logParams)

        drawerLayout!!.addView(mainContent)

        // ============================================================
        // Drawer Content
        // ============================================================
        drawerContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_SURFACE)
            layoutParams = DrawerLayout.LayoutParams(
                minOf(dp(340), (resources.displayMetrics.widthPixels * 0.88f).toInt()),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START
            )
        }

        val drawerInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10))
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        buildAccountCardInDrawer()
        drawerInner.addView(accountCardView)

        val drawerHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(8), dp(10))
            setBackgroundColor(COLOR_SURFACE_ALT)
        }

        val tvDrawerTitle = TextView(this).apply {
            text = "هيكل المشروع"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(14f)
            typeface = Typeface.DEFAULT_BOLD
        }
        drawerHeader.addView(
            tvDrawerTitle,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val btnAddAtRoot = Button(this).apply {
            text = "+"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(20f)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = UiStyle.rounded(
                this@MainActivity, COLOR_PRIMARY, 8, COLOR_PRIMARY_LIGHT, 1
            )
            setOnClickListener { showRootAddDialog() }
        }
        drawerHeader.addView(
            btnAddAtRoot,
            LinearLayout.LayoutParams(dp(36), dp(36))
        )

        drawerInner.addView(drawerHeader, LinearLayout.LayoutParams(-1, -2))

        rvFileTree = RecyclerView(this).apply {
            setBackgroundColor(COLOR_SURFACE)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            itemAnimator = DefaultItemAnimator()
            setHasFixedSize(false)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
        }

        fileTreeLayoutManager = LinearLayoutManager(this)
        rvFileTree!!.layoutManager = fileTreeLayoutManager

        fileTreeAdapter = FileTreeAdapter(
            object : OnFileTreeListener {
                override fun onFileClick(file: File, isDirectory: Boolean) {
                    if (isDirectory) {
                        toggleDirectory(file)
                    } else {
                        openFileInEditor(file)
                        drawerLayout?.closeDrawer(Gravity.START)
                    }
                }

                override fun onFileLongClick(file: File) {
                    selectedFileForDialog = file
                    showFileOptionsDialog()
                }
            },
            COLOR_TEXT_PRIMARY,
            dp(1),
            COLOR_ARROW,
            R.drawable.ic_tree_folder,
            R.drawable.ic_tree_folder_open,
            R.drawable.ic_tree_file
        )
        rvFileTree!!.adapter = fileTreeAdapter

        drawerInner.addView(rvFileTree)

        val buttonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(COLOR_SURFACE)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnProjectSettings = createDrawerButton("إعدادات المشروع", false).apply {
            setOnClickListener {
                drawerLayout?.closeDrawer(Gravity.START)
                showProjectSettings()
            }
        }
        row1.addView(btnProjectSettings, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            setMargins(0, 0, dp(4), 0)
        })

        val btnAiAssistant = createDrawerButton("الذكاء الاصطناعي", true).apply {
            setOnClickListener {
                drawerLayout?.closeDrawer(Gravity.START)
                openAiChat()
            }
        }
        row1.addView(btnAiAssistant, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            setMargins(dp(4), 0, 0, 0)
        })

        buttonsContainer.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val row2Params = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        }
        row2.layoutParams = row2Params

        val btnExportZip = createDrawerButton("تصدير ZIP", false).apply {
            setOnClickListener { exportProjectAsZip() }
        }
        row2.addView(btnExportZip, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            setMargins(0, 0, dp(4), 0)
        })

        val btnBuildApk = createDrawerButton("بناء APK", false).apply {
            setOnClickListener { buildApk() }
        }
        row2.addView(btnBuildApk, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            setMargins(dp(4), 0, 0, 0)
        })

        buttonsContainer.addView(row2)

        // سيتم ربط زر البناء المحلي مجدداً بمحرك CodeAssist بعد دمج وحداته الأساسية.

        // ⭐ v14.0: صف "تغيير طريقة الرفع"
        val row2b = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val row2bParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        }
        row2b.layoutParams = row2bParams

        btnChangeUploadMethodInDrawer = createDrawerButton(
            buildUploadMethodButtonLabel(), false
        ).apply {
            setOnClickListener {
                drawerLayout?.closeDrawer(Gravity.START)
                showUploadMethodPreferencesDialog()
            }
        }
        row2b.addView(
            btnChangeUploadMethodInDrawer,
            LinearLayout.LayoutParams(-1, dp(42))
        )

        buttonsContainer.addView(row2b)

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val row3Params = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        }
        row3.layoutParams = row3Params

        val btnBackToProjects = createDrawerButton("كل المشاريع", false).apply {
            setOnClickListener {
                saveAllOpenTabs()
                drawerLayout?.closeDrawer(Gravity.START)
                showScreen(SCREEN_PROJECTS_LIST, true)
            }
        }
        row3.addView(btnBackToProjects, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            setMargins(dp(60), 0, dp(60), 0)
        })

        buttonsContainer.addView(row3)

        drawerInner.addView(buttonsContainer, LinearLayout.LayoutParams(-1, -2))

        drawerContent?.addView(drawerInner)

        drawerLayout?.addView(drawerContent)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setHomeButtonEnabled(false)

        toolbar?.navigationIcon = createWhiteMenuIcon()
        toolbar?.setNavigationOnClickListener {
            drawerLayout?.let { dl ->
                if (dl.isDrawerOpen(Gravity.START)) {
                    dl.closeDrawer(Gravity.START)
                } else {
                    refreshAccountCardInDrawer()
                    refreshUploadMethodButtonLabel()
                    dl.openDrawer(Gravity.START)
                }
            }
        }

        drawerLayout?.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                restoreTreeScrollState()
                refreshAccountCardInDrawer()
                refreshUploadMethodButtonLabel()
            }

            override fun onDrawerClosed(drawerView: View) {
                saveTreeScrollState()
            }
        })

        setupDraggableLogPanel()

        applyEditorInsets()
    }

    /**
     * ⭐ v15.1 — تطبيق WindowInsets على drawerLayout (المحرر).
     *
     * التغييرات:
     *   - editorTopInset = 0 و editorBottomInset = 0
     *     (rootLayout بياخد top/bottom insets بالفعل)
     *   - لا نطبق bottom padding على logPanel
     *     (لأن rootLayout بياخد bottom inset)
     *   - يبقى تحديث logPanelExpandedHeight يعمل بشكل صحيح
     */
    private fun applyEditorInsets() {
        val drawer = drawerLayout ?: return
        val editorRoot = layoutEditor ?: return

        ViewCompat.setOnApplyWindowInsetsListener(drawer) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // ⭐ v15.1: rootLayout بياخد top/bottom insets بالفعل
            // لذلك هنا editorTopInset و editorBottomInset بيبقوا 0
            // لأن insets تم استهلاكها من parent (rootLayout)
            editorTopInset = 0
            editorBottomInset = 0

            editorRoot.post {
                logPanelExpandedHeight = getLogPanelMaxHeight()

                val panel = logPanel
                if (panel != null && isLogPanelExpanded) {
                    val params = panel.layoutParams
                    params.height = logPanelExpandedHeight
                    panel.layoutParams = params
                    updateLogPanelContentVisibility(params.height)
                }
            }

            // ⭐ v15.1: لا نطبق bottom padding على logPanel
            // لأن rootLayout بياخد bottom inset بالفعل
            logPanel?.setPadding(0, 0, 0, 0)

            insets
        }

        ViewCompat.requestApplyInsets(drawer)
    }

    // ============================================================
    // بطاقة الحساب في Drawer
    // ============================================================
    private fun buildAccountCardInDrawer() {
        accountCardView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(COLOR_SURFACE)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                drawerLayout?.closeDrawer(Gravity.START)
                showAccountSwitcher()
            }
        }

        accountIconView = TextView(this).apply {
            text = "?"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(16f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COLOR_PRIMARY)
            }
        }
        accountCardView!!.addView(
            accountIconView,
            LinearLayout.LayoutParams(dp(40), dp(40))
        )

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        accountCardView!!.addView(
            textCol,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(12)
            }
        )

        accountNameView = TextView(this).apply {
            text = "لا يوجد حساب"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(14f)
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        textCol.addView(accountNameView)

        accountUserView = TextView(this).apply {
            text = "اضغط للتسجيل"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(11f)
            isSingleLine = true
        }
        textCol.addView(accountUserView)

        val arrow = TextView(this).apply {
            text = "⇄"
            setTextColor(COLOR_ARROW)
            setTextSize(20f)
            gravity = Gravity.CENTER
        }
        accountCardView!!.addView(
            arrow,
            LinearLayout.LayoutParams(dp(28), dp(28))
        )
    }

    private fun refreshAccountCardInDrawer() {
        val iconView = accountIconView ?: return
        val nameView = accountNameView ?: return
        val userView = accountUserView ?: return

        val active = GitHubAccountManager.getActive(this)
        val accounts = GitHubAccountManager.listAccounts(this)

        if (active == null) {
            iconView.text = "?"
            iconView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COLOR_SURFACE_ALT)
            }
            nameView.text = "لا يوجد حساب"
            userView.text = if (accounts.isEmpty())
                "اضغط للتسجيل"
            else
                "${accounts.size} حسابات محفوظة — اضغط للاختيار"
            return
        }

        iconView.text = active.initial
        iconView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(active.color)
        }

        nameView.text = active.safeDisplayName
        userView.text = "@${active.username}" +
                if (accounts.size > 1) " • ${accounts.size} حسابات" else ""
    }

    private fun showAccountSwitcher() {
        val accounts = GitHubAccountManager.listAccounts(this)

        if (accounts.isEmpty()) {
            goToLoginActivity()
            return
        }

        GitHubAccountManager.showSwitcher(this) { newAccount ->
            refreshAccountCardInDrawer()

            currentProjectDir?.let { projDir ->
                val linkedAccount = GitHubAccountManager.getAccountForProject(
                    this, projDir.name
                )
                val type = getCurrentProjectType(projDir)
                val appName = prefs.getString(
                    "name_${projDir.name}", projDir.name
                ) ?: projDir.name
                val packageName = prefs.getString(
                    "package_${projDir.name}", ""
                ) ?: ""

                val accountInfo = if (linkedAccount != null) {
                    " | الحساب: ${linkedAccount.username}"
                } else {
                    " | الحساب: ${newAccount.username} (سيُربط)"
                }

                tvProjectInfo?.text =
                    "المشروع: $appName | النوع: ${type.displayName} | " +
                    "الحزمة: $packageName$accountInfo"
            }

            Toast.makeText(
                this,
                "الحساب النشط: ${newAccount.username}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun createWhiteMenuIcon(): android.graphics.drawable.Drawable {
        val size = dp(24)
        val barHeight = dp(2)
        val barGap = dp(5)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = android.graphics.Paint.Style.FILL
        }

        val marginX = dp(4)
        val left = marginX
        val right = size - marginX
        val startY = (size - (barHeight * 3 + barGap * 2)) / 2

        for (i in 0 until 3) {
            val top = startY + i * (barHeight + barGap)
            val bottom = top + barHeight
            canvas.drawRect(
                left.toFloat(), top.toFloat(),
                right.toFloat(), bottom.toFloat(),
                paint
            )
        }

        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }

    private fun updateSaveButtonVisibility() {
        val btn = btnToolbarSave ?: return
        val hasOpenFile = currentOpenFile != null && openTabs.isNotEmpty()
        val isText = (activeViewerKind == FileKind.TEXT ||
                activeViewerKind == FileKind.UNKNOWN ||
                activeViewerKind == null)
        btn.visibility = if (hasOpenFile && isText) View.VISIBLE else View.GONE
    }

    // ============================================================
    // ⭐ v14.0: Dialog اختيار طريقة الرفع
    // ============================================================

    private fun buildUploadMethodButtonLabel(): String {
        val saved = prefs.getString(KEY_UPLOAD_METHOD, "") ?: ""
        if (saved.isEmpty()) {
            return "طريقة الرفع: اسأل كل مرة"
        }
        val method = GitHubApi.UploadMethod.valueOf(
            if (saved == UPLOAD_METHOD_BATCHES) "BATCHES" else "GRAPHQL"
        )
        val label = if (method == GitHubApi.UploadMethod.GRAPHQL) "GraphQL" else "دفعات"
        return "طريقة الرفع: $label"
    }

    private fun refreshUploadMethodButtonLabel() {
        btnChangeUploadMethodInDrawer?.text = buildUploadMethodButtonLabel()
    }

    private fun showUploadMethodDialog(
        projectDir: File,
        projectType: ProjectType,
        from499Dialog: Boolean = false
    ) {
        val rememberEnabled = prefs.getBoolean(KEY_UPLOAD_REMEMBER, false)
        val savedMethod = prefs.getString(KEY_UPLOAD_METHOD, "") ?: ""

        if (!from499Dialog && rememberEnabled && savedMethod.isNotEmpty()) {
            val method = if (savedMethod == UPLOAD_METHOD_BATCHES) {
                GitHubApi.UploadMethod.BATCHES
            } else {
                GitHubApi.UploadMethod.GRAPHQL
            }
            startBuildWithMethod(projectDir, projectType, method)
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(12), dp(8), dp(8))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val subtitle = TextView(this).apply {
            text = "اختر طريقة رفع مشروعك إلى GitHub.\n" +
                    "الطريقة تُؤثر فقط على سرعة الرفع، وليس على نتيجة البناء."
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(12f)
            setPadding(dp(12), 0, dp(12), dp(14))
            setLineSpacing(0f, 1.25f)
        }
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))

        var selectedMethod = GitHubApi.UploadMethod.GRAPHQL

        val graphqlCard = buildUploadMethodCard(
            title = "GraphQL (سريع)",
            description = "مناسب للمشاريع حتى 10 MB\nأسرع طريقة، commit واحد",
            isRecommended = true,
            isSelected = true
        )
        root.addView(
            graphqlCard,
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(dp(8), 0, dp(8), dp(8))
            }
        )

        val batchesCard = buildUploadMethodCard(
            title = "دفعات (يعمل مع أي حجم)",
            description = "مناسب للمشاريع الكبيرة\nقد يرفع عدة commits (10 MB لكل دفعة)",
            isRecommended = false,
            isSelected = false
        )
        root.addView(
            batchesCard,
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(dp(8), 0, dp(8), dp(8))
            }
        )

        val rememberBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = UiStyle.rounded(
                this@MainActivity, COLOR_SURFACE, 12, COLOR_BORDER, 1
            )
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(dp(8), dp(4), dp(8), dp(8))
            }
        }

        val checkbox = android.widget.CheckBox(this).apply {
            isChecked = false
            buttonTintList = android.content.res.ColorStateList.valueOf(COLOR_PRIMARY_LIGHT)
        }
        rememberBox.addView(checkbox, LinearLayout.LayoutParams(dp(28), dp(28)))

        val rememberText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        rememberBox.addView(
            rememberText,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        rememberText.addView(TextView(this).apply {
            text = "تذكّر اختياري"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            typeface = Typeface.DEFAULT_BOLD
        })

        rememberText.addView(TextView(this).apply {
            text = "لن يظهر هذا الحوار مرة أخرى"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(11f)
            setPadding(0, dp(2), 0, 0)
        })

        rememberBox.setOnClickListener {
            checkbox.isChecked = !checkbox.isChecked
        }

        root.addView(rememberBox)

        fun updateCards() {
            val graphqlSelected = (selectedMethod == GitHubApi.UploadMethod.GRAPHQL)
            updateUploadMethodCard(
                graphqlCard,
                isSelected = graphqlSelected,
                isRecommended = true
            )
            updateUploadMethodCard(
                batchesCard,
                isSelected = !graphqlSelected,
                isRecommended = false
            )
        }

        graphqlCard.setOnClickListener {
            selectedMethod = GitHubApi.UploadMethod.GRAPHQL
            updateCards()
        }
        batchesCard.setOnClickListener {
            selectedMethod = GitHubApi.UploadMethod.BATCHES
            updateCards()
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(COLOR_BACKGROUND)
            addView(root, FrameLayout.LayoutParams(-1, -2))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (from499Dialog) "تجربة الرفع على دفعات" else "اختر طريقة الرفع")
            .setView(scroll)
            .setPositiveButton(
                if (from499Dialog) "نعم، جرّب الدفعات" else "متابعة"
            ) { _, _ ->
                if (checkbox.isChecked) {
                    prefs.edit()
                        .putBoolean(KEY_UPLOAD_REMEMBER, true)
                        .putString(
                            KEY_UPLOAD_METHOD,
                            if (selectedMethod == GitHubApi.UploadMethod.BATCHES)
                                UPLOAD_METHOD_BATCHES
                            else
                                UPLOAD_METHOD_GRAPHQL
                        )
                        .apply()
                    refreshUploadMethodButtonLabel()
                } else if (from499Dialog) {
                    prefs.edit()
                        .putString(KEY_UPLOAD_METHOD, UPLOAD_METHOD_BATCHES)
                        .apply()
                    refreshUploadMethodButtonLabel()
                }

                val finalMethod = if (from499Dialog) {
                    GitHubApi.UploadMethod.BATCHES
                } else {
                    selectedMethod
                }

                startBuildWithMethod(projectDir, projectType, finalMethod)
            }
            .setNegativeButton("إلغاء", null)
            .create()

        dialog.show()
        styleDialog(dialog)

        dialog.window?.let { window ->
            val width = (resources.displayMetrics.widthPixels * 0.94).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun buildUploadMethodCard(
        title: String,
        description: String,
        isRecommended: Boolean,
        isSelected: Boolean
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
        }
        updateUploadMethodCardBackground(card, isSelected, isRecommended)

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val radioIcon = TextView(this).apply {
            text = if (isSelected) "◉" else "○"
            setTextColor(if (isSelected) COLOR_PRIMARY_LIGHT else COLOR_TEXT_SECONDARY)
            setTextSize(20f)
            typeface = Typeface.DEFAULT_BOLD
            tag = "radio_icon"
        }
        titleRow.addView(radioIcon, LinearLayout.LayoutParams(dp(28), -2).apply {
            marginEnd = dp(6)
        })

        val titleView = TextView(this).apply {
            text = title
            setTextColor(if (isSelected) COLOR_PRIMARY_LIGHT else COLOR_TEXT_PRIMARY)
            setTextSize(14f)
            typeface = Typeface.DEFAULT_BOLD
            tag = "title"
        }
        titleRow.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))

        if (isRecommended) {
            val badge = TextView(this).apply {
                text = "موصى به"
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(10f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = UiStyle.rounded(
                    this@MainActivity, COLOR_SUCCESS, 8, COLOR_SUCCESS, 0
                )
            }
            titleRow.addView(badge)
        }

        card.addView(titleRow)

        val descView = TextView(this).apply {
            text = description
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(11f)
            setPadding(dp(34), dp(4), 0, 0)
            setLineSpacing(0f, 1.25f)
        }
        card.addView(descView)

        return card
    }

    private fun updateUploadMethodCard(
        card: LinearLayout,
        isSelected: Boolean,
        isRecommended: Boolean
    ) {
        updateUploadMethodCardBackground(card, isSelected, isRecommended)

        val radio = card.findViewWithTag<TextView>("radio_icon")
        if (radio != null) {
            radio.text = if (isSelected) "◉" else "○"
            radio.setTextColor(
                if (isSelected) COLOR_PRIMARY_LIGHT else COLOR_TEXT_SECONDARY
            )
        }

        val title = card.findViewWithTag<TextView>("title")
        if (title != null) {
            title.setTextColor(
                if (isSelected) COLOR_PRIMARY_LIGHT else COLOR_TEXT_PRIMARY
            )
        }
    }

    private fun updateUploadMethodCardBackground(
        card: LinearLayout,
        isSelected: Boolean,
        isRecommended: Boolean
    ) {
        card.background = UiStyle.rounded(
            this,
            if (isSelected) 0xFF1F2D3F.toInt() else COLOR_SURFACE,
            14,
            if (isSelected) COLOR_PRIMARY_LIGHT else COLOR_BORDER,
            if (isSelected) 2 else 1
        )
    }

    private fun showUploadMethodPreferencesDialog() {
        val rememberEnabled = prefs.getBoolean(KEY_UPLOAD_REMEMBER, false)
        val savedMethod = prefs.getString(KEY_UPLOAD_METHOD, "") ?: ""

        val currentMethodLabel = when (savedMethod) {
            UPLOAD_METHOD_GRAPHQL -> "GraphQL (سريع)"
            UPLOAD_METHOD_BATCHES -> "دفعات (يعمل مع أي حجم)"
            else -> "لم تُحدد بعد"
        }

        val rememberLabel = if (rememberEnabled) "مفعّل" else "معطّل"

        val options = arrayOf(
            "إعادة تعيين التفضيل (اسأل كل مرة)",
            "إظهار الحوار الآن لاختيار طريقة جديدة"
        )

        val infoView = TextView(this).apply {
            text = "الإعدادات الحالية:\n" +
                    "• الطريقة المحفوظة: $currentMethodLabel\n" +
                    "• تذكّر اختياري: $rememberLabel"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            setLineSpacing(0f, 1.3f)
            setPadding(dp(20), dp(20), dp(20), dp(12))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("طريقة الرفع إلى GitHub")
            .setView(infoView)
            .setAdapter(
                UiStyle.dialogListAdapter(this, options)
            ) { _, which ->
                when (which) {
                    0 -> {
                        prefs.edit()
                            .remove(KEY_UPLOAD_METHOD)
                            .putBoolean(KEY_UPLOAD_REMEMBER, false)
                            .apply()
                        refreshUploadMethodButtonLabel()
                        Toast.makeText(
                            this,
                            "تم إعادة التعيين — سيظهر الحوار في كل مرة",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    1 -> {
                        val projDir = currentProjectDir
                        if (projDir == null) {
                            Toast.makeText(
                                this,
                                "افتح مشروعاً أولاً",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val type = getCurrentProjectType(projDir)
                            showUploadMethodDialog(projDir, type)
                        }
                    }
                }
            }
            .setNegativeButton("إغلاق", null)
            .create()

        dialog.show()
        styleDialog(dialog)
    }

    // ============================================================
    // Log Panel
    // ============================================================
    private fun buildLogPanel() {
        logPanelCollapsedHeight = dp(32)
        logPanelExpandedHeight = maxOf(
            logPanelCollapsedHeight,
            resources.displayMetrics.heightPixels - dp(48)
        )

        logPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BACKGROUND)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(-1, logPanelCollapsedHeight)
        }

        val dragHandleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_DRAG_HANDLE)
            layoutParams = LinearLayout.LayoutParams(-1, dp(32))
            gravity = Gravity.CENTER
        }

        dragIcon = TextView(this).apply {
            text = "▲"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(12f)
            gravity = Gravity.CENTER
        }
        dragHandleContainer.addView(dragIcon)
        logDragHandle = dragHandleContainer

        logPanel!!.addView(logDragHandle)

        val logHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setBackgroundColor(COLOR_SURFACE)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(36))
        }
        logHeaderRef = logHeader

        val fixedSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-2, -2)
        }

        val logIcon = TextView(this).apply {
            text = "السجل"
            setTextColor(0xFF8B949E.toInt())
            setTextSize(10f)
            setPadding(0, 0, dp(4), 0)
        }
        fixedSection.addView(logIcon)

        val tvLogTitle = TextView(this).apply {
            text = "البناء"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(10f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(6), 0)
        }
        fixedSection.addView(tvLogTitle)

        tvBuildTimer = TextView(this).apply {
            text = ""
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(9f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(2), 0, dp(4), 0)
        }
        fixedSection.addView(tvBuildTimer)

        tvErrorSummary = TextView(this).apply {
            text = "0"
            setTextColor(0xFF8B949E.toInt())
            setTextSize(9f)
            setPadding(dp(2), 0, dp(4), 0)
        }
        fixedSection.addView(tvErrorSummary)

        logHeader.addView(fixedSection)

        val scrollableButtons = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val btnOpenGithub = createLogHeaderButton("GitHub").apply {
            setOnClickListener { openBuildOnGitHub() }
        }
        buttonsRow.addView(btnOpenGithub)

        val btnClearLogs = createLogHeaderButton("مسح").apply {
            setOnClickListener { clearLogPanel() }
        }
        buttonsRow.addView(btnClearLogs)

        val btnCopyLogs = createLogHeaderButton("نسخ").apply {
            setOnClickListener {
                val text = buildStepsText()
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("steps", text)
                )
                Toast.makeText(
                    this@MainActivity,
                    "تم نسخ السجل",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        buttonsRow.addView(btnCopyLogs)

        scrollableButtons.addView(
            buttonsRow,
            FrameLayout.LayoutParams(-2, -2)
        )

        logHeader.addView(scrollableButtons)

        logPanel!!.addView(logHeader)

        uploadCounterBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(COLOR_BACKGROUND)
            visibility = View.GONE
        }

        tvUploadCounter = TextView(this).apply {
            text = ""
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(11f)
            typeface = Typeface.MONOSPACE
        }
        uploadCounterBox!!.addView(tvUploadCounter)

        logPanel!!.addView(
            uploadCounterBox,
            LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        waitingIndicator = TextView(this).apply {
            text = "في انتظار بدء GitHub Actions..."
            setTextColor(COLOR_PRIMARY_LIGHT)
            setTextSize(12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(20), dp(16), dp(20))
            visibility = View.GONE
            setLineSpacing(0f, 1.3f)
        }
        logPanel!!.addView(
            waitingIndicator,
            LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        rvSteps = RecyclerView(this).apply {
            setBackgroundColor(COLOR_BACKGROUND)
            setPadding(0, dp(4), 0, dp(4))
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = DefaultItemAnimator()
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            visibility = View.GONE
        }
        stepsAdapter = StepsAdapter()
        rvSteps!!.adapter = stepsAdapter
        logPanel!!.addView(rvSteps)

        tvLogs = TextView(this).apply {
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(10f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(12), dp(8), dp(12), dp(8))
            visibility = View.GONE
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = 0x553F8CFF.toInt()
        }
        logPanel!!.addView(tvLogs, LinearLayout.LayoutParams(0, 0))

        updateLogPanelContentVisibility(logPanelCollapsedHeight)
    }

    private fun createLogHeaderButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            setTextSize(9f)
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT_PRIMARY)
            background = GradientDrawable().apply {
                setColor(COLOR_SURFACE_ALT)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), COLOR_BORDER)
            }
            setPadding(dp(6), 0, dp(6), 0)
            layoutParams = LinearLayout.LayoutParams(-2, dp(26)).apply {
                marginStart = dp(3)
            }
        }
    }

    private fun showDownloadProgressDialog() {
        if (downloadProgressDialog?.isShowing == true) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val titleView = TextView(this).apply {
            text = "جارٍ تحميل APK..."
            setTextSize(15f)
            setTextColor(COLOR_TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(titleView, LinearLayout.LayoutParams(-1, -2))

        downloadProgressBar = ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(downloadProgressBar, LinearLayout.LayoutParams(-1, dp(8)))

        downloadProgressPercent = TextView(this).apply {
            text = "0%"
            setTextSize(20f)
            setTextColor(COLOR_PRIMARY)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(downloadProgressPercent, LinearLayout.LayoutParams(-1, -2))

        downloadProgressText = TextView(this).apply {
            text = "جارٍ البدء..."
            setTextSize(11f)
            setTextColor(COLOR_TEXT_SECONDARY)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        root.addView(downloadProgressText, LinearLayout.LayoutParams(-1, -2))

        downloadProgressDialog = AlertDialog.Builder(this)
            .setView(root)
            .setCancelable(false)
            .create()

        downloadProgressDialog?.show()
        downloadProgressDialog?.let { styleDialog(it) }
    }

    private fun updateDownloadProgress(downloadedBytes: Long, totalBytes: Long) {
        runOnUiThread {
            if (downloadProgressDialog?.isShowing != true) {
                showDownloadProgressDialog()
            }
            val bar = downloadProgressBar ?: return@runOnUiThread

            if (totalBytes > 0) {
                var percent = ((downloadedBytes * 100L) / totalBytes).toInt()
                percent = percent.coerceIn(0, 100)
                bar.progress = percent
                bar.isIndeterminate = false
                downloadProgressPercent?.text = "$percent%"
                downloadProgressText?.text =
                    "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"
            } else {
                bar.isIndeterminate = true
                downloadProgressPercent?.text = "..."
                downloadProgressText?.text =
                    "${formatBytes(downloadedBytes)} تم تحميله"
            }
        }
    }

    private fun hideDownloadProgressDialog() {
        runOnUiThread {
            downloadProgressDialog?.takeIf { it.isShowing }?.dismiss()
            downloadProgressDialog = null
            downloadProgressBar = null
            downloadProgressText = null
            downloadProgressPercent = null
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 ->
                String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L ->
                String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            else ->
                String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun createDrawerButton(title: String, primary: Boolean): Button {
        return Button(this).apply {
            text = title
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(12f)
            isAllCaps = false
            minimumHeight = 0
            minimumWidth = 0
            setPadding(dp(6), 0, dp(6), 0)
            background = UiStyle.rounded(
                this@MainActivity,
                if (primary) COLOR_PRIMARY else COLOR_SURFACE_ALT,
                10,
                if (primary) COLOR_PRIMARY_LIGHT else COLOR_BORDER,
                1
            )
        }
    }

    // ============================================================
    // GitHub operations
    // ============================================================
    private fun openBuildOnGitHub() {
        val projDir = currentProjectDir
        if (projDir == null || !projDir.isDirectory) {
            Toast.makeText(this, "افتح مشروعاً أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        val savedRepoName = prefs.getString("repo_${projDir.name}", "") ?: ""
        if (savedRepoName.isEmpty()) {
            showNotBuiltYetDialog()
            return
        }

        val projectAccount = GitHubAccountManager.getAccountForProject(
            this, projDir.name
        )
        val username = projectAccount?.username
            ?: AuthManager.getInstance(this).getGithubUsername()

        if (username.isNullOrEmpty()) {
            fetchUsernameAndOpen(savedRepoName)
            return
        }

        showGitHubDialog(username, savedRepoName)
    }

    private fun showNotBuiltYetDialog() {
        AlertDialog.Builder(this)
            .setTitle("المشروع لم يُبنَ بعد")
            .setMessage(
                "لم يتم بناء هذا المشروع على GitHub بعد.\n\n" +
                        "يمكنك:\n" +
                        "• بناء APK الآن (لإنشاء المستودع)\n" +
                        "• فتح صفحة GitHub المتوقعة (قد لا تعمل)"
            )
            .setPositiveButton("بناء APK") { _, _ -> buildApk() }
            .setNeutralButton("فتح الرابط المتوقع") { _, _ -> openGitHubWithGuessedName() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun openGitHubWithGuessedName() {
        val projDir = currentProjectDir ?: return
        val appName = prefs.getString("name_${projDir.name}", projDir.name) ?: projDir.name
        val guessedRepoName = repositoryNameFor(appName)

        val projectAccount = GitHubAccountManager.getAccountForProject(
            this, projDir.name
        )
        val username = projectAccount?.username
            ?: AuthManager.getInstance(this).getGithubUsername()

        if (username.isNullOrEmpty()) {
            fetchUsernameAndOpen(guessedRepoName)
            return
        }
        showGitHubDialog(username, guessedRepoName)
    }

    private fun fetchUsernameAndOpen(repoName: String) {
        val token = GitHubAccountManager.getActiveToken(this)
        if (token.isEmpty()) {
            Toast.makeText(
                this,
                "لم يتم الربط بحساب GitHub. أعد تسجيل الدخول",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Toast.makeText(this, "جارٍ التعرف على حسابك...", Toast.LENGTH_SHORT).show()

        GitHubApi.getUser(token, object : GitHubApi.Callback<String> {
            override fun onSuccess(username: String) {
                if (username.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "تعذر التعرف على اسم الحساب",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return
                }
                runOnUiThread {
                    showGitHubDialog(username, repoName)
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "تعذر التعرف على حسابك:\n$message",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    private fun showGitHubDialog(username: String, repoName: String) {
        val url = "https://github.com/$username/$repoName/actions"
        AlertDialog.Builder(this)
            .setTitle("فتح GitHub Actions")
            .setMessage(
                "سيتم فتح صفحة البناء على GitHub:\n\n$url\n\n" +
                        "الحساب: $username"
            )
            .setPositiveButton("فتح") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "تعذر فتح الرابط: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun repositoryNameFor(raw: String?): String {
        var name = raw?.trim() ?: "app"
        if (name.isEmpty()) name = "app"

        val sb = StringBuilder()
        for (c in name) {
            val isArabic = (c in '\u0600'..'\u06FF') ||
                    (c in '\u0750'..'\u077F') ||
                    (c in '\uFB50'..'\uFDFF') ||
                    (c in '\uFE70'..'\uFEFF')
            val isLatinDigit = (c in 'A'..'Z') || (c in 'a'..'z') || (c in '0'..'9')
            val isSafe = c == '-' || c == '_' || c == '.'

            when {
                isArabic || isLatinDigit || isSafe -> sb.append(c)
                c == ' ' -> sb.append('-')
            }
        }

        var result = sb.toString()
        while (result.startsWith("-") || result.startsWith(".")) {
            result = result.substring(1)
        }
        while (result.endsWith("-") || result.endsWith(".")) {
            result = result.substring(0, result.length - 1)
        }
        if (result.isEmpty()) result = "app"
        return if (result.length > 90) result.substring(0, 90) else result
    }

    private fun openAiChat() {
        val projDir = currentProjectDir
        if (projDir == null || !projDir.isDirectory) {
            Toast.makeText(this, "افتح مشروعًا أولًا", Toast.LENGTH_SHORT).show()
            return
        }
        saveAllOpenTabs()
        reloadFileAfterAi = true

        val intent = Intent(this, AiChatActivity::class.java)
        intent.putExtra(AiChatActivity.EXTRA_PROJECT_DIR, projDir.absolutePath)

        currentOpenFile?.let { openFile ->
            try {
                val rootPath = projDir.canonicalPath
                val filePath = openFile.canonicalPath
                var relative = if (filePath.startsWith(rootPath)) {
                    filePath.substring(rootPath.length)
                } else {
                    filePath
                }
                while (relative.startsWith(File.separator)) {
                    relative = relative.substring(1)
                }
                intent.putExtra(
                    AiChatActivity.EXTRA_CURRENT_FILE,
                    relative.replace(File.separatorChar, '/')
                )
            } catch (_: Exception) {
            }
        }

        startActivity(intent)
    }

    private fun showProjectSettings() {
        val projDir = currentProjectDir
        if (projDir == null) {
            Toast.makeText(this, "لا يوجد مشروع مفتوح", Toast.LENGTH_SHORT).show()
            return
        }

        settingsDialog = ProjectSettingsDialog(this, projDir, Runnable {
            val folderName = projDir.name
            val appName = prefs.getString("name_$folderName", folderName) ?: folderName
            val packageName = prefs.getString("package_$folderName", "") ?: ""
            val apiLevel = prefs.getString("api_$folderName", "") ?: ""
            tvProjectInfo?.text =
                "المشروع: $appName | الحزمة: $packageName | API: $apiLevel"
            invalidateProjectIconCache(projDir)
        })
        settingsDialog?.show()
    }

    // ============================================================
    // Log panel helpers
    // ============================================================
    private fun getLogPanelMaxHeight(): Int {
        var parentHeight = 0
        val panel = logPanel
        if (panel != null && panel.parent is View) {
            parentHeight = (panel.parent as View).height
        }
        if (parentHeight <= 0) {
            parentHeight = resources.displayMetrics.heightPixels
        }
        val available = parentHeight - dp(44) - editorBottomInset
        return maxOf(logPanelCollapsedHeight, available)
    }

    private fun setupDraggableLogPanel() {
        logPanelExpandedHeight = getLogPanelMaxHeight()

        logDragHandle?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startHeight = logPanel?.layoutParams?.height ?: 0
                    if (startHeight <= 0) startHeight = logPanelCollapsedHeight
                    logPanelExpandedHeight = getLogPanelMaxHeight()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - startY
                    var newHeight = (startHeight - deltaY).toInt()
                    val maxHeight = getLogPanelMaxHeight()
                    newHeight = newHeight.coerceIn(logPanelCollapsedHeight, maxHeight)
                    logPanelExpandedHeight = maxHeight

                    val params = logPanel?.layoutParams
                    params?.height = newHeight
                    logPanel?.layoutParams = params

                    updateLogPanelContentVisibility(newHeight)

                    isLogPanelExpanded = newHeight > logPanelCollapsedHeight + 20
                    updateDragIcon()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val currentHeight = logPanel?.layoutParams?.height ?: 0
                    logPanelExpandedHeight = getLogPanelMaxHeight()
                    val middleHeight = (logPanelCollapsedHeight + logPanelExpandedHeight) / 2
                    val targetHeight: Int
                    if (currentHeight >= middleHeight) {
                        targetHeight = logPanelExpandedHeight
                        isLogPanelExpanded = true
                    } else {
                        targetHeight = logPanelCollapsedHeight
                        isLogPanelExpanded = false
                    }
                    val params = logPanel?.layoutParams
                    params?.height = targetHeight
                    logPanel?.layoutParams = params

                    updateLogPanelContentVisibility(targetHeight)
                    updateDragIcon()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateLogPanelContentVisibility(height: Int) {
        val isOpen = height > dp(45)

        logHeaderRef?.visibility = if (isOpen) View.VISIBLE else View.GONE
        rvSteps?.visibility = if (isOpen) View.VISIBLE else View.GONE

        uploadCounterBox?.let { box ->
            if (isOpen && !tvUploadCounter?.text.isNullOrEmpty()) {
                box.visibility = View.VISIBLE
            } else {
                box.visibility = View.GONE
            }
        }
    }

    private fun updateDragIcon() {
        dragIcon?.text = if (isLogPanelExpanded) "▼" else "▲"
    }

    private fun showSettingsDialog() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun resetStepsState() {
        uiSteps.clear()
        stepsAdapter?.notifyDataSetChanged()
        tvBuildTimer?.text = ""
        uploadCounterBox?.visibility = View.GONE
    }

    private fun expandLogPanelAutomatically() {
        val panel = logPanel ?: return
        val params = panel.layoutParams ?: return

        if (!isLogPanelExpanded) {
            logPanelExpandedHeight = getLogPanelMaxHeight()
            params.height = logPanelExpandedHeight
            panel.layoutParams = params
            isLogPanelExpanded = true
            updateDragIcon()
        }

        updateLogPanelContentVisibility(params.height)

        rvSteps?.visibility = View.VISIBLE

        panel.requestLayout()
        panel.invalidate()
        rvSteps?.requestLayout()
        rvSteps?.invalidate()
    }

    private fun applySteps(steps: List<GitHubApi.Step>?) {
        if (steps.isNullOrEmpty()) return

        uiSteps.clear()
        for (step in steps) {
            val ui = UiStep(
                step.name, step.status, step.conclusion,
                step.startedAtMs, step.durationMs(), 0L
            )
            uiSteps.add(ui)
        }

        expandLogPanelAutomatically()

        stepsAdapter?.notifyDataSetChanged()

        uploadCounterBox?.visibility = View.GONE
        waitingIndicator?.visibility = View.GONE

        rvSteps?.post {
            rvSteps?.requestLayout()
            rvSteps?.invalidate()
            stepsAdapter?.notifyDataSetChanged()
        }

        rvSteps?.postDelayed({
            rvSteps?.requestLayout()
            rvSteps?.invalidate()
        }, 150L)
    }

    private fun applyAnnotations(rawAnnotations: String?) {
        if (rawAnnotations.isNullOrBlank()) return
        val parts = rawAnnotations.split(FlutterBuildApi.ANNOTATIONS_SEPARATOR)
        val parsed = mutableListOf<FlutterBuildApi.BuildLogLine>()

        for (part in parts) {
            if (part.isBlank()) continue
            val message = part.trim()
            val logType = FlutterBuildApi.classifyAnnotationStatic(message)
            parsed.add(FlutterBuildApi.BuildLogLine(message, null, 0, 0, logType))
        }
        if (parsed.isEmpty()) return

        tvLogs?.let { logs ->
            logs.append("\n")
            logs.append("━━━━━━━━━━━━━━━━━━━━━━\n")
            logs.append("رسائل GitHub الرسمية:\n\n")
            for (line in parsed) {
                val prefix = when (line.logType) {
                    FlutterBuildApi.LOG_TYPE_ERROR -> "[خطأ] "
                    FlutterBuildApi.LOG_TYPE_WARNING -> "[تحذير] "
                    else -> ""
                }
                logs.append(prefix)
                logs.append(line.text)
                logs.append("\n\n")
            }
        }

        val targets = mutableListOf<UiStep>()
        for (step in uiSteps) {
            if (step.isFailure()) targets.add(step)
        }
        if (targets.isEmpty() && uiSteps.isNotEmpty()) {
            targets.add(uiSteps.last())
        }

        for (step in targets) {
            step.annotations.clear()
            step.annotations.addAll(parsed)
            step.logsFetched = true
            if (step.isFailure()) step.logsExpanded = true
        }
        stepsAdapter?.notifyDataSetChanged()
    }

    private fun applyStepLogs(
        jobId: Long,
        allLogs: List<FlutterBuildApi.BuildLogLine>?,
        errorLogs: List<FlutterBuildApi.BuildLogLine>?
    ) {
        val safeAll = allLogs ?: emptyList()
        val safeErrors = errorLogs ?: emptyList()

        val targets = mutableListOf<UiStep>()
        for (step in uiSteps) {
            if (step.isFailure()) targets.add(step)
        }

        if (targets.isEmpty()) {
            val buildStep = uiSteps.firstOrNull {
                it.name.lowercase(Locale.US).contains("build")
            }
            if (buildStep != null) {
                targets.add(buildStep)
            } else if (uiSteps.isNotEmpty()) {
                targets.add(uiSteps.last())
            }
        }

        for (step in targets) {
            step.allLogs.clear()
            step.allLogs.addAll(safeAll)
            step.errorLogs.clear()
            step.errorLogs.addAll(safeErrors)
            step.logsFetched = true
        }

        for (line in safeErrors) {
            if (line.logType == FlutterBuildApi.LOG_TYPE_ERROR &&
                line.filePath != null && line.line > 0
            ) {
                val be = BuildError(
                    line.text,
                    cleanBuildPath(line.filePath),
                    maxOf(1, line.line),
                    maxOf(0, line.column),
                    false
                )
                val exists = buildErrors.any {
                    it.path == be.path && it.line == be.line
                }
                if (!exists) buildErrors.add(be)
            }
        }

        tvErrorSummary?.text = "أخطاء: ${buildErrors.size}"
        refreshExternalErrorMarkersForCurrentFile()
        stepsAdapter?.notifyDataSetChanged()
    }

    private fun startTimerUpdates() {
        val handler = timerHandler ?: return
        stopTimerUpdates()

        if (!buildInProgress || buildStartedAtMs <= 0L) return

        timerRunnable = object : Runnable {
            override fun run() {
                if (!buildInProgress) {
                    stopTimerUpdates()
                    return
                }
                val elapsed = ((System.currentTimeMillis() - buildStartedAtMs) / 1000L)
                    .coerceAtLeast(0L)
                val minutes = elapsed / 60L
                val seconds = elapsed % 60L
                tvBuildTimer?.text =
                    "$minutes:" + String.format(Locale.US, "%02d", seconds)
                stepsAdapter?.notifyDataSetChanged()
                handler.postDelayed(this, 1000L)
            }
        }
        timerHandler?.post(timerRunnable!!)
    }

    private fun stopTimerUpdates() {
        timerRunnable?.let { timerHandler?.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun clearLogPanel() {
        tvLogs?.text = ""
        uiSteps.clear()
        stepsAdapter?.notifyDataSetChanged()
        tvBuildTimer?.text = ""
        uploadCounterBox?.visibility = View.GONE
        clearBuildErrors()
        buildInProgress = false
        stopTimerUpdates()
        waitingIndicator?.visibility = View.GONE
    }

    private fun buildStepsText(): String {
        val sb = StringBuilder()
        var logsAlreadyShown = false
        var annotationsAlreadyShown = false

        sb.append("═══════════════════════════════════\n")
        sb.append("  ملخص البناء - Klencod IDE\n")
        sb.append("═══════════════════════════════════\n\n")

        for (step in uiSteps) {
            val marker = when {
                step.isSuccess() -> "[OK]"
                step.isFailure() -> "[X]"
                step.isRunning() -> "[..]"
                else -> "[ ]"
            }
            sb.append(marker).append("  ").append(step.name).append("  ")
                .append(formatDuration(step)).append("\n")
        }

        for (step in uiSteps) {
            if (step.annotations.isEmpty()) continue
            sb.append("\n")
            sb.append("═══════════════════════════════════\n")
            sb.append("  رسائل GitHub الرسمية\n")
            sb.append("═══════════════════════════════════\n\n")
            for (line in step.annotations) {
                val prefix = when (line.logType) {
                    FlutterBuildApi.LOG_TYPE_ERROR -> "[خطأ] "
                    FlutterBuildApi.LOG_TYPE_WARNING -> "[تحذير] "
                    else -> ""
                }
                sb.append(prefix).append(line.text).append("\n\n")
            }
            annotationsAlreadyShown = true
            break
        }

        for (step in uiSteps) {
            if (!step.isFailure()) continue
            val logs = if (step.logsExpanded) step.allLogs else step.errorLogs
            if (logs.isEmpty()) continue

            sb.append("═══════════════════════════════════\n")
            sb.append("  سجل ").append(step.name).append("\n")
            sb.append("═══════════════════════════════════\n\n")
            for (line in logs) sb.append("     ").append(line.text).append("\n")
            sb.append("\n")
            logsAlreadyShown = true
            break
        }

        if (!logsAlreadyShown && !annotationsAlreadyShown) {
            sb.append("═══════════════════════════════════\n")
            sb.append("  السجل النصي\n")
            sb.append("═══════════════════════════════════\n\n")
            sb.append(tvLogs?.text?.toString() ?: "")
        }

        return sb.toString()
    }

    private fun formatDuration(step: UiStep): String {
        val ms = if (step.isRunning()) {
            System.currentTimeMillis() - step.startedAtMs
        } else {
            step.durationMs
        }
        if (ms <= 0) return ""
        val s = ms / 1000L
        val m = s / 60L
        val sec = s % 60L
        return if (m > 0) "${m}m ${sec}s" else "${sec}s"
    }

    // ============================================================
    // Dialog السجل الكامل
    // ============================================================
    private fun showFullLogDialog(step: UiStep) {
        if (isFinishing || isDestroyed) return

        val logs = step.allLogs
        if (logs.isEmpty()) {
            Toast.makeText(this, "لا يوجد سجل كامل", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder(logs.size * 60)
        for (line in logs) {
            sb.append(line.text).append('\n')
        }
        val fullText = sb.toString()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }

        val countView = TextView(this).apply {
            text = "${logs.size} سطر"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(11f)
            typeface = Typeface.MONOSPACE
        }
        infoRow.addView(countView, LinearLayout.LayoutParams(0, -2, 1f))

        val nameView = TextView(this).apply {
            text = step.name
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
        }
        infoRow.addView(nameView)

        root.addView(infoRow, LinearLayout.LayoutParams(-1, -2))

        val logView = TextView(this).apply {
            text = fullText
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(11f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setTextIsSelectable(true)
            background = UiStyle.rounded(
                this@MainActivity, COLOR_SURFACE, 12, COLOR_BORDER, 1
            )
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(COLOR_BACKGROUND)
            setPadding(dp(12), dp(4), dp(12), dp(12))
            addView(logView, FrameLayout.LayoutParams(-1, -2))
        }

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val dialog = AlertDialog.Builder(this)
            .setTitle("السجل الكامل")
            .setView(root)
            .setPositiveButton("تحميل كملف") { _, _ ->
                saveFullLogToDownloads(step, fullText)
            }
            .setNeutralButton("نسخ الكل") { _, _ ->
                try {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    cm.setPrimaryClip(
                        android.content.ClipData.newPlainText("full_log", fullText)
                    )
                    Toast.makeText(
                        this,
                        "تم نسخ ${logs.size} سطر",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (_: Exception) {
                }
            }
            .setNegativeButton("إغلاق", null)
            .create()

        dialog.show()
        styleDialog(dialog)

        dialog.window?.let { window ->
            val width = (resources.displayMetrics.widthPixels * 0.96).toInt()
            val height = (resources.displayMetrics.heightPixels * 0.88).toInt()
            window.setLayout(width, height)
        }
    }

    private fun saveFullLogToDownloads(step: UiStep, fullText: String) {
        try {
            val downloads = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloads.exists() && !downloads.mkdirs()) {
                Toast.makeText(
                    this,
                    "تعذر الوصول لمجلد التنزيلات",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val logsDir = File(downloads, "klencod_logs")
            if (!logsDir.exists() && !logsDir.mkdirs()) {
                Toast.makeText(
                    this,
                    "تعذر إنشاء مجلد السجلات",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val stamp = SimpleDateFormat(
                "yyyyMMdd_HHmmss", Locale.getDefault()
            ).format(Date())

            val safeStepName = step.name
                .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                .take(40)
                .ifEmpty { "build_log" }

            val fileName = "${safeStepName}_$stamp.txt"
            val file = File(logsDir, fileName)

            FileWriter(file).use { writer ->
                writer.write(fullText)
                writer.flush()
            }

            Toast.makeText(
                this,
                "تم الحفظ في:\nDownloads/klencod_logs/$fileName",
                Toast.LENGTH_LONG
            ).show()

            addLog("تم حفظ السجل: $fileName")
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "تعذر الحفظ: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            addLog("خطأ في حفظ السجل: ${e.message}")
        }
    }

    // ============================================================
    // StepsAdapter
    // ============================================================
    private inner class StepsAdapter : RecyclerView.Adapter<StepsAdapter.StepVH>() {

        @NonNull
        override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): StepVH {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(4), dp(8), dp(4))
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            }

            val main = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val circle = TextView(this@MainActivity).apply {
                setTextSize(9f)
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFFFFFFF.toInt())
            }
            val circleParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                setMargins(0, 0, dp(8), 0)
            }
            main.addView(circle, circleParams)

            val name = TextView(this@MainActivity).apply {
                setTextSize(11f)
                setTextColor(COLOR_TEXT_PRIMARY)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            }
            val nameParams = LinearLayout.LayoutParams(-2, -2).apply {
                setMargins(0, 0, dp(8), 0)
            }
            main.addView(name, nameParams)

            val duration = TextView(this@MainActivity).apply {
                setTextSize(10f)
                setTextColor(COLOR_TEXT_SECONDARY)
                typeface = Typeface.MONOSPACE
            }
            main.addView(duration)

            row.addView(main)

            val logsBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(4), dp(6), dp(4))
                visibility = View.GONE
            }
            row.addView(logsBox)

            return StepVH(row, circle, name, duration, logsBox)
        }

        override fun onBindViewHolder(@NonNull holder: StepVH, position: Int) {
            val step = uiSteps[position]
            holder.name.text = step.name
            holder.duration.text = formatDuration(step)

            val circleBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                when {
                    step.isSuccess() -> {
                        setColor(STEP_COLOR_SUCCESS)
                        holder.circle.text = "+"
                    }
                    step.isFailure() -> {
                        setColor(STEP_COLOR_FAILURE)
                        holder.circle.text = "X"
                    }
                    step.isRunning() -> {
                        setColor(STEP_COLOR_RUNNING)
                        holder.circle.text = ""
                    }
                    else -> {
                        setColor(STEP_COLOR_QUEUED)
                        holder.circle.text = ""
                    }
                }
            }
            holder.circle.background = circleBg

            holder.logsBox.removeAllViews()

            if (step.isFailure() && step.logsFetched && step.errorLogs.isNotEmpty()) {
                displayFocusedLogs(holder.logsBox, step, position)
                holder.logsBox.visibility = View.VISIBLE
                return
            }

            if (!step.logsExpanded) {
                if (step.annotations.isNotEmpty()) {
                    displayAnnotations(holder.logsBox, step)
                    holder.logsBox.visibility = View.VISIBLE
                } else {
                    holder.logsBox.visibility = View.GONE
                }
                if (step.logsFetched && step.allLogs.isNotEmpty()) {
                    addExpandButton(holder.logsBox, step, position)
                }
                return
            }

            val logsToShow = step.allLogs

            if (step.annotations.isNotEmpty()) {
                displayAnnotations(holder.logsBox, step)
                val spacer = View(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, dp(6))
                }
                holder.logsBox.addView(spacer)
            }

            if (logsToShow.isEmpty() && step.annotations.isEmpty()) {
                holder.logsBox.visibility = View.GONE
                return
            }
            holder.logsBox.visibility = View.VISIBLE

            if (logsToShow.isEmpty()) {
                addCollapseButton(holder.logsBox, step, position)
                return
            }

            val total = logsToShow.size
            var toShow = minOf(step.visibleCount, total)
            var startIndex = 0

            if (step.showFromEnd && total > step.visibleCount) {
                startIndex = total - step.visibleCount
                toShow = total
            }

            if (startIndex > 0 || toShow < total) {
                val shownStart = startIndex + 1
                var shownEnd = startIndex + (toShow - startIndex)
                if (startIndex == 0 && toShow < total) shownEnd = toShow

                val indicator = TextView(this@MainActivity).apply {
                    text = "- عرض $shownStart إلى $shownEnd من $total -"
                    setTextSize(9f)
                    gravity = Gravity.CENTER
                    setTextColor(LOG_COLOR_LINK)
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                }
                holder.logsBox.addView(indicator)
            }

            for (i in startIndex until toShow) {
                val line = logsToShow[i]
                val lineView = TextView(this@MainActivity).apply {
                    setTextSize(9f)
                    typeface = Typeface.MONOSPACE
                    val prefix = getLogLinePrefix(line)
                    text = prefix + line.text
                    setPadding(dp(3), 0, dp(3), 0)
                    setTextIsSelectable(true)
                    var color = getLogLineColor(line)
                    if (step.isFailure() && color == LOG_COLOR_NORMAL) {
                        color = LOG_COLOR_ERROR
                    }
                    setTextColor(color)
                    if (line.filePath != null && line.line > 0) {
                        paintFlags = paintFlags or
                                android.graphics.Paint.UNDERLINE_TEXT_FLAG
                        setOnClickListener {
                            val be = BuildError(
                                line.text,
                                cleanBuildPath(line.filePath),
                                maxOf(1, line.line),
                                maxOf(0, line.column),
                                false
                            )
                            openBuildError(be)
                        }
                    }
                }
                holder.logsBox.addView(lineView)
            }

            if (toShow < total) {
                val remaining = total - toShow
                val toAdd = minOf(LOGS_BATCH_SIZE, remaining)
                val moreBtn = Button(this@MainActivity).apply {
                    text = "عرض $toAdd سطر إضافي"
                    setTextSize(9f)
                    isAllCaps = false
                    setTextColor(LOG_COLOR_LINK)
                    background = UiStyle.rounded(
                        this@MainActivity, COLOR_SURFACE_ALT, 6, COLOR_BORDER, 1
                    )
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    layoutParams = LinearLayout.LayoutParams(-1, dp(28)).apply {
                        setMargins(0, dp(3), 0, dp(3))
                    }
                    setOnClickListener {
                        step.visibleCount += toAdd
                        step.showFromEnd = false
                        notifyItemChanged(holder.adapterPosition)
                    }
                }
                holder.logsBox.addView(moreBtn)

                if (remaining > toAdd) {
                    val allBtn = Button(this@MainActivity).apply {
                        text = "عرض الكل ($total سطر)"
                        setTextSize(9f)
                        isAllCaps = false
                        setTextColor(LOG_COLOR_LINK)
                        background = UiStyle.rounded(
                            this@MainActivity, COLOR_SURFACE_ALT, 6, COLOR_BORDER, 1
                        )
                        setPadding(dp(8), dp(4), dp(8), dp(4))
                        layoutParams = LinearLayout.LayoutParams(-1, dp(28)).apply {
                            setMargins(0, 0, 0, dp(3))
                        }
                        setOnClickListener {
                            step.visibleCount = total
                            step.showFromEnd = false
                            notifyItemChanged(holder.adapterPosition)
                        }
                    }
                    holder.logsBox.addView(allBtn)
                }
            }

            addCollapseButton(holder.logsBox, step, position)
        }

        private fun displayFocusedLogs(
            parent: LinearLayout,
            step: UiStep,
            position: Int
        ) {
            for (line in step.errorLogs) {
                val lineView = TextView(this@MainActivity).apply {
                    setTextSize(10f)
                    typeface = Typeface.MONOSPACE
                    text = line.text
                    setPadding(dp(4), dp(1), dp(4), dp(1))
                    setTextIsSelectable(true)
                    setTextColor(getLogLineColor(line))
                    if (line.filePath != null && line.line > 0) {
                        paintFlags = paintFlags or
                                android.graphics.Paint.UNDERLINE_TEXT_FLAG
                        setOnClickListener {
                            val be = BuildError(
                                line.text,
                                cleanBuildPath(line.filePath),
                                maxOf(1, line.line),
                                maxOf(0, line.column),
                                false
                            )
                            openBuildError(be)
                        }
                    }
                }
                parent.addView(lineView, LinearLayout.LayoutParams(-1, -2))
            }

            val totalLines = step.allLogs.size
            if (totalLines > 0) {
                val fullLogBtn = Button(this@MainActivity).apply {
                    text = "عرض السجل الكامل ($totalLines سطر)"
                    setTextSize(10f)
                    isAllCaps = false
                    setTextColor(LOG_COLOR_LINK)
                    background = UiStyle.rounded(
                        this@MainActivity, COLOR_SURFACE_ALT, 8, COLOR_BORDER, 1
                    )
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    layoutParams = LinearLayout.LayoutParams(-1, dp(34)).apply {
                        setMargins(0, dp(6), 0, dp(2))
                    }
                    setOnClickListener {
                        showFullLogDialog(step)
                    }
                }
                parent.addView(fullLogBtn)
            }
        }

        private fun displayAnnotations(parent: LinearLayout, step: UiStep) {
            if (step.annotations.isEmpty()) return

            val annBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(6), dp(4), dp(6), dp(6))
                background = UiStyle.rounded(
                    this@MainActivity, COLOR_SURFACE_ALT, 8, COLOR_BORDER, 1
                )
            }

            val annTitle = TextView(this@MainActivity).apply {
                text = "رسائل GitHub الرسمية:"
                setTextSize(10f)
                setTextColor(LOG_COLOR_LINK)
                setPadding(dp(4), dp(2), dp(4), dp(4))
                typeface = Typeface.DEFAULT_BOLD
            }
            annBox.addView(annTitle)

            for (line in step.annotations) {
                val annLine = TextView(this@MainActivity).apply {
                    setTextSize(10f)
                    typeface = Typeface.MONOSPACE
                    setTextIsSelectable(true)
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                    val prefix: String
                    val color: Int
                    when (line.logType) {
                        FlutterBuildApi.LOG_TYPE_ERROR -> {
                            prefix = "[خطأ] "
                            color = LOG_COLOR_ERROR
                        }
                        FlutterBuildApi.LOG_TYPE_WARNING -> {
                            prefix = "[تحذير] "
                            color = LOG_COLOR_WARNING
                        }
                        else -> {
                            prefix = ""
                            color = LOG_COLOR_NORMAL
                        }
                    }
                    text = prefix + line.text
                    setTextColor(color)
                }
                annBox.addView(annLine)
            }

            parent.addView(annBox)
        }

        private fun addExpandButton(parent: LinearLayout, step: UiStep, position: Int) {
            val expandBtn = Button(this@MainActivity).apply {
                text = "عرض كل السجل (${step.allLogs.size} سطر)"
                setTextSize(9f)
                isAllCaps = false
                setTextColor(LOG_COLOR_LINK)
                background = UiStyle.rounded(
                    this@MainActivity, COLOR_SURFACE_ALT, 6, COLOR_BORDER, 1
                )
                setPadding(dp(8), dp(4), dp(8), dp(4))
                layoutParams = LinearLayout.LayoutParams(-1, dp(28)).apply {
                    setMargins(0, dp(3), 0, dp(2))
                }
                setOnClickListener {
                    step.logsExpanded = true
                    if (step.isFailure() && step.allLogs.size > DEFAULT_VISIBLE_LOGS) {
                        step.showFromEnd = true
                    }
                    notifyItemChanged(position)
                }
            }
            parent.addView(expandBtn)
        }

        private fun addCollapseButton(parent: LinearLayout, step: UiStep, position: Int) {
            val collapseBtn = Button(this@MainActivity).apply {
                text = "طوي السجل"
                setTextSize(9f)
                isAllCaps = false
                setTextColor(LOG_COLOR_LINK)
                background = UiStyle.rounded(
                    this@MainActivity, COLOR_SURFACE_ALT, 6, COLOR_BORDER, 1
                )
                setPadding(dp(8), dp(4), dp(8), dp(4))
                layoutParams = LinearLayout.LayoutParams(-1, dp(28)).apply {
                    setMargins(0, dp(2), 0, dp(2))
                }
                setOnClickListener {
                    step.logsExpanded = false
                    notifyItemChanged(position)
                }
            }
            parent.addView(collapseBtn)
        }

        override fun getItemCount(): Int = uiSteps.size

        private fun isContextLine(text: String?): Boolean {
            if (text == null) return false
            val lower = text.lowercase(Locale.US)
            return lower.startsWith("at ") ||
                    lower.startsWith("    at ") ||
                    lower.startsWith("> run with") ||
                    lower.startsWith("> get more help") ||
                    lower.contains("daemon") ||
                    lower.contains("starting a gradle daemon")
        }

        private fun getLogLineColor(line: FlutterBuildApi.BuildLogLine?): Int {
            if (line == null) return LOG_COLOR_NORMAL
            return when (line.logType) {
                FlutterBuildApi.LOG_TYPE_ERROR -> LOG_COLOR_ERROR
                FlutterBuildApi.LOG_TYPE_WARNING -> LOG_COLOR_WARNING
                FlutterBuildApi.LOG_TYPE_SUCCESS -> LOG_COLOR_SUCCESS
                else -> if (isContextLine(line.text)) LOG_COLOR_CONTEXT
                else LOG_COLOR_NORMAL
            }
        }

        private fun getLogLinePrefix(line: FlutterBuildApi.BuildLogLine?): String {
            if (line == null) return ""
            return when (line.logType) {
                FlutterBuildApi.LOG_TYPE_ERROR -> "[خطأ] "
                FlutterBuildApi.LOG_TYPE_WARNING -> "[تحذير] "
                FlutterBuildApi.LOG_TYPE_SUCCESS -> "[OK] "
                else -> ""
            }
        }

        inner class StepVH(
            itemView: View,
            val circle: TextView,
            val name: TextView,
            val duration: TextView,
            val logsBox: LinearLayout
        ) : RecyclerView.ViewHolder(itemView)
    }

    // ============================================================
    // Public helpers
    // ============================================================
    fun performUndo() {
        codeEditorView?.undo()
    }

    fun performRedo() {
        codeEditorView?.redo()
    }

    fun performZoomIn() {
        codeEditorView?.zoomIn()
    }

    fun performZoomOut() {
        codeEditorView?.zoomOut()
    }

    fun showFindBar() {
        if (activeViewerKind != FileKind.TEXT && activeViewerKind != FileKind.UNKNOWN) {
            Toast.makeText(
                this,
                "البحث متاح للملفات النصية فقط",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (findReplaceBar == null) {
            findReplaceBar = FindReplaceBar(this).apply {
                setEditor(codeEditorView)
                visibility = View.GONE
            }
            val editor = layoutEditor ?: return
            val toolbarIndex = editor.indexOfChild(codeToolbar)
            editor.addView(findReplaceBar, toolbarIndex + 1)
        }
        findReplaceBar?.show()
    }

    // ============================================================
    // Lifecycle — polling
    // ============================================================
    override fun onPause() {
        super.onPause()

        stopBuildPolling()

        try {
            activeVideoViewer?.release()
        } catch (_: Exception) {
        }
        try {
            activeAudioViewer?.release()
        } catch (_: Exception) {
        }
        if (isEditorVisible && currentProjectDir != null) {
            saveAllOpenTabsSilently()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isEditorVisible && currentProjectDir != null) {
            saveAllOpenTabsSilently()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeProjectsDirtyFlagIfNeeded()
    }

    private fun consumeProjectsDirtyFlagIfNeeded(): Boolean {
        if (!prefs.getBoolean(KEY_PROJECTS_DIRTY, false)) return false

        prefs.edit().putBoolean(KEY_PROJECTS_DIRTY, false).apply()
        loadProjectsList()
        return true
    }

    override fun onResume() {
        super.onResume()

        codeEditorView?.let { editor ->
            val currentThemeId = EditorThemes.getSelectedId(this)
            if (lastAppliedEditorThemeId == null) {
                lastAppliedEditorThemeId = currentThemeId
            } else if (lastAppliedEditorThemeId != currentThemeId) {
                lastAppliedEditorThemeId = currentThemeId
                editor.refreshTheme()
            }
        }

        if (reloadFileAfterAi && isEditorVisible && currentOpenFile != null) {
            reloadFileAfterAi = false
            reloadFileFromDisk(currentOpenFile!!)
        }

        if (currentScreen == SCREEN_PROJECTS_LIST) {
            if (shouldReloadProjectsList) {
                shouldReloadProjectsList = false
                loadProjectsList()
            } else {
                consumeProjectsDirtyFlagIfNeeded()
            }
        }

        refreshAccountCardInDrawer()
        refreshUploadMethodButtonLabel()

        if (BuildStateStore.hasActiveBuild(this)) {
            startBuildPolling()
        }
    }

    private fun startBuildPolling() {
        if (isPolling) return

        if (!BuildStateStore.hasActiveBuild(this)) {
            return
        }

        val runInfo = BuildStateStore.getRun(this) ?: return

        isPolling = true
        buildInProgress = true
        buildStartedAtMs = runInfo.startedAt
        startTimerUpdates()

        expandLogPanelAutomatically()

        buildApi.startPolling(object : FlutterBuildApi.BuildCallback {

            override fun onStarted(owner: String, repo: String, runId: Long) {
            }

            override fun onProgress(message: String) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    addLog(message)
                }
            }

            override fun onStepsUpdate(steps: List<GitHubApi.Step>) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    applySteps(steps)
                }
            }

            override fun onStepLogs(
                jobId: Long,
                allLogs: List<FlutterBuildApi.BuildLogLine>,
                errorLogs: List<FlutterBuildApi.BuildLogLine>
            ) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    applyStepLogs(jobId, allLogs, errorLogs)
                }
            }

            override fun onAnnotations(rawAnnotations: String) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    applyAnnotations(rawAnnotations)
                }
            }

            override fun onUploadProgress(current: Int, total: Int) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    uploadCounterBox?.let { box ->
                        tvUploadCounter?.text = "تم رفع ($current/$total ملف)"
                        if (isLogPanelExpanded) box.visibility = View.VISIBLE
                    }
                }
            }

            override fun onDownloadProgress(downloadedBytes: Long, totalBytes: Long) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    updateDownloadProgress(downloadedBytes, totalBytes)
                }
            }

            override fun onRepoReady(repoName: String) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    onRepoReady(repoName)
                }
            }

            override fun onStillRunning() {
            }

            override fun onError(error: String) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread

                    isPolling = false
                    buildInProgress = false
                    stopTimerUpdates()

                    waitingIndicator?.visibility = View.GONE

                    addLog("━━━━━━━━━━━━━━━━━━━━━━")
                    addLog("فشل البناء: $error")

                    val isQuota = error.startsWith(FlutterBuildApi.GITHUB_QUOTA_PREFIX)
                    val is499 = isHttp499Error(error)

                    if (isQuota) {
                        val quotaMsg = error.substring(
                            FlutterBuildApi.GITHUB_QUOTA_PREFIX.length
                        ).trim()
                        onBuildQuotaError(quotaMsg)
                    } else if (is499) {
                        onGraphQLTooLargeError()
                    } else if (error.startsWith(FlutterBuildApi.ANNOTATIONS_PREFIX)) {
                        val rawAnnot = error.substring(
                            FlutterBuildApi.ANNOTATIONS_PREFIX.length
                        )
                        applyAnnotations(rawAnnot)
                        showBuildFailedDialog(error)
                    } else {
                        showBuildFailedDialog(error)
                    }

                    Thread {
                        try {
                            BuildForegroundService.showFailureNotification(
                                this@MainActivity,
                                error,
                                isQuota
                            )
                        } catch (_: Exception) {
                        }
                    }.start()
                }
            }

            override fun onSuccess(apkFile: File) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread

                    isPolling = false
                    buildInProgress = false
                    stopTimerUpdates()
                    hideDownloadProgressDialog()

                    waitingIndicator?.visibility = View.GONE

                    addLog("━━━━━━━━━━━━━━━━━━━━━━")
                    addLog("تم بناء APK بنجاح")
                    addLog("المسار: ${apkFile.absolutePath}")

                    try {
                        val projectName = currentProjectDir?.name
                        BuiltAppsStore(this@MainActivity).save(apkFile, projectName)
                        addLog("تم حفظ نسخة في مكتبة التطبيقات")
                    } catch (e: Exception) {
                        addLog("تعذر حفظ نسخة: ${e.message}")
                    }

                    BuildForegroundService.showSuccessNotification(
                        this@MainActivity, apkFile
                    )

                    showBuildSuccessDialog(apkFile)
                }
            }
        })
    }

    private fun stopBuildPolling() {
        if (!isPolling) return
        isPolling = false
        try {
            buildApi.stopPolling()
        } catch (_: Exception) {
        }
    }

    private fun showBuildSuccessDialog(apkFile: File) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val iconView = TextView(this).apply {
            text = "بناء ناجح"
            setTextSize(22f)
            setTextColor(COLOR_SUCCESS)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
        }
        root.addView(iconView, LinearLayout.LayoutParams(-1, -2))

        val titleView = TextView(this).apply {
            text = "اكتمل البناء بنجاح"
            setTextSize(16f)
            setTextColor(COLOR_TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(titleView, LinearLayout.LayoutParams(-1, -2))

        val fileName = TextView(this).apply {
            text = apkFile.name
            setTextSize(12f)
            setTextColor(COLOR_TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(fileName, LinearLayout.LayoutParams(-1, -2))

        val messageView = TextView(this).apply {
            text = "التطبيق جاهز للتثبيت الآن"
            setTextSize(14f)
            setTextColor(COLOR_TEXT_PRIMARY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        }
        root.addView(messageView, LinearLayout.LayoutParams(-1, -2))

        val dialog = AlertDialog.Builder(this)
            .setView(root)
            .setPositiveButton("تثبيت الآن") { _, _ ->
                FlutterBuildApi(this).installApk(apkFile)
            }
            .setNeutralButton("لاحقاً") { _, _ ->
                Toast.makeText(
                    this,
                    "يمكنك التثبيت من مكتبة التطبيقات",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setCancelable(true)
            .create()

        dialog.show()
        styleDialog(dialog)
    }

    private fun showBuildFailedDialog(error: String) {
        val message = if (error.length > 400) {
            error.substring(0, 400) + "..."
        } else {
            error
        }

        val messageView = TextView(this).apply {
            text = message
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            setLineSpacing(0f, 1.3f)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setTextIsSelectable(true)
        }

        val scrollView = ScrollView(this).apply {
            addView(messageView)
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("فشل البناء")
            .setView(scrollView)
            .setPositiveButton("عرض السجل") { _, _ ->
                expandLogPanelAutomatically()
            }
            .setNegativeButton("إغلاق", null)
            .create()

        dialog.show()
        styleDialog(dialog)
    }

    private fun onBuildQuotaError(quotaMessage: String) {
        try {
            hideDownloadProgressDialog()
            buildInProgress = false
            stopTimerUpdates()

            waitingIndicator?.visibility = View.GONE

            addLog("━━━━━━━━━━━━━━━━━━━━━━")
            addLog("توقف البناء: حصة GitHub")
            showGithubQuotaDialog(quotaMessage)
        } catch (_: Exception) {
        }
    }

    private fun isHttp499Error(error: String?): Boolean {
        if (error == null) return false
        val lower = error.lowercase(Locale.US)
        return lower.contains("http 499") ||
                lower.contains("http 413") ||
                lower.contains("request body too large") ||
                lower.contains("payload too large") ||
                lower.contains("entity too large") ||
                lower.contains("content too large")
    }

    private fun onGraphQLTooLargeError() {
        try {
            hideDownloadProgressDialog()
            buildInProgress = false
            stopTimerUpdates()
            waitingIndicator?.visibility = View.GONE

            addLog("━━━━━━━━━━━━━━━━━━━━━━")
            addLog("فشل الرفع: المشروع كبير على GraphQL (HTTP 499)")
            addLog("اقتراح: أعد المحاولة بطريقة \"الدفعات\"")

            val projDir = currentProjectDir
            if (projDir == null) {
                showBuildFailedDialog(
                    "المشروع كبير على GraphQL. افتح المشروع ثم أعد المحاولة بطريقة الدفعات."
                )
                return
            }

            val type = getCurrentProjectType(projDir)

            AlertDialog.Builder(this)
                .setTitle("المشروع كبير على GraphQL")
                .setMessage(
                    "GraphQL يدعم فقط المشاريع حتى ~10 MB.\n\n" +
                            "مشروعك أكبر من ذلك.\n\n" +
                            "هل تريد إعادة البناء باستخدام طريقة \"الدفعات\"؟\n" +
                            "(تعمل مع أي حجم، لكن قد ترفع عدة commits)"
                )
                .setPositiveButton("نعم، جرّب الدفعات") { _, _ ->
                    showUploadMethodDialog(projDir, type, from499Dialog = true)
                }
                .setNegativeButton("إلغاء", null)
                .show()
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // File Node classes
    // ============================================================
    class FileNode(
        val file: File,
        val depth: Int,
        val isExpanded: Boolean
    )

    interface OnFileTreeListener {
        fun onFileClick(file: File, isDirectory: Boolean)
        fun onFileLongClick(file: File)
    }

    class FileTreeAdapter(
        private val listener: OnFileTreeListener?,
        private val colorTextPrimary: Int,
        private val dpUnit: Int,
        private val colorArrow: Int,
        private val iconFolder: Int,
        private val iconFolderOpen: Int,
        private val iconFile: Int
    ) : RecyclerView.Adapter<FileTreeAdapter.VH>() {

        private val nodes = mutableListOf<FileNode>()

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long {
            return nodes[position].file.absolutePath.hashCode().toLong()
        }

        @NonNull
        override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                textDirection = View.TEXT_DIRECTION_LTR
                minimumHeight = dpUnit * 36
            }

            val arrow = TextView(parent.context).apply {
                setTextColor(colorArrow)
                setTextSize(10f)
                gravity = Gravity.CENTER
            }
            row.addView(arrow, LinearLayout.LayoutParams(dpUnit * 16, dpUnit * 24))

            val icon = ImageView(parent.context)
            val iconParams = LinearLayout.LayoutParams(dpUnit * 20, dpUnit * 20).apply {
                setMargins(0, 0, dpUnit * 6, 0)
            }
            row.addView(icon, iconParams)

            val text = TextView(parent.context).apply {
                setTextColor(colorTextPrimary)
                setTextSize(13f)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.MIDDLE
                textDirection = View.TEXT_DIRECTION_LTR
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
            }
            row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))

            row.setBackgroundColor(0x00000000)
            return VH(row, arrow, icon, text)
        }

        override fun onBindViewHolder(@NonNull holder: VH, position: Int) {
            val node = nodes[position]
            val indentPx = dpUnit * (8 + node.depth * 18)
            holder.row.setPadding(indentPx, 0, dpUnit * 8, 0)

            if (node.file.isDirectory) {
                holder.arrow.visibility = View.VISIBLE
                holder.arrow.text = if (node.isExpanded) "▼" else "▶"
            } else {
                holder.arrow.visibility = View.INVISIBLE
            }

            if (node.file.isDirectory) {
                holder.icon.setImageResource(
                    if (node.isExpanded) iconFolderOpen else iconFolder
                )
            } else {
                holder.icon.setImageResource(iconFile)
            }

            holder.text.text = node.file.name

            holder.row.setOnClickListener {
                listener?.onFileClick(node.file, node.file.isDirectory)
            }
            holder.row.setOnLongClickListener {
                listener?.onFileLongClick(node.file)
                true
            }
        }

        override fun getItemCount(): Int = nodes.size

        fun submitList(newNodes: List<FileNode>) {
            val diff = DiffUtil.calculateDiff(FileTreeDiff(nodes, newNodes))
            nodes.clear()
            nodes.addAll(newNodes)
            diff.dispatchUpdatesTo(this)
        }

        class VH(
            itemView: View,
            val arrow: TextView,
            val icon: ImageView,
            val text: TextView
        ) : RecyclerView.ViewHolder(itemView) {
            val row: LinearLayout = itemView as LinearLayout
        }

        class FileTreeDiff(
            oldList: List<FileNode>,
            newList: List<FileNode>
        ) : DiffUtil.Callback() {

            private val oldList = ArrayList(oldList)
            private val newList = ArrayList(newList)

            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = newList.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                return oldList[oldPos].file.absolutePath ==
                        newList[newPos].file.absolutePath
            }

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val a = oldList[oldPos]
                val b = newList[newPos]
                return a.depth == b.depth && a.isExpanded == b.isExpanded
            }
        }
    }

    // ============================================================
    // Dialog styling
    // ============================================================
    private fun styleDialog(dialog: AlertDialog) {
        dialog.window?.let { window ->
            window.setBackgroundDrawable(
                GradientDrawable().apply {
                    setColor(COLOR_BACKGROUND)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), COLOR_BORDER)
                }
            )
        }

        val titleViewId = dialog.context.resources.getIdentifier(
            "android:id/alertTitle", null, null
        )
        if (titleViewId != 0) {
            dialog.findViewById<TextView>(titleViewId)?.let {
                it.setTextColor(COLOR_TEXT_PRIMARY)
                it.typeface = Typeface.DEFAULT_BOLD
            }
        }

        val messageViewId = dialog.context.resources.getIdentifier(
            "android:id/message", null, null
        )
        if (messageViewId != 0) {
            dialog.findViewById<TextView>(messageViewId)?.let {
                it.setTextColor(COLOR_TEXT_SECONDARY)
            }
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(COLOR_TEXT_BLUE)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(COLOR_TEXT_SECONDARY)

        dialog.listView?.let { lv ->
            lv.setBackgroundColor(COLOR_BACKGROUND)
            lv.divider = android.graphics.drawable.ColorDrawable(COLOR_BORDER)
            lv.dividerHeight = dp(1)
        }

        UiStyle.styleDialog(dialog)
    }

    // ============================================================
    // buildApk() معدّلة — تعرض Dialog طريقة الرفع
    // ============================================================
    private fun buildApk() {
        val projDir = currentProjectDir
        if (projDir == null || !projDir.exists()) {
            Toast.makeText(this, "لا يوجد مشروع مفتوح", Toast.LENGTH_SHORT).show()
            return
        }

        if (buildInProgress) {
            Toast.makeText(
                this,
                "هناك عملية بناء جارية بالفعل",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (BuildStateStore.hasActiveBuild(this)) {
            Toast.makeText(
                this,
                "يوجد بناء سابق لم يُكتمل",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Local Android builds must remain available without a GitHub account.
        // The remote path performs account validation and project linking only after the user selects it.
        val projectType = getCurrentProjectType(projDir)
        showBuildModeDialog(projDir, projectType)
    }

    /** Select local on-device build or the existing GitHub Actions workflow. */
    private fun showBuildModeDialog(projectDir: File, projectType: ProjectType) {
        val localSupported = supportsLocalAndroidBuild(projectDir)
        val choices = if (localSupported) {
            arrayOf("البناء المحلي على الجهاز", "البناء عبر GitHub Actions")
        } else {
            arrayOf("البناء عبر GitHub Actions")
        }
        AlertDialog.Builder(this)
            .setTitle("طريقة بناء APK")
            .setItems(choices) { _, which ->
                if (localSupported && which == 0) startLocalBuild(projectDir)
                else showUploadMethodDialog(projectDir, projectType)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    /** Static capability gate: do not offer the local Android engine for Flutter/non-Gradle roots. */
    private fun supportsLocalAndroidBuild(projectDir: File): Boolean {
        val settings = File(projectDir, "settings.gradle").isFile || File(projectDir, "settings.gradle.kts").isFile
        if (!settings) return false
        val candidates = ArrayList<File>()
        projectDir.listFiles()?.forEach { child ->
            if (child.isDirectory && child.name != ".gradle" && child.name != "build") {
                File(child, "build.gradle").takeIf { it.isFile }?.let(candidates::add)
                File(child, "build.gradle.kts").takeIf { it.isFile }?.let(candidates::add)
            }
        }
        File(projectDir, "build.gradle").takeIf { it.isFile }?.let(candidates::add)
        File(projectDir, "build.gradle.kts").takeIf { it.isFile }?.let(candidates::add)
        val pluginCatalog = File(projectDir, "gradle/libs.versions.toml")
        val catalogText = runCatching { pluginCatalog.readText().take(256 * 1024) }.getOrDefault("")
        return candidates.any { file ->
            val source = runCatching { file.readText().take(256 * 1024) }.getOrDefault("")
            val pluginAlias = Regex("""alias\s*\(\s*libs\.plugins\.[A-Za-z0-9_.]+\s*\)""").containsMatchIn(source)
            source.contains("com.android.application") ||
                (pluginAlias && catalogText.contains("com.android.application"))
        }
    }

    private fun startLocalBuild(projectDir: File) {
        if (!projectDir.isDirectory) {
            Toast.makeText(this, "مجلد المشروع غير صالح", Toast.LENGTH_LONG).show()
            return
        }
        if (buildInProgress || BuildStateStore.hasActiveBuild(this)) {
            Toast.makeText(this, "هناك عملية بناء جارية بالفعل", Toast.LENGTH_LONG).show()
            return
        }
        saveAllOpenTabs()
        clearBuildErrors()
        resetStepsState()
        drawerLayout?.closeDrawer(Gravity.START)
        buildStartedAtMs = System.currentTimeMillis()
        buildInProgress = true
        startTimerUpdates()
        uiSteps.clear()
        stepsAdapter?.notifyDataSetChanged()
        uploadCounterBox?.visibility = View.GONE
        hideDownloadProgressDialog()
        waitingIndicator?.visibility = View.VISIBLE
        waitingIndicator?.text = "يجري تجهيز بيئة البناء المحلي على الجهاز..."
        addLog("بدء البناء المحلي على الجهاز...")
        addLog("المشروع: ${projectDir.name}")
        addLog("لن يتم رفع ملفات المشروع إلى GitHub في هذا الوضع.")
        expandLogPanelAutomatically()
        try {
            LocalBuildForegroundService.start(this, projectDir)
        } catch (e: Exception) {
            buildInProgress = false
            stopTimerUpdates()
            waitingIndicator?.visibility = View.GONE
            addLog("تعذر بدء خدمة البناء المحلي: ${e.message}")
            Toast.makeText(this, "تعذر بدء البناء المحلي: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startBuildWithMethod(
        projDir: File,
        projectType: ProjectType,
        uploadMethod: GitHubApi.UploadMethod
    ) {
        val activeAccount = GitHubAccountManager.getActive(this) ?: run {
            Toast.makeText(
                this,
                "لا يوجد حساب GitHub نشط. سجّل الدخول أولاً",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        GitHubAccountManager.linkProjectToAccount(
            this,
            projDir.name,
            activeAccount.id
        )

        saveAllOpenTabs()
        clearBuildErrors()
        resetStepsState()
        drawerLayout?.closeDrawer(Gravity.START)

        buildStartedAtMs = System.currentTimeMillis()
        buildInProgress = true
        startTimerUpdates()
        uiSteps.clear()
        stepsAdapter?.notifyDataSetChanged()
        uploadCounterBox?.visibility = View.GONE

        val methodLabel = if (uploadMethod == GitHubApi.UploadMethod.GRAPHQL) {
            "GraphQL (سريع)"
        } else {
            "دفعات (يعمل مع أي حجم)"
        }

        addLog("بدء عملية بناء APK (${projectType.displayName}) عبر GitHub Actions...")
        addLog("الحساب: ${activeAccount.username}")
        addLog("طريقة الرفع: $methodLabel")

        expandLogPanelAutomatically()

        waitingIndicator?.visibility = View.VISIBLE
        waitingIndicator?.text =
            "في انتظار بدء GitHub Actions...\n" +
            "(قد يستغرق 1-3 دقائق)"

        hideDownloadProgressDialog()

        try {
            BuildForegroundService.startBuild(
                this, projDir, projectType, uploadMethod
            )
        } catch (e: Exception) {
            buildInProgress = false
            stopTimerUpdates()

            waitingIndicator?.visibility = View.GONE

            addLog("فشل بدء خدمة البناء: ${e.message}")
            Toast.makeText(
                this,
                "تعذر بدء البناء: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun getCurrentProjectType(projectDir: File): ProjectType {
        val typeId = prefs.getString("type_${projectDir.name}", "") ?: ""
        return if (typeId.isNotEmpty()) {
            ProjectType.fromId(typeId)
        } else {
            ProjectType.detectFromProject(projectDir)
        }
    }

    private fun onRepoReady(repoName: String) {
        if (repoName.isEmpty()) return
        val projDir = currentProjectDir ?: return
        prefs.edit().putString("repo_${projDir.name}", repoName).apply()
    }

    private fun showGithubQuotaDialog(githubMessage: String?) {
        val finalMessage = if (githubMessage.isNullOrBlank()) {
            "The job was not started because recent account payments have failed " +
                    "or your spending limit needs to be increased."
        } else {
            githubMessage
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        root.addView(TextView(this).apply {
            text = "GitHub أعاد الرسالة الرسمية التالية:"
            setTextSize(13f)
            setTextColor(COLOR_TEXT_PRIMARY)
        }, LinearLayout.LayoutParams(-1, -2))

        root.addView(TextView(this).apply {
            text = finalMessage
            setTextSize(12f)
            typeface = Typeface.MONOSPACE
            setTextColor(COLOR_TEXT_PRIMARY)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = UiStyle.rounded(
                this@MainActivity, COLOR_SURFACE, 12, COLOR_BORDER, 1
            )
            setTextIsSelectable(true)
        }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })

        root.addView(TextView(this).apply {
            text = "\nهذا يعني:\n" +
                    "• رصيد GitHub Actions المجاني انتهى\n" +
                    "• أو مشكلة في طريقة الدفع\n\n" +
                    "الحل:\n" +
                    "• بدّل إلى حساب GitHub آخر\n" +
                    "• أو افتح الفواتير وأصلح المشكلة"
            setTextSize(12f)
            setTextColor(COLOR_TEXT_PRIMARY)
            setLineSpacing(0f, 1.3f)
        }, LinearLayout.LayoutParams(-1, -2))

        val dialog = AlertDialog.Builder(this)
            .setTitle("لم يبدأ البناء على GitHub")
            .setView(root)
            .setPositiveButton("تبديل الحساب") { _, _ ->
                showAccountSwitcher()
            }
            .setNeutralButton("فتح الفواتير") { _, _ -> openBillingPage() }
            .setNegativeButton("إغلاق", null)
            .create()
        dialog.show()
        styleDialog(dialog)
    }

    private fun openBillingPage() {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/settings/billing")
                )
            )
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // BroadcastReceiver
    // ============================================================
    private fun registerBuildReceiver() {
        if (buildReceiverRegistered) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                if (isFinishing || isDestroyed) return

                when (intent.action) {

                    BuildForegroundService.ACTION_BUILD_STARTED -> {
                        val owner = intent.getStringExtra(
                            BuildForegroundService.EXTRA_OWNER
                        ) ?: ""
                        val repo = intent.getStringExtra(
                            BuildForegroundService.EXTRA_REPO
                        ) ?: ""
                        val runId = intent.getLongExtra(
                            BuildForegroundService.EXTRA_RUN_ID, 0L
                        )

                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            addLog("بدأ البناء على GitHub: $owner/$repo (#$runId)")
                            startBuildPolling()
                        }
                    }

                    BuildForegroundService.ACTION_BUILD_PROGRESS -> {
                        val message = intent.getStringExtra(
                            BuildForegroundService.EXTRA_MESSAGE
                        ) ?: ""
                        addLog(message)
                    }

                    BuildForegroundService.ACTION_BUILD_LOG -> {
                        val message = intent.getStringExtra(
                            BuildForegroundService.EXTRA_MESSAGE
                        ) ?: ""
                        addLog(message)
                    }

                    BuildForegroundService.ACTION_BUILD_STEPS -> {
                        val raw = intent.getStringExtra(
                            BuildForegroundService.EXTRA_STEPS_JSON
                        ) ?: ""
                        val steps = deserializeSteps(raw)
                        if (steps.isNotEmpty()) {
                            applySteps(steps)
                        }
                    }

                    BuildForegroundService.ACTION_BUILD_ANNOTATIONS -> {
                        val raw = intent.getStringExtra(
                            BuildForegroundService.EXTRA_MESSAGE
                        ) ?: ""
                        applyAnnotations(raw)
                    }

                    BuildForegroundService.ACTION_BUILD_STEP_LOGS -> {
                        val jobId = intent.getLongExtra(
                            BuildForegroundService.EXTRA_JOB_ID, 0L
                        )
                        val allRaw = intent.getStringExtra(
                            BuildForegroundService.EXTRA_ALL_LOGS
                        ) ?: ""
                        val errRaw = intent.getStringExtra(
                            BuildForegroundService.EXTRA_ERROR_LOGS
                        ) ?: ""
                        val allLogs = deserializeLogs(allRaw)
                        val errLogs = deserializeLogs(errRaw)
                        applyStepLogs(jobId, allLogs, errLogs)
                    }

                    BuildForegroundService.ACTION_BUILD_RUN_LOGS -> {
                        val logs = intent.getStringExtra(
                            BuildForegroundService.EXTRA_MESSAGE
                        ) ?: ""
                        addLog("━━━━━━━━━━━━━━━━━━━━━━\nسجل GitHub الكامل:\n$logs")
                    }

                    BuildForegroundService.ACTION_BUILD_REPO_READY -> {
                        val repoName = intent.getStringExtra(
                            BuildForegroundService.EXTRA_REPO_NAME
                        ) ?: ""
                        onRepoReady(repoName)
                    }

                    BuildForegroundService.ACTION_BUILD_UPLOAD_PROGRESS -> {
                        val current = intent.getIntExtra(
                            BuildForegroundService.EXTRA_CURRENT, 0
                        )
                        val total = intent.getIntExtra(
                            BuildForegroundService.EXTRA_TOTAL, 0
                        )
                        uploadCounterBox?.let { box ->
                            tvUploadCounter?.text = "تم رفع ($current/$total ملف)"
                            if (isLogPanelExpanded) box.visibility = View.VISIBLE
                        }
                    }

                    BuildForegroundService.ACTION_BUILD_DOWNLOAD_PROGRESS -> {
                        val downloaded = intent.getLongExtra(
                            BuildForegroundService.EXTRA_DOWNLOADED, 0L
                        )
                        val total = intent.getLongExtra(
                            BuildForegroundService.EXTRA_TOTAL_BYTES, 0L
                        )
                        updateDownloadProgress(downloaded, total)
                    }

                    BuildForegroundService.ACTION_BUILD_SUCCESS -> {
                        val apkPath = intent.getStringExtra(
                            BuildForegroundService.EXTRA_APK_PATH_BROADCAST
                        ) ?: ""

                        hideDownloadProgressDialog()
                        buildInProgress = false
                        stopTimerUpdates()

                        waitingIndicator?.visibility = View.GONE

                        addLog("━━━━━━━━━━━━━━━━━━━━━━")
                        addLog("تم بناء APK بنجاح")
                        addLog("المسار: $apkPath")

                        val apkFile = File(apkPath)
                        if (apkFile.exists()) {
                            showBuildSuccessDialog(apkFile)
                        }
                    }

                    BuildForegroundService.ACTION_BUILD_FAILED -> {
                        val error = intent.getStringExtra(
                            BuildForegroundService.EXTRA_ERROR_BROADCAST
                        ) ?: "خطأ غير معروف"

                        hideDownloadProgressDialog()
                        buildInProgress = false
                        stopTimerUpdates()

                        waitingIndicator?.visibility = View.GONE

                        addLog("━━━━━━━━━━━━━━━━━━━━━━")
                        addLog("فشل البناء: $error")

                        val is499 = isHttp499Error(error)

                        if (error.startsWith(FlutterBuildApi.GITHUB_QUOTA_PREFIX)) {
                            val quotaMsg = error.substring(
                                FlutterBuildApi.GITHUB_QUOTA_PREFIX.length
                            ).trim()
                            onBuildQuotaError(quotaMsg)
                        } else if (is499) {
                            onGraphQLTooLargeError()
                        } else if (error.startsWith(FlutterBuildApi.ANNOTATIONS_PREFIX)) {
                            val rawAnnot = error.substring(
                                FlutterBuildApi.ANNOTATIONS_PREFIX.length
                            )
                            applyAnnotations(rawAnnot)
                            showBuildFailedDialog(error)
                        } else {
                            showBuildFailedDialog(error)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BuildForegroundService.ACTION_BUILD_STARTED)
            addAction(BuildForegroundService.ACTION_BUILD_PROGRESS)
            addAction(BuildForegroundService.ACTION_BUILD_LOG)
            addAction(BuildForegroundService.ACTION_BUILD_STEPS)
            addAction(BuildForegroundService.ACTION_BUILD_ANNOTATIONS)
            addAction(BuildForegroundService.ACTION_BUILD_STEP_LOGS)
            addAction(BuildForegroundService.ACTION_BUILD_RUN_LOGS)
            addAction(BuildForegroundService.ACTION_BUILD_REPO_READY)
            addAction(BuildForegroundService.ACTION_BUILD_UPLOAD_PROGRESS)
            addAction(BuildForegroundService.ACTION_BUILD_DOWNLOAD_PROGRESS)
            addAction(BuildForegroundService.ACTION_BUILD_SUCCESS)
            addAction(BuildForegroundService.ACTION_BUILD_FAILED)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
            buildReceiver = receiver
            buildReceiverRegistered = true
        } catch (e: Exception) {
            Log.e("MainActivity", "فشل تسجيل BroadcastReceiver: ${e.message}")
        }
    }

    private fun unregisterBuildReceiver() {
        if (!buildReceiverRegistered) return
        try {
            buildReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {
        }
        buildReceiver = null
        buildReceiverRegistered = false
    }

    // ============================================================
    // Deserializers
    // ============================================================
    private fun deserializeSteps(raw: String): List<GitHubApi.Step> {
        if (raw.isEmpty()) return emptyList()
        val result = mutableListOf<GitHubApi.Step>()

        val items = raw.split("\u001F")
        for (item in items) {
            if (item.isEmpty()) continue
            val parts = item.split("\u001E")
            if (parts.size < 6) continue

            try {
                result.add(
                    GitHubApi.Step(
                        parts[0].toIntOrNull() ?: 0,
                        parts[1],
                        parts[2],
                        parts[3],
                        parts[4].toLongOrNull() ?: 0L,
                        parts[5].toLongOrNull() ?: 0L
                    )
                )
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun deserializeLogs(raw: String): List<FlutterBuildApi.BuildLogLine> {
        if (raw.isEmpty()) return emptyList()
        val result = mutableListOf<FlutterBuildApi.BuildLogLine>()

        val items = raw.split("\u001F")
        for (item in items) {
            if (item.isEmpty()) continue
            val parts = item.split("\u001E")
            if (parts.size < 5) continue

            try {
                result.add(
                    FlutterBuildApi.BuildLogLine(
                        parts[0],
                        parts[1].takeIf { it.isNotEmpty() },
                        parts[2].toIntOrNull() ?: 0,
                        parts[3].toIntOrNull() ?: 0,
                        parts[4].toIntOrNull() ?: 0
                    )
                )
            } catch (_: Exception) {
            }
        }
        return result
    }

    // ============================================================
    // طلب إذن الإشعارات
    // ============================================================
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            requestPermissions(arrayOf(permission), REQUEST_NOTIFICATION_PERMISSION)
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // Project management
    // ============================================================
    private fun loadProjectsList() {
        projectsList.clear()
        val dirs = projectsBaseDir.listFiles()
        if (dirs != null) {
            for (dir in dirs) {
                if (dir.isDirectory) projectsList.add(dir)
            }
        }
        projectsList.sortWith { f1, f2 ->
            f2.lastModified().compareTo(f1.lastModified())
        }

        val count = layoutProjectsList.findViewWithTag<TextView>("project_count")
        count?.text = "${projectsList.size} مشروع"

        val adapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_list_item_1, android.R.id.text1
        ) {
            override fun getCount(): Int = projectsList.size
            override fun getItem(position: Int): String? = null

            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val project = projectsList[position]

                val card = UiStyle.projectCard(this@MainActivity).apply {
                    isClickable = false
                    isFocusable = false
                    layoutParams = android.widget.AbsListView.LayoutParams(-1, -2)
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                }

                val appName = prefs.getString(
                    "name_${project.name}", project.name
                ) ?: project.name
                val firstLetter = appName.firstOrNull()
                    ?.uppercaseChar()?.toString() ?: "?"

                val iconContainer = FrameLayout(this@MainActivity).apply {
                    val iconSize = dp(48)
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                }

                val color = pickProjectColor(project.name)
                val fallback = UiStyle.circleIcon(
                    this@MainActivity, firstLetter, color
                ).apply {
                    layoutParams = FrameLayout.LayoutParams(-1, -1)
                }
                iconContainer.addView(fallback)

                card.addView(iconContainer)

                val textCol = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                val tcParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginStart = dp(14)
                }
                card.addView(textCol, tcParams)

                val title = TextView(this@MainActivity).apply {
                    text = appName
                    setTextColor(COLOR_TEXT_PRIMARY)
                    setTextSize(15f)
                    typeface = Typeface.DEFAULT_BOLD
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                }
                textCol.addView(title)

                val projectType = getCurrentProjectType(project)
                val typeBadge = TextView(this@MainActivity).apply {
                    text = "${ProjectType.symbolFor(projectType)}  ${projectType.displayName}"
                    setTextColor(ProjectType.colorFor(projectType))
                    setTextSize(10f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(7), dp(2), dp(7), dp(2))
                    background = UiStyle.rounded(
                        this@MainActivity,
                        withAlpha(ProjectType.colorFor(projectType), 0.15f),
                        6,
                        ProjectType.colorFor(projectType),
                        1
                    )
                }
                val badgeParams = LinearLayout.LayoutParams(-2, -2).apply {
                    topMargin = dp(4)
                }
                textCol.addView(typeBadge, badgeParams)

                val linkedAccount = GitHubAccountManager.getAccountForProject(
                    this@MainActivity, project.name
                )

                if (linkedAccount != null) {
                    val accountBadge = TextView(this@MainActivity).apply {
                        text = "@${linkedAccount.username}"
                        setTextColor(COLOR_PRIMARY_LIGHT)
                        setTextSize(10f)
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(7), dp(2), dp(7), dp(2))
                        background = UiStyle.rounded(
                            this@MainActivity,
                            0x221F6FEB,
                            6,
                            COLOR_PRIMARY_LIGHT,
                            1
                        )
                    }
                    val accountParams = LinearLayout.LayoutParams(-2, -2).apply {
                        topMargin = dp(4)
                    }
                    textCol.addView(accountBadge, accountParams)
                }

                val packageName = prefs.getString(
                    "package_${project.name}", ""
                ) ?: ""
                val subtitle = TextView(this@MainActivity).apply {
                    text = packageName.ifEmpty { "بدون حزمة" }
                    setTextColor(COLOR_TEXT_SECONDARY)
                    setTextSize(11f)
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                }
                val subParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(3)
                }
                textCol.addView(subtitle, subParams)

                val dateView = TextView(this@MainActivity).apply {
                    text = formatRelativeTime(project.lastModified())
                    setTextColor(COLOR_TEXT_SECONDARY)
                    setTextSize(10f)
                }
                val dateParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(4)
                }
                textCol.addView(dateView, dateParams)

                val arrow = TextView(this@MainActivity).apply {
                    text = "\u203A"
                    setTextColor(COLOR_ARROW)
                    setTextSize(22f)
                    gravity = Gravity.CENTER
                }
                val arrowParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    marginStart = dp(6)
                }
                card.addView(arrow, arrowParams)

                loadProjectIconAsync(project, iconContainer)

                return card
            }
        }
        lvProjects.adapter = adapter
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    private fun pickProjectColor(projectKey: String?): Int {
        val palette = intArrayOf(
            0xFF1F6FEB.toInt(), 0xFF2EA043.toInt(), 0xFFD29922.toInt(),
            0xFFCF222E.toInt(), 0xFF8957E5.toInt(), 0xFFDB61A2.toInt(),
            0xFF0EA5E9.toInt(), 0xFFF97316.toInt()
        )
        if (projectKey == null) return palette[0]
        val hash = kotlin.math.abs(projectKey.hashCode())
        return palette[hash % palette.size]
    }

    private fun formatRelativeTime(timestampMs: Long): String {
        val diff = System.currentTimeMillis() - timestampMs
        val minutes = diff / (60 * 1000)
        val hours = diff / (60 * 60 * 1000)
        val days = diff / (24 * 60 * 60 * 1000)

        return when {
            minutes < 1 -> "الآن"
            minutes < 60 -> "منذ $minutes دقيقة"
            hours < 24 -> "منذ $hours ساعة"
            days < 7 -> "منذ $days يوم"
            days < 30 -> "منذ ${days / 7} أسبوع"
            days < 365 -> "منذ ${days / 30} شهر"
            else -> "منذ ${days / 365} سنة"
        }
    }

    private fun isValidPackageName(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        val parts = packageName.split(".")
        if (parts.size < 2) return false

        for (part in parts) {
            if (part.isEmpty()) return false
            if (part[0].isDigit()) return false

            for (c in part) {
                val valid = (c in 'a'..'z') || (c in 'A'..'Z') ||
                        (c in '0'..'9') || c == '_'
                if (!valid) return false
            }
        }
        return true
    }

    private fun createProject() {
        when (currentCreateType) {
            ProjectType.FLUTTER -> createFlutterProject()
            ProjectType.JAVA -> createJavaProject()
            ProjectType.KOTLIN -> createKotlinProject()
            ProjectType.REACT_NATIVE -> createReactNativeProject()
        }
    }

    private fun createFlutterProject() {
        val appName = etAppName?.text?.toString()?.trim() ?: ""
        val packageName = etPackageName?.text?.toString()?.trim() ?: ""
        val selectedApi = spinnerApi?.selectedItem?.toString() ?: "33 (Android 13)"
        val apiLevel = selectedApi.split(" ").firstOrNull() ?: "33"

        if (appName.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال اسم التطبيق", Toast.LENGTH_SHORT).show()
            return
        }
        if (packageName.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال اسم الحزمة", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isValidPackageName(packageName)) {
            Toast.makeText(
                this,
                "اسم الحزمة غير صحيح\nمثال: com.example.myapp",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        var flutterIndex = spinnerFlutterVersion?.selectedItemPosition ?: 0
        if (flutterIndex < 0 || flutterIndex >= FLUTTER_VERSIONS.size) flutterIndex = 0
        val flutterVersion = FLUTTER_VERSIONS[flutterIndex]

        val uniqueAppName = findUniqueAppName(appName)
        val folderName = "project_${System.currentTimeMillis()}"
        val projDir = File(projectsBaseDir, folderName)
        if (!projDir.mkdirs()) {
            Toast.makeText(this, "خطأ في إنشاء مجلد المشروع", Toast.LENGTH_LONG).show()
            return
        }
        currentProjectDir = projDir

        val isPublicRepo = (repoTypeGroup?.checkedRadioButtonId ?: android.R.id.button1) ==
                android.R.id.button1

        prefs.edit()
            .putString("name_$folderName", uniqueAppName)
            .putString("package_$folderName", packageName)
            .putString("api_$folderName", apiLevel)
            .putString("flutter_version_$folderName", flutterVersion)
            .putString("type_$folderName", ProjectType.FLUTTER.id)
            .putBoolean("repo_public_$folderName", isPublicRepo)
            .apply()

        val ok = TemplateManager.writeTemplate(
            this,
            TemplateManager.DEFAULT_TEMPLATE,
            projDir,
            packageName,
            uniqueAppName,
            apiLevel
        )

        if (!ok) {
            Toast.makeText(
                this,
                "خطأ في إنشاء هيكل المشروع",
                Toast.LENGTH_LONG
            ).show()
            deleteRecursive(projDir)
            return
        }

        if (selectedIconUri != null) {
            saveIconToProject(selectedIconUri!!, projDir, ProjectType.FLUTTER)
        }

        invalidateProjectIconCache(projDir)

        if (uniqueAppName != appName) {
            Toast.makeText(
                this,
                "الاسم \"$appName\" كان مستخدماً، تم استخدام \"$uniqueAppName\"",
                Toast.LENGTH_LONG
            ).show()
        }

        addLog("تم إنشاء مشروع Flutter: $uniqueAppName (Flutter $flutterVersion)")
        resetIconSelection()
        shouldReloadProjectsList = true
        openProject(projDir)
    }

    private fun createJavaProject() {
        val appName = etAppName?.text?.toString()?.trim() ?: ""
        val packageName = etPackageName?.text?.toString()?.trim() ?: ""
        val selectedApi = spinnerApi?.selectedItem?.toString() ?: "34 (Android 14)"
        val apiLevel = selectedApi.split(" ").firstOrNull() ?: "34"

        if (appName.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال اسم التطبيق", Toast.LENGTH_SHORT).show()
            return
        }
        if (packageName.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال اسم الحزمة", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isValidPackageName(packageName)) {
            Toast.makeText(
                this,
                "اسم الحزمة غير صحيح\nمثال: com.example.myapp",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val uniqueAppName = findUniqueAppName(appName)
        val folderName = "project_${System.currentTimeMillis()}"
        val projDir = File(projectsBaseDir, folderName)

        if (!projDir.mkdirs()) {
            Toast.makeText(this, "خطأ في إنشاء مجلد المشروع", Toast.LENGTH_LONG).show()
            return
        }
        currentProjectDir = projDir

        val isPublicRepo = (repoTypeGroup?.checkedRadioButtonId ?: android.R.id.button1) ==
                android.R.id.button1

        val result = JavaProjectCreator.create(
            context = this,
            targetDir = projDir,
            packageName = packageName,
            appName = uniqueAppName,
            apiLevel = apiLevel
        )

        if (!result.success) {
            deleteRecursive(projDir)
            Toast.makeText(
                this,
                "فشل إنشاء المشروع: ${result.message}",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        prefs.edit()
            .putString("name_$folderName", uniqueAppName)
            .putString("package_$folderName", packageName)
            .putString("api_$folderName", apiLevel)
            .putString("type_$folderName", ProjectType.JAVA.id)
            .putBoolean("repo_public_$folderName", isPublicRepo)
            .apply()

        selectedIconUri?.let { saveIconToProject(it, projDir, ProjectType.JAVA) }
            ?: copyDefaultIcon(projDir, ProjectType.JAVA)

        invalidateProjectIconCache(projDir)

        if (uniqueAppName != appName) {
            Toast.makeText(
                this,
                "الاسم \"$appName\" كان مستخدماً، تم استخدام \"$uniqueAppName\"",
                Toast.LENGTH_LONG
            ).show()
        }

        addLog("تم إنشاء مشروع Java: $uniqueAppName")
        resetIconSelection()
        shouldReloadProjectsList = true
        openProject(projDir)
    }

    private fun createKotlinProject() {
        val appName = etAppName?.text?.toString()?.trim() ?: ""
        val packageName = etPackageName?.text?.toString()?.trim() ?: ""
        val selectedApi = spinnerApi?.selectedItem?.toString() ?: "34 (Android 14)"
        val apiLevel = selectedApi.split(" ").firstOrNull() ?: "34"

        if (appName.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال اسم التطبيق", Toast.LENGTH_SHORT).show()
            return
        }
        if (packageName.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال اسم الحزمة", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isValidPackageName(packageName)) {
            Toast.makeText(
                this,
                "اسم الحزمة غير صحيح\nمثال: com.example.myapp",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val uniqueAppName = findUniqueAppName(appName)
        val folderName = "project_${System.currentTimeMillis()}"
        val projDir = File(projectsBaseDir, folderName)

        if (!projDir.mkdirs()) {
            Toast.makeText(this, "خطأ في إنشاء مجلد المشروع", Toast.LENGTH_LONG).show()
            return
        }
        currentProjectDir = projDir

        val isPublicRepo = (repoTypeGroup?.checkedRadioButtonId ?: android.R.id.button1) ==
                android.R.id.button1

        val result = KotlinProjectCreator.create(
            context = this,
            targetDir = projDir,
            packageName = packageName,
            appName = uniqueAppName,
            apiLevel = apiLevel
        )

        if (!result.success) {
            deleteRecursive(projDir)
            Toast.makeText(
                this,
                "فشل إنشاء المشروع: ${result.message}",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        prefs.edit()
            .putString("name_$folderName", uniqueAppName)
            .putString("package_$folderName", packageName)
            .putString("api_$folderName", apiLevel)
            .putString("type_$folderName", ProjectType.KOTLIN.id)
            .putBoolean("repo_public_$folderName", isPublicRepo)
            .apply()

        selectedIconUri?.let { saveIconToProject(it, projDir, ProjectType.KOTLIN) }
            ?: copyDefaultIcon(projDir, ProjectType.KOTLIN)

        invalidateProjectIconCache(projDir)

        if (uniqueAppName != appName) {
            Toast.makeText(
                this,
                "الاسم \"$appName\" كان مستخدماً، تم استخدام \"$uniqueAppName\"",
                Toast.LENGTH_LONG
            ).show()
        }

        addLog("تم إنشاء مشروع Kotlin: $uniqueAppName")
        resetIconSelection()
        shouldReloadProjectsList = true
        openProject(projDir)
    }

    private fun createReactNativeProject() {
        val appName = etAppName?.text?.toString()?.trim() ?: ""
        val packageName = etPackageName?.text?.toString()?.trim() ?: ""
        val selectedApi = spinnerApi?.selectedItem?.toString() ?: "34 (Android 14)"
        val apiLevel = selectedApi.split(" ").firstOrNull() ?: "34"

        if (appName.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال اسم التطبيق", Toast.LENGTH_SHORT).show()
            return
        }
        if (packageName.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال اسم الحزمة", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isValidPackageName(packageName)) {
            Toast.makeText(
                this,
                "اسم الحزمة غير صحيح\nمثال: com.example.myapp",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val uniqueAppName = findUniqueAppName(appName)
        val folderName = "project_${System.currentTimeMillis()}"
        val projDir = File(projectsBaseDir, folderName)

        if (!projDir.mkdirs()) {
            Toast.makeText(this, "خطأ في إنشاء مجلد المشروع", Toast.LENGTH_LONG).show()
            return
        }
        currentProjectDir = projDir

        val isPublicRepo = (repoTypeGroup?.checkedRadioButtonId ?: android.R.id.button1) ==
                android.R.id.button1

        val result = ReactNativeProjectCreator.create(
            context = this,
            targetDir = projDir,
            packageName = packageName,
            appName = uniqueAppName,
            apiLevel = apiLevel
        )

        if (!result.success) {
            deleteRecursive(projDir)
            Toast.makeText(
                this,
                "فشل إنشاء المشروع: ${result.message}",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        prefs.edit()
            .putString("name_$folderName", uniqueAppName)
            .putString("package_$folderName", packageName)
            .putString("api_$folderName", apiLevel)
            .putString("type_$folderName", ProjectType.REACT_NATIVE.id)
            .putBoolean("repo_public_$folderName", isPublicRepo)
            .apply()

        selectedIconUri?.let { saveIconToProject(it, projDir, ProjectType.REACT_NATIVE) }
            ?: copyDefaultIcon(projDir, ProjectType.REACT_NATIVE)

        invalidateProjectIconCache(projDir)

        if (uniqueAppName != appName) {
            Toast.makeText(
                this,
                "الاسم \"$appName\" كان مستخدماً، تم استخدام \"$uniqueAppName\"",
                Toast.LENGTH_LONG
            ).show()
        }

        addLog("تم إنشاء مشروع React Native: $uniqueAppName")
        resetIconSelection()
        shouldReloadProjectsList = true
        openProject(projDir)
    }

    private fun findUniqueAppName(baseName: String?): String {
        if (baseName.isNullOrBlank()) return baseName ?: ""
        val trimmed = baseName.trim()

        val existingNames = HashSet<String>()
        projectsBaseDir.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val name = prefs.getString("name_${dir.name}", "") ?: ""
            if (name.isNotEmpty()) existingNames.add(name)
        }

        if (!existingNames.contains(trimmed)) return trimmed

        for (suffix in 1..999) {
            val candidate = "${trimmed}_$suffix"
            if (!existingNames.contains(candidate)) return candidate
        }
        return trimmed
    }

    private fun copyDefaultIcon(projectDir: File, type: ProjectType = ProjectType.FLUTTER) {
        if (type == ProjectType.FLUTTER) {
            val xmlIcon = File(
                projectDir,
                "android/app/src/main/res/drawable/ic_launcher.xml"
            )
            if (xmlIcon.exists()) {
                val pngIcon = File(
                    projectDir,
                    "android/app/src/main/res/drawable/ic_launcher.png"
                )
                if (pngIcon.exists()) pngIcon.delete()
                return
            }
        }

        try {
            val targetDir: File
            val targetFile: File

            when (type) {
                ProjectType.FLUTTER -> {
                    targetDir = File(projectDir, "android/app/src/main/res/drawable")
                    targetFile = File(targetDir, "ic_launcher.png")
                }
                ProjectType.JAVA, ProjectType.KOTLIN -> {
                    targetDir = File(projectDir, "app/src/main/res/drawable")
                    targetFile = File(targetDir, "app_icon.png")
                }
                ProjectType.REACT_NATIVE -> {
                    targetDir = File(projectDir, "android/app/src/main/res/drawable")
                    targetFile = File(targetDir, "ic_launcher.png")
                }
            }

            if (!targetDir.exists() && !targetDir.mkdirs()) return
            if (targetFile.exists()) return

            var defaultIcon = BitmapFactory.decodeResource(
                resources, R.drawable.ic_launcher
            )
            if (defaultIcon == null) {
                defaultIcon = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(defaultIcon)
                canvas.drawColor(COLOR_PRIMARY)
            }

            val resized = Bitmap.createScaledBitmap(defaultIcon, 512, 512, true)
            FileOutputStream(targetFile).use { fos ->
                resized.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
            if (resized != defaultIcon) resized.recycle()
            defaultIcon.recycle()
        } catch (e: Exception) {
            addLog("تعذر إنشاء أيقونة افتراضية: ${e.message}")
        }
    }

    private fun saveIconToProject(
        imageUri: Uri,
        projectDir: File,
        type: ProjectType = ProjectType.FLUTTER
    ) {
        try {
            val targetDir: File
            val targetFile: File

            when (type) {
                ProjectType.FLUTTER -> {
                    targetDir = File(projectDir, "android/app/src/main/res/drawable")
                    targetFile = File(targetDir, "ic_launcher.png")
                }
                ProjectType.JAVA, ProjectType.KOTLIN -> {
                    targetDir = File(projectDir, "app/src/main/res/drawable")
                    targetFile = File(targetDir, "app_icon.png")
                }
                ProjectType.REACT_NATIVE -> {
                    targetDir = File(projectDir, "android/app/src/main/res/drawable")
                    targetFile = File(targetDir, "ic_launcher.png")
                }
            }

            if (!targetDir.exists() && !targetDir.mkdirs()) {
                addLog("تعذر إنشاء مجلد الأيقونة")
                return
            }

            if (type == ProjectType.FLUTTER) {
                val xmlIcon = File(targetDir, "ic_launcher.xml")
                if (xmlIcon.exists()) xmlIcon.delete()
            }

            val inputStream = contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                addLog("تعذر فتح الصورة")
                copyDefaultIcon(projectDir, type)
                return
            }

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val originalBitmap = BitmapFactory.decodeStream(inputStream, null, opts)
            inputStream.close()

            if (originalBitmap == null) {
                addLog("تعذر قراءة الصورة")
                copyDefaultIcon(projectDir, type)
                return
            }

            val width = originalBitmap.width
            val height = originalBitmap.height
            val size = minOf(width, height)
            val left = (width - size) / 2
            val top = (height - size) / 2

            val squaredBitmap = Bitmap.createBitmap(
                originalBitmap, left, top, size, size
            )
            val resizedBitmap = Bitmap.createScaledBitmap(
                squaredBitmap, 512, 512, true
            )

            FileOutputStream(targetFile).use { fos ->
                resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }

            if (resizedBitmap != squaredBitmap) resizedBitmap.recycle()
            if (squaredBitmap != originalBitmap) squaredBitmap.recycle()
            originalBitmap.recycle()

            addLog("تم حفظ أيقونة التطبيق (${type.displayName})")
        } catch (e: Exception) {
            addLog("خطأ في حفظ الأيقونة: ${e.message}")
            copyDefaultIcon(projectDir, type)
        }
    }

    private fun restartBrandAnimationIfPossible() {
        layoutProjectsList.post {
            val brand = findBrandTextView(layoutProjectsList) ?: return@post
            brand.alpha = 0f
            brand.translationY = -40f
            brand.scaleX = 0.85f
            brand.scaleY = 0.85f
            playBrandAnimation(brand)
        }
    }

    private fun findBrandTextView(root: ViewGroup): TextView? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is TextView) {
                if (child.text?.toString() == "KlencodIDE") return child
            }
            if (child is ViewGroup) {
                val found = findBrandTextView(child)
                if (found != null) return found
            }
        }
        return null
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentScreen == SCREEN_EDITOR) {
            drawerLayout?.let { dl ->
                if (dl.isDrawerOpen(Gravity.START)) {
                    dl.closeDrawer(Gravity.START)
                    return
                }
            }
            saveAllOpenTabs()
            showScreen(SCREEN_PROJECTS_LIST, true)
            isEditorVisible = false
        } else if (currentScreen == SCREEN_CREATE_PROJECT) {
            resetIconSelection()
            showScreen(SCREEN_PROJECTS_LIST, true)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_PICK_ICON_CREATE &&
            resultCode == RESULT_OK && data != null
        ) {
            val originalUri = data.data
            if (originalUri != null) {
                selectedIconUri = cropImageToSquare(originalUri)
                val fileName = getFileNameFromUri(selectedIconUri)
                tvIconSelected?.text = "تم اختيار: ${fileName ?: "صورة"}"
                tvIconSelected?.setTextColor(COLOR_PRIMARY)
                ivIconPreview?.visibility = View.VISIBLE
                ivIconPreview?.setImageURI(selectedIconUri)
            }
        } else if (requestCode == REQUEST_PICK_ICON && settingsDialog != null) {
            settingsDialog?.onActivityResult(requestCode, resultCode, data)
        } else if (requestCode == REQUEST_IMPORT_PROJECT_ZIP &&
            resultCode == RESULT_OK && data != null
        ) {
            data.data?.let { importProjectFromZip(it) }
        }
    }

    private fun getFileNameFromUri(uri: Uri?): String? {
        if (uri == null) return null

        if (uri.scheme == "content") {
            var cursor: Cursor? = null
            try {
                cursor = contentResolver.query(uri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) return cursor.getString(nameIndex)
                }
            } catch (_: Exception) {
            } finally {
                cursor?.close()
            }
        }

        var fileName = uri.path
        if (fileName != null) {
            val cut = fileName.lastIndexOf('/')
            if (cut != -1) fileName = fileName.substring(cut + 1)
        }
        return fileName
    }

    // ============================================================
    // Project operations
    // ============================================================
    private fun openProject(projectDir: File) {
        currentProjectDir = projectDir
        openTabs.clear()
        activeTabIndex = -1
        currentOpenFile = null
        releaseCurrentViewer()

        favoritePaths = editorStateStore.getFavorites(editorProjectKey())

        val folderName = projectDir.name
        val appName = prefs.getString("name_$folderName", folderName) ?: folderName
        val packageName = prefs.getString("package_$folderName", "") ?: ""
        val apiLevel = prefs.getString("api_$folderName", "") ?: ""

        val projectType = getCurrentProjectType(projectDir)

        val linkedAccount = GitHubAccountManager.getAccountForProject(
            this, folderName
        )
        val accountInfo = if (linkedAccount != null) {
            " | الحساب: ${linkedAccount.username}"
        } else {
            val active = GitHubAccountManager.getActive(this)
            if (active != null) {
                " | الحساب: ${active.username} (سيُربط)"
            } else {
                " | لا يوجد حساب"
            }
        }

        val infoText = if (projectType.isFlutter) {
            val flutterVersion = prefs.getString(
                "flutter_version_$folderName", "3.47.2"
            ) ?: "3.47.2"
            "المشروع: $appName | النوع: ${projectType.displayName} | " +
                    "الحزمة: $packageName | API: $apiLevel | Flutter: $flutterVersion$accountInfo"
        } else {
            "المشروع: $appName | النوع: ${projectType.displayName} | " +
                    "الحزمة: $packageName | API: $apiLevel$accountInfo"
        }

        tvProjectInfo?.text = infoText

        expandedDirs.clear()
        fileTreeAdapter?.submitList(emptyList())
        showScreen(SCREEN_EDITOR, true)
        renderTabStrip()

        val recentTabs = editorStateStore.getRecentTabs(editorProjectKey())

        var activeFile: File? = null
        if (recentTabs.isEmpty()) {
            activeFile = findMainFile(projectDir, projectType)
        } else {
            val activePath = editorStateStore.getActiveTab(editorProjectKey())
            if (activePath.isNotEmpty()) {
                val candidate = File(projectDir, activePath)
                if (candidate.isFile) activeFile = candidate
            }
            if (activeFile == null) {
                activeFile = findMainFile(projectDir, projectType)
            }
        }

        activeFile?.let { openFileInEditor(it) }

        if (recentTabs.isNotEmpty()) {
            val finalActiveFile = activeFile
            val tabsToOpen = ArrayList(recentTabs)
            backgroundExecutor?.execute {
                for (relative in tabsToOpen) {
                    val file = File(projectDir, relative)
                    if (!file.isFile) continue
                    if (finalActiveFile != null && file == finalActiveFile) continue
                    if (currentProjectDir != projectDir) return@execute
                    runOnUiThread {
                        if (currentProjectDir != projectDir) return@runOnUiThread
                        openFileInBackground(file)
                    }
                }
            }
        }

        loadFileTreeAsync(projectDir)

        addLog("تم فتح المشروع: $appName (${projectType.displayName})")
    }

    private fun findMainFile(projectDir: File, type: ProjectType): File? {
        return when (type) {
            ProjectType.FLUTTER -> {
                val mainDart = File(File(projectDir, "lib"), "main.dart")
                if (mainDart.exists()) mainDart else null
            }
            ProjectType.JAVA -> {
                findFileRecursive(
                    File(projectDir, "app/src/main/java"),
                    "MainActivity.java"
                )
            }
            ProjectType.KOTLIN -> {
                findFileRecursive(
                    File(projectDir, "app/src/main/java"),
                    "MainActivity.kt"
                ) ?: findFileRecursive(
                    File(projectDir, "app/src/main/kotlin"),
                    "MainActivity.kt"
                )
            }
            ProjectType.REACT_NATIVE -> {
                val candidates = arrayOf(
                    "App.tsx", "App.ts", "App.jsx", "App.js", "index.js", "index.ts"
                )
                for (name in candidates) {
                    val f = File(projectDir, name)
                    if (f.isFile) return f
                }
                val srcDir = File(projectDir, "src")
                if (srcDir.isDirectory) {
                    val appInSrc = findFileRecursive(srcDir, "App.tsx")
                        ?: findFileRecursive(srcDir, "App.ts")
                        ?: findFileRecursive(srcDir, "App.jsx")
                        ?: findFileRecursive(srcDir, "App.js")
                    if (appInSrc != null) return appInSrc
                }
                null
            }
        }
    }

    private fun openFileInBackground(file: File) {
        if (!file.isFile) return
        if (findTab(file.absolutePath) != null) return

        val kind = FileKind.detect(file.name)
        val state = EditorTabState(file.absolutePath)

        if (kind == FileKind.TEXT || kind == FileKind.UNKNOWN) {
            try {
                state.content = readFileText(file)
            } catch (_: IOException) {
                return
            }
        } else {
            state.content = ""
        }

        state.loaded = true
        state.dirty = false
        openTabs.add(state)

        renderTabStrip()
        saveEditorProjectState()
    }

    private fun loadFileTreeAsync(projectDir: File) {
        val executor = backgroundExecutor ?: return
        loadingTreeForProject = projectDir

        executor.execute {
            val nodes = mutableListOf<FileNode>()
            try {
                collectAllFilesAsTree(projectDir, nodes)
            } catch (_: Exception) {
            }

            runOnUiThread {
                if (loadingTreeForProject != projectDir) return@runOnUiThread
                if (currentProjectDir != projectDir) return@runOnUiThread
                fileTreeAdapter?.submitList(nodes)
            }
        }
    }

    private fun showProjectOptionsDialog(projectDir: File) {
        val name = prefs.getString("name_${projectDir.name}", "") ?: ""
        val repoName = prefs.getString("repo_${projectDir.name}", "") ?: ""
        val projectType = getCurrentProjectType(projectDir)

        val options = mutableListOf<String>()
        options.add("فتح المشروع (${projectType.displayName})")
        options.add("حذف المشروع")
        options.add("حذف المستودع من GitHub")

        val linkedAccount = GitHubAccountManager.getAccountForProject(
            this, projectDir.name
        )
        if (linkedAccount != null) {
            options.add("تغيير الحساب المرتبط (${linkedAccount.username})")
        } else {
            options.add("ربط بحساب GitHub")
        }

        val arr = options.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("خيارات المشروع: $name")
            .setAdapter(UiStyle.dialogListAdapter(this, arr)) { _, which ->
                when (which) {
                    0 -> openProject(projectDir)
                    1 -> deleteProject(projectDir)
                    2 -> showDeleteRepoDialog(projectDir, repoName)
                    3 -> showChangeProjectAccountDialog(projectDir)
                }
            }
            .setNegativeButton("إلغاء", null)
            .create()
            .also { dialog ->
                dialog.show()
                styleDialog(dialog)
            }
    }

    private fun showChangeProjectAccountDialog(projectDir: File) {
        val accounts = GitHubAccountManager.listAccounts(this)
        if (accounts.isEmpty()) {
            Toast.makeText(
                this,
                "لا توجد حسابات محفوظة. أضف حساباً أولاً",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val currentAccount = GitHubAccountManager.getAccountForProject(
            this, projectDir.name
        )

        val labels = accounts.map { account ->
            val active = if (account.id == currentAccount?.id) " ✓" else ""
            "@${account.username}$active"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("اختر الحساب المرتبط بالمشروع")
            .setItems(labels) { _, which ->
                val selected = accounts[which]
                GitHubAccountManager.linkProjectToAccount(
                    this, projectDir.name, selected.id
                )
                Toast.makeText(
                    this,
                    "تم ربط المشروع بـ @${selected.username}",
                    Toast.LENGTH_SHORT
                ).show()
                loadProjectsList()
            }
            .setNegativeButton("إلغاء", null)
            .create()
            .also { dialog ->
                dialog.show()
                styleDialog(dialog)
            }
    }

    private fun deleteProject(projectDir: File) {
        val name = prefs.getString("name_${projectDir.name}", "") ?: ""

        val messageView = TextView(this).apply {
            text = "هل أنت متأكد أنك تريد حذف المشروع \"$name\"؟\n\n" +
                    "ملاحظة: هذا يحذف الملفات من التطبيق فقط.\n" +
                    "المستودع على GitHub سيبقى كما هو."
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(14f)
            setLineSpacing(0f, 1.2f)
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("تأكيد حذف المشروع")
            .setView(messageView)
            .setPositiveButton("حذف") { _, _ ->
                performDeleteProjectLocal(projectDir, name)
            }
            .setNegativeButton("إلغاء", null)
            .create()

        dialog.show()
        styleDialog(dialog)
    }

    private fun performDeleteProjectLocal(projectDir: File, name: String) {
        invalidateProjectIconCache(projectDir)
        deleteRecursive(projectDir)

        prefs.edit()
            .remove("name_${projectDir.name}")
            .remove("package_${projectDir.name}")
            .remove("api_${projectDir.name}")
            .remove("repo_public_${projectDir.name}")
            .remove("flutter_version_${projectDir.name}")
            .remove("type_${projectDir.name}")
            .remove("repo_${projectDir.name}")
            .remove("account_${projectDir.name}")
            .apply()

        shouldReloadProjectsList = true
        loadProjectsList()
        addLog("تم حذف المشروع: $name")
        Toast.makeText(
            this,
            "تم حذف المشروع \"$name\"",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showDeleteRepoDialog(projectDir: File, repoName: String) {
        val name = prefs.getString("name_${projectDir.name}", "") ?: ""

        if (repoName.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("لا يوجد مستودع مرتبط")
                .setMessage(
                    "المشروع \"$name\" لم يتم بناؤه بعد على GitHub.\n\n" +
                            "لإنشاء مستودع، استخدم \"بناء APK\" من داخل المشروع."
                )
                .setPositiveButton("حسناً", null)
                .create()
                .also { dialog ->
                    dialog.show()
                    styleDialog(dialog)
                }
            return
        }

        val linkedAccount = GitHubAccountManager.getAccountForProject(
            this, projectDir.name
        )
        val activeAccount = GitHubAccountManager.getActive(this)

        val username = linkedAccount?.username
            ?: activeAccount?.username
            ?: AuthManager.getInstance(this).getGithubUsername()

        if (username.isNullOrEmpty()) {
            Toast.makeText(
                this,
                "لا يوجد حساب مرتبط. اربط حساباً أولاً",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        showDeleteRepoConfirm(username, repoName, name)
    }

    private fun showDeleteRepoConfirm(
        username: String,
        repoName: String,
        projectName: String
    ) {
        val url = "https://github.com/$username/$repoName/settings"

        val messageView = TextView(this).apply {
            text = "سيتم فتح صفحة إعدادات المستودع على GitHub.\n\n" +
                    "المستودع: $repoName\n" +
                    "الحساب: $username\n\n" +
                    "من هناك، اضغط على \"Delete this repository\" " +
                    "في أسفل الصفحة لحذف المستودع نهائياً.\n\n" +
                    "ملاحظة: هذا الإجراء لا يحذف المشروع من التطبيق."
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            setLineSpacing(0f, 1.25f)
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("حذف المستودع من GitHub")
            .setView(messageView)
            .setPositiveButton("فتح GitHub") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "تعذر فتح الرابط: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .create()

        dialog.show()
        styleDialog(dialog)
    }

    private fun showRootAddDialog() {
        val projDir = currentProjectDir ?: run {
            Toast.makeText(this, "لا يوجد مشروع مفتوح", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf("إضافة ملف", "إضافة مجلد", "استيراد")

        AlertDialog.Builder(this)
            .setTitle("إضافة إلى جذر المشروع")
            .setAdapter(UiStyle.dialogListAdapter(this, options)) { _, which ->
                when (which) {
                    0 -> showCreateFileAtRootDialog(projDir)
                    1 -> showCreateFolderAtRootDialog(projDir)
                    2 -> openImportAtRoot(projDir)
                }
            }
            .setNegativeButton("إلغاء", null)
            .create()
            .also { dialog ->
                dialog.show()
                styleDialog(dialog)
            }
    }

    private fun showCreateFileAtRootDialog(projDir: File) {
        val input = EditText(this).apply {
            hint = "اسم الملف (مثلاً: README.md)"
            setHintTextColor(COLOR_TEXT_PRIMARY)
            setTextColor(COLOR_TEXT_PRIMARY)
            background = UiStyle.rounded(
                this@MainActivity, COLOR_SURFACE, 12, COLOR_BORDER, 1
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        AlertDialog.Builder(this)
            .setTitle("ملف جديد في الجذر")
            .setView(input)
            .setPositiveButton("إنشاء") { _, _ ->
                val fileName = input.text.toString().trim()
                if (fileName.isEmpty()) {
                    Toast.makeText(
                        this,
                        "الرجاء إدخال اسم الملف",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                if (fileName.contains("/") || fileName.contains("\\")) {
                    Toast.makeText(
                        this,
                        "لا يمكن أن يحتوي الاسم على / أو \\",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val newFile = File(projDir, fileName)
                if (newFile.exists()) {
                    Toast.makeText(
                        this,
                        "الملف موجود بالفعل",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                try {
                    if (newFile.createNewFile()) {
                        refreshFileTree()
                        openFileInEditor(newFile)
                        addLog("تم إنشاء ملف في الجذر: $fileName")
                    } else {
                        Toast.makeText(
                            this,
                            "تعذر إنشاء الملف",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: IOException) {
                    addLog("خطأ: ${e.message}")
                }
            }
            .setNegativeButton("إلغاء", null)
            .create()
            .also { dialog ->
                dialog.show()
                styleDialog(dialog)
            }
    }

    private fun showCreateFolderAtRootDialog(projDir: File) {
        val input = EditText(this).apply {
            hint = "اسم المجلد (مثلاً: assets)"
            setHintTextColor(COLOR_TEXT_PRIMARY)
            setTextColor(COLOR_TEXT_PRIMARY)
            background = UiStyle.rounded(
                this@MainActivity, COLOR_SURFACE, 12, COLOR_BORDER, 1
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        AlertDialog.Builder(this)
            .setTitle("مجلد جديد في الجذر")
            .setView(input)
            .setPositiveButton("إنشاء") { _, _ ->
                val folderName = input.text.toString().trim()
                if (folderName.isEmpty()) {
                    Toast.makeText(
                        this,
                        "الرجاء إدخال اسم المجلد",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                if (folderName.contains("/") || folderName.contains("\\")) {
                    Toast.makeText(
                        this,
                        "لا يمكن أن يحتوي الاسم على / أو \\",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val newFolder = File(projDir, folderName)
                if (newFolder.exists()) {
                    Toast.makeText(
                        this,
                        "المجلد موجود بالفعل",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                if (newFolder.mkdirs()) {
                    refreshFileTree()
                    addLog("تم إنشاء مجلد في الجذر: $folderName")
                } else {
                    Toast.makeText(
                        this,
                        "تعذر إنشاء المجلد",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .create()
            .also { dialog ->
                dialog.show()
                styleDialog(dialog)
            }
    }

    private fun openImportAtRoot(projDir: File) {
        val intent = Intent(this, FileManagerActivity::class.java)
        intent.putExtra(FileManagerActivity.EXTRA_TARGET_DIR, projDir.absolutePath)
        startActivity(intent)
    }

    private fun toggleDirectory(dir: File) {
        if (expandedDirs.contains(dir)) expandedDirs.remove(dir)
        else expandedDirs.add(dir)
        refreshFileTree()
    }

    private fun saveTreeScrollState() {
        val layoutManager = fileTreeLayoutManager ?: return
        treeScrollPosition = layoutManager.findFirstVisibleItemPosition()
        val firstChild = layoutManager.findViewByPosition(treeScrollPosition)
        treeScrollOffset = firstChild?.top ?: 0
    }

    private fun restoreTreeScrollState() {
        val rv = rvFileTree ?: return
        val layoutManager = fileTreeLayoutManager ?: return

        rv.post {
            val adapter = fileTreeAdapter
            if (adapter != null && adapter.itemCount > 0) {
                val safe = minOf(treeScrollPosition, adapter.itemCount - 1)
                layoutManager.scrollToPositionWithOffset(safe, treeScrollOffset)
            }
        }
    }

    private fun refreshFileTree() {
        val projDir = currentProjectDir ?: return
        if (!projDir.exists()) return
        if (rvFileTree == null || fileTreeAdapter == null) return
        loadFileTreeAsync(projDir)
    }

    private fun collectAllFilesAsTree(dir: File, result: MutableList<FileNode>) {
        val dirs = mutableListOf<File>()
        val files = mutableListOf<File>()

        dir.listFiles()?.forEach { child ->
            if (child.name == ".gitignore") return@forEach
            if (child.isDirectory) dirs.add(child) else files.add(child)
        }

        dirs.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        files.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        val allItems = mutableListOf<File>()
        allItems.addAll(dirs)
        allItems.addAll(files)

        for (item in allItems) addFileNode(item, result, 0)
    }

    private fun addFileNode(item: File, result: MutableList<FileNode>, depth: Int) {
        val isExpanded = item.isDirectory && expandedDirs.contains(item)
        result.add(FileNode(item, depth, isExpanded))

        if (isExpanded) {
            val subDirs = mutableListOf<File>()
            val subFiles = mutableListOf<File>()

            item.listFiles()?.forEach { child ->
                if (child.name == ".gitignore") return@forEach
                if (child.isDirectory) subDirs.add(child) else subFiles.add(child)
            }

            subDirs.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            subFiles.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            val allSub = mutableListOf<File>()
            allSub.addAll(subDirs)
            allSub.addAll(subFiles)

            for (child in allSub) addFileNode(child, result, depth + 1)
        }
    }

    private fun exportProjectAsZip() {
        try {
            val projDir = currentProjectDir
            if (projDir == null || !projDir.exists()) {
                Toast.makeText(this, "لا يوجد مشروع مفتوح", Toast.LENGTH_SHORT).show()
                return
            }

            saveAllOpenTabs()
            drawerLayout?.closeDrawer(Gravity.START)

            var downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir == null) downloadsDir = File(filesDir, "exports")
            if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                Toast.makeText(
                    this,
                    "تعذر إنشاء مجلد التصدير",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val appName = prefs.getString(
                "name_${projDir.name}", "project"
            ) ?: "project"

            var safeName = appName.replace(Regex("[^A-Za-z0-9._\\-]+"), "_")
            if (safeName.isEmpty()) safeName = "project"

            val zipFile = File(
                downloadsDir,
                "${safeName}_${System.currentTimeMillis()}.zip"
            )

            FileOutputStream(zipFile).use { fos ->
                BufferedOutputStream(fos).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        addFilesToZip(projDir, projDir.absolutePath, zos)
                        zos.flush()
                    }
                }
            }

            addLog("تم تصدير ZIP: ${zipFile.name}")

            AlertDialog.Builder(this)
                .setTitle("تم تصدير ZIP")
                .setMessage(
                    "تم الحفظ في:\n${zipFile.absolutePath}\n\nيمكنك فتحه أو مشاركته."
                )
                .setPositiveButton("فتح") { _, _ -> openZipFile(zipFile) }
                .setNeutralButton("مشاركة") { _, _ -> shareZipFile(zipFile) }
                .setNegativeButton("إغلاق", null)
                .show()
        } catch (t: Throwable) {
            val err = StringBuilder()
            err.append("خطأ: ").append(t.javaClass.simpleName)
            t.message?.let { err.append("\n").append(it) }
            addLog(err.toString())
            Toast.makeText(this, err.toString(), Toast.LENGTH_LONG).show()
        }
    }

    @Throws(IOException::class)
    private fun addFilesToZip(source: File, basePath: String, zos: ZipOutputStream) {
        val files = source.listFiles() ?: return
        val buffer = ByteArray(4096)

        for (file in files) {
            if (file.name == ".gitignore") continue

            val entryPath = file.absolutePath.substring(basePath.length + 1)

            if (file.isDirectory) {
                zos.putNextEntry(ZipEntry("$entryPath/"))
                zos.closeEntry()
                addFilesToZip(file, basePath, zos)
            } else {
                FileInputStream(file).use { fis ->
                    zos.putNextEntry(ZipEntry(entryPath))
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        zos.write(buffer, 0, bytesRead)
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun openZipFile(zipFile: File) {
        if (!zipFile.exists()) return
        try {
            val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", zipFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/zip")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "فتح ZIP"))
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "تعذر فتح الملف: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun shareZipFile(zipFile: File) {
        if (!zipFile.exists()) return
        try {
            val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", zipFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "مشاركة ZIP"))
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "تعذر مشاركة الملف: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun reloadFileFromDisk(file: File) {
        if (!file.exists()) return
        val projDir = currentProjectDir ?: return

        try {
            var state = findTab(file.absolutePath)
            val kind = FileKind.detect(file.name)

            if (kind == FileKind.TEXT || kind == FileKind.UNKNOWN) {
                val content = readFileText(file)
                if (state == null) {
                    state = EditorTabState(file.absolutePath)
                    openTabs.add(state)
                }
                state.content = content
                state.loaded = true
                state.dirty = false
                currentOpenFile = file
                activeTabIndex = openTabs.indexOf(state)

                showFileViewer(file)

                if (activeViewerKind == FileKind.TEXT ||
                    activeViewerKind == FileKind.UNKNOWN
                ) {
                    codeEditorView?.setTextImmediate(content, file.absolutePath)
                }

                refreshExternalErrorMarkersForCurrentFile()
                tvCurrentFilePath?.text =
                    "الملف: ${file.name} - ${getRelativePath(file)}"
                renderTabStrip()
                saveEditorProjectState()
            } else {
                currentOpenFile = file
                showFileViewer(file)
                tvCurrentFilePath?.text =
                    "الملف: ${file.name} - ${getRelativePath(file)}"
            }
        } catch (e: IOException) {
            addLog("خطأ في إعادة تحميل الملف بعد AI: ${e.message}")
        }
    }

    private fun openFileInEditor(file: File) {
        if (!file.isFile) return

        saveCurrentFile()

        var state = findTab(file.absolutePath)
        val kind = FileKind.detect(file.name)

        try {
            if (state == null) {
                state = EditorTabState(file.absolutePath)
                state.content = if (kind == FileKind.TEXT || kind == FileKind.UNKNOWN) {
                    readFileText(file)
                } else {
                    ""
                }
                state.loaded = true
                state.dirty = false
                openTabs.add(state)
            }

            activeTabIndex = openTabs.indexOf(state)
            currentOpenFile = file

            showFileViewer(file)

            if (kind == FileKind.TEXT || kind == FileKind.UNKNOWN) {
                codeEditorView?.let { editor ->
                    editor.setTextImmediate(state.content, file.absolutePath)
                    editor.restoreVerticalScrollPosition(state.scrollY)
                }
            }

            refreshExternalErrorMarkersForCurrentFile()
            tvCurrentFilePath?.text =
                "الملف: ${file.name} - ${getRelativePath(file)}"
            updateFavoriteButton()
            renderTabStrip()
            saveEditorProjectState()
        } catch (e: IOException) {
            addLog("خطأ في فتح الملف: ${e.message}")
        }
    }

    @Throws(IOException::class)
    private fun readFileText(file: File): String {
        val builder = StringBuilder()
        BufferedReader(FileReader(file)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                builder.append(line).append("\n")
            }
        }
        if (builder.isNotEmpty()) builder.setLength(builder.length - 1)
        return builder.toString()
    }

    private fun saveCurrentFile() {
        val openFile = currentOpenFile ?: return
        if (!openFile.exists()) return
        val editor = codeEditorView ?: return
        if (activeViewerKind != FileKind.TEXT &&
            activeViewerKind != FileKind.UNKNOWN &&
            activeViewerKind != null
        ) return

        editor.saveTo(openFile) { success ->
            if (success) {
                val state = findTab(openFile.absolutePath)
                state?.dirty = false
                renderTabStrip()
            }
        }
    }

    private fun getRelativePath(file: File): String {
        val projDir = currentProjectDir ?: return file.name
        val projectPath = projDir.absolutePath
        val filePath = file.absolutePath
        return if (filePath.startsWith(projectPath)) {
            filePath.substring(projectPath.length + 1)
        } else {
            filePath
        }
    }

    private fun editorProjectKey(): String {
        val projDir = currentProjectDir ?: return "none"
        return Integer.toHexString(projDir.absolutePath.hashCode())
    }

    private fun findTab(absolutePath: String?): EditorTabState? {
        if (absolutePath == null) return null
        return openTabs.firstOrNull { it.absolutePath == absolutePath }
    }

    private fun updateActiveTabFromEditor() {
        if (activeTabIndex !in openTabs.indices) return
        val editor = codeEditorView ?: return
        if (activeViewerKind != FileKind.TEXT &&
            activeViewerKind != FileKind.UNKNOWN &&
            activeViewerKind != null
        ) return

        val state = openTabs[activeTabIndex]
        val text = editor.getText()
        if (text != state.content) state.dirty = true
        state.content = text
        state.selectionStart = 0
        state.selectionEnd = 0
        state.scrollY = editor.verticalScrollPosition
        state.loaded = true
    }

    private fun saveEditorProjectState() {
        val projDir = currentProjectDir ?: return

        val paths = openTabs.map { getRelativePath(File(it.absolutePath)) }
        editorStateStore.setRecentTabs(editorProjectKey(), paths)
        editorStateStore.setFavorites(editorProjectKey(), favoritePaths)

        val activePath = currentOpenFile?.let { getRelativePath(it) } ?: ""
        editorStateStore.setActiveTab(editorProjectKey(), activePath)
    }

    private fun switchToTab(index: Int) {
        if (index !in openTabs.indices) return
        if (activeTabIndex == index) return

        updateActiveTabFromEditor()
        activeTabIndex = index

        val state = openTabs[index]
        val file = File(state.absolutePath)
        currentOpenFile = file

        val kind = FileKind.detect(file.name)
        showFileViewer(file)

        if (kind == FileKind.TEXT || kind == FileKind.UNKNOWN) {
            codeEditorView?.let { editor ->
                editor.setTextImmediate(state.content, state.absolutePath)
                editor.restoreVerticalScrollPosition(state.scrollY)
            }
        }

        refreshExternalErrorMarkersForCurrentFile()
        tvCurrentFilePath?.text =
            "الملف: ${file.name} - ${getRelativePath(file)}"
        updateFavoriteButton()
        renderTabStrip()
        saveEditorProjectState()
    }

    private fun closeTab(index: Int) {
        if (index !in openTabs.indices) return

        val wasActive = (index == activeTabIndex)
        if (wasActive) updateActiveTabFromEditor()

        openTabs.removeAt(index)

        if (openTabs.isEmpty()) {
            activeTabIndex = -1
            currentOpenFile = null
            releaseCurrentViewer()
            viewerContainer?.removeAllViews()
            codeEditorView?.clear()
            tvCurrentFilePath?.text = "لا يوجد ملف مفتوح"
        } else {
            if (index < activeTabIndex) activeTabIndex--
            if (wasActive || activeTabIndex !in openTabs.indices) {
                activeTabIndex = minOf(index, openTabs.size - 1)
                val state = openTabs[activeTabIndex]
                val file = File(state.absolutePath)
                currentOpenFile = file

                val kind = FileKind.detect(file.name)
                showFileViewer(file)

                if (kind == FileKind.TEXT || kind == FileKind.UNKNOWN) {
                    codeEditorView?.let { editor ->
                        editor.setTextImmediate(state.content, state.absolutePath)
                        editor.restoreVerticalScrollPosition(state.scrollY)
                    }
                }

                tvCurrentFilePath?.text =
                    "الملف: ${file.name} - ${getRelativePath(file)}"
            }
        }

        refreshExternalErrorMarkersForCurrentFile()
        renderTabStrip()
        saveEditorProjectState()
    }

    private fun saveAllOpenTabs() {
        updateActiveTabFromEditor()

        val activeFile = currentOpenFile
        if (activeFile != null) {
            codeEditorView?.saveTo(activeFile) { }
        }

        for (state in openTabs) {
            if (!state.loaded) continue
            val file = File(state.absolutePath)
            if (!FileKind.isTextLike(file.name)) continue

            if (file.absolutePath == activeFile?.absolutePath) continue

            try {
                FileWriter(file).use { writer ->
                    writer.write(state.content)
                    writer.flush()
                    state.dirty = false
                }
            } catch (_: IOException) {
                addLog("تعذر حفظ التبويب: ${file.name}")
            }
        }
        saveEditorProjectState()
        renderTabStrip()
    }

    private fun saveAllOpenTabsSilently() {
        val projDir = currentProjectDir ?: return
        if (openTabs.isEmpty()) return

        updateActiveTabFromEditor()

        val paths = mutableListOf<String>()
        val contents = mutableListOf<String>()

        for (state in openTabs) {
            if (!state.loaded) continue
            val file = File(state.absolutePath)
            if (!FileKind.isTextLike(file.name)) continue
            paths.add(state.absolutePath)
            contents.add(state.content)
        }

        if (paths.isEmpty()) return

        for (state in openTabs) state.dirty = false
        saveEditorProjectState()

        Thread({
            for (i in paths.indices) {
                try {
                    FileWriter(paths[i]).use { writer ->
                        writer.write(contents[i])
                        writer.flush()
                    }
                } catch (_: IOException) {
                }
            }
        }, "auto-save").start()
    }

    private fun closeAllTabs() {
        if (openTabs.isEmpty()) {
            Toast.makeText(this, "لا توجد تبويبات مفتوحة", Toast.LENGTH_SHORT).show()
            return
        }

        updateActiveTabFromEditor()
        val hasDirtyTabs = openTabs.any { it.dirty }

        if (!hasDirtyTabs) {
            clearAllTabsAfterConfirmation()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("تبويبات غير محفوظة")
            .setMessage("توجد ملفات معدلة. هل تريد حفظها قبل إغلاق جميع التبويبات؟")
            .setPositiveButton("حفظ وإغلاق") { _, _ ->
                saveAllOpenTabs()
                clearAllTabsAfterConfirmation()
            }
            .setNeutralButton("إغلاق دون حفظ") { _, _ ->
                clearAllTabsAfterConfirmation()
            }
            .setNegativeButton("إلغاء", null)
            .create()
            .also { dialog ->
                dialog.show()
                styleDialog(dialog)
            }
    }

    private fun clearAllTabsAfterConfirmation() {
        openTabs.clear()
        activeTabIndex = -1
        currentOpenFile = null
        releaseCurrentViewer()
        viewerContainer?.removeAllViews()
        codeEditorView?.clear()
        tvCurrentFilePath?.text = "لا يوجد ملف مفتوح"
        refreshExternalErrorMarkersForCurrentFile()
        renderTabStrip()
        saveEditorProjectState()
        Toast.makeText(this, "تم إغلاق جميع التبويبات", Toast.LENGTH_SHORT).show()
    }

    private fun showFileOptionsDialog() {
        val file = selectedFileForDialog ?: return
        val options = arrayOf(
            "ملف جديد", "مجلد جديد", "استيراد ملف/مجلد", "إعادة تسمية", "حذف"
        )

        AlertDialog.Builder(this)
            .setTitle("خيارات: ${file.name}")
            .setAdapter(
                UiStyle.dialogListAdapter(this, options)
            ) { _, which ->
                when (which) {
                    0 -> showCreateFileDialog()
                    1 -> showCreateFolderDialog()
                    2 -> openFileImport()
                    3 -> showRenameDialog()
                    4 -> deleteSelectedFile()
                }
            }
            .create()
            .also { dialog ->
                dialog.show()
                styleDialog(dialog)
            }
    }

    private fun showCreateFileDialog() {
        val input = EditText(this).apply {
            hint = "اسم الملف (مثلاً: home.dart)"
            setHintTextColor(COLOR_TEXT_PRIMARY)
            setTextColor(COLOR_TEXT_PRIMARY)
            background = UiStyle.rounded(
                this@MainActivity, COLOR_SURFACE, 12, COLOR_BORDER, 1
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        AlertDialog.Builder(this)
            .setTitle("ملف جديد")
            .setView(input)
            .setPositiveButton("إنشاء") { _, _ ->
                val fileName = input.text.toString().trim()
                if (fileName.isEmpty()) {
                    Toast.makeText(
                        this,
                        "الرجاء إدخال اسم الملف",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val selected = selectedFileForDialog ?: return@setPositiveButton
                val targetDir = if (selected.isDirectory) {
                    selected
                } else {
                    selected.parentFile
                } ?: return@setPositiveButton

                val newFile = File(targetDir, fileName)
                try {
                    if (newFile.createNewFile()) {
                        refreshFileTree()
                        openFileInEditor(newFile)
                        addLog("تم إنشاء ملف: $fileName")
                    } else {
                        Toast.makeText(
                            this,
                            "الملف موجود بالفعل",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: IOException) {
                    addLog("خطأ: ${e.message}")
                }
            }
            .setNegativeButton("إلغاء", null)
            .create()
            .also { dialog ->
                dialog.show()
                styleDialog(dialog)
            }
    }

    private fun showCreateFolderDialog() {
        val input = EditText(this).apply {
            hint = "اسم المجلد"
            setHintTextColor(COLOR_TEXT_PRIMARY)
            setTextColor(COLOR_TEXT_PRIMARY)
            background = UiStyle.rounded(
                this@MainActivity, COLOR_SURFACE, 12, COLOR_BORDER, 1
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        AlertDialog.Builder(this)
            .setTitle("مجلد جديد")
            .setView(input)
            .setPositiveButton("إنشاء") { _, _ ->
                val folderName = input.text.toString().trim()
                if (folderName.isEmpty()) {
                    Toast.makeText(
                        this,
                        "الرجاء إدخال اسم المجلد",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val selected = selectedFileForDialog ?: return@setPositiveButton
                val targetDir = if (selected.isDirectory) {
                    selected
                } else {
                    selected.parentFile
                } ?: return@setPositiveButton

                val newFolder = File(targetDir, folderName)
                if (newFolder.mkdirs()) {
                    refreshFileTree()
                    addLog("تم إنشاء مجلد: $folderName")
                } else {
                    Toast.makeText(
                        this,
                        "المجلد موجود بالفعل",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .create()
            .also { dialog ->
                dialog.show()
                styleDialog(dialog)
            }
    }

    private fun showRenameDialog() {
        val selected = selectedFileForDialog ?: return
        val parent = selected.parentFile ?: return

        if (currentProjectDir == selected) {
            Toast.makeText(
                this,
                "لا يمكن إعادة تسمية مجلد المشروع من هنا",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val input = EditText(this).apply {
            setText(selected.name)
            setSelectAllOnFocus(true)
            isSingleLine = true
            setTextColor(COLOR_TEXT_PRIMARY)
            setHintTextColor(COLOR_TEXT_SECONDARY)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = UiStyle.rounded(
                this@MainActivity, COLOR_SURFACE, 12, COLOR_BORDER, 1
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("إعادة تسمية")
            .setView(input)
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = input.text.toString().trim()
                if (newName.isEmpty() || newName.contains("/") || newName.contains("\\")) {
                    input.error = "أدخل اسمًا صالحًا"
                    return@setOnClickListener
                }

                val target = File(parent, newName)
                if (target.exists()) {
                    input.error = "يوجد ملف أو مجلد بهذا الاسم"
                    return@setOnClickListener
                }

                if (!selected.renameTo(target)) {
                    Toast.makeText(
                        this,
                        "تعذرت إعادة التسمية",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                updateOpenPathsAfterRename(selected, target)
                selectedFileForDialog = target
                refreshFileTree()
                renderTabStrip()
                saveEditorProjectState()
                addLog("تمت إعادة التسمية إلى: $newName")
                dialog.dismiss()
            }
        }

        dialog.show()
        styleDialog(dialog)
        input.requestFocus()
    }

    private fun updateOpenPathsAfterRename(oldFile: File, newFile: File) {
        val oldPath = oldFile.absolutePath
        val newPath = newFile.absolutePath

        for (i in openTabs.indices) {
            val state = openTabs[i]
            if (state.absolutePath == oldPath ||
                state.absolutePath.startsWith("$oldPath${File.separator}")
            ) {
                val suffix = state.absolutePath.substring(oldPath.length)
                val replacement = EditorTabState("$newPath$suffix").apply {
                    content = state.content
                    selectionStart = state.selectionStart
                    selectionEnd = state.selectionEnd
                    scrollY = state.scrollY
                    loaded = state.loaded
                    dirty = state.dirty
                }
                openTabs[i] = replacement
            }
        }

        currentOpenFile?.let { openFile ->
            val currentPath = openFile.absolutePath
            if (currentPath == oldPath ||
                currentPath.startsWith("$oldPath${File.separator}")
            ) {
                currentOpenFile = File(
                    "$newPath${currentPath.substring(oldPath.length)}"
                )
            }
        }

        val projDir = currentProjectDir
        if (projDir != null) {
            val oldRelative = getRelativePath(oldFile).replace(File.separatorChar, '/')
            val newRelative = getRelativePath(newFile).replace(File.separatorChar, '/')

            val updatedFavorites = HashSet<String>()
            for (favorite in favoritePaths) {
                if (favorite == oldRelative ||
                    favorite.startsWith("$oldRelative/")
                ) {
                    updatedFavorites.add(
                        newRelative + favorite.substring(oldRelative.length)
                    )
                } else {
                    updatedFavorites.add(favorite)
                }
            }
            favoritePaths = updatedFavorites
        }
    }

    private fun openProjectZipImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/zip", "application/x-zip-compressed")
            )
        }
        startActivityForResult(intent, REQUEST_IMPORT_PROJECT_ZIP)
    }

    private fun openFileImport() {
        val selected = selectedFileForDialog ?: return
        val targetDir = if (selected.isDirectory) {
            selected
        } else {
            selected.parentFile
        }
        if (targetDir == null) {
            Toast.makeText(this, "لم يتم تحديد مجلد الوجهة", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, FileManagerActivity::class.java)
        intent.putExtra(FileManagerActivity.EXTRA_TARGET_DIR, targetDir.absolutePath)
        startActivity(intent)
    }

    private fun deleteSelectedFile() {
        val selected = selectedFileForDialog ?: return

        AlertDialog.Builder(this)
            .setTitle("حذف")
            .setMessage("هل أنت متأكد من حذف \"${selected.name}\"؟")
            .setPositiveButton("نعم") { _, _ ->
                val name = selected.name

                if (currentOpenFile == selected) {
                    currentOpenFile = null
                    releaseCurrentViewer()
                    viewerContainer?.removeAllViews()
                    tvCurrentFilePath?.text = ""
                    updateSaveButtonVisibility()
                }

                deleteRecursive(selected)
                refreshFileTree()
                addLog("تم حذف: $name")
            }
            .setNegativeButton("إلغاء", null)
            .create()
            .also { dialog ->
                dialog.show()
                styleDialog(dialog)
            }
    }

    private fun importProjectFromZip(uri: Uri) {
        showRepoTypeDialogForImport(uri)
    }

    private fun showRepoTypeDialogForImport(uri: Uri) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val warningBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = UiStyle.rounded(
                this@MainActivity, 0xFF2A2110.toInt(), 10, 0xFF6B5318.toInt(), 1
            )
        }

        warningBox.addView(TextView(this).apply {
            text = "تنبيه مهم"
            setTextSize(13f)
            setTextColor(0xFFD29922.toInt())
            typeface = Typeface.DEFAULT_BOLD
        })

        warningBox.addView(TextView(this).apply {
            text = "اختر النوع الصحيح للمشروع.\n" +
                    "إذا كان النوع المختار مختلفاً عن النوع الحقيقي، " +
                    "سيتم رفع المشروع لكن البناء على GitHub سيفشل."
            setTextSize(11f)
            setTextColor(COLOR_TEXT_PRIMARY)
            setLineSpacing(0f, 1.3f)
            setPadding(0, dp(6), 0, 0)
        })

        root.addView(
            warningBox,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(16)
            }
        )

        root.addView(TextView(this).apply {
            text = "نوع المشروع:"
            setTextSize(14f)
            setTextColor(COLOR_TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        })

        val projectTypeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }

        val types = listOf(
            ProjectType.FLUTTER,
            ProjectType.JAVA,
            ProjectType.KOTLIN,
            ProjectType.REACT_NATIVE
        )

        for ((index, type) in types.withIndex()) {
            val rb = RadioButton(this).apply {
                text = type.displayName
                setTextColor(COLOR_TEXT_PRIMARY)
                setTextSize(13f)
                id = IMPORT_TYPE_ID_BASE + index
            }
            projectTypeGroup.addView(rb)
        }

        projectTypeGroup.check(IMPORT_TYPE_ID_BASE)

        root.addView(projectTypeGroup, LinearLayout.LayoutParams(-1, -2))

        root.addView(TextView(this).apply {
            text = "نوع المستودع على GitHub:"
            setTextSize(14f)
            setTextColor(COLOR_TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(18), 0, dp(8))
        })

        val repoTypeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }

        val rbPublic = RadioButton(this).apply {
            text = "عام (Public) - موصى به"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            id = android.R.id.button1
        }
        repoTypeGroup.addView(rbPublic)

        val rbPrivate = RadioButton(this).apply {
            text = "خاص (Private)"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            id = android.R.id.button2
        }
        repoTypeGroup.addView(rbPrivate)

        repoTypeGroup.check(android.R.id.button1)
        root.addView(repoTypeGroup)

        val dialog = AlertDialog.Builder(this)
            .setTitle("استيراد مشروع من ZIP")
            .setView(root)
            .setPositiveButton("استيراد", null)
            .setNegativeButton("إلغاء", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val isPublicRepo = repoTypeGroup.checkedRadioButtonId ==
                        android.R.id.button1

                val selectedId = projectTypeGroup.checkedRadioButtonId
                val selectedIndex = selectedId - IMPORT_TYPE_ID_BASE

                if (selectedIndex < 0 || selectedIndex >= types.size) {
                    Toast.makeText(
                        this,
                        "اختر نوع المشروع",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val selectedType = types[selectedIndex]

                dialog.dismiss()
                performImportProjectFromZip(uri, isPublicRepo, selectedType)
            }
        }

        dialog.show()
        styleDialog(dialog)
    }

    private fun performImportProjectFromZip(
        uri: Uri,
        isPublicRepo: Boolean,
        projectType: ProjectType
    ) {
        val tempDir = File(projectsBaseDir, ".zip_import_${System.currentTimeMillis()}")
        var targetDir: File? = null

        try {
            if (!tempDir.mkdirs()) {
                throw IOException("تعذر إنشاء مجلد الاستيراد المؤقت")
            }

            val input = contentResolver.openInputStream(uri)
                ?: throw IOException("تعذر قراءة ملف ZIP")

            input.use { extractZipSafely(it, tempDir) }

            val sourceRoot = findImportedProjectRoot(tempDir)
                ?: throw IOException("الأرشيف فارغ")

            val zipName = getFileNameFromUri(uri) ?: "imported"
            val projectName = sanitizeImportedProjectName(zipName)
            targetDir = nextAvailableProjectDir(projectName)

            copyDirectory(sourceRoot, targetDir)
            deleteRecursive(tempDir)

            ensureWorkflowExists(targetDir, projectType)

            try {
                GradleWrapperWriter.ensureGradleWrapper(this, targetDir)
                addLog("تم التحقق من Gradle Wrapper")
            } catch (e: Exception) {
                addLog("تعذر إنشاء Gradle Wrapper: ${e.message}")
            }

            val folderName = targetDir.name
            prefs.edit()
                .putString("name_$folderName", projectName)
                .putBoolean("repo_public_$folderName", isPublicRepo)
                .putString("type_$folderName", projectType.id)
                .apply()

            if (projectType.isFlutter) {
                prefs.edit()
                    .putString("flutter_version_$folderName", "3.47.2")
                    .apply()
            }

            shouldReloadProjectsList = true
            loadProjectsList()
            addLog(
                "تم استيراد المشروع: ${targetDir.name} " +
                        "(${projectType.displayName}، ${if (isPublicRepo) "عام" else "خاص"})"
            )
            Toast.makeText(
                this,
                "تم استيراد المشروع بنجاح (${projectType.displayName})",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            targetDir?.takeIf { it.exists() }?.let { deleteRecursive(it) }
            if (tempDir.exists()) deleteRecursive(tempDir)
            addLog("خطأ في استيراد المشروع: ${e.message}")
            Toast.makeText(
                this,
                "تعذر استيراد المشروع: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Throws(IOException::class)
    private fun extractZipSafely(input: InputStream, destination: File) {
        ZipInputStream(input).use { zipInput ->
            val rootPath = destination.canonicalPath
            var totalBytes = 0L
            val buffer = ByteArray(8192)
            var entry: ZipEntry?

            while (zipInput.nextEntry.also { entry = it } != null) {
                var entryName = entry!!.name?.replace('\\', '/') ?: ""
                while (entryName.startsWith("./")) entryName = entryName.substring(2)

                if (entryName.isEmpty() ||
                    entryName.startsWith("/") ||
                    !isSafeZipPath(entryName)
                ) {
                    throw IOException("مسار غير آمن داخل الأرشيف")
                }

                if (entryName.startsWith("__MACOSX/") ||
                    entryName.endsWith("/.DS_Store") ||
                    entryName == ".DS_Store"
                ) {
                    zipInput.closeEntry()
                    continue
                }

                val output = File(destination, entryName)
                val outputPath = output.canonicalPath
                if (outputPath != rootPath &&
                    !outputPath.startsWith("$rootPath${File.separator}")
                ) {
                    throw IOException("مسار خارج مجلد المشروع")
                }

                if (entry!!.isDirectory) {
                    if (!output.exists() && !output.mkdirs()) {
                        throw IOException("تعذر إنشاء مجلد داخل الأرشيف")
                    }
                } else {
                    output.parentFile?.let { parent ->
                        if (!parent.exists() && !parent.mkdirs()) {
                            throw IOException("تعذر إنشاء مجلد الملف")
                        }
                    }
                    FileOutputStream(output).use { fileOutput ->
                        var count: Int
                        while (zipInput.read(buffer).also { count = it } != -1) {
                            totalBytes += count
                            if (totalBytes > 300L * 1024L * 1024L) {
                                throw IOException("الأرشيف أكبر من الحد المسموح")
                            }
                            fileOutput.write(buffer, 0, count)
                        }
                    }
                }
                zipInput.closeEntry()
            }
        }
    }

    private fun isSafeZipPath(path: String): Boolean {
        return path.split("/").none { it == ".." }
    }

    private fun findImportedProjectRoot(extractionDir: File): File? {
        val children = extractionDir.listFiles() ?: return null
        if (children.isEmpty()) return null

        val meaningful = children.filter {
            it.name != "__MACOSX" && it.name != ".DS_Store"
        }

        return if (meaningful.size == 1 && meaningful[0].isDirectory) {
            meaningful[0]
        } else {
            extractionDir
        }
    }

    private fun sanitizeImportedProjectName(zipName: String?): String {
        var name = zipName ?: "ImportedProject"
        if (name.lowercase(Locale.US).endsWith(".zip")) {
            name = name.substring(0, name.length - 4)
        }
        name = name.trim().replace(Regex("[^\\p{L}\\p{N}_-]"), "_")
        while (name.startsWith(".")) name = name.substring(1)
        if (name.isEmpty()) name = "ImportedProject"
        return name
    }

    private fun nextAvailableProjectDir(projectName: String): File {
        var candidate = File(projectsBaseDir, projectName)
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(projectsBaseDir, "${projectName}_$suffix")
            suffix++
        }
        return candidate
    }

    @Throws(IOException::class)
    private fun copyDirectory(source: File, destination: File) {
        if (source.isDirectory) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw IOException("تعذر إنشاء مجلد المشروع")
            }
            source.listFiles()?.forEach { child ->
                copyDirectory(child, File(destination, child.name))
            }
            return
        }

        destination.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("تعذر إنشاء مجلد الملف")
            }
        }

        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } != -1) {
                    output.write(buffer, 0, count)
                }
            }
        }
    }

    private fun ensureWorkflowExists(
        projectDir: File,
        projectType: ProjectType
    ) {
        if (!projectDir.isDirectory) return

        try {
            val workflowFileName = projectType.workflowFile

            val githubDir = File(projectDir, ".github")
            val workflowsDir = File(githubDir, "workflows")
            val workflowFile = File(workflowsDir, workflowFileName)

            if (!workflowFile.exists()) {
                if (!workflowsDir.exists() && !workflowsDir.mkdirs()) {
                    addLog("تعذر إنشاء مجلد .github/workflows")
                    return
                }
                writeDefaultWorkflow(workflowFile, projectType)
                addLog("تم إنشاء ملف البناء $workflowFileName تلقائياً")
                return
            }

            val content = readFileText(workflowFile)
            if (hasWorkflowDispatch(content)) {
                addLog("المشروع يحتوي على $workflowFileName جاهز، لن نعدّله")
                return
            }

            val updated = injectWorkflowDispatch(content)
            if (updated != null && updated != content) {
                writeTextFile(workflowFile, updated)
                addLog("تم تحديث $workflowFileName بإضافة workflow_dispatch")
            } else {
                writeDefaultWorkflow(workflowFile, projectType)
                addLog("لم نتمكن من تحليل $workflowFileName، تم استبداله بالنسخة الافتراضية")
            }
        } catch (e: Exception) {
            addLog("تعذر التحقق من workflow: ${e.message}")
        }
    }

    private fun hasWorkflowDispatch(content: String?): Boolean {
        if (content.isNullOrEmpty()) return false
        val p = Pattern.compile("^\\s*workflow_dispatch\\s*:", Pattern.MULTILINE)
        return p.matcher(content).find()
    }

    private fun injectWorkflowDispatch(content: String?): String? {
        if (content.isNullOrEmpty()) return null
        return try {
            val lines = content.split("\n")
            val result = StringBuilder()
            var onFound = false
            var dispatchInjected = false

            for (i in lines.indices) {
                val line = lines[i]
                result.append(line)

                if (!onFound && line.matches(Regex("^on\\s*:\\s*$"))) {
                    onFound = true
                    result.append("\n  workflow_dispatch:")
                    dispatchInjected = true
                } else if (!onFound &&
                    line.matches(Regex("^on\\s*:\\s*\\[.*\\]\\s*$"))
                ) {
                    val inner = line
                        .replace(Regex("^on\\s*:\\s*\\[|\\]\\s*$"), "")
                        .trim()
                    result.setLength(result.length - line.length)
                    result.append("on:")
                    if (inner.isNotEmpty()) {
                        for (item in inner.split(",")) {
                            result.append("\n  ").append(item.trim()).append(":")
                        }
                    }
                    result.append("\n  workflow_dispatch:")
                    onFound = true
                    dispatchInjected = true
                } else if (!onFound && line.matches(Regex("^on\\s*:\\s*\\S+.*$"))) {
                    val value = line.replace(Regex("^on\\s*:\\s*"), "").trim()
                    result.setLength(result.length - line.length)
                    result.append("on:")
                    if (value.isNotEmpty()) {
                        result.append("\n  ").append(value).append(":")
                    }
                    result.append("\n  workflow_dispatch:")
                    onFound = true
                    dispatchInjected = true
                }

                if (i < lines.size - 1) result.append("\n")
            }

            if (dispatchInjected) result.toString() else null
        } catch (_: Exception) {
            null
        }
    }

    @Throws(IOException::class)
    private fun writeDefaultWorkflow(workflowFile: File, type: ProjectType) {
        val yml = when (type) {
            ProjectType.FLUTTER -> buildFlutterWorkflowYml()
            ProjectType.JAVA -> JavaBuildWorkflow.build(34)
            ProjectType.KOTLIN -> KotlinBuildWorkflow.build(34)
            ProjectType.REACT_NATIVE -> ReactNativeBuildWorkflow.build(34)
        }
        writeTextFile(workflowFile, yml)
    }

    private fun buildFlutterWorkflowYml(): String {
        return """
            |name: Build Flutter APK
            |
            |on:
            |  workflow_dispatch:
            |    inputs:
            |      flutter_version:
            |        description: 'Flutter version'
            |        required: true
            |        default: '3.47.2'
            |
            |jobs:
            |  build:
            |    runs-on: ubuntu-24.04
            |    steps:
            |      - name: Checkout
            |        uses: actions/checkout@v5
            |
            |      - name: Setup Gradle Cache
            |        uses: actions/cache@v5
            |        with:
            |          path: |
            |            ~/.gradle/caches
            |            ~/.gradle/wrapper
            |          key: ${'$'}{{ runner.os }}-gradle-${'$'}{{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
            |          restore-keys: |
            |            ${'$'}{{ runner.os }}-gradle-
            |
            |      - name: Setup Java 17
            |        uses: actions/setup-java@v5
            |        with:
            |          distribution: temurin
            |          java-version: '17'
            |
            |      - name: Setup Flutter
            |        uses: subosito/flutter-action@v2
            |        with:
            |          flutter-version: ${'$'}{{ github.event.inputs.flutter_version }}
            |          cache: true
            |          channel: stable
            |
            |      - name: Get dependencies
            |        run: flutter pub get
            |
            |      - name: Build APK
            |        run: flutter build apk --release
            |
            |      - name: Upload APK
            |        uses: actions/upload-artifact@v4
            |        with:
            |          name: app-release
            |          path: build/app/outputs/flutter-apk/app-release.apk
            |          if-no-files-found: error
            |          retention-days: 7
        """.trimMargin() + "\n"
    }

    @Throws(IOException::class)
    private fun writeTextFile(file: File, content: String) {
        file.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }
        FileWriter(file).use { writer ->
            writer.write(content)
            writer.flush()
        }
    }

    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { deleteRecursive(it) }
        }
        fileOrDirectory.delete()
    }

    // ============================================================
    // Companion
    // ============================================================
    companion object {
        const val KEY_PROJECTS_DIRTY = "projects_list_dirty"

        // مفاتيح SharedPreferences لطريقة الرفع
        const val KEY_UPLOAD_METHOD = "upload_method"
        const val KEY_UPLOAD_REMEMBER = "upload_method_remember"
        const val UPLOAD_METHOD_GRAPHQL = "graphql"
        const val UPLOAD_METHOD_BATCHES = "batches"

        private val BUILD_LOCATION_PATTERN = Pattern.compile(
            "(?m)([^\\r\\n]*?\\.(?:dart|java|kt|kts|py|js|ts|tsx|jsx|xml|yaml|yml|json)):(\\d+)(?::(\\d+))?(?=\\D|\$)"
        )
    }

    private fun dp(value: Int): Int {
        return Math.round(value * resources.displayMetrics.density)
    }
}