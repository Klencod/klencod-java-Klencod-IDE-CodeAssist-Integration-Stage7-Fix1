package com.flutteride.codweva

import java.util.Locale

/**
 * تصنيف أنواع الملفات لتحديد العارض المناسب.
 *
 * الفئات:
 *   - TEXT       : يُعرض في CodeEditorView (مع تلوين)
 *   - IMAGE      : يُعرض في ImageViewer
 *   - VIDEO      : يُعرض في VideoViewer
 *   - AUDIO      : يُعرض في AudioViewer
 *   - PDF        : يُفتح عبر Intent
 *   - APK        : يُفتح عبر Intent (تثبيت)
 *   - ARCHIVE    : يُفتح عبر Intent (ZIP/RAR)
 *   - BINARY     : غير قابل للعرض (ملف غير مدعوم)
 *   - UNKNOWN    : امتداد غير معروف → نحاول عرضه كنص
 */
enum class FileKind {

    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    APK,
    ARCHIVE,
    BINARY,
    UNKNOWN;

    /**
     * وصف عربي للنوع (للعرض في الواجهة).
     */
    val arabicName: String
        get() = when (this) {
            TEXT -> "ملف نصي"
            IMAGE -> "صورة"
            VIDEO -> "فيديو"
            AUDIO -> "ملف صوتي"
            PDF -> "مستند PDF"
            APK -> "حزمة تطبيق"
            ARCHIVE -> "أرشيف"
            BINARY -> "ملف ثنائي"
            UNKNOWN -> "ملف"
        }

    companion object {

        // ============================================================
        // قائمة الملفات النصية (موسّعة)
        // ============================================================
        private val TEXT_EXTENSIONS = hashSetOf(
            // لغات برمجة
            "dart", "java", "kt", "kts", "py", "pyw", "js", "mjs", "ts", "tsx", "jsx",
            "c", "cpp", "cc", "cxx", "h", "hpp", "cs", "go", "rs", "rb", "php", "swift",
            "m", "mm", "scala", "clj", "cljs", "lua", "pl", "r", "jl", "vb",
            // ويب
            "html", "htm", "xhtml", "css", "scss", "sass", "less", "vue", "svelte",
            // بيانات وتهيئة
            "xml", "json", "yaml", "yml", "toml", "ini", "conf", "cfg", "properties",
            "env", "lock", "gradle", "pro", "plist", "pbxproj", "xcconfig",
            // نصوص
            "md", "markdown", "txt", "rtf", "log", "csv", "tsv",
            // سكربتات
            "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1", "makefile", "mk",
            // أخرى
            "gitignore", "gitattributes", "editorconfig", "dockerignore",
            "sql", "graphql", "proto", "diff", "patch"
        )

        // أسماء ملفات كاملة بدون امتداد
        private val TEXT_FILENAMES = hashSetOf(
            "makefile", "dockerfile", "gradlew", "gradlew.bat", "procfile",
            "rakefile", "gemfile", "podfile", "vagrantfile"
        )

        // ============================================================
        // الامتدادات المدعومة لكل نوع
        // ============================================================
        private val IMAGE_EXTENSIONS = hashSetOf(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "heif", "ico", "tiff", "tif"
        )

        private val VIDEO_EXTENSIONS = hashSetOf(
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "m4v", "mpeg", "mpg", "wmv"
        )

        private val AUDIO_EXTENSIONS = hashSetOf(
            "mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "3gp", "amr", "wma"
        )

        private val PDF_EXTENSIONS = hashSetOf("pdf")

        private val APK_EXTENSIONS = hashSetOf("apk", "aab", "xapk")

        private val ARCHIVE_EXTENSIONS = hashSetOf(
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz"
        )

        /**
         * يصنّف ملف حسب اسمه.
         */
        @JvmStatic
        fun detect(fileNameOrPath: String?): FileKind {
            if (fileNameOrPath.isNullOrEmpty()) return UNKNOWN

            var name = fileNameOrPath
            // نأخذ اسم الملف فقط (بعد آخر /)
            val slash = name.lastIndexOf('/')
            if (slash >= 0) name = name.substring(slash + 1)
            val bslash = name.lastIndexOf('\\')
            if (bslash >= 0) name = name.substring(bslash + 1)

            val lower = name.lowercase(Locale.US)

            // 1) اسم ملف كامل بدون امتداد
            if (TEXT_FILENAMES.contains(lower)) return TEXT

            // 2) استخراج الامتداد
            val ext = extensionOf(lower)
            if (ext.isEmpty()) return UNKNOWN // بدون امتداد → نحاول كنص

            // 3) البحث في القوائم
            return when {
                TEXT_EXTENSIONS.contains(ext) -> TEXT
                IMAGE_EXTENSIONS.contains(ext) -> IMAGE
                VIDEO_EXTENSIONS.contains(ext) -> VIDEO
                AUDIO_EXTENSIONS.contains(ext) -> AUDIO
                PDF_EXTENSIONS.contains(ext) -> PDF
                APK_EXTENSIONS.contains(ext) -> APK
                ARCHIVE_EXTENSIONS.contains(ext) -> ARCHIVE
                // 4) امتداد غير معروف → UNKNOWN (سيُعرض كنص بحذر)
                else -> UNKNOWN
            }
        }

        /**
         * هل الملف قابل للعرض في المحرر النصي؟
         */
        @JvmStatic
        fun isTextLike(fileNameOrPath: String?): Boolean {
            val kind = detect(fileNameOrPath)
            return kind == TEXT || kind == UNKNOWN
        }

        /**
         * يُرجع الامتداد بدون نقطة (أو "").
         */
        @JvmStatic
        fun extensionOf(fileName: String?): String {
            if (fileName.isNullOrEmpty()) return ""
            val dot = fileName.lastIndexOf('.')
            if (dot < 0 || dot == fileName.length - 1) return ""
            val ext = fileName.substring(dot + 1).lowercase(Locale.US)
            // نُقصّر الامتدادات الطويلة جداً (حماية)
            return if (ext.length > 12) ext.substring(0, 12) else ext
        }
    }
}