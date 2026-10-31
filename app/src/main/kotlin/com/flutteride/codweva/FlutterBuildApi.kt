package com.flutteride.codweva

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * FlutterBuildApi — v7.0 (دعم uploadMethod).
 *
 * ⭐ v7.0 — إضافة دعم طريقة الرفع:
 *   - triggerBuild() يقبل GitHubApi.UploadMethod كمعامل ثالث
 *   - يُمرَّر إلى GitHubProjectPublisher.publish() مباشرة
 *   - افتراضياً GRAPHQL للتوافق الخلفي
 *
 * ⭐ v6.7 — حل مشكلة "السجل فارغ عند الفشل":
 *   - fetchStepLogsIfNeeded: تسجيل أخطاء downloadJobLogs (بدل تجاهلها)
 *   - fetchFailureDetails: بعد annotations، يحمّل Run logs
 *   - downloadRunLogsAsFallback: يأخذ آخر 200 سطر من Run logs
 *   - النتيجة: عند فشل البناء، يظهر الإشعار المركّز + السجل الكامل
 *
 * ⭐ v6.6 — إشعار مركّز مثل GitHub
 * ⭐ v6.5 — استخراج الخطوة الفاشلة
 * ⭐ v6.4 — توكن المشروع
 * ⭐ v6.3 — polling كل ثانية
 */
class FlutterBuildApi(private val context: Context) {

    private val appContext: Context = context.applicationContext

    // ============================================================
    // Polling عبر Handler(MainLooper)
    // ============================================================
    private val pollHandler: Handler = Handler(Looper.getMainLooper())

    @Volatile
    private var pollRunnable: Runnable? = null

    @Volatile
    private var pollingActive: Boolean = false

    @Volatile
    private var pollingCancelled: Boolean = false

    private var pollingCallback: BuildCallback? = null

    private var pollingStartedAtMs: Long = 0L

    var logMode: Int = 0

    // ============================================================
    // أنواع السجل
    // ============================================================
    companion object {
        private const val TAG = "FlutterBuildApi"

        const val LOG_TYPE_NORMAL = 0
        const val LOG_TYPE_SUCCESS = 1
        const val LOG_TYPE_WARNING = 2
        const val LOG_TYPE_ERROR = 3

        const val GITHUB_QUOTA_PREFIX = "__GITHUB_QUOTA__:"
        const val ANNOTATIONS_PREFIX = "__ANNOTATIONS__:"
        const val ANNOTATIONS_SEPARATOR = "__ANN_SEP__"

        private const val MAX_ALL_LOG_LINES = 10000
        private const val MAX_ERROR_LOG_LINES = 5000

        /** فاصل polling سريع (1 ثانية). */
        private const val POLL_INTERVAL_MS = 1000L

        /** ⭐ v6.7: عدد أسطر fallback من Run logs. */
        private const val FALLBACK_RUN_LOG_LINES = 200

        @JvmStatic
        fun classifyAnnotationStatic(annotation: String?): Int {
            if (annotation.isNullOrBlank()) return LOG_TYPE_NORMAL
            val lower = annotation.lowercase(Locale.US).trim()

            if (lower.startsWith("error:") || lower.startsWith("error ")) return LOG_TYPE_ERROR
            if (lower.contains("no file in") && lower.contains("matched to")) return LOG_TYPE_ERROR
            if (lower.contains("make sure you have checked out")) return LOG_TYPE_ERROR
            if (lower.contains("process completed with exit code")) return LOG_TYPE_ERROR
            if (lower.contains("build failed")) return LOG_TYPE_ERROR
            if (lower.contains("compilation failed")) return LOG_TYPE_ERROR
            if (lower.contains("command failed")) return LOG_TYPE_ERROR
            if (lower.contains("not found")) return LOG_TYPE_ERROR
            if (lower.contains("missing")) return LOG_TYPE_ERROR

            if (lower.startsWith("warning:") || lower.startsWith("warning ")) return LOG_TYPE_WARNING
            if (lower.contains("deprecated")) return LOG_TYPE_WARNING
            if (lower.contains("will no longer receive updates")) return LOG_TYPE_WARNING
            if (lower.contains("will migrate to")) return LOG_TYPE_WARNING
            if (lower.contains("node 20 is being deprecated")) return LOG_TYPE_WARNING

            return LOG_TYPE_NORMAL
        }
    }

    // ============================================================
    // BuildLogLine
    // ============================================================
    class BuildLogLine @JvmOverloads constructor(
        val text: String,
        val filePath: String?,
        val line: Int,
        val column: Int,
        val logType: Int = LOG_TYPE_NORMAL
    ) {
        val isError: Boolean = logType == LOG_TYPE_ERROR
    }

    // ============================================================
    // BuildCallback
    // ============================================================
    interface BuildCallback {
        fun onStarted(owner: String, repo: String, runId: Long) {}
        fun onProgress(message: String) {}
        fun onStepsUpdate(steps: List<GitHubApi.Step>) {}
        fun onStepLogs(
            jobId: Long,
            allLogs: List<BuildLogLine>,
            errorLogs: List<BuildLogLine>
        ) {}
        fun onAnnotations(rawAnnotations: String) {}
        fun onUploadProgress(current: Int, total: Int) {}
        fun onDownloadProgress(downloadedBytes: Long, totalBytes: Long) {}
        fun onRepoReady(repoName: String) {}
        fun onStillRunning() {}
        fun onError(error: String) {}
        fun onSuccess(apkFile: File) {}
    }

    // ============================================================
    // قراءة توكن المشروع
    // ============================================================
    private fun resolveTokenForProject(projectDir: File?): String {
        if (projectDir == null) {
            return try {
                GitHubAccountManager.getActiveToken(appContext)
            } catch (_: Exception) {
                ""
            }
        }

        val projectKey = projectDir.name

        try {
            val linked = GitHubAccountManager.getTokenForProject(appContext, projectKey)
            if (linked.isNotBlank()) return linked
        } catch (_: Exception) {
        }

        try {
            val active = GitHubAccountManager.getActiveToken(appContext)
            if (active.isNotBlank()) return active
        } catch (_: Exception) {
        }

        return try {
            SecureStore(appContext).readGitHubToken()
        } catch (_: Exception) {
            ""
        }
    }

    // ============================================================
    // ⭐ v7.0: Trigger Build مع uploadMethod
    // ============================================================
    /**
     * يبدأ عملية بناء APK على GitHub.
     *
     * @param projectDir مجلد المشروع
     * @param projectType نوع المشروع
     * @param uploadMethod طريقة الرفع (GraphQL / BATCHES) — افتراضياً GRAPHQL
     * @param callback Callback لتتبع الحالة
     */
    @JvmOverloads
    fun triggerBuild(
        projectDir: File?,
        projectType: ProjectType,
        uploadMethod: GitHubApi.UploadMethod = GitHubApi.UploadMethod.GRAPHQL,
        callback: BuildCallback?
    ) {
        pollingCancelled = false

        val token = resolveTokenForProject(projectDir)
        if (token.isBlank()) {
            callback?.onError("الرجاء ربط حساب GitHub أولًا")
            return
        }

        if (projectDir == null || !projectDir.isDirectory) {
            callback?.onError("مجلد المشروع غير صالح")
            return
        }

        var customRepoName = ""
        var isPublicRepo = true
        var flutterVersion = "3.47.2"

        try {
            val prefs = appContext.getSharedPreferences("flutter_ide_prefs", Context.MODE_PRIVATE)
            customRepoName = prefs.getString("name_${projectDir.name}", "") ?: ""
            isPublicRepo = prefs.getBoolean("repo_public_${projectDir.name}", true)

            if (projectType.isFlutter) {
                val savedVersion = prefs.getString("flutter_version_${projectDir.name}", "")
                if (!savedVersion.isNullOrBlank()) {
                    flutterVersion = savedVersion.trim()
                }
            }
        } catch (_: Exception) {
        }

        val methodLabel = if (uploadMethod == GitHubApi.UploadMethod.GRAPHQL) {
            "GraphQL (سريع)"
        } else {
            "دفعات (يعمل مع أي حجم)"
        }

        callback?.onProgress(
            "بدء بناء APK (${projectType.displayName}) على GitHub — $methodLabel..."
        )

        // ⭐ v7.0: نُمرّر uploadMethod إلى الناشر
        GitHubProjectPublisher.publish(
            token,
            projectDir,
            projectType,
            customRepoName,
            isPublicRepo,
            flutterVersion,
            uploadMethod,
            object : GitHubProjectPublisher.Listener {
                override fun onProgress(message: String, progress: Int) {
                    if (message.startsWith("__UPLOAD__")) {
                        val payload = message.substring("__UPLOAD__".length)
                        val slash = payload.indexOf('/')
                        if (slash > 0) {
                            try {
                                val current = payload.substring(0, slash).toInt()
                                val total = payload.substring(slash + 1).toInt()
                                callback?.onUploadProgress(current, total)
                            } catch (_: NumberFormatException) {
                            }
                        }
                        return
                    }
                    callback?.onProgress("$message ($progress%)")
                }

                override fun onSuccess(
                    repository: GitHubApi.Repository,
                    run: GitHubApi.Run
                ) {
                    Log.i(TAG, "triggerBuild OK: ${repository.owner}/${repository.name} #${run.id}")

                    BuildStateStore.saveRun(
                        appContext,
                        repository.owner,
                        repository.name,
                        run.id,
                        projectDir.absolutePath,
                        projectType.id
                    )

                    callback?.onRepoReady(repository.name)
                    callback?.onStarted(repository.owner, repository.name, run.id)
                    callback?.onProgress("بدأت GitHub Actions رقم ${run.id}")
                }

                override fun onError(message: String) {
                    Log.e(TAG, "triggerBuild failed: $message")
                    callback?.onError(message.ifEmpty { "فشل نشر المشروع" })
                }
            }
        )
    }

    // ============================================================
    // Start Polling
    // ============================================================
    @Synchronized
    fun startPolling(callback: BuildCallback?) {
        if (callback == null) return

        if (!BuildStateStore.hasActiveBuild(appContext)) {
            Log.d(TAG, "startPolling: لا يوجد build محفوظ")
            return
        }

        if (pollingActive && pollingCallback === callback) return

        stopPollingInternal()

        pollingCallback = callback
        pollingActive = true
        pollingCancelled = false
        pollingStartedAtMs = System.currentTimeMillis()

        Log.i(TAG, "startPolling: بدء polling كل ${POLL_INTERVAL_MS}ms")

        doPollOnce(callback)
    }

    @Synchronized
    fun startPollingNow(callback: BuildCallback?) {
        if (callback == null) return

        if (!BuildStateStore.hasActiveBuild(appContext)) {
            Log.d(TAG, "startPollingNow: لا يوجد build محفوظ بعد")
            return
        }

        val runInfo = BuildStateStore.getRun(appContext)
        if (runInfo == null || !runInfo.isValid) {
            Log.d(TAG, "startPollingNow: runInfo غير صالح")
            return
        }

        Log.i(TAG, "startPollingNow: runId=${runInfo.runId} — بدء فوري")

        stopPollingInternal()

        pollingCallback = callback
        pollingActive = true
        pollingCancelled = false
        pollingStartedAtMs = System.currentTimeMillis()

        doPollOnce(callback)
    }

    fun forcePollNow() {
        if (!pollingActive || pollingCancelled) {
            Log.d(TAG, "forcePollNow: polling غير نشط")
            return
        }

        Log.i(TAG, "forcePollNow: poll فوري")

        pollRunnable?.let { pollHandler.removeCallbacks(it) }
        pollRunnable = null

        val cb = pollingCallback ?: return
        doPollOnce(cb)
    }

    // ============================================================
    // Stop Polling
    // ============================================================
    @Synchronized
    fun stopPolling() {
        stopPollingInternal()
    }

    @Synchronized
    private fun stopPollingInternal() {
        pollRunnable?.let { pollHandler.removeCallbacks(it) }
        pollRunnable = null
        pollingActive = false
        pollingCallback = null
        Log.d(TAG, "stopPolling")
    }

    fun cancelPolling() {
        pollingCancelled = true
        stopPollingInternal()
    }

    fun cleanup() {
        cancelPolling()
    }

    fun checkOnce(callback: BuildCallback?) {
        doPollOnce(callback)
    }

    // ============================================================
    // doPollOnce
    // ============================================================
    private fun doPollOnce(callback: BuildCallback?) {
        val runInfo = BuildStateStore.getRun(appContext)
        if (runInfo == null) {
            Log.d(TAG, "doPollOnce: لا يوجد run")
            stopPollingInternal()
            return
        }

        val projectDir = runInfo.safeProjectPath.takeIf { it.isNotEmpty() }
            ?.let { File(it) }

        val token = resolveTokenForProject(projectDir)
        if (token.isBlank()) {
            Log.d(TAG, "doPollOnce: التوكن فارغ")
            callback?.onError("انتهت الجلسة")
            stopPollingInternal()
            return
        }

        if (pollingActive && !pollingCancelled) {
            scheduleNextPoll(POLL_INTERVAL_MS)
        }

        GitHubApi.getRun(
            token, runInfo.safeOwner, runInfo.safeRepo, runInfo.runId,
            object : GitHubApi.Callback<GitHubApi.Run> {
                override fun onSuccess(run: GitHubApi.Run) {
                    if (pollingCancelled) return

                    Log.d(TAG, "poll: status=${run.status} conclusion=${run.conclusion}")

                    fetchJobsAndSteps(token, runInfo, callback)

                    when {
                        run.status.equals("completed", ignoreCase = true) -> {
                            handleRunCompleted(token, runInfo, run.conclusion, callback)
                        }
                        run.status.equals("in_progress", ignoreCase = true) ||
                                run.status.equals("queued", ignoreCase = true) -> {
                            callback?.onStillRunning()
                        }
                        else -> {
                            Log.w(TAG, "poll: حالة غير معروفة ${run.status}")
                        }
                    }
                }

                override fun onError(message: String) {
                    Log.e(TAG, "poll failed: $message")
                }
            }
        )
    }

    // ============================================================
    // scheduleNextPoll
    // ============================================================
    @Synchronized
    private fun scheduleNextPoll(delayMs: Long) {
        if (!pollingActive || pollingCancelled) return

        pollRunnable?.let { pollHandler.removeCallbacks(it) }

        val runnable = Runnable {
            if (!pollingActive || pollingCancelled) return@Runnable
            val cb = pollingCallback ?: return@Runnable
            doPollOnce(cb)
        }

        pollRunnable = runnable
        pollHandler.postDelayed(runnable, delayMs)
    }

    // ============================================================
    // fetchJobsAndSteps
    // ============================================================
    private fun fetchJobsAndSteps(
        token: String,
        runInfo: BuildStateStore.RunInfo,
        callback: BuildCallback?
    ) {
        GitHubApi.getRunJobs(
            token, runInfo.safeOwner, runInfo.safeRepo, runInfo.runId,
            object : GitHubApi.Callback<List<GitHubApi.Job>> {
                override fun onSuccess(jobs: List<GitHubApi.Job>) {
                    if (pollingCancelled) return

                    if (jobs.isEmpty()) {
                        Log.d(TAG, "fetchJobsAndSteps: jobs فارغة")
                        return
                    }

                    val job = jobs[0]
                    Log.d(TAG, "fetchJobsAndSteps: job id=${job.id} steps=${job.steps.size}")

                    if (job.steps.isNotEmpty()) {
                        callback?.onStepsUpdate(job.steps)
                    }

                    fetchStepLogsIfNeeded(token, runInfo, job, callback)
                }

                override fun onError(message: String) {
                    Log.w(TAG, "getRunJobs failed: $message")
                }
            }
        )
    }

    // ============================================================
    // تتبع آخر job
    // ============================================================
    private var lastFetchedJobId: Long = 0L
    private var lastFetchedStepCount: Int = 0

    private fun fetchStepLogsIfNeeded(
        token: String,
        runInfo: BuildStateStore.RunInfo,
        job: GitHubApi.Job,
        callback: BuildCallback?
    ) {
        if (job.id == lastFetchedJobId &&
            job.steps.size == lastFetchedStepCount
        ) {
            return
        }

        lastFetchedJobId = job.id
        lastFetchedStepCount = job.steps.size

        GitHubApi.downloadJobLogs(
            token, runInfo.safeOwner, runInfo.safeRepo, job.id,
            object : GitHubApi.Callback<String> {
                override fun onSuccess(logs: String) {
                    if (pollingCancelled) return

                    val allLogs = extractAllLines(logs)
                    val errorLogs = extractErrorLines(logs)

                    callback?.onStepLogs(job.id, allLogs, errorLogs)
                }

                override fun onError(message: String) {
                    // ⭐ v6.7: سجّل الخطأ بدل تجاهله
                    Log.w(TAG, "downloadJobLogs failed: $message")
                }
            }
        )
    }

    // ============================================================
    // handleRunCompleted
    // ============================================================
    private fun handleRunCompleted(
        token: String,
        runInfo: BuildStateStore.RunInfo,
        conclusion: String,
        callback: BuildCallback?
    ) {
        stopPollingInternal()

        if (conclusion.equals("success", ignoreCase = true)) {
            downloadArtifact(
                token, runInfo.safeOwner, runInfo.safeRepo,
                runInfo.safeProjectType,
                callback
            )
        } else {
            fetchFailureDetails(
                token, runInfo.safeOwner, runInfo.safeRepo,
                runInfo.runId, conclusion, callback
            )
        }
    }

    // ============================================================
    // downloadArtifact
    // ============================================================
    private fun downloadArtifact(
        token: String,
        owner: String,
        repo: String,
        projectTypeId: String,
        callback: BuildCallback?
    ) {
        val projectType = ProjectType.fromId(projectTypeId)

        callback?.onProgress("اكتمل البناء، جارٍ تنزيل APK...")

        val artifactName = GitHubConfig.artifactNameFor(projectType)

        val apkFileName = when (projectType) {
            ProjectType.FLUTTER -> "app-release.apk"
            ProjectType.JAVA -> "app-debug.apk"
            ProjectType.KOTLIN -> "app-debug.apk"
            ProjectType.REACT_NATIVE -> "app-release.apk"
        }

        GitHubApi.latestArtifact(
            token, owner, repo, artifactName,
            object : GitHubApi.Callback<GitHubApi.Artifact> {
                override fun onSuccess(artifact: GitHubApi.Artifact) {
                    if (artifact.expired) {
                        BuildStateStore.clearRun(appContext)
                        callback?.onError("الملف منتهي الصلاحية على GitHub")
                        return
                    }

                    val destination = File(
                        appContext.cacheDir, "klencod-direct-$apkFileName"
                    )

                    GitHubApi.downloadArtifact(
                        token, owner, repo, artifact.id, destination,
                        object : GitHubApi.DownloadCallback<File> {
                            override fun onProgress(
                                downloadedBytes: Long,
                                totalBytes: Long
                            ) {
                                callback?.onDownloadProgress(downloadedBytes, totalBytes)
                            }

                            override fun onSuccess(apk: File) {
                                try {
                                    val downloads = Environment
                                        .getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_DOWNLOADS
                                        )
                                    if (!downloads.exists() && !downloads.mkdirs()) {
                                        throw IOException("تعذر إنشاء مجلد التنزيلات")
                                    }

                                    val stamp = SimpleDateFormat(
                                        "yyyyMMdd_HHmmss", Locale.getDefault()
                                    ).format(Date())

                                    val prefix = when (projectType) {
                                        ProjectType.FLUTTER -> "flutter"
                                        ProjectType.JAVA -> "java"
                                        ProjectType.KOTLIN -> "kotlin"
                                        ProjectType.REACT_NATIVE -> "reactnative"
                                    }

                                    val saved = File(
                                        downloads,
                                        "${prefix}_${safeName(repo)}_$stamp.apk"
                                    )
                                    copy(apk, saved)

                                    Log.i(TAG, "APK saved: ${saved.absolutePath}")

                                    BuildStateStore.clearRun(appContext)

                                    callback?.onSuccess(saved)
                                } catch (e: Exception) {
                                    Log.e(TAG, "فشل حفظ APK: ${e.message}")
                                    callback?.onError("تعذر حفظ APK: ${e.message}")
                                }
                            }

                            override fun onError(message: String) {
                                Log.e(TAG, "downloadArtifact failed: $message")
                                callback?.onError("تعذر تنزيل APK: $message")
                            }
                        }
                    )
                }

                override fun onError(message: String) {
                    Log.w(TAG, "latestArtifact failed: $message")
                    BuildStateStore.clearRun(appContext)
                    callback?.onError(
                        "لم يُعثر على APK — قد يستغرق رفعه لحظات. أعد المحاولة."
                    )
                }
            }
        )
    }

    // ============================================================
    // ⭐ v6.7: fetchFailureDetails مع Run logs fallback
    // ============================================================
    private fun fetchFailureDetails(
        token: String,
        owner: String,
        repo: String,
        runId: Long,
        conclusion: String,
        callback: BuildCallback?
    ) {
        callback?.onProgress("فشل البناء، جارٍ جلب التفاصيل...")

        GitHubApi.getRunAnnotations(
            token, owner, repo, runId,
            object : GitHubApi.Callback<List<String>> {
                override fun onSuccess(annotations: List<String>) {
                    val sb = StringBuilder()
                    var hasQuota = false

                    for (msg in annotations) {
                        if (msg.isBlank()) continue
                        val lower = msg.lowercase(Locale.US)
                        if (lower.contains("job was not started") ||
                            lower.contains("recent account payments") ||
                            lower.contains("spending limit")
                        ) {
                            hasQuota = true
                        }
                        if (sb.isNotEmpty()) sb.append("\n\n")
                        sb.append(msg.trim())
                    }

                    // حصّة GitHub؟ أرسل خطأ خاصاً
                    if (hasQuota) {
                        BuildStateStore.clearRun(appContext)
                        callback?.onError(GITHUB_QUOTA_PREFIX + sb.toString())
                        return
                    }

                    // أرسل annotations (لقسم "رسائل GitHub الرسمية")
                    if (sb.isNotEmpty()) {
                        callback?.onAnnotations(sb.toString())
                    }

                    // ⭐ v6.7: حمّل Run logs كـ fallback
                    downloadRunLogsAsFallback(
                        token, owner, repo, runId, conclusion, callback
                    )
                }

                override fun onError(message: String) {
                    // ⭐ v6.7: حتى لو فشلت annotations، جرّب Run logs
                    downloadRunLogsAsFallback(
                        token, owner, repo, runId, conclusion, callback
                    )
                }
            }
        )
    }

    // ============================================================
    // ⭐ v6.7: Run logs fallback (آخر 200 سطر)
    // ============================================================
    /**
     * يحمّل سجل الـ Run الكامل، ويأخذ آخر [FALLBACK_RUN_LOG_LINES] سطر.
     * يستخرج منها الإشعار المركّز + السجل الكامل.
     */
    private fun downloadRunLogsAsFallback(
        token: String,
        owner: String,
        repo: String,
        runId: Long,
        conclusion: String,
        callback: BuildCallback?
    ) {
        GitHubApi.downloadRunLogs(
            token, owner, repo, runId,
            object : GitHubApi.Callback<String> {
                override fun onSuccess(fullLogs: String) {
                    try {
                        val allLines = fullLogs.split(Regex("\\r?\\n"))

                        // آخر N سطر
                        val lastLines = if (allLines.size <= FALLBACK_RUN_LOG_LINES) {
                            allLines
                        } else {
                            allLines.subList(
                                allLines.size - FALLBACK_RUN_LOG_LINES,
                                allLines.size
                            )
                        }

                        val lastText = lastLines.joinToString("\n")

                        val allLogs = extractAllLines(lastText)
                        val errorLogs = extractErrorLines(lastText)

                        BuildStateStore.clearRun(appContext)

                        // أرسل السجل الكامل + الإشعار المركّز
                        callback?.onStepLogs(runId, allLogs, errorLogs)

                        if (errorLogs.isEmpty() && allLogs.isEmpty()) {
                            callback?.onError("فشل البناء: $conclusion")
                        } else {
                            callback?.onError(ANNOTATIONS_PREFIX + conclusion)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "downloadRunLogsAsFallback failed: ${e.message}")
                        BuildStateStore.clearRun(appContext)
                        callback?.onError("فشل البناء: $conclusion")
                    }
                }

                override fun onError(message: String) {
                    Log.w(TAG, "downloadRunLogs failed: $message")
                    BuildStateStore.clearRun(appContext)
                    callback?.onError("فشل البناء: $conclusion")
                }
            }
        )
    }

    // ============================================================
    // أدوات
    // ============================================================
    private fun safeName(value: String?): String {
        if (value.isNullOrBlank()) return "app"
        return value.replace(Regex("[^A-Za-z0-9._-]+"), "_")
    }

    @Throws(IOException::class)
    private fun copy(source: File, target: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } != -1) {
                    output.write(buffer, 0, count)
                }
                output.flush()
            }
        }
    }

    // ============================================================
    // Install APK
    // ============================================================
    fun installApk(apkFile: File?) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(appContext, "ملف APK غير موجود", Toast.LENGTH_SHORT).show()
            return
        }
        ApkInstallDiagnostics.install(appContext, apkFile)
    }

    // ============================================================
    // تنظيف علامات GitHub من السطر
    // ============================================================
    private fun cleanGitHubMarkers(line: String): String {
        var result = line

        result = result.replace(Regex("^##\\[(?:group|endgroup|error|warning|command|debug|notice|section)\\]\\s*"), "")
        result = result.replace(Regex("^::(?:error|warning|notice|debug)\\s*::\\s*"), "")
        result = result.replace(Regex("^##\\[.*?\\]\\s*"), "")

        return result
    }

    // ============================================================
    // extractAllLines — كل السجل
    // ============================================================
    fun extractAllLines(logs: String?): List<BuildLogLine> {
        val result = mutableListOf<BuildLogLine>()
        if (logs.isNullOrBlank()) return result

        val locationPattern = Pattern.compile(
            "([^\\s]+\\.(?:dart|java|kt|kts|py|js|ts|tsx|jsx|xml|yaml|yml|json|gradle))[:(](\\d+)(?::(\\d+))?"
        )

        val lines = logs.split(Regex("\\r?\\n"))
        for (line in lines) {
            val trimmed = line.trimEnd()
            if (trimmed.isEmpty()) continue

            val clean = cleanGitHubMarkers(trimmed)

            var filePath: String? = null
            var lineNum = 0
            var colNum = 0

            val m = locationPattern.matcher(clean)
            if (m.find()) {
                filePath = m.group(1)
                try {
                    lineNum = m.group(2).toInt()
                    m.group(3)?.let { colNum = it.toInt() }
                } catch (_: NumberFormatException) {
                }
            }

            val logType = classifyLogLine(clean)
            result.add(BuildLogLine(clean, filePath, lineNum, colNum, logType))
            if (result.size >= MAX_ALL_LOG_LINES) break
        }
        return result
    }

    // ============================================================
    // extractErrorLines — الإشعار المركّز
    // ============================================================
    /**
     * يبدأ من أول سطر يحتوي:
     *   - "FAILURE:" (Gradle failure)
     *   - أو "BUILD FAILED"
     *   - أو "##[error]" / "::error::"
     * حتى نهاية السجل.
     */
    fun extractErrorLines(logs: String?): List<BuildLogLine> {
        val result = mutableListOf<BuildLogLine>()
        if (logs.isNullOrBlank()) return result

        val rawLines = logs.split(Regex("\\r?\\n"))
        if (rawLines.isEmpty()) return result

        // 1) ابحث عن بداية الفشل
        var startIndex = -1

        for (i in rawLines.indices) {
            val trimmedLower = rawLines[i].lowercase(Locale.US).trim()

            if (trimmedLower.contains("failure:") &&
                !trimmedLower.contains("failures:")
            ) {
                startIndex = i
                break
            }

            if (trimmedLower.startsWith("build failed") ||
                trimmedLower.contains("build failed with an exception")
            ) {
                startIndex = i
                break
            }

            if (trimmedLower.contains("##[error]") ||
                trimmedLower.contains("::error::")
            ) {
                startIndex = i
                break
            }
        }

        // 2) إذا لم نجد → آخر 50 سطر (احتياطي)
        if (startIndex < 0) {
            startIndex = maxOf(0, rawLines.size - 50)
        }

        // 3) ابنِ النتيجة
        val locationPattern = Pattern.compile(
            "([^\\s]+\\.(?:dart|java|kt|kts|py|js|ts|tsx|jsx|xml|yaml|yml|json|gradle))[:(](\\d+)(?::(\\d+))?"
        )

        for (i in startIndex until rawLines.size) {
            val rawLine = rawLines[i]
            val clean = cleanGitHubMarkers(rawLine)

            if (clean.isBlank()) {
                result.add(BuildLogLine("", null, 0, 0, LOG_TYPE_NORMAL))
                continue
            }

            var filePath: String? = null
            var lineNum = 0
            var colNum = 0

            val m = locationPattern.matcher(clean)
            if (m.find()) {
                filePath = m.group(1)
                try {
                    lineNum = m.group(2).toInt()
                    m.group(3)?.let { colNum = it.toInt() }
                } catch (_: NumberFormatException) {
                }
            }

            val logType = classifyLogLine(clean)
            result.add(BuildLogLine(clean, filePath, lineNum, colNum, logType))

            if (result.size >= MAX_ERROR_LOG_LINES) break
        }

        return result
    }

    // ============================================================
    // classifyLogLine — يحدد الألوان
    // ============================================================
    private fun classifyLogLine(line: String?): Int {
        if (line == null) return LOG_TYPE_NORMAL
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return LOG_TYPE_NORMAL
        val lower = trimmed.lowercase(Locale.US)

        if (lower.startsWith("(node:")) return LOG_TYPE_NORMAL
        if (lower.contains("deprecationwarning")) return LOG_TYPE_NORMAL
        if (lower.contains("--trace-deprecation")) return LOG_TYPE_NORMAL
        if (lower.contains("use `node --trace")) return LOG_TYPE_NORMAL
        if (lower.contains("[dep0040]")) return LOG_TYPE_NORMAL
        if (lower.contains("[dep0169]")) return LOG_TYPE_NORMAL
        if (lower.contains("punycode")) return LOG_TYPE_NORMAL
        if (lower.contains("url.parse()")) return LOG_TYPE_NORMAL

        if (lower.startsWith("resolved java")) return LOG_TYPE_SUCCESS
        if (lower.startsWith("setting java")) return LOG_TYPE_SUCCESS
        if (lower.contains("\"install android sdk platform") && lower.contains("complete")) return LOG_TYPE_SUCCESS
        if (lower.contains("\"install android sdk platform") && lower.contains("finished")) return LOG_TYPE_SUCCESS
        if (lower.contains("install android sdk build-tools") && lower.contains("complete")) return LOG_TYPE_SUCCESS
        if (lower.startsWith("running gradle task")) return LOG_TYPE_SUCCESS
        if (lower.contains("artifact name is valid")) return LOG_TYPE_SUCCESS
        if (lower.contains("root directory input is valid")) return LOG_TYPE_SUCCESS
        if (lower.contains("beginning upload of artifact")) return LOG_TYPE_SUCCESS
        if (lower.startsWith("uploaded bytes")) return LOG_TYPE_SUCCESS
        if (lower.startsWith("sent ") && lower.contains("bytes")) return LOG_TYPE_SUCCESS
        if (lower.contains("with the provided path, there will be")) return LOG_TYPE_SUCCESS
        if (lower.contains("build succeeded")) return LOG_TYPE_SUCCESS
        if (lower.contains("built build/app")) return LOG_TYPE_SUCCESS
        if (lower.contains("apk generated")) return LOG_TYPE_SUCCESS
        if (lower.contains("✓ built")) return LOG_TYPE_SUCCESS

        if (lower.contains("font asset") && lower.contains("tree-shaken")) return LOG_TYPE_WARNING
        if (lower.contains("tree-shaking can be disabled")) return LOG_TYPE_WARNING
        if (lower.contains("--no-tree-shake-icons")) return LOG_TYPE_WARNING
        if (lower.contains("unable to find directory entry in pubspec.yaml")) return LOG_TYPE_WARNING
        if (lower.contains("warning:") && !lower.contains("0 warning")) return LOG_TYPE_WARNING
        if (lower.contains("warn:") && !lower.contains("0 warning")) return LOG_TYPE_WARNING
        if (lower.startsWith("warning:")) return LOG_TYPE_WARNING
        if (lower.contains("deprecated") && !lower.contains("deprecationwarning")) return LOG_TYPE_WARNING
        if (lower.contains("only the first match will be used")) return LOG_TYPE_WARNING

        // أسطر Gradle المهمة
        if (lower.startsWith("* where:")) return LOG_TYPE_ERROR
        if (lower.startsWith("* what went wrong:")) return LOG_TYPE_ERROR
        if (lower.startsWith("* try:")) return LOG_TYPE_WARNING
        if (lower.startsWith("> failed to apply plugin")) return LOG_TYPE_ERROR
        if (lower.startsWith("> error:")) return LOG_TYPE_ERROR
        if (lower.startsWith("> minimum supported")) return LOG_TYPE_ERROR
        if (lower.contains("build failed with an exception")) return LOG_TYPE_ERROR
        if (lower.startsWith("potential fix:")) return LOG_TYPE_WARNING
        if (lower.contains("┌─ flutter fix")) return LOG_TYPE_WARNING
        if (lower.startsWith("│")) return LOG_TYPE_WARNING
        if (lower.startsWith("└─")) return LOG_TYPE_WARNING
        if (lower.startsWith("failed to apply plugin")) return LOG_TYPE_ERROR
        if (lower.startsWith("an exception occurred")) return LOG_TYPE_ERROR

        if (lower.contains("0 warning") || lower.contains("warnings: 0")) return LOG_TYPE_SUCCESS
        if (lower.contains("0 error") || lower.contains("errors: 0")) return LOG_TYPE_SUCCESS

        if (lower.contains("error:") || lower.startsWith("error")) return LOG_TYPE_ERROR
        if (lower.contains("build failed")) return LOG_TYPE_ERROR
        if (lower.contains("compilation failed")) return LOG_TYPE_ERROR
        if (lower.contains("execution failed")) return LOG_TYPE_ERROR
        if (lower.contains("a problem occurred")) return LOG_TYPE_ERROR
        if (lower.contains("what went wrong")) return LOG_TYPE_ERROR
        if (lower.contains("fatal:")) return LOG_TYPE_ERROR
        if (lower.contains("critical:")) return LOG_TYPE_ERROR
        if (lower.contains("exception:")) return LOG_TYPE_ERROR
        if (lower.contains("could not resolve")) return LOG_TYPE_ERROR
        if (lower.contains("could not find")) return LOG_TYPE_ERROR
        if (lower.contains("could not download")) return LOG_TYPE_ERROR
        if (lower.contains("could not create")) return LOG_TYPE_ERROR
        if (lower.contains("could not determine")) return LOG_TYPE_ERROR
        if (lower.contains("cannot find")) return LOG_TYPE_ERROR
        if (lower.contains("cannot resolve")) return LOG_TYPE_ERROR
        if (lower.contains("unable to load")) return LOG_TYPE_ERROR
        if (lower.contains("undefined name")) return LOG_TYPE_ERROR
        if (lower.contains("isn't defined")) return LOG_TYPE_ERROR
        if (lower.contains("no such file")) return LOG_TYPE_ERROR
        if (lower.contains("unhandled exception")) return LOG_TYPE_ERROR
        if (lower.contains("formatexception")) return LOG_TYPE_ERROR
        if (lower.contains("stateerror")) return LOG_TYPE_ERROR
        if (lower.contains("assertionerror")) return LOG_TYPE_ERROR
        if (lower.contains("indexerror")) return LOG_TYPE_ERROR
        if (lower.contains("rangeerror")) return LOG_TYPE_ERROR
        if (lower.contains("nosuchmethoderror")) return LOG_TYPE_ERROR
        if (lower.contains("caused by")) return LOG_TYPE_ERROR
        if (lower.contains("rejected")) return LOG_TYPE_ERROR
        if (lower.contains("failed")) return LOG_TYPE_ERROR
        if (lower.contains("failure")) return LOG_TYPE_ERROR
        if (lower.contains("fatal")) return LOG_TYPE_ERROR

        return LOG_TYPE_NORMAL
    }
}