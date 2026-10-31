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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.util.Locale

/**
 * شاشة تفاصيل القالب:
 *   - أيقونة كبيرة + اسم + حجم
 *   - زر "إنشاء مشروع"
 *   - صف أفقي من المعاينات (قابلة للنقر)
 *   - حوار إنشاء المشروع مع تحقق قوي
 *   - تستخدم cache القرص للأيقونة والمعاينات
 *
 * v7.0 — إصلاح Edge-to-Edge على Android 15/16:
 *   - WindowCompat.setDecorFitsSystemWindows(window, false)
 *   - layoutInDisplayCutoutMode = SHORT_EDGES (دعم النوتش)
 *   - تطبيق WindowInsets على الـ root layout
 *   - دعم displayCutout في الحسابات
 *   - أيقونات شريط الحالة بيضاء على الخلفية الداكنة
 *
 * v6.0 — رفع علامة "قائمة المشاريع تحتاج تحديث" قبل فتح MainActivity
 *        لضمان ظهور المشروع الجديد فوراً في القائمة.
 */
class TemplateDetailActivity : AppCompatActivity() {

    // ============================================================
    // ثوابت الألوان
    // ============================================================
    private val COLOR_BACKGROUND = 0xFF0D1117.toInt()
    private val COLOR_SURFACE = 0xFF161B22.toInt()
    private val COLOR_SURFACE_ALT = 0xFF21262D.toInt()
    private val COLOR_PRIMARY = 0xFF1F6FEB.toInt()
    private val COLOR_PRIMARY_LIGHT = 0xFF58A6FF.toInt()
    private val COLOR_TEXT_PRIMARY = 0xFFE6EDF3.toInt()
    private val COLOR_TEXT_SECONDARY = 0xFF8B949E.toInt()
    private val COLOR_BORDER = 0xFF30363D.toInt()
    private val COLOR_ERROR = 0xFFCF222E.toInt()

    // ============================================================
    // قائمة API الموحّدة (نفس MainActivity)
    // ============================================================
    private val API_LEVELS = arrayOf(
        "21 (Android 5.0)",
        "24 (Android 7.0)",
        "28 (Android 9)",
        "31 (Android 12)",
        "33 (Android 13)",
        "34 (Android 14)",
        "35 (Android 15)",
        "36 (Android 16)"
    )

    private val DEFAULT_API_INDEX = 4

    // ============================================================
    // مفتاح علامة "قائمة المشاريع تحتاج تحديث"
    // ============================================================
    private val KEY_PROJECTS_DIRTY = "projects_list_dirty"

    // ============================================================
    // الحقول
    // ============================================================
    private lateinit var rootLayout: LinearLayout
    private var templateId: String? = null
    private var template: TemplateInfo? = null
    private lateinit var previewsRow: LinearLayout
    private lateinit var iconView: ImageView
    private lateinit var nameView: TextView
    private lateinit var nameEnView: TextView
    private lateinit var sizeView: TextView
    private lateinit var noPreviewsView: TextView

    private fun dp(value: Int): Int {
        return Math.round(value * resources.displayMetrics.density)
    }

    // ============================================================
    // onCreate
    // ============================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ============================================================
        // ⭐ v7.0: إعداد النافذة لـ Edge-to-Edge (قبل أي شيء)
        // ============================================================
        setupWindowEdgeToEdge()

        templateId = intent.getStringExtra("template_id")
        if (templateId.isNullOrEmpty()) {
            finish()
            return
        }

        template = TemplateManager.findById(this, templateId)
        if (template == null) {
            Toast.makeText(this, "القالب غير موجود", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        title = template!!.displayName
        buildLayout()
        loadShowcaseAsync()
    }

    /**
     * ⭐ v7.0 — إعداد النافذة لـ Edge-to-Edge بشكل كامل.
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

    // ============================================================
    // الواجهة
    // ============================================================
    private fun buildLayout() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BACKGROUND)
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(COLOR_BACKGROUND)
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        // ============================================================
        // ترويسة القالب
        // ============================================================
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(16), dp(14), dp(16))
            background = UiStyle.rounded(
                this@TemplateDetailActivity, COLOR_SURFACE, 20, COLOR_BORDER, 1
            )
        }

        // الأيقونة
        val iconFrame = FrameLayout(this).apply {
            val iconSize = dp(100)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        }

        val iconBg = GradientDrawable().apply {
            setColor(COLOR_SURFACE_ALT)
            cornerRadius = dp(20).toFloat()
        }
        iconFrame.background = iconBg

        val defaultIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.35f
        }
        val defParams = FrameLayout.LayoutParams(-1, -1).apply {
            gravity = Gravity.CENTER
            val defPad = dp(24)
            setMargins(defPad, defPad, defPad, defPad)
        }
        iconFrame.addView(defaultIcon, defParams)

        iconView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val iconImgParams = FrameLayout.LayoutParams(-1, -1)
        iconFrame.addView(iconView, iconImgParams)

        iconFrame.clipToOutline = true
        iconFrame.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(20).toFloat())
            }
        }

        header.addView(iconFrame)

        // عمود النصوص
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val textColParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(14)
        }
        header.addView(textCol, textColParams)

        nameView = TextView(this).apply {
            text = template!!.displayName
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(22f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textCol.addView(nameView)

        nameEnView = TextView(this).apply {
            text = template!!.englishName
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(13f)
        }
        val nameEnParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(4)
        }
        textCol.addView(nameEnView, nameEnParams)

        sizeView = TextView(this).apply {
            text = "الحجم: ${template!!.sizeLabel}"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(12f)
        }
        val sizeParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(6)
        }
        textCol.addView(sizeView, sizeParams)

        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        // ============================================================
        // زر "إنشاء مشروع"
        // ============================================================
        val createBtn = Button(this).apply {
            text = "إنشاء مشروع من هذا القالب"
            setTextSize(15f)
            setTextColor(0xFFFFFFFF.toInt())
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            background = UiStyle.rounded(
                this@TemplateDetailActivity, COLOR_PRIMARY, 14, COLOR_PRIMARY_LIGHT, 1
            )
            setOnClickListener { showCreateProjectDialog() }
        }
        val createBtnParams = LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(20)
        }
        root.addView(createBtn, createBtnParams)

        // ============================================================
        // عنوان "معاينات"
        // ============================================================
        val previewsTitle = TextView(this).apply {
            text = "معاينات"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(17f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val prevTitleParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(28)
            bottomMargin = dp(12)
        }
        root.addView(previewsTitle, prevTitleParams)

        // ============================================================
        // صف المعاينات (أفقي)
        // ============================================================
        val hScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        previewsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        hScroll.addView(previewsRow, FrameLayout.LayoutParams(-2, -2))

        root.addView(hScroll, LinearLayout.LayoutParams(-1, -2))

        // ============================================================
        // رسالة افتراضية عند عدم وجود معاينات
        // ============================================================
        noPreviewsView = TextView(this).apply {
            text = "لا توجد معاينات لهذا القالب"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(13f)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(40), dp(20), dp(40))
            visibility = View.GONE
        }
        root.addView(noPreviewsView, LinearLayout.LayoutParams(-1, -2))

        scroll.addView(root)
        rootLayout.addView(scroll)

        setContentView(rootLayout)

        // ============================================================
        // ⭐ v7.0: تطبيق الـ insets على الـ root layout
        // ============================================================
        applyEdgeToEdgeInsets()
    }

    /**
     * ⭐ v7.0 — تطبيق WindowInsets على الـ root layout.
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
    // تحميل الأيقونة والمعاينات
    // ============================================================
    private fun loadShowcaseAsync() {
        Thread({
            try {
                val cachedIcon = IconCache.loadTemplateIcon(this, template)
                val showcaseDir = TemplateManager.prepareShowcase(this, template)
                if (showcaseDir == null) return@Thread

                var iconBmp = cachedIcon
                val iconFile = template!!.iconFile
                if ((iconBmp == null || iconBmp.isRecycled) &&
                    iconFile != null && iconFile.exists()
                ) {
                    val opts = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(iconFile.absolutePath, opts)

                    val target = dp(220)
                    var sample = 1
                    val maxDim = maxOf(opts.outWidth, opts.outHeight)
                    while (maxDim / (sample * 2) >= target) sample *= 2

                    opts.inJustDecodeBounds = false
                    opts.inSampleSize = sample
                    opts.inPreferredConfig = Bitmap.Config.RGB_565

                    iconBmp = BitmapFactory.decodeFile(iconFile.absolutePath, opts)

                    if (iconBmp != null && !iconBmp.isRecycled) {
                        IconCache.saveTemplateIcon(this, template, iconBmp)
                    }
                }

                val previews = mutableListOf<Bitmap>()
                for (f in template!!.previewFiles) {
                    if (f == null || !f.exists()) continue

                    val opts = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(f.absolutePath, opts)

                    val target = dp(700)
                    var sample = 1
                    val maxDim = maxOf(opts.outWidth, opts.outHeight)
                    while (maxDim / (sample * 2) >= target) sample *= 2

                    opts.inJustDecodeBounds = false
                    opts.inSampleSize = sample
                    opts.inPreferredConfig = Bitmap.Config.RGB_565

                    val bmp = BitmapFactory.decodeFile(f.absolutePath, opts)
                    if (bmp != null) previews.add(bmp)
                }

                val finalIcon = iconBmp
                val finalPreviews = previews

                runOnUiThread {
                    if (isFinishing) return@runOnUiThread

                    if (finalIcon != null && !finalIcon.isRecycled) {
                        iconView.setImageBitmap(finalIcon)
                    }
                    displayPreviews(finalPreviews)
                }
            } catch (_: Exception) {
            }
        }, "template-showcase").start()
    }

    private fun displayPreviews(previews: List<Bitmap>) {
        if (previewsRow == null) return
        previewsRow.removeAllViews()

        if (previews.isEmpty()) {
            noPreviewsView?.visibility = View.VISIBLE
            return
        }
        noPreviewsView?.visibility = View.GONE

        for (i in previews.indices) {
            val bmp = previews[i]
            val index = i

            val card = FrameLayout(this).apply {
                val cardWidth = dp(180)
                val cardHeight = dp(320)
                val cardParams = LinearLayout.LayoutParams(cardWidth, cardHeight)
                if (i > 0) cardParams.marginStart = dp(10)
                layoutParams = cardParams
            }

            val bg = GradientDrawable().apply {
                setColor(COLOR_SURFACE_ALT)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), COLOR_BORDER)
            }
            card.background = bg
            card.clipToOutline = true
            card.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(14).toFloat())
                }
            }

            val img = ImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            card.addView(img, FrameLayout.LayoutParams(-1, -1))

            card.setOnClickListener { openPreview(index) }

            previewsRow.addView(card)
        }
    }

    // ============================================================
    // فتح صورة المعاينة كاملة
    // ============================================================
    private fun openPreview(index: Int) {
        val intent = Intent(this, ImagePreviewActivity::class.java)
        intent.putExtra("template_id", templateId)
        intent.putExtra("start_index", index)
        startActivity(intent)
    }

    // ============================================================
    // حوار إنشاء المشروع
    // ============================================================
    private fun showCreateProjectDialog() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        // ============================================================
        // اسم التطبيق
        // ============================================================
        val nameLabel = TextView(this).apply {
            text = "اسم التطبيق"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(nameLabel)

        val nameInput = EditText(this).apply {
            hint = "مثلاً: رفيق المسلم"
            setText(template!!.displayName)
            setTextColor(COLOR_TEXT_PRIMARY)
            setHintTextColor(COLOR_TEXT_SECONDARY)
            background = UiStyle.rounded(
                this@TemplateDetailActivity, COLOR_SURFACE, 12, COLOR_BORDER, 1
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isSingleLine = true
            maxLines = 1
        }
        val nameParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(6)
        }
        root.addView(nameInput, nameParams)

        val nameError = TextView(this).apply {
            setTextColor(COLOR_ERROR)
            setTextSize(11f)
            setPadding(dp(4), dp(4), dp(4), dp(8))
            visibility = View.GONE
        }
        root.addView(nameError)

        val nameHint = TextView(this).apply {
            text = "ملاحظة: يُستخدم للعرض فقط"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(10f)
            setPadding(dp(4), 0, dp(4), dp(10))
        }
        root.addView(nameHint)

        // ============================================================
        // اسم الحزمة
        // ============================================================
        val pkgLabel = TextView(this).apply {
            text = "اسم الحزمة (Package)"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(pkgLabel)

        val pkgInput = EditText(this).apply {
            hint = "com.example.myapp"
            setText("com.example." + template!!.id.replace(Regex("[^a-z0-9_]"), ""))
            setTextColor(COLOR_TEXT_PRIMARY)
            setHintTextColor(COLOR_TEXT_SECONDARY)
            background = UiStyle.rounded(
                this@TemplateDetailActivity, COLOR_SURFACE, 12, COLOR_BORDER, 1
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isSingleLine = true
        }
        val pkgParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(6)
        }
        root.addView(pkgInput, pkgParams)

        val pkgError = TextView(this).apply {
            setTextColor(COLOR_ERROR)
            setTextSize(11f)
            setPadding(dp(4), dp(4), dp(4), dp(8))
            visibility = View.GONE
        }
        root.addView(pkgError)

        val pkgHint = TextView(this).apply {
            text = "حروف صغيرة وأرقام ونقاط فقط: com.ahmed.quran"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(10f)
            setPadding(dp(4), 0, dp(4), dp(10))
        }
        root.addView(pkgHint)

        // ============================================================
        // Target API (Spinner)
        // ============================================================
        val apiLabel = TextView(this).apply {
            text = "Target API"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(apiLabel)

        val apiSpinner = Spinner(this).apply {
            adapter = UiStyle.spinnerAdapter(this@TemplateDetailActivity, API_LEVELS)
            setSelection(DEFAULT_API_INDEX)
            val apiParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(6)
                bottomMargin = dp(14)
            }
            layoutParams = apiParams
        }
        UiStyle.styleSpinner(apiSpinner)
        root.addView(apiSpinner)

        // ============================================================
        // نوع المستودع
        // ============================================================
        val repoLabel = TextView(this).apply {
            text = "نوع المستودع على GitHub:"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(repoLabel)

        val repoGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(4))
        }

        val rbPublic = RadioButton(this).apply {
            text = "عام (Public) - موصى به"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            id = android.R.id.button1
        }
        repoGroup.addView(rbPublic)

        val tvPublicInfo = TextView(this).apply {
            text = "بناء مجاني غير محدود\nمناسب للمشاريع التعليمية\nالكود مرئي للجميع"
            setTextSize(11f)
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(dp(30), 0, 0, dp(10))
            setLineSpacing(0f, 1.15f)
        }
        repoGroup.addView(tvPublicInfo)

        val rbPrivate = RadioButton(this).apply {
            text = "خاص (Private)"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(13f)
            id = android.R.id.button2
        }
        repoGroup.addView(rbPrivate)

        val tvPrivateInfo = TextView(this).apply {
            text = "كودك خاص تماماً\n2,000 دقيقة بناء مجاناً/شهر\nبعدها قد تحتاج دفع"
            setTextSize(11f)
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(dp(30), 0, 0, dp(10))
            setLineSpacing(0f, 1.15f)
        }
        repoGroup.addView(tvPrivateInfo)

        repoGroup.check(android.R.id.button1)
        root.addView(repoGroup)

        // ============================================================
        // الحوار
        // ============================================================
        val scroll = ScrollView(this)
        scroll.addView(root)

        val dialog = AlertDialog.Builder(this)
            .setTitle("إنشاء مشروع من القالب")
            .setView(scroll)
            .setPositiveButton("إنشاء", null)
            .setNegativeButton("إلغاء", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString()
                val pkg = pkgInput.text.toString().trim()

                // إخفاء الأخطاء القديمة
                nameError.visibility = View.GONE
                pkgError.visibility = View.GONE
                nameInput.background = UiStyle.rounded(
                    this, COLOR_SURFACE, 12, COLOR_BORDER, 1
                )
                pkgInput.background = UiStyle.rounded(
                    this, COLOR_SURFACE, 12, COLOR_BORDER, 1
                )

                var ok = true

                // 1) اسم التطبيق
                val nameErr = TemplateInstaller.validateAppName(name)
                if (nameErr != null) {
                    nameError.text = "خطأ: $nameErr"
                    nameError.visibility = View.VISIBLE
                    nameInput.background = UiStyle.rounded(
                        this, COLOR_SURFACE, 12, COLOR_ERROR, 2
                    )
                    ok = false
                }

                // 2) اسم الحزمة
                if (pkg.isEmpty()) {
                    pkgError.text = "خطأ: أدخل اسم الحزمة"
                    pkgError.visibility = View.VISIBLE
                    pkgInput.background = UiStyle.rounded(
                        this, COLOR_SURFACE, 12, COLOR_ERROR, 2
                    )
                    ok = false
                } else if (!isValidPackage(pkg)) {
                    pkgError.text = "خطأ: صيغة غير صحيحة. مثال: com.example.myapp"
                    pkgError.visibility = View.VISIBLE
                    pkgInput.background = UiStyle.rounded(
                        this, COLOR_SURFACE, 12, COLOR_ERROR, 2
                    )
                    ok = false
                }

                if (!ok) {
                    scroll.post { scroll.fullScroll(View.FOCUS_UP) }
                    return@setOnClickListener
                }

                // استخراج API من القائمة
                val selectedApi = API_LEVELS[apiSpinner.selectedItemPosition]
                val apiLevel = selectedApi.split(" ").first()

                val isPublicRepo = (repoGroup.checkedRadioButtonId == android.R.id.button1)

                val cleanName = TemplateInstaller.sanitizeAppNameForDisplay(name)

                if (cleanName != name.trim()) {
                    AlertDialog.Builder(this)
                        .setTitle("تنبيه: تم تعديل الاسم")
                        .setMessage(
                            "الاسم الأصلي:\n«$name»\n\n" +
                                    "الاسم بعد التنظيف:\n«$cleanName»\n\n" +
                                    "هل تريد المتابعة بالاسم المُنظَّف؟"
                        )
                        .setPositiveButton("نعم، تابع") { _, _ ->
                            dialog.dismiss()
                            createProject(cleanName, pkg, apiLevel, isPublicRepo)
                        }
                        .setNegativeButton("تعديل يدويًا") { _, _ ->
                            nameInput.setText(cleanName)
                            nameInput.setSelection(cleanName.length)
                            nameInput.requestFocus()
                        }
                        .setNeutralButton("إلغاء", null)
                        .show()
                    return@setOnClickListener
                }

                dialog.dismiss()
                createProject(cleanName, pkg, apiLevel, isPublicRepo)
            }
        }

        dialog.show()
        styleDialog(dialog)
    }

    // ============================================================
    // التحقق من صحة اسم الحزمة
    // ============================================================
    private fun isValidPackage(pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return false
        val parts = pkg.split(".")
        if (parts.size < 2) return false

        for (part in parts) {
            if (part.isEmpty()) return false
            if (!part[0].isLetter()) return false
            if (part[0].isUpperCase()) return false

            for (c in part) {
                val ok = (c in 'a'..'z') || (c in '0'..'9') || c == '_'
                if (!ok) return false
            }
        }
        return true
    }

    // ============================================================
    // إنشاء المشروع فعلياً
    // ============================================================
    private fun createProject(
        appName: String,
        packageName: String,
        apiLevel: String,
        isPublicRepo: Boolean
    ) {
        try {
            val baseDir = File(filesDir, "projects")
            if (!baseDir.exists()) baseDir.mkdirs()

            val folderName = "project_${System.currentTimeMillis()}"
            val projectDir = File(baseDir, folderName)
            if (!projectDir.mkdirs()) {
                Toast.makeText(this, "تعذر إنشاء المجلد", Toast.LENGTH_SHORT).show()
                return
            }

            val ok = TemplateManager.install(
                this, template, projectDir,
                appName, packageName, apiLevel, isPublicRepo
            )

            if (!ok) {
                deleteRecursive(projectDir)
                Toast.makeText(
                    this,
                    "تعذر تثبيت القالب - تحقق من الأسماء",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // حفظ معلومات المشروع
            getSharedPreferences("flutter_ide_prefs", MODE_PRIVATE)
                .edit()
                .putString("name_$folderName", appName)
                .putString("package_$folderName", packageName)
                .putString("api_$folderName", apiLevel)
                .putBoolean("repo_public_$folderName", isPublicRepo)
                .apply()

            Toast.makeText(this, "تم إنشاء المشروع بنجاح", Toast.LENGTH_LONG).show()

            // ارفع علامة "قائمة المشاريع تحتاج تحديث"
            getSharedPreferences("flutter_ide_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PROJECTS_DIRTY, true)
                .apply()

            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteRecursive(file: File?) {
        if (file == null || !file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        file.delete()
    }

    // ============================================================
    // تطبيق نمط الحوار
    // ============================================================
    private fun styleDialog(dialog: AlertDialog?) {
        if (dialog == null) return

        dialog.window?.setBackgroundDrawable(
            UiStyle.rounded(this, COLOR_BACKGROUND, 16, COLOR_BORDER, 1)
        )

        val titleId = dialog.context.resources.getIdentifier(
            "android:id/alertTitle", null, null
        )
        if (titleId != 0) {
            dialog.findViewById<TextView>(titleId)?.setTextColor(COLOR_TEXT_PRIMARY)
        }

        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(COLOR_TEXT_PRIMARY)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(COLOR_PRIMARY_LIGHT)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(COLOR_TEXT_PRIMARY)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(COLOR_TEXT_PRIMARY)
    }
}