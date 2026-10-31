package com.flutteride.codweva

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale

/**
 * عارض صور كامل:
 *   - عرض بحجم الشاشة (مع تقليص تلقائي للصور الضخمة)
 *   - تكبير بإصبعين
 *   - تحريك (pan) عند التكبير
 *   - نقرة مزدوجة → إعادة تعيين
 *   - أزرار تحكم أسفل
 *   - معلومات الملف
 */
class ImageViewer(context: Context) : FrameLayout(context) {

    // ============================================================
    // الألوان
    // ============================================================
    private val colorBg = 0xFF0D1117.toInt()
    private val colorSurface = 0xFF161B22.toInt()
    private val colorSurfaceAlt = 0xFF21262D.toInt()
    private val colorPrimary = 0xFF1F6FEB.toInt()
    private val colorText = 0xFFE6EDF3.toInt()
    private val colorMuted = 0xFF8B949E.toInt()
    private val colorBorder = 0xFF30363D.toInt()

    private val minScale = 0.5f
    private val maxScale = 5.0f

    private lateinit var imageView: ImageView
    private lateinit var infoView: TextView

    private var currentBitmap: Bitmap? = null
    private var currentPath: String? = null

    // حالة اللمس
    private val matrix = Matrix()
    private val savedMatrix = Matrix()

    private val NONE = 0
    private val DRAG = 1
    private val ZOOM = 2
    private var mode = NONE

    private val start = PointF()
    private val mid = PointF()
    private var oldDist = 1f

    private var lastTapTime = 0L

    init {
        setBackgroundColor(colorBg)
        buildLayout()
    }

    private fun dp(value: Int): Int {
        return Math.round(value * resources.displayMetrics.density)
    }

    // ============================================================
    // البناء
    // ============================================================
    private fun buildLayout() {
        layoutParams = LinearLayout.LayoutParams(-1, -1)

        // حاوية الصورة
        val imageContainer = FrameLayout(context).apply {
            setBackgroundColor(colorBg)
        }
        val containerParams = FrameLayout.LayoutParams(-1, -1).apply {
            bottomMargin = dp(96)
        }
        addView(imageContainer, containerParams)

        imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.MATRIX
            setBackgroundColor(colorBg)
        }
        imageContainer.addView(imageView, FrameLayout.LayoutParams(-1, -1))

        // شريط المعلومات
        infoView = TextView(context).apply {
            setTextColor(colorMuted)
            setTextSize(10f)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(colorSurfaceAlt)
            gravity = Gravity.CENTER
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }

        val infoParams = FrameLayout.LayoutParams(-1, dp(24)).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = dp(72)
        }
        addView(infoView, infoParams)

        // شريط الأزرار
        val toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(colorSurface)
        }

        val toolbarParams = FrameLayout.LayoutParams(-1, dp(72)).apply {
            gravity = Gravity.BOTTOM
        }
        addView(toolbar, toolbarParams)

        val btnZoomOut = makeToolbarButton("−").apply {
            setOnClickListener { applyZoom(1f / 1.25f) }
        }
        toolbar.addView(btnZoomOut, LinearLayout.LayoutParams(0, dp(52), 1f))

        val btnReset = makeToolbarButton("إعادة تعيين").apply {
            setTextSize(12f)
            setOnClickListener { resetView() }
        }
        val resetParams = LinearLayout.LayoutParams(0, dp(52), 1.4f).apply {
            marginStart = dp(6)
            marginEnd = dp(6)
        }
        toolbar.addView(btnReset, resetParams)

        val btnZoomIn = makeToolbarButton("+").apply {
            setOnClickListener { applyZoom(1.25f) }
        }
        toolbar.addView(btnZoomIn, LinearLayout.LayoutParams(0, dp(52), 1f))

        setupTouch()
    }

    private fun makeToolbarButton(text: String): Button {
        return Button(context).apply {
            this.text = text
            setTextColor(colorText)
            setTextSize(16f)
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
            background = UiStyle.rounded(context, colorSurfaceAlt, 10, colorBorder, 1)
        }
    }

    // ============================================================
    // تحميل صورة
    // ============================================================
    fun loadImage(path: String?) {
        currentPath = path
        currentBitmap = null
        matrix.reset()
        savedMatrix.reset()
        imageView.setImageBitmap(null)

        if (path == null) {
            showError("مسار فارغ")
            return
        }

        val file = File(path)
        if (!file.exists() || !file.isFile) {
            showError("الملف غير موجود")
            return
        }

        // 1) قراءة الأبعاد
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, opts)

        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            showError("تعذر قراءة الصورة")
            return
        }

        val origW = opts.outWidth
        val origH = opts.outHeight

        // 2) حساب inSampleSize
        val reqW = 2048
        val reqH = 2048
        var sample = 1
        while ((origW / (sample * 2)) >= reqW && (origH / (sample * 2)) >= reqH) {
            sample *= 2
        }

        // 3) فك الصورة
        opts.inJustDecodeBounds = false
        opts.inSampleSize = sample
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888

        var tempBmp: Bitmap? = try {
            BitmapFactory.decodeFile(path, opts)
        } catch (_: OutOfMemoryError) {
            opts.inPreferredConfig = Bitmap.Config.RGB_565
            try {
                BitmapFactory.decodeFile(path, opts)
            } catch (_: OutOfMemoryError) {
                showError("الصورة كبيرة جداً للعرض")
                return
            }
        }

        if (tempBmp == null) {
            showError("تعذر فك ترميز الصورة")
            return
        }

        val bmp = tempBmp
        currentBitmap = bmp
        imageView.setImageBitmap(bmp)

        // 4) ضبط المصفوفة الأولية
        post {
            if (imageView.width == 0 || imageView.height == 0) return@post
            if (bmp.isRecycled) return@post
            matrix.reset()
            val viewW = imageView.width.toFloat()
            val viewH = imageView.height.toFloat()
            val imgW = bmp.width.toFloat()
            val imgH = bmp.height.toFloat()
            var fitScale = minOf(viewW / imgW, viewH / imgH)
            if (fitScale <= 0 || fitScale.isNaN() || fitScale.isInfinite()) fitScale = 1f
            matrix.postScale(fitScale, fitScale)
            val dx = (viewW - imgW * fitScale) / 2f
            val dy = (viewH - imgH * fitScale) / 2f
            matrix.postTranslate(dx, dy)
            imageView.imageMatrix = matrix
        }

        // 5) تحديث سطر المعلومات
        updateInfo(file, origW, origH, sample)
    }

    private fun updateInfo(file: File, origW: Int, origH: Int, sample: Int) {
        val sb = StringBuilder()
        sb.append(file.name)
        sb.append("  •  ").append(formatSize(file.length()))
        sb.append("  •  ").append(origW).append("×").append(origH)
        if (sample > 1) {
            sb.append(" (مُقلّصة 1/").append(sample).append(")")
        }
        infoView.text = sb.toString()
    }

    private fun showError(msg: String) {
        infoView.text = msg
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    // ============================================================
    // اللمس
    // ============================================================
    private fun setupTouch() {
        imageView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(matrix)
                    start.set(event.x, event.y)
                    mode = DRAG
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    oldDist = spacing(event)
                    if (oldDist > 10f) {
                        savedMatrix.set(matrix)
                        midPoint(mid, event)
                        mode = ZOOM
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (mode == DRAG) {
                        matrix.set(savedMatrix)
                        matrix.postTranslate(event.x - start.x, event.y - start.y)
                        imageView.imageMatrix = matrix
                    } else if (mode == ZOOM) {
                        val newDist = spacing(event)
                        if (newDist > 10f) {
                            matrix.set(savedMatrix)

                            val savedValues = FloatArray(9)
                            savedMatrix.getValues(savedValues)
                            var savedScale = savedValues[Matrix.MSCALE_X]
                            if (savedScale <= 0f) savedScale = 1f

                            val desiredFactor = newDist / oldDist
                            var targetScale = savedScale * desiredFactor

                            if (targetScale < minScale) targetScale = minScale
                            if (targetScale > maxScale) targetScale = maxScale

                            val actualFactor = targetScale / savedScale
                            matrix.postScale(actualFactor, actualFactor, mid.x, mid.y)
                            imageView.imageMatrix = matrix
                        }
                    }
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    mode = NONE
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mode = NONE
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 300) {
                        resetView()
                        lastTapTime = 0
                    } else {
                        lastTapTime = now
                    }
                }
            }
            true
        }
    }

    private fun spacing(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        if (event.pointerCount < 2) return
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2f, y / 2f)
    }

    // ============================================================
    // Zoom buttons
    // ============================================================
    private fun applyZoom(factor: Float) {
        if (currentBitmap == null) return
        val cx = imageView.width / 2f
        val cy = imageView.height / 2f

        var currentScaleValue = getCurrentScale()
        if (currentScaleValue <= 0f) currentScaleValue = 1f

        var targetScale = currentScaleValue * factor
        if (targetScale < minScale) targetScale = minScale
        if (targetScale > maxScale) targetScale = maxScale
        if (Math.abs(targetScale - currentScaleValue) < 0.0001f) return

        val actualFactor = targetScale / currentScaleValue
        matrix.postScale(actualFactor, actualFactor, cx, cy)
        imageView.imageMatrix = matrix
    }

    private fun getCurrentScale(): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    private fun resetView() {
        val bmp = currentBitmap ?: return
        if (imageView.width == 0) return
        matrix.reset()
        val viewW = imageView.width.toFloat()
        val viewH = imageView.height.toFloat()
        val imgW = bmp.width.toFloat()
        val imgH = bmp.height.toFloat()
        var fitScale = minOf(viewW / imgW, viewH / imgH)
        if (fitScale <= 0 || fitScale.isNaN() || fitScale.isInfinite()) fitScale = 1f
        matrix.postScale(fitScale, fitScale)
        val dx = (viewW - imgW * fitScale) / 2f
        val dy = (viewH - imgH * fitScale) / 2f
        matrix.postTranslate(dx, dy)
        imageView.imageMatrix = matrix
    }

    // ============================================================
    // دورة الحياة
    // ============================================================
    fun release() {
        try {
            imageView.setImageBitmap(null)
        } catch (_: Exception) {
        }
        currentBitmap?.let { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        currentBitmap = null
    }

    val getCurrentPath: String?
        get() = currentPath

    val hasBitmap: Boolean
        get() = currentBitmap?.let { !it.isRecycled } ?: false

    // ============================================================
    // أدوات
    // ============================================================
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 ->
                String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else ->
                String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}