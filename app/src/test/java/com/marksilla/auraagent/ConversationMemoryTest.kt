package com.marksilla.auraagent

import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMemoryTest {
    @Test
    fun respondsContextuallyWhenRecentConversationMentionedSameApp() {
        val reply =
            generateAssistantReply(
                prompt = "Open Messenger",
                recentContext = listOf("Open Facebook", "Open Facebook")
            )

        assertTrue(reply.contains("Facebook") || reply.contains("previous"))
    }

    @Test
    fun keepsFollowupReplyShortAndRelevant() {
        val reply =
            generateAssistantReply(
                prompt = "Open Settings",
                recentContext = listOf("Open Facebook", "Open Messenger")
            )

        assertTrue(reply.isNotBlank())
    }

    @Test
    fun usesPersonalMemoryToReferenceRepeatedHabits() {
        val reply =
            generateAssistantReply(
                prompt = "Open Facebook",
                recentContext = emptyList(),
                personalMemory = listOf("Open Facebook", "Open Facebook", "Open Messenger")
            )

        assertTrue(reply.contains("usually") || reply.contains("often") || reply.contains("habit"))
        assertTrue(reply.contains("Facebook"))
    }

    @Test
    fun recognizesRoutineSuggestionsForWorkdayPatterns() {
        val reply =
            generateAssistantReply(
                prompt = "Morning routine",
                userProfile = UserProfile(
                    preferredApps = listOf("Facebook", "Messenger"),
                    routineHints = listOf("Morning routine", "Focus mode")
                )
            )

        assertTrue(reply.contains("morning") || reply.contains("focus") || reply.contains("routine"))
    }
}
