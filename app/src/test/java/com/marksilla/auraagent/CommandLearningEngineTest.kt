package com.marksilla.auraagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandLearningEngineTest {
    @Test
    fun learnsGeneralizedPatternAndMatchesNewApp() {
        val engine = CommandLearningEngine()
        val apps = listOf(
            InstalledApp("Facebook", "com.facebook.katana"),
            InstalledApp("Messenger", "com.facebook.orca"),
            InstalledApp("YouTube", "com.google.android.youtube")
        )

        engine.learn(
            command = "AURA, pasok tayo sa Facebook",
            installedApps = apps,
            understanding = CommandUnderstanding(
                intent = AuraCommandIntent.OPEN_APP,
                target = "Facebook",
                confidence = 0.96f,
                source = CommandUnderstandingSource.LOCAL_AI,
                learnedPattern = "pasok tayo sa {app}"
            )
        )

        val match = engine.match(
            command = "Pasok tayo sa Messenger",
            installedApps = apps
        )

        assertNotNull(match)
        assertEquals(AuraCommandIntent.OPEN_APP, match?.intent)
        assertEquals("Messenger", match?.target)
        assertTrue(match?.learnedPattern?.contains("{app}") == true)
    }

    @Test
    fun storesGeneralizedPatternInsteadOfRawPhrase() {
        val engine = CommandLearningEngine()
        val apps = listOf(
            InstalledApp("Facebook", "com.facebook.katana")
        )

        engine.learn(
            command = "Hey AURA, sakay tayo sa Facebook",
            installedApps = apps,
            understanding = CommandUnderstanding(
                intent = AuraCommandIntent.OPEN_APP,
                target = "Facebook",
                confidence = 0.91f,
                source = CommandUnderstandingSource.LOCAL_AI,
                learnedPattern = "sakay tayo sa {app}"
            )
        )

        val pattern = engine.patterns.firstOrNull()

        assertNotNull(pattern)
        assertEquals("sakay tayo sa {app}", pattern?.normalizedPattern)
        assertTrue(pattern?.normalizedPattern?.contains("{app}") == true)
        assertTrue(pattern?.normalizedPattern?.contains("facebook") == false)
    }
}
