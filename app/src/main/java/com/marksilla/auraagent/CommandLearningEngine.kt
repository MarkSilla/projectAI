package com.marksilla.auraagent

import java.util.Locale

private const val DEFAULT_PATTERN_CONFIDENCE = 0.82f

class CommandLearningEngine {
    private val learnedPatterns = mutableListOf<LearnedPattern>()

    val patterns: List<LearnedPattern>
        get() = learnedPatterns.toList()

    fun learn(
        command: String,
        installedApps: List<InstalledApp>,
        understanding: CommandUnderstanding
    ) {
        val normalizedCommand = normalizeLearningText(command)
        if (normalizedCommand.isBlank()) {
            return
        }

        val patternText =
            understanding.learnedPattern
                ?.takeIf { it.isNotBlank() }
                ?: createOpenAppLearningPattern(
                    command = command,
                    targetCandidates =
                        understanding.target
                            ?.let { listOf(it) }
                            ?: emptyList()
                )

        val pattern =
            LearnedPattern(
                normalizedPattern = normalizePattern(patternText),
                intent = understanding.intent,
                targetSlot = if (understanding.intent == AuraCommandIntent.OPEN_APP) "app" else null,
                confidence = understanding.confidence.coerceIn(0.0f, 1.0f),
                usageCount = 1,
                lastUsedAt = System.currentTimeMillis()
            )

        if (pattern.normalizedPattern.isBlank()) {
            return
        }

        val existingIndex =
            learnedPatterns.indexOfFirst {
                it.normalizedPattern == pattern.normalizedPattern
            }

        if (existingIndex >= 0) {
            val existing = learnedPatterns[existingIndex]
            learnedPatterns[existingIndex] =
                existing.copy(
                    confidence = minConfidence(existing.confidence, pattern.confidence),
                    usageCount = existing.usageCount + 1,
                    lastUsedAt = System.currentTimeMillis()
                )
            return
        }

        learnedPatterns += pattern
    }

    fun match(
        command: String,
        installedApps: List<InstalledApp>
    ): CommandUnderstanding? {
        val normalizedCommand = normalizeLearningText(command)
        if (normalizedCommand.isBlank()) {
            return null
        }

        val candidates =
            learnedPatterns
                .sortedByDescending { it.usageCount }
                .sortedByDescending { it.confidence }

        for (pattern in candidates) {
            val response = matchPattern(pattern, normalizedCommand, installedApps)
            if (response != null) {
                return response
            }
        }

        return null
    }

    private fun matchPattern(
        pattern: LearnedPattern,
        command: String,
        installedApps: List<InstalledApp>
    ): CommandUnderstanding? {
        if (pattern.intent != AuraCommandIntent.OPEN_APP) {
            return null
        }

        val patternText = pattern.normalizedPattern.trim()
        val placeholder = "{app}"
        if (!patternText.contains(placeholder)) {
            return null
        }

        val beforeApp = patternText.substringBefore(placeholder).trim()
        val afterApp = patternText.substringAfter(placeholder).trim()

        if (beforeApp.isNotEmpty() && !command.startsWith(beforeApp)) {
            return null
        }

        if (afterApp.isNotEmpty() && !command.endsWith(afterApp)) {
            return null
        }

        val appStart = if (beforeApp.isEmpty()) 0 else beforeApp.length
        val appEnd = if (afterApp.isEmpty()) command.length else command.length - afterApp.length

        if (appEnd <= appStart) {
            return null
        }

        val targetText = command.substring(appStart, appEnd).trim()
        val app = findApp(installedApps, targetText)
            ?: return null

        return CommandUnderstanding(
            intent = pattern.intent,
            target = app.name,
            confidence = (pattern.confidence.coerceIn(0.0f, 1.0f) + 0.08f).coerceAtMost(0.99f),
            source = CommandUnderstandingSource.LEARNED_PATTERN,
            learnedPattern = pattern.normalizedPattern
        )
    }
}

data class LearnedPattern(
    val normalizedPattern: String,
    val intent: AuraCommandIntent,
    val targetSlot: String? = null,
    val confidence: Float,
    val usageCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = createdAt
)

fun createOpenAppLearningPattern(
    command: String,
    targetCandidates: List<String>
): String {
    var normalized = normalizeLearningText(command)
    if (normalized.isBlank()) {
        return ""
    }

    val appNames =
        targetCandidates
            .map(::normalizeLearningText)
            .filter { it.isNotBlank() }
            .distinct()
            .sortedByDescending { it.length }

    for (candidate in appNames) {
        normalized = normalized.replace(candidate, "{app}")
    }

    normalized =
        normalized
            .replace("{ app }", "{app}")
            .replace("\\s+".toRegex(), " ")
            .trim()

    if (normalized.contains("{app}")) {
        return normalized
    }

    val fallback =
        when {
            normalized.contains("open") -> normalized.replaceFirst("open ", "open {app} ")
            normalized.contains("buksan") -> normalized.replaceFirst("buksan ", "buksan {app} ")
            normalized.contains("pasok") -> normalized.replaceFirst("pasok ", "pasok {app} ")
            normalized.contains("sakay") -> normalized.replaceFirst("sakay ", "sakay {app} ")
            normalized.contains("lipat") -> normalized.replaceFirst("lipat ", "lipat {app} ")
            else -> normalized
        }

    return fallback
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun normalizePattern(pattern: String): String {
    var normalized = normalizeLearningText(pattern)
    normalized = normalized
        .replace("{ app }", "{app}")
        .replace("{ app}", "{app}")
        .replace("{app }", "{app}")
        .replace("\\s+".toRegex(), " ")
        .trim()

    return normalized
}

private fun minConfidence(
    existing: Float,
    incoming: Float
): Float =
    ((existing + incoming) / 2f).coerceIn(0.0f, 1.0f)

private fun normalizeLearningText(text: String): String {
    var normalized = text
        .lowercase(Locale.US)
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9{} ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    val assistantPrefixes =
        listOf(
            "hey aura ",
            "hi aura ",
            "hello aura ",
            "aura "
        )

    assistantPrefixes.firstOrNull { normalized.startsWith(it) }
        ?.let { normalized = normalized.removePrefix(it).trim() }

    return normalized
}
