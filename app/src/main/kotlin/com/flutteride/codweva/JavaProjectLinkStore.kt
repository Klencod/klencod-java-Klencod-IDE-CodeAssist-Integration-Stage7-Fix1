package com.flutteride.codweva.java

import android.content.Context
import android.content.SharedPreferences
import com.flutteride.codweva.GitHubApi
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * JavaProjectLinkStore — يحفظ ربط المشروع المحلي بمستودع GitHub.
 *
 * مفيد لـ:
 *   - فتح صفحة المستودع مباشرة
 *   - حذف المستودع بشكل آمن
 *   - تجنّب البحث المتكرر عن المستودع
 *
 * v1.0 — نسخة Kotlin محسّنة من GitHubProjectLinkStore.java
 */
object JavaProjectLinkStore {

    private const val PREFS_NAME = "klencod_java_project_links"
    private const val KEY_LINKS = "links"

    // ============================================================
    // نموذج الرابط
    // ============================================================
    class Link(
        val projectPath: String,
        val owner: String,
        val repository: String,
        val htmlUrl: String,
        val branch: String
    ) {
        /** هل الرابط صالح للاستخدام؟ */
        val isUsable: Boolean
            get() = projectPath.isNotEmpty() &&
                    owner.isNotEmpty() &&
                    repository.isNotEmpty()

        /** هل الـ branch معروف؟ */
        val safeBranch: String
            get() = branch.ifEmpty { "main" }

        override fun toString(): String {
            return "Link{$owner/$repository@$safeBranch}"
        }
    }

    // ============================================================
    // حفظ
    // ============================================================

    /**
     * يحفظ ربط المشروع بـ repository.
     */
    @JvmStatic
    fun save(
        context: Context?,
        project: File?,
        repository: GitHubApi.Repository?
    ) {
        if (context == null || project == null || repository == null) return

        val path = canonicalPath(project)
        if (path.isEmpty()) return

        if (repository.owner.isBlank() || repository.name.isBlank()) return

        val links = readLinks(context)
        try {
            val value = JSONObject().apply {
                put("owner", repository.owner.trim())
                put("repository", repository.name.trim())
                put("htmlUrl", repository.htmlUrl?.trim() ?: "")
                put("branch", repository.defaultBranch?.trim() ?: "main")
            }
            links.put(path, value)
            writeLinks(context, links)
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // قراءة
    // ============================================================

    /**
     * يقرأ الرابط المحفوظ لمشروع معيّن.
     */
    @JvmStatic
    fun find(context: Context?, project: File?): Link? {
        if (context == null || project == null) return null

        val path = canonicalPath(project)
        if (path.isEmpty()) return null

        val links = readLinks(context)
        val value = links.optJSONObject(path) ?: return null

        val link = Link(
            projectPath = path,
            owner = value.optString("owner", "").trim(),
            repository = value.optString("repository", "").trim(),
            htmlUrl = value.optString("htmlUrl", "").trim(),
            branch = value.optString("branch", "main").trim()
        )

        return if (link.isUsable) link else null
    }

    // ============================================================
    // حذف
    // ============================================================

    /**
     * يحذف ربط مشروع معيّن.
     */
    @JvmStatic
    fun remove(context: Context?, project: File?) {
        if (context == null || project == null) return

        val path = canonicalPath(project)
        if (path.isEmpty()) return

        val links = readLinks(context)
        if (!links.has(path)) return

        links.remove(path)
        writeLinks(context, links)
    }

    /**
     * يحذف كل الروابط.
     */
    @JvmStatic
    fun clearAll(context: Context?) {
        if (context == null) return
        prefs(context).edit().remove(KEY_LINKS).apply()
    }

    // ============================================================
    // أدوات داخلية
    // ============================================================

    private fun readLinks(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY_LINKS, "") ?: ""
        if (raw.isBlank()) return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun writeLinks(context: Context, links: JSONObject) {
        prefs(context).edit()
            .putString(KEY_LINKS, links.toString())
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun canonicalPath(file: File): String {
        return try {
            file.canonicalPath
        } catch (_: Exception) {
            file.absolutePath
        }
    }
}