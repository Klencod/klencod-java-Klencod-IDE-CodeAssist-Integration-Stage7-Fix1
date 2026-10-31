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
 * JavaTemplate — يولّد مشروع Android Java كامل من الصفر.
 *
 * v2.2 — القالب الرسمي: لوحة رسم بسيطة
 *
 * الميزات المُنتجة:
 *   - رسم بالإصبع (Canvas + Path)
 *   - 8 ألوان + 3 سماكات فرشاة
 *   - تراجع + مسح
 *   - حفظ PNG في Pictures/KlencodDrawings (Android 10+)
 *   - كل المنطق في MainActivity.java واحد
 *   - أيقونة: 3 دوائر ملوّنة (مثلث)
 */
object JavaTemplate {

    private const val DEFAULT_PACKAGE = "com.klencod.local.project"
    private const val DEFAULT_APP_NAME = "لوحة الرسم"
    private const val DEFAULT_MIN_SDK = 24
    private const val DEFAULT_TARGET_SDK = 34
    private const val DEFAULT_COMPILE_SDK = 34
    private const val AGP_VERSION = "8.5.2"

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
            val javaDir = File(srcMain, "java/$packagePath")
            val resValues = File(srcMain, "res/values")
            val resDrawable = File(srcMain, "res/drawable")
            val assetsDir = File(srcMain, "assets")
            val platformDir = File(projectDir, ".platform")
            val workflowsDir = File(projectDir, ".github/workflows")

            listOf(
                appDir, srcMain, javaDir, resValues, resDrawable,
                assetsDir, platformDir, workflowsDir
            ).forEach { dir ->
                if (!dir.exists() && !dir.mkdirs()) return false
            }

            // ============================================================
            // 2) module.toml
            // ============================================================
            writeFile(
                File(appDir, "module.toml"),
                buildModuleToml(safePackage, safeMinSdk, safeTargetSdk, DEFAULT_COMPILE_SDK)
            )

            // ============================================================
            // 3) AndroidManifest.xml
            // ============================================================
            writeFile(
                File(srcMain, "AndroidManifest.xml"),
                buildAndroidManifest()
            )

            // ============================================================
            // 4) MainActivity.java — لوحة الرسم
            // ============================================================
            writeFile(
                File(javaDir, "MainActivity.java"),
                buildMainActivityJava(safePackage)
            )

            // ============================================================
            // 5) res/values/strings.xml
            // ============================================================
            writeFile(
                File(resValues, "strings.xml"),
                buildStringsXml(safeAppName)
            )

            // ============================================================
            // 6) res/drawable/app_icon.png — 3 دوائر ملوّنة
            // ============================================================
            writeDefaultIconPng(resDrawable)

            // ============================================================
            // 7) .platform/workspace.json
            // ============================================================
            writeFile(
                File(platformDir, "workspace.json"),
                buildWorkspaceJson(appNameSnake)
            )

            // ============================================================
            // 8) .gitignore
            // ============================================================
            writeFile(
                File(projectDir, ".gitignore"),
                buildGitignore()
            )

            // ============================================================
            // 9) README.md
            // ============================================================
            writeFile(
                File(projectDir, "README.md"),
                buildReadme(safeAppName)
            )

            // ============================================================
            // 10) Gradle files
            // ============================================================
            writeFile(File(projectDir, "build.gradle"), buildRootBuildGradle())
            writeFile(File(projectDir, "settings.gradle"), buildSettingsGradle(appNameSnake))
            writeFile(File(projectDir, "gradle.properties"), buildGradleProperties())
            writeFile(File(appDir, "build.gradle"), buildAppBuildGradle(safePackage, safeTargetSdk))

            // ============================================================
            // 11) GitHub Actions workflow
            // ============================================================
            writeFile(
                File(workflowsDir, "build-android.yml"),
                JavaBuildWorkflow.build(safeTargetSdk)
            )

            // ============================================================
            // 12) Gradle Wrapper
            // ============================================================
            GradleWrapperWriter.ensureGradleWrapper(context, projectDir)

            true
        } catch (_: Exception) {
            false
        }
    }

    // ============================================================
    // أيقونة التطبيق — 3 دوائر ملوّنة (مثلث)
    //
    // التصميم:
    //   - خلفية زرقاء (KlencodIDE blue)
    //   - 🟡 أصفر (أعلى)
    //   - 🔴 أحمر (أسفل يسار)
    //   - 🟢 أخضر (أسفل يمين)
    //   - حلقات بيضاء رقيقة حول كل دائرة
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

            // 1) خلفية زرقاء
            canvas.drawColor(0xFF1F6FEB.toInt())

            // 2) إعداد الفرشاة
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = s * 0.012f
                color = 0xFFFFFFFF.toInt()
            }

            val radius = s * 0.165f

            // 3) 🟡 أصفر — أعلى
            val topX = s * 0.50f
            val topY = s * 0.33f
            fillPaint.color = 0xFFFFD33D.toInt()
            canvas.drawCircle(topX, topY, radius, fillPaint)
            canvas.drawCircle(topX, topY, radius, ringPaint)

            // 4) 🔴 أحمر — أسفل يسار
            val leftX = s * 0.30f
            val bottomY = s * 0.68f
            fillPaint.color = 0xFFFF7B72.toInt()
            canvas.drawCircle(leftX, bottomY, radius, fillPaint)
            canvas.drawCircle(leftX, bottomY, radius, ringPaint)

            // 5) 🟢 أخضر — أسفل يمين
            val rightX = s * 0.70f
            fillPaint.color = 0xFF7EE787.toInt()
            canvas.drawCircle(rightX, bottomY, radius, fillPaint)
            canvas.drawCircle(rightX, bottomY, radius, ringPaint)

            // 6) حفظ
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
    // module.toml
    // ============================================================
    private fun buildModuleToml(
        packageName: String,
        minSdk: Int,
        targetSdk: Int,
        compileSdk: Int
    ): String {
        return """
            |version = 2
            |
            |[module]
            |type = "android-app"
            |name = "app"
            |languageLevel = "JAVA_8"
            |output = "build/classes"
            |
            |[sourceSets.main]
            |scope = "IMPLEMENTATION"
            |assets = ["src/main/assets"]
            |java = ["src/main/java"]
            |res = ["src/main/res"]
            |
            |[android]
            |namespace = "$packageName"
            |compileSdk = $compileSdk
            |minSdk = $minSdk
            |targetSdk = $targetSdk
            |manifest = "src/main/AndroidManifest.xml"
            |versionCode = 1
            |versionName = "1.0"
            |isApplication = true
        """.trimMargin() + "\n"
    }

    // ============================================================
    // AndroidManifest.xml
    // ============================================================
    private fun buildAndroidManifest(): String {
        return """
            |<?xml version="1.0" encoding="utf-8"?>
            |<manifest xmlns:android="http://schemas.android.com/apk/res/android">
            |
            |    <application
            |        android:allowBackup="true"
            |        android:icon="@drawable/app_icon"
            |        android:label="@string/app_name"
            |        android:roundIcon="@drawable/app_icon"
            |        android:supportsRtl="true"
            |        android:theme="@android:style/Theme.Material.NoActionBar">
            |
            |        <activity
            |            android:name=".MainActivity"
            |            android:exported="true"
            |            android:screenOrientation="portrait">
            |            <intent-filter>
            |                <action android:name="android.intent.action.MAIN" />
            |                <category android:name="android.intent.category.LAUNCHER" />
            |            </intent-filter>
            |        </activity>
            |
            |    </application>
            |</manifest>
        """.trimMargin() + "\n"
    }

    // ============================================================
    // strings.xml
    // ============================================================
    private fun buildStringsXml(appName: String): String {
        return """
            |<?xml version="1.0" encoding="utf-8"?>
            |<resources>
            |    <string name="app_name">${xmlEscape(appName)}</string>
            |</resources>
        """.trimMargin() + "\n"
    }

    // ============================================================
    // workspace.json
    // ============================================================
    private fun buildWorkspaceJson(projectName: String): String {
        return """
            |{
            |  "buildSystem": "native",
            |  "language": "java",
            |  "projectName": "${jsonEscape(projectName)}"
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // .gitignore
    // ============================================================
    private fun buildGitignore(): String {
        return """
            |.gradle/
            |**/build/
            |local.properties
            |*.iml
            |.idea/
            |*.apk
            |*.aab
            |.platform/
            |.klencod/
            |.ds_store
        """.trimMargin() + "\n"
    }

    // ============================================================
    // README.md
    // ============================================================
    private fun buildReadme(appName: String): String {
        return """
            |# $appName
            |
            |مشروع Android Java أُنشئ بـ KlencodIDE.
            |
            |## القالب: لوحة رسم بسيطة 🎨
            |
            |### الميزات
            |- رسم بالإصبع (Canvas + Path)
            |- 8 ألوان + 3 سماكات فرشاة
            |- تراجع (Undo) + مسح الكل
            |- حفظ PNG في Pictures/KlencodDrawings (Android 10+)
            |- كل المنطق في ملف MainActivity.java واحد
            |- لا مكتبات خارجية
            |
            |### البناء
            |
            |يتم البناء تلقائياً على GitHub Actions عند الضغط على "بناء APK" من داخل التطبيق.
            |
            |### الهيكل
            |- `app/src/main/java/` — كود Java (MainActivity.java فقط)
            |- `app/src/main/res/` — الموارد (strings.xml + app_icon.png)
            |- `app/src/main/AndroidManifest.xml` — بيان التطبيق
            |- `app/module.toml` — إعدادات الوحدة
            |
            |### التوسيعات المقترحة
            |- 🧹 ممحاة (Eraser) — بتغيير Paint xfermode
            |- ↪️ إعادة (Redo) — بمكدس ثانٍ
            |- 📤 مشاركة الصورة — عبر Intent.ACTION_SEND
            |- 🎨 منتقي ألوان HSL
            |- 🌐 شبكة خلفية
        """.trimMargin() + "\n"
    }

    // ============================================================
    // Gradle — root
    // ============================================================
    private fun buildRootBuildGradle(): String {
        return """
            |// Root build.gradle
            |// ملاحظة: repositories معرّفة في settings.gradle فقط.
            |
            |plugins {
            |    id 'com.android.application' version '$AGP_VERSION' apply false
            |    id 'com.android.library' version '$AGP_VERSION' apply false
            |}
            |
            |subprojects {
            |    project.evaluationDependsOn(":app")
            |}
            |
            |tasks.register("clean", Delete) {
            |    delete rootProject.buildDir
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // Gradle — settings
    // ============================================================
    private fun buildSettingsGradle(projectName: String): String {
        return """
            |pluginManagement {
            |    repositories {
            |        google()
            |        mavenCentral()
            |        gradlePluginPortal()
            |    }
            |}
            |
            |plugins {
            |    id 'com.android.application' version '$AGP_VERSION' apply false
            |    id 'com.android.library' version '$AGP_VERSION' apply false
            |}
            |
            |dependencyResolutionManagement {
            |    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            |    repositories {
            |        google()
            |        mavenCentral()
            |    }
            |}
            |
            |rootProject.name = "$projectName"
            |include ":app"
        """.trimMargin() + "\n"
    }

    // ============================================================
    // Gradle — properties
    // ============================================================
    private fun buildGradleProperties(): String {
        return """
            |org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            |android.useAndroidX=true
            |android.enableJetifier=true
            |android.nonTransitiveRClass=true
        """.trimMargin() + "\n"
    }

    // ============================================================
    // Gradle — app module
    // ============================================================
    private fun buildAppBuildGradle(packageName: String, targetSdk: Int): String {
        return """
            |plugins {
            |    id 'com.android.application'
            |}
            |
            |android {
            |    namespace "$packageName"
            |    compileSdk 34
            |
            |    defaultConfig {
            |        applicationId "$packageName"
            |        minSdk 24
            |        targetSdk $targetSdk
            |        versionCode 1
            |        versionName "1.0"
            |    }
            |
            |    buildTypes {
            |        debug {
            |            minifyEnabled false
            |        }
            |        release {
            |            minifyEnabled false
            |            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            |        }
            |    }
            |
            |    compileOptions {
            |        sourceCompatibility JavaVersion.VERSION_17
            |        targetCompatibility JavaVersion.VERSION_17
            |    }
            |
            |    sourceSets {
            |        main {
            |            java.srcDirs = ['src/main/java']
            |            res.srcDirs = ['src/main/res']
            |            assets.srcDirs = ['src/main/assets']
            |        }
            |    }
            |}
            |
            |dependencies {
            |    implementation fileTree(dir: 'libs', include: ['*.jar', '*.aar'])
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // MainActivity.java — لوحة الرسم
    // ============================================================
    private fun buildMainActivityJava(packageName: String): String {
        return """
            |package $packageName;
            |
            |import android.app.Activity;
            |import android.app.AlertDialog;
            |import android.content.ContentValues;
            |import android.graphics.Bitmap;
            |import android.graphics.Canvas;
            |import android.graphics.Color;
            |import android.graphics.Paint;
            |import android.graphics.Path;
            |import android.graphics.drawable.GradientDrawable;
            |import android.net.Uri;
            |import android.os.Build;
            |import android.os.Bundle;
            |import android.os.Environment;
            |import android.provider.MediaStore;
            |import android.view.Gravity;
            |import android.view.MotionEvent;
            |import android.view.View;
            |import android.widget.Button;
            |import android.widget.FrameLayout;
            |import android.widget.LinearLayout;
            |import android.widget.TextView;
            |import android.widget.Toast;
            |
            |import java.io.OutputStream;
            |import java.text.SimpleDateFormat;
            |import java.util.ArrayList;
            |import java.util.Date;
            |import java.util.Locale;
            |
            |/**
            | * لوحة رسم بسيطة — قالب KlencodIDE
            | *
            | * الميزات:
            | *   - رسم بالإصبع (Canvas + Path)
            | *   - 8 ألوان
            | *   - 3 سماكات فرشاة
            | *   - تراجع (Undo)
            | *   - مسح الكل
            | *   - حفظ PNG في Pictures/KlencodDrawings (Android 10+)
            | *
            | * © 2026 KlencodIDE
            | */
            |public class MainActivity extends Activity {
            |
            |    // ============================================================
            |    // الألوان الأساسية (نفس ألوان KlencodIDE)
            |    // ============================================================
            |    private static final int COLOR_BG = 0xFF0D1117;
            |    private static final int COLOR_SURFACE = 0xFF161B22;
            |    private static final int COLOR_SURFACE_ALT = 0xFF21262D;
            |    private static final int COLOR_PRIMARY = 0xFF1F6FEB;
            |    private static final int COLOR_PRIMARY_LIGHT = 0xFF58A6FF;
            |    private static final int COLOR_TEXT = 0xFFE6EDF3;
            |    private static final int COLOR_MUTED = 0xFF8B949E;
            |    private static final int COLOR_BORDER = 0xFF30363D;
            |
            |    // 8 ألوان للرسم
            |    private static final int[] PALETTE = {
            |            0xFFFFFFFF, // أبيض
            |            0xFF000000, // أسود
            |            0xFFFF7B72, // أحمر
            |            0xFF7EE787, // أخضر
            |            0xFF58A6FF, // أزرق
            |            0xFFD2A8FF, // بنفسجي
            |            0xFFFFD33D, // أصفر
            |            0xFFFF9E64  // برتقالي
            |    };
            |
            |    // 3 سماكات
            |    private static final float[] BRUSH_SIZES = {4f, 10f, 22f};
            |
            |    // ============================================================
            |    // الحالة
            |    // ============================================================
            |    private DrawingView drawingView;
            |    private int currentColorIndex = 2;  // أحمر افتراضي
            |    private int currentSizeIndex = 1;   // متوسط افتراضي
            |    private final ArrayList<Button> colorButtons = new ArrayList<>();
            |    private final ArrayList<Button> sizeButtons = new ArrayList<>();
            |
            |    // ============================================================
            |    // dp helper
            |    // ============================================================
            |    private int dp(int value) {
            |        return Math.round(value * getResources().getDisplayMetrics().density);
            |    }
            |
            |    // ============================================================
            |    // onCreate
            |    // ============================================================
            |    @Override
            |    protected void onCreate(Bundle state) {
            |        super.onCreate(state);
            |
            |        getWindow().setStatusBarColor(COLOR_BG);
            |        getWindow().setNavigationBarColor(COLOR_BG);
            |
            |        // Root
            |        LinearLayout root = new LinearLayout(this);
            |        root.setOrientation(LinearLayout.VERTICAL);
            |        root.setBackgroundColor(COLOR_BG);
            |
            |        // ============================================================
            |        // الترويسة العلوية
            |        // ============================================================
            |        LinearLayout header = new LinearLayout(this);
            |        header.setOrientation(LinearLayout.HORIZONTAL);
            |        header.setGravity(Gravity.CENTER_VERTICAL);
            |        header.setPadding(dp(14), dp(10), dp(14), dp(10));
            |        header.setBackgroundColor(COLOR_SURFACE);
            |
            |        TextView title = new TextView(this);
            |        title.setText("🎨 لوحة الرسم");
            |        title.setTextColor(COLOR_TEXT);
            |        title.setTextSize(17);
            |        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            |        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            |
            |        Button undoBtn = makeTopButton("↶ تراجع");
            |        undoBtn.setOnClickListener(v -> drawingView.undo());
            |        header.addView(undoBtn, new LinearLayout.LayoutParams(-2, dp(40)));
            |
            |        Button clearBtn = makeTopButton("🗑 مسح");
            |        clearBtn.setOnClickListener(v -> confirmClear());
            |        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(-2, dp(40));
            |        clearParams.leftMargin = dp(6);
            |        header.addView(clearBtn, clearParams);
            |
            |        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
            |
            |        // ============================================================
            |        // منطقة الرسم
            |        // ============================================================
            |        FrameLayout drawContainer = new FrameLayout(this);
            |        drawContainer.setBackgroundColor(Color.WHITE);
            |
            |        drawingView = new DrawingView(this);
            |        drawContainer.addView(drawingView, new FrameLayout.LayoutParams(-1, -1));
            |
            |        LinearLayout.LayoutParams drawParams = new LinearLayout.LayoutParams(-1, 0, 1f);
            |        drawParams.setMargins(dp(12), dp(12), dp(12), dp(12));
            |        root.addView(drawContainer, drawParams);
            |
            |        // ============================================================
            |        // شريط التحكم السفلي
            |        // ============================================================
            |        LinearLayout controls = new LinearLayout(this);
            |        controls.setOrientation(LinearLayout.VERTICAL);
            |        controls.setPadding(dp(12), dp(10), dp(12), dp(12));
            |        controls.setBackgroundColor(COLOR_SURFACE);
            |
            |        // --- صف الألوان ---
            |        TextView colorLabel = new TextView(this);
            |        colorLabel.setText("اللون:");
            |        colorLabel.setTextColor(COLOR_MUTED);
            |        colorLabel.setTextSize(12);
            |        controls.addView(colorLabel);
            |
            |        LinearLayout colorRow = new LinearLayout(this);
            |        colorRow.setOrientation(LinearLayout.HORIZONTAL);
            |        colorRow.setGravity(Gravity.CENTER_VERTICAL);
            |        LinearLayout.LayoutParams colorRowParams = new LinearLayout.LayoutParams(-1, dp(50));
            |        colorRowParams.topMargin = dp(6);
            |        controls.addView(colorRow, colorRowParams);
            |
            |        for (int i = 0; i < PALETTE.length; i++) {
            |            final int index = i;
            |            Button colorBtn = new Button(this);
            |            colorBtn.setBackground(makeCircleDrawable(PALETTE[i]));
            |            colorBtn.setPadding(0, 0, 0, 0);
            |            colorBtn.setOnClickListener(v -> selectColor(index));
            |
            |            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            |            cp.setMargins(dp(3), 0, dp(3), 0);
            |            colorRow.addView(colorBtn, cp);
            |            colorButtons.add(colorBtn);
            |        }
            |
            |        // --- صف السماكات ---
            |        TextView sizeLabel = new TextView(this);
            |        sizeLabel.setText("سماكة الفرشاة:");
            |        sizeLabel.setTextColor(COLOR_MUTED);
            |        sizeLabel.setTextSize(12);
            |        LinearLayout.LayoutParams sizeLabelParams = new LinearLayout.LayoutParams(-1, -2);
            |        sizeLabelParams.topMargin = dp(12);
            |        controls.addView(sizeLabel, sizeLabelParams);
            |
            |        LinearLayout sizeRow = new LinearLayout(this);
            |        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
            |        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
            |        LinearLayout.LayoutParams sizeRowParams = new LinearLayout.LayoutParams(-1, dp(46));
            |        sizeRowParams.topMargin = dp(6);
            |        controls.addView(sizeRow, sizeRowParams);
            |
            |        String[] sizeNames = {"رفيع", "متوسط", "عريض"};
            |        for (int i = 0; i < BRUSH_SIZES.length; i++) {
            |            final int index = i;
            |            Button sizeBtn = new Button(this);
            |            sizeBtn.setText(sizeNames[i]);
            |            sizeBtn.setTextSize(12);
            |            sizeBtn.setAllCaps(false);
            |            sizeBtn.setPadding(0, 0, 0, 0);
            |            sizeBtn.setOnClickListener(v -> selectSize(index));
            |
            |            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            |            sp.setMargins(dp(4), 0, dp(4), 0);
            |            sizeRow.addView(sizeBtn, sp);
            |            sizeButtons.add(sizeBtn);
            |        }
            |
            |        // --- صف الحفظ ---
            |        Button saveBtn = new Button(this);
            |        saveBtn.setText("💾 حفظ الصورة");
            |        saveBtn.setTextSize(14);
            |        saveBtn.setAllCaps(false);
            |        saveBtn.setTextColor(Color.WHITE);
            |        saveBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            |        GradientDrawable saveBg = new GradientDrawable();
            |        saveBg.setColor(0xFF238636);
            |        saveBg.setCornerRadius(dp(12));
            |        saveBg.setStroke(dp(1), 0xFF56D364);
            |        saveBtn.setBackground(saveBg);
            |        saveBtn.setElevation(dp(2));
            |        saveBtn.setOnClickListener(v -> saveImage());
            |
            |        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(50));
            |        saveParams.topMargin = dp(14);
            |        controls.addView(saveBtn, saveParams);
            |
            |        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
            |
            |        setContentView(root);
            |
            |        // الاختيارات الافتراضية
            |        selectColor(currentColorIndex);
            |        selectSize(currentSizeIndex);
            |    }
            |
            |    // ============================================================
            |    // دوال مساعدة للواجهة
            |    // ============================================================
            |    private Button makeTopButton(String text) {
            |        Button btn = new Button(this);
            |        btn.setText(text);
            |        btn.setTextSize(12);
            |        btn.setAllCaps(false);
            |        btn.setTextColor(COLOR_TEXT);
            |        btn.setPadding(dp(12), 0, dp(12), 0);
            |        GradientDrawable bg = new GradientDrawable();
            |        bg.setColor(COLOR_SURFACE_ALT);
            |        bg.setCornerRadius(dp(10));
            |        bg.setStroke(dp(1), COLOR_BORDER);
            |        btn.setBackground(bg);
            |        return btn;
            |    }
            |
            |    private GradientDrawable makeCircleDrawable(int color) {
            |        GradientDrawable d = new GradientDrawable();
            |        d.setShape(GradientDrawable.OVAL);
            |        d.setColor(color);
            |        d.setStroke(dp(2), COLOR_BORDER);
            |        return d;
            |    }
            |
            |    private GradientDrawable makeSelectedCircleDrawable(int color) {
            |        GradientDrawable d = new GradientDrawable();
            |        d.setShape(GradientDrawable.OVAL);
            |        d.setColor(color);
            |        d.setStroke(dp(3), COLOR_PRIMARY_LIGHT);
            |        return d;
            |    }
            |
            |    private GradientDrawable makeSizeButtonDrawable(boolean selected) {
            |        GradientDrawable d = new GradientDrawable();
            |        d.setCornerRadius(dp(10));
            |        d.setColor(selected ? COLOR_PRIMARY : COLOR_SURFACE_ALT);
            |        d.setStroke(dp(1), selected ? COLOR_PRIMARY_LIGHT : COLOR_BORDER);
            |        return d;
            |    }
            |
            |    private void selectColor(int index) {
            |        currentColorIndex = index;
            |        drawingView.setColor(PALETTE[index]);
            |
            |        for (int i = 0; i < colorButtons.size(); i++) {
            |            Button b = colorButtons.get(i);
            |            if (i == index) {
            |                b.setBackground(makeSelectedCircleDrawable(PALETTE[i]));
            |            } else {
            |                b.setBackground(makeCircleDrawable(PALETTE[i]));
            |            }
            |        }
            |    }
            |
            |    private void selectSize(int index) {
            |        currentSizeIndex = index;
            |        drawingView.setStrokeWidth(BRUSH_SIZES[index]);
            |
            |        for (int i = 0; i < sizeButtons.size(); i++) {
            |            Button b = sizeButtons.get(i);
            |            b.setBackground(makeSizeButtonDrawable(i == index));
            |            b.setTextColor(i == index ? Color.WHITE : COLOR_TEXT);
            |        }
            |    }
            |
            |    // ============================================================
            |    // حفظ الصورة
            |    // ============================================================
            |    private void saveImage() {
            |        try {
            |            Bitmap bitmap = drawingView.exportBitmap();
            |            if (bitmap == null) {
            |                Toast.makeText(this, "لا يوجد شيء لحفظه", Toast.LENGTH_SHORT).show();
            |                return;
            |            }
            |
            |            String timestamp = new SimpleDateFormat(
            |                    "yyyyMMdd_HHmmss", Locale.US).format(new Date());
            |            String fileName = "drawing_" + timestamp + ".png";
            |
            |            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            |                ContentValues values = new ContentValues();
            |                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            |                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            |                values.put(MediaStore.Images.Media.RELATIVE_PATH,
            |                        Environment.DIRECTORY_PICTURES + "/KlencodDrawings");
            |
            |                Uri uri = getContentResolver().insert(
            |                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            |
            |                if (uri == null) {
            |                    Toast.makeText(this, "تعذر إنشاء ملف", Toast.LENGTH_SHORT).show();
            |                    return;
            |                }
            |
            |                OutputStream out = getContentResolver().openOutputStream(uri);
            |                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            |                if (out != null) out.close();
            |                bitmap.recycle();
            |
            |                Toast.makeText(this,
            |                        "✅ تم حفظ الصورة في:\nPictures/KlencodDrawings/" + fileName,
            |                        Toast.LENGTH_LONG).show();
            |            } else {
            |                Toast.makeText(this,
            |                        "يحتاج Android 10+ للحفظ",
            |                        Toast.LENGTH_LONG).show();
            |            }
            |        } catch (Exception e) {
            |            Toast.makeText(this, "خطأ: " + e.getMessage(), Toast.LENGTH_LONG).show();
            |        }
            |    }
            |
            |    private void confirmClear() {
            |        new AlertDialog.Builder(this)
            |                .setTitle("مسح اللوحة")
            |                .setMessage("هل أنت متأكد من مسح كل الرسم؟")
            |                .setPositiveButton("مسح", (d, w) -> drawingView.clear())
            |                .setNegativeButton("إلغاء", null)
            |                .show();
            |    }
            |
            |    // ============================================================
            |    // DrawingView — Canvas مخصص
            |    // ============================================================
            |    private static class DrawingView extends View {
            |
            |        private final ArrayList<Stroke> strokes = new ArrayList<>();
            |        private Stroke currentStroke;
            |
            |        private int currentColor = Color.RED;
            |        private float currentStrokeWidth = 10f;
            |
            |        public DrawingView(Activity context) {
            |            super(context);
            |            setFocusable(true);
            |            setBackgroundColor(Color.WHITE);
            |        }
            |
            |        public void setColor(int color) {
            |            this.currentColor = color;
            |        }
            |
            |        public void setStrokeWidth(float width) {
            |            this.currentStrokeWidth = width;
            |        }
            |
            |        public void clear() {
            |            strokes.clear();
            |            currentStroke = null;
            |            invalidate();
            |        }
            |
            |        public void undo() {
            |            if (strokes.isEmpty()) return;
            |            strokes.remove(strokes.size() - 1);
            |            invalidate();
            |        }
            |
            |        public Bitmap exportBitmap() {
            |            if (getWidth() <= 0 || getHeight() <= 0) return null;
            |            Bitmap bmp = Bitmap.createBitmap(
            |                    getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            |            Canvas canvas = new Canvas(bmp);
            |            canvas.drawColor(Color.WHITE);
            |
            |            for (Stroke stroke : strokes) {
            |                stroke.draw(canvas);
            |            }
            |            if (currentStroke != null) {
            |                currentStroke.draw(canvas);
            |            }
            |            return bmp;
            |        }
            |
            |        @Override
            |        protected void onDraw(Canvas canvas) {
            |            super.onDraw(canvas);
            |
            |            for (Stroke stroke : strokes) {
            |                stroke.draw(canvas);
            |            }
            |
            |            if (currentStroke != null) {
            |                currentStroke.draw(canvas);
            |            }
            |        }
            |
            |        @Override
            |        public boolean onTouchEvent(MotionEvent event) {
            |            float x = event.getX();
            |            float y = event.getY();
            |
            |            switch (event.getAction()) {
            |                case MotionEvent.ACTION_DOWN:
            |                    currentStroke = new Stroke(currentColor, currentStrokeWidth);
            |                    currentStroke.addPoint(x, y);
            |                    invalidate();
            |                    return true;
            |
            |                case MotionEvent.ACTION_MOVE:
            |                    if (currentStroke != null) {
            |                        currentStroke.addPoint(x, y);
            |                        invalidate();
            |                    }
            |                    return true;
            |
            |                case MotionEvent.ACTION_UP:
            |                case MotionEvent.ACTION_CANCEL:
            |                    if (currentStroke != null) {
            |                        strokes.add(currentStroke);
            |                        currentStroke = null;
            |                        invalidate();
            |                    }
            |                    return true;
            |            }
            |            return super.onTouchEvent(event);
            |        }
            |    }
            |
            |    // ============================================================
            |    // Stroke — ضربة واحدة (مسار واحد)
            |    // ============================================================
            |    private static class Stroke {
            |
            |        private final Paint paint;
            |        private final Path path;
            |
            |        public Stroke(int color, float strokeWidth) {
            |            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            |            paint.setColor(color);
            |            paint.setStyle(Paint.Style.STROKE);
            |            paint.setStrokeWidth(strokeWidth);
            |            paint.setStrokeCap(Paint.Cap.ROUND);
            |            paint.setStrokeJoin(Paint.Join.ROUND);
            |            paint.setDither(true);
            |
            |            path = new Path();
            |        }
            |
            |        public void addPoint(float x, float y) {
            |            if (path.isEmpty()) {
            |                path.moveTo(x, y);
            |            } else {
            |                path.lineTo(x, y);
            |            }
            |        }
            |
            |        public void draw(Canvas canvas) {
            |            canvas.drawPath(path, paint);
            |        }
            |    }
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // أدوات
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