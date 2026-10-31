package com.flutteride.codweva

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * HTTP client بسيط لمزودي AI.
 * يستخدم HttpURLConnection (بدون مكتبات خارجية).
 */
internal object AiHttpClient {

    /**
     * نسخة قديمة — تستخدم timeouts افتراضية.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun postJson(
        endpoint: String,
        apiKey: String?,
        body: JSONObject,
        keyInQuery: Boolean
    ): JSONObject {
        return postJson(endpoint, apiKey, body, keyInQuery, 30000, 120000)
    }

    /**
     * نسخة موسّعة — timeouts قابلة للتخصيص.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun postJson(
        endpoint: String,
        apiKey: String?,
        body: JSONObject,
        keyInQuery: Boolean,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): JSONObject {
        var finalEndpoint = endpoint
        if (keyInQuery) {
            val separator = if (endpoint.contains("?")) "&" else "?"
            val encodedKey = URLEncoder.encode(apiKey ?: "", "UTF-8")
            finalEndpoint += "$separator" + "key=$encodedKey"
        }

        val connection = (URL(finalEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (!keyInQuery) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }

        try {
            val payload = body.toString().toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { output ->
                output.write(payload)
                output.flush()
            }

            val status = connection.responseCode
            val stream: InputStream? = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val response = readFully(stream)

            if (status !in 200..299) {
                throw AiApiException(status, extractError(response))
            }

            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    // ============================================================
    // أدوات داخلية
    // ============================================================
    @Throws(Exception::class)
    private fun readFully(stream: InputStream?): String {
        if (stream == null) return ""
        val builder = StringBuilder()
        BufferedReader(
            InputStreamReader(stream, StandardCharsets.UTF_8)
        ).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                builder.append(line)
            }
        }
        return builder.toString()
    }

    private fun extractError(response: String?): String {
        if (response.isNullOrBlank()) {
            return "استجابة فارغة من مزود الذكاء الاصطناعي"
        }
        return try {
            val json = JSONObject(response)
            if (json.has("error")) {
                val error = json.get("error")
                if (error is JSONObject) {
                    error.optString("message", "فشل طلب الذكاء الاصطناعي")
                } else {
                    error.toString()
                }
            } else {
                json.optString("message", "فشل طلب الذكاء الاصطناعي")
            }
        } catch (_: Exception) {
            if (response.length > 500) response.substring(0, 500) else response
        }
    }

    // ============================================================
    // استثناء مخصص
    // ============================================================
    class AiApiException(
        val statusCode: Int,
        message: String
    ) : Exception("HTTP $statusCode: $message")
}