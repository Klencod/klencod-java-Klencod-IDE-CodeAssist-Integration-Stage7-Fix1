package com.flutteride.codweva

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.flutteride.codweva.editor.CodeView
import java.util.Locale

/**
 * CompletionController — مدير الإكمال التلقائي.
 *
 * v2.1 — بعد الإصلاحات:
 *   - `coords` يتحقق من `size < 2`
 *   - يستخدم CodeView الجديد
 */
class CompletionController(
    private val context: Context,
    private val codeView: CodeView,
    private val editorView: CodeEditorView
) {

    companion object {
        private const val TAG = "CompletionController"

        private const val MIN_PREFIX_LENGTH = 1
        private const val MAX_SUGGESTIONS = 15
        private const val DEBOUNCE_MS = 150L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val completionPopup = CompletionPopup(context)

    private var isEnabled = true
    private var autoTriggerEnabled = true

    private var currentLanguage: String = "dart"
    private var currentFilePath: String? = null

    private var pendingAnalyze: Runnable? = null
    private var isPopupShowing = false

    private var isInsertingProgrammatically = false

    private var insertPrefixStart = 0
    private var insertPrefixEnd = 0

    // ============================================================
    // onTextChanged
    // ============================================================
    fun onTextChanged() {
        if (!isEnabled) return
        if (!autoTriggerEnabled) return
        if (isInsertingProgrammatically) return

        scheduleAnalyze()
    }

    fun onCursorChanged(line: Int, column: Int) {
        // لا شيء
    }

    private fun scheduleAnalyze() {
        pendingAnalyze?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingAnalyze = null
            performAnalyze()
        }
        pendingAnalyze = runnable
        mainHandler.postDelayed(runnable, DEBOUNCE_MS)
    }

    private fun performAnalyze() {
        if (!isEnabled) return

        val text = try {
            codeView.getTextContent()
        } catch (_: Exception) {
            return
        }

        val cursorLine = codeView.getCursorLine()
        val cursorColumn = codeView.getCursorColumn()

        val buffer = codeView.getTextBuffer()
        var cursorPos = 0
        for (i in 0 until cursorLine) {
            cursorPos += buffer.getLine(i).length + 1
        }
        cursorPos += cursorColumn

        if (cursorPos <= 0 || cursorPos > text.length) {
            dismiss()
            return
        }

        val analysis = try {
            CompletionContext.analyze(text, cursorPos)
        } catch (e: Exception) {
            Log.w(TAG, "analyze failed: ${e.message}")
            dismiss()
            return
        }

        val prefix = analysis.prefix
        if (prefix.length < MIN_PREFIX_LENGTH) {
            if (analysis.customSuggestions.isEmpty()) {
                dismiss()
                return
            }
        }

        val entries = try {
            fetchSuggestions(analysis)
        } catch (e: Exception) {
            Log.w(TAG, "fetchSuggestions failed: ${e.message}")
            dismiss()
            return
        }

        if (entries.isEmpty()) {
            dismiss()
            return
        }

        val coords = calculateCursorPosition(cursorLine, cursorColumn)
        if (coords == null || coords.size < 2) {
            dismiss()
            return
        }

        insertPrefixStart = cursorPos - prefix.length
        insertPrefixEnd = cursorPos

        showPopup(entries, coords[0], coords[1])
    }

    private fun calculateCursorPosition(line: Int, column: Int): IntArray? {
        return try {
            val location = IntArray(2)
            codeView.getLocationOnScreen(location)

            val lineHeight = codeView.getLineHeight()
            val textPaddingLeft = codeView.getTextPaddingLeft()
            val textPaddingTop = codeView.getTextPaddingTop()

            val lineText = codeView.getTextBuffer().getLine(line)
            val prefixText = if (column <= lineText.length) {
                lineText.substring(0, column)
            } else lineText

            val paint = Paint().apply {
                typeface = Typeface.MONOSPACE
                textSize = codeView.getTextSize()
            }
            val textWidth = paint.measureText(prefixText)

            val scrollX = codeView.getScrollXPos()
            val scrollY = codeView.getScrollYPos()

            val x = location[0] + textPaddingLeft + textWidth - scrollX
            val y = location[1] + textPaddingTop + ((line + 1) * lineHeight) - scrollY

            intArrayOf(x.toInt().coerceAtLeast(0), y.toInt().coerceAtLeast(0))
        } catch (e: Exception) {
            Log.w(TAG, "calculateCursorPosition failed: ${e.message}")
            null
        }
    }

    private fun fetchSuggestions(
        analysis: CompletionContext.AnalysisResult
    ): List<CompletionPopup.CompletionEntry> {

        if (analysis.customSuggestions.isNotEmpty()) {
            if (analysis.useFilteredMode && analysis.prefix.isEmpty()) {
                return analysis.customSuggestions.map { name ->
                    CompletionPopup.CompletionEntry(
                        displayName = name,
                        insertText = name,
                        kindLabel = "سياق",
                        description = "اقتراح سياقي",
                        kindColor = 0xFF58A6FF.toInt(),
                        priority = 100
                    )
                }.take(MAX_SUGGESTIONS)
            }
        }

        if (currentLanguage.equals("dart", ignoreCase = true)) {
            return fetchDartSuggestions(analysis)
        }

        return fetchGeneralSuggestions(analysis)
    }

    private fun fetchDartSuggestions(
        analysis: CompletionContext.AnalysisResult
    ): List<CompletionPopup.CompletionEntry> {
        val completions = DartCompletions.get(context)
        if (!completions.isLoaded()) return emptyList()

        val widgetContext = analysis.currentWidget

        val items = if (widgetContext != null) {
            completions.byPrefixAndWidget(
                analysis.prefix,
                widgetContext,
                MAX_SUGGESTIONS
            )
        } else {
            completions.byPrefixWithVariants(
                analysis.prefix,
                null,
                MAX_SUGGESTIONS
            )
        }

        if (items.isEmpty()) return emptyList()

        return CompletionPopup.fromDartItems(items)
    }

    private fun fetchGeneralSuggestions(
        analysis: CompletionContext.AnalysisResult
    ): List<CompletionPopup.CompletionEntry> {
        val completions = GeneralCompletions.get(context, currentLanguage)
            ?: return emptyList()

        var items = completions.byPrefix(analysis.prefix, MAX_SUGGESTIONS)

        if (items.isEmpty() && analysis.prefix.isNotEmpty()) {
            items = completions.fuzzyMatch(analysis.prefix, MAX_SUGGESTIONS)
        }

        if (items.isEmpty()) return emptyList()

        return CompletionPopup.fromGeneralItems(items)
    }

    private fun showPopup(
        entries: List<CompletionPopup.CompletionEntry>,
        x: Int,
        y: Int
    ) {
        completionPopup.show(
            anchor = codeView,
            entries = entries,
            xOffset = x,
            yOffset = y,
            onSelect = { entry -> applySuggestion(entry) }
        )
        isPopupShowing = completionPopup.isShowing
    }

    private fun dismiss() {
        if (isPopupShowing) {
            completionPopup.dismiss()
            isPopupShowing = false
        }
    }

    private fun applySuggestion(entry: CompletionPopup.CompletionEntry) {
        try {
            isInsertingProgrammatically = true

            val buffer = codeView.getTextBuffer()
            val start = insertPrefixStart.coerceIn(0, buffer.length)
            val end = insertPrefixEnd.coerceIn(start, buffer.length)
            val length = end - start

            val text = entry.insertText
            buffer.replace(start, length, text)

            val finalPos = computeFinalCursor(start, text)
            moveCursorToAbsolutePosition(finalPos)

            codeView.invalidate()
        } catch (e: Exception) {
            Log.w(TAG, "applySuggestion failed: ${e.message}")
        } finally {
            mainHandler.postDelayed({
                isInsertingProgrammatically = false
            }, 200)
        }
    }

    private fun computeFinalCursor(start: Int, text: String): Int {
        val placeholder = text.indexOf("\$0")
        if (placeholder >= 0) return start + placeholder

        if (text.contains("\n")) {
            val lastNewline = text.lastIndexOf('\n')
            val lastLine = text.substring(lastNewline + 1)
            val trailingClosers = countTrailingClosers(lastLine)
            return start + text.length - trailingClosers
        }

        return start + text.length
    }

    private fun countTrailingClosers(line: String): Int {
        var count = 0
        for (i in line.length - 1 downTo 0) {
            val c = line[i]
            if (c == ')' || c == '}' || c == ']' || c == '>' || c == ';') {
                count++
            } else break
        }
        return count
    }

    private fun moveCursorToAbsolutePosition(position: Int) {
        try {
            val buffer = codeView.getTextBuffer()
            val safePos = position.coerceIn(0, buffer.length)

            var line = 0
            var lineStart = 0
            var remaining = safePos

            while (line < buffer.lineCount()) {
                val lineLen = buffer.getLine(line).length + 1
                if (remaining < lineLen) {
                    lineStart = safePos - remaining
                    break
                }
                remaining -= lineLen
                line++
            }

            val column = safePos - lineStart
            codeView.setCursor(line, column)
        } catch (e: Exception) {
            Log.w(TAG, "moveCursorToAbsolutePosition failed: ${e.message}")
        }
    }

    // ============================================================
    // Public API
    // ============================================================
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (!enabled) dismiss()
    }

    fun isEnabled(): Boolean = isEnabled

    fun setAutoTrigger(enabled: Boolean) {
        autoTriggerEnabled = enabled
        if (!enabled) dismiss()
    }

    fun isAutoTrigger(): Boolean = autoTriggerEnabled

    fun setLanguage(language: String?) {
        val normalized = language?.lowercase(Locale.US) ?: "dart"
        if (normalized != currentLanguage) {
            currentLanguage = normalized
            dismiss()
        }
    }

    fun setFilePath(path: String?) {
        currentFilePath = path
        val lang = detectLanguage(path)
        if (lang != null) setLanguage(lang)
    }

    private fun detectLanguage(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        val dot = path.lastIndexOf('.')
        if (dot < 0) return null

        return when (path.substring(dot + 1).lowercase(Locale.US)) {
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
            "yaml", "yml" -> "yaml"
            "sh", "bash", "zsh", "fish" -> "shell"
            "gradle", "groovy", "gvy" -> "groovy"
            else -> null
        }
    }

    fun moveDown() {
        if (isPopupShowing) completionPopup.moveSelectionDown()
    }

    fun moveUp() {
        if (isPopupShowing) completionPopup.moveSelectionUp()
    }

    fun commit() {
        if (isPopupShowing) completionPopup.commitSelection()
    }

    fun cancel() {
        dismiss()
    }

    fun triggerManually() {
        if (!isEnabled) return
        performAnalyze()
    }

    fun release() {
        pendingAnalyze?.let { mainHandler.removeCallbacks(it) }
        pendingAnalyze = null
        completionPopup.dismiss()
        isPopupShowing = false
    }
}