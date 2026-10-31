package com.flutteride.codweva

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * تخزين دائم (على القرص) لأيقونات APK والقوالب.
 * المفتاح = مسار الملف + آخر تعديل -> أي تغيير = أيقونة جديدة.
 *
 * v2.0: حفظ ذري + فحص سلامة الملفات
 */
object IconCache {

    private const val APK_ICONS_DIR = "apk_icons"
    private const val TEMPLATE_ICONS_DIR = "template_icons"

    /** الحد الأدنى لحجم ملف صالح (100 بايت) */
    private const val MIN_VALID_SIZE = 100L

    // ============================================================
    // أيقونات APK
    // ============================================================

    @JvmStatic
    fun loadApkIcon(context: Context?, apkFile: File?): Bitmap? {
        if (context == null || apkFile == null || !apkFile.isFile) return null
        val cacheFile = apkIconCacheFile(context, apkFile)
        return loadBitmap(cacheFile)
    }

    @JvmStatic
    fun saveApkIcon(context: Context?, apkFile: File?, bitmap: Bitmap?) {
        if (context == null || apkFile == null || bitmap == null || bitmap.isRecycled) return
        val cacheFile = apkIconCacheFile(context, apkFile)
        saveBitmap(cacheFile, bitmap)
    }

    @JvmStatic
    fun removeApkIcon(context: Context?, apkFile: File?) {
        if (context == null || apkFile == null) return
        val cacheFile = apkIconCacheFile(context, apkFile)
        if (cacheFile.exists()) cacheFile.delete()

        val dir = File(context.cacheDir, APK_ICONS_DIR)
        if (dir.isDirectory) {
            val prefix = sha256(apkFile.absolutePath)
            dir.listFiles()?.forEach { child ->
                if (child.name.startsWith("${prefix}_")) child.delete()
            }
        }
    }

    private fun apkIconCacheFile(context: Context, apkFile: File): File {
        val dir = File(context.cacheDir, APK_ICONS_DIR)
        if (!dir.exists()) dir.mkdirs()
        val key = "${sha256(apkFile.absolutePath)}_${apkFile.lastModified()}"
        return File(dir, "$key.png")
    }

    // ============================================================
    // أيقونات القوالب
    // ============================================================

    @JvmStatic
    fun loadTemplateIcon(context: Context?, template: TemplateInfo?): Bitmap? {
        if (context == null || template == null) return null
        val cacheFile = templateIconCacheFile(context, template)
        return loadBitmap(cacheFile)
    }

    @JvmStatic
    fun saveTemplateIcon(context: Context?, template: TemplateInfo?, bitmap: Bitmap?) {
        if (context == null || template == null || bitmap == null || bitmap.isRecycled) return
        val cacheFile = templateIconCacheFile(context, template)
        saveBitmap(cacheFile, bitmap)
    }

    @JvmStatic
    fun removeTemplateIcon(context: Context?, template: TemplateInfo?) {
        if (context == null || template == null) return
        val cacheFile = templateIconCacheFile(context, template)
        if (cacheFile.exists()) cacheFile.delete()
    }

    private fun templateIconCacheFile(context: Context, template: TemplateInfo): File {
        val dir = File(context.cacheDir, TEMPLATE_ICONS_DIR)
        if (!dir.exists()) dir.mkdirs()
        val key = sha256("${template.id}_${template.sizeBytes}")
        return File(dir, "$key.png")
    }

    // ============================================================
    // أدوات مساعدة - محسّنة
    // ============================================================

    /**
     * تحميل آمن للصورة مع فحص سلامة
     */
    private fun loadBitmap(file: File?): Bitmap? {
        if (file == null || !file.isFile || file.length() == 0L) return null

        // فحص الحجم الأدنى
        if (file.length() < MIN_VALID_SIZE) {
            file.delete()
            return null
        }

        return try {
            // 1. اقرأ الأبعاد أولاً
            val opts = BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, opts)

            // 2. تحقق من الأبعاد
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                file.delete()
                return null
            }

            // 3. فك الصورة الفعلي
            opts.inJustDecodeBounds = false
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)

            if (bmp == null || bmp.isRecycled) {
                file.delete()
                return null
            }

            // 4. تحقق من تطابق الأبعاد
            if (bmp.width != opts.outWidth || bmp.height != opts.outHeight) {
                bmp.recycle()
                file.delete()
                return null
            }

            bmp
        } catch (e: Exception) {
            // ملف تالف — احذفه
            if (file.exists()) file.delete()
            null
        }
    }

    /**
     * حفظ ذري للصورة (لا يترك ملفات نصف مكتوبة)
     */
    private fun saveBitmap(file: File?, bitmap: Bitmap?) {
        if (file == null || bitmap == null || bitmap.isRecycled) return

        file.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }

        // احفظ في ملف مؤقت أولاً
        val tempFile = File(file.absolutePath + ".tmp")
        try {
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            return
        }

        // تحقق من أن الملف المؤقت صالح
        if (!tempFile.exists() || tempFile.length() < MIN_VALID_SIZE) {
            if (tempFile.exists()) tempFile.delete()
            return
        }

        // نقل ذري
        try {
            if (file.exists()) file.delete()
            if (!tempFile.renameTo(file)) {
                // فشل rename — انسخ يدويًا
                copyFile(tempFile, file)
                tempFile.delete()
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    /**
     * نسخة احتياطية للـ rename
     */
    private fun copyFile(source: File, target: File) {
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(8192)
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun sha256(value: String?): String {
        if (value == null) return "null"
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder()
            for (b in bytes) sb.append(String.format(Locale.US, "%02x", b))
            sb.toString()
        } catch (e: Exception) {
            Integer.toHexString(value.hashCode())
        }
    }

    // ============================================================
    // تنظيف كامل (اختياري)
    // ============================================================

    @JvmStatic
    fun clearAll(context: Context?) {
        if (context == null) return
        deleteRecursive(File(context.cacheDir, APK_ICONS_DIR))
        deleteRecursive(File(context.cacheDir, TEMPLATE_ICONS_DIR))
    }

    private fun deleteRecursive(file: File?) {
        if (file == null || !file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        file.delete()
    }
}