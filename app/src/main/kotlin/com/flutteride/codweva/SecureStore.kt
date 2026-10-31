package com.flutteride.codweva

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * تخزين محلي مشفّر لتوكنات GitHub وأسماء المستخدمين.
 *
 * v3.0 — دعم متعدد الحسابات:
 *   - كل حساب له توكنه المشفر منفصل (accountId)
 *   - توافق مع الإصدار القديم (بدون accountId)
 *   - الاحتفاظ بدالة readGitHubToken() بدون معامل للتوافق
 *
 * الأمان:
 *   - المفتاح (Key) محفوظ في Keystore النظام
 *   - القيم مشفّرة بـ AES-256-GCM
 *   - كل تشفير يولّد IV جديد
 */
class SecureStore(context: Context) {

    private val appContext: Context = context.applicationContext

    // ============================================================
    // ⭐ v3.0: حفظ توكن حساب محدد
    // ============================================================

    /**
     * يحفظ توكن GitHub لحساب محدد.
     *
     * @param accountId معرّف الحساب (lowercase username)
     * @param token التوكن
     */
    fun saveGitHubToken(accountId: String, token: String?): Boolean {
        if (accountId.isBlank() || token.isNullOrBlank()) return false

        val valueKey = valueKeyFor(accountId)
        val ivKey = ivKeyFor(accountId)

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
            prefs().edit()
                .putString(valueKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * يقرأ توكن حساب محدد.
     */
    fun readGitHubToken(accountId: String): String {
        if (accountId.isBlank()) return ""

        val valueKey = valueKeyFor(accountId)
        val ivKey = ivKeyFor(accountId)

        return try {
            val encoded = prefs().getString(valueKey, null) ?: return ""
            val encodedIv = prefs().getString(ivKey, null) ?: return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE, getKey(),
                GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * يحذف توكن حساب محدد.
     */
    fun clearGitHubToken(accountId: String) {
        if (accountId.isBlank()) return
        prefs().edit()
            .remove(valueKeyFor(accountId))
            .remove(ivKeyFor(accountId))
            .apply()
    }

    // ============================================================
    // ⭐ v3.0: دوال التوافق مع الإصدار القديم
    // ============================================================

    /**
     * للتوافق: يحفظ التوكن في مفتاح "default".
     *
     * ⚠️ deprecrated — استخدم saveGitHubToken(accountId, token)
     */
    @Deprecated("Use saveGitHubToken(accountId, token)")
    fun saveGitHubToken(token: String?): Boolean {
        return saveGitHubToken(DEFAULT_ACCOUNT_ID, token)
    }

    /**
     * للتوافق: يقرأ التوكن من مفتاح "default".
     *
     * ⚠️ deprecrated — استخدم readGitHubToken(accountId)
     */
    @Deprecated("Use readGitHubToken(accountId)")
    fun readGitHubToken(): String {
        return readGitHubToken(DEFAULT_ACCOUNT_ID)
    }

    /**
     * للتوافق: يحذف التوكن من مفتاح "default".
     */
    @Deprecated("Use clearGitHubToken(accountId)")
    fun clearGitHubToken() {
        clearGitHubToken(DEFAULT_ACCOUNT_ID)
    }

    // ============================================================
    // Username (متعدد الحسابات)
    // ============================================================

    /**
     * يحفظ اسم مستخدم GitHub لحساب محدد.
     */
    fun saveGitHubUsername(accountId: String, username: String?): Boolean {
        if (accountId.isBlank() || username.isNullOrBlank()) return false

        val valueKey = usernameValueKeyFor(accountId)
        val ivKey = usernameIvKeyFor(accountId)

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val encrypted = cipher.doFinal(username.toByteArray(StandardCharsets.UTF_8))
            prefs().edit()
                .putString(valueKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
        } catch (_: Exception) {
            false
        }
    }

    fun readGitHubUsername(accountId: String): String {
        if (accountId.isBlank()) return ""

        val valueKey = usernameValueKeyFor(accountId)
        val ivKey = usernameIvKeyFor(accountId)

        return try {
            val encoded = prefs().getString(valueKey, null) ?: return ""
            val encodedIv = prefs().getString(ivKey, null) ?: return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE, getKey(),
                GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * للتوافق: يحفظ اسم المستخدم في مفتاح "default".
     */
    @Deprecated("Use saveGitHubUsername(accountId, username)")
    fun saveGitHubUsername(username: String?): Boolean {
        return saveGitHubUsername(DEFAULT_ACCOUNT_ID, username)
    }

    /**
     * للتوافق: يقرأ اسم المستخدم من مفتاح "default".
     */
    @Deprecated("Use readGitHubUsername(accountId)")
    fun readGitHubUsername(): String {
        val value = readGitHubUsername(DEFAULT_ACCOUNT_ID)
        return if (value.isEmpty()) "GitHub user" else value
    }

    // ============================================================
    // مسح شامل
    // ============================================================

    /**
     * يمسح كل التوكنات (كل الحسابات).
     */
    fun clearAllTokens() {
        val p = prefs()
        val editor = p.edit()

        for (key in p.all.keys) {
            if (key.startsWith(TOKEN_VALUE_PREFIX) ||
                key.startsWith(TOKEN_IV_PREFIX) ||
                key.startsWith(USERNAME_VALUE_PREFIX) ||
                key.startsWith(USERNAME_IV_PREFIX) ||
                key == LEGACY_VALUE ||
                key == LEGACY_IV ||
                key == LEGACY_USERNAME ||
                key == LEGACY_USERNAME_IV
            ) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    /**
     * للتوافق: مسح التوكن القديم (default).
     */
    fun clearGitHubTokenLegacy() {
        prefs().edit()
            .remove(LEGACY_VALUE)
            .remove(LEGACY_IV)
            .remove(LEGACY_USERNAME)
            .remove(LEGACY_USERNAME_IV)
            .apply()
    }

    // ============================================================
    // أدوات داخلية
    // ============================================================

    private fun prefs(): SharedPreferences =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun valueKeyFor(accountId: String): String =
        "$TOKEN_VALUE_PREFIX$accountId"

    private fun ivKeyFor(accountId: String): String =
        "$TOKEN_IV_PREFIX$accountId"

    private fun usernameValueKeyFor(accountId: String): String =
        "$USERNAME_VALUE_PREFIX$accountId"

    private fun usernameIvKeyFor(accountId: String): String =
        "$USERNAME_IV_PREFIX$accountId"

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    // ============================================================
    // ثوابت
    // ============================================================
    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "klencod_ide_secure_store"
        private const val PREFS = "klencod_secure_store"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        // ⭐ v3.0: بادئات المفاتيح للحسابات
        private const val TOKEN_VALUE_PREFIX = "github_token_account_"
        private const val TOKEN_IV_PREFIX = "github_token_account_iv_"
        private const val USERNAME_VALUE_PREFIX = "github_username_account_"
        private const val USERNAME_IV_PREFIX = "github_username_account_iv_"

        // ⭐ v3.0: المفاتيح القديمة (للتوافق)
        private const val LEGACY_VALUE = "github_access_token"
        private const val LEGACY_IV = "github_access_token_iv"
        private const val LEGACY_USERNAME = "github_username"
        private const val LEGACY_USERNAME_IV = "github_username_iv"

        // ⭐ v3.0: معرّف افتراضي
        private const val DEFAULT_ACCOUNT_ID = "default"
    }
}