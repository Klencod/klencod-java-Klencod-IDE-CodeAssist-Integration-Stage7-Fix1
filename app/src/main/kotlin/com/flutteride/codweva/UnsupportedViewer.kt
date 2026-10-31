package com.flutteride.codweva

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/**
 * عارض الملفات غير المدعومة (PDF، APK، أرشيف، ثنائيات):
 *   - يعرض اسم الملف، حجمه، نوعه
 *   - زر "فتح في تطبيق آخر"
 *   - زر "مشاركة"
 *   - رسالة توضح سبب عدم العرض
 */
class UnsupportedViewer(context: Context) : FrameLayout(context) {

    // ============================================================
    // الألوان
    // ============================================================
    private val colorBg = 0xFF0D1117.toInt()
    private val colorSurface = 0xFF161B22.toInt()
    private val colorSurfaceAlt = 0xFF21262D.toInt()
    private val colorPrimary = 0xFF1F6FEB.toInt()
    private val colorPrimaryLight = 0xFF58A6FF.toInt()
    private val colorText = 0xFFE6EDF3.toInt()
    private val colorMuted = 0xFF8B949E.toInt()
    private val colorBorder = 0xFF30363D.toInt()

    private var currentFile: File? = null
    private var currentKind: FileKind? = null

    private lateinit var nameView: TextView
    private lateinit var metaView: TextView

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

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(colorBg)
        }
        addView(content, FrameLayout.LayoutParams(-1, -1))

        // 1) أيقونة رمزية
        val iconFrame = FrameLayout(context).apply {
            val iconSize = dp(140)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            background = UiStyle.rounded(context, colorSurface, 70, colorBorder, 2)
        }

        val icon = DocumentIconView(context)
        iconFrame.addView(icon, FrameLayout.LayoutParams(-1, -1))

        val wrapper = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(iconFrame, wrapper)

        // 2) اسم الملف
        nameView = TextView(context).apply {
            setTextColor(colorText)
            setTextSize(16f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        val nameParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(24)
        }
        content.addView(nameView, nameParams)

        // 3) معلومات
        metaView = TextView(context).apply {
            setTextColor(colorMuted)
            setTextSize(11f)
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        val metaParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(6)
        }
        content.addView(metaView, metaParams)

        // 4) رسالة
        val messageView = TextView(context).apply {
            setTextColor(colorMuted)
            setTextSize(13f)
            gravity = Gravity.CENTER
            text = "لا يمكن عرض هذا النوع من الملفات داخل التطبيق.\n" +
                    "يمكنك فتحه في تطبيق آخر أو مشاركته."
            setLineSpacing(0f, 1.3f)
        }
        val msgParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(28)
            leftMargin = dp(16)
            rightMargin = dp(16)
        }
        content.addView(messageView, msgParams)

        // 5) الأزرار
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val btnRowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(32)
        }
        content.addView(btnRow, btnRowParams)

        val btnOpen = makeButton("فتح في تطبيق آخر", true).apply {
            setOnClickListener { openExternally() }
        }
        val openParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply {
            marginEnd = dp(6)
        }
        btnRow.addView(btnOpen, openParams)

        val btnShare = makeButton("مشاركة", false).apply {
            setOnClickListener { shareExternally() }
        }
        val shareParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply {
            marginStart = dp(6)
        }
        btnRow.addView(btnShare, shareParams)
    }

    private fun makeButton(text: String, primary: Boolean): Button {
        return Button(context).apply {
            this.text = text
            setTextColor(if (primary) 0xFFFFFFFF.toInt() else colorText)
            setTextSize(13f)
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
            background = UiStyle.rounded(
                context,
                if (primary) colorPrimary else colorSurfaceAlt,
                10,
                if (primary) colorPrimaryLight else colorBorder,
                1
            )
        }
    }

    // ============================================================
    // التحميل
    // ============================================================
    fun loadFile(path: String?, kind: FileKind?) {
        currentFile = if (path != null) File(path) else null
        currentKind = kind

        val file = currentFile
        if (file == null || !file.exists() || !file.isFile) {
            nameView.text = "ملف غير موجود"
            metaView.text = ""
            return
        }

        nameView.text = file.name
        val typeLabel = kind?.arabicName ?: "ملف"
        metaView.text = "${formatSize(file.length())}  •  $typeLabel"
    }

    // ============================================================
    // فتح خارجي
    // ============================================================
    private fun openExternally() {
        val file = currentFile ?: return
        if (!file.exists()) return

        try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, guessMimeType(file.name))
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            context.startActivity(Intent.createChooser(intent, "فتح باستخدام"))
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "لا يوجد تطبيق يدعم هذا النوع: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun shareExternally() {
        val file = currentFile ?: return
        if (!file.exists()) return

        try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = guessMimeType(file.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة"))
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "تعذر المشاركة: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun guessMimeType(fileName: String?): String {
        if (fileName == null) return "*/*"
        val lower = fileName.lowercase(Locale.US)
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".apk") -> "application/vnd.android.package-archive"
            lower.endsWith(".zip") -> "application/zip"
            lower.endsWith(".rar") -> "application/x-rar-compressed"
            lower.endsWith(".7z") -> "application/x-7z-compressed"
            lower.endsWith(".tar") -> "application/x-tar"
            lower.endsWith(".gz") || lower.endsWith(".tgz") -> "application/gzip"
            else -> "*/*"
        }
    }

    val getCurrentPath: String?
        get() = currentFile?.absolutePath

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

    // ============================================================
    // أيقونة مستند مرسومة
    // ============================================================
    private class DocumentIconView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val size = minOf(w, h)

            val paperW = size * 0.45f
            val paperH = size * 0.58f
            val left = cx - paperW / 2f
            val top = cy - paperH / 2f
            val right = cx + paperW / 2f
            val bottom = cy + paperH / 2f
            val radius = size * 0.04f
            val fold = size * 0.12f

            // الورقة
            paint.color = 0xFFE6EDF3.toInt()
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)

            // الركن المطوي
            paint.color = 0xFF8B949E.toInt()
            val foldPath = Path().apply {
                moveTo(right - fold, top)
                lineTo(right, top + fold)
                lineTo(right - fold, top + fold)
                close()
            }
            canvas.drawPath(foldPath, paint)

            // خطوط نصية
            paint.color = 0xFF8B949E.toInt()
            paint.strokeWidth = size * 0.015f
            val lineLeft = left + paperW * 0.15f
            val lineRight = right - paperW * 0.15f
            for (i in 0 until 4) {
                val y = top + paperH * 0.45f + i * paperH * 0.10f
                canvas.drawLine(lineLeft, y, lineRight, y, paint)
            }
        }
    }
}