package com.marksilla.auraagent

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_REVIEWER =
            "com.marksilla.auraagent.extra.OPEN_REVIEWER"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AuraApp(
                context = this@MainActivity,
                openReviewerOnStart =
                    intent?.getBooleanExtra(
                        EXTRA_OPEN_REVIEWER,
                        false
                    ) == true
            )
        }
    }
}

private enum class AuraScreen(
    val title: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    HOME(
        title = "AURA Agent",
        label = "Home",
        icon = Icons.Filled.Home
    ),
    REVIEWER(
        title = "Document Reviewer",
        label = "Reviewer",
        icon = Icons.Filled.Article
    ),
    SETTINGS(
        title = "Settings",
        label = "Settings",
        icon = Icons.Filled.Settings
    )
}

private enum class ChatRole {
    USER,
    ASSISTANT
}

private data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String
)

private fun generateAssistantReply(
    prompt: String,
    recentContext: List<String> = emptyList(),
    personalMemory: List<String> = emptyList(),
    userProfile: UserProfile = UserProfile()
): String {
    val trimmed = prompt.trim()
    if (trimmed.isBlank()) {
        return "I’m ready when you are."
    }

    val normalized = trimmed.lowercase(Locale.US)
    val lastContext =
        recentContext
            .map { it.lowercase(Locale.US) }
            .filter { it.isNotBlank() }
            .lastOrNull()
    val repeatedTarget =
        personalMemory
            .map { it.lowercase(Locale.US) }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

    val profileHint =
        userProfile.preferredApps
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(" and ")
            .ifBlank { null }

    val habitReminder =
        if (repeatedTarget != null && repeatedTarget.contains("facebook") && normalized.contains("facebook")) {
            "You often open Facebook, so I can keep it ready as one of your usual quick actions."
        } else if (repeatedTarget != null && repeatedTarget.contains("messenger") && normalized.contains("messenger")) {
            "You usually open Messenger, so I can treat that as part of your routine."
        } else if (repeatedTarget != null && repeatedTarget.contains("settings") && normalized.contains("settings")) {
            "You often check settings, so I can handle that quickly for you."
        } else if (profileHint != null && (normalized.contains("open") || normalized.contains("launch") || normalized.contains("buksan") || normalized.contains("pasok") || normalized.contains("sakay") || normalized.contains("lipat"))) {
            "I know you usually prefer ${profileHint}, so I can keep that workflow ready."
        } else {
            null
        }

    val quickReply =
        when {
            normalized.contains("morning") || normalized.contains("workday") || normalized.contains("focus mode") || normalized.contains("routine") ->
                "I can set up a focus-friendly morning routine for you: open your priority apps, reduce distractions, and keep your first tasks ready."

            normalized.contains("open") || normalized.contains("launch") || normalized.contains("buksan") || normalized.contains("pasok") || normalized.contains("sakay") || normalized.contains("lipat") ->
                if (habitReminder != null) {
                    "I can open that for you. ${habitReminder}"
                } else if (lastContext != null && (lastContext.contains("facebook") || lastContext.contains("messenger") || lastContext.contains("settings"))) {
                    "I can open that for you. Based on your recent activity, you were looking at ${lastContext.replace(Regex("^open "), "").trim()} earlier."
                } else {
                    "I can open that app for you and keep the action smooth and direct."
                }

            normalized.contains("review") || normalized.contains("summarize") || normalized.contains("document") || normalized.contains("analyze") || normalized.contains("read") ->
                "I can review the document and give you a concise, professional summary with key findings and action points."

            normalized.contains("settings") || normalized.contains("brightness") || normalized.contains("volume") || normalized.contains("wifi") || normalized.contains("bluetooth") || normalized.contains("alarm") ->
                "I can handle that system setting for you and keep the workflow simple and controlled."

            normalized.contains("chat") || normalized.contains("assistant") || normalized.contains("hello") || normalized.contains("hi") || normalized.contains("hey") ->
                "I’m here to help. You can ask me to open apps, review files, or adjust settings in a professional workflow."

            normalized.contains("favorite") || normalized.contains("save") || normalized.contains("remember") ->
                "I can save that as a preferred action and use it again the next time you ask for it."

            else ->
                "I understand your request and can help you act on it quickly with a clean, focused workflow."
        }

    return quickReply
}

private data class FavoriteAction(
    val label: String,
    val command: String
)

private data class RoutinePreset(
    val title: String,
    val description: String,
    val appTargets: List<String>
)

private data class LearnedCommandUsage(
    val command: String,
    val count: Int
)

private data class UserProfile(
    val preferredApps: List<String> = emptyList(),
    val routineHints: List<String> = emptyList()
)

private const val AURA_PREFERENCES = "aura_preferences"
private const val KEY_FAVORITES = "favorite_actions"
private const val KEY_COMMAND_HISTORY = "command_history"
private const val KEY_CHAT_MEMORY = "chat_memory"
private const val KEY_USER_PROFILE = "user_profile"

private fun loadFavoriteActions(context: Context): List<FavoriteAction> {
    val prefs = context.getSharedPreferences(AURA_PREFERENCES, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_FAVORITES, "") ?: ""
    if (raw.isBlank()) {
        return listOf(
            FavoriteAction("Open Facebook", "Open Facebook"),
            FavoriteAction("Open Messenger", "Open Messenger"),
            FavoriteAction("Open Settings", "Open Settings")
        )
    }

    return runCatching {
        raw
            .split("\n")
            .filter { it.contains("::") }
            .mapNotNull { entry ->
                val parts = entry.split("::", limit = 2)
                if (parts.size != 2) {
                    null
                } else {
                    FavoriteAction(
                        label = parts[0].trim(),
                        command = parts[1].trim()
                    )
                }
            }
            .distinctBy { it.command.lowercase(Locale.US) }
    }.getOrElse {
        listOf(
            FavoriteAction("Open Facebook", "Open Facebook"),
            FavoriteAction("Open Messenger", "Open Messenger"),
            FavoriteAction("Open Settings", "Open Settings")
        )
    }
}

private fun saveFavoriteActions(context: Context, actions: List<FavoriteAction>) {
    val prefs = context.getSharedPreferences(AURA_PREFERENCES, Context.MODE_PRIVATE)
    val serialized =
        actions
            .distinctBy { it.command.lowercase(Locale.US) }
            .joinToString("\n") { "${it.label}::${it.command}" }
    prefs.edit().putString(KEY_FAVORITES, serialized).apply()
}

private fun loadCommandHistory(context: Context): List<LearnedCommandUsage> {
    val prefs = context.getSharedPreferences(AURA_PREFERENCES, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_COMMAND_HISTORY, "") ?: ""
    if (raw.isBlank()) {
        return emptyList()
    }

    return runCatching {
        raw
            .split("\n")
            .filter { it.contains("::") }
            .mapNotNull { entry ->
                val parts = entry.split("::", limit = 2)
                if (parts.size != 2) {
                    null
                } else {
                    val command = parts[0].trim()
                    val count = parts[1].trim().toIntOrNull() ?: 1
                    if (command.isBlank()) null else LearnedCommandUsage(command, count)
                }
            }
    }.getOrElse { emptyList() }
}

private fun saveCommandHistory(context: Context, history: List<LearnedCommandUsage>) {
    val prefs = context.getSharedPreferences(AURA_PREFERENCES, Context.MODE_PRIVATE)
    val serialized =
        history
            .sortedByDescending { it.count }
            .joinToString("\n") { "${it.command}::${it.count}" }
    prefs.edit().putString(KEY_COMMAND_HISTORY, serialized).apply()
}

private fun loadChatMemory(context: Context): List<String> {
    val prefs = context.getSharedPreferences(AURA_PREFERENCES, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_CHAT_MEMORY, "") ?: ""
    if (raw.isBlank()) {
        return emptyList()
    }

    return raw.split("\n").filter { it.isNotBlank() }.takeLast(20)
}

private fun saveChatMemory(context: Context, memory: List<String>) {
    val prefs = context.getSharedPreferences(AURA_PREFERENCES, Context.MODE_PRIVATE)
    val serialized = memory.filter { it.isNotBlank() }.takeLast(20).joinToString("\n")
    prefs.edit().putString(KEY_CHAT_MEMORY, serialized).apply()
}

private fun loadUserProfile(context: Context): UserProfile {
    val prefs = context.getSharedPreferences(AURA_PREFERENCES, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_USER_PROFILE, "") ?: ""
    if (raw.isBlank()) {
        return UserProfile()
    }

    return runCatching {
        val lines = raw.split("\n").filter { it.isNotBlank() }
        val preferredApps = lines.firstOrNull { it.startsWith("preferred_apps::") }
            ?.removePrefix("preferred_apps::")
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val routineHints = lines.firstOrNull { it.startsWith("routine::") }
            ?.removePrefix("routine::")
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        UserProfile(
            preferredApps = preferredApps,
            routineHints = routineHints
        )
    }.getOrElse { UserProfile() }
}

private fun saveUserProfile(context: Context, profile: UserProfile) {
    val prefs = context.getSharedPreferences(AURA_PREFERENCES, Context.MODE_PRIVATE)
    val serialized = buildList {
        if (profile.preferredApps.isNotEmpty()) {
            add("preferred_apps::${profile.preferredApps.joinToString("|")}")
        }
        if (profile.routineHints.isNotEmpty()) {
            add("routine::${profile.routineHints.joinToString("|")}")
        }
    }.joinToString("\n")
    prefs.edit().putString(KEY_USER_PROFILE, serialized).apply()
}

private fun inferUserProfile(
    history: List<LearnedCommandUsage>,
    memory: List<String>,
    favorites: List<FavoriteAction>
): UserProfile {
    val tracked = mutableListOf<String>()
    tracked += favorites.map { it.label }
    tracked += history.map { it.command }
    tracked += memory

    val detected =
        tracked
            .map { it.lowercase(Locale.US) }
            .filter { it.isNotBlank() }
            .fold(mutableListOf<String>()) { acc, item ->
                when {
                    item.contains("facebook") -> acc += "Facebook"
                    item.contains("messenger") -> acc += "Messenger"
                    item.contains("settings") -> acc += "Settings"
                    item.contains("brightness") -> acc += "Brightness"
                    item.contains("volume") -> acc += "Volume"
                    item.contains("review") || item.contains("document") -> acc += "Document Review"
                    else -> Unit
                }
                acc
            }
            .distinct()
            .take(3)

    val routineHints =
        if (detected.isNotEmpty()) {
            detected
        } else {
            listOf("Open Facebook", "Open Settings")
        }

    return UserProfile(
        preferredApps = detected,
        routineHints = routineHints
    )
}

private fun mergeConversationMemory(
    memory: List<String>,
    userPrompt: String,
    assistantReply: String
): List<String> {
    val updated =
        memory.toMutableList().apply {
            add(userPrompt.trim())
            add(assistantReply.trim())
        }

    return updated
        .filter { it.isNotBlank() }
        .takeLast(20)
}

private fun mergeCommandHistory(
    history: List<LearnedCommandUsage>,
    command: String
): List<LearnedCommandUsage> {
    val trimmed = command.trim()
    if (trimmed.isBlank()) {
        return history
    }

    val updated =
        history
            .map {
                if (it.command.equals(trimmed, ignoreCase = true)) {
                    it.copy(count = it.count + 1)
                } else {
                    it
                }
            }
            .toMutableList()

    if (updated.none { it.command.equals(trimmed, ignoreCase = true) }) {
        updated += LearnedCommandUsage(trimmed, 1)
    }

    return updated
        .sortedByDescending { it.count }
        .take(10)
}

private fun buildHabitSuggestions(
    memory: List<String>,
    history: List<LearnedCommandUsage>
): List<String> {
    val routineCommands =
        memory
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.lowercase(Locale.US) }
            .filter { text ->
                text.contains("open") ||
                    text.contains("buksan") ||
                    text.contains("settings") ||
                    text.contains("brightness") ||
                    text.contains("volume") ||
                    text.contains("review")
            }
            .groupBy { it }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(3)

    val learned =
        history
            .sortedByDescending { it.count }
            .map { it.command }
            .filter { it.isNotBlank() }

    return (routineCommands + learned)
        .distinct()
        .take(5)
}

private fun buildSmartSuggestions(
    history: List<LearnedCommandUsage>,
    memory: List<String>,
    fallback: List<String>
): List<String> {
    val learned =
        history
            .sortedByDescending { it.count }
            .map { it.command }
            .filter { it.isNotBlank() }

    val habits =
        buildHabitSuggestions(
            memory = memory,
            history = history
        )

    return (habits + learned + fallback)
        .distinct()
        .take(5)
}

private fun defaultRoutinePresets(): List<RoutinePreset> =
    listOf(
        RoutinePreset(
            title = "Morning",
            description = "Priority start-up",
            appTargets = listOf("Facebook", "Messenger", "Settings")
        ),
        RoutinePreset(
            title = "Focus",
            description = "Deep work setup",
            appTargets = listOf("Chrome", "Settings")
        ),
        RoutinePreset(
            title = "Evening",
            description = "Wind down mode",
            appTargets = listOf("YouTube", "Settings")
        )
    )

private data class PendingConfirmation(
    val target: String,
    val originalCommand: String
)

@Composable
fun AuraApp(
    context: Context,
    openReviewerOnStart: Boolean = false
) {
    var currentScreen by remember {
        mutableStateOf(
            if (openReviewerOnStart) {
                AuraScreen.REVIEWER
            } else {
                AuraScreen.HOME
            }
        )
    }
    var command by remember {
        mutableStateOf("")
    }
    var dark by remember {
        mutableStateOf(true)
    }
    var listening by remember {
        mutableStateOf(false)
    }
    var auraEnabled by remember {
        mutableStateOf(
            AuraServiceState.isActive(context)
        )
    }
    var auraListening by remember {
        mutableStateOf(
            AuraServiceState.isListening(context)
        )
    }
    var auraPaused by remember {
        mutableStateOf(
            AuraServiceState.isPaused(context)
        )
    }
    var status by remember {
        mutableStateOf(
            AuraServiceState.lastStatus(context)
        )
    }
    var recent by remember {
        mutableStateOf(
            listOf(
                "Open Downloads",
                "Find screenshots",
                "Open Settings"
            )
        )
    }
    var chatInput by remember {
        mutableStateOf("")
    }
    val commandUnderstandingEngine = remember { AuraCommandUnderstanding() }
    var commandHistory by remember {
        mutableStateOf(loadCommandHistory(context))
    }
    var favoriteActions by remember {
        mutableStateOf(loadFavoriteActions(context))
    }
    var routinePresets by remember {
        mutableStateOf(defaultRoutinePresets())
    }
    var pendingConfirmation by remember {
        mutableStateOf<PendingConfirmation?>(null)
    }
    var chatMessages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    id = 1L,
                    role = ChatRole.ASSISTANT,
                    text = "Hello. I’m AURA. Ask me to open an app, review a file, or change a system setting."
                )
            )
        )
    }
    var conversationMemory by remember {
        mutableStateOf(loadChatMemory(context))
    }
    var userProfile by remember {
        mutableStateOf(loadUserProfile(context))
    }
    val recentChatContext =
        remember(chatMessages.size, conversationMemory.size) {
            (conversationMemory + chatMessages.map { it.text }).takeLast(12)
        }
    val smartSuggestions =
        buildSmartSuggestions(
            history = commandHistory,
            memory = conversationMemory,
            fallback = listOf(
                "Open Facebook",
                "Open Messenger",
                "Review document",
                "Open Settings",
                "Brightness low"
            )
        )
    val derivedProfile =
        remember(commandHistory, conversationMemory, favoriteActions) {
            inferUserProfile(
                history = commandHistory,
                memory = conversationMemory,
                favorites = favoriteActions
            )
        }

    LaunchedEffect(derivedProfile) {
        userProfile = derivedProfile
        saveUserProfile(context, derivedProfile)
    }
    var documentName by remember {
        mutableStateOf<String?>(null)
    }
    var documentSummary by remember {
        mutableStateOf<DocumentSummary?>(null)
    }
    var documentError by remember {
        mutableStateOf<String?>(null)
    }
    var reviewingDocument by remember {
        mutableStateOf(false)
    }
    var installedApps by remember {
        mutableStateOf(
            getInstalledApps(context)
        )
    }
    var pendingDeviceCommand by remember {
        mutableStateOf<DeviceCommand?>(null)
    }

    fun overlaySettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )

    fun startAuraService() {
        ContextCompat.startForegroundService(
            context,
            Intent(
                context,
                AuraService::class.java
            ).apply {
                action = AuraService.ACTION_START
            }
        )

        auraEnabled = true
        auraPaused = false
        status = "Starting AURA..."
    }

    fun pauseAuraService() {
        context.startService(
            Intent(
                context,
                AuraService::class.java
            ).apply {
                action = AuraService.ACTION_PAUSE
            }
        )

        auraEnabled = true
        auraPaused = true
        auraListening = false
        listening = false
        status = "AURA paused"
    }

    fun resumeAuraService() {
        context.startService(
            Intent(
                context,
                AuraService::class.java
            ).apply {
                action = AuraService.ACTION_RESUME
            }
        )

        auraEnabled = true
        auraPaused = false
        status = "Resuming AURA..."
    }

    fun stopAuraService() {
        context.stopService(
            Intent(
                context,
                AuraService::class.java
            )
        )

        auraEnabled = false
        auraPaused = false
        auraListening = false
        listening = false
        status = "AURA stopped"
    }

    val overlaySettingsLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            if (Settings.canDrawOverlays(context)) {
                startAuraService()
            } else {
                auraEnabled = false
                status = "Overlay permission is required"
            }
        }

    val voiceLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val text =
                result.data
                    ?.getStringArrayListExtra(
                        "android.speech.extra.RESULTS"
                    )
                    ?.firstOrNull()

            if (!text.isNullOrBlank()) {
                command = text
                recent = listOf(text) + recent.take(4)
                status = "Command received"
            }

            listening = false
        }

    fun startManualVoiceInput() {
        val intent =
            Intent(
                "android.speech.action.RECOGNIZE_SPEECH"
            ).apply {
                putExtra(
                    "android.speech.extra.LANGUAGE_MODEL",
                    "free_form"
                )
                putExtra(
                    "android.speech.extra.LANGUAGE",
                    Locale.getDefault()
                )
            }

        listening = true
        voiceLauncher.launch(intent)
    }

    val auraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                auraEnabled = false
                status = "Microphone permission is required"
                return@rememberLauncherForActivityResult
            }

            if (Settings.canDrawOverlays(context)) {
                startAuraService()
            } else {
                status = "Overlay permission is required"
                overlaySettingsLauncher.launch(
                    overlaySettingsIntent()
                )
            }
        }

    val manualVoicePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startManualVoiceInput()
            } else {
                listening = false
                status = "Microphone permission is required"
            }
        }

    val writeSettingsLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            val commandToRetry = pendingDeviceCommand
            pendingDeviceCommand = null

            if (
                commandToRetry != null &&
                Settings.System.canWrite(context)
            ) {
                val result =
                    performDeviceCommand(
                        context = context,
                        command = commandToRetry
                    )

                status = result.status
                recent =
                    listOf(result.recent) +
                        recent.take(4)
            } else {
                status = "Modify system settings is required"
            }
        }

    fun runDeviceCommand(deviceCommand: DeviceCommand) {
        val result =
            performDeviceCommand(
                context = context,
                command = deviceCommand
            )

        status = result.status
        recent =
            listOf(result.recent) +
                recent.take(4)

        if (result.needsWriteSettingsPermission) {
            pendingDeviceCommand = deviceCommand
            writeSettingsLauncher.launch(
                writeSettingsPermissionIntent(context)
            )
        }
    }

    fun runRoutinePreset(preset: RoutinePreset) {
        val openedTargets =
            preset.appTargets.mapNotNull { target ->
                val app = findApp(installedApps, target)
                if (app != null && openApp(context, app)) {
                    app.name
                } else {
                    null
                }
            }

        if (openedTargets.isNotEmpty()) {
            status = "Started ${preset.title} routine"
            recent = listOf("${preset.title} routine") + recent.take(4)
            val reply =
                "Started the ${preset.title.lowercase(Locale.US)} routine. I opened ${openedTargets.joinToString(", ")}."
            conversationMemory =
                mergeConversationMemory(
                    memory = conversationMemory,
                    userPrompt = "Start ${preset.title} routine",
                    assistantReply = reply
                )
            saveChatMemory(context, conversationMemory)
            chatMessages +=
                ChatMessage(
                    id = System.currentTimeMillis(),
                    role = ChatRole.USER,
                    text = "Start ${preset.title} routine"
                ) + ChatMessage(
                    id = System.currentTimeMillis() + 1L,
                    role = ChatRole.ASSISTANT,
                    text = reply
                )
        } else {
            status = "Unable to start ${preset.title} routine"
        }
    }

    val documentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            currentScreen = AuraScreen.REVIEWER
            reviewingDocument = true
            documentError = null

            runCatching {
                val name =
                    getDocumentDisplayName(
                        context = context,
                        uri = uri
                    )

                val documentText =
                    readDocumentTextFromUri(
                        context = context,
                        uri = uri,
                        displayName = name
                    )

                Triple(
                    name,
                    documentText.sourceType,
                    summarizeDocumentText(
                        title = name,
                        rawText = documentText.text
                    )
                )
            }
                .onSuccess { result ->
                    val (name, sourceType, summary) = result
                    documentName = name
                    documentSummary = summary

                    if (summary == null) {
                        documentError =
                            "Not enough readable text found in this $sourceType."
                        status = "Document could not be summarized"
                    } else {
                        documentError = null
                        status = "$sourceType summarized"
                        recent =
                            listOf(
                                "Reviewed $name"
                            ) + recent.take(4)
                    }
                }
                .onFailure {
                    documentSummary = null
                    documentError =
                        "Couldn't read this document."
                    status = "Document read failed"
                }

            reviewingDocument = false
        }

    val saveReviewerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/markdown")
        ) { uri ->
            val summary = documentSummary

            if (uri == null || summary == null) {
                return@rememberLauncherForActivityResult
            }

            val saved =
                runCatching {
                    context.contentResolver
                        .openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use { writer ->
                            writer.write(
                                formatReviewerMarkdown(summary)
                            )
                        }
                        ?: error("No output stream")
                }.isSuccess

            if (saved) {
                status = "Reviewer saved"
                recent =
                    listOf("Saved reviewer") +
                        recent.take(4)
            } else {
                status = "Reviewer save failed"
            }
        }

    fun enableAura() {
        if (
            context.checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            auraPermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
            return
        }

        if (!Settings.canDrawOverlays(context)) {
            status = "Overlay permission is required"
            overlaySettingsLauncher.launch(
                overlaySettingsIntent()
            )
            return
        }

        startAuraService()
    }

    fun openDocumentReviewer() {
        currentScreen = AuraScreen.REVIEWER
        documentLauncher.launch(
            arrayOf(
                "text/*",
                "application/json",
                "application/xml",
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/octet-stream"
            )
        )
    }

    fun saveReviewer(summary: DocumentSummary) {
        saveReviewerLauncher.launch(
            suggestedReviewerFileName(summary.title)
        )
    }

    fun executeCommand() {
        val input = command.trim()

        if (input.isBlank()) {
            status = "Enter a command"
            return
        }

        val deviceCommand = parseDeviceCommand(input)

        if (deviceCommand != null) {
            runDeviceCommand(deviceCommand)
            command = ""
            return
        }

        if (isDocumentReviewCommand(input)) {
            status = "Choose a document"
            recent =
                listOf("Review document") +
                    recent.take(4)
            command = ""
            openDocumentReviewer()
            return
        }

        val appName = extractOpenCommand(input)

        if (appName != null) {
            val app =
                findApp(
                    installedApps,
                    appName
                )

            if (app != null) {
                val opened =
                    openApp(
                        context,
                        app
                    )

                if (opened) {
                    status = "Opening ${app.name}"
                    recent =
                        listOf(
                            "Open ${app.name}"
                        ) + recent.take(4)
                    command = ""
                } else {
                    status = "Cannot open ${app.name}"
                }
            } else {
                status = "App \"$appName\" was not found"
            }
        } else {
            status = "Try: Open Facebook"
            recent = listOf(input) + recent.take(4)
        }
    }

    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    receiverContext: Context?,
                    intent: Intent?
                ) {
                    if (intent?.action != AuraServiceState.ACTION_STATUS) {
                        return
                    }

                    auraEnabled =
                        intent.getBooleanExtra(
                            AuraServiceState.EXTRA_ACTIVE,
                            auraEnabled
                        )
                    auraListening =
                        intent.getBooleanExtra(
                            AuraServiceState.EXTRA_LISTENING,
                            false
                        )
                    auraPaused =
                        intent.getBooleanExtra(
                            AuraServiceState.EXTRA_PAUSED,
                            false
                        )
                    status =
                        intent.getStringExtra(
                            AuraServiceState.EXTRA_STATUS
                        ) ?: status
                }
            }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AuraServiceState.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        auraEnabled = AuraServiceState.isActive(context)
        auraListening = AuraServiceState.isListening(context)
        auraPaused = AuraServiceState.isPaused(context)
        status = AuraServiceState.lastStatus(context)

        onDispose {
            runCatching {
                context.unregisterReceiver(receiver)
            }
        }
    }

    val bg =
        if (dark) {
            Color(0xFF0B0D12)
        } else {
            Color(0xFFF4F6F8)
        }
    val fg =
        if (dark) {
            Color.White
        } else {
            Color(0xFF111318)
        }
    val card =
        if (dark) {
            Color(0xFF151922)
        } else {
            Color.White
        }

    MaterialTheme(
        colorScheme =
            if (dark) {
                darkColorScheme()
            } else {
                lightColorScheme()
            }
    ) {
        Scaffold(
            containerColor = bg,
            bottomBar = {
                AuraBottomBar(
                    currentScreen = currentScreen,
                    onScreenSelected = {
                        currentScreen = it
                    }
                )
            }
        ) { scaffoldPadding ->
            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding),
                color = bg
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val compact = maxWidth < 420.dp
                    val contentMaxWidth =
                        if (maxWidth < 720.dp) {
                            maxWidth
                        } else {
                            680.dp
                        }
                    val horizontalPadding =
                        if (compact) {
                            14.dp
                        } else {
                            20.dp
                        }
                    val verticalSpacing =
                        if (compact) {
                            14.dp
                        } else {
                            18.dp
                        }
                    val actionHeight =
                        if (compact) {
                            52.dp
                        } else {
                            56.dp
                        }
                    val orbSize =
                        if (compact) {
                            96.dp
                        } else {
                            118.dp
                        }

                    when (currentScreen) {
                        AuraScreen.HOME ->
                            HomeScreen(
                                fg = fg,
                                card = card,
                                contentMaxWidth = contentMaxWidth,
                                horizontalPadding = horizontalPadding,
                                verticalSpacing = verticalSpacing,
                                actionHeight = actionHeight,
                                orbSize = orbSize,
                                listening = listening,
                                auraListening = auraListening,
                                auraEnabled = auraEnabled,
                                auraPaused = auraPaused,
                                status = status,
                                chatInput = chatInput,
                                chatMessages = chatMessages,
                                favoriteActions = favoriteActions,
                                routinePresets = routinePresets,
                                pendingConfirmation = pendingConfirmation,
                                onChatInputChange = {
                                    chatInput = it
                                },
                                onSendChat = {
                                    val input = chatInput.trim()
                                    if (input.isBlank()) {
                                        return@HomeScreen
                                    }

                                    val lowerInput = input.lowercase(Locale.US)
                                    val replyText =
                                        generateAssistantReply(
                                            prompt = input,
                                            recentContext = recentChatContext,
                                            personalMemory = conversationMemory,
                                            userProfile = userProfile
                                        )

                                    if (
                                        lowerInput.contains("remember") ||
                                        lowerInput.contains("save") ||
                                        lowerInput.contains("favorite")
                                    ) {
                                        val label =
                                            input
                                                .replace(Regex("(?i)remember|save|favorite"), "")
                                                .trim()
                                                .ifBlank { "Custom action" }

                                        val favorite = FavoriteAction(label, input)
                                        val merged =
                                            listOf(favorite) + favoriteActions.filter {
                                                it.command.lowercase(Locale.US) != input.lowercase(Locale.US)
                                            }
                                        favoriteActions = merged.take(5)
                                        saveFavoriteActions(context, favoriteActions)
                                        conversationMemory =
                                            mergeConversationMemory(
                                                memory = conversationMemory,
                                                userPrompt = input,
                                                assistantReply = "Saved as a favorite action. I’ll remember that for next time."
                                            )
                                        saveChatMemory(context, conversationMemory)
                                        chatMessages =
                                            chatMessages + ChatMessage(
                                                id = System.currentTimeMillis(),
                                                role = ChatRole.USER,
                                                text = input
                                            ) + ChatMessage(
                                                id = System.currentTimeMillis() + 1L,
                                                role = ChatRole.ASSISTANT,
                                                text = "Saved as a favorite action. I’ll remember that for next time."
                                            )
                                        chatInput = ""
                                        return@HomeScreen
                                    }

                                    if (pendingConfirmation != null &&
                                        (
                                            isAffirmativeConfirmation(input) ||
                                                isNegativeConfirmation(input)
                                        )
                                    ) {
                                        val pending = pendingConfirmation!!
                                        if (isAffirmativeConfirmation(input)) {
                                            val app = findApp(installedApps, pending.target)
                                            if (app != null) {
                                                val opened = openApp(context, app)
                                                status =
                                                    if (opened) {
                                                        "Opening ${app.name}"
                                                    } else {
                                                        "Cannot open ${app.name}"
                                                    }
                                                recent =
                                                    listOf("Open ${app.name}") + recent.take(4)
                                                commandHistory =
                                                    mergeCommandHistory(commandHistory, pending.originalCommand)
                                                saveCommandHistory(context, commandHistory)
                                            }
                                            chatMessages =
                                                chatMessages + ChatMessage(
                                                    id = System.currentTimeMillis(),
                                                    role = ChatRole.USER,
                                                    text = input
                                                ) + ChatMessage(
                                                    id = System.currentTimeMillis() + 1L,
                                                    role = ChatRole.ASSISTANT,
                                                    text = if (app != null) {
                                                        "Opening ${app.name} now."
                                                    } else {
                                                        "I’m ready to open ${pending.target}."
                                                    }
                                                )
                                        } else {
                                            chatMessages =
                                                chatMessages + ChatMessage(
                                                    id = System.currentTimeMillis(),
                                                    role = ChatRole.USER,
                                                    text = input
                                                ) + ChatMessage(
                                                    id = System.currentTimeMillis() + 1L,
                                                    role = ChatRole.ASSISTANT,
                                                    text = "Okay, I won’t open it."
                                                )
                                        }
                                        pendingConfirmation = null
                                        chatInput = ""
                                        return@HomeScreen
                                    }

                                    val understanding =
                                        commandUnderstandingEngine.understand(
                                            command = input,
                                            installedApps = installedApps
                                        )
                                    val action = decideOpenAppAction(understanding)
                                    val userMessage =
                                        ChatMessage(
                                            id = System.currentTimeMillis(),
                                            role = ChatRole.USER,
                                            text = input
                                        )

                                    when (action) {
                                        CommandAction.EXECUTE -> {
                                            val target = understanding.target ?: ""
                                            val app = findApp(installedApps, target)
                                            if (app != null) {
                                                val opened = openApp(context, app)
                                                if (opened) {
                                                    status = "Opening ${app.name}"
                                                    recent = listOf("Open ${app.name}") + recent.take(4)
                                                    commandUnderstandingEngine.learnSuccessfulCommand(
                                                        command = input,
                                                        installedApps = installedApps,
                                                        understanding = understanding
                                                    )
                                                    commandHistory =
                                                        mergeCommandHistory(commandHistory, input)
                                                    saveCommandHistory(context, commandHistory)
                                                } else {
                                                    status = "Cannot open ${app.name}"
                                                }
                                            }
                                            val finalAssistantReply =
                                                if (app != null) {
                                                    "Opening ${app.name} now."
                                                } else {
                                                    replyText
                                                }
                                            conversationMemory =
                                                mergeConversationMemory(
                                                    memory = conversationMemory,
                                                    userPrompt = input,
                                                    assistantReply = finalAssistantReply
                                                )
                                            saveChatMemory(context, conversationMemory)
                                            chatMessages =
                                                chatMessages + userMessage + ChatMessage(
                                                    id = System.currentTimeMillis() + 1L,
                                                    role = ChatRole.ASSISTANT,
                                                    text = finalAssistantReply
                                                )
                                        }

                                        CommandAction.CONFIRM -> {
                                            val target = understanding.target ?: "Unknown app"
                                            pendingConfirmation = PendingConfirmation(target, input)
                                            val confirmReply = "Did you mean ${target}? Please say yes or no."
                                            conversationMemory =
                                                mergeConversationMemory(
                                                    memory = conversationMemory,
                                                    userPrompt = input,
                                                    assistantReply = confirmReply
                                                )
                                            saveChatMemory(context, conversationMemory)
                                            chatMessages =
                                                chatMessages + userMessage + ChatMessage(
                                                    id = System.currentTimeMillis() + 1L,
                                                    role = ChatRole.ASSISTANT,
                                                    text = confirmReply
                                                )
                                        }

                                        CommandAction.UNKNOWN -> {
                                            conversationMemory =
                                                mergeConversationMemory(
                                                    memory = conversationMemory,
                                                    userPrompt = input,
                                                    assistantReply = replyText
                                                )
                                            saveChatMemory(context, conversationMemory)
                                            chatMessages =
                                                chatMessages + userMessage + ChatMessage(
                                                    id = System.currentTimeMillis() + 1L,
                                                    role = ChatRole.ASSISTANT,
                                                    text = replyText
                                                )
                                        }
                                    }

                                    chatInput = ""
                                },
                                onCopyMessage = { text ->
                                    val clipboardManager =
                                        androidx.compose.ui.platform.LocalClipboardManager.current
                                    clipboardManager.setText(
                                        androidx.compose.ui.text.AnnotatedString(text)
                                    )
                                    status = "Copied response"
                                },
                                onRegenerateLast = {
                                    val lastUserPrompt =
                                        chatMessages.lastOrNull { it.role == ChatRole.USER }?.text
                                            ?: return@HomeScreen

                                    val lastAssistantIndex =
                                        chatMessages.lastIndexOfLast { it.role == ChatRole.ASSISTANT }

                                    if (lastAssistantIndex >= 0) {
                                        val regeneratedText =
                                            generateAssistantReply(
                                                prompt = lastUserPrompt,
                                                recentContext = recentChatContext,
                                                personalMemory = conversationMemory,
                                                userProfile = userProfile
                                            )
                                        val regenerated =
                                            ChatMessage(
                                                id = System.currentTimeMillis(),
                                                role = ChatRole.ASSISTANT,
                                                text = regeneratedText
                                            )
                                        conversationMemory =
                                            mergeConversationMemory(
                                                memory = conversationMemory,
                                                userPrompt = lastUserPrompt,
                                                assistantReply = regeneratedText
                                            )
                                        saveChatMemory(context, conversationMemory)

                                        chatMessages =
                                            chatMessages.toMutableList().apply {
                                                set(lastAssistantIndex, regenerated)
                                            }
                                    }
                                },
                                onEnableAura = ::enableAura,
                                onPauseAura = ::pauseAuraService,
                                onResumeAura = ::resumeAuraService,
                                onStopAura = ::stopAuraService,
                                onTalkToAura = {
                                    if (
                                        context.checkSelfPermission(
                                            Manifest.permission.RECORD_AUDIO
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        manualVoicePermissionLauncher.launch(
                                            Manifest.permission.RECORD_AUDIO
                                        )
                                    } else {
                                        startManualVoiceInput()
                                    }
                                },
                                onSaveFavorite = { favorite ->
                                    val updated = listOf(favorite) + favoriteActions.filter {
                                        it.command.lowercase(Locale.US) != favorite.command.lowercase(Locale.US)
                                    }
                                    favoriteActions = updated.take(5)
                                    saveFavoriteActions(context, favoriteActions)
                                },
                                onSelectFavorite = { commandText ->
                                    chatInput = commandText
                                }
                            )

                        AuraScreen.REVIEWER ->
                            ReviewerScreen(
                                fg = fg,
                                card = card,
                                contentMaxWidth = contentMaxWidth,
                                horizontalPadding = horizontalPadding,
                                verticalSpacing = verticalSpacing,
                                documentName = documentName,
                                documentSummary = documentSummary,
                                documentError = documentError,
                                reviewingDocument = reviewingDocument,
                                onPickDocument = ::openDocumentReviewer,
                                onSave = ::saveReviewer,
                                onClear = {
                                    documentName = null
                                    documentSummary = null
                                    documentError = null
                                    status = "Ready"
                                }
                            )

                        AuraScreen.SETTINGS ->
                            SettingsScreen(
                                context = context,
                                fg = fg,
                                card = card,
                                contentMaxWidth = contentMaxWidth,
                                horizontalPadding = horizontalPadding,
                                verticalSpacing = verticalSpacing,
                                actionHeight = actionHeight,
                                dark = dark,
                                auraEnabled = auraEnabled,
                                auraPaused = auraPaused,
                                auraListening = auraListening,
                                status = status,
                                installedApps = installedApps,
                                onToggleDark = {
                                    dark = !dark
                                },
                                onRefreshApps = {
                                    installedApps = getInstalledApps(context)
                                    status =
                                        "${installedApps.size} apps found"
                                },
                                onOpenApp = { app ->
                                    if (openApp(context, app)) {
                                        status = "Opening ${app.name}"
                                        recent =
                                            listOf(
                                                "Open ${app.name}"
                                            ) + recent.take(4)
                                    } else {
                                        status =
                                            "Cannot open ${app.name}"
                                    }
                                },
                                onOpenOverlaySettings = {
                                    status =
                                        if (
                                            startActivitySafely(
                                                context,
                                                overlaySettingsIntent()
                                            )
                                        ) {
                                            "Opening overlay settings"
                                        } else {
                                            "Overlay settings unavailable"
                                        }
                                },
                                onOpenSystemSettings = {
                                    status =
                                        if (
                                            startActivitySafely(
                                                context,
                                                Intent(Settings.ACTION_SETTINGS)
                                            )
                                        ) {
                                            "Opening Android settings"
                                        } else {
                                            "Android settings unavailable"
                                        }
                                },
                                onOpenFiles = {
                                    status =
                                        if (
                                            startActivitySafely(
                                                context,
                                                Intent(
                                                    Intent.ACTION_OPEN_DOCUMENT
                                                ).apply {
                                                    type = "*/*"
                                                    addCategory(
                                                        Intent.CATEGORY_OPENABLE
                                                    )
                                                }
                                            )
                                        ) {
                                            "Opening file picker"
                                        } else {
                                            "File picker unavailable"
                                        }
                                },
                                onOpenDownloads = {
                                    status =
                                        if (
                                            startActivitySafely(
                                                context,
                                                Intent(
                                                    "android.intent.action.VIEW_DOWNLOADS"
                                                )
                                            )
                                        ) {
                                            "Opening Downloads"
                                        } else {
                                            "Downloads shortcut unavailable"
                                        }
                                },
                                onBrightnessLow = {
                                    runDeviceCommand(
                                        DeviceCommand.SetBrightness(20)
                                    )
                                },
                                onBrightnessMedium = {
                                    runDeviceCommand(
                                        DeviceCommand.SetBrightness(55)
                                    )
                                },
                                onBrightnessHigh = {
                                    runDeviceCommand(
                                        DeviceCommand.SetBrightness(90)
                                    )
                                },
                                onVolumeLow = {
                                    runDeviceCommand(
                                        DeviceCommand.SetVolume(25)
                                    )
                                },
                                onVolumeMedium = {
                                    runDeviceCommand(
                                        DeviceCommand.SetVolume(55)
                                    )
                                },
                                onVolumeHigh = {
                                    runDeviceCommand(
                                        DeviceCommand.SetVolume(90)
                                    )
                                },
                                onSetMorningAlarm = {
                                    runDeviceCommand(
                                        DeviceCommand.SetAlarm(
                                            hour = 9,
                                            minute = 0
                                        )
                                    )
                                },
                                onOpenWifi = {
                                    runDeviceCommand(
                                        DeviceCommand.OpenSettings(
                                            DeviceSettingTarget.WIFI
                                        )
                                    )
                                },
                                onOpenBluetooth = {
                                    runDeviceCommand(
                                        DeviceCommand.OpenSettings(
                                            DeviceSettingTarget.BLUETOOTH
                                        )
                                    )
                                },
                                onOpenDisplay = {
                                    runDeviceCommand(
                                        DeviceCommand.OpenSettings(
                                            DeviceSettingTarget.DISPLAY
                                        )
                                    )
                                },
                                onOpenBattery = {
                                    runDeviceCommand(
                                        DeviceCommand.OpenSettings(
                                            DeviceSettingTarget.BATTERY
                                        )
                                    )
                                }
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuraBottomBar(
    currentScreen: AuraScreen,
    onScreenSelected: (AuraScreen) -> Unit
) {
    NavigationBar {
        AuraScreen.entries.forEach { screen ->
            NavigationBarItem(
                selected = currentScreen == screen,
                onClick = {
                    onScreenSelected(screen)
                },
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.label
                    )
                },
                label = {
                    Text(
                        text = screen.label,
                        maxLines = 1
                    )
                }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    fg: Color,
    card: Color,
    contentMaxWidth: Dp,
    horizontalPadding: Dp,
    verticalSpacing: Dp,
    actionHeight: Dp,
    orbSize: Dp,
    listening: Boolean,
    auraListening: Boolean,
    auraEnabled: Boolean,
    auraPaused: Boolean,
    status: String,
    chatInput: String,
    chatMessages: List<ChatMessage>,
    favoriteActions: List<FavoriteAction>,
    routinePresets: List<RoutinePreset>,
    pendingConfirmation: PendingConfirmation?,
    onChatInputChange: (String) -> Unit,
    onSendChat: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onRegenerateLast: () -> Unit,
    onEnableAura: () -> Unit,
    onPauseAura: () -> Unit,
    onResumeAura: () -> Unit,
    onStopAura: () -> Unit,
    onTalkToAura: () -> Unit,
    onSaveFavorite: (FavoriteAction) -> Unit,
    onSelectFavorite: (String) -> Unit,
    onRunRoutinePreset: (RoutinePreset) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = horizontalPadding,
                vertical = 20.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(verticalSpacing)
    ) {
        item {
            ScreenHeader(
                title = "AURA Agent",
                subtitle = "Professional assistant, ready to act.",
                fg = fg,
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
            )
        }

        item {
            OrbCard(
                listening = listening || auraListening,
                card = card,
                fg = fg,
                status = status,
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(),
                orbSize = orbSize
            )
        }

        item {
            AuraControls(
                auraEnabled = auraEnabled,
                auraPaused = auraPaused,
                actionHeight = actionHeight,
                contentMaxWidth = contentMaxWidth,
                onEnableAura = onEnableAura,
                onPauseAura = onPauseAura,
                onResumeAura = onResumeAura,
                onStopAura = onStopAura
            )
        }

        item {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Quick actions",
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    smartSuggestions.forEach { suggestion ->
                        TextButton(
                            onClick = {
                                onChatInputChange(suggestion)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = suggestion,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (routinePresets.isNotEmpty()) {
            item {
                Column(
                    modifier =
                        Modifier
                            .widthIn(max = contentMaxWidth)
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Routine presets",
                        color = fg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    routinePresets.forEach { preset ->
                        Button(
                            onClick = {
                                onRunRoutinePreset(preset)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = preset.title,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = preset.description,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        if (favoriteActions.isNotEmpty()) {
            item {
                Column(
                    modifier =
                        Modifier
                            .widthIn(max = contentMaxWidth)
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Favorites",
                        color = fg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    favoriteActions.forEach { favorite ->
                        TextButton(
                            onClick = {
                                onSelectFavorite(favorite.command)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = favorite.label,
                                color = fg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (pendingConfirmation != null) {
            item {
                Card(
                    modifier =
                        Modifier
                            .widthIn(max = contentMaxWidth)
                            .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Confirm action",
                            color = fg,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Did you mean ${pendingConfirmation.target}?",
                            color = fg,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = card)
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    chatMessages.forEach { message ->
                        val isUser = message.role == ChatRole.USER
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .widthIn(max = 300.dp)
                                        .background(
                                            color =
                                                if (isUser) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                },
                                            shape = RoundedCornerShape(
                                                topStart = if (isUser) 18.dp else 8.dp,
                                                topEnd = if (isUser) 8.dp else 18.dp,
                                                bottomStart = 18.dp,
                                                bottomEnd = 18.dp
                                            )
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = message.text,
                                    color = if (isUser) Color.White else fg,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )

                                if (!isUser && message.text.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { onCopyMessage(message.text) }
                                        ) {
                                            Text("Copy")
                                        }
                                        TextButton(
                                            onClick = onRegenerateLast
                                        ) {
                                            Text("Regenerate")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = onChatInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Message AURA...")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )

                Button(
                    onClick = onSendChat,
                    shape = RoundedCornerShape(18.dp),
                    enabled = chatInput.isNotBlank()
                ) {
                    Text("Send")
                }
            }
        }

        item {
            Button(
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
                        .height(actionHeight),
                shape = RoundedCornerShape(18.dp),
                onClick = onTalkToAura
            ) {
                Text(
                    text =
                        if (listening) {
                            "Listening..."
                        } else {
                            "Talk to AURA"
                        },
                    fontSize = 16.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ReviewerScreen(
    fg: Color,
    card: Color,
    contentMaxWidth: Dp,
    horizontalPadding: Dp,
    verticalSpacing: Dp,
    documentName: String?,
    documentSummary: DocumentSummary?,
    documentError: String?,
    reviewingDocument: Boolean,
    onPickDocument: () -> Unit,
    onSave: (DocumentSummary) -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = horizontalPadding,
                vertical = 20.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(verticalSpacing)
    ) {
        item {
            ScreenHeader(
                title = "Document Reviewer",
                subtitle = "Summaries, notes, actions, and keywords.",
                fg = fg,
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
            )
        }

        item {
            DocumentReviewPanel(
                card = card,
                fg = fg,
                documentName = documentName,
                summary = documentSummary,
                error = documentError,
                reviewing = reviewingDocument,
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(),
                onPickDocument = onPickDocument,
                onSave = onSave,
                onClear = onClear
            )
        }

        item {
            InfoCard(
                title = "Supported files",
                body =
                    "Works with TXT, Markdown, JSON, XML, DOCX, and text-based PDFs. Image-only PDFs need OCR later.",
                card = card,
                fg = fg,
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    context: Context,
    fg: Color,
    card: Color,
    contentMaxWidth: Dp,
    horizontalPadding: Dp,
    verticalSpacing: Dp,
    actionHeight: Dp,
    dark: Boolean,
    auraEnabled: Boolean,
    auraPaused: Boolean,
    auraListening: Boolean,
    status: String,
    installedApps: List<InstalledApp>,
    onToggleDark: () -> Unit,
    onRefreshApps: () -> Unit,
    onOpenApp: (InstalledApp) -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenDownloads: () -> Unit,
    onBrightnessLow: () -> Unit,
    onBrightnessMedium: () -> Unit,
    onBrightnessHigh: () -> Unit,
    onVolumeLow: () -> Unit,
    onVolumeMedium: () -> Unit,
    onVolumeHigh: () -> Unit,
    onSetMorningAlarm: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenBattery: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = horizontalPadding,
                vertical = 20.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(verticalSpacing)
    ) {
        item {
            ScreenHeader(
                title = "Settings",
                subtitle = "AURA controls, permissions, and installed apps.",
                fg = fg,
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
            )
        }

        item {
            SettingsStatusCard(
                context = context,
                card = card,
                fg = fg,
                dark = dark,
                auraEnabled = auraEnabled,
                auraPaused = auraPaused,
                auraListening = auraListening,
                status = status,
                onToggleDark = onToggleDark,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenSystemSettings = onOpenSystemSettings,
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
            )
        }

        item {
            SectionTitle(
                text = "Brightness",
                fg = fg,
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
            )
        }

        item {
            Row(
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                Quick(
                    title = "Low",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onBrightnessLow()
                }

                Quick(
                    title = "Medium",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onBrightnessMedium()
                }

                Quick(
                    title = "High",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onBrightnessHigh()
                }
            }
        }

        item {
            SectionTitle(
                text = "Media volume",
                fg = fg,
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
            )
        }

        item {
            Row(
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                Quick(
                    title = "Low",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onVolumeLow()
                }

                Quick(
                    title = "Medium",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onVolumeMedium()
                }

                Quick(
                    title = "High",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onVolumeHigh()
                }
            }
        }

        item {
            SectionTitle(
                text = "System shortcuts",
                fg = fg,
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
            )
        }

        item {
            Row(
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                Quick(
                    title = "Alarm",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onSetMorningAlarm()
                }

                Quick(
                    title = "Wi-Fi",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onOpenWifi()
                }

                Quick(
                    title = "Bluetooth",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onOpenBluetooth()
                }
            }
        }

        item {
            Row(
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                Quick(
                    title = "Display",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onOpenDisplay()
                }

                Quick(
                    title = "Battery",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onOpenBattery()
                }

                Quick(
                    title = "Files",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onOpenFiles()
                }
            }
        }

        item {
            Row(
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                Quick(
                    title = "Downloads",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onOpenDownloads()
                }

                Quick(
                    title = "Settings",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onOpenSystemSettings()
                }
            }
        }

        item {
            Row(
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                SectionTitle(
                    text = "Installed apps (${installedApps.size})",
                    fg = fg,
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    onClick = onRefreshApps
                ) {
                    Text("Refresh")
                }
            }
        }

        if (installedApps.isEmpty()) {
            item {
                InfoCard(
                    title = "No launchable apps found",
                    body = "Refresh after installing apps or granting launcher visibility.",
                    card = card,
                    fg = fg,
                    modifier =
                        Modifier
                            .widthIn(max = contentMaxWidth)
                            .fillMaxWidth()
                )
            }
        } else {
            items(
                minOf(installedApps.size, 60)
            ) { index ->
                InstalledAppCard(
                    app = installedApps[index],
                    card = card,
                    fg = fg,
                    modifier =
                        Modifier
                            .widthIn(max = contentMaxWidth)
                            .fillMaxWidth(),
                    onOpenApp = {
                        onOpenApp(installedApps[index])
                    }
                )
            }
        }
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = title,
            color = fg,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = subtitle,
            color = fg.copy(alpha = 0.6f),
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        color = fg,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun AuraControls(
    auraEnabled: Boolean,
    auraPaused: Boolean,
    actionHeight: Dp,
    contentMaxWidth: Dp,
    onEnableAura: () -> Unit,
    onPauseAura: () -> Unit,
    onResumeAura: () -> Unit,
    onStopAura: () -> Unit
) {
    if (auraEnabled) {
        Row(
            modifier =
                Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Button(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(actionHeight),
                shape = RoundedCornerShape(18.dp),
                onClick =
                    if (auraPaused) {
                        onResumeAura
                    } else {
                        onPauseAura
                    }
            ) {
                Text(
                    text =
                        if (auraPaused) {
                            "Resume AURA"
                        } else {
                            "Pause AURA"
                        },
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(actionHeight),
                shape = RoundedCornerShape(18.dp),
                onClick = onStopAura
            ) {
                Text(
                    text = "Stop AURA",
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Button(
            modifier =
                Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxWidth()
                    .height(actionHeight),
            shape = RoundedCornerShape(18.dp),
            onClick = onEnableAura
        ) {
            Text(
                text = "Enable AURA",
                fontSize = 16.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun OrbCard(
    listening: Boolean,
    card: Color,
    fg: Color,
    status: String,
    modifier: Modifier = Modifier,
    orbSize: Dp = 118.dp
) {
    val infiniteTransition =
        rememberInfiniteTransition(
            label = "orb"
        )

    val pulse by
        infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.06f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1400),
                    repeatMode = RepeatMode.Reverse
                ),
            label = "pulse"
        )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = card
            )
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Box(
                modifier =
                    Modifier
                        .size(orbSize)
                        .scale(pulse)
                        .background(
                            brush =
                                Brush.radialGradient(
                                    colors =
                                        listOf(
                                            Color(0xFF65D6A3),
                                            Color(0xFF3A6FF7),
                                            Color.Transparent
                                        )
                                ),
                            shape = CircleShape
                        ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    text =
                        if (listening) {
                            "ON"
                        } else {
                            "AI"
                        },
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text =
                    if (listening) {
                        "Listening"
                    } else {
                        status
                    },
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "What can I do for you?",
                color = fg.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DocumentReviewPanel(
    card: Color,
    fg: Color,
    documentName: String?,
    summary: DocumentSummary?,
    error: String?,
    reviewing: Boolean,
    modifier: Modifier = Modifier,
    onPickDocument: () -> Unit,
    onSave: (DocumentSummary) -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = card
            ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Document reviewer",
                        color = fg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text =
                            documentName ?: "No document selected",
                        color = fg.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(
                    modifier = Modifier.width(10.dp)
                )

                Button(
                    onClick = onPickDocument,
                    enabled = !reviewing,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text =
                            if (reviewing) {
                                "Reading"
                            } else {
                                "Open"
                            },
                        maxLines = 1
                    )
                }
            }

            if (error != null) {
                Text(
                    text = error,
                    color = Color(0xFFFFB4AB),
                    fontSize = 13.sp
                )
            }

            if (summary != null) {
                Text(
                    text =
                        "${summary.wordCount} words | ${summary.readingTimeMinutes} min read",
                    color = fg.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                SummaryGroup(
                    title = "Key points",
                    items = summary.keyPoints,
                    fg = fg
                )

                SummaryGroup(
                    title = "Reviewer notes",
                    items = summary.reviewerNotes,
                    fg = fg
                )

                if (summary.actionItems.isNotEmpty()) {
                    SummaryGroup(
                        title = "Action items",
                        items = summary.actionItems,
                        fg = fg
                    )
                }

                if (summary.keywords.isNotEmpty()) {
                    Text(
                        text =
                            "Keywords: ${summary.keywords.joinToString(", ")}",
                        color = fg.copy(alpha = 0.66f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            onSave(summary)
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Save",
                            maxLines = 1
                        )
                    }

                    TextButton(
                        onClick = onClear,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Clear review",
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryGroup(
    title: String,
    items: List<String>,
    fg: Color
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "-",
                    color = Color(0xFF65D6A3),
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Text(
                    text = item,
                    color = fg.copy(alpha = 0.86f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsStatusCard(
    context: Context,
    card: Color,
    fg: Color,
    dark: Boolean,
    auraEnabled: Boolean,
    auraPaused: Boolean,
    auraListening: Boolean,
    status: String,
    onToggleDark: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val micGranted =
        context.checkSelfPermission(
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = card
            ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Theme",
                        color = fg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Text(
                        text =
                            if (dark) {
                                "Dark mode"
                            } else {
                                "Light mode"
                            },
                        color = fg.copy(alpha = 0.58f),
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = dark,
                    onCheckedChange = {
                        onToggleDark()
                    }
                )
            }

            StatusLine(
                label = "AURA",
                value =
                    when {
                        !auraEnabled -> "Stopped"
                        auraPaused -> "Paused"
                        auraListening -> "Listening"
                        else -> "Waiting"
                    },
                fg = fg
            )

            StatusLine(
                label = "Status",
                value = status,
                fg = fg
            )

            StatusLine(
                label = "Microphone permission",
                value =
                    if (micGranted) {
                        "Granted"
                    } else {
                        "Not granted"
                    },
                fg = fg
            )

            StatusLine(
                label = "Overlay permission",
                value =
                    if (Settings.canDrawOverlays(context)) {
                        "Granted"
                    } else {
                        "Not granted"
                    },
                fg = fg
            )

            StatusLine(
                label = "System settings permission",
                value =
                    if (Settings.System.canWrite(context)) {
                        "Granted"
                    } else {
                        "Not granted"
                    },
                fg = fg
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onOpenOverlaySettings
                ) {
                    Text(
                        text = "Overlay",
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSystemSettings
                ) {
                    Text(
                        text = "Android Settings",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLine(
    label: String,
    value: String,
    fg: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.Top
    ) {
        Text(
            text = label,
            color = fg.copy(alpha = 0.58f),
            fontSize = 12.sp,
            modifier = Modifier.weight(0.9f)
        )

        Text(
            text = value,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.1f)
        )
    }
}

@Composable
private fun InstalledAppCard(
    app: InstalledApp,
    card: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    onOpenApp: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onOpenApp,
        colors =
            CardDefaults.cardColors(
                containerColor = card
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .background(
                            Color(0xFF65D6A3),
                            CircleShape
                        ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    text =
                        app.name
                            .firstOrNull()
                            ?.uppercase()
                            ?: "?",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = app.name,
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = app.packageName,
                    color = fg.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecentCommandCard(
    command: String,
    card: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = card
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                text = ">",
                color = Color(0xFF65D6A3),
                fontSize = 22.sp
            )

            Spacer(
                modifier = Modifier.width(10.dp)
            )

            Text(
                text = command,
                color = fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RowScope.Quick(
    title: String,
    card: Color,
    fg: Color,
    height: Dp = 92.dp,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .weight(1f)
                .height(height),
        colors =
            CardDefaults.cardColors(
                containerColor = card
            ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
    card: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = card
            ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Text(
                text = body,
                color = fg.copy(alpha = 0.66f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

private fun getDocumentDisplayName(
    context: Context,
    uri: Uri
): String {
    context.contentResolver
        .query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index =
                    cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )

                if (index >= 0) {
                    val name = cursor.getString(index)

                    if (!name.isNullOrBlank()) {
                        return name
                    }
                }
            }
        }

    return uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf {
            it.isNotBlank()
        }
        ?: "Document"
}

private fun startActivitySafely(
    context: Context,
    intent: Intent
): Boolean =
    runCatching {
        context.startActivity(intent)
    }.isSuccess
