package com.flutteride.codweva

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BuildForegroundService — خدمة بناء APK في الخلفية (v7.0).
 *
 * ⭐ v7.0 — إضافة دعم uploadMethod:
 *   - startBuild() يقبل GitHubApi.UploadMethod كمعامل
 *   - يُمرَّر عبر Intent إلى runBuild()
 *   - يُمرَّر إلى FlutterBuildApi.triggerBuild()
 *
 * ⭐ v6.0 — إصلاح كراش startForegroundService():
 *   - ACTION_SHOW_SUCCESS و ACTION_SHOW_FAILURE يستدعيان startForeground() فوراً
 *   - إشعار مؤقت (placeholder) لإرضاء Android قبل الإشعار الحقيقي
 *   - stopForegroundNow() قبل stopSelf()
 *   - منع ForegroundServiceDidNotStartInTimeException
 *
 * ⭐ v5.0 — إغلاق فوري بعد البث
 */
class BuildForegroundService : Service() {

    // ============================================================
    // الثوابت العامة
    // ============================================================
    companion object {
        private const val TAG = "BuildService"

        private const val CHANNEL_ID = "klencod_build_channel"
        private const val CHANNEL_NAME = "بناء التطبيقات"
        private const val CHANNEL_DESC = "يعرض حالة بناء APK على GitHub"
        private const val NOTIFICATION_ID_PROGRESS = 7001
        private const val NOTIFICATION_ID_RESULT = 7002

        const val ACTION_START = "com.flutteride.codweva.action.START_BUILD"
        const val ACTION_STOP = "com.flutteride.codweva.action.STOP_BUILD"
        const val ACTION_SHOW_SUCCESS = "com.flutteride.codweva.action.SHOW_SUCCESS"
        const val ACTION_SHOW_FAILURE = "com.flutteride.codweva.action.SHOW_FAILURE"

        const val EXTRA_PROJECT_PATH = "extra_project_path"
        const val EXTRA_PROJECT_TYPE = "extra_project_type"
        const val EXTRA_APK_PATH = "extra_apk_path"
        const val EXTRA_ERROR = "extra_error"
        const val EXTRA_ERROR_IS_QUOTA = "extra_error_is_quota"

        // ⭐ v7.0: مفتاح طريقة الرفع
        const val EXTRA_UPLOAD_METHOD = "extra_upload_method"

        const val ACTION_BUILD_STARTED = "com.flutteride.codweva.BUILD_STARTED"
        const val ACTION_BUILD_PROGRESS = "com.flutteride.codweva.BUILD_PROGRESS"
        const val ACTION_BUILD_LOG = "com.flutteride.codweva.BUILD_LOG"
        const val ACTION_BUILD_STEPS = "com.flutteride.codweva.BUILD_STEPS"
        const val ACTION_BUILD_ANNOTATIONS = "com.flutteride.codweva.BUILD_ANNOTATIONS"
        const val ACTION_BUILD_STEP_LOGS = "com.flutteride.codweva.BUILD_STEP_LOGS"
        const val ACTION_BUILD_RUN_LOGS = "com.flutteride.codweva.BUILD_RUN_LOGS"
        const val ACTION_BUILD_REPO_READY = "com.flutteride.codweva.BUILD_REPO_READY"
        const val ACTION_BUILD_UPLOAD_PROGRESS = "com.flutteride.codweva.BUILD_UPLOAD_PROGRESS"
        const val ACTION_BUILD_DOWNLOAD_PROGRESS = "com.flutteride.codweva.BUILD_DOWNLOAD_PROGRESS"
        const val ACTION_BUILD_SUCCESS = "com.flutteride.codweva.BUILD_SUCCESS"
        const val ACTION_BUILD_FAILED = "com.flutteride.codweva.BUILD_FAILED"

        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_PERCENT = "extra_percent"
        const val EXTRA_APK_PATH_BROADCAST = "extra_apk_path_broadcast"
        const val EXTRA_ERROR_BROADCAST = "extra_error_broadcast"
        const val EXTRA_CURRENT = "extra_current"
        const val EXTRA_TOTAL = "extra_total"
        const val EXTRA_JOB_ID = "extra_job_id"
        const val EXTRA_REPO_NAME = "extra_repo_name"
        const val EXTRA_OWNER = "extra_owner"
        const val EXTRA_REPO = "extra_repo"
        const val EXTRA_RUN_ID = "extra_run_id"
        const val EXTRA_STEPS_JSON = "extra_steps_json"
        const val EXTRA_ALL_LOGS = "extra_all_logs"
        const val EXTRA_ERROR_LOGS = "extra_error_logs"
        const val EXTRA_DOWNLOADED = "extra_downloaded"
        const val EXTRA_TOTAL_BYTES = "extra_total_bytes"

        // ============================================================
        // نقطة الدخول
        // ============================================================
        /**
         * ⭐ v7.0: يبدأ البناء بطريقة رفع محددة.
         *
         * @param context سياق التطبيق
         * @param projectDir مجلد المشروع
         * @param projectType نوع المشروع
         * @param uploadMethod طريقة الرفع (GraphQL أو BATCHES) — افتراضياً GraphQL
         */
        @JvmStatic
        @JvmOverloads
        fun startBuild(
            context: Context,
            projectDir: File,
            projectType: ProjectType,
            uploadMethod: GitHubApi.UploadMethod = GitHubApi.UploadMethod.GRAPHQL
        ) {
            if (context == null || !projectDir.isDirectory) return

            val intent = Intent(context, BuildForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROJECT_PATH, projectDir.absolutePath)
                putExtra(EXTRA_PROJECT_TYPE, projectType.id)
                // ⭐ v7.0: نُمرّر طريقة الرفع
                putExtra(EXTRA_UPLOAD_METHOD, uploadMethod.name)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "فشل بدء الخدمة: ${e.message}", e)
            }
        }

        @JvmStatic
        fun stopBuild(context: Context) {
            try {
                context.stopService(
                    Intent(context, BuildForegroundService::class.java)
                )
            } catch (_: Exception) {
            }
        }

        /**
         * يُظهر إشعار النجاح (يستدعيه MainActivity بعد poll).
         */
        @JvmStatic
        fun showSuccessNotification(context: Context, apkFile: File) {
            try {
                val intent = Intent(context, BuildForegroundService::class.java).apply {
                    action = ACTION_SHOW_SUCCESS
                    putExtra(EXTRA_APK_PATH, apkFile.absolutePath)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        /**
         * يُظهر إشعار الفشل.
         */
        @JvmStatic
        fun showFailureNotification(
            context: Context,
            error: String,
            isQuota: Boolean
        ) {
            try {
                val intent = Intent(context, BuildForegroundService::class.java).apply {
                    action = ACTION_SHOW_FAILURE
                    putExtra(EXTRA_ERROR, error)
                    putExtra(EXTRA_ERROR_IS_QUOTA, isQuota)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        @JvmStatic
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val manager = context.getSystemService(NotificationManager::class.java)
                ?: return

            if (manager.getNotificationChannel(CHANNEL_ID) != null) return

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESC
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }

            manager.createNotificationChannel(channel)
        }
    }

    // ============================================================
    // الحالة الداخلية
    // ============================================================
    private val mainHandler = Handler(Looper.getMainLooper())

    private val buildExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "klencod-build-worker")
    }

    @Volatile private var cancelRequested = false

    private var currentProjectDir: File? = null
    private var currentProjectType: ProjectType = ProjectType.FLUTTER
    private var buildApi: FlutterBuildApi? = null

    // ⭐ v7.0: طريقة الرفع الحالية
    @Volatile
    private var currentUploadMethod: GitHubApi.UploadMethod = GitHubApi.UploadMethod.GRAPHQL

    private val started = AtomicBoolean(false)

    /** ⭐ v6.0: تتبع ما إذا تم استدعاء startForeground(). */
    @Volatile private var foregroundStarted = false

    // ============================================================
    // onCreate
    // ============================================================
    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    // ============================================================
    // onBind
    // ============================================================
    override fun onBind(intent: Intent?): IBinder? = null

    // ============================================================
    // onStartCommand
    // ============================================================
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // ============================================================
        // إيقاف
        // ============================================================
        if (intent?.action == ACTION_STOP) {
            cancelRequested = true
            stopForegroundNow()
            stopSelf()
            return START_NOT_STICKY
        }

        // ============================================================
        // ⭐ v6.0: إظهار إشعار النجاح — مع startForeground() فوري
        // ============================================================
        if (intent?.action == ACTION_SHOW_SUCCESS) {
            // ⭐ إجباري على Android O+ قبل أي عملية
            startForegroundSafely(buildPlaceholderNotification())

            val apkPath = intent.getStringExtra(EXTRA_APK_PATH)
            if (!apkPath.isNullOrEmpty()) {
                val apkFile = File(apkPath)
                if (apkFile.exists()) {
                    showSuccessNotification(apkFile)
                }
            }

            // ⭐ أوقف الـ Foreground قبل الإغلاق
            stopForegroundNow()
            stopSelf()
            return START_NOT_STICKY
        }

        // ============================================================
        // ⭐ v6.0: إظهار إشعار الفشل — مع startForeground() فوري
        // ============================================================
        if (intent?.action == ACTION_SHOW_FAILURE) {
            // ⭐ إجباري على Android O+ قبل أي عملية
            startForegroundSafely(buildPlaceholderNotification())

            val error = intent.getStringExtra(EXTRA_ERROR) ?: "خطأ غير معروف"
            val isQuota = intent.getBooleanExtra(EXTRA_ERROR_IS_QUOTA, false)
            showFailureNotification(error, isQuota)

            // ⭐ أوقف الـ Foreground قبل الإغلاق
            stopForegroundNow()
            stopSelf()
            return START_NOT_STICKY
        }

        // ============================================================
        // بدء البناء
        // ============================================================
        if (intent?.action == ACTION_START) {
            val path = intent.getStringExtra(EXTRA_PROJECT_PATH)
            val typeId = intent.getStringExtra(EXTRA_PROJECT_TYPE)

            // ⭐ v7.0: قراءة طريقة الرفع
            val uploadMethodName = intent.getStringExtra(EXTRA_UPLOAD_METHOD)
            val uploadMethod = try {
                if (uploadMethodName != null) {
                    GitHubApi.UploadMethod.valueOf(uploadMethodName)
                } else {
                    GitHubApi.UploadMethod.GRAPHQL
                }
            } catch (_: Exception) {
                GitHubApi.UploadMethod.GRAPHQL
            }

            if (path.isNullOrEmpty()) {
                Log.e(TAG, "مسار المشروع مفقود")
                stopSelf()
                return START_NOT_STICKY
            }

            val dir = File(path)
            if (!dir.isDirectory) {
                Log.e(TAG, "مسار المشروع غير صالح: $path")
                stopSelf()
                return START_NOT_STICKY
            }

            val type = ProjectType.fromId(typeId)

            if (started.getAndSet(true)) {
                Log.w(TAG, "البناء يعمل بالفعل")
                return START_NOT_STICKY
            }

            currentProjectDir = dir
            currentProjectType = type
            currentUploadMethod = uploadMethod
            cancelRequested = false

            val methodLabel = if (uploadMethod == GitHubApi.UploadMethod.GRAPHQL) {
                "GraphQL (سريع)"
            } else {
                "دفعات (يعمل مع أي حجم)"
            }

            // ---- إشعار ثابت ----
            startForegroundSafely(
                buildRunningNotification(
                    projectName = dir.name,
                    projectType = type
                )
            )

            // ---- بث إلى MainActivity ----
            broadcastLog(
                "بدء بناء APK (${type.displayName}) على GitHub Actions...\n" +
                "طريقة الرفع: $methodLabel"
            )

            // ---- بدء البناء في خيط منفصل ----
            buildExecutor.execute { runBuild() }

            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    // ============================================================
    // onDestroy
    // ============================================================
    override fun onDestroy() {
        super.onDestroy()
        cancelRequested = true

        try {
            buildApi?.cancelPolling()
        } catch (_: Exception) {
        }

        try {
            buildExecutor.shutdownNow()
        } catch (_: Exception) {
        }

        started.set(false)
        foregroundStarted = false
    }

    // ============================================================
    // runBuild
    // ============================================================
    private fun runBuild() {
        val projectDir = currentProjectDir ?: return
        val projectType = currentProjectType
        // ⭐ v7.0: نقرأ طريقة الرفع
        val uploadMethod = currentUploadMethod

        try {
            val api = FlutterBuildApi(applicationContext)
            buildApi = api

            // ⭐ v7.0: نُمرّر uploadMethod إلى triggerBuild
            api.triggerBuild(
                projectDir,
                projectType,
                uploadMethod,
                object : FlutterBuildApi.BuildCallback {

                    override fun onStarted(owner: String, repo: String, runId: Long) {
                        if (cancelRequested) return

                        Log.i(TAG, "onStarted: $owner/$repo #$runId")

                        broadcastStarted(owner, repo, runId)
                        broadcastRepoReady(repo)
                        broadcastLog("بدأت GitHub Actions رقم $runId")

                        // ⭐ v5.0: إغلاق فوري (500ms فقط ليتم توصيل البث)
                        mainHandler.postDelayed({
                            stopForegroundKeepNotification()
                            stopSelf()
                        }, 500L)
                    }

                    override fun onProgress(message: String) {
                        if (cancelRequested) return
                        broadcastLog(message)
                    }

                    override fun onStepsUpdate(steps: List<GitHubApi.Step>) {
                        if (cancelRequested) return
                        broadcastSteps(steps)
                    }

                    override fun onStepLogs(
                        jobId: Long,
                        allLogs: List<FlutterBuildApi.BuildLogLine>,
                        errorLogs: List<FlutterBuildApi.BuildLogLine>
                    ) {
                        if (cancelRequested) return
                        broadcastStepLogs(jobId, allLogs, errorLogs)
                    }

                    override fun onAnnotations(rawAnnotations: String) {
                        if (cancelRequested) return
                        broadcastAnnotations(rawAnnotations)
                    }

                    override fun onUploadProgress(current: Int, total: Int) {
                        if (cancelRequested) return
                        broadcastUploadProgress(current, total)
                    }

                    override fun onDownloadProgress(downloadedBytes: Long, totalBytes: Long) {
                        if (cancelRequested) return
                        broadcastDownloadProgress(downloadedBytes, totalBytes)
                    }

                    override fun onRepoReady(repoName: String) {
                        if (cancelRequested) return
                        broadcastRepoReady(repoName)
                    }

                    override fun onStillRunning() {
                        // لا شيء
                    }

                    override fun onError(error: String) {
                        if (cancelRequested) return

                        Log.e(TAG, "onError: $error")

                        stopForegroundNow()

                        val isQuota = error.startsWith(FlutterBuildApi.GITHUB_QUOTA_PREFIX)
                        showFailureNotification(error, isQuota)

                        broadcastFailure(error)

                        BuildStateStore.clearRun(this@BuildForegroundService)

                        mainHandler.postDelayed({
                            stopSelf()
                        }, 500L)
                    }

                    override fun onSuccess(apkFile: File) {
                        // لا شيء — MainActivity تعالج النجاح
                    }
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "استثناء أثناء البناء: ${e.message}", e)
            onTriggerError(e.message ?: "خطأ غير معروف")
        }
    }

    // ============================================================
    // onTriggerError
    // ============================================================
    private fun onTriggerError(error: String) {
        stopForegroundNow()
        showFailureNotification(error, isQuota = false)
        broadcastFailure(error)
        BuildStateStore.clearRun(this)
        mainHandler.postDelayed({
            stopSelf()
        }, 500L)
    }

    // ============================================================
    // الإشعارات
    // ============================================================

    /**
     * ⭐ v6.0: إشعار مؤقت — لإرضاء startForeground() على Android O+.
     * يُستبدل فوراً بالإشعار الحقيقي أو يُزال عند stopForegroundNow().
     */
    private fun buildPlaceholderNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Klencod IDE")
            .setContentText("جارٍ التحضير...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            )
            .build()
    }

    private fun buildRunningNotification(
        projectName: String,
        projectType: ProjectType
    ): Notification {

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("⏳ جارٍ بناء التطبيق")
            .setContentText("جارٍ البناء في الخلفية — اضغط لمتابعة التقدم في التطبيق")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "⏳ جارٍ بناء ${projectType.displayName}\n\n" +
                                "هذا الإشعار للتنبيه فقط.\n" +
                                "اضغط لفتح التطبيق ومتابعة الحالة."
                    )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            )

        val contentIntent = buildAppPendingIntent()
        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }

        return builder.build()
    }

    private fun showSuccessNotification(apkFile: File) {
        try {
            NotificationManagerCompat.from(this)
                .cancel(NOTIFICATION_ID_PROGRESS)
        } catch (_: Exception) {
        }

        val installIntent = buildInstallPendingIntent(apkFile)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("✅ اكتمل البناء بنجاح")
            .setContentText("اضغط على \"تثبيت\" لتثبيت التطبيق")
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDefaults(
                NotificationCompat.DEFAULT_SOUND or
                        NotificationCompat.DEFAULT_VIBRATE
            )

        val contentIntent = buildAppPendingIntent()
        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }

        if (installIntent != null) {
            builder.addAction(
                android.R.drawable.stat_sys_download_done,
                "تثبيت",
                installIntent
            )
        }

        try {
            val manager = NotificationManagerCompat.from(this)
            if (manager.areNotificationsEnabled()) {
                manager.notify(NOTIFICATION_ID_RESULT, builder.build())
            }
        } catch (_: SecurityException) {
        } catch (e: Exception) {
            Log.w(TAG, "فشل إظهار إشعار النجاح: ${e.message}")
        }
    }

    private fun showFailureNotification(error: String, isQuota: Boolean) {
        try {
            NotificationManagerCompat.from(this)
                .cancel(NOTIFICATION_ID_PROGRESS)
        } catch (_: Exception) {
        }

        val title = if (isQuota) {
            "⛔ توقف البناء — حصة GitHub"
        } else {
            "❌ فشل البناء"
        }

        val cleanError = if (error.startsWith(FlutterBuildApi.GITHUB_QUOTA_PREFIX)) {
            error.substring(FlutterBuildApi.GITHUB_QUOTA_PREFIX.length).trim()
        } else if (error.startsWith(FlutterBuildApi.ANNOTATIONS_PREFIX)) {
            error.substring(FlutterBuildApi.ANNOTATIONS_PREFIX.length).trim()
        } else {
            error
        }

        val body = if (cleanError.length > 250) {
            cleanError.substring(0, 250) + "..."
        } else {
            cleanError
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText("اضغط لفتح التطبيق وعرض التفاصيل")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$title\n\n$body\n\nاضغط لفتح التطبيق.")
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setDefaults(
                NotificationCompat.DEFAULT_SOUND or
                        NotificationCompat.DEFAULT_VIBRATE
            )

        val contentIntent = buildAppPendingIntent()
        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }

        try {
            val manager = NotificationManagerCompat.from(this)
            if (manager.areNotificationsEnabled()) {
                manager.notify(NOTIFICATION_ID_RESULT, builder.build())
            }
        } catch (_: SecurityException) {
        } catch (e: Exception) {
            Log.w(TAG, "فشل إظهار إشعار الفشل: ${e.message}")
        }
    }

    // ============================================================
    // PendingIntents
    // ============================================================
    private fun buildAppPendingIntent(): PendingIntent? {
        return try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            PendingIntent.getActivity(this, 100, intent, flags)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildInstallPendingIntent(apkFile: File): PendingIntent? {
        if (!apkFile.exists() || apkFile.length() == 0L) return null

        return try {
            val apkUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            PendingIntent.getActivity(this, 200, intent, flags)
        } catch (e: Exception) {
            Log.w(TAG, "فشل بناء Intent التثبيت: ${e.message}")
            null
        }
    }

    // ============================================================
    // Foreground helpers
    // ============================================================

    /**
     * ⭐ v6.0: يستدعي startForeground() مع تتبع الحالة.
     */
    private fun startForegroundSafely(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID_PROGRESS,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID_PROGRESS, notification)
            }
            foregroundStarted = true
        } catch (e: Exception) {
            Log.e(TAG, "فشل startForeground: ${e.message}", e)
            try {
                startForeground(NOTIFICATION_ID_PROGRESS, notification)
                foregroundStarted = true
            } catch (_: Exception) {
            }
        }
    }

    private fun stopForegroundNow() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
        foregroundStarted = false
    }

    private fun stopForegroundKeepNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // Broadcasters
    // ============================================================
    private fun broadcastStarted(owner: String, repo: String, runId: Long) {
        val intent = Intent(ACTION_BUILD_STARTED).apply {
            setPackage(packageName)
            putExtra(EXTRA_OWNER, owner)
            putExtra(EXTRA_REPO, repo)
            putExtra(EXTRA_RUN_ID, runId)
        }
        sendBroadcast(intent)
    }

    private fun broadcastLog(message: String) {
        val intent = Intent(ACTION_BUILD_LOG).apply {
            setPackage(packageName)
            putExtra(EXTRA_MESSAGE, message)
        }
        sendBroadcast(intent)
    }

    private fun broadcastSteps(steps: List<GitHubApi.Step>) {
        val sb = StringBuilder()
        for (i in steps.indices) {
            val s = steps[i]
            if (i > 0) sb.append("\u001F")
            sb.append(s.number).append("\u001E")
            sb.append(s.name).append("\u001E")
            sb.append(s.status).append("\u001E")
            sb.append(s.conclusion).append("\u001E")
            sb.append(s.startedAtMs).append("\u001E")
            sb.append(s.completedAtMs)
        }

        val intent = Intent(ACTION_BUILD_STEPS).apply {
            setPackage(packageName)
            putExtra(EXTRA_STEPS_JSON, sb.toString())
        }
        sendBroadcast(intent)
    }

    private fun broadcastStepLogs(
        jobId: Long,
        allLogs: List<FlutterBuildApi.BuildLogLine>,
        errorLogs: List<FlutterBuildApi.BuildLogLine>
    ) {
        val all = serializeLogs(allLogs)
        val errors = serializeLogs(errorLogs)

        val intent = Intent(ACTION_BUILD_STEP_LOGS).apply {
            setPackage(packageName)
            putExtra(EXTRA_JOB_ID, jobId)
            putExtra(EXTRA_ALL_LOGS, all)
            putExtra(EXTRA_ERROR_LOGS, errors)
        }
        sendBroadcast(intent)
    }

    private fun serializeLogs(logs: List<FlutterBuildApi.BuildLogLine>): String {
        val sb = StringBuilder()
        for (i in logs.indices) {
            val line = logs[i]
            if (i > 0) sb.append("\u001F")
            sb.append(line.text.replace("\u001E", " ").replace("\u001F", " "))
                .append("\u001E")
            sb.append(line.filePath ?: "").append("\u001E")
            sb.append(line.line).append("\u001E")
            sb.append(line.column).append("\u001E")
            sb.append(line.logType)
        }
        return sb.toString()
    }

    private fun broadcastAnnotations(rawAnnotations: String) {
        val intent = Intent(ACTION_BUILD_ANNOTATIONS).apply {
            setPackage(packageName)
            putExtra(EXTRA_MESSAGE, rawAnnotations)
        }
        sendBroadcast(intent)
    }

    private fun broadcastRepoReady(repoName: String) {
        val intent = Intent(ACTION_BUILD_REPO_READY).apply {
            setPackage(packageName)
            putExtra(EXTRA_REPO_NAME, repoName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastUploadProgress(current: Int, total: Int) {
        val intent = Intent(ACTION_BUILD_UPLOAD_PROGRESS).apply {
            setPackage(packageName)
            putExtra(EXTRA_CURRENT, current)
            putExtra(EXTRA_TOTAL, total)
        }
        sendBroadcast(intent)
    }

    private fun broadcastDownloadProgress(downloadedBytes: Long, totalBytes: Long) {
        val intent = Intent(ACTION_BUILD_DOWNLOAD_PROGRESS).apply {
            setPackage(packageName)
            putExtra(EXTRA_DOWNLOADED, downloadedBytes)
            putExtra(EXTRA_TOTAL_BYTES, totalBytes)
        }
        sendBroadcast(intent)
    }

    private fun broadcastFailure(error: String) {
        val intent = Intent(ACTION_BUILD_FAILED).apply {
            setPackage(packageName)
            putExtra(EXTRA_ERROR_BROADCAST, error)
        }
        sendBroadcast(intent)
    }
}