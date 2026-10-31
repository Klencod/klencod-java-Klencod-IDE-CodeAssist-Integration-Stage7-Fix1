package com.flutteride.codweva

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * حوار إعدادات المشروع.
 */
class ProjectSettingsDialog(
    private val context: Context,
    private val projectDir: File,
    private val onSettingsChanged: Runnable?
) : Dialog(context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("flutter_ide_prefs", Context.MODE_PRIVATE)
    private val projectName: String = projectDir.name

    private lateinit var btnChangeIcon: Button
    private lateinit var btnClose: Button
    private lateinit var ivIconPreview: ImageView
    private lateinit var btnChangeAppName: LinearLayout
    private lateinit var btnChangePackageName: LinearLayout
    private lateinit var btnChangeApi: LinearLayout

    private var currentIconFile: File? = null

    private fun dp(value: Int): Int {
        return Math.round(value * context.resources.displayMetrics.density)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(createLayout())

        window?.let { window ->
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.setGravity(Gravity.CENTER)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.7f)
        }

        setupViews()
        loadCurrentSettings()
    }

    private fun createLayout(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        root.background = GradientDrawable().apply {
            setColor(COLOR_BACKGROUND)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), 0xFF30363D.toInt())
        }

        val title = TextView(context).apply {
            text = "إعدادات المشروع"
            setTextSize(18f)
            setTextColor(COLOR_TEXT_PRIMARY)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        }
        root.addView(title)

        val iconContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(COLOR_SURFACE)
                cornerRadius = dp(12).toFloat()
            }
        }

        val iconTextInfo = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        iconTextInfo.addView(TextView(context).apply {
            text = "أيقونة التطبيق"
            setTextSize(14f)
            setTextColor(COLOR_TEXT_PRIMARY)
        })

        iconTextInfo.addView(TextView(context).apply {
            text = "ستظهر في متجر التطبيقات"
            setTextSize(11f)
            setTextColor(COLOR_TEXT_SECONDARY)
        })

        iconContainer.addView(iconTextInfo)

        ivIconPreview = ImageView(context).apply {
            val size = dp(48)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                rightMargin = dp(12)
            }
        }

        // ✅ التصحيح: outlineProvider الصحيح فقط، بدون OnLayoutChangeListener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ivIconPreview.clipToOutline = true
            ivIconPreview.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(8).toFloat())
                }
            }
        }

        iconContainer.addView(ivIconPreview)

        btnChangeIcon = createStyledButton("تغيير", false).apply {
            setTextSize(12f)
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(36))
            setOnClickListener { pickImageFromGallery() }
        }
        iconContainer.addView(btnChangeIcon)

        root.addView(iconContainer)

        val settingsList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
                bottomMargin = dp(24)
            }
            background = GradientDrawable().apply {
                setColor(COLOR_SURFACE)
                cornerRadius = dp(12).toFloat()
            }
        }

        btnChangeAppName = createSettingItem("اسم التطبيق", "تغيير اسم التطبيق المعروض")
        settingsList.addView(btnChangeAppName)
        settingsList.addView(createSeparator())

        btnChangePackageName = createSettingItem("اسم الحزمة", "تغيير المعرف الفريد (متقدم)")
        settingsList.addView(btnChangePackageName)
        settingsList.addView(createSeparator())

        btnChangeApi = createSettingItem("إصدار API", "تغيير Target SDK Level")
        settingsList.addView(btnChangeApi)

        root.addView(settingsList)

        btnClose = createStyledButton("إغلاق", true).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
            )
            setOnClickListener { dismiss() }
        }
        root.addView(btnClose)

        return root
    }

    private fun createSettingItem(title: String, subtitle: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true

            addView(TextView(context).apply {
                text = title
                setTextSize(14f)
                setTextColor(COLOR_TEXT_PRIMARY)
            })

            addView(TextView(context).apply {
                text = subtitle
                setTextSize(11f)
                setTextColor(COLOR_TEXT_SECONDARY)
            })
        }
    }

    private fun createSeparator(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply {
                leftMargin = dp(16)
                rightMargin = dp(16)
            }
            setBackgroundColor(0xFF30363D.toInt())
        }
    }

    private fun createStyledButton(text: String, isPrimary: Boolean): Button {
        return Button(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            isAllCaps = false
            setTextSize(14f)
            background = GradientDrawable().apply {
                setColor(if (isPrimary) COLOR_PRIMARY else 0xFF21262D.toInt())
                cornerRadius = dp(8).toFloat()
                if (!isPrimary) {
                    setStroke(dp(1), 0xFF30363D.toInt())
                }
            }
        }
    }

    private fun setupViews() {
        btnChangeAppName.setOnClickListener { showChangeAppNameDialog() }
        btnChangePackageName.setOnClickListener { showChangePackageNameDialog() }
        btnChangeApi.setOnClickListener { showChangeApiDialog() }
    }

    private fun loadCurrentSettings() {
        loadIconPreview()
    }

    private fun loadIconPreview() {
        val iconFile = File(projectDir, "android/app/src/main/res/drawable/ic_launcher.png")
        if (iconFile.exists()) {
            currentIconFile = iconFile
            ivIconPreview.setImageURI(Uri.fromFile(iconFile))
        } else {
            ivIconPreview.setImageResource(android.R.drawable.sym_def_app_icon)
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        if (context is Activity) {
            (context as Activity).startActivityForResult(intent, REQUEST_PICK_ICON)
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_PICK_ICON &&
            resultCode == Activity.RESULT_OK &&
            data != null
        ) {
            val selectedImageUri = data.data
            if (selectedImageUri != null) {
                saveIconToProject(selectedImageUri)
            }
        }
    }

    private fun saveIconToProject(imageUri: Uri) {
        try {
            val drawableDir = File(projectDir, "android/app/src/main/res/drawable")
            if (!drawableDir.exists()) {
                drawableDir.mkdirs()
            }

            val iconFile = File(drawableDir, "ic_launcher.png")
            context.contentResolver.openInputStream(imageUri).use { inputStream ->
                if (inputStream == null) {
                    Toast.makeText(context, "تعذر فتح الصورة", Toast.LENGTH_SHORT).show()
                    return
                }
                FileOutputStream(iconFile).use { outputStream ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }

            currentIconFile = iconFile
            ivIconPreview.setImageURI(imageUri)
            Toast.makeText(context, "تم تغيير أيقونة التطبيق", Toast.LENGTH_SHORT).show()
            onSettingsChanged?.run()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "خطأ في حفظ الأيقونة: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showChangeAppNameDialog() {
        val builder = android.app.AlertDialog.Builder(context)
        builder.setTitle("تغيير اسم التطبيق")

        val input = EditText(context).apply {
            hint = "اسم التطبيق الجديد"
            setText(prefs.getString("name_$projectName", ""))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        builder.setView(input)

        builder.setPositiveButton("حفظ") { _, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                prefs.edit().putString("name_$projectName", newName).apply()
                updateAndroidManifestLabel(newName)
                Toast.makeText(context, "تم تغيير اسم التطبيق", Toast.LENGTH_SHORT).show()
                onSettingsChanged?.run()
            }
        }
        builder.setNegativeButton("إلغاء", null)
        val dialog = builder.create()
        dialog.show()
        UiStyle.styleDialog(dialog)
    }

    private fun updateAndroidManifestLabel(newName: String) {
        val manifestFile = File(projectDir, "android/app/src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            try {
                val reader = java.io.BufferedReader(java.io.FileReader(manifestFile))
                val content = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.contains("android:label=")) {
                        line = line!!.replace(
                            Regex("android:label=\"[^\"]*\""),
                            "android:label=\"$newName\""
                        )
                    }
                    content.append(line).append("\n")
                }
                reader.close()

                java.io.FileWriter(manifestFile).use { writer ->
                    writer.write(content.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showChangePackageNameDialog() {
        val builder = android.app.AlertDialog.Builder(context)
        builder.setTitle("تغيير اسم الحزمة")
        builder.setMessage("تحذير: تغيير اسم الحزمة قد يتطلب تعديلات إضافية.")

        val input = EditText(context).apply {
            hint = "com.example.newpackage"
            setText(prefs.getString("package_$projectName", ""))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        builder.setView(input)

        builder.setPositiveButton("تغيير") { _, _ ->
            val newPackage = input.text.toString().trim()
            if (newPackage.isNotEmpty() && isValidPackageName(newPackage)) {
                prefs.edit().putString("package_$projectName", newPackage).apply()
                Toast.makeText(context, "تم تغيير اسم الحزمة", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "اسم حزمة غير صالح", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("إلغاء", null)
        val dialog = builder.create()
        dialog.show()
        UiStyle.styleDialog(dialog)
    }

    private fun showChangeApiDialog() {
        val apiLevels = arrayOf("21", "24", "28", "31", "33", "34", "35", "36")
        val apiNames = arrayOf(
            "Android 5.0 (API 21)",
            "Android 7.0 (API 24)",
            "Android 9 (API 28)",
            "Android 12 (API 31)",
            "Android 13 (API 33)",
            "Android 14 (API 34)",
            "Android 15 (API 35)",
            "Android 16 (API 36)"
        )

        val builder = android.app.AlertDialog.Builder(context)
        builder.setTitle("اختر Target API")

        val currentApi = prefs.getString("api_$projectName", "33")?.toIntOrNull() ?: 33
        var selectedIndex = 0
        for (i in apiLevels.indices) {
            if (apiLevels[i].toInt() == currentApi) {
                selectedIndex = i
                break
            }
        }

        builder.setSingleChoiceItems(
            UiStyle.dialogChoiceAdapter(context, apiNames),
            selectedIndex
        ) { dialog, which ->
            val newApi = apiLevels[which]
            prefs.edit().putString("api_$projectName", newApi).apply()
            updateAppBuildGradleApi(newApi)
            Toast.makeText(
                context,
                "تم تغيير Target API إلى $newApi",
                Toast.LENGTH_SHORT
            ).show()
            dialog.dismiss()
        }

        builder.setNegativeButton("إلغاء", null)
        val dialog = builder.create()
        dialog.show()
        UiStyle.styleDialog(dialog)
    }

    private fun updateAppBuildGradleApi(apiLevel: String) {
        val buildGradle = File(projectDir, "android/app/build.gradle")
        if (buildGradle.exists()) {
            try {
                val reader = java.io.BufferedReader(java.io.FileReader(buildGradle))
                val content = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.trim().startsWith("targetSdk")) {
                        line = "        targetSdk = $apiLevel"
                    }
                    content.append(line).append("\n")
                }
                reader.close()

                java.io.FileWriter(buildGradle).use { writer ->
                    writer.write(content.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isValidPackageName(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        val parts = packageName.split(".")
        if (parts.size < 2) return false
        for (part in parts) {
            if (part.isEmpty()) return false
            if (!part.matches(Regex("^[a-z][a-z0-9_]*\$"))) return false
        }
        return true
    }

    companion object {
        private const val COLOR_BACKGROUND = 0xFF0D1117.toInt()
        private const val COLOR_SURFACE = 0xFF161B22.toInt()
        private const val COLOR_PRIMARY = 0xFF1F6FEB.toInt()
        private const val COLOR_TEXT_PRIMARY = 0xFFE6EDF3.toInt()
        private const val COLOR_TEXT_SECONDARY = COLOR_TEXT_PRIMARY
        private const val REQUEST_PICK_ICON = 200
    }
}