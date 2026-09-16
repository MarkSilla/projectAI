package com.marksilla.auraagent

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import java.util.Locale

class AuraService : Service() {
    enum class ListeningMode {
        WAKE_MODE,
        COMMAND_MODE
    }

    companion object {
        const val ACTION_START = "com.marksilla.auraagent.action.START_AURA"
        const val ACTION_STOP = "com.marksilla.auraagent.action.STOP_AURA"

        private const val NOTIFICATION_CHANNEL_ID = "aura_active"
        private const val NOTIFICATION_CHANNEL_NAME = "AURA voice activation"
        private const val NOTIFICATION_ID = 1001
    }

    private val handler = Handler(Looper.getMainLooper())
    private val wakeRestartRunnable = Runnable {
        startWakeMode()
    }
    private val commandStartRunnable = Runnable {
        startRecognizer(ListeningMode.COMMAND_MODE)
    }

    private lateinit var overlay: AuraEdgeOverlay
    private var recognizer: SpeechRecognizer? = null
    private var currentMode = ListeningMode.WAKE_MODE
    private var isActive = false
    private var lastRecognizerStartAt = 0L
    private var rapidRestartCount = 0

    override fun onCreate() {
        super.onCreate()
        overlay = AuraEdgeOverlay(this)
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopAura()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification()
        )

        startAura()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAura()
        super.onDestroy()
    }

    private fun startAura() {
        if (isActive) {
            publishState(
                status =
                    if (currentMode == ListeningMode.COMMAND_MODE) {
                        "AURA is listening..."
                    } else {
                        "Waiting for \"Hey AURA\""
                    },
                listening = currentMode == ListeningMode.COMMAND_MODE
            )
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            publishState(
                status = "Microphone permission is required",
                listening = false,
                active = false
            )
            showToast("Microphone permission is required")
            stopSelf()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            publishState(
                status = "Speech recognition is unavailable",
                listening = false,
                active = false
            )
            showToast("Speech recognition is unavailable")
            stopSelf()
            return
        }

        isActive = true
        startWakeMode()
    }

    private fun stopAura() {
        isActive = false
        handler.removeCallbacks(wakeRestartRunnable)
        handler.removeCallbacks(commandStartRunnable)
        overlay.hide()
        destroyRecognizer()
        publishState(
            status = "AURA stopped",
            listening = false,
            active = false
        )

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: RuntimeException) {
            // Foreground state may already be gone if Android is tearing down.
        }
    }

    private fun startWakeMode() {
        if (!isActive) {
            return
        }

        currentMode = ListeningMode.WAKE_MODE
        overlay.hide()
        publishState(
            status = "Waiting for \"Hey AURA\"",
            listening = false
        )
        startRecognizer(ListeningMode.WAKE_MODE)
    }

    private fun enterCommandMode(
        inlineCommand: String? = null
    ) {
        if (!isActive || currentMode != ListeningMode.WAKE_MODE) {
            return
        }

        currentMode = ListeningMode.COMMAND_MODE
        handler.removeCallbacks(wakeRestartRunnable)
        handler.removeCallbacks(commandStartRunnable)
        destroyRecognizer()
        overlay.show("AURA is listening...")
        publishState(
            status = "AURA is listening...",
            listening = true
        )

        if (inlineCommand.isNullOrBlank()) {
            handler.postDelayed(commandStartRunnable, 300L)
        } else {
            handler.postDelayed(
                {
                    handleCommandResults(
                        listOf(inlineCommand)
                    )
                },
                450L
            )
        }
    }

    private fun startRecognizer(mode: ListeningMode) {
        if (!isActive) {
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            publishState(
                status = "Microphone permission is required",
                listening = false,
                active = false
            )
            stopAura()
            stopSelf()
            return
        }

        destroyRecognizer()
        currentMode = mode

        val speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(AuraRecognitionListener(mode))
            }

        recognizer = speechRecognizer
        lastRecognizerStartAt = SystemClock.elapsedRealtime()

        try {
            speechRecognizer.startListening(
                buildSpeechIntent(mode)
            )
        } catch (e: RuntimeException) {
            handleRecognizerFailure(mode)
        }
    }

    private fun buildSpeechIntent(mode: ListeningMode): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault().toLanguageTag()
            )
            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                5
            )
            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                mode == ListeningMode.WAKE_MODE
            )
        }

    private fun destroyRecognizer() {
        recognizer?.let {
            runCatching {
                it.stopListening()
            }
            runCatching {
                it.cancel()
            }
            runCatching {
                it.destroy()
            }
        }
        recognizer = null
    }

    private fun handleWakeResults(
        results: List<String>,
        allowInlineCommand: Boolean = false
    ) {
        if (currentMode != ListeningMode.WAKE_MODE) {
            return
        }

        val wakeResult =
            results.firstOrNull(::containsWakeWord)

        if (wakeResult != null) {
            val inlineCommand =
                if (
                    allowInlineCommand &&
                    !extractOpenCommand(wakeResult).isNullOrBlank()
                ) {
                    wakeResult
                } else {
                    null
                }

            rapidRestartCount = 0
            publishState(
                status = "Wake phrase detected",
                listening = true
            )
            enterCommandMode(inlineCommand)
        }
    }

    private fun handleCommandResults(results: List<String>) {
        if (currentMode != ListeningMode.COMMAND_MODE) {
            return
        }

        destroyRecognizer()

        val command =
            results.firstOrNull {
                it.isNotBlank()
            }?.trim()

        if (command.isNullOrBlank()) {
            finishCommandWithMessage("App not found")
            return
        }

        val requestedName = extractOpenCommand(command)

        if (requestedName.isNullOrBlank()) {
            finishCommandWithMessage("App not found")
            return
        }

        val app =
            findApp(
                getInstalledApps(this),
                requestedName
            )

        if (app == null) {
            finishCommandWithMessage("App not found")
            return
        }

        if (openApp(this, app)) {
            publishState(
                status = "Opening ${app.name}",
                listening = false
            )
            overlay.hide()
            scheduleWakeRestart(800L)
        } else {
            finishCommandWithMessage("Couldn't open the app")
        }
    }

    private fun finishCommandWithMessage(message: String) {
        overlay.setMessage(message)
        publishState(
            status = message,
            listening = false
        )
        showToast(message)
        handler.postDelayed(
            {
                overlay.hide()
                scheduleWakeRestart(400L)
            },
            1200L
        )
    }

    private fun handleRecognizerFailure(mode: ListeningMode) {
        destroyRecognizer()

        if (mode == ListeningMode.WAKE_MODE) {
            publishState(
                status = "Restarting wake listener",
                listening = false
            )
            scheduleWakeRestart(nextWakeRestartDelay())
        } else {
            overlay.hide()
            publishState(
                status = "Returning to wake mode",
                listening = false
            )
            scheduleWakeRestart(700L)
        }
    }

    private fun scheduleWakeRestart(delayMillis: Long) {
        if (!isActive) {
            return
        }

        handler.removeCallbacks(wakeRestartRunnable)
        handler.postDelayed(
            wakeRestartRunnable,
            delayMillis
        )
    }

    private fun nextWakeRestartDelay(): Long {
        val elapsed =
            SystemClock.elapsedRealtime() - lastRecognizerStartAt

        rapidRestartCount =
            if (elapsed < 2500L) {
                rapidRestartCount + 1
            } else {
                0
            }

        return (650L + rapidRestartCount * 450L)
            .coerceAtMost(3500L)
    }

    private fun isRecoverableWakeError(error: Int): Boolean =
        error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
            error == SpeechRecognizer.ERROR_NETWORK ||
            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == SpeechRecognizer.ERROR_CLIENT

    private fun createNotificationChannel() {
        val manager =
            getSystemService(NotificationManager::class.java)

        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps AURA listening for the wake phrase."
                setShowBadge(false)
            }

        manager.createNotificationChannel(channel)
    }

    private fun publishState(
        status: String,
        listening: Boolean,
        active: Boolean = isActive
    ) {
        AuraServiceState.publish(
            context = this,
            active = active,
            status = status,
            listening = listening,
            mode = currentMode.name
        )

        if (active) {
            getSystemService(NotificationManager::class.java)
                .notify(
                    NOTIFICATION_ID,
                    buildNotification(status)
                )
        }
    }

    private fun buildNotification(
        text: String = "Say \"Hey AURA\" to activate"
    ): Notification {
        val contentIntent =
            Intent(this, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        return Notification.Builder(
            this,
            NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.aura_icon)
            .setContentTitle("AURA is active")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun showToast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    private inner class AuraRecognitionListener(
        private val listenerMode: ListeningMode
    ) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (!isActive || listenerMode != currentMode) {
                return
            }

            when (listenerMode) {
                ListeningMode.WAKE_MODE ->
                    publishState(
                        status = "Waiting for \"Hey AURA\"",
                        listening = false
                    )

                ListeningMode.COMMAND_MODE ->
                    publishState(
                        status = "AURA is listening...",
                        listening = true
                    )
            }
        }

        override fun onBeginningOfSpeech() {
            if (!isActive || listenerMode != currentMode) {
                return
            }

            if (listenerMode == ListeningMode.COMMAND_MODE) {
                publishState(
                    status = "Listening for command",
                    listening = true
                )
            }
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            if (!isActive || listenerMode != currentMode) {
                return
            }

            destroyRecognizer()

            if (
                listenerMode == ListeningMode.WAKE_MODE &&
                isRecoverableWakeError(error)
            ) {
                scheduleWakeRestart(nextWakeRestartDelay())
            } else {
                overlay.hide()
                scheduleWakeRestart(700L)
            }
        }

        override fun onResults(results: Bundle?) {
            if (!isActive || listenerMode != currentMode) {
                return
            }

            val matches =
                results
                    ?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    .orEmpty()

            when (listenerMode) {
                ListeningMode.WAKE_MODE -> {
                    handleWakeResults(
                        results = matches,
                        allowInlineCommand = true
                    )
                    if (currentMode == ListeningMode.WAKE_MODE) {
                        scheduleWakeRestart(500L)
                    }
                }

                ListeningMode.COMMAND_MODE ->
                    handleCommandResults(matches)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (
                !isActive ||
                listenerMode != ListeningMode.WAKE_MODE ||
                currentMode != ListeningMode.WAKE_MODE
            ) {
                return
            }

            val matches =
                partialResults
                    ?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    .orEmpty()

            handleWakeResults(matches)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
