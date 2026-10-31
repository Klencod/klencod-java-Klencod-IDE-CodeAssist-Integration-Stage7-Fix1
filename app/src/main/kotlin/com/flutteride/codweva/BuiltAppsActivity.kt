package com.flutteride.codweva

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.text.DateFormat
import java.util.Date
import java.util.HashMap
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * مكتبة التطبيقات المبنية - مع عرض أيقونة كل تطبيق (cache على القرص).
 *
 * v2.0 — إصلاح Edge-to-Edge على Android 15/16:
 *   - WindowCompat.setDecorFitsSystemWindows(window, false)
 *   - layoutInDisplayCutoutMode = SHORT_EDGES (دعم النوتش)
 *   - تطبيق WindowInsets على الـ root layout
 *   - دعم displayCutout في الحسابات
 *   - أيقونات شريط الحالة بيضاء على الخلفية الداكنة
 */
class BuiltAppsActivity : AppCompatActivity() {

    private val colorBg = 0xFF0D1117.toInt()
    private val colorSurface = 0xFF161B22.toInt()
    private val colorSurfaceAlt = 0xFF21262D.toInt()
    private val colorPrimary = 0xFF1F6FEB.toInt()
    private val colorText = 0xFFE6EDF3.toInt()
    private val colorMuted = colorText
    private val colorBorder = 0xFF30363D.toInt()

    private lateinit var rootLayout: LinearLayout
    private lateinit var store: BuiltAppsStore
    private lateinit var list: LinearLayout
    private val apkIconCache = HashMap<String, Bitmap>()

    private fun dp(value: Int): Int {
        return Math.round(value * resources.displayMetrics.density)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ============================================================
        // ⭐ v2.0: إعداد النافذة لـ Edge-to-Edge (قبل أي شيء)
        // ============================================================
        setupWindowEdgeToEdge()

        store = BuiltAppsStore(this)
        title = "مكتبة التطبيقات المبنية"
        buildLayout()
        cleanupOldCachesAsync()
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

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroy() {
        super.onDestroy()
        for (bmp in apkIconCache.values) {
            if (!bmp.isRecycled) bmp.recycle()
        }
        apkIconCache.clear()
    }

    private fun cleanupOldCachesAsync() {
        Thread({
            try {
                val monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                val dir = File(cacheDir, "apk_icons")
                if (!dir.isDirectory) return@Thread
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && f.lastModified() < monthAgo) f.delete()
                }
            } catch (_: Exception) {
            }
        }, "cache-cleanup").start()
    }

    private fun buildLayout() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
            setPadding(dp(20), dp(18), dp(20), dp(20))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = UiStyle.rounded(this@BuiltAppsActivity, colorSurface, 18, colorBorder, 1)
        }

        val titleView = UiStyle.title(this, "مكتبة التطبيقات", 21f)
        header.addView(titleView, params(0, -2, 1f, 0))

        val refresh = button("تحديث", colorSurfaceAlt).apply {
            setOnClickListener { render() }
        }
        header.addView(refresh, params(-2, -2, 0f, 0))

        rootLayout.addView(header, params(-1, -2, 0f, 0))

        val subtitle = label(
            "كل APK تم بناؤه محفوظ هنا ويمكن تثبيته من جديد لاحقًا.",
            13f, colorText
        ).apply {
            setPadding(dp(8), dp(12), dp(8), dp(14))
        }
        rootLayout.addView(subtitle, params(-1, -2, 0f, 0))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(list, FrameLayout.LayoutParams(-1, -2))

        rootLayout.addView(scroll, params(-1, 0, 1f, 0))

        setContentView(rootLayout)

        // ============================================================
        // ⭐ v2.0: تطبيق الـ insets على الـ root layout
        // ============================================================
        applyEdgeToEdgeInsets()

        render()
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

            // نحافظ على padding الأصلي (dp 18-20) + insets
            view.setPadding(
                dp(20) + left,
                dp(18) + top,
                dp(20) + right,
                dp(20) + bottom
            )
            insets
        }

        ViewCompat.requestApplyInsets(rootLayout)
    }

    private fun render() {
        list.removeAllViews()
        val apps = store.list()

        if (apps.isEmpty()) {
            val empty = label(
                "لا توجد تطبيقات مبنية بعد.\nبعد نجاح أي عملية بناء ستظهر النسخة هنا تلقائيًا.",
                15f, colorMuted
            ).apply {
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(80), dp(20), dp(80))
                background = UiStyle.rounded(this@BuiltAppsActivity, colorSurface, 16, colorBorder, 1)
            }
            list.addView(empty, params(-1, -2, 0f, 0))
            return
        }

        for (app in apps) addCard(app)
    }

    private fun addCard(app: File) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = UiStyle.rounded(this@BuiltAppsActivity, colorSurface, 16, colorBorder, 1)
        }

        val iconContainer = FrameLayout(this).apply {
            val iconSize = dp(52)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        }

        val placeholder = TextView(this).apply {
            text = ""
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(colorSurfaceAlt)
                setStroke(dp(1), colorBorder)
            }
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        iconContainer.addView(placeholder)

        card.addView(iconContainer)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val tcParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(14)
        }
        card.addView(textCol, tcParams)

        var titleText = app.name
        if (titleText.lowercase(Locale.US).endsWith(".apk")) {
            titleText = titleText.substring(0, titleText.length - 4)
        }

        val titleView = TextView(this).apply {
            text = titleText
            setTextColor(colorText)
            setTextSize(15f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textCol.addView(titleView)

        val detailsView = TextView(this).apply {
            text = "${formatSize(app.length())}  •  " +
                    DateFormat.getDateTimeInstance(
                        DateFormat.SHORT, DateFormat.SHORT
                    ).format(Date(app.lastModified()))
            setTextColor(colorText)
            setTextSize(11f)
        }
        val detailsParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(4)
        }
        textCol.addView(detailsView, detailsParams)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val acParams = LinearLayout.LayoutParams(-2, -2).apply {
            marginStart = dp(8)
        }
        card.addView(actions, acParams)

        val install = button("تثبيت", colorPrimary).apply {
            setTextSize(12f)
            setOnClickListener { FlutterBuildApi(this@BuiltAppsActivity).installApk(app) }
        }
        actions.addView(install, LinearLayout.LayoutParams(dp(70), dp(40)))

        val delete = button("حذف", colorSurfaceAlt).apply {
            setTextSize(12f)
            setOnClickListener { confirmDelete(app) }
        }
        val delParams = LinearLayout.LayoutParams(dp(60), dp(40)).apply {
            marginStart = dp(6)
        }
        actions.addView(delete, delParams)

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val wrapParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(4), 0, dp(4))
            }
            layoutParams = wrapParams
        }
        wrapper.addView(card, LinearLayout.LayoutParams(-1, -2))

        list.addView(wrapper)

        loadApkIconAsync(app, iconContainer)
    }

    private fun loadApkIconAsync(apkFile: File, container: FrameLayout) {
        val cachedMem = apkIconCache[apkFile.absolutePath]
        if (cachedMem != null && !cachedMem.isRecycled) {
            applyIconToContainer(container, cachedMem)
            return
        }

        val cachedDisk = IconCache.loadApkIcon(this, apkFile)
        if (cachedDisk != null && !cachedDisk.isRecycled) {
            apkIconCache[apkFile.absolutePath] = cachedDisk
            applyIconToContainer(container, cachedDisk)
            return
        }

        Thread({
            val bmp = extractApkIcon(apkFile)
            if (bmp == null || bmp.isRecycled) return@Thread

            apkIconCache[apkFile.absolutePath] = bmp
            IconCache.saveApkIcon(this@BuiltAppsActivity, apkFile, bmp)

            container.post {
                if (isFinishing || isDestroyed) return@post
                if (bmp.isRecycled) return@post
                applyIconToContainer(container, bmp)
            }
        }, "apk-icon-loader").start()
    }

    private fun applyIconToContainer(container: FrameLayout, bmp: Bitmap) {
        if (bmp.isRecycled) return
        if (container.childCount == 0) return

        container.removeAllViews()
        val iconView = ImageView(this).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
                }
            }
        }
        container.addView(iconView)
    }

    private fun extractApkIcon(apkFile: File): Bitmap? {
        if (!apkFile.isFile) return null

        val key = apkFile.absolutePath
        apkIconCache[key]?.let {
            if (!it.isRecycled) return it
        }

        var bestIcon: Bitmap? = null
        var bestDensity = -1

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(apkFile))).use { zip ->
                var entry: ZipEntry?
                while (zip.nextEntry.also { entry = it } != null) {
                    val name = entry!!.name ?: continue

                    val isLauncherIcon = name.endsWith("ic_launcher.png") ||
                            name.endsWith("ic_launcher_foreground.png") ||
                            name.endsWith("ic_launcher.webp") ||
                            name.endsWith("app_icon.png")

                    if (!isLauncherIcon) continue

                    val priority = getDensityPriority(name)
                    if (priority < bestDensity) continue

                    val bos = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var count: Int
                    while (zip.read(buffer).also { count = it } != -1) {
                        bos.write(buffer, 0, count)
                        if (bos.size() > 3 * 1024 * 1024) break
                    }
                    zip.closeEntry()

                    val bytes = bos.toByteArray()
                    if (bytes.isEmpty()) continue

                    val opts = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    if (opts.outWidth <= 0 || opts.outHeight <= 0) continue

                    val targetPx = dp(96)
                    var sample = 1
                    val maxDim = maxOf(opts.outWidth, opts.outHeight)
                    while (maxDim / (sample * 2) >= targetPx) sample *= 2

                    opts.inJustDecodeBounds = false
                    opts.inSampleSize = sample
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888

                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    if (bmp != null) {
                        bestIcon?.let { if (!it.isRecycled) it.recycle() }
                        bestIcon = bmp
                        bestDensity = priority
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (bestIcon == null) {
            try {
                val info = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                val appInfo = info?.applicationInfo
                if (appInfo != null) {
                    appInfo.sourceDir = apkFile.absolutePath
                    appInfo.publicSourceDir = apkFile.absolutePath
                    val d = appInfo.loadIcon(packageManager)
                    if (d != null) {
                        val size = dp(96)
                        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        d.setBounds(0, 0, size, size)
                        d.draw(canvas)
                        bestIcon = bmp
                    }
                }
            } catch (_: Exception) {
            }
        }

        bestIcon?.let { apkIconCache[key] = it }
        return bestIcon
    }

    private fun getDensityPriority(path: String?): Int {
        if (path == null) return 0
        return when {
            path.contains("xxxhdpi") -> 5
            path.contains("xxhdpi") -> 4
            path.contains("xhdpi") -> 3
            path.contains("hdpi") -> 2
            path.contains("mdpi") -> 1
            else -> 0
        }
    }

    private fun confirmDelete(app: File) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("حذف التطبيق")
            .setMessage("هل تريد حذف نسخة APK هذه من المكتبة؟")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حذف") { _, _ ->
                try {
                    invalidateApkIconCache(app)
                    store.delete(app)
                    render()
                } catch (e: Exception) {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
            .create()
        dialog.show()
        UiStyle.styleDialog(dialog)
    }

    private fun invalidateApkIconCache(apkFile: File) {
        apkIconCache.remove(apkFile.absolutePath)?.let {
            if (!it.isRecycled) it.recycle()
        }
        IconCache.removeApkIcon(this, apkFile)
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 ->
                String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else ->
                String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun label(text: String, size: Float, color: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextSize(size)
            setTextColor(color)
        }
    }

    private fun button(text: String, color: Int): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(colorText)
            isAllCaps = false
            setTextSize(14f)
            minHeight = dp(46)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = UiStyle.rounded(
                this@BuiltAppsActivity, color, 12,
                if (color == colorPrimary) 0xFF58A6FF.toInt() else colorBorder, 1
            )
        }
    }

    private fun params(width: Int, height: Int, weight: Float, margin: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(width, height, weight).apply {
            setMargins(dp(margin), dp(5), dp(margin), dp(5))
        }
    }
}