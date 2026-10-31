package com.flutteride.codweva

import android.content.Context
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

/**
 * CodeToolbar — شريط أدوات سريع لمحرر الكود.
 *
 * v5.1 — إضافة:
 *   - زر نسخ (⧉)
 *   - زر قطع (✂)
 *   - زر لصق (📋)
 *   - زر تحديد الكل (⬚)
 *
 * الأزرار:
 *   - رموز رياضية: ( ) { } [ ] = + - * / " ' ; , . ! ? && ||
 *   - ↵ سطر جديد، ⇥ مسافة بادئة
 *   - ↩ تراجع، ↪ إعادة
 *   - ➕ تكبير، ➖ تصغير
 *   - 🔍 بحث، 💡 إكمال
 *   - ⧉ نسخ، ✂ قطع، 📋 لصق، ⬚ تحديد الكل
 */
class CodeToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val container: LinearLayout

    // مرجع للمحرر
    private var editorView: CodeEditorView? = null

    // ============================================================
    // الألوان
    // ============================================================
    private val bgColor = 0xFF0D1117.toInt()
    private val btnColor = 0xFF161B22.toInt()
    private val btnPressedColor = 0xFF1F6FEB.toInt()
    private val btnTextColor = 0xFFE6EDF3.toInt()
    private val separatorColor = 0xFF30363D.toInt()
    private val cornerRadius = 6

    // ============================================================
    // الأبعاد
    // ============================================================
    private val btnHeightDp = 32
    private val btnWidthDp = 40
    private val barPaddingVerticalDp = 4

    init {
        setBackgroundColor(bgColor)
        isHorizontalScrollBarEnabled = false

        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius.toFloat())
            }
        }

        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(4), dp(barPaddingVerticalDp),
                dp(4), dp(barPaddingVerticalDp)
            )
        }

        addView(container)
        createAllButtons()
    }

    private fun dp(value: Int): Int {
        return Math.round(value * resources.displayMetrics.density)
    }

    // ============================================================
    // إنشاء الأزرار
    // ============================================================
    private fun createAllButtons() {
        // ============================================================
        // 1) الأقواس
        // ============================================================
        addSmallButton("(")
        addSmallButton(")")
        addSmallButton("{")
        addSmallButton("}")
        addSmallButton("[")
        addSmallButton("]")
        addSmallSeparator()

        // ============================================================
        // 2) عمليات حسابية
        // ============================================================
        addSmallButton("=")
        addSmallButton("+")
        addSmallButton("-")
        addSmallButton("*")
        addSmallButton("/")
        addSmallSeparator()

        // ============================================================
        // 3) علامات ترقيم
        // ============================================================
        addSmallButton("\"")
        addSmallButton("'")
        addSmallButton(";")
        addSmallButton(",")
        addSmallButton(".")
        addSmallSeparator()

        // ============================================================
        // 4) منطق
        // ============================================================
        addSmallButton("!")
        addSmallButton("?")
        addSmallButton("&&")
        addSmallButton("||")
        addSmallSeparator()

        // ============================================================
        // 5) مسافات وأسطر
        // ============================================================
        addSmallButton("↵")
        addSmallButton("⇥")
        addSmallSeparator()

        // ============================================================
        // 6) Undo / Redo
        // ============================================================
        addSmallButton("↩")
        addSmallButton("↪")
        addSmallSeparator()

        // ============================================================
        // 7) Zoom
        // ============================================================
        addSmallButton("➕")
        addSmallButton("➖")
        addSmallSeparator()

        // ============================================================
        // 8) Find + Completions
        // ============================================================
        addSmallButton("🔍")
        addSmallButton("💡")
        addSmallSeparator()

        // ============================================================
        // 9) ⭐ Copy / Cut / Paste / Select All
        // ============================================================
        addSmallButton("⧉")   // نسخ
        addSmallButton("✂")   // قطع
        addSmallButton("📋")  // لصق
        addSmallButton("⬚")   // تحديد الكل
    }

    // ============================================================
    // زر صغير
    // ============================================================
    private fun addSmallButton(symbol: String) {
        val button = Button(context).apply {
            text = symbol
            setTextSize(11f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(2), 0, dp(2), 0)
            minimumWidth = 0
            minimumHeight = 0
            height = dp(btnHeightDp)
            width = dp(btnWidthDp)

            background = GradientDrawable().apply {
                setColor(btnColor)
                cornerRadius = dp(this@CodeToolbar.cornerRadius).toFloat()
            }

            setTextColor(btnTextColor)
            isAllCaps = false

            setOnClickListener { v ->
                // تأثير الضغط
                background = GradientDrawable().apply {
                    setColor(btnPressedColor)
                    cornerRadius = dp(this@CodeToolbar.cornerRadius).toFloat()
                }
                v.postDelayed({
                    background = GradientDrawable().apply {
                        setColor(btnColor)
                        cornerRadius = dp(this@CodeToolbar.cornerRadius).toFloat()
                    }
                }, 120)

                handleButtonClick(symbol)
            }
        }

        val params = LinearLayout.LayoutParams(
            dp(btnWidthDp), dp(btnHeightDp)
        ).apply {
            setMargins(dp(1), 0, dp(1), 0)
        }
        container.addView(button, params)
    }

    // ============================================================
    // فاصل
    // ============================================================
    private fun addSmallSeparator() {
        val separator = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(16)).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            setBackgroundColor(separatorColor)
        }
        container.addView(separator)
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
    // معالجة الضغطات
    // ============================================================
    private fun handleButtonClick(symbol: String) {
        val editor = editorView ?: return

        when (symbol) {
            // ============================================================
            // ↩ تراجع
            // ============================================================
            "↩" -> {
                editor.undo()
                return
            }

            // ============================================================
            // ↪ إعادة
            // ============================================================
            "↪" -> {
                editor.redo()
                return
            }

            // ============================================================
            // ➕ تكبير
            // ============================================================
            "➕" -> {
                editor.zoomIn()
                return
            }

            // ============================================================
            // ➖ تصغير
            // ============================================================
            "➖" -> {
                editor.zoomOut()
                return
            }

            // ============================================================
            // 🔍 بحث
            // ============================================================
            "🔍" -> {
                val ctx = context
                if (ctx is MainActivity) ctx.showFindBar()
                return
            }

            // ============================================================
            // 💡 إكمال
            // ============================================================
            "💡" -> {
                editor.showCompletions()
                return
            }

            // ============================================================
            // ⭐ ⧉ نسخ
            // ============================================================
            "⧉" -> {
                editor.copySelection()
                return
            }

            // ============================================================
            // ⭐ ✂ قطع
            // ============================================================
            "✂" -> {
                editor.cutSelection()
                return
            }

            // ============================================================
            // ⭐ 📋 لصق
            // ============================================================
            "📋" -> {
                editor.pasteFromClipboard()
                return
            }

            // ============================================================
            // ⭐ ⬚ تحديد الكل
            // ============================================================
            "⬚" -> {
                editor.selectAllText()
                return
            }
        }

        // ============================================================
        // إدراج الرمز
        // ============================================================
        when (symbol) {
            // الأقواس — أزواج
            "{" -> editor.insertTextAndWrap("{", "}")
            "(" -> editor.insertTextAndWrap("(", ")")
            "[" -> editor.insertTextAndWrap("[", "]")
            "<" -> editor.insertTextAndWrap("<", ">")

            // علامات التنصيص
            "\"" -> editor.insertTextAndWrap("\"", "\"")
            "'" -> editor.insertTextAndWrap("'", "'")

            // سطر جديد
            "↵" -> editor.insertText("\n")

            // مسافة بادئة
            "⇥" -> editor.insertText("    ")

            // رمز عادي
            else -> editor.insertText(symbol)
        }
    }
}