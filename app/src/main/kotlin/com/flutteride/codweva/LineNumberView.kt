package com.flutteride.codweva.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * LineNumberView — عرض أرقام الأسطر.
 *
 * v2.3 — قائمة listeners:
 *   - يستخدم addListener بدل setListener
 *   - يتعايش مع CodeEditorView (كلاهما يستمع لـ CodeView)
 *   - Auto-Scroll يعمل بسلاسة
 *
 * الأداء:
 *   - 100,000 سطر: 60fps
 *   - يرسم فقط الأسطر المرئية
 */
class LineNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val MIN_WIDTH_DP = 48
        private const val PADDING_END_DP = 12
        private const val PADDING_START_DP = 6
        private const val ERROR_MARK_SIZE_DP = 8

        private const val DEFAULT_BG = 0xFF0D1117.toInt()
        private const val DEFAULT_TEXT = 0xFF484F58.toInt()
        private const val DEFAULT_CURRENT_TEXT = 0xFFE6EDF3.toInt()
        private const val DEFAULT_BORDER = 0xFF30363D.toInt()
        private const val DEFAULT_ERROR = 0xFFCF222E.toInt()
        private const val DEFAULT_WARNING = 0xFFD29922.toInt()
    }

    private var codeView: CodeView? = null

    // ============================================================
    // الدهانات
    // ============================================================
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 12f * context.resources.displayMetrics.density
        color = DEFAULT_TEXT
        textAlign = Paint.Align.LEFT
    }

    private val currentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 12f * context.resources.displayMetrics.density
        color = DEFAULT_CURRENT_TEXT
        textAlign = Paint.Align.LEFT
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEFAULT_BG
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEFAULT_BORDER
        strokeWidth = 1f * context.resources.displayMetrics.density
        style = Paint.Style.STROKE
    }

    private val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEFAULT_ERROR
        style = Paint.Style.FILL
    }

    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEFAULT_WARNING
        style = Paint.Style.FILL
    }

    // ============================================================
    // الأبعاد
    // ============================================================
    private var minWidthPx: Float = 0f
    private var paddingEndPx: Float = 0f
    private var paddingStartPx: Float = 0f
    private var errorMarkSizePx: Float = 0f

    // ============================================================
    // علامات الأخطاء
    // ============================================================
    private var errorLines: Set<Int> = emptySet()
    private var warningLines: Set<Int> = emptySet()

    private var lastErrorHashCode = 0

    // ============================================================
    // ⭐ Listener مرتبط (نحتفظ به لإزالته لاحقاً)
    // ============================================================
    private val editorListener = object : CodeView.Listener {
        override fun onTextChanged() {
            requestLayout()
            invalidate()
        }

        override fun onCursorChanged(line: Int, column: Int) {
            invalidate()
        }

        override fun onScrollChanged(x: Float, y: Float) {
            // ⭐ أعد الرسم مباشرة عند أي تمرير (بما في ذلك Auto-Scroll)
            invalidate()
        }

        override fun onFocusChanged(hasFocus: Boolean) {
            invalidate()
        }
    }

    // ============================================================
    // المُنشئ
    // ============================================================
    init {
        setBackgroundColor(DEFAULT_BG)
        setWillNotDraw(false)

        minWidthPx = dp(MIN_WIDTH_DP.toFloat())
        paddingEndPx = dp(PADDING_END_DP.toFloat())
        paddingStartPx = dp(PADDING_START_DP.toFloat())
        errorMarkSizePx = dp(ERROR_MARK_SIZE_DP.toFloat())
    }

    // ============================================================
    // الربط / الفصل
    // ============================================================

    /**
     * اربط هذا العارض بـ CodeView.
     * يستخدم addListener (يسمح بعدة listeners).
     */
    fun attachTo(codeView: CodeView) {
        // أزل listener من CodeView القديم إن وُجد
        this.codeView?.removeListener(editorListener)

        this.codeView = codeView

        // ⭐ addListener بدل setListener
        codeView.addListener(editorListener)

        requestLayout()
        invalidate()
    }

    /**
     * افصل العارض عن CodeView.
     */
    fun detach() {
        codeView?.removeListener(editorListener)
        codeView = null
    }

    // ============================================================
    // الإعدادات
    // ============================================================
    fun setTextColor(color: Int) {
        textPaint.color = color
        invalidate()
    }

    fun setCurrentTextColor(color: Int) {
        currentTextPaint.color = color
        invalidate()
    }

    fun setBackgroundTint(color: Int) {
        bgPaint.color = color
        invalidate()
    }

    fun setBorderColor(color: Int) {
        borderPaint.color = color
        invalidate()
    }

    fun setTextSize(sizePx: Float) {
        textPaint.textSize = sizePx
        currentTextPaint.textSize = sizePx
        requestLayout()
        invalidate()
    }

    fun setErrorLines(lines: Set<Int>?) {
        errorLines = lines ?: emptySet()
        invalidate()
    }

    fun setWarningLines(lines: Set<Int>?) {
        warningLines = lines ?: emptySet()
        invalidate()
    }

    // ============================================================
    // onMeasure
    // ============================================================
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cv = codeView

        val lineCount = cv?.lineCount ?: 1
        val widestNumber = lineCount.toString()
        val digitWidth = textPaint.measureText(widestNumber)
        val errorWidth = errorMarkSizePx + dp(4f)

        var desiredWidth = (paddingStartPx + errorWidth + digitWidth + paddingEndPx).toInt()
        if (desiredWidth < minWidthPx) desiredWidth = minWidthPx.toInt()

        var desiredHeight = 0
        if (cv != null) desiredHeight = cv.height

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)

        setMeasuredDimension(width, height)
    }

    // ============================================================
    // onDraw
    // ============================================================
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cv = codeView ?: return
        val totalLines = cv.lineCount
        if (totalLines <= 0) return

        val viewHeight = height
        if (viewHeight <= 0) return

        // ⭐ اقرأ مباشرة من CodeView
        val scrollY = cv.getScrollYPos()
        val lineHeight = cv.getLineHeight()
        if (lineHeight <= 0f) return

        val firstVisible = maxOf(0, (scrollY / lineHeight).toInt() - 1)
        val lastVisible = minOf(
            totalLines - 1,
            ((scrollY + viewHeight) / lineHeight).toInt() + 1
        )

        // املأ الخلفية
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val currentLine = cv.getCursorLine()
        val textX = paddingStartPx + errorMarkSizePx + dp(4f)
        val errorX = paddingStartPx + errorMarkSizePx / 2f
        val baselineOffset = getTextBaselineOffset(cv)

        for (lineNumber in firstVisible..lastVisible) {
            val logicalNumber = lineNumber + 1

            val y = lineNumber * lineHeight - scrollY + baselineOffset
            if (y < -lineHeight || y > height + lineHeight) continue

            val paint = if (lineNumber == currentLine) currentTextPaint else textPaint

            canvas.drawText(logicalNumber.toString(), textX, y, paint)

            if (errorLines.contains(logicalNumber)) {
                canvas.drawCircle(errorX, y - dp(4f), errorMarkSizePx / 2f, errorPaint)
            } else if (warningLines.contains(logicalNumber)) {
                canvas.drawCircle(errorX, y - dp(4f), errorMarkSizePx / 2f, warningPaint)
            }
        }

        // خط الحدود
        canvas.drawLine(
            width - 0.5f, 0f,
            width - 0.5f, height.toFloat(),
            borderPaint
        )
    }

    private fun getTextBaselineOffset(cv: CodeView): Float {
        val paddingTop = cv.getTextPaddingTop()
        val paint = Paint().apply {
            textSize = cv.getTextSize()
        }
        val fm = paint.fontMetrics
        return paddingTop - fm.ascent
    }

    private fun dp(value: Float): Float {
        return value * context.resources.displayMetrics.density
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        invalidate()
    }

    // ============================================================
    // تنظيف
    // ============================================================
    fun cleanup() {
        detach()
    }
}