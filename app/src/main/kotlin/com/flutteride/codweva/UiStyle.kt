package com.flutteride.codweva

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * نظام التصميم المشترك للواجهة.
 * لا يُستخدم لتلوين نصوص البرمجة داخل المحرر.
 */
object UiStyle {

    // ═══════════════════════════════════════════════════════
    // الألوان الأساسية
    // ═══════════════════════════════════════════════════════
    const val BACKGROUND = 0xFF0D1117.toInt()
    const val SURFACE = 0xFF161B22.toInt()
    const val SURFACE_ALT = 0xFF21262D.toInt()
    const val PRIMARY = 0xFF1F6FEB.toInt()
    const val TEXT = 0xFFE6EDF3.toInt()
    const val MUTED = TEXT
    const val BORDER = 0xFF30363D.toInt()
    const val ACCENT = 0xFF58A6FF.toInt()

    // ============================================================
    // أدوات أساسية
    // ============================================================

    @JvmStatic
    fun dp(context: Context?, value: Int): Int {
        if (context == null) return value
        return Math.round(value * context.resources.displayMetrics.density)
    }

    @JvmStatic
    fun rounded(
        context: Context?,
        color: Int,
        radiusDp: Int,
        strokeColor: Int,
        strokeDp: Int
    ): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        if (context != null) {
            drawable.cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeDp > 0) {
                drawable.setStroke(dp(context, strokeDp), strokeColor)
            }
        }
        return drawable
    }

    @JvmStatic
    fun applySurface(
        view: View?,
        context: Context?,
        color: Int,
        radiusDp: Int,
        border: Boolean
    ) {
        if (view == null || context == null) return
        view.background = rounded(context, color, radiusDp, BORDER, if (border) 1 else 0)
    }

    // ============================================================
    // نصوص
    // ============================================================

    @JvmStatic
    fun title(context: Context, text: String?, size: Float): TextView {
        val view = TextView(context)
        view.text = text.orEmpty()
        view.setTextColor(TEXT)
        view.setTextSize(size)
        view.typeface = Typeface.DEFAULT_BOLD
        view.includeFontPadding = false
        return view
    }

    @JvmStatic
    fun text(context: Context, value: String?, size: Float): TextView {
        val view = TextView(context)
        view.text = value.orEmpty()
        view.setTextColor(TEXT)
        view.setTextSize(size)
        view.includeFontPadding = false
        return view
    }

    @JvmStatic
    fun sectionLabel(context: Context, value: String?): TextView {
        val view = text(context, value, 12f)
        view.typeface = Typeface.DEFAULT_BOLD
        view.letterSpacing = 0.02f
        return view
    }

    // ============================================================
    // حقول الإدخال
    // ============================================================

    @JvmStatic
    fun field(context: Context, hint: String?): EditText {
        val field = EditText(context)
        field.hint = hint.orEmpty()
        field.setHintTextColor(TEXT)
        field.setTextColor(TEXT)
        field.setTextSize(14f)
        field.isSingleLine = true
        field.setPadding(
            dp(context, 14), dp(context, 12),
            dp(context, 14), dp(context, 12)
        )
        field.background = rounded(context, SURFACE, 14, BORDER, 1)
        return field
    }

    // ============================================================
    // فواصل ومسافات
    // ============================================================

    @JvmStatic
    fun divider(context: Context): View {
        val divider = View(context)
        divider.setBackgroundColor(BORDER)
        divider.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(context, 1)
        )
        return divider
    }

    @JvmStatic
    fun spacer(context: Context, heightDp: Int): View {
        val spacer = View(context)
        spacer.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(context, heightDp)
        )
        return spacer
    }

    // ============================================================
    // أزرار
    // ============================================================

    @JvmStatic
    fun button(context: Context, text: String?, primary: Boolean): Button {
        val button = Button(context)
        button.text = text.orEmpty()
        applyButton(button, context, primary)
        return button
    }

    @JvmStatic
    fun applyButton(button: Button?, context: Context?, primary: Boolean) {
        if (button == null || context == null) return
        button.setTextColor(TEXT)
        button.isAllCaps = false
        button.setTextSize(14f)
        button.gravity = Gravity.CENTER
        button.minHeight = dp(context, 44)
        button.minimumHeight = dp(context, 44)
        button.setPadding(
            dp(context, 14), dp(context, 6),
            dp(context, 14), dp(context, 6)
        )
        button.background = rounded(
            context,
            if (primary) PRIMARY else SURFACE_ALT,
            14,
            if (primary) ACCENT else BORDER,
            1
        )
    }

    /** زر إجراء أساسي (أزرق) — للاستخدام في الإجراءات الرئيسية */
    @JvmStatic
    fun primaryButton(context: Context, text: String?): Button {
        val btn = Button(context)
        btn.text = text.orEmpty()
        btn.setTextColor(0xFFFFFFFF.toInt())
        btn.setTextSize(13f)
        btn.isAllCaps = false
        btn.typeface = Typeface.DEFAULT_BOLD
        btn.gravity = Gravity.CENTER
        btn.minHeight = dp(context, 44)
        btn.minimumHeight = dp(context, 44)
        btn.setPadding(dp(context, 12), 0, dp(context, 12), 0)
        btn.background = rounded(context, PRIMARY, 12, ACCENT, 1)
        return btn
    }

    /** زر إجراء خفيف (شفاف مع حدود) — للاستخدام الثانوي */
    @JvmStatic
    fun ghostButton(context: Context, text: String?): Button {
        val btn = Button(context)
        btn.text = text.orEmpty()
        btn.setTextColor(TEXT)
        btn.setTextSize(13f)
        btn.isAllCaps = false
        btn.gravity = Gravity.CENTER
        btn.minHeight = dp(context, 44)
        btn.minimumHeight = dp(context, 44)
        btn.setPadding(dp(context, 12), 0, dp(context, 12), 0)
        btn.background = rounded(context, 0x00000000, 12, BORDER, 1)
        return btn
    }

    // ============================================================
    // أيقونات مرسومة (بدون إيموجي)
    // ============================================================

    /** دائرة ملونة بحرف أول — لمشروع أو عنصر */
    @JvmStatic
    fun circleIcon(context: Context, letter: String?, color: Int): TextView {
        val view = TextView(context)
        view.text = letter ?: "?"
        view.setTextColor(0xFFFFFFFF.toInt())
        view.setTextSize(20f)
        view.typeface = Typeface.DEFAULT_BOLD
        view.gravity = Gravity.CENTER
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.OVAL
        gd.setColor(color)
        view.background = gd
        val size = dp(context, 48)
        view.layoutParams = LinearLayout.LayoutParams(size, size)
        return view
    }

    /** مربع بزوايا دائرية + رمز Unicode بسيط */
    @JvmStatic
    fun squareIcon(context: Context, symbol: String?, color: Int): TextView {
        val view = TextView(context)
        view.text = symbol.orEmpty()
        view.setTextColor(0xFFFFFFFF.toInt())
        view.setTextSize(16f)
        view.typeface = Typeface.DEFAULT_BOLD
        view.gravity = Gravity.CENTER
        val gd = GradientDrawable()
        gd.cornerRadius = dp(context, 12).toFloat()
        gd.setColor(color)
        view.background = gd
        val size = dp(context, 40)
        view.layoutParams = LinearLayout.LayoutParams(size, size)
        return view
    }

    /** زر دائري نصي — للاستخدام في AppBar (بحث، إعدادات) */
    @JvmStatic
    fun circleTextButton(context: Context, symbol: String?, bgColor: Int): TextView {
        val view = TextView(context)
        view.text = symbol.orEmpty()
        view.setTextColor(0xFFFFFFFF.toInt())
        view.setTextSize(14f)
        view.typeface = Typeface.DEFAULT_BOLD
        view.gravity = Gravity.CENTER
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.OVAL
        gd.setColor(bgColor)
        view.background = gd
        val size = dp(context, 40)
        view.layoutParams = LinearLayout.LayoutParams(size, size)
        view.isClickable = true
        view.isFocusable = true
        return view
    }

    // ============================================================
    // بطاقات
    // ============================================================

    /** بطاقة مشروع أنيقة */
    @JvmStatic
    fun projectCard(context: Context): LinearLayout {
        val card = LinearLayout(context)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(
            dp(context, 16), dp(context, 14),
            dp(context, 16), dp(context, 14)
        )
        card.background = rounded(context, SURFACE, 16, BORDER, 1)
        card.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        card.isClickable = true
        card.isFocusable = true
        return card
    }

    // ============================================================
    // الحوارات
    // ============================================================

    @JvmStatic
    fun styleDialog(dialog: AlertDialog?) {
        if (dialog == null) return
        val context = dialog.context

        dialog.window?.setBackgroundDrawable(rounded(context, BACKGROUND, 18, BORDER, 1))

        val titleId = context.resources.getIdentifier(
            "android:id/alertTitle", null, null
        )
        val title = if (titleId == 0) null else dialog.findViewById<TextView>(titleId)
        title?.let {
            it.setTextColor(TEXT)
            it.typeface = Typeface.DEFAULT_BOLD
        }

        val message = dialog.findViewById<TextView>(android.R.id.message)
        message?.setTextColor(TEXT)

        styleButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), TEXT)
        styleButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), TEXT)
        styleButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), TEXT)

        val list = dialog.listView
        if (list != null) {
            list.setBackgroundColor(BACKGROUND)
            list.divider = ColorDrawable(BORDER)
            list.dividerHeight = dp(context, 1)
            list.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    child?.let { styleTextTree(it, TEXT) }
                }
                override fun onChildViewRemoved(parent: View?, child: View?) {}
            })
            for (i in 0 until list.childCount) {
                styleTextTree(list.getChildAt(i), TEXT)
            }
            list.post {
                for (i in 0 until list.childCount) {
                    styleTextTree(list.getChildAt(i), TEXT)
                }
            }
        }

        dialog.window?.decorView?.post {
            dialog.window?.decorView?.let { styleTextTree(it, TEXT) }
            styleButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), TEXT)
            styleButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), TEXT)
            styleButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), TEXT)
        }
    }

    @JvmStatic
    fun styleDialog(dialog: android.app.AlertDialog?) {
        if (dialog == null) return
        val context = dialog.context

        dialog.window?.setBackgroundDrawable(rounded(context, SURFACE, 18, BORDER, 1))

        val titleId = context.resources.getIdentifier(
            "android:id/alertTitle", null, null
        )
        val title = if (titleId == 0) null else dialog.findViewById<TextView>(titleId)
        title?.let {
            it.setTextColor(TEXT)
            it.typeface = Typeface.DEFAULT_BOLD
        }

        val message = dialog.findViewById<TextView>(android.R.id.message)
        message?.setTextColor(TEXT)

        styleButton(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE), TEXT)
        styleButton(dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE), TEXT)
        styleButton(dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL), TEXT)

        val list = dialog.listView
        if (list != null) {
            list.setBackgroundColor(SURFACE)
            list.divider = ColorDrawable(BORDER)
            list.dividerHeight = dp(context, 1)
        }

        dialog.window?.decorView?.post {
            dialog.window?.decorView?.let { styleTextTree(it, TEXT) }
            styleButton(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE), TEXT)
            styleButton(dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE), TEXT)
            styleButton(dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL), TEXT)
        }
    }

    private fun styleButton(button: Button?, color: Int) {
        if (button != null) {
            button.setTextColor(color)
            button.isAllCaps = false
        }
    }

    private fun styleTextTree(view: View?, color: Int) {
        when (view) {
            is TextView -> view.setTextColor(color)
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    styleTextTree(view.getChildAt(i), color)
                }
            }
        }
    }

    // ============================================================
    // Spinner والمحولات
    // ============================================================

    @JvmStatic
    fun <T> spinnerAdapter(context: Context, values: Array<T>): ArrayAdapter<T> {
        return object : ArrayAdapter<T>(context, android.R.layout.simple_spinner_item, values) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(TEXT)
                view.setTextSize(14f)
                view.gravity = Gravity.CENTER_VERTICAL
                view.setPadding(
                    dp(context, 14), dp(context, 10),
                    dp(context, 14), dp(context, 10)
                )
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(TEXT)
                view.setTextSize(14f)
                view.gravity = Gravity.CENTER_VERTICAL
                view.setPadding(
                    dp(context, 20), dp(context, 14),
                    dp(context, 20), dp(context, 14)
                )
                view.setBackgroundColor(SURFACE)
                return view
            }
        }
    }

    @JvmStatic
    fun dialogListAdapter(context: Context, values: Array<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(
            context, android.R.layout.simple_list_item_1, values
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = super.getView(position, convertView, parent) as TextView
                styleDialogRow(row)
                return row
            }
        }
    }

    @JvmStatic
    fun dialogChoiceAdapter(context: Context, values: Array<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(
            context, android.R.layout.simple_list_item_single_choice, values
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = super.getView(position, convertView, parent) as TextView
                styleDialogRow(row)
                return row
            }
        }
    }

    private fun styleDialogRow(row: TextView) {
        row.setTextColor(TEXT)
        row.setTextSize(15f)
        row.setBackgroundColor(SURFACE)
        row.setPadding(24, 18, 24, 18)
        row.isSingleLine = false
    }

    @JvmStatic
    fun styleSpinner(spinner: Spinner?) {
        if (spinner != null) {
            val context = spinner.context
            spinner.background = rounded(context, SURFACE, 12, BORDER, 1)
            spinner.setPadding(
                dp(context, 10), 0,
                dp(context, 10), 0
            )
        }
    }
}