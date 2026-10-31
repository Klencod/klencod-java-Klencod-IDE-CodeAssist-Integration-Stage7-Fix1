package com.flutteride.codweva

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.util.HashMap

/**
 * شاشة "متجر القوالب".
 *
 * v2.0 — إصلاح Edge-to-Edge على Android 15/16:
 *   - WindowCompat.setDecorFitsSystemWindows(window, false)
 *   - layoutInDisplayCutoutMode = SHORT_EDGES (دعم النوتش)
 *   - تطبيق WindowInsets على الـ root layout
 *   - دعم displayCutout في الحسابات
 *   - أيقونات شريط الحالة بيضاء على الخلفية الداكنة
 *
 * - شبكة من عمودين
 * - كل بطاقة: أيقونة كبيرة + اسم القالب
 * - النقر على بطاقة → فتح TemplateDetailActivity
 * - تستخدم cache القرص للأيقونات (لا فك ZIP إلا مرة واحدة)
 */
class TemplateStoreActivity : AppCompatActivity() {

    // ============================================================
    // ثوابت الألوان
    // ============================================================
    private val COLOR_BACKGROUND = 0xFF0D1117.toInt()
    private val COLOR_SURFACE = 0xFF161B22.toInt()
    private val COLOR_SURFACE_ALT = 0xFF21262D.toInt()
    private val COLOR_PRIMARY = 0xFF1F6FEB.toInt()
    private val COLOR_TEXT_PRIMARY = 0xFFE6EDF3.toInt()
    private val COLOR_TEXT_SECONDARY = 0xFF8B949E.toInt()
    private val COLOR_BORDER = 0xFF30363D.toInt()

    // ============================================================
    // الحقول
    // ============================================================
    private lateinit var rootLayout: LinearLayout
    private lateinit var gridContainer: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var scrollView: ScrollView

    private var templatesLoaded = false
    private var isLoading = false

    private val iconMemoryCache = HashMap<String, Bitmap>()

    private fun dp(value: Int): Int {
        return Math.round(value * resources.displayMetrics.density)
    }

    // ============================================================
    // onCreate
    // ============================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ============================================================
        // ⭐ v2.0: إعداد النافذة لـ Edge-to-Edge (قبل أي شيء)
        // ============================================================
        setupWindowEdgeToEdge()

        title = "القوالب المتاحة"
        buildLayout()
        loadTemplatesAsync()
    }

    /**
     * ⭐ v2.0 — إعداد النافذة لـ Edge-to-Edge بشكل كامل.
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

        // 4) أيقونات شريط الحالة بيضاء
        try {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        // لا نُعيد التحميل — القوالب ثابتة ولا تتغير
    }

    override fun onDestroy() {
        super.onDestroy()
        for (bmp in iconMemoryCache.values) {
            if (!bmp.isRecycled) bmp.recycle()
        }
        iconMemoryCache.clear()
    }

    // ============================================================
    // بناء الواجهة
    // ============================================================
    private fun buildLayout() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BACKGROUND)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        // ============================================================
        // الترويسة
        // ============================================================
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(COLOR_SURFACE)
        }

        val backBtn = Button(this).apply {
            text = "رجوع"
            setTextColor(COLOR_TEXT_PRIMARY)
            isAllCaps = false
            setTextSize(13f)
            background = UiStyle.rounded(
                this@TemplateStoreActivity, COLOR_SURFACE_ALT, 10, COLOR_BORDER, 1
            )
            setOnClickListener { finish() }
        }
        header.addView(backBtn, LinearLayout.LayoutParams(dp(80), dp(40)))

        val titleView = TextView(this).apply {
            text = "اختر قالباً"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val titleParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        header.addView(titleView, titleParams)

        val spacer = View(this)
        header.addView(spacer, LinearLayout.LayoutParams(dp(80), dp(40)))

        rootLayout.addView(header, LinearLayout.LayoutParams(-1, -2))

        // ============================================================
        // شبكة القوالب
        // ============================================================
        scrollView = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(COLOR_BACKGROUND)
        }

        gridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(24))
        }
        scrollView.addView(gridContainer, FrameLayout.LayoutParams(-1, -2))

        rootLayout.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))

        // ============================================================
        // حالة "لا توجد قوالب"
        // ============================================================
        emptyState = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(80), dp(24), dp(80))
            visibility = View.GONE
        }

        val emptyIcon = TextView(this).apply {
            text = "لا توجد قوالب"
            setTextSize(20f)
            setTextColor(COLOR_TEXT_SECONDARY)
            gravity = Gravity.CENTER
        }
        emptyState.addView(emptyIcon)

        val emptyHint = TextView(this).apply {
            text = "ضع ملفات ZIP في مجلد assets/templates/"
            setTextSize(13f)
            setTextColor(COLOR_TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        emptyState.addView(emptyHint)

        rootLayout.addView(emptyState, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(rootLayout)

        // ============================================================
        // ⭐ v2.0: تطبيق الـ insets على الـ root layout
        // ============================================================
        applyEdgeToEdgeInsets()
    }

    /**
     * ⭐ v2.0 — تطبيق WindowInsets على الـ root layout.
     */
    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
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

        ViewCompat.requestApplyInsets(rootLayout)
    }

    // ============================================================
    // تحميل القوالب
    // ============================================================
    private fun loadTemplatesAsync() {
        if (isLoading) return
        isLoading = true

        Thread({
            val templates = TemplateManager.loadTemplates(this)
            runOnUiThread {
                isLoading = false
                displayTemplates(templates)
            }
        }, "template-load").start()
    }

    private fun displayTemplates(templates: List<TemplateInfo>?) {
        if (gridContainer == null) return

        gridContainer.removeAllViews()

        if (templates.isNullOrEmpty()) {
            gridContainer.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            scrollView.visibility = View.GONE
            templatesLoaded = true
            return
        }

        gridContainer.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        scrollView.visibility = View.VISIBLE

        // نبني الصفوف: كل صف عمودان
        var i = 0
        while (i < templates.size) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }

            val t1 = templates[i]
            row.addView(
                buildCard(t1),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )

            if (i + 1 < templates.size) {
                val t2 = templates[i + 1]
                val p = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply {
                    marginStart = dp(8)
                }
                row.addView(buildCard(t2), p)
            } else {
                val spacer = View(this)
                val p = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply {
                    marginStart = dp(8)
                }
                row.addView(spacer, p)
            }

            val rowParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            }
            gridContainer.addView(row, rowParams)

            i += 2
        }

        templatesLoaded = true
    }

    // ============================================================
    // بطاقة قالب واحدة
    // ============================================================
    private fun buildCard(template: TemplateInfo): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(16), dp(12), dp(14))
            background = UiStyle.rounded(
                this@TemplateStoreActivity, COLOR_SURFACE, 18, COLOR_BORDER, 1
            )
            isClickable = true
            isFocusable = true
        }

        // ============================================================
        // الأيقونة
        // ============================================================
        val iconFrame = FrameLayout(this).apply {
            val iconSize = dp(120)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        }

        val iconBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(COLOR_SURFACE_ALT)
            cornerRadius = dp(22).toFloat()
        }
        iconFrame.background = iconBg

        // صورة افتراضية
        val defaultIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.35f
        }
        val defParams = FrameLayout.LayoutParams(-1, -1).apply {
            gravity = Gravity.CENTER
            val defPad = dp(28)
            setMargins(defPad, defPad, defPad, defPad)
        }
        iconFrame.addView(defaultIcon, defParams)

        // ImageView للأيقونة الحقيقية
        val iconView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val iconImgParams = FrameLayout.LayoutParams(-1, -1)
        iconFrame.addView(iconView, iconImgParams)

        iconFrame.clipToOutline = true
        iconFrame.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(22).toFloat())
            }
        }

        card.addView(iconFrame)

        // ============================================================
        // اسم القالب
        // ============================================================
        val name = TextView(this).apply {
            text = template.displayName
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(15f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val nameParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14)
        }
        card.addView(name, nameParams)

        // ============================================================
        // النقر
        // ============================================================
        card.setOnClickListener { openTemplate(template) }

        // ============================================================
        // تحميل الأيقونة
        // ============================================================
        loadTemplateIcon(template, iconView)

        return card
    }

    // ============================================================
    // تحميل أيقونة القالب (cache متعدد المستويات)
    // ============================================================
    private fun loadTemplateIcon(template: TemplateInfo, iconView: ImageView) {
        // 1. cache الذاكرة
        val cachedMem = iconMemoryCache[template.id]
        if (cachedMem != null && !cachedMem.isRecycled) {
            iconView.setImageBitmap(cachedMem)
            return
        }

        // 2. cache القرص
        val cachedDisk = IconCache.loadTemplateIcon(this, template)
        if (cachedDisk != null && !cachedDisk.isRecycled) {
            iconMemoryCache[template.id] = cachedDisk
            iconView.setImageBitmap(cachedDisk)
            return
        }

        // 3. لم يوجد - استخرج في الخلفية
        Thread({
            try {
                val showcaseDir = TemplateManager.prepareShowcase(
                    this@TemplateStoreActivity, template
                )
                if (showcaseDir == null) return@Thread

                val iconFile = template.iconFile
                if (iconFile == null || !iconFile.exists()) return@Thread

                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(iconFile.absolutePath, opts)

                val targetSize = dp(240)
                var sample = 1
                val maxDim = maxOf(opts.outWidth, opts.outHeight)
                while (maxDim / (sample * 2) >= targetSize) sample *= 2

                opts.inJustDecodeBounds = false
                opts.inSampleSize = sample
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888

                val bmp = BitmapFactory.decodeFile(iconFile.absolutePath, opts)
                if (bmp == null || bmp.isRecycled) return@Thread

                iconMemoryCache[template.id] = bmp

                iconView.post {
                    if (isFinishing || isDestroyed) return@post
                    if (bmp.isRecycled) return@post
                    iconView.setImageBitmap(bmp)
                }
            } catch (_: Exception) {
            }
        }, "template-icon").start()
    }

    // ============================================================
    // فتح تفاصيل القالب
    // ============================================================
    private fun openTemplate(template: TemplateInfo) {
        val intent = Intent(this, TemplateDetailActivity::class.java)
        intent.putExtra("template_id", template.id)
        startActivity(intent)
    }
}