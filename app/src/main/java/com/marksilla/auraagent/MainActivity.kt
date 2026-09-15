package com.marksilla.auraagent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AuraApp(context = this)
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

    var recent by remember {
        mutableStateOf(
            listOf(
                "Open Downloads",
                "Find screenshots",
                "Open Settings"
            )
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) {
            // Permission result handled here.
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
            }

            listening = false
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

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = bg
        ) {

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement =
                    Arrangement.spacedBy(18.dp)
            ) {

                item {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    FontWeight.Bold
                            )

                            Text(
                                text =
                                    "Your phone, ready to help.",
                                color =
                                    fg.copy(alpha = 0.6f),
                                fontSize = 13.sp
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
                                        "☀"
                                    } else {
                                        "☾"
                                    },
                                fontSize = 22.sp
                            )
                        }
                    }
                }

                item {

                    OrbCard(
                        listening = listening,
                        card = card,
                        fg = fg
                    )
                }

                item {

                    OutlinedTextField(
                        value = command,
                        onValueChange = {
                            command = it
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Tell AURA what to do…"
                            )
                        },
                        singleLine = true,
                        trailingIcon = {

                            TextButton(
                                onClick = {

                                    if (command.isNotBlank()) {

                                        recent =
                                            listOf(command) +
                                                recent.take(4)

                                        command = ""
                                    }
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
                                .fillMaxWidth()
                                .height(56.dp),
                        shape =
                            RoundedCornerShape(18.dp),
                        onClick = {

                            if (
                                context.checkSelfPermission(
                                    Manifest.permission.RECORD_AUDIO
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {

                                permissionLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )

                                return@Button
                            }

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
                            title = "Camera",
                            card = card,
                            fg = fg
                        ) {

                            context.startActivity(
                                Intent(
                                    "android.media.action.IMAGE_CAPTURE"
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

@Composable
fun OrbCard(
    listening: Boolean,
    card: Color,
    fg: Color
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
                        "Ready"
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
