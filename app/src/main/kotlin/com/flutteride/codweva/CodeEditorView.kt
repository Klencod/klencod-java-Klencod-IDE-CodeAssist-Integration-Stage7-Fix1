package com.flutteride.codweva

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.flutteride.codweva.editor.CodeLexer
import com.flutteride.codweva.editor.CodeView
import com.flutteride.codweva.editor.LineNumberView
import com.flutteride.codweva.editor.TextBuffer
import java.io.File
import java.io.FileWriter
import java.util.Locale
import kotlin.concurrent.thread

/**
 * CodeEditorView — المحرر النهائي.
 *
 * v5.0 — دعم كامل لثيمات المحرر:
 *   - refreshTheme() يُطبّق الثيم على كل المكونات:
 *     • CodeView (خلفية، نص، مؤشر، تحديد)
 *     • CodeLexer (كلمات، نصوص، تعليقات، ...)
 *     • LineNumberView (خلفية، نص، حدود)
 *   - يعمل عند تغيير الثيم من الإعدادات
 *
 * v4.4 — قائمة listeners (addListener)
 */
class CodeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "CodeEditorView"

        private const val PREFS = "flutter_ide_prefs"
        private const val KEY_FONT_SIZE = "editor_font_size"
        private const val KEY_SNIPPETS = "editor_snippets_enabled"
        private const val DEFAULT_FONT_SIZE = 14
        private const val MIN_FONT_SIZE = 8
        private const val MAX_FONT_SIZE = 32

        private const val CHANGE_NOTIFY_DELAY = 250L
    }

    // ============================================================
    // الحقول
    // ============================================================
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val mainHandler = Handler(Looper.getMainLooper())

    // المكونات
    private lateinit var container: LinearLayout
    private lateinit var lineNumbersView: LineNumberView
    private lateinit var codeView: CodeView
    private val lexer = CodeLexer()

    // الإكمال
    private var completionController: CompletionController? = null

    // المستمعون
    interface OnEditorListener {
        fun onContentChanged(dirty: Boolean)
        fun onCursorChanged(line: Int, column: Int)
    }

    private var editorListener: OnEditorListener? = null

    // Listener الخاص بـ CodeView
    private var codeViewListener: CodeView.Listener? = null

    // الحالة
    private var editorReleased = false
    private var suppressEvents = false
    private var isDirty = false
    private var currentLanguage = "dart"
    private var currentFilePath = ""
    private var currentContent = ""

    private var notifyRunnable: Runnable? = null

    private var lastSearchQuery = ""
    private var lastSearchCaseSensitive = false

    // ⭐ v5.0: مراقبة الثيم الحالي لتجنب إعادة التطبيق غير الضرورية
    private var currentThemeId: String = ""

    // ============================================================
    // init
    // ============================================================
    init {
        setBackgroundColor(Color.rgb(13, 17, 23))
        buildLayout()

        try {
            completionController = CompletionController(context, codeView, this)
            completionController?.setLanguage(currentLanguage)
            Log.i(TAG, "CompletionController initialized")
        } catch (e: Exception) {
            Log.w(TAG, "CompletionController failed: ${e.message}")
        }

        // ⭐ v5.0: طبّق الثيم المحفوظ عند البدء
        currentThemeId = ""
        applyCurrentTheme()
    }

    // ============================================================
    // بناء الواجهة
    // ============================================================
    private fun buildLayout() {
        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.rgb(13, 17, 23))
        }

        // 1) أرقام الأسطر
        lineNumbersView = LineNumberView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(lineNumbersView)

        // 2) محرر الكود
        codeView = CodeView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            setTextSize(getFontSize().toFloat() * resources.displayMetrics.density)
        }
        container.addView(codeView)

        addView(
            container,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        // 3) اربط LineNumberView
        lineNumbersView.attachTo(codeView)

        // 4) اربط Lexer
        codeView.setLexer(lexer)

        // 5) اربط المستمع
        attachEditorListener()
    }

    // ============================================================
    // ⭐ v5.0: applyCurrentTheme — يقرأ الثيم ويُطبّقه
    // ============================================================
    private fun applyCurrentTheme() {
        try {
            val themeId = EditorThemes.getSelectedId(context)
            if (themeId == currentThemeId && currentThemeId.isNotEmpty()) {
                // لا تغيير
                return
            }
            currentThemeId = themeId
            refreshTheme()
        } catch (e: Exception) {
            Log.w(TAG, "applyCurrentTheme failed: ${e.message}")
        }
    }

    // ============================================================
    // المستمع — يستخدم addListener
    // ============================================================
    private fun attachEditorListener() {
        val listener = object : CodeView.Listener {
            override fun onTextChanged() {
                if (suppressEvents || editorReleased) return

                isDirty = true

                try {
                    lexer.analyzeFrom(codeView.getTextBuffer(), 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Lexer failed: ${e.message}")
                }

                completionController?.onTextChanged()

                codeView.invalidate()
                scheduleContentChanged()
            }

            override fun onCursorChanged(line: Int, column: Int) {
                if (editorReleased) return
                completionController?.onCursorChanged(line, column)
                editorListener?.onCursorChanged(line, column)
            }

            override fun onScrollChanged(x: Float, y: Float) {
                // LineNumberView يعالج تمريره بنفسه
            }

            override fun onFocusChanged(hasFocus: Boolean) {
                // لا شيء
            }
        }

        codeViewListener = listener
        codeView.addListener(listener)
    }

    private fun scheduleContentChanged() {
        notifyRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            notifyRunnable = null
            editorListener?.onContentChanged(isDirty)
        }
        notifyRunnable = runnable
        mainHandler.postDelayed(runnable, CHANGE_NOTIFY_DELAY)
    }

    // ============================================================
    // Public API — Listener
    // ============================================================
    fun setOnEditorListener(listener: OnEditorListener?) {
        editorListener = listener
    }

    // ============================================================
    // Public API — النص
    // ============================================================
    fun setText(text: String?) {
        setTextWithLanguage(text, "dart")
    }

    fun setTextWithLanguage(text: String?, filePath: String?) {
        val value = text ?: ""

        currentLanguage = detectLanguage(filePath)
        currentFilePath = filePath ?: ""
        currentContent = value
        isDirty = false

        suppressEvents = true
        try {
            codeView.setText(value)
            lexer.setLanguage(currentLanguage)
            codeView.setLexer(lexer)
            lexer.analyzeAll(codeView.getTextBuffer())

            completionController?.setFilePath(currentFilePath)
            completionController?.setLanguage(currentLanguage)

            codeView.invalidate()
            lineNumbersView.postInvalidateOnAnimation()
        } catch (e: Exception) {
            Log.e(TAG, "setTextWithLanguage failed: ${e.message}", e)
        } finally {
            suppressEvents = false
        }
    }

    fun setTextImmediate(text: String?, filePath: String?) {
        setTextWithLanguage(text, filePath)
    }

    fun getText(): String {
        if (editorReleased) return currentContent
        currentContent = codeView.getText()
        return currentContent
    }

    fun getTextAsync(callback: (String) -> Unit) {
        val snapshot = codeView.getText()
        currentContent = snapshot
        callback(snapshot)
    }

    fun markSaved() {
        isDirty = false
    }

    fun setContentFromSave(content: String?) {
        currentContent = content ?: ""
        isDirty = false
    }

    fun updateKnownContent(content: String?) {
        currentContent = content ?: ""
    }

    fun hasUnsavedChanges(): Boolean = isDirty
    fun getCurrentLanguage(): String = currentLanguage
    fun getCurrentFilePath(): String = currentFilePath
    fun getCurrentLine(): Int = codeView.getCursorLine() + 1
    fun getCurrentColumn(): Int = codeView.getCursorColumn() + 1
    fun isReady(): Boolean = !editorReleased

    // ============================================================
    // الحفظ
    // ============================================================
    fun saveTo(targetFile: File, callback: ((Boolean) -> Unit)? = null) {
        val content = codeView.getText()
        currentContent = content

        thread(start = true, name = "EditorSave") {
            val success = try {
                FileWriter(targetFile).use { writer ->
                    writer.write(content)
                    writer.flush()
                }
                isDirty = false
                true
            } catch (e: Exception) {
                Log.e(TAG, "Save failed: ${e.message}")
                false
            }
            post { callback?.invoke(success) }
        }
    }

    // ============================================================
    // ⭐ v5.0: refreshTheme — يطبّق الثيم على كل المكونات
    // ============================================================
    /**
     * يطبّق ثيم المحرر الحالي على كل المكونات:
     *
     *   1. CodeEditorView (خلفية)
     *   2. container (خلفية)
     *   3. CodeView.applyTheme() — النص، المؤشر، التحديد
     *   4. CodeLexer.applyTheme() — الكلمات، النصوص، التعليقات
     *   5. LineNumberView (خلفية، نص، حدود)
     *
     * ثم يعيد رسم الكل.
     */
    fun refreshTheme() {
        if (editorReleased) return

        try {
            val themeId = EditorThemes.getSelectedId(context)
            val theme = EditorThemes.get(themeId)
            currentThemeId = themeId

            Log.i(TAG, "refreshTheme: applying ${theme.displayName}")

            // ============================================================
            // 1) الخلفيات
            // ============================================================
            setBackgroundColor(theme.background)
            container.setBackgroundColor(theme.background)
            codeView.setBackgroundColor(theme.background)

            // ============================================================
            // 2) ألوان Lexer (الكلمات، النصوص، التعليقات...)
            // ============================================================
            lexer.applyTheme(theme)

            // ============================================================
            // 3) ألوان CodeView (النص، المؤشر، التحديد، السطر الحالي)
            // ============================================================
            codeView.applyTheme(theme)

            // ============================================================
            // 4) ألوان LineNumberView (خلفية، نص، حدود)
            // ============================================================
            lineNumbersView.setBackgroundTint(theme.background)
            lineNumbersView.setBorderColor(theme.lineNumber)
            lineNumbersView.setTextColor(theme.lineNumber)
            lineNumbersView.setCurrentTextColor(theme.defaultText)

            // ============================================================
            // 5) إعادة تحليل كامل (لتحديث كل الرموز بالألوان الجديدة)
            // ============================================================
            try {
                lexer.analyzeAll(codeView.getTextBuffer())
            } catch (e: Exception) {
                Log.w(TAG, "analyzeAll failed: ${e.message}")
            }

            // ============================================================
            // 6) إعادة الرسم
            // ============================================================
            codeView.invalidate()
            lineNumbersView.postInvalidateOnAnimation()

            Log.i(TAG, "refreshTheme: done")
        } catch (e: Exception) {
            Log.e(TAG, "refreshTheme failed: ${e.message}", e)
        }
    }

    // ============================================================
    // حجم الخط
    // ============================================================
    fun setFontSize(size: Int) {
        val clamped = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        prefs.edit().putInt(KEY_FONT_SIZE, clamped).apply()

        if (!editorReleased) {
            val sizePx = clamped * resources.displayMetrics.density
            codeView.setTextSize(sizePx)
            lineNumbersView.setTextSize(sizePx * 0.85f)
        }
    }

    fun getFontSize(): Int = prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)

    fun zoomIn() {
        if (editorReleased) return
        setFontSize((getFontSize() + 2).coerceAtMost(MAX_FONT_SIZE))
    }

    fun zoomOut() {
        if (editorReleased) return
        setFontSize((getFontSize() - 2).coerceAtLeast(MIN_FONT_SIZE))
    }

    fun zoomReset() {
        setFontSize(DEFAULT_FONT_SIZE)
    }

    // ============================================================
    // Snippets
    // ============================================================
    fun setSnippetsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SNIPPETS, enabled).apply()
    }

    fun isSnippetsEnabled(): Boolean = prefs.getBoolean(KEY_SNIPPETS, true)

    // ============================================================
    // التنقل
    // ============================================================
    fun goToLine(lineNumber: Int) = goToPosition(lineNumber, 1)

    fun goToPosition(lineNumber: Int, columnNumber: Int) {
        if (editorReleased) return
        val line = (lineNumber - 1).coerceAtLeast(0)
        val column = (columnNumber - 1).coerceAtLeast(0)
        codeView.setCursor(line, column)
        codeView.requestFocus()
    }

    // ============================================================
    // البحث والاستبدال
    // ============================================================
    fun findText(query: String?, caseSensitive: Boolean) {
        if (query.isNullOrEmpty() || editorReleased) return
        lastSearchQuery = query
        lastSearchCaseSensitive = caseSensitive
        findNext(query)
    }

    fun findNext(query: String?): Boolean {
        if (query.isNullOrEmpty() || editorReleased) return false

        val buffer = codeView.getTextBuffer()
        val fullText = buffer.toStringText()
        val searchText = if (lastSearchCaseSensitive) fullText else fullText.lowercase(Locale.US)
        val queryText = if (lastSearchCaseSensitive) query else query.lowercase(Locale.US)

        val currentOffset = getCurrentOffset()
        var index = searchText.indexOf(queryText, currentOffset + 1)

        if (index < 0) {
            index = searchText.indexOf(queryText, 0)
        }

        if (index < 0) return false

        selectRange(index, index + query.length)
        return true
    }

    fun findPrevious(query: String?): Boolean {
        if (query.isNullOrEmpty() || editorReleased) return false

        val buffer = codeView.getTextBuffer()
        val fullText = buffer.toStringText()
        val searchText = if (lastSearchCaseSensitive) fullText else fullText.lowercase(Locale.US)
        val queryText = if (lastSearchCaseSensitive) query else query.lowercase(Locale.US)

        val currentOffset = getCurrentOffset()
        var index = searchText.lastIndexOf(queryText, currentOffset - 1)

        if (index < 0) {
            index = searchText.lastIndexOf(queryText)
        }

        if (index < 0) return false

        selectRange(index, index + query.length)
        return true
    }

    fun replaceAll(query: String?, replacement: String?): Int {
        if (query.isNullOrEmpty() || editorReleased) return 0

        val buffer = codeView.getTextBuffer()
        val fullText = buffer.toStringText()

        var count = 0
        var index = fullText.indexOf(query)
        var result = fullText

        while (index >= 0) {
            result = result.substring(0, index) +
                    replacement +
                    result.substring(index + query.length)
            count++
            index = result.indexOf(query, index + (replacement?.length ?: 0))
        }

        if (count > 0) {
            setTextWithLanguage(result, currentFilePath)
            isDirty = true
            scheduleContentChanged()
        }

        return count
    }

    fun replaceCurrent(query: String?, replacement: String?): Boolean {
        if (query.isNullOrEmpty() || editorReleased) return false

        val selectionText = getSelectedText()
        if (selectionText == query) {
            codeView.insertTextAtCursor(replacement ?: "")
            return true
        }

        if (!findNext(query)) return false

        codeView.insertTextAtCursor(replacement ?: "")
        return true
    }

    private fun getCurrentOffset(): Int {
        val line = codeView.getCursorLine()
        val column = codeView.getCursorColumn()
        val buffer = codeView.getTextBuffer()

        var offset = 0
        for (i in 0 until line) {
            offset += buffer.getLine(i).length + 1
        }
        offset += column
        return offset
    }

    private fun selectRange(startOffset: Int, endOffset: Int) {
        val buffer = codeView.getTextBuffer()

        var remaining = startOffset
        var startLine = 0
        while (startLine < buffer.lineCount()) {
            val lineLen = buffer.getLine(startLine).length + 1
            if (remaining < lineLen) break
            remaining -= lineLen
            startLine++
        }
        val startCol = remaining

        var endRemaining = endOffset
        var endLine = 0
        while (endLine < buffer.lineCount()) {
            val lineLen = buffer.getLine(endLine).length + 1
            if (endRemaining < lineLen) break
            endRemaining -= lineLen
            endLine++
        }
        val endCol = endRemaining

        codeView.setCursor(endLine, endCol)
    }

    // ============================================================
    // الإدراج
    // ============================================================
    fun insertText(text: String?) {
        if (!text.isNullOrEmpty() && !editorReleased) {
            codeView.insertTextAtCursor(text)
        }
    }

    fun insertTextAndWrap(open: String?, close: String?) {
        if (editorReleased) return
        val left = open ?: ""
        val right = close ?: ""

        val selection = getSelectedText()
        if (selection.isNotEmpty()) {
            codeView.insertTextAtCursor(left + selection + right)
        } else {
            codeView.insertTextAtCursor(left + right)
        }
    }

    fun getSelectedText(): String {
        return codeView.getSelectionText()
    }

    // ============================================================
    // النسخ واللصق
    // ============================================================
    fun copySelection() {
        if (editorReleased) return
        codeView.copySelection()
    }

    fun cutSelection() {
        if (editorReleased) return
        codeView.cutSelection()
    }

    fun pasteFromClipboard() {
        if (editorReleased) return
        codeView.pasteFromClipboard()
    }

    fun selectAllText() {
        if (editorReleased) return
        codeView.selectAll()
    }

    fun hasSelection(): Boolean {
        if (editorReleased) return false
        return codeView.getSelectionText().isNotEmpty()
    }

    // ============================================================
    // Undo / Redo
    // ============================================================
    fun undo() {
        if (!editorReleased) codeView.undo()
    }

    fun redo() {
        if (!editorReleased) codeView.redo()
    }

    val canUndo: Boolean
        get() = !editorReleased

    val canRedo: Boolean
        get() = !editorReleased

    // ============================================================
    // التركيز
    // ============================================================
    fun requestEditorFocus() {
        codeView.requestFocus()
        codeView.showSoftKeyboard()
    }

    fun setEditable(editable: Boolean) {
        codeView.isEnabled = editable
    }

    // ============================================================
    // التمرير
    // ============================================================
    val verticalScrollPosition: Int
        get() = if (editorReleased) 0 else codeView.getScrollYPos().toInt()

    fun restoreVerticalScrollPosition(scrollY: Int) {
        if (!editorReleased) {
            codeView.scrollTo(codeView.getScrollXPos(), scrollY.toFloat())
        }
    }

    // ============================================================
    // التوافق
    // ============================================================
    fun evalJs(jsCode: String?) {
        // no-op
    }

    val isHugeFile: Boolean
        get() = getText().length > 5_000_000

    val isLargeFile: Boolean
        get() = getText().length > 1_000_000

    val fileSizeLabel: String
        get() = String.format(Locale.US, "%,d حرف", getText().length)

    // ============================================================
    // اللغة
    // ============================================================
    fun setLanguage(filePath: String?) {
        currentFilePath = filePath ?: ""
        currentLanguage = detectLanguage(currentFilePath)
        lexer.setLanguage(currentLanguage)
        completionController?.setLanguage(currentLanguage)

        try {
            lexer.analyzeAll(codeView.getTextBuffer())
            codeView.invalidate()
        } catch (e: Exception) {
            Log.w(TAG, "setLanguage analysis failed: ${e.message}")
        }
    }

    fun refreshHighlighting() {
        if (!editorReleased) {
            try {
                lexer.analyzeAll(codeView.getTextBuffer())
                codeView.invalidate()
            } catch (_: Exception) {
            }
        }
    }

    fun setExternalErrorLines(lines: Set<Int>?) {
        lineNumbersView.setErrorLines(lines)
    }

    // ============================================================
    // الإكمال
    // ============================================================
    fun setCompletionsEnabled(enabled: Boolean) {
        completionController?.setEnabled(enabled)
    }

    fun isCompletionsEnabled(): Boolean = completionController?.isEnabled() ?: false

    fun setAutoTriggerCompletions(enabled: Boolean) {
        completionController?.setAutoTrigger(enabled)
    }

    fun isAutoTriggerCompletions(): Boolean =
        completionController?.isAutoTrigger() ?: false

    fun showCompletions() {
        completionController?.triggerManually()
    }

    fun hideCompletions() {
        completionController?.cancel()
    }

    fun moveCompletionDown() {
        completionController?.moveDown()
    }

    fun moveCompletionUp() {
        completionController?.moveUp()
    }

    fun commitCompletion() {
        completionController?.commit()
    }

    // ============================================================
    // Release
    // ============================================================
    fun release() {
        if (editorReleased) return
        editorReleased = true

        notifyRunnable?.let { mainHandler.removeCallbacks(it) }
        notifyRunnable = null

        codeViewListener?.let {
            try {
                codeView.removeListener(it)
            } catch (_: Exception) {
            }
        }
        codeViewListener = null

        try {
            completionController?.release()
        } catch (_: Exception) {
        }
        completionController = null

        try {
            lineNumbersView.cleanup()
            codeView.cleanup()
            lexer.invalidateAll()
        } catch (_: Exception) {
        }
    }

    fun clear() = setTextWithLanguage("", "dart")

    // ============================================================
    // اكتشاف اللغة
    // ============================================================
    private fun detectLanguage(filePath: String?): String {
        if (filePath.isNullOrEmpty()) return "dart"
        val dot = filePath.lastIndexOf('.')
        if (dot < 0) return "text"
        return when (filePath.substring(dot + 1).lowercase(Locale.US)) {
            "dart" -> "dart"
            "java" -> "java"
            "kt", "kts" -> "kotlin"
            "ts" -> "typescript"
            "tsx" -> "typescript"
            "js", "mjs", "cjs" -> "javascript"
            "jsx" -> "javascript"
            "xml" -> "xml"
            "html", "htm" -> "html"
            "css", "scss", "sass", "less" -> "css"
            "md", "markdown" -> "markdown"
            "gradle" -> "groovy"
            "groovy", "gvy" -> "groovy"
            "sh", "bash", "zsh", "fish" -> "shell"
            "yaml", "yml" -> "yaml"
            "json" -> "json"
            "sql" -> "sql"
            "toml", "properties" -> "text"
            else -> "text"
        }
    }

    // ============================================================
    // Getters
    // ============================================================
    fun getCodeView(): CodeView = codeView

    fun getLexer(): CodeLexer = lexer

    fun getTextBuffer(): TextBuffer = codeView.getTextBuffer()

    fun getLineNumbersView(): LineNumberView = lineNumbersView
}