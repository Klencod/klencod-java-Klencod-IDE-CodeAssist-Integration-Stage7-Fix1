package com.flutteride.codweva.java

/**
 * KotlinBuildWorkflow — يولّد محتوى `.github/workflows/build-android.yml`
 * لمشاريع Android Kotlin التي تُبنى عبر GitHub Actions.
 *
 * v2.0 — دعم ذكي لـ Gradle Wrapper:
 *   - إذا كان gradlew موجود في المشروع → يستخدمه
 *   - إذا لم يكن موجوداً (مشروع مستورد) → يستخدم gradle من النظام
 *   - الفحص يتم في workflow نفسه (bash condition)
 *   - النتيجة: يعمل مع كل أنواع المشاريع بدون تعديل يدوي
 *
 * v1.0 — workflow أساسي مع Java 17 + Android SDK + Gradle Wrapper
 */
object KotlinBuildWorkflow {

    @JvmStatic
    fun build(targetSdk: Int): String {
        val safeTargetSdk = targetSdk.coerceIn(21, 36)
        val sb = StringBuilder(8192)

        sb.append("name: Build Android Kotlin APK\n")
        sb.append("\n")
        sb.append("on:\n")
        sb.append("  workflow_dispatch:\n")
        sb.append("\n")
        sb.append("permissions:\n")
        sb.append("  contents: read\n")
        sb.append("\n")
        sb.append("concurrency:\n")
        sb.append("  group: klencod-kotlin-").append("${'$'}").append("{{ github.repository }}\n")
        sb.append("  cancel-in-progress: true\n")
        sb.append("\n")
        sb.append("jobs:\n")
        sb.append("  build:\n")
        sb.append("    name: Build debug APK\n")
        sb.append("    runs-on: ubuntu-latest\n")
        sb.append("    timeout-minutes: 30\n")
        sb.append("\n")
        sb.append("    steps:\n")

        // ---- 1) Checkout ----
        sb.append("      - name: Checkout project\n")
        sb.append("        uses: actions/checkout@v4\n")
        sb.append("        with:\n")
        sb.append("          fetch-depth: 1\n")
        sb.append("\n")

        // ---- 2) Java 17 ----
        sb.append("      - name: Set up Java 17\n")
        sb.append("        uses: actions/setup-java@v4\n")
        sb.append("        with:\n")
        sb.append("          distribution: temurin\n")
        sb.append("          java-version: '17'\n")
        sb.append("\n")

        // ---- 3) Android SDK ----
        sb.append("      - name: Set up Android SDK\n")
        sb.append("        uses: android-actions/setup-android@v3\n")
        sb.append("        with:\n")
        sb.append("          packages: 'platforms;android-34 build-tools;34.0.0'\n")
        sb.append("          accept-android-sdk-licenses: true\n")
        sb.append("\n")

        // ---- 4) Build (مع fallback ذكي) ----
        sb.append("      - name: Build debug APK\n")
        sb.append("        shell: bash\n")
        sb.append("        run: |\n")
        sb.append("          set -o pipefail\n")
        sb.append("          LOG=\"${'$'}RUNNER_TEMP/klencod-gradle.log\"\n")
        sb.append("\n")
        sb.append("          if [ -f gradlew ]; then\n")
        sb.append("            echo \"✅ Using Gradle Wrapper (gradlew)\"\n")
        sb.append("            chmod +x gradlew || true\n")
        sb.append("            ./gradlew --no-daemon --build-cache --parallel \\\n")
        sb.append("              :app:assembleDebug -x lint -x test 2>&1 | tee \"${'$'}LOG\"\n")
        sb.append("          else\n")
        sb.append("            echo \"⚠️ gradlew غير موجود — استخدام gradle من النظام\"\n")
        sb.append("            echo \"ℹ️ هذا شائع في المشاريع المستوردة من الخارج\"\n")
        sb.append("            gradle --no-daemon --build-cache --parallel \\\n")
        sb.append("              :app:assembleDebug -x lint -x test 2>&1 | tee \"${'$'}LOG\"\n")
        sb.append("          fi\n")
        sb.append("\n")

        // ---- 5) Diagnostics on failure ----
        sb.append("      - name: Print compiler diagnostics on failure\n")
        sb.append("        if: failure()\n")
        sb.append("        shell: bash\n")
        sb.append("        run: |\n")
        sb.append("          LOG=\"${'$'}RUNNER_TEMP/klencod-gradle.log\"\n")
        sb.append("          echo \"===== Klencod compiler diagnostics =====\"\n")
        sb.append("          if [[ -f \"${'$'}LOG\" ]]; then\n")
        sb.append("            grep -E 'error:|warning:|FAILED|Compilation failed|BUILD FAILED|What went wrong' \"${'$'}LOG\" | tail -n 120 || true\n")
        sb.append("          else\n")
        sb.append("            echo \"Gradle output file was not created.\"\n")
        sb.append("          fi\n")
        sb.append("\n")

        // ---- 6) Upload APK ----
        sb.append("      - name: Upload APK artifact\n")
        sb.append("        uses: actions/upload-artifact@v4\n")
        sb.append("        with:\n")
        sb.append("          name: klencod-kotlin-debug-apk\n")
        sb.append("          path: app/build/outputs/apk/debug/*.apk\n")
        sb.append("          if-no-files-found: error\n")
        sb.append("          retention-days: 7\n")

        return sb.toString()
    }

    /**
     * نسخة مختصرة للاختبار.
     */
    @JvmStatic
    fun preview(): String = build(34)

    /**
     * تحقق هل النص يحتوي على workflow_dispatch.
     */
    @JvmStatic
    fun hasWorkflowDispatch(content: String?): Boolean {
        if (content.isNullOrEmpty()) return false
        val pattern = Regex("^\\s*workflow_dispatch\\s*:", RegexOption.MULTILINE)
        return pattern.containsMatchIn(content)
    }
}