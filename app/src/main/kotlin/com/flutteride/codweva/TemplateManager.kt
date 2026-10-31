package com.flutteride.codweva

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * مدير القوالب — v6.0
 *
 * التحسينات:
 *   - cache على القرص لبيانات القوالب الوصفية (metadata)
 *   - cache لحجم الـ ZIP
 *   - لا نحسب checksum عند التحميل
 *   - يعمل مع TemplateInstaller v5.0
 */
object TemplateManager {

    private const val TAG = "TemplateManager"
    private const val ASSETS_TEMPLATES_DIR = "templates"
    private const val ZIP_EXTENSION = ".zip"
    private const val METADATA_CACHE_FILE = "templates_metadata.json"
    private const val CACHE_VERSION = 1

    // ============================================================
    // القالب الافتراضي (To-Do)
    // ============================================================
    const val TEMPLATE_TODO = "todo"
    const val DEFAULT_TEMPLATE = TEMPLATE_TODO

    /**
     * يكتب قالباً (غير ZIP) في مجلد المشروع.
     */
    @JvmStatic
    fun writeTemplate(
        context: Context?,
        templateId: String?,
        projectDir: File?,
        packageName: String?,
        appName: String?,
        apiLevel: String?
    ): Boolean {
        val id = templateId ?: DEFAULT_TEMPLATE
        if (projectDir == null || !projectDir.isDirectory) return false
        if (packageName == null || appName == null || apiLevel == null) return false

        return when (id) {
            TEMPLATE_TODO -> TodoTemplate.write(
                context, projectDir, packageName, appName, apiLevel
            )
            else -> TodoTemplate.write(
                context, projectDir, packageName, appName, apiLevel
            )
        }
    }

    @JvmStatic
    fun getDefaultTemplateDisplayName(templateId: String?): String {
        return when (templateId) {
            TEMPLATE_TODO -> "مدير المهام"
            else -> "مدير المهام"
        }
    }

    @JvmStatic
    fun getDefaultTemplateDescription(templateId: String?): String {
        return when (templateId) {
            TEMPLATE_TODO -> "تطبيق لإدارة المهام اليومية مع حفظ محلي."
            else -> "تطبيق لإدارة المهام اليومية مع حفظ محلي."
        }
    }

    // ============================================================
    // تحميل القوالب (مع cache قوي)
    // ============================================================
    @JvmStatic
    fun loadTemplates(context: Context?): List<TemplateInfo> {
        if (context == null) return emptyList()

        // 1. اقرأ قائمة ملفات ZIP من assets
        val zipFiles = listTemplateZips(context)
        if (zipFiles.isEmpty()) return emptyList()

        // 2. اقرأ cache البيانات الوصفية
        val cachedMetadata = readMetadataCache(context)

        // 3. هل الـ cache صالح؟
        if (isCacheValid(cachedMetadata, zipFiles)) {
            val cached = buildTemplatesFromCache(context, cachedMetadata!!, zipFiles)
            if (cached != null) return cached
        }

        // 4. cache غير صالح → ابنِ من الصفر
        val templates = mutableListOf<TemplateInfo>()
        val newMetadata = JSONObject()

        try {
            newMetadata.put("version", CACHE_VERSION)
            val filesArray = JSONArray()

            for (fileName in zipFiles) {
                val id = fileName.substring(0, fileName.length - ZIP_EXTENSION.length)
                if (id.isEmpty()) continue

                val assetPath = "$ASSETS_TEMPLATES_DIR/$fileName"
                val info = TemplateInfo(id, null, assetPath)

                // قراءة حجم الـ ZIP + metadata
                val result = readZipOnce(context, assetPath)
                info.sizeBytes = result.sizeBytes

                result.metaJson?.let { parseTemplateJson(it, info) }

                templates.add(info)

                val fileEntry = JSONObject().apply {
                    put("fileName", fileName)
                    put("id", id)
                    put("sizeBytes", info.sizeBytes)
                    put("displayName", info.displayName)
                    put("englishName", info.englishName)
                }
                filesArray.put(fileEntry)
            }

            newMetadata.put("files", filesArray)
            writeMetadataCache(context, newMetadata)
        } catch (e: Exception) {
            Log.w(TAG, "فشل بناء البيانات الوصفية: ${e.message}")
        }

        templates.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.id })
        return templates
    }

    @JvmStatic
    fun findById(context: Context?, id: String?): TemplateInfo? {
        if (id == null) return null
        return loadTemplates(context).firstOrNull { it.id == id }
    }

    @JvmStatic
    fun hasTemplates(context: Context?): Boolean {
        if (context == null) return false
        return listTemplateZips(context).isNotEmpty()
    }

    // ============================================================
    // قائمة ملفات ZIP
    // ============================================================
    private fun listTemplateZips(context: Context): List<String> {
        val result = mutableListOf<String>()
        try {
            val assets: AssetManager = context.assets
            val files = assets.list(ASSETS_TEMPLATES_DIR) ?: return result

            for (fileName in files) {
                if (!fileName.lowercase(Locale.US).endsWith(ZIP_EXTENSION)) continue
                if (fileName.length <= ZIP_EXTENSION.length) continue
                result.add(fileName)
            }
            result.sortWith(String.CASE_INSENSITIVE_ORDER)
        } catch (e: Exception) {
            Log.w(TAG, "فشل قراءة مجلد القوالب: ${e.message}")
        }
        return result
    }

    // ============================================================
    // قراءة ZIP مرة واحدة
    // ============================================================
    private class ZipReadResult {
        var sizeBytes: Long = 0L
        var metaJson: String? = null
    }

    private fun readZipOnce(context: Context, assetPath: String): ZipReadResult {
        val result = ZipReadResult()
        try {
            context.assets.open(assetPath).use { input ->
                ZipInputStream(input).use { zip ->
                    var entry: ZipEntry?
                    val buffer = ByteArray(8192)

                    while (zip.nextEntry.also { entry = it } != null) {
                        var name = entry!!.name?.replace('\\', '/') ?: ""
                        while (name.startsWith("./")) name = name.substring(2)

                        if (name == "template.json" || name.endsWith("/template.json")) {
                            val bos = java.io.ByteArrayOutputStream()
                            var count: Int
                            while (zip.read(buffer).also { count = it } != -1) {
                                bos.write(buffer, 0, count)
                                result.sizeBytes += count
                                if (bos.size() > 20 * 1024) break
                            }
                            result.metaJson = String(bos.toByteArray(), Charsets.UTF_8)
                        } else {
                            var count: Int
                            while (zip.read(buffer).also { count = it } != -1) {
                                result.sizeBytes += count
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }

            // +1% تقديري لحجم الهيدر
            result.sizeBytes += (result.sizeBytes / 100)
        } catch (e: Exception) {
            Log.w(TAG, "فشل قراءة ZIP: ${e.message}")
        }

        if (result.sizeBytes == 0L) {
            result.sizeBytes = getAssetSizeFallback(context, assetPath)
        }
        return result
    }

    private fun getAssetSizeFallback(context: Context, assetPath: String): Long {
        return try {
            context.assets.open(assetPath).use { input ->
                var total = 0L
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } != -1) {
                    total += count
                    if (total > 50L * 1024L * 1024L) break
                }
                total
            }
        } catch (_: Exception) {
            0L
        }
    }

    // ============================================================
    // cache البيانات الوصفية
    // ============================================================
    private fun metadataCacheFile(context: Context): File =
        File(context.cacheDir, METADATA_CACHE_FILE)

    private fun readMetadataCache(context: Context): JSONObject? {
        val file = metadataCacheFile(context)
        if (!file.isFile || file.length() == 0L) return null

        return try {
            FileReader(file).use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(4096)
                var n: Int
                while (reader.read(buf).also { n = it } != -1) {
                    sb.append(buf, 0, n)
                }
                JSONObject(sb.toString())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeMetadataCache(context: Context, metadata: JSONObject) {
        val file = metadataCacheFile(context)
        try {
            file.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }

            val temp = File(file.absolutePath + ".tmp")
            FileWriter(temp).use { writer ->
                writer.write(metadata.toString())
                writer.flush()
            }

            if (file.exists()) file.delete()
            if (!temp.renameTo(file)) {
                copyFileSimple(temp, file)
                temp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "فشل حفظ cache: ${e.message}")
        }
    }

    private fun isCacheValid(cache: JSONObject?, currentZipFiles: List<String>): Boolean {
        if (cache == null) return false

        return try {
            val version = cache.optInt("version", 0)
            if (version != CACHE_VERSION) return false

            val filesArray = cache.optJSONArray("files") ?: return false
            if (filesArray.length() != currentZipFiles.size) return false

            for (i in 0 until filesArray.length()) {
                val fileEntry = filesArray.optJSONObject(i) ?: return false
                val cachedName = fileEntry.optString("fileName", "")
                if (!currentZipFiles.contains(cachedName)) return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildTemplatesFromCache(
        context: Context,
        cache: JSONObject,
        currentZipFiles: List<String>
    ): List<TemplateInfo>? {
        return try {
            val filesArray = cache.optJSONArray("files") ?: return null
            val result = mutableListOf<TemplateInfo>()

            for (i in 0 until filesArray.length()) {
                val fileEntry = filesArray.optJSONObject(i) ?: continue
                val fileName = fileEntry.optString("fileName", "")
                val id = fileEntry.optString("id", "")
                val sizeBytes = fileEntry.optLong("sizeBytes", 0L)
                val displayName = fileEntry.optString("displayName", "")
                val englishName = fileEntry.optString("englishName", "")

                if (id.isEmpty()) continue
                if (!currentZipFiles.contains(fileName)) continue

                val assetPath = "$ASSETS_TEMPLATES_DIR/$fileName"
                val info = TemplateInfo(id, null, assetPath)
                info.sizeBytes = sizeBytes
                if (displayName.isNotEmpty()) info.displayName = displayName
                if (englishName.isNotEmpty()) info.englishName = englishName

                result.add(info)
            }

            result.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.id })
            result
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun clearMetadataCache(context: Context?) {
        if (context == null) return
        val file = metadataCacheFile(context)
        if (file.exists()) file.delete()
    }

    // ============================================================
    // cache أيقونة القالب
    // ============================================================
    private fun templateCacheKey(context: Context, template: TemplateInfo): String {
        val base = "${template.id}_${template.sizeBytes}"
        return sha256(base)
    }

    @JvmStatic
    fun prepareShowcase(context: Context?, template: TemplateInfo?): File? {
        if (context == null || template == null) return null

        // 1. جرّب الأيقونة المخزنة
        val cachedIcon = getCachedTemplateIconFile(context, template)
        if (cachedIcon != null && cachedIcon.exists() && cachedIcon.length() > 0) {
            template.iconFile = cachedIcon
            loadCachedPreviews(context, template)
            return cachedIcon.parentFile
        }

        // 2. استخرج من ZIP
        val showcaseDir = try {
            TemplateInstaller.extractShowcase(context, template)
        } catch (e: Exception) {
            Log.w(TAG, "فشل استخراج العرض: ${e.message}")
            return null
        }

        // 3. احفظ الأيقونة
        template.iconFile?.takeIf { it.exists() }?.let {
            saveIconToPersistentCache(context, template, it)
        }

        // 4. احفظ المعاينات
        if (template.previewFiles.isNotEmpty()) {
            savePreviewsToPersistentCache(context, template)
        }

        return showcaseDir
    }

    // ============================================================
    // أدوات التخزين المؤقت
    // ============================================================
    private fun getPersistentIconDir(context: Context): File =
        File(context.cacheDir, "template_icons").apply {
            if (!exists()) mkdirs()
        }

    private fun getPersistentPreviewsDir(context: Context, template: TemplateInfo): File {
        val key = templateCacheKey(context, template)
        return File(context.cacheDir, "template_previews/$key").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun getCachedTemplateIconFile(context: Context, template: TemplateInfo): File? {
        val dir = getPersistentIconDir(context)
        val key = templateCacheKey(context, template)
        val f = File(dir, "$key.png")
        return if (f.isFile) f else null
    }

    private fun saveIconToPersistentCache(
        context: Context,
        template: TemplateInfo,
        sourceIcon: File
    ) {
        try {
            val destDir = getPersistentIconDir(context)
            val key = templateCacheKey(context, template)
            val dest = File(destDir, "$key.png")

            if (dest.exists() && dest.length() > 100) return

            val tempFile = File(dest.absolutePath + ".tmp")
            val bmp = BitmapFactory.decodeFile(sourceIcon.absolutePath)
            if (bmp == null || bmp.isRecycled) return

            FileOutputStream(tempFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
            if (!bmp.isRecycled) bmp.recycle()

            if (tempFile.length() < 100) {
                tempFile.delete()
                return
            }
            if (dest.exists()) dest.delete()
            if (!tempFile.renameTo(dest)) {
                copyFileSimple(tempFile, dest)
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "فشل حفظ أيقونة القالب: ${e.message}")
        }
    }

    private fun loadCachedPreviews(context: Context, template: TemplateInfo) {
        template.previewFiles.clear()
        val dir = getPersistentPreviewsDir(context, template)
        if (!dir.isDirectory) return

        val files = dir.listFiles() ?: return
        val previews = mutableListOf<File>()

        for (f in files) {
            val name = f.name.lowercase(Locale.US)
            if (name.startsWith("preview_") &&
                (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) &&
                f.length() > 100
            ) {
                previews.add(f)
            }
        }

        previews.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        template.previewFiles.addAll(previews)
    }

    private fun savePreviewsToPersistentCache(context: Context, template: TemplateInfo) {
        try {
            val destDir = getPersistentPreviewsDir(context, template)
            for (i in template.previewFiles.indices) {
                val src = template.previewFiles[i]
                if (!src.isFile) continue

                val dest = File(destDir, "preview_${i + 1}.png")
                if (dest.exists() && dest.length() > 100) continue

                FileInputStream(src).use { input ->
                    FileOutputStream(dest).use { output ->
                        val buf = ByteArray(8192)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            output.write(buf, 0, n)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "فشل حفظ معاينات القالب: ${e.message}")
        }
    }

    // ============================================================
    // تنظيف cache القالب
    // ============================================================
    @JvmStatic
    fun clearTemplateCache(context: Context?, template: TemplateInfo?) {
        if (context == null || template == null) return
        try {
            val iconDir = getPersistentIconDir(context)
            val iconKey = templateCacheKey(context, template)
            val iconFile = File(iconDir, "$iconKey.png")
            if (iconFile.exists()) iconFile.delete()

            val previewDir = getPersistentPreviewsDir(context, template)
            deleteRecursive(previewDir)

            val showcaseDir = File(context.cacheDir, "template_showcase/${template.id}")
            deleteRecursive(showcaseDir)
        } catch (e: Exception) {
            Log.w(TAG, "فشل حذف cache القالب: ${e.message}")
        }
    }

    @JvmStatic
    fun clearAllTemplatesCache(context: Context?) {
        if (context == null) return
        try {
            deleteRecursive(File(context.cacheDir, "template_icons"))
            deleteRecursive(File(context.cacheDir, "template_previews"))
            deleteRecursive(File(context.cacheDir, "template_showcase"))
        } catch (e: Exception) {
            Log.w(TAG, "فشل حذف cache القوالب: ${e.message}")
        }
    }

    private fun deleteRecursive(file: File?) {
        if (file == null || !file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        file.delete()
    }

    private fun copyFileSimple(source: File, target: File) {
        try {
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
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // تثبيت قالب
    // ============================================================
    @JvmStatic
    fun install(
        context: Context?,
        template: TemplateInfo?,
        targetDir: File?,
        appName: String?,
        packageName: String?,
        apiLevel: String?,
        isPublicRepo: Boolean
    ): Boolean {
        if (context == null || template == null || targetDir == null) return false
        if (appName == null || packageName == null || apiLevel == null) return false

        return TemplateInstaller.install(
            context, template, targetDir, appName, packageName, apiLevel
        )
    }

    // ============================================================
    // أدوات داخلية
    // ============================================================
    private fun parseTemplateJson(json: String?, info: TemplateInfo) {
        if (json.isNullOrEmpty()) return

        val name = extractJsonString(json, "name")
        val nameEn = extractJsonString(json, "name_en")

        if (!name.isNullOrEmpty()) info.displayName = name
        if (!nameEn.isNullOrEmpty()) info.englishName = nameEn
    }

    private fun extractJsonString(json: String?, key: String?): String? {
        if (json == null || key == null) return null

        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return null

        val colon = json.indexOf(':', keyIdx + key.length + 2)
        if (colon < 0) return null

        val start = json.indexOf('"', colon + 1)
        if (start < 0) return null

        var end = start + 1
        val sb = StringBuilder()
        while (end < json.length) {
            val c = json[end]
            if (c == '\\' && end + 1 < json.length) {
                val next = json[end + 1]
                if (next == '"' || next == '\\' || next == 'n') {
                    if (next == 'n') sb.append('\n') else sb.append(next)
                    end += 2
                    continue
                }
            }
            if (c == '"') break
            sb.append(c)
            end++
        }
        return sb.toString()
    }

    private fun sha256(value: String?): String {
        if (value == null) return "null"
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder()
            for (b in bytes) sb.append(String.format(Locale.US, "%02x", b))
            sb.toString()
        } catch (_: Exception) {
            Integer.toHexString(value.hashCode())
        }
    }

    // ============================================================
    // معلومات مساعدة للواجهة
    // ============================================================
    @JvmStatic
    fun getDisplayName(template: TemplateInfo?): String {
        if (template == null) return ""
        return template.displayName.ifEmpty { template.id }
    }

    @JvmStatic
    fun getSecondaryName(template: TemplateInfo?): String {
        if (template == null) return ""
        if (template.englishName.isNotEmpty()) return template.englishName
        return template.id
    }
}