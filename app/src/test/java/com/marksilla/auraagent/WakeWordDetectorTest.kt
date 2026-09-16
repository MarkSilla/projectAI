package com.marksilla.auraagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordDetectorTest {
    @Test
    fun recognizesWakeWordVariants() {
        assertTrue(containsWakeWord("Hey AURA"))
        assertTrue(containsWakeWord("hey aura"))
        assertTrue(containsWakeWord("HEY AURA"))
        assertTrue(containsWakeWord("Hey Aura"))
        assertTrue(containsWakeWord("Hey AURA, open Facebook"))
    }

    @Test
    fun normalSpeechDoesNotTriggerWakeWord() {
        assertFalse(containsWakeWord("please open Facebook"))
        assertFalse(containsWakeWord("Aura, open Facebook"))
        assertFalse(containsWakeWord("hey assistant"))
    }

    @Test
    fun extractsEnglishOpenCommands() {
        assertEquals(
            "facebook",
            extractOpenCommand("Open Facebook")
        )
        assertEquals(
            "facebook",
            extractOpenCommand("Please open Facebook")
        )
        assertEquals(
            "facebook",
            extractOpenCommand("Can you open Facebook?")
        )
        assertEquals(
            "messenger",
            extractOpenCommand("Could you please open Messenger?")
        )
        assertEquals(
            "facebook",
            extractOpenCommand("Launch Facebook")
        )
        assertEquals(
            "messenger",
            extractOpenCommand("Start Messenger")
        )
        assertEquals(
            "youtube",
            extractOpenCommand("Run YouTube")
        )
        assertEquals(
            "facebook",
            extractOpenCommand("Take me to Facebook")
        )
    }

    @Test
    fun extractsTaglishAndFilipinoOpenCommands() {
        assertEquals(
            "facebook",
            extractOpenCommand("Buksan mo Facebook")
        )
        assertEquals(
            "facebook",
            extractOpenCommand("Buksan mo yung Facebook")
        )
        assertEquals(
            "messenger",
            extractOpenCommand("Paki buksan ang Messenger")
        )
        assertEquals(
            "youtube",
            extractOpenCommand("Paki open yung YouTube")
        )
        assertEquals(
            "facebook",
            extractOpenCommand("Pwede mo bang buksan ang Facebook")
        )
        assertEquals(
            "messenger",
            extractOpenCommand("Maaari mo bang buksan ang Messenger")
        )
        assertEquals(
            "youtube",
            extractOpenCommand("Gusto kong buksan ang YouTube")
        )
        assertEquals(
            "facebook",
            extractOpenCommand("Pasok tayo sa Facebook")
        )
        assertEquals(
            "messenger",
            extractOpenCommand("Sakay tayo sa Messenger")
        )
        assertEquals(
            "youtube",
            extractOpenCommand("Lipat tayo sa YouTube")
        )
        assertEquals(
            "facebook",
            extractOpenCommand("Let's open Facebook")
        )
    }

    @Test
    fun removesAssistantPrefixesAndTrailingWords() {
        assertEquals(
            "facebook",
            extractOpenCommand("Hey AURA, open Facebook please")
        )
        assertEquals(
            "messenger",
            extractOpenCommand("Hi AURA launch Messenger for me")
        )
        assertEquals(
            "youtube",
            extractOpenCommand("AURA paki buksan ang YouTube naman")
        )
    }

    @Test
    fun matchesAppsWithSpeechRecognizerNameVariants() {
        val apps =
            listOf(
                InstalledApp(
                    name = "Facebook",
                    packageName = "com.facebook.katana"
                ),
                InstalledApp(
                    name = "Messenger",
                    packageName = "com.facebook.orca"
                ),
                InstalledApp(
                    name = "YouTube",
                    packageName = "com.google.android.youtube"
                )
            )

        assertEquals(
            "Facebook",
            findApp(apps, "face book")?.name
        )
        assertEquals(
            "Facebook",
            findApp(apps, "fb")?.name
        )
        assertEquals(
            "Messenger",
            findApp(apps, "mess")?.name
        )
        assertEquals(
            "YouTube",
            findApp(apps, "you tube")?.name
        )
    }

    @Test
    fun returnsMultipleCandidatesForGenericChatRequest() {
        val apps =
            listOf(
                InstalledApp("Messenger", "com.facebook.orca"),
                InstalledApp("WhatsApp", "com.whatsapp"),
                InstalledApp("YouTube", "com.google.android.youtube")
            )

        val candidates = findAppCandidates(apps, "Open a chat app")

        assertEquals(
            listOf("Messenger", "WhatsApp"),
            candidates.map { it.name }
        )
    }

    @Test
    fun detectsDocumentReviewCommands() {
        assertTrue(isDocumentReviewCommand("Summarize document"))
        assertTrue(isDocumentReviewCommand("Can you review this document?"))
        assertTrue(isDocumentReviewCommand("Hey AURA make a reviewer"))
        assertTrue(isDocumentReviewCommand("Paki summarize ng document"))
        assertFalse(isDocumentReviewCommand("Open Facebook"))
    }

    @Test
    fun parsesDeviceCommands() {
        val alarm =
            parseDeviceCommand(
                "Can you set an alarm 9:00am?"
            ) as DeviceCommand.SetAlarm

        assertEquals(9, alarm.hour)
        assertEquals(0, alarm.minute)

        val brightness =
            parseDeviceCommand(
                "Hey AURA low brightness"
            ) as DeviceCommand.SetBrightness

        assertEquals(20, brightness.percent)

        val volume =
            parseDeviceCommand(
                "volume high"
            ) as DeviceCommand.SetVolume

        assertEquals(85, volume.percent)

        val wifi =
            parseDeviceCommand(
                "open Wi-Fi settings"
            ) as DeviceCommand.OpenSettings

        assertEquals(DeviceSettingTarget.WIFI, wifi.target)
    }
}
