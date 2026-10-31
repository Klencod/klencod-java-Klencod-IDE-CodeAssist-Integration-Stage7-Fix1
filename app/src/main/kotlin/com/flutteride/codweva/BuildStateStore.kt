package com.flutteride.codweva

import android.content.Context
import android.content.SharedPreferences

/**
 * BuildStateStore — تخزين مبسّط لحالة البناء الحالي (v2.0).
 *
 * ⚠️ ملاحظة مهمة: هذا ملف **جديد تماماً** بديل عن النسخة القديمة.
 *    لا علاقة له بالنسخة القديمة (التي كانت تُخزّن progress + notification state).
 *
 * المسؤولية الوحيدة:
 *   تخزين موقع البناء على GitHub (owner, repo, runId) + معلومات المشروع.
 *
 * لماذا؟
 *   - عند بدء البناء: نحفظ runId
 *   - عند رجوع المستخدم: نستخدم runId لفحص الحالة
 *   - بعد النهاية: نمسح البيانات
 *
 * البيانات المحفوظة (7 فقط):
 *   1. owner           — صاحب المستودع
 *   2. repo            — اسم المستودع
 *   3. runId           — رقم GitHub Actions run
 *   4. projectPath     — مسار المشروع المحلي
 *   5. projectType     — نوع المشروع (flutter/java/...)
 *   6. startedAt       — وقت البدء (milliseconds)
 *   7. repoName        — اسم المستودع المعروض (اختياري)
 *
 * لا يُخزَّن:
 *   ❌ نسبة تقدم
 *   ❌ حالة الإشعار
 *   ❌ رسائل التقدم
 *   ❌ أي شيء آخر
 *
 * Singleton (object) لسهولة الوصول من أي مكان.
 */
object BuildStateStore {

    // ============================================================
    // ثوابت
    // ============================================================
    private const val PREFS = "klencod_build_run_store_v2"

    private const val KEY_OWNER = "owner"
    private const val KEY_REPO = "repo"
    private const val KEY_RUN_ID = "run_id"
    private const val KEY_PROJECT_PATH = "project_path"
    private const val KEY_PROJECT_TYPE = "project_type"
    private const val KEY_STARTED_AT = "started_at"
    private const val KEY_REPO_NAME = "repo_name"

    // ============================================================
    // نموذج البيانات
    // ============================================================
    /**
     * معلومات البناء الحالي.
     * جميع الحقول immutable — تُبنى عند القراءة.
     */
    class RunInfo(
        val owner: String,
        val repo: String,
        val runId: Long,
        val projectPath: String,
        val projectType: String,
        val startedAt: Long,
        val repoName: String
    ) {

        /** هل البيانات صالحة للاستخدام؟ */
        val isValid: Boolean
            get() = owner.isNotEmpty() &&
                    repo.isNotEmpty() &&
                    runId > 0L

        /** قيم آمنة (بدون null) */
        val safeOwner: String get() = owner.ifEmpty { "" }
        val safeRepo: String get() = repo.ifEmpty { "" }
        val safeProjectPath: String get() = projectPath.ifEmpty { "" }
        val safeProjectType: String
            get() = projectType.ifEmpty { ProjectType.FLUTTER.id }
        val safeRepoName: String get() = repoName.ifEmpty { repo }

        /** المدة المنقضية بالثواني منذ بدء البناء. */
        val elapsedSeconds: Long
            get() {
                if (startedAt <= 0L) return 0L
                return (System.currentTimeMillis() - startedAt) / 1000L
            }

        override fun toString(): String {
            return "RunInfo{$safeOwner/$safeRepo #$runId ($safeProjectType)}"
        }
    }

    // ============================================================
    // الوصول للـ SharedPreferences
    // ============================================================
    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    // ============================================================
    // حفظ معلومات البناء
    // ============================================================
    /**
     * يحفظ معلومات بدء بناء جديد.
     *
     * @param owner صاحب المستودع
     * @param repo اسم المستودع
     * @param runId رقم الـ run على GitHub
     * @param projectPath مسار المشروع المحلي
     * @param projectType نوع المشروع (flutter, java, kotlin, reactnative)
     * @param repoName اسم معروض (اختياري — الافتراضي = repo)
     */
    @JvmStatic
    @JvmOverloads
    fun saveRun(
        context: Context?,
        owner: String,
        repo: String,
        runId: Long,
        projectPath: String,
        projectType: String,
        repoName: String = ""
    ) {
        if (context == null) return
        if (owner.isEmpty() || repo.isEmpty() || runId <= 0L) return

        try {
            prefs(context).edit()
                .putString(KEY_OWNER, owner)
                .putString(KEY_REPO, repo)
                .putLong(KEY_RUN_ID, runId)
                .putString(KEY_PROJECT_PATH, projectPath)
                .putString(KEY_PROJECT_TYPE, projectType)
                .putLong(KEY_STARTED_AT, System.currentTimeMillis())
                .putString(KEY_REPO_NAME, repoName.ifEmpty { repo })
                .apply()
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // قراءة معلومات البناء
    // ============================================================
    /**
     * يقرأ معلومات البناء الحالي.
     *
     * @return RunInfo إذا وُجدت بيانات صالحة، أو null
     */
    @JvmStatic
    fun getRun(context: Context?): RunInfo? {
        if (context == null) return null

        return try {
            val p = prefs(context)

            val owner = p.getString(KEY_OWNER, "") ?: ""
            val repo = p.getString(KEY_REPO, "") ?: ""
            val runId = p.getLong(KEY_RUN_ID, 0L)
            val projectPath = p.getString(KEY_PROJECT_PATH, "") ?: ""
            val projectType = p.getString(KEY_PROJECT_TYPE, "") ?: ""
            val startedAt = p.getLong(KEY_STARTED_AT, 0L)
            val repoName = p.getString(KEY_REPO_NAME, "") ?: ""

            if (owner.isEmpty() || repo.isEmpty() || runId <= 0L) {
                return null
            }

            RunInfo(
                owner = owner,
                repo = repo,
                runId = runId,
                projectPath = projectPath,
                projectType = projectType,
                startedAt = startedAt,
                repoName = repoName
            )
        } catch (_: Exception) {
            null
        }
    }

    // ============================================================
    // التحقق من وجود بناء نشط
    // ============================================================
    /**
     * هل يوجد بناء محفوظ؟
     */
    @JvmStatic
    fun hasActiveBuild(context: Context?): Boolean {
        return getRun(context) != null
    }

    // ============================================================
    // مسح البيانات
    // ============================================================
    /**
     * يمسح كل بيانات البناء.
     * يُستدعى بعد:
     *   - نجاح البناء (بعد تنزيل APK)
     *   - فشل البناء
     *   - إلغاء البناء
     */
    @JvmStatic
    fun clearRun(context: Context?) {
        if (context == null) return

        try {
            prefs(context).edit()
                .remove(KEY_OWNER)
                .remove(KEY_REPO)
                .remove(KEY_RUN_ID)
                .remove(KEY_PROJECT_PATH)
                .remove(KEY_PROJECT_TYPE)
                .remove(KEY_STARTED_AT)
                .remove(KEY_REPO_NAME)
                .apply()
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // أدوات مساعدة
    // ============================================================

    /**
     * المدة المنقضية بالثواني منذ بدء البناء.
     */
    @JvmStatic
    fun getElapsedSeconds(context: Context?): Long {
        return getRun(context)?.elapsedSeconds ?: 0L
    }

    /**
     * يُرجع وقت البدء (milliseconds)، أو 0.
     */
    @JvmStatic
    fun getStartedAtMs(context: Context?): Long {
        return getRun(context)?.startedAt ?: 0L
    }

    /**
     * يُرجع نوع المشروع، أو FLUTTER كافتراضي.
     */
    @JvmStatic
    fun getProjectType(context: Context?): ProjectType {
        val runInfo = getRun(context) ?: return ProjectType.FLUTTER
        return ProjectType.fromId(runInfo.safeProjectType)
    }

    /**
     * يُرجع مسار المشروع، أو "".
     */
    @JvmStatic
    fun getProjectPath(context: Context?): String {
        return getRun(context)?.safeProjectPath ?: ""
    }

    /**
     * يُرجع runId، أو 0.
     */
    @JvmStatic
    fun getRunId(context: Context?): Long {
        return getRun(context)?.runId ?: 0L
    }

    /**
     * يُرجع owner/repo كنص للعرض.
     */
    @JvmStatic
    fun getRepoDisplay(context: Context?): String {
        val runInfo = getRun(context) ?: return ""
        return "${runInfo.safeOwner}/${runInfo.safeRepo}"
    }

    /**
     * ملخص التشخيص (للتطوير فقط).
     */
    @JvmStatic
    fun debugSummary(context: Context?): String {
        val runInfo = getRun(context) ?: return "BuildStateStore{empty}"
        return buildString {
            append("BuildStateStore{")
            append("owner=").append(runInfo.safeOwner)
            append(", repo=").append(runInfo.safeRepo)
            append(", runId=").append(runInfo.runId)
            append(", type=").append(runInfo.safeProjectType)
            append(", elapsed=").append(runInfo.elapsedSeconds).append("s")
            append("}")
        }
    }
}