package com.flutteride.codweva

import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import java.io.File
import java.util.Locale

/**
 * عارض فيديو مع:
 *   - VideoView (يتوسط الشاشة)
 *   - أزرار: تشغيل/إيقاف، إعادة
 *   - شريط تقدم قابل للسحب
 *   - عرض الوقت الحالي / المدة
 *   - معلومات الملف
 */
class VideoViewer(context: Context) : FrameLayout(context) {

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

    private lateinit var videoView: VideoView
    private lateinit var infoView: TextView
    private lateinit var timeCurrentView: TextView
    private lateinit var timeTotalView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: Button
    private lateinit var btnRestart: Button

    private var currentPath: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressUpdater: Runnable? = null

    private var isUserSeeking = false
    private var autoPlayed = false

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

        // 1) عرض الفيديو
        val videoContainer = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
        }

        videoView = VideoView(context)
        val vvParams = FrameLayout.LayoutParams(-1, -1).apply {
            gravity = Gravity.CENTER
        }
        videoContainer.addView(videoView, vvParams)

        val containerParams = FrameLayout.LayoutParams(-1, -1).apply {
            bottomMargin = dp(140)
        }
        addView(videoContainer, containerParams)

        // 2) معلومات الملف
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
            bottomMargin = dp(116)
        }
        addView(infoView, infoParams)

        // 3) منطقة التحكم
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(colorSurface)
        }

        val controlsParams = FrameLayout.LayoutParams(-1, dp(116)).apply {
            gravity = Gravity.BOTTOM
        }
        addView(controls, controlsParams)

        // صف 1: الوقت + شريط التقدم
        val timeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        timeCurrentView = TextView(context).apply {
            setTextColor(colorText)
            setTextSize(11f)
            typeface = android.graphics.Typeface.MONOSPACE
            text = "00:00"
            minWidth = dp(48)
            gravity = Gravity.CENTER
        }
        timeRow.addView(timeCurrentView)

        seekBar = SeekBar(context).apply {
            max = 1000
            progress = 0
        }
        val seekParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
            marginStart = dp(6)
            marginEnd = dp(6)
        }
        timeRow.addView(seekBar, seekParams)

        timeTotalView = TextView(context).apply {
            setTextColor(colorText)
            setTextSize(11f)
            typeface = android.graphics.Typeface.MONOSPACE
            text = "00:00"
            minWidth = dp(48)
            gravity = Gravity.CENTER
        }
        timeRow.addView(timeTotalView)

        controls.addView(timeRow, LinearLayout.LayoutParams(-1, dp(44)))

        // صف 2: الأزرار
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        btnPlay = makeControlButton("تشغيل", true).apply {
            setOnClickListener { togglePlay() }
        }
        val playParams = LinearLayout.LayoutParams(0, dp(44), 2f).apply {
            marginEnd = dp(4)
        }
        btnRow.addView(btnPlay, playParams)

        btnRestart = makeControlButton("إعادة", false).apply {
            setOnClickListener { restart() }
        }
        val restartParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(4)
        }
        btnRow.addView(btnRestart, restartParams)

        controls.addView(btnRow, LinearLayout.LayoutParams(-1, dp(52)))

        // 4) مستمعو SeekBar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = videoView.duration
                    if (duration > 0) {
                        val pos = (duration.toLong() * progress / 1000L).toInt()
                        videoView.seekTo(pos)
                        timeCurrentView.text = formatTime(pos)
                    }
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
            setTextSize(12f)
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
    // تحميل الفيديو
    // ============================================================
    fun loadVideo(path: String?) {
        currentPath = path
        autoPlayed = false
        isUserSeeking = false

        if (path == null) {
            showError("مسار فارغ")
            return
        }

        val file = File(path)
        if (!file.exists() || !file.isFile) {
            showError("الملف غير موجود")
            return
        }

        infoView.text = "${file.name}  •  ${formatSize(file.length())}"

        try {
            videoView.setVideoPath(path)
        } catch (e: Exception) {
            showError("تعذر تحميل الفيديو: ${e.message}")
            return
        }

        videoView.setOnPreparedListener { mp ->
            val duration = mp.duration
            timeTotalView.text = formatTime(duration)
            if (!autoPlayed) {
                autoPlayed = true
                videoView.start()
                btnPlay.text = "إيقاف"
            }
            startProgressUpdater()
        }

        videoView.setOnCompletionListener {
            btnPlay.text = "تشغيل"
            seekBar.progress = 1000
            stopProgressUpdater()
        }

        videoView.setOnErrorListener { _, what, _ ->
            showError("تعذر تشغيل الفيديو (خطأ $what)")
            btnPlay.text = "تشغيل"
            stopProgressUpdater()
            true
        }
    }

    // ============================================================
    // التحكم
    // ============================================================
    private fun togglePlay() {
        if (videoView.isPlaying) {
            videoView.pause()
            btnPlay.text = "تشغيل"
            stopProgressUpdater()
        } else {
            videoView.start()
            btnPlay.text = "إيقاف"
            startProgressUpdater()
        }
    }

    private fun restart() {
        videoView.seekTo(0)
        seekBar.progress = 0
        timeCurrentView.text = "00:00"
        if (!videoView.isPlaying) {
            videoView.start()
            btnPlay.text = "إيقاف"
        }
        startProgressUpdater()
    }

    // ============================================================
    // تحديث شريط التقدم
    // ============================================================
    private fun startProgressUpdater() {
        stopProgressUpdater()
        progressUpdater = object : Runnable {
            override fun run() {
                try {
                    val duration = videoView.duration
                    val position = videoView.currentPosition
                    if (duration > 0) {
                        val progress = (position.toLong() * 1000L / duration).toInt()
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
        try {
            if (videoView.isPlaying) videoView.stopPlayback()
            videoView.suspend()
        } catch (_: Exception) {
        }
    }

    val getCurrentPath: String?
        get() = currentPath

    // ============================================================
    // أدوات
    // ============================================================
    private fun showError(msg: String) {
        infoView.text = msg
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
}