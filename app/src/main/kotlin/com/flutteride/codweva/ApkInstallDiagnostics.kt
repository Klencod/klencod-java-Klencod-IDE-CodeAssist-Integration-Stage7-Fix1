package com.flutteride.codweva

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Installs an APK through PackageInstaller so Android's actual install status can be recorded. */
object ApkInstallDiagnostics {
    const val ACTION_INSTALL_STATUS = "com.flutteride.codweva.ACTION_APK_INSTALL_STATUS"
    const val EXTRA_APK_PATH = "klencod_apk_path"
    const val EXTRA_SESSION_ID = "klencod_session_id"

    fun install(context: Context, apk: File) {
        val app = context.applicationContext
        if (!apk.isFile || apk.length() <= 0L) {
            Toast.makeText(app, "ملف APK غير موجود أو فارغ", Toast.LENGTH_LONG).show()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(app, "اسمح لـ Klencod IDE بتثبيت التطبيقات من هذا المصدر، ثم أعد المحاولة", Toast.LENGTH_LONG).show()
                val settings = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(settings)
                return
            }
            val installer = app.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output -> input.copyTo(output); session.fsync(output) }
                }
                val callback = Intent(app, ApkInstallStatusReceiver::class.java).apply {
                    action = ACTION_INSTALL_STATUS
                    putExtra(EXTRA_APK_PATH, apk.absolutePath)
                    putExtra(EXTRA_SESSION_ID, sessionId)
                    data = Uri.parse("klencod-install://session/$sessionId")
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(app, sessionId, callback, flags)
                session.commit(pending.intentSender)
            }
            Toast.makeText(app, "تم إرسال APK إلى مثبت Android؛ سيُحفظ تقرير بنتيجة التثبيت", Toast.LENGTH_LONG).show()
        } catch (e: Throwable) {
            saveReport(app, "فشل بدء التثبيت", "Exception: ${e.javaClass.name}\nMessage: ${e.message}\nAPK: ${apk.absolutePath}\n", "install-start")
            Toast.makeText(app, "تعذر بدء التثبيت: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    internal fun recordStatus(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val apkPath = intent.getStringExtra(EXTRA_APK_PATH).orEmpty()
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        val statusName = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "PENDING_USER_ACTION"
            PackageInstaller.STATUS_SUCCESS -> "SUCCESS"
            PackageInstaller.STATUS_FAILURE -> "FAILURE"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "FAILURE_BLOCKED"
            PackageInstaller.STATUS_FAILURE_ABORTED -> "FAILURE_ABORTED"
            PackageInstaller.STATUS_FAILURE_INVALID -> "FAILURE_INVALID"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "FAILURE_CONFLICT"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "FAILURE_STORAGE"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "FAILURE_INCOMPATIBLE"
            else -> "STATUS_$status"
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
            }
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { context.startActivity(confirm) } catch (_: Throwable) {}
            }
            return
        }
        val report = """Klencod IDE - APK installation diagnostic
Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}
Result: $statusName
Status code: $status
Android message: ${message.ifBlank { "(no message supplied by Android)" }}
Session ID: $sessionId
APK: $apkPath

Note: Android may return a generic message. This report records the status supplied by PackageInstaller and does not always expose the underlying system reason.
""".trimIndent() + "\n"
        val file = saveReport(context.applicationContext, "نتيجة التثبيت: $statusName", report, "install-$sessionId")
        Toast.makeText(context, if (status == PackageInstaller.STATUS_SUCCESS) "تم تثبيت التطبيق. حُفظ تقرير النتيجة" else "رفض Android التثبيت أو أُوقف. حُفظ التقرير: ${file?.name ?: "Klencod"}", Toast.LENGTH_LONG).show()
    }

    private fun saveReport(context: Context, title: String, content: String, suffix: String): File? {
        return try {
            val dir = File(context.filesDir, "install-diagnostics").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "klencod-$suffix-$stamp.txt")
            file.writeText(content, Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Klencod")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            }
            file
        } catch (_: Throwable) { null }
    }
}

class ApkInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ApkInstallDiagnostics.ACTION_INSTALL_STATUS) {
            ApkInstallDiagnostics.recordStatus(context, intent)
        }
    }
}
