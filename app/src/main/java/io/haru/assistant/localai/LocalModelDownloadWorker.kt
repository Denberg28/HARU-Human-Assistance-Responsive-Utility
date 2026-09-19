package io.haru.assistant.localai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI

class LocalModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID).orEmpty()
        val modelName = inputData.getString(KEY_MODEL_NAME).orEmpty()
        val fileName = inputData.getString(KEY_FILE_NAME).orEmpty()
        val url = inputData.getString(KEY_URL).orEmpty()

        if (modelId.isBlank() || modelName.isBlank() || fileName.isBlank() ||
            !fileName.endsWith(".litertlm", ignoreCase = true)
        ) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "Invalid model download request."))
        }

        val parsed = runCatching { URI(url) }.getOrNull()
            ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "Invalid model URL."))
        if (!parsed.scheme.equals("https", ignoreCase = true)) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "Only HTTPS model downloads are allowed."))
        }

        val modelDir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val target = File(modelDir, fileName)
        val partial = File(modelDir, fileName + ".partial")

        if (target.exists() && target.length() > 1_000_000L) {
            return@withContext Result.success(workDataOf(KEY_FILE_NAME to target.name))
        }

        var existing = partial.length().coerceAtLeast(0L)
        setForeground(createForegroundInfo(modelName, existing, null))
        setProgress(progressData(modelId, modelName, existing, null))

        var connection: HttpURLConnection? = null
        try {
            connection = parsed.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "HARU-Android/0.1")
            if (existing > 0L) {
                connection.setRequestProperty("Range", "bytes=" + existing + "-")
            }

            connection.connect()
            require(connection.url.protocol.equals("https", ignoreCase = true)) {
                "Download redirected to a non-HTTPS location."
            }

            val response = connection.responseCode
            val resumed = existing > 0L && response == HttpURLConnection.HTTP_PARTIAL
            require(response in 200..299) {
                "Model download failed with HTTP " + response + "."
            }

            if (existing > 0L && !resumed) {
                existing = 0L
                partial.delete()
            }

            val remaining = connection.contentLengthLong.takeIf { it > 0L }
            val total = when {
                remaining == null -> null
                resumed -> existing + remaining
                else -> remaining
            }

            val stat = StatFs(applicationContext.filesDir.absolutePath)
            val reserveBytes = 512L * 1024L * 1024L
            val maxWritable = (stat.availableBytes - reserveBytes).coerceAtLeast(0L)
            if (total != null) {
                require((total - existing).coerceAtLeast(0L) <= maxWritable) {
                    "Not enough free storage for this model."
                }
            }

            FileOutputStream(partial, resumed).buffered().use { output ->
                connection.inputStream.buffered().use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    var downloaded = existing
                    var lastNotify = existing

                    while (true) {
                        if (isStopped) throw CancellationException("Download stopped.")
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloaded += read

                        require(downloaded - existing <= maxWritable) {
                            "Model download stopped before storage was exhausted."
                        }

                        output.write(buffer, 0, read)

                        if (downloaded - lastNotify >= 4L * 1024L * 1024L) {
                            lastNotify = downloaded
                            setProgress(progressData(modelId, modelName, downloaded, total))
                            setForeground(createForegroundInfo(modelName, downloaded, total))
                        }
                    }
                    output.flush()
                }
            }

            require(partial.length() > 1_000_000L) {
                "Downloaded model is unexpectedly small."
            }
            if (target.exists()) target.delete()
            require(partial.renameTo(target)) {
                "Could not finalize downloaded model."
            }

            setProgress(progressData(modelId, modelName, target.length(), target.length()))
            Result.success(
                workDataOf(
                    KEY_MODEL_ID to modelId,
                    KEY_MODEL_NAME to modelName,
                    KEY_FILE_NAME to target.name,
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exc: Exception) {
            Result.failure(
                workDataOf(
                    KEY_MODEL_ID to modelId,
                    KEY_MODEL_NAME to modelName,
                    KEY_ERROR to (exc.message ?: "Model download failed."),
                )
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun createForegroundInfo(
        modelName: String,
        downloaded: Long,
        total: Long?,
    ): ForegroundInfo {
        ensureChannel()
        val progress = if (total != null && total > 0L) {
            ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
        } else 0

        val text = if (total != null && total > 0L) {
            mb(downloaded).toString() + " / " + mb(total) + " MB · " + progress + "%"
        } else {
            mb(downloaded).toString() + " MB downloaded"
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading " + modelName)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, total == null)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "HARU model downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Background Local AI model downloads"
            }
        )
    }

    private fun progressData(
        modelId: String,
        modelName: String,
        downloaded: Long,
        total: Long?,
    ): Data = workDataOf(
        KEY_MODEL_ID to modelId,
        KEY_MODEL_NAME to modelName,
        KEY_DOWNLOADED to downloaded,
        KEY_TOTAL to (total ?: -1L),
    )

    private fun mb(bytes: Long): Long = bytes / (1024L * 1024L)

    companion object {
        const val UNIQUE_WORK = "haru_local_model_download"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_URL = "url"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"

        private const val CHANNEL_ID = "haru_model_downloads"
        private const val NOTIFICATION_ID = 42017
    }
}
