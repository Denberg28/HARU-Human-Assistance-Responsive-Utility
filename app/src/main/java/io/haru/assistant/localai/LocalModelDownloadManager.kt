package io.haru.assistant.localai

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File

data class LocalModelDownloadState(
    val modelId: String = "",
    val modelName: String = "",
    val state: String = "IDLE",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val message: String = "",
) {
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0L }?.let {
            (downloadedBytes.toFloat() / it.toFloat()).coerceIn(0f, 1f)
        }
}

class LocalModelDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val prefs =
        appContext.getSharedPreferences("haru_model_download", Context.MODE_PRIVATE)

    fun start(option: LocalModelOption) {
        persistOption(option)
        prefs.edit().putBoolean(KEY_PAUSED, false).apply()

        val request = OneTimeWorkRequestBuilder<LocalModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    LocalModelDownloadWorker.KEY_MODEL_ID to option.id,
                    LocalModelDownloadWorker.KEY_MODEL_NAME to option.name,
                    LocalModelDownloadWorker.KEY_FILE_NAME to option.fileName,
                    LocalModelDownloadWorker.KEY_URL to option.downloadUrl,
                    LocalModelDownloadWorker.KEY_SHA256 to option.sha256,
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag(option.id)
            .build()

        workManager.enqueueUniqueWork(
            LocalModelDownloadWorker.UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun pause() {
        prefs.edit().putBoolean(KEY_PAUSED, true).apply()
        workManager.cancelUniqueWork(LocalModelDownloadWorker.UNIQUE_WORK)
    }

    fun resume(options: List<LocalModelOption>): Boolean {
        val id = prefs.getString(KEY_MODEL_ID, "").orEmpty()
        val option = options.firstOrNull { it.id == id } ?: return false
        start(option)
        return true
    }

    fun cancel() {
        val fileName = prefs.getString(KEY_FILE_NAME, "").orEmpty()
        workManager.cancelUniqueWork(LocalModelDownloadWorker.UNIQUE_WORK)
        if (fileName.isNotBlank()) {
            File(appContext.filesDir, "models/" + fileName + ".partial").delete()
        }
        prefs.edit().clear().apply()
    }

    fun isPaused(): Boolean = prefs.getBoolean(KEY_PAUSED, false)

    fun liveData() =
        workManager.getWorkInfosForUniqueWorkLiveData(
            LocalModelDownloadWorker.UNIQUE_WORK
        )

    fun stateFrom(workInfos: List<WorkInfo>?): LocalModelDownloadState {
        val info = workInfos?.lastOrNull()
        val fallbackId = prefs.getString(KEY_MODEL_ID, "").orEmpty()
        val fallbackName = prefs.getString(KEY_MODEL_NAME, "").orEmpty()

        if (isPaused()) {
            return LocalModelDownloadState(
                modelId = fallbackId,
                modelName = fallbackName,
                state = "PAUSED",
                downloadedBytes = partialFile()?.length() ?: 0L,
                message = "Paused · tap Resume to continue.",
            )
        }

        if (info == null) return LocalModelDownloadState()

        val progress = info.progress
        val output = info.outputData
        val downloaded = progress.getLong(
            LocalModelDownloadWorker.KEY_DOWNLOADED,
            partialFile()?.length() ?: 0L,
        )
        val totalRaw = progress.getLong(LocalModelDownloadWorker.KEY_TOTAL, -1L)
        val modelId = progress.getString(LocalModelDownloadWorker.KEY_MODEL_ID)
            ?: output.getString(LocalModelDownloadWorker.KEY_MODEL_ID)
            ?: fallbackId
        val modelName = progress.getString(LocalModelDownloadWorker.KEY_MODEL_NAME)
            ?: output.getString(LocalModelDownloadWorker.KEY_MODEL_NAME)
            ?: fallbackName

        return when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> LocalModelDownloadState(
                modelId, modelName, "QUEUED", downloaded, totalRaw.takeIf { it > 0L },
                "Waiting for network or sufficient battery…"
            )
            WorkInfo.State.RUNNING -> LocalModelDownloadState(
                modelId, modelName, "DOWNLOADING", downloaded, totalRaw.takeIf { it > 0L },
                "Downloading in background."
            )
            WorkInfo.State.SUCCEEDED -> LocalModelDownloadState(
                modelId, modelName, "COMPLETE", downloaded,
                totalRaw.takeIf { it > 0L } ?: downloaded.takeIf { it > 0L },
                "Download complete."
            )
            WorkInfo.State.FAILED -> LocalModelDownloadState(
                modelId, modelName, "FAILED", downloaded, totalRaw.takeIf { it > 0L },
                output.getString(LocalModelDownloadWorker.KEY_ERROR)
                    ?: "Download failed. Tap Resume to retry."
            )
            WorkInfo.State.CANCELLED -> LocalModelDownloadState(
                modelId, modelName, "IDLE", partialFile()?.length() ?: 0L,
                totalRaw.takeIf { it > 0L }, "Download stopped."
            )
        }
    }

    private fun persistOption(option: LocalModelOption) {
        prefs.edit()
            .putString(KEY_MODEL_ID, option.id)
            .putString(KEY_MODEL_NAME, option.name)
            .putString(KEY_FILE_NAME, option.fileName)
            .apply()
    }

    private fun partialFile(): File? {
        val fileName = prefs.getString(KEY_FILE_NAME, "").orEmpty()
        if (fileName.isBlank()) return null
        return File(appContext.filesDir, "models/" + fileName + ".partial")
    }

    companion object {
        private const val KEY_MODEL_ID = "model_id"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_PAUSED = "paused"
    }
}
