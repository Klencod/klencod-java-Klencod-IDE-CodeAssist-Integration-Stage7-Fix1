package com.flutteride.codweva

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * CompletionPopup — نافذة الإكمال المنبثقة.
 *
 * v2.0 — متوافقة مع CodeView الجديد:
 *   - نافذة عائمة فوق المحرر
 *   - عرض: اسم + وصف + شارة نوع
 *   - حجم محدود 40% من الشاشة
 *   - موضع يحترم حدود الشاشة
 */
class CompletionPopup(private val context: Context) {

    // ============================================================
    // الألوان
    // ============================================================
    private val colorBg = 0xFF0D1117.toInt()
    private val colorBorder = 0xFF30363D.toInt()
    private val colorPrimary = 0xFF1F6FEB.toInt()
    private val colorPrimaryLight = 0xFF58A6FF.toInt()
    private val colorText = 0xFFE6EDF3.toInt()
    private val colorMuted = 0xFF8B949E.toInt()

    // ============================================================
    // الحالة
    // ============================================================
    private var popupWindow: PopupWindow? = null
    private var listView: RecyclerView? = null
    private var adapter: CompletionAdapter? = null

    private var onSelect: ((CompletionEntry) -> Unit)? = null

    private val entries = mutableListOf<CompletionEntry>()
    private var selectedIndex = 0

    // ============================================================
    // نموذج العنصر
    // ============================================================
    class CompletionEntry(
        val displayName: String,
        val insertText: String,
        val kindLabel: String,
        val description: String,
        val kindColor: Int,
        val priority: Int
    )

    // ============================================================
    // show
    // ============================================================
    fun show(
        anchor: View,
        entries: List<CompletionEntry>,
        xOffset: Int,
        yOffset: Int,
        onSelect: (CompletionEntry) -> Unit
    ) {
        if (entries.isEmpty()) {
            dismiss()
            return
        }

        this.onSelect = onSelect
        this.selectedIndex = 0

        this.entries.clear()
        this.entries.addAll(entries)

        if (popupWindow == null) {
            buildPopup()
        }

        adapter?.notifyDataSetChanged()
        listView?.scrollToPosition(0)

        val popup = popupWindow ?: return

        // قياس
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        val measuredWidth = View.MeasureSpec.makeMeasureSpec(
            (screenWidth * 0.88f).toInt(),
            View.MeasureSpec.AT_MOST
        )
        val measuredHeight = View.MeasureSpec.makeMeasureSpec(
            (screenHeight * 0.4f).toInt(),
            View.MeasureSpec.AT_MOST
        )
        popup.contentView.measure(measuredWidth, measuredHeight)

        val width = popup.contentView.measuredWidth
        val height = minOf(
            popup.contentView.measuredHeight,
            (screenHeight * 0.4f).toInt()
        )

        // الموضع
        var finalX = xOffset
        var finalY = yOffset

        if (finalX + width > screenWidth) {
            finalX = screenWidth - width - dp(8)
        }
        if (finalX < dp(8)) finalX = dp(8)

        if (finalY + height > screenHeight - dp(8)) {
            finalY = yOffset - height - dp(40)
            if (finalY < dp(8)) finalY = dp(8)
        }

        try {
            popup.width = width
            popup.height = height
            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, finalX, finalY)
        } catch (_: Exception) {
        }
    }

    fun dismiss() {
        try {
            popupWindow?.dismiss()
        } catch (_: Exception) {
        }
    }

    val isShowing: Boolean
        get() = popupWindow?.isShowing ?: false

    val count: Int
        get() = entries.size

    // ============================================================
    // التنقل
    // ============================================================
    fun moveSelectionDown() {
        if (entries.isEmpty()) return
        selectedIndex = (selectedIndex + 1) % entries.size
        adapter?.notifyDataSetChanged()
        listView?.smoothScrollToPosition(selectedIndex)
    }

    fun moveSelectionUp() {
        if (entries.isEmpty()) return
        selectedIndex = if (selectedIndex <= 0) entries.size - 1 else selectedIndex - 1
        adapter?.notifyDataSetChanged()
        listView?.smoothScrollToPosition(selectedIndex)
    }

    fun commitSelection() {
        if (entries.isEmpty()) return
        if (selectedIndex !in entries.indices) return

        val entry = entries[selectedIndex]
        onSelect?.invoke(entry)
        dismiss()
    }

    // ============================================================
    // buildPopup
    // ============================================================
    private fun buildPopup() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
            background = GradientDrawable().apply {
                setColor(colorBg)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), colorBorder)
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        listView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            setBackgroundColor(0x00000000)
            isVerticalScrollBarEnabled = true
            itemAnimator = null
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        adapter = CompletionAdapter()
        listView?.adapter = adapter

        root.addView(listView)

        popupWindow = PopupWindow(root).apply {
            isFocusable = false
            isOutsideTouchable = false
            isTouchable = true
            elevation = dp(12).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return Math.round(value * context.resources.displayMetrics.density)
    }

    // ============================================================
    // Adapter
    // ============================================================
    private inner class CompletionAdapter : RecyclerView.Adapter<CompletionAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                isClickable = true
                isFocusable = true
            }

            val textCol = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
            }
            row.addView(
                textCol,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            val name = TextView(parent.context).apply {
                setTextColor(colorText)
                setTextSize(14f)
                typeface = Typeface.MONOSPACE
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            }
            textCol.addView(name)

            val desc = TextView(parent.context).apply {
                setTextColor(colorMuted)
                setTextSize(11f)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(3), 0, 0)
            }
            textCol.addView(desc)

            val badge = TextView(parent.context).apply {
                setTextSize(10f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(3), dp(8), dp(3))
                minWidth = dp(56)
                minHeight = dp(24)
            }
            val badgeParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(12)
            }
            row.addView(badge, badgeParams)

            return VH(row, name, desc, badge)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = entries[position]
            val isSelected = (position == selectedIndex)

            holder.name.text = entry.displayName

            holder.desc.text = if (entry.description.isNotEmpty()) {
                entry.description
            } else {
                entry.kindLabel
            }

            holder.badge.text = entry.kindLabel
            holder.badge.setTextColor(entry.kindColor)
            holder.badge.background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(withAlpha(entry.kindColor, 0.15f))
                setStroke(dp(1), withAlpha(entry.kindColor, 0.4f))
            }

            holder.row.background = if (isSelected) {
                GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(colorPrimary)
                    setStroke(dp(1), colorPrimaryLight)
                }
            } else {
                GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(0x00000000)
                }
            }

            holder.row.setOnClickListener {
                selectedIndex = holder.adapterPosition
                commitSelection()
            }
        }

        override fun getItemCount(): Int = entries.size

        inner class VH(
            val row: LinearLayout,
            val name: TextView,
            val desc: TextView,
            val badge: TextView
        ) : RecyclerView.ViewHolder(row)
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    // ============================================================
    // المحوّلات
    // ============================================================
    companion object {

        @JvmStatic
        fun fromDartItems(items: List<DartCompletions.Item>): List<CompletionEntry> {
            val result = ArrayList<CompletionEntry>(items.size)
            for (item in items) {
                val kindLabel: String
                val kindColor: Int
                when (item.kind) {
                    DartCompletions.ItemKind.WIDGET -> {
                        kindLabel = "widget"
                        kindColor = 0xFF79C0FF.toInt()
                    }
                    DartCompletions.ItemKind.PROPERTY -> {
                        kindLabel = "خصيصة"
                        kindColor = 0xFF6A8CFF.toInt()
                    }
                    DartCompletions.ItemKind.METHOD -> {
                        kindLabel = "دالة"
                        kindColor = 0xFFD2A8FF.toInt()
                    }
                    DartCompletions.ItemKind.TYPE -> {
                        kindLabel = "نوع"
                        kindColor = 0xFF79C0FF.toInt()
                    }
                    DartCompletions.ItemKind.KEYWORD -> {
                        kindLabel = "كلمة"
                        kindColor = 0xFF58A6FF.toInt()
                    }
                    DartCompletions.ItemKind.IMPORT -> {
                        kindLabel = "import"
                        kindColor = 0xFF7EE787.toInt()
                    }
                    DartCompletions.ItemKind.ENUM_VALUE -> {
                        kindLabel = "قيمة"
                        kindColor = 0xFFFFA657.toInt()
                    }
                }

                val insertText = if (item.hasSnippet()) item.snippet else item.name

                result.add(
                    CompletionEntry(
                        displayName = item.name,
                        insertText = insertText,
                        kindLabel = kindLabel,
                        description = item.description,
                        kindColor = kindColor,
                        priority = item.priority
                    )
                )
            }
            return result
        }

        @JvmStatic
        fun fromGeneralItems(items: List<GeneralCompletions.Item>): List<CompletionEntry> {
            val result = ArrayList<CompletionEntry>(items.size)
            for (item in items) {
                val kindLabel: String
                val kindColor: Int
                when (item.kind) {
                    GeneralCompletions.Kind.KEYWORD -> {
                        kindLabel = "كلمة"
                        kindColor = 0xFF58A6FF.toInt()
                    }
                    GeneralCompletions.Kind.TYPE -> {
                        kindLabel = "نوع"
                        kindColor = 0xFF79C0FF.toInt()
                    }
                    GeneralCompletions.Kind.METHOD -> {
                        kindLabel = "دالة"
                        kindColor = 0xFFD2A8FF.toInt()
                    }
                    GeneralCompletions.Kind.SNIPPET -> {
                        kindLabel = "قالب"
                        kindColor = 0xFF7EE787.toInt()
                    }
                    GeneralCompletions.Kind.TAG -> {
                        kindLabel = "وسم"
                        kindColor = 0xFFFFA657.toInt()
                    }
                    GeneralCompletions.Kind.ATTRIBUTE -> {
                        kindLabel = "خاصية"
                        kindColor = 0xFF6A8CFF.toInt()
                    }
                    GeneralCompletions.Kind.PROPERTY -> {
                        kindLabel = "خصيصة"
                        kindColor = 0xFF6A8CFF.toInt()
                    }
                    GeneralCompletions.Kind.VALUE -> {
                        kindLabel = "قيمة"
                        kindColor = 0xFFFFA657.toInt()
                    }
                    GeneralCompletions.Kind.UNKNOWN -> {
                        kindLabel = "—"
                        kindColor = 0xFF8B949E.toInt()
                    }
                }

                result.add(
                    CompletionEntry(
                        displayName = item.name,
                        insertText = item.insertText,
                        kindLabel = kindLabel,
                        description = item.description,
                        kindColor = kindColor,
                        priority = item.priority
                    )
                )
            }
            return result
        }
    }
}