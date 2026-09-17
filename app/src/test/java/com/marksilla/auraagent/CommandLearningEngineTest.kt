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

    @Test
    fun learnedAliasResolvesBeforeOpenCommandParsing() {
        val memory = AuraMemoryEngine(InMemoryMemoryStore())
        memory.learnFromExplicitInstruction("tap means open")

        val rewritten = resolveLearnedCommandAliases("tap Facebook", memory)

        assertEquals("open Facebook", rewritten)

        val understanding = AuraCommandUnderstanding(
            learningEngine = CommandLearningEngine(),
            localAi = LocalCommandAi(),
            memory = memory
        ).understand(
            command = rewritten,
            installedApps = listOf(
                InstalledApp("Facebook", "com.facebook.katana")
            )
        )

        assertEquals(AuraCommandIntent.OPEN_APP, understanding.intent)
        assertEquals("Facebook", understanding.target)
    }

    @Test
    fun successfulOpenCommandAutomaticallyLearnsAliasForNextUse() {
        val memory = AuraMemoryEngine(InMemoryMemoryStore())
        val understandingEngine = AuraCommandUnderstanding(
            learningEngine = CommandLearningEngine(),
            localAi = LocalCommandAi(),
            memory = memory
        )

        understandingEngine.learnSuccessfulCommand(
            command = "tap Facebook",
            installedApps = listOf(
                InstalledApp("Facebook", "com.facebook.katana")
            ),
            understanding = CommandUnderstanding(
                intent = AuraCommandIntent.OPEN_APP,
                target = "Facebook",
                confidence = 0.96f,
                source = CommandUnderstandingSource.LOCAL_AI,
                learnedPattern = "tap {app}"
            )
        )

        val rewritten = resolveLearnedCommandAliases("tap Facebook", memory)

        assertEquals("open Facebook", rewritten)

        val nextUnderstanding = understandingEngine.understand(
            command = "tap Facebook",
            installedApps = listOf(
                InstalledApp("Facebook", "com.facebook.katana")
            )
        )

        assertEquals(AuraCommandIntent.OPEN_APP, nextUnderstanding.intent)
        assertEquals("Facebook", nextUnderstanding.target)
    }

    @Test
    fun memoryEntriesAreAvailableForDisplayInTheMemoryScreen() {
        val memory = AuraMemoryEngine(InMemoryMemoryStore())
        memory.learnFromExplicitInstruction("tap means open")
        memory.remember(
            type = "pattern",
            category = "implicit",
            value = "open facebook",
            source = "SYSTEM",
            confidence = 0.87f,
            matchText = "tap facebook"
        )

        val entries = memory.allMemory()

        assertTrue(entries.isNotEmpty())
        assertTrue(entries.any { it.type == "application_alias" || it.category == "implicit" })
        assertTrue(entries.any { it.matchText == "tap" || it.matchText == "tap facebook" })
    }

    @Test
    fun standardOpenVerbIsNotOverwrittenByLearnedAppAlias() {
        val memory = AuraMemoryEngine(InMemoryMemoryStore())
        memory.learnFromExplicitInstruction("open means facebook")

        val rewritten = resolveLearnedCommandAliases("open insta", memory)

        assertEquals("open insta", rewritten)

        val understanding = AuraCommandUnderstanding(
            learningEngine = CommandLearningEngine(),
            localAi = LocalCommandAi(),
            memory = memory
        ).understand(
            command = "open insta",
            installedApps = listOf(
                InstalledApp("Facebook", "com.facebook.katana"),
                InstalledApp("Instagram", "com.instagram.android")
            )
        )

        assertEquals(AuraCommandIntent.OPEN_APP, understanding.intent)
        assertEquals("Instagram", understanding.target)
    }
}
