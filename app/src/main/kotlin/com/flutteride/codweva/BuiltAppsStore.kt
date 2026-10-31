package com.flutteride.codweva

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/**
 * مخزن داخلي مستقل لنسخ APK التي نجح بناؤها.
 *
 * ملاحظة:
 *   - الملفات تُحفظ داخل filesDir/built_apps
 *   - لا يمكن الوصول إليها من تطبيقات أخرى.
 *   - sorted حسب lastModified (الأحدث أولاً).
 */
class BuiltAppsStore(context: Context) {

    private val libraryDir: File =
        File(context.filesDir, "built_apps").apply {
            if (!exists()) mkdirs()
        }

    // ============================================================
    // حفظ APK جديد
    // ============================================================
    @Throws(IOException::class)
    fun save(apkFile: File?, projectName: String?): File {
        if (apkFile == null || !apkFile.exists() || apkFile.length() == 0L) {
            throw IOException("ملف APK غير موجود أو فارغ")
        }
        if (!libraryDir.exists() && !libraryDir.mkdirs()) {
            throw IOException("تعذر إنشاء مكتبة التطبيقات")
        }

        var safeName = projectName?.trim() ?: "app"
        safeName = safeName.replace(Regex("[^a-zA-Z0-9._-]+"), "_")
        if (safeName.isEmpty()) safeName = "app"

        val destination = File(
            libraryDir,
            "${safeName}_${System.currentTimeMillis()}.apk"
        )
        copy(apkFile, destination)
        return destination
    }

    // ============================================================
    // قائمة APK
    // ============================================================
    fun list(): List<File> {
        val files = libraryDir.listFiles { _, name ->
            name != null && name.lowercase(Locale.US).endsWith(".apk")
        }
        val result = files?.toMutableList() ?: mutableListOf()
        result.sortWith(compareByDescending { it.lastModified() })
        return result
    }

    // ============================================================
    // حذف APK
    // ============================================================
    @Throws(IOException::class)
    fun delete(file: File?) {
        val safeFile = safeFile(file)
        if (!safeFile.exists() || !safeFile.delete()) {
            throw IOException("تعذر حذف التطبيق")
        }
    }

    // ============================================================
    // مسار المكتبة
    // ============================================================
    val getLibraryDir: File
        get() = libraryDir

    // ============================================================
    // أدوات داخلية
    // ============================================================
    @Throws(IOException::class)
    private fun safeFile(file: File?): File {
        if (file == null) throw IOException("ملف غير صالح")

        val root = libraryDir.canonicalPath + File.separator
        val path = file.canonicalPath
        if (!path.startsWith(root) || !path.lowercase(Locale.US).endsWith(".apk")) {
            throw IOException("مسار التطبيق خارج المكتبة")
        }
        return file
    }

    @Throws(IOException::class)
    private fun copy(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } != -1) {
                    output.write(buffer, 0, count)
                }
                output.flush()
            }
        }
    }
}