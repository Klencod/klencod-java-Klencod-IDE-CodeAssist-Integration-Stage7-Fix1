package com.flutteride.codweva

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * DartCompletions — محمّل قاموس Dart/Flutter.
 */
class DartCompletions private constructor() {

    class Item(
        val name: String,
        val description: String,
        val snippet: String,
        val kind: ItemKind,
        val category: String,
        val signature: String,
        val returnType: String,
        val className: String,
        val priority: Int,
        val applicableTo: List<String>?,
        val enumValues: List<String>?,
        val isSnippetVariant: Boolean = false
    ) {
        val safeApplicableTo: List<String> get() = applicableTo ?: emptyList()
        val safeEnumValues: List<String> get() = enumValues ?: emptyList()

        fun appliesTo(widgetName: String?): Boolean {
            if (widgetName.isNullOrEmpty()) return true
            if (safeApplicableTo.isEmpty()) return true
            return safeApplicableTo.contains(widgetName)
        }

        fun hasSnippet(): Boolean = snippet.isNotEmpty()

        fun asSnippetVariant(displayName: String): Item {
            return Item(
                displayName, "$description — إدراج كامل", snippet, kind, category,
                signature, returnType, className, priority, applicableTo, enumValues, true
            )
        }

        fun asPlainVariant(): Item {
            return Item(
                name, description, "", kind, category, signature, returnType,
                className, priority, applicableTo, enumValues, false
            )
        }

        override fun toString(): String =
            "$kind:$name" + if (isSnippetVariant) " [snippet]" else ""
    }

    enum class ItemKind(val symbol: String) {
        WIDGET("W"), PROPERTY("P"), METHOD("M"), TYPE("T"),
        KEYWORD("K"), IMPORT("I"), ENUM_VALUE("E")
    }

    private val byFirstChar = hashMapOf<String, MutableList<Item>>()
    private val allItems = mutableListOf<Item>()
    private var loaded = false

    private val widgetsByName = hashMapOf<String, Item>()
    private val propertiesByWidget = hashMapOf<String, MutableList<Item>>()
    private val enumsByName = hashMapOf<String, Item>()

    companion object {
        private const val TAG = "DartCompletions"
        private const val ASSETS_DIR = "dart_completions"

        @Volatile
        private var instance: DartCompletions? = null

        @JvmStatic
        fun get(context: Context): DartCompletions {
            return instance ?: synchronized(this) {
                instance ?: DartCompletions().apply {
                    load(context.applicationContext)
                    instance = this
                }
            }
        }

        @JvmStatic
        fun isUsefulSnippet(snippet: String?): Boolean {
            if (snippet.isNullOrEmpty()) return false
            if (!snippet.contains("\n")) return false
            return snippet.length > 40
        }
    }

    private fun load(context: Context) {
        if (loaded) return
        val start = System.currentTimeMillis()

        try {
            loadWidgetsFile(context, "widgets_part1.json")
            loadWidgetsFile(context, "widgets_part2.json")
            loadWidgetsFile(context, "widgets_part3.json")
            loadPropertiesFile(context)
            loadMethodsFile(context)
            loadTypesFile(context)
            loadKeywordsFile(context)
            loadImportsFile(context)
            loadSnippetsFile(context)
            buildFirstCharIndex()
            loaded = true
            Log.i(TAG, "تم تحميل ${allItems.size} عنصر في ${System.currentTimeMillis() - start}ms")
        } catch (e: Exception) {
            Log.e(TAG, "فشل التحميل: ${e.message}", e)
        }
    }

    private fun readJson(context: Context, fileName: String): JSONObject {
        context.assets.open("$ASSETS_DIR/$fileName").use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.startsWith("//")) continue
                    sb.append(stripTrailingComment(line!!)).append('\n')
                }
                return JSONObject(sb.toString())
            }
        }
    }

    private fun stripTrailingComment(line: String?): String {
        if (line == null) return ""
        var commentIdx = -1
        var insideString = false
        var escaped = false
        var stringQuote = ' '

        for (i in 0 until line.length - 1) {
            val c = line[i]
            if (escaped) { escaped = false; continue }
            if (c == '\\' && insideString) { escaped = true; continue }
            if (c == '"' || c == '\'') {
                if (!insideString) { insideString = true; stringQuote = c }
                else if (c == stringQuote) { insideString = false }
                continue
            }
            if (!insideString && c == '/' && line[i + 1] == '/') {
                commentIdx = i
                break
            }
        }
        if (commentIdx < 0) return line
        return line.substring(0, commentIdx).trim()
    }

    private fun loadWidgetsFile(context: Context, fileName: String) {
        try {
            val json = readJson(context, fileName)
            val widgets = json.optJSONArray("widgets") ?: return
            for (i in 0 until widgets.length()) {
                val w = widgets.optJSONObject(i) ?: continue
                val name = w.optString("name", "")
                if (name.isEmpty()) continue
                val item = Item(
                    name, w.optString("description", ""), w.optString("snippet", ""),
                    ItemKind.WIDGET, w.optString("category", ""),
                    "", "", "", w.optInt("priority", 5), null, null
                )
                addItem(item)
                if (!widgetsByName.containsKey(name)) widgetsByName[name] = item
            }
        } catch (e: Exception) {
            Log.w(TAG, "widgets: ${e.message}")
        }
    }

    private fun loadPropertiesFile(context: Context) {
        try {
            val json = readJson(context, "properties.json")
            val props = json.optJSONArray("properties") ?: return
            val merged = hashMapOf<String, Item>()
            for (i in 0 until props.length()) {
                val p = props.optJSONObject(i) ?: continue
                val name = p.optString("name", "")
                if (name.isEmpty()) continue
                val applicableTo = readStringArray(p.optJSONArray("applicable_to"))
                val existing = merged[name]
                if (existing != null) {
                    val combined = HashSet(existing.safeApplicableTo)
                    combined.addAll(applicableTo)
                    merged[name] = Item(
                        name,
                        if (existing.description.isEmpty()) p.optString("description", "")
                        else existing.description,
                        existing.snippet, ItemKind.PROPERTY,
                        if (existing.category.isEmpty()) p.optString("type", "") else existing.category,
                        "", p.optString("type", ""), "", existing.priority,
                        ArrayList(combined), null
                    )
                } else {
                    merged[name] = Item(
                        name, p.optString("description", ""), "",
                        ItemKind.PROPERTY, p.optString("type", ""),
                        "", p.optString("type", ""), "", 5, applicableTo, null
                    )
                }
            }
            for (item in merged.values) {
                addItem(item)
                for (widget in item.safeApplicableTo) {
                    propertiesByWidget.getOrPut(widget) { mutableListOf() }.add(item)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "properties: ${e.message}")
        }
    }

    private fun loadMethodsFile(context: Context) {
        try {
            val json = readJson(context, "methods.json")
            val methods = json.optJSONArray("methods") ?: return
            for (i in 0 until methods.length()) {
                val m = methods.optJSONObject(i) ?: continue
                val name = m.optString("name", "")
                if (name.isEmpty()) continue
                addItem(Item(
                    name, m.optString("description", ""), "",
                    ItemKind.METHOD, m.optString("category", ""),
                    m.optString("signature", ""), m.optString("return", ""),
                    m.optString("class", ""), 5, null, null
                ))
            }
        } catch (e: Exception) {
            Log.w(TAG, "methods: ${e.message}")
        }
    }

    private fun loadTypesFile(context: Context) {
        try {
            val json = readJson(context, "types.json")
            json.optJSONArray("types")?.let { types ->
                for (i in 0 until types.length()) {
                    val t = types.optJSONObject(i) ?: continue
                    val name = t.optString("name", "")
                    if (name.isEmpty()) continue
                    addItem(Item(
                        name, t.optString("description", ""), "",
                        ItemKind.TYPE, t.optString("category", ""),
                        "", "", "", 6, null, null
                    ))
                }
            }
            json.optJSONArray("enums")?.let { enums ->
                for (i in 0 until enums.length()) {
                    val e = enums.optJSONObject(i) ?: continue
                    val name = e.optString("name", "")
                    if (name.isEmpty()) continue
                    val values = readStringArray(e.optJSONArray("values"))
                    val enumType = Item(
                        name, "قيم: ${values.size}", "",
                        ItemKind.TYPE, "enum", "", "", "", 5, null, values
                    )
                    addItem(enumType)
                    enumsByName[name] = enumType
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "types: ${e.message}")
        }
    }

    private fun loadKeywordsFile(context: Context) {
        try {
            val json = readJson(context, "keywords.json")
            val keywords = json.optJSONArray("keywords") ?: return
            for (i in 0 until keywords.length()) {
                val k = keywords.optJSONObject(i) ?: continue
                val name = k.optString("name", "")
                if (name.isEmpty()) continue
                addItem(Item(
                    name, k.optString("description", ""), "",
                    ItemKind.KEYWORD, k.optString("category", ""),
                    "", "", "", 4, null, null
                ))
            }
        } catch (e: Exception) {
            Log.w(TAG, "keywords: ${e.message}")
        }
    }

    private fun loadImportsFile(context: Context) {
        try {
            val json = readJson(context, "imports.json")
            val imports = json.optJSONArray("imports") ?: return
            for (i in 0 until imports.length()) {
                val imp = imports.optJSONObject(i) ?: continue
                val path = imp.optString("path", "")
                if (path.isEmpty()) continue
                addItem(Item(
                    path, imp.optString("description", ""),
                    "import '$path';", ItemKind.IMPORT,
                    imp.optString("category", ""), "", "", "",
                    imp.optInt("priority", 5), null, null
                ))
            }
        } catch (e: Exception) {
            Log.w(TAG, "imports: ${e.message}")
        }
    }

    private fun loadSnippetsFile(context: Context) {
        try {
            val json = readJson(context, "snippets.json")
            val snippets = json.optJSONArray("snippets") ?: return
            for (i in 0 until snippets.length()) {
                val s = snippets.optJSONObject(i) ?: continue
                val prefix = s.optString("prefix", "")
                if (prefix.isEmpty()) continue
                addItem(Item(
                    prefix, s.optString("description", ""),
                    s.optString("body", ""), ItemKind.WIDGET,
                    s.optString("category", ""), "", "", "", 7, null, null
                ))
            }
        } catch (e: Exception) {
            Log.w(TAG, "snippets: ${e.message}")
        }
    }

    private fun addItem(item: Item?) {
        if (item == null || item.name.isEmpty()) return
        allItems.add(item)
    }

    private fun buildFirstCharIndex() {
        byFirstChar.clear()
        for (item in allItems) {
            val key = firstChar(item.name)
            byFirstChar.getOrPut(key) { mutableListOf() }.add(item)
        }
        for (list in byFirstChar.values) {
            list.sortWith(compareByDescending { it.priority })
        }
    }

    private fun firstChar(s: String?): String {
        if (s.isNullOrEmpty()) return "_"
        val c = s[0]
        return when {
            c in 'A'..'Z' -> c.lowercaseChar().toString()
            c in 'a'..'z' -> c.toString()
            c in '0'..'9' -> "#"
            else -> "_"
        }
    }

    @JvmOverloads
    fun byPrefix(prefix: String?, limit: Int, widgetContext: String? = null): List<Item> {
        val safePrefix = prefix ?: ""
        val lower = safePrefix.lowercase(Locale.US)
        val results = mutableListOf<Item>()

        if (lower.isEmpty()) {
            val sorted = allItems.sortedWith(compareByDescending { it.priority })
            for (item in sorted) {
                if (results.size >= limit) break
                if (!matchesContext(item, widgetContext)) continue
                results.add(item)
            }
            return results
        }

        val bucket = byFirstChar[lower.substring(0, 1)] ?: return results
        for (item in bucket) {
            if (results.size >= limit) break
            if (!item.name.lowercase(Locale.US).startsWith(lower)) continue
            if (!matchesContext(item, widgetContext)) continue
            results.add(item)
        }

        results.sortWith(Comparator { a, b ->
            val lenCmp = a.name.length.compareTo(b.name.length)
            if (lenCmp != 0) lenCmp else b.priority.compareTo(a.priority)
        })
        return results
    }

    fun byPrefixAndWidget(prefix: String?, widgetName: String?, limit: Int): List<Item> {
        return byPrefix(prefix, limit, widgetName)
    }

    private fun matchesContext(item: Item?, widgetContext: String?): Boolean {
        if (item == null) return false
        if (item.kind != ItemKind.PROPERTY) return true
        if (widgetContext.isNullOrEmpty()) return true
        return item.appliesTo(widgetContext)
    }

    fun byPrefixWithVariants(
        prefix: String?,
        widgetContext: String?,
        limit: Int
    ): List<Item> {
        val base = byPrefix(prefix, limit, widgetContext)
        return expandVariants(base, limit)
    }

    private fun expandVariants(base: List<Item>?, limit: Int): List<Item> {
        if (base.isNullOrEmpty()) return base ?: emptyList()
        val expanded = mutableListOf<Item>()
        for (item in base) {
            if (item.kind == ItemKind.WIDGET && isUsefulSnippet(item.snippet)) {
                expanded.add(item.asPlainVariant())
                expanded.add(item.asSnippetVariant("${item.name}( … )"))
            } else {
                expanded.add(item)
            }
            if (expanded.size >= limit) break
        }
        return if (expanded.size > limit) expanded.take(limit) else expanded
    }

    fun isLoaded(): Boolean = loaded

    private fun readStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val result = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val s = array.optString(i, "")
            if (s.isNotEmpty()) result.add(s)
        }
        return result
    }
}