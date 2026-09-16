package com.marksilla.auraagent

import java.util.Locale
import kotlin.math.min

class LocalCommandAi {
    fun understand(
        command: String,
        installedApps: List<InstalledApp>
    ): CommandUnderstanding {
        val text = normalizeLearningText(command)

        if (text.isBlank()) {
            return unknown(0.0f)
        }

        val target =
            findMentionedApp(
                text = text,
                installedApps = installedApps
            )

        if (target == null) {
            return unknown(0.24f)
        }

        val actionScore = openActionScore(text)
        val destinationScore =
            if (destinationSignals.any { text.containsWordOrPhrase(it) }) {
                0.10f
            } else {
                0.0f
            }
        val appScore = 0.48f
        val politeScore =
            if (politeSignals.any { text.containsWordOrPhrase(it) }) {
                0.04f
            } else {
                0.0f
            }

        val confidence =
            min(
                0.98f,
                appScore +
                    actionScore +
                    destinationScore +
                    politeScore
            )

        return if (confidence < 0.50f) {
            unknown(confidence)
        } else {
            CommandUnderstanding(
                intent = AuraCommandIntent.OPEN_APP,
                target = target.detectedText,
                confidence = confidence,
                source = CommandUnderstandingSource.LOCAL_AI,
                learnedPattern =
                    createOpenAppLearningPattern(
                        command = command,
                        targetCandidates =
                            listOf(
                                target.detectedText,
                                target.app.name
                            )
                    )
            )
        }
    }

    private fun unknown(
        confidence: Float
    ): CommandUnderstanding =
        CommandUnderstanding(
            intent = AuraCommandIntent.UNKNOWN,
            target = null,
            confidence = confidence.coerceIn(0.0f, 0.49f),
            source = CommandUnderstandingSource.UNKNOWN
        )
}

private data class DetectedAppTarget(
    val app: InstalledApp,
    val detectedText: String
)

private fun findMentionedApp(
    text: String,
    installedApps: List<InstalledApp>
): DetectedAppTarget? {
    val candidates =
        installedApps
            .flatMap { app ->
                appNameCandidates(app.name).map { candidate ->
                    app to candidate
                }
            }
            .distinctBy {
                it.first.packageName to it.second
            }
            .sortedByDescending {
                it.second.length
            }

    for ((app, candidate) in candidates) {
        if (text.containsWordOrPhrase(candidate)) {
            return DetectedAppTarget(
                app = app,
                detectedText = candidate
            )
        }
    }

    return null
}

private fun appNameCandidates(appName: String): List<String> {
    val normalized =
        normalizeLearningText(appName)
    val compact =
        normalized.replace(" ", "")
    val aliases =
        when (compact) {
            "facebook" ->
                listOf(
                    "facebook",
                    "face book",
                    "fb"
                )

            "messenger" ->
                listOf(
                    "messenger",
                    "messenger app",
                    "msg",
                    "msgs"
                )

            "youtube" ->
                listOf(
                    "youtube",
                    "you tube",
                    "yt"
                )

            "instagram" ->
                listOf(
                    "instagram",
                    "insta",
                    "ig"
                )

            "whatsapp" ->
                listOf(
                    "whatsapp",
                    "wa",
                    "wapp"
                )

            "discord" ->
                listOf(
                    "discord",
                    "dc"
                )

            "chrome" ->
                listOf(
                    "chrome",
                    "google chrome"
                )

            "gmail" ->
                listOf(
                    "gmail",
                    "google mail"
                )

            else ->
                emptyList()
        }

    return (listOf(normalized) + aliases)
        .filter {
            it.isNotBlank()
        }
        .distinct()
}

private fun openActionScore(text: String): Float {
    val highConfidenceSignals =
        listOf(
            "open",
            "launch",
            "start",
            "run",
            "buksan",
            "i open",
            "iopen",
            "paki open",
            "paki buksan",
            "please open",
            "can you open",
            "could you open",
            "let s open",
            "lets open",
            "let us open",
            "pwede bang buksan",
            "pwede mo bang buksan",
            "please launch",
            "can you launch",
            "could you launch"
        )

    if (highConfidenceSignals.any { text.containsWordOrPhrase(it) }) {
        return 0.38f
    }

    val navigationSignals =
        listOf(
            "go",
            "go to",
            "lets go to",
            "let s go to",
            "punta",
            "puntahan",
            "dalhin",
            "take",
            "bring",
            "pasok",
            "pasok tayo sa",
            "sakay",
            "sakay tayo sa",
            "lipat",
            "lipat tayo sa",
            "tungo",
            "navigate",
            "open mo",
            "buksan mo",
            "i open mo"
        )

    if (navigationSignals.any { text.containsWordOrPhrase(it) }) {
        return 0.36f
    }

    return 0.05f
}

private fun String.containsWordOrPhrase(
    phrase: String
): Boolean {
    val normalized =
        normalizeLearningText(phrase)

    if (normalized.isBlank()) {
        return false
    }

    return Regex(
        "(^|\\s)${Regex.escape(normalized)}($|\\s)"
    ).containsMatchIn(this)
}

private val destinationSignals =
    listOf(
        "to",
        "into",
        "sa",
        "kay",
        "papunta",
        "inside"
    )

private val politeSignals =
    listOf(
        "could you",
        "can you",
        "please",
        "paki",
        "pwede",
        "maaari"
    )

fun normalizeLearningText(text: String): String {
    var normalized =
        text
            .lowercase(Locale.US)
            .replace("aura", " aura ")
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    val assistantPrefixes =
        listOf(
            "hey aura ",
            "hi aura ",
            "hello aura ",
            "aura "
        )

    assistantPrefixes.firstOrNull {
        normalized.startsWith(it)
    }?.let {
        normalized =
            normalized
                .removePrefix(it)
                .trim()
    }

    return normalized
}
