package com.flutteride.codweva.reactnative

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * ReactNativeProjectCreator — المسؤول عن إنشاء مشروع React Native CLI جديد.
 *
 * v1.0 — نفس نمط JavaProjectCreator:
 *   - يستدعي ReactNativeTemplate لتوليد الملفات
 *   - يتحقق من البنية
 *   - يضبط targetSdk في Gradle
 *   - يدير التحديثات اللاحقة
 */
object ReactNativeProjectCreator {

    // ============================================================
    // ثوابت
    // ============================================================
    private const val DEFAULT_MIN_SDK = 24
    private const val DEFAULT_TARGET_SDK = 34
    private const val DEFAULT_COMPILE_SDK = 35

    // ============================================================
    // نتيجة الإنشاء
    // ============================================================
    class Result(
        val success: Boolean,
        val projectDir: File?,
        val message: String
    ) {
        companion object {
            fun ok(dir: File) = Result(true, dir, "تم إنشاء المشروع بنجاح")
            fun fail(msg: String) = Result(false, null, msg)
        }
    }

    // ============================================================
    // الدالة الرئيسية
    // ============================================================
    @JvmStatic
    fun create(
        context: Context?,
        targetDir: File?,
        packageName: String?,
        appName: String?,
        apiLevel: String?
    ): Result {
        // 1) تحقق من المدخلات
        if (targetDir == null) return Result.fail("مجلد المشروع غير محدد")
        if (targetDir.exists() && !targetDir.isDirectory) {
            return Result.fail("المسار موجود لكنه ليس مجلداً")
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return Result.fail("تعذر إنشاء مجلد المشروع")
        }
        if (packageName.isNullOrBlank()) return Result.fail("اسم الحزمة مطلوب")
        if (!isValidPackageName(packageName)) {
            return Result.fail("اسم الحزمة غير صالح: $packageName")
        }
        if (appName.isNullOrBlank()) return Result.fail("اسم التطبيق مطلوب")

        // 2) تنظيف API
        val targetSdk = parseSdk(apiLevel) ?: DEFAULT_TARGET_SDK

        // 3) استدعاء ReactNativeTemplate
        val ok = try {
            ReactNativeTemplate.write(
                context = context,
                projectDir = targetDir,
                packageName = packageName,
                appName = appName,
                apiLevel = targetSdk.toString()
            )
        } catch (e: Exception) {
            return Result.fail("فشل توليد القالب: ${e.message}")
        }

        if (!ok) {
            safeDeleteRecursive(targetDir)
            return Result.fail("فشل توليد ملفات القالب")
        }

        // 4) تحقق من البنية
        val validation = validateStructure(targetDir)
        if (validation != null) {
            safeDeleteRecursive(targetDir)
            return Result.fail(validation)
        }

        // 5) ضبط targetSdk احتياطياً
        try {
            ReactNativeGradleSupport.applyTargetSdk(targetDir, targetSdk)
        } catch (_: Exception) {
        }

        // 6) نجاح
        return Result.ok(targetDir)
    }

    // ============================================================
    // تحقق من البنية
    // ============================================================
    @JvmStatic
    fun validateStructure(projectDir: File): String? {
        if (!projectDir.isDirectory) return "المجلد الجذر غير موجود"

        // الملفات الأساسية
        val requiredFiles = listOf(
            "App.tsx",
            "index.js",
            "package.json",
            "app.json",
            "babel.config.js",
            "metro.config.js",
            "tsconfig.json",
            "react-native.config.js",
            "android/build.gradle",
            "android/settings.gradle",
            "android/gradle.properties",
            "android/app/build.gradle",
            "android/app/src/main/AndroidManifest.xml",
            "android/app/src/debug/AndroidManifest.xml",
            ".github/workflows/build-android.yml",
            ".klencod/project.json"
        )

        for (path in requiredFiles) {
            val file = File(projectDir, path)
            if (!file.isFile) {
                return "ملف مفقود: $path"
            }
        }

        // مجلد Java
        val srcJavaDir = File(projectDir, "android/app/src/main/java")
        if (!srcJavaDir.isDirectory) {
            return "مجلد src/main/java غير موجود"
        }

        // MainActivity.kt + MainApplication.kt
        val mainActivity = findFileRecursive(srcJavaDir, "MainActivity.kt")
        if (mainActivity == null) return "ملف MainActivity.kt غير موجود"

        val mainApplication = findFileRecursive(srcJavaDir, "MainApplication.kt")
        if (mainApplication == null) return "ملف MainApplication.kt غير موجود"

        // CrashLogger.java
        val crashLogger = findFileRecursive(srcJavaDir, "CrashLogger.java")
        if (crashLogger == null) return "ملف CrashLogger.java غير موجود"

        // AppLoggerPackage.java
        val appLogger = findFileRecursive(srcJavaDir, "AppLoggerPackage.java")
        if (appLogger == null) return "ملف AppLoggerPackage.java غير موجود"

        // الأيقونة
        val icon = File(projectDir, "android/app/src/main/res/drawable/ic_launcher.png")
        if (!icon.isFile) return "ملف الأيقونة غير موجود"

        return null
    }

    // ============================================================
    // تحديث الإعدادات لاحقاً
    // ============================================================
    @JvmStatic
    fun updateSettings(
        projectDir: File,
        newAppName: String?,
        newPackageName: String?,
        newTargetSdk: Int?
    ): String? {
        if (!projectDir.isDirectory) return "المجلد غير موجود"

        if (!newAppName.isNullOrBlank()) {
            try {
                updateAppName(projectDir, newAppName)
            } catch (e: Exception) {
                return "فشل تحديث اسم التطبيق: ${e.message}"
            }
        }

        if (!newPackageName.isNullOrBlank()) {
            if (!isValidPackageName(newPackageName)) {
                return "اسم الحزمة الجديد غير صالح"
            }
            try {
                updatePackageName(projectDir, newPackageName)
            } catch (e: Exception) {
                return "فشل تحديث اسم الحزمة: ${e.message}"
            }
        }

        if (newTargetSdk != null) {
            if (newTargetSdk < 21 || newTargetSdk > 36) {
                return "targetSdk خارج النطاق المسموح (21-36)"
            }
            try {
                ReactNativeGradleSupport.applyTargetSdk(projectDir, newTargetSdk)
            } catch (e: Exception) {
                return "فشل تحديث targetSdk: ${e.message}"
            }
        }

        return null
    }

    // ============================================================
    // تحديث اسم التطبيق
    // ============================================================
    private fun updateAppName(projectDir: File, newName: String) {
        // 1) strings.xml
        val stringsFile = File(
            projectDir,
            "android/app/src/main/res/values/strings.xml"
        )
        if (stringsFile.isFile) {
            val content = stringsFile.readText()
            val escaped = xmlEscape(newName)
            val updated = content.replace(
                Regex("<string\\s+name=\"app_name\"\\s*>.*?</string>"),
                "<string name=\"app_name\">$escaped</string>"
            )
            if (updated != content) stringsFile.writeText(updated)
        }

        // 2) app.json (displayName)
        val appJson = File(projectDir, "app.json")
        if (appJson.isFile) {
            val content = appJson.readText()
            val updated = content.replace(
                Regex("\"displayName\"\\s*:\\s*\"[^\"]*\""),
                "\"displayName\": \"${jsonEscape(newName)}\""
            )
            if (updated != content) appJson.writeText(updated)
        }
    }

    // ============================================================
    // تحديث اسم الحزمة
    // ============================================================
    private fun updatePackageName(projectDir: File, newPackage: String) {
        // 1) app.json
        val appJson = File(projectDir, "app.json")
        if (appJson.isFile) {
            val content = appJson.readText()
            val updated = content.replace(
                Regex("\"packageName\"\\s*:\\s*\"[^\"]*\""),
                "\"packageName\": \"${jsonEscape(newPackage)}\""
            )
            if (updated != content) appJson.writeText(updated)
        }

        // 2) react-native.config.js
        val rnConfig = File(projectDir, "react-native.config.js")
        if (rnConfig.isFile) {
            val content = rnConfig.readText()
            val updated = content.replace(
                Regex("packageName\\s*:\\s*'[^']*'"),
                "packageName: '$newPackage'"
            )
            if (updated != content) rnConfig.writeText(updated)
        }

        // 3) android/app/build.gradle
        val appGradle = File(projectDir, "android/app/build.gradle")
        if (appGradle.isFile) {
            val content = appGradle.readText()
            var updated = content.replace(
                Regex("namespace\\s+['\"][^'\"]+['\"]"),
                "namespace \"$newPackage\""
            )
            updated = updated.replace(
                Regex("applicationId\\s+['\"][^'\"]+['\"]"),
                "applicationId \"$newPackage\""
            )
            if (updated != content) appGradle.writeText(updated)
        }

        // 4) .klencod/project.json
        val metadata = File(projectDir, ".klencod/project.json")
        if (metadata.isFile) {
            val content = metadata.readText()
            val updated = content.replace(
                Regex("\"applicationId\"\\s*:\\s*\"[^\"]*\""),
                "\"applicationId\": \"${jsonEscape(newPackage)}\""
            )
            if (updated != content) metadata.writeText(updated)
        }

        // 5) نقل ملفات Kotlin/Java
        relocateSourceFiles(projectDir, newPackage)
    }

    // ============================================================
    // نقل ملفات المصدر
    // ============================================================
    private fun relocateSourceFiles(projectDir: File, newPackage: String) {
        val srcJavaDir = File(projectDir, "android/app/src/main/java")
        if (!srcJavaDir.isDirectory) return

        val newPackagePath = newPackage.replace('.', '/')
        val newDir = File(srcJavaDir, newPackagePath)

        val sourceFiles = mutableListOf<File>()
        collectSourceFiles(srcJavaDir, sourceFiles)
        if (sourceFiles.isEmpty()) return

        if (!newDir.exists() && !newDir.mkdirs()) return

        for (file in sourceFiles) {
            try {
                // تحديث package declaration
                var content = file.readText()
                when {
                    file.name.endsWith(".kt") -> {
                        content = content.replace(
                            Regex("(?m)^package\\s+[a-zA-Z0-9_.]+\\s*$"),
                            "package $newPackage"
                        )
                    }
                    file.name.endsWith(".java") -> {
                        content = content.replace(
                            Regex("(?m)^package\\s+[a-zA-Z0-9_.]+\\s*;"),
                            "package $newPackage;"
                        )
                    }
                }
                file.writeText(content)

                // نقل الملف
                val dest = File(newDir, file.name)
                if (file.canonicalPath != dest.canonicalPath) {
                    if (dest.exists()) dest.delete()
                    file.renameTo(dest)
                }
            } catch (_: Exception) {
            }
        }

        // تنظيف المجلدات الفارغة القديمة
        cleanupEmptyDirs(srcJavaDir)
    }

    private fun collectSourceFiles(dir: File, result: MutableList<File>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                collectSourceFiles(child, result)
            } else if (child.name.endsWith(".kt") || child.name.endsWith(".java")) {
                result.add(child)
            }
        }
    }

    private fun cleanupEmptyDirs(dir: File) {
        if (!dir.isDirectory) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                cleanupEmptyDirs(child)
                if (child.listFiles()?.isEmpty() == true) {
                    child.delete()
                }
            }
        }
    }

    // ============================================================
    // أدوات
    // ============================================================
    private fun isValidPackageName(pkg: String): Boolean {
        val parts = pkg.trim().split(".")
        if (parts.size < 2) return false
        return parts.all { part ->
            part.isNotEmpty() && part.matches(Regex("[a-z][a-z0-9_]*"))
        }
    }

    private fun parseSdk(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return value.trim()
            .split(" ")
            .firstOrNull()
            ?.toIntOrNull()
            ?.coerceIn(21, 36)
    }

    private fun findFileRecursive(dir: File, fileName: String): File? {
        if (!dir.isDirectory) return null
        val children = dir.listFiles() ?: return null
        for (child in children) {
            if (child.isFile && child.name == fileName) return child
            if (child.isDirectory) {
                val found = findFileRecursive(child, fileName)
                if (found != null) return found
            }
        }
        return null
    }

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
            .replace("\n", " ")
    }

    private fun safeDeleteRecursive(file: File?) {
        if (file == null || !file.exists()) return
        try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { safeDeleteRecursive(it) }
            }
            file.delete()
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // قائمة الملفات القابلة للتحرير
    // ============================================================
    @JvmStatic
    fun listEditableFiles(projectDir: File, limit: Int = 20): List<File> {
        if (!projectDir.isDirectory) return emptyList()

        val result = mutableListOf<File>()
        val stack = ArrayDeque<File>()
        stack.addLast(projectDir)

        while (stack.isNotEmpty() && result.size < limit) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue

            for (child in children) {
                if (result.size >= limit) break

                val name = child.name
                if (child.isDirectory) {
                    if (name in EXCLUDED_DIRS) continue
                    stack.addLast(child)
                } else if (child.isFile && isEditableFile(name)) {
                    result.add(child)
                }
            }
        }

        return result.sortedBy { it.absolutePath.lowercase(Locale.US) }
    }

    private val EXCLUDED_DIRS = setOf(
        ".git", ".gradle", ".idea", "build", ".platform", ".klencod",
        "node_modules", "__pycache__", "android/.cxx"
    )

    private fun isEditableFile(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return lower.endsWith(".js") ||
                lower.endsWith(".jsx") ||
                lower.endsWith(".ts") ||
                lower.endsWith(".tsx") ||
                lower.endsWith(".json") ||
                lower.endsWith(".java") ||
                lower.endsWith(".kt") ||
                lower.endsWith(".xml") ||
                lower.endsWith(".gradle") ||
                lower.endsWith(".properties") ||
                lower.endsWith(".md") ||
                lower.endsWith(".txt") ||
                lower.endsWith(".yml") ||
                lower.endsWith(".yaml")
    }
}