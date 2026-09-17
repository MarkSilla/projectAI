package com.marksilla.auraagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalLLMTest {
    @Test
    fun resolvesDefaultModelPathFromAppFilesDirectory() {
        val config = LocalLLMConfig()
        val filesDir = File("/tmp/aura-test")
        val resolved = config.resolvedModelPath(filesDir)

        assertEquals(File("/tmp/aura-test/models/qwen2.5-0.5b-instruct-q4_k_m.gguf"), resolved)
    }

    @Test
    fun detectsMissingModelFileAsInvalid() {
        val missingFile = File("/tmp/aura-test/does-not-exist.gguf")

        assertTrue(!isValidGGUFModel(missingFile))
    }

    @Test
    fun detectsValidGGUFHeader() {
        val validFile = File.createTempFile("valid-model", ".gguf")
        val header = byteArrayOf(
            'G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(),
            3, 0, 0, 0,
            1, 0, 0, 0, 0, 0, 0, 0,
            1, 0, 0, 0, 0, 0, 0, 0
        )
        validFile.writeBytes(header)

        assertTrue(isValidGGUFModel(validFile))
        validFile.delete()
    }

    @Test
    fun rejectsPlaceholderGGUFHeader() {
        val invalidFile = File.createTempFile("placeholder-model", ".gguf")
        invalidFile.writeBytes(byteArrayOf(
            'G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(),
            0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0
        ))

        assertTrue(!isValidGGUFModel(invalidFile))
        invalidFile.delete()
    }

    @Test
    fun detectsValidQwenGGUFWithItsActualFilename() {
        val modelDir = File.createTempFile("qwen2.5-0.5b-instruct-q4_k_m", ".gguf")
        val header = byteArrayOf(
            'G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(),
            3, 0, 0, 0,
            1, 0, 0, 0, 0, 0, 0, 0,
            1, 0, 0, 0, 0, 0, 0, 0
        )
        modelDir.writeBytes(header)

        assertTrue(isValidGGUFModel(modelDir))
        modelDir.delete()
    }

    @Test
    fun fallbackLocalLlmEchoesPromptWhenNativeRuntimeIsUnavailable() {
        val llm = LocalRuleFallbackLLM()

        val result = llm.generate("Hello from a local assistant", emptyList(), 32)

        assertTrue(result != null)
        assertTrue(result!!.contains("Hello"))
    }
}
