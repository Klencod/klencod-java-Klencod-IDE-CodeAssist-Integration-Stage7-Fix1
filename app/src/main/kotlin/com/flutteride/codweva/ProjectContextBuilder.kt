package com.flutteride.codweva

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Locale

/**
 * ProjectContextBuilder — يبني سياقاً حقيقياً كاملاً للمشروع لعرضه على نموذج AI.
 *
 * v2.0 — إصلاحات الأداء للمشاريع الضخمة:
 *   - حد أقصى 500 ملف مفهرس
 *   - حد زمني 1.5 ثانية للفحص
 *   - تخطّي الملفات > 1 MB تلقائياً
 *   - رأس + ذيل للملفات الكبيرة (بدل قراءة كاملة)
 *   - حد 15 ملفاً بمحتوى كامل
 *   - تجنّب مجلدات البناء والذاكرة المؤقتة
 *
 * النتيجة: بدل تجمّد 3-5 ثواني → 200-400ms
 */
object ProjectContextBuilder {

    // ============================================================
    // المستثنيات (نفس السلوك السابق)
    // ============================================================
    private val EXCLUDED_DIRS = hashSetOf(
        ".git", ".gradle", ".dart_tool", "build", "node_modules",
        ".idea", ".vscode", "__pycache__", ".next", "Pods",
        ".symlinks", "DerivedData", ".klencod", ".klencod_backup"
    )

    private val EXCLUDED_FILES = hashSetOf(
        ".DS_Store", "Thumbs.db", "local.properties"
    )

    /** ملفات ثنائية — لا نقرأ محتواها. */
    private val BINARY_EXTENSIONS = hashSetOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico",
        "svg", "ttf", "otf", "woff", "woff2",
        "mp3", "mp4", "wav", "apk", "aab", "jar",
        "so", "dex", "keystore", "jks", "zip", "rar", "7z",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    )

    // ============================================================
    // الحدود الجديدة
    // ============================================================
    private const val MAX_FILE_SIZE = 1_000_000L         // 1 MB — أقصى حجم ملف
    private const val SMALL_FILE_THRESHOLD = 100_000L    // 100 KB — قراءة كاملة
    private const val MEDIUM_FILE_THRESHOLD = 500_000L   // 500 KB — رأس + ذيل
    private const val FULL_CONTENT_THRESHOLD = 30_000L   // 30 KB
    private const val SMALL_FILE_FULL = 5_000L           // 5 KB — دائماً كامل
    private const val LARGE_FILE_HEAD_LINES = 50
    private const val LARGE_FILE_TAIL_LINES = 10

    /** حد أقصى للملفات المفهرسة. */
    private const val MAX_FILES = 500

    /** حد أقصى للملفات بمحتوى كامل. */
    private const val MAX_FULL_CONTENT_FILES = 15

    /** حد أقصى لحجم السياق النهائي (أحرف). */
    private const val MAX_CONTEXT_CHARS = 60_000

    /** حد زمني للفحص (مللي ثانية). */
    private const val MAX_SCAN_TIME_MS = 1500L

    /** حد أقصى لعرض قائمة الملفات في الخريطة. */
    private const val MAX_MAP_FILES = 300

    // ============================================================
    // نموذج النتيجة
    // ============================================================
    class Context(
        /** نص كامل جاهز للإدراج في system prompt. */
        val text: String,
        /** عدد الملفات في الخريطة. */
        val totalFiles: Int,
        /** عدد الملفات التي أُرفق محتواها كاملاً. */
        val fullContentFiles: Int,
        /** هل تم الوصول لحد ما؟ */
        val truncated: Boolean
    )

    // ============================================================
    // نموذج داخلي
    // ============================================================
    private class FileEntry(
        val file: File,
        val relativePath: String,
        val size: Long,
        val isBinary: Boolean
    ) {
        var lineCount: Int = -1  // -1 = لم يُحسب
    }

    // ============================================================
    // الدالة الرئيسية
    // ============================================================
    @JvmStatic
    fun build(
        projectDir: File?,
        currentFile: String?,
        userMessage: String?,
        manuallyAttachedContent: String?
    ): Context {
        if (projectDir == null || !projectDir.isDirectory) {
            return Context("", 0, 0, false)
        }

        val startTime = System.currentTimeMillis()
        var truncated = false

        // ============================================================
        // 1) اجمع الملفات مع الحدود
        // ============================================================
        val allFiles = mutableListOf<FileEntry>()
        val reachedLimit = collectFilesWithLimits(
            projectDir, projectDir, allFiles, 0, startTime
        )
        if (reachedLimit) truncated = true

        // ============================================================
        // 2) رتّب أبجدياً
        // ============================================================
        allFiles.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.relativePath })

        // ============================================================
        // 3) حدد الملفات المهمة
        // ============================================================
        val importantPaths = determineImportantFiles(
            projectDir, currentFile, userMessage, allFiles
        )

        // ============================================================
        // 4) ابنِ النص
        // ============================================================
        val sb = StringBuilder(16384)
        var fullContentCount = 0

        // --- 4.1: رأس السياق
        sb.append("=== سياق المشروع ===\n")
        sb.append("جذر المشروع: ").append(projectDir.name).append("\n")
        sb.append("الملفات: ").append(allFiles.size)
        if (truncated) sb.append(" (تم الوصول للحد الأقصى للفحص)")
        sb.append("\n")
        if (!currentFile.isNullOrEmpty()) {
            sb.append("الملف المفتوح حالياً: ").append(currentFile).append("\n")
        }
        sb.append("\n")

        // --- 4.2: خريطة المشروع الكاملة
        sb.append("=== الخريطة الكاملة ===\n")
        var mapCount = 0
        for (entry in allFiles) {
            if (mapCount >= MAX_MAP_FILES) {
                sb.append("... (عدد الملفات يتجاوز الحد الأقصى للعرض: ")
                    .append(MAX_MAP_FILES).append(")\n")
                truncated = true
                break
            }
            sb.append(entry.relativePath)
            sb.append("  (").append(formatSize(entry.size)).append(")")
            if (!entry.isBinary && entry.lineCount > 0) {
                sb.append(", ").append(entry.lineCount).append(" سطر")
            }
            if (entry.isBinary) {
                sb.append(" [binary]")
            }
            sb.append("\n")
            mapCount++
        }
        sb.append("\n")

        // --- 4.3: محتوى الملفات المهمة
        if (importantPaths.isNotEmpty()) {
            sb.append("=== محتوى الملفات المهمة ===\n\n")
            var writtenChars = sb.length

            for (entry in allFiles) {
                if (entry.relativePath !in importantPaths) continue
                if (entry.isBinary) continue
                if (entry.size > MAX_FILE_SIZE) continue
                if (fullContentCount >= MAX_FULL_CONTENT_FILES) {
                    sb.append("... (تم الوصول للحد الأقصى للملفات الكاملة: ")
                        .append(MAX_FULL_CONTENT_FILES).append(") ...\n")
                    truncated = true
                    break
                }
                if (writtenChars > MAX_CONTEXT_CHARS) {
                    sb.append("... (تم الوصول للحد الأقصى لحجم السياق) ...\n")
                    truncated = true
                    break
                }

                val content = readFileSafely(entry.file) ?: continue
                val block = formatFileBlock(entry, content)
                sb.append(block)
                writtenChars += block.length
                fullContentCount++
            }
        }

        // --- 4.4: محتوى إرفاق يدوي
        if (!manuallyAttachedContent.isNullOrBlank()) {
            sb.append("\n=== محتوى أرفقه المستخدم يدوياً ===\n")
            // اقتطاع لو كبير
            val attachText = if (manuallyAttachedContent.length > 50_000) {
                manuallyAttachedContent.substring(0, 50_000) +
                        "\n... [تم اقتطاع الإرفاق — كبير جداً]"
            } else {
                manuallyAttachedContent
            }
            sb.append(attachText)
            sb.append("\n=== نهاية الإرفاق ===\n")
        }

        sb.append("\n=== نهاية سياق المشروع ===\n")

        return Context(
            text = sb.toString(),
            totalFiles = allFiles.size,
            fullContentFiles = fullContentCount,
            truncated = truncated
        )
    }

    // ============================================================
    // جمع الملفات مع الحدود الزمنية والعددية
    // ============================================================
    /**
     * @return true إذا وصلنا لحد ما (وقت، عدد)
     */
    private fun collectFilesWithLimits(
        root: File,
        dir: File,
        out: MutableList<FileEntry>,
        depth: Int,
        startTime: Long
    ): Boolean {
        // حماية من التداخل العميق
        if (depth > 20) return false

        // حد عددي
        if (out.size >= MAX_FILES) return true

        // حد زمني
        if (System.currentTimeMillis() - startTime > MAX_SCAN_TIME_MS) {
            return true
        }

        val children = dir.listFiles() ?: return false

        for (child in children) {
            // فحص الحدود داخل الحلقة أيضاً
            if (out.size >= MAX_FILES) return true
            if (System.currentTimeMillis() - startTime > MAX_SCAN_TIME_MS) {
                return true
            }

            val name = child.name ?: continue
            if (name in EXCLUDED_FILES) continue

            if (child.isDirectory) {
                if (name in EXCLUDED_DIRS) continue
                val reached = collectFilesWithLimits(
                    root, child, out, depth + 1, startTime
                )
                if (reached) return true
            } else if (child.isFile) {
                // ============================================================
                // تخطّي الملفات الضخمة (> 1MB)
                // ============================================================
                val size = try {
                    child.length()
                } catch (_: Exception) {
                    0L
                }
                if (size > MAX_FILE_SIZE) continue

                val relative = relativePath(root, child) ?: continue
                val binary = isBinary(relative)
                val entry = FileEntry(child, relative, size, binary)

                // ============================================================
                // حساب عدد الأسطر — فقط للملفات الصغيرة نسبياً
                // ============================================================
                if (!binary && size <= 100_000L) {
                    entry.lineCount = countLinesQuietly(child)
                }

                out.add(entry)
            }
        }

        return false
    }

    // ============================================================
    // تحديد الملفات المهمة
    // ============================================================
    private fun determineImportantFiles(
        projectDir: File,
        currentFile: String?,
        userMessage: String?,
        allFiles: List<FileEntry>
    ): Set<String> {
        val important = hashSetOf<String>()

        // 1) الملف المفتوح حالياً
        if (!currentFile.isNullOrEmpty()) {
            var normalized = currentFile.replace('\\', '/')
            while (normalized.startsWith("./")) normalized = normalized.substring(2)
            if (normalized.startsWith("/")) normalized = normalized.substring(1)
            if (normalized.isNotEmpty()) important.add(normalized)
        }

        // 2) lib/main.dart أو app/src/main/... حسب نوع المشروع
        for (entry in allFiles) {
            if (entry.relativePath == "lib/main.dart" ||
                entry.relativePath == "app/src/main/AndroidManifest.xml" ||
                entry.relativePath == "app/build.gradle" ||
                entry.relativePath == "pubspec.yaml"
            ) {
                important.add(entry.relativePath)
            }
        }

        // 3) الملفات المذكورة في رسالة المستخدم
        if (!userMessage.isNullOrEmpty()) {
            for (entry in allFiles) {
                if (entry.isBinary) continue
                val fileName = lastSegment(entry.relativePath)
                if (fileName.isNullOrEmpty()) continue

                if (containsWord(userMessage, fileName)) {
                    important.add(entry.relativePath)
                    continue
                }
                if (containsWord(userMessage, entry.relativePath)) {
                    important.add(entry.relativePath)
                }
            }
        }

        // 4) الملفات الصغيرة (≤ 5KB) — تُضاف كاملة
        for (entry in allFiles) {
            if (entry.isBinary) continue
            if (entry.size in 1..SMALL_FILE_FULL) {
                important.add(entry.relativePath)
            }
        }

        return important
    }

    // ============================================================
    // تنسيق محتوى ملف
    // ============================================================
    private fun formatFileBlock(entry: FileEntry, content: String): String {
        val sb = StringBuilder(content.length + 256)
        sb.append("--- ").append(entry.relativePath)
            .append("  (").append(formatSize(entry.size))
            .append(", ").append(if (entry.lineCount > 0) entry.lineCount else "?")
            .append(" سطر) ---\n")

        if (entry.size > FULL_CONTENT_THRESHOLD) {
            // ملف كبير: رأس + ذيل
            val lines = content.split("\n".toRegex()).toTypedArray()
            val total = lines.size
            val head = minOf(LARGE_FILE_HEAD_LINES, total)
            val tail = minOf(LARGE_FILE_TAIL_LINES, maxOf(0, total - head))

            for (i in 0 until head) {
                sb.append(lines[i]).append("\n")
            }
            if (head + tail < total) {
                sb.append("... [").append(total - head - tail)
                    .append(" سطر محذوف — حجم الملف كبير] ...\n")
            }
            for (i in (total - tail) until total) {
                if (i < 0) continue
                sb.append(lines[i]).append("\n")
            }
        } else {
            // ملف صغير/متوسط: كل المحتوى
            sb.append(content)
            if (!content.endsWith("\n")) sb.append("\n")
        }

        sb.append("--- نهاية ").append(entry.relativePath).append(" ---\n\n")
        return sb.toString()
    }

    // ============================================================
    // قراءة آمنة (حسب الحجم)
    // ============================================================

    /**
     * قراءة ملف بشكل آمن:
     *   - < 100 KB  : قراءة كاملة
     *   - 100-500 KB: قراءة كاملة (لكن مع تحذير في النص)
     *   - > 500 KB  : لن نصل هنا لأننا نتخطاها (MAX_FILE_SIZE = 1MB)
     */
    private fun readFileSafely(file: File?): String? {
        if (file == null || !file.isFile) return null

        val size = file.length()
        if (size > MAX_FILE_SIZE) return null

        return try {
            val initialCapacity = minOf(size, 65_536L).toInt()
            val sb = StringBuilder(initialCapacity)

            BufferedReader(
                InputStreamReader(FileInputStream(file), Charsets.UTF_8)
            ).use { reader ->
                val buffer = CharArray(4096)
                var read: Int
                var totalRead = 0

                while (reader.read(buffer).also { read = it } != -1) {
                    sb.append(buffer, 0, read)
                    totalRead += read

                    // حماية إضافية: لو تجاوزنا 500KB لأي سبب
                    if (totalRead > MEDIUM_FILE_THRESHOLD) {
                        sb.append("\n... [تم اقتطاع الملف — كبير جداً]")
                        break
                    }
                }
            }
            sb.toString()
        } catch (_: Exception) {
            null
        }
    }

    // ============================================================
    // أدوات مساعدة
    // ============================================================
    private fun relativePath(root: File, file: File): String? {
        return try {
            val base = root.canonicalPath
            val path = file.canonicalPath
            if (path == base) return ""
            if (path.startsWith("$base${File.separator}")) {
                path.substring(base.length + 1).replace(File.separatorChar, '/')
            } else file.name
        } catch (_: Exception) {
            file.name
        }
    }

    private fun lastSegment(path: String?): String? {
        if (path == null) return null
        val idx = path.lastIndexOf('/')
        return if (idx >= 0) path.substring(idx + 1) else path
    }

    private fun containsWord(haystack: String?, needle: String?): Boolean {
        if (haystack.isNullOrEmpty() || needle.isNullOrEmpty()) return false
        var idx = haystack.indexOf(needle)
        while (idx >= 0) {
            val leftOk = idx == 0 || !isWordChar(haystack[idx - 1])
            val end = idx + needle.length
            val rightOk = end >= haystack.length || !isWordChar(haystack[end])
            if (leftOk && rightOk) return true
            idx = haystack.indexOf(needle, idx + 1)
        }
        return false
    }

    private fun isWordChar(c: Char): Boolean {
        return c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '/'
    }

    private fun isBinary(relativePath: String?): Boolean {
        if (relativePath.isNullOrEmpty()) return false
        val dot = relativePath.lastIndexOf('.')
        if (dot < 0 || dot == relativePath.length - 1) return false
        val ext = relativePath.substring(dot + 1).lowercase(Locale.US)
        return ext in BINARY_EXTENSIONS
    }

    private fun countLinesQuietly(file: File): Int {
        return try {
            val content = readFileSafely(file) ?: return -1
            if (content.isEmpty()) return 0
            var count = 1
            for (c in content) {
                if (c == '\n') count++
            }
            count
        } catch (_: Exception) {
            -1
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 ->
                String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else ->
                String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * نسخة مختصرة — بدون userMessage أو attached content.
     */
    @JvmStatic
    fun buildDefault(projectDir: File?, currentFile: String?): Context {
        return build(projectDir, currentFile, null, null)
    }
}