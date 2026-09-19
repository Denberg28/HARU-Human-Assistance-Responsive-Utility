package io.haru.assistant.localai

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import android.os.StatFs
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

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

        val ramGb = (memoryInfo.totalMem / BYTES_PER_GIB).toInt().coerceAtLeast(1)
        val freeGb =
            (StatFs(context.filesDir.absolutePath).availableBytes / BYTES_PER_GIB)
                .toInt()
                .coerceAtLeast(0)
        val recommended = CURATED_MODELS.first()

        val installed = modelDir
            .listFiles { file ->
                file.isFile &&
                    file.extension.equals("gguf", ignoreCase = true) &&
                    isSafeModelName(file.name)
            }
            ?.sortedBy { it.name.lowercase() }
            ?.map { it.name }
            .orEmpty()

        val preferredModel = activeModel.ifBlank {
            preferences.getString(KEY_ACTIVE_MODEL, "").orEmpty()
        }

        return LocalAiStatus(
            ramGb = ramGb,
            freeStorageGb = freeGb,
            recommendedTier = recommended.name,
            recommendedModelId = recommended.id,
            installedModels = installed,
            activeModel = preferredModel.takeIf(installed::contains).orEmpty(),
            state = if (installed.isEmpty()) "SETUP" else "READY",
            message = if (installed.isEmpty()) {
                "Download Qwen3.5 2B Q4_K_M GGUF to enable private on-device AI."
            } else {
                installed.size.toString() + " local GGUF model(s) available."
            },
        )
    }

    fun curatedModels(): List<LocalModelOption> = CURATED_MODELS

    suspend fun validateInstalledModel(fileName: String): String = withContext(Dispatchers.IO) {
        val target = modelFile(fileName)
        validateModel(target)
        preferences.edit().putString(KEY_ACTIVE_MODEL, target.name).apply()
        target.name
    }

    suspend fun generate(fileName: String, prompt: String): String = withContext(Dispatchers.IO) {
        val target = modelFile(fileName)
        require(prompt.isNotBlank()) { "Prompt is empty." }
        require(prompt.length <= MAX_PROMPT_CHARS) {
            "Prompt is too large for on-device mode."
        }
        require(hasGgufMagic(target)) {
            "Local model is not a valid GGUF file."
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val powerSave = powerManager.isPowerSaveMode
        val threads = if (powerSave) {
            2
        } else {
            Runtime.getRuntime().availableProcessors().coerceIn(2, 3)
        }

        val model = Llama.loadModel(
            modelPath = target.absolutePath,
            config = LlamaConfig(
                contextSize = if (powerSave) 1536 else 2048,
                threads = threads,
                gpuLayers = 0,
                temperature = 0.65f,
                topP = 0.90f,
                topK = 40,
            ),
        )

        try {
            val response = Llama.complete(
                model = model,
                prompt = prompt,
                systemPrompt =
                    "You are HARU's private on-device assistant. Be concise, useful, and honest. " +
                        "Do not claim internet access or actions you cannot perform.",
                maxTokens = if (powerSave) 384 else 512,
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
            preferences.getString(KEY_ACTIVE_MODEL, "").orEmpty() == target.name
        ) {
            preferences.edit().remove(KEY_ACTIVE_MODEL).apply()
        }
        return deleted
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
        require(file.length() > MIN_MODEL_BYTES) {
            "Local model file is unexpectedly small."
        }
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

    private fun isSafeModelName(value: String): Boolean =
        value.length in 1..120 &&
            value.endsWith(".gguf", ignoreCase = true) &&
            SAFE_MODEL_NAME.matches(value) &&
            !value.contains("..")

    companion object {
        private const val KEY_ACTIVE_MODEL = "active_model"
        private const val BYTES_PER_GIB = 1_073_741_824L
        private const val MIN_MODEL_BYTES = 1_000_000L
        private const val MAX_PROMPT_CHARS = 12_000
        private val SAFE_MODEL_NAME = Regex("^[A-Za-z0-9._-]+$")

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
