package com.flutteride.codweva

import org.json.JSONArray
import org.json.JSONObject

/**
 * نموذج موحد لأدوات الوكيل.
 *
 * الأدوات المتاحة:
 *   - read_file(path)           : يقرأ محتوى ملف
 *   - list_files()              : يسرد كل ملفات المشروع
 *   - get_file_info(path)       : معلومات (حجم + أسطر) بدون قراءة كاملة
 *   - search(query, ext?)       : يبحث عن نص في كل الملفات
 *   - move_file(from, to)       : ينقل أو يُعيد تسمية ملف
 *
 * ملاحظة أمنية:
 *   الوكيل لا يستطيع الكتابة أو الحذف مباشرة. الكتابة/النقل فقط عبر
 *   وسوم <klencod_action> + موافقة المستخدم.
 */
object AgentTool {

    // ============================================================
    // أسماء الأدوات (ثوابت)
    // ============================================================
    const val TOOL_READ_FILE = "read_file"
    const val TOOL_LIST_FILES = "list_files"
    const val TOOL_FILE_INFO = "get_file_info"
    const val TOOL_SEARCH = "search"
    const val TOOL_MOVE_FILE = "move_file"

    // ============================================================
    // نموذج استدعاء أداة من الـ AI
    // ============================================================
    class ToolCall(
        val id: String?,
        val name: String?,
        val arguments: JSONObject?
    ) {

        val safeId: String get() = id ?: ""
        val safeName: String get() = name ?: ""
        val safeArguments: JSONObject get() = arguments ?: JSONObject()

        fun getString(key: String): String {
            return safeArguments.optString(key, "") ?: ""
        }

        fun getOptionalString(key: String): String? {
            if (!safeArguments.has(key)) return null
            val v = safeArguments.optString(key, "") ?: return null
            return if (v.trim().isEmpty()) null else v
        }

        override fun toString(): String {
            return "ToolCall{$safeName($safeArguments)}"
        }
    }

    // ============================================================
    // نموذج نتيجة تنفيذ أداة
    // ============================================================
    class ToolResult(
        val callId: String?,
        val toolName: String?,
        val content: String?,
        val isError: Boolean
    ) {

        val safeCallId: String get() = callId ?: ""
        val safeToolName: String get() = toolName ?: ""
        val safeContent: String get() = content ?: ""

        companion object {
            @JvmStatic
            fun success(callId: String?, toolName: String?, content: String?): ToolResult =
                ToolResult(callId, toolName, content ?: "", false)

            @JvmStatic
            fun error(callId: String?, toolName: String?, message: String?): ToolResult =
                ToolResult(
                    callId,
                    toolName,
                    "ERROR: " + (message ?: "unknown"),
                    true
                )
        }
    }

    // ============================================================
    // Schemas بصيغة OpenAI
    // ============================================================
    @JvmStatic
    fun buildOpenAiTools(): JSONArray {
        val tools = JSONArray()
        try {
            tools.put(
                buildOpenAiFunction(
                    TOOL_READ_FILE,
                    "يقرأ محتوى ملف نصي داخل المشروع. استخدم هذا إذا احتجت رؤية كود ملف قبل تعديله.",
                    arrayOf(
                        arrayOf("path", "string", "المسار النسبي من جذر المشروع (مثال: lib/main.dart)")
                    ),
                    arrayOf("path")
                )
            )

            tools.put(
                buildOpenAiFunction(
                    TOOL_LIST_FILES,
                    "يسرد كل ملفات المشروع مع أحجامها وعدد أسطرها. استخدمه للاستكشاف الأولي.",
                    emptyArray(),
                    emptyArray()
                )
            )

            tools.put(
                buildOpenAiFunction(
                    TOOL_FILE_INFO,
                    "يُعطي معلومات عن ملف (حجم + عدد أسطر) بدون قراءة محتواه. أسرع من read_file.",
                    arrayOf(
                        arrayOf("path", "string", "المسار النسبي للملف")
                    ),
                    arrayOf("path")
                )
            )

            tools.put(
                buildOpenAiFunction(
                    TOOL_SEARCH,
                    "يبحث عن نص أو نمط في كل ملفات المشروع. مثال: ابحث عن 'setState' أو 'MyWidget'.",
                    arrayOf(
                        arrayOf("query", "string", "النص المطلوب البحث عنه"),
                        arrayOf("extension", "string", "امتداد الملفات (اختياري، مثال: dart). إن لم يُحدَّد، يبحث في كل الملفات النصية.")
                    ),
                    arrayOf("query")
                )
            )

            tools.put(
                buildOpenAiFunction(
                    TOOL_MOVE_FILE,
                    "ينقل ملفاً من مسار إلى آخر، أو يُعيد تسميته. " +
                            "يُنشئ المجلدات الوجهة تلقائياً. " +
                            "استخدمه لتغيير بنية المشروع (مثل إعادة تسمية مجلد الحزمة)، أو نقل ملف بين مجلدات. " +
                            "إذا لم تكن متأكداً أن الملف الوجهة غير موجود، تحقق أولاً بـ list_files.",
                    arrayOf(
                        arrayOf("from", "string", "المسار الحالي للملف (نسبي من جذر المشروع)"),
                        arrayOf("to", "string", "المسار الجديد (نسبي من جذر المشروع)")
                    ),
                    arrayOf("from", "to")
                )
            )
        } catch (_: Exception) {
        }
        return tools
    }

    private fun buildOpenAiFunction(
        name: String,
        description: String,
        params: Array<Array<String>>,
        required: Array<String>
    ): JSONObject {
        val fn = JSONObject()
        fn.put("name", name)
        fn.put("description", description)

        val paramsSchema = JSONObject()
        paramsSchema.put("type", "object")

        val props = JSONObject()
        for (p in params) {
            val prop = JSONObject()
            prop.put("type", p[1])
            prop.put("description", p[2])
            props.put(p[0], prop)
        }
        paramsSchema.put("properties", props)

        val req = JSONArray()
        for (r in required) req.put(r)
        paramsSchema.put("required", req)

        fn.put("parameters", paramsSchema)

        val wrapper = JSONObject()
        wrapper.put("type", "function")
        wrapper.put("function", fn)
        return wrapper
    }

    // ============================================================
    // Schemas بصيغة Gemini
    // ============================================================
    @JvmStatic
    fun buildGeminiFunctionDeclarations(): JSONArray {
        val arr = JSONArray()
        try {
            arr.put(
                buildGeminiFunction(
                    TOOL_READ_FILE,
                    "يقرأ محتوى ملف نصي داخل المشروع.",
                    arrayOf(
                        arrayOf("path", "STRING", "المسار النسبي من جذر المشروع")
                    ),
                    arrayOf("path")
                )
            )
            arr.put(
                buildGeminiFunction(
                    TOOL_LIST_FILES,
                    "يسرد كل ملفات المشروع مع أحجامها.",
                    emptyArray(),
                    emptyArray()
                )
            )
            arr.put(
                buildGeminiFunction(
                    TOOL_FILE_INFO,
                    "معلومات عن ملف (حجم + عدد أسطر) بدون قراءته.",
                    arrayOf(
                        arrayOf("path", "STRING", "المسار النسبي للملف")
                    ),
                    arrayOf("path")
                )
            )
            arr.put(
                buildGeminiFunction(
                    TOOL_SEARCH,
                    "يبحث عن نص في كل ملفات المشروع.",
                    arrayOf(
                        arrayOf("query", "STRING", "النص المطلوب البحث عنه"),
                        arrayOf("extension", "STRING", "امتداد الملفات (اختياري)")
                    ),
                    arrayOf("query")
                )
            )
            arr.put(
                buildGeminiFunction(
                    TOOL_MOVE_FILE,
                    "ينقل ملفاً من مسار إلى آخر، أو يُعيد تسميته. " +
                            "يُنشئ المجلدات الوجهة تلقائياً. مفيد لإعادة هيكلة المشروع.",
                    arrayOf(
                        arrayOf("from", "STRING", "المسار الحالي للملف"),
                        arrayOf("to", "STRING", "المسار الجديد")
                    ),
                    arrayOf("from", "to")
                )
            )
        } catch (_: Exception) {
        }
        return arr
    }

    private fun buildGeminiFunction(
        name: String,
        description: String,
        params: Array<Array<String>>,
        required: Array<String>
    ): JSONObject {
        val fn = JSONObject()
        fn.put("name", name)
        fn.put("description", description)

        val paramsSchema = JSONObject()
        paramsSchema.put("type", "OBJECT")

        val props = JSONObject()
        for (p in params) {
            val prop = JSONObject()
            prop.put("type", p[1])
            prop.put("description", p[2])
            props.put(p[0], prop)
        }
        paramsSchema.put("properties", props)

        val req = JSONArray()
        for (r in required) req.put(r)
        paramsSchema.put("required", req)

        fn.put("parameters", paramsSchema)
        return fn
    }

    // ============================================================
    // تحليل Tool Calls
    // ============================================================
    @JvmStatic
    fun parseOpenAiToolCalls(message: JSONObject?): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        if (message == null) return calls

        val arr = message.optJSONArray("tool_calls") ?: return calls

        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            val fn = item.optJSONObject("function") ?: continue
            val name = fn.optString("name", "")
            val argsRaw = fn.optString("arguments", "{}")

            val args = try {
                JSONObject(argsRaw)
            } catch (_: Exception) {
                JSONObject()
            }

            if (name.isEmpty()) continue
            calls.add(ToolCall(id, name, args))
        }
        return calls
    }

    @JvmStatic
    fun parseGeminiFunctionCalls(parts: JSONArray?): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        if (parts == null) return calls

        var counter = 0
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val fnCall = part.optJSONObject("functionCall") ?: continue
            val name = fnCall.optString("name", "")
            var args = fnCall.optJSONObject("args")
            if (args == null) args = JSONObject()
            if (name.isEmpty()) continue

            val id = "gemini_call_" + System.currentTimeMillis() + "_" + (counter++)
            calls.add(ToolCall(id, name, args))
        }
        return calls
    }

    // ============================================================
    // تنسيق النتائج
    // ============================================================
    @JvmStatic
    fun buildOpenAiToolMessage(result: ToolResult): JSONObject {
        val msg = JSONObject()
        msg.put("role", "tool")
        msg.put("tool_call_id", result.safeCallId)
        msg.put("content", result.safeContent)
        return msg
    }

    @JvmStatic
    fun buildGeminiFunctionResponse(result: ToolResult): JSONObject {
        val fnResp = JSONObject()
        val resp = JSONObject()
        resp.put("content", result.safeContent)
        if (result.isError) resp.put("error", true)
        fnResp.put("name", result.safeToolName)
        fnResp.put("response", resp)

        val part = JSONObject()
        part.put("functionResponse", fnResp)
        return part
    }
}