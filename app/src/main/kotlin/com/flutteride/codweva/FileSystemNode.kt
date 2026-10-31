package com.flutteride.codweva

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * يمثل عنصراً واحداً في نظام الملفات (ملف أو مجلد).
 *
 * الغرض: نموذج بيانات بسيط يستخدمه FileManagerAdapter لعرض الصف.
 *
 * الملاحظات:
 *   - lazy-loading: لا نحسب الحجم إلا عند الطلب (sizeBytes).
 *   - دعم المجلدات: يمكن أن يحتوي على items فرعية (اختياري).
 *   - التنبؤ بالمسار: يُخزّن المسار المطلق + الاسم المعروض.
 */
class FileSystemNode private constructor(
    /** الملف الفعلي على القرص. */
    val file: File,
    /** هل هو مجلد؟ (يُحسب مرة واحدة عند الإنشاء). */
    val isDirectory: Boolean,
    /** هل هو ملف مخفي (يبدأ بـ . أو مخفي من النظام)؟ */
    val isHidden: Boolean,
    /** هل يمكن قراءته؟ */
    val canRead: Boolean,
    /** آخر مرة تم تعديله (ms). 0 إذا فشل الاستعلام. */
    val lastModified: Long,
    /** الحجم بالبايت (0 للمجلدات). */
    val sizeBytes: Long
) {

    // ============================================================
    // معلومات الوصول السريع
    // ============================================================

    /** اسم الملف/المجلد. */
    val name: String
        get() = file.name ?: ""

    /** المسار المطلق. */
    val absolutePath: String
        get() = file.absolutePath ?: ""

    /** المسار الأب، أو null إن كان الجذر. */
    val parent: FileSystemNode?
        get() {
            val parentFile = file.parentFile ?: return null
            if (!parentFile.exists()) return null
            return fromFile(parentFile)
        }

    /** هل هو جذر؟ */
    val isRoot: Boolean
        get() = file.parentFile == null

    /** نوع امتداد الملف (بدون نقطة)، أو "" للمجلدات. */
    val extension: String
        get() {
            if (isDirectory) return ""
            val n = name
            if (n.isEmpty()) return ""
            val dot = n.lastIndexOf('.')
            if (dot < 0 || dot == n.length - 1) return ""
            val ext = n.substring(dot + 1).lowercase(Locale.US)
            // حماية: لو الامتداد طويل جداً
            return if (ext.length > 12) ext.substring(0, 12) else ext
        }

    /** الحجم بصيغة مقروءة (0 B، 45 KB، 1.2 MB، ...). */
    val readableSize: String
        get() = if (isDirectory) "مجلد" else formatSize(sizeBytes)

    /** عدد العناصر الفرعية إن كان مجلداً (0 للملفات أو عند فشل القراءة). */
    val childCount: Int
        get() {
            if (!isDirectory) return 0
            return try {
                file.list()?.size ?: 0
            } catch (_: Exception) {
                0
            }
        }

    /** تاريخ التعديل بصيغة مختصرة. */
    val readableDate: String
        get() {
            if (lastModified <= 0) return ""
            return try {
                val fmt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
                fmt.format(Date(lastModified))
            } catch (_: Exception) {
                ""
            }
        }

    // ============================================================
    // المساواة (للـ RecyclerView DiffUtil)
    // ============================================================

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileSystemNode) return false
        return file.absolutePath == other.file.absolutePath
    }

    override fun hashCode(): Int {
        return file.absolutePath.hashCode()
    }

    override fun toString(): String {
        return "FileSystemNode{$name${if (isDirectory) "/" else ""}}"
    }

    // ============================================================
    // Companion
    // ============================================================
    companion object {

        /**
         * ينشئ عقدة من ملف على القرص. آمن تماماً — لا يرمي استثناءات.
         */
        @JvmStatic
        fun fromFile(file: File?): FileSystemNode? {
            if (file == null) return null
            return try {
                val isDir = file.isDirectory
                val hidden = isHiddenFile(file)
                val readable = file.canRead()
                val modified = safeLastModified(file)
                val size = if (isDir) 0L else safeLength(file)

                FileSystemNode(
                    file = file,
                    isDirectory = isDir,
                    isHidden = hidden,
                    canRead = readable,
                    lastModified = modified,
                    sizeBytes = size
                )
            } catch (_: Exception) {
                null
            }
        }

        /**
         * هل الملف مخفي؟
         * - يبدأ بـ "." (مثل .git، .config)
         * - أو علم "hidden" في النظام
         */
        @JvmStatic
        fun isHiddenFile(file: File?): Boolean {
            if (file == null) return false
            return try {
                val name = file.name ?: return false
                if (name.isEmpty()) return false
                if (name.startsWith(".")) return true
                file.isHidden
            } catch (_: Exception) {
                false
            }
        }

        /**
         * تنسيق الحجم بصيغة مقروءة.
         */
        @JvmStatic
        fun formatSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024L * 1024L ->
                    String.format(Locale.US, "%.1f KB", bytes / 1024.0)
                bytes < 1024L * 1024L * 1024L ->
                    String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
                else ->
                    String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            }
        }

        // ============================================================
        // أدوات داخلية آمنة
        // ============================================================
        private fun safeLength(file: File): Long {
            return try {
                file.length()
            } catch (_: Exception) {
                0L
            }
        }

        private fun safeLastModified(file: File): Long {
            return try {
                file.lastModified()
            } catch (_: Exception) {
                0L
            }
        }
    }
}