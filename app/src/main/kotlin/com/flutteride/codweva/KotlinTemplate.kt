package com.flutteride.codweva.java

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.flutteride.codweva.GradleWrapperWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException

/**
 * KotlinTemplate — يولّد مشروع Android Kotlin كامل من الصفر.
 *
 * v1.0 — القالب الرسمي: لوحة رسم بسيطة (Kotlin)
 *
 * يستخدم StringBuilder بدل String Templates
 * لتفادي مشاكل $ و trimMargin في الكود المضمّن.
 */
object KotlinTemplate {

    // ============================================================
    // ثوابت
    // ============================================================
    private const val DEFAULT_PACKAGE = "com.klencod.local.kotlinapp"
    private const val DEFAULT_APP_NAME = "لوحة الرسم"
    private const val DEFAULT_MIN_SDK = 24
    private const val DEFAULT_TARGET_SDK = 34
    private const val DEFAULT_COMPILE_SDK = 34

    private const val AGP_VERSION = "8.5.2"
    private const val KOTLIN_VERSION = "1.9.20"

    // ============================================================
    // الدالة الرئيسية
    // ============================================================
    @JvmStatic
    fun write(
        context: Context?,
        projectDir: File,
        packageName: String?,
        appName: String?,
        apiLevel: String?
    ): Boolean {
        if (!projectDir.isDirectory) return false

        val safePackage = sanitizePackage(packageName)
        val safeAppName = sanitizeAppName(appName)
        val safeTargetSdk = sanitizeSdk(apiLevel, DEFAULT_TARGET_SDK)
        val safeMinSdk = minOf(DEFAULT_MIN_SDK, safeTargetSdk)

        val packagePath = safePackage.replace('.', '/')
        val appNameSnake = toSnakeCase(safeAppName)

        return try {
            // ============================================================
            // 1) إنشاء المجلدات
            // ============================================================
            val appDir = File(projectDir, "app")
            val srcMain = File(appDir, "src/main")
            val kotlinDir = File(srcMain, "java/$packagePath")
            val resValues = File(srcMain, "res/values")
            val resDrawable = File(srcMain, "res/drawable")
            val assetsDir = File(srcMain, "assets")
            val platformDir = File(projectDir, ".platform")
            val workflowsDir = File(projectDir, ".github/workflows")

            listOf(
                appDir, srcMain, kotlinDir, resValues, resDrawable,
                assetsDir, platformDir, workflowsDir
            ).forEach { dir ->
                if (!dir.exists() && !dir.mkdirs()) return false
            }

            // 2) module.toml
            writeFile(
                File(appDir, "module.toml"),
                buildModuleToml(safePackage, safeMinSdk, safeTargetSdk, DEFAULT_COMPILE_SDK)
            )

            // 3) AndroidManifest.xml
            writeFile(
                File(srcMain, "AndroidManifest.xml"),
                buildAndroidManifest()
            )

            // 4) MainActivity.kt
            writeFile(
                File(kotlinDir, "MainActivity.kt"),
                buildMainActivityKotlin(safePackage)
            )

            // 5) strings.xml
            writeFile(
                File(resValues, "strings.xml"),
                buildStringsXml(safeAppName)
            )

            // 6) app_icon.png
            writeDefaultIconPng(resDrawable)

            // 7) workspace.json
            writeFile(
                File(platformDir, "workspace.json"),
                buildWorkspaceJson(appNameSnake)
            )

            // 8) .gitignore
            writeFile(File(projectDir, ".gitignore"), buildGitignore())

            // 9) README.md
            writeFile(File(projectDir, "README.md"), buildReadme(safeAppName))

            // 10) Gradle files
            writeFile(File(projectDir, "build.gradle"), buildRootBuildGradle())
            writeFile(File(projectDir, "settings.gradle"), buildSettingsGradle(appNameSnake))
            writeFile(File(projectDir, "gradle.properties"), buildGradleProperties())
            writeFile(File(appDir, "build.gradle"), buildAppBuildGradle(safePackage, safeTargetSdk))

            // 11) Workflow
            writeFile(
                File(workflowsDir, "build-android.yml"),
                KotlinBuildWorkflow.build(safeTargetSdk)
            )

            // 12) Gradle Wrapper
            GradleWrapperWriter.ensureGradleWrapper(context, projectDir)

            true
        } catch (_: Exception) {
            false
        }
    }

    // ============================================================
    // أيقونة التطبيق — 3 دوائر ملوّنة (مثلث)
    // ============================================================
    private fun writeDefaultIconPng(resDrawable: File) {
        try {
            val targetFile = File(resDrawable, "app_icon.png")
            if (targetFile.exists()) targetFile.delete()
            if (!resDrawable.exists() && !resDrawable.mkdirs()) return

            val size = 192
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val s = size.toFloat()

            // خلفية زرقاء
            canvas.drawColor(0xFF1F6FEB.toInt())

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = s * 0.012f
                color = 0xFFFFFFFF.toInt()
            }

            val radius = s * 0.165f

            // 🟡 أصفر — أعلى
            fillPaint.color = 0xFFFFD33D.toInt()
            canvas.drawCircle(s * 0.50f, s * 0.33f, radius, fillPaint)
            canvas.drawCircle(s * 0.50f, s * 0.33f, radius, ringPaint)

            // 🔴 أحمر — أسفل يسار
            fillPaint.color = 0xFFFF7B72.toInt()
            canvas.drawCircle(s * 0.30f, s * 0.68f, radius, fillPaint)
            canvas.drawCircle(s * 0.30f, s * 0.68f, radius, ringPaint)

            // 🟢 أخضر — أسفل يمين
            fillPaint.color = 0xFF7EE787.toInt()
            canvas.drawCircle(s * 0.70f, s * 0.68f, radius, fillPaint)
            canvas.drawCircle(s * 0.70f, s * 0.68f, radius, ringPaint)

            FileOutputStream(targetFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
            bitmap.recycle()
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // أدوات التنظيف
    // ============================================================

    private fun sanitizePackage(pkg: String?): String {
        if (pkg.isNullOrBlank()) return DEFAULT_PACKAGE
        val lower = pkg.trim().lowercase()
        if (!isValidPackage(lower)) return DEFAULT_PACKAGE
        return lower
    }

    private fun isValidPackage(pkg: String): Boolean {
        val parts = pkg.split(".")
        if (parts.size < 2) return false
        return parts.all { it.matches(Regex("[a-z][a-z0-9_]*")) }
    }

    private fun sanitizeAppName(name: String?): String {
        if (name.isNullOrBlank()) return DEFAULT_APP_NAME
        val trimmed = name.trim()
        val sb = StringBuilder(trimmed.length)
        for (c in trimmed) {
            if (c.code in 0x20..0x7E || c.code in 0x0600..0x06FF) sb.append(c)
        }
        val result = sb.toString().trim()
        return if (result.isEmpty()) DEFAULT_APP_NAME else result.take(60)
    }

    private fun sanitizeSdk(value: String?, fallback: Int): Int {
        if (value.isNullOrBlank()) return fallback
        val parsed = value.trim().split(" ").firstOrNull()?.toIntOrNull() ?: return fallback
        return parsed.coerceIn(21, 36)
    }

    private fun toSnakeCase(value: String): String {
        var result = value.lowercase()
            .replace(" ", "_")
            .replace(Regex("[^a-z0-9_]"), "")
        if (result.isEmpty() || result[0].isDigit()) result = "app"
        return result.take(40)
    }

    // ============================================================
    // كتابة الملفات
    // ============================================================
    @Throws(IOException::class)
    private fun writeFile(file: File, content: String) {
        file.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }
        FileWriter(file).use { writer ->
            writer.write(content)
            writer.flush()
        }
    }

    // ============================================================
    // module.toml — Kotlin
    // ============================================================
    private fun buildModuleToml(
        packageName: String,
        minSdk: Int,
        targetSdk: Int,
        compileSdk: Int
    ): String {
        val sb = StringBuilder(512)
        sb.append("version = 2\n\n")
        sb.append("[module]\n")
        sb.append("type = \"android-app\"\n")
        sb.append("name = \"app\"\n")
        sb.append("languageLevel = \"KOTLIN_1_9\"\n")
        sb.append("output = \"build/classes\"\n\n")
        sb.append("[sourceSets.main]\n")
        sb.append("scope = \"IMPLEMENTATION\"\n")
        sb.append("assets = [\"src/main/assets\"]\n")
        sb.append("java = [\"src/main/java\"]\n")
        sb.append("res = [\"src/main/res\"]\n\n")
        sb.append("[android]\n")
        sb.append("namespace = \"").append(packageName).append("\"\n")
        sb.append("compileSdk = ").append(compileSdk).append("\n")
        sb.append("minSdk = ").append(minSdk).append("\n")
        sb.append("targetSdk = ").append(targetSdk).append("\n")
        sb.append("manifest = \"src/main/AndroidManifest.xml\"\n")
        sb.append("versionCode = 1\n")
        sb.append("versionName = \"1.0\"\n")
        sb.append("isApplication = true\n")
        return sb.toString()
    }

    // ============================================================
    // AndroidManifest.xml
    // ============================================================
    private fun buildAndroidManifest(): String {
        val sb = StringBuilder(1024)
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n\n")
        sb.append("    <application\n")
        sb.append("        android:allowBackup=\"true\"\n")
        sb.append("        android:icon=\"@drawable/app_icon\"\n")
        sb.append("        android:label=\"@string/app_name\"\n")
        sb.append("        android:roundIcon=\"@drawable/app_icon\"\n")
        sb.append("        android:supportsRtl=\"true\"\n")
        sb.append("        android:theme=\"@android:style/Theme.Material.NoActionBar\">\n\n")
        sb.append("        <activity\n")
        sb.append("            android:name=\".MainActivity\"\n")
        sb.append("            android:exported=\"true\"\n")
        sb.append("            android:screenOrientation=\"portrait\">\n")
        sb.append("            <intent-filter>\n")
        sb.append("                <action android:name=\"android.intent.action.MAIN\" />\n")
        sb.append("                <category android:name=\"android.intent.category.LAUNCHER\" />\n")
        sb.append("            </intent-filter>\n")
        sb.append("        </activity>\n\n")
        sb.append("    </application>\n")
        sb.append("</manifest>\n")
        return sb.toString()
    }

    // ============================================================
    // strings.xml
    // ============================================================
    private fun buildStringsXml(appName: String): String {
        val sb = StringBuilder(256)
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<resources>\n")
        sb.append("    <string name=\"app_name\">")
            .append(xmlEscape(appName))
            .append("</string>\n")
        sb.append("</resources>\n")
        return sb.toString()
    }

    // ============================================================
    // workspace.json
    // ============================================================
    private fun buildWorkspaceJson(projectName: String): String {
        val sb = StringBuilder(256)
        sb.append("{\n")
        sb.append("  \"buildSystem\": \"native\",\n")
        sb.append("  \"language\": \"kotlin\",\n")
        sb.append("  \"projectName\": \"").append(jsonEscape(projectName)).append("\"\n")
        sb.append("}\n")
        return sb.toString()
    }

    // ============================================================
    // .gitignore
    // ============================================================
    private fun buildGitignore(): String {
        val sb = StringBuilder(256)
        sb.append(".gradle/\n")
        sb.append("**/build/\n")
        sb.append("local.properties\n")
        sb.append("*.iml\n")
        sb.append(".idea/\n")
        sb.append("*.apk\n")
        sb.append("*.aab\n")
        sb.append(".platform/\n")
        sb.append(".klencod/\n")
        sb.append(".ds_store\n")
        return sb.toString()
    }

    // ============================================================
    // README.md
    // ============================================================
    private fun buildReadme(appName: String): String {
        val sb = StringBuilder(1024)
        sb.append("# ").append(appName).append("\n\n")
        sb.append("مشروع Android Kotlin أُنشئ بـ KlencodIDE.\n\n")
        sb.append("## القالب: لوحة رسم بسيطة 🎨\n\n")
        sb.append("### الميزات\n")
        sb.append("- رسم بالإصبع (Canvas + Path)\n")
        sb.append("- 8 ألوان + 3 سماكات فرشاة\n")
        sb.append("- تراجع (Undo) + مسح الكل\n")
        sb.append("- حفظ PNG في Pictures/KlencodDrawings (Android 10+)\n")
        sb.append("- كل المنطق في ملف MainActivity.kt واحد\n")
        sb.append("- لا مكتبات خارجية (فقط kotlin-stdlib)\n\n")
        sb.append("### البناء\n\n")
        sb.append("يتم البناء تلقائياً على GitHub Actions عند الضغط على \"بناء APK\" من داخل التطبيق.\n\n")
        sb.append("### الهيكل\n")
        sb.append("- `app/src/main/java/` — كود Kotlin (MainActivity.kt فقط)\n")
        sb.append("- `app/src/main/res/` — الموارد (strings.xml + app_icon.png)\n")
        sb.append("- `app/src/main/AndroidManifest.xml` — بيان التطبيق\n")
        sb.append("- `app/module.toml` — إعدادات الوحدة\n\n")
        sb.append("### التوسيعات المقترحة\n")
        sb.append("- 🧹 ممحاة (Eraser)\n")
        sb.append("- ↪️ إعادة (Redo)\n")
        sb.append("- 📤 مشاركة الصورة\n")
        sb.append("- 🎨 منتقي ألوان HSL\n")
        sb.append("- 🌐 شبكة خلفية\n\n")
        sb.append("### Kotlin idiom\n\n")
        sb.append("هذا القالب يستخدم:\n")
        sb.append("- `data class` للـ Stroke\n")
        sb.append("- `when` بدل `switch`\n")
        sb.append("- `?.` و `?:` لـ Null safety\n")
        sb.append("- `mutableListOf` بدل `ArrayList`\n")
        sb.append("- Top-level functions داخل `object`\n")
        return sb.toString()
    }

    // ============================================================
    // Gradle — root
    // ============================================================
    private fun buildRootBuildGradle(): String {
        val sb = StringBuilder(512)
        sb.append("// Root build.gradle (Kotlin)\n")
        sb.append("// ملاحظة: repositories معرّفة في settings.gradle فقط.\n\n")
        sb.append("plugins {\n")
        sb.append("    id 'com.android.application' version '")
            .append(AGP_VERSION).append("' apply false\n")
        sb.append("    id 'com.android.library' version '")
            .append(AGP_VERSION).append("' apply false\n")
        sb.append("    id 'org.jetbrains.kotlin.android' version '")
            .append(KOTLIN_VERSION).append("' apply false\n")
        sb.append("}\n\n")
        sb.append("subprojects {\n")
        sb.append("    project.evaluationDependsOn(\":app\")\n")
        sb.append("}\n\n")
        sb.append("tasks.register(\"clean\", Delete) {\n")
        sb.append("    delete rootProject.buildDir\n")
        sb.append("}\n")
        return sb.toString()
    }

    // ============================================================
    // Gradle — settings
    // ============================================================
    private fun buildSettingsGradle(projectName: String): String {
        val sb = StringBuilder(1024)
        sb.append("pluginManagement {\n")
        sb.append("    repositories {\n")
        sb.append("        google()\n")
        sb.append("        mavenCentral()\n")
        sb.append("        gradlePluginPortal()\n")
        sb.append("    }\n")
        sb.append("}\n\n")
        sb.append("plugins {\n")
        sb.append("    id 'com.android.application' version '")
            .append(AGP_VERSION).append("' apply false\n")
        sb.append("    id 'com.android.library' version '")
            .append(AGP_VERSION).append("' apply false\n")
        sb.append("    id 'org.jetbrains.kotlin.android' version '")
            .append(KOTLIN_VERSION).append("' apply false\n")
        sb.append("}\n\n")
        sb.append("dependencyResolutionManagement {\n")
        sb.append("    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n")
        sb.append("    repositories {\n")
        sb.append("        google()\n")
        sb.append("        mavenCentral()\n")
        sb.append("    }\n")
        sb.append("}\n\n")
        sb.append("rootProject.name = \"").append(projectName).append("\"\n")
        sb.append("include \":app\"\n")
        return sb.toString()
    }

    // ============================================================
    // Gradle — properties
    // ============================================================
    private fun buildGradleProperties(): String {
        val sb = StringBuilder(256)
        sb.append("org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\n")
        sb.append("android.useAndroidX=true\n")
        sb.append("android.enableJetifier=true\n")
        sb.append("android.nonTransitiveRClass=true\n")
        sb.append("kotlin.code.style=official\n")
        return sb.toString()
    }

    // ============================================================
    // Gradle — app module (Kotlin plugin)
    // ============================================================
    private fun buildAppBuildGradle(packageName: String, targetSdk: Int): String {
        val sb = StringBuilder(1024)
        sb.append("plugins {\n")
        sb.append("    id 'com.android.application'\n")
        sb.append("    id 'org.jetbrains.kotlin.android'\n")
        sb.append("}\n\n")
        sb.append("android {\n")
        sb.append("    namespace \"").append(packageName).append("\"\n")
        sb.append("    compileSdk 34\n\n")
        sb.append("    defaultConfig {\n")
        sb.append("        applicationId \"").append(packageName).append("\"\n")
        sb.append("        minSdk 24\n")
        sb.append("        targetSdk ").append(targetSdk).append("\n")
        sb.append("        versionCode 1\n")
        sb.append("        versionName \"1.0\"\n")
        sb.append("    }\n\n")
        sb.append("    buildTypes {\n")
        sb.append("        debug {\n")
        sb.append("            minifyEnabled false\n")
        sb.append("        }\n")
        sb.append("        release {\n")
        sb.append("            minifyEnabled false\n")
        sb.append("            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'\n")
        sb.append("        }\n")
        sb.append("    }\n\n")
        sb.append("    compileOptions {\n")
        sb.append("        sourceCompatibility JavaVersion.VERSION_17\n")
        sb.append("        targetCompatibility JavaVersion.VERSION_17\n")
        sb.append("    }\n\n")
        sb.append("    kotlinOptions {\n")
        sb.append("        jvmTarget = '17'\n")
        sb.append("    }\n\n")
        sb.append("    sourceSets {\n")
        sb.append("        main {\n")
        sb.append("            java.srcDirs = ['src/main/java']\n")
        sb.append("            res.srcDirs = ['src/main/res']\n")
        sb.append("            assets.srcDirs = ['src/main/assets']\n")
        sb.append("        }\n")
        sb.append("    }\n")
        sb.append("}\n\n")
        sb.append("dependencies {\n")
        sb.append("    implementation fileTree(dir: 'libs', include: ['*.jar', '*.aar'])\n")
        sb.append("    // kotlin-stdlib مضمّن تلقائياً من Kotlin Gradle Plugin\n")
        sb.append("}\n")
        return sb.toString()
    }

    // ============================================================
    // MainActivity.kt — لوحة الرسم (Kotlin)
    //
    // ⚠️ مهم: نستخدم StringBuilder لتجنّب مشاكل $ في Kotlin
    // كل "$" في الكود المضمّن يُكتب كـ "\$" في StringBuilder
    // ============================================================
    private fun buildMainActivityKotlin(packageName: String): String {
        val sb = StringBuilder(16384)

        // ============================================================
        // Header
        // ============================================================
        sb.append("package ").append(packageName).append("\n\n")

        // ============================================================
        // Imports
        // ============================================================
        sb.append("import android.app.Activity\n")
        sb.append("import android.app.AlertDialog\n")
        sb.append("import android.content.ContentValues\n")
        sb.append("import android.graphics.Bitmap\n")
        sb.append("import android.graphics.Canvas\n")
        sb.append("import android.graphics.Color\n")
        sb.append("import android.graphics.Paint\n")
        sb.append("import android.graphics.Path\n")
        sb.append("import android.graphics.drawable.GradientDrawable\n")
        sb.append("import android.os.Build\n")
        sb.append("import android.os.Bundle\n")
        sb.append("import android.os.Environment\n")
        sb.append("import android.provider.MediaStore\n")
        sb.append("import android.view.Gravity\n")
        sb.append("import android.view.MotionEvent\n")
        sb.append("import android.view.View\n")
        sb.append("import android.widget.Button\n")
        sb.append("import android.widget.FrameLayout\n")
        sb.append("import android.widget.LinearLayout\n")
        sb.append("import android.widget.TextView\n")
        sb.append("import android.widget.Toast\n")
        sb.append("import java.text.SimpleDateFormat\n")
        sb.append("import java.util.Date\n")
        sb.append("import java.util.Locale\n\n")

        // ============================================================
        // Class documentation
        // ============================================================
        sb.append("/**\n")
        sb.append(" * لوحة رسم بسيطة — قالب KlencodIDE (Kotlin)\n")
        sb.append(" *\n")
        sb.append(" * الميزات:\n")
        sb.append(" *   - رسم بالإصبع (Canvas + Path)\n")
        sb.append(" *   - 8 ألوان\n")
        sb.append(" *   - 3 سماكات فرشاة\n")
        sb.append(" *   - تراجع (Undo)\n")
        sb.append(" *   - مسح الكل\n")
        sb.append(" *   - حفظ PNG في Pictures/KlencodDrawings (Android 10+)\n")
        sb.append(" *\n")
        sb.append(" * © 2026 KlencodIDE\n")
        sb.append(" */\n")

        // ============================================================
        // Class declaration
        // ============================================================
        sb.append("class MainActivity : Activity() {\n\n")

        // ============================================================
        // Colors
        // ============================================================
        sb.append("    // ============================================================\n")
        sb.append("    // الألوان الأساسية\n")
        sb.append("    // ============================================================\n")
        sb.append("    private val colorBg = 0xFF0D1117.toInt()\n")
        sb.append("    private val colorSurface = 0xFF161B22.toInt()\n")
        sb.append("    private val colorSurfaceAlt = 0xFF21262D.toInt()\n")
        sb.append("    private val colorPrimary = 0xFF1F6FEB.toInt()\n")
        sb.append("    private val colorPrimaryLight = 0xFF58A6FF.toInt()\n")
        sb.append("    private val colorText = 0xFFE6EDF3.toInt()\n")
        sb.append("    private val colorMuted = 0xFF8B949E.toInt()\n")
        sb.append("    private val colorBorder = 0xFF30363D.toInt()\n\n")

        // ============================================================
        // Palette
        // ============================================================
        sb.append("    // ============================================================\n")
        sb.append("    // 8 ألوان للرسم\n")
        sb.append("    // ============================================================\n")
        sb.append("    private val palette = intArrayOf(\n")
        sb.append("        0xFFFFFFFF.toInt(), // أبيض\n")
        sb.append("        0xFF000000.toInt(), // أسود\n")
        sb.append("        0xFFFF7B72.toInt(), // أحمر\n")
        sb.append("        0xFF7EE787.toInt(), // أخضر\n")
        sb.append("        0xFF58A6FF.toInt(), // أزرق\n")
        sb.append("        0xFFD2A8FF.toInt(), // بنفسجي\n")
        sb.append("        0xFFFFD33D.toInt(), // أصفر\n")
        sb.append("        0xFFFF9E64.toInt()  // برتقالي\n")
        sb.append("    )\n\n")

        // ============================================================
        // Brush sizes
        // ============================================================
        sb.append("    // ============================================================\n")
        sb.append("    // 3 سماكات\n")
        sb.append("    // ============================================================\n")
        sb.append("    private val brushSizes = floatArrayOf(4f, 10f, 22f)\n\n")

        // ============================================================
        // State
        // ============================================================
        sb.append("    // ============================================================\n")
        sb.append("    // الحالة\n")
        sb.append("    // ============================================================\n")
        sb.append("    private lateinit var drawingView: DrawingView\n")
        sb.append("    private var currentColorIndex = 2  // أحمر افتراضي\n")
        sb.append("    private var currentSizeIndex = 1   // متوسط افتراضي\n")
        sb.append("    private val colorButtons = mutableListOf<Button>()\n")
        sb.append("    private val sizeButtons = mutableListOf<Button>()\n\n")

        // ============================================================
        // dp helper
        // ============================================================
        sb.append("    // ============================================================\n")
        sb.append("    // dp helper\n")
        sb.append("    // ============================================================\n")
        sb.append("    private fun dp(value: Int): Int =\n")
        sb.append("        Math.round(value * resources.displayMetrics.density)\n\n")

        // ============================================================
        // onCreate
        // ============================================================
        sb.append("    // ============================================================\n")
        sb.append("    // onCreate\n")
        sb.append("    // ============================================================\n")
        sb.append("    override fun onCreate(state: Bundle?) {\n")
        sb.append("        super.onCreate(state)\n\n")
        sb.append("        window.statusBarColor = colorBg\n")
        sb.append("        window.navigationBarColor = colorBg\n\n")
        sb.append("        // Root\n")
        sb.append("        val root = LinearLayout(this).apply {\n")
        sb.append("            orientation = LinearLayout.VERTICAL\n")
        sb.append("            setBackgroundColor(colorBg)\n")
        sb.append("        }\n\n")

        // --- Header ---
        sb.append("        // ============================================================\n")
        sb.append("        // الترويسة العلوية\n")
        sb.append("        // ============================================================\n")
        sb.append("        val header = LinearLayout(this).apply {\n")
        sb.append("            orientation = LinearLayout.HORIZONTAL\n")
        sb.append("            gravity = Gravity.CENTER_VERTICAL\n")
        sb.append("            setPadding(dp(14), dp(10), dp(14), dp(10))\n")
        sb.append("            setBackgroundColor(colorSurface)\n")
        sb.append("        }\n\n")
        sb.append("        val title = TextView(this).apply {\n")
        sb.append("            text = \"🎨 لوحة الرسم\"\n")
        sb.append("            setTextColor(colorText)\n")
        sb.append("            setTextSize(17f)\n")
        sb.append("            typeface = android.graphics.Typeface.DEFAULT_BOLD\n")
        sb.append("        }\n")
        sb.append("        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))\n\n")
        sb.append("        val undoBtn = makeTopButton(\"↶ تراجع\").apply {\n")
        sb.append("            setOnClickListener { drawingView.undo() }\n")
        sb.append("        }\n")
        sb.append("        header.addView(undoBtn, LinearLayout.LayoutParams(-2, dp(40)))\n\n")
        sb.append("        val clearBtn = makeTopButton(\"🗑 مسح\").apply {\n")
        sb.append("            setOnClickListener { confirmClear() }\n")
        sb.append("        }\n")
        sb.append("        val clearParams = LinearLayout.LayoutParams(-2, dp(40)).apply {\n")
        sb.append("            leftMargin = dp(6)\n")
        sb.append("        }\n")
        sb.append("        header.addView(clearBtn, clearParams)\n\n")
        sb.append("        root.addView(header, LinearLayout.LayoutParams(-1, -2))\n\n")

        // --- Drawing area ---
        sb.append("        // ============================================================\n")
        sb.append("        // منطقة الرسم\n")
        sb.append("        // ============================================================\n")
        sb.append("        val drawContainer = FrameLayout(this).apply {\n")
        sb.append("            setBackgroundColor(Color.WHITE)\n")
        sb.append("        }\n\n")
        sb.append("        drawingView = DrawingView(this)\n")
        sb.append("        drawContainer.addView(\n")
        sb.append("            drawingView,\n")
        sb.append("            FrameLayout.LayoutParams(-1, -1)\n")
        sb.append("        )\n\n")
        sb.append("        val drawParams = LinearLayout.LayoutParams(-1, 0, 1f).apply {\n")
        sb.append("            setMargins(dp(12), dp(12), dp(12), dp(12))\n")
        sb.append("        }\n")
        sb.append("        root.addView(drawContainer, drawParams)\n\n")

        // --- Controls ---
        sb.append("        // ============================================================\n")
        sb.append("        // شريط التحكم السفلي\n")
        sb.append("        // ============================================================\n")
        sb.append("        val controls = LinearLayout(this).apply {\n")
        sb.append("            orientation = LinearLayout.VERTICAL\n")
        sb.append("            setPadding(dp(12), dp(10), dp(12), dp(12))\n")
        sb.append("            setBackgroundColor(colorSurface)\n")
        sb.append("        }\n\n")

        // Color row
        sb.append("        // --- صف الألوان ---\n")
        sb.append("        val colorLabel = TextView(this).apply {\n")
        sb.append("            text = \"اللون:\"\n")
        sb.append("            setTextColor(colorMuted)\n")
        sb.append("            setTextSize(12f)\n")
        sb.append("        }\n")
        sb.append("        controls.addView(colorLabel)\n\n")
        sb.append("        val colorRow = LinearLayout(this).apply {\n")
        sb.append("            orientation = LinearLayout.HORIZONTAL\n")
        sb.append("            gravity = Gravity.CENTER_VERTICAL\n")
        sb.append("        }\n")
        sb.append("        val colorRowParams = LinearLayout.LayoutParams(-1, dp(50)).apply {\n")
        sb.append("            topMargin = dp(6)\n")
        sb.append("        }\n")
        sb.append("        controls.addView(colorRow, colorRowParams)\n\n")
        sb.append("        palette.forEachIndexed { index, color ->\n")
        sb.append("            val colorBtn = Button(this).apply {\n")
        sb.append("                background = makeCircleDrawable(color)\n")
        sb.append("                setPadding(0, 0, 0, 0)\n")
        sb.append("                setOnClickListener { selectColor(index) }\n")
        sb.append("            }\n")
        sb.append("            val cp = LinearLayout.LayoutParams(0, dp(40), 1f).apply {\n")
        sb.append("                setMargins(dp(3), 0, dp(3), 0)\n")
        sb.append("            }\n")
        sb.append("            colorRow.addView(colorBtn, cp)\n")
        sb.append("            colorButtons.add(colorBtn)\n")
        sb.append("        }\n\n")

        // Size row
        sb.append("        // --- صف السماكات ---\n")
        sb.append("        val sizeLabel = TextView(this).apply {\n")
        sb.append("            text = \"سماكة الفرشاة:\"\n")
        sb.append("            setTextColor(colorMuted)\n")
        sb.append("            setTextSize(12f)\n")
        sb.append("        }\n")
        sb.append("        val sizeLabelParams = LinearLayout.LayoutParams(-1, -2).apply {\n")
        sb.append("            topMargin = dp(12)\n")
        sb.append("        }\n")
        sb.append("        controls.addView(sizeLabel, sizeLabelParams)\n\n")
        sb.append("        val sizeRow = LinearLayout(this).apply {\n")
        sb.append("            orientation = LinearLayout.HORIZONTAL\n")
        sb.append("            gravity = Gravity.CENTER_VERTICAL\n")
        sb.append("        }\n")
        sb.append("        val sizeRowParams = LinearLayout.LayoutParams(-1, dp(46)).apply {\n")
        sb.append("            topMargin = dp(6)\n")
        sb.append("        }\n")
        sb.append("        controls.addView(sizeRow, sizeRowParams)\n\n")
        sb.append("        val sizeNames = arrayOf(\"رفيع\", \"متوسط\", \"عريض\")\n")
        sb.append("        sizeNames.forEachIndexed { index, name ->\n")
        sb.append("            val sizeBtn = Button(this).apply {\n")
        sb.append("                text = name\n")
        sb.append("                setTextSize(12f)\n")
        sb.append("                isAllCaps = false\n")
        sb.append("                setPadding(0, 0, 0, 0)\n")
        sb.append("                setOnClickListener { selectSize(index) }\n")
        sb.append("            }\n")
        sb.append("            val sp = LinearLayout.LayoutParams(0, dp(42), 1f).apply {\n")
        sb.append("                setMargins(dp(4), 0, dp(4), 0)\n")
        sb.append("            }\n")
        sb.append("            sizeRow.addView(sizeBtn, sp)\n")
        sb.append("            sizeButtons.add(sizeBtn)\n")
        sb.append("        }\n\n")

        // Save button
        sb.append("        // --- زر الحفظ ---\n")
        sb.append("        val saveBtn = Button(this).apply {\n")
        sb.append("            text = \"💾 حفظ الصورة\"\n")
        sb.append("            setTextSize(14f)\n")
        sb.append("            isAllCaps = false\n")
        sb.append("            setTextColor(Color.WHITE)\n")
        sb.append("            typeface = android.graphics.Typeface.DEFAULT_BOLD\n")
        sb.append("            background = GradientDrawable().apply {\n")
        sb.append("                setColor(0xFF238636.toInt())\n")
        sb.append("                cornerRadius = dp(12).toFloat()\n")
        sb.append("                setStroke(dp(1), 0xFF56D364.toInt())\n")
        sb.append("            }\n")
        sb.append("            elevation = dp(2).toFloat()\n")
        sb.append("            setOnClickListener { saveImage() }\n")
        sb.append("        }\n")
        sb.append("        val saveParams = LinearLayout.LayoutParams(-1, dp(50)).apply {\n")
        sb.append("            topMargin = dp(14)\n")
        sb.append("        }\n")
        sb.append("        controls.addView(saveBtn, saveParams)\n\n")
        sb.append("        root.addView(controls, LinearLayout.LayoutParams(-1, -2))\n\n")
        sb.append("        setContentView(root)\n\n")

        // Defaults
        sb.append("        // الاختيارات الافتراضية\n")
        sb.append("        selectColor(currentColorIndex)\n")
        sb.append("        selectSize(currentSizeIndex)\n")
        sb.append("    }\n\n")

        // ============================================================
        // Helper methods
        // ============================================================
        sb.append("    // ============================================================\n")
        sb.append("    // دوال مساعدة للواجهة\n")
        sb.append("    // ============================================================\n")
        sb.append("    private fun makeTopButton(text: String): Button =\n")
        sb.append("        Button(this).apply {\n")
        sb.append("            this.text = text\n")
        sb.append("            setTextSize(12f)\n")
        sb.append("            isAllCaps = false\n")
        sb.append("            setTextColor(colorText)\n")
        sb.append("            setPadding(dp(12), 0, dp(12), 0)\n")
        sb.append("            background = GradientDrawable().apply {\n")
        sb.append("                setColor(colorSurfaceAlt)\n")
        sb.append("                cornerRadius = dp(10).toFloat()\n")
        sb.append("                setStroke(dp(1), colorBorder)\n")
        sb.append("            }\n")
        sb.append("        }\n\n")

        sb.append("    private fun makeCircleDrawable(color: Int): GradientDrawable =\n")
        sb.append("        GradientDrawable().apply {\n")
        sb.append("            shape = GradientDrawable.OVAL\n")
        sb.append("            setColor(color)\n")
        sb.append("            setStroke(dp(2), colorBorder)\n")
        sb.append("        }\n\n")

        sb.append("    private fun makeSelectedCircleDrawable(color: Int): GradientDrawable =\n")
        sb.append("        GradientDrawable().apply {\n")
        sb.append("            shape = GradientDrawable.OVAL\n")
        sb.append("            setColor(color)\n")
        sb.append("            setStroke(dp(3), colorPrimaryLight)\n")
        sb.append("        }\n\n")

        sb.append("    private fun makeSizeButtonDrawable(selected: Boolean): GradientDrawable =\n")
        sb.append("        GradientDrawable().apply {\n")
        sb.append("            cornerRadius = dp(10).toFloat()\n")
        sb.append("            setColor(if (selected) colorPrimary else colorSurfaceAlt)\n")
        sb.append("            setStroke(dp(1), if (selected) colorPrimaryLight else colorBorder)\n")
        sb.append("        }\n\n")

        sb.append("    private fun selectColor(index: Int) {\n")
        sb.append("        currentColorIndex = index\n")
        sb.append("        drawingView.setColor(palette[index])\n\n")
        sb.append("        colorButtons.forEachIndexed { i, button ->\n")
        sb.append("            button.background = if (i == index) {\n")
        sb.append("                makeSelectedCircleDrawable(palette[i])\n")
        sb.append("            } else {\n")
        sb.append("                makeCircleDrawable(palette[i])\n")
        sb.append("            }\n")
        sb.append("        }\n")
        sb.append("    }\n\n")

        sb.append("    private fun selectSize(index: Int) {\n")
        sb.append("        currentSizeIndex = index\n")
        sb.append("        drawingView.setStrokeWidth(brushSizes[index])\n\n")
        sb.append("        sizeButtons.forEachIndexed { i, button ->\n")
        sb.append("            button.background = makeSizeButtonDrawable(i == index)\n")
        sb.append("            button.setTextColor(if (i == index) Color.WHITE else colorText)\n")
        sb.append("        }\n")
        sb.append("    }\n\n")

        // ============================================================
        // saveImage
        // ============================================================
        sb.append("    // ============================================================\n")
        sb.append("    // حفظ الصورة\n")
        sb.append("    // ============================================================\n")
        sb.append("    private fun saveImage() {\n")
        sb.append("        try {\n")
        sb.append("            val bitmap = drawingView.exportBitmap()\n")
        sb.append("            if (bitmap == null) {\n")
        sb.append("                Toast.makeText(this, \"لا يوجد شيء لحفظه\", Toast.LENGTH_SHORT).show()\n")
        sb.append("                return\n")
        sb.append("            }\n\n")
        sb.append("            val timestamp = SimpleDateFormat(\"yyyyMMdd_HHmmss\", Locale.US)\n")
        sb.append("                .format(Date())\n")
        sb.append("            val fileName = \"drawing_\".plus(timestamp).plus(\".png\")\n\n")
        sb.append("            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {\n")
        sb.append("                val values = ContentValues().apply {\n")
        sb.append("                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)\n")
        sb.append("                    put(MediaStore.Images.Media.MIME_TYPE, \"image/png\")\n")
        sb.append("                    put(\n")
        sb.append("                        MediaStore.Images.Media.RELATIVE_PATH,\n")
        sb.append("                        Environment.DIRECTORY_PICTURES + \"/KlencodDrawings\"\n")
        sb.append("                    )\n")
        sb.append("                }\n\n")
        sb.append("                val uri = contentResolver.insert(\n")
        sb.append("                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,\n")
        sb.append("                    values\n")
        sb.append("                )\n\n")
        sb.append("                if (uri == null) {\n")
        sb.append("                    Toast.makeText(this, \"تعذر إنشاء ملف\", Toast.LENGTH_SHORT).show()\n")
        sb.append("                    return\n")
        sb.append("                }\n\n")
        sb.append("                contentResolver.openOutputStream(uri)?.use { out ->\n")
        sb.append("                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)\n")
        sb.append("                }\n")
        sb.append("                bitmap.recycle()\n\n")
        sb.append("                Toast.makeText(\n")
        sb.append("                    this,\n")
        sb.append("                    \"✅ تم حفظ الصورة في:\\nPictures/KlencodDrawings/\".plus(fileName),\n")
        sb.append("                    Toast.LENGTH_LONG\n")
        sb.append("                ).show()\n")
        sb.append("            } else {\n")
        sb.append("                Toast.makeText(\n")
        sb.append("                    this,\n")
        sb.append("                    \"يحتاج Android 10+ للحفظ\",\n")
        sb.append("                    Toast.LENGTH_LONG\n")
        sb.append("                ).show()\n")
        sb.append("            }\n")
        sb.append("        } catch (e: Exception) {\n")
        sb.append("            Toast.makeText(this, \"خطأ: \".plus(e.message), Toast.LENGTH_LONG).show()\n")
        sb.append("        }\n")
        sb.append("    }\n\n")

        // ============================================================
        // confirmClear
        // ============================================================
        sb.append("    private fun confirmClear() {\n")
        sb.append("        AlertDialog.Builder(this)\n")
        sb.append("            .setTitle(\"مسح اللوحة\")\n")
        sb.append("            .setMessage(\"هل أنت متأكد من مسح كل الرسم؟\")\n")
        sb.append("            .setPositiveButton(\"مسح\") { _, _ -> drawingView.clear() }\n")
        sb.append("            .setNegativeButton(\"إلغاء\", null)\n")
        sb.append("            .show()\n")
        sb.append("    }\n\n")

        // ============================================================
        // DrawingView class
        // ============================================================
        sb.append("    // ============================================================\n")
        sb.append("    // DrawingView — Canvas مخصص\n")
        sb.append("    // ============================================================\n")
        sb.append("    private class DrawingView(context: Activity) : View(context) {\n\n")
        sb.append("        private val strokes = mutableListOf<Stroke>()\n")
        sb.append("        private var currentStroke: Stroke? = null\n\n")
        sb.append("        private var currentColor = Color.RED\n")
        sb.append("        private var currentStrokeWidth = 10f\n\n")
        sb.append("        init {\n")
        sb.append("            isFocusable = true\n")
        sb.append("            setBackgroundColor(Color.WHITE)\n")
        sb.append("        }\n\n")
        sb.append("        fun setColor(color: Int) {\n")
        sb.append("            currentColor = color\n")
        sb.append("        }\n\n")
        sb.append("        fun setStrokeWidth(width: Float) {\n")
        sb.append("            currentStrokeWidth = width\n")
        sb.append("        }\n\n")
        sb.append("        fun clear() {\n")
        sb.append("            strokes.clear()\n")
        sb.append("            currentStroke = null\n")
        sb.append("            invalidate()\n")
        sb.append("        }\n\n")
        sb.append("        fun undo() {\n")
        sb.append("            if (strokes.isEmpty()) return\n")
        sb.append("            strokes.removeAt(strokes.size - 1)\n")
        sb.append("            invalidate()\n")
        sb.append("        }\n\n")
        sb.append("        fun exportBitmap(): Bitmap? {\n")
        sb.append("            if (width <= 0 || height <= 0) return null\n")
        sb.append("            val bmp = Bitmap.createBitmap(\n")
        sb.append("                width, height, Bitmap.Config.ARGB_8888\n")
        sb.append("            )\n")
        sb.append("            val canvas = Canvas(bmp)\n")
        sb.append("            canvas.drawColor(Color.WHITE)\n")
        sb.append("            strokes.forEach { it.draw(canvas) }\n")
        sb.append("            currentStroke?.draw(canvas)\n")
        sb.append("            return bmp\n")
        sb.append("        }\n\n")
        sb.append("        override fun onDraw(canvas: Canvas) {\n")
        sb.append("            super.onDraw(canvas)\n")
        sb.append("            strokes.forEach { it.draw(canvas) }\n")
        sb.append("            currentStroke?.draw(canvas)\n")
        sb.append("        }\n\n")
        sb.append("        override fun onTouchEvent(event: MotionEvent): Boolean {\n")
        sb.append("            val x = event.x\n")
        sb.append("            val y = event.y\n\n")
        sb.append("            when (event.action) {\n")
        sb.append("                MotionEvent.ACTION_DOWN -> {\n")
        sb.append("                    currentStroke = Stroke(currentColor, currentStrokeWidth)\n")
        sb.append("                    currentStroke?.addPoint(x, y)\n")
        sb.append("                    invalidate()\n")
        sb.append("                    return true\n")
        sb.append("                }\n")
        sb.append("                MotionEvent.ACTION_MOVE -> {\n")
        sb.append("                    currentStroke?.addPoint(x, y)\n")
        sb.append("                    invalidate()\n")
        sb.append("                    return true\n")
        sb.append("                }\n")
        sb.append("                MotionEvent.ACTION_UP,\n")
        sb.append("                MotionEvent.ACTION_CANCEL -> {\n")
        sb.append("                    currentStroke?.let {\n")
        sb.append("                        strokes.add(it)\n")
        sb.append("                        currentStroke = null\n")
        sb.append("                        invalidate()\n")
        sb.append("                    }\n")
        sb.append("                    return true\n")
        sb.append("                }\n")
        sb.append("            }\n")
        sb.append("            return super.onTouchEvent(event)\n")
        sb.append("        }\n")
        sb.append("    }\n\n")

        // ============================================================
        // Stroke class
        // ============================================================
        sb.append("    // ============================================================\n")
        sb.append("    // Stroke — ضربة واحدة (مسار واحد)\n")
        sb.append("    // ============================================================\n")
        sb.append("    private class Stroke(color: Int, strokeWidth: Float) {\n\n")
        sb.append("        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {\n")
        sb.append("            this.color = color\n")
        sb.append("            style = Paint.Style.STROKE\n")
        sb.append("            this.strokeWidth = strokeWidth\n")
        sb.append("            strokeCap = Paint.Cap.ROUND\n")
        sb.append("            strokeJoin = Paint.Join.ROUND\n")
        sb.append("            isDither = true\n")
        sb.append("        }\n\n")
        sb.append("        private val path = Path()\n\n")
        sb.append("        fun addPoint(x: Float, y: Float) {\n")
        sb.append("            if (path.isEmpty) {\n")
        sb.append("                path.moveTo(x, y)\n")
        sb.append("            } else {\n")
        sb.append("                path.lineTo(x, y)\n")
        sb.append("            }\n")
        sb.append("        }\n\n")
        sb.append("        fun draw(canvas: Canvas) {\n")
        sb.append("            canvas.drawPath(path, paint)\n")
        sb.append("        }\n")
        sb.append("    }\n")

        // ============================================================
        // End of class
        // ============================================================
        sb.append("}\n")

        return sb.toString()
    }

    // ============================================================
    // أدوات مساعدة
    // ============================================================
    private fun xmlEscape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun jsonEscape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }
}