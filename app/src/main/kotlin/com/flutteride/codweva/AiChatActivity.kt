package com.flutteride.codweva

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

/**
 * شاشة محادثة AI محفوظة لكل مشروع، مع وضعين: مساعد ذكي / وكيل كامل.
 *
 * v2.2 — إصلاح Edge-to-Edge على Android 15/16:
 *   - WindowCompat.setDecorFitsSystemWindows(window, false)
 *   - layoutInDisplayCutoutMode = SHORT_EDGES (دعم النوتش)
 *   - تطبيق WindowInsets على الـ root layout
 *   - دعم displayCutout في الحسابات
 *   - دعم IME (لوحة المفاتيح) لضمان ظهور حقل الإدخال فوقها
 *
 * v2.1 — دعم المزود المخصص:
 *   - استخدام AiProviderFactory.isReady() للتحقق
 *   - استخدام AiProviderFactory.getReadinessMessage() لرسائل الخطأ
 *   - استخدام AiSettings.displayNameFor() للاسم المعروض
 *   - استخدام CustomProviderStore للاسم المخصص
 */
class AiChatActivity : AppCompatActivity() {

    // ============================================================
    // الثوابت
    // ============================================================
    private val BG = 0xFF0D1117.toInt()
    private val SURFACE = 0xFF161B22.toInt()
    private val SURFACE_ALT = 0xFF21262D.toInt()
    private val PRIMARY = 0xFF1F6FEB.toInt()
    private val TEXT = 0xFFE6EDF3.toInt()
    private val MUTED = 0xFF8B949E.toInt()
    private val USER_BUBBLE = 0xFF123E73.toInt()
    private val AI_BUBBLE = 0xFF161B22.toInt()
    private val BORDER = 0xFF30363D.toInt()
    private val STEP_BG = 0xFF1A1F27.toInt()

    private val MAX_AGENTIC_ROUNDS = 15

    // ============================================================
    // الحقول
    // ============================================================
    private lateinit var rootLayout: LinearLayout
    private lateinit var messageList: LinearLayout
    private lateinit var messageScroll: ScrollView
    private lateinit var input: EditText
    private lateinit var contextView: TextView
    private lateinit var progressView: TextView
    private lateinit var sendButton: Button
    private lateinit var undoButton: Button
    private lateinit var btnModeSmart: Button
    private lateinit var btnModeAgentic: Button

    private var isSending = false
    @Volatile private var cancelRequested = false
    @Volatile private var requestThread: Thread? = null

    @Volatile private var isDestroyedFlag = false

    private var lastAppliedChange: List<AgentChangeStore.PreviousFile>? = null

    private lateinit var settings: AiSettings
    private lateinit var keyStore: AiKeyStore
    private lateinit var modeStore: AiModeStore
    private lateinit var conversationStore: AiConversationStore
    private lateinit var customStore: CustomProviderStore

    private var projectDir: File? = null
    private var currentFile: String? = null
    private var contextText: String = ""
    private val conversation = mutableListOf<AiMessage>()

    private fun dp(value: Int): Int {
        return Math.round(value * resources.displayMetrics.density)
    }

    // ============================================================
    // onCreate
    // ============================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ============================================================
        // ⭐ v2.2: إعداد النافذة لـ Edge-to-Edge (قبل أي شيء)
        // ============================================================
        setupWindowEdgeToEdge()

        settings = AiSettings(this)
        keyStore = AiKeyStore(this)
        modeStore = AiModeStore(this)
        conversationStore = AiConversationStore(this)
        customStore = CustomProviderStore(this)

        val projectPath = intent.getStringExtra(EXTRA_PROJECT_DIR)
        currentFile = intent.getStringExtra(EXTRA_CURRENT_FILE)
        if (!projectPath.isNullOrEmpty()) projectDir = File(projectPath)

        title = "مساعد AI"

        projectDir?.let {
            conversation.addAll(conversationStore.load(it.absolutePath))
        }

        buildLayout()

        if (conversation.isEmpty()) {
            val welcome = "مرحبًا. أنا مساعدك داخل KlencodIDE. أرى مشروعك كاملاً، " +
                    "ويمكنني قراءة أي ملف واقتراح التعديلات. لن أكتب أو أحذف أي شيء قبل موافقتك."
            conversation.add(AiMessage.assistant(welcome))
            conversationStore.save(projectPath(), conversation)
        }

        renderConversation()
        updateContextLine()
        refreshModeButtons()
    }

    /**
     * ⭐ v2.2 — إعداد النافذة لـ Edge-to-Edge بشكل كامل.
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
            window.statusBarColor = BG
            window.navigationBarColor = BG
        }

        // 4) أيقونات شريط الحالة بيضاء
        try {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        } catch (_: Exception) {
        }

        // 5) دعم لوحة المفاتيح (IME) — adjustResize
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onResume() {
        super.onResume()
        updateContextLine()
    }

    override fun onPause() {
        super.onPause()
        persistConversation()
    }

    override fun onDestroy() {
        isDestroyedFlag = true
        cancelRequested = true
        requestThread?.interrupt()
        requestThread = null

        try {
            messageList.removeAllViews()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    // ============================================================
    // البناء
    // ============================================================
    private fun buildLayout() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        // الترويسة
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(SURFACE)
            layoutParams = LinearLayout.LayoutParams(-1, dp(56))
        }

        val menuButton = Button(this).apply {
            text = "≡"
            setTextColor(TEXT)
            setTextSize(20f)
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = UiStyle.rounded(this@AiChatActivity, 0x00000000, 10, 0x00000000, 0)
            setOnClickListener { showMenuDialog() }
        }
        header.addView(menuButton, LinearLayout.LayoutParams(dp(44), dp(44)))

        val titleView = TextView(this).apply {
            text = "مساعد AI"
            setTextColor(TEXT)
            setTextSize(17f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, 0, 0)
        }
        header.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))

        val settingsButton = Button(this).apply {
            text = "إعدادات"
            setTextColor(TEXT)
            setTextSize(12f)
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setPadding(dp(12), 0, dp(12), 0)
            background = UiStyle.rounded(this@AiChatActivity, SURFACE_ALT, 10, BORDER, 1)
            setOnClickListener {
                startActivity(Intent(this@AiChatActivity, AiSettingsActivity::class.java))
            }
        }
        header.addView(settingsButton, LinearLayout.LayoutParams(-2, dp(38)))

        rootLayout.addView(header)

        // شريط اختيار الوضع
        val modeBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(SURFACE)
            gravity = Gravity.CENTER_VERTICAL
        }

        val modeLabel = TextView(this).apply {
            text = "الوضع:"
            setTextColor(MUTED)
            setTextSize(11f)
            setPadding(0, 0, dp(8), 0)
        }
        modeBar.addView(modeLabel)

        btnModeSmart = Button(this).apply {
            text = "مساعد ذكي"
            setTextSize(11f)
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { selectMode(AiMode.SMART) }
        }
        modeBar.addView(btnModeSmart, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
            marginEnd = dp(4)
        })

        btnModeAgentic = Button(this).apply {
            text = "وكيل كامل"
            setTextSize(11f)
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { selectMode(AiMode.AGENTIC) }
        }
        modeBar.addView(btnModeAgentic, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
            marginStart = dp(4)
        })

        rootLayout.addView(modeBar, LinearLayout.LayoutParams(-1, -2))

        // سطر السياق
        contextView = TextView(this).apply {
            text = ""
            setTextColor(MUTED)
            setTextSize(11f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setPadding(dp(14), dp(4), dp(14), dp(4))
            setBackgroundColor(BG)
        }
        rootLayout.addView(contextView, LinearLayout.LayoutParams(-1, -2))

        // التحذير
        val warningRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setBackgroundColor(BG)
        }

        val warningIcon = TextView(this).apply {
            text = "!"
            setTextColor(0xFFD29922.toInt())
            setTextSize(11f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF2A2110.toInt())
                setStroke(dp(1), 0xFF6B5318.toInt())
            }
        }
        warningRow.addView(warningIcon, LinearLayout.LayoutParams(dp(18), dp(18)))

        val warningText = TextView(this).apply {
            text = "راجع التعديلات قبل تطبيقها — قد يخطئ AI."
            setTextColor(MUTED)
            setTextSize(11f)
            setPadding(dp(8), 0, 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        warningRow.addView(warningText, LinearLayout.LayoutParams(0, -2, 1f))

        rootLayout.addView(warningRow, LinearLayout.LayoutParams(-1, -2))

        // شريط الحالة
        progressView = TextView(this).apply {
            text = ""
            setTextColor(PRIMARY)
            setTextSize(11f)
            visibility = View.GONE
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }
        rootLayout.addView(progressView, LinearLayout.LayoutParams(-1, -2))

        // قائمة الرسائل
        messageScroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BG)
        }

        messageList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(14))
        }

        messageScroll.addView(messageList, FrameLayout.LayoutParams(-1, -2))

        rootLayout.addView(messageScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // زر التراجع
        undoButton = Button(this).apply {
            text = "تراجع عن آخر تعديل"
            setTextColor(TEXT)
            setTextSize(12f)
            isAllCaps = false
            minimumHeight = 0
            background = UiStyle.rounded(this@AiChatActivity, SURFACE_ALT, 10, BORDER, 1)
            visibility = View.GONE
            setOnClickListener { undoLastChange() }
        }
        rootLayout.addView(undoButton, LinearLayout.LayoutParams(-1, dp(38)).apply {
            setMargins(dp(14), 0, dp(14), dp(6))
        })

        // صف الإدخال
        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(8))
            setBackgroundColor(SURFACE)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val attachButton = Button(this).apply {
            text = "إرفاق"
            setTextColor(TEXT)
            setTextSize(11f)
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            background = UiStyle.rounded(this@AiChatActivity, SURFACE_ALT, 10, BORDER, 1)
            setOnClickListener { showFilePicker() }
        }
        composer.addView(attachButton, LinearLayout.LayoutParams(-2, dp(40)))

        input = EditText(this).apply {
            hint = "اكتب رسالتك…"
            setHintTextColor(MUTED)
            setTextColor(TEXT)
            setTextSize(13f)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            maxLines = 3
            minLines = 1
            setPadding(dp(12), dp(8), dp(12), dp(8))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = UiStyle.rounded(this@AiChatActivity, SURFACE_ALT, 18, BORDER, 1)
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f).apply {
            setMargins(dp(6), 0, dp(6), 0)
        })

        sendButton = Button(this).apply {
            text = "إرسال"
            setTextColor(Color.WHITE)
            setTextSize(12f)
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setPadding(dp(14), 0, dp(14), 0)
            background = UiStyle.rounded(this@AiChatActivity, PRIMARY, 12, 0xFF58A6FF.toInt(), 1)
            setOnClickListener {
                if (isSending) stopCurrentRequest() else sendMessage()
            }
        }
        composer.addView(sendButton, LinearLayout.LayoutParams(-2, dp(40)))

        rootLayout.addView(composer, LinearLayout.LayoutParams(-1, -2))

        setContentView(rootLayout)

        // ============================================================
        // ⭐ v2.2: تطبيق الـ insets على الـ root layout
        // ============================================================
        applyEdgeToEdgeInsets()
    }

    /**
     * ⭐ v2.2 — تطبيق WindowInsets على الـ root layout.
     *
     * - systemBars: شريط الحالة (top) + شريط التنقل (bottom)
     * - displayCutout: النوتش (إن وُجد)
     * - ime: لوحة المفاتيح — نطبقها على الـ bottom أيضاً لضمان
     *   ظهور حقل الإدخال فوق لوحة المفاتيح
     */
    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // ⭐ دعم النوتش
            val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            } else {
                androidx.core.graphics.Insets.NONE
            }

            // ⭐ دعم لوحة المفاتيح (IME) — نطبقها فقط على الـ bottom
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val left = maxOf(systemBars.left, cutout.left)
            val top = maxOf(systemBars.top, cutout.top)
            val right = maxOf(systemBars.right, cutout.right)
            val bottom = maxOf(
                maxOf(systemBars.bottom, cutout.bottom),
                ime.bottom
            )

            view.setPadding(left, top, right, bottom)
            insets
        }

        ViewCompat.requestApplyInsets(rootLayout)
    }

    // ============================================================
    // اختيار الوضع
    // ============================================================
    private fun selectMode(mode: AiMode) {
        if (mode == AiMode.AGENTIC && !modeStore.isAgenticAvailable) {
            val dialog = AlertDialog.Builder(this)
                .setTitle("وكيل كامل — غير متاح")
                .setMessage("هذا الوضع غير متاح حالياً.")
                .setPositiveButton("حسناً", null)
                .create()
            dialog.show()
            UiStyle.styleDialog(dialog)
            return
        }
        modeStore.setMode(mode)
        refreshModeButtons()
        updateContextLine()
        Toast.makeText(this, "الوضع الحالي: ${mode.displayName}", Toast.LENGTH_SHORT).show()
    }

    private fun refreshModeButtons() {
        val current = modeStore.mode
        val smartActive = current == AiMode.SMART
        val agenticActive = current == AiMode.AGENTIC

        btnModeSmart.background = UiStyle.rounded(
            this,
            if (smartActive) PRIMARY else SURFACE_ALT,
            10,
            if (smartActive) 0xFF58A6FF.toInt() else BORDER,
            1
        )
        btnModeSmart.setTextColor(TEXT)

        btnModeAgentic.background = UiStyle.rounded(
            this,
            if (agenticActive) PRIMARY else SURFACE_ALT,
            10,
            if (agenticActive) 0xFF58A6FF.toInt() else BORDER,
            1
        )
        btnModeAgentic.setTextColor(if (agenticActive) TEXT else MUTED)
    }

    // ============================================================
    // قائمة ≡
    // ============================================================
    private fun showMenuDialog() {
        val options = arrayOf("محادثة جديدة", "فتح ملف للسياق")
        val dialog = AlertDialog.Builder(this)
            .setTitle("خيارات")
            .setAdapter(UiStyle.dialogListAdapter(this, options)) { _, which ->
                when (which) {
                    0 -> confirmClearConversation()
                    1 -> showFilePicker()
                }
            }
            .setNegativeButton("إغلاق", null)
            .create()
        dialog.show()
        UiStyle.styleDialog(dialog)
    }

    // ============================================================
    // سطر السياق
    // ============================================================
    private fun updateContextLine() {
        val sb = StringBuilder()
        val provider = settings.defaultProvider

        // ⭐ استخدام الاسم المخصص إذا كان المزود مخصصاً
        val providerDisplayName = getProviderDisplayName(provider)
        sb.append(providerDisplayName)

        // النموذج
        val model = if (provider == AiSettings.CUSTOM) {
            customStore.getConfig()?.model ?: ""
        } else {
            settings.getModel(provider)
        }
        if (model.isNotEmpty()) sb.append(" · ").append(model)

        if (!currentFile.isNullOrEmpty()) sb.append("  |  ").append(currentFile)
        sb.append("  |  ").append(modeStore.mode.displayName)
        contextView.text = sb.toString()
    }

    /**
     * ⭐ الاسم المعروض للمزود (يدعم المخصص).
     */
    private fun getProviderDisplayName(providerId: String): String {
        return if (providerId == AiSettings.CUSTOM) {
            customStore.getDisplayName()
        } else {
            when (providerId) {
                AiSettings.GEMINI -> "Gemini"
                AiSettings.OPENROUTER -> "OpenRouter"
                else -> "OpenAI"
            }
        }
    }

    // ============================================================
    // الإرسال
    // ============================================================
    private fun sendMessage() {
        if (isDestroyedFlag) return

        val text = input.text.toString().trim()
        if (text.isEmpty()) return

        val providerId = settings.defaultProvider

        // ⭐ استخدام isReady() بدل فحص المفتاح يدوياً
        if (!AiProviderFactory.isReady(this, providerId)) {
            val msg = AiProviderFactory.getReadinessMessage(this, providerId)
                ?: "المزود غير جاهز"

            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, AiSettingsActivity::class.java))
            return
        }

        input.setText("")
        conversation.add(AiMessage.user(text))
        persistConversation()
        appendUser(text)

        isSending = true
        cancelRequested = false
        sendButton.text = "إيقاف"

        val model = if (providerId == AiSettings.CUSTOM) {
            customStore.getConfig()?.model ?: ""
        } else {
            settings.getModel(providerId)
        }
        val mode = modeStore.mode

        requestThread = Thread {
            try {
                if (mode == AiMode.AGENTIC) {
                    runAgenticLoop(providerId, model, text)
                } else {
                    runSmartMode(providerId, model, text)
                }
            } catch (e: Exception) {
                if (isDestroyedFlag) return@Thread
                if (cancelRequested || Thread.currentThread().isInterrupted) return@Thread
                runOnUiThread {
                    if (isDestroyedFlag) return@runOnUiThread
                    appendAssistantPlain("تعذر الاتصال:\n\n${safeError(e)}")
                    finishSending()
                }
            }
        }.also { it.start() }
    }

    // ============================================================
    // الوضع الذكي
    // ============================================================
    private fun runSmartMode(providerId: String, model: String, userMessage: String) {
        if (isDestroyedFlag || cancelRequested) return

        runOnUiThread {
            if (isDestroyedFlag) return@runOnUiThread
            progressView.text = "جارٍ تحليل المشروع وإعداد الرد…"
            progressView.visibility = View.VISIBLE
        }

        val systemPrompt = buildSystemPrompt(userMessage)
        val requestMessages = mutableListOf<AiMessage>()
        requestMessages.add(AiMessage.system(systemPrompt))
        requestMessages.addAll(conversation)

        val provider = AiProviderFactory.create(this, providerId)

        val response = provider.send(
            requestMessages, model,
            settings.temperature, settings.maxTokens
        )

        if (isDestroyedFlag) return
        if (cancelRequested || Thread.currentThread().isInterrupted) return

        val cleanText = AgentEngine.removeActionBlocks(response.safeText)
        val actions = AgentEngine.parseActions(response.safeText)

        runOnUiThread {
            if (isDestroyedFlag) return@runOnUiThread
            if (cancelRequested) return@runOnUiThread
            conversation.add(AiMessage.assistant(cleanText))
            persistConversation()

            if (actions.isEmpty()) {
                appendAssistantPlain(cleanText)
            } else {
                appendAssistantWithActions(cleanText, actions.size)
                showActionsDialog(actions)
            }
            finishSending()
        }
    }

    // ============================================================
    // الوضع الكامل (Agentic Loop)
    // ============================================================
    private fun runAgenticLoop(providerId: String, model: String, userMessage: String) {
        if (isDestroyedFlag) return

        runOnUiThread {
            if (isDestroyedFlag) return@runOnUiThread
            progressView.text = "الوكيل الكامل يعمل… الجولة 1"
            progressView.visibility = View.VISIBLE
        }

        val projDir = projectDir ?: return
        val executor = AgentToolExecutor(projDir)

        val messages = mutableListOf<AiMessage>()
        messages.add(AiMessage.system(buildAgenticSystemPrompt(userMessage)))
        messages.addAll(conversation)

        val provider = AiProviderFactory.create(this, providerId)

        var round = 0
        val finalText = StringBuilder()
        var reachedFinalAnswer = false

        while (round < MAX_AGENTIC_ROUNDS && !cancelRequested && !isDestroyedFlag) {
            round++
            val currentRound = round

            runOnUiThread {
                if (isDestroyedFlag) return@runOnUiThread
                progressView.text = "الوكيل الكامل — الجولة $currentRound/$MAX_AGENTIC_ROUNDS"
            }

            val response = try {
                provider.sendWithTools(
                    messages, model,
                    settings.temperature, settings.maxTokens,
                    emptyList()
                )
            } catch (e: Exception) {
                if (isDestroyedFlag) return
                val errMsg = safeError(e)
                runOnUiThread {
                    if (isDestroyedFlag) return@runOnUiThread
                    appendAssistantPlain("تعذر الاتصال بالوكيل:\n\n$errMsg")
                    finishSending()
                }
                return
            }

            if (isDestroyedFlag) return
            if (cancelRequested) return

            if (!response.hasToolCalls) {
                finalText.append(response.safeText)
                reachedFinalAnswer = true
                break
            }

            val rawAssistant = response.rawAssistantMessage
            if (rawAssistant is org.json.JSONObject) {
                val raw = AiMessage.assistantWithToolCalls(rawAssistant)
                conversation.add(raw)
                messages.add(raw)
            }

            for (call in response.safeToolCalls) {
                if (isDestroyedFlag) return
                if (cancelRequested) return

                runOnUiThread {
                    if (isDestroyedFlag) return@runOnUiThread
                    appendStepBubble(call)
                }

                val result = executor.execute(call)

                val toolMsg = if (providerId == AiSettings.GEMINI) {
                    AiMessage.tool(call.safeName, result.safeContent)
                } else {
                    AiMessage.tool(call.safeId, result.safeContent)
                }
                messages.add(toolMsg)

                runOnUiThread {
                    if (isDestroyedFlag) return@runOnUiThread
                    appendStepResultBubble(result)
                }
            }
        }

        val text = finalText.toString()
        val cleanText = AgentEngine.removeActionBlocks(text)
        val actions = AgentEngine.parseActions(text)
        val hitLimit = (round >= MAX_AGENTIC_ROUNDS && !reachedFinalAnswer)
        val finalAnswerReached = reachedFinalAnswer
        val finalCleanText = cleanText

        runOnUiThread {
            if (isDestroyedFlag) return@runOnUiThread
            if (cancelRequested) return@runOnUiThread

            if (finalCleanText.isNotEmpty()) {
                conversation.add(AiMessage.assistant(finalCleanText))
                persistConversation()
            }

            if (actions.isNotEmpty()) {
                appendAssistantWithActions(finalCleanText, actions.size)
                showActionsDialog(actions)
            } else if (finalAnswerReached) {
                appendAssistantPlain(
                    if (finalCleanText.isEmpty()) "انتهى الوكيل دون رد نصي."
                    else finalCleanText
                )
            } else if (hitLimit) {
                appendLimitReachedBubble()
            } else {
                appendAssistantPlain(
                    if (finalCleanText.isEmpty()) "انتهى الوكيل."
                    else finalCleanText
                )
            }

            finishSending()
        }
    }

    private fun buildAgenticSystemPrompt(userMessage: String): String {
        val prompt = StringBuilder()
        prompt.append("أنت وكيل برمجي داخل KlencodIDE. أنت خبير في Flutter وDart.\n\n")

        prompt.append("لديك أدوات يمكنك استدعاؤها:\n")
        prompt.append("• read_file(path): يقرأ ملفاً قبل تعديله.\n")
        prompt.append("• list_files(): يعرض كل ملفات المشروع.\n")
        prompt.append("• get_file_info(path): حجم + أسطر ملف.\n")
        prompt.append("• search(query, extension?): يبحث عن نص في المشروع.\n\n")

        prompt.append("آلية العمل:\n")
        prompt.append("1. عندما تحتاج رؤية ملف، استدعِ read_file.\n")
        prompt.append("2. إذا احتجت البحث، استخدم search.\n")
        prompt.append("3. بعد الفهم الكافي، أرسل تعديلاتك عبر وسوم <klencod_action>.\n")
        prompt.append("4. لا تكتب أي تعديل خارج الوسوم.\n\n")

        prompt.append(AgentEngine.buildInstruction()).append("\n\n")

        projectDir?.takeIf { it.isDirectory }?.let {
            try {
                val ctx = ProjectContextBuilder.build(
                    it, currentFile, userMessage, contextText
                )
                if (ctx.text.isNotEmpty()) prompt.append(ctx.text)
            } catch (e: Exception) {
                prompt.append("\n[تعذر بناء سياق المشروع: ${e.message}]\n")
            }
        }
        return prompt.toString()
    }

    // ============================================================
    // system prompt للوضع الذكي
    // ============================================================
    private fun buildSystemPrompt(userMessage: String): String {
        val prompt = StringBuilder()
        prompt.append("أنت مساعد برمجي داخل KlencodIDE. أنت خبير في Flutter وDart. ")
        prompt.append("تستطيع قراءة أي ملف في المشروع واقتراح تعديلات دقيقة عليه. ")
        prompt.append("لا تفترض أنك عدّلت أي ملف، ولا تطلب تغييرات في إعدادات الخادم أو البناء.\n\n")
        prompt.append(AgentEngine.buildInstruction()).append("\n\n")

        projectDir?.takeIf { it.isDirectory }?.let {
            try {
                val ctx = ProjectContextBuilder.build(
                    it, currentFile, userMessage, contextText
                )
                if (ctx.text.isNotEmpty()) prompt.append(ctx.text)
            } catch (e: Exception) {
                prompt.append("\n[تعذر بناء سياق المشروع: ${e.message}]\n")
            }
        }
        return prompt.toString()
    }

    // ============================================================
    // فقاعات الخطوات
    // ============================================================
    private fun appendStepBubble(call: AgentTool.ToolCall) {
        if (isDestroyedFlag) return

        val row = LinearLayout(this).apply {
            gravity = Gravity.START
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = UiStyle.rounded(this@AiChatActivity, STEP_BG, 10, BORDER, 1)
        }

        val line = TextView(this).apply {
            setTextColor(TEXT)
            setTextSize(11f)
            typeface = android.graphics.Typeface.MONOSPACE
            text = "→ ${formatToolCall(call)}"
        }
        bubble.addView(line, LinearLayout.LayoutParams(-1, -2))

        val width = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        row.addView(bubble, LinearLayout.LayoutParams(width, -2))
        messageList.addView(row, LinearLayout.LayoutParams(-1, -2))
        scrollToBottom()
    }

    private fun appendStepResultBubble(result: AgentTool.ToolResult) {
        if (isDestroyedFlag) return

        val row = LinearLayout(this).apply {
            gravity = Gravity.START
            setPadding(dp(20), dp(0), dp(4), dp(4))
        }

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = UiStyle.rounded(this@AiChatActivity, 0x00000000, 8, 0x00000000, 0)
        }

        val summary = summarizeToolResult(result)
        val line = TextView(this).apply {
            setTextColor(MUTED)
            setTextSize(10f)
            typeface = android.graphics.Typeface.MONOSPACE
            text = "← $summary"
        }
        bubble.addView(line, LinearLayout.LayoutParams(-1, -2))

        val width = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        row.addView(bubble, LinearLayout.LayoutParams(width, -2))
        messageList.addView(row, LinearLayout.LayoutParams(-1, -2))
        scrollToBottom()
    }

    private fun appendLimitReachedBubble() {
        if (isDestroyedFlag) return

        val row = LinearLayout(this).apply {
            gravity = Gravity.START
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = UiStyle.rounded(this@AiChatActivity, STEP_BG, 10, 0xFFD29922.toInt(), 1)
        }

        val line = TextView(this).apply {
            setTextColor(TEXT)
            setTextSize(12f)
            text = "وصل الوكيل إلى الحد الأقصى من الجولات ($MAX_AGENTIC_ROUNDS)."
        }
        bubble.addView(line, LinearLayout.LayoutParams(-1, -2))

        val continueBtn = Button(this).apply {
            text = "اطلب المزيد"
            setTextColor(TEXT)
            setTextSize(11f)
            isAllCaps = false
            minimumHeight = 0
            background = UiStyle.rounded(this@AiChatActivity, SURFACE_ALT, 8, BORDER, 1)
            setOnClickListener {
                Toast.makeText(
                    this@AiChatActivity,
                    "اكتب طلبك التالي، وسيستمر الوكيل من حيث توقف.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        bubble.addView(continueBtn, LinearLayout.LayoutParams(-1, dp(34)).apply {
            topMargin = dp(6)
        })

        val width = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        row.addView(bubble, LinearLayout.LayoutParams(width, -2))
        messageList.addView(row, LinearLayout.LayoutParams(-1, -2))
        scrollToBottom()
    }

    private fun formatToolCall(call: AgentTool.ToolCall): String {
        return when (call.safeName) {
            AgentTool.TOOL_READ_FILE -> "قراءة ${call.getString("path")}"
            AgentTool.TOOL_LIST_FILES -> "سرد ملفات المشروع"
            AgentTool.TOOL_FILE_INFO -> "معلومات ${call.getString("path")}"
            AgentTool.TOOL_SEARCH -> {
                val q = call.getString("query")
                val ext = call.getOptionalString("extension")
                "بحث عن \"$q\"" + if (ext != null) " في *.$ext" else ""
            }
            else -> call.safeName
        }
    }

    private fun summarizeToolResult(result: AgentTool.ToolResult): String {
        if (result.isError) {
            return "خطأ: ${shorten(result.safeContent, 100)}"
        }
        val content = result.safeContent
        val newlineIdx = content.indexOf('\n')
        var firstLine = if (newlineIdx > 0) content.substring(0, newlineIdx) else content
        if (firstLine.length > 120) firstLine = firstLine.substring(0, 120) + "…"
        return firstLine
    }

    private fun shorten(s: String, max: Int): String {
        return if (s.length <= max) s else s.substring(0, max) + "…"
    }

    // ============================================================
    // إدارة الطلب
    // ============================================================
    private fun stopCurrentRequest() {
        if (!isSending) return
        cancelRequested = true
        requestThread?.interrupt()
        finishSending()
        progressView.text = "تم إيقاف الطلب"
        progressView.visibility = View.VISIBLE
    }

    private fun finishSending() {
        if (isDestroyedFlag) return
        isSending = false
        requestThread = null
        sendButton.isEnabled = true
        sendButton.text = "إرسال"
        progressView.visibility = View.GONE
    }

    // ============================================================
    // إرفاق ملف
    // ============================================================
    private fun showFilePicker() {
        if (isDestroyedFlag) return
        val projDir = projectDir
        if (projDir == null || !projDir.isDirectory) {
            Toast.makeText(this, "افتح مشروعًا أولًا لإرفاق ملف", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val files = ProjectFileService(projDir).listFiles()
            if (files.isEmpty()) {
                Toast.makeText(this, "لا توجد ملفات قابلة للإرفاق", Toast.LENGTH_SHORT).show()
                return
            }
            val choices = files.toTypedArray()
            val dialog = AlertDialog.Builder(this)
                .setTitle("اختر ملفًا لإرفاقه يدوياً")
                .setAdapter(UiStyle.dialogListAdapter(this, choices)) { _, which ->
                    attachFile(choices[which])
                }
                .setNegativeButton("إلغاء", null)
                .create()
            dialog.show()
            UiStyle.styleDialog(dialog)
        } catch (e: Exception) {
            Toast.makeText(this, safeError(e), Toast.LENGTH_SHORT).show()
        }
    }

    private fun attachFile(relativePath: String) {
        if (isDestroyedFlag) return
        val projDir = projectDir ?: return
        try {
            val content = ProjectFileService(projDir).readFile(relativePath)
            contextText = "\n--- $relativePath ---\n$content\n--- نهاية $relativePath ---\n"
            currentFile = relativePath
            updateContextLine()
            Toast.makeText(this, "تم إرفاق $relativePath", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, safeError(e), Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // حوار الموافقة على التعديلات
    // ============================================================
    private fun showActionsDialog(actions: List<AgentEngine.AgentAction>) {
        if (isDestroyedFlag) return
        val projDir = projectDir ?: return
        if (actions.isEmpty()) return

        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val title = label("التغييرات المقترحة — راجعها قبل التطبيق", 13f, PRIMARY)
        title.setPadding(0, 0, 0, dp(8))
        preview.addView(title, LinearLayout.LayoutParams(-1, -2))

        val diffView = buildDiffPreviewView(actions)

        val diffScroll = ScrollView(this)
        diffScroll.addView(diffView, FrameLayout.LayoutParams(-1, -2))
        preview.addView(diffScroll, LinearLayout.LayoutParams(-1, dp(380)))

        val dialog = AlertDialog.Builder(this)
            .setTitle("مراجعة تغييرات الملفات")
            .setView(preview)
            .setNegativeButton("رفض", null)
            .setPositiveButton("تطبيق التغييرات") { _, _ -> applyActions(actions) }
            .create()
        dialog.show()
        UiStyle.styleDialog(dialog)
    }

    private fun buildDiffPreviewView(actions: List<AgentEngine.AgentAction>): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val projDir = projectDir ?: return container
        val files = try {
            ProjectFileService(projDir)
        } catch (e: Exception) {
            val errorView = label("تعذر قراءة الملفات: ${safeError(e)}", 11f, MUTED)
            errorView.setPadding(dp(8), dp(8), dp(8), dp(8))
            container.addView(errorView)
            return container
        }

        for (action in actions) {
            val fileHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(6))
                background = UiStyle.rounded(this@AiChatActivity, SURFACE_ALT, 8, BORDER, 1)
            }

            val fileIcon = TextView(this).apply {
                text = when {
                    action.isDelete -> "🗑"
                    action.isMove -> "↔"
                    else -> "📄"
                }
                setTextSize(12f)
                setPadding(0, 0, dp(6), 0)
            }
            fileHeader.addView(fileIcon)

            val filePath = TextView(this).apply {
                text = when {
                    action.isDelete -> "حذف: ${action.path}"
                    action.isMove -> "نقل: ${action.path} → ${action.destination}"
                    else -> "تعديل: ${action.path}"
                }
                setTextSize(11f)
                setTextColor(TEXT)
                typeface = android.graphics.Typeface.MONOSPACE
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.MIDDLE
            }
            fileHeader.addView(filePath, LinearLayout.LayoutParams(0, -2, 1f))

            var oldText = ""
            try {
                if (files.exists(action.path)) oldText = files.readFile(action.path)
            } catch (_: Exception) {
            }
            val newText = if (action.isDelete) "" else action.content

            val lines = DiffEngine.compute(oldText, newText)

            var added = 0
            var removed = 0
            for (line in lines) {
                when (line.type) {
                    DiffLine.TYPE_ADD -> added++
                    DiffLine.TYPE_REMOVE -> removed++
                }
            }

            val counter = TextView(this).apply {
                text = "[+$added -$removed]"
                setTextSize(9f)
                setTextColor(0xFF7EE787.toInt())
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(dp(6), dp(2), dp(6), dp(2))
                background = UiStyle.rounded(this@AiChatActivity, BG, 6, BORDER, 1)
            }
            val counterParams = LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(6)
            }
            fileHeader.addView(counter, counterParams)

            container.addView(fileHeader, LinearLayout.LayoutParams(-1, -2))

            val diffContent = buildDiffView(lines)
            val diffParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(4), 0, dp(12))
            }
            container.addView(diffContent, diffParams)
        }

        return container
    }

    private fun buildDiffView(lines: List<DiffLine>): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyle.rounded(this@AiChatActivity, SURFACE_ALT, 8, BORDER, 1)
            setPadding(0, dp(4), 0, dp(4))
        }

        if (lines.isEmpty()) {
            val empty = label("لا توجد تغييرات", 10f, MUTED)
            empty.setPadding(dp(8), dp(6), dp(8), dp(6))
            container.addView(empty)
            return container
        }

        for (line in lines) {
            container.addView(buildDiffLineView(line))
        }
        return container
    }

    private fun buildDiffLineView(line: DiffLine): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val bgColor: Int
        val textColor: Int
        val barColor: Int
        when (line.type) {
            DiffLine.TYPE_ADD -> {
                bgColor = 0x332EA043.toInt()
                textColor = 0xFF7EE787.toInt()
                barColor = 0xFF2EA043.toInt()
            }
            DiffLine.TYPE_REMOVE -> {
                bgColor = 0x33CF222E.toInt()
                textColor = 0xFFFF7B72.toInt()
                barColor = 0xFFCF222E.toInt()
            }
            DiffLine.TYPE_HEADER -> {
                bgColor = 0x33D29922.toInt()
                textColor = TEXT
                barColor = 0xFFD29922.toInt()
            }
            else -> {
                bgColor = 0x00000000
                textColor = 0xFF8B949E.toInt()
                barColor = 0x00000000
            }
        }

        row.setBackgroundColor(bgColor)

        val sideBar = View(this).apply {
            setBackgroundColor(barColor)
        }
        row.addView(sideBar, LinearLayout.LayoutParams(dp(3), dp(18)))

        val oldNum = TextView(this).apply {
            setTextSize(9f)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(4), 0, dp(4), 0)
            minWidth = dp(28)
            gravity = Gravity.CENTER
            text = if (line.oldLineNumber > 0) line.oldLineNumber.toString() else ""
            setTextColor(0xFF6E7681.toInt())
        }
        row.addView(oldNum)

        val newNum = TextView(this).apply {
            setTextSize(9f)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(4), 0, dp(6), 0)
            minWidth = dp(28)
            gravity = Gravity.CENTER
            text = if (line.newLineNumber > 0) line.newLineNumber.toString() else ""
            setTextColor(0xFF6E7681.toInt())
        }
        row.addView(newNum)

        val content = TextView(this).apply {
            setTextSize(10f)
            typeface = android.graphics.Typeface.MONOSPACE
            text = line.text
            setTextColor(textColor)
            setPadding(dp(6), dp(2), dp(8), dp(2))
            setTextIsSelectable(true)
            isSingleLine = false
        }
        row.addView(content, LinearLayout.LayoutParams(0, -2, 1f))

        return row
    }

    // ============================================================
    // تطبيق / تراجع
    // ============================================================
    private fun applyActions(actions: List<AgentEngine.AgentAction>) {
        val projDir = projectDir ?: return
        Thread {
            try {
                val files = ProjectFileService(projDir)
                val previous = AgentChangeStore.capture(files, actions)
                for (action in actions) AgentEngine.apply(files, action)
                lastAppliedChange = previous
                if (isDestroyedFlag) return@Thread
                runOnUiThread {
                    if (isDestroyedFlag) return@runOnUiThread
                    undoButton.visibility = View.VISIBLE
                    Toast.makeText(
                        this,
                        "تم تطبيق التغييرات بعد موافقتك",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                if (isDestroyedFlag) return@Thread
                runOnUiThread {
                    if (isDestroyedFlag) return@runOnUiThread
                    Toast.makeText(
                        this,
                        "فشل تطبيق التغييرات: ${safeError(e)}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun undoLastChange() {
        val projDir = projectDir ?: return
        val previous = lastAppliedChange ?: return
        lastAppliedChange = null
        Thread {
            try {
                AgentChangeStore.restore(ProjectFileService(projDir), previous)
                if (isDestroyedFlag) return@Thread
                runOnUiThread {
                    if (isDestroyedFlag) return@runOnUiThread
                    undoButton.visibility = View.GONE
                    Toast.makeText(
                        this,
                        "تم التراجع عن آخر تعديل",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                if (isDestroyedFlag) return@Thread
                runOnUiThread {
                    if (isDestroyedFlag) return@runOnUiThread
                    Toast.makeText(
                        this,
                        "فشل التراجع: ${safeError(e)}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun confirmClearConversation() {
        if (isDestroyedFlag) return
        val dialog = AlertDialog.Builder(this)
            .setTitle("محادثة جديدة")
            .setMessage("سيتم حذف محادثة هذا المشروع فقط. هل تريد المتابعة؟")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("متابعة") { _, _ ->
                if (isDestroyedFlag) return@setPositiveButton
                conversation.clear()
                conversationStore.clear(projectPath())
                val welcome = "بدأت محادثة جديدة. أرى مشروعك كاملاً — اكتب طلبك."
                conversation.add(AiMessage.assistant(welcome))
                persistConversation()
                renderConversation()
            }
            .create()
        dialog.show()
        UiStyle.styleDialog(dialog)
    }

    // ============================================================
    // رسم الرسائل
    // ============================================================
    private fun renderConversation() {
        if (isDestroyedFlag) return
        messageList.removeAllViews()
        for (message in conversation) {
            when (message.role) {
                "user" -> appendUser(message.content)
                "assistant" -> appendAssistantPlain(message.content)
            }
        }
        scrollToBottom()
    }

    private fun appendUser(text: String) {
        if (isDestroyedFlag) return
        addMessageBubble("أنت", text, true, 0)
    }

    private fun appendAssistantPlain(text: String?) {
        if (isDestroyedFlag) return
        addMessageBubble("مساعد AI", text ?: "", false, 0)
    }

    private fun appendAssistantWithActions(text: String?, actionCount: Int) {
        if (isDestroyedFlag) return
        addMessageBubble("مساعد AI", text ?: "", false, actionCount)
    }

    private fun addMessageBubble(sender: String, text: String, user: Boolean, actionCount: Int) {
        if (isDestroyedFlag) return

        val row = LinearLayout(this).apply {
            gravity = if (user) Gravity.END else Gravity.START
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = UiStyle.rounded(
                this@AiChatActivity,
                if (user) USER_BUBBLE else AI_BUBBLE,
                14,
                if (user) 0xFF1F6FEB.toInt() else BORDER,
                1
            )
        }

        val senderView = label(sender, 10f, MUTED).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        bubble.addView(senderView, LinearLayout.LayoutParams(-1, -2))

        val body = label(text, 14f, TEXT).apply {
            setTextIsSelectable(true)
            gravity = Gravity.RIGHT
        }
        bubble.addView(body, LinearLayout.LayoutParams(-1, -2))

        if (!user) {
            if (actionCount > 0) {
                val hint = label(
                    if (actionCount == 1) "تعديل واحد مقترح — راجعه في الحوار."
                    else "$actionCount تعديلات مقترحة — راجعها في الحوار.",
                    10f, PRIMARY
                ).apply {
                    setPadding(0, dp(4), 0, 0)
                }
                bubble.addView(hint, LinearLayout.LayoutParams(-1, -2))
            } else {
                val hint = label("قراءة فقط — لا توجد تعديلات.", 10f, MUTED).apply {
                    setPadding(0, dp(4), 0, 0)
                }
                bubble.addView(hint, LinearLayout.LayoutParams(-1, -2))
            }
        }

        val width = (resources.displayMetrics.widthPixels * 0.78f).toInt()
        row.addView(bubble, LinearLayout.LayoutParams(width, -2))
        messageList.addView(row, LinearLayout.LayoutParams(-1, -2))
        scrollToBottom()
    }

    private fun scrollToBottom() {
        if (isDestroyedFlag) return
        messageScroll.post { messageScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun persistConversation() {
        conversationStore.save(projectPath(), conversation)
    }

    private fun projectPath(): String = projectDir?.absolutePath ?: ""

    private fun safeError(e: Exception): String = AiErrorFormatter.format(e)

    // ============================================================
    // أدوات
    // ============================================================
    private fun label(text: String, size: Float, color: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextSize(size)
            setTextColor(color)
        }
    }

    // ============================================================
    // Diff inner classes
    // ============================================================
    private class DiffLine(
        val type: Int,
        val text: String,
        val oldLineNumber: Int,
        val newLineNumber: Int
    ) {
        companion object {
            const val TYPE_CONTEXT = 0
            const val TYPE_ADD = 1
            const val TYPE_REMOVE = 2
            const val TYPE_HEADER = 3
        }
    }

    private object DiffEngine {
        fun compute(oldText: String?, newText: String?): List<DiffLine> {
            val result = mutableListOf<DiffLine>()
            val oldLines = (oldText ?: "").split("\n")
            val newLines = (newText ?: "").split("\n")

            val n = oldLines.size
            val m = newLines.size

            if (n > 2000 || m > 2000) {
                var oldNum = 1
                for (line in oldLines) {
                    result.add(DiffLine(DiffLine.TYPE_REMOVE, "- $line", oldNum++, -1))
                }
                var newNum = 1
                for (line in newLines) {
                    result.add(DiffLine(DiffLine.TYPE_ADD, "+ $line", -1, newNum++))
                }
                return result
            }

            val lcs = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) {
                for (j in m - 1 downTo 0) {
                    if (oldLines[i] == newLines[j]) {
                        lcs[i][j] = lcs[i + 1][j + 1] + 1
                    } else {
                        lcs[i][j] = maxOf(lcs[i + 1][j], lcs[i][j + 1])
                    }
                }
            }

            var i = 0
            var j = 0
            var oldLineNum = 1
            var newLineNum = 1
            while (i < n && j < m) {
                when {
                    oldLines[i] == newLines[j] -> {
                        result.add(
                            DiffLine(
                                DiffLine.TYPE_CONTEXT,
                                "  ${oldLines[i]}",
                                oldLineNum, newLineNum
                            )
                        )
                        i++; j++; oldLineNum++; newLineNum++
                    }
                    lcs[i + 1][j] >= lcs[i][j + 1] -> {
                        result.add(
                            DiffLine(
                                DiffLine.TYPE_REMOVE,
                                "- ${oldLines[i]}",
                                oldLineNum, -1
                            )
                        )
                        i++; oldLineNum++
                    }
                    else -> {
                        result.add(
                            DiffLine(
                                DiffLine.TYPE_ADD,
                                "+ ${newLines[j]}",
                                -1, newLineNum
                            )
                        )
                        j++; newLineNum++
                    }
                }
            }
            while (i < n) {
                result.add(
                    DiffLine(DiffLine.TYPE_REMOVE, "- ${oldLines[i]}", oldLineNum, -1)
                )
                i++; oldLineNum++
            }
            while (j < m) {
                result.add(
                    DiffLine(DiffLine.TYPE_ADD, "+ ${newLines[j]}", -1, newLineNum)
                )
                j++; newLineNum++
            }
            return result
        }
    }

    // ============================================================
    // Extras
    // ============================================================
    companion object {
        const val EXTRA_PROJECT_DIR = "project_dir"
        const val EXTRA_CURRENT_FILE = "current_file"
    }
}