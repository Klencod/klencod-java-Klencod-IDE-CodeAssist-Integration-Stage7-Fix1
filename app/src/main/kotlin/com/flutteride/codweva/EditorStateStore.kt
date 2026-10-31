package com.flutteride.codweva

import android.content.Context
import android.content.SharedPreferences

/**
 * تخزين محلي لحالة محرر المشروع.
 */
class EditorStateStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ============================================================
    // Favorites
    // ============================================================
    fun getFavorites(projectKey: String): Set<String> {
        val stored = prefs.getStringSet(FAVORITES_PREFIX + projectKey, emptySet())
            ?: emptySet()
        return HashSet(stored)
    }

    fun setFavorites(projectKey: String, favorites: Set<String>?) {
        prefs.edit().putStringSet(
            FAVORITES_PREFIX + projectKey,
            HashSet(favorites ?: emptySet())
        ).apply()
    }

    // ============================================================
    // Recent Tabs
    // ============================================================
    fun getRecentTabs(projectKey: String): List<String> {
        val encoded = prefs.getString(TABS_PREFIX + projectKey, "") ?: ""
        if (encoded.isEmpty()) return emptyList()
        return encoded.split("\n").filter { it.trim().isNotEmpty() }
    }

    fun setRecentTabs(projectKey: String, tabs: List<String>?) {
        val builder = StringBuilder()
        tabs?.forEach { tab ->
            if (tab.isNotEmpty()) {
                if (builder.isNotEmpty()) builder.append('\n')
                builder.append(tab)
            }
        }
        prefs.edit().putString(TABS_PREFIX + projectKey, builder.toString()).apply()
    }

    // ============================================================
    // Active Tab
    // ============================================================
    fun getActiveTab(projectKey: String): String {
        return prefs.getString(ACTIVE_PREFIX + projectKey, "") ?: ""
    }

    fun setActiveTab(projectKey: String, path: String?) {
        prefs.edit().putString(ACTIVE_PREFIX + projectKey, path ?: "").apply()
    }

    // ============================================================
    // Clear
    // ============================================================
    fun clearProjectState(projectKey: String) {
        prefs.edit()
            .remove(FAVORITES_PREFIX + projectKey)
            .remove(TABS_PREFIX + projectKey)
            .remove(ACTIVE_PREFIX + projectKey)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "klencod_editor_state"
        private const val FAVORITES_PREFIX = "favorites_"
        private const val TABS_PREFIX = "tabs_"
        private const val ACTIVE_PREFIX = "active_"
    }
}