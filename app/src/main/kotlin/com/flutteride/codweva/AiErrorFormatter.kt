package com.flutteride.codweva

import java.util.regex.Pattern

/**
 * مُنسّق رسائل الأخطاء الخاص بمزودي AI.
 *
 * الهدف:
 *   - الاحتفاظ بالرسالة الخام الحقيقية (بدون تبسيط).
 *   - إخفاء التفاصيل الداخلية للتطبيق:
 *       • أسماء الحزم (com.flutteride.codweva.*)
 *       • أسماء ملفات الكود المصدري (*.java:NN)
 *       • الـ Stack trace
 *   - حماية المفاتيح الحساسة (sk-*, ghp_*, ...).
 */
object AiErrorFormatter {

    // ============================================================
    // الأنماط التي نُخفيها
    // ============================================================

    /** مسار كامل: /data/.../AiHttpClient.java:59 أو AiHttpClient.java:59 أو AiHttpClient.kt */
    private val INTERNAL_FILE_REF = Pattern.compile(
        "([A-Za-z_][A-Za-z0-9_]*)\\.(java|kt|kts|dart|xml|gradle)(:\\d+)?"
    )

    /** الحزمة الكاملة: com.flutteride.codweva.XxxClass أو com.flutteride.codweva.XxxClass$Inner */
    private val FULL_PACKAGE = Pattern.compile(
        "com\\.flutteride\\.codweva(\\.[A-Za-z_][A-Za-z0-9_]*)*(\\\$[A-Za-z_][A-Za-z0-9_]*)?"
    )

    /** سطر Stack trace: at com.xxx.Yyy.method(File.java:59) */
    private val STACK_TRACE_LINE = Pattern.compile(
        "(?m)^\\s*at\\s+[^\\n]+\\n?"
    )

    // ============================================================
    // المفاتيح الحساسة
    // ============================================================
    private val KEY_VALUE = Pattern.compile(
        "(?i)(key|token|authorization|bearer|api[_-]?key)\\s*[:=]\\s*\\S+"
    )

    private val SK_KEY = Pattern.compile(
        "(?i)(sk-)[A-Za-z0-9_\\-]{10,}"
    )

    private val GITHUB_TOKEN = Pattern.compile(
        "(?i)(ghp_|gho_|ghu_|ghs_|ghr_)[A-Za-z0-9]{10,}"
    )

    // ============================================================
    // الدالة الرئيسية
    // ============================================================

    /**
     * يُنسّق أي استثناء ويُعيد رسالة خام نظيفة.
     */
    @JvmStatic
    fun format(error: Throwable?): String {
        if (error == null) return "Unknown error"

        val sb = StringBuilder()

        // 1. نوع الاستثناء (مُبسّط، بدون حزمة)
        val simpleName = simplifyType(error.javaClass.name)
        sb.append("Type: ").append(simpleName).append("\n\n")

        // 2. الرسالة الرئيسية (نظيفة)
        sb.append("Message: ")
        sb.append(cleanMessage(error.message))

        // 3. سلسلة الأسباب (حتى 5 مستويات)
        var cause: Throwable? = error.cause
        var depth = 1
        while (cause != null && depth <= 5) {
            sb.append("\n\nCaused by [").append(depth).append("]: ")
                .append(simplifyType(cause.javaClass.name))
            sb.append("\nMessage: ")
            sb.append(cleanMessage(cause.message))
            cause = cause.cause
            depth++
        }

        // 4. تنظيف أخير (حماية المفاتيح)
        return sanitizeSecrets(sb.toString())
    }

    // ============================================================
    // تنظيف رسالة
    // ============================================================
    private fun cleanMessage(message: String?): String {
        if (message.isNullOrBlank()) {
            return "(no message)"
        }

        var result = message

        // 1. حذف أسطر Stack trace كاملة (at xxx)
        result = STACK_TRACE_LINE.matcher(result).replaceAll("")

        // 2. حذف الحزم الكاملة (com.flutteride.codweva.Xxx)
        result = FULL_PACKAGE.matcher(result).replaceAll("")

        // 3. حذف مراجع الملفات الداخلية (AiHttpClient.java:59)
        result = INTERNAL_FILE_REF.matcher(result).replaceAll("")

        // 4. حذف الفواصل البيضاء الزائدة والنقاط المعلقة
        result = result.replace(Regex("\\s+"), " ").trim()
        result = result.replace(Regex("\\s*\\.\\s*\\."), ".")
        result = result.replace(Regex("[:,\\s]+$"), "")
        result = result.replace(Regex("^[:,\\s]+"), "")

        if (result.isEmpty()) {
            return "(no message)"
        }

        // 5. حماية المفاتيح
        result = KEY_VALUE.matcher(result).replaceAll("$1: [hidden]")
        result = SK_KEY.matcher(result).replaceAll("$1[hidden]")
        result = GITHUB_TOKEN.matcher(result).replaceAll("$1[hidden]")

        return result
    }

    // ============================================================
    // تبسيط نوع الاستثناء
    // ============================================================
    private fun simplifyType(fullClassName: String?): String {
        if (fullClassName.isNullOrEmpty()) return "Exception"

        // حذف الحزمة: com.flutteride.codweva.AiHttpClient$AiApiException → AiApiException
        var result = fullClassName
        val lastDot = result.lastIndexOf('.')
        if (lastDot >= 0) result = result.substring(lastDot + 1)

        // حذف $ClassName$Inner → Inner
        val dollar = result.lastIndexOf('$')
        if (dollar >= 0) result = result.substring(dollar + 1)

        return result
    }

    // ============================================================
    // حماية المفاتيح في الناتج النهائي
    // ============================================================
    private fun sanitizeSecrets(text: String?): String {
        if (text.isNullOrEmpty()) return ""

        var result = text
        result = KEY_VALUE.matcher(result).replaceAll("$1: [hidden]")
        result = SK_KEY.matcher(result).replaceAll("$1[hidden]")
        result = GITHUB_TOKEN.matcher(result).replaceAll("$1[hidden]")

        return result
    }
}