package com.flutteride.codweva.reactnative

import java.io.File
import java.io.FileWriter
import java.io.IOException

/**
 * ReactNativeCrashLogger — يولّد أدوات تسجيل الأعطال لمشروع React Native.
 *
 * v1.0 — ينتج ملفين Java:
 *   - CrashLogger.java       (يسجل الأخطاء والبدء في Downloads)
 *   - AppLoggerPackage.java  (Native Module للتواصل مع JS)
 *
 * المنطق مستخرج من ProjectStore.java.
 */
object ReactNativeCrashLogger {

    // ============================================================
    // الدالة الرئيسية
    // ============================================================
    @JvmStatic
    fun writeAll(javaDir: File, packageName: String) {
        if (!javaDir.exists() && !javaDir.mkdirs()) return

        writeFile(
            File(javaDir, "CrashLogger.java"),
            buildCrashLogger(packageName)
        )
        writeFile(
            File(javaDir, "AppLoggerPackage.java"),
            buildAppLoggerPackage(packageName)
        )
    }

    // ============================================================
    // CrashLogger.java
    // ============================================================
    private fun buildCrashLogger(packageName: String): String {
        val logFileName = "$packageName.txt"
        val crashFileName = "$packageName-crash.txt"

        return """
            |package $packageName;
            |
            |import android.content.ContentResolver;
            |import android.content.ContentValues;
            |import android.content.Context;
            |import android.database.Cursor;
            |import android.net.Uri;
            |import android.os.Build;
            |import android.os.Environment;
            |import android.provider.MediaStore;
            |
            |import java.io.File;
            |import java.io.FileOutputStream;
            |import java.io.IOException;
            |import java.io.OutputStream;
            |import java.io.PrintWriter;
            |import java.io.StringWriter;
            |import java.nio.charset.StandardCharsets;
            |import java.text.SimpleDateFormat;
            |import java.util.Date;
            |import java.util.Locale;
            |
            |/**
            | * Temporary startup crash logger for the generated APK.
            | * Remove this class after the runtime crash has been diagnosed.
            | */
            |public final class CrashLogger {
            |    private static final String FILE_NAME = "$crashFileName";
            |    private static final String LOG_FILE_NAME = "$logFileName";
            |
            |    private CrashLogger() {
            |    }
            |
            |    public static void log(Context context, String message) {
            |        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
            |                + " | " + (message == null ? "" : message) + "\n";
            |        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            |        appendPrivateLog(context, bytes);
            |        appendPhoneLog(context, bytes);
            |    }
            |
            |    public static void install(final Context context) {
            |        final Context appContext = context.getApplicationContext();
            |        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            |
            |        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            |            @Override
            |            public void uncaughtException(Thread thread, Throwable throwable) {
            |                try {
            |                    writeCrash(appContext, thread, throwable);
            |                } catch (Throwable ignored) {
            |                    // Never prevent Android's normal crash handling.
            |                }
            |
            |                if (previous != null) {
            |                    previous.uncaughtException(thread, throwable);
            |                } else {
            |                    android.os.Process.killProcess(android.os.Process.myPid());
            |                    System.exit(10);
            |                }
            |            }
            |        });
            |    }
            |
            |    private static void appendPrivateLog(Context context, byte[] bytes) {
            |        File file = new File(context.getFilesDir(), LOG_FILE_NAME);
            |        FileOutputStream output = null;
            |        try {
            |            output = new FileOutputStream(file, true);
            |            output.write(bytes);
            |            output.flush();
            |        } catch (IOException ignored) {
            |        } finally {
            |            if (output != null) {
            |                try { output.close(); } catch (IOException ignored) { }
            |            }
            |        }
            |    }
            |
            |    private static void appendPhoneLog(Context context, byte[] bytes) {
            |        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            |            appendToDownloads(context, LOG_FILE_NAME, bytes);
            |            return;
            |        }
            |        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            |        if (base == null) return;
            |        if (!base.exists() && !base.mkdirs()) return;
            |        File file = new File(base, LOG_FILE_NAME);
            |        FileOutputStream output = null;
            |        try {
            |            output = new FileOutputStream(file, true);
            |            output.write(bytes);
            |            output.flush();
            |        } catch (IOException ignored) {
            |        } finally {
            |            if (output != null) {
            |                try { output.close(); } catch (IOException ignored) { }
            |            }
            |        }
            |    }
            |
            |    private static void appendToDownloads(Context context, String fileName, byte[] bytes) {
            |        ContentResolver resolver = context.getContentResolver();
            |        Uri uri = findExistingDownload(resolver, fileName);
            |        boolean inserted = false;
            |        try {
            |            if (uri == null) {
            |                ContentValues values = new ContentValues();
            |                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            |                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            |                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            |                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            |                inserted = true;
            |            }
            |            if (uri == null) return;
            |            OutputStream output = null;
            |            try {
            |                output = resolver.openOutputStream(uri, "wa");
            |                if (output != null) { output.write(bytes); output.flush(); }
            |            } finally {
            |                if (output != null) output.close();
            |            }
            |        } catch (Exception ignored) {
            |            if (inserted && uri != null) {
            |                try { resolver.delete(uri, null, null); } catch (Exception ignoredDelete) { }
            |            }
            |        }
            |    }
            |
            |    private static void writeCrash(Context context, Thread thread, Throwable throwable) {
            |        StringWriter stackWriter = new StringWriter();
            |        PrintWriter printWriter = new PrintWriter(stackWriter);
            |        throwable.printStackTrace(printWriter);
            |        printWriter.flush();
            |
            |        StringBuilder report = new StringBuilder();
            |        report.append("startup crash report\n");
            |        report.append("created: ")
            |                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
            |                .append("\n");
            |        report.append("package: ").append(context.getPackageName()).append("\n");
            |        report.append("thread: ").append(thread == null ? "unknown" : thread.getName()).append("\n");
            |        report.append("android: ").append(Build.VERSION.RELEASE)
            |                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
            |        report.append("device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
            |        report.append("\nstacktrace:\n");
            |        report.append(stackWriter.toString());
            |
            |        byte[] bytes = report.toString().getBytes(StandardCharsets.UTF_8);
            |        writePrivateCopy(context, bytes);
            |        writePhoneReadableCopy(context, bytes);
            |    }
            |
            |    private static void writePrivateCopy(Context context, byte[] bytes) {
            |        File file = new File(context.getFilesDir(), FILE_NAME);
            |        FileOutputStream output = null;
            |        try {
            |            output = new FileOutputStream(file, false);
            |            output.write(bytes);
            |            output.flush();
            |        } catch (IOException ignored) {
            |        } finally {
            |            if (output != null) {
            |                try { output.close(); } catch (IOException ignored) { }
            |            }
            |        }
            |    }
            |
            |    private static void writePhoneReadableCopy(Context context, byte[] bytes) {
            |        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            |            writeToDownloads(context, bytes);
            |            return;
            |        }
            |        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            |        if (base == null) return;
            |        if (!base.exists() && !base.mkdirs()) return;
            |        File file = new File(base, FILE_NAME);
            |        FileOutputStream output = null;
            |        try {
            |            output = new FileOutputStream(file, false);
            |            output.write(bytes);
            |            output.flush();
            |        } catch (IOException ignored) {
            |        } finally {
            |            if (output != null) {
            |                try { output.close(); } catch (IOException ignored) { }
            |            }
            |        }
            |    }
            |
            |    private static void writeToDownloads(Context context, byte[] bytes) {
            |        ContentResolver resolver = context.getContentResolver();
            |        Uri uri = findExistingDownload(resolver);
            |        boolean inserted = false;
            |        try {
            |            if (uri == null) {
            |                ContentValues values = new ContentValues();
            |                values.put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME);
            |                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            |                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            |                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            |                inserted = true;
            |            }
            |            if (uri == null) return;
            |            OutputStream output = null;
            |            try {
            |                output = resolver.openOutputStream(uri, "wt");
            |                if (output != null) {
            |                    output.write(bytes);
            |                    output.flush();
            |                }
            |            } finally {
            |                if (output != null) output.close();
            |            }
            |        } catch (Exception ignored) {
            |            if (inserted && uri != null) {
            |                try { resolver.delete(uri, null, null); } catch (Exception ignoredDelete) { }
            |            }
            |        }
            |    }
            |
            |    private static Uri findExistingDownload(ContentResolver resolver) {
            |        return findExistingDownload(resolver, FILE_NAME);
            |    }
            |
            |    private static Uri findExistingDownload(ContentResolver resolver, String fileName) {
            |        Cursor cursor = null;
            |        try {
            |            String[] projection = new String[]{MediaStore.Downloads._ID};
            |            String selection = MediaStore.Downloads.DISPLAY_NAME + "=?";
            |            String[] args = new String[]{fileName};
            |            cursor = resolver.query(
            |                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            |                    projection,
            |                    selection,
            |                    args,
            |                    null
            |            );
            |            if (cursor != null && cursor.moveToFirst()) {
            |                long id = cursor.getLong(0);
            |                return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, String.valueOf(id));
            |            }
            |        } catch (Exception ignored) {
            |        } finally {
            |            if (cursor != null) {
            |                cursor.close();
            |            }
            |        }
            |        return null;
            |    }
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // AppLoggerPackage.java
    // ============================================================
    private fun buildAppLoggerPackage(packageName: String): String {
        return """
            |package $packageName;
            |
            |import com.facebook.react.bridge.NativeModule;
            |import com.facebook.react.bridge.ReactApplicationContext;
            |import com.facebook.react.bridge.ReactContextBaseJavaModule;
            |import com.facebook.react.bridge.ReactMethod;
            |import com.facebook.react.bridge.UiThreadUtil;
            |import com.facebook.react.uimanager.ViewManager;
            |import com.facebook.react.ReactPackage;
            |
            |import java.util.Collections;
            |import java.util.List;
            |
            |public final class AppLoggerPackage implements ReactPackage {
            |    @Override public List<NativeModule> createNativeModules(ReactApplicationContext context) {
            |        return Collections.<NativeModule>singletonList(new AppLoggerModule(context));
            |    }
            |
            |    @Override public List<ViewManager> createViewManagers(ReactApplicationContext context) {
            |        return Collections.<ViewManager>emptyList();
            |    }
            |
            |    private static final class AppLoggerModule extends ReactContextBaseJavaModule {
            |        AppLoggerModule(ReactApplicationContext context) { super(context); }
            |        @Override public String getName() { return "AppLogger"; }
            |
            |        @ReactMethod public void log(final String message) {
            |            UiThreadUtil.runOnUiThread(new Runnable() {
            |                @Override public void run() { CrashLogger.log(getReactApplicationContext(), message); }
            |            });
            |        }
            |    }
            |}
        """.trimMargin() + "\n"
    }

    // ============================================================
    // كتابة ملفات
    // ============================================================
    @Throws(IOException::class)
    private fun writeFile(file: File, content: String) {
        file.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }
        FileWriter(file).use { writer ->
            writer.write(content)
            writer.flush()
        }
    }
}