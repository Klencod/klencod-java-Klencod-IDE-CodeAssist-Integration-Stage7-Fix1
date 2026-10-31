package com.flutteride.codweva

import android.content.Context
import android.content.SharedPreferences

/**
 * ثيمات محرر الكود.
 *
 * كل ثيم يعرّف 10 ألوان:
 *   - background        : خلفية المحرر
 *   - defaultText       : النص العادي
 *   - keyword           : الكلمات المفتاحية
 *   - function          : الدوال والـ Widgets
 *   - string            : النصوص
 *   - number            : الأرقام
 *   - operator          : العمليات
 *   - comment           : التعليقات
 *   - lineNumber        : أرقام الأسطر
 *   - tag               : الوسوم (HTML/CSS/Markdown)
 *
 * v2.0 — ألوان احترافية جديدة:
 *   - Dark Blue  (افتراضي)
 *   - Purple     (Dracula)
 *   - Vivid      (Monokai)
 *   - Light      (GitHub Light)
 *   - Blue       (Nord)
 *   - Green      (Solarized Dark)
 *   - Charcoal   (VSCode Dark+)
 */
object EditorThemes {

    // ═══════════════════════════════════════════════════════
    // ثوابت معرّفات الثيمات
    // ═══════════════════════════════════════════════════════
    const val DARK_BLUE = "dark_blue"
    const val PURPLE = "purple"
    const val VIVID = "vivid"
    const val LIGHT = "light"
    const val BLUE = "blue"
    const val GREEN = "green"
    const val CHARCOAL = "charcoal"

    const val DEFAULT_THEME = DARK_BLUE

    private const val PREFS = "flutter_ide_prefs"
    private const val KEY_THEME = "editor_theme"

    // ═══════════════════════════════════════════════════════
    // نموذج الثيم
    // ═══════════════════════════════════════════════════════
    class Theme(
        val id: String,
        val displayName: String,
        val background: Int,
        val defaultText: Int,
        val keyword: Int,
        val function: Int,
        val string: Int,
        val number: Int,
        val operator: Int,
        val comment: Int,
        val lineNumber: Int,
        val tag: Int
    )

    // ═══════════════════════════════════════════════════════
    // الثيمات المتاحة
    // ═══════════════════════════════════════════════════════
    private val THEMES: List<Theme> = listOf(

        // 1. Dark Blue (افتراضي)
        Theme(
            DARK_BLUE, "Dark Blue",
            0xFF0D1117.toInt(),   // background
            0xFFE6EDF3.toInt(),   // defaultText
            0xFF58A6FF.toInt(),   // keyword
            0xFF79C0FF.toInt(),   // function
            0xFF4A9EFF.toInt(),   // string
            0xFF6A8CFF.toInt(),   // number
            0xFF1F6FEB.toInt(),   // operator
            0xFF8B949E.toInt(),   // comment
            0xFF484F58.toInt(),   // lineNumber
            0xFF58A6FF.toInt()    // tag
        ),

        // 2. Purple (Dracula)
        Theme(
            PURPLE, "Purple",
            0xFF282A36.toInt(),
            0xFFF8F8F2.toInt(),
            0xFFFF79C6.toInt(),
            0xFF50FA7B.toInt(),
            0xFFF1FA8C.toInt(),
            0xFFBD93F9.toInt(),
            0xFFFF79C6.toInt(),
            0xFF6272A4.toInt(),
            0xFF6272A4.toInt(),
            0xFF8BE9FD.toInt()
        ),

        // 3. Vivid (Monokai)
        Theme(
            VIVID, "Vivid",
            0xFF272822.toInt(),
            0xFFF8F8F2.toInt(),
            0xFFF92672.toInt(),
            0xFFA6E22E.toInt(),
            0xFFE6DB74.toInt(),
            0xFFAE81FF.toInt(),
            0xFFF92672.toInt(),
            0xFF75715E.toInt(),
            0xFF75715E.toInt(),
            0xFFFD971F.toInt()
        ),

        // 4. Light (GitHub Light)
        Theme(
            LIGHT, "Light",
            0xFFFFFFFF.toInt(),
            0xFF24292F.toInt(),
            0xFFCF222E.toInt(),
            0xFF8250DF.toInt(),
            0xFF0A3069.toInt(),
            0xFF0550AE.toInt(),
            0xFFCF222E.toInt(),
            0xFF6E7781.toInt(),
            0xFF8C959F.toInt(),
            0xFF116329.toInt()
        ),

        // 5. Blue (Nord)
        Theme(
            BLUE, "Blue",
            0xFF2E3440.toInt(),
            0xFFD8DEE9.toInt(),
            0xFF81A1C1.toInt(),
            0xFF88C0D0.toInt(),
            0xFFA3BE8C.toInt(),
            0xFFB48EAD.toInt(),
            0xFF81A1C1.toInt(),
            0xFF616E88.toInt(),
            0xFF4C566A.toInt(),
            0xFFEBCB8B.toInt()
        ),

        // 6. Green (Solarized Dark)
        Theme(
            GREEN, "Green",
            0xFF002B36.toInt(),
            0xFF93A1A1.toInt(),
            0xFF859900.toInt(),
            0xFF268BD2.toInt(),
            0xFF2AA198.toInt(),
            0xFFD33682.toInt(),
            0xFF859900.toInt(),
            0xFF586E75.toInt(),
            0xFF586E75.toInt(),
            0xFFB58900.toInt()
        ),

        // 7. Charcoal (VSCode Dark+)
        Theme(
            CHARCOAL, "Charcoal",
            0xFF1E1E1E.toInt(),
            0xFFD4D4D4.toInt(),
            0xFF569CD6.toInt(),
            0xFFDCDCAA.toInt(),
            0xFFCE9178.toInt(),
            0xFFB5CEA8.toInt(),
            0xFFD4D4D4.toInt(),
            0xFF6A9955.toInt(),
            0xFF858585.toInt(),
            0xFF569CD6.toInt()
        )
    )

    // ═══════════════════════════════════════════════════════
    // الوصول
    // ═══════════════════════════════════════════════════════
    @JvmStatic
    fun getAll(): List<Theme> = THEMES.toList()

    @JvmStatic
    fun get(id: String?): Theme {
        if (id == null) return THEMES[0]
        return THEMES.firstOrNull { it.id == id } ?: THEMES[0]
    }

    @JvmStatic
    fun getAllDisplayNames(): Array<String> {
        return THEMES.map { it.displayName }.toTypedArray()
    }

    @JvmStatic
    fun indexOf(id: String?): Int {
        if (id == null) return 0
        val idx = THEMES.indexOfFirst { it.id == id }
        return if (idx < 0) 0 else idx
    }

    // ═══════════════════════════════════════════════════════
    // التخزين
    // ═══════════════════════════════════════════════════════
    @JvmStatic
    fun getSelectedId(context: Context?): String {
        if (context == null) return DEFAULT_THEME
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
    }

    @JvmStatic
    fun setSelectedId(context: Context?, id: String?) {
        if (context == null) return
        val safe = id ?: DEFAULT_THEME
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, safe).apply()
    }

    @JvmStatic
    fun getSelected(context: Context?): Theme {
        return get(getSelectedId(context))
    }
}