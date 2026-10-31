package com.flutteride.codweva

import java.io.File
import java.util.Locale

/**
 * نموذج بيانات القالب.
 * يُقرأ من assets/templates/ أو من داخل ZIP.
 */
class TemplateInfo(
    /** معرّف القالب (اسم ملف ZIP بدون .zip) */
    val id: String,

    /** ملف ZIP المصدر (null للقوالب المدمجة) */
    val zipFile: File?,

    /** المسار داخل assets (مثلاً "templates/quran.zip") — null إذا كان ملفاً خارجياً */
    val assetsPath: String?
) {

    /** الاسم المعروض (بالعربية إن وُجد) */
    var displayName: String = capitalize(id)

    /** الاسم الإنجليزي (اختياري) */
    var englishName: String = capitalize(id)

    /** حجم القالب بالبايت */
    var sizeBytes: Long = zipFile?.takeIf { it.exists() }?.length() ?: 0L

    /** هل هو من assets (لا يمكن حذفه) */
    val isBuiltIn: Boolean = assetsPath != null

    // ============================================================
    // مسارات مؤقتة للأيقونة والمعاينات (بعد فكّها)
    // ============================================================
    var iconFile: File? = null
    val previewFiles: MutableList<File> = mutableListOf()

    // ============================================================
    // مساعدات
    // ============================================================

    /** حجم القالب بصيغة مقروءة */
    val sizeLabel: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 ->
                String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0)
            else ->
                String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
        }

    override fun toString(): String {
        return "TemplateInfo{$id, name=$displayName, size=$sizeBytes}"
    }

    companion object {
        /** يحوّل "quran" إلى "Quran" */
        private fun capitalize(value: String?): String {
            if (value.isNullOrEmpty()) return ""
            return value.substring(0, 1).uppercase(Locale.US) + value.substring(1)
        }
    }
}