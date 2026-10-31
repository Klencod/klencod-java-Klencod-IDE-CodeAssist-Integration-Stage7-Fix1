package com.flutteride.codweva.java

import com.flutteride.codweva.ProjectFileService
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * KotlinGradleSupport — يضبط ملفات Gradle لمشاريع Android Kotlin.
 *
 * v1.0 — نسخة Kotlin من JavaGradleSupport:
 *   - نفس الوظائف
 *   - لكن يقرأ/يكتب Kotlin plugin و kotlinOptions
 *
 * الفرق عن JavaGradleSupport:
 *   - لا فرق فعلياً في الـ regex (نفس صيغة Gradle)
 *   - لكن يضيف قراءة kotlinOptions.jvmTarget
 */
object KotlinGradleSupport {

    // ============================================================
    // applyTargetSdk
    // ============================================================
    /**
     * يحدّث targetSdk في:
     *   - app/build.gradle
     *   - app/module.toml
     *
     * @return true إذا تم التحديث فعلاً
     */
    @JvmStatic
    @Throws(IOException::class)
    fun applyTargetSdk(projectDir: File, targetSdk: Int): Boolean {
        if (targetSdk !in 21..36) {
            throw IOException("targetSdk خارج النطاق: $targetSdk")
        }

        var changed = false

        // 1) app/build.gradle
        val buildGradle = File(projectDir, "app/build.gradle")
        if (buildGradle.isFile) {
            val content = buildGradle.readText()
            val updated = replaceSdkValue(content, "targetSdk", targetSdk)
            if (updated != content) {
                buildGradle.writeText(updated)
                changed = true
            }
        }

        // 2) module.toml
        val moduleToml = File(projectDir, "app/module.toml")
        if (moduleToml.isFile) {
            val content = moduleToml.readText()
            val updated = content.replace(
                Regex("targetSdk\\s*=\\s*\\d+"),
                "targetSdk = $targetSdk"
            )
            if (updated != content) {
                moduleToml.writeText(updated)
                changed = true
            }
        }

        return changed
    }

    // ============================================================
    // applyMinSdk
    // ============================================================
    @JvmStatic
    @Throws(IOException::class)
    fun applyMinSdk(projectDir: File, minSdk: Int): Boolean {
        if (minSdk !in 21..36) {
            throw IOException("minSdk خارج النطاق: $minSdk")
        }

        var changed = false

        val buildGradle = File(projectDir, "app/build.gradle")
        if (buildGradle.isFile) {
            val content = buildGradle.readText()
            val updated = replaceSdkValue(content, "minSdk", minSdk)
            if (updated != content) {
                buildGradle.writeText(updated)
                changed = true
            }
        }

        val moduleToml = File(projectDir, "app/module.toml")
        if (moduleToml.isFile) {
            val content = moduleToml.readText()
            val updated = content.replace(
                Regex("minSdk\\s*=\\s*\\d+"),
                "minSdk = $minSdk"
            )
            if (updated != content) {
                moduleToml.writeText(updated)
                changed = true
            }
        }

        return changed
    }

    // ============================================================
    // applyVersionName
    // ============================================================
    @JvmStatic
    @Throws(IOException::class)
    fun applyVersionName(projectDir: File, versionName: String): Boolean {
        if (versionName.isBlank()) {
            throw IOException("versionName فارغ")
        }

        var changed = false

        val buildGradle = File(projectDir, "app/build.gradle")
        if (buildGradle.isFile) {
            val content = buildGradle.readText()
            val updated = content.replace(
                Regex("versionName\\s+['\"][^'\"]*['\"]"),
                "versionName \"$versionName\""
            )
            if (updated != content) {
                buildGradle.writeText(updated)
                changed = true
            }
        }

        val moduleToml = File(projectDir, "app/module.toml")
        if (moduleToml.isFile) {
            val content = moduleToml.readText()
            val updated = content.replace(
                Regex("versionName\\s*=\\s*\"[^\"]*\""),
                "versionName = \"$versionName\""
            )
            if (updated != content) {
                moduleToml.writeText(updated)
                changed = true
            }
        }

        return changed
    }

    // ============================================================
    // applyVersionCode
    // ============================================================
    @JvmStatic
    @Throws(IOException::class)
    fun applyVersionCode(projectDir: File, versionCode: Int): Boolean {
        if (versionCode < 1) {
            throw IOException("versionCode يجب أن يكون >= 1")
        }

        var changed = false

        val buildGradle = File(projectDir, "app/build.gradle")
        if (buildGradle.isFile) {
            val content = buildGradle.readText()
            val updated = content.replace(
                Regex("versionCode\\s+\\d+"),
                "versionCode $versionCode"
            )
            if (updated != content) {
                buildGradle.writeText(updated)
                changed = true
            }
        }

        val moduleToml = File(projectDir, "app/module.toml")
        if (moduleToml.isFile) {
            val content = moduleToml.readText()
            val updated = content.replace(
                Regex("versionCode\\s*=\\s*\\d+"),
                "versionCode = $versionCode"
            )
            if (updated != content) {
                moduleToml.writeText(updated)
                changed = true
            }
        }

        return changed
    }

    // ============================================================
    // applyJvmTarget (خاص بـ Kotlin)
    // ============================================================
    /**
     * يحدّث kotlinOptions.jvmTarget في app/build.gradle.
     * القيم المدعومة: "1.8" أو "17".
     */
    @JvmStatic
    @Throws(IOException::class)
    fun applyJvmTarget(projectDir: File, jvmTarget: String): Boolean {
        if (jvmTarget.isBlank()) {
            throw IOException("jvmTarget فارغ")
        }

        val buildGradle = File(projectDir, "app/build.gradle")
        if (!buildGradle.isFile) return false

        val content = buildGradle.readText()
        val updated = content.replace(
            Regex("jvmTarget\\s*=\\s*['\"][^'\"]*['\"]"),
            "jvmTarget = '$jvmTarget'"
        )

        if (updated != content) {
            buildGradle.writeText(updated)
            return true
        }

        return false
    }

    // ============================================================
    // ensureWorkflowExists
    // ============================================================
    /**
     * يضمن وجود `.github/workflows/build-android.yml`.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun ensureWorkflowExists(projectDir: File, targetSdk: Int = 34): Boolean {
        val workflowsDir = File(projectDir, ".github/workflows")
        if (!workflowsDir.exists() && !workflowsDir.mkdirs()) {
            throw IOException("تعذر إنشاء مجلد .github/workflows")
        }

        val workflowFile = File(workflowsDir, "build-android.yml")

        if (!workflowFile.isFile) {
            workflowFile.writeText(KotlinBuildWorkflow.build(targetSdk))
            return true
        }

        // موجود مسبقاً — تحقق من workflow_dispatch
        val content = workflowFile.readText()
        if (!KotlinBuildWorkflow.hasWorkflowDispatch(content)) {
            workflowFile.writeText(KotlinBuildWorkflow.build(targetSdk))
            return true
        }

        return false
    }

    // ============================================================
    // قراءة الإعدادات الحالية
    // ============================================================
    /**
     * يقرأ قيم SDK من app/build.gradle.
     * إذا لم يجدها، يقرأها من module.toml.
     */
    @JvmStatic
    fun readSdkValues(projectDir: File): SdkValues {
        var minSdk = 24
        var targetSdk = 34
        var compileSdk = 34
        var versionCode = 1
        var versionName = "1.0"
        var jvmTarget = "17"

        // 1) من build.gradle
        val buildGradle = File(projectDir, "app/build.gradle")
        if (buildGradle.isFile) {
            val content = buildGradle.readText()
            minSdk = readIntFromGradle(content, "minSdk") ?: minSdk
            targetSdk = readIntFromGradle(content, "targetSdk") ?: targetSdk
            compileSdk = readIntFromGradle(content, "compileSdk") ?: compileSdk
            versionCode = readIntFromGradle(content, "versionCode") ?: versionCode
            versionName = readStringFromGradle(content, "versionName") ?: versionName
            jvmTarget = readStringFromGradle(content, "jvmTarget") ?: jvmTarget
        }

        // 2) من module.toml (يعلو على build.gradle)
        val moduleToml = File(projectDir, "app/module.toml")
        if (moduleToml.isFile) {
            val content = moduleToml.readText()
            minSdk = readIntFromToml(content, "minSdk") ?: minSdk
            targetSdk = readIntFromToml(content, "targetSdk") ?: targetSdk
            compileSdk = readIntFromToml(content, "compileSdk") ?: compileSdk
            versionCode = readIntFromToml(content, "versionCode") ?: versionCode
            versionName = readStringFromToml(content, "versionName") ?: versionName
        }

        return SdkValues(
            minSdk = minSdk.coerceIn(21, 36),
            targetSdk = targetSdk.coerceIn(21, 36),
            compileSdk = compileSdk.coerceIn(21, 36),
            versionCode = maxOf(1, versionCode),
            versionName = versionName,
            jvmTarget = jvmTarget
        )
    }

    // ============================================================
    // قراءة التبعيات
    // ============================================================
    /**
     * يقرأ التبعيات من module.toml أو app/build.gradle.
     */
    @JvmStatic
    fun readDependencies(projectDir: File): List<String> {
        // 1) module.toml
        val moduleToml = File(projectDir, "app/module.toml")
        if (moduleToml.isFile) {
            val content = moduleToml.readText()
            val blockMatch = Regex(
                "implementation\\s*=\\s*\\[(.*?)\\]",
                RegexOption.DOT_MATCHES_ALL
            ).find(content)
            if (blockMatch != null) {
                val quoted = Regex("\"([^\"]+)\"")
                val deps = quoted.findAll(blockMatch.groupValues[1])
                    .map { it.groupValues[1] }
                    .filter { it.matches(MAVEN_COORDINATES) }
                    .toList()
                if (deps.isNotEmpty()) return deps
            }
        }

        // 2) app/build.gradle
        val buildGradle = File(projectDir, "app/build.gradle")
        if (buildGradle.isFile) {
            val content = buildGradle.readText()
            val pattern = Regex("implementation\\s+['\"]([^'\"]+)['\"]")
            return pattern.findAll(content).map { it.groupValues[1] }.toList()
        }

        return emptyList()
    }

    // ============================================================
    // أدوات داخلية
    // ============================================================
    private fun replaceSdkValue(content: String, key: String, value: Int): String {
        val pattern = Regex("(?m)(^\\s*${Regex.escape(key)}\\s*[=\\s]\\s*)['\"]?\\d+['\"]?")
        return content.replace(pattern) { match ->
            val prefix = match.groupValues[1]
            "$prefix$value"
        }
    }

    private fun readIntFromGradle(content: String, key: String): Int? {
        val pattern = Regex("(?m)^\\s*${Regex.escape(key)}\\s*[=\\s]\\s*(\\d+)")
        return pattern.find(content)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun readStringFromGradle(content: String, key: String): String? {
        val pattern = Regex("(?m)^\\s*${Regex.escape(key)}\\s*[=\\s]\\s*['\"]([^'\"]+)['\"]")
        return pattern.find(content)?.groupValues?.get(1)
    }

    private fun readIntFromToml(content: String, key: String): Int? {
        val pattern = Regex("(?m)^\\s*${Regex.escape(key)}\\s*=\\s*(\\d+)")
        return pattern.find(content)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun readStringFromToml(content: String, key: String): String? {
        val pattern = Regex("(?m)^\\s*${Regex.escape(key)}\\s*=\\s*\"([^\"]+)\"")
        return pattern.find(content)?.groupValues?.get(1)
    }

    // ============================================================
    // نماذج البيانات
    // ============================================================
    class SdkValues(
        val minSdk: Int,
        val targetSdk: Int,
        val compileSdk: Int,
        val versionCode: Int,
        val versionName: String,
        val jvmTarget: String
    )

    // ============================================================
    // ثوابت
    // ============================================================
    private val MAVEN_COORDINATES = Regex(
        "[A-Za-z0-9_.\\-]+:[A-Za-z0-9_.\\-]+:[A-Za-z0-9_.+\\-]+"
    )
}