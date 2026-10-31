package com.flutteride.codweva.editor

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.Log
import android.view.ActionMode
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Magnifier
import android.widget.OverScroller
import android.widget.Toast
import com.flutteride.codweva.EditorThemes
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * CodeView — محرك عرض محرر كود عالي الأداء.
 *
 * v3.4 — إصلاح Enter + indent:
 *   - IME_ACTION_NONE + IME_FLAG_NO_ENTER_ACTION (Enter = سطر جديد)
 *   - إصلاح مسافة الإزاحة (indent) بعد Enter
 *   - logging عند الحاجة
 *
 * v3.3 — تحسين التوافق مع لوحات المفاتيح (SwiftKey):
 *   - getExtractedText
 *   - requestCursorUpdates
 *   - buildCursorAnchorInfo
 *   - notifyCursorAnchorChanged
 *   - setComposingRegion
 *   - getCursorCapsMode
 *
 * v3.2 — إصلاح ظهور شريط اقتراحات IME.
 * v3.1 — إصلاح إظهار الكيبورد عند النقرة فقط.
 * v3.0 — دعم الثيمات.
 * v2.9 — تحديد الكل + تمرير سلس.
 */
class CodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "CodeView"

        private const val MAX_VISIBLE_LINES = 100
        private const val MIN_LINE_HEIGHT = 20f
        private const val CACHE_MAX_SIZE = 200

        private const val DEFAULT_BG = 0xFF0D1117.toInt()
        private const val DEFAULT_TEXT = 0xFFE6EDF3.toInt()
        private const val DEFAULT_CURSOR = 0xFF58A6FF.toInt()
        private const val DEFAULT_CURRENT_LINE = 0xFF161B22.toInt()
        private const val DEFAULT_SELECTION = 0x7758A6FF.toInt()
        private const val DEFAULT_HANDLE_COLOR = 0xFF4A9EFF.toInt()

        private const val HANDLE_HIDE_DELAY = 2000L

        private const val HANDLE_RADIUS_DP = 6f
        private const val HANDLE_TOUCH_RADIUS_DP = 24f
        private const val HANDLE_BOTTOM_OFFSET_DP = 1f

        private const val ACTION_COPY = 1001
        private const val ACTION_CUT = 1002
        private const val ACTION_PASTE = 1003
        private const val ACTION_SELECT_ALL = 1004

        private const val MAGNIFIER_SIZE_DP = 180
        private const val MAGNIFIER_CORNER_DP = 60f
        private const val MAGNIFIER_ELEVATION_DP = 8f
        private const val MAGNIFIER_ZOOM = 1.8f

        private const val AUTO_SCROLL_THRESHOLD_DP = 80f
        private const val AUTO_SCROLL_SPEED_DP = 5f
        private const val AUTO_SCROLL_INTERVAL_MS = 35L
        private const val AUTO_SCROLL_MAX_DISTANCE_FACTOR = 3f
        private const val AUTO_SCROLL_ACCELERATION_EXPONENT = 1.5

        private const val SELECT_ALL_SCROLL_DURATION_MS = 500

        private const val TAB_SIZE = 4
    }

    // ============================================================
    // نموذج النص
    // ============================================================
    private val textBuffer = TextBuffer()

    // ============================================================
    // Lexer
    // ============================================================
    private var lexer: CodeLexer? = null
    private var highlightEnabled: Boolean = true

    // ============================================================
    // الخطوط
    // ============================================================
    private val monospaceTypeface: Typeface = Typeface.MONOSPACE
    private val unicodeTypeface: Typeface = try {
        Typeface.SANS_SERIF
    } catch (_: Exception) {
        Typeface.DEFAULT
    }

    // ============================================================
    // الدهانات
    // ============================================================
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = monospaceTypeface
        textSize = 14f * context.resources.displayMetrics.density
        color = DEFAULT_TEXT
        isElegantTextHeight = true
        fontFeatureSettings = "'liga' 1, 'calt' 1, 'kern' 1"
    }

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEFAULT_CURSOR
        strokeWidth = 2f * context.resources.displayMetrics.density
        style = Paint.Style.STROKE
    }

    private val currentLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEFAULT_CURRENT_LINE
        style = Paint.Style.FILL
    }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEFAULT_SELECTION
        style = Paint.Style.FILL
    }

    private val startHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEFAULT_HANDLE_COLOR
        style = Paint.Style.FILL
        setShadowLayer(dp(2f), 0f, dp(1f), 0x40000000.toInt())
    }

    private val endHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEFAULT_HANDLE_COLOR
        style = Paint.Style.FILL
        setShadowLayer(dp(2f), 0f, dp(1f), 0x40000000.toInt())
    }

    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val measurePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * context.resources.displayMetrics.density
        isElegantTextHeight = true
        fontFeatureSettings = "'liga' 1, 'calt' 1, 'kern' 1"
    }

    // ============================================================
    // الحالة
    // ============================================================
    private var currentBackground: Int = DEFAULT_BG

    // ============================================================
    // cache
    // ============================================================
    private val lineCache = object : LinkedHashMap<Int, CachedLayout>(CACHE_MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, CachedLayout>?): Boolean {
            return size > CACHE_MAX_SIZE
        }
    }

    private val coloredLayoutCache = object : LinkedHashMap<Int, ColoredCachedLayout>(CACHE_MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ColoredCachedLayout>?): Boolean {
            return size > CACHE_MAX_SIZE
        }
    }

    private class CachedLayout(
        val text: String,
        val layout: StaticLayout
    )

    private class ColoredCachedLayout(
        val text: String,
        val tokensHash: Int,
        val layout: StaticLayout
    )

    // ============================================================
    // الحالة
    // ============================================================
    private var totalLines: Int = 1
    private var lineHeight: Float = 0f
    private var textPaddingLeft: Float = 0f
    private var textPaddingTop: Float = 0f

    private var cursorLine: Int = 0
    private var cursorColumn: Int = 0
    private var cursorVisible: Boolean = true

    private var selectionStartLine: Int = -1
    private var selectionStartColumn: Int = -1
    private var selectionEndLine: Int = -1
    private var selectionEndColumn: Int = -1

    private var scrollX: Float = 0f
    private var scrollY: Float = 0f
    private var maxScrollX: Float = 0f
    private var maxScrollY: Float = 0f

    private var contentWidth: Float = 0f
    private var contentHeight: Float = 0f

    private var startHandleX: Float = 0f
    private var startHandleY: Float = 0f
    private var endHandleX: Float = 0f
    private var endHandleY: Float = 0f
    private var handlesVisible: Boolean = false

    private val handleHideHandler = Handler(Looper.getMainLooper())
    private var handleHideRunnable: Runnable? = null

    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maximumVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    private val gestureDetector = GestureDetector(context, GestureListener())
    private var isBeingDragged = false
    private var lastTouchY = 0f
    private var lastTouchX = 0f
    private var downX = 0f
    private var downY = 0f

    private var isSelecting = false
    private var isDraggingStartHandle = false
    private var isDraggingEndHandle = false

    private var actionMode: ActionMode? = null
    private var actionModeCallback: ActionMode.Callback? = null

    private var magnifier: Magnifier? = null

    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private var autoScrollRunnable: Runnable? = null
    private var isAutoScrolling = false
    private var lastAutoScrollY = 0f

    // ============================================================
    // Listener Interface
    // ============================================================
    interface Listener {
        fun onTextChanged()
        fun onCursorChanged(line: Int, column: Int)
        fun onScrollChanged(x: Float, y: Float)
        fun onFocusChanged(hasFocus: Boolean)
    }

    private val listeners = mutableListOf<Listener>()

    fun addListener(listener: Listener?) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: Listener?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    @Deprecated("Use addListener instead")
    fun setListener(listener: Listener?) {
        listeners.clear()
        if (listener != null) {
            listeners.add(listener)
        }
    }

    private fun notifyTextChanged() {
        for (l in listeners) {
            try {
                l.onTextChanged()
            } catch (_: Exception) {
            }
        }
    }

    private fun notifyCursorChanged(line: Int, column: Int) {
        for (l in listeners) {
            try {
                l.onCursorChanged(line, column)
            } catch (_: Exception) {
            }
        }

        // v3.3: إعلام IME بموقع المؤشر الجديد
        notifyCursorAnchorChanged()
    }

    private fun notifyScrollChanged(x: Float, y: Float) {
        for (l in listeners) {
            try {
                l.onScrollChanged(x, y)
            } catch (_: Exception) {
            }
        }
    }

    private fun notifyFocusChanged(hasFocus: Boolean) {
        for (l in listeners) {
            try {
                l.onFocusChanged(hasFocus)
            } catch (_: Exception) {
            }
        }
    }

    // ============================================================
    // اختيار الخط
    // ============================================================
    private fun needsUnicodeFallback(text: String): Boolean {
        val limit = minOf(30, text.length)
        for (i in 0 until limit) {
            val code = text[i].code

            if (code < 0x80) continue
            if (code < 0x100) continue
            if (code < 0x250) continue
            if (code in 0x2000..0x206F) continue
            if (code in 0x20A0..0x20CF) continue
            if (code in 0x2100..0x214F) continue
            if (code in 0x2190..0x21FF) continue
            if (code in 0x2200..0x22FF) continue
            if (code in 0x25A0..0x25FF) continue
            if (code in 0x2600..0x26FF) continue
            if (code in 0x2700..0x27BF) continue

            if (code in 0x0600..0x06FF) return true
            if (code in 0x0750..0x077F) return true
            if (code in 0xFB50..0xFDFF) return true
            if (code in 0xFE70..0xFEFF) return true

            if (code in 0x0590..0x05FF) return true
            if (code in 0x3000..0x9FFF) return true
            if (code in 0xAC00..0xD7AF) return true

            if (code >= 0x0250) return true
        }
        return false
    }

    private fun pickTypeface(text: String): Typeface {
        return if (needsUnicodeFallback(text)) {
            unicodeTypeface
        } else {
            monospaceTypeface
        }
    }

    private fun measureText(text: String): Float {
        if (text.isEmpty()) return 0f
        measurePaint.textSize = textPaint.textSize
        measurePaint.typeface = pickTypeface(text)
        return measurePaint.measureText(text)
    }

    private fun measureTextRange(text: String, start: Int, end: Int): Float {
        if (text.isEmpty() || start >= end) return 0f
        measurePaint.textSize = textPaint.textSize
        measurePaint.typeface = pickTypeface(text)
        return measurePaint.measureText(text, start, end)
    }

    private fun getTextDirection(text: String): TextDirectionHeuristic {
        return TextDirectionHeuristics.LTR
    }

    // ============================================================
    // الحالة الداخلية
    // ============================================================
    private var suppressBufferCallback = false
    private var inputConnection: CodeInputConnection? = null
    private var composingStartOffset = -1
    private var composingEndOffset = -1

    // v3.3: تتبع وضع تحديثات المؤشر
    private var lastCursorUpdateMode = 0

    // ============================================================
    // المُنشئ
    // ============================================================
    init {
        setBackgroundColor(DEFAULT_BG)
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        isLongClickable = true
        isHapticFeedbackEnabled = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }

        recalculateLineHeight()

        textBuffer.addListener(object : TextBuffer.Listener {
            override fun onTextChanged(offset: Int, oldLength: Int, newLength: Int) {
                if (suppressBufferCallback) return
                onBufferChanged()
            }

            override fun onTextReloaded() {
                onBufferReloaded()
            }
        })

        scheduleHandleHide()
    }

    // ============================================================
    // applyTheme
    // ============================================================
    fun applyTheme(theme: EditorThemes.Theme) {
        try {
            textPaint.color = theme.defaultText
            measurePaint.color = theme.defaultText
            cursorPaint.color = theme.keyword
            currentLinePaint.color = blendColors(
                theme.background,
                theme.defaultText,
                0.05f
            )
            selectionPaint.color = withAlpha(theme.keyword, 0x55)

            startHandlePaint.color = theme.function
            endHandlePaint.color = theme.function

            currentBackground = theme.background
            setBackgroundColor(theme.background)

            lineCache.clear()
            coloredLayoutCache.clear()

            invalidate()

            Log.d(TAG, "applyTheme: applied ${theme.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "applyTheme failed: ${e.message}", e)
        }
    }

    private fun blendColors(base: Int, overlay: Int, ratio: Float): Int {
        val r = (ratio.coerceIn(0f, 1f))
        val br = (base shr 16) and 0xFF
        val bg = (base shr 8) and 0xFF
        val bb = base and 0xFF
        val ba = (base shr 24) and 0xFF

        val or = (overlay shr 16) and 0xFF
        val og = (overlay shr 8) and 0xFF
        val ob = overlay and 0xFF

        val nr = (br + (or - br) * r).toInt().coerceIn(0, 255)
        val ng = (bg + (og - bg) * r).toInt().coerceIn(0, 255)
        val nb = (bb + (ob - bb) * r).toInt().coerceIn(0, 255)

        return (ba shl 24) or (nr shl 16) or (ng shl 8) or nb
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    // ============================================================
    // Public API
    // ============================================================
    fun setText(text: String?) {
        textBuffer.setText(text ?: "")
    }

    fun getText(): String = textBuffer.toStringText()

    fun getTextContent(): String = textBuffer.toStringText()

    fun setTextSize(sizePx: Float) {
        textPaint.textSize = sizePx
        measurePaint.textSize = sizePx
        recalculateLineHeight()
        lineCache.clear()
        coloredLayoutCache.clear()
        recalculateContentSize()
        requestLayout()
        invalidate()
    }

    fun getTextSize(): Float = textPaint.textSize

    fun getTextBuffer(): TextBuffer = textBuffer

    fun getCursorLine(): Int = cursorLine
    fun getCursorColumn(): Int = cursorColumn

    val lineCount: Int
        get() = totalLines

    fun setLexer(lexer: CodeLexer?) {
        this.lexer = lexer
        lineCache.clear()
        coloredLayoutCache.clear()
        invalidate()
    }

    fun setHighlightEnabled(enabled: Boolean) {
        if (highlightEnabled == enabled) return
        highlightEnabled = enabled
        coloredLayoutCache.clear()
        invalidate()
    }

    fun isHighlightEnabled(): Boolean = highlightEnabled

    fun relexLine(lineNumber: Int) {
        val lx = lexer ?: return
        if (lineNumber < 0 || lineNumber >= totalLines) return

        lx.analyzeFrom(textBuffer, lineNumber)

        val iterator = coloredLayoutCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key >= lineNumber) iterator.remove()
        }

        invalidate()
    }

    fun getLineHeight(): Float = lineHeight
    fun getTextPaddingTop(): Float = textPaddingTop
    fun getTextPaddingLeft(): Float = textPaddingLeft

    fun setCursor(line: Int, column: Int) {
        stopSelectionActionMode()

        val safeLine = line.coerceIn(0, maxOf(0, totalLines - 1))
        val lineText = textBuffer.getLine(safeLine)
        val safeColumn = column.coerceIn(0, lineText.length)

        if (cursorLine == safeLine && cursorColumn == safeColumn) return

        cursorLine = safeLine
        cursorColumn = safeColumn
        cursorVisible = true

        clearSelection()
        ensureCursorVisible()
        invalidate()
        notifyCursorChanged(cursorLine, cursorColumn)
    }

    fun getSelectionText(): String {
        if (!hasSelection()) return ""
        val range = getNormalizedSelection()
        return extractSelectionText(range)
    }

    fun selectAll() {
        if (totalLines == 0) return

        val lastLine = totalLines - 1
        val lastCol = textBuffer.getLine(lastLine).length

        selectionStartLine = 0
        selectionStartColumn = 0
        selectionEndLine = lastLine
        selectionEndColumn = lastCol

        cursorLine = lastLine
        cursorColumn = lastCol

        val startY = scrollY.toInt()
        val endY = maxScrollY.toInt()
        if (startY != endY) {
            scroller.startScroll(
                scrollX.toInt(), startY,
                0, endY - startY,
                SELECT_ALL_SCROLL_DURATION_MS
            )
            postInvalidateOnAnimation()
        }

        handlesVisible = true
        updateHandlePositions()
        scheduleHandleHide()
        invalidate()
        notifyCursorChanged(cursorLine, cursorColumn)
    }

    // ============================================================
    // النسخ / القطع / اللصق
    // ============================================================
    fun copySelection() {
        if (!hasSelection()) {
            Toast.makeText(context, "لا يوجد نص محدد", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedText = getSelectionText()
        if (selectedText.isEmpty()) {
            Toast.makeText(context, "لا يوجد نص محدد", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            val clip = ClipData.newPlainText("Klencod", selectedText)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(
                context,
                "تم النسخ (${selectedText.length} حرف)",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.w(TAG, "copySelection failed: ${e.message}")
        }
    }

    fun cutSelection() {
        if (!hasSelection()) {
            Toast.makeText(context, "لا يوجد نص محدد", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedText = getSelectionText()
        if (selectedText.isEmpty()) return

        copySelection()

        val range = getNormalizedSelection()
        val startOffset = offsetOf(range.startLine, range.startColumn)
        val endOffset = offsetOf(range.endLine, range.endColumn)
        val length = endOffset - startOffset

        if (length > 0) {
            textBuffer.delete(startOffset, length)

            val pos = positionOf(startOffset)
            cursorLine = pos.first
            cursorColumn = pos.second

            clearSelection()
            ensureCursorVisible()
            invalidate()
            notifyTextChanged()
            notifyCursorChanged(cursorLine, cursorColumn)
        }
    }

    fun pasteFromClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager

            if (!clipboard.hasPrimaryClip()) {
                Toast.makeText(context, "الحافظة فارغة", Toast.LENGTH_SHORT).show()
                return
            }

            val clip = clipboard.primaryClip ?: return
            if (clip.itemCount == 0) {
                Toast.makeText(context, "الحافظة فارغة", Toast.LENGTH_SHORT).show()
                return
            }

            val text = clip.getItemAt(0).coerceToText(context).toString()
            if (text.isEmpty()) {
                Toast.makeText(context, "الحافظة فارغة", Toast.LENGTH_SHORT).show()
                return
            }

            replaceSelection(text)

            Toast.makeText(
                context,
                "تم اللصق (${text.length} حرف)",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.w(TAG, "pasteFromClipboard failed: ${e.message}")
        }
    }

    // ============================================================
    // Magnifier
    // ============================================================
    private fun startMagnifier(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (magnifier != null) return

        try {
            magnifier = Magnifier.Builder(this)
                .setSize(
                    dp(MAGNIFIER_SIZE_DP).toInt(),
                    dp(MAGNIFIER_SIZE_DP).toInt()
                )
                .setCornerRadius(dp(MAGNIFIER_CORNER_DP))
                .setElevation(dp(MAGNIFIER_ELEVATION_DP))
                .setInitialZoom(MAGNIFIER_ZOOM)
                .build()

            magnifier?.show(x, y)
        } catch (e: Exception) {
            Log.w(TAG, "startMagnifier failed: ${e.message}")
            magnifier = null
        }
    }

    private fun updateMagnifier(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            magnifier?.show(x, y)
        } catch (_: Exception) {
        }
    }

    private fun hideMagnifier() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            magnifier?.dismiss()
        } catch (_: Exception) {
        }
        magnifier = null
    }

    // ============================================================
    // Auto-Scroll
    // ============================================================
    private fun checkAutoScroll(y: Float) {
        val threshold = dp(AUTO_SCROLL_THRESHOLD_DP)
        val viewHeight = height.toFloat()

        val shouldScroll = y < threshold || y > viewHeight - threshold

        if (shouldScroll && !isAutoScrolling) {
            lastAutoScrollY = y
            isAutoScrolling = true
            startAutoScrollLoop()
        } else if (shouldScroll && isAutoScrolling) {
            lastAutoScrollY = y
        } else if (!shouldScroll && isAutoScrolling) {
            stopAutoScrollLoop()
        }
    }

    private fun startAutoScrollLoop() {
        stopAutoScrollLoop()
        isAutoScrolling = true

        val baseSpeed = dp(AUTO_SCROLL_SPEED_DP)

        val runnable = object : Runnable {
            override fun run() {
                if (!isAutoScrolling) return

                val threshold = dp(AUTO_SCROLL_THRESHOLD_DP)
                val viewHeight = height.toFloat()
                val y = lastAutoScrollY

                val direction: Float
                when {
                    y < threshold -> direction = -1f
                    y > viewHeight - threshold -> direction = 1f
                    else -> {
                        isAutoScrolling = false
                        return
                    }
                }

                val distanceFactor: Float
                if (direction < 0) {
                    val distanceFromTop = threshold - y
                    distanceFactor = (distanceFromTop / threshold)
                        .coerceIn(0f, AUTO_SCROLL_MAX_DISTANCE_FACTOR)
                } else {
                    val distanceFromBottom = y - (viewHeight - threshold)
                    distanceFactor = (distanceFromBottom / threshold)
                        .coerceIn(0f, AUTO_SCROLL_MAX_DISTANCE_FACTOR)
                }

                val acceleration = 1f + Math.pow(
                    distanceFactor.toDouble(),
                    AUTO_SCROLL_ACCELERATION_EXPONENT
                ).toFloat()

                val actualSpeed = baseSpeed * acceleration

                val newScrollY = (scrollY + direction * actualSpeed)
                    .coerceIn(0f, maxScrollY)

                if (newScrollY != scrollY) {
                    scrollY = newScrollY
                    updateCursorFromAutoScroll(y)

                    if (hasSelection() || handlesVisible) {
                        updateHandlePositions()
                    }

                    invalidate()

                    notifyScrollChanged(scrollX, scrollY)
                }

                if (isAutoScrolling) {
                    autoScrollHandler.postDelayed(this, AUTO_SCROLL_INTERVAL_MS)
                }
            }
        }

        autoScrollRunnable = runnable
        autoScrollHandler.post(runnable)
    }

    private fun updateCursorFromAutoScroll(screenY: Float) {
        val contentY = screenY + scrollY - textPaddingTop

        var line = (contentY / lineHeight).toInt()
        line = line.coerceIn(0, maxOf(0, totalLines - 1))

        val lineText = textBuffer.getLine(line)
        val currentCol = cursorColumn.coerceIn(0, lineText.length)

        if (isDraggingStartHandle) {
            selectionStartLine = line
            selectionStartColumn = currentCol.coerceIn(0, lineText.length)
        } else if (isDraggingEndHandle) {
            selectionEndLine = line
            selectionEndColumn = currentCol.coerceIn(0, lineText.length)
        } else if (isSelecting) {
            selectionEndLine = line
            selectionEndColumn = currentCol.coerceIn(0, lineText.length)
        }

        cursorLine = line
        cursorColumn = currentCol.coerceIn(0, lineText.length)

        updateHandlePositions()
        notifyCursorChanged(cursorLine, cursorColumn)
    }

    private fun stopAutoScrollLoop() {
        autoScrollRunnable?.let { autoScrollHandler.removeCallbacks(it) }
        autoScrollRunnable = null
        isAutoScrolling = false
    }

    // ============================================================
    // ActionMode
    // ============================================================
    private fun startSelectionActionMode() {
        if (actionMode != null) return
        if (!hasSelection()) return

        handlesVisible = true
        updateHandlePositions()
        scheduleHandleHide()

        actionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, ACTION_COPY, 0, "نسخ")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.add(0, ACTION_CUT, 1, "قطع")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.add(0, ACTION_PASTE, 2, "لصق")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.add(0, ACTION_SELECT_ALL, 3, "تحديد الكل")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager
                val hasClipboardText = clipboard.hasPrimaryClip() &&
                        clipboard.primaryClipDescription
                            ?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true

                menu.findItem(ACTION_PASTE)?.isVisible = hasClipboardText
                menu.findItem(ACTION_CUT)?.isVisible = hasSelection()
                menu.findItem(ACTION_COPY)?.isVisible = hasSelection()
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    ACTION_COPY -> {
                        copySelection()
                        mode.finish()
                        return true
                    }
                    ACTION_CUT -> {
                        cutSelection()
                        mode.finish()
                        return true
                    }
                    ACTION_PASTE -> {
                        pasteFromClipboard()
                        mode.finish()
                        return true
                    }
                    ACTION_SELECT_ALL -> {
                        selectAll()
                        mode.invalidate()
                        return true
                    }
                }
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                actionMode = null
                invalidate()
            }
        }

        actionMode = startActionMode(actionModeCallback!!)
    }

    private fun stopSelectionActionMode() {
        actionMode?.finish()
        actionMode = null
    }

    // ============================================================
    // حساب الأبعاد
    // ============================================================
    private fun recalculateLineHeight() {
        val fm = textPaint.fontMetrics
        val baseHeight = fm.descent - fm.ascent
        lineHeight = maxOf(MIN_LINE_HEIGHT, baseHeight + dp(6))
        textPaddingLeft = dp(8)
        textPaddingTop = dp(4)
    }

    private fun recalculateContentSize() {
        if (totalLines <= 0) {
            contentWidth = 0f
            contentHeight = 0f
            maxScrollX = 0f
            maxScrollY = 0f
            return
        }

        contentHeight = totalLines * lineHeight

        val lines = textBuffer.getAllLines()

        var maxWidth = 0f
        val step = max(1, lines.size / 100)
        var i = 0
        while (i < lines.size) {
            val w = measureText(lines[i])
            if (w > maxWidth) maxWidth = w
            i += step
        }

        contentWidth = maxWidth + textPaddingLeft * 2 + dp(64)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        maxScrollX = max(0f, contentWidth - viewWidth)
        maxScrollY = max(0f, contentHeight - viewHeight)

        scrollX = scrollX.coerceIn(0f, maxScrollX)
        scrollY = scrollY.coerceIn(0f, maxScrollY)
    }

    override fun getLayoutDirection(): Int = LAYOUT_DIRECTION_LTR

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        recalculateContentSize()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateContentSize()
        if (hasSelection()) {
            updateHandlePositions()
        }
    }

    // ============================================================
    // onDraw
    // ============================================================
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(currentBackground)

        if (totalLines <= 0) {
            drawCursor(canvas)
            return
        }

        val firstVisibleLine = max(0, (scrollY / lineHeight).toInt())
        val lastVisibleLine = min(
            totalLines - 1,
            ((scrollY + height) / lineHeight).toInt() + 1
        )

        if (cursorLine in firstVisibleLine..lastVisibleLine) {
            drawCurrentLine(canvas, cursorLine)
        }

        val saveCount = canvas.save()
        canvas.translate(-scrollX, -scrollY)

        drawSelection(canvas, firstVisibleLine, lastVisibleLine)
        drawSelectionBorders(canvas, firstVisibleLine, lastVisibleLine)

        for (lineNumber in firstVisibleLine..lastVisibleLine) {
            drawLineWithHighlight(canvas, lineNumber)
        }

        canvas.restoreToCount(saveCount)

        if (handlesVisible && hasSelection()) {
            computeHandlePositions()
            drawHandles(canvas)
        }

        drawCursor(canvas)

        notifyScrollChanged(scrollX, scrollY)
    }

    // ============================================================
    // رسم سطر
    // ============================================================
    private fun drawLineWithHighlight(canvas: Canvas, lineNumber: Int) {
        if (lineNumber < 0 || lineNumber >= totalLines) return

        val lineText = textBuffer.getLine(lineNumber)
        if (lineText.isEmpty()) return

        val lx = lexer

        if (lx == null || !highlightEnabled) {
            val layout = getOrCreateLayout(lineNumber, lineText) ?: return
            val y = lineNumber * lineHeight + textPaddingTop
            val save = canvas.save()
            canvas.translate(textPaddingLeft, y)
            layout.draw(canvas)
            canvas.restoreToCount(save)
            return
        }

        val tokens = lx.getTokens(lineNumber)

        if (tokens.isEmpty()) {
            val layout = getOrCreateLayout(lineNumber, lineText) ?: return
            val y = lineNumber * lineHeight + textPaddingTop
            val save = canvas.save()
            canvas.translate(textPaddingLeft, y)
            layout.draw(canvas)
            canvas.restoreToCount(save)
            return
        }

        val coloredLayout = getOrCreateColoredLayout(lineNumber, lineText, tokens)
        if (coloredLayout == null) {
            val layout = getOrCreateLayout(lineNumber, lineText) ?: return
            val y = lineNumber * lineHeight + textPaddingTop
            val save = canvas.save()
            canvas.translate(textPaddingLeft, y)
            layout.draw(canvas)
            canvas.restoreToCount(save)
            return
        }

        val y = lineNumber * lineHeight + textPaddingTop
        val save = canvas.save()
        canvas.translate(textPaddingLeft, y)
        coloredLayout.draw(canvas)
        canvas.restoreToCount(save)
    }

    // ============================================================
    // Layout
    // ============================================================
    private fun getOrCreateLayout(lineNumber: Int, text: String): StaticLayout? {
        val cached = lineCache[lineNumber]
        if (cached != null && cached.text == text) return cached.layout

        val paint = TextPaint(textPaint)
        paint.typeface = pickTypeface(text)

        return try {
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, Int.MAX_VALUE)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .setTextDirection(getTextDirection(text))
                .build()

            lineCache[lineNumber] = CachedLayout(text, layout)
            layout
        } catch (e: Exception) {
            Log.w(TAG, "Layout failed for line $lineNumber: ${e.message}")
            null
        }
    }

    private fun getOrCreateColoredLayout(
        lineNumber: Int,
        text: String,
        tokens: List<CodeLexer.Token>
    ): StaticLayout? {
        val lx = lexer ?: return null

        var hash = 17
        for (t in tokens) {
            hash = hash * 31 + t.type
            hash = hash * 31 + t.start
            hash = hash * 31 + t.end
        }

        val cached = coloredLayoutCache[lineNumber]
        if (cached != null && cached.text == text && cached.tokensHash == hash) {
            return cached.layout
        }

        val spannable = SpannableString(text)

        for (token in tokens) {
            val safeStart = token.start.coerceIn(0, text.length)
            val safeEnd = token.end.coerceIn(safeStart, text.length)
            if (safeStart == safeEnd) continue

            val color = lx.getColorForToken(token.type)
            spannable.setSpan(
                ForegroundColorSpan(color),
                safeStart, safeEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val paint = TextPaint(textPaint)
        paint.typeface = pickTypeface(text)

        return try {
            val layout = StaticLayout.Builder
                .obtain(spannable, 0, spannable.length, paint, Int.MAX_VALUE)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .setTextDirection(getTextDirection(text))
                .build()

            coloredLayoutCache[lineNumber] = ColoredCachedLayout(text, hash, layout)
            layout
        } catch (e: Exception) {
            Log.w(TAG, "Colored layout failed for line $lineNumber: ${e.message}")
            null
        }
    }

    // ============================================================
    // الرسم العام
    // ============================================================
    private fun drawCurrentLine(canvas: Canvas, lineNumber: Int) {
        val y = lineNumber * lineHeight - scrollY
        canvas.drawRect(0f, y, width.toFloat(), y + lineHeight, currentLinePaint)
    }

    private fun drawSelection(canvas: Canvas, firstVisible: Int, lastVisible: Int) {
        if (!hasSelection()) return

        val range = getNormalizedSelection()
        val start = max(range.startLine, firstVisible)
        val end = min(range.endLine, lastVisible)

        for (lineNumber in start..end) {
            val lineText = textBuffer.getLine(lineNumber)

            val fromCol = if (lineNumber == range.startLine) range.startColumn else 0
            val toCol = if (lineNumber == range.endLine) range.endColumn else lineText.length

            if (fromCol >= toCol) continue

            val safeFrom = fromCol.coerceIn(0, lineText.length)
            val safeTo = toCol.coerceIn(safeFrom, lineText.length)

            val startX = textPaddingLeft + measureTextRange(lineText, 0, safeFrom)
            val endX = textPaddingLeft + measureTextRange(lineText, 0, safeTo)

            val y = lineNumber * lineHeight + textPaddingTop

            canvas.drawRect(startX, y, endX, y + lineHeight, selectionPaint)
        }
    }

    private fun drawSelectionBorders(canvas: Canvas, firstVisible: Int, lastVisible: Int) {
        if (!hasSelection()) return

        val range = getNormalizedSelection()

        if (range.startLine in firstVisible..lastVisible) {
            val lineText = textBuffer.getLine(range.startLine)
            val safeCol = range.startColumn.coerceIn(0, lineText.length)

            val x = textPaddingLeft + measureTextRange(lineText, 0, safeCol)
            val y = range.startLine * lineHeight + textPaddingTop

            canvas.drawLine(x, y, x, y + lineHeight, cursorPaint)
        }

        if (range.endLine in firstVisible..lastVisible) {
            val lineText = textBuffer.getLine(range.endLine)
            val safeCol = range.endColumn.coerceIn(0, lineText.length)

            val x = textPaddingLeft + measureTextRange(lineText, 0, safeCol)
            val y = range.endLine * lineHeight + textPaddingTop

            canvas.drawLine(x, y, x, y + lineHeight, cursorPaint)
        }
    }

    private fun drawCursor(canvas: Canvas) {
        if (!hasFocus() || !cursorVisible) return
        if (hasSelection()) return

        val lineText = textBuffer.getLine(cursorLine)
        val safeCol = cursorColumn.coerceIn(0, lineText.length)

        val cursorX = textPaddingLeft + measureTextRange(lineText, 0, safeCol) - scrollX
        val cursorY = cursorLine * lineHeight + textPaddingTop - scrollY

        if (cursorY < -lineHeight || cursorY > height + lineHeight) return

        canvas.drawLine(
            cursorX, cursorY,
            cursorX, cursorY + lineHeight - dp(4),
            cursorPaint
        )
    }

    // ============================================================
    // رسم المقابض
    // ============================================================
    private fun drawHandles(canvas: Canvas) {
        drawHandleCircle(canvas, startHandleX, startHandleY, startHandlePaint)
        drawHandleCircle(canvas, endHandleX, endHandleY, endHandlePaint)
    }

    private fun drawHandleCircle(canvas: Canvas, cx: Float, cy: Float, paint: Paint) {
        val radius = dp(HANDLE_RADIUS_DP)

        canvas.drawCircle(cx, cy, radius, paint)
        canvas.drawCircle(cx, cy, radius, handleBorderPaint)
    }

    // ============================================================
    // التحديد
    // ============================================================
    private fun hasSelection(): Boolean {
        return selectionStartLine >= 0 &&
                selectionEndLine >= 0 &&
                (selectionStartLine != selectionEndLine ||
                        selectionStartColumn != selectionEndColumn)
    }

    private fun clearSelection() {
        selectionStartLine = -1
        selectionStartColumn = -1
        selectionEndLine = -1
        selectionEndColumn = -1
        handlesVisible = false
    }

    private fun getNormalizedSelection(): SelectionRange {
        val aLine = selectionStartLine
        val aCol = selectionStartColumn
        val bLine = selectionEndLine
        val bCol = selectionEndColumn

        return if (aLine < bLine || (aLine == bLine && aCol <= bCol)) {
            SelectionRange(aLine, aCol, bLine, bCol)
        } else {
            SelectionRange(bLine, bCol, aLine, aCol)
        }
    }

    private fun extractSelectionText(range: SelectionRange): String {
        if (range.startLine == range.endLine) {
            val line = textBuffer.getLine(range.startLine)
            val from = range.startColumn.coerceIn(0, line.length)
            val to = range.endColumn.coerceIn(from, line.length)
            return line.substring(from, to)
        }

        val sb = StringBuilder()
        val firstLine = textBuffer.getLine(range.startLine)
        val from = range.startColumn.coerceIn(0, firstLine.length)
        sb.append(firstLine.substring(from)).append('\n')

        for (i in (range.startLine + 1) until range.endLine) {
            sb.append(textBuffer.getLine(i)).append('\n')
        }

        val lastLine = textBuffer.getLine(range.endLine)
        val to = range.endColumn.coerceIn(0, lastLine.length)
        sb.append(lastLine.substring(0, to))

        return sb.toString()
    }

    private fun updateHandlePositions() {
        if (!hasSelection()) {
            handlesVisible = false
            return
        }

        computeHandlePositions()
        handlesVisible = true
    }

    private fun computeHandlePositions() {
        if (!hasSelection()) return

        val range = getNormalizedSelection()
        val radius = dp(HANDLE_RADIUS_DP)
        val offset = dp(HANDLE_BOTTOM_OFFSET_DP)

        val startLineText = textBuffer.getLine(range.startLine)
        val startCol = range.startColumn.coerceIn(0, startLineText.length)

        startHandleX = textPaddingLeft +
                measureTextRange(startLineText, 0, startCol) - scrollX
        startHandleY = range.startLine * lineHeight + lineHeight - scrollY
                + textPaddingTop + offset + radius

        val endLineText = textBuffer.getLine(range.endLine)
        val endCol = range.endColumn.coerceIn(0, endLineText.length)

        endHandleX = textPaddingLeft +
                measureTextRange(endLineText, 0, endCol) - scrollX
        endHandleY = range.endLine * lineHeight + lineHeight - scrollY
                + textPaddingTop + offset + radius
    }

    private fun scheduleHandleHide() {
        handleHideRunnable?.let { handleHideHandler.removeCallbacks(it) }

        val runnable = Runnable {
            if (hasSelection()) {
                scheduleHandleHide()
                return@Runnable
            }

            if (isDraggingStartHandle || isDraggingEndHandle || isSelecting) {
                scheduleHandleHide()
                return@Runnable
            }

            if (handlesVisible) {
                handlesVisible = false
                invalidate()
            }
        }

        handleHideRunnable = runnable
        handleHideHandler.postDelayed(runnable, HANDLE_HIDE_DELAY)
    }

    private data class SelectionRange(
        val startLine: Int,
        val startColumn: Int,
        val endLine: Int,
        val endColumn: Int
    )

    // ============================================================
    // IME — v3.4: توافق موسّع + إصلاح Enter
    // ============================================================
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        // inputType كامل — لتحديد الحقل كحقل نص عادي
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_NORMAL or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
                InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_ENABLE_TEXT_CONVERSION_SUGGESTIONS

        // v3.4: إصلاح Enter — يضيف سطراً جديداً
        // IME_ACTION_NONE يخبر IME أن لا يوجد action
        // IME_FLAG_NO_ENTER_ACTION يخبر IME أن Enter = newline
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_ACTION_NONE or
                EditorInfo.IME_FLAG_NO_ENTER_ACTION

        // موضع المؤشر الأولي
        val cursorOffset = offsetOf(cursorLine, cursorColumn)
        outAttrs.initialSelStart = cursorOffset
        outAttrs.initialSelEnd = cursorOffset

        // hintText بدل null — يُظهر الاقتراحات
        outAttrs.hintText = "اكتب الكود هنا"

        // label للحقل
        outAttrs.label = "محرر الكود"

        val ic = CodeInputConnection(this, true)
        inputConnection = ic
        return ic
    }

    override fun onCheckIsTextEditor(): Boolean = true

    private inner class CodeInputConnection(
        targetView: View,
        fullEditor: Boolean
    ) : BaseInputConnection(targetView, fullEditor) {

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (text == null) return false
            val value = text.toString()

            // v3.4: إذا كان Enter جديداً، أضف سطراً مع indent
            if (value == "\n" || value == "\r\n") {
                insertNewlineWithIndent()
                return true
            }

            if (hasComposingText()) {
                replaceTextRange(composingStartOffset, composingEndOffset, value)
                clearComposingText()
            } else {
                replaceSelection(value)
            }
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (text == null) return false
            val value = text.toString()
            val start: Int
            val end: Int
            if (hasComposingText()) {
                start = composingStartOffset
                end = composingEndOffset
            } else if (hasSelection()) {
                val range = getNormalizedSelection()
                start = offsetOf(range.startLine, range.startColumn)
                end = offsetOf(range.endLine, range.endColumn)
            } else {
                start = offsetOf(cursorLine, cursorColumn)
                end = start
            }
            replaceTextRange(start, end, value)
            composingStartOffset = start
            composingEndOffset = start + value.length
            return true
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean {
            composingStartOffset = start
            composingEndOffset = end
            return true
        }

        override fun finishComposingText(): Boolean {
            clearComposingText()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength > 0) deleteBefore(beforeLength)
            if (afterLength > 0) deleteAfter(afterLength)
            return true
        }

        override fun sendKeyEvent(event: KeyEvent?): Boolean {
            if (event == null) return false
            return handleKeyEvent(event)
        }

        override fun performEditorAction(editorAction: Int): Boolean {
            when (editorAction) {
                EditorInfo.IME_ACTION_NONE,
                EditorInfo.IME_ACTION_DONE,
                EditorInfo.IME_ACTION_GO,
                EditorInfo.IME_ACTION_SEND,
                EditorInfo.IME_ACTION_NEXT -> {
                    insertNewlineWithIndent()
                    return true
                }
            }
            return true
        }

        override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence? {
            if (length <= 0) return ""

            val cursorOffset = offsetOf(cursorLine, cursorColumn)
            val start = (cursorOffset - length).coerceAtLeast(0)

            return try {
                textBuffer.substring(start, cursorOffset)
            } catch (_: Exception) {
                val text = textBuffer.getLine(cursorLine)
                val before = (cursorColumn - length).coerceAtLeast(0)
                text.substring(before, cursorColumn.coerceAtMost(text.length))
            }
        }

        override fun getTextAfterCursor(length: Int, flags: Int): CharSequence? {
            if (length <= 0) return ""

            val cursorOffset = offsetOf(cursorLine, cursorColumn)
            val end = (cursorOffset + length).coerceAtMost(textBuffer.length)

            return try {
                textBuffer.substring(cursorOffset, end)
            } catch (_: Exception) {
                val text = textBuffer.getLine(cursorLine)
                val after = (cursorColumn + length).coerceAtMost(text.length)
                text.substring(cursorColumn.coerceAtMost(text.length), after)
            }
        }

        override fun getSelectedText(flags: Int): CharSequence? {
            return if (hasSelection()) {
                val range = getNormalizedSelection()
                extractSelectionText(range)
            } else {
                null
            }
        }

        override fun getCursorCapsMode(reqModes: Int): Int {
            return 0
        }

        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean {
            lastCursorUpdateMode = cursorUpdateMode

            if ((cursorUpdateMode and CURSOR_UPDATE_MONITOR) != 0) {
                notifyCursorAnchorChanged()
            }

            return true
        }

        override fun getExtractedText(
            request: ExtractedTextRequest?,
            flags: Int
        ): ExtractedText? {
            return try {
                val extracted = ExtractedText()
                extracted.text = textBuffer.toStringText()
                extracted.startOffset = 0

                if (hasSelection()) {
                    val range = getNormalizedSelection()
                    extracted.selectionStart = offsetOf(range.startLine, range.startColumn)
                    extracted.selectionEnd = offsetOf(range.endLine, range.endColumn)
                } else {
                    val cursorOffset = offsetOf(cursorLine, cursorColumn)
                    extracted.selectionStart = cursorOffset
                    extracted.selectionEnd = cursorOffset
                }

                extracted.partialStartOffset = -1
                extracted.partialEndOffset = -1
                extracted.flags = 0

                extracted
            } catch (_: Exception) {
                null
            }
        }
    }

    // ============================================================
    // v3.3: CursorAnchorInfo — موقع المؤشر على الشاشة
    // ============================================================
    private fun buildCursorAnchorInfo(): CursorAnchorInfo? {
        if (totalLines <= 0) return null

        return try {
            val builder = CursorAnchorInfo.Builder()

            val lineText = textBuffer.getLine(cursorLine)
            val safeCol = cursorColumn.coerceIn(0, lineText.length)

            val cursorX = textPaddingLeft + measureTextRange(lineText, 0, safeCol) - scrollX
            val cursorY = cursorLine * lineHeight + textPaddingTop - scrollY

            builder.setInsertionMarkerLocation(
                cursorX,
                cursorY,
                cursorY,
                cursorY + lineHeight,
                CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION
            )

            builder.build()
        } catch (_: Exception) {
            null
        }
    }

    private fun notifyCursorAnchorChanged() {
        if (!isFocused) return

        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as? InputMethodManager ?: return

            val info = buildCursorAnchorInfo() ?: return
            imm.updateCursorAnchorInfo(this, info)
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // التحرير
    // ============================================================
    private fun hasComposingText(): Boolean {
        return composingStartOffset >= 0 && composingEndOffset >= composingStartOffset
    }

    private fun clearComposingText() {
        composingStartOffset = -1
        composingEndOffset = -1
    }

    private fun replaceTextRange(startOffset: Int, endOffset: Int, newText: String) {
        val safeStart = startOffset.coerceIn(0, textBuffer.length)
        val safeEnd = endOffset.coerceIn(safeStart, textBuffer.length)
        val start = positionOf(safeStart)
        val end = positionOf(safeEnd)
        selectionStartLine = start.first
        selectionStartColumn = start.second
        selectionEndLine = end.first
        selectionEndColumn = end.second
        cursorLine = end.first
        cursorColumn = end.second
        replaceSelection(newText)
    }

    private fun replaceSelection(newText: String) {
        val offset = offsetOf(cursorLine, cursorColumn)

        if (hasSelection()) {
            val range = getNormalizedSelection()
            val startOffset = offsetOf(range.startLine, range.startColumn)
            val endOffset = offsetOf(range.endLine, range.endColumn)
            val length = endOffset - startOffset

            textBuffer.replace(startOffset, length, newText)

            val newPos = startOffset + newText.length
            val pos = positionOf(newPos)
            cursorLine = pos.first
            cursorColumn = pos.second

            clearSelection()
        } else {
            textBuffer.insert(offset, newText)

            val newPos = offset + newText.length
            val pos = positionOf(newPos)
            cursorLine = pos.first
            cursorColumn = pos.second
        }

        ensureCursorVisible()
        invalidate()
        notifyTextChanged()
        notifyCursorChanged(cursorLine, cursorColumn)
    }

    private fun deleteBefore(length: Int) {
        if (length <= 0) return
        if (hasSelection()) {
            replaceSelection("")
            return
        }

        val offset = offsetOf(cursorLine, cursorColumn)
        val from = (offset - length).coerceAtLeast(0)
        val actualLength = offset - from
        if (actualLength <= 0) return

        textBuffer.delete(from, actualLength)

        val pos = positionOf(from)
        cursorLine = pos.first
        cursorColumn = pos.second

        ensureCursorVisible()
        invalidate()
        notifyTextChanged()
        notifyCursorChanged(cursorLine, cursorColumn)
    }

    private fun deleteAfter(length: Int) {
        if (length <= 0) return
        if (hasSelection()) {
            replaceSelection("")
            return
        }

        val offset = offsetOf(cursorLine, cursorColumn)
        val end = (offset + length).coerceAtMost(textBuffer.length)
        val actualLength = end - offset
        if (actualLength <= 0) return

        textBuffer.delete(offset, actualLength)

        invalidate()
        notifyTextChanged()
    }

    private fun insertText(text: String) {
        replaceSelection(text)
    }

    /**
     * v3.4: إضافة سطر جديد مع indent تلقائي.
     *    - يحسب indent السطر الحالي
     *    - إذا انتهى السطر بـ `{` أو `(` أو `[`، يزيد الإزاحة
     *    - إذا بدأ بـ `}` أو `)` أو `]`، يقلل الإزاحة
     */
    private fun insertNewlineWithIndent() {
        val lineText = textBuffer.getLine(cursorLine)
        val safeCol = cursorColumn.coerceIn(0, lineText.length)

        // احسب indent السطر الحالي
        val currentIndent = extractIndent(lineText.substring(0, safeCol))

        // الحرف قبل المؤشر
        val charBefore = if (safeCol > 0) lineText[safeCol - 1] else ' '

        // الحرف بعد المؤشر (على نفس السطر)
        val charAfter = if (safeCol < lineText.length) lineText[safeCol] else ' '

        // إذا كان السطر ينتهي بـ { ( [ → indent إضافي
        val additionalIndent = if (charBefore == '{' || charBefore == '(' || charBefore == '[') {
            " ".repeat(TAB_SIZE)
        } else {
            ""
        }

        // إذا كان الحرف التالي } ) ] → السطر الجديد يعود للخلف
        // (يجب إدراج السطر الجديد قبل الإغلاق)
        val newIndent = if (charAfter == '}' || charAfter == ')' || charAfter == ']') {
            // العودة للخلف
            currentIndent.removeSuffix(" ".repeat(TAB_SIZE))
                .ifEmpty { "" }
        } else {
            currentIndent + additionalIndent
        }

        insertText("\n$newIndent")
    }

    private fun insertTab() {
        insertText(" ".repeat(TAB_SIZE))
    }

    private fun backspace() {
        if (hasSelection()) {
            replaceSelection("")
            return
        }
        deleteBefore(1)
    }

    private fun extractIndent(line: String): String {
        val sb = StringBuilder()
        for (c in line) {
            if (c == ' ' || c == '\t') sb.append(c) else break
        }
        return sb.toString()
    }

    // ============================================================
    // المواضع
    // ============================================================
    private fun offsetOf(line: Int, column: Int): Int {
        var offset = 0
        for (i in 0 until line) {
            offset += textBuffer.getLine(i).length + 1
        }
        offset += column.coerceAtLeast(0)
        return offset.coerceIn(0, textBuffer.length)
    }

    private fun positionOf(offset: Int): Pair<Int, Int> {
        val safeOffset = offset.coerceIn(0, textBuffer.length)
        var remaining = safeOffset
        var line = 0

        while (line < totalLines) {
            val lineText = textBuffer.getLine(line)
            val lineLen = lineText.length + 1
            if (remaining < lineLen) return Pair(line, remaining)
            remaining -= lineLen
            line++
        }

        val lastLine = (totalLines - 1).coerceAtLeast(0)
        return Pair(lastLine, textBuffer.getLine(lastLine).length)
    }

    private fun screenToPosition(x: Float, y: Float): Pair<Int, Int> {
        val contentX = x + scrollX - textPaddingLeft
        val contentY = y + scrollY - textPaddingTop

        var line = (contentY / lineHeight).toInt()
        line = line.coerceIn(0, maxOf(0, totalLines - 1))

        val lineText = textBuffer.getLine(line)

        measurePaint.textSize = textPaint.textSize
        measurePaint.typeface = pickTypeface(lineText)

        var column = 0
        var accumulatedWidth = 0f
        for (i in lineText.indices) {
            val charWidth = measurePaint.measureText(lineText, i, i + 1)
            if (accumulatedWidth + charWidth / 2f >= contentX) break
            accumulatedWidth += charWidth
            column++
        }

        return Pair(line, column.coerceIn(0, lineText.length))
    }

    private fun ensureCursorVisible() {
        val lineText = textBuffer.getLine(cursorLine)
        val safeCol = cursorColumn.coerceIn(0, lineText.length)

        val cursorX = textPaddingLeft + measureTextRange(lineText, 0, safeCol)
        val cursorY = cursorLine * lineHeight

        var newScrollX = scrollX
        var newScrollY = scrollY

        val visibleTop = scrollY
        val visibleBottom = scrollY + height - lineHeight

        if (cursorY < visibleTop) {
            newScrollY = cursorY
        } else if (cursorY > visibleBottom) {
            newScrollY = cursorY - height + lineHeight + dp(8)
        }

        val visibleLeft = scrollX
        val visibleRight = scrollX + width - dp(32)

        if (cursorX < visibleLeft) {
            newScrollX = cursorX - dp(8)
        } else if (cursorX > visibleRight) {
            newScrollX = cursorX - width + dp(32)
        }

        if (newScrollX != scrollX || newScrollY != scrollY) {
            scrollX = newScrollX.coerceIn(0f, maxScrollX)
            scrollY = newScrollY.coerceIn(0f, maxScrollY)
        }
    }

    // ============================================================
    // التمرير
    // ============================================================
    fun scrollTo(x: Float, y: Float) {
        val newX = x.coerceIn(0f, maxScrollX)
        val newY = y.coerceIn(0f, maxScrollY)

        if (newX != scrollX || newY != scrollY) {
            scrollX = newX
            scrollY = newY

            if (hasSelection() || handlesVisible) {
                updateHandlePositions()
            }

            invalidate()
            notifyScrollChanged(scrollX, scrollY)
        }
    }

    fun scrollBy(dx: Float, dy: Float) {
        scrollTo(scrollX + dx, scrollY + dy)
    }

    fun getScrollXPos(): Float = scrollX
    fun getScrollYPos(): Float = scrollY

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX.toFloat(), scroller.currY.toFloat())
            postInvalidateOnAnimation()
        }
    }

    // ============================================================
    // v3.1: onTouchEvent — لا يظهر الكيبورد عند التمرير
    // ============================================================
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                requestFocus()

                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                isBeingDragged = false
                isSelecting = false
                isDraggingStartHandle = false
                isDraggingEndHandle = false

                if (handlesVisible && hasSelection()) {
                    val touchRadius = dp(HANDLE_TOUCH_RADIUS_DP)
                    val distStart = distance(event.x, event.y, startHandleX, startHandleY)
                    val distEnd = distance(event.x, event.y, endHandleX, endHandleY)

                    if (distStart < touchRadius) {
                        isDraggingStartHandle = true
                        startMagnifier(event.x, event.y)
                        return true
                    }
                    if (distEnd < touchRadius) {
                        isDraggingEndHandle = true
                        startMagnifier(event.x, event.y)
                        return true
                    }
                }

                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)

                if (isDraggingStartHandle || isDraggingEndHandle) {
                    val pos = screenToPosition(event.x, event.y)
                    if (isDraggingStartHandle) {
                        selectionStartLine = pos.first
                        selectionStartColumn = pos.second
                    } else {
                        selectionEndLine = pos.first
                        selectionEndColumn = pos.second
                    }
                    cursorLine = pos.first
                    cursorColumn = pos.second

                    updateHandlePositions()
                    scheduleHandleHide()
                    invalidate()

                    updateMagnifier(event.x, event.y)
                    checkAutoScroll(event.y)

                    notifyCursorChanged(cursorLine, cursorColumn)
                    return true
                }

                if (isSelecting) {
                    val pos = screenToPosition(event.x, event.y)
                    selectionEndLine = pos.first
                    selectionEndColumn = pos.second
                    cursorLine = pos.first
                    cursorColumn = pos.second

                    ensureCursorVisible()
                    updateHandlePositions()
                    scheduleHandleHide()
                    invalidate()

                    checkAutoScroll(event.y)

                    notifyCursorChanged(cursorLine, cursorColumn)
                    return true
                }

                val dx = lastTouchX - event.x
                val dy = lastTouchY - event.y

                if (!isBeingDragged) {
                    val totalDx = abs(event.x - downX)
                    val totalDy = abs(event.y - downY)
                    if (totalDx > touchSlop || totalDy > touchSlop) {
                        isBeingDragged = true
                    }
                }

                if (isBeingDragged) {
                    scrollBy(dx, dy)
                    lastTouchX = event.x
                    lastTouchY = event.y
                }

                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingStartHandle || isDraggingEndHandle) {
                    isDraggingStartHandle = false
                    isDraggingEndHandle = false
                    velocityTracker?.recycle()
                    velocityTracker = null

                    hideMagnifier()
                    stopAutoScrollLoop()

                    if (hasSelection()) {
                        updateHandlePositions()
                        scheduleHandleHide()
                        invalidate()
                    }
                    return true
                }

                if (isSelecting) {
                    isSelecting = false
                    velocityTracker?.recycle()
                    velocityTracker = null

                    stopAutoScrollLoop()

                    if (hasSelection()) {
                        updateHandlePositions()
                        scheduleHandleHide()
                        invalidate()
                    }
                    return true
                }

                if (isBeingDragged) {
                    val vt = velocityTracker
                    if (vt != null) {
                        vt.computeCurrentVelocity(1000, maximumVelocity.toFloat())
                        val vx = vt.xVelocity.toInt()
                        val vy = vt.yVelocity.toInt()

                        if (abs(vx) > minimumVelocity || abs(vy) > minimumVelocity) {
                            scroller.fling(
                                scrollX.toInt(), scrollY.toInt(),
                                -vx, -vy,
                                0, maxScrollX.toInt(),
                                0, maxScrollY.toInt()
                            )
                            postInvalidateOnAnimation()
                        }
                    }
                }

                isBeingDragged = false
                velocityTracker?.recycle()
                velocityTracker = null

                if (hasSelection() || handlesVisible) {
                    updateHandlePositions()
                    invalidate()
                }

                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    // ============================================================
    // v3.1: GestureListener
    // ============================================================
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            showSoftKeyboard()

            val pos = screenToPosition(e.x, e.y)

            if (hasSelection() && handlesVisible) {
                clearSelection()
                cursorLine = pos.first
                cursorColumn = pos.second
            } else {
                cursorLine = pos.first
                cursorColumn = pos.second
                clearSelection()
            }

            ensureCursorVisible()
            invalidate()
            notifyCursorChanged(cursorLine, cursorColumn)
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean = true

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val pos = screenToPosition(e.x, e.y)
            val lineText = textBuffer.getLine(pos.first)

            var start = pos.second
            var end = pos.second

            while (start > 0 && isWordChar(lineText[start - 1])) start--
            while (end < lineText.length && isWordChar(lineText[end])) end++

            if (start == end) return false

            selectionStartLine = pos.first
            selectionStartColumn = start
            selectionEndLine = pos.first
            selectionEndColumn = end

            cursorLine = pos.first
            cursorColumn = end

            handlesVisible = true
            updateHandlePositions()
            scheduleHandleHide()
            invalidate()
            notifyCursorChanged(cursorLine, cursorColumn)

            startSelectionActionMode()

            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val pos = screenToPosition(e.x, e.y)

            if (hasSelection()) {
                startSelectionActionMode()
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                return
            }

            val lineText = textBuffer.getLine(pos.first)
            var start = pos.second
            var end = pos.second

            while (start > 0 && isWordChar(lineText[start - 1])) start--
            while (end < lineText.length && isWordChar(lineText[end])) end++

            if (start == end) {
                start = pos.second
                end = minOf(pos.second + 1, lineText.length)
            }

            selectionStartLine = pos.first
            selectionStartColumn = start
            selectionEndLine = pos.first
            selectionEndColumn = end

            cursorLine = pos.first
            cursorColumn = end

            isSelecting = true
            handlesVisible = true
            updateHandlePositions()
            scheduleHandleHide()
            invalidate()

            startSelectionActionMode()

            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean = false
    }

    private fun isWordChar(c: Char): Boolean {
        if (c.isLetterOrDigit()) return true
        if (c == '_') return true

        val code = c.code
        if (code in 0x0600..0x06FF) return true
        if (code in 0x0750..0x077F) return true
        if (code in 0xFB50..0xFDFF) return true
        if (code in 0xFE70..0xFEFF) return true

        return false
    }

    // ============================================================
    // المفاتيح
    // ============================================================
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                backspace()
                return true
            }
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                if (hasSelection()) replaceSelection("") else deleteAfter(1)
                return true
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                insertNewlineWithIndent()
                return true
            }
            KeyEvent.KEYCODE_TAB -> {
                insertTab()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveCursorLeft(event.isShiftPressed)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveCursorRight(event.isShiftPressed)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                moveCursorUp(event.isShiftPressed)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveCursorDown(event.isShiftPressed)
                return true
            }
            KeyEvent.KEYCODE_MOVE_HOME -> {
                setCursor(cursorLine, 0)
                return true
            }
            KeyEvent.KEYCODE_MOVE_END -> {
                val lineLen = textBuffer.getLine(cursorLine).length
                setCursor(cursorLine, lineLen)
                return true
            }
            KeyEvent.KEYCODE_A -> {
                if (event.isCtrlPressed) {
                    selectAll()
                    return true
                }
            }
            KeyEvent.KEYCODE_C -> {
                if (event.isCtrlPressed) {
                    copySelection()
                    return true
                }
            }
            KeyEvent.KEYCODE_X -> {
                if (event.isCtrlPressed) {
                    cutSelection()
                    return true
                }
            }
            KeyEvent.KEYCODE_V -> {
                if (event.isCtrlPressed) {
                    pasteFromClipboard()
                    return true
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            return onKeyDown(event.keyCode, event)
        }
        return false
    }

    private fun moveCursorLeft(extendSelection: Boolean) {
        val offset = offsetOf(cursorLine, cursorColumn)
        if (offset <= 0) return
        val pos = positionOf(offset - 1)

        if (extendSelection) {
            if (!hasSelection()) {
                selectionStartLine = cursorLine
                selectionStartColumn = cursorColumn
            }
            selectionEndLine = pos.first
            selectionEndColumn = pos.second
            handlesVisible = true
            updateHandlePositions()
            scheduleHandleHide()
        } else {
            clearSelection()
        }

        cursorLine = pos.first
        cursorColumn = pos.second
        ensureCursorVisible()
        invalidate()
        notifyCursorChanged(cursorLine, cursorColumn)
    }

    private fun moveCursorRight(extendSelection: Boolean) {
        val offset = offsetOf(cursorLine, cursorColumn)
        if (offset >= textBuffer.length) return
        val pos = positionOf(offset + 1)

        if (extendSelection) {
            if (!hasSelection()) {
                selectionStartLine = cursorLine
                selectionStartColumn = cursorColumn
            }
            selectionEndLine = pos.first
            selectionEndColumn = pos.second
            handlesVisible = true
            updateHandlePositions()
            scheduleHandleHide()
        } else {
            clearSelection()
        }

        cursorLine = pos.first
        cursorColumn = pos.second
        ensureCursorVisible()
        invalidate()
        notifyCursorChanged(cursorLine, cursorColumn)
    }

    private fun moveCursorUp(extendSelection: Boolean) {
        if (cursorLine <= 0) return
        moveCursorTo(cursorLine - 1, cursorColumn, extendSelection)
    }

    private fun moveCursorDown(extendSelection: Boolean) {
        if (cursorLine >= totalLines - 1) return
        moveCursorTo(cursorLine + 1, cursorColumn, extendSelection)
    }

    private fun moveCursorTo(line: Int, column: Int, extendSelection: Boolean) {
        val safeLine = line.coerceIn(0, maxOf(0, totalLines - 1))
        val lineText = textBuffer.getLine(safeLine)
        val safeCol = column.coerceIn(0, lineText.length)

        if (extendSelection) {
            if (!hasSelection()) {
                selectionStartLine = cursorLine
                selectionStartColumn = cursorColumn
            }
            selectionEndLine = safeLine
            selectionEndColumn = safeCol
            handlesVisible = true
            updateHandlePositions()
            scheduleHandleHide()
        } else {
            clearSelection()
        }

        cursorLine = safeLine
        cursorColumn = safeCol

        ensureCursorVisible()
        invalidate()
        notifyCursorChanged(cursorLine, cursorColumn)
    }

    // ============================================================
    // onBufferChanged
    // ============================================================
    private fun onBufferChanged() {
        totalLines = textBuffer.lineCount()
        lineCache.clear()
        coloredLayoutCache.clear()
        recalculateContentSize()

        try {
            val startLine = maxOf(0, cursorLine - 20)
            lexer?.analyzeFrom(textBuffer, startLine)
        } catch (_: Exception) {
        }

        cursorLine = cursorLine.coerceIn(0, maxOf(0, totalLines - 1))
        val maxCol = textBuffer.getLine(cursorLine).length
        cursorColumn = cursorColumn.coerceIn(0, maxCol)

        if (hasSelection()) {
            updateHandlePositions()
        }

        postInvalidateOnAnimation()
        notifyTextChanged()
    }

    private fun onBufferReloaded() {
        totalLines = textBuffer.lineCount()
        lineCache.clear()
        coloredLayoutCache.clear()
        cursorLine = 0
        cursorColumn = 0
        clearSelection()
        scrollX = 0f
        scrollY = 0f
        recalculateContentSize()

        try {
            lexer?.analyzeAll(textBuffer)
        } catch (_: Exception) {
        }

        invalidate()
        notifyTextChanged()
        notifyCursorChanged(cursorLine, cursorColumn)
    }

    // ============================================================
    // التركيز
    // ============================================================
    override fun onFocusChanged(gainedFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainedFocus, direction, previouslyFocusedRect)
        notifyFocusChanged(gainedFocus)

        if (gainedFocus) {
            cursorVisible = true

            // v3.3: إعلام IME بموقع المؤشر
            post { notifyCursorAnchorChanged() }

            postDelayed({
                if (hasFocus()) {
                    cursorVisible = false
                    invalidate()
                    postDelayed({
                        cursorVisible = true
                        invalidate()
                    }, 500)
                }
            }, 500)
        } else {
            stopSelectionActionMode()
            clearSelection()
            invalidate()
        }
    }

    fun showSoftKeyboard() {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        } catch (_: Exception) {
        }
    }

    fun hideSoftKeyboard() {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(windowToken, 0)
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // Public API
    // ============================================================
    fun insertTextAtCursor(text: String) {
        insertText(text)
    }

    fun undo() {
        // TODO
    }

    fun redo() {
        // TODO
    }

    // ============================================================
    // أدوات
    // ============================================================
    private fun dp(value: Int): Float = value * context.resources.displayMetrics.density
    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density

    fun cleanup() {
        stopSelectionActionMode()

        hideMagnifier()
        stopAutoScrollLoop()

        handleHideRunnable?.let { handleHideHandler.removeCallbacks(it) }
        handleHideRunnable = null

        try {
            velocityTracker?.recycle()
        } catch (_: Exception) {
        }
        velocityTracker = null

        lineCache.clear()
        coloredLayoutCache.clear()
        inputConnection = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanup()
    }
}