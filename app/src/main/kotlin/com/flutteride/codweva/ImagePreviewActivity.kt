package com.flutteride.codweva

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.io.File

/**
 * شاشة عرض الصور كاملة الشاشة مع التنقل.
 *
 * الميزات:
 *   - عرض الصور عبر ViewPager2 (سحب يمين/يسار)
 *   - زر X للإغلاق (أعلى يسار)
 *   - عدّاد (1 / N) (أعلى يمين)
 *   - شريط سفلي: "السابق" / "التالي"
 *   - تحميل الصور مع inSampleSize (حماية من OOM)
 *
 * v2.0 — إصلاح Edge-to-Edge على Android 15/16:
 *   - WindowCompat.setDecorFitsSystemWindows(window, false)
 *   - layoutInDisplayCutoutMode = SHORT_EDGES (دعم النوتش)
 *   - تطبيق WindowInsets على الـ root layout
 *   - دعم displayCutout في الحسابات
 *   - إخفاء شريط الحالة للحصول على تجربة عرض كاملة
 *   - أيقونات شريط الحالة بيضاء (في حال ظهوره)
 */
class ImagePreviewActivity : AppCompatActivity() {

    // ============================================================
    // الألوان
    // ============================================================
    private val COLOR_BLACK = 0xFF000000.toInt()
    private val COLOR_TEXT = 0xFFE6EDF3.toInt()
    private val COLOR_TEXT_DIM = 0xFF8B949E.toInt()

    // ============================================================
    // الحقول
    // ============================================================
    private lateinit var rootLayout: FrameLayout
    private var templateId: String? = null
    private var template: TemplateInfo? = null
    private var previewFiles: List<File> = emptyList()
    private var viewPager: ViewPager2? = null
    private var counterView: TextView? = null

    private fun dp(v: Int): Int {
        return Math.round(v * resources.displayMetrics.density)
    }

    // ============================================================
    // onCreate
    // ============================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ============================================================
        // ⭐ v2.0: إعداد النافذة لـ Edge-to-Edge + إخفاء شريط الحالة
        // ============================================================
        setupWindowEdgeToEdge()

        templateId = intent.getStringExtra("template_id")
        val startIndex = intent.getIntExtra("start_index", 0)

        if (templateId == null) {
            finish()
            return
        }

        template = TemplateManager.findById(this, templateId)
        if (template == null) {
            finish()
            return
        }

        // جهّز العرض
        TemplateManager.prepareShowcase(this, template)
        previewFiles = ArrayList(template!!.previewFiles)

        if (previewFiles.isEmpty()) {
            Toast.makeText(this, "لا توجد معاينات", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        buildLayout()

        viewPager?.let { vp ->
            val safeIndex = startIndex.coerceIn(0, previewFiles.size - 1)
            vp.setCurrentItem(safeIndex, false)
        }
    }

    /**
     * ⭐ v2.0 — إعداد النافذة لـ Edge-to-Edge + إخفاء شريط الحالة.
     *
     * هذه الشاشة عرض صور كامل الشاشة (gallery)، لذلك نخفي شريط الحالة
     * للحصول على تجربة عرض كاملة. الـ insets تُطبق على الأزرار فقط
     * (زر الإغلاق، العدّاد، الشريط السفلي) وليس على الصور نفسها.
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

        // 3) ألوان شريط الحالة والتنقل (شفافة)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = COLOR_BLACK
            window.navigationBarColor = COLOR_BLACK
        }

        // 4) إخفاء شريط الحالة (تجربة عرض كاملة)
        try {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // بناء الواجهة
    // ============================================================
    private fun buildLayout() {
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(COLOR_BLACK)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        // ============================================================
        // ViewPager للصور
        // ============================================================
        viewPager = ViewPager2(this).apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            setBackgroundColor(COLOR_BLACK)
            adapter = ImagePagerAdapter(previewFiles)
            offscreenPageLimit = 1
        }

        val pagerParams = FrameLayout.LayoutParams(-1, -1)
        rootLayout.addView(viewPager, pagerParams)

        // ============================================================
        // زر X (إغلاق)
        // ============================================================
        val closeBtn = Button(this).apply {
            text = "X"
            setTextColor(Color.WHITE)
            setTextSize(18f)
            isAllCaps = false
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
        }

        val closeParams = FrameLayout.LayoutParams(dp(56), dp(56)).apply {
            gravity = Gravity.TOP or Gravity.START
            setMargins(dp(12), dp(12), 0, 0)
        }
        rootLayout.addView(closeBtn, closeParams)

        // ============================================================
        // العداد (1 / 3)
        // ============================================================
        counterView = TextView(this).apply {
            setTextColor(COLOR_TEXT)
            setTextSize(14f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setBackgroundColor(0x55000000)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }

        val counterParams = FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(0, dp(20), dp(20), 0)
        }
        rootLayout.addView(counterView, counterParams)

        // ============================================================
        // شريط سفلي
        // ============================================================
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(0x33000000)
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }

        val prevBtn = Button(this).apply {
            text = "السابق"
            setTextColor(Color.WHITE)
            isAllCaps = false
            setTextSize(13f)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                val cur = viewPager?.currentItem ?: 0
                if (cur > 0) viewPager?.setCurrentItem(cur - 1, true)
            }
        }
        bottomBar.addView(prevBtn, LinearLayout.LayoutParams(dp(100), dp(44)))

        val spacer = View(this)
        bottomBar.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))

        val nextBtn = Button(this).apply {
            text = "التالي"
            setTextColor(Color.WHITE)
            isAllCaps = false
            setTextSize(13f)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                val cur = viewPager?.currentItem ?: 0
                if (cur < previewFiles.size - 1) viewPager?.setCurrentItem(cur + 1, true)
            }
        }
        bottomBar.addView(nextBtn, LinearLayout.LayoutParams(dp(100), dp(44)))

        val bottomParams = FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM
        }
        rootLayout.addView(bottomBar, bottomParams)

        // ============================================================
        // مراقبة التغيير
        // ============================================================
        viewPager?.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    updateCounter(position)
                }
            }
        )

        // عرض العداد الحالي
        updateCounter(0)

        setContentView(rootLayout)

        // ============================================================
        // ⭐ v2.0: تطبيق الـ insets على عناصر التحكم فقط
        // ============================================================
        applyEdgeToEdgeInsetsForControls(closeBtn, counterView, bottomBar)
    }

    /**
     * ⭐ v2.0 — تطبيق WindowInsets على عناصر التحكم فقط.
     *
     * في شاشات عرض الصور، لا نريد تطبيق الـ padding على الصور نفسها
     * (لأنها ممتدة بالكامل). بدلاً من ذلك، نطبق الـ top inset على
     * زر الإغلاق والعداد، والـ bottom inset على الشريط السفلي.
     *
     * ملاحظة: شريط الحالة مخفي في هذه الشاشة، لذلك الـ top inset
     * سيكون 0 عادةً. لكننا نحسبه احتياطياً في حال ظهوره مؤقتاً
     * (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE).
     *
     * v2.1 — إصلاح Argument type mismatch:
     *   - المعاملات أصبحت nullable (View?) لدعم counterView: TextView?
     *   - استخدام ?.let للتعامل الآمن مع null
     *   - استخدام as? (safe cast) لتجنب ClassCastException
     */
    private fun applyEdgeToEdgeInsetsForControls(
        closeBtn: View?,
        counterView: View?,
        bottomBar: View?
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // ⭐ دعم النوتش
            val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            } else {
                androidx.core.graphics.Insets.NONE
            }

            val top = maxOf(systemBars.top, cutout.top)
            val bottom = maxOf(systemBars.bottom, cutout.bottom)

            // ─── زر الإغلاق: يزيح للأسفل بمقدار top ───
            closeBtn?.let { view ->
                val closeParams = view.layoutParams as? FrameLayout.LayoutParams
                if (closeParams != null) {
                    closeParams.topMargin = dp(12) + top
                    view.layoutParams = closeParams
                }
            }

            // ─── العدّاد: يزيح للأسفل بمقدار top ───
            counterView?.let { view ->
                val counterParams = view.layoutParams as? FrameLayout.LayoutParams
                if (counterParams != null) {
                    counterParams.topMargin = dp(20) + top
                    view.layoutParams = counterParams
                }
            }

            // ─── الشريط السفلي: يزيح للأعلى بمقدار bottom ───
            bottomBar?.let { view ->
                val bottomParams = view.layoutParams as? FrameLayout.LayoutParams
                if (bottomParams != null) {
                    bottomParams.bottomMargin = bottom
                    view.layoutParams = bottomParams
                }
            }

            insets
        }

        ViewCompat.requestApplyInsets(rootLayout)
    }

    private fun updateCounter(index: Int) {
        counterView?.text = "${index + 1} / ${previewFiles.size}"
    }

    // ============================================================
    // Adapter
    // ============================================================
    private inner class ImagePagerAdapter(
        private val files: List<File>
    ) : RecyclerView.Adapter<ImagePagerAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val frame = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                setBackgroundColor(COLOR_BLACK)
            }

            val img = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            frame.addView(img, FrameLayout.LayoutParams(-1, -1))

            return VH(frame, img)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = files[position]
            if (f == null || !f.exists()) return

            // فكّ بحجم محدود
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(f.absolutePath, opts)

            val target = dp(1400)
            var sample = 1
            val maxDim = maxOf(opts.outWidth, opts.outHeight)
            while (maxDim / (sample * 2) >= target) {
                sample *= 2
            }
            opts.inJustDecodeBounds = false
            opts.inSampleSize = sample
            opts.inPreferredConfig = Bitmap.Config.RGB_565

            val bmp = BitmapFactory.decodeFile(f.absolutePath, opts)
            if (bmp != null) {
                holder.image.setImageBitmap(bmp)
            }
        }

        override fun getItemCount(): Int = files.size

        inner class VH(
            itemView: View,
            val image: ImageView
        ) : RecyclerView.ViewHolder(itemView)
    }
}