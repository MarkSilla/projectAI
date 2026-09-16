package com.marksilla.auraagent

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
    private val localAi: LocalCommandAi = LocalCommandAi()
) {
    fun understand(
        command: String,
        installedApps: List<InstalledApp>
    ): CommandUnderstanding {
        val parserTarget =
            runCatching {
                extractOpenCommand(command)
            }.getOrNull()

        if (!parserTarget.isNullOrBlank()) {
            return CommandUnderstanding(
                intent = AuraCommandIntent.OPEN_APP,
                target = parserTarget,
                confidence = 0.99f,
                source = CommandUnderstandingSource.FAST_PARSER
            )
        }

        learningEngine.match(
            command = command,
            installedApps = installedApps
        )?.let {
            return it
        }

        return runCatching {
            localAi.understand(
                command = command,
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
