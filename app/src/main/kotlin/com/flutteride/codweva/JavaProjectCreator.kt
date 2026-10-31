package com.flutteride.codweva.java

import android.content.Context
import com.flutteride.codweva.ProjectFileService
import com.flutteride.codweva.ProjectType
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * JavaProjectCreator — المسؤول عن إنشاء مشروع Android Java جديد.
 *
 * يستدعي JavaTemplate لتوليد الملفات، ثم يتحقق من صحتها ويضبط
 * الإعدادات الإضافية (targetSdk، اسم التطبيق، إلخ).
 *
 * v1.0
 */
object JavaProjectCreator {

    // ============================================================
    // ثوابت
    // ============================================================
    private const val DEFAULT_MIN_SDK = 24
    private const val DEFAULT_TARGET_SDK = 34
    private const val DEFAULT_COMPILE_SDK = 34

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

    /**
     * ينشئ مشروع Android Java كامل.
     *
     * @param context سياق التطبيق
     * @param targetDir المجلد الجذر للمشروع (يُنشأ إن لم يكن موجوداً)
     * @param packageName اسم الحزمة (com.example.app)
     * @param appName اسم التطبيق المعروض
     * @param apiLevel Target SDK (مثل "33" أو "33 (Android 13)")
     * @return نتيجة الإنشاء
     */
    @JvmStatic
    fun create(
        context: Context?,
        targetDir: File?,
        packageName: String?,
        appName: String?,
        apiLevel: String?
    ): Result {
        // ============================================================
        // 1) التحقق من المدخلات
        // ============================================================
        if (targetDir == null) {
            return Result.fail("مجلد المشروع غير محدد")
        }

        if (targetDir.exists() && !targetDir.isDirectory) {
            return Result.fail("المسار موجود لكنه ليس مجلداً")
        }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return Result.fail("تعذر إنشاء مجلد المشروع")
        }

        if (packageName.isNullOrBlank()) {
            return Result.fail("اسم الحزمة مطلوب")
        }

        if (!isValidPackageName(packageName)) {
            return Result.fail("اسم الحزمة غير صالح: $packageName")
        }

        if (appName.isNullOrBlank()) {
            return Result.fail("اسم التطبيق مطلوب")
        }

        // ============================================================
        // 2) تنظيف API
        // ============================================================
        val targetSdk = parseSdk(apiLevel) ?: DEFAULT_TARGET_SDK

        // ============================================================
        // 3) استدعاء JavaTemplate
        // ============================================================
        val ok = try {
            JavaTemplate.write(
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
            // تنظيف جزئي
            safeDeleteRecursive(targetDir)
            return Result.fail("فشل توليد ملفات القالب")
        }

        // ============================================================
        // 4) التحقق من البنية
        // ============================================================
        val validation = validateStructure(targetDir)
        if (validation != null) {
            safeDeleteRecursive(targetDir)
            return Result.fail(validation)
        }

        // ============================================================
        // 5) ضبط targetSdk في Gradle (احتياطي)
        // ============================================================
        try {
            JavaGradleSupport.applyTargetSdk(targetDir, targetSdk)
        } catch (_: Exception) {
            // لا نفشل الإنشاء إذا فشل هذا — القالب يستخدم القيمة الافتراضية
        }

        // ============================================================
        // 6) نجاح
        // ============================================================
        return Result.ok(targetDir)
    }

    // ============================================================
    // التحقق من البنية
    // ============================================================

    /**
     * يتحقق من أن المشروع تم إنشاؤه بشكل صحيح.
     * @return null إذا كان سليماً، أو رسالة خطأ
     */
    @JvmStatic
    fun validateStructure(projectDir: File): String? {
        if (!projectDir.isDirectory) {
            return "المجلد الجذر غير موجود"
        }

        // الملفات الأساسية المطلوبة
        val required = listOf(
            "app/module.toml",
            "app/src/main/AndroidManifest.xml",
            "app/build.gradle",
            "build.gradle",
            "settings.gradle"
        )

        for (path in required) {
            val file = File(projectDir, path)
            if (!file.isFile) {
                return "ملف مفقود: $path"
            }
        }

        // التحقق من وجود MainActivity.java
        val srcJavaDir = File(projectDir, "app/src/main/java")
        if (!srcJavaDir.isDirectory) {
            return "مجلد src/main/java غير موجود"
        }

        val mainActivity = findMainActivity(srcJavaDir)
        if (mainActivity == null) {
            return "ملف MainActivity.java غير موجود"
        }

        // التحقق من وجود workflow
        val workflow = File(projectDir, ".github/workflows/build-android.yml")
        if (!workflow.isFile) {
            return "ملف workflow غير موجود"
        }

        return null
    }

    /**
     * يبحث عن أول ملف MainActivity.java في شجرة المصادر.
     */
    private fun findMainActivity(srcJavaDir: File): File? {
        if (!srcJavaDir.isDirectory) return null
        val stack = ArrayDeque<File>()
        stack.addLast(srcJavaDir)

        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                } else if (child.name == "MainActivity.java") {
                    return child
                }
            }
        }
        return null
    }

    // ============================================================
    // تحديث الإعدادات لاحقاً
    // ============================================================

    /**
     * يحدّث إعدادات المشروع بعد الإنشاء (من شاشة الإعدادات).
     *
     * @return null إذا نجح، أو رسالة خطأ
     */
    @JvmStatic
    fun updateSettings(
        projectDir: File,
        newAppName: String?,
        newPackageName: String?,
        newTargetSdk: Int?
    ): String? {
        if (!projectDir.isDirectory) {
            return "المجلد غير موجود"
        }

        // 1) اسم التطبيق
        if (!newAppName.isNullOrBlank()) {
            try {
                updateAppName(projectDir, newAppName)
            } catch (e: Exception) {
                return "فشل تحديث اسم التطبيق: ${e.message}"
            }
        }

        // 2) اسم الحزمة
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

        // 3) targetSdk
        if (newTargetSdk != null) {
            if (newTargetSdk < 21 || newTargetSdk > 36) {
                return "targetSdk خارج النطاق المسموح (21-36)"
            }
            try {
                JavaGradleSupport.applyTargetSdk(projectDir, newTargetSdk)
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
        val stringsFile = File(
            projectDir,
            "app/src/main/res/values/strings.xml"
        )
        if (!stringsFile.isFile) return

        val content = stringsFile.readText()
        val escaped = xmlEscape(newName)
        val updated = content.replace(
            Regex("<string\\s+name=\"app_name\"\\s*>.*?</string>"),
            "<string name=\"app_name\">$escaped</string>"
        )
        if (updated != content) {
            stringsFile.writeText(updated)
        }
    }

    // ============================================================
    // تحديث اسم الحزمة
    // ============================================================

    private fun updatePackageName(projectDir: File, newPackage: String) {
        val service = ProjectFileService(projectDir)

        // 1) module.toml
        val moduleToml = File(projectDir, "app/module.toml")
        if (moduleToml.isFile) {
            val content = moduleToml.readText()
            val updated = content.replace(
                Regex("namespace\\s*=\\s*\"[^\"]+\""),
                "namespace = \"$newPackage\""
            )
            if (updated != content) moduleToml.writeText(updated)
        }

        // 2) AndroidManifest.xml
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        if (manifest.isFile) {
            val content = manifest.readText()
            val updated = content.replace(
                Regex("package\\s*=\\s*\"[^\"]+\""),
                "package=\"$newPackage\""
            )
            if (updated != content) manifest.writeText(updated)
        }

        // 3) app/build.gradle
        val appGradle = File(projectDir, "app/build.gradle")
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

        // 4) نقل مجلد الـ Java
        relocateJavaSources(projectDir, newPackage)
    }

    /**
     * ينقل ملفات Java إلى المجلد الجديد للحزمة.
     */
    private fun relocateJavaSources(projectDir: File, newPackage: String) {
        val srcJavaDir = File(projectDir, "app/src/main/java")
        if (!srcJavaDir.isDirectory) return

        val newPackagePath = newPackage.replace('.', '/')
        val newDir = File(srcJavaDir, newPackagePath)

        // اجمع كل ملفات Java
        val javaFiles = mutableListOf<File>()
        collectJavaFiles(srcJavaDir, javaFiles)
        if (javaFiles.isEmpty()) return

        if (!newDir.exists() && !newDir.mkdirs()) return

        for (file in javaFiles) {
            try {
                // 1) تحديث محتوى الملف
                var content = file.readText()
                val packagePattern = Regex(
                    "^\\s*package\\s+[a-zA-Z0-9_.]+\\s*;",
                    RegexOption.MULTILINE
                )
                if (packagePattern.containsMatchIn(content)) {
                    content = content.replace(packagePattern, "package $newPackage;")
                    file.writeText(content)
                }

                // 2) نقل الملف
                val destination = File(newDir, file.name)
                if (file.canonicalPath != destination.canonicalPath) {
                    if (destination.exists()) destination.delete()
                    file.renameTo(destination)
                }
            } catch (_: Exception) {
                // تجاهل — قد يكون الملف مقفلاً
            }
        }

        // نظّف المجلدات الفارغة القديمة
        cleanupEmptyDirs(srcJavaDir)
    }

    private fun collectJavaFiles(dir: File, result: MutableList<File>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                collectJavaFiles(child, result)
            } else if (child.name.endsWith(".java")) {
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
                if (child.listFiles()?.isEmpty() == true && child != dir) {
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
            part.isNotEmpty() &&
                    part.matches(Regex("[a-z][a-z0-9_]*"))
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

    private fun xmlEscape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
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

    /**
     * يُرجع قائمة الملفات النصية القابلة للتحرير.
     * مفيد لعرضها في الملفات الحديثة.
     */
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
                // تجاهل مجلدات البناء
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
        "node_modules", "__pycache__"
    )

    private fun isEditableFile(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return lower.endsWith(".java") ||
                lower.endsWith(".xml") ||
                lower.endsWith(".gradle") ||
                lower.endsWith(".toml") ||
                lower.endsWith(".properties") ||
                lower.endsWith(".json") ||
                lower.endsWith(".md") ||
                lower.endsWith(".txt") ||
                lower.endsWith(".yml") ||
                lower.endsWith(".yaml")
    }
}