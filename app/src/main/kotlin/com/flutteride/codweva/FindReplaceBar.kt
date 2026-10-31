package com.flutteride.codweva

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * FindReplaceBar — شريط البحث والاستبدال.
 *
 * v4.0 — متوافق مع CodeEditorView الجديد:
 *   - يستخدم CodeEditorView مباشرة
 *   - لا يعتمد على Sora
 *
 * الأزرار:
 *   - ▲ السابق
 *   - ▼ التالي
 *   - استبدال
 *   - استبدال الكل
 *   - إغلاق
 */
class FindReplaceBar(private val appContext: Context) : LinearLayout(appContext) {

    // ============================================================
    // الألوان
    // ============================================================
    private val colorBackground = 0xFF0D1117.toInt()
    private val colorSurface = 0xFF161B22.toInt()
    private val colorSurfaceAlt = 0xFF21262D.toInt()
    private val colorPrimary = 0xFF1F6FEB.toInt()
    private val colorPrimaryLight = 0xFF58A6FF.toInt()
    private val colorTextPrimary = 0xFFE6EDF3.toInt()
    private val colorTextSecondary = 0xFF8B949E.toInt()
    private val colorBorder = 0xFF30363D.toInt()
    private val colorAccent = 0xFFD29922.toInt()

    // ============================================================
    // الحقول
    // ============================================================
    private lateinit var etFind: EditText
    private lateinit var etReplace: EditText
    private lateinit var btnFindPrev: Button
    private lateinit var btnFindNext: Button
    private lateinit var btnReplace: Button
    private lateinit var btnReplaceAll: Button
    private lateinit var btnClose: Button
    private lateinit var tvResults: TextView

    private var editorView: CodeEditorView? = null

    // ============================================================
    // init
    // ============================================================
    init {
        orientation = VERTICAL
        background = UiStyle.rounded(appContext, colorSurface, 12, colorBorder, 1)
        setPadding(dp(12), dp(10), dp(12), dp(10))

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            elevation = dp(8).toFloat()
        }

        buildLayout()
    }

    private fun dp(value: Int): Int {
        return Math.round(value * resources.displayMetrics.density)
    }

    // ============================================================
    // البناء
    // ============================================================
    private fun buildLayout() {
        // ─── صف البحث ───
        val findRow = LinearLayout(appContext).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        etFind = createStyledEditText("ابحث في الملف…").apply {
            layoutParams = LayoutParams(0, dp(40), 1f)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        findRow.addView(etFind)

        btnFindPrev = createStyledButton("▲", false).apply {
            setTextSize(14f)
            layoutParams = LayoutParams(dp(42), dp(40)).apply {
                leftMargin = dp(4)
            }
        }
        findRow.addView(btnFindPrev)

        btnFindNext = createStyledButton("▼", false).apply {
            setTextSize(14f)
            layoutParams = LayoutParams(dp(42), dp(40)).apply {
                leftMargin = dp(4)
            }
        }
        findRow.addView(btnFindNext)

        addView(findRow)

        // ─── صف الاستبدال ───
        val replaceRow = LinearLayout(appContext).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        etReplace = createStyledEditText("استبدال بـ…").apply {
            layoutParams = LayoutParams(0, dp(40), 1f)
        }
        replaceRow.addView(etReplace)

        btnReplace = createStyledButton("استبدال", false).apply {
            setTextSize(12f)
            layoutParams = LayoutParams(dp(74), dp(40)).apply {
                leftMargin = dp(4)
            }
        }
        replaceRow.addView(btnReplace)

        btnReplaceAll = createStyledButton("الكل", false).apply {
            setTextSize(12f)
            layoutParams = LayoutParams(dp(54), dp(40)).apply {
                leftMargin = dp(4)
            }
        }
        replaceRow.addView(btnReplaceAll)

        addView(replaceRow)

        // ─── صف النتائج ───
        val bottomRow = LinearLayout(appContext).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        tvResults = TextView(appContext).apply {
            text = "اكتب كلمة للبحث…"
            setTextColor(colorTextSecondary)
            setTextSize(14f)
            typeface = Typeface.MONOSPACE
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        bottomRow.addView(tvResults)

        btnClose = createStyledButton("إغلاق ✖", false).apply {
            setTextSize(12f)
            setTextColor(colorTextPrimary)
            layoutParams = LayoutParams(dp(88), dp(36))
        }
        bottomRow.addView(btnClose)

        addView(bottomRow)

        setupListeners()
    }

    private fun createStyledEditText(hint: String): EditText {
        return EditText(appContext).apply {
            this.hint = hint
            setHintTextColor(colorTextSecondary)
            setTextColor(colorTextPrimary)
            setTextSize(13f)
            setPadding(dp(12), 0, dp(12), 0)
            isSingleLine = true

            background = GradientDrawable().apply {
                setColor(colorBackground)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), colorBorder)
            }
        }
    }

    private fun createStyledButton(text: String, isPrimary: Boolean): Button {
        return Button(appContext).apply {
            this.text = text
            setTextColor(Color.WHITE)
            isAllCaps = false
            setPadding(0, 0, 0, 0)

            background = GradientDrawable().apply {
                setColor(if (isPrimary) colorPrimary else colorSurfaceAlt)
                cornerRadius = dp(10).toFloat()
                if (!isPrimary) {
                    setStroke(dp(1), colorBorder)
                }
            }
        }
    }

    // ============================================================
    // تعيين المحرر
    // ============================================================
    fun setEditor(codeEditorView: CodeEditorView?) {
        this.editorView = codeEditorView
    }

    // للتوافق مع الكود القديم
    @Deprecated("Use setEditor(CodeEditorView) instead")
    fun setSoraEditor(soraEditor: Any?) {
        // لا تفعل شيء
    }

    @Deprecated("Use setEditor(CodeEditorView) instead")
    fun setEditor(editorRef: android.widget.EditText?) {
        // لا تفعل شيء
    }

    // ============================================================
    // المستمعون
    // ============================================================
    private fun setupListeners() {
        etFind.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                if (query.isEmpty()) {
                    tvResults.text = "اكتب كلمة للبحث…"
                    tvResults.setTextColor(colorTextSecondary)
                } else {
                    tvResults.text = "اضغط ▼ للبحث"
                    tvResults.setTextColor(colorPrimaryLight)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        btnFindPrev.setOnClickListener { goToPrev() }
        btnFindNext.setOnClickListener { goToNext() }
        btnReplace.setOnClickListener { replaceCurrent() }
        btnReplaceAll.setOnClickListener { replaceAll() }
        btnClose.setOnClickListener { hide() }

        etFind.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_NEXT ||
                actionId == EditorInfo.IME_ACTION_DONE
            ) {
                goToNext()
                true
            } else false
        }
    }

    // ============================================================
    // البحث
    // ============================================================
    private fun goToNext() {
        val editor = editorView ?: return
        val query = etFind.text.toString()
        if (query.isEmpty()) return

        try {
            editor.findNext(query)
            tvResults.text = "▼"
            tvResults.setTextColor(colorPrimaryLight)
        } catch (e: Exception) {
            tvResults.text = "خطأ في البحث"
            tvResults.setTextColor(colorAccent)
        }

        restoreFindFocus()
    }

    private fun goToPrev() {
        val editor = editorView ?: return
        val query = etFind.text.toString()
        if (query.isEmpty()) return

        try {
            editor.findPrevious(query)
            tvResults.text = "▲"
            tvResults.setTextColor(colorPrimaryLight)
        } catch (e: Exception) {
            tvResults.text = "خطأ في البحث"
            tvResults.setTextColor(colorAccent)
        }

        restoreFindFocus()
    }

    // ============================================================
    // الاستبدال
    // ============================================================
    private fun replaceCurrent() {
        val editor = editorView ?: return
        val query = etFind.text.toString()
        val replacement = etReplace.text.toString()
        if (query.isEmpty()) return

        try {
            editor.replaceCurrent(query, replacement)
            tvResults.text = "تم الاستبدال"
            tvResults.setTextColor(colorPrimaryLight)
        } catch (e: Exception) {
            tvResults.text = "خطأ في الاستبدال"
            tvResults.setTextColor(colorAccent)
        }

        restoreFindFocus()
    }

    private fun replaceAll() {
        val editor = editorView ?: return
        val query = etFind.text.toString()
        val replacement = etReplace.text.toString()
        if (query.isEmpty()) return

        try {
            val count = editor.replaceAll(query, replacement)
            tvResults.text = "تم استبدال $count"
            tvResults.setTextColor(colorPrimaryLight)
        } catch (e: Exception) {
            tvResults.text = "خطأ في الاستبدال"
            tvResults.setTextColor(colorAccent)
        }

        restoreFindFocus()
    }

    private fun restoreFindFocus() {
        etFind.post {
            if (!etFind.hasFocus()) {
                etFind.requestFocus()
            }
            try {
                val len = etFind.text.length
                etFind.setSelection(len)
            } catch (_: Exception) {
            }
        }
    }

    // ============================================================
    // الإظهار والإخفاء
    // ============================================================
    fun show() {
        visibility = VISIBLE
        etFind.requestFocus()
        etFind.setText("")
        etFind.hint = "ابحث في الملف…"
        tvResults.text = "اكتب كلمة للبحث…"
        tvResults.setTextColor(colorTextSecondary)
    }

    fun hide() {
        visibility = GONE
    }
}