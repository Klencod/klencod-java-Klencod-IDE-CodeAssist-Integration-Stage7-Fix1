package com.flutteride.codweva

/**
 * أوضاع تشغيل مساعد الذكاء الاصطناعي.
 */
enum class AiMode(
    val id: String,
    val displayName: String,
    val description: String
) {

    /** مساعد ذكي: يرى المشروع كاملاً في كل رسالة ويعدّل أي ملف. */
    SMART(
        "smart",
        "مساعد ذكي",
        "يرى المشروع كاملاً في كل رسالة. مثالي للمشاريع الصغيرة والمتوسطة."
    ),

    /** وكيل كامل: يستخدم أدوات للقراءة والبحث والاستكشاف. */
    AGENTIC(
        "agentic",
        "وكيل كامل",
        "يستكشف المشروع بنفسه ويقرأ الملفات حسب الحاجة. مثالي للمشاريع الكبيرة."
    );

    companion object {
        @JvmStatic
        fun fromId(id: String?): AiMode {
            if (id == null) return SMART
            return values().firstOrNull { it.id == id } ?: SMART
        }
    }
}