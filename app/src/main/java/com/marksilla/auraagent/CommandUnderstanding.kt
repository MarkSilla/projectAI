package com.marksilla.auraagent

import java.util.Locale

enum class AuraCommandIntent {
    OPEN_APP,
    UNKNOWN
}

enum class CommandUnderstandingSource {
    FAST_PARSER,
    LEARNED_PATTERN,
    LOCAL_AI,
    UNKNOWN
}

enum class CommandAction {
    EXECUTE,
    CONFIRM,
    UNKNOWN
}

data class CommandUnderstanding(
    val intent: AuraCommandIntent,
    val target: String?,
    val confidence: Float,
    val source: CommandUnderstandingSource,
    val learnedPattern: String? = null
)

const val AUTO_EXECUTE_OPEN_APP_CONFIDENCE = 0.84f
const val CONFIRM_OPEN_APP_CONFIDENCE = 0.65f
const val LEARN_OPEN_APP_CONFIDENCE = 0.82f

class AuraCommandUnderstanding(
    private val learningEngine: CommandLearningEngine = CommandLearningEngine(),
    private val localAi: LocalCommandAi = LocalCommandAi(),
    private val memory: AuraMemoryEngine? = null
) {
    fun understand(
        command: String,
        installedApps: List<InstalledApp>
    ): CommandUnderstanding {
        val rewrittenCommand = memory?.let { resolveLearnedCommandAliases(command, it) } ?: command
        val parserTarget =
            runCatching {
                extractOpenCommand(rewrittenCommand)
            }.getOrNull()

        if (!parserTarget.isNullOrBlank()) {
            val canonicalTarget = installedApps
                .firstOrNull { app ->
                    val normalizedAppName = app.name.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
                    val normalizedParserTarget = parserTarget.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
                    normalizedAppName == normalizedParserTarget
                }
                ?.name
                ?: parserTarget

            return CommandUnderstanding(
                intent = AuraCommandIntent.OPEN_APP,
                target = canonicalTarget,
                confidence = 0.99f,
                source = CommandUnderstandingSource.FAST_PARSER
            )
        }

        learningEngine.match(
            command = rewrittenCommand,
            installedApps = installedApps
        )?.let {
            return it
        }

        return runCatching {
            localAi.understand(
                command = rewrittenCommand,
                installedApps = installedApps
            )
        }.getOrElse {
            CommandUnderstanding(
                intent = AuraCommandIntent.UNKNOWN,
                target = null,
                confidence = 0.0f,
                source = CommandUnderstandingSource.UNKNOWN
            )
        }
    }

    fun learnSuccessfulCommand(
        command: String,
        installedApps: List<InstalledApp>,
        understanding: CommandUnderstanding
    ) {
        if (understanding.intent == AuraCommandIntent.UNKNOWN) {
            return
        }

        if (understanding.confidence < LEARN_OPEN_APP_CONFIDENCE) {
            return
        }

        learningEngine.learn(
            command = command,
            installedApps = installedApps,
            understanding = understanding
        )
    }
}

fun decideOpenAppAction(
    understanding: CommandUnderstanding
): CommandAction =
    when {
        understanding.intent != AuraCommandIntent.OPEN_APP || understanding.target.isNullOrBlank() ->
            CommandAction.UNKNOWN

        understanding.confidence >= AUTO_EXECUTE_OPEN_APP_CONFIDENCE ->
            CommandAction.EXECUTE

        understanding.confidence >= CONFIRM_OPEN_APP_CONFIDENCE ->
            CommandAction.CONFIRM

        else ->
            CommandAction.UNKNOWN
    }

fun shouldAutoExecuteOpenApp(
    understanding: CommandUnderstanding
): Boolean =
    decideOpenAppAction(understanding) == CommandAction.EXECUTE

fun shouldConfirmOpenApp(
    understanding: CommandUnderstanding
): Boolean =
    decideOpenAppAction(understanding) == CommandAction.CONFIRM

private fun normalizeLearnedAction(value: String): String =
    when {
        value.equals("OPEN", ignoreCase = true) || value.equals("OPEN_APP", ignoreCase = true) -> "open"
        value.equals("NAVIGATE", ignoreCase = true) || value.equals("GO_TO", ignoreCase = true) -> "go to"
        else -> value.trim()
    }

fun resolveLearnedCommandAliases(
    command: String,
    memory: AuraMemoryEngine
): String {
    val input = command.trim()
    if (input.isBlank()) {
        return input
    }

    val aliasEntries = memory
        .searchMemory(input)
        .filter { entry ->
            val hasAliasValue = entry.value.trim().isNotBlank()
            val hasMeaning = entry.matchText?.trim().isNullOrBlank().not()
            hasAliasValue && hasMeaning && (
                entry.type.contains("learned") ||
                    entry.type.contains("application_alias") ||
                    entry.category.contains("alias") ||
                    entry.category.contains("learned")
                )
        }
        .sortedByDescending { maxOf(it.value.length, it.matchText?.length ?: 0) }

    var rewritten = input
    for (entry in aliasEntries) {
        val alias = when {
            entry.type.contains("application_alias") || entry.category.contains("alias") -> entry.value.trim()
            entry.matchText != null -> entry.matchText!!.trim()
            else -> entry.value.trim()
        }

        val canonical = when {
            entry.type.contains("application_alias") || entry.category.contains("alias") ->
                normalizeLearnedAction(entry.matchText?.trim().orEmpty())
            else ->
                normalizeLearnedAction(entry.value.trim())
        }

        val aliasLower = alias.lowercase(Locale.US)
        if (aliasLower.isBlank() || canonical.isBlank()) continue

        val aliasPattern = Regex("(?i)(^|\\s)${Regex.escape(aliasLower)}($|\\s)")
        rewritten = aliasPattern.replace(rewritten) { match ->
            val leading = match.groupValues[1]
            val trailing = match.groupValues[2]
            "$leading$canonical$trailing"
        }
    }

    return rewritten.replace(Regex("\\s+"), " ").trim()
}

fun isAffirmativeConfirmation(command: String): Boolean {
    val text = normalizeLearningText(command)

    return text in setOf(
        "yes",
        "yeah",
        "yep",
        "ok",
        "okay",
        "sure",
        "go",
        "go ahead",
        "confirm",
        "oo",
        "opo",
        "sige",
        "tuloy",
        "ituloy"
    )
}

fun isNegativeConfirmation(command: String): Boolean {
    val text = normalizeLearningText(command)

    return text in setOf(
        "no",
        "nope",
        "cancel",
        "stop",
        "never mind",
        "nevermind",
        "hindi",
        "wag",
        "huwag",
        "cancel mo"
    )
}
