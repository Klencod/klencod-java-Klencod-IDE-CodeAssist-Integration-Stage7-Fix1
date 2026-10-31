package com.flutteride.codweva

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * شاشة تسجيل الدخول إلى GitHub عبر Device Flow.
 *
 * v2.4 — إصلاح Edge-to-Edge على Android 15/16:
 *   - WindowCompat.setDecorFitsSystemWindows(window, false)
 *   - layoutInDisplayCutoutMode = SHORT_EDGES (دعم النوتش)
 *   - تطبيق WindowInsets على الـ root ScrollView
 *   - دعم displayCutout في الحسابات
 *   - أيقونات شريط الحالة بيضاء على الخلفية الداكنة
 *
 * v2.3 — تكامل مع إعدادات المستخدم:
 *   - يقرأ الإعداد المحفوظ من SettingsActivity (طريقة الفتح)
 *   - إذا كان "ASK" أو لم يُحفظ شيء → يعرض Dialog الاختيار
 *   - إذا كان "INTERNAL" → يفتح المتصفح الداخلي مباشرة
 *   - إذا كان "EXTERNAL" → يفتح المتصفح الخارجي مباشرة
 */
class LoginActivity : Activity() {

    private val colorBg = Color.rgb(10, 14, 20)
    private val colorSurface = Color.rgb(20, 27, 36)
    private val colorAlt = Color.rgb(27, 36, 47)
    private val colorBorder = Color.rgb(48, 62, 77)
    private val colorText = Color.rgb(245, 247, 250)
    private val colorMuted = Color.rgb(157, 171, 187)
    private val colorBlue = Color.rgb(55, 145, 255)
    private val colorSuccess = Color.rgb(46, 160, 67)
    private val colorDanger = Color.rgb(207, 34, 46)

    private lateinit var root: LinearLayout
    private lateinit var rootScrollView: ScrollView
    private lateinit var status: TextView
    private lateinit var codeView: TextView
    private lateinit var connect: Button
    private lateinit var secureStore: SecureStore

    private var verificationUri: String = GitHubConfig.VERIFICATION_URL
    private var pendingDeviceCode: GitHubApi.DeviceCode? = null

    /** ⭐ v2.0: عند إضافة حساب جديد، هل هذا الحساب سيصبح النشط؟ */
    private var shouldActivateAfterLogin: Boolean = true

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    // ============================================================
    // onCreate
    // ============================================================
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        // ============================================================
        // ⭐ v2.4: إعداد النافذة لـ Edge-to-Edge (قبل أي شيء)
        // ============================================================
        setupWindowEdgeToEdge()

        // ⭐ أخفِ المحتوى حتى نتحقق من الجلسة (لمنع الوميض)
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(colorBg)
        )

        // ⭐ قراءة العلامة أولاً
        val activateAfterLogin = intent.getBooleanExtra(
            EXTRA_ACTIVATE_AFTER_LOGIN, true
        )

        // ⭐ v2.1: تخطّي شاشة الدخول إذا كان هناك حساب نشط صالح
        if (activateAfterLogin && hasValidSession()) {
            goToMainActivity()
            return
        }

        // ---- لا توجد جلسة صالحة → اعرض الشاشة ----
        secureStore = SecureStore(this)
        shouldActivateAfterLogin = activateAfterLogin

        buildUi()
        refreshConnection()
    }

    /**
     * ⭐ v2.4 — إعداد النافذة لـ Edge-to-Edge بشكل كامل.
     *
     * نفس المنطق المطبق في SplashActivity و MainActivity:
     *   1. WindowCompat.setDecorFitsSystemWindows(window, false)
     *   2. layoutInDisplayCutoutMode = SHORT_EDGES (دعم النوتش)
     *   3. statusBarColor + navigationBarColor
     *   4. أيقونات شريط الحالة بيضاء (خلفية داكنة)
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
            window.statusBarColor = colorBg
            window.navigationBarColor = colorBg
        }

        // 4) أيقونات شريط الحالة بيضاء
        try {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        } catch (_: Exception) {
        }
    }

    /**
     * ⭐ v2.4: هل يوجد جلسة صالحة؟
     */
    private fun hasValidSession(): Boolean {
        return try {
            val accounts = GitHubAccountManager.listAccounts(this)
            if (accounts.isEmpty()) return false

            val active = GitHubAccountManager.getActive(this) ?: return false

            val token = SecureStore(this).readGitHubToken(active.id)
            token.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * ⭐ v2.1: انتقال مباشر إلى MainActivity بدون شاشة دخول.
     */
    private fun goToMainActivity() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            startActivity(intent)
            overridePendingTransition(0, 0)
        } catch (_: Exception) {
        }
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val data = intent?.data
        if (data != null && data.scheme == "flutteride") {
            val codeValue = data.getQueryParameter("code")
            if (!codeValue.isNullOrEmpty()) {
                Toast.makeText(
                    this,
                    "تم استلام الرمز، جارٍ التحقق...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ============================================================
    // ⭐ v2.3: قراءة إعداد طريقة الفتح من SettingsActivity
    // ============================================================
    /**
     * يقرأ الإعداد المحفوظ.
     *    MODE_ASK      → اسأل في كل مرة (افتراضي)
     *    MODE_INTERNAL → داخلي دائماً
     *    MODE_EXTERNAL → خارجي دائماً
     */
    private fun getSavedBrowserMode(): String {
        return try {
            val prefs = getSharedPreferences(
                "flutter_ide_prefs",
                Context.MODE_PRIVATE
            )
            prefs.getString(
                SettingsActivity.KEY_LOGIN_BROWSER_MODE,
                SettingsActivity.MODE_ASK
            ) ?: SettingsActivity.MODE_ASK
        } catch (_: Exception) {
            SettingsActivity.MODE_ASK
        }
    }

    // ============================================================
    // بناء الواجهة
    // ============================================================
    private fun buildUi() {
        rootScrollView = ScrollView(this).apply {
            setBackgroundColor(colorBg)
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            setBackgroundColor(colorBg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val eyebrow = text("KLENCOD  /  CONNECT", 11f, colorBlue, true).apply {
            letterSpacing = 0.08f
        }
        root.addView(eyebrow)

        val title = text("اربط مساحة العمل بـ GitHub", 27f, colorText, true)
        root.addView(title, params(0, 6, 0, 0))

        root.addView(
            text("تفويض رسمي من دون خادم وسيط", 13f, colorMuted, false),
            params(0, 3, 0, 20)
        )

        // ============================================================
        // الحسابات المحفوظة (إن وُجدت)
        // ============================================================
        val savedAccounts = GitHubAccountManager.listAccounts(this)
        if (savedAccounts.isNotEmpty()) {
            root.addView(buildSavedAccountsSection(savedAccounts))
        }

        // ============================================================
        // عنوان قسم
        // ============================================================
        val isAddingAccount = savedAccounts.isNotEmpty()
        val sectionTitle = if (isAddingAccount) {
            "إضافة حساب جديد"
        } else {
            "تسجيل الدخول"
        }

        val sectionLabel = text(sectionTitle, 16f, colorText, true).apply {
            setPadding(0, 12, 0, 8)
        }
        root.addView(sectionLabel)

        val explanation = card()
        explanation.addView(text("كيف يعمل الربط؟", 17f, colorText, true))
        val body = text(
            "يتم طلب رمز مؤقت من GitHub. سيُملأ الرمز تلقائياً في صفحة التحقق " +
                    "(إذا اخترت المتصفح الداخلي). أنت فقط تضغط Continue ثم توافق. " +
                    "لا يتم حفظ كلمة المرور.",
            14f, colorMuted, false
        ).apply {
            setLineSpacing(0f, 1.15f)
        }
        explanation.addView(body, params(0, 8, 0, 0))
        root.addView(explanation, params(0, 0, 0, 14))

        status = text("جارٍ التحقق من الحالة...", 14f, colorMuted, false)
        root.addView(status, params(2, 0, 0, 10))

        codeView = text("", 30f, colorText, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.14f
            background = round(colorAlt, 20, colorBorder)
        }
        root.addView(codeView, params(0, 0, 0, 10, 74))

        val copy = button("نسخ رمز التحقق", colorAlt, colorText)
        root.addView(copy, params(0, 0, 0, 8, 48))
        copy.setOnClickListener {
            val value = codeView.text.toString()
            if (value.trim().isEmpty()) {
                Toast.makeText(
                    this,
                    "اطلب الرمز أولاً",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            copyCodeToClipboard(value)
        }

        // ⭐ زر "فتح صفحة التحقق" — يعيد فتح Dialog الاختيار أو يستخدم الإعداد
        val open = button("فتح صفحة التحقق", colorAlt, colorText)
        root.addView(open, params(0, 0, 0, 8, 50))
        open.setOnClickListener {
            val device = pendingDeviceCode
            if (device == null) {
                Toast.makeText(
                    this,
                    "اضغط \"بدء تسجيل الدخول\" أولاً",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // ⭐ v2.3: اقرأ الإعداد المحفوظ
            val savedMode = getSavedBrowserMode()
            when (savedMode) {
                SettingsActivity.MODE_INTERNAL -> openInInternalBrowser(device)
                SettingsActivity.MODE_EXTERNAL -> openInExternalBrowser(device)
                else -> showBrowserChoiceDialog(device)
            }
        }

        connect = button(
            if (isAddingAccount) "بدء تسجيل الدخول للحساب الجديد" else "بدء تسجيل الدخول",
            colorBlue, Color.WHITE
        )
        root.addView(connect, params(0, 0, 0, 8, 50))
        connect.setOnClickListener { beginLogin() }

        val close = button("إغلاق", Color.TRANSPARENT, colorMuted)
        root.addView(close, params(0, 4, 0, 0, 44))
        close.setOnClickListener { finish() }

        rootScrollView.addView(root)
        setContentView(rootScrollView)

        // ============================================================
        // ⭐ v2.4: تطبيق الـ insets على الـ root ScrollView
        // ============================================================
        applyEdgeToEdgeInsets()
    }

    /**
     * ⭐ v2.4 — تطبيق WindowInsets على الـ root ScrollView.
     *
     * نطبق الـ padding على الـ ScrollView نفسه (وليس على الـ LinearLayout
     * الداخلي) لأن الـ ScrollView هو الـ root view الذي يملأ الشاشة.
     *
     * - systemBars: شريط الحالة (top) + شريط التنقل (bottom)
     * - displayCutout: النوتش (إن وُجد)
     */
    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootScrollView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // ⭐ دعم النوتش
            val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            } else {
                androidx.core.graphics.Insets.NONE
            }

            val left = maxOf(systemBars.left, cutout.left)
            val top = maxOf(systemBars.top, cutout.top)
            val right = maxOf(systemBars.right, cutout.right)
            val bottom = maxOf(systemBars.bottom, cutout.bottom)

            view.setPadding(left, top, right, bottom)
            insets
        }

        ViewCompat.requestApplyInsets(rootScrollView)
    }

    // ============================================================
    // قسم الحسابات المحفوظة
    // ============================================================
    private fun buildSavedAccountsSection(accounts: List<GitHubAccountManager.Account>): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        val header = text("الحسابات المحفوظة", 16f, colorText, true).apply {
            setPadding(0, 0, 0, dp(8))
        }
        container.addView(header)

        val active = GitHubAccountManager.getActive(this)

        for (account in accounts) {
            val isActive = (account.id == active?.id)
            val card = buildSavedAccountCard(account, isActive)
            container.addView(
                card,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                }
            )
        }

        return container
    }

    private fun buildSavedAccountCard(
        account: GitHubAccountManager.Account,
        isActive: Boolean
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = round(
                if (isActive) Color.rgb(31, 45, 63) else colorSurface,
                14,
                if (isActive) colorBlue else colorBorder
            )
            isClickable = true
            isFocusable = true
        }

        // أيقونة دائرية
        val iconView = TextView(this).apply {
            text = account.initial
            setTextColor(Color.WHITE)
            setTextSize(18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(account.color)
            }
        }
        card.addView(iconView, LinearLayout.LayoutParams(
            dp(44), dp(44)
        ))

        // النصوص
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        card.addView(
            textCol,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(12)
            }
        )

        textCol.addView(TextView(this).apply {
            text = account.safeDisplayName
            setTextColor(colorText)
            setTextSize(15f)
            typeface = Typeface.DEFAULT_BOLD
        })

        textCol.addView(TextView(this).apply {
            text = "@${account.username}"
            setTextColor(colorMuted)
            setTextSize(11f)
        })

        // شارة "نشط" أو زر "تبديل"
        if (isActive) {
            val badge = TextView(this).apply {
                text = "نشط"
                setTextColor(Color.WHITE)
                setTextSize(10f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = round(colorSuccess, 8, colorSuccess)
            }
            card.addView(badge)
        } else {
            card.setOnClickListener {
                val success = GitHubAccountManager.switchTo(this, account.id)
                if (success) {
                    Toast.makeText(
                        this,
                        "تم التبديل إلى ${account.username}",
                        Toast.LENGTH_SHORT
                    ).show()
                    enterApp()
                } else {
                    Toast.makeText(
                        this,
                        "تعذر التبديل — التوكن غير متوفر",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        return card
    }

    // ============================================================
    // تحديث الحالة
    // ============================================================
    private fun refreshConnection() {
        val active = GitHubAccountManager.getActive(this)

        if (active != null) {
            val token = secureStore.readGitHubToken(active.id)
            if (token.isNotEmpty()) {
                status.text = "الحساب النشط: ${active.username}"
                status.setTextColor(colorSuccess)
                connect.text = "إضافة حساب آخر"
                return
            }
        }

        // لا يوجد حساب نشط
        val accounts = GitHubAccountManager.listAccounts(this)
        if (accounts.isEmpty()) {
            status.text = "لا توجد حسابات محفوظة — ابدأ بتسجيل الدخول"
            status.setTextColor(colorMuted)
            connect.text = "بدء تسجيل الدخول"
        } else {
            status.text = "لا يوجد حساب نشط — اختر حساباً من القائمة أو أضف جديداً"
            status.setTextColor(colorMuted)
            connect.text = "إضافة حساب جديد"
        }
    }

    // ============================================================
    // Dialog اختيار المتصفح (يظهر فقط في وضع ASK)
    // ============================================================
    private fun showBrowserChoiceDialog(device: GitHubApi.DeviceCode) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
            setBackgroundColor(colorSurface)
        }

        // ============================================================
        // الخيار 1: المتصفح الداخلي
        // ============================================================
        val internalOption = buildOptionCard(
            title = "المتصفح الداخلي",
            subtitle = "موصى به — يملأ الرمز تلقائياً",
            features = listOf(
                "✓ ملء الرمز تلقائياً",
                "✓ لا يخرجك من التطبيق",
                "✓ تجربة أسرع وأسهل"
            ),
            isRecommended = true
        )
        content.addView(internalOption, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
        })

        // ============================================================
        // الخيار 2: المتصفح الخارجي
        // ============================================================
        val externalOption = buildOptionCard(
            title = "المتصفح الخارجي",
            subtitle = "Chrome / Samsung Internet",
            features = listOf(
                "✓ الرمز يُنسخ تلقائياً",
                "• الصقه يدوياً في GitHub",
                "• يعمل مع أي متصفح مثبّت"
            ),
            isRecommended = false
        )
        content.addView(externalOption, LinearLayout.LayoutParams(-1, -2))

        // ============================================================
        // عرض الرمز للتأكيد
        // ============================================================
        val codeBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = round(colorAlt, 12, colorBorder)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(16)
            }
        }

        codeBox.addView(TextView(this).apply {
            text = "رمزك الحالي"
            setTextColor(colorMuted)
            setTextSize(11f)
            gravity = Gravity.CENTER
        })

        codeBox.addView(TextView(this).apply {
            text = device.userCode
            setTextColor(colorText)
            setTextSize(22f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.16f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        })

        codeBox.addView(TextView(this).apply {
            text = "سيُنسخ هذا الرمز تلقائياً"
            setTextColor(colorMuted)
            setTextSize(10f)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        })

        content.addView(codeBox)

        // ============================================================
        // الحوار
        // ============================================================
        val dialog = AlertDialog.Builder(this)
            .setTitle("كيف تريد فتح صفحة GitHub؟")
            .setView(content)
            .setNegativeButton("إلغاء", null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(round(colorSurface, 18, colorBorder))
        }

        dialog.show()

        // ربط النقر
        internalOption.setOnClickListener {
            dialog.dismiss()
            openInInternalBrowser(device)
        }
        externalOption.setOnClickListener {
            dialog.dismiss()
            openInExternalBrowser(device)
        }
    }

    /**
     * يبني بطاقة خيار واحدة داخل Dialog.
     */
    private fun buildOptionCard(
        title: String,
        subtitle: String,
        features: List<String>,
        isRecommended: Boolean
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = round(
                if (isRecommended) Color.rgb(31, 45, 63) else colorSurface,
                14,
                if (isRecommended) colorBlue else colorBorder
            )
            isClickable = true
            isFocusable = true
        }

        // ---- صف العنوان ----
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = TextView(this).apply {
            text = if (isRecommended) "🌐" else "🔗"
            setTextSize(20f)
            setPadding(0, 0, dp(10), 0)
        }
        titleRow.addView(icon)

        val titleView = TextView(this).apply {
            text = title
            setTextColor(colorText)
            setTextSize(15f)
            typeface = Typeface.DEFAULT_BOLD
        }
        titleRow.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))

        if (isRecommended) {
            val badge = TextView(this).apply {
                text = "موصى به"
                setTextColor(Color.WHITE)
                setTextSize(9f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = round(colorBlue, 6, colorBlue)
            }
            titleRow.addView(badge)
        }

        card.addView(titleRow)

        // ---- الوصف ----
        val subtitleView = TextView(this).apply {
            text = subtitle
            setTextColor(colorMuted)
            setTextSize(11f)
            setPadding(0, dp(4), 0, dp(8))
        }
        card.addView(subtitleView)

        // ---- الميزات ----
        for (feature in features) {
            val featureView = TextView(this).apply {
                text = feature
                setTextColor(
                    if (feature.startsWith("✓")) colorSuccess else colorMuted
                )
                setTextSize(11f)
                setPadding(0, dp(2), 0, dp(2))
            }
            card.addView(featureView)
        }

        return card
    }

    // ============================================================
    // فتح صفحة التحقق — داخلياً أو خارجياً
    // ============================================================
    private fun openInInternalBrowser(device: GitHubApi.DeviceCode) {
        try {
            // انسخ الرمز احتياطياً
            copyCodeToClipboard(device.userCode)

            pendingDeviceCode = device
            verificationUri = if (device.verificationUri.isBlank()) {
                GitHubConfig.VERIFICATION_URL
            } else {
                device.verificationUri
            }

            val intent = Intent(this, WebViewAuthActivity::class.java)
            intent.putExtra(WebViewAuthActivity.EXTRA_URL, verificationUri)
            intent.putExtra(WebViewAuthActivity.EXTRA_USER_CODE, device.userCode)
            startActivity(intent)

            status.text = "تم فتح GitHub — اضغط Continue ثم Authorize"
            status.setTextColor(colorMuted)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "تعذر فتح المتصفح الداخلي: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openInExternalBrowser(device: GitHubApi.DeviceCode) {
        try {
            // 1. انسخ الرمز تلقائياً
            copyCodeToClipboard(device.userCode)

            // 2. احفظ الـ deviceCode
            pendingDeviceCode = device
            verificationUri = if (device.verificationUri.isBlank()) {
                GitHubConfig.VERIFICATION_URL
            } else {
                device.verificationUri
            }

            // 3. افتح المتصفح الخارجي
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(verificationUri)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)

            // 4. أظهر Dialog تعليمات
            showExternalBrowserInstructions(device.userCode)

            status.text = "تم فتح المتصفح — الصق الرمز في GitHub"
            status.setTextColor(colorMuted)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "تعذر فتح المتصفح الخارجي: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showExternalBrowserInstructions(userCode: String) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setBackgroundColor(colorSurface)
        }

        // رسالة النجاح
        val successRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = round(Color.rgb(23, 43, 28), 12, colorSuccess)
        }

        successRow.addView(TextView(this).apply {
            text = "✓"
            setTextColor(colorSuccess)
            setTextSize(20f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(10), 0)
        })

        successRow.addView(TextView(this).apply {
            text = "تم نسخ الرمز تلقائياً"
            setTextColor(colorSuccess)
            setTextSize(13f)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))

        content.addView(successRow, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(14)
        })

        // عرض الرمز
        val codeBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = round(colorAlt, 12, colorBorder)
        }

        codeBox.addView(TextView(this).apply {
            text = "الرمز"
            setTextColor(colorMuted)
            setTextSize(11f)
            gravity = Gravity.CENTER
        })

        codeBox.addView(TextView(this).apply {
            text = userCode
            setTextColor(colorText)
            setTextSize(24f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.16f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        })

        content.addView(codeBox, LinearLayout.LayoutParams(-1, -2))

        // التعليمات
        val instructions = TextView(this).apply {
            text = "الخطوات:\n" +
                    "1. في المتصفح المفتوح، اضغط Continue\n" +
                    "2. ألصق الرمز (اضغط مطوّلاً → لصق)\n" +
                    "3. اضغط Authorize\n" +
                    "4. ارجع إلى التطبيق — سيُكمل تلقائياً"
            setTextColor(colorText)
            setTextSize(12f)
            setLineSpacing(0f, 1.3f)
            setPadding(dp(8), dp(16), dp(8), dp(8))
        }
        content.addView(instructions)

        val dialog = AlertDialog.Builder(this)
            .setTitle("اتبع التعليمات")
            .setView(content)
            .setPositiveButton("حسناً، فهمت", null)
            .setNeutralButton("نسخ مرة أخرى") { _, _ ->
                copyCodeToClipboard(userCode)
            }
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(round(colorSurface, 18, colorBorder))
        }
        dialog.show()
    }

    private fun copyCodeToClipboard(code: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("GitHub code", code))
            Toast.makeText(
                this,
                "تم نسخ الرمز: $code",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "تعذر نسخ الرمز: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ============================================================
    // بدء تسجيل الدخول (Device Flow)
    // ============================================================
    private fun beginLogin() {
        shouldActivateAfterLogin = true

        connect.isEnabled = false
        connect.text = "جارٍ طلب رمز مؤقت..."

        GitHubApi.requestDeviceCode(object : GitHubApi.Callback<GitHubApi.DeviceCode> {
            override fun onSuccess(device: GitHubApi.DeviceCode) {
                pendingDeviceCode = device

                runOnUiThread {
                    connect.isEnabled = true
                    connect.text = "انتظر موافقة GitHub"
                    codeView.text = device.userCode
                    verificationUri = if (device.verificationUri.isBlank()) {
                        GitHubConfig.VERIFICATION_URL
                    } else {
                        device.verificationUri
                    }

                    status.text = "جارٍ تجهيز صفحة التحقق..."
                    status.setTextColor(colorMuted)

                    showDeviceCodeNotification(device)

                    // ⭐ v2.3: اقرأ الإعداد المحفوظ من SettingsActivity
                    val savedMode = getSavedBrowserMode()
                    when (savedMode) {
                        SettingsActivity.MODE_INTERNAL -> {
                            // داخلي دائماً
                            openInInternalBrowser(device)
                        }
                        SettingsActivity.MODE_EXTERNAL -> {
                            // خارجي دائماً
                            openInExternalBrowser(device)
                        }
                        else -> {
                            // ASK → اسأل في كل مرة
                            status.text = "اختر طريقة فتح صفحة GitHub"
                            showBrowserChoiceDialog(device)
                        }
                    }
                }

                GitHubApi.pollAccessToken(device, object : GitHubApi.Callback<String> {
                    override fun onSuccess(token: String) {
                        GitHubApi.getUser(token, object : GitHubApi.Callback<String> {
                            override fun onSuccess(username: String) {
                                handleLoginSuccess(token, username)
                            }

                            override fun onError(message: String) {
                                runOnUiThread {
                                    connect.isEnabled = true
                                    connect.text = "بدء تسجيل الدخول"
                                    showError("تم التفويض لكن تعذر قراءة الحساب:\n$message")
                                }
                            }
                        })
                    }

                    override fun onError(message: String) {
                        runOnUiThread {
                            connect.isEnabled = true
                            connect.text = "بدء تسجيل الدخول"
                            showError(message)
                        }
                    }
                })
            }

            override fun onError(message: String) {
                runOnUiThread {
                    connect.isEnabled = true
                    connect.text = "بدء تسجيل الدخول"
                    showError(message)
                }
            }
        })
    }

    // ============================================================
    // نجاح تسجيل الدخول
    // ============================================================
    private fun handleLoginSuccess(token: String, username: String) {
        val added = GitHubAccountManager.addAccount(
            context = this,
            username = username,
            token = token,
            displayName = username
        )

        if (!added) {
            runOnUiThread {
                connect.isEnabled = true
                connect.text = "بدء تسجيل الدخول"
                showError("تعذر حفظ التفويض بأمان")
            }
            return
        }

        if (shouldActivateAfterLogin) {
            val accountId = username.lowercase()
            GitHubAccountManager.switchTo(this, accountId)
        }

        try {
            AuthManager.getInstance(this).saveGithubUsername(username)
        } catch (_: Exception) {
        }

        runOnUiThread {
            clearDeviceCodeNotification()
            connect.isEnabled = true

            val accounts = GitHubAccountManager.listAccounts(this)
            status.text = "تم ربط الحساب: $username" +
                    if (accounts.size > 1) " (${accounts.size} حسابات)" else ""
            status.setTextColor(colorSuccess)

            codeView.text = ""
            connect.text = "دخول إلى التطبيق"
            connect.background = round(colorSuccess, 13, colorBorder)

            connect.setOnClickListener { enterApp() }

            Toast.makeText(
                this,
                "تم إضافة الحساب $username بنجاح",
                Toast.LENGTH_LONG
            ).show()

            root.postDelayed({
                if (!isFinishing) enterApp()
            }, 800L)
        }
    }

    // ============================================================
    // الإشعار
    // ============================================================
    private fun showDeviceCodeNotification(device: GitHubApi.DeviceCode) {
        pendingDeviceCode = device
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST
            )
            return
        }
        postDeviceCodeNotification(device)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            pendingDeviceCode?.let { postDeviceCodeNotification(it) }
        }
    }

    private fun postDeviceCodeNotification(device: GitHubApi.DeviceCode) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                DEVICE_CODE_CHANNEL,
                "تسجيل الدخول إلى GitHub",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "رمز تسجيل الدخول المؤقت إلى GitHub"
            }
            manager.createNotificationChannel(channel)
        }

        val verificationUrl = if (device.verificationUri.isBlank()) {
            GitHubConfig.VERIFICATION_URL
        } else {
            device.verificationUri
        }

        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(verificationUrl))
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE

        val pending = PendingIntent.getActivity(
            this, DEVICE_CODE_NOTIFICATION, openIntent, flags
        )

        val message = "الرمز: ${device.userCode}\n" +
                "افتح GitHub، الصق الرمز، ثم اضغط Authorize."

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, DEVICE_CODE_CHANNEL)
        } else {
            Notification.Builder(this)
        }

        builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("رمز تسجيل الدخول إلى GitHub")
            .setContentText("الرمز: ${device.userCode}")
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setAutoCancel(false)
            .setOngoing(false)
            .setOnlyAlertOnce(true)

        manager.notify(DEVICE_CODE_NOTIFICATION, builder.build())
    }

    private fun clearDeviceCodeNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(DEVICE_CODE_NOTIFICATION)
        pendingDeviceCode = null
    }

    // ============================================================
    // الدخول إلى التطبيق
    // ============================================================
    private fun enterApp() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    // ============================================================
    // عرض الخطأ
    // ============================================================
    private fun showError(message: String?) {
        val fullText = message ?: "Unknown error"

        val content = TextView(this).apply {
            text = fullText
            setTextColor(colorText)
            setTextSize(12f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setTextIsSelectable(true)
            gravity = Gravity.START
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(colorBg)
            addView(content, FrameLayout.LayoutParams(-1, -2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.55).toInt()
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("تفاصيل الخطأ")
            .setView(scroll)
            .setPositiveButton("نسخ") { _, _ ->
                try {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("error", fullText))
                    Toast.makeText(this, "تم نسخ التفاصيل", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                }
            }
            .setNeutralButton("إعادة المحاولة") { _, _ ->
                connect.isEnabled = true
                connect.text = "بدء تسجيل الدخول"
                beginLogin()
            }
            .setNegativeButton("إغلاق", null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(round(colorSurface, 16, colorBorder))
        }
        dialog.show()
    }

    // ============================================================
    // أدوات الواجهة
    // ============================================================
    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = round(colorSurface, 22, colorBorder)
            elevation = dp(3).toFloat()
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = value
            setTextSize(size)
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.DEFAULT
            setTypeface(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun button(value: String, background: Int, color: Int): Button {
        return Button(this).apply {
            text = value
            setTextSize(13f)
            setTextColor(color)
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(14), 0, dp(14), 0)
            gravity = Gravity.CENTER
            this.background = round(
                background,
                15,
                if (background == Color.TRANSPARENT) Color.TRANSPARENT else colorBorder
            )
        }
    }

    private fun round(color: Int, radius: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }
    }

    private fun params(left: Int, top: Int, right: Int, bottom: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(left), dp(top), dp(right), dp(bottom))
        }
    }

    private fun params(
        left: Int, top: Int, right: Int, bottom: Int, height: Int
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(-1, dp(height)).apply {
            setMargins(dp(left), dp(top), dp(right), dp(bottom))
        }
    }

    companion object {
        private const val DEVICE_CODE_CHANNEL = "github-device-code"
        private const val DEVICE_CODE_NOTIFICATION = 7710
        private const val NOTIFICATION_PERMISSION_REQUEST = 7711

        const val EXTRA_ACTIVATE_AFTER_LOGIN = "activate_after_login"
    }
}