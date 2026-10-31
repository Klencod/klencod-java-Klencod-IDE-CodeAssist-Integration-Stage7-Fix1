package com.flutteride.codweva

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CancellationException
import androidx.core.app.NotificationCompat
import com.flutteride.codweva.build.KlencodLocalAndroidBuildSession
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Local build worker. Kept separate from BuildForegroundService so GitHub workflows remain untouched. */
class LocalBuildForegroundService : Service() {
    companion object {
        private const val TAG = "KlencodLocalBuild"
        private const val CHANNEL_ID = "klencod_local_build"
        private const val NOTIFICATION_ID = 7101
        const val ACTION_START = "com.flutteride.codweva.local.START"
        const val ACTION_CANCEL = "com.flutteride.codweva.local.CANCEL"
        const val EXTRA_PROJECT_PATH = "local_project_path"

        @JvmStatic fun start(context: Context, projectDir: File) {
            val intent = Intent(context, LocalBuildForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROJECT_PATH, projectDir.absolutePath)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        @JvmStatic fun cancel(context: Context) {
            context.startService(Intent(context, LocalBuildForegroundService::class.java).apply { action = ACTION_CANCEL })
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val started = AtomicBoolean(false)
    private val canceled = AtomicBoolean(false)
    @Volatile private var projectDir: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                canceled.set(true)
                broadcastLog("تم طلب إلغاء البناء المحلي. يجري إيقاف المهام الحالية...")
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val path = intent.getStringExtra(EXTRA_PROJECT_PATH)
                val dir = path?.let(::File)
                if (dir == null || !dir.isDirectory) {
                    broadcastFailure("مسار مشروع البناء المحلي غير صالح")
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                if (!started.compareAndSet(false, true)) return START_NOT_STICKY
                projectDir = dir
                canceled.set(false)
                createChannel()
                startForeground(NOTIFICATION_ID, buildNotification("تهيئة البناء المحلي"))
                broadcastLog("بدء البناء المحلي للمشروع: ${dir.name}")
                executor.execute { runLocalBuild(startId, dir) }
            }
        }
        return START_NOT_STICKY
    }

    private fun runLocalBuild(startId: Int, dir: File) {
        try {
            reportMemoryBudget()
            val session = KlencodLocalAndroidBuildSession(applicationContext)
            val result = runBlocking {
                session.buildDebugApk(
                    projectRoot = dir.toPath(),
                    onProgress = { fraction, message ->
                        val percent = if (fraction < 0.0) -1 else (fraction.coerceIn(0.0, 1.0) * 100).toInt()
                        val status = message ?: if (percent < 0) "البناء المحلي جارٍ..." else "البناء المحلي: $percent%"
                        updateNotification(status)
                        broadcastProgress(status)
                    },
                    onLog = { broadcastLog(it) },
                    onStructuredLog = { broadcastLog(it.toString()) },
                    onDiagnostic = { diagnostic ->
                        broadcastLog("[${diagnostic.severity}] ${diagnostic.message}")
                    },
                    onTask = { task, status -> broadcastLog("[$status] $task") },
                    shouldCancel = { canceled.get() },
                )
            }
            if (canceled.get()) {
                broadcastFailure("تم إلغاء البناء المحلي")
            } else if (!result.outcome.succeeded) {
                val details = result.outcome.toString()
                broadcastFailure("فشل البناء المحلي. $details")
            } else if (result.apk == null || !result.apk.toFile().isFile) {
                broadcastFailure("انتهى تنفيذ مهام البناء دون العثور على ملف APK في build/outputs/apk")
            } else {
                broadcastLog("تم إنشاء APK: ${result.apk}")
                broadcastSuccess(result.apk.toFile())
            }
        } catch (cancelled: CancellationException) {
            broadcastFailure("تم إلغاء البناء المحلي")
        } catch (t: Throwable) {
            Log.e(TAG, "Local build failed", t)
            broadcastFailure("${t.javaClass.simpleName}: ${t.message ?: "خطأ غير معروف"}")
        } finally {
            stopForegroundCompat()
            started.set(false)
            stopSelf(startId)
        }
    }

    /** Emit a realistic device-memory snapshot; never start parallel compiler workers on this device. */
    private fun reportMemoryBudget() {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val availableMb = info.availMem / (1024L * 1024L)
        val heapMaxMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L)
        val memoryClassMb = manager.memoryClass
        broadcastLog("ذاكرة النظام المتاحة قبل البناء: ${availableMb}MB؛ حد ذاكرة التطبيق: ${heapMaxMb}MB؛ memoryClass: ${memoryClassMb}MB")
        if (info.lowMemory || availableMb < 350L) {
            broadcastLog("تحذير: الذاكرة المتاحة منخفضة. قد يتوقف البناء إذا نفدت ذاكرة التطبيق.")
        }
        broadcastLog("تم تثبيت توازي مهام البناء على مهمة واحدة لتقليل استهلاك الذاكرة.")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "البناء المحلي", NotificationManager.IMPORTANCE_LOW).apply {
                description = "حالة بناء APK على الجهاز"
                setShowBadge(false)
            })
        }
    }

    private fun buildNotification(message: String): Notification {
        val cancelIntent = PendingIntent.getService(
            this, 7102, Intent(this, LocalBuildForegroundService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Klencod — البناء المحلي")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إلغاء", cancelIntent)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
    }

    private fun broadcast(action: String, fill: Intent.() -> Unit = {}) {
        sendBroadcast(Intent(action).setPackage(packageName).apply(fill))
    }
    private fun broadcastLog(message: String) = broadcast(BuildForegroundService.ACTION_BUILD_LOG) {
        putExtra(BuildForegroundService.EXTRA_MESSAGE, message)
    }
    private fun broadcastProgress(message: String) = broadcast(BuildForegroundService.ACTION_BUILD_PROGRESS) {
        putExtra(BuildForegroundService.EXTRA_MESSAGE, message)
    }
    private fun broadcastFailure(error: String) = broadcast(BuildForegroundService.ACTION_BUILD_FAILED) {
        putExtra(BuildForegroundService.EXTRA_ERROR_BROADCAST, error)
    }
    private fun broadcastSuccess(apk: File) = broadcast(BuildForegroundService.ACTION_BUILD_SUCCESS) {
        putExtra(BuildForegroundService.EXTRA_APK_PATH_BROADCAST, apk.absolutePath)
    }

    override fun onDestroy() {
        canceled.set(true)
        executor.shutdownNow()
        super.onDestroy()
    }
}
