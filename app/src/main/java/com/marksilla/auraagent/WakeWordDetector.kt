package com.marksilla.auraagent

fun containsWakeWord(text: String): Boolean {
    val normalized = text
        .lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    return normalized.contains("hey aura")
}
