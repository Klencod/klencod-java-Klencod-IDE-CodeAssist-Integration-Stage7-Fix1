package com.flutteride.codweva

/**
 * يحتفظ بنسخ ما قبل التعديل لتمكين التراجع الفوري عن آخر عملية للوكيل.
 *
 * v2.0 — إصلاحات أمنية:
 *   - نسخ احتياطي في الذاكرة قبل أي عملية حذف
 *   - استرجاع تلقائي عند فشل النقل
 *   - معالجة شاملة للأخطاء بدون فقدان بيانات
 */
object AgentChangeStore {

    /**
     * سجل تغيير واحد:
     *   - WRITE: نحفظ النسخة القديمة (أو عدم الوجود).
     *   - DELETE: نحفظ المحتوى (لإعادة إنشائه).
     *   - MOVE: نحفظ المسار الأصلي والوجهة.
     */
    class PreviousFile(
        val path: String,
        val existed: Boolean,
        val content: String?,
        /** للـ move: الوجهة (المسار الجديد). null للأنواع الأخرى. */
        val moveDestination: String?,
        /** هل هذا السجل لعملية نقل؟ */
        val isMove: Boolean
    ) {

        constructor(path: String, existed: Boolean, content: String?) : this(
            path, existed, content, null, false
        )

        val safeContent: String get() = content ?: ""
        val safeMoveDestination: String get() = moveDestination ?: ""
    }

    // ============================================================
    // الحفظ
    // ============================================================
    @Throws(Exception::class)
    fun capture(
        files: ProjectFileService,
        actions: List<AgentEngine.AgentAction>?
    ): List<PreviousFile> {
        val result = mutableListOf<PreviousFile>()
        if (actions == null) return result

        for (action in actions) {
            if (action.isMove) {
                // نقل: نحفظ المصدر والوجهة
                if (action.path.isBlank()) continue
                if (action.destination.isBlank()) continue

                val fromExists = files.exists(action.path)
                val fromContent = if (fromExists) files.readFile(action.path) else ""

                result.add(
                    PreviousFile(
                        action.path,
                        fromExists,
                        fromContent,
                        action.destination,
                        true
                    )
                )
                continue
            }

            // write / delete
            if (action.path.isBlank()) continue
            val exists = files.exists(action.path)
            val old = if (exists) files.readFile(action.path) else ""
            result.add(PreviousFile(action.path, exists, old))
        }
        return result
    }

    // ============================================================
    // الاستعادة (بالعكس) — مع حماية من فقدان البيانات
    // ============================================================
    @Throws(Exception::class)
    fun restore(files: ProjectFileService, previous: List<PreviousFile>?) {
        if (previous.isNullOrEmpty()) return

        val errors = mutableListOf<String>()
        var restored = 0
        var failed = 0

        // نستعيد بترتيب معكوس — آخر تغيير أولاً
        for (i in previous.indices.reversed()) {
            val file = previous[i] ?: continue

            try {
                val ok = restoreOne(files, file)
                if (ok) restored++ else failed++
            } catch (e: Exception) {
                failed++
                errors.add("فشل استرجاع ${file.path}: ${safeMsg(e)}")
                // نستمر بالبقية — لا نتوقف
            }
        }

        // إذا فشلت كل العمليات، نُبلّغ عن خطأ
        if (failed > 0 && restored == 0) {
            val sb = StringBuilder("تعذر استرجاع أي تعديل.")
            if (errors.isNotEmpty()) {
                sb.append("\n")
                for (err in errors) sb.append("• ").append(err).append("\n")
            }
            throw Exception(sb.toString())
        }

        // إذا نجح بعضها وفشل بعضها، لا نرفع استثناء
        // (لأن الاسترجاع جزئي أفضل من لا شيء)
    }

    // ============================================================
    // استرجاع عملية واحدة بأمان
    // ============================================================
    private fun restoreOne(files: ProjectFileService, file: PreviousFile): Boolean {
        if (file.isMove) {
            return restoreMove(files, file)
        }

        // write / delete
        return try {
            if (file.existed) {
                // الملف كان موجوداً → أعد كتابته بمحتواه
                files.writeFile(file.path, file.safeContent)
            } else {
                // الملف لم يكن موجوداً → احذفه
                if (files.exists(file.path)) {
                    files.deleteFile(file.path)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * استرجاع عملية نقل بأمان.
     *
     * المنطق:
     *   1) تحقق أن الوجهة موجودة
     *   2) إذا كان المصدر موجوداً → احفظ نسخته في الذاكرة أولاً
     *   3) احذف المصدر (إن وُجد)
     *   4) انقل الوجهة → المصدر
     *   5) إذا فشل النقل: أعد المصدر من النسخة الاحتياطية
     */
    private fun restoreMove(files: ProjectFileService, file: PreviousFile): Boolean {
        if (file.safeMoveDestination.isBlank()) return false

        // 1) هل الوجهة موجودة؟
        val destinationExists = try {
            files.exists(file.safeMoveDestination)
        } catch (_: Exception) {
            return false
        }
        if (!destinationExists) {
            // الوجهة غير موجودة — ربما نُقل الملف مرة أخرى
            // أو العملية لم تكتمل. لا نفعل شيئاً.
            return true
        }

        // 2) هل المصدر موجود؟
        var sourceExists = false
        var sourceBackup: String? = null
        try {
            sourceExists = files.exists(file.path)
            if (sourceExists) {
                // نسخ احتياطي في الذاكرة
                sourceBackup = files.readFile(file.path)
            }
        } catch (_: Exception) {
            sourceExists = false
        }

        // 3) إذا كان المصدر موجوداً، احذفه (استعداداً للنقل)
        if (sourceExists) {
            try {
                files.deleteFile(file.path)
            } catch (_: Exception) {
                // فشل الحذف → لا يمكننا المتابعة بأمان
                return false
            }
        }

        // 4) انقل الوجهة → المصدر
        return try {
            files.moveFile(file.safeMoveDestination, file.path)
            true
        } catch (_: Exception) {
            // فشل النقل — إليك حماية البيانات!
            if (sourceExists && sourceBackup != null) {
                try {
                    files.writeFile(file.path, sourceBackup)
                } catch (_: Exception) {
                    // لا يمكننا فعل شيء أكثر — الوجهة لا تزال موجودة
                }
            }
            false
        }
    }

    private fun safeMsg(e: Exception?): String {
        if (e == null) return "خطأ غير معروف"
        val msg = e.message
        return if (msg.isNullOrBlank()) e.javaClass.simpleName else msg
    }
}