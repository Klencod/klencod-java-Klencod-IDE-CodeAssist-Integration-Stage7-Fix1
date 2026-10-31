package com.flutteride.codweva

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

/**
 * مساعد إدارة أذونات التخزين.
 *
 * يدعم حالتين:
 *   1. Android 11+ (API 30+)  → MANAGE_EXTERNAL_STORAGE (وصول كامل)
 *   2. Android 6-10 (API 23-29) → READ/WRITE_EXTERNAL_STORAGE (وصول عادي)
 *
 * الاستخدام:
 *   if (StoragePermissionHelper.hasStoragePermission(this)) {
 *       // لديك الإذن — افتح مدير الملفات
 *   } else {
 *       StoragePermissionHelper.requestStoragePermission(this, REQUEST_CODE)
 *   }
 */
object StoragePermissionHelper {

    /** كود الطلب الافتراضي — يمكنك استخدام كود مخصص. */
    const val REQUEST_CODE_STORAGE = 9001

    // ============================================================
    // فحص الإذن الحالي
    // ============================================================

    /**
     * هل التطبيق يملك إذن التخزين الكافي للعمل؟
     *
     * @return true إذا كان لدينا الإذن (كامل على Android 11+، أو عادي على الإصدارات الأقدم)
     */
    @JvmStatic
    fun hasStoragePermission(context: Context?): Boolean {
        if (context == null) return false

        // 1. Android 11+ (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }

        // 2. Android 6-10 (API 23-29)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val read = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            )
            val write = ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            return read == PackageManager.PERMISSION_GRANTED &&
                    write == PackageManager.PERMISSION_GRANTED
        }

        // 3. أقل من Android 6 → الإذن ممنوح تلقائياً
        return true
    }

    /**
     * هل نحن بحاجة لاستخدام شاشة إعدادات النظام (MANAGE_EXTERNAL_STORAGE)؟
     */
    @JvmStatic
    fun needsSystemSettings(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    // ============================================================
    // طلب الإذن
    // ============================================================

    /**
     * يطلب إذن التخزين بالطريقة المناسبة لإصدار Android.
     *
     * - Android 11+  → يفتح شاشة "الوصول لجميع الملفات" في إعدادات النظام
     * - Android 6-10 → يفتح حوار الإذن العادي
     *
     * @param activity النشاط الحالي
     * @param requestCode كود الطلب (يُستخدم في onActivityResult للإصدارات الأقدم)
     */
    @JvmStatic
    fun requestStoragePermission(activity: Activity?, requestCode: Int) {
        if (activity == null) return

        // 1. Android 11+ → افتح إعدادات النظام
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                return // الإذن ممنوح بالفعل
            }
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                )
                intent.data = Uri.parse("package:${activity.packageName}")
                activity.startActivityForResult(intent, requestCode)
            } catch (_: Exception) {
                // قد لا يدعم الجهاز الشاشة المخصصة → افتح الشاشة العامة
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    )
                    activity.startActivityForResult(intent, requestCode)
                } catch (_: Exception) {
                    // فشل كامل — لا يمكن فعل شيء
                }
            }
            return
        }

        // 2. Android 6-10 → حوار الإذن العادي
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            ActivityCompat.requestPermissions(activity, permissions, requestCode)
        }

        // 3. أقل من Android 6 → ممنوح تلقائياً
    }

    // ============================================================
    // فحوصات إضافية للمدير
    // ============================================================

    /**
     * هل نحن بحاجة لعرض رسالة توضيحية للمستخدم
     * (بعد رفضه الإذن مثلاً)؟
     */
    @JvmStatic
    fun shouldShowRationale(activity: Activity?): Boolean {
        if (activity == null) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return !Environment.isExternalStorageManager()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

        return false
    }

    // ============================================================
    // الحصول على مسارات التخزين الجذرية
    // ============================================================

    /**
     * هل بطاقة SD متوفرة للقراءة؟
     */
    @JvmStatic
    fun isSdCardAvailable(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
            externalDirs != null && externalDirs.size > 1 && externalDirs[1] != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * يُرجع مسار التخزين الخارجي الأساسي، أو null.
     */
    @JvmStatic
    fun getPrimaryExternalStorage(): File? {
        return try {
            val dir = Environment.getExternalStorageDirectory()
            if (dir != null && dir.exists() && dir.canRead()) dir else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * يُرجع مسار بطاقة SD إن وُجدت، أو null.
     */
    @JvmStatic
    fun getSdCardStorage(context: Context?): File? {
        if (context == null) return null
        return try {
            val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
            if (externalDirs == null || externalDirs.size < 2) return null
            var sd = externalDirs[1] ?: return null

            // مساحة التطبيق على SD → نرجع الجذر
            // /storage/XXXX-XXXX/Android/data/... → /storage/XXXX-XXXX
            for (i in 0 until 4) {
                sd = sd.parentFile ?: break
            }
            if (sd.exists() && sd.canRead()) sd else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * يُرجع مساحة التطبيق الخاصة (filesDir)، أو null.
     */
    @JvmStatic
    fun getAppPrivateStorage(context: Context?): File? {
        if (context == null) return null
        return try {
            val dir = context.filesDir
            if (dir != null && dir.exists()) {
                dir.parentFile ?: dir
            } else null
        } catch (_: Exception) {
            null
        }
    }
}