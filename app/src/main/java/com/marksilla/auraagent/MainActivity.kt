package com.marksilla.auraagent

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AuraApp(context = this@MainActivity)
        }
    }
}

@Composable
fun AuraApp(context: Context) {

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

    val installedApps = remember {
        getInstalledApps(context)
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
        status = "Starting AURA..."
    }

    fun stopAuraService() {
        context.stopService(
            Intent(
                context,
                AuraService::class.java
            )
        )

        auraEnabled = false
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

    fun executeCommand() {

        val input = command.trim()

        if (input.isBlank()) {
            status = "Enter a command"
            return
        }

        val appName = extractOpenCommand(input)

        if (appName != null) {

            val app = findApp(
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
                    status =
                        "Cannot open ${app.name}"
                }

            } else {

                status =
                    "App \"$appName\" was not found"
            }

        } else {

            status =
                "Try: Open Facebook"

            recent =
                listOf(input) + recent.take(4)
        }
    }

    MaterialTheme(
        colorScheme =
            if (dark) {
                darkColorScheme()
            } else {
                lightColorScheme()
            }
    ) {

        Surface(
            modifier = Modifier.fillMaxSize(),
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
                val quickHeight =
                    if (compact) {
                        74.dp
                    } else {
                        92.dp
                    }
                val orbSize =
                    if (compact) {
                        96.dp
                    } else {
                        118.dp
                    }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            horizontal = horizontalPadding,
                            vertical = 20.dp
                        ),
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                    verticalArrangement =
                        Arrangement.spacedBy(verticalSpacing)
                ) {

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

                        Column {

                            Text(
                                text = "AURA Agent",
                                color = fg,
                                fontSize = 25.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text =
                                    "Your phone, ready to help.",
                                color =
                                    fg.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        TextButton(
                            onClick = {
                                dark = !dark
                            }
                        ) {

                            Text(
                                text =
                                    if (dark) {
                                        "LIGHT"
                                    } else {
                                        "DARK"
                                    },
                                fontSize = 12.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
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

                    Button(
                        modifier =
                            Modifier
                                .widthIn(max = contentMaxWidth)
                                .fillMaxWidth()
                                .height(actionHeight),
                        shape =
                            RoundedCornerShape(18.dp),
                        onClick = {
                            if (auraEnabled) {
                                stopAuraService()
                            } else {
                                enableAura()
                            }
                        }
                    ) {

                        Text(
                            text =
                                if (auraEnabled) {
                                    "Stop AURA"
                                } else {
                                    "Enable AURA"
                                },
                            fontSize = 16.sp
                        )
                    }
                }

                item {

                    OutlinedTextField(
                        value = command,
                        onValueChange = {
                            command = it
                        },
                        modifier =
                            Modifier
                                .widthIn(max = contentMaxWidth)
                                .fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Try: Open Facebook"
                            )
                        },
                        singleLine = true,
                        trailingIcon = {

                            TextButton(
                                onClick = {
                                    executeCommand()
                                }
                            ) {

                                Text(
                                    text = "GO",
                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }
                        },
                        shape =
                            RoundedCornerShape(18.dp)
                    )
                }

                item {

                    Button(
                        modifier =
                            Modifier
                                .widthIn(max = contentMaxWidth)
                                .fillMaxWidth()
                                .height(actionHeight),
                        shape =
                            RoundedCornerShape(18.dp),
                        onClick = {

                            if (
                                context.checkSelfPermission(
                                    Manifest.permission.RECORD_AUDIO
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {

                                manualVoicePermissionLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )

                                return@Button
                            }

                            startManualVoiceInput()
                        }
                    ) {

                        Text(
                            text =
                                if (listening) {
                                    "Listening…"
                                } else {
                                    "🎙  Talk to AURA"
                                },
                            fontSize = 16.sp
                        )
                    }
                }

                item {

                    Text(
                        text = "Quick actions",
                        color = fg,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                item {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {

                        Quick(
                            title = "Apps",
                            card = card,
                            fg = fg
                        ) {
                            status =
                                "${installedApps.size} apps found"
                        }

                        Quick(
                            title = "Files",
                            card = card,
                            fg = fg
                        ) {

                            context.startActivity(
                                Intent(
                                    Intent.ACTION_OPEN_DOCUMENT
                                ).apply {
                                    type = "*/*"

                                    addCategory(
                                        Intent.CATEGORY_OPENABLE
                                    )
                                }
                            )
                        }

                        Quick(
                            title = "Settings",
                            card = card,
                            fg = fg
                        ) {

                            context.startActivity(
                                Intent(
                                    Settings.ACTION_SETTINGS
                                )
                            )
                        }

                        Quick(
                            title = "Downloads",
                            card = card,
                            fg = fg
                        ) {

                            context.startActivity(
                                Intent(
                                    "android.intent.action.VIEW_DOWNLOADS"
                                )
                            )
                        }
                    }
                }

                item {

                    Text(
                        text =
                            "Installed apps (${installedApps.size})",
                        color = fg,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                items(
                    minOf(installedApps.size, 20)
                ) { index ->

                    val app =
                        installedApps[index]

                    Card(
                        modifier =
                            Modifier.fillMaxWidth(),
                        onClick = {

                            if (
                                openApp(
                                    context,
                                    app
                                )
                            ) {

                                status =
                                    "Opening ${app.name}"

                                recent =
                                    listOf(
                                        "Open ${app.name}"
                                    ) + recent.take(4)
                            }
                        },
                        colors =
                            CardDefaults.cardColors(
                                containerColor = card
                            ),
                        shape =
                            RoundedCornerShape(16.dp)
                    ) {

                        Row(
                            modifier =
                                Modifier.padding(16.dp),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Box(
                                modifier =
                                    Modifier
                                        .size(42.dp)
                                        .background(
                                            Color(
                                                0xFF65D6A3
                                            ),
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
                                    color =
                                        Color.Black,
                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }

                            Spacer(
                                modifier =
                                    Modifier.width(12.dp)
                            )

                            Column {

                                Text(
                                    text = app.name,
                                    color = fg,
                                    fontWeight =
                                        FontWeight.SemiBold
                                )

                                Text(
                                    text =
                                        app.packageName,
                                    color =
                                        fg.copy(
                                            alpha = 0.5f
                                        ),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                item {

                    Text(
                        text = "Recent commands",
                        color = fg,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                items(recent.size) { index ->

                    Card(
                        modifier =
                            Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = card
                            ),
                        shape =
                            RoundedCornerShape(16.dp)
                    ) {

                        Row(
                            modifier =
                                Modifier.padding(16.dp),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(
                                text = "›",
                                color =
                                    Color(0xFF65D6A3),
                                fontSize = 22.sp
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(10.dp)
                            )

                            Text(
                                text = recent[index],
                                color = fg
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
fun OrbCard(
    listening: Boolean,
    card: Color,
    fg: Color,
    status: String
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
                    animation =
                        tween(1400),
                    repeatMode =
                        RepeatMode.Reverse
                ),
            label = "pulse"
        )

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(28.dp),
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
                        .size(118.dp)
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
                            "●"
                        } else {
                            "✦"
                        },
                    color = Color.White,
                    fontSize = 30.sp
                )
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text =
                    if (listening) {
                        "Listening"
                    } else {
                        status
                    },
                color = fg,
                fontWeight =
                    FontWeight.Bold,
                fontSize = 18.sp
            )

            Text(
                text =
                    "What can I do for you?",
                color =
                    fg.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
fun RowScope.Quick(
    title: String,
    card: Color,
    fg: Color,
    onClick: () -> Unit
) {

    Card(
        onClick = onClick,
        modifier =
            Modifier
                .weight(1f)
                .height(92.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = card
            ),
        shape =
            RoundedCornerShape(18.dp)
    ) {

        Box(
            modifier =
                Modifier.fillMaxSize(),
            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = title,
                color = fg,
                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}
