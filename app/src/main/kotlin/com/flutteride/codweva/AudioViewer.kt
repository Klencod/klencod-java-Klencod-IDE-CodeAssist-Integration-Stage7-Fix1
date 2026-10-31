package com.flutteride.codweva

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale

/**
 * عارض صوتي بأسلوب أفقي بسيط:
 *   - بطاقة من الأعلى لأسفل (تملأ العرض، ارتفاع صغير)
 *   - صف علوي: أيقونة دائرية (56dp) + اسم الملف + معلومات
 *   - شريط تقدم مع الأوقات
 *   - أزرار: تشغيل/إيقاف، إعادة
 */
class AudioViewer(context: Context) : FrameLayout(context) {

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

    private var mediaPlayer: MediaPlayer? = null
    private var currentPath: String? = null

    private lateinit var nameView: TextView
    private lateinit var metaView: TextView
    private lateinit var timeCurrentView: TextView
    private lateinit var timeTotalView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: Button
    private lateinit var btnRestart: Button

    private val handler = Handler(Looper.getMainLooper())
    private var progressUpdater: Runnable? = null
    private var isUserSeeking = false
    private var durationMs = 0

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

        // حاوية عمودية تتوسط عمودياً وأفقياً
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        addView(wrapper, FrameLayout.LayoutParams(-1, -1))

        // بطاقة بسيطة
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = UiStyle.rounded(context, colorSurface, 16, colorBorder, 1)
        }

        wrapper.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ═══════════════════════════════════════════════════════
        // الصف 1: أيقونة + نصوص
        // ═══════════════════════════════════════════════════════
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // أيقونة دائرية 56dp
        val icon = ImageView(context).apply {
            val iconSize = dp(56)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorPrimary)
            }
            setImageDrawable(MusicNoteDrawable())
        }
        topRow.addView(icon)

        // عمود النصوص
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val textColParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginStart = dp(12)
        }
        topRow.addView(textCol, textColParams)

        nameView = TextView(context).apply {
            setTextColor(colorText)
            setTextSize(14f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        textCol.addView(
            nameView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        metaView = TextView(context).apply {
            setTextColor(colorMuted)
            setTextSize(11f)
            typeface = android.graphics.Typeface.MONOSPACE
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val metaParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(2)
        }
        textCol.addView(metaView, metaParams)

        card.addView(
            topRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ═══════════════════════════════════════════════════════
        // الصف 2: شريط التقدم
        // ═══════════════════════════════════════════════════════
        val timeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val timeRowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(14)
        }
        card.addView(timeRow, timeRowParams)

        timeCurrentView = TextView(context).apply {
            setTextColor(colorText)
            setTextSize(10f)
            typeface = android.graphics.Typeface.MONOSPACE
            text = "00:00"
            minWidth = dp(36)
            gravity = Gravity.CENTER
        }
        timeRow.addView(timeCurrentView)

        seekBar = SeekBar(context).apply {
            max = 1000
        }
        val seekParams = LinearLayout.LayoutParams(0, dp(28), 1f).apply {
            marginStart = dp(6)
            marginEnd = dp(6)
        }
        timeRow.addView(seekBar, seekParams)

        timeTotalView = TextView(context).apply {
            setTextColor(colorText)
            setTextSize(10f)
            typeface = android.graphics.Typeface.MONOSPACE
            text = "00:00"
            minWidth = dp(36)
            gravity = Gravity.CENTER
        }
        timeRow.addView(timeTotalView)

        // ═══════════════════════════════════════════════════════
        // الصف 3: الأزرار
        // ═══════════════════════════════════════════════════════
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val btnRowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(12)
        }
        card.addView(btnRow, btnRowParams)

        btnPlay = makeControlButton("تشغيل", true).apply {
            setOnClickListener { togglePlay() }
        }
        val playParams = LinearLayout.LayoutParams(0, dp(42), 2f).apply {
            marginEnd = dp(5)
        }
        btnRow.addView(btnPlay, playParams)

        btnRestart = makeControlButton("إعادة", false).apply {
            setOnClickListener { restart() }
        }
        val restartParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            marginStart = dp(5)
        }
        btnRow.addView(btnRestart, restartParams)

        // ═══════════════════════════════════════════════════════
        // SeekBar Listener
        // ═══════════════════════════════════════════════════════
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && durationMs > 0) {
                    val pos = (durationMs.toLong() * progress / 1000L).toInt()
                    mediaPlayer?.seekTo(pos)
                    timeCurrentView.text = formatTime(pos)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                isUserSeeking = false
            }
        })
    }

    private fun makeControlButton(text: String, primary: Boolean): Button {
        return Button(context).apply {
            this.text = text
            setTextColor(if (primary) 0xFFFFFFFF.toInt() else colorText)
            setTextSize(13f)
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setPadding(dp(6), 0, dp(6), 0)
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
    // تحميل الصوت
    // ============================================================
    fun loadAudio(path: String?) {
        currentPath = path
        isUserSeeking = false
        durationMs = 0
        release()

        if (path == null) {
            showError("مسار فارغ")
            return
        }

        val file = File(path)
        if (!file.exists() || !file.isFile) {
            showError("الملف غير موجود")
            return
        }

        nameView.text = file.name
        metaView.text = formatSize(file.length())

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener { mp ->
                    durationMs = mp.duration
                    timeTotalView.text = formatTime(durationMs)
                    val f = File(path)
                    metaView.text = "${formatSize(f.length())}  •  ${formatTime(durationMs)}"
                    try {
                        mp.start()
                        btnPlay.text = "إيقاف"
                        startProgressUpdater()
                    } catch (_: Exception) {
                    }
                }
                setOnCompletionListener {
                    btnPlay.text = "تشغيل"
                    seekBar.progress = 1000
                    stopProgressUpdater()
                }
                setOnErrorListener { _, what, _ ->
                    showError("تعذر تشغيل الملف الصوتي (خطأ $what)")
                    btnPlay.text = "تشغيل"
                    stopProgressUpdater()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            showError("تعذر تحميل الملف الصوتي: ${e.message}")
        }
    }

    // ============================================================
    // التحكم
    // ============================================================
    private fun togglePlay() {
        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                btnPlay.text = "تشغيل"
                stopProgressUpdater()
            } else {
                mp.start()
                btnPlay.text = "إيقاف"
                startProgressUpdater()
            }
        } catch (_: Exception) {
        }
    }

    private fun restart() {
        val mp = mediaPlayer ?: return
        try {
            mp.seekTo(0)
            seekBar.progress = 0
            timeCurrentView.text = "00:00"
            if (!mp.isPlaying) {
                mp.start()
                btnPlay.text = "إيقاف"
            }
            startProgressUpdater()
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // تحديث شريط التقدم
    // ============================================================
    private fun startProgressUpdater() {
        stopProgressUpdater()
        progressUpdater = object : Runnable {
            override fun run() {
                val mp = mediaPlayer ?: return
                try {
                    val position = mp.currentPosition
                    if (durationMs > 0) {
                        val progress = (position.toLong() * 1000L / durationMs).toInt()
                        if (!isUserSeeking) {
                            seekBar.progress = progress
                            timeCurrentView.text = formatTime(position)
                        }
                    }
                } catch (_: Exception) {
                }
                handler.postDelayed(this, 500L)
            }
        }.also { handler.post(it) }
    }

    private fun stopProgressUpdater() {
        progressUpdater?.let { handler.removeCallbacks(it) }
        progressUpdater = null
    }

    // ============================================================
    // دورة الحياة
    // ============================================================
    fun release() {
        stopProgressUpdater()
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
    }

    val getCurrentPath: String?
        get() = currentPath

    // ============================================================
    // أدوات
    // ============================================================
    private fun showError(msg: String) {
        metaView.text = msg
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    private fun formatTime(ms: Int): String {
        var safeMs = ms
        if (safeMs < 0) safeMs = 0
        val totalSec = safeMs / 1000
        var min = totalSec / 60
        val sec = totalSec % 60
        val hour = min / 60
        return if (hour > 0) {
            min %= 60
            String.format(Locale.US, "%d:%02d:%02d", hour, min, sec)
        } else {
            String.format(Locale.US, "%02d:%02d", min, sec)
        }
    }

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
    // أيقونة نوتة موسيقية
    // ============================================================
    private class MusicNoteDrawable : android.graphics.drawable.Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val colorNote = 0xFFFFFFFF.toInt()

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            if (bounds == null || bounds.width() == 0 || bounds.height() == 0) return

            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val cx = bounds.left + w / 2f
            val cy = bounds.top + h / 2f
            val size = minOf(w, h)

            paint.style = Paint.Style.FILL
            paint.color = colorNote

            // رأس النوتة
            val noteCx = cx - size * 0.05f
            val noteCy = cy + size * 0.15f
            canvas.drawOval(
                noteCx - size * 0.18f,
                noteCy - size * 0.12f,
                noteCx + size * 0.18f,
                noteCy + size * 0.12f,
                paint
            )

            // ساق النوتة
            val stemX = noteCx + size * 0.14f
            val stemTop = cy - size * 0.38f
            val stemBottom = noteCy
            canvas.drawRect(
                stemX - size * 0.025f, stemTop,
                stemX + size * 0.025f, stemBottom,
                paint
            )

            // رأس صغير على قمة الساق
            canvas.drawCircle(stemX, stemTop, size * 0.055f, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}