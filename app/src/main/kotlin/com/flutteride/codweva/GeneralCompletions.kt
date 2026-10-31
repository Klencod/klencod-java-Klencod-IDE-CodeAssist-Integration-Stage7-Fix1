package com.flutteride.codweva

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * GeneralCompletions — محمّل عام لملفات الإكمال.
 */
class GeneralCompletions private constructor(
    val language: String,
    val items: List<Item>
) {

    class Item(
        val name: String,
        val kind: Kind,
        val description: String,
        val insert: String?,
        val priority: Int
    ) {
        val insertText: String get() = insert ?: name
        val hasCursor: Boolean get() = insertText.contains("\$0")
        val isMultiLine: Boolean get() = insertText.contains("\n")
        override fun toString(): String = "$kind:$name"
    }

    enum class Kind(val label: String) {
        KEYWORD("كلمة"), TYPE("نوع"), METHOD("دالة"), SNIPPET("قالب"),
        TAG("وسم"), ATTRIBUTE("خاصية"), PROPERTY("خصيصة"),
        VALUE("قيمة"), UNKNOWN("—");

        companion object {
            @JvmStatic
            fun fromString(s: String?): Kind {
                if (s == null) return UNKNOWN
                return when (s.lowercase(Locale.US)) {
                    "keyword" -> KEYWORD
                    "type" -> TYPE
                    "method" -> METHOD
                    "snippet" -> SNIPPET
                    "tag" -> TAG
                    "attribute" -> ATTRIBUTE
                    "property" -> PROPERTY
                    "value" -> VALUE
                    else -> UNKNOWN
                }
            }
        }
    }

    private val byFirstChar = hashMapOf<String, MutableList<Item>>()
    private val byLowerName = hashMapOf<String, Item>()

    private fun buildIndexes() {
        byFirstChar.clear()
        byLowerName.clear()
        for (item in items) {
            byFirstChar.getOrPut(firstChar(item.name)) { mutableListOf() }.add(item)
            byLowerName[item.name.lowercase(Locale.US)] = item
        }
        for (list in byFirstChar.values) {
            list.sortWith(compareByDescending<Item> { it.priority }.thenBy { it.name.length })
        }
    }

    private fun firstChar(s: String): String {
        if (s.isEmpty()) return "_"
        val c = s[0]
        return when {
            c in 'A'..'Z' -> c.lowercaseChar().toString()
            c in 'a'..'z' -> c.toString()
            c in '0'..'9' -> "#"
            else -> "_"
        }
    }

    fun byPrefix(prefix: String?, limit: Int = 50): List<Item> {
        val safe = prefix ?: ""
        val lower = safe.lowercase(Locale.US)
        val results = mutableListOf<Item>()

        if (lower.isEmpty()) {
            val sorted = items.sortedWith(
                compareByDescending<Item> { it.priority }.thenBy { it.name }
            )
            for (item in sorted) {
                if (results.size >= limit) break
                results.add(item)
            }
            return results
        }

        val bucket = byFirstChar[lower.substring(0, 1)] ?: return results
        for (item in bucket) {
            if (results.size >= limit) break
            if (!item.name.lowercase(Locale.US).startsWith(lower)) continue
            results.add(item)
        }
        results.sortWith(
            compareByDescending<Item> { it.priority }.thenBy { it.name.length }
        )
        return results
    }

    fun fuzzyMatch(prefix: String?, limit: Int = 30): List<Item> {
        val safe = prefix ?: ""
        val lower = safe.lowercase(Locale.US)
        if (lower.isEmpty()) return emptyList()

        val results = mutableListOf<Pair<Item, Int>>()
        for (item in items) {
            val score = fuzzyScore(item.name.lowercase(Locale.US), lower)
            if (score > 0) results.add(item to score)
        }
        results.sortWith(
            compareByDescending<Pair<Item, Int>> { it.second }
                .thenByDescending { it.first.priority }
        )
        return results.take(limit).map { it.first }
    }

    private fun fuzzyScore(name: String, query: String): Int {
        if (name.isEmpty() || query.isEmpty()) return 0
        if (name.startsWith(query)) return 1000 - name.length
        val idx = name.indexOf(query)
        if (idx >= 0) return 500 - idx - name.length
        var qi = 0
        for (c in name) if (qi < query.length && c == query[qi]) qi++
        if (qi == query.length) return 100 - name.length
        return 0
    }

    fun size(): Int = items.size

    companion object {
        private const val TAG = "GeneralCompletions"
        private const val ASSETS_DIR = "completions"

        val SUPPORTED_LANGUAGES = hashSetOf(
            "java", "kotlin", "typescript", "javascript",
            "xml", "html", "css", "yaml", "shell", "groovy"
        )

        private val cache = hashMapOf<String, GeneralCompletions?>()

        @JvmStatic
        fun get(context: Context?, language: String?): GeneralCompletions? {
            if (context == null || language.isNullOrBlank()) return null
            val lang = normalizeLanguage(language) ?: return null
            if (!SUPPORTED_LANGUAGES.contains(lang)) return null

            synchronized(cache) {
                if (cache.containsKey(lang)) return cache[lang]
                val loaded = load(context.applicationContext, lang)
                cache[lang] = loaded
                return loaded
            }
        }

        @JvmStatic
        fun normalizeLanguage(input: String?): String? {
            if (input.isNullOrBlank()) return null
            return when (input.lowercase(Locale.US).trim()) {
                "java" -> "java"
                "kotlin", "kt", "kts" -> "kotlin"
                "typescript", "ts" -> "typescript"
                "javascript", "js", "mjs", "cjs" -> "javascript"
                "xml" -> "xml"
                "html", "htm" -> "html"
                "css", "scss", "sass", "less" -> "css"
                "yaml", "yml" -> "yaml"
                "shell", "sh", "bash", "zsh", "fish" -> "shell"
                "groovy", "gradle", "gvy" -> "groovy"
                else -> null
            }
        }

        private fun load(context: Context, language: String): GeneralCompletions? {
            return try {
                val json = readAsset(context, "$ASSETS_DIR/$language.json") ?: return null
                val itemsArray = json.optJSONArray("items") ?: return null
                val items = mutableListOf<Item>()

                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "")
                    if (name.isEmpty()) continue
                    items.add(Item(
                        name = name,
                        kind = Kind.fromString(obj.optString("kind", "")),
                        description = obj.optString("description", ""),
                        insert = obj.optString("insert", "").takeIf { it.isNotEmpty() },
                        priority = obj.optInt("priority", 5)
                    ))
                }

                val result = GeneralCompletions(language, items)
                result.buildIndexes()
                Log.i(TAG, "Loaded $language: ${items.size} items")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $language: ${e.message}", e)
                null
            }
        }

        private fun readAsset(context: Context, path: String): JSONObject? {
            return try {
                context.assets.open(path).use { input ->
                    BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                        val sb = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val trimmed = line!!.trim()
                            if (trimmed.startsWith("//")) continue
                            sb.append(line).append('\n')
                        }
                        JSONObject(sb.toString())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "readAsset($path): ${e.message}")
                null
            }
        }
    }
}