package com.flutteride.codweva.reactnative

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.flutteride.codweva.GradleWrapperWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.util.Locale

/**
 * ReactNativeTemplate — يولّد مشروع React Native CLI كامل من الصفر.
 *
 * v1.2 — القالب الافتراضي: محوّل الوحدات (Unit Converter) — نسخة مبسطة
 *   - 3 فئات: الطول، الوزن، الحرارة
 *   - 11 وحدة قياس
 *   - تصميم بسيط وواضح
 *   - بدون مكتبات خارجية، بدون إنترنت
 *   - بدون NDK (newArchEnabled=false)
 */
object ReactNativeTemplate {

    // ============================================================
    // ثوابت
    // ============================================================
    private const val DEFAULT_PACKAGE = "com.example.myapp"
    private const val DEFAULT_APP_NAME = "My React Native App"
    private const val DEFAULT_MIN_SDK = 24
    private const val DEFAULT_TARGET_SDK = 34
    private const val DEFAULT_COMPILE_SDK = 35

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
        val safeCompileSdk = maxOf(DEFAULT_COMPILE_SDK, safeTargetSdk)

        val projectName = toProjectName(safeAppName)
        val packagePath = safePackage.replace('.', '/')

        return try {
            // ============================================================
            // 1) المجلدات
            // ============================================================
            val androidDir = File(projectDir, "android")
            val appDir = File(androidDir, "app")
            val srcMainDir = File(appDir, "src/main")
            val javaDir = File(srcMainDir, "java/$packagePath")
            val resValuesDir = File(srcMainDir, "res/values")
            val resDrawableDir = File(srcMainDir, "res/drawable")
            val debugDir = File(appDir, "src/debug")
            val gradleWrapperDir = File(androidDir, "gradle/wrapper")
            val workflowsDir = File(projectDir, ".github/workflows")
            val klencodDir = File(projectDir, ".klencod")

            listOf(
                androidDir, appDir, srcMainDir, javaDir, resValuesDir,
                resDrawableDir, debugDir, gradleWrapperDir,
                workflowsDir, klencodDir
            ).forEach { dir ->
                if (!dir.exists() && !dir.mkdirs()) return false
            }

            // ============================================================
            // 2) ملفات JS/TS
            // ============================================================
            writeFile(File(projectDir, "App.tsx"), appTsx(safeAppName))
            writeFile(File(projectDir, "index.js"), indexJs())
            writeFile(File(projectDir, "package.json"), packageJson(projectName))
            writeFile(File(projectDir, "app.json"), appJson(projectName, safeAppName, safePackage))
            writeFile(File(projectDir, "react-native.config.js"), reactNativeConfig(safePackage))
            writeFile(File(projectDir, "babel.config.js"), babelConfig())
            writeFile(File(projectDir, "metro.config.js"), metroConfig())
            writeFile(File(projectDir, "tsconfig.json"), tsConfig())

            // ============================================================
            // 3) ملفات Android
            // ============================================================
            ReactNativeAndroidFiles.writeAll(
                projectDir = projectDir,
                packageName = safePackage,
                appName = safeAppName,
                projectName = projectName,
                minSdk = safeMinSdk,
                compileSdk = safeCompileSdk,
                targetSdk = safeTargetSdk
            )

            // ============================================================
            // 4) CrashLogger + AppLoggerPackage
            // ============================================================
            ReactNativeCrashLogger.writeAll(
                javaDir = javaDir,
                packageName = safePackage
            )

            // ============================================================
            // 5) ملفات المشروع العامة
            // ============================================================
            writeFile(File(projectDir, ".gitignore"), gitIgnore())
            writeFile(File(projectDir, "README.md"), readme(safeAppName, safePackage))

            // ============================================================
            // 6) GitHub Actions Workflow
            // ============================================================
            writeFile(
                File(workflowsDir, "build-android.yml"),
                ReactNativeBuildWorkflow.build(safeTargetSdk)
            )

            // ============================================================
            // 7) .klencod/project.json
            // ============================================================
            writeFile(
                File(klencodDir, "project.json"),
                buildProjectMetadata(
                    projectName, safeAppName, safePackage,
                    safeMinSdk, safeCompileSdk, safeTargetSdk
                )
            )

            // ============================================================
            // 8) الأيقونة الافتراضية
            // ============================================================
            writeDefaultIconPng(resDrawableDir)

            // ============================================================
            // 9) Gradle Wrapper
            // ============================================================
            GradleWrapperWriter.ensureGradleWrapper(context, androidDir)

            true
        } catch (_: Exception) {
            false
        }
    }

    // ============================================================
    // App.tsx — محوّل الوحدات (نسخة مبسطة)
    // ============================================================
    private fun appTsx(appName: String): String {
        val safeTitle = appName.replace("'", "\\'")
        return """
            |import React, {useState} from 'react';
            |import {
            |  SafeAreaView,
            |  ScrollView,
            |  StyleSheet,
            |  Text,
            |  TextInput,
            |  TouchableOpacity,
            |  View,
            |} from 'react-native';
            |
            |// ============================================================
            |// الفئات والوحدات (بسيطة جداً)
            |// ============================================================
            |type Unit = {
            |  id: string;
            |  name: string;
            |  factor: number; // معامل التحويل إلى الوحدة الأساسية
            |};
            |
            |type Category = {
            |  id: string;
            |  name: string;
            |  baseUnitId: string;
            |  units: Unit[];
            |  isTemperature?: boolean;
            |};
            |
            |const CATEGORIES: Category[] = [
            |  {
            |    id: 'length',
            |    name: 'الطول',
            |    baseUnitId: 'm',
            |    units: [
            |      {id: 'mm', name: 'مم', factor: 0.001},
            |      {id: 'cm', name: 'سم', factor: 0.01},
            |      {id: 'm', name: 'متر', factor: 1},
            |      {id: 'km', name: 'كم', factor: 1000},
            |    ],
            |  },
            |  {
            |    id: 'weight',
            |    name: 'الوزن',
            |    baseUnitId: 'kg',
            |    units: [
            |      {id: 'g', name: 'جرام', factor: 0.001},
            |      {id: 'kg', name: 'كجم', factor: 1},
            |      {id: 'ton', name: 'طن', factor: 1000},
            |      {id: 'lb', name: 'رطل', factor: 0.453592},
            |    ],
            |  },
            |  {
            |    id: 'temperature',
            |    name: 'الحرارة',
            |    baseUnitId: 'c',
            |    isTemperature: true,
            |    units: [
            |      {id: 'c', name: 'سيلزيوس', factor: 1},
            |      {id: 'f', name: 'فهرنهايت', factor: 1},
            |      {id: 'k', name: 'كلفن', factor: 1},
            |    ],
            |  },
            |];
            |
            |// ============================================================
            |// التحويل (دالة واحدة بسيطة)
            |// ============================================================
            |function convert(value: number, fromId: string, toId: string, category: Category): number {
            |  // حالة الحرارة (منطق خاص)
            |  if (category.isTemperature) {
            |    let celsius = value;
            |    if (fromId === 'f') celsius = (value - 32) * 5 / 9;
            |    else if (fromId === 'k') celsius = value - 273.15;
            |
            |    if (toId === 'c') return celsius;
            |    if (toId === 'f') return celsius * 9 / 5 + 32;
            |    return celsius + 273.15;
            |  }
            |
            |  // الحالة العادية
            |  const fromUnit = category.units.find(u => u.id === fromId);
            |  const toUnit = category.units.find(u => u.id === toId);
            |  if (!fromUnit || !toUnit) return 0;
            |
            |  const valueInBase = value * fromUnit.factor;
            |  return valueInBase / toUnit.factor;
            |}
            |
            |// ============================================================
            |// تنسيق الرقم
            |// ============================================================
            |function fmt(n: number): string {
            |  if (!isFinite(n)) return '0';
            |  if (n === 0) return '0';
            |  if (Math.abs(n) < 0.0001) return n.toExponential(3);
            |  if (Math.abs(n) > 1000000) return n.toExponential(3);
            |  return (Math.round(n * 10000) / 10000).toString();
            |}
            |
            |// ============================================================
            |// التطبيق
            |// ============================================================
            |export default function App() {
            |  const [categoryIndex, setCategoryIndex] = useState(0);
            |  const [fromId, setFromId] = useState('mm');
            |  const [toId, setToId] = useState('m');
            |  const [input, setInput] = useState('1');
            |
            |  const category = CATEGORIES[categoryIndex];
            |
            |  // حساب النتيجة مباشرة
            |  const numInput = parseFloat(input) || 0;
            |  const result = convert(numInput, fromId, toId, category);
            |
            |  // تغيير الفئة
            |  const changeCategory = (idx: number) => {
            |    setCategoryIndex(idx);
            |    const newCat = CATEGORIES[idx];
            |    setFromId(newCat.units[0].id);
            |    setToId(newCat.units[1].id);
            |    setInput('1');
            |  };
            |
            |  // تبديل
            |  const swap = () => {
            |    const tmp = fromId;
            |    setFromId(toId);
            |    setToId(tmp);
            |  };
            |
            |  return (
            |    <SafeAreaView style={styles.safe}>
            |      <View style={styles.header}>
            |        <Text style={styles.title}>$safeTitle</Text>
            |      </View>
            |
            |      {/* التبويبات */}
            |      <View style={styles.tabsRow}>
            |        {CATEGORIES.map((cat, idx) => (
            |          <TouchableOpacity
            |            key={cat.id}
            |            style={[styles.tab, idx === categoryIndex && styles.tabActive]}
            |            onPress={() => changeCategory(idx)}>
            |            <Text style={[styles.tabText, idx === categoryIndex && styles.tabTextActive]}>
            |              {cat.name}
            |            </Text>
            |          </TouchableOpacity>
            |        ))}
            |      </View>
            |
            |      <ScrollView style={styles.body} contentContainerStyle={styles.bodyContent}>
            |        {/* حقل الإدخال */}
            |        <Text style={styles.label}>القيمة</Text>
            |        <TextInput
            |          style={styles.input}
            |          keyboardType="numeric"
            |          value={input}
            |          onChangeText={setInput}
            |          placeholder="1"
            |          placeholderTextColor="#5A6472"
            |        />
            |
            |        {/* اختيار الوحدة من */}
            |        <Text style={styles.label}>من</Text>
            |        <View style={styles.chipsRow}>
            |          {category.units.map(unit => (
            |            <TouchableOpacity
            |              key={unit.id}
            |              style={[styles.chip, unit.id === fromId && styles.chipActive]}
            |              onPress={() => setFromId(unit.id)}>
            |              <Text style={[styles.chipText, unit.id === fromId && styles.chipTextActive]}>
            |                {unit.name}
            |              </Text>
            |            </TouchableOpacity>
            |          ))}
            |        </View>
            |
            |        {/* زر التبديل */}
            |        <TouchableOpacity style={styles.swapBtn} onPress={swap}>
            |          <Text style={styles.swapBtnText}>تبديل الوحدات</Text>
            |        </TouchableOpacity>
            |
            |        {/* اختيار الوحدة إلى */}
            |        <Text style={styles.label}>إلى</Text>
            |        <View style={styles.chipsRow}>
            |          {category.units.map(unit => (
            |            <TouchableOpacity
            |              key={unit.id}
            |              style={[styles.chip, unit.id === toId && styles.chipActive]}
            |              onPress={() => setToId(unit.id)}>
            |              <Text style={[styles.chipText, unit.id === toId && styles.chipTextActive]}>
            |                {unit.name}
            |              </Text>
            |            </TouchableOpacity>
            |          ))}
            |        </View>
            |
            |        {/* النتيجة */}
            |        <View style={styles.resultBox}>
            |          <Text style={styles.resultLabel}>النتيجة</Text>
            |          <Text style={styles.resultValue}>{fmt(result)}</Text>
            |          <Text style={styles.formula}>
            |            {numInput} {category.units.find(u => u.id === fromId)?.name} = {fmt(result)} {category.units.find(u => u.id === toId)?.name}
            |          </Text>
            |        </View>
            |      </ScrollView>
            |    </SafeAreaView>
            |  );
            |}
            |
            |// ============================================================
            |// الأنماط
            |// ============================================================
            |const C = {
            |  bg: '#0A0E14',
            |  surface: '#141B24',
            |  surfaceAlt: '#1B242F',
            |  border: '#303E4D',
            |  text: '#F5F7FA',
            |  muted: '#9DABBB',
            |  primary: '#3791FF',
            |  primaryDark: '#2570CC',
            |  success: '#36C284',
            |};
            |
            |const styles = StyleSheet.create({
            |  safe: {flex: 1, backgroundColor: C.bg},
            |  header: {padding: 20, paddingBottom: 12},
            |  title: {color: C.text, fontSize: 26, fontWeight: '800', textAlign: 'right'},
            |  tabsRow: {
            |    flexDirection: 'row',
            |    paddingHorizontal: 14,
            |    paddingBottom: 10,
            |    borderBottomWidth: 1,
            |    borderBottomColor: C.border,
            |  },
            |  tab: {
            |    paddingHorizontal: 16,
            |    paddingVertical: 8,
            |    borderRadius: 18,
            |    backgroundColor: C.surface,
            |    borderWidth: 1,
            |    borderColor: C.border,
            |    marginRight: 8,
            |  },
            |  tabActive: {backgroundColor: C.primary, borderColor: C.primary},
            |  tabText: {color: C.muted, fontSize: 13, fontWeight: '600'},
            |  tabTextActive: {color: '#FFFFFF'},
            |  body: {flex: 1},
            |  bodyContent: {padding: 16, paddingBottom: 40},
            |  label: {
            |    color: C.muted,
            |    fontSize: 13,
            |    fontWeight: '600',
            |    marginBottom: 8,
            |    marginTop: 16,
            |    textAlign: 'right',
            |  },
            |  input: {
            |    color: C.text,
            |    fontSize: 28,
            |    fontWeight: '700',
            |    paddingVertical: 10,
            |    paddingHorizontal: 12,
            |    borderRadius: 12,
            |    backgroundColor: C.surface,
            |    textAlign: 'right',
            |    borderWidth: 1,
            |    borderColor: C.border,
            |  },
            |  chipsRow: {
            |    flexDirection: 'row',
            |    flexWrap: 'wrap',
            |    gap: 8,
            |  },
            |  chip: {
            |    paddingHorizontal: 14,
            |    paddingVertical: 8,
            |    borderRadius: 12,
            |    backgroundColor: C.surface,
            |    borderWidth: 1,
            |    borderColor: C.border,
            |  },
            |  chipActive: {backgroundColor: C.primaryDark, borderColor: C.primary},
            |  chipText: {color: C.muted, fontSize: 13, fontWeight: '600'},
            |  chipTextActive: {color: '#FFFFFF'},
            |  swapBtn: {
            |    alignSelf: 'center',
            |    marginVertical: 16,
            |    paddingHorizontal: 24,
            |    paddingVertical: 10,
            |    borderRadius: 22,
            |    backgroundColor: C.surfaceAlt,
            |    borderWidth: 1,
            |    borderColor: C.border,
            |  },
            |  swapBtnText: {color: C.primary, fontSize: 14, fontWeight: '700'},
            |  resultBox: {
            |    marginTop: 24,
            |    padding: 20,
            |    borderRadius: 18,
            |    backgroundColor: C.surfaceAlt,
            |    borderWidth: 1,
            |    borderColor: C.primary,
            |  },
            |  resultLabel: {
            |    color: C.muted,
            |    fontSize: 12,
            |    fontWeight: '600',
            |    textAlign: 'right',
            |    marginBottom: 6,
            |  },
            |  resultValue: {
            |    color: C.success,
            |    fontSize: 36,
            |    fontWeight: '800',
            |    textAlign: 'right',
            |  },
            |  formula: {
            |    color: C.muted,
            |    fontSize: 12,
            |    textAlign: 'right',
            |    marginTop: 10,
            |  },
            |});
        """.trimMargin() + "\n"
    }

    // ============================================================
    // index.js
    // ============================================================
    private fun indexJs(): String {
        return """
            |import {AppRegistry} from 'react-native';
            |import App from './App';
            |import {name as appName} from './app.json';
            |
            |AppRegistry.registerComponent(appName, () => App);
        """.trimMargin() + "\n"
    }

    // ============================================================
    // package.json
    // ============================================================
    private fun packageJson(projectName: String): String {
        val npmName = projectName.lowercase(Locale.US).replace('_', '-')
        return """
            |{
            |  "name": "$npmName",
            |  "version": "0.1.0",
            |  "private": true,
            |  "scripts": {
            |    "android": "react-native run-android",
            |    "start": "react-native start",
            |    "lint": "eslint .",
            |    "test": "jest"
            |  },
            |  "dependencies": {
            |    "react": "18.3.1",
            |    "react-native": "0.76.7"
            |  },
            |  "devDependencies": {
            |    "@react-native-community/cli": "15.0.1",
            |    "@react-native-community/cli-platform-android": "15.0.1",
            |    "@react-native-community/cli-platform-ios": "15.0.1",
            |    "@babel/core": "^7.26.0",
            |    "@babel/preset-env": "^7.26.0",
            |    "@babel/runtime": "^7.26.0",
            |    "@react-native/babel-preset": "0.76.7",
            |    "@react-native/eslint-config": "0.76.7",
            |    "@react-native/metro-config": "0.76.7",
            |    "@react-native/typescript-config": "0.76.7",
            |    "@types/react": "^18.3.0",
            |    "eslint": "^8.57.0",
            |    "prettier": "3.4.2",
            |    "typescript": "^5.7.0"
            |  },
            |  "engines": {
            |    "node": ">=18"
            |  }
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // app.json
    // ============================================================
    private fun appJson(
        projectName: String,
        displayName: String,
        packageName: String
    ): String {
        return """
            |{
            |  "name": "${jsonEscape(projectName)}",
            |  "displayName": "${jsonEscape(displayName)}",
            |  "android": {"packageName": "${jsonEscape(packageName)}"}
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // react-native.config.js
    // ============================================================
    private fun reactNativeConfig(packageName: String): String {
        return """
            |module.exports = {
            |  project: {
            |    android: {
            |      packageName: '$packageName'
            |    }
            |  }
            |};
        """.trimMargin() + "\n"
    }

    // ============================================================
    // babel.config.js
    // ============================================================
    private fun babelConfig(): String {
        return """
            |module.exports = {
            |  presets: ['module:@react-native/babel-preset'],
            |};
        """.trimMargin() + "\n"
    }

    // ============================================================
    // metro.config.js
    // ============================================================
    private fun metroConfig(): String {
        return """
            |const {getDefaultConfig, mergeConfig} = require('@react-native/metro-config');
            |
            |const config = {};
            |module.exports = mergeConfig(getDefaultConfig(__dirname), config);
        """.trimMargin() + "\n"
    }

    // ============================================================
    // tsconfig.json
    // ============================================================
    private fun tsConfig(): String {
        return """
            |{
            |  "extends": "@react-native/typescript-config",
            |  "include": ["**/*.ts", "**/*.tsx"]
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // .gitignore
    // ============================================================
    private fun gitIgnore(): String {
        return """
            |node_modules/
            |.gradle/
            |.idea/
            |*.iml
            |android/.cxx/
            |android/app/build/
            |ios/Pods/
            |.DS_Store
            |.env
        """.trimMargin() + "\n"
    }

    // ============================================================
    // README.md
    // ============================================================
    private fun readme(appName: String, packageName: String): String {
        return """
            |# $appName
            |
            |مشروع React Native CLI مُنشأ بواسطة KlencodIDE.
            |
            |## القالب: محوّل الوحدات (Unit Converter)
            |
            |### الميزات
            |- 3 فئات: الطول، الوزن، الحرارة
            |- 11 وحدة قياس
            |- تبديل فوري بين الوحدات
            |- عرض الصيغة الحسابية
            |- تصميم داكن بسيط
            |- بدون مكتبات خارجية، بدون إنترنت
            |- بدون NDK (لبناء أسرع)
            |
            |## التقنيات
            |- React Native 0.76.7
            |- TypeScript
            |- Android Native (Kotlin)
            |- GitHub Actions للبناء التلقائي
            |
            |## البناء
            |
            |يتم البناء تلقائياً على GitHub Actions عند الضغط على "بناء APK".
            |
            |## الهيكل
            |- `App.tsx` — واجهة التطبيق (محوّل الوحدات)
            |- `index.js` — نقطة الدخول
            |- `android/` — مشروع Android الأصلي
            |- `.github/workflows/` — GitHub Actions
            |- `.klencod/project.json` — بيانات المشروع
            |
            |## Package
            |``$packageName``
        """.trimMargin() + "\n"
    }

    // ============================================================
    // .klencod/project.json
    // ============================================================
    private fun buildProjectMetadata(
        projectName: String,
        displayName: String,
        packageName: String,
        minSdk: Int,
        compileSdk: Int,
        targetSdk: Int
    ): String {
        return """
            |{
            |  "name": "${jsonEscape(projectName)}",
            |  "displayName": "${jsonEscape(displayName)}",
            |  "applicationId": "${jsonEscape(packageName)}",
            |  "minSdk": $minSdk,
            |  "compileSdk": $compileSdk,
            |  "targetSdk": $targetSdk,
            |  "hasIcon": false,
            |  "template": "react-native-cli",
            |  "createdAt": "${System.currentTimeMillis()}"
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // أيقونة افتراضية PNG — ميزان تحويل
    // ============================================================
    private fun writeDefaultIconPng(resDrawableDir: File) {
        try {
            val targetFile = File(resDrawableDir, "ic_launcher.png")
            if (targetFile.exists()) return
            if (!resDrawableDir.exists() && !resDrawableDir.mkdirs()) return

            val size = 192
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val s = size.toFloat()

            // خلفية زرقاء
            canvas.drawColor(0xFF1F6FEB.toInt())

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

            // ميزان: عمود + ذراع أفقي + كفتان
            val cx = s * 0.5f
            val cy = s * 0.5f
            val armLength = s * 0.28f
            val stroke = s * 0.028f

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = 0xFFFFFFFF.toInt()

            // عمود رأسي
            canvas.drawLine(cx, cy - armLength * 0.8f, cx, cy + armLength * 0.6f, paint)
            // ذراع أفقي
            canvas.drawLine(cx - armLength, cy - armLength * 0.6f, cx + armLength, cy - armLength * 0.6f, paint)
            // قاعدة
            canvas.drawLine(cx - armLength * 0.5f, cy + armLength * 0.6f, cx + armLength * 0.5f, cy + armLength * 0.6f, paint)

            // كفة يسرى
            paint.style = Paint.Style.FILL
            paint.color = 0xFF61DAFB.toInt()
            canvas.drawRect(
                cx - armLength - s * 0.03f,
                cy - armLength * 0.6f,
                cx - armLength + s * 0.03f,
                cy - armLength * 0.25f,
                paint
            )

            // كفة يمنى
            paint.color = 0xFFFFD33D.toInt()
            canvas.drawRect(
                cx + armLength - s * 0.03f,
                cy - armLength * 0.6f,
                cx + armLength + s * 0.03f,
                cy - armLength * 0.25f,
                paint
            )

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
        val lower = pkg.trim().lowercase(Locale.US)
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

    private fun toProjectName(appName: String): String {
        val sb = StringBuilder()
        for (c in appName) {
            when {
                c.isLetterOrDigit() && c.code < 128 -> sb.append(c)
                c == '_' || c == '-' || c == ' ' -> sb.append(' ')
            }
        }
        var result = sb.toString().trim()
        if (result.isEmpty() || !result[0].isLetter()) {
            result = "App$result"
        }
        val words = result.split(" ").filter { it.isNotEmpty() }
        val pascal = words.joinToString("") { word ->
            word.substring(0, 1).uppercase(Locale.US) + word.substring(1)
        }
        return pascal.take(40).ifEmpty { "App" }
    }

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

    private fun jsonEscape(value: String?): String {
        if (value == null) return ""
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
    }
}