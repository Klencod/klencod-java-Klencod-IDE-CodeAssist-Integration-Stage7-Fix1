package com.flutteride.codweva

import android.content.Context
import android.content.SharedPreferences

/**
 * تخزين محلي بسيط لاختيار وضع المساعد (عام للتطبيق).
 */
class AiModeStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ============================================================
    // الوضع الحالي
    // ============================================================
    val mode: AiMode
        get() = AiMode.fromId(prefs.getString(KEY_MODE, AiMode.SMART.id))

    fun setMode(mode: AiMode?) {
        val safe = mode ?: AiMode.SMART
        prefs.edit().putString(KEY_MODE, safe.id).apply()
    }

    // ============================================================
    // هل الوكيل الكامل متاح؟
    // ============================================================
    val isAgenticAvailable: Boolean
        get() = true

    companion object {
        private const val PREFS = "ai_mode_prefs"
        private const val KEY_MODE = "selected_mode"
    }
}