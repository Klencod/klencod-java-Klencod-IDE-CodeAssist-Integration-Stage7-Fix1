package com.flutteride.codweva

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * حوارات توليد البرومبت الجاهز.
 * يوفّر 3 أنماط تصميم لتطبيقات Flutter.
 */
object Prompt {

    // أنماط التصميم الثلاثة (بدون رموز)
    private val STYLE_NAMES = arrayOf(
        "أنيق وبسيط",
        "عصري وجريء",
        "نظيف وعملي"
    )

    private val STYLE_PROMPTS = arrayOf(
        // النمط الأول: أنيق
        "Design a Flutter application with an elegant and minimal interface, relying on wide white spaces,\n" +
                "calm colors (such as white, light gray, and a soft primary color).\n" +
                "Use clean fonts and appropriate font sizes, with a focus on proper spacing between elements.\n" +
                "Make the design comfortable for the eyes, with touches of subtle shadows and rounded corners.\n" +
                "Ensure the experience is smooth and intuitive, so the user feels comfortable and confident.\n" +
                "\nDetails of the application I want to design:",

        // النمط الثاني: عصري
        "Design a Flutter application with a modern and bold interface, relying on vibrant and harmonious colors,\n" +
                "with color gradients and non-traditional geometric shapes.\n" +
                "Use modern and bold fonts for titles, with distinctive icons and light animation upon interaction.\n" +
                "Make the design dynamic and attractive, using cards and buttons with 3D effects.\n" +
                "Ensure the design is enjoyable and motivating to use, with simple surprising elements.\n" +
                "\nDetails of the application I want to design:",

        // النمط الثالث: عملي
        "Design a Flutter application with a clean and functional interface, focusing on ease of use and clarity.\n" +
                "Rely on high contrast between background and text, with neutral colors and gray shades,\n" +
                "and a distinctive color for interactive elements (buttons, links, notifications).\n" +
                "Use an organized layout with clear headings and logical content division.\n" +
                "Ensure all interactive elements are large enough, with appropriate spacing,\n" +
                "and a distraction-free experience, so the user can complete tasks quickly and smoothly.\n" +
                "\nDetails of the application I want to design:"
    )

    // ============================================================
    // الواجهة الرئيسية
    // ============================================================
    @JvmStatic
    fun showPromptDialog(context: Context) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("اختر نمط التصميم")

        builder.setAdapter(UiStyle.dialogListAdapter(context, STYLE_NAMES)) { _, which ->
            showDescriptionInput(context, which)
        }

        builder.setNegativeButton("إلغاء", null)
        val dialog = builder.create()
        dialog.show()
        UiStyle.styleDialog(dialog)
    }

    // ============================================================
    // صندوق إدخال وصف التطبيق
    // ============================================================
    private fun showDescriptionInput(context: Context, styleIndex: Int) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("صف تطبيقك")

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 18))
        }

        val input = EditText(context).apply {
            hint = "مثال: تطبيق لإدارة المهام مع الإشعارات..."
            setHintTextColor(0xFFE6EDF3.toInt())
            setTextColor(0xFFE6EDF3.toInt())
            background = UiStyle.rounded(context, 0xFF161B22.toInt(), 12, 0xFF30363D.toInt(), 1)
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            minLines = 4
            maxLines = 8
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        layout.addView(input)

        val scrollView = ScrollView(context)
        scrollView.addView(layout)
        builder.setView(scrollView)

        builder.setPositiveButton("إنشاء البرومبت") { _, _ ->
            val userDescription = input.text.toString().trim()
            if (userDescription.isEmpty()) {
                Toast.makeText(context, "اكتب وصف التطبيق أولًا", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            val finalPrompt = buildFinalPrompt(styleIndex, userDescription)
            showFinalPrompt(context, finalPrompt)
        }

        builder.setNegativeButton("إلغاء", null)
        val dialog = builder.create()
        dialog.show()
        UiStyle.styleDialog(dialog)
    }

    // ============================================================
    // دمج النمط مع وصف المستخدم
    // ============================================================
    private fun buildFinalPrompt(styleIndex: Int, userDescription: String): String {
        return STYLE_PROMPTS[styleIndex] + "\n\n" + userDescription
    }

    // ============================================================
    // عرض البرومبت النهائي مع زر النسخ
    // ============================================================
    private fun showFinalPrompt(context: Context, prompt: String) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("البرومبت الجاهز")

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 16))
        }

        val promptView = EditText(context).apply {
            setText(prompt)
            setTextColor(0xFFE0E0E0.toInt())
            background = UiStyle.rounded(context, 0xFF0D1117.toInt(), 12, 0xFF30363D.toInt(), 1)
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            minLines = 8
            maxLines = 15
            gravity = Gravity.TOP
            typeface = Typeface.MONOSPACE
            setTextSize(12f)
            isFocusable = false
            isFocusableInTouchMode = false
            keyListener = null // منع التعديل
        }
        layout.addView(promptView)

        val scrollView = ScrollView(context)
        scrollView.addView(layout)
        builder.setView(scrollView)

        builder.setPositiveButton("نسخ") { _, _ ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            val clip = ClipData.newPlainText("prompt", prompt)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "تم نسخ البرومبت", Toast.LENGTH_SHORT).show()
        }

        builder.setNegativeButton("إغلاق", null)
        val dialog = builder.create()
        dialog.show()
        UiStyle.styleDialog(dialog)
    }

    // ============================================================
    // أدوات
    // ============================================================
    private fun dp(context: Context, value: Int): Int {
        return Math.round(value * context.resources.displayMetrics.density)
    }
}