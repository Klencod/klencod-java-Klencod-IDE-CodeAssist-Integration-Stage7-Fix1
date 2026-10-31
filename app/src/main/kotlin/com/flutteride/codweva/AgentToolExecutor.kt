package com.flutteride.codweva

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Locale

/**
 * ينفّذ استدعاءات أدوات القراءة (read_file/list_files/get_file_info/search).
 *
 * v2.0 — إصلاحات الأداء للملفات الضخمة:
 *   - قراءة ذكية حسب حجم الملف (3 مستويات)
 *   - قراءة رأس + ذيل للملفات المتوسطة
 *   - قراءة رأس فقط للملفات الكبيرة
 *   - حد أقصى 500 KB لأي قراءة
 *   - search يحمي من الملفات الكبيرة
 *   - لا OOM على أي ملف
 *
 * ملاحظة مهمة:
 *   move_file لا تُنفّذ هنا. الوكيل يقترحها عبر <klencod_action>، فتمر
 *   بموافقة المستخدم ثم تُطبَّق عبر AgentEngine.
 */
class AgentToolExecutor(projectRoot: File) {

    private val projectRoot: File = projectRoot
    private val files: ProjectFileService = ProjectFileService(projectRoot)

    // ============================================================
    // نقطة الدخول
    // ============================================================
    fun execute(call: AgentTool.ToolCall?): AgentTool.ToolResult {
        if (call == null) {
            return AgentTool.ToolResult.error("", "", "استدعاء فارغ")
        }

        return try {
            when (call.safeName) {
                AgentTool.TOOL_READ_FILE -> readFile(call)
                AgentTool.TOOL_LIST_FILES -> listFiles(call)
                AgentTool.TOOL_FILE_INFO -> getFileInfo(call)
                AgentTool.TOOL_SEARCH -> search(call)
                AgentTool.TOOL_MOVE_FILE ->
                    AgentTool.ToolResult.error(
                        call.safeId,
                        call.safeName,
                        "move_file يجب أن تُرسَل كإجراء عبر <klencod_action> " +
                                "ليتمكن المستخدم من الموافقة عليها. أعد إرسالها كوسم JSON."
                    )
                else ->
                    AgentTool.ToolResult.error(
                        call.safeId, call.safeName,
                        "أداة غير معروفة: ${call.safeName}"
                    )
            }
        } catch (se: SecurityException) {
            AgentTool.ToolResult.error(
                call.safeId, call.safeName,
                "مسار مرفوض: ${se.message ?: "غير معروف"}"
            )
        } catch (e: Exception) {
            AgentTool.ToolResult.error(
                call.safeId, call.safeName,
                e.message ?: e.javaClass.simpleName
            )
        }
    }

    // ============================================================
    // read_file(path) — v2.0 ذكي
    // ============================================================
    @Throws(Exception::class)
    private fun readFile(call: AgentTool.ToolCall): AgentTool.ToolResult {
        val path = call.getString("path")
        if (path.isBlank()) {
            return AgentTool.ToolResult.error(call.safeId, call.safeName, "المسار مطلوب")
        }

        val normalized = normalizePath(path)
        if (normalized.isEmpty()) {
            return AgentTool.ToolResult.error(call.safeId, call.safeName, "مسار غير صالح")
        }

        val file = files.safeFile(normalized)
        if (!file.exists() || !file.isFile) {
            return AgentTool.ToolResult.error(
                call.safeId, call.safeName,
                "الملف غير موجود: $normalized"
            )
        }

        val fileSize = file.length()

        // ============================================================
        // قراءة ذكية حسب الحجم
        // ============================================================
        return when {
            // ملف صغير (< 100 KB) — قراءة كاملة
            fileSize <= SMALL_FILE_THRESHOLD -> {
                readSmallFile(file, normalized, call)
            }
            // ملف متوسط (100 KB - 500 KB) — رأس + ذيل
            fileSize <= MEDIUM_FILE_THRESHOLD -> {
                readMediumFile(file, normalized, call)
            }
            // ملف كبير (> 500 KB) — رأس فقط
            else -> {
                readLargeFile(file, normalized, call)
            }
        }
    }

    /**
     * ملف صغير (< 100 KB) — قراءة كاملة.
     */
    private fun readSmallFile(
        file: File,
        path: String,
        call: AgentTool.ToolCall
    ): AgentTool.ToolResult {
        val content = readText(file)
            ?: return AgentTool.ToolResult.error(
                call.safeId, call.safeName,
                "تعذر قراءة الملف (قد يكون ثنائياً)"
            )

        val lines = countLines(content)
        val sb = StringBuilder(content.length + 128)
        sb.append("--- ").append(path)
            .append("  (").append(formatSize(file.length()))
            .append(", ").append(lines).append(" سطر) ---\n")
        sb.append(content)
        if (!content.endsWith("\n")) sb.append("\n")
        sb.append("--- نهاية ").append(path).append(" ---")

        return AgentTool.ToolResult.success(call.safeId, call.safeName, sb.toString())
    }

    /**
     * ملف متوسط (100 KB - 500 KB) — رأس 200 سطر + ذيل 50 سطر.
     */
    private fun readMediumFile(
        file: File,
        path: String,
        call: AgentTool.ToolCall
    ): AgentTool.ToolResult {
        val content = readTextWithLimit(file, MEDIUM_FILE_THRESHOLD)
            ?: return AgentTool.ToolResult.error(
                call.safeId, call.safeName,
                "تعذر قراءة الملف"
            )

        val lines = content.split("\n".toRegex())
        val total = lines.size
        val head = minOf(MEDIUM_HEAD_LINES, total)
        val tail = minOf(MEDIUM_TAIL_LINES, maxOf(0, total - head))

        val sb = StringBuilder(content.length / 2 + 256)
        sb.append("--- ").append(path)
            .append("  (").append(formatSize(file.length()))
            .append(", ").append(total).append(" سطر — ملف متوسط) ---\n")
        sb.append("📌 عرض أول ").append(head).append(" سطر + آخر ")
            .append(tail).append(" سطر\n\n")

        // الرأس
        for (i in 0 until head) {
            sb.append(lines[i]).append("\n")
        }

        // الإشعار
        if (head + tail < total) {
            sb.append("\n... [").append(total - head - tail)
                .append(" سطر محذوف — استخدم search للبحث في الملف] ...\n\n")
        }

        // الذيل
        for (i in (total - tail) until total) {
            if (i < 0) continue
            sb.append(lines[i]).append("\n")
        }

        sb.append("--- نهاية ").append(path).append(" ---")

        return AgentTool.ToolResult.success(call.safeId, call.safeName, sb.toString())
    }

    /**
     * ملف كبير (> 500 KB) — رأس 100 سطر فقط.
     */
    private fun readLargeFile(
        file: File,
        path: String,
        call: AgentTool.ToolCall
    ): AgentTool.ToolResult {
        val content = readTextWithLimit(file, LARGE_FILE_THRESHOLD)
            ?: return AgentTool.ToolResult.error(
                call.safeId, call.safeName,
                "تعذر قراءة الملف"
            )

        val lines = content.split("\n".toRegex())
        val total = lines.size
        val head = minOf(LARGE_HEAD_LINES, total)

        val sb = StringBuilder(content.length / 4 + 256)
        sb.append("--- ").append(path)
            .append("  (").append(formatSize(file.length()))
            .append(", ~").append(total).append(" سطر — ملف كبير جداً) ---\n")
        sb.append("⚠️ الملف ضخم جداً — يُعرض أول ").append(head).append(" سطر فقط.\n")
        sb.append("للوصول إلى جزء معين، استخدم search(query) للبحث عن نص.\n\n")

        // الرأس
        for (i in 0 until head) {
            sb.append(lines[i]).append("\n")
        }

        sb.append("\n... [").append(maxOf(0, total - head))
            .append(" سطر غير معروض — استخدم search] ...\n")

        sb.append("--- نهاية ").append(path).append(" ---")

        return AgentTool.ToolResult.success(call.safeId, call.safeName, sb.toString())
    }

    // ============================================================
    // list_files() — v2.0 مع حد أقصى
    // ============================================================
    @Throws(Exception::class)
    private fun listFiles(call: AgentTool.ToolCall): AgentTool.ToolResult {
        val relativePaths = files.listFiles()
        if (relativePaths.isEmpty()) {
            return AgentTool.ToolResult.success(
                call.safeId, call.safeName,
                "المشروع لا يحتوي أي ملفات نصية."
            )
        }

        val sorted = relativePaths.sortedWith(String.CASE_INSENSITIVE_ORDER)
        val shown = if (sorted.size > MAX_LIST_FILES) {
            sorted.take(MAX_LIST_FILES)
        } else {
            sorted
        }

        val sb = StringBuilder(shown.size * 48 + 128)
        sb.append("ملفات المشروع (").append(sorted.size).append("):\n")

        if (sorted.size > MAX_LIST_FILES) {
            sb.append("📌 يتم عرض أول ").append(MAX_LIST_FILES)
                .append(" ملف من ").append(sorted.size).append("\n\n")
        }

        for (rel in shown) {
            try {
                val f = files.safeFile(rel)
                val size = f.length()
                sb.append(rel).append("  (").append(formatSize(size)).append(")")

                // حساب الأسطر فقط للملفات الصغيرة
                if (size <= 30L * 1024L) {
                    val lines = countLinesQuietly(f)
                    if (lines >= 0) sb.append(", ").append(lines).append(" سطر")
                } else if (size > 500L * 1024L) {
                    sb.append(" [كبير]")
                }

                sb.append("\n")
            } catch (_: Exception) {
            }
        }

        if (sorted.size > MAX_LIST_FILES) {
            sb.append("\n... [").append(sorted.size - MAX_LIST_FILES)
                .append(" ملف إضافي غير معروض] ...\n")
        }

        return AgentTool.ToolResult.success(call.safeId, call.safeName, sb.toString())
    }

    // ============================================================
    // get_file_info(path) — v2.0 مع معلومات إضافية
    // ============================================================
    @Throws(Exception::class)
    private fun getFileInfo(call: AgentTool.ToolCall): AgentTool.ToolResult {
        val path = call.getString("path")
        if (path.isBlank()) {
            return AgentTool.ToolResult.error(call.safeId, call.safeName, "المسار مطلوب")
        }

        val normalized = normalizePath(path)
        val file = files.safeFile(normalized)
        if (!file.exists() || !file.isFile) {
            return AgentTool.ToolResult.error(
                call.safeId, call.safeName,
                "الملف غير موجود: $normalized"
            )
        }

        val size = file.length()
        val sb = StringBuilder(160)
        sb.append("الملف: ").append(normalized).append("\n")
        sb.append("الحجم: ").append(formatSize(size)).append("\n")

        // تصنيف الحجم
        when {
            size > 500L * 1024L -> sb.append("التصنيف: كبير جداً (يُنصح بـ search)\n")
            size > 100L * 1024L -> sb.append("التصنيف: متوسط\n")
            else -> sb.append("التصنيف: صغير (قراءة كاملة آمنة)\n")
        }

        // عدد الأسطر (فقط للملفات الصغيرة/المتوسطة)
        if (size <= MEDIUM_FILE_THRESHOLD) {
            val lines = countLinesQuietly(file)
            if (lines >= 0) sb.append("عدد الأسطر: ").append(lines).append("\n")
        } else {
            sb.append("عدد الأسطر: (الملف كبير جداً للعد السريع)\n")
        }

        sb.append("ثنائي: ").append(if (isBinaryPath(normalized)) "نعم" else "لا").append("\n")

        return AgentTool.ToolResult.success(call.safeId, call.safeName, sb.toString())
    }

    // ============================================================
    // search(query, extension?) — v2.0 مع حماية
    // ============================================================
    @Throws(Exception::class)
    private fun search(call: AgentTool.ToolCall): AgentTool.ToolResult {
        val query = call.getString("query")
        if (query.isBlank()) {
            return AgentTool.ToolResult.error(call.safeId, call.safeName, "نص البحث مطلوب")
        }

        var ext = call.getOptionalString("extension")
        if (ext != null) {
            ext = ext.trim().lowercase(Locale.US)
            if (ext.startsWith(".")) ext = ext.substring(1)
        }

        val queryLower = query.lowercase(Locale.US)
        val allFiles = files.listFiles()
        val sb = StringBuilder(1024)
        var matchCount = 0
        var fileCount = 0
        var skippedFiles = 0
        val startTime = System.currentTimeMillis()

        for (rel in allFiles) {
            // حد النتائج
            if (matchCount >= MAX_SEARCH_RESULTS) break

            // حد زمني
            if (System.currentTimeMillis() - startTime > MAX_SEARCH_TIME_MS) {
                sb.append("\n⏱️ تم إيقاف البحث — تجاوز الوقت الحد المسموح.\n")
                break
            }

            // فلتر الامتداد
            if (ext != null && !rel.lowercase(Locale.US).endsWith(".$ext")) continue

            try {
                val file = files.safeFile(rel)

                // ============================================================
                // حماية: تخطّي الملفات الكبيرة
                // ============================================================
                if (file.length() > MAX_SEARCH_FILE_SIZE) {
                    skippedFiles++
                    continue
                }

                if (isBinaryPath(rel)) continue

                val content = readText(file) ?: continue
                val lines = content.split("\n")
                val hits = mutableListOf<Int>()

                for (i in lines.indices) {
                    if (lines[i].lowercase(Locale.US).contains(queryLower)) {
                        hits.add(i)
                    }
                }
                if (hits.isEmpty()) continue

                fileCount++
                sb.append("\n=== ").append(rel)
                    .append(" (").append(hits.size).append(" نتيجة) ===\n")

                for (lineIdx in hits) {
                    if (matchCount >= MAX_SEARCH_RESULTS) {
                        sb.append("... (تم الوصول للحد الأقصى من النتائج)\n")
                        break
                    }

                    val start = maxOf(0, lineIdx - SEARCH_CONTEXT_LINES)
                    val end = minOf(lines.size - 1, lineIdx + SEARCH_CONTEXT_LINES)

                    for (i in start..end) {
                        val prefix = if (i == lineIdx) "> " else "  "
                        var snippet = lines[i]
                        if (snippet.length > SEARCH_SNIPPET_LEN) {
                            snippet = snippet.substring(0, SEARCH_SNIPPET_LEN) + "…"
                        }
                        sb.append(prefix)
                            .append("سطر ").append(i + 1).append(": ")
                            .append(snippet).append("\n")
                    }
                    matchCount++
                }
            } catch (_: Exception) {
            }
        }

        if (matchCount == 0) {
            val noResult = StringBuilder(128)
            noResult.append("لا نتائج للبحث عن: \"").append(query).append("\"")
            if (ext != null) noResult.append(" (النطاق: *.").append(ext).append(")")
            if (skippedFiles > 0) {
                noResult.append("\n(تم تخطّي ").append(skippedFiles)
                    .append(" ملف كبير — استخدم read_file مع مسار محدد)")
            }
            return AgentTool.ToolResult.success(
                call.safeId, call.safeName, noResult.toString()
            )
        }

        val header = StringBuilder(160)
        header.append("نتائج البحث عن \"").append(query).append("\"")
        if (ext != null) header.append(" (النطاق: *.").append(ext).append(")")
        header.append(":\n")
        header.append("ملفات مطابقة: ").append(fileCount)
            .append(", نتائج: ").append(matchCount)
        if (skippedFiles > 0) {
            header.append(", تخطّي: ").append(skippedFiles).append(" ملف كبير")
        }
        header.append("\n")
        header.append(sb)

        return AgentTool.ToolResult.success(call.safeId, call.safeName, header.toString())
    }

    // ============================================================
    // أدوات مساعدة
    // ============================================================
    private fun normalizePath(path: String?): String {
        if (path == null) return ""
        var p = path.trim().replace('\\', '/')
        while (p.startsWith("./")) p = p.substring(2)
        while (p.startsWith("/")) p = p.substring(1)
        return p
    }

    /**
     * قراءة ملف مع حد أقصى (للحماية).
     */
    private fun readTextWithLimit(file: File, maxBytes: Long): String? {
        return try {
            val initialCapacity = minOf(file.length(), 65_536L).toInt()
            val sb = StringBuilder(initialCapacity)

            BufferedReader(
                InputStreamReader(FileInputStream(file), Charsets.UTF_8)
            ).use { reader ->
                val buffer = CharArray(4096)
                var read: Int
                var totalBytes = 0L

                while (reader.read(buffer).also { read = it } != -1) {
                    sb.append(buffer, 0, read)
                    totalBytes += read * 2  // تقديري

                    if (totalBytes > maxBytes) break
                }
            }
            sb.toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * قراءة ملف كاملاً (بدون حد).
     */
    private fun readText(file: File): String? {
        return try {
            val initialCapacity = minOf(file.length(), 65_536L).toInt()
            val sb = StringBuilder(initialCapacity)

            BufferedReader(
                InputStreamReader(FileInputStream(file), Charsets.UTF_8)
            ).use { reader ->
                val buffer = CharArray(4096)
                var read: Int
                while (reader.read(buffer).also { read = it } != -1) {
                    sb.append(buffer, 0, read)
                }
            }
            sb.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun countLines(text: String?): Int {
        if (text.isNullOrEmpty()) return 0
        var count = 1
        for (c in text) {
            if (c == '\n') count++
        }
        return count
    }

    private fun countLinesQuietly(file: File): Int {
        return try {
            val content = readText(file) ?: return -1
            countLines(content)
        } catch (_: Exception) {
            -1
        }
    }

    private fun isBinaryPath(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        val dot = path.lastIndexOf('.')
        if (dot < 0 || dot == path.length - 1) return false

        return when (path.substring(dot + 1).lowercase(Locale.US)) {
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico",
            "ttf", "otf", "woff", "woff2",
            "mp3", "mp4", "wav", "apk", "aab", "jar",
            "so", "dex", "keystore", "jks", "zip", "rar", "7z",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> true
            else -> false
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

    // ============================================================
    // ثوابت
    // ============================================================
    companion object {
        // ============================================================
        // حدود القراءة الذكية
        // ============================================================

        /** ملف صغير: قراءة كاملة (< 100 KB). */
        private const val SMALL_FILE_THRESHOLD = 100_000L

        /** ملف متوسط: رأس + ذيل (100 KB - 500 KB). */
        private const val MEDIUM_FILE_THRESHOLD = 500_000L

        /** ملف كبير: رأس فقط (> 500 KB). */
        private const val LARGE_FILE_THRESHOLD = 500_000L

        /** عدد أسطر الرأس للملفات المتوسطة. */
        private const val MEDIUM_HEAD_LINES = 200

        /** عدد أسطر الذيل للملفات المتوسطة. */
        private const val MEDIUM_TAIL_LINES = 50

        /** عدد أسطر الرأس للملفات الكبيرة. */
        private const val LARGE_HEAD_LINES = 100

        // ============================================================
        // حدود عامة
        // ============================================================

        /** حد أقصى لحجم أي قراءة (حماية إضافية). */
        private const val MAX_READ_BYTES = 500L * 1024L

        /** حد أقصى لعدد الملفات المعروضة في list_files. */
        private const val MAX_LIST_FILES = 500

        /** حد أقصى للنتائج من search. */
        private const val MAX_SEARCH_RESULTS = 200

        /** حد أقصى لحجم ملف في search. */
        private const val MAX_SEARCH_FILE_SIZE = 500L * 1024L

        /** طول مقتطف السطر في search. */
        private const val SEARCH_SNIPPET_LEN = 120

        /** عدد أسطر السياق في search. */
        private const val SEARCH_CONTEXT_LINES = 1

        /** حد زمني للبحث (مللي ثانية). */
        private const val MAX_SEARCH_TIME_MS = 3000L
    }
}