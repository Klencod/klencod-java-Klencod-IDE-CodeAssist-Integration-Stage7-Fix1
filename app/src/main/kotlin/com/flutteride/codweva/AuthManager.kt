package com.flutteride.codweva

import android.content.Context
import android.content.SharedPreferences

/**
 * واجهة توافقية قديمة تستخدمها MainActivity.
 *
 * v2.0 — تكامل مع GitHubAccountManager:
 *   - isLoggedIn يقرأ من الحساب النشط
 *   - getAccessToken يقرأ من الحساب النشط
 *   - logout يحذف الحساب النشط فقط
 *
 * Singleton — نسخة واحدة لكل تطبيق.
 */
class AuthManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val secureStore: SecureStore = SecureStore(appContext)
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("github_profile", Context.MODE_PRIVATE)

    // ============================================================
    // GitHub Token
    // ============================================================

    /**
     * للتوافق: يحفظ التوكن للحساب النشط.
     */
    @Deprecated("Use GitHubAccountManager.addAccount()")
    fun saveAccessToken(token: String?) {
        val active = GitHubAccountManager.getActive(appContext)
        if (active != null) {
            secureStore.saveGitHubToken(active.id, token)
        } else {
            // لا يوجد حساب نشط → احفظ كـ "default"
            secureStore.saveGitHubToken(token)
        }
    }

    /**
     * للتوافق: يقرأ توكن الحساب النشط.
     */
    fun getAccessToken(): String {
        // 1) جرّب الحساب النشط
        val active = GitHubAccountManager.getActive(appContext)
        if (active != null) {
            val token = secureStore.readGitHubToken(active.id)
            if (token.isNotEmpty()) return token
        }

        // 2) جرّب "default" (للتوافق مع الإصدار القديم)
        return secureStore.readGitHubToken()
    }

    // ============================================================
    // GitHub Username
    // ============================================================

    fun saveGithubUsername(username: String?) {
        val active = GitHubAccountManager.getActive(appContext)
        if (active != null) {
            secureStore.saveGitHubUsername(active.id, username)
        } else {
            secureStore.saveGitHubUsername(username)
        }
        prefs.edit().putString("github_username", username ?: "").apply()
    }

    fun getGithubUsername(): String {
        val active = GitHubAccountManager.getActive(appContext)
        if (active != null) {
            val name = secureStore.readGitHubUsername(active.id)
            if (name.isNotEmpty()) return name
        }
        return prefs.getString("github_username", "") ?: ""
    }

    // ============================================================
    // هل المستخدم مسجّل دخول؟
    // ============================================================

    val isLoggedIn: Boolean
        get() {
            // 1) الحساب النشط
            val active = GitHubAccountManager.getActive(appContext)
            if (active != null) {
                val token = secureStore.readGitHubToken(active.id)
                if (token.isNotEmpty()) return true
            }

            // 2) توكن قديم (للتوافق)
            val legacy = secureStore.readGitHubToken()
            return legacy.isNotEmpty()
        }

    // ============================================================
    // تسجيل الخروج
    // ============================================================

    /**
     * v2.0: يحذف الحساب النشط فقط، وليس كل الحسابات.
     *
     * إذا أردت حذف الكل → استخدم GitHubAccountManager.clearAll()
     */
    fun logout() {
        val active = GitHubAccountManager.getActive(appContext)
        if (active != null) {
            GitHubAccountManager.removeAccount(appContext, active.id)
        } else {
            // لا يوجد حساب نشط → امسح القديم
            secureStore.clearGitHubTokenLegacy()
        }
        prefs.edit().clear().apply()
    }

    /**
     * v2.0: حذف كل الحسابات (تسجيل خروج كامل).
     */
    fun logoutAll() {
        GitHubAccountManager.clearAll(appContext)
        secureStore.clearAllTokens()
        prefs.edit().clear().apply()
    }

    // ============================================================
    // رأس المصادقة
    // ============================================================

    fun getAuthHeader(): String? {
        val token = getAccessToken()
        return if (token.trim().isEmpty()) null else "Bearer $token"
    }

    fun refreshAccessToken(): Boolean {
        return isLoggedIn
    }

    // ============================================================
    // Singleton
    // ============================================================
    companion object {
        @Volatile
        private var instance: AuthManager? = null

        @JvmStatic
        fun getInstance(context: Context): AuthManager {
            return instance ?: synchronized(this) {
                instance ?: AuthManager(context).also { instance = it }
            }
        }
    }
}