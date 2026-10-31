package com.flutteride.codweva

/**
 * حالة ملف مفتوح داخل محرر التبويبات.
 *
 * ملاحظة:
 *   - content يُخزَّن في الذاكرة فقط (لا يُحفظ في SharedPreferences).
 *   - عند الحفظ، يُكتب في الملف على القرص.
 *   - dirty = يحتوي تغييرات غير محفوظة.
 */
class EditorTabState(
    /** المسار المطلق للملف على القرص. */
    val absolutePath: String
) {

    /** محتوى الملف الحالي في الذاكرة. */
    var content: String = ""

    /** بداية التحديد في المحرر. */
    var selectionStart: Int = 0

    /** نهاية التحديد في المحرر. */
    var selectionEnd: Int = 0

    /** موضع التمرير العمودي. */
    var scrollY: Int = 0

    /** هل تم تحميل المحتوى من القرص؟ */
    var loaded: Boolean = false

    /** هل هناك تغييرات غير محفوظة؟ */
    var dirty: Boolean = false

    override fun toString(): String {
        return "EditorTabState($absolutePath, dirty=$dirty, loaded=$loaded)"
    }
}