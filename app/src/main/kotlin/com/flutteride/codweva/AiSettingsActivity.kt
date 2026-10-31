package com.flutteride.codweva

import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * شاشة إعدادات الذكاء الاصطناعي.
 *
 * v2.0 — إصلاح Edge-to-Edge على Android 15/16:
 *   - WindowCompat.setDecorFitsSystemWindows(window, false)
 *   - layoutInDisplayCutoutMode = SHORT_EDGES (دعم النوتش)
 *   - تطبيق WindowInsets على الـ root ScrollView
 *   - دعم displayCutout في الحسابات
 *   - أيقونات شريط الحالة بيضاء على الخلفية الداكنة
 *
 * مبسّط:
 *   - 4 مزودين: OpenAI / Gemini / OpenRouter / مخصص
 *   - المخصص: 3 حقول فقط (Base URL + API Key + Model)
 *   - بدون Presets / Format / Tools / تصدير / استيراد
 */
class AiSettingsActivity : AppCompatActivity() {

    // ============================================================
    // الألوان
    // ============================================================
    private val colorBg = 0xFF0D1117.toInt()
    private val colorSurface = 0xFF161B22.toInt()
    private val colorSurfaceAlt = 0xFF21262D.toInt()
    private val colorPrimary = 0xFF1F6FEB.toInt()
    private val colorPrimaryLight = 0xFF58A6FF.toInt()
    private val colorText = 0xFFE6EDF3.toInt()
    private val colorMuted = 0xFF8B949E.toInt()
    private val colorBorder = 0xFF30363D.toInt()
    private val colorSuccess = 0xFF2EA043.toInt()
    private val colorError = 0xFFCF222E.toInt()

    // ============================================================
    // الحقول
    // ============================================================
    private lateinit var rootScrollView: ScrollView
    private lateinit var rootLayout: LinearLayout

    private lateinit var providerSpinner: Spinner
    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var baseUrlInput: EditText
    private lateinit var baseUrlLabel: TextView
    private lateinit var baseUrlHint: TextView
    private lateinit var status: TextView

    private lateinit var keyStore: AiKeyStore
    private lateinit var settings: AiSettings
    private lateinit var customStore: CustomProviderStore

    private val providerIds = listOf(
        AiSettings.OPENAI,
        AiSettings.GEMINI,
        AiSettings.OPENROUTER,
        AiSettings.CUSTOM
    )

    private val providerLabels = arrayOf(
        "OpenAI",
        "Gemini",
        "OpenRouter",
        "مخصص"
    )

    private fun dp(value: Int): Int {
        return Math.round(value * resources.displayMetrics.density)
    }

    // ============================================================
    // onCreate
    // ============================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ============================================================
        // ⭐ v2.0: إعداد النافذة لـ Edge-to-Edge (قبل أي شيء)
        // ============================================================
        setupWindowEdgeToEdge()

        keyStore = AiKeyStore(this)
        settings = AiSettings(this)
        customStore = CustomProviderStore(this)

        title = "إعدادات الذكاء الاصطناعي"
        buildLayout()

        // اختيار المزود الافتراضي
        val initialProvider = settings.defaultProvider
        val index = providerIds.indexOf(initialProvider).coerceAtLeast(0)
        providerSpinner.setSelection(index)
        onProviderChanged(providerIds[index])
    }

    /**
     * ⭐ v2.0 — إعداد النافذة لـ Edge-to-Edge بشكل كامل.
     */
    private fun setupWindowEdgeToEdge() {
        // 1) Edge-to-Edge إجباري
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 2) دعم Display Cutout (النوتش) — من API 28
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // 3) ألوان شريط الحالة والتنقل
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = colorBg
            window.navigationBarColor = colorBg
        }

        // 4) أيقونات شريط الحالة بيضاء
        try {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // البناء
    // ============================================================
    private fun buildLayout() {
        rootScrollView = ScrollView(this).apply {
            setBackgroundColor(colorBg)
        }

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
            setBackgroundColor(colorBg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        // الترويسة
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = UiStyle.rounded(
                this@AiSettingsActivity, colorSurface, 18, colorBorder, 1
            )
        }

        header.addView(
            UiStyle.title(this, "إعدادات الذكاء الاصطناعي", 22f),
            LinearLayout.LayoutParams(-1, -2)
        )

        header.addView(
            label(
                "أدخل مفتاحك الشخصي. لا يُرسل إلى خادم البناء ولا يظهر في السجلات.",
                13f, colorText
            ).apply { setPadding(0, dp(8), 0, 0) },
            LinearLayout.LayoutParams(-1, -2)
        )

        rootLayout.addView(header, params(-1, -2, 0f, 0, 0, 20))

        // ============================================================
        // المزود
        // ============================================================
        rootLayout.addView(label("المزود", 13f, colorText))

        providerSpinner = Spinner(this).apply {
            adapter = UiStyle.spinnerAdapter(
                this@AiSettingsActivity,
                providerLabels
            )
        }
        UiStyle.styleSpinner(providerSpinner)
        rootLayout.addView(providerSpinner, params(-1, -2, 0f, 0, 0, 14))

        // ============================================================
        // Base URL (يظهر للمخصص فقط)
        // ============================================================
        baseUrlLabel = label("عنوان الخادم (Base URL)", 13f, colorText).apply {
            visibility = View.GONE
        }
        rootLayout.addView(baseUrlLabel)

        baseUrlInput = UiStyle.field(
            this, "https://api.groq.com/openai/v1"
        ).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
            visibility = View.GONE
        }
        rootLayout.addView(baseUrlInput, params(-1, -2, 0f, 0, 0, 4))

        baseUrlHint = label(
            "ابحث في موقع المزود عن \"Base URL\" أو \"API Endpoint\"",
            10f, colorMuted
        ).apply {
            setPadding(dp(4), 0, dp(4), dp(10))
            visibility = View.GONE
        }
        rootLayout.addView(baseUrlHint)

        // ============================================================
        // مفتاح API
        // ============================================================
        rootLayout.addView(label("مفتاح API", 13f, colorText))

        keyInput = UiStyle.field(this, "أدخل المفتاح هنا").apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        rootLayout.addView(keyInput, params(-1, -2, 0f, 0, 0, 14))

        // ============================================================
        // النموذج
        // ============================================================
        rootLayout.addView(label("اسم النموذج", 13f, colorText))

        modelInput = UiStyle.field(
            this, "مثال: gpt-4o-mini"
        )
        rootLayout.addView(modelInput, params(-1, -2, 0f, 0, 0, 16))

        // ============================================================
        // الأزرار
        // ============================================================
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val save = button("حفظ", colorPrimary)
        val test = button("اختبار الاتصال", colorSurface)

        buttons.addView(save, params(0, -2, 1f, 0, 0, 0))
        buttons.addView(test, params(0, -2, 1f, 8, 0, 0))

        rootLayout.addView(buttons, params(-1, -2, 0f, 0, 0, 16))

        // الحالة
        status = label("", 13f, colorText).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = UiStyle.rounded(
                this@AiSettingsActivity, colorSurface, 12, colorBorder, 1
            )
        }
        rootLayout.addView(status, params(-1, -2, 0f, 0, 0, 16))

        // حذف المفتاح
        val clear = button("حذف مفتاح المزود الحالي", colorSurface)
        rootLayout.addView(clear, params(-1, -2, 0f, 0, 0, 12))
        clear.setOnClickListener { clearCurrentKey() }

        // ============================================================
        // المستمعون
        // ============================================================
        providerSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    onProviderChanged(providerIds[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        save.setOnClickListener { saveCurrent() }
        test.setOnClickListener { testCurrent() }

        rootScrollView.addView(rootLayout)
        setContentView(rootScrollView)

        // ============================================================
        // ⭐ v2.0: تطبيق الـ insets على الـ root ScrollView
        // ============================================================
        applyEdgeToEdgeInsets()
    }

    /**
     * ⭐ v2.0 — تطبيق WindowInsets على الـ root ScrollView.
     */
    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootScrollView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // ⭐ دعم النوتش
            val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            } else {
                androidx.core.graphics.Insets.NONE
            }

            val left = maxOf(systemBars.left, cutout.left)
            val top = maxOf(systemBars.top, cutout.top)
            val right = maxOf(systemBars.right, cutout.right)
            val bottom = maxOf(systemBars.bottom, cutout.bottom)

            view.setPadding(left, top, right, bottom)
            insets
        }

        ViewCompat.requestApplyInsets(rootScrollView)
    }

    // ============================================================
    // تغيير المزود
    // ============================================================
    private fun onProviderChanged(providerId: String) {
        if (providerId == AiSettings.CUSTOM) {
            // إظهار حقل Base URL
            baseUrlLabel.visibility = View.VISIBLE
            baseUrlInput.visibility = View.VISIBLE
            baseUrlHint.visibility = View.VISIBLE

            loadCustomConfig()
        } else {
            // إخفاء حقل Base URL
            baseUrlLabel.visibility = View.GONE
            baseUrlInput.visibility = View.GONE
            baseUrlHint.visibility = View.GONE

            loadProvider(providerId)
        }
    }

    // ============================================================
    // تحميل الإعدادات
    // ============================================================
    private fun loadProvider(providerId: String) {
        val key = keyStore.get(providerId)
        keyInput.setText(key ?: "")
        modelInput.setText(settings.getModel(providerId))
        status.text = "جاهز: ${displayName(providerId)}"
        status.setTextColor(colorMuted)
    }

    private fun loadCustomConfig() {
        val config = customStore.getConfig()

        if (config == null) {
            baseUrlInput.setText(CustomProviderStore.DEFAULT_BASE_URL)
            keyInput.setText("")
            modelInput.setText("")
            status.text = "أدخل Base URL + المفتاح + النموذج"
            status.setTextColor(colorMuted)
            return
        }

        baseUrlInput.setText(config.baseUrl)
        keyInput.setText(customStore.getApiKey() ?: "")
        modelInput.setText(config.model)

        status.text = "جاهز: مخصص"
        status.setTextColor(colorMuted)
    }

    // ============================================================
    // الحفظ
    // ============================================================
    private fun saveCurrent() {
        val id = currentProvider()

        if (id == AiSettings.CUSTOM) {
            saveCustomConfig()
            return
        }

        // المزودون المدمجون
        if (!keyStore.isAvailable) {
            status.text = "تعذر تهيئة التخزين المشفر"
            status.setTextColor(colorError)
            return
        }

        val key = keyInput.text.toString().trim()
        val model = modelInput.text.toString().trim()

        if (key.isEmpty() || model.isEmpty()) {
            status.text = "أدخل المفتاح واسم النموذج"
            status.setTextColor(colorError)
            return
        }

        keyStore.save(id, key)
        settings.setModel(id, model)
        settings.setDefaultProvider(id)

        status.text = "تم حفظ إعدادات ${displayName(id)} بأمان"
        status.setTextColor(colorSuccess)
    }

    private fun saveCustomConfig() {
        if (!keyStore.isAvailable) {
            status.text = "تعذر تهيئة التخزين المشفر"
            status.setTextColor(colorError)
            return
        }

        val baseUrl = baseUrlInput.text.toString().trim()
        val key = keyInput.text.toString().trim()
        val model = modelInput.text.toString().trim()

        // التحقق
        if (baseUrl.isEmpty() || !baseUrl.startsWith("http")) {
            status.text = "أدخل Base URL صالح (يبدأ بـ http)"
            status.setTextColor(colorError)
            return
        }
        if (key.isEmpty()) {
            status.text = "أدخل مفتاح API"
            status.setTextColor(colorError)
            return
        }
        if (model.isEmpty()) {
            status.text = "أدخل اسم النموذج"
            status.setTextColor(colorError)
            return
        }

        val config = CustomProviderConfig(
            baseUrl = baseUrl,
            model = model,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens
        )

        val ok = customStore.save(config, key)

        if (ok) {
            settings.setDefaultProvider(AiSettings.CUSTOM)
            settings.setModel(AiSettings.CUSTOM, model)

            status.text = "تم حفظ إعدادات المزود المخصص"
            status.setTextColor(colorSuccess)

            Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show()
        } else {
            status.text = "فشل الحفظ"
            status.setTextColor(colorError)
        }
    }

    // ============================================================
    // الاختبار
    // ============================================================
    private fun testCurrent() {
        val id = currentProvider()

        if (id == AiSettings.CUSTOM) {
            testCustomConfig()
            return
        }

        saveCurrent()

        val key = keyStore.get(id)
        val model = modelInput.text.toString().trim()

        if (key.isNullOrEmpty() || model.isEmpty()) return

        status.text = "جارٍ اختبار الاتصال..."
        status.setTextColor(colorMuted)

        Thread {
            try {
                val provider = AiProviderFactory.create(this, id)
                val messages = listOf(AiMessage.user("أجب بكلمة واحدة فقط: متصل"))
                val response = provider.send(messages, model, 0.0, 32)

                runOnUiThread {
                    status.text = "✓ نجح الاتصال: ${response.safeText.trim()}"
                    status.setTextColor(colorSuccess)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "✗ فشل:\n\n${safeError(e)}"
                    status.setTextColor(colorError)
                }
            }
        }.start()
    }

    private fun testCustomConfig() {
        val baseUrl = baseUrlInput.text.toString().trim()
        val key = keyInput.text.toString().trim()
        val model = modelInput.text.toString().trim()

        if (baseUrl.isEmpty() || key.isEmpty() || model.isEmpty()) {
            status.text = "أكمل الحقول أولاً"
            status.setTextColor(colorError)
            return
        }

        // احفظ أولاً
        saveCustomConfig()

        status.text = "جارٍ اختبار الاتصال..."
        status.setTextColor(colorMuted)

        val config = CustomProviderConfig(
            baseUrl = baseUrl,
            model = model,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens
        )

        Thread {
            try {
                val provider = CustomProvider(config, key)
                val messages = listOf(
                    AiMessage.user("أجب بكلمة واحدة فقط: متصل")
                )
                val response = provider.send(messages, model, 0.0, 32)

                runOnUiThread {
                    status.text = "✓ نجح الاتصال: ${response.safeText.trim()}"
                    status.setTextColor(colorSuccess)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "✗ فشل:\n\n${safeError(e)}"
                    status.setTextColor(colorError)
                }
            }
        }.start()
    }

    // ============================================================
    // حذف المفتاح
    // ============================================================
    private fun clearCurrentKey() {
        val id = currentProvider()

        if (id == AiSettings.CUSTOM) {
            customStore.clear()
            baseUrlInput.setText(CustomProviderStore.DEFAULT_BASE_URL)
            keyInput.setText("")
            modelInput.setText("")
            status.text = "تم حذف إعدادات المخصص"
            status.setTextColor(colorMuted)
            return
        }

        keyStore.clear(id)
        keyInput.setText("")
        status.text = "تم حذف مفتاح ${displayName(id)}"
        status.setTextColor(colorMuted)
    }

    // ============================================================
    // أدوات
    // ============================================================
    private fun currentProvider(): String {
        val position = providerSpinner.selectedItemPosition.coerceIn(
            0, providerIds.size - 1
        )
        return providerIds[position]
    }

    private fun displayName(id: String): String {
        return when (id) {
            AiSettings.GEMINI -> "Gemini"
            AiSettings.OPENROUTER -> "OpenRouter"
            AiSettings.CUSTOM -> "مخصص"
            else -> "OpenAI"
        }
    }

    private fun safeError(e: Exception): String = AiErrorFormatter.format(e)

    private fun label(text: String, size: Float, color: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextSize(size)
            setTextColor(color)
        }
    }

    private fun button(text: String, color: Int): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(colorText)
            isAllCaps = false
            setTextSize(14f)
            minHeight = dp(46)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = UiStyle.rounded(
                this@AiSettingsActivity, color, 12,
                if (color == colorPrimary) colorPrimaryLight else colorBorder, 1
            )
        }
    }

    private fun params(
        w: Int, h: Int, weight: Float,
        left: Int, top: Int, bottom: Int
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(w, h, weight).apply {
            setMargins(dp(left), dp(top), dp(left), dp(bottom))
        }
    }
}