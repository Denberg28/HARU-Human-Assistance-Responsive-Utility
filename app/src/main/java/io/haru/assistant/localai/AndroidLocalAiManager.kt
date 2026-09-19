package io.haru.assistant.localai

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

data class LocalAiStatus(
    val ramGb: Int = 0,
    val freeStorageGb: Int = 0,
    val recommendedTier: String = "Checking…",
    val installedModels: List<String> = emptyList(),
    val activeModel: String = "",
    val state: String = "IDLE",
    val message: String = "Local AI is not configured yet.",
)

class AndroidLocalAiManager(
    private val context: Context,
) {
    private val modelDir = File(context.filesDir, "models").apply { mkdirs() }

    fun inspect(activeModel: String = ""): LocalAiStatus {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val ramGb = (memoryInfo.totalMem / 1_073_741_824L).toInt().coerceAtLeast(1)

        val stat = StatFs(context.filesDir.absolutePath)
        val freeGb = (stat.availableBytes / 1_073_741_824L).toInt().coerceAtLeast(0)

        val recommendation = when {
            ramGb >= 12 && freeGb >= 6 -> "Gemma 4 E4B"
            ramGb >= 8 && freeGb >= 4 -> "Gemma 4 E2B"
            else -> "Use a smaller LiteRT-LM model"
        }

        val installed = modelDir
            .listFiles { file -> file.isFile && file.extension.equals("litertlm", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?.map { it.name }
            ?: emptyList()

        return LocalAiStatus(
            ramGb = ramGb,
            freeStorageGb = freeGb,
            recommendedTier = recommendation,
            installedModels = installed,
            activeModel = activeModel.takeIf { installed.contains(it) } ?: "",
            state = if (installed.isEmpty()) "SETUP" else "READY",
            message = if (installed.isEmpty()) {
                "Import or download a .litertlm model to enable on-device AI."
            } else {
                installed.size.toString() + " local model(s) available."
            },
        )
    }

    suspend fun importModel(uri: Uri): String = withContext(Dispatchers.IO) {
        val displayName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.endsWith(".litertlm", ignoreCase = true) }
            ?: "haru-model.litertlm"

        val safeName = displayName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(120)

        val target = File(modelDir, safeName)
        val temp = File(modelDir, safeName + ".partial")

        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not read the selected file." }
                temp.outputStream().buffered().use { output ->
                    input.copyTo(output, bufferSize = 1024 * 1024)
                }
            }

            require(temp.length() > 1_000_000L) { "Selected model file is unexpectedly small." }

            if (target.exists()) target.delete()
            require(temp.renameTo(target)) { "Could not finalize imported model." }
            target.name
        } catch (exc: Exception) {
            temp.delete()
            throw exc
        }
    }

    suspend fun downloadModel(url: String): String = withContext(Dispatchers.IO) {
        val parsed = URI(url.trim())
        require(parsed.scheme.equals("https", ignoreCase = true)) {
            "Only HTTPS model downloads are allowed."
        }

        val rawName = parsed.path.substringAfterLast('/').substringBefore('?')
        require(rawName.endsWith(".litertlm", ignoreCase = true)) {
            "The download URL must point to a .litertlm model."
        }

        val safeName = rawName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(120)

        val target = File(modelDir, safeName)
        val temp = File(modelDir, safeName + ".partial")

        val connection = parsed.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 120_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "HARU-Android/0.1")

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
            if (expected > 0) {
                require(expected <= maxWritable) {
                    "Not enough free storage for this model."
                }
            }

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
                    }
                }
            }

            require(temp.length() > 1_000_000L) { "Downloaded model is unexpectedly small." }
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
        target.name
    }

    suspend fun generate(fileName: String, prompt: String): String = withContext(Dispatchers.IO) {
        val target = modelFile(fileName)
        require(prompt.isNotBlank()) { "Prompt is empty." }
        require(prompt.length <= 12_000) { "Prompt is too large for on-device mode." }

        val config = EngineConfig(
            modelPath = target.absolutePath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.absolutePath,
        )

        Engine(config).use { engine ->
            engine.initialize()
            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(
                    "You are HARU's private on-device assistant. Be concise, useful, and honest. " +
                        "Do not claim internet access or actions you cannot perform."
                )
            )
            engine.createConversation(conversationConfig).use { conversation ->
                val response = conversation.sendMessage(
                    prompt,
                    maxOutputToken = 1024,
                ).toString().trim()
                require(response.isNotBlank()) { "Local model returned no text." }
                response
            }
        }
    }

    fun deleteModel(fileName: String): Boolean {
        val target = modelFile(fileName)
        return target.exists() && target.delete()
    }

    fun modelPath(fileName: String): String = modelFile(fileName).absolutePath

    private fun modelFile(fileName: String): File {
        val safe = fileName.substringAfterLast('/').substringAfterLast('\\')
        require(safe == fileName && safe.endsWith(".litertlm", ignoreCase = true)) {
            "Invalid local model name."
        }
        return File(modelDir, safe)
    }

    private fun validateModel(file: File) {
        require(file.exists() && file.isFile) { "Local model file does not exist." }

        val config = EngineConfig(
            modelPath = file.absolutePath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.absolutePath,
        )

        Engine(config).use { engine ->
            engine.initialize()
        }
    }
}
