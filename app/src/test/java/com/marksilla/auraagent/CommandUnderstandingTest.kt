package com.marksilla.auraagent

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandUnderstandingTest {
    @Test
    fun decideOpenAppActionReturnsExecuteForHighConfidence() {
        val result =
            decideOpenAppAction(
                CommandUnderstanding(
                    intent = AuraCommandIntent.OPEN_APP,
                    target = "Facebook",
                    confidence = 0.96f,
                    source = CommandUnderstandingSource.LOCAL_AI
                )
            )

        assertEquals(CommandAction.EXECUTE, result)
    }

    @Test
    fun decideOpenAppActionReturnsConfirmForMediumConfidence() {
        val result =
            decideOpenAppAction(
                CommandUnderstanding(
                    intent = AuraCommandIntent.OPEN_APP,
                    target = "Messenger",
                    confidence = 0.75f,
                    source = CommandUnderstandingSource.LEARNED_PATTERN
                )
            )

        assertEquals(CommandAction.CONFIRM, result)
    }

    @Test
    fun decideOpenAppActionReturnsUnknownForLowConfidence() {
        val result =
            decideOpenAppAction(
                CommandUnderstanding(
                    intent = AuraCommandIntent.OPEN_APP,
                    target = "Instagram",
                    confidence = 0.40f,
                    source = CommandUnderstandingSource.UNKNOWN
                )
            )

        assertEquals(CommandAction.UNKNOWN, result)
    }
}
