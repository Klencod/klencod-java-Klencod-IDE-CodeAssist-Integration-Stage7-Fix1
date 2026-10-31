package com.flutteride.codweva.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.flutteride.codweva.ProjectType
import com.flutteride.codweva.UiStyle

/**
 * ProjectTypeSelectorDialog — شاشة اختيار نوع المشروع.
 *
 * v3.0 — أيقونات صور من assets:
 *   - ic_f.jpg  → Flutter
 *   - ic_j.jpg  → Java
 *   - ic_k.jpg  → Kotlin
 *   - ic_r.jpg  → React Native
 *   - Fallback: الأحرف الأولى إذا فشل التحميل
 */
object ProjectTypeSelectorDialog {

    /**
     * يعرض الحوار ويستدعي onSelected عند الاختيار.
     */
    @JvmStatic
    fun show(activity: Activity?, onSelected: (ProjectType) -> Unit) {
        if (activity == null || activity.isFinishing) return

        val types = ProjectType.available()
        if (types.isEmpty()) return

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(activity, 16),
                dp(activity, 8),
                dp(activity, 16),
                dp(activity, 14)
            )
            setBackgroundColor(0xFF0D1117.toInt())
        }

        // عنوان فرعي
        val subtitle = TextView(activity).apply {
            text = "اختر نوع المشروع الذي تريد إنشاءه:"
            setTextColor(0xFF8B949E.toInt())
            setTextSize(13f)
            setPadding(dp(activity, 4), 0, dp(activity, 4), dp(activity, 12))
        }
        container.addView(subtitle)

        // الحوار
        val dialog = AlertDialog.Builder(activity)
            .setTitle("نوع المشروع")
            .setView(container)
            .setNegativeButton("إلغاء", null)
            .create()

        // بطاقات الأنواع
        for (type in types) {
            val card = buildTypeCard(activity, type) {
                dialog.dismiss()
                onSelected(type)
            }
            container.addView(
                card,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(activity, 10)
                }
            )
        }

        dialog.show()
        UiStyle.styleDialog(dialog)
    }

    // ============================================================
    // بطاقة نوع واحد
    // ============================================================
    private fun buildTypeCard(
        activity: Activity,
        type: ProjectType,
        onClick: () -> Unit
    ): View {

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(activity, 12),
                dp(activity, 12),
                dp(activity, 12),
                dp(activity, 12)
            )
            background = UiStyle.rounded(
                activity,
                0xFF161B22.toInt(),
                16,
                0xFF30363D.toInt(),
                1
            )
            isClickable = true
            isFocusable = true
        }

        // ============================================================
        // الأيقونة (صورة من assets)
        // ============================================================
        val iconSize = dp(activity, 64)

        val iconContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val iconView = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(activity, 12).toFloat()
                setColor(0xFFFFFFFF.toInt())
            }
            clipToOutline = true
        }

        // حاول تحميل الصورة من assets
        val iconDrawable = loadTypeIcon(activity, type)

        if (iconDrawable != null) {
            iconView.setImageDrawable(iconDrawable)
        } else {
            // Fallback: رسم دائرة بلون النوع + الحرف الأول
            iconView.setImageDrawable(
                buildFallbackIcon(activity, type)
            )
        }

        iconContainer.addView(
            iconView,
            LinearLayout.LayoutParams(iconSize, iconSize)
        )

        card.addView(iconContainer)

        // ============================================================
        // عمود النصوص
        // ============================================================
        val textCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        card.addView(
            textCol,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(activity, 14)
            }
        )

        // اسم النوع
        val title = TextView(activity).apply {
            text = type.displayName
            setTextColor(0xFFE6EDF3.toInt())
            setTextSize(16f)
            typeface = Typeface.DEFAULT_BOLD
        }
        textCol.addView(title)

        // وصف النوع
        val description = TextView(activity).apply {
            text = type.description
            setTextColor(0xFF8B949E.toInt())
            setTextSize(12f)
            setPadding(0, dp(activity, 3), 0, 0)
            setLineSpacing(0f, 1.15f)
        }
        textCol.addView(description)

        // شارات
        val badgesRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(activity, 7), 0, 0)
        }

        val langBadge = makeBadge(
            activity,
            ProjectType.colorFor(type),
            type.primaryLanguage
        )
        badgesRow.addView(langBadge)

        val extBadge = makeBadge(
            activity,
            0xFF30363D.toInt(),
            ".${type.primaryExtension}"
        )
        val extParams = LinearLayout.LayoutParams(-2, -2).apply {
            marginStart = dp(activity, 6)
        }
        badgesRow.addView(extBadge, extParams)

        val wfBadge = makeBadge(
            activity,
            0xFF1F6FEB.toInt(),
            "GitHub Actions"
        )
        val wfParams = LinearLayout.LayoutParams(-2, -2).apply {
            marginStart = dp(activity, 6)
        }
        badgesRow.addView(wfBadge, wfParams)

        textCol.addView(badgesRow)

        // سهم
        val arrow = TextView(activity).apply {
            text = "‹"
            setTextColor(0xFF8B949E.toInt())
            setTextSize(22f)
            gravity = Gravity.CENTER
        }
        card.addView(
            arrow,
            LinearLayout.LayoutParams(dp(activity, 22), dp(activity, 22))
        )

        card.setOnClickListener { onClick() }

        return card
    }

    // ============================================================
    // تحميل الأيقونة من assets
    // ============================================================

    /**
     * يحمّل صورة الأيقونة من assets.
     *
     * @return Drawable، أو null إذا فشل التحميل
     */
    private fun loadTypeIcon(
        context: Context,
        type: ProjectType
    ): android.graphics.drawable.Drawable? {
        val assetName = assetNameFor(type) ?: return null

        return try {
            context.assets.open(assetName).use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    // تقليل الحجم لتفادي OOM
                    inSampleSize = 1
                }

                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                    ?: return null

                if (bitmap.isRecycled) return null

                // إذا كانت الصورة أكبر من 256x256 → تصغيرها
                val maxSize = 256
                if (bitmap.width > maxSize || bitmap.height > maxSize) {
                    val scaled = Bitmap.createScaledBitmap(
                        bitmap,
                        maxSize,
                        maxSize,
                        true
                    )
                    if (scaled != bitmap) bitmap.recycle()
                    android.graphics.drawable.BitmapDrawable(
                        context.resources, scaled
                    )
                } else {
                    android.graphics.drawable.BitmapDrawable(
                        context.resources, bitmap
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * اسم الملف في assets حسب نوع المشروع.
     */
    private fun assetNameFor(type: ProjectType): String? {
        return when (type) {
            ProjectType.FLUTTER -> "project_types/ic_f.jpg"
            ProjectType.JAVA -> "project_types/ic_j.jpg"
            ProjectType.KOTLIN -> "project_types/ic_k.jpg"
            ProjectType.REACT_NATIVE -> "project_types/ic_r.jpg"
        }
    }

    // ============================================================
    // Fallback (إذا فشل التحميل)
    // ============================================================
    private fun buildFallbackIcon(
        activity: Activity,
        type: ProjectType
    ): android.graphics.drawable.Drawable {
        // دائرة بلون النوع
        val color = ProjectType.colorFor(type)
        val letter = when (type) {
            ProjectType.FLUTTER -> "F"
            ProjectType.JAVA -> "J"
            ProjectType.KOTLIN -> "K"
            ProjectType.REACT_NATIVE -> "R"
        }

        // إنشاء Bitmap 128x128
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // دائرة
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // الحرف
        val textPaint = android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG
        ).apply {
            this.color = 0xFFFFFFFF.toInt()
            textSize = size * 0.5f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val textY = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(letter, size / 2f, textY, textPaint)

        return android.graphics.drawable.BitmapDrawable(
            activity.resources, bitmap
        )
    }

    // ============================================================
    // أدوات
    // ============================================================
    private fun makeBadge(
        activity: Activity,
        color: Int,
        text: String
    ): TextView {
        return TextView(activity).apply {
            this.text = text
            setTextColor(0xFFE6EDF3.toInt())
            setTextSize(10f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(
                dp(activity, 7),
                dp(activity, 2),
                dp(activity, 7),
                dp(activity, 2)
            )
            background = GradientDrawable().apply {
                cornerRadius = dp(activity, 6).toFloat()
                setColor(withAlpha(color, 0.20f))
                setStroke(dp(activity, 1), color)
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    private fun dp(activity: Activity, value: Int): Int {
        return Math.round(value * activity.resources.displayMetrics.density)
    }
}