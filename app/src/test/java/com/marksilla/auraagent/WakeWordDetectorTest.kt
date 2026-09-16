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
}
