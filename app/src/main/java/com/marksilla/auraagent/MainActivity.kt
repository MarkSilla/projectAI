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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AuraApp(context = this@MainActivity)
        }
    }
}

private enum class AuraScreen(
    val title: String,
    val label: String,
    val mark: String
) {
    HOME(
        title = "AURA Agent",
        label = "Home",
        mark = "A"
    ),
    REVIEWER(
        title = "Document Reviewer",
        label = "Reviewer",
        mark = "R"
    ),
    SETTINGS(
        title = "Settings",
        label = "Settings",
        mark = "S"
    )
}

@Composable
fun AuraApp(context: Context) {
    var currentScreen by remember {
        mutableStateOf(AuraScreen.HOME)
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

                val text =
                    readDocumentText(
                        context = context,
                        uri = uri
                    )

                name to summarizeDocumentText(
                    title = name,
                    rawText = text
                )
            }
                .onSuccess { result ->
                    val (name, summary) = result
                    documentName = name
                    documentSummary = summary

                    if (summary == null) {
                        documentError =
                            "Not enough readable text found."
                        status = "Document could not be summarized"
                    } else {
                        documentError = null
                        status = "Document summarized"
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

    fun executeCommand() {
        val input = command.trim()

        if (input.isBlank()) {
            status = "Enter a command"
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
                                command = command,
                                recent = recent,
                                onCommandChange = {
                                    command = it
                                },
                                onExecuteCommand = ::executeCommand,
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
                    Text(
                        text = screen.mark,
                        fontWeight = FontWeight.Bold
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
    command: String,
    recent: List<String>,
    onCommandChange: (String) -> Unit,
    onExecuteCommand: () -> Unit,
    onEnableAura: () -> Unit,
    onPauseAura: () -> Unit,
    onResumeAura: () -> Unit,
    onStopAura: () -> Unit,
    onTalkToAura: () -> Unit
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
                subtitle = "Your phone, ready to help.",
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
            OutlinedTextField(
                value = command,
                onValueChange = onCommandChange,
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(),
                placeholder = {
                    Text("Try: Open Facebook")
                },
                singleLine = true,
                trailingIcon = {
                    TextButton(
                        onClick = onExecuteCommand
                    ) {
                        Text(
                            text = "GO",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                shape = RoundedCornerShape(18.dp)
            )
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

        item {
            SectionTitle(
                text = "Recent commands",
                fg = fg,
                modifier =
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
            )
        }

        items(recent.size) { index ->
            RecentCommandCard(
                command = recent[index],
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
                onClear = onClear
            )
        }

        item {
            InfoCard(
                title = "Readable files",
                body =
                    "Best for TXT, Markdown, JSON, XML, and exported notes. PDF and DOCX need stronger extraction later.",
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
    onOpenDownloads: () -> Unit
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
                    title = "Files",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onOpenFiles()
                }

                Quick(
                    title = "Downloads",
                    card = card,
                    fg = fg,
                    height = actionHeight
                ) {
                    onOpenDownloads()
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

private fun readDocumentText(
    context: Context,
    uri: Uri,
    maxChars: Int = 250_000
): String {
    val builder = StringBuilder()

    context.contentResolver
        .openInputStream(uri)
        ?.bufferedReader()
        ?.use { reader ->
            val buffer = CharArray(4096)

            while (builder.length < maxChars) {
                val read = reader.read(buffer)

                if (read <= 0) {
                    break
                }

                val remaining = maxChars - builder.length
                builder.append(
                    buffer,
                    0,
                    minOf(read, remaining)
                )
            }
        }

    return builder.toString()
}

private fun startActivitySafely(
    context: Context,
    intent: Intent
): Boolean =
    runCatching {
        context.startActivity(intent)
    }.isSuccess
