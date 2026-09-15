package com.marksilla.auraagent

import android.Manifest
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuraApp() }
    }
}

@Composable
fun AuraApp() {
    val context = LocalContext.current
    var command by remember { mutableStateOf("") }
    var dark by remember { mutableStateOf(true) }
    var listening by remember { mutableStateOf(false) }
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
            ActivityResultContracts.RequestPermission()
        ) { }

    val voiceLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val text = result.data
                ?.getStringArrayListExtra("android.speech.extra.RESULTS")
                ?.firstOrNull()

            if (!text.isNullOrBlank()) {
                command = text
                recent = listOf(text) + recent.take(4)
            }

            listening = false
        }

    val bg = if (dark) Color(0xFF0B0D12) else Color(0xFFF4F6F8)
    val fg = if (dark) Color.White else Color(0xFF111318)
    val card = if (dark) Color(0xFF151922) else Color.White

    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = bg
        ) {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "AURA Agent",
                                color = fg,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "Your phone, ready to help.",
                                color = fg.copy(alpha = .6f),
                                fontSize = 13.sp
                            )
                        }

                        TextButton(
                            onClick = { dark = !dark }
                        ) {
                            Text(
                                if (dark) "☀" else "☾",
                                fontSize = 22.sp
                            )
                        }
                    }
                }

                item {
                    OrbCard(listening, card, fg)
                }

                item {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("Tell AURA what to do…")
                        },
                        singleLine = true,
                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    if (command.isNotBlank()) {
                                        recent =
                                            listOf(command) + recent.take(4)
                                        command = ""
                                    }
                                }
                            ) {
                                Text(
                                    "GO",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        shape = RoundedCornerShape(18.dp)
                    )
                }

                item {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        onClick = {

                            if (
                                context.checkSelfPermission(
                                    Manifest.permission.RECORD_AUDIO
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )
                            }

                            val i =
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
                            voiceLauncher.launch(i)
                        }
                    ) {
                        Text(
                            if (listening)
                                "Listening…"
                            else
                                "🎙  Talk to AURA",
                            fontSize = 16.sp
                        )
                    }
                }

                item {
                    Text(
                        "Quick actions",
                        color = fg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        Quick(
                            "Files",
                            card,
                            fg
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
                            "Settings",
                            card,
                            fg
                        ) {
                            context.startActivity(
                                Intent(Settings.ACTION_SETTINGS)
                            )
                        }

                        Quick(
                            "Camera",
                            card,
                            fg
                        ) {
                            context.startActivity(
                                Intent(
                                    "android.media.action.IMAGE_CAPTURE"
                                )
                            )
                        }

                        Quick(
                            "Downloads",
                            card,
                            fg
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
                        "Recent commands",
                        color = fg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                items(recent.size) { idx ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = card
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "›",
                                color = Color(0xFF65D6A3),
                                fontSize = 22.sp
                            )

                            Spacer(Modifier.width(10.dp))

                            Text(
                                recent[idx],
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
    val inf = rememberInfiniteTransition(
        label = "orb"
    )

    val pulse by inf.animateFloat(
        0.96f,
        1.06f,
        infiniteRepeatable(
            tween(1400),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = card
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                Modifier
                    .size(118.dp)
                    .scale(pulse)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF65D6A3),
                                Color(0xFF3A6FF7),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (listening) "●" else "✦",
                    color = Color.White,
                    fontSize = 30.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                if (listening) "Listening" else "Ready",
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Text(
                "What can I do for you?",
                color = fg.copy(alpha = .55f)
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
        modifier = Modifier
            .weight(1f)
            .height(92.dp),
        colors = CardDefaults.cardColors(
            containerColor = card
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                title,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
