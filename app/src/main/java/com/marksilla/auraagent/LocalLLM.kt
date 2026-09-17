package com.marksilla.auraagent

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_MODEL_FILENAME = "model.gguf"
private const val PREFERRED_QWEN_MODEL_FILENAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"

private fun preferredModelCandidates(): List<String> = listOf(
    PREFERRED_QWEN_MODEL_FILENAME,
    DEFAULT_MODEL_FILENAME
)

private fun preferredAssetCandidates(): List<String> = listOf(
    "models/$PREFERRED_QWEN_MODEL_FILENAME",
    "models/$DEFAULT_MODEL_FILENAME"
)

private fun isValidGGUFBytes(bytes: ByteArray): Boolean {
    if (bytes.size < 4 + 4 + 8 + 8) {
        return false
    }

    if (bytes.copyOfRange(0, 4).decodeToString() != "GGUF") {
        return false
    }

    val version = (
        (bytes[4].toInt() and 0xFF) or
            ((bytes[5].toInt() and 0xFF) shl 8) or
            ((bytes[6].toInt() and 0xFF) shl 16) or
            ((bytes[7].toInt() and 0xFF) shl 24)
        )

    if (version < 1) {
        return false
    }

    val tensorCountValue = readUInt64LE(bytes.copyOfRange(8, 16))
    val kvCountValue = readUInt64LE(bytes.copyOfRange(16, 24))

    return tensorCountValue > 0L && kvCountValue >= 0L
}

internal fun isValidGGUFModel(file: File): Boolean {
    if (!file.exists() || !file.isFile) {
        return false
    }

    return runCatching {
        val bytes = file.readBytes()
        isValidGGUFBytes(bytes)
    }.getOrDefault(false)
}

private fun readUInt64LE(bytes: ByteArray): Long {
    var value = 0L

    for (idx in bytes.indices) {
        value = value or (
            (bytes[idx].toLong() and 0xFFL) shl (idx * 8)
        )
    }

    return value
}

data class LocalLLMConfig(
    val modelPath: String = "",
    val contextSize: Int = 2048,
    val maxTokens: Int = 256,
    val temperature: Float = 0.7f,
    val threads: Int = 4,
    val useGpu: Boolean = false
) {
    fun resolvedModelPath(baseDir: File): File {
        val explicit = modelPath.trim()

        if (explicit.isNotBlank()) {
            return File(explicit)
        }

        val defaultDir = File(baseDir, "models")
        return File(defaultDir, PREFERRED_QWEN_MODEL_FILENAME)
    }
}

interface LocalLLM {
    val status: String

    fun loadModel(): Boolean

    fun unloadModel()

    fun isModelLoaded(): Boolean

    fun generate(
        prompt: String,
        context: List<String> = emptyList(),
        maxTokens: Int = 256
    ): String?

    suspend fun streamGenerate(
        prompt: String,
        context: List<String> = emptyList(),
        maxTokens: Int = 256,
        onToken: (String) -> Unit
    ): String?

    fun cancelGeneration()

    fun getModelInfo(): String
}

class LocalLLMRuntime(
    private val context: Context,
    private val config: LocalLLMConfig = LocalLLMConfig()
) : LocalLLM {

    @Volatile
    private var modelLoaded = false

    @Volatile
    private var generationCancelled = false

    @Volatile
    private var currentStatus = "No local model loaded"

    private val nativeBridge = LocalLLMNativeBridge()

    override val status: String
        get() = currentStatus

    init {
        val modelDir = File(context.filesDir, "models")

        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        ensureBundledModel()
    }

    /**
     * Makes sure the bundled GGUF model exists
     * inside the app's internal storage.
     *
     * APK:
     * assets/models/model.gguf
     *
     * ↓ copied to
     *
     * /data/data/com.marksilla.auraagent/files/models/model.gguf
     */
    private fun ensureBundledModel(): File {
        val explicitFile = config.resolvedModelPath(context.filesDir)

        // 1. Use existing model if already present and valid.
        if (
            explicitFile.exists() &&
            explicitFile.isFile &&
            isValidGGUFModel(explicitFile)
        ) {
            return explicitFile
        }

        // 2. Search app storage for another valid GGUF.
        val discovered = findValidModelInAppStorage()

        if (discovered != null) {
            return discovered
        }

        // 3. Copy bundled model from APK assets.
        val assetPaths = preferredAssetCandidates()

        var copied = false

        for (assetPath in assetPaths) {
            copied = runCatching {
                val assetBytes = context.assets
                    .open(assetPath)
                    .use { it.readBytes() }

                // Make sure the asset is really a GGUF file.
                if (!isValidGGUFBytes(assetBytes)) {
                    return@runCatching false
                }

                val targetDir =
                    explicitFile.parentFile
                        ?: File(context.filesDir, "models")

                if (
                    !targetDir.exists() &&
                    !targetDir.mkdirs()
                ) {
                    return@runCatching false
                }

                explicitFile.writeBytes(assetBytes)

                true
            }.getOrDefault(false)

            if (copied) {
                break
            }
        }

        return explicitFile
    }

    /**
     * Searches the app's internal model directory
     * for a valid GGUF file.
     */
    private fun findValidModelInAppStorage(): File? {
        val storageDir = File(
            context.filesDir,
            "models"
        )

        if (
            !storageDir.exists() ||
            !storageDir.isDirectory
        ) {
            return null
        }

        val nestedFiles = storageDir
            .walkTopDown()
            .filter { it.isFile }
            .toList()

        // Prefer the bundled Qwen model, then the generic fallback.
        val preferred = nestedFiles
            .sortedBy { file ->
                when (file.name) {
                    PREFERRED_QWEN_MODEL_FILENAME -> 0
                    DEFAULT_MODEL_FILENAME -> 1
                    else -> 2
                }
            }
            .firstOrNull {
                isValidGGUFModel(it)
            }

        if (preferred != null) {
            return preferred
        }

        // Fallback: any valid .gguf file.
        return nestedFiles.firstOrNull {
            it.name.endsWith(
                ".gguf",
                ignoreCase = true
            ) && isValidGGUFModel(it)
        }
    }

    override fun loadModel(): Boolean {
        return try {
            val modelFile = ensureBundledModel()

            if (!isValidGGUFModel(modelFile)) {
                currentStatus =
                    "No valid GGUF model found at " +
                        "${modelFile.absolutePath}. " +
                        "Add a compatible .gguf file such as " +
                        "${PREFERRED_QWEN_MODEL_FILENAME} under " +
                        "app/src/main/assets/models/."

                modelLoaded = false

                return false
            }

            val loaded = nativeBridge.loadModel(
                modelPath = modelFile.absolutePath,
                contextSize = config.contextSize,
                threads = config.threads,
                useGpu = config.useGpu
            )

            modelLoaded = loaded

            currentStatus = if (loaded) {
                "Local model loaded: ${modelFile.name}"
            } else {
                "Failed to load local model"
            }

            loaded
        } catch (_: UnsatisfiedLinkError) {
            modelLoaded = false

            currentStatus =
                "Native llama bridge unavailable; " +
                    "using local fallback logic"

            false
        }
    }

    override fun unloadModel() {
        try {
            nativeBridge.unloadModel()
        } catch (_: UnsatisfiedLinkError) {
        }

        modelLoaded = false
        currentStatus = "Local model unloaded"
    }

    override fun isModelLoaded(): Boolean {
        return modelLoaded
    }

    override fun generate(
        prompt: String,
        context: List<String>,
        maxTokens: Int
    ): String? {

        val cleanPrompt = prompt.trim()

        if (cleanPrompt.isBlank()) {
            return null
        }

        if (!modelLoaded) {
            val loaded = loadModel()

            if (!loaded) {
                return null
            }
        }

        generationCancelled = false

        return try {
            nativeBridge.generate(
                prompt = buildPrompt(
                    cleanPrompt,
                    context
                ),
                maxTokens = maxTokens
                    .coerceAtLeast(1)
                    .coerceAtMost(
                        config.maxTokens.coerceAtLeast(1)
                    )
            )
        } catch (_: UnsatisfiedLinkError) {
            null
        }
    }

    override suspend fun streamGenerate(
        prompt: String,
        context: List<String>,
        maxTokens: Int,
        onToken: (String) -> Unit
    ): String? = withContext(Dispatchers.Default) {

        val cleanPrompt = prompt.trim()

        if (cleanPrompt.isBlank()) {
            return@withContext null
        }

        if (!modelLoaded) {
            val loaded = loadModel()

            if (!loaded) {
                return@withContext null
            }
        }

        generationCancelled = false

        return@withContext try {
            val fullText = nativeBridge.streamGenerate(
                prompt = buildPrompt(
                    cleanPrompt,
                    context
                ),
                maxTokens = maxTokens
                    .coerceAtLeast(1)
                    .coerceAtMost(
                        config.maxTokens.coerceAtLeast(1)
                    )
            )

            if (!fullText.isNullOrBlank()) {
                fullText.forEach { ch ->
                    onToken(ch.toString())
                }
            }

            fullText
        } catch (_: UnsatisfiedLinkError) {
            null
        }
    }

    override fun cancelGeneration() {
        generationCancelled = true

        try {
            nativeBridge.cancelGeneration()
        } catch (_: UnsatisfiedLinkError) {
        }

        currentStatus = "Generation cancelled"
    }

    override fun getModelInfo(): String {
        val base = if (modelLoaded) {
            "Loaded"
        } else {
            "Not loaded"
        }

        return "$base | " +
            "model=$DEFAULT_MODEL_FILENAME | " +
            "context=${config.contextSize} | " +
            "maxTokens=${config.maxTokens} | " +
            "threads=${config.threads}"
    }

    private fun buildPrompt(
        prompt: String,
        context: List<String>
    ): String {

        val trimmedContext = context
            .filter { it.isNotBlank() }
            .takeLast(8)

        return if (trimmedContext.isEmpty()) {
            prompt
        } else {
            (trimmedContext + prompt)
                .joinToString(
                    separator = "\n\n"
                )
        }
    }
}

class LocalRuleFallbackLLM : LocalLLM {

    override val status: String =
        "Fallback local logic active"

    override fun loadModel(): Boolean = true

    override fun unloadModel() = Unit

    override fun isModelLoaded(): Boolean = true

    override fun generate(
        prompt: String,
        context: List<String>,
        maxTokens: Int
    ): String? {

        return if (prompt.isBlank()) {
            null
        } else {
            prompt
        }
    }

    override suspend fun streamGenerate(
        prompt: String,
        context: List<String>,
        maxTokens: Int,
        onToken: (String) -> Unit
    ): String? {

        val text = prompt.ifBlank {
            null
        } ?: return null

        text.forEach { ch ->
            onToken(ch.toString())
        }

        return text
    }

    override fun cancelGeneration() = Unit

    override fun getModelInfo(): String =
        "Fallback local rule engine"
}

private class LocalLLMNativeBridge {

    companion object {

        init {
            try {
                System.loadLibrary("aura_llama")
            } catch (_: UnsatisfiedLinkError) {
            }
        }
    }

    external fun loadModel(
        modelPath: String,
        contextSize: Int,
        threads: Int,
        useGpu: Boolean
    ): Boolean

    external fun unloadModel()

    external fun generate(
        prompt: String,
        maxTokens: Int
    ): String?

    external fun streamGenerate(
        prompt: String,
        maxTokens: Int
    ): String?

    external fun cancelGeneration()
}
