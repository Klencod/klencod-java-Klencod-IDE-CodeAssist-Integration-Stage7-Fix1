package com.flutteride.codweva.editor

import android.util.Log
import com.flutteride.codweva.EditorThemes
import java.util.Locale

/**
 * CodeLexer — محرك تلوين تزايدي (Incremental Lexer).
 *
 * v3.0 — دعم ثيمات المحرر:
 *   - إضافة applyTheme() لتحديث الألوان
 *   - الألوان أصبحت قابلة للتحديث ديناميكياً
 *   - getColorForToken يستخدم الألوان الحالية
 *
 * v2.0 — إصلاحات أداء حاسمة:
 *   - analyzeAll يستخدم getAllLines() مرة واحدة → O(n) بدل O(n²)
 *   - analyzeFrom يستخدم getAllLines() مرة واحدة → O(n) بدل O(n²)
 *   - فتح ملف 20K سطر: من 10+ ثواني إلى < 200ms
 *
 * المبادئ:
 *   1. نحتفظ بحالة كل سطر (inBlockComment, inString, ...)
 *   2. عند التعديل، نعيد تحليل من السطر حتى تطابق الحالة المخزنة
 *   3. النتيجة: O(affected lines) بدل O(file size)
 *
 * اللغات المدعومة:
 *   Dart, Java, Kotlin, TypeScript, JavaScript,
 *   XML, HTML, CSS, YAML, Shell, Groovy, JSON, Markdown, SQL
 */
class CodeLexer {

    companion object {
        private const val TAG = "CodeLexer"

        // ============================================================
        // أنواع الرموز
        // ============================================================
        const val TOKEN_NONE = 0
        const val TOKEN_KEYWORD = 1
        const val TOKEN_TYPE = 2
        const val TOKEN_FUNCTION = 3
        const val TOKEN_STRING = 4
        const val TOKEN_NUMBER = 5
        const val TOKEN_COMMENT = 6
        const val TOKEN_OPERATOR = 7
        const val TOKEN_TAG = 8
        const val TOKEN_ATTRIBUTE = 9
        const val TOKEN_VARIABLE = 10
        const val TOKEN_ANNOTATION = 11
        const val TOKEN_CONSTANT = 12
        const val TOKEN_PUNCTUATION = 13

        // ============================================================
        // الألوان الافتراضية (Dark Blue theme)
        // ============================================================
        private const val DEFAULT_KEYWORD = 0xFF58A6FF.toInt()
        private const val DEFAULT_TYPE = 0xFF79C0FF.toInt()
        private const val DEFAULT_FUNCTION = 0xFFD2A8FF.toInt()
        private const val DEFAULT_STRING = 0xFF4A9EFF.toInt()
        private const val DEFAULT_NUMBER = 0xFF6A8CFF.toInt()
        private const val DEFAULT_COMMENT = 0xFF8B949E.toInt()
        private const val DEFAULT_OPERATOR = 0xFF1F6FEB.toInt()
        private const val DEFAULT_TAG = 0xFF7EE787.toInt()
        private const val DEFAULT_ATTRIBUTE = 0xFFFFA657.toInt()
        private const val DEFAULT_VARIABLE = 0xFFE6EDF3.toInt()
        private const val DEFAULT_ANNOTATION = 0xFFD2A8FF.toInt()
        private const val DEFAULT_CONSTANT = 0xFF79C0FF.toInt()
        private const val DEFAULT_PUNCTUATION = 0xFFE6EDF3.toInt()
    }

    // ============================================================
    // نموذج الرمز
    // ============================================================
    class Token(
        val type: Int,
        val start: Int,
        val end: Int
    ) {
        val length: Int get() = end - start
        override fun toString(): String = "Token(type=$type, start=$start, end=$end)"
    }

    // ============================================================
    // حالة كل سطر
    // ============================================================
    class LineState(
        var inBlockComment: Boolean = false,
        var inString: Boolean = false,
        var stringDelimiter: Char = '"',
        var inComment: Boolean = false,
        var inCdata: Boolean = false
    ) {
        fun copyFrom(other: LineState) {
            inBlockComment = other.inBlockComment
            inString = other.inString
            stringDelimiter = other.stringDelimiter
            inComment = other.inComment
            inCdata = other.inCdata
        }

        fun sameAs(other: LineState): Boolean {
            return inBlockComment == other.inBlockComment &&
                    inString == other.inString &&
                    stringDelimiter == other.stringDelimiter &&
                    inComment == other.inComment &&
                    inCdata == other.inCdata
        }

        fun reset() {
            inBlockComment = false
            inString = false
            stringDelimiter = '"'
            inComment = false
            inCdata = false
        }
    }

    // ============================================================
    // الحالة
    // ============================================================
    private var language: String = "dart"
    private var rules: LanguageRules = LanguageRules.forLanguage("dart")

    private val lineStates = mutableListOf<LineState>()
    private val lineTokens = mutableListOf<MutableList<Token>>()

    // ============================================================
    // ⭐ الألوان الحالية (قابلة للتحديث حسب الثيم)
    // ============================================================
    private var currentKeywordColor: Int = DEFAULT_KEYWORD
    private var currentTypeColor: Int = DEFAULT_TYPE
    private var currentFunctionColor: Int = DEFAULT_FUNCTION
    private var currentStringColor: Int = DEFAULT_STRING
    private var currentNumberColor: Int = DEFAULT_NUMBER
    private var currentCommentColor: Int = DEFAULT_COMMENT
    private var currentOperatorColor: Int = DEFAULT_OPERATOR
    private var currentTagColor: Int = DEFAULT_TAG
    private var currentAttributeColor: Int = DEFAULT_ATTRIBUTE
    private var currentVariableColor: Int = DEFAULT_VARIABLE
    private var currentAnnotationColor: Int = DEFAULT_ANNOTATION
    private var currentConstantColor: Int = DEFAULT_CONSTANT
    private var currentPunctuationColor: Int = DEFAULT_PUNCTUATION

    // ============================================================
    // Public API
    // ============================================================

    fun setLanguage(newLanguage: String?) {
        val normalized = newLanguage?.lowercase(Locale.US) ?: "dart"
        if (normalized == language) return
        language = normalized
        rules = LanguageRules.forLanguage(normalized)
        invalidateAll()
    }

    fun getLanguage(): String = language

    // ============================================================
    // ⭐ applyTheme — تحديث الألوان حسب الثيم
    // ============================================================
    /**
     * يطبّق ألوان ثيم على الـ Lexer.
     * يُستدعى من CodeEditorView.refreshTheme().
     *
     * @param theme ثيم المحرر المختار
     */
    fun applyTheme(theme: EditorThemes.Theme) {
        currentKeywordColor = theme.keyword
        currentTypeColor = theme.function
        currentFunctionColor = theme.function
        currentStringColor = theme.string
        currentNumberColor = theme.number
        currentCommentColor = theme.comment
        currentOperatorColor = theme.operator
        currentTagColor = theme.tag
        currentAttributeColor = theme.tag
        currentVariableColor = theme.defaultText
        currentAnnotationColor = theme.function
        currentConstantColor = theme.number
        currentPunctuationColor = theme.defaultText
    }

    // ============================================================
    // ⭐ التحليل الكامل — O(n) بدل O(n²)
    // ============================================================
    fun analyzeAll(buffer: TextBuffer) {
        invalidateAll()

        val lines = buffer.getAllLines()
        val lineCount = lines.size

        val state = LineState()

        for (i in 0 until lineCount) {
            val line = lines[i]
            val tokens = tokenize(line, state)
            lineTokens.add(tokens)
            lineStates.add(copyState(state))
        }

        Log.d(TAG, "analyzed $lineCount lines with ${rules.name}")
    }

    // ============================================================
    // ⭐ التحليل التزايدي
    // ============================================================
    fun analyzeFrom(buffer: TextBuffer, startLine: Int) {
        if (startLine < 0) {
            analyzeAll(buffer)
            return
        }

        val lines = buffer.getAllLines()
        val lineCount = lines.size

        val safeStart = startLine.coerceIn(0, lineCount)

        val initialState = LineState()
        if (safeStart > 0 && safeStart - 1 < lineStates.size) {
            initialState.copyFrom(lineStates[safeStart - 1])
        }

        while (lineTokens.size <= safeStart) lineTokens.add(mutableListOf())
        while (lineStates.size <= safeStart) lineStates.add(LineState())

        val currentState = LineState()
        currentState.copyFrom(initialState)

        for (i in safeStart until lineCount) {
            val line = lines[i]
            val tokens = tokenize(line, currentState)

            if (i < lineTokens.size) {
                lineTokens[i] = tokens
            } else {
                lineTokens.add(tokens)
            }

            val stateAfter = copyState(currentState)
            val stateBefore = if (i < lineStates.size) lineStates[i] else null

            if (i < lineStates.size) {
                lineStates[i].copyFrom(stateAfter)
            } else {
                lineStates.add(stateAfter)
            }

            if (stateBefore != null && stateBefore.sameAs(stateAfter) && i > safeStart) {
                Log.d(TAG, "stopped relexing at line $i (state matched)")
                break
            }
        }

        while (lineStates.size < lineCount) lineStates.add(LineState())
        while (lineTokens.size < lineCount) lineTokens.add(mutableListOf())
    }

    fun shiftLines(fromLine: Int, delta: Int) {
        if (fromLine < 0) return

        if (delta > 0) {
            repeat(delta) {
                if (fromLine <= lineStates.size) {
                    lineStates.add(fromLine, LineState())
                    lineTokens.add(fromLine, mutableListOf())
                }
            }
        } else if (delta < 0) {
            repeat(-delta) {
                if (fromLine < lineStates.size) {
                    lineStates.removeAt(fromLine)
                    lineTokens.removeAt(fromLine)
                }
            }
        }
    }

    fun getTokens(lineNumber: Int): List<Token> {
        if (lineNumber < 0 || lineNumber >= lineTokens.size) return emptyList()
        return lineTokens[lineNumber]
    }

    // ============================================================
    // ⭐ getColorForToken — يستخدم الألوان الحالية
    // ============================================================
    fun getColorForToken(tokenType: Int): Int {
        return when (tokenType) {
            TOKEN_KEYWORD -> currentKeywordColor
            TOKEN_TYPE -> currentTypeColor
            TOKEN_FUNCTION -> currentFunctionColor
            TOKEN_STRING -> currentStringColor
            TOKEN_NUMBER -> currentNumberColor
            TOKEN_COMMENT -> currentCommentColor
            TOKEN_OPERATOR -> currentOperatorColor
            TOKEN_TAG -> currentTagColor
            TOKEN_ATTRIBUTE -> currentAttributeColor
            TOKEN_VARIABLE -> currentVariableColor
            TOKEN_ANNOTATION -> currentAnnotationColor
            TOKEN_CONSTANT -> currentConstantColor
            TOKEN_PUNCTUATION -> currentPunctuationColor
            else -> currentVariableColor
        }
    }

    fun invalidateAll() {
        lineStates.clear()
        lineTokens.clear()
    }

    val analyzedLineCount: Int
        get() = lineTokens.size

    // ============================================================
    // النسخ
    // ============================================================
    private fun copyState(source: LineState): LineState {
        return LineState(
            inBlockComment = source.inBlockComment,
            inString = source.inString,
            stringDelimiter = source.stringDelimiter,
            inComment = source.inComment,
            inCdata = source.inCdata
        )
    }

    // ============================================================
    // التحليل الأساسي (Tokenizer)
    // ============================================================
    private fun tokenize(line: String, state: LineState): MutableList<Token> {
        val tokens = mutableListOf<Token>()

        if (state.inString) {
            tokenizeStringContinuation(line, state, tokens)
            return tokens
        }

        if (state.inBlockComment) {
            tokenizeBlockCommentContinuation(line, state, tokens)
            return tokens
        }

        if (state.inComment) {
            tokenizeXmlCommentContinuation(line, state, tokens)
            return tokens
        }

        if (state.inCdata) {
            tokenizeCdataContinuation(line, state, tokens)
            return tokens
        }

        return tokenizeNormal(line, state)
    }

    // ============================================================
    // تحليل عادي
    // ============================================================
    private fun tokenizeNormal(line: String, state: LineState): MutableList<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val n = line.length

        while (i < n) {
            val c = line[i]

            // 1) مسافات
            if (c == ' ' || c == '\t' || c == '\r') {
                i++
                continue
            }

            // 2) تعليق سطر واحد (//)
            if (i + 1 < n && c == '/' && line[i + 1] == '/') {
                tokens.add(Token(TOKEN_COMMENT, i, n))
                return tokens
            }

            // 3) تعليق سطر واحد (#) — shell/yaml
            if (rules.supportsHashComment && c == '#') {
                tokens.add(Token(TOKEN_COMMENT, i, n))
                return tokens
            }

            // 4) تعليق كتلة (/* ... */)
            if (i + 1 < n && c == '/' && line[i + 1] == '*') {
                val end = line.indexOf("*/", i + 2)
                if (end >= 0) {
                    tokens.add(Token(TOKEN_COMMENT, i, end + 2))
                    i = end + 2
                } else {
                    tokens.add(Token(TOKEN_COMMENT, i, n))
                    state.inBlockComment = true
                    return tokens
                }
                continue
            }

            // 5) نصوص
            if (c == '"' || c == '\'') {
                val result = scanString(line, i, state)
                tokens.add(Token(TOKEN_STRING, i, result))
                i = result
                continue
            }

            // 6) نصوص backtick
            if (c == '`' && rules.supportsBacktick) {
                val result = scanBacktickString(line, i, state)
                tokens.add(Token(TOKEN_STRING, i, result))
                i = result
                continue
            }

            // 7) أرقام
            if (c.isDigit() || (c == '.' && i + 1 < n && line[i + 1].isDigit())) {
                val end = scanNumber(line, i)
                tokens.add(Token(TOKEN_NUMBER, i, end))
                i = end
                continue
            }

            // 8) تعليقات مسبقة (@Annotation)
            if (c == '@' && rules.supportsAnnotations) {
                val end = scanIdentifier(line, i + 1)
                tokens.add(Token(TOKEN_ANNOTATION, i, end))
                i = end
                continue
            }

            // 9) وسوم HTML/XML
            if (rules.isMarkupLanguage && c == '<') {
                if (i + 3 < n && line.substring(i, i + 4) == "<!--") {
                    val end = line.indexOf("-->", i + 4)
                    if (end >= 0) {
                        tokens.add(Token(TOKEN_COMMENT, i, end + 3))
                        i = end + 3
                    } else {
                        tokens.add(Token(TOKEN_COMMENT, i, n))
                        state.inComment = true
                        return tokens
                    }
                    continue
                }

                if (i + 8 < n && line.substring(i, i + 9) == "<![CDATA[") {
                    val end = line.indexOf("]]>", i + 9)
                    if (end >= 0) {
                        tokens.add(Token(TOKEN_STRING, i, end + 3))
                        i = end + 3
                    } else {
                        tokens.add(Token(TOKEN_STRING, i, n))
                        state.inCdata = true
                        return tokens
                    }
                    continue
                }

                val end = scanMarkupTag(line, i, tokens)
                i = end
                continue
            }

            // 10) معرّفات
            if (c.isLetter() || c == '_' || c == '$') {
                val end = scanIdentifier(line, i)
                val word = line.substring(i, end)
                val type = classifyWord(word)
                val isFunction = isFollowedByParenthesis(line, end)

                val tokenType = if (isFunction) TOKEN_FUNCTION else type
                tokens.add(Token(tokenType, i, end))
                i = end
                continue
            }

            // 11) عوامل
            val opEnd = scanOperator(line, i)
            if (opEnd > i) {
                tokens.add(Token(TOKEN_OPERATOR, i, opEnd))
                i = opEnd
                continue
            }

            // 12) ترقيم
            tokens.add(Token(TOKEN_PUNCTUATION, i, i + 1))
            i++
        }

        return tokens
    }

    // ============================================================
    // نصوص
    // ============================================================
    private fun scanString(line: String, start: Int, state: LineState): Int {
        val delimiter = line[start]
        var i = start + 1
        val n = line.length

        while (i < n) {
            val c = line[i]
            if (c == '\\' && i + 1 < n) {
                i += 2
                continue
            }
            if (c == delimiter) return i + 1
            i++
        }

        state.inString = true
        state.stringDelimiter = delimiter
        return n
    }

    private fun scanBacktickString(line: String, start: Int, state: LineState): Int {
        var i = start + 1
        val n = line.length
        while (i < n) {
            val c = line[i]
            if (c == '\\' && i + 1 < n) {
                i += 2
                continue
            }
            if (c == '`') return i + 1
            i++
        }
        state.inString = true
        state.stringDelimiter = '`'
        return n
    }

    private fun tokenizeStringContinuation(
        line: String,
        state: LineState,
        tokens: MutableList<Token>
    ) {
        val delimiter = state.stringDelimiter
        var i = 0
        val n = line.length

        while (i < n) {
            val c = line[i]
            if (c == '\\' && i + 1 < n) {
                i += 2
                continue
            }
            if (c == delimiter) {
                tokens.add(Token(TOKEN_STRING, 0, i + 1))
                state.inString = false

                val rest = tokenizeNormal(line.substring(i + 1), state)
                val offset = i + 1
                for (t in rest) {
                    tokens.add(Token(t.type, t.start + offset, t.end + offset))
                }
                return
            }
            i++
        }

        tokens.add(Token(TOKEN_STRING, 0, n))
    }

    // ============================================================
    // تعليقات كتلة
    // ============================================================
    private fun tokenizeBlockCommentContinuation(
        line: String,
        state: LineState,
        tokens: MutableList<Token>
    ) {
        val end = line.indexOf("*/")
        if (end >= 0) {
            tokens.add(Token(TOKEN_COMMENT, 0, end + 2))
            state.inBlockComment = false
            val rest = tokenizeNormal(line.substring(end + 2), state)
            val offset = end + 2
            for (t in rest) {
                tokens.add(Token(t.type, t.start + offset, t.end + offset))
            }
        } else {
            tokens.add(Token(TOKEN_COMMENT, 0, line.length))
        }
    }

    private fun tokenizeXmlCommentContinuation(
        line: String,
        state: LineState,
        tokens: MutableList<Token>
    ) {
        val end = line.indexOf("-->")
        if (end >= 0) {
            tokens.add(Token(TOKEN_COMMENT, 0, end + 3))
            state.inComment = false
            val rest = tokenizeNormal(line.substring(end + 3), state)
            val offset = end + 3
            for (t in rest) {
                tokens.add(Token(t.type, t.start + offset, t.end + offset))
            }
        } else {
            tokens.add(Token(TOKEN_COMMENT, 0, line.length))
        }
    }

    private fun tokenizeCdataContinuation(
        line: String,
        state: LineState,
        tokens: MutableList<Token>
    ) {
        val end = line.indexOf("]]>")
        if (end >= 0) {
            tokens.add(Token(TOKEN_STRING, 0, end + 3))
            state.inCdata = false
            val rest = tokenizeNormal(line.substring(end + 3), state)
            val offset = end + 3
            for (t in rest) {
                tokens.add(Token(t.type, t.start + offset, t.end + offset))
            }
        } else {
            tokens.add(Token(TOKEN_STRING, 0, line.length))
        }
    }

    // ============================================================
    // وسوم HTML/XML
    // ============================================================
    private fun scanMarkupTag(
        line: String,
        start: Int,
        tokens: MutableList<Token>
    ): Int {
        var i = start + 1
        val n = line.length

        if (i < n && line[i] == '/') i++

        val nameStart = i
        while (i < n && (line[i].isLetterOrDigit() || line[i] == '-' || line[i] == ':')) i++

        if (i > nameStart) {
            tokens.add(Token(TOKEN_TAG, start, i))
        }

        while (i < n) {
            val c = line[i]

            if (c == '>') {
                tokens.add(Token(TOKEN_TAG, i, i + 1))
                i++
                break
            }

            if (c == '/' && i + 1 < n && line[i + 1] == '>') {
                tokens.add(Token(TOKEN_TAG, i, i + 2))
                i += 2
                break
            }

            if (c == ' ' || c == '\t' || c == '\n') {
                i++
                continue
            }

            if (c.isLetter() || c == '_' || c == ':' || c == '@') {
                val attrStart = i
                while (i < n && (line[i].isLetterOrDigit() ||
                            line[i] == '-' || line[i] == '_' ||
                            line[i] == ':' || line[i] == '@')) i++
                tokens.add(Token(TOKEN_ATTRIBUTE, attrStart, i))
                continue
            }

            if (c == '=') {
                tokens.add(Token(TOKEN_OPERATOR, i, i + 1))
                i++
                if (i < n && (line[i] == '"' || line[i] == '\'')) {
                    val delim = line[i]
                    val valueEnd = line.indexOf(delim, i + 1)
                    if (valueEnd >= 0) {
                        tokens.add(Token(TOKEN_STRING, i, valueEnd + 1))
                        i = valueEnd + 1
                    } else {
                        tokens.add(Token(TOKEN_STRING, i, n))
                        i = n
                    }
                }
                continue
            }

            i++
        }

        return i
    }

    // ============================================================
    // أدوات
    // ============================================================
    private fun scanIdentifier(line: String, start: Int): Int {
        var i = start
        val n = line.length
        while (i < n) {
            val c = line[i]
            if (c.isLetterOrDigit() || c == '_' || c == '$') i++
            else break
        }
        return i
    }

    private fun scanNumber(line: String, start: Int): Int {
        var i = start
        val n = line.length
        var hasDot = false
        var hasExp = false

        while (i < n) {
            val c = line[i]
            when {
                c.isDigit() -> i++
                c == '.' && !hasDot && !hasExp -> {
                    hasDot = true
                    i++
                }
                (c == 'e' || c == 'E') && !hasExp -> {
                    hasExp = true
                    i++
                    if (i < n && (line[i] == '+' || line[i] == '-')) i++
                }
                c == 'x' || c == 'X' -> i++
                c in 'a'..'f' || c in 'A'..'F' -> i++
                c == 'L' || c == 'l' || c == 'f' || c == 'F' ||
                        c == 'd' || c == 'D' -> {
                    i++
                    break
                }
                else -> break
            }
        }
        return i
    }

    private fun scanOperator(line: String, start: Int): Int {
        val n = line.length
        if (start >= n) return start

        val multiCharOps = arrayOf(
            "===", "!==", ">>>", "<<=", ">>=",
            "==", "!=", "<=", ">=", "&&", "||", "++", "--",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
            "<<", ">>", "?.", "??", "=>", "->", "::", ".."
        )

        for (op in multiCharOps) {
            if (start + op.length <= n &&
                line.substring(start, start + op.length) == op
            ) {
                return start + op.length
            }
        }

        val c = line[start]
        return if (c in "+-*/%=<>!&|^~?:,;") start + 1 else start
    }

    private fun isFollowedByParenthesis(line: String, fromIndex: Int): Boolean {
        var i = fromIndex
        while (i < line.length && (line[i] == ' ' || line[i] == '\t')) i++
        return i < line.length && line[i] == '('
    }

    private fun classifyWord(word: String): Int {
        if (rules.keywords.contains(word)) return TOKEN_KEYWORD
        if (rules.types.contains(word)) return TOKEN_TYPE
        if (rules.constants.contains(word)) return TOKEN_CONSTANT
        return TOKEN_VARIABLE
    }
}