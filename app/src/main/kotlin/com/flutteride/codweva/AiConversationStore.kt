package com.flutteride.codweva

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * تخزين محادثة مستقلة لكل مشروع داخل مساحة التطبيق الخاصة.
 *
 * ملاحظة أمنية:
 *   - ملفات JSON محفوظة داخل filesDir/ai_conversations
 *   - لا تُشارك مع أي تطبيق آخر.
 *   - الرسائل المحتوية على دور "system" لا تُحفظ.
 */
class AiConversationStore(context: Context) {

    private val directory: File =
        File(context.filesDir, "ai_conversations").apply {
            if (!exists()) mkdirs()
        }

    // ============================================================
    // التحميل
    // ============================================================

    fun load(projectPath: String?): List<AiMessage> {
        val result = mutableListOf<AiMessage>()
        if (projectPath.isNullOrBlank()) return result

        val file = conversationFile(projectPath)
        if (!file.exists()) return result

        return try {
            val json = read(file)
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val role = item.optString("role", "assistant")
                val content = item.optString("content", "")
                if (content.isNotEmpty()) {
                    result.add(AiMessage(role, content))
                }
            }
            result
        } catch (_: Exception) {
            result
        }
    }

    // ============================================================
    // الحفظ
    // ============================================================

    fun save(projectPath: String?, messages: List<AiMessage>?) {
        if (projectPath.isNullOrBlank() || messages == null) return
        try {
            val array = JSONArray()
            for (message in messages) {
                if (message.role == "system") continue
                val item = JSONObject()
                item.put("role", message.role)
                item.put("content", message.content)
                array.put(item)
            }
            write(conversationFile(projectPath), array.toString())
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // الحذف
    // ============================================================

    fun clear(projectPath: String?) {
        if (projectPath.isNullOrBlank()) return
        val file = conversationFile(projectPath)
        if (file.exists()) file.delete()
    }

    // ============================================================
    // أدوات داخلية
    // ============================================================

    private fun conversationFile(projectPath: String): File =
        File(directory, sha256(projectPath) + ".json")

    private fun read(file: File): String {
        val builder = StringBuilder()
        BufferedReader(
            InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)
        ).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                builder.append(line)
            }
        }
        return builder.toString()
    }

    private fun write(file: File, content: String) {
        FileOutputStream(file).use { output ->
            OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                writer.write(content)
                writer.flush()
            }
        }
    }

    private fun sha256(value: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(value.toByteArray(StandardCharsets.UTF_8))
            val result = StringBuilder()
            for (b in bytes) result.append(String.format("%02x", b))
            result.toString()
        } catch (_: Exception) {
            Integer.toHexString(value.hashCode())
        }
    }
}