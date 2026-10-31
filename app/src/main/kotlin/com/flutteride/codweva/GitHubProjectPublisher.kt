package com.flutteride.codweva

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale

/**
 * GitHubProjectPublisher — ينسّق نشر مشروع وتشغيل GitHub Actions.
 *
 * v10.0 — اختيار طريقة الرفع:
 *   - GRAPHQL: سريع، حد ~10 MB (commit واحد)
 *   - BATCHES: يعمل مع أي حجم، دفعات 10 MB (عدة commits)
 *   - المستخدم يختار الطريقة
 *
 * v9.0 — Git CLI (ملغى)
 * v8.0 — رسائل الخطأ الخام من GitHub
 */
object GitHubProjectPublisher {

    interface Listener {
        fun onProgress(message: String, progress: Int)
        fun onSuccess(repository: GitHubApi.Repository, run: GitHubApi.Run)
        fun onError(message: String)
    }

    // ============================================================
    // ثوابت
    // ============================================================
    private const val WORKFLOW_POLL_INTERVAL_MS = 1000L
    private const val MAX_WORKFLOW_CHECK_ATTEMPTS = 60
    private const val TRIGGER_RETRY_INTERVAL_MS = 3000L
    private const val MAX_TRIGGER_ATTEMPTS = 10

    // ============================================================
    // النسخة القديمة — Flutter افتراضياً
    // ============================================================
    @Deprecated("Use version with projectType and uploadMethod")
    @JvmStatic
    fun publish(
        token: String,
        project: File,
        customRepoName: String?,
        isPublicRepo: Boolean,
        listener: Listener
    ) {
        publish(
            token, project, ProjectType.FLUTTER,
            customRepoName, isPublicRepo, "3.47.2",
            GitHubApi.UploadMethod.GRAPHQL, listener
        )
    }

    @Deprecated("Use version with projectType and uploadMethod")
    @JvmStatic
    fun publish(
        token: String?,
        project: File?,
        customRepoName: String?,
        isPublicRepo: Boolean,
        flutterVersion: String?,
        listener: Listener
    ) {
        publish(
            token, project, ProjectType.FLUTTER,
            customRepoName, isPublicRepo, flutterVersion,
            GitHubApi.UploadMethod.GRAPHQL, listener
        )
    }

    @Deprecated("Use version with uploadMethod")
    @JvmStatic
    fun publish(
        token: String?,
        project: File?,
        projectType: ProjectType,
        customRepoName: String?,
        isPublicRepo: Boolean,
        flutterVersion: String?,
        listener: Listener
    ) {
        publish(
            token, project, projectType,
            customRepoName, isPublicRepo, flutterVersion,
            GitHubApi.UploadMethod.GRAPHQL, listener
        )
    }

    // ============================================================
    // النسخة الرئيسية — مع اختيار طريقة الرفع
    // ============================================================
    @JvmStatic
    fun publish(
        token: String?,
        project: File?,
        projectType: ProjectType,
        customRepoName: String?,
        isPublicRepo: Boolean,
        flutterVersion: String?,
        uploadMethod: GitHubApi.UploadMethod,
        listener: Listener
    ) {
        // ============================================================
        // 1) التحقق من المدخلات
        // ============================================================
        if (token.isNullOrBlank()) {
            listener.onError("اربط حساب GitHub أولًا")
            return
        }
        if (project == null || !project.isDirectory) {
            listener.onError("مجلد المشروع غير صالح")
            return
        }

        // ============================================================
        // 2) الإعدادات
        // ============================================================
        val version = if (flutterVersion.isNullOrBlank()) "3.47.2" else flutterVersion.trim()
        val publishStartedAtMs = System.currentTimeMillis()
        val typeLabel = projectType.displayName

        val methodLabel = when (uploadMethod) {
            GitHubApi.UploadMethod.GRAPHQL -> "GraphQL (سريع)"
            GitHubApi.UploadMethod.BATCHES -> "دفعات (يعمل مع أي حجم)"
        }

        listener.onProgress(
            "التحقق من حساب GitHub لمشروع $typeLabel — $methodLabel...",
            5
        )

        // ============================================================
        // 3) التحقق من الحساب
        // ============================================================
        GitHubApi.getUser(token, object : GitHubApi.Callback<String> {
            override fun onSuccess(owner: String) {
                val rawName = if (!customRepoName.isNullOrBlank()) customRepoName else project.name
                val repoName = repositoryName(rawName, projectType)

                // ============================================================
                // 4) البحث عن المستودع أو إنشاؤه
                // ============================================================
                GitHubApi.getRepository(
                    token, owner, repoName,
                    object : GitHubApi.Callback<GitHubApi.Repository> {
                        override fun onSuccess(repository: GitHubApi.Repository) {
                            listener.onProgress(
                                "تم العثور على المستودع، جارٍ تجهيز الملفات...",
                                18
                            )
                            uploadByMethod(
                                token, project, repository, projectType,
                                publishStartedAtMs, version, uploadMethod, listener
                            )
                        }

                        override fun onError(message: String) {
                            if (message.contains("GitHub HTTP 404")) {
                                createRepository(
                                    token, repoName, project, isPublicRepo, projectType,
                                    publishStartedAtMs, version, uploadMethod, listener
                                )
                            } else {
                                listener.onError("فشل الوصول للمستودع:\n$message")
                            }
                        }
                    }
                )
            }

            override fun onError(message: String) {
                listener.onError("فشل التعرف على الحساب:\n$message")
            }
        })
    }

    // ============================================================
    // إنشاء المستودع + تفعيل Actions
    // ============================================================
    private fun createRepository(
        token: String,
        name: String,
        project: File,
        isPublicRepo: Boolean,
        projectType: ProjectType,
        publishStartedAtMs: Long,
        flutterVersion: String,
        uploadMethod: GitHubApi.UploadMethod,
        listener: Listener
    ) {
        val typeLabel = if (isPublicRepo) "عام" else "خاص"
        listener.onProgress("لم يوجد المستودع، جارٍ إنشاؤه ($typeLabel)...", 10)

        createRepositoryWithRetry(
            token, name, 0, project, isPublicRepo, projectType,
            publishStartedAtMs, flutterVersion, uploadMethod, listener
        )
    }

    private fun createRepositoryWithRetry(
        token: String,
        baseName: String,
        suffix: Int,
        project: File,
        isPublicRepo: Boolean,
        projectType: ProjectType,
        publishStartedAtMs: Long,
        flutterVersion: String,
        uploadMethod: GitHubApi.UploadMethod,
        listener: Listener
    ) {
        if (suffix > 50) {
            listener.onError("تعذر إيجاد اسم مستودع متاح (جرب 50 اسماً)")
            return
        }

        val candidate = if (suffix == 0) baseName else "${baseName}_$suffix"

        if (suffix > 0) {
            listener.onProgress("جارٍ تجربة الاسم: \"$candidate\"...", 12)
        }

        GitHubApi.createRepository(
            token, candidate, !isPublicRepo,
            object : GitHubApi.Callback<GitHubApi.Repository> {
                override fun onSuccess(repository: GitHubApi.Repository) {
                    val typeLabel = if (isPublicRepo) "عام" else "خاص"
                    if (suffix > 0) {
                        listener.onProgress(
                            "الاسم \"$baseName\" كان مستخدماً، تم استخدام \"$candidate\" ($typeLabel)",
                            20
                        )
                    } else {
                        listener.onProgress(
                            "تم إنشاء المستودع \"$candidate\" ($typeLabel) بنجاح",
                            20
                        )
                    }

                    enableActionsAndContinue(
                        token, repository, project, projectType,
                        publishStartedAtMs, flutterVersion, uploadMethod, listener
                    )
                }

                override fun onError(message: String) {
                    if (isNameConflict(message)) {
                        createRepositoryWithRetry(
                            token, baseName, suffix + 1, project, isPublicRepo, projectType,
                            publishStartedAtMs, flutterVersion, uploadMethod, listener
                        )
                    } else {
                        listener.onError("فشل إنشاء المستودع:\n$message")
                    }
                }
            }
        )
    }

    private fun enableActionsAndContinue(
        token: String,
        repository: GitHubApi.Repository,
        project: File,
        projectType: ProjectType,
        publishStartedAtMs: Long,
        flutterVersion: String,
        uploadMethod: GitHubApi.UploadMethod,
        listener: Listener
    ) {
        listener.onProgress("تفعيل GitHub Actions في المستودع...", 22)

        GitHubApi.enableActions(
            token, repository.owner, repository.name,
            object : GitHubApi.Callback<Boolean> {
                override fun onSuccess(value: Boolean) {
                    if (value) {
                        listener.onProgress("تم تفعيل Actions بنجاح", 24)
                    }
                    uploadByMethod(
                        token, project, repository, projectType,
                        publishStartedAtMs, flutterVersion, uploadMethod, listener
                    )
                }

                override fun onError(message: String) {
                    uploadByMethod(
                        token, project, repository, projectType,
                        publishStartedAtMs, flutterVersion, uploadMethod, listener
                    )
                }
            }
        )
    }

    private fun isNameConflict(message: String?): Boolean {
        if (message == null) return false
        val lower = message.lowercase(Locale.US)
        return lower.contains("http 422") ||
                lower.contains("already exists") ||
                lower.contains("name already") ||
                lower.contains("repository creation failed") ||
                lower.contains("validation failed") ||
                lower.contains("name is not available")
    }

    // ============================================================
    // ⭐ v10.0: اختيار طريقة الرفع
    // ============================================================
    private fun uploadByMethod(
        token: String,
        project: File,
        repository: GitHubApi.Repository,
        projectType: ProjectType,
        publishStartedAtMs: Long,
        flutterVersion: String,
        uploadMethod: GitHubApi.UploadMethod,
        listener: Listener
    ) {
        when (uploadMethod) {
            GitHubApi.UploadMethod.GRAPHQL -> {
                uploadViaGraphQL(
                    token, project, repository, projectType,
                    publishStartedAtMs, flutterVersion, listener
                )
            }
            GitHubApi.UploadMethod.BATCHES -> {
                uploadViaBatches(
                    token, project, repository, projectType,
                    publishStartedAtMs, flutterVersion, listener
                )
            }
        }
    }

    // ============================================================
    // رفع عبر GraphQL (سريع، حد ~10 MB)
    // ============================================================
    private fun uploadViaGraphQL(
        token: String,
        project: File,
        repository: GitHubApi.Repository,
        projectType: ProjectType,
        publishStartedAtMs: Long,
        flutterVersion: String,
        listener: Listener
    ) {
        // ============================================================
        // 1) جمع الملفات
        // ============================================================
        val files = mutableListOf<File>()
        collect(project, project, files)

        if (files.isEmpty()) {
            listener.onError("لا توجد ملفات قابلة للرفع داخل المشروع")
            return
        }

        files.sortWith(compareBy { relative(project, it) })

        val total = files.size
        listener.onProgress("تجهيز $total ملف لرفع GraphQL واحد...", 25)

        // ============================================================
        // 2) تحويل الملفات إلى FileEntry
        // ============================================================
        val fileEntries = mutableListOf<GitHubApi.FileEntry>()

        for (file in files) {
            try {
                val path = relative(project, file)
                val bytes = readBytes(file)
                val isBinary = isBinaryFile(file.name, bytes)

                if (isBinary) {
                    fileEntries.add(GitHubApi.FileEntry.fromBytes(path, bytes))
                } else {
                    fileEntries.add(
                        GitHubApi.FileEntry.fromText(
                            path,
                            String(bytes, Charsets.UTF_8)
                        )
                    )
                }
            } catch (e: Exception) {
                listener.onError("تعذر قراءة \"${file.name}\": ${e.message}")
                return
            }
        }

        // ============================================================
        // 3) رفع عبر GraphQL
        // ============================================================
        val startTime = System.currentTimeMillis()

        GitHubApi.createCommitWithFiles(
            token,
            repository.owner,
            repository.name,
            repository.defaultBranch,
            fileEntries,
            "Initial commit: $total files",
            object : GitHubApi.CommitProgressCallback {
                override fun onBlobProgress(current: Int, total: Int) {
                    val progress = 25 + (current * 55 / total)
                    listener.onProgress("__UPLOAD__$current/$total", progress)
                }

                override fun onSuccess(commitSha: String) {
                    val elapsed = System.currentTimeMillis() - startTime
                    listener.onProgress(
                        "✅ تم رفع $total ملف في ${elapsed / 1000} ثانية (GraphQL)",
                        82
                    )
                    afterUploadSuccess(
                        token, repository, projectType,
                        publishStartedAtMs, flutterVersion, listener
                    )
                }

                override fun onError(message: String) {
                    // ============================================================
                    // ⭐ كشف HTTP 499 — المشروع كبير على GraphQL
                    // ============================================================
                    if (message.contains("HTTP 499") ||
                        message.contains("Request body too large") ||
                        message.contains("HTTP 413")
                    ) {
                        listener.onError(
                            "❌ فشل الرفع عبر GraphQL\n\n" +
                            "المشروع كبير جداً على GraphQL (حد ~10 MB).\n\n" +
                            "الحل:\n" +
                            "• أعد المحاولة مع اختيار \"دفعات\"\n" +
                            "• أو احذف الملفات الكبيرة (libs/, jniLibs/, build/)"
                        )
                    } else {
                        listener.onError("فشل إنشاء الـ commit:\n$message")
                    }
                }
            }
        )
    }

    // ============================================================
    // رفع عبر Git Data API (دفعات، أي حجم)
    // ============================================================
    private fun uploadViaBatches(
        token: String,
        project: File,
        repository: GitHubApi.Repository,
        projectType: ProjectType,
        publishStartedAtMs: Long,
        flutterVersion: String,
        listener: Listener
    ) {
        // ============================================================
        // 1) جمع الملفات
        // ============================================================
        val files = mutableListOf<File>()
        collect(project, project, files)

        if (files.isEmpty()) {
            listener.onError("لا توجد ملفات قابلة للرفع داخل المشروع")
            return
        }

        files.sortWith(compareBy { relative(project, it) })

        val total = files.size
        listener.onProgress("تجهيز $total ملف للرفع على دفعات...", 25)

        // ============================================================
        // 2) تحويل الملفات إلى FileEntry
        // ============================================================
        val fileEntries = mutableListOf<GitHubApi.FileEntry>()

        for (file in files) {
            try {
                val path = relative(project, file)
                val bytes = readBytes(file)
                val isBinary = isBinaryFile(file.name, bytes)

                if (isBinary) {
                    fileEntries.add(GitHubApi.FileEntry.fromBytes(path, bytes))
                } else {
                    fileEntries.add(
                        GitHubApi.FileEntry.fromText(
                            path,
                            String(bytes, Charsets.UTF_8)
                        )
                    )
                }
            } catch (e: Exception) {
                listener.onError("تعذر قراءة \"${file.name}\": ${e.message}")
                return
            }
        }

        // ============================================================
        // 3) رفع عبر Git Data API (دفعات)
        // ============================================================
        val startTime = System.currentTimeMillis()

        GitHubApi.uploadInBatches(
            token,
            repository.owner,
            repository.name,
            repository.defaultBranch,
            fileEntries,
            "Initial commit: $total files",
            object : GitHubApi.BatchProgressCallback {

                override fun onBatchStart(
                    batchIndex: Int,
                    totalBatches: Int,
                    filesCount: Int
                ) {
                    listener.onProgress(
                        "📦 الدفعة $batchIndex/$totalBatches — $filesCount ملف...",
                        30 + (batchIndex * 40 / totalBatches)
                    )
                }

                override fun onFileProgress(
                    current: Int,
                    total: Int,
                    fileName: String
                ) {
                    // لا نرسل كل ملف — يكفي تقدم الدفعة
                }

                override fun onBatchComplete(
                    batchIndex: Int,
                    totalBatches: Int,
                    commitSha: String
                ) {
                    val progress = 30 + ((batchIndex + 1) * 40 / totalBatches)
                    listener.onProgress(
                        "✅ الدفعة $batchIndex/$totalBatches اكتملت",
                        progress
                    )
                }

                override fun onSuccess(finalCommitSha: String, totalBatches: Int) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val commitLabel = if (totalBatches == 1) {
                        "commit واحد"
                    } else {
                        "$totalBatches commits"
                    }
                    listener.onProgress(
                        "✅ تم رفع $total ملف في ${elapsed / 1000} ثانية ($commitLabel)",
                        82
                    )
                    afterUploadSuccess(
                        token, repository, projectType,
                        publishStartedAtMs, flutterVersion, listener
                    )
                }

                override fun onError(message: String) {
                    listener.onError("فشل الرفع على دفعات:\n$message")
                }
            }
        )
    }

    // ============================================================
    // بعد نجاح الرفع
    // ============================================================
    private fun afterUploadSuccess(
        token: String,
        repository: GitHubApi.Repository,
        projectType: ProjectType,
        publishStartedAtMs: Long,
        flutterVersion: String,
        listener: Listener
    ) {
        Thread {
            try {
                val workflowFile = GitHubConfig.workflowFileFor(projectType)

                listener.onProgress(
                    "🔍 جارٍ فحص GitHub Actions للتحقق من جاهزية الـ workflow...",
                    83
                )

                waitForWorkflowRegistered(
                    token, repository, workflowFile,
                    0, publishStartedAtMs, listener
                ) { workflowReady ->
                    if (!workflowReady) {
                        listener.onError(
                            "الـ workflow \"$workflowFile\" لم يُسجّل في GitHub Actions.\n\n" +
                            "تحقق من أن المشروع يحتوي على .github/workflows/$workflowFile"
                        )
                        return@waitForWorkflowRegistered
                    }

                    triggerBuildWithRetry(
                        token, repository, projectType,
                        workflowFile, flutterVersion,
                        publishStartedAtMs, listener, 0
                    )
                }

            } catch (e: Exception) {
                listener.onError("خطأ أثناء فحص الـ workflow: ${e.message}")
            }
        }.start()
    }

    // ============================================================
    // فحص فوري متوازي للـ workflow
    // ============================================================
    private fun waitForWorkflowRegistered(
        token: String,
        repository: GitHubApi.Repository,
        workflowFile: String,
        attempt: Int,
        publishStartedAtMs: Long,
        listener: Listener,
        onReady: (Boolean) -> Unit
    ) {
        if (attempt >= MAX_WORKFLOW_CHECK_ATTEMPTS) {
            val elapsedSec = (System.currentTimeMillis() - publishStartedAtMs) / 1000
            listener.onProgress(
                "⚠️ لم يظهر الـ workflow بعد ${elapsedSec}ث — محاولة البناء على أي حال...",
                84
            )
            onReady(true)
            return
        }

        if (attempt == 0) {
            listener.onProgress(
                "🔍 جارٍ فحص الـ workflow على GitHub (محاولة 1/$MAX_WORKFLOW_CHECK_ATTEMPTS)...",
                83
            )
        } else {
            val elapsedSec = (System.currentTimeMillis() - publishStartedAtMs) / 1000
            listener.onProgress(
                "⏳ في انتظار GitHub Actions (محاولة ${attempt + 1}/$MAX_WORKFLOW_CHECK_ATTEMPTS — ${elapsedSec}ث)...",
                83
            )
        }

        GitHubApi.checkWorkflowExists(
            token, repository.owner, repository.name, workflowFile,
            object : GitHubApi.Callback<Boolean> {
                override fun onSuccess(value: Boolean) {
                    if (value) {
                        val elapsedSec = (System.currentTimeMillis() - publishStartedAtMs) / 1000
                        listener.onProgress(
                            "✅ الـ workflow جاهز بعد ${elapsedSec}ث — جارٍ تشغيل البناء...",
                            84
                        )
                        onReady(true)
                    } else {
                        scheduleNextCheck(
                            token, repository, workflowFile,
                            attempt + 1, publishStartedAtMs, listener, onReady
                        )
                    }
                }

                override fun onError(message: String) {
                    scheduleNextCheck(
                        token, repository, workflowFile,
                        attempt + 1, publishStartedAtMs, listener, onReady
                    )
                }
            }
        )
    }

    private fun scheduleNextCheck(
        token: String,
        repository: GitHubApi.Repository,
        workflowFile: String,
        nextAttempt: Int,
        publishStartedAtMs: Long,
        listener: Listener,
        onReady: (Boolean) -> Unit
    ) {
        Thread {
            try {
                Thread.sleep(WORKFLOW_POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
            }
            waitForWorkflowRegistered(
                token, repository, workflowFile,
                nextAttempt, publishStartedAtMs, listener, onReady
            )
        }.start()
    }

    // ============================================================
    // triggerBuild مع إعادة محاولة
    // ============================================================
    private fun triggerBuildWithRetry(
        token: String,
        repository: GitHubApi.Repository,
        projectType: ProjectType,
        workflowFile: String,
        flutterVersion: String,
        publishStartedAtMs: Long,
        listener: Listener,
        attempt: Int
    ) {
        if (attempt >= MAX_TRIGGER_ATTEMPTS) {
            listener.onError(
                "فشل تشغيل GitHub Actions بعد $MAX_TRIGGER_ATTEMPTS محاولات.\n\n" +
                "المستودع أُنشئ ورفع بنجاح، لكن البناء لم يبدأ."
            )
            return
        }

        if (attempt > 0) {
            listener.onProgress(
                "إعادة محاولة trigger (${attempt + 1}/$MAX_TRIGGER_ATTEMPTS)...",
                84
            )
        }

        GitHubApi.triggerBuild(
            token,
            repository.owner,
            repository.name,
            repository.defaultBranch,
            workflowFile,
            flutterVersion,
            object : GitHubApi.Callback<Boolean> {
                override fun onSuccess(value: Boolean) {
                    listener.onProgress(
                        "✅ تم تشغيل GitHub Actions، جارٍ انتظار البناء...",
                        85
                    )
                    findStartedRun(
                        token, repository, projectType,
                        publishStartedAtMs, listener, 0
                    )
                }

                override fun onError(message: String) {
                    val is404 = message.contains("HTTP 404") ||
                            message.contains("Not Found")
                    val is422 = message.contains("HTTP 422")

                    if (is404 || is422) {
                        listener.onProgress(
                            "GitHub لم يستجب بعد، إعادة المحاولة...",
                            84
                        )

                        Thread {
                            try {
                                Thread.sleep(TRIGGER_RETRY_INTERVAL_MS)
                            } catch (_: InterruptedException) {
                            }
                            triggerBuildWithRetry(
                                token, repository, projectType,
                                workflowFile, flutterVersion,
                                publishStartedAtMs, listener, attempt + 1
                            )
                        }.start()
                    } else {
                        listener.onError("فشل تشغيل GitHub Actions:\n$message")
                    }
                }
            }
        )
    }

    // ============================================================
    // انتظار بدء الـ Run
    // ============================================================
    private fun findStartedRun(
        token: String,
        repository: GitHubApi.Repository,
        projectType: ProjectType,
        publishStartedAtMs: Long,
        listener: Listener,
        attempt: Int
    ) {
        val workflowFile = GitHubConfig.workflowFileFor(projectType)

        GitHubApi.latestRun(
            token, repository.owner, repository.name, publishStartedAtMs,
            workflowFile,
            object : GitHubApi.Callback<GitHubApi.Run> {
                override fun onSuccess(run: GitHubApi.Run) {
                    listener.onSuccess(repository, run)
                }

                override fun onError(message: String) {
                    if (attempt >= 10) {
                        listener.onError(
                            "تم تشغيل البناء، لكن تعذر الحصول على رقم العملية:\n$message"
                        )
                        return
                    }
                    Thread {
                        try {
                            Thread.sleep(2500L)
                        } catch (_: InterruptedException) {
                        }
                        findStartedRun(
                            token, repository, projectType,
                            publishStartedAtMs, listener, attempt + 1
                        )
                    }.start()
                }
            }
        )
    }

    // ============================================================
    // أدوات تجميع الملفات
    // ============================================================
    private fun collect(root: File, directory: File, result: MutableList<File>) {
        val children = directory.listFiles() ?: return
        for (child in children) {
            val path = relative(root, child)
            if (isExcluded(path)) continue
            if (child.isDirectory) {
                collect(root, child, result)
            } else if (child.isFile && child.length() <= 100L * 1024L * 1024L) {
                result.add(child)
            }
        }
    }

    private fun isExcluded(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        val lower = normalized.lowercase(Locale.US)
        return lower == "local.properties" ||
                lower.startsWith(".git/") ||
                lower.startsWith(".klencod_backup/") ||
                lower.startsWith(".klencod/") ||
                lower.startsWith("build/") ||
                lower.contains("/build/") ||
                lower.contains(".gradle/") ||
                lower.contains("keystore") ||
                lower.endsWith(".jks") ||
                lower.endsWith(".keystore") ||
                lower.endsWith(".pem") ||
                lower.endsWith(".apk") ||
                lower.endsWith(".aab")
    }

    @Throws(IOException::class)
    private fun readBytes(file: File): ByteArray {
        FileInputStream(file).use { input ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } != -1) {
                    output.write(buffer, 0, count)
                }
                return output.toByteArray()
            }
        }
    }

    private fun isBinaryFile(fileName: String, bytes: ByteArray): Boolean {
        val lower = fileName.lowercase(Locale.US)
        val binaryExtensions = listOf(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".ico",
            ".ttf", ".otf", ".woff", ".woff2",
            ".mp3", ".mp4", ".wav", ".ogg",
            ".so", ".dylib", ".dll", ".a",
            ".jar", ".class", ".dex",
            ".zip", ".rar", ".7z", ".gz", ".tar"
        )
        for (ext in binaryExtensions) {
            if (lower.endsWith(ext)) return true
        }

        val sampleSize = minOf(512, bytes.size)
        if (sampleSize == 0) return false

        var nonTextCount = 0
        for (i in 0 until sampleSize) {
            val b = bytes[i].toInt() and 0xFF
            val isText = b == 0x09 || b == 0x0A || b == 0x0D ||
                    (b in 0x20..0x7E) ||
                    b >= 0x80
            if (!isText) nonTextCount++
        }

        return nonTextCount * 100 / sampleSize > 30
    }

    private fun relative(root: File, file: File): String {
        return try {
            val base = root.canonicalPath
            val value = file.canonicalPath
            if (value.startsWith("$base${File.separator}")) {
                value.substring(base.length + 1).replace(File.separatorChar, '/')
            } else {
                file.name
            }
        } catch (_: IOException) {
            file.name
        }
    }

    private fun repositoryName(raw: String?, projectType: ProjectType): String {
        var name = raw?.trim() ?: "app"
        if (name.isEmpty()) name = "app"

        val sb = StringBuilder()
        for (c in name) {
            val isArabic = (c in '\u0600'..'\u06FF') ||
                    (c in '\u0750'..'\u077F') ||
                    (c in '\uFB50'..'\uFDFF') ||
                    (c in '\uFE70'..'\uFEFF')
            val isLatinDigit = (c in 'A'..'Z') || (c in 'a'..'z') || (c in '0'..'9')
            val isSafe = c == '-' || c == '_' || c == '.'

            when {
                isArabic || isLatinDigit || isSafe -> sb.append(c)
                c == ' ' -> sb.append('-')
            }
        }

        name = sb.toString()
        while (name.startsWith("-") || name.startsWith(".")) name = name.substring(1)
        while (name.endsWith("-") || name.endsWith(".")) {
            name = name.substring(0, name.length - 1)
        }
        if (name.isEmpty()) name = "app"

        val prefix = GitHubConfig.repoPrefixFor(projectType)

        val finalName = if (name.startsWith(prefix)) {
            name
        } else {
            "$prefix$name"
        }

        return if (finalName.length > 90) finalName.substring(0, 90) else finalName
    }
}