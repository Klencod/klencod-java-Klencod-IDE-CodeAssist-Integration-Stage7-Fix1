package com.flutteride.codweva

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistent storage for the latest build's GitHub logs.
 * Large log content is stored in app-private files, never inside an Intent/Broadcast.
 */
object BuildLogStore {
    private const val TAG = "BuildLogStore"
    private const val DIR_NAME = "build_log_store"
    private const val STEP_LOGS_FILE = "step_logs_latest.json"
    private const val STEPS_FILE = "steps_latest.json"
    private const val RUN_LOG_FILE = "run_logs_latest.txt"
    private const val RUN_ID_FILE = "run_logs_latest_id.txt"

    data class StepLogs(
        val jobId: Long,
        val allLogs: List<FlutterBuildApi.BuildLogLine>,
        val errorLogs: List<FlutterBuildApi.BuildLogLine>
    )

    private fun directory(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }

    /** Clears only the previous build's cached display data when a new build starts. */
    @JvmStatic
    fun clearCurrentBuild(context: Context) {
        val dir = directory(context)
        listOf(STEP_LOGS_FILE, STEPS_FILE, RUN_LOG_FILE, RUN_ID_FILE).forEach {
            try { File(dir, it).delete() } catch (_: Exception) { }
        }
    }

    @JvmStatic
    fun saveSteps(context: Context, steps: List<GitHubApi.Step>) {
        try {
            val array = JSONArray()
            steps.forEach { step ->
                array.put(JSONObject().apply {
                    put("number", step.number)
                    put("name", step.name)
                    put("status", step.status)
                    put("conclusion", step.conclusion)
                    put("startedAt", step.startedAtMs)
                    put("completedAt", step.completedAtMs)
                })
            }
            writeAtomically(File(directory(context), STEPS_FILE), array.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Could not save build steps: ${e.message}")
        }
    }

    @JvmStatic
    fun readSteps(context: Context): List<GitHubApi.Step> {
        return try {
            val file = File(directory(context), STEPS_FILE)
            if (!file.isFile) return emptyList()
            val array = JSONArray(file.readText(Charsets.UTF_8))
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(GitHubApi.Step(
                        obj.optInt("number", i + 1),
                        obj.optString("name", ""),
                        obj.optString("status", ""),
                        obj.optString("conclusion", ""),
                        obj.optLong("startedAt", 0L),
                        obj.optLong("completedAt", 0L)
                    ))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read build steps: ${e.message}")
            emptyList()
        }
    }

    @JvmStatic
    fun saveStepLogs(
        context: Context,
        jobId: Long,
        allLogs: List<FlutterBuildApi.BuildLogLine>,
        errorLogs: List<FlutterBuildApi.BuildLogLine>
    ) {
        try {
            fun linesToJson(lines: List<FlutterBuildApi.BuildLogLine>): JSONArray {
                val array = JSONArray()
                lines.forEach { line ->
                    array.put(JSONObject().apply {
                        put("text", line.text)
                        put("filePath", line.filePath ?: "")
                        put("line", line.line)
                        put("column", line.column)
                        put("logType", line.logType)
                    })
                }
                return array
            }
            val data = JSONObject().apply {
                put("jobId", jobId)
                put("allLogs", linesToJson(allLogs))
                put("errorLogs", linesToJson(errorLogs))
            }
            writeAtomically(File(directory(context), STEP_LOGS_FILE), data.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Could not save step logs: ${e.message}")
        }
    }

    @JvmStatic
    fun readLatestStepLogs(context: Context): StepLogs? {
        return try {
            val file = File(directory(context), STEP_LOGS_FILE)
            if (!file.isFile) return null
            val data = JSONObject(file.readText(Charsets.UTF_8))
            fun parseLines(array: JSONArray): List<FlutterBuildApi.BuildLogLine> {
                return buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        add(FlutterBuildApi.BuildLogLine(
                            obj.optString("text", ""),
                            obj.optString("filePath", "").takeIf { it.isNotEmpty() },
                            obj.optInt("line", 0),
                            obj.optInt("column", 0),
                            obj.optInt("logType", FlutterBuildApi.LOG_TYPE_NORMAL)
                        ))
                    }
                }
            }
            StepLogs(
                data.optLong("jobId", 0L),
                parseLines(data.optJSONArray("allLogs") ?: JSONArray()),
                parseLines(data.optJSONArray("errorLogs") ?: JSONArray())
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not read step logs: ${e.message}")
            null
        }
    }

    @JvmStatic
    fun saveRunLogs(context: Context, runId: Long, logs: String) {
        try {
            val dir = directory(context)
            writeAtomically(File(dir, RUN_LOG_FILE), logs)
            writeAtomically(File(dir, RUN_ID_FILE), runId.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Could not save run logs: ${e.message}")
        }
    }

    @JvmStatic
    fun readLatestRunLogs(context: Context): Pair<Long, String>? {
        return try {
            val dir = directory(context)
            val idFile = File(dir, RUN_ID_FILE)
            val logFile = File(dir, RUN_LOG_FILE)
            if (!idFile.isFile || !logFile.isFile) return null
            val runId = idFile.readText(Charsets.UTF_8).trim().toLongOrNull() ?: return null
            runId to logFile.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read run logs: ${e.message}")
            null
        }
    }

    private fun writeAtomically(destination: File, content: String) {
        val temp = File(destination.parentFile, destination.name + ".tmp")
        temp.writeText(content, Charsets.UTF_8)
        if (!temp.renameTo(destination)) {
            destination.writeText(content, Charsets.UTF_8)
            temp.delete()
        }
    }
}
