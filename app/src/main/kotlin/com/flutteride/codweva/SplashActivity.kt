package com.flutteride.codweva

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * SplashActivity — شاشة البداية مع أنميشن احترافي.
 *
 * التدفق:
 *   1. خلفية داكنة
 *   2. الأيقونة تظهر (scale + fade) مع نبض خفيف
 *   3. النص "Klencod IDE" يظهر تدريجياً (LTR)
 *   4. خط ملون يتمدد تحت النص
 *   5. 3 نقاط تحميل تتلألأ
 *   6. Fade out → LoginActivity أو MainActivity
 *
 * المدة الكلية: ~2.8 ثانية + fade out
 *
 * v2.0 — إصلاح جذري لمشكلة Edge-to-Edge على Android 15/16:
 *   - WindowCompat.setDecorFitsSystemWindows(window, false) مفعّل
 *   - الـ insets تُطبّق على rootLayout (FrameLayout) كـ padding
 *   - دعم Display Cutout (النوتش) عبر layoutInDisplayCutoutMode
 *   - إعادة تطبيق الـ insets بعد setContentView
 *   - إخفاء شريط الحالة بجعل الخلفية داكنة متناسقة (اختياري)
 */
class SplashActivity : AppCompatActivity() {

    // ============================================================
    // الألوان (نفس ألوان التطبيق)
    // ============================================================
    private val COLOR_BACKGROUND = 0xFF0D1117.toInt()
    private val COLOR_TEXT_PRIMARY = 0xFFE6EDF3.toInt()
    private val COLOR_PRIMARY = 0xFF1F6FEB.toInt()
    private val COLOR_PRIMARY_LIGHT = 0xFF58A6FF.toInt()
    private val COLOR_BORDER = 0xFF30363D.toInt()

    // ============================================================
    // المدد (بالميلي ثانية)
    // ============================================================
    private val DURATION_ICON_APPEAR = 700L
    private val DURATION_TEXT_APPEAR = 500L
    private val DURATION_UNDERLINE = 400L
    private val DURATION_TOTAL_BEFORE_NAVIGATE = 2800L
    private val DURATION_FADE_OUT = 350L

    // ============================================================
    // عناصر الواجهة
    // ============================================================
    private lateinit var rootLayout: FrameLayout
    private lateinit var iconContainer: FrameLayout
    private lateinit var iconView: ImageView
    private lateinit var textContainer: LinearLayout
    private lateinit var klencodView: TextView
    private lateinit var ideView: TextView
    private lateinit var underlineView: View
    private lateinit var dotsContainer: LinearLayout

    private val dots = mutableListOf<View>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // منع التنقل المزدوج
    @Volatile
    private var navigated = false

    private fun dp(value: Int): Int {
        return Math.round(value * resources.displayMetrics.density)
    }

    // ============================================================
    // onCreate
    // ============================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ============================================================
        // ⭐ v2.0: إعداد النافذة بشكل صحيح قبل بناء الواجهة
        // ============================================================
        setupWindowEdgeToEdge()

        buildLayout()
        startAnimation()

        // احتياطي: إذا فشل الأنميشن لأي سبب، انتقل بعد 4 ثوان
        mainHandler.postDelayed({
            if (!navigated) navigateNext()
        }, 4000L)
    }

    /**
     * ⭐ v2.0 — إعداد النافذة لـ Edge-to-Edge بشكل كامل.
     *
     * الخطوات:
     *   1. WindowCompat.setDecorFitsSystemWindows(window, false)
     *      → يخلي المحتوى يمتد خلف شريط الحالة وشريط التنقل (إجباري على Android 15+)
     *   2. layoutInDisplayCutoutMode = SHORT_EDGES (API 28+)
     *      → يسمح للمحتوى بالامتداد خلف النوتش/الكاميرا
     *   3. statusBarColor + navigationBarColor شفافة/داكنة
     *   4. أيقونات شريط الحالة بيضاء (لأن الخلفية داكنة)
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
        }

        // 4) أيقونات شريط الحالة بيضاء (خلفية داكنة)
        try {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }

    // ============================================================
    // بناء الواجهة
    // ============================================================
    private fun buildLayout() {
        // ============================================================
        // الجذر (خلفية داكنة)
        // ============================================================
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(COLOR_BACKGROUND)
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }

        // ============================================================
        // ⭐ v2.0: طبّق الـ insets مبكراً (قبل بناء المحتوى)
        // ============================================================
        applyInsets()

        // ============================================================
        // حاوية عمودية مركزية
        // ============================================================
        val centerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        // ============================================================
        // 1) الأيقونة (مع حواف دائرية)
        // ============================================================
        val iconSize = dp(140)

        iconContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            alpha = 0f
            scaleX = 0.4f
            scaleY = 0.4f

            // ⭐ حواف دائرية ناعمة
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(
                        0, 0, view.width, view.height,
                        dp(32).toFloat()  // ⭐ 32dp = زوايا ناعمة
                    )
                }
            }
        }

        // خلفية placeholder خلف الأيقونة (في حال كانت شفافة)
        val iconBg = View(this).apply {
            background = GradientDrawable().apply {
                setColor(COLOR_PRIMARY)
                cornerRadius = dp(32).toFloat()
            }
        }
        iconContainer.addView(
            iconBg,
            FrameLayout.LayoutParams(-1, -1)
        )

        // الأيقونة نفسها
        iconView = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        iconContainer.addView(
            iconView,
            FrameLayout.LayoutParams(-1, -1)
        )

        // ظل خفيف أسفل الأيقونة (اختياري: يعطي عمقاً)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            iconContainer.elevation = dp(12).toFloat()
        }

        centerContainer.addView(iconContainer)

        // ============================================================
        // 2) النص (Klencod IDE) — LTR إجباري
        // ============================================================
        textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            alpha = 0f
            // ⭐ إجبار LTR لأن النص إنجليزي (يمنع انعكاس RTL)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
        }
        val textParams = LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = dp(28)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        centerContainer.addView(textContainer, textParams)

        // "Klencod " (أبيض)
        klencodView = TextView(this).apply {
            text = "Klencod "
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(32f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = false
            letterSpacing = 0.02f
            // ⭐ إجبار LTR
            textDirection = View.TEXT_DIRECTION_LTR
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        textContainer.addView(klencodView)

        // "IDE" (أزرق لامع)
        ideView = TextView(this).apply {
            text = "IDE"
            setTextColor(COLOR_PRIMARY_LIGHT)
            setTextSize(32f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = false
            letterSpacing = 0.06f
            // ⭐ إجبار LTR
            textDirection = View.TEXT_DIRECTION_LTR
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        textContainer.addView(ideView)

        // ============================================================
        // 3) الخط الملون تحت النص
        // ============================================================
        underlineView = View(this).apply {
            background = GradientDrawable().apply {
                setColor(COLOR_PRIMARY)
                cornerRadius = dp(2).toFloat()
            }
            alpha = 0f
        }
        val underlineParams = LinearLayout.LayoutParams(0, dp(3)).apply {
            topMargin = dp(14)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        centerContainer.addView(underlineView, underlineParams)

        // ============================================================
        // 4) 3 نقاط تحميل
        // ============================================================
        dotsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            alpha = 0f
            // ⭐ LTR لترتيب النقاط من اليسار
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val dotsParams = LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = dp(36)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        centerContainer.addView(dotsContainer, dotsParams)

        for (i in 0 until 3) {
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(COLOR_PRIMARY_LIGHT)
                }
                alpha = 0.3f
            }
            val dotParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                marginStart = if (i == 0) 0 else dp(8)
                marginEnd = if (i == 2) 0 else dp(8)
            }
            dotsContainer.addView(dot, dotParams)
            dots.add(dot)
        }

        rootLayout.addView(centerContainer)

        setContentView(rootLayout)

        // ⭐ v2.0: أعد تطبيق insets بعد setContentView (لضمان التطبيق)
        ViewCompat.requestApplyInsets(rootLayout)
    }

    /**
     * ⭐ v2.0 — تطبيق WindowInsets على rootLayout كـ padding.
     *
     * - systemBars: شريط الحالة (top) + شريط التنقل (bottom) + الأطراف
     * - displayCutout: النوتش/الكاميرا (إن وُجد)
     *
     * نطبقها على rootLayout كـ padding حتى لا يمتد المحتوى تحت الأشرطة.
     */
    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // ⭐ دعم النوتش: استخدم أكبر قيمة بين systemBars و displayCutout
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
        ViewCompat.requestApplyInsets(rootLayout)
    }

    // ============================================================
    // الأنميشن الرئيسي
    // ============================================================
    private fun startAnimation() {
        // ============================================================
        // 1) الأيقونة تظهر (scale + fade + overshoot)
        // ============================================================
        val iconAlpha = ObjectAnimator.ofFloat(iconContainer, "alpha", 0f, 1f).apply {
            duration = DURATION_ICON_APPEAR
            interpolator = DecelerateInterpolator()
        }

        val iconScaleX = ObjectAnimator.ofFloat(iconContainer, "scaleX", 0.4f, 1f).apply {
            duration = DURATION_ICON_APPEAR
            interpolator = OvershootInterpolator(1.2f)
        }
        val iconScaleY = ObjectAnimator.ofFloat(iconContainer, "scaleY", 0.4f, 1f).apply {
            duration = DURATION_ICON_APPEAR
            interpolator = OvershootInterpolator(1.2f)
        }

        val iconAnimator = AnimatorSet().apply {
            playTogether(iconAlpha, iconScaleX, iconScaleY)
        }

        // ============================================================
        // 2) النص يظهر (fade + slide up)
        // ============================================================
        val textAlpha = ObjectAnimator.ofFloat(textContainer, "alpha", 0f, 1f).apply {
            duration = DURATION_TEXT_APPEAR
            interpolator = DecelerateInterpolator()
        }
        val textSlide = ObjectAnimator.ofFloat(
            textContainer, "translationY", dp(20).toFloat(), 0f
        ).apply {
            duration = DURATION_TEXT_APPEAR
            interpolator = DecelerateInterpolator()
        }

        val textAnimator = AnimatorSet().apply {
            playTogether(textAlpha, textSlide)
            startDelay = 500L  // بعد 0.5s من بدء الأيقونة
        }

        // ============================================================
        // 3) الخط الملون يتمدد من المنتصف
        // ============================================================
        val underlineExpand = ValueAnimator.ofInt(0, dp(180)).apply {
            duration = DURATION_UNDERLINE
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val width = animator.animatedValue as Int
                val params = underlineView.layoutParams
                params.width = width
                underlineView.layoutParams = params
            }
        }

        val underlineAlpha = ObjectAnimator.ofFloat(underlineView, "alpha", 0f, 1f).apply {
            duration = DURATION_UNDERLINE
        }

        val underlineAnimator = AnimatorSet().apply {
            playTogether(underlineExpand, underlineAlpha)
            startDelay = 1200L  // بعد ظهور النص
        }

        // ============================================================
        // 4) النقاط تظهر
        // ============================================================
        val dotsAlpha = ObjectAnimator.ofFloat(dotsContainer, "alpha", 0f, 1f).apply {
            duration = 300L
        }
        val dotsAnimator = AnimatorSet().apply {
            play(dotsAlpha)
            startDelay = 1600L
        }

        // ============================================================
        // 5) تشغيل كل شيء
        // ============================================================
        val masterSet = AnimatorSet()
        masterSet.playTogether(
            iconAnimator,
            textAnimator,
            underlineAnimator,
            dotsAnimator
        )
        masterSet.start()

        // ============================================================
        // 6) أنميشن النقاط اللانهائي (نبض)
        // ============================================================
        mainHandler.postDelayed({
            startDotsPulseAnimation()
        }, 1900L)

        // ============================================================
        // 7) الانتقال بعد المدة الكلية
        // ============================================================
        mainHandler.postDelayed({
            if (!navigated) {
                fadeOutAndNavigate()
            }
        }, DURATION_TOTAL_BEFORE_NAVIGATE)
    }

    /**
     * أنميشن نبض مستمر للنقاط (كل نقطة بنبض متأخر قليلاً).
     */
    private fun startDotsPulseAnimation() {
        for (i in dots.indices) {
            val dot = dots[i]
            val delay = i * 150L

            val pulse = ObjectAnimator.ofFloat(dot, "alpha", 0.3f, 1f, 0.3f).apply {
                duration = 900L
                repeatCount = ValueAnimator.INFINITE
                startDelay = delay
                interpolator = AccelerateDecelerateInterpolator()
            }
            pulse.start()
        }
    }

    /**
     * Fade out سلس ثم الانتقال للشاشة التالية.
     */
    private fun fadeOutAndNavigate() {
        if (navigated) return

        val fadeOut = ObjectAnimator.ofFloat(rootLayout, "alpha", 1f, 0f).apply {
            duration = DURATION_FADE_OUT
            interpolator = DecelerateInterpolator()
        }

        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                navigateNext()
            }
        })

        fadeOut.start()
    }

    // ============================================================
    // الانتقال للشاشة التالية
    // ============================================================
    private fun navigateNext() {
        if (navigated) return
        navigated = true

        if (isFinishing || isDestroyed) return

        val targetActivity = if (hasValidSession()) {
            // ✅ المستخدم مسجّل → MainActivity مباشرة
            MainActivity::class.java
        } else {
            // ❌ غير مسجّل → LoginActivity
            LoginActivity::class.java
        }

        try {
            val intent = Intent(this, targetActivity)
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

    /**
     * هل يوجد حساب نشط مع توكن صالح؟
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
}