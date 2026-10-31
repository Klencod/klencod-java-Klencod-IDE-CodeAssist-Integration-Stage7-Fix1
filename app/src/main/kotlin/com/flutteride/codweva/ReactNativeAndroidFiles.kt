package com.flutteride.codweva.reactnative

import java.io.File
import java.io.FileWriter
import java.io.IOException

/**
 * ReactNativeAndroidFiles — يولّد كل ملفات android/ لمشروع React Native CLI.
 *
 * v1.1 — إصلاحات NDK:
 *   - إزالة ndkVersion من android/build.gradle
 *   - تعطيل newArchEnabled في gradle.properties
 *   - بدون ndkVersion في android/app/build.gradle
 *
 * النتيجة: البناء يعمل بدون تنزيل NDK (فشل التنزيل كان يعطّل البناء).
 */
object ReactNativeAndroidFiles {

    // ============================================================
    // إصدارات
    // ============================================================
    private const val BUILD_TOOLS_VERSION = "35.0.0"
    private const val KOTLIN_VERSION = "1.9.25"
    private const val GRADLE_VERSION = "8.10.2"

    // ============================================================
    // الدالة الرئيسية
    // ============================================================
    @JvmStatic
    fun writeAll(
        projectDir: File,
        packageName: String,
        appName: String,
        projectName: String,
        minSdk: Int,
        compileSdk: Int,
        targetSdk: Int
    ) {
        val androidDir = File(projectDir, "android")
        val appDir = File(androidDir, "app")
        val srcMainDir = File(appDir, "src/main")
        val srcDebugDir = File(appDir, "src/debug")
        val javaDir = File(srcMainDir, "java/${packageName.replace('.', '/')}")
        val resValuesDir = File(srcMainDir, "res/values")
        val gradleWrapperDir = File(androidDir, "gradle/wrapper")

        // ضمان المجلدات
        listOf(
            androidDir, appDir, srcMainDir, srcDebugDir, javaDir,
            resValuesDir, gradleWrapperDir
        ).forEach { dir ->
            if (!dir.exists()) dir.mkdirs()
        }

        // ============================================================
        // 1) gradle files
        // ============================================================
        writeFile(
            File(androidDir, "build.gradle"),
            buildRootGradle(minSdk, compileSdk, targetSdk)
        )
        writeFile(
            File(androidDir, "settings.gradle"),
            buildSettingsGradle(projectName)
        )
        writeFile(
            File(androidDir, "gradle.properties"),
            buildGradleProperties()
        )
        writeFile(
            File(gradleWrapperDir, "gradle-wrapper.properties"),
            buildGradleWrapperProperties()
        )
        writeFile(
            File(appDir, "build.gradle"),
            buildAppGradle(packageName)
        )
        writeFile(
            File(appDir, "proguard-rules.pro"),
            buildProguardRules()
        )

        // ============================================================
        // 2) Manifests
        // ============================================================
        writeFile(
            File(srcMainDir, "AndroidManifest.xml"),
            buildMainManifest()
        )
        writeFile(
            File(srcDebugDir, "AndroidManifest.xml"),
            buildDebugManifest()
        )

        // ============================================================
        // 3) MainActivity.kt + MainApplication.kt
        // ============================================================
        writeFile(
            File(javaDir, "MainActivity.kt"),
            buildMainActivity(packageName, projectName)
        )
        writeFile(
            File(javaDir, "MainApplication.kt"),
            buildMainApplication(packageName)
        )

        // ============================================================
        // 4) res/values
        // ============================================================
        writeFile(
            File(resValuesDir, "strings.xml"),
            buildStringsXml(appName)
        )
        writeFile(
            File(resValuesDir, "styles.xml"),
            buildStylesXml()
        )
    }

    // ============================================================
    // android/build.gradle (root) — بدون ndkVersion
    // ============================================================
    private fun buildRootGradle(
        minSdk: Int,
        compileSdk: Int,
        targetSdk: Int
    ): String {
        return """
            |buildscript {
            |    ext {
            |        buildToolsVersion = "$BUILD_TOOLS_VERSION"
            |        minSdkVersion = $minSdk
            |        compileSdkVersion = $compileSdk
            |        targetSdkVersion = $targetSdk
            |        kotlinVersion = "$KOTLIN_VERSION"
            |    }
            |    repositories {
            |        google()
            |        mavenCentral()
            |    }
            |    dependencies {
            |        classpath("com.android.tools.build:gradle")
            |        classpath("com.facebook.react:react-native-gradle-plugin")
            |        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin")
            |    }
            |}
            |
            |apply plugin: "com.facebook.react.rootproject"
        """.trimMargin() + "\n"
    }

    // ============================================================
    // android/settings.gradle
    // ============================================================
    private fun buildSettingsGradle(projectName: String): String {
        return """
            |pluginManagement { includeBuild("../node_modules/@react-native/gradle-plugin") }
            |plugins { id("com.facebook.react.settings") }
            |extensions.configure(com.facebook.react.ReactSettingsExtension){ ex -> ex.autolinkLibrariesFromCommand() }
            |rootProject.name = '$projectName'
            |include ':app'
            |includeBuild('../node_modules/@react-native/gradle-plugin')
        """.trimMargin() + "\n"
    }

    // ============================================================
    // android/gradle.properties — newArchEnabled=false
    // ============================================================
    private fun buildGradleProperties(): String {
        return """
            |# ============================================================
            |# React Native Gradle Properties
            |# ============================================================
            |
            |# JVM args
            |org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
            |
            |# AndroidX
            |android.useAndroidX=true
            |
            |# Hermes (JS Engine)
            |hermesEnabled=true
            |
            |# New Architecture (Fabric + TurboModules)
            |# معطّلة لتجنب الحاجة إلى NDK
            |newArchEnabled=false
            |
            |# Kotlin
            |kotlin.code.style=official
        """.trimMargin() + "\n"
    }

    // ============================================================
    // android/gradle/wrapper/gradle-wrapper.properties
    // ============================================================
    private fun buildGradleWrapperProperties(): String {
        return """
            |distributionBase=GRADLE_USER_HOME
            |distributionPath=wrapper/dists
            |distributionUrl=https\://services.gradle.org/distributions/gradle-$GRADLE_VERSION-all.zip
            |networkTimeout=10000
            |validateDistributionUrl=true
            |zipStoreBase=GRADLE_USER_HOME
            |zipStorePath=wrapper/dists
        """.trimMargin() + "\n"
    }

    // ============================================================
    // android/app/build.gradle — بدون ndkVersion
    // ============================================================
    private fun buildAppGradle(packageName: String): String {
        return """
            |apply plugin: "com.android.application"
            |apply plugin: "org.jetbrains.kotlin.android"
            |apply plugin: "com.facebook.react"
            |
            |react {
            |    autolinkLibrariesWithApp()
            |}
            |
            |def enableProguardInReleaseBuilds = false
            |def jscFlavor = 'org.webkit:android-jsc:+'
            |
            |android {
            |    buildToolsVersion rootProject.ext.buildToolsVersion
            |    compileSdk rootProject.ext.compileSdkVersion
            |
            |    namespace "$packageName"
            |    defaultConfig {
            |        applicationId "$packageName"
            |        minSdkVersion rootProject.ext.minSdkVersion
            |        targetSdkVersion rootProject.ext.targetSdkVersion
            |        versionCode 1
            |        versionName "1.0"
            |    }
            |    signingConfigs {
            |        debug {
            |            storeFile file('debug.keystore')
            |            storePassword 'android'
            |            keyAlias 'androiddebugkey'
            |            keyPassword 'android'
            |        }
            |    }
            |    buildTypes {
            |        debug {
            |            signingConfig signingConfigs.debug
            |        }
            |        release {
            |            signingConfig signingConfigs.debug
            |            minifyEnabled enableProguardInReleaseBuilds
            |            proguardFiles getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"
            |        }
            |    }
            |}
            |
            |dependencies {
            |    implementation("com.facebook.react:react-android")
            |    implementation("androidx.appcompat:appcompat:1.7.0")
            |    if (hermesEnabled.toBoolean()) {
            |        implementation("com.facebook.react:hermes-android")
            |    } else {
            |        implementation jscFlavor
            |    }
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // android/app/proguard-rules.pro
    // ============================================================
    private fun buildProguardRules(): String {
        return """
            |# Project-specific ProGuard rules for the generated React Native application.
        """.trimMargin() + "\n"
    }

    // ============================================================
    // AndroidManifest.xml (main)
    // ============================================================
    private fun buildMainManifest(): String {
        return """
            |<manifest xmlns:android="http://schemas.android.com/apk/res/android">
            |    <uses-permission android:name="android.permission.INTERNET" />
            |    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
            |    <application
            |        android:name=".MainApplication"
            |        android:label="@string/app_name"
            |        android:icon="@drawable/ic_launcher"
            |        android:allowBackup="false"
            |        android:theme="@style/AppTheme"
            |        android:supportsRtl="true">
            |        <activity
            |            android:name=".MainActivity"
            |            android:label="@string/app_name"
            |            android:configChanges="keyboard|keyboardHidden|orientation|screenLayout|screenSize|smallestScreenSize|uiMode"
            |            android:launchMode="singleTask"
            |            android:windowSoftInputMode="adjustResize"
            |            android:exported="true">
            |            <intent-filter>
            |                <action android:name="android.intent.action.MAIN" />
            |                <category android:name="android.intent.category.LAUNCHER" />
            |            </intent-filter>
            |        </activity>
            |    </application>
            |</manifest>
        """.trimMargin() + "\n"
    }

    // ============================================================
    // AndroidManifest.xml (debug)
    // ============================================================
    private fun buildDebugManifest(): String {
        return """
            |<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
            |    <application android:usesCleartextTraffic="true" tools:targetApi="28" tools:ignore="GoogleAppIndexingWarning" />
            |</manifest>
        """.trimMargin() + "\n"
    }

    // ============================================================
    // MainActivity.kt
    // ============================================================
    private fun buildMainActivity(packageName: String, appName: String): String {
        return """
            |package $packageName
            |
            |import android.Manifest
            |import android.content.pm.PackageManager
            |import android.os.Build
            |import android.os.Bundle
            |import com.facebook.react.ReactActivity
            |import com.facebook.react.ReactActivityDelegate
            |import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
            |import com.facebook.react.defaults.DefaultReactActivityDelegate
            |
            |class MainActivity : ReactActivity() {
            |    override fun onCreate(savedInstanceState: Bundle?) {
            |        super.onCreate(savedInstanceState)
            |        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            |            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 4101)
            |        }
            |    }
            |
            |    override fun getMainComponentName(): String = "$appName"
            |
            |    override fun createReactActivityDelegate(): ReactActivityDelegate =
            |        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // MainApplication.kt
    // ============================================================
    private fun buildMainApplication(packageName: String): String {
        return """
            |package $packageName
            |
            |import android.app.Application
            |import com.facebook.react.PackageList
            |import com.facebook.react.ReactApplication
            |import com.facebook.react.ReactHost
            |import com.facebook.react.ReactNativeHost
            |import com.facebook.react.ReactPackage
            |import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
            |import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
            |import com.facebook.react.defaults.DefaultReactNativeHost
            |import com.facebook.react.soloader.OpenSourceMergedSoMapping
            |import com.facebook.soloader.SoLoader
            |
            |class MainApplication : Application(), ReactApplication {
            |    override val reactNativeHost: ReactNativeHost =
            |        object : DefaultReactNativeHost(this) {
            |            override fun getPackages(): List<ReactPackage> =
            |                PackageList(this).packages.apply {
            |                    add(AppLoggerPackage())
            |                    // Add manually linked packages here when needed.
            |                }
            |
            |            override fun getJSMainModuleName(): String = "index"
            |            override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG
            |            override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
            |            override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
            |        }
            |
            |    override val reactHost: ReactHost
            |        get() = getDefaultReactHost(applicationContext, reactNativeHost)
            |
            |    override fun onCreate() {
            |        super.onCreate()
            |        CrashLogger.install(this)
            |        SoLoader.init(this, OpenSourceMergedSoMapping)
            |        if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            |            load()
            |        }
            |    }
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // strings.xml
    // ============================================================
    private fun buildStringsXml(appName: String): String {
        return """
            |<resources>
            |    <string name="app_name">${xmlEscape(appName)}</string>
            |</resources>
        """.trimMargin() + "\n"
    }

    // ============================================================
    // styles.xml
    // ============================================================
    private fun buildStylesXml(): String {
        return """
            |<resources>
            |    <style name="AppTheme" parent="Theme.AppCompat.Light.NoActionBar">
            |        <item name="android:fontFamily">sans</item>
            |        <item name="android:colorAccent">#3791FF</item>
            |        <item name="android:navigationBarColor">#0A0E14</item>
            |        <item name="android:statusBarColor">#0A0E14</item>
            |        <item name="android:windowLightStatusBar">false</item>
            |    </style>
            |</resources>
        """.trimMargin() + "\n"
    }

    // ============================================================
    // أدوات
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

    private fun xmlEscape(value: String?): String {
        if (value == null) return ""
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}