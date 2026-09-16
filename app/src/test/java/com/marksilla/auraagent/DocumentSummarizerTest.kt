package com.marksilla.auraagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSummarizerTest {
    @Test
    fun summarizesReadableReviewerDocument() {
        val text =
            """
            The project review explains that the mobile agent should help students prepare faster for exams and document reviews.
            The important finding is that students need short summaries, reviewer notes, and clear action items before they start studying.
            The current challenge is that long documents take too much time to scan and reviewers may miss deadlines.
            The recommendation is to highlight key points, risks, and required follow up work in a compact format.
            The team should test the summary on real study notes and revise the wording when the extracted notes are unclear.
            The conclusion is that a local first version is useful while a stronger AI backend can be added later.
            """.trimIndent()

        val summary =
            summarizeDocumentText(
                title = "Reviewer Notes.txt",
                rawText = text
            )

        assertNotNull(summary)
        requireNotNull(summary)
        assertEquals("Reviewer Notes.txt", summary.title)
        assertTrue(summary.keyPoints.isNotEmpty())
        assertTrue(summary.reviewerNotes.isNotEmpty())
        assertTrue(summary.actionItems.isNotEmpty())
        assertTrue(summary.keywords.isNotEmpty())
    }

    @Test
    fun rejectsTextWithoutEnoughReadableContent() {
        val summary =
            summarizeDocumentText(
                title = "empty.bin",
                rawText = "\u0000\u0001\u0002 short"
            )

        assertNull(summary)
    }
}
