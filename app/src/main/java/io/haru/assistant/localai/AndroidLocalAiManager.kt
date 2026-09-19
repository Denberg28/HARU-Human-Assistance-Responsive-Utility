package io.haru.assistant.localai

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URI

data class LocalModelOption(
    val id: String,
    val name: String,
    val description: String,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val minRamGb: Int,
    val minFreeStorageGb: Int,
)

data class LocalAiStatus(
    val ramGb: Int = 0,
    val freeStorageGb: Int = 0,
    val recommendedTier: String = "Checking…",
    val recommendedModelId: String = "",
    val installedModels: List<String> = emptyList(),
    val activeModel: String = "",
    val state: String = "IDLE",
    val message: String = "Local AI is not configured yet.",
)

class AndroidLocalAiManager(
    private val context: Context,
) {
    private val modelDir = File(context.filesDir, "models").apply { mkdirs() }
    private val preferences =
        context.getSharedPreferences("haru_local_ai", Context.MODE_PRIVATE)

    fun inspect(activeModel: String = ""): LocalAiStatus {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val ramGb = (memoryInfo.totalMem / 1_073_741_824L).toInt().coerceAtLeast(1)

        val stat = StatFs(context.filesDir.absolutePath)
        val freeGb = (stat.availableBytes / 1_073_741_824L).toInt().coerceAtLeast(0)
        val recommended = CURATED_MODELS.first()

        val installed = modelDir
            .listFiles { file ->
                file.isFile &&
                    file.extension.equals("gguf", ignoreCase = true) &&
                    isSafeModelName(file.name)
            }
            ?.sortedBy { it.name.lowercase() }
            ?.map { it.name }
            ?: emptyList()

        val preferredModel = activeModel.ifBlank {
            preferences.getString("active_model", "").orEmpty()
        }

        return LocalAiStatus(
            ramGb = ramGb,
            freeStorageGb = freeGb,
            recommendedTier = recommended.name,
            recommendedModelId = recommended.id,
            installedModels = installed,
            activeModel = preferredModel.takeIf { installed.contains(it) } ?: "",
            state = if (installed.isEmpty()) "SETUP" else "READY",
            message = if (installed.isEmpty()) {
                "Download Qwen3.5 2B Q4_K_M GGUF to enable private on-device AI."
            } else {
                installed.size.toString() + " local GGUF model(s) available."
            },
        )
    }

    suspend fun importModel(uri: Uri): String = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri).orEmpty()
        require(displayName.endsWith(".gguf", ignoreCase = true)) {
            "Select a .gguf model file."
        }

        val safeName = sanitizeModelName(displayName)
        val target = File(modelDir, safeName)
        val temp = File(modelDir, safeName + ".partial")

        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not read the selected file." }
                temp.outputStream().buffered().use { output ->
                    input.copyTo(output, bufferSize = 1024 * 1024)
                }
            }

            require(temp.length() > 1_000_000L) {
                "Selected model file is unexpectedly small."
            }
            require(hasGgufMagic(temp)) {
                "Selected file is not a valid GGUF model."
            }

            if (target.exists()) target.delete()
            require(temp.renameTo(target)) { "Could not finalize imported model." }
            target.name
        } catch (exc: Exception) {
            temp.delete()
            throw exc
        }
    }

    fun curatedModels(): List<LocalModelOption> = CURATED_MODELS

    suspend fun downloadModel(
        url: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): String = withContext(Dispatchers.IO) {
        val parsed = URI(url.trim())
        require(parsed.scheme.equals("https", ignoreCase = true)) {
            "Only HTTPS model downloads are allowed."
        }

        val rawName = parsed.path.substringAfterLast('/').substringBefore('?')
        require(rawName.endsWith(".gguf", ignoreCase = true)) {
            "The download URL must point to a .gguf model."
        }

        val safeName = sanitizeModelName(rawName)
        val target = File(modelDir, safeName)
        val temp = File(modelDir, safeName + ".partial")

        val connection = parsed.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 120_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "HARU-Android/0.2")

        try {
            connection.connect()
            require(connection.url.protocol.equals("https", ignoreCase = true)) {
                "Download redirected to a non-HTTPS location."
            }
            require(connection.responseCode in 200..299) {
                "Model download failed with HTTP " + connection.responseCode + "."
            }

            val stat = StatFs(context.filesDir.absolutePath)
            val reserveBytes = 512L * 1024L * 1024L
            val maxWritable = (stat.availableBytes - reserveBytes).coerceAtLeast(0L)
            val expected = connection.contentLengthLong
            val expectedOrNull = expected.takeIf { it > 0 }
            if (expectedOrNull != null) {
                require(expectedOrNull <= maxWritable) {
                    "Not enough free storage for this model."
                }
            }
            onProgress(0L, expectedOrNull)

            connection.inputStream.buffered().use { input ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= maxWritable) {
                            "Model download stopped before storage was exhausted."
                        }
                        output.write(buffer, 0, read)
                        onProgress(total, expectedOrNull)
                    }
                }
            }

            require(temp.length() > 1_000_000L) {
                "Downloaded model is unexpectedly small."
            }
            require(hasGgufMagic(temp)) {
                "Downloaded file is not a valid GGUF model."
            }
            if (target.exists()) target.delete()
            require(temp.renameTo(target)) { "Could not finalize downloaded model." }
            target.name
        } catch (exc: Exception) {
            temp.delete()
            throw exc
        } finally {
            connection.disconnect()
        }
    }

    suspend fun validateInstalledModel(fileName: String): String = withContext(Dispatchers.IO) {
        val target = modelFile(fileName)
        validateModel(target)
        preferences.edit().putString("active_model", target.name).apply()
        target.name
    }

    suspend fun generate(fileName: String, prompt: String): String = withContext(Dispatchers.IO) {
        val target = modelFile(fileName)
        require(prompt.isNotBlank()) { "Prompt is empty." }
        require(prompt.length <= 12_000) {
            "Prompt is too large for on-device mode."
        }
        require(hasGgufMagic(target)) {
            "Local model is not a valid GGUF file."
        }

        val config = LlamaConfig(
            contextSize = 2048,
            threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
            gpuLayers = 0,
            temperature = 0.65f,
            topP = 0.90f,
            topK = 40,
        )
        val model = Llama.loadModel(target.absolutePath, config)
        try {
            val response = Llama.complete(
                model = model,
                prompt = prompt,
                systemPrompt =
                    "You are HARU's private on-device assistant. Be concise, useful, and honest. " +
                        "Do not claim internet access or actions you cannot perform.",
                maxTokens = 512,
            ).text.trim()
            require(response.isNotBlank()) { "Local model returned no text." }
            response
        } finally {
            Llama.releaseModel(model)
        }
    }

    fun deleteModel(fileName: String): Boolean {
        val target = modelFile(fileName)
        val deleted = target.exists() && target.delete()
        if (
            deleted &&
            preferences.getString("active_model", "").orEmpty() == target.name
        ) {
            preferences.edit().remove("active_model").apply()
        }
        return deleted
    }

    fun modelPath(fileName: String): String = modelFile(fileName).absolutePath

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
        }.getOrNull()
    }

    private fun modelFile(fileName: String): File {
        require(isSafeModelName(fileName)) { "Invalid local model name." }
        val candidate = File(modelDir, fileName).canonicalFile
        require(candidate.parentFile == modelDir.canonicalFile) {
            "Invalid local model path."
        }
        return candidate
    }

    private suspend fun validateModel(file: File) {
        require(file.exists() && file.isFile) { "Local model file does not exist." }
        require(file.length() > 1_000_000L) { "Local model file is unexpectedly small." }
        require(hasGgufMagic(file)) { "Local model is not a valid GGUF file." }

        val model = Llama.loadModel(
            modelPath = file.absolutePath,
            config = LlamaConfig(
                contextSize = 512,
                threads = 2,
                gpuLayers = 0,
                temperature = 0f,
            ),
        )
        Llama.releaseModel(model)
    }

    private fun hasGgufMagic(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        val header = ByteArray(4)
        val read = FileInputStream(file).use { it.read(header) }
        return read == 4 &&
            header[0] == 'G'.code.toByte() &&
            header[1] == 'G'.code.toByte() &&
            header[2] == 'U'.code.toByte() &&
            header[3] == 'F'.code.toByte()
    }

    private fun sanitizeModelName(value: String): String {
        val safe = value
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(120)
        require(isSafeModelName(safe)) { "Invalid local model name." }
        return safe
    }

    private fun isSafeModelName(value: String): Boolean =
        value.length in 1..120 &&
            value.endsWith(".gguf", ignoreCase = true) &&
            Regex("^[A-Za-z0-9._-]+$").matches(value) &&
            !value.contains("..")

    companion object {
        val CURATED_MODELS = listOf(
            LocalModelOption(
                id = "unsloth-qwen3.5-2b-q4-k-m",
                name = "Qwen3.5 2B Q4_K_M",
                description = "Unsloth GGUF · ~1.28 GB · recommended for POCO-class phones",
                fileName = "Qwen3.5-2B-Q4_K_M.gguf",
                downloadUrl =
                    "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/" +
                        "Qwen3.5-2B-Q4_K_M.gguf?download=true",
                sha256 = "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223",
                minRamGb = 6,
                minFreeStorageGb = 3,
            ),
        )
    }
}
