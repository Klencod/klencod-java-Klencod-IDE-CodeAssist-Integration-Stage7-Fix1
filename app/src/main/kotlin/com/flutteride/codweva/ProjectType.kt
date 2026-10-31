package com.flutteride.codweva

import java.util.Locale

/**
 * ProjectType — نوع المشروع الذي ينشئه KlencodIDE.
 *
 * v3.0 — إضافة React Native:
 *   - 4 أنواع مدعومة: Flutter + Java + Kotlin + React Native
 *
 * كل نوع يحدد:
 *   - build command المستخدم في GitHub Actions
 *   - اسم workflow file
 *   - اسم artifact على GitHub
 *   - لاحقة الاسم الافتراضية للـ repo
 *   - الامتداد الأساسي للملفات
 */
enum class ProjectType(
    val id: String,
    val displayName: String,
    val description: String,
    val buildCommand: String,
    val workflowFile: String,
    val artifactName: String,
    val repoPrefix: String,
    val primaryLanguage: String,
    val primaryExtension: String,
    val defaultSdkConstraints: String?
) {

    // ============================================================
    // FLUTTER
    // ============================================================
    FLUTTER(
        id = "flutter",
        displayName = "Flutter",
        description = "تطبيق Flutter متعدد المنصات (Dart)",
        buildCommand = "flutter build apk --release",
        workflowFile = "main.yml",
        artifactName = "app-release",
        repoPrefix = "klencod-flutter-",
        primaryLanguage = "Dart",
        primaryExtension = "dart",
        defaultSdkConstraints = null
    ),

    // ============================================================
    // JAVA
    // ============================================================
    JAVA(
        id = "java",
        displayName = "Android (Java)",
        description = "تطبيق Android أصلي بلغة Java",
        buildCommand = "./gradlew assembleDebug",
        workflowFile = "build-android.yml",
        artifactName = "klencod-java-debug-apk",
        repoPrefix = "klencod-java-",
        primaryLanguage = "Java",
        primaryExtension = "java",
        defaultSdkConstraints = "24..34"
    ),

    // ============================================================
    // KOTLIN
    // ============================================================
    KOTLIN(
        id = "kotlin",
        displayName = "Android (Kotlin)",
        description = "تطبيق Android أصلي بلغة Kotlin",
        buildCommand = "./gradlew assembleDebug",
        workflowFile = "build-android.yml",
        artifactName = "klencod-kotlin-debug-apk",
        repoPrefix = "klencod-kotlin-",
        primaryLanguage = "Kotlin",
        primaryExtension = "kt",
        defaultSdkConstraints = "24..34"
    ),

    // ============================================================
    // REACT NATIVE  ⭐ NEW
    // ============================================================
    REACT_NATIVE(
        id = "reactnative",
        displayName = "React Native",
        description = "تطبيق React Native CLI (TypeScript + Native Android)",
        buildCommand = "gradle -p android assembleRelease",
        workflowFile = "build-android.yml",
        artifactName = "klencod-reactnative-release-apk",
        repoPrefix = "klencod-reactnative-",
        primaryLanguage = "TypeScript",
        primaryExtension = "tsx",
        defaultSdkConstraints = "24..35"
    );

    // ============================================================
    // خصائص مساعدة
    // ============================================================

    /** هل هو مشروع Flutter؟ */
    val isFlutter: Boolean
        get() = this == FLUTTER

    /** هل هو مشروع Android أصلي (Java/Kotlin)؟ */
    val isNative: Boolean
        get() = this == JAVA || this == KOTLIN

    /** هل هو مشروع React Native؟ */
    val isReactNative: Boolean
        get() = this == REACT_NATIVE

    /** هل يستخدم Gradle Wrapper؟ */
    val needsGradleWrapper: Boolean
        get() = isNative || isReactNative

    /** هل يستخدم pubspec.yaml؟ */
    val needsPubspec: Boolean
        get() = isFlutter

    /** هل يستخدم module.toml؟ */
    val needsModuleToml: Boolean
        get() = isNative

    /** هل يستخدم package.json؟ */
    val needsPackageJson: Boolean
        get() = isReactNative

    /** هل يحتاج Flutter version؟ */
    val requiresFlutterVersion: Boolean
        get() = isFlutter

    /** هل يحتاج Node.js في الـ workflow؟ */
    val requiresNode: Boolean
        get() = isReactNative

    /** هل هو Kotlin؟ */
    val isKotlin: Boolean
        get() = this == KOTLIN

    /** هل هو Java؟ */
    val isJava: Boolean
        get() = this == JAVA

    /** الحقول المطلوبة في شاشة "إنشاء مشروع جديد" */
    val requiresFlutterVersion_: Boolean
        get() = requiresFlutterVersion

    /** الامتدادات الإضافية (للملفات القابلة للتحرير) */
    val additionalExtensions: List<String>
        get() = when (this) {
            FLUTTER -> listOf("dart", "yaml", "yml")
            JAVA -> listOf("java", "xml", "gradle", "properties", "toml")
            KOTLIN -> listOf("kt", "kts", "java", "xml", "gradle", "properties", "toml")
            REACT_NATIVE -> listOf("js", "jsx", "ts", "tsx", "json", "java", "kt", "xml", "gradle", "properties")
        }

    // ============================================================
    // Companion
    // ============================================================
    companion object {

        /** النوع الافتراضي عند عدم تحديد شيء. */
        @JvmStatic
        val DEFAULT: ProjectType
            get() = FLUTTER

        /**
         * يحول id إلى ProjectType.
         * أي قيمة غير معروفة أو null تُعيد DEFAULT.
         */
        @JvmStatic
        fun fromId(id: String?): ProjectType {
            if (id.isNullOrEmpty()) return DEFAULT
            val normalized = id.trim().lowercase(Locale.US)
            return values().firstOrNull { it.id == normalized } ?: DEFAULT
        }

        /**
         * قائمة الأنواع المتاحة حالياً (لبناء قائمة UI).
         * v3.0 — 4 أنواع كاملة.
         */
        @JvmStatic
        fun available(): List<ProjectType> {
            return listOf(FLUTTER, JAVA, KOTLIN, REACT_NATIVE)
        }

        /**
         * قائمة الأنواع المتاحة مع عرض الاسم فقط.
         */
        @JvmStatic
        fun availableDisplayNames(): Array<String> {
            return available().map { it.displayName }.toTypedArray()
        }

        /**
         * يكتشف النوع من ملفات المشروع (للمشاريع القديمة أو المستوردة).
         *
         * المنطق (بالترتيب):
         *   1. pubspec.yaml → FLUTTER
         *   2. package.json + react-native → REACT_NATIVE
         *   3. package.json + react-native in deps → REACT_NATIVE
         *   4. module.toml + languageLevel = KOTLIN → KOTLIN
         *   5. module.toml + languageLevel = JAVA → JAVA
         *   6. build.gradle + kotlin → KOTLIN
         *   7. build.gradle → JAVA
         *   8. الافتراضي: FLUTTER
         */
        @JvmStatic
        fun detectFromProject(projectDir: java.io.File?): ProjectType {
            if (projectDir == null || !projectDir.isDirectory) return DEFAULT

            // 1. Flutter
            if (java.io.File(projectDir, "pubspec.yaml").isFile) {
                return FLUTTER
            }

            // 2. React Native (package.json + react-native)
            val packageJson = java.io.File(projectDir, "package.json")
            if (packageJson.isFile) {
                val isRn = try {
                    val text = packageJson.readText()
                    val lower = text.lowercase(Locale.US)
                    lower.contains("\"react-native\"") ||
                            lower.contains("'react-native'") ||
                            lower.contains("\"@react-native/")
                } catch (_: Exception) {
                    false
                }
                if (isRn) return REACT_NATIVE

                // package.json موجود لكن بدون react-native → نستمر
                // (قد يكون مشروع Node.js آخر، نكتشف من Android)
            }

            // 3. React Native بدون package.json (نادر) — ابحث عن App.tsx/App.js
            if (java.io.File(projectDir, "App.tsx").isFile ||
                java.io.File(projectDir, "App.jsx").isFile ||
                java.io.File(projectDir, "App.js").isFile
            ) {
                val hasAndroidRn = java.io.File(
                    projectDir,
                    "android/app/src/main/java"
                ).isDirectory
                val hasMetro = java.io.File(projectDir, "metro.config.js").isFile
                if (hasAndroidRn || hasMetro) return REACT_NATIVE
            }

            // 4. Android Native (Java/Kotlin) — عبر module.toml
            val moduleToml = java.io.File(projectDir, "app/module.toml")
            if (moduleToml.isFile) {
                return try {
                    val text = moduleToml.readText()
                    val upper = text.uppercase(Locale.US)

                    when {
                        upper.contains("LANGUAGELEVEL") && upper.contains("KOTLIN") -> KOTLIN
                        upper.contains("LANGUAGELEVEL") && upper.contains("JAVA") -> JAVA
                        upper.contains("KOTLIN") -> KOTLIN
                        else -> JAVA
                    }
                } catch (_: Exception) {
                    JAVA
                }
            }

            // 5. build.gradle بدون module.toml
            val hasAppGradle = java.io.File(projectDir, "app/build.gradle").isFile ||
                    java.io.File(projectDir, "app/build.gradle.kts").isFile

            if (hasAppGradle) {
                val appGradle = java.io.File(projectDir, "app/build.gradle")
                if (appGradle.isFile) {
                    return try {
                        val text = appGradle.readText()
                        if (text.contains("kotlin", ignoreCase = true)) KOTLIN else JAVA
                    } catch (_: Exception) {
                        JAVA
                    }
                }
                return JAVA
            }

            // 6. Default
            return DEFAULT
        }

        /**
         * يُرجع الـ workflow file المناسب لنوع معيّن.
         */
        @JvmStatic
        fun workflowFileFor(type: ProjectType): String = type.workflowFile

        /**
         * يُرجع اسم الـ artifact المناسب لنوع معيّن.
         */
        @JvmStatic
        fun artifactNameFor(type: ProjectType): String = type.artifactName

        /**
         * يُرجع أمر البناء المناسب.
         */
        @JvmStatic
        fun buildCommandFor(type: ProjectType): String = type.buildCommand

        /**
         * يُرجع بادئة اسم المستودع.
         */
        @JvmStatic
        fun repoPrefixFor(type: ProjectType): String = type.repoPrefix

        /**
         * يُرجع الاسم المعروض للـ UI.
         */
        @JvmStatic
        fun displayNameFor(type: ProjectType): String = type.displayName

        /**
         * يُرجع الوصف للـ UI.
         */
        @JvmStatic
        fun descriptionFor(type: ProjectType): String = type.description

        /**
         * يُرجع رمز الأيقونة النصية (للعرض في UI).
         */
        @JvmStatic
        fun symbolFor(type: ProjectType): String {
            return when (type) {
                FLUTTER -> "F"
                JAVA -> "J"
                KOTLIN -> "K"
                REACT_NATIVE -> "⚛"
            }
        }

        /**
         * يُرجع لون الأيقونة (للعرض في UI).
         */
        @JvmStatic
        fun colorFor(type: ProjectType): Int {
            return when (type) {
                FLUTTER -> 0xFF1F6FEB.toInt()   // أزرق Flutter
                JAVA -> 0xFFD29922.toInt()      // ذهبي Java
                KOTLIN -> 0xFF8957E5.toInt()    // بنفسجي Kotlin
                REACT_NATIVE -> 0xFF61DAFB.toInt() // React cyan
            }
        }
    }
}