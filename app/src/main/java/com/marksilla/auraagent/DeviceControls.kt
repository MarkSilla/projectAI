package com.marksilla.auraagent

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import kotlin.math.roundToInt

sealed class DeviceCommand {
    data class SetBrightness(
        val percent: Int
    ) : DeviceCommand()

    data class SetVolume(
        val percent: Int
    ) : DeviceCommand()

    data class SetAlarm(
        val hour: Int,
        val minute: Int
    ) : DeviceCommand()

    data class SetTimer(
        val seconds: Int
    ) : DeviceCommand()

    data class OpenSettings(
        val target: DeviceSettingTarget
    ) : DeviceCommand()
}

enum class DeviceSettingTarget(
    val label: String
) {
    WIFI("Wi-Fi settings"),
    BLUETOOTH("Bluetooth settings"),
    DISPLAY("Display settings"),
    BATTERY("Battery settings"),
    SOUND("Sound settings"),
    ACCESSIBILITY("Accessibility settings"),
    DO_NOT_DISTURB("Do Not Disturb settings"),
    ALARMS("Alarms"),
    ANDROID_SETTINGS("Android settings")
}

data class DeviceCommandResult(
    val status: String,
    val recent: String,
    val needsWriteSettingsPermission: Boolean = false
)

fun parseDeviceCommand(command: String): DeviceCommand? {
    val text = normalizeDeviceCommand(command)

    if (text.isBlank()) {
        return null
    }

    parseAlarmCommand(text)?.let {
        return it
    }

    parseTimerCommand(text)?.let {
        return it
    }

    parseBrightnessCommand(text)?.let {
        return it
    }

    parseVolumeCommand(text)?.let {
        return it
    }

    return parseSettingsCommand(text)
}

fun performDeviceCommand(
    context: Context,
    command: DeviceCommand
): DeviceCommandResult =
    when (command) {
        is DeviceCommand.SetBrightness ->
            setBrightness(
                context = context,
                percent = command.percent
            )

        is DeviceCommand.SetVolume ->
            setVolume(
                context = context,
                percent = command.percent
            )

        is DeviceCommand.SetAlarm ->
            openAlarm(
                context = context,
                hour = command.hour,
                minute = command.minute
            )

        is DeviceCommand.SetTimer ->
            openTimer(
                context = context,
                seconds = command.seconds
            )

        is DeviceCommand.OpenSettings ->
            openSettings(
                context = context,
                target = command.target
            )
    }

fun writeSettingsPermissionIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${context.packageName}")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

fun startDeviceActivity(
    context: Context,
    intent: Intent
): Boolean =
    runCatching {
        context.startActivity(
            intent.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }.isSuccess

private fun normalizeDeviceCommand(command: String): String {
    var text =
        command
            .lowercase()
            .replace("a.m.", "am")
            .replace("p.m.", "pm")
            .replace("a m", "am")
            .replace("p m", "pm")
            .replace(Regex("[^a-z0-9:% ]"), " ")
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
        text.startsWith(it)
    }?.let {
        text = text.removePrefix(it).trim()
    }

    val politePrefixes =
        listOf(
            "could you please ",
            "can you please ",
            "would you please ",
            "will you please ",
            "could you ",
            "can you ",
            "would you ",
            "will you ",
            "please ",
            "paki ",
            "pakisuyo "
        )

    politePrefixes.firstOrNull {
        text.startsWith(it)
    }?.let {
        text = text.removePrefix(it).trim()
    }

    return text
}

private fun parseAlarmCommand(text: String): DeviceCommand? {
    val alarmWords =
        listOf(
            "alarm",
            "wake me",
            "wake up",
            "gisingin",
            "mag alarm",
            "pa alarm"
        )

    if (alarmWords.none { text.contains(it) }) {
        return null
    }

    val clockTime = parseClockTime(text)

    return if (clockTime == null) {
        DeviceCommand.OpenSettings(DeviceSettingTarget.ALARMS)
    } else {
        DeviceCommand.SetAlarm(
            hour = clockTime.hour,
            minute = clockTime.minute
        )
    }
}

private fun parseTimerCommand(text: String): DeviceCommand? {
    if (!text.contains("timer") && !text.contains("countdown")) {
        return null
    }

    val match =
        Regex("(\\d{1,3})\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)")
            .find(text)
            ?: return null

    val amount =
        match.groupValues[1].toIntOrNull()
            ?: return null

    val unit = match.groupValues[2]
    val seconds =
        when {
            unit.startsWith("hour") || unit.startsWith("hr") ->
                amount * 60 * 60

            unit.startsWith("second") || unit.startsWith("sec") ->
                amount

            else ->
                amount * 60
        }

    return DeviceCommand.SetTimer(
        seconds = seconds.coerceIn(1, 24 * 60 * 60)
    )
}

private fun parseBrightnessCommand(text: String): DeviceCommand? {
    val mentionsBrightness =
        text.contains("brightness") ||
            text.contains("liwanag") ||
            text.contains("dim screen")

    if (!mentionsBrightness) {
        return null
    }

    val percent =
        parsePercent(text)
            ?: when {
                text.contains("lowest") ||
                    text.contains("very low") -> 8

                text.contains("low") ||
                    text.contains("lower") ||
                    text.contains("dim") ||
                    text.contains("baba") -> 20

                text.contains("medium") ||
                    text.contains("normal") -> 50

                text.contains("highest") ||
                    text.contains("full") -> 100

                text.contains("high") ||
                    text.contains("bright") ||
                    text.contains("taas") -> 85

                else -> null
            }
            ?: return null

    return DeviceCommand.SetBrightness(percent)
}

private fun parseVolumeCommand(text: String): DeviceCommand? {
    val mentionsVolume =
        text.contains("volume") ||
            text.contains("sound level") ||
            text.contains("lakas ng sound")

    if (!mentionsVolume) {
        return null
    }

    val percent =
        parsePercent(text)
            ?: when {
                text.contains("mute") ||
                    text.contains("silent") -> 0

                text.contains("lowest") ||
                    text.contains("very low") -> 8

                text.contains("low") ||
                    text.contains("lower") ||
                    text.contains("baba") -> 25

                text.contains("medium") ||
                    text.contains("normal") -> 55

                text.contains("highest") ||
                    text.contains("full") -> 100

                text.contains("high") ||
                    text.contains("loud") ||
                    text.contains("taas") -> 85

                else -> null
            }
            ?: return null

    return DeviceCommand.SetVolume(percent)
}

private fun parseSettingsCommand(text: String): DeviceCommand? =
    when {
        text.contains("wifi") ||
            text.contains("wi fi") ->
            DeviceCommand.OpenSettings(DeviceSettingTarget.WIFI)

        text.contains("bluetooth") ->
            DeviceCommand.OpenSettings(DeviceSettingTarget.BLUETOOTH)

        text.contains("battery saver") ||
            text.contains("battery settings") ||
            text == "battery" ->
            DeviceCommand.OpenSettings(DeviceSettingTarget.BATTERY)

        text.contains("display settings") ||
            text.contains("screen settings") ->
            DeviceCommand.OpenSettings(DeviceSettingTarget.DISPLAY)

        text.contains("sound settings") ->
            DeviceCommand.OpenSettings(DeviceSettingTarget.SOUND)

        text.contains("do not disturb") ||
            text.contains("dnd") ->
            DeviceCommand.OpenSettings(DeviceSettingTarget.DO_NOT_DISTURB)

        text.contains("accessibility") ->
            DeviceCommand.OpenSettings(DeviceSettingTarget.ACCESSIBILITY)

        text == "open settings" ||
            text == "settings" ||
            text == "android settings" ->
            DeviceCommand.OpenSettings(DeviceSettingTarget.ANDROID_SETTINGS)

        else -> null
    }

private fun parsePercent(text: String): Int? =
    Regex("(\\d{1,3})\\s*%?")
        .findAll(text)
        .mapNotNull {
            it.groupValues[1].toIntOrNull()
        }
        .firstOrNull {
            it in 0..100
        }

private data class ClockTime(
    val hour: Int,
    val minute: Int
)

private fun parseClockTime(text: String): ClockTime? {
    val match =
        Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")
            .findAll(text)
            .firstOrNull { result ->
                val hour =
                    result.groupValues[1].toIntOrNull()
                        ?: return@firstOrNull false

                val minute =
                    result.groupValues[2]
                        .ifBlank {
                            "0"
                        }
                        .toIntOrNull()
                        ?: return@firstOrNull false

                hour in 0..23 && minute in 0..59
            }
            ?: return null

    var hour =
        match.groupValues[1].toIntOrNull()
            ?: return null
    val minute =
        match.groupValues[2]
            .ifBlank {
                "0"
            }
            .toIntOrNull()
            ?: return null

    when (match.groupValues[3]) {
        "am" -> {
            if (hour == 12) {
                hour = 0
            }
        }

        "pm" -> {
            if (hour in 1..11) {
                hour += 12
            }
        }
    }

    if (hour !in 0..23 || minute !in 0..59) {
        return null
    }

    return ClockTime(
        hour = hour,
        minute = minute
    )
}

private fun setBrightness(
    context: Context,
    percent: Int
): DeviceCommandResult {
    val safePercent = percent.coerceIn(1, 100)

    if (!Settings.System.canWrite(context)) {
        return DeviceCommandResult(
            status = "Allow Modify system settings for brightness",
            recent = "Brightness permission",
            needsWriteSettingsPermission = true
        )
    }

    val brightnessValue =
        (safePercent / 100f * 255f)
            .roundToInt()
            .coerceIn(1, 255)

    val changed =
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightnessValue
            )
        }.getOrDefault(false)

    return if (changed) {
        DeviceCommandResult(
            status = "Brightness set to $safePercent%",
            recent = "Brightness $safePercent%"
        )
    } else {
        DeviceCommandResult(
            status = "Could not change brightness",
            recent = "Brightness failed"
        )
    }
}

private fun setVolume(
    context: Context,
    percent: Int
): DeviceCommandResult {
    val safePercent = percent.coerceIn(0, 100)

    val audioManager =
        context.getSystemService(AudioManager::class.java)
            ?: return DeviceCommandResult(
                status = "Audio controls unavailable",
                recent = "Volume failed"
            )

    val stream = AudioManager.STREAM_MUSIC
    val maxVolume =
        audioManager.getStreamMaxVolume(stream)
            .coerceAtLeast(1)
    val targetVolume =
        (maxVolume * (safePercent / 100f))
            .roundToInt()
            .coerceIn(0, maxVolume)

    val changed =
        runCatching {
            audioManager.setStreamVolume(
                stream,
                targetVolume,
                AudioManager.FLAG_SHOW_UI
            )
        }.isSuccess

    return if (changed) {
        DeviceCommandResult(
            status = "Media volume set to $safePercent%",
            recent = "Volume $safePercent%"
        )
    } else {
        DeviceCommandResult(
            status = "Could not change volume",
            recent = "Volume failed"
        )
    }
}

private fun openAlarm(
    context: Context,
    hour: Int,
    minute: Int
): DeviceCommandResult {
    val label = formatClockTime(hour, minute)
    val opened =
        startDeviceActivity(
            context,
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(
                    AlarmClock.EXTRA_HOUR,
                    hour
                )
                putExtra(
                    AlarmClock.EXTRA_MINUTES,
                    minute
                )
                putExtra(
                    AlarmClock.EXTRA_MESSAGE,
                    "AURA alarm"
                )
                putExtra(
                    AlarmClock.EXTRA_SKIP_UI,
                    false
                )
            }
        )

    return if (opened) {
        DeviceCommandResult(
            status = "Opening alarm for $label",
            recent = "Alarm $label"
        )
    } else {
        DeviceCommandResult(
            status = "Alarm app unavailable",
            recent = "Alarm failed"
        )
    }
}

private fun openTimer(
    context: Context,
    seconds: Int
): DeviceCommandResult {
    val minutes =
        (seconds / 60)
            .coerceAtLeast(1)
    val opened =
        startDeviceActivity(
            context,
            Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(
                    AlarmClock.EXTRA_LENGTH,
                    seconds
                )
                putExtra(
                    AlarmClock.EXTRA_MESSAGE,
                    "AURA timer"
                )
                putExtra(
                    AlarmClock.EXTRA_SKIP_UI,
                    false
                )
            }
        )

    return if (opened) {
        DeviceCommandResult(
            status = "Opening timer for $minutes min",
            recent = "Timer $minutes min"
        )
    } else {
        DeviceCommandResult(
            status = "Timer app unavailable",
            recent = "Timer failed"
        )
    }
}

private fun openSettings(
    context: Context,
    target: DeviceSettingTarget
): DeviceCommandResult {
    val intent =
        when (target) {
            DeviceSettingTarget.WIFI ->
                Intent(Settings.ACTION_WIFI_SETTINGS)

            DeviceSettingTarget.BLUETOOTH ->
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

            DeviceSettingTarget.DISPLAY ->
                Intent(Settings.ACTION_DISPLAY_SETTINGS)

            DeviceSettingTarget.BATTERY ->
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)

            DeviceSettingTarget.SOUND ->
                Intent(Settings.ACTION_SOUND_SETTINGS)

            DeviceSettingTarget.ACCESSIBILITY ->
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

            DeviceSettingTarget.DO_NOT_DISTURB ->
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

            DeviceSettingTarget.ALARMS ->
                Intent(AlarmClock.ACTION_SHOW_ALARMS)

            DeviceSettingTarget.ANDROID_SETTINGS ->
                Intent(Settings.ACTION_SETTINGS)
        }

    val opened =
        startDeviceActivity(
            context = context,
            intent = intent
        )

    return if (opened) {
        DeviceCommandResult(
            status = "Opening ${target.label}",
            recent = target.label
        )
    } else {
        DeviceCommandResult(
            status = "${target.label} unavailable",
            recent = "${target.label} failed"
        )
    }
}

private fun formatClockTime(
    hour: Int,
    minute: Int
): String {
    val suffix =
        if (hour < 12) {
            "AM"
        } else {
            "PM"
        }
    val displayHour =
        when (val twelveHour = hour % 12) {
            0 -> 12
            else -> twelveHour
        }

    return "$displayHour:${minute.toString().padStart(2, '0')} $suffix"
}
