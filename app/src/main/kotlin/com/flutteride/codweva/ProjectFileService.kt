package com.flutteride.codweva

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * وصول محدود إلى ملفات المشروع فقط.
 * لا يتعامل مع FlutterBuildApi أو إعدادات البناء.
 */
class ProjectFileService(projectRoot: File) {

    /** مجلد المشروع بمسار canonical. */
    val getProjectRoot: File = projectRoot.canonicalFile

    // ============================================================
    // القراءة
    // ============================================================
    @Throws(Exception::class)
    fun readFile(relativePath: String?): String {
        val file = safeFile(relativePath)
        if (!file.isFile) {
            throw Exception("الملف غير موجود: $relativePath")
        }
        if (file.length() > MAX_READ_BYTES) {
            throw Exception("الملف كبير جدًا لإرساله: $relativePath")
        }

        val builder = StringBuilder()
        BufferedReader(
            InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)
        ).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                builder.append(line).append('\n')
            }
        }
        return builder.toString()
    }

    // ============================================================
    // قائمة الملفات
    // ============================================================
    @Throws(Exception::class)
    fun listFiles(): List<String> {
        val result = mutableListOf<String>()
        collect(getProjectRoot, result)
        result.sort()
        return result
    }

    // ============================================================
    // الفحص
    // ============================================================
    @Throws(Exception::class)
    fun exists(relativePath: String?): Boolean {
        return safeFile(relativePath).exists()
    }

    // ============================================================
    // الحذف
    // ============================================================
    @Throws(Exception::class)
    fun deleteFile(relativePath: String?) {
        val file = safeFile(relativePath)
        if (!file.isFile) {
            throw Exception("الملف غير موجود: $relativePath")
        }
        backupExisting(file, relativePath ?: "")
        if (!file.delete()) {
            throw Exception("تعذر حذف الملف: $relativePath")
        }
        cleanupEmptyParentDirs(file.parentFile)
    }

    // ============================================================
    // الكتابة
    // ============================================================
    @Throws(Exception::class)
    fun writeFile(relativePath: String?, content: String?) {
        val file = safeFile(relativePath)
        val safeContent = content ?: ""

        if (safeContent.toByteArray(StandardCharsets.UTF_8).size > 1024 * 1024) {
            throw Exception("التغيير كبير جدًا")
        }

        if (file.exists()) {
            backupExisting(file, relativePath ?: "")
        }

        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw Exception("تعذر إنشاء مجلد الملف")
        }

        FileOutputStream(file).use { output ->
            output.write(safeContent.toByteArray(StandardCharsets.UTF_8))
            output.flush()
        }
    }

    // ============================================================
    // نقل / إعادة تسمية ملف
    // ============================================================
    @Throws(Exception::class)
    fun moveFile(fromRelative: String?, toRelative: String?) {
        val from = safeFile(fromRelative)
        val to = safeFile(toRelative)

        if (!from.isFile) {
            throw Exception("الملف المصدر غير موجود: $fromRelative")
        }
        if (to.exists()) {
            throw Exception("الملف الوجهة موجود بالفعل: $toRelative")
        }

        val parent = to.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw Exception("تعذر إنشاء مجلد الوجهة")
        }

        if (from.renameTo(to)) {
            cleanupEmptyParentDirs(from.parentFile)
            return
        }

        copyFile(from, to)
        if (!from.delete()) {
            try {
                to.delete()
            } catch (_: Exception) {
            }
            throw Exception("تعذر حذف الملف الأصلي بعد النقل")
        }
        cleanupEmptyParentDirs(from.parentFile)
    }

    // ============================================================
    // safeFile — الحماية الأساسية
    // ============================================================
    @Throws(Exception::class)
    fun safeFile(relativePath: String?): File {
        if (relativePath.isNullOrBlank()) {
            throw Exception("مسار فارغ")
        }

        val normalized = relativePath.replace('\\', '/')
        if (normalized.startsWith("/") ||
            normalized.contains("../") ||
            normalized == ".."
        ) {
            throw SecurityException("مسار خارج المشروع مرفوض")
        }

        val file = File(getProjectRoot, normalized).canonicalFile
        val root = getProjectRoot.path
        if (file.path != root && !file.path.startsWith("$root${File.separator}")) {
            throw SecurityException("مسار خارج المشروع مرفوض")
        }
        if (isSensitive(normalized)) {
            throw SecurityException("الملف محمي ولا يُرسل للوكيل")
        }
        return file
    }

    // ============================================================
    // أدوات داخلية
    // ============================================================
    @Throws(Exception::class)
    private fun backupExisting(file: File, relativePath: String) {
        val backup = File(getProjectRoot, ".klencod_backup")
        if (!backup.exists() && !backup.mkdirs()) {
            throw Exception("تعذر إنشاء النسخة الاحتياطية")
        }
        val backupFile = File(
            backup,
            relativePath.replace('/', '_').replace('\\', '_')
        )
        copyFile(file, backupFile)
    }

    @Throws(Exception::class)
    private fun copyFile(source: File, target: File) {
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
    }

    private fun cleanupEmptyParentDirs(dir: File?) {
        if (dir == null) return
        try {
            val rootPath = getProjectRoot.canonicalPath
            var current: File? = dir.canonicalFile
            while (current != null) {
                val currentPath = current.canonicalPath
                if (currentPath == rootPath) return
                if (!currentPath.startsWith("$rootPath${File.separator}")) return

                val children = current.listFiles()
                if (children == null || children.isNotEmpty()) return
                if (!current.delete()) return
                current = current.parentFile
            }
        } catch (_: Exception) {
        }
    }

    private fun isSensitive(path: String): Boolean {
        val lower = path.lowercase(java.util.Locale.US)
        return lower == "local.properties" ||
                lower.startsWith(".git/") ||
                lower.contains("keystore") ||
                lower.endsWith(".jks") ||
                lower.endsWith(".keystore") ||
                lower.endsWith(".pem")
    }

    /**
     * يُدرج كل الملفات في القائمة (بما فيها الصور والفيديو).
     * الحد الأقصى للحجم يُطبَّق عند القراءة (readFile)، وليس عند السرد.
     */
    @Throws(Exception::class)
    private fun collect(directory: File, result: MutableList<String>) {
        val children = directory.listFiles() ?: return
        for (child in children) {
            val relative = getProjectRoot.toPath()
                .relativize(child.canonicalFile.toPath())
                .toString()

            if (isSensitive(relative)) continue

            if (child.isDirectory) {
                collect(child, result)
            } else {
                result.add(relative.replace(File.separatorChar, '/'))
            }
        }
    }

    companion object {
        private const val MAX_READ_BYTES = 250L * 1024L
    }
}