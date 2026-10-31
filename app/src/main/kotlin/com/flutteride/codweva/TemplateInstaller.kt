package com.flutteride.codweva

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * TemplateInstaller v5.1 — استبدال ذكي بدون اكتشاف مسبق.
 *
 * الجديد في v5.1:
 *   - حقن main.yml إجبارياً (يحذف القديم ويضع الجديد)
 */
object TemplateInstaller {

    private const val TAG = "TemplateInstaller"
    private const val SHOWCASE_DIR = "assets/showcase"
    private const val ICON_NAME = "icon.png"
    private const val PREVIEW_PREFIX = "preview_"
    private const val MAX_PREVIEWS = 10
    private const val MAX_SCAN_FILE_SIZE = 5L * 1024L * 1024L

    private val TEXT_EXTENSIONS = hashSetOf(
        "dart", "kt", "kts", "java", "xml", "gradle", "yaml", "yml",
        "json", "properties", "md", "txt", "html", "css", "js", "pro",
        "plist", "pbxproj", "gitignore", "metadata", "lock"
    )

    // ============================================================
    // التحقق والتنظيف
    // ============================================================
    @JvmStatic
    fun validateAppName(appName: String?): String? {
        if (appName == null) return "اسم التطبيق فارغ"
        val trimmed = appName.trim()
        if (trimmed.isEmpty()) return "اسم التطبيق فارغ"
        if (trimmed.length > 60) return "اسم التطبيق طويل جدًا (الحد الأقصى 60 حرفًا)"

        for (c in trimmed) {
            if (c.code < 0x20 || c.code == 0x7F) {
                return "اسم التطبيق يحتوي على محارف غير مرئية"
            }
        }

        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains(":") ||
            trimmed.contains("*") || trimmed.contains("?") || trimmed.contains("\"") ||
            trimmed.contains("<") || trimmed.contains(">") || trimmed.contains("|")
        ) {
            return "اسم التطبيق يحتوي على محارف غير مسموح بها"
        }

        if (trimmed.replace(Regex("[.\\s]"), "").isEmpty()) {
            return "اسم التطبيق لا يمكن أن يكون نقاطًا أو مسافات فقط"
        }

        return null
    }

    private fun sanitizeAppName(appName: String?): String {
        if (appName == null) return "App"
        var result = appName.trim()

        val sb = StringBuilder(result.length)
        for (c in result) {
            sb.append(if (c.code < 0x20 || c.code == 0x7F) ' ' else c)
        }
        result = sb.toString().replace(Regex("\\s+"), " ").trim()

        if (result.length > 60) result = result.substring(0, 60).trim()
        if (result.isEmpty()) result = "App"

        return result
    }

    @JvmStatic
    fun sanitizeAppNameForDisplay(appName: String?): String = sanitizeAppName(appName)

    // ============================================================
    // التثبيت
    // ============================================================
    @JvmStatic
    fun install(
        context: Context?,
        template: TemplateInfo?,
        targetDir: File?,
        appName: String?,
        packageName: String?,
        apiLevel: String?
    ): Boolean {
        if (context == null || template == null || targetDir == null) return false
        if (packageName == null) return false

        if (!isValidPackageName(packageName)) {
            Log.w(TAG, "اسم حزمة غير صالح: $packageName")
            return false
        }

        val nameError = validateAppName(appName)
        if (nameError != null) {
            Log.w(TAG, "اسم التطبيق غير صالح: $nameError")
            return false
        }

        return try {
            if (!targetDir.exists() && !targetDir.mkdirs()) return false

            // 1) فك الـ ZIP
            val zipStream: InputStream = if (template.isBuiltIn) {
                context.assets.open(template.assetsPath ?: return false)
            } else {
                FileInputStream(template.zipFile)
            }
            extractZip(zipStream, targetDir)
            zipStream.close()

            // 2) استبدال placeholders القديمة
            replacePlaceholders(targetDir, appName, packageName, apiLevel)

            // 3) الاستبدال الذكي
            val newProjectName = packageNameToProjectName(packageName)
            val safeAppName = sanitizeAppName(appName)

            val updatedFiles = smartRename(
                targetDir, packageName, safeAppName, newProjectName
            )
            Log.i(TAG, "تم تعديل $updatedFiles ملف")

            // 4) نقل مجلدات الحزمة
            relocatePackageFoldersSmart(targetDir, packageName)

            // 5) حقن main.yml
            injectWorkflow(targetDir)

            // 6) ضمان Gradle Wrapper
            GradleWrapperWriter.ensureGradleWrapper(context, targetDir)

            // 7) تطبيق targetSdk
            applyApiLevel(targetDir, apiLevel)

            // 8) حذف showcase
            removeShowcaseAssets(targetDir)

            true
        } catch (e: Exception) {
            Log.e(TAG, "فشل التثبيت: ${e.message}", e)
            false
        }
    }

    // ============================================================
    // حقن main.yml
    // ============================================================
    private fun injectWorkflow(projectDir: File) {
        if (!projectDir.isDirectory) return

        try {
            val githubDir = File(projectDir, ".github")
            val workflowsDir = File(githubDir, "workflows")

            if (!workflowsDir.exists() && !workflowsDir.mkdirs()) {
                Log.w(TAG, "تعذر إنشاء مجلد .github/workflows")
                return
            }

            val workflowFile = File(workflowsDir, "main.yml")
            if (workflowFile.exists() && !workflowFile.delete()) {
                Log.w(TAG, "تعذر حذف main.yml القديم")
            }

            writeTextFile(workflowFile, buildDefaultWorkflow())
            Log.i(TAG, "تم حقن main.yml جديد")
        } catch (e: Exception) {
            Log.w(TAG, "فشل حقن workflow: ${e.message}")
        }
    }

    private fun buildDefaultWorkflow(): String {
        return """
            name: Build Flutter APK

            on:
              workflow_dispatch:
                inputs:
                  flutter_version:
                    description: 'Flutter version'
                    required: true
                    default: '3.47.2'

            jobs:
              build:
                runs-on: ubuntu-24.04
                steps:
                  - name: Checkout
                    uses: actions/checkout@v5

                  - name: Setup Gradle Cache
                    uses: actions/cache@v5
                    with:
                      path: |
                        ~/.gradle/caches
                        ~/.gradle/wrapper
                      key: ${'$'}{{ runner.os }}-gradle-${'$'}{{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
                      restore-keys: |
                        ${'$'}{{ runner.os }}-gradle-

                  - name: Setup Java 17
                    uses: actions/setup-java@v5
                    with:
                      distribution: temurin
                      java-version: '17'

                  - name: Setup Flutter
                    uses: subosito/flutter-action@v2
                    with:
                      flutter-version: ${'$'}{{ github.event.inputs.flutter_version }}
                      cache: true
                      channel: stable

                  - name: Get dependencies
                    run: flutter pub get

                  - name: Build APK
                    run: flutter build apk --release

                  - name: Upload APK
                    uses: actions/upload-artifact@v4
                    with:
                      name: app-release
                      path: build/app/outputs/flutter-apk/app-release.apk
                      if-no-files-found: error
                      retention-days: 7
        """.trimIndent() + "\n"
    }

    // ============================================================
    // تطبيق targetSdk
    // ============================================================
    private fun applyApiLevel(projectDir: File, apiLevel: String?) {
        if (apiLevel.isNullOrBlank() || !apiLevel.matches(Regex("\\d+"))) return

        val buildGradle = File(projectDir, "android/app/build.gradle")
        if (!buildGradle.isFile) return

        try {
            val content = readTextFile(buildGradle) ?: return
            if (content.isEmpty()) return

            var updated = content.replace(
                Regex("targetSdk\\s*=\\s*\\d+"),
                "targetSdk = $apiLevel"
            )
            updated = updated.replace(
                Regex("targetSdkVersion\\s+\\d+"),
                "targetSdkVersion $apiLevel"
            )

            if (updated != content) {
                writeTextFile(buildGradle, updated)
                Log.i(TAG, "تم تطبيق targetSdk = $apiLevel")
            }
        } catch (e: Exception) {
            Log.w(TAG, "فشل تطبيق API: ${e.message}")
        }
    }

    // ============================================================
    // الاستبدال الذكي
    // ============================================================
    private fun smartRename(
        projectDir: File,
        newPackage: String,
        newAppName: String,
        newProjectName: String
    ): Int {
        val textFiles = mutableListOf<File>()
        collectTextFiles(projectDir, textFiles)

        var updatedCount = 0

        for (file in textFiles) {
            try {
                val content = readTextFile(file) ?: continue
                if (content.isEmpty()) continue

                var updated = content

                // اسم الحزمة
                updated = replaceRegexValue(updated,
                    "(applicationId\\s+)([\"'])([^\"']+)([\"'])", newPackage)
                updated = replaceRegexValue(updated,
                    "(namespace\\s+)([\"'])([^\"']+)([\"'])", newPackage)
                updated = replaceRegexValue(updated,
                    "(applicationId\\s*=\\s*)([\"'])([^\"']+)([\"'])", newPackage)
                updated = replaceRegexValue(updated,
                    "(namespace\\s*=\\s*)([\"'])([^\"']+)([\"'])", newPackage)
                updated = replaceRegexValue(updated,
                    "(<manifest[^>]*?package\\s*=\\s*)([\"'])([^\"']+)([\"'])", newPackage)
                updated = replacePackageDeclaration(updated, newPackage)

                // اسم التطبيق
                updated = replaceRegexValue(updated,
                    "(android:label\\s*=\\s*)([\"'])([^\"']*)([\"'])", newAppName)
                updated = replaceStringResource(updated, "app_name", newAppName)
                updated = replaceDartTitle(updated, newAppName)

                // اسم المشروع
                updated = replacePubspecName(updated, newProjectName)
                updated = replaceSettingsGradleName(updated, newProjectName)

                if (updated != content) {
                    writeTextFile(file, updated)
                    updatedCount++
                }
            } catch (e: Exception) {
                Log.w(TAG, "فشل تعديل ${file.name}: ${e.message}")
            }
        }

        return updatedCount
    }

    private fun replaceRegexValue(
        content: String,
        pattern: String,
        newValue: String
    ): String {
        return try {
            val p = Regex(pattern)
            p.replace(content) { matchResult ->
                val prefix = matchResult.groupValues[1]
                val quote = matchResult.groupValues[2]
                val replacement = escapeForContext(newValue, quote)
                "$prefix$quote$replacement$quote"
            }
        } catch (_: Exception) {
            content
        }
    }

    private fun replacePackageDeclaration(content: String, newPackage: String): String {
        return try {
            val p = Regex("^(\\s*package\\s+)([a-zA-Z][a-zA-Z0-9_.]+)(\\s*;?\\s*)\$",
                RegexOption.MULTILINE)
            p.replace(content) { matchResult ->
                val prefix = matchResult.groupValues[1]
                val suffix = matchResult.groupValues[3]
                "$prefix$newPackage$suffix"
            }
        } catch (_: Exception) {
            content
        }
    }

    private fun replaceStringResource(content: String, name: String, newValue: String): String {
        return try {
            val p = Regex(
                "(<string\\s+name\\s*=\\s*[\"']${Regex.escape(name)}[\"']\\s*>)([^<]*)(</string>)"
            )
            p.replace(content) { matchResult ->
                val prefix = matchResult.groupValues[1]
                val suffix = matchResult.groupValues[3]
                "$prefix${escapeXml(newValue)}$suffix"
            }
        } catch (_: Exception) {
            content
        }
    }

    private fun replaceDartTitle(content: String, newValue: String): String {
        return try {
            val p = Regex("(title\\s*:\\s*)([\"'])((?:\\\\.|(?!\\2).)*)(\\2)")
            p.replace(content) { matchResult ->
                val prefix = matchResult.groupValues[1]
                val quote = matchResult.groupValues[2]
                val replacement = escapeDart(newValue, quote)
                "$prefix$quote$replacement$quote"
            }
        } catch (_: Exception) {
            content
        }
    }

    private fun replacePubspecName(content: String, newValue: String): String {
        return try {
            val p = Regex("^(name\\s*:\\s*)([^\\s#]+)(\\s*)\$", RegexOption.MULTILINE)
            p.replace(content) { matchResult ->
                val prefix = matchResult.groupValues[1]
                val suffix = matchResult.groupValues[3]
                "$prefix$newValue$suffix"
            }
        } catch (_: Exception) {
            content
        }
    }

    private fun replaceSettingsGradleName(content: String, newValue: String): String {
        return try {
            val p = Regex("(rootProject\\.name\\s*=\\s*)([\"'])([^\"']+)([\"'])")
            p.replace(content) { matchResult ->
                val prefix = matchResult.groupValues[1]
                val quote = matchResult.groupValues[2]
                "$prefix$quote$newValue$quote"
            }
        } catch (_: Exception) {
            content
        }
    }

    // ============================================================
    // نقل مجلدات الحزمة
    // ============================================================
    private fun relocatePackageFoldersSmart(projectDir: File, newPackage: String) {
        val newPath = newPackage.replace('.', '/')

        val roots = arrayOf(
            "android/app/src/main/kotlin/",
            "android/app/src/main/java/",
            "android/app/src/debug/kotlin/",
            "android/app/src/debug/java/",
            "android/app/src/profile/kotlin/",
            "android/app/src/profile/java/"
        )

        for (root in roots) {
            val rootDir = File(projectDir, root)
            if (!rootDir.isDirectory) continue

            val sourceFiles = mutableListOf<File>()
            collectSourceFiles(rootDir, sourceFiles)
            if (sourceFiles.isEmpty()) continue

            for (sourceFile in sourceFiles) {
                try {
                    val fileName = sourceFile.name
                    val newFile = File(rootDir, "$newPath/$fileName")

                    if (sourceFile.canonicalPath == newFile.canonicalPath) continue

                    newFile.parentFile?.let { parent ->
                        if (!parent.exists()) parent.mkdirs()
                    }

                    if (sourceFile.renameTo(newFile)) {
                        Log.i(TAG, "نقل: ${sourceFile.name} → $newPath")
                    } else {
                        copyFile(sourceFile, newFile)
                        sourceFile.delete()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "فشل نقل ${sourceFile.name}: ${e.message}")
                }
            }

            deleteEmptyDirs(rootDir, rootDir)
        }
    }

    private fun collectSourceFiles(dir: File, result: MutableList<File>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                collectSourceFiles(child, result)
            } else {
                val name = child.name.lowercase(Locale.US)
                if (name.endsWith(".kt") || name.endsWith(".java")) {
                    result.add(child)
                }
            }
        }
    }

    private fun deleteEmptyDirs(dir: File, stopAt: File) {
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) deleteEmptyDirs(child, stopAt)
        }
        if (dir == stopAt) return
        val remaining = dir.listFiles()
        if (remaining != null && remaining.isEmpty()) {
            dir.delete()
        }
    }

    // ============================================================
    // استبدال placeholders
    // ============================================================
    private fun replacePlaceholders(
        projectDir: File,
        appName: String?,
        packageName: String?,
        apiLevel: String?
    ) {
        val safeAppName = appName ?: "App"
        val safePackage = packageName ?: "com.example.app"
        val safeApi = apiLevel ?: "33"
        val projectNameSnake = packageNameToProjectName(packageName)

        val targets = mutableListOf<File>()
        addIfExists(targets, File(projectDir, "pubspec.yaml"))
        addIfExists(targets, File(projectDir, "lib/main.dart"))
        addIfExists(targets, File(projectDir, "android/app/build.gradle"))
        addIfExists(targets, File(projectDir, "android/app/build.gradle.kts"))
        addIfExists(targets, File(projectDir, "android/build.gradle"))
        addIfExists(targets, File(projectDir, "android/settings.gradle"))
        addIfExists(targets, File(projectDir, "android/app/src/main/AndroidManifest.xml"))

        for (file in targets) {
            try {
                val content = readTextFile(file) ?: continue
                val updated = content
                    .replace("{{APP_NAME}}", safeAppName)
                    .replace("{{PACKAGE_NAME}}", safePackage)
                    .replace("{{API_LEVEL}}", safeApi)
                    .replace("{{PROJECT_NAME}}", projectNameSnake)

                if (updated != content) writeTextFile(file, updated)
            } catch (_: Exception) {
            }
        }
    }

    // ============================================================
    // أدوات مساعدة
    // ============================================================
    private fun packageNameToProjectName(packageName: String?): String {
        if (packageName.isNullOrEmpty()) return "flutter_app"

        val parts = packageName.split(".")
        var last = parts.last()

        last = last.lowercase(Locale.US).replace(Regex("[^a-z0-9_]"), "_")

        if (last.isEmpty() || last[0].isDigit()) {
            last = "app_$last"
        }
        if (last.length > 40) last = last.substring(0, 40)
        if (isDartReserved(last)) last = "${last}_app"

        return last
    }

    private fun isDartReserved(name: String): Boolean {
        return when (name) {
            "abstract", "as", "assert", "async", "await",
            "break", "case", "catch", "class", "const",
            "continue", "default", "do", "else", "enum",
            "export", "extends", "false", "final", "finally",
            "for", "if", "import", "in", "is", "library",
            "new", "null", "on", "return", "set",
            "static", "super", "switch", "this", "throw",
            "true", "try", "var", "void", "while",
            "with", "yield", "test", "main" -> true
            else -> false
        }
    }

    private fun isValidPackageName(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        val parts = packageName.split(".")
        if (parts.size < 2) return false
        return parts.all { it.matches(Regex("[a-z][a-z0-9_]*")) }
    }

    private fun escapeForContext(value: String?, quote: String): String {
        if (value == null) return ""
        return if ("\"" == quote) {
            value.replace("\\", "\\\\").replace("\"", "\\\"")
        } else {
            value.replace("\\", "\\\\").replace("'", "\\'")
        }
    }

    private fun escapeXml(value: String?): String {
        if (value == null) return ""
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun escapeDart(value: String?, quote: String): String {
        if (value == null) return ""
        return if ("\"" == quote) {
            value.replace("\\", "\\\\").replace("\"", "\\\"")
        } else {
            value.replace("\\", "\\\\").replace("'", "\\'")
        }
    }

    private fun readTextFile(file: File?): String? {
        if (file == null || !file.exists() || !file.isFile) return null
        if (file.length() > MAX_SCAN_FILE_SIZE) return null
        if (looksBinary(file)) return null

        return try {
            BufferedReader(FileReader(file)).use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(4096)
                var n: Int
                while (reader.read(buf).also { n = it } != -1) {
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun looksBinary(file: File): Boolean {
        return try {
            FileInputStream(file).use { input ->
                val buf = ByteArray(512)
                val n = input.read(buf)
                if (n <= 0) return false
                for (i in 0 until n) {
                    if (buf[i] == 0.toByte()) return true
                }
                false
            }
        } catch (_: Exception) {
            true
        }
    }

    @Throws(IOException::class)
    private fun writeTextFile(file: File, content: String?) {
        file.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }
        FileWriter(file).use { writer ->
            writer.write(content ?: "")
            writer.flush()
        }
    }

    private fun collectTextFiles(root: File, result: MutableList<File>) {
        val children = root.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                val name = child.name.lowercase(Locale.US)
                if (name == ".git" || name == "build" ||
                    name == ".dart_tool" || name == ".gradle" ||
                    name == "node_modules"
                ) {
                    continue
                }
                collectTextFiles(child, result)
            } else if (isTextFile(child)) {
                result.add(child)
            }
        }
    }

    private fun isTextFile(file: File): Boolean {
        val name = file.name.lowercase(Locale.US)
        if (name == ".gitignore" || name == ".metadata" ||
            name == "pubspec.lock" || name == "gradlew" ||
            name == "gradlew.bat"
        ) {
            return true
        }
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return false
        val ext = name.substring(dot + 1)
        return ext in TEXT_EXTENSIONS
    }

    private fun addIfExists(list: MutableList<File>, file: File?) {
        if (file != null && file.exists() && file.isFile) list.add(file)
    }

    @Throws(IOException::class)
    private fun copyFile(source: File, target: File) {
        target.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                }
            }
        }
    }

    private fun deleteRecursive(file: File?) {
        if (file == null || !file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        file.delete()
    }

    // ============================================================
    // استخراج ZIP
    // ============================================================
    @Throws(IOException::class)
    private fun extractZip(input: InputStream, destination: File) {
        ZipInputStream(input).use { zip ->
            val buffer = ByteArray(8192)
            var entry: ZipEntry?

            while (zip.nextEntry.also { entry = it } != null) {
                var entryName = entry!!.name?.replace('\\', '/') ?: ""
                if (entryName.isEmpty()) {
                    zip.closeEntry()
                    continue
                }

                if (entryName.startsWith("__MACOSX/") ||
                    entryName == ".DS_Store"
                ) {
                    zip.closeEntry()
                    continue
                }

                if (entryName.startsWith("$SHOWCASE_DIR/") ||
                    entryName == SHOWCASE_DIR
                ) {
                    zip.closeEntry()
                    continue
                }

                while (entryName.startsWith("./")) entryName = entryName.substring(2)
                while (entryName.startsWith("/")) entryName = entryName.substring(1)

                if (entryName.isEmpty() || entryName.contains("..")) {
                    zip.closeEntry()
                    continue
                }

                val output = File(destination, entryName)
                val destPath = destination.canonicalPath
                val outPath = output.canonicalPath
                if (outPath != destPath && !outPath.startsWith("$destPath${File.separator}")) {
                    zip.closeEntry()
                    continue
                }

                if (entry!!.isDirectory) {
                    if (!output.exists()) output.mkdirs()
                } else {
                    output.parentFile?.let { parent ->
                        if (!parent.exists()) parent.mkdirs()
                    }
                    FileOutputStream(output).use { fos ->
                        var count: Int
                        while (zip.read(buffer).also { count = it } != -1) {
                            fos.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    // ============================================================
    // حذف showcase
    // ============================================================
    private fun removeShowcaseAssets(projectDir: File) {
        val showcaseDir = File(projectDir, SHOWCASE_DIR)
        if (showcaseDir.exists()) deleteRecursive(showcaseDir)

        val assetsDir = File(projectDir, "assets")
        if (assetsDir.exists() && isEmptyDir(assetsDir)) assetsDir.delete()

        val pubspecFile = File(projectDir, "pubspec.yaml")
        if (!pubspecFile.exists()) return

        try {
            val content = readTextFile(pubspecFile) ?: return
            val updated = removeShowcaseFromPubspec(content)
            if (updated != content) writeTextFile(pubspecFile, updated)
        } catch (_: Exception) {
        }
    }

    private fun isEmptyDir(dir: File): Boolean {
        val children = dir.listFiles()
        return children == null || children.isEmpty()
    }

    private fun removeShowcaseFromPubspec(content: String?): String {
        if (content == null) return ""
        val lines = content.split("\n")
        val result = StringBuilder()
        var insideAssets = false
        var hasOtherAssets = false
        var assetsLineStart = -1

        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trim()

            if (!insideAssets && trimmed == "assets:") {
                insideAssets = true
                assetsLineStart = result.length
                result.append(line)
                if (i < lines.size - 1) result.append("\n")
                continue
            }

            if (insideAssets) {
                if (trimmed.startsWith("- ")) {
                    val path = trimmed.substring(2).trim()
                    if (!path.startsWith("assets/showcase")) {
                        hasOtherAssets = true
                        result.append(line)
                        if (i < lines.size - 1) result.append("\n")
                    }
                } else {
                    insideAssets = false
                    if (!hasOtherAssets && assetsLineStart >= 0) {
                        result.setLength(assetsLineStart)
                    }
                    assetsLineStart = -1
                    result.append(line)
                    if (i < lines.size - 1) result.append("\n")
                }
            } else {
                result.append(line)
                if (i < lines.size - 1) result.append("\n")
            }
        }

        if (insideAssets && !hasOtherAssets && assetsLineStart >= 0) {
            result.setLength(assetsLineStart)
        }
        return result.toString()
    }

    // ============================================================
    // استخراج showcase
    // ============================================================
    @JvmStatic
    @Throws(IOException::class)
    fun extractShowcase(context: Context?, template: TemplateInfo?): File? {
        if (context == null || template == null) return null

        val cacheDir = File(context.cacheDir, "template_showcase/${template.id}")
        if (cacheDir.exists()) deleteRecursive(cacheDir)
        if (!cacheDir.mkdirs()) throw IOException("تعذر إنشاء مجلد cache")

        val zipStream: InputStream = if (template.isBuiltIn) {
            context.assets.open(template.assetsPath ?: throw IOException("assetsPath null"))
        } else {
            FileInputStream(template.zipFile)
        }

        ZipInputStream(zipStream).use { zip ->
            val buffer = ByteArray(8192)
            var entry: ZipEntry?

            while (zip.nextEntry.also { entry = it } != null) {
                var name = entry!!.name?.replace('\\', '/') ?: ""
                while (name.startsWith("./")) name = name.substring(2)

                if (!name.startsWith("$SHOWCASE_DIR/") && !name.startsWith("showcase/")) {
                    zip.closeEntry()
                    continue
                }
                if (entry!!.isDirectory) {
                    zip.closeEntry()
                    continue
                }

                val fileName = name.substring(name.lastIndexOf('/') + 1)
                if (fileName.isEmpty()) {
                    zip.closeEntry()
                    continue
                }

                val output = File(cacheDir, fileName)
                FileOutputStream(output).use { fos ->
                    var count: Int
                    while (zip.read(buffer).also { count = it } != -1) {
                        fos.write(buffer, 0, count)
                    }
                }
                zip.closeEntry()
            }
        }

        val iconFile = File(cacheDir, ICON_NAME)
        if (iconFile.exists()) {
            template.iconFile = iconFile
        } else {
            cacheDir.listFiles()?.firstOrNull { it.name.startsWith("icon") }?.let {
                template.iconFile = it
            }
        }

        template.previewFiles.clear()
        for (i in 1..MAX_PREVIEWS) {
            val preview = File(cacheDir, "$PREVIEW_PREFIX$i.png")
            if (preview.exists()) {
                template.previewFiles.add(preview)
                continue
            }
            val previewJpg = File(cacheDir, "$PREVIEW_PREFIX$i.jpg")
            if (previewJpg.exists()) template.previewFiles.add(previewJpg)
        }

        if (template.previewFiles.isEmpty()) {
            val files = cacheDir.listFiles()
            if (files != null) {
                val previews = mutableListOf<File>()
                for (f in files) {
                    val n = f.name.lowercase(Locale.US)
                    if (n.startsWith(PREVIEW_PREFIX) &&
                        (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"))
                    ) {
                        previews.add(f)
                    }
                }
                previews.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                template.previewFiles.addAll(previews)
            }
        }

        return cacheDir
    }
}