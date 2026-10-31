package com.flutteride.codweva

import android.content.Context
import java.io.File

/**
 * يمثل جذر تخزين (قرص) في مدير الملفات.
 *
 * الأنواع المدعومة:
 *   1. التخزين الداخلي الرئيسي  → /storage/emulated/0/
 *   2. بطاقة SD (إن وُجدت)      → /storage/XXXX-XXXX/
 *   3. مساحة التطبيق الخاصة    → /data/data/<package>/files/
 *
 * الاستخدام:
 *   val roots = FileSystemRoot.detectRoots(context)
 *   for (root in roots) {
 *       // اعرضها في قائمة البداية
 *   }
 */
class FileSystemRoot private constructor(
    /** نوع الجذر. */
    val type: Type,
    /** المسار الفعلي. */
    val directory: File?,
    /** الاسم المعروض للمستخدم (بالعربية). */
    val displayName: String,
    /** هل هو متاح للقراءة الآن؟ */
    val available: Boolean,
    /** المساحة الحرة بالبايت (0 إذا فشل الاستعلام). */
    val freeSpace: Long,
    /** المساحة الكلية بالبايت (0 إذا فشل الاستعلام). */
    val totalSpace: Long
) {

    // ============================================================
    // الأنواع
    // ============================================================
    enum class Type {
        INTERNAL,       // التخزين الداخلي الرئيسي
        SD_CARD,        // بطاقة SD
        APP_PRIVATE     // مساحة التطبيق الخاصة
    }

    // ============================================================
    // معلومات مقروءة
    // ============================================================

    /**
     * وصف المساحة المتاحة بصيغة مقروءة.
     * مثال: "12.4 GB متاحة من 64 GB"
     */
    val spaceInfo: String
        get() {
            if (!available || totalSpace <= 0L) return "غير متاح"
            val free = FileSystemNode.formatSize(freeSpace)
            val total = FileSystemNode.formatSize(totalSpace)
            return "$free متاحة من $total"
        }

    /**
     * نسبة الاستخدام (0.0 → 1.0).
     */
    val usedRatio: Float
        get() {
            if (totalSpace <= 0L) return 0f
            val used = (totalSpace - freeSpace).coerceAtLeast(0L)
            return used.toFloat() / totalSpace.toFloat()
        }

    /**
     * هل هذا الجذر هو مساحة التطبيق الخاصة؟
     */
    val isAppPrivate: Boolean
        get() = type == Type.APP_PRIVATE

    /**
     * يُرجع المسار المطلق.
     */
    val absolutePath: String
        get() = directory?.absolutePath ?: ""

    // ============================================================
    // المساواة
    // ============================================================

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileSystemRoot) return false
        return type == other.type && absolutePath == other.absolutePath
    }

    override fun hashCode(): Int {
        return type.hashCode() * 31 + absolutePath.hashCode()
    }

    override fun toString(): String {
        return "FileSystemRoot{$displayName @ $absolutePath}"
    }

    // ============================================================
    // Companion
    // ============================================================
    companion object {

        /**
         * يكتشف كل جذور التخزين المتاحة على الجهاز.
         *
         * @return قائمة (قد تكون فارغة إذا لم يوجد شيء).
         */
        @JvmStatic
        fun detectRoots(context: Context?): List<FileSystemRoot> {
            val roots = mutableListOf<FileSystemRoot>()

            // 1. التخزين الداخلي الرئيسي
            val primary = StoragePermissionHelper.getPrimaryExternalStorage()
            if (primary != null) {
                roots.add(buildRoot(Type.INTERNAL, primary, "التخزين الداخلي"))
            }

            // 2. بطاقة SD (إن وُجدت)
            val sd = StoragePermissionHelper.getSdCardStorage(context)
            if (sd != null && (primary == null || sd.absolutePath != primary.absolutePath)) {
                roots.add(buildRoot(Type.SD_CARD, sd, "بطاقة SD"))
            }

            // 3. مساحة التطبيق الخاصة
            if (context != null) {
                val appDir = StoragePermissionHelper.getAppPrivateStorage(context)
                if (appDir != null && appDir.exists()) {
                    roots.add(buildRoot(Type.APP_PRIVATE, appDir, "مساحة التطبيق"))
                }
            }

            return roots
        }

        /**
         * يبني جذر تخزين من مسار معيّن.
         */
        private fun buildRoot(type: Type, dir: File?, displayName: String): FileSystemRoot {
            val available = dir != null && dir.exists() && dir.canRead()
            var free = 0L
            var total = 0L

            if (available && dir != null) {
                try {
                    free = dir.freeSpace
                    total = dir.totalSpace
                } catch (_: Exception) {
                }
            }

            return FileSystemRoot(
                type = type,
                directory = dir,
                displayName = displayName,
                available = available,
                freeSpace = free,
                totalSpace = total
            )
        }
    }
}