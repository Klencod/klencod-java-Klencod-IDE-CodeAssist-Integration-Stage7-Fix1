package com.flutteride.codweva

/**
 * إعدادات GitHub العامة اللازمة لتدفق Device Flow.
 *
 * لا نحتفظ بـ Client Secret داخل APK.
 *
 * v3.0 — يدعم أنواع مشاريع متعددة:
 *   - Flutter (main.yml + app-release)
 *   - Java    (build-android.yml + klencod-java-debug-apk)
 *   - Kotlin  (build-android.yml + klencod-kotlin-debug-apk)
 *   - React Native (build-android.yml + klencod-reactnative-release-apk)
 */
object GitHubConfig {

    /** Client ID عام لتطبيق OAuth. */
    const val CLIENT_ID = "Ov23liD3bSfWr2wF5e1B"

    /** URL لطلب رمز الجهاز. */
    const val DEVICE_CODE_URL = "https://github.com/login/device/code"

    /** URL لتبديل رمز الجهاز بـ Access Token. */
    const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"

    /** قاعدة GitHub API. */
    const val API_URL = "https://api.github.com"

    /** صفحة التحقق للمستخدم. */
    const val VERIFICATION_URL = "https://github.com/login/device"

    /** نسخة API المُثبَّتة في الـ Headers. */
    const val API_VERSION = "2022-11-28"

    /** Scope المطلوب (repo + workflow). */
    const val OAUTH_SCOPE = "repo workflow"

    // ============================================================
    // قيم افتراضية للـ Flutter (للتوافق مع الكود القديم)
    // ============================================================
    /** اسم ملف Workflow الافتراضي (Flutter). */
    const val WORKFLOW_FILE = "main.yml"

    /** اسم الـ Artifact الافتراضي (Flutter). */
    const val ARTIFACT_NAME = "app-release"

    // ============================================================
    // ثوابت خاصة بـ React Native  ⭐ NEW
    // ============================================================
    /** اسم ملف workflow لـ RN. */
    const val RN_WORKFLOW_FILE = "build-android.yml"

    /** اسم الـ artifact لـ RN. */
    const val RN_ARTIFACT_NAME = "klencod-reactnative-release-apk"

    /** بادئة اسم المستودع لـ RN. */
    const val RN_REPO_PREFIX = "klencod-reactnative-"

    // ============================================================
    // ثوابت خاصة بـ Java / Kotlin (موجودة مسبقاً — للتوثيق)
    // ============================================================
    /** اسم الـ artifact لـ Java. */
    const val JAVA_ARTIFACT_NAME = "klencod-java-debug-apk"

    /** اسم الـ artifact لـ Kotlin. */
    const val KOTLIN_ARTIFACT_NAME = "klencod-kotlin-debug-apk"

    /** اسم ملف workflow لـ Java / Kotlin. */
    const val NATIVE_WORKFLOW_FILE = "build-android.yml"

    /** بادئة اسم المستودع لـ Java. */
    const val JAVA_REPO_PREFIX = "klencod-java-"

    /** بادئة اسم المستودع لـ Kotlin. */
    const val KOTLIN_REPO_PREFIX = "klencod-kotlin-"

    // ============================================================
    // دوال حسب نوع المشروع
    // ============================================================

    /**
     * يُرجع اسم ملف workflow المناسب لنوع المشروع.
     */
    @JvmStatic
    fun workflowFileFor(type: ProjectType?): String {
        if (type == null) return WORKFLOW_FILE
        return when (type) {
            ProjectType.FLUTTER -> WORKFLOW_FILE
            ProjectType.JAVA -> NATIVE_WORKFLOW_FILE
            ProjectType.KOTLIN -> NATIVE_WORKFLOW_FILE
            ProjectType.REACT_NATIVE -> RN_WORKFLOW_FILE
        }
    }

    /**
     * يُرجع اسم artifact المناسب لنوع المشروع.
     */
    @JvmStatic
    fun artifactNameFor(type: ProjectType?): String {
        if (type == null) return ARTIFACT_NAME
        return when (type) {
            ProjectType.FLUTTER -> ARTIFACT_NAME
            ProjectType.JAVA -> JAVA_ARTIFACT_NAME
            ProjectType.KOTLIN -> KOTLIN_ARTIFACT_NAME
            ProjectType.REACT_NATIVE -> RN_ARTIFACT_NAME
        }
    }

    /**
     * يُرجع بادئة اسم المستودع.
     */
    @JvmStatic
    fun repoPrefixFor(type: ProjectType?): String {
        if (type == null) return "klencod-"
        return when (type) {
            ProjectType.FLUTTER -> "klencod-flutter-"
            ProjectType.JAVA -> JAVA_REPO_PREFIX
            ProjectType.KOTLIN -> KOTLIN_REPO_PREFIX
            ProjectType.REACT_NATIVE -> RN_REPO_PREFIX
        }
    }

    /**
     * يُرجع أمر البناء.
     */
    @JvmStatic
    fun buildCommandFor(type: ProjectType?): String {
        if (type == null) return "flutter build apk --release"
        return when (type) {
            ProjectType.FLUTTER -> "flutter build apk --release"
            ProjectType.JAVA -> "./gradlew assembleDebug"
            ProjectType.KOTLIN -> "./gradlew assembleDebug"
            ProjectType.REACT_NATIVE -> "gradle -p android assembleRelease"
        }
    }

    /**
     * هل النوع يحتاج Node.js في البناء؟
     */
    @JvmStatic
    fun requiresNodeFor(type: ProjectType?): Boolean {
        return type == ProjectType.REACT_NATIVE
    }

    /**
     * هل النوع يحتاج Flutter SDK؟
     */
    @JvmStatic
    fun requiresFlutterSdkFor(type: ProjectType?): Boolean {
        return type == ProjectType.FLUTTER
    }

    /**
     * هل النوع يستخدم gradlew من المشروع؟
     */
    @JvmStatic
    fun usesGradleWrapperFor(type: ProjectType?): Boolean {
        return type == ProjectType.JAVA || type == ProjectType.KOTLIN
    }

    /**
     * هل النوع يستخدم gradle command من النظام؟
     */
    @JvmStatic
    fun usesSystemGradleFor(type: ProjectType?): Boolean {
        return type == ProjectType.REACT_NATIVE
    }

    /**
     * هل النوع يبني release APK؟
     */
    @JvmStatic
    fun buildsReleaseApkFor(type: ProjectType?): Boolean {
        return type == ProjectType.FLUTTER || type == ProjectType.REACT_NATIVE
    }

    /**
     * هل النوع يبني debug APK؟
     */
    @JvmStatic
    fun buildsDebugApkFor(type: ProjectType?): Boolean {
        return type == ProjectType.JAVA || type == ProjectType.KOTLIN
    }

    /**
     * مسار APK النهائي (نسبةً لجذر المشروع).
     */
    @JvmStatic
    fun apkPathFor(type: ProjectType?): String {
        if (type == null) return "build/app/outputs/flutter-apk/app-release.apk"
        return when (type) {
            ProjectType.FLUTTER -> "build/app/outputs/flutter-apk/app-release.apk"
            ProjectType.JAVA -> "app/build/outputs/apk/debug/app-debug.apk"
            ProjectType.KOTLIN -> "app/build/outputs/apk/debug/app-debug.apk"
            ProjectType.REACT_NATIVE -> "android/app/build/outputs/apk/release/app-release.apk"
        }
    }

    /**
     * يُرجع وصف نصي للنوع (للعرض في السجلات).
     */
    @JvmStatic
    fun typeLabelFor(type: ProjectType?): String {
        if (type == null) return "غير معروف"
        return when (type) {
            ProjectType.FLUTTER -> "Flutter (Dart)"
            ProjectType.JAVA -> "Android (Java)"
            ProjectType.KOTLIN -> "Android (Kotlin)"
            ProjectType.REACT_NATIVE -> "React Native (TypeScript)"
        }
    }
}