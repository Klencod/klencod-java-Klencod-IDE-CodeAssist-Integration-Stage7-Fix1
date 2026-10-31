package com.flutteride.codweva

import org.json.JSONObject
import java.util.regex.Pattern

/**
 * وكيل آمن: يحول إجراءات JSON أو كتل الكود المقترحة إلى تغييرات تنتظر موافقة المستخدم.
 */
object AgentEngine {

    // ============================================================
    // أنماط التحليل
    // ============================================================
    private val ACTION_PATTERN = Pattern.compile(
        "<klencod_action>\\s*(.*?)\\s*</klencod_action>",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    private val CODE_BLOCK_PATTERN = Pattern.compile(
        "```(?:dart)?\\s*(.*?)```",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    private val DART_PATH_PATTERN = Pattern.compile(
        "(?<![A-Za-z0-9_./-])((?:lib|test|bin|web)/[A-Za-z0-9_./-]+\\.dart|[A-Za-z0-9_.-]+\\.dart)"
    )

    private val READ_ONLY_PATTERNS = Pattern.compile(
        "(?i)(?:^|\\s)(?:اقرأ|اقرء|أرني|ارني|اعرض|أعرض|اشرح|وضح|ما\\s+هو|ما\\s+هي|كيف|أين|اين|ماذا|هل\\s+يمكن|اشرح\\s+لي|اقتبس|انقل\\s+لي)(?:\\s|$|[،,.!?])"
    )

    private val MODIFY_PATTERNS = Pattern.compile(
        "(?i)(?:^|\\s)(?:أضف|اضف|عدل|عدّل|أصلح|اصلح|غير|غيّر|احذف|امسح|أنشئ|انشئ|اكتب|أكتب|استبدل|استبدلي|حول|حوّل|طور|حسّن|حدث|حدّث|refactor|add|fix|modify|update|create|delete|remove|change|write|insert|append|move|rename|نقل|انقل)(?:\\s|$|[،,.!?])"
    )

    // ============================================================
    // التحليل
    // ============================================================
    @JvmStatic
    fun parseActions(response: String?): List<AgentAction> {
        val actions = mutableListOf<AgentAction>()
        if (response == null) return actions

        val matcher = ACTION_PATTERN.matcher(response)
        while (matcher.find()) {
            try {
                val json = JSONObject(matcher.group(1))
                val action = json.optString("action", "")
                val path = json.optString("path", "")

                when (action) {
                    "write_file", "create_file" -> {
                        actions.add(AgentAction.write(path, json.optString("content", "")))
                    }
                    "delete_file" -> {
                        actions.add(AgentAction.delete(path))
                    }
                    "move_file" -> {
                        val from = json.optString("from", path)
                        val to = json.optString("to", "")
                        if (from.isNotEmpty() && to.isNotEmpty()) {
                            actions.add(AgentAction.move(from, to))
                        }
                    }
                }
            } catch (_: Exception) {
                // الرد النصي يبقى ظاهرًا للمستخدم، والإجراء المعيب لا ينفذ.
            }
        }
        return actions
    }

    @Deprecated("Use parseActions instead")
    @JvmStatic
    fun parseCodeBlocks(response: String?, fallbackPath: String?): List<AgentAction> {
        val actions = mutableListOf<AgentAction>()
        if (response == null) return actions

        val matcher = CODE_BLOCK_PATTERN.matcher(response)
        while (matcher.find()) {
            val content = matcher.group(1)?.trim() ?: ""
            if (content.isEmpty()) continue

            var path = findPathBefore(response, matcher.start())
            if (path.isEmpty()) path = fallbackPath?.trim() ?: ""
            if (!isSafePath(path)) continue

            actions.add(AgentAction.write(path, content))
        }
        return actions
    }

    private fun findPathBefore(response: String, end: Int): String {
        val prefix = response.substring(0, maxOf(0, end))
        val matcher = DART_PATH_PATTERN.matcher(prefix)
        var path = ""
        while (matcher.find()) {
            path = matcher.group(1) ?: ""
        }
        return path
    }

    private fun isSafePath(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        return !path.startsWith("/") &&
                !path.contains("../") &&
                path != ".."
    }

    @JvmStatic
    fun removeActionBlocks(response: String?): String {
        if (response == null) return ""
        return ACTION_PATTERN.matcher(response).replaceAll("").trim()
    }

    // ============================================================
    // تعليمات الوكيل
    // ============================================================
    @JvmStatic
    fun buildInstruction(): String {
        return "قواعد إلزامية:\n" +
                "\n" +
                "1. عند طلب المستخدم **القراءة فقط** (اقرأ، أرني، اعرض، اشرح، وضّح، ما هو، كيف، أين، ماذا، هل):\n" +
                "   • أجب بالنص العادي فقط.\n" +
                "   • **لا** تُرسل أي وسم <klencod_action>.\n" +
                "   • **لا** تُرسل كود داخل ```dart على أنها تعديل — يمكنك عرضه كنص توضيحي فقط.\n" +
                "   • مثال: «اقرأ أول 5 أسطر» → اكتب الأسطر كنص، ولا تقترح أي تعديل.\n" +
                "\n" +
                "2. عند طلب المستخدم **التعديل** (أضف، عدّل، أصلح، غيّر، احذف، أنشئ، اكتب، استبدل، حدّث، انقل، أعد تسمية):\n" +
                "   • أرسل التعديلات فقط داخل وسوم <klencod_action>.\n" +
                "   • كل إجراء داخل وسم منفصل بصيغة JSON صحيحة.\n" +
                "   • استخدم فقط: write_file، create_file، delete_file، move_file.\n" +
                "\n" +
                "3. لم تُطلب منك تعديلات = لا ترسل أي وسوم.\n" +
                "\n" +
                "4. عند إرسال تعديل، التزم بالصيغ التالية:\n" +
                "   • كتابة/تعديل ملف:\n" +
                "     <klencod_action>{\"action\":\"write_file\",\"path\":\"lib/main.dart\",\"content\":\"...\"}</klencod_action>\n" +
                "   • حذف ملف:\n" +
                "     <klencod_action>{\"action\":\"delete_file\",\"path\":\"lib/old.dart\"}</klencod_action>\n" +
                "   • نقل/إعادة تسمية ملف:\n" +
                "     <klencod_action>{\"action\":\"move_file\",\"from\":\"old/path.dart\",\"to\":\"new/path.dart\"}</klencod_action>\n" +
                "\n" +
                "5. أرسل إجراءً واحداً لكل ملف. لا تُدمج ملفات متعددة في إجراء واحد.\n" +
                "\n" +
                "6. مسارات آمنة فقط: ابدأ من جذر المشروع (lib/, test/, pubspec.yaml).\n" +
                "   لا مسارات تبدأ بـ / ولا تحتوي ../ ولا مسارات خارج المشروع.\n" +
                "\n" +
                "7. لا تعدّل ملفات الخادم أو البناء (build/, .git/, .dart_tool/, .gradle/) — ممنوعة تماماً.\n" +
                "\n" +
                "8. لن يُطبّق أي إجراء إلا بعد موافقة المستخدم. لذلك لا تفترض أنك عدّلت شيئاً.\n" +
                "\n" +
                "9. في ردود القراءة، يمكنك استخدام Markdown عادي (نقاط، اقتباسات، كود توضيحي).\n" +
                "\n" +
                "10. عند تغيير بنية المشروع (مثل إعادة تسمية مجلد حزمة):\n" +
                "    • استخدم move_file لنقل الملفات إلى المسارات الجديدة.\n" +
                "    • ثم استخدم write_file لتعديل محتوى الملفات (استيرادات، package، إلخ).\n" +
                "    • كل ذلك في نفس الرد إذا أمكن.\n" +
                "\n" +
                "11. إذا لم تكن متأكداً مما إذا كان الطلب قراءة أم تعديل، اسأل المستخدم أولاً."
    }

    // ============================================================
    // دالة مساعدة
    // ============================================================
    @JvmStatic
    fun isReadOnlyRequest(userMessage: String?): Boolean {
        if (userMessage == null) return false
        val msg = userMessage.trim()
        if (msg.isEmpty()) return false

        val hasModify = MODIFY_PATTERNS.matcher(msg).find()
        if (hasModify) return false

        return READ_ONLY_PATTERNS.matcher(msg).find()
    }

    // ============================================================
    // التطبيق
    // ============================================================
    @JvmStatic
    @Throws(Exception::class)
    fun apply(files: ProjectFileService, action: AgentAction?) {
        if (action == null) throw IllegalArgumentException("إجراء فارغ")

        when {
            action.isDelete -> files.deleteFile(action.path)
            action.isMove -> files.moveFile(action.path, action.destination)
            else -> files.writeFile(action.path, action.content)
        }
    }

    // ============================================================
    // نموذج الإجراء
    // ============================================================
    class AgentAction private constructor(
        val type: String,
        val path: String,
        val content: String,
        /** للـ move_file: المسار الجديد. */
        val destination: String,
        val isDelete: Boolean,
        val isMove: Boolean
    ) {

        fun summary(): String = when {
            isDelete -> "حذف: $path"
            isMove -> "نقل: $path → $destination"
            else -> "$type: $path (${content.length} حرف)"
        }

        companion object {
            @JvmStatic
            fun write(path: String?, content: String?): AgentAction =
                AgentAction(
                    "write_file",
                    path ?: "",
                    content ?: "",
                    "",
                    false,
                    false
                )

            @JvmStatic
            fun delete(path: String?): AgentAction =
                AgentAction(
                    "delete_file",
                    path ?: "",
                    "",
                    "",
                    true,
                    false
                )

            /** إجراء نقل: from = path، to = destination. */
            @JvmStatic
            fun move(from: String?, to: String?): AgentAction =
                AgentAction(
                    "move_file",
                    from ?: "",
                    "",
                    to ?: "",
                    false,
                    true
                )
        }
    }
}