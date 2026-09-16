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

    @Test
    fun asksForAppWhenOpenRequestHasNoTarget() {
        val reply = generateAssistantReply("Paki open")

        assertTrue(reply.contains("Which app", ignoreCase = true))
    }

    @Test
    fun understandsTaglishDocumentReviewRequest() {
        val reply = generateAssistantReply("Paki i-review itong PDF")

        assertTrue(reply.contains("review", ignoreCase = true))
        assertTrue(reply.contains("key points", ignoreCase = true))
    }

    @Test
    fun explainsCapabilitiesForCapabilityQuestion() {
        val reply = generateAssistantReply("Ano kaya mo?")

        assertTrue(reply.contains("open apps", ignoreCase = true))
        assertTrue(reply.contains("documents", ignoreCase = true))
    }

    @Test
    fun understandsWorkFollowUpFromRecentContext() {
        val reply =
            generateAssistantReply(
                prompt = "Yung pang-work",
                recentContext = listOf("Open Messenger")
            )

        assertTrue(reply.contains("work", ignoreCase = true))
        assertTrue(reply.contains("Messenger", ignoreCase = true))
    }
}
