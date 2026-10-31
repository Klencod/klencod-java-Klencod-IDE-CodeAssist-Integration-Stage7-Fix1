package com.flutteride.codweva

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * GitHubAccountManager — إدارة متعددة حسابات GitHub.
 *
 * v2.0 — إضافة الحسابات عبر Device Flow:
 *   - إلغاء الحقول اليدوية (username + PAT)
 *   - بدلاً من ذلك: Device Flow كامل (نفس تجربة LoginActivity)
 *   - المستخدم يضغط "بدء الربط" → يُفتح WebView → يوافق
 *
 * v1.0 — ملف واحد شامل:
 *   - نموذج الحساب (Account)
 *   - التخزين (SharedPreferences + SecureStore)
 *   - التبديل بين الحسابات
 *   - Dialog التبديل
 *   - Dialog إدارة الحسابات
 */
object GitHubAccountManager {

    // ============================================================
    // ثوابت التخزين
    // ============================================================
    private const val PREFS_NAME = "github_accounts"
    private const val KEY_ACCOUNTS_JSON = "accounts_json"
    private const val KEY_ACTIVE_ID = "active_id"

    // ============================================================
    // نموذج البيانات
    // ============================================================
    class Account(
        val id: String,
        val username: String,
        val displayName: String,
        val addedAtMs: Long
    ) {

        val isValid: Boolean
            get() = id.isNotEmpty() && username.isNotEmpty()

        val safeDisplayName: String
            get() = displayName.ifEmpty { username }

        val initial: String
            get() = username.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        val color: Int
            get() {
                val palette = intArrayOf(
                    0xFF1F6FEB.toInt(), 0xFF2EA043.toInt(), 0xFFD29922.toInt(),
                    0xFFCF222E.toInt(), 0xFF8957E5.toInt(), 0xFFDB61A2.toInt(),
                    0xFF0EA5E9.toInt(), 0xFFF97316.toInt()
                )
                val hash = kotlin.math.abs(id.hashCode())
                return palette[hash % palette.size]
            }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Account) return false
            return id == other.id
        }

        override fun hashCode(): Int = id.hashCode()

        override fun toString(): String = "Account($username)"

        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("id", id)
                put("username", username)
                put("displayName", displayName)
                put("addedAtMs", addedAtMs)
            }
        }

        companion object {
            fun fromJson(json: JSONObject): Account? {
                return try {
                    val id = json.optString("id", "")
                    val username = json.optString("username", "")
                    if (id.isEmpty() || username.isEmpty()) return null

                    Account(
                        id = id,
                        username = username,
                        displayName = json.optString("displayName", ""),
                        addedAtMs = json.optLong("addedAtMs", 0L)
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    // ============================================================
    // SharedPreferences
    // ============================================================
    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ============================================================
    // قائمة الحسابات
    // ============================================================
    @JvmStatic
    fun listAccounts(context: Context?): List<Account> {
        if (context == null) return emptyList()

        return try {
            val raw = prefs(context).getString(KEY_ACCOUNTS_JSON, "") ?: ""
            if (raw.isBlank()) return emptyList()

            val array = JSONArray(raw)
            val result = mutableListOf<Account>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                Account.fromJson(obj)?.let { result.add(it) }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAccounts(context: Context, accounts: List<Account>) {
        try {
            val array = JSONArray()
            for (acc in accounts) {
                array.put(acc.toJson())
            }
            prefs(context).edit()
                .putString(KEY_ACCOUNTS_JSON, array.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // الحساب النشط
    // ============================================================
    @JvmStatic
    fun getActive(context: Context?): Account? {
        if (context == null) return null

        val activeId = prefs(context).getString(KEY_ACTIVE_ID, "") ?: ""
        if (activeId.isEmpty()) return null

        return listAccounts(context).firstOrNull { it.id == activeId }
    }

    @JvmStatic
    fun switchTo(context: Context?, accountId: String): Boolean {
        if (context == null || accountId.isEmpty()) return false

        val accounts = listAccounts(context)
        val target = accounts.firstOrNull { it.id == accountId } ?: return false

        val token = SecureStore(context).readGitHubToken(accountId)
        if (token.isEmpty()) return false

        prefs(context).edit().putString(KEY_ACTIVE_ID, target.id).apply()

        try {
            AuthManager.getInstance(context).saveGithubUsername(target.username)
        } catch (_: Exception) {
        }

        return true
    }

    // ============================================================
    // إضافة حساب
    // ============================================================
    @JvmStatic
    fun addAccount(
        context: Context?,
        username: String,
        token: String,
        displayName: String = ""
    ): Boolean {
        if (context == null) return false
        if (username.isBlank() || token.isBlank()) return false

        val cleanUsername = username.trim()
        val accountId = cleanUsername.lowercase(Locale.US)

        val tokenSaved = SecureStore(context).saveGitHubToken(accountId, token)
        if (!tokenSaved) return false

        val accounts = listAccounts(context).toMutableList()
        val existingIndex = accounts.indexOfFirst { it.id == accountId }

        val newAccount = Account(
            id = accountId,
            username = cleanUsername,
            displayName = displayName.ifBlank { cleanUsername },
            addedAtMs = System.currentTimeMillis()
        )

        if (existingIndex >= 0) {
            accounts[existingIndex] = newAccount
        } else {
            accounts.add(newAccount)
        }

        saveAccounts(context, accounts)

        val currentActive = getActive(context)
        if (currentActive == null) {
            prefs(context).edit().putString(KEY_ACTIVE_ID, accountId).apply()
        }

        try {
            AuthManager.getInstance(context).saveGithubUsername(cleanUsername)
        } catch (_: Exception) {
        }

        return true
    }

    // ============================================================
    // حذف حساب
    // ============================================================
    @JvmStatic
    fun removeAccount(context: Context?, accountId: String): Boolean {
        if (context == null || accountId.isEmpty()) return false

        val accounts = listAccounts(context).toMutableList()
        val removed = accounts.removeAll { it.id == accountId }
        if (!removed) return false

        try {
            SecureStore(context).clearGitHubToken(accountId)
        } catch (_: Exception) {
        }

        saveAccounts(context, accounts)

        val activeId = prefs(context).getString(KEY_ACTIVE_ID, "") ?: ""
        if (activeId == accountId) {
            val newActive = accounts.firstOrNull()
            prefs(context).edit()
                .putString(KEY_ACTIVE_ID, newActive?.id ?: "")
                .apply()

            try {
                val am = AuthManager.getInstance(context)
                if (newActive != null) {
                    am.saveGithubUsername(newActive.username)
                } else {
                    am.logout()
                }
            } catch (_: Exception) {
            }
        }

        return true
    }

    @JvmStatic
    fun clearAll(context: Context?) {
        if (context == null) return

        val accounts = listAccounts(context)
        for (acc in accounts) {
            try {
                SecureStore(context).clearGitHubToken(acc.id)
            } catch (_: Exception) {
            }
        }

        prefs(context).edit().clear().apply()
    }

    // ============================================================
    // قراءة التوكن
    // ============================================================
    @JvmStatic
    fun getActiveToken(context: Context?): String {
        if (context == null) return ""

        val active = getActive(context) ?: return ""
        return try {
            SecureStore(context).readGitHubToken(active.id)
        } catch (_: Exception) {
            ""
        }
    }

    // ============================================================
    // ربط المشروع بحساب
    // ============================================================
    @JvmStatic
    fun getProjectAccountId(context: Context?, projectKey: String): String {
        if (context == null) return ""

        val prefs = context.applicationContext
            .getSharedPreferences("flutter_ide_prefs", Context.MODE_PRIVATE)

        val saved = prefs.getString("account_$projectKey", "") ?: ""
        if (saved.isNotEmpty()) {
            val exists = listAccounts(context).any { it.id == saved }
            if (exists) return saved
        }

        return getActive(context)?.id ?: ""
    }

    @JvmStatic
    fun linkProjectToAccount(
        context: Context?,
        projectKey: String,
        accountId: String
    ) {
        if (context == null || projectKey.isEmpty() || accountId.isEmpty()) return

        context.applicationContext
            .getSharedPreferences("flutter_ide_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("account_$projectKey", accountId)
            .apply()
    }

    @JvmStatic
    fun getTokenForProject(context: Context?, projectKey: String): String {
        if (context == null) return ""

        val accountId = getProjectAccountId(context, projectKey)
        if (accountId.isEmpty()) return getActiveToken(context)

        return try {
            SecureStore(context).readGitHubToken(accountId)
        } catch (_: Exception) {
            ""
        }
    }

    @JvmStatic
    fun getAccountForProject(context: Context?, projectKey: String): Account? {
        if (context == null) return null
        val accountId = getProjectAccountId(context, projectKey)
        if (accountId.isEmpty()) return null
        return listAccounts(context).firstOrNull { it.id == accountId }
    }

    // ============================================================
    // Dialog التبديل
    // ============================================================
    @JvmStatic
    fun showSwitcher(
        activity: Activity?,
        onSwitched: (Account) -> Unit
    ) {
        if (activity == null || activity.isFinishing) return

        val accounts = listAccounts(activity)
        if (accounts.isEmpty()) {
            Toast.makeText(
                activity,
                "لا توجد حسابات محفوظة",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val active = getActive(activity)

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val subtitle = TextView(activity).apply {
            text = "اختر الحساب النشط:"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(13f)
            setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 12))
        }
        container.addView(subtitle)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("تبديل الحساب")
            .setView(container)
            .setNegativeButton("إلغاء", null)
            .create()

        for (account in accounts) {
            val isActive = (account.id == active?.id)
            val card = buildAccountCard(activity, account, isActive) {
                dialog.dismiss()
                if (isActive) {
                    Toast.makeText(
                        activity,
                        "هذا الحساب نشط بالفعل",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@buildAccountCard
                }

                val success = switchTo(activity, account.id)
                if (success) {
                    Toast.makeText(
                        activity,
                        "تم التبديل إلى ${account.username}",
                        Toast.LENGTH_SHORT
                    ).show()
                    onSwitched(account)
                } else {
                    Toast.makeText(
                        activity,
                        "تعذر التبديل — التوكن غير متوفر",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            container.addView(
                card,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(activity, 8)
                }
            )
        }

        val manageBtn = Button(activity).apply {
            text = "إدارة الحسابات"
            setTextColor(COLOR_TEXT_PRIMARY)
            isAllCaps = false
            setTextSize(13f)
            setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12))
            background = rounded(activity, COLOR_SURFACE_ALT, 12, COLOR_BORDER, 1)
            setOnClickListener {
                dialog.dismiss()
                showManager(activity) {
                    showSwitcher(activity, onSwitched)
                }
            }
        }
        container.addView(
            manageBtn,
            LinearLayout.LayoutParams(-1, dp(activity, 48)).apply {
                topMargin = dp(activity, 8)
            }
        )

        dialog.show()
        UiStyle.styleDialog(dialog)

        dialog.window?.let { window ->
            val width = (activity.resources.displayMetrics.widthPixels * 0.92).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    // ============================================================
    // Dialog الإدارة
    // ============================================================
    @JvmStatic
    fun showManager(
        activity: Activity?,
        onChanged: (() -> Unit)? = null
    ) {
        if (activity == null || activity.isFinishing) return

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("إدارة الحسابات")
            .setView(container)
            .setNegativeButton("إغلاق", null)
            .create()

        fun rebuildList() {
            container.removeAllViews()

            val accounts = listAccounts(activity)
            val active = getActive(activity)

            if (accounts.isEmpty()) {
                val empty = TextView(activity).apply {
                    text = "لا توجد حسابات محفوظة"
                    setTextColor(COLOR_TEXT_SECONDARY)
                    setTextSize(13f)
                    gravity = Gravity.CENTER
                    setPadding(
                        dp(activity, 16), dp(activity, 32),
                        dp(activity, 16), dp(activity, 32)
                    )
                }
                container.addView(empty)
            } else {
                for (account in accounts) {
                    val isActive = (account.id == active?.id)
                    val row = buildManageRow(activity, account, isActive,
                        onDelete = {
                            AlertDialog.Builder(activity)
                                .setTitle("حذف الحساب")
                                .setMessage(
                                    "سيتم حذف توكن ${account.username}.\n\n" +
                                    "المشاريع ستبقى لكنها لن تُبنى حتى تربط حساباً آخر."
                                )
                                .setPositiveButton("حذف") { _, _ ->
                                    val success = removeAccount(activity, account.id)
                                    if (success) {
                                        Toast.makeText(
                                            activity,
                                            "تم حذف ${account.username}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        rebuildList()
                                        onChanged?.invoke()
                                    }
                                }
                                .setNegativeButton("إلغاء", null)
                                .show()
                        },
                        onSwitch = {
                            val success = switchTo(activity, account.id)
                            if (success) {
                                Toast.makeText(
                                    activity,
                                    "تم التبديل إلى ${account.username}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                rebuildList()
                                onChanged?.invoke()
                            }
                        }
                    )
                    container.addView(
                        row,
                        LinearLayout.LayoutParams(-1, -2).apply {
                            bottomMargin = dp(activity, 8)
                        }
                    )
                }
            }

            val addBtn = Button(activity).apply {
                text = "+ إضافة حساب جديد"
                setTextColor(0xFFFFFFFF.toInt())
                isAllCaps = false
                setTextSize(13f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(
                    dp(activity, 12), dp(activity, 14),
                    dp(activity, 12), dp(activity, 14)
                )
                background = rounded(activity, COLOR_PRIMARY, 12, COLOR_PRIMARY_LIGHT, 1)
                setOnClickListener {
                    // ⭐ v2.0: Device Flow
                    showAddAccountDialog(activity) { added ->
                        if (added) {
                            rebuildList()
                            onChanged?.invoke()
                        }
                    }
                }
            }
            container.addView(
                addBtn,
                LinearLayout.LayoutParams(-1, dp(activity, 50)).apply {
                    topMargin = dp(activity, 8)
                }
            )
        }

        rebuildList()
        dialog.show()
        UiStyle.styleDialog(dialog)

        dialog.window?.let { window ->
            val width = (activity.resources.displayMetrics.widthPixels * 0.92).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    // ============================================================
    // ⭐ v2.0: Dialog إضافة حساب عبر Device Flow
    // ============================================================
    /**
     * يعرض Dialog لإضافة حساب جديد عبر Device Flow.
     * نفس تجربة LoginActivity — بدون PAT يدوي.
     */
    @JvmStatic
    fun showAddAccountDialog(
        activity: Activity?,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        if (activity == null || activity.isFinishing) return

        // ============================================================
        // حالة الحوار (لتحديث الواجهة)
        // ============================================================
        val statusView = TextView(activity).apply {
            text = "جاهز للبدء"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(12f)
            setPadding(0, dp(activity, 8), 0, 0)
            gravity = Gravity.CENTER
        }

        val codeView = TextView(activity).apply {
            text = ""
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(24f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            setPadding(
                dp(activity, 12), dp(activity, 16),
                dp(activity, 12), dp(activity, 16)
            )
            background = rounded(activity, COLOR_SURFACE_ALT, 16, COLOR_BORDER, 1)
            visibility = View.GONE
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(activity, 16), dp(activity, 12),
                dp(activity, 16), dp(activity, 12)
            )
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val description = TextView(activity).apply {
            text = "سيتم فتح صفحة GitHub تلقائياً.\n" +
                    "اضغط Continue ثم Authorize لإضافة الحساب."
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(12f)
            setLineSpacing(0f, 1.3f)
            setPadding(0, 0, 0, dp(activity, 12))
        }
        container.addView(description)
        container.addView(codeView)
        container.addView(statusView)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("إضافة حساب GitHub")
            .setView(container)
            .setPositiveButton("بدء الربط", null)
            .setNegativeButton("إلغاء", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                btn.isEnabled = false
                btn.text = "جارٍ الطلب..."

                statusView.text = "جارٍ طلب رمز من GitHub..."
                statusView.setTextColor(COLOR_PRIMARY_LIGHT)

                // ============================================================
                // 1) اطلب Device Code
                // ============================================================
                GitHubApi.requestDeviceCode(object : GitHubApi.Callback<GitHubApi.DeviceCode> {
                    override fun onSuccess(device: GitHubApi.DeviceCode) {
                        activity.runOnUiThread {
                            if (activity.isFinishing) return@runOnUiThread

                            codeView.text = device.userCode
                            codeView.visibility = View.VISIBLE

                            statusView.text =
                                "تم نسخ الرمز.\n" +
                                "سيتم فتح GitHub الآن — اضغط Continue ثم Authorize."
                            statusView.setTextColor(COLOR_PRIMARY_LIGHT)

                            btn.text = "في انتظار الموافقة..."
                        }

                        // انسخ الرمز للحافظة
                        try {
                            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager
                            cm.setPrimaryClip(
                                ClipData.newPlainText(
                                    "GitHub code",
                                    device.userCode
                                )
                            )
                        } catch (_: Exception) {
                        }

                        // افتح صفحة التحقق
                        try {
                            val intent = Intent(
                                activity,
                                WebViewAuthActivity::class.java
                            )
                            intent.putExtra(
                                WebViewAuthActivity.EXTRA_URL,
                                device.verificationUri.ifBlank {
                                    GitHubConfig.VERIFICATION_URL
                                }
                            )
                            intent.putExtra(
                                WebViewAuthActivity.EXTRA_USER_CODE,
                                device.userCode
                            )
                            activity.startActivity(intent)
                        } catch (_: Exception) {
                        }

                        // ابدأ polling
                        GitHubApi.pollAccessToken(
                            device,
                            object : GitHubApi.Callback<String> {
                                override fun onSuccess(token: String) {
                                    GitHubApi.getUser(token, object : GitHubApi.Callback<String> {
                                        override fun onSuccess(username: String) {
                                            val added = addAccount(
                                                activity,
                                                username,
                                                token,
                                                username
                                            )

                                            activity.runOnUiThread {
                                                if (activity.isFinishing) return@runOnUiThread

                                                if (added) {
                                                    Toast.makeText(
                                                        activity,
                                                        "تم إضافة الحساب $username بنجاح",
                                                        Toast.LENGTH_LONG
                                                    ).show()

                                                    dialog.dismiss()
                                                    onResult?.invoke(true)
                                                } else {
                                                    statusView.text = "تعذر حفظ الحساب"
                                                    statusView.setTextColor(COLOR_ERROR)
                                                    btn.isEnabled = true
                                                    btn.text = "حاول مرة أخرى"
                                                }
                                            }
                                        }

                                        override fun onError(message: String) {
                                            activity.runOnUiThread {
                                                statusView.text = message
                                                statusView.setTextColor(COLOR_ERROR)
                                                btn.isEnabled = true
                                                btn.text = "حاول مرة أخرى"
                                            }
                                        }
                                    })
                                }

                                override fun onError(message: String) {
                                    activity.runOnUiThread {
                                        if (activity.isFinishing) return@runOnUiThread
                                        statusView.text = message
                                        statusView.setTextColor(COLOR_ERROR)
                                        btn.isEnabled = true
                                        btn.text = "حاول مرة أخرى"
                                    }
                                }
                            }
                        )
                    }

                    override fun onError(message: String) {
                        activity.runOnUiThread {
                            if (activity.isFinishing) return@runOnUiThread
                            statusView.text = message
                            statusView.setTextColor(COLOR_ERROR)
                            btn.isEnabled = true
                            btn.text = "حاول مرة أخرى"
                        }
                    }
                })
            }
        }

        dialog.show()
        UiStyle.styleDialog(dialog)

        dialog.window?.let { window ->
            val width = (activity.resources.displayMetrics.widthPixels * 0.92).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    // ============================================================
    // بطاقة حساب (للتبديل)
    // ============================================================
    private fun buildAccountCard(
        activity: Activity,
        account: Account,
        isActive: Boolean,
        onClick: () -> Unit
    ): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(activity, 14), dp(activity, 14),
                dp(activity, 14), dp(activity, 14)
            )
            background = rounded(
                activity,
                if (isActive) 0xFF1F2D3F.toInt() else COLOR_SURFACE,
                14,
                if (isActive) COLOR_PRIMARY_LIGHT else COLOR_BORDER,
                if (isActive) 2 else 1
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val iconView = TextView(activity).apply {
            text = account.initial
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(account.color)
            }
        }
        card.addView(iconView, LinearLayout.LayoutParams(
            dp(activity, 44), dp(activity, 44)
        ))

        val textCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        card.addView(
            textCol,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(activity, 12)
            }
        )

        val nameView = TextView(activity).apply {
            text = account.safeDisplayName
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(15f)
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textCol.addView(nameView)

        val usernameView = TextView(activity).apply {
            text = "@${account.username}"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(11f)
            isSingleLine = true
        }
        textCol.addView(usernameView)

        if (isActive) {
            val badge = TextView(activity).apply {
                text = "نشط"
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(10f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(
                    dp(activity, 8), dp(activity, 3),
                    dp(activity, 8), dp(activity, 3)
                )
                background = rounded(activity, COLOR_PRIMARY, 8, COLOR_PRIMARY_LIGHT, 0)
            }
            card.addView(badge)
        }

        return card
    }

    // ============================================================
    // صف إدارة
    // ============================================================
    private fun buildManageRow(
        activity: Activity,
        account: Account,
        isActive: Boolean,
        onDelete: () -> Unit,
        onSwitch: () -> Unit
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(activity, 12), dp(activity, 12),
                dp(activity, 12), dp(activity, 12)
            )
            background = rounded(
                activity,
                if (isActive) 0xFF1F2D3F.toInt() else COLOR_SURFACE,
                14,
                if (isActive) COLOR_PRIMARY_LIGHT else COLOR_BORDER,
                if (isActive) 2 else 1
            )
        }

        val iconView = TextView(activity).apply {
            text = account.initial
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(16f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(account.color)
            }
        }
        row.addView(iconView, LinearLayout.LayoutParams(
            dp(activity, 40), dp(activity, 40)
        ))

        val textCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        row.addView(
            textCol,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(activity, 12)
            }
        )

        textCol.addView(TextView(activity).apply {
            text = account.safeDisplayName
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(14f)
            typeface = Typeface.DEFAULT_BOLD
        })
        textCol.addView(TextView(activity).apply {
            text = if (isActive) "@${account.username} • نشط" else "@${account.username}"
            setTextColor(if (isActive) COLOR_PRIMARY_LIGHT else COLOR_TEXT_SECONDARY)
            setTextSize(11f)
        })

        if (!isActive) {
            val switchBtn = Button(activity).apply {
                text = "تبديل"
                setTextColor(COLOR_TEXT_PRIMARY)
                isAllCaps = false
                setTextSize(11f)
                setPadding(
                    dp(activity, 10), 0,
                    dp(activity, 10), 0
                )
                minimumWidth = 0
                background = rounded(activity, COLOR_SURFACE_ALT, 8, COLOR_BORDER, 1)
                setOnClickListener { onSwitch() }
            }
            row.addView(
                switchBtn,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(activity, 34)
                )
            )
        }

        val deleteBtn = Button(activity).apply {
            text = "حذف"
            setTextColor(COLOR_ERROR)
            isAllCaps = false
            setTextSize(11f)
            setPadding(
                dp(activity, 10), 0,
                dp(activity, 10), 0
            )
            minimumWidth = 0
            background = rounded(activity, COLOR_SURFACE_ALT, 8, COLOR_BORDER, 1)
            setOnClickListener { onDelete() }
        }
        row.addView(
            deleteBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(activity, 34)
            ).apply {
                marginStart = dp(activity, 6)
            }
        )

        return row
    }

    // ============================================================
    // أدوات
    // ============================================================
    private const val COLOR_BACKGROUND = 0xFF0D1117.toInt()
    private const val COLOR_SURFACE = 0xFF161B22.toInt()
    private const val COLOR_SURFACE_ALT = 0xFF21262D.toInt()
    private const val COLOR_PRIMARY = 0xFF1F6FEB.toInt()
    private const val COLOR_PRIMARY_LIGHT = 0xFF58A6FF.toInt()
    private const val COLOR_TEXT_PRIMARY = 0xFFE6EDF3.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFF8B949E.toInt()
    private const val COLOR_BORDER = 0xFF30363D.toInt()
    private const val COLOR_ERROR = 0xFFCF222E.toInt()

    private fun dp(activity: Activity, value: Int): Int {
        return Math.round(value * activity.resources.displayMetrics.density)
    }

    private fun rounded(
        activity: Activity,
        color: Int,
        radiusDp: Int,
        strokeColor: Int,
        strokeDp: Int
    ): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = dp(activity, radiusDp).toFloat()
        if (strokeDp > 0) {
            drawable.setStroke(dp(activity, strokeDp), strokeColor)
        }
        return drawable
    }
}