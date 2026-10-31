package com.flutteride.codweva.reactnative

import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * ReactNativeGradleSupport — يضبط ملفات Gradle لمشاريع React Native CLI.
 *
 * v1.0 — نفس نمط JavaGradleSupport:
 *   - applyTargetSdk / applyMinSdk / applyCompileSdk
 *   - applyVersionName / applyVersionCode
 *   - readSdkValues
 *   - readDependencies (من package.json)
 *
 * ملاحظة: RN يستخدم Gradle Ext في android/build.gradle:
 *   ext { minSdkVersion = 24; compileSdkVersion = 35; targetSdkVersion = 34 }
 * وليس defaultConfig مباشرة.
 */
object ReactNativeGradleSupport {

    // ============================================================
    // applyTargetSdk
    // ============================================================
    @JvmStatic
    @Throws(IOException::class)
    fun applyTargetSdk(projectDir: File, targetSdk: Int): Boolean {
        if (targetSdk !in 21..36) {
            throw IOException("targetSdk خارج النطاق: $targetSdk")
        }

        var changed = false

        // 1) android/build.gradle → ext { targetSdkVersion = X }
        val rootGradle = File(projectDir, "android/build.gradle")
        if (rootGradle.isFile) {
            val content = rootGradle.readText()
            val updated = replaceExtValue(content, "targetSdkVersion", targetSdk)
            if (updated != content) {
                rootGradle.writeText(updated)
                changed = true
            }
        }

        // 2) .klencod/project.json
        val metadata = File(projectDir, ".klencod/project.json")
        if (metadata.isFile) {
            val content = metadata.readText()
            val updated = content.replace(
                Regex("\"targetSdk\"\\s*:\\s*\\d+"),
                "\"targetSdk\": $targetSdk"
            )
            if (updated != content) {
                metadata.writeText(updated)
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

        val rootGradle = File(projectDir, "android/build.gradle")
        if (rootGradle.isFile) {
            val content = rootGradle.readText()
            val updated = replaceExtValue(content, "minSdkVersion", minSdk)
            if (updated != content) {
                rootGradle.writeText(updated)
                changed = true
            }
        }

        val metadata = File(projectDir, ".klencod/project.json")
        if (metadata.isFile) {
            val content = metadata.readText()
            val updated = content.replace(
                Regex("\"minSdk\"\\s*:\\s*\\d+"),
                "\"minSdk\": $minSdk"
            )
            if (updated != content) {
                metadata.writeText(updated)
                changed = true
            }
        }

        return changed
    }

    // ============================================================
    // applyCompileSdk
    // ============================================================
    @JvmStatic
    @Throws(IOException::class)
    fun applyCompileSdk(projectDir: File, compileSdk: Int): Boolean {
        if (compileSdk !in 21..36) {
            throw IOException("compileSdk خارج النطاق: $compileSdk")
        }

        var changed = false

        val rootGradle = File(projectDir, "android/build.gradle")
        if (rootGradle.isFile) {
            val content = rootGradle.readText()
            val updated = replaceExtValue(content, "compileSdkVersion", compileSdk)
            if (updated != content) {
                rootGradle.writeText(updated)
                changed = true
            }
        }

        val metadata = File(projectDir, ".klencod/project.json")
        if (metadata.isFile) {
            val content = metadata.readText()
            val updated = content.replace(
                Regex("\"compileSdk\"\\s*:\\s*\\d+"),
                "\"compileSdk\": $compileSdk"
            )
            if (updated != content) {
                metadata.writeText(updated)
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

        val appGradle = File(projectDir, "android/app/build.gradle")
        if (!appGradle.isFile) return false

        val content = appGradle.readText()
        val updated = content.replace(
            Regex("versionName\\s+['\"][^'\"]*['\"]"),
            "versionName \"$versionName\""
        )
        if (updated != content) {
            appGradle.writeText(updated)
            return true
        }
        return false
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

        val appGradle = File(projectDir, "android/app/build.gradle")
        if (!appGradle.isFile) return false

        val content = appGradle.readText()
        val updated = content.replace(
            Regex("versionCode\\s+\\d+"),
            "versionCode $versionCode"
        )
        if (updated != content) {
            appGradle.writeText(updated)
            return true
        }
        return false
    }

    // ============================================================
    // readSdkValues
    // ============================================================
    @JvmStatic
    fun readSdkValues(projectDir: File): SdkValues {
        var minSdk = 24
        var targetSdk = 34
        var compileSdk = 35
        var versionCode = 1
        var versionName = "1.0"

        // 1) من .klencod/project.json (الأولوية الأعلى)
        val metadata = File(projectDir, ".klencod/project.json")
        if (metadata.isFile) {
            val content = metadata.readText()
            minSdk = readIntFromJson(content, "minSdk") ?: minSdk
            targetSdk = readIntFromJson(content, "targetSdk") ?: targetSdk
            compileSdk = readIntFromJson(content, "compileSdk") ?: compileSdk
        }

        // 2) من android/build.gradle (ext block)
        val rootGradle = File(projectDir, "android/build.gradle")
        if (rootGradle.isFile) {
            val content = rootGradle.readText()
            minSdk = readExtInt(content, "minSdkVersion") ?: minSdk
            targetSdk = readExtInt(content, "targetSdkVersion") ?: targetSdk
            compileSdk = readExtInt(content, "compileSdkVersion") ?: compileSdk
        }

        // 3) من android/app/build.gradle (defaultConfig)
        val appGradle = File(projectDir, "android/app/build.gradle")
        if (appGradle.isFile) {
            val content = appGradle.readText()
            versionCode = readIntFromGradle(content, "versionCode") ?: versionCode
            versionName = readStringFromGradle(content, "versionName") ?: versionName
        }

        return SdkValues(
            minSdk = minSdk.coerceIn(21, 36),
            targetSdk = targetSdk.coerceIn(21, 36),
            compileSdk = compileSdk.coerceIn(21, 36),
            versionCode = maxOf(1, versionCode),
            versionName = versionName
        )
    }

    // ============================================================
    // readDependencies (من package.json)
    // ============================================================
    @JvmStatic
    fun readDependencies(projectDir: File): List<String> {
        val packageJson = File(projectDir, "package.json")
        if (!packageJson.isFile) return emptyList()

        return try {
            val content = packageJson.readText()
            val deps = mutableListOf<String>()

            // ابحث عن قسم dependencies
            val depsMatch = Regex(
                "\"dependencies\"\\s*:\\s*\\{([^}]+)\\}",
                RegexOption.DOT_MATCHES_ALL
            ).find(content)

            if (depsMatch != null) {
                val quoted = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"")
                for (match in quoted.findAll(depsMatch.groupValues[1])) {
                    val name = match.groupValues[1]
                    val version = match.groupValues[2]
                    deps.add("$name@$version")
                }
            }

            deps
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ============================================================
    // أدوات داخلية
    // ============================================================

    /**
     * يستبدل قيمة في ext block:
     *   minSdkVersion = 24  →  minSdkVersion = XX
     */
    private fun replaceExtValue(content: String, key: String, value: Int): String {
        val pattern = Regex("(?m)(^\\s*${Regex.escape(key)}\\s*=\\s*)\\d+")
        return content.replace(pattern) { match ->
            "${match.groupValues[1]}$value"
        }
    }

    private fun readExtInt(content: String, key: String): Int? {
        val pattern = Regex("(?m)^\\s*${Regex.escape(key)}\\s*=\\s*(\\d+)")
        return pattern.find(content)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun readIntFromGradle(content: String, key: String): Int? {
        val pattern = Regex("(?m)^\\s*${Regex.escape(key)}\\s+['\"]?(\\d+)")
        return pattern.find(content)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun readStringFromGradle(content: String, key: String): String? {
        val pattern = Regex("(?m)^\\s*${Regex.escape(key)}\\s+['\"]([^'\"]+)['\"]")
        return pattern.find(content)?.groupValues?.get(1)
    }

    private fun readIntFromJson(content: String, key: String): Int? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(\\d+)")
        return pattern.find(content)?.groupValues?.get(1)?.toIntOrNull()
    }

    // ============================================================
    // نماذج البيانات
    // ============================================================
    class SdkValues(
        val minSdk: Int,
        val targetSdk: Int,
        val compileSdk: Int,
        val versionCode: Int,
        val versionName: String
    )
}