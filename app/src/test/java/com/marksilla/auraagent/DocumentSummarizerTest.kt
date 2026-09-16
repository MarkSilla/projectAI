package com.marksilla.auraagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
        assertTrue(summary.executiveSummary.isNotBlank())
        assertTrue(summary.riskFlags.isNotEmpty())
        assertTrue(summary.recommendations.isNotEmpty())
        assertTrue(summary.overallAssessment.isNotBlank())
        assertTrue(summary.confidenceScore in 35..98)
        assertTrue(summary.actionDetails.isNotEmpty())
    }

    @Test
    fun removesRepeatedHeadersAndBoilerplateBeforeScoring() {
        val text =
            """
            PROJECT HEADER
            The report identifies a critical budget risk that requires immediate review.
            PROJECT HEADER
            The assigned team must submit the corrective action before the deadline.
            PROJECT HEADER
            The recommendation is to monitor the project timeline and approve the revised plan.
            All rights reserved. Do not distribute.
            """.trimIndent()

        val summary =
            summarizeDocumentText(
                title = "Project Report.txt",
                rawText = text
            )

        assertNotNull(summary)
        requireNotNull(summary)
        val scoredText =
            (summary.keyPoints + summary.reviewerNotes + summary.actionItems)
                .joinToString(" ")
        assertTrue(!scoredText.contains("PROJECT HEADER", ignoreCase = true))
        assertTrue(!scoredText.contains("all rights reserved", ignoreCase = true))
        assertTrue(scoredText.contains("budget", ignoreCase = true))
    }

    @Test
    fun appliesSelectedReviewFocus() {
        val text =
            "The project has a serious budget risk. " +
                "The assigned team must submit a correction by Friday. " +
                "The research method explains the study results and evidence."

        val summary =
            summarizeDocumentText(
                title = "Focused review.txt",
                rawText = text,
                options = SummaryOptions(mode = ReviewMode.ACTIONS)
            )

        assertNotNull(summary)
        requireNotNull(summary)
        assertEquals(ReviewMode.ACTIONS, summary.reviewMode)
        assertTrue(summary.actionItems.any { it.contains("submit", ignoreCase = true) })
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

    @Test
    fun extractsDocxDocumentText() {
        val docxBytes =
            ByteArrayOutputStream()
                .also { output ->
                    ZipOutputStream(output).use { zip ->
                        zip.putNextEntry(
                            ZipEntry("word/document.xml")
                        )
                        zip.write(
                            """
                            <?xml version="1.0" encoding="UTF-8"?>
                            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                                <w:body>
                                    <w:p>
                                        <w:r><w:t>AURA can summarize DOCX notes.</w:t></w:r>
                                    </w:p>
                                    <w:p>
                                        <w:r><w:t>Reviewer exports should stay readable.</w:t></w:r>
                                    </w:p>
                                </w:body>
                            </w:document>
                            """.trimIndent()
                                .toByteArray()
                        )
                        zip.closeEntry()
                    }
                }
                .toByteArray()

        val text =
            extractDocxText(
                inputStream = ByteArrayInputStream(docxBytes)
            )

        assertTrue(text.contains("AURA can summarize DOCX notes."))
        assertTrue(text.contains("Reviewer exports should stay readable."))
    }

    @Test
    fun formatsReviewerMarkdown() {
        val summary =
            DocumentSummary(
                title = "Study Notes.docx",
                wordCount = 220,
                readingTimeMinutes = 1,
                keyPoints = listOf("Focus on the main idea."),
                reviewerNotes = listOf("Review the evidence."),
                actionItems = listOf("Submit the reviewer."),
                keywords = listOf("study", "reviewer"),
                executiveSummary = "The document covers the main study findings.",
                overallAssessment = "Stable review: the main findings are coherent and easy to follow.",
                confidenceScore = 84,
                actionDetails = listOf(
                    ReviewAction(
                        action = "Submit the reviewer.",
                        owner = "Study team",
                        deadline = "Friday",
                        priority = "High"
                    )
                )
            )

        val markdown =
            formatReviewerMarkdown(
                summary = summary,
                webResults = listOf(
                    WebSearchResult(
                        title = "Study reference",
                        url = "https://example.com/study",
                        snippet = "Related context."
                    )
                )
            )

        assertTrue(markdown.contains("# Study Notes.docx"))
        assertTrue(markdown.contains("## Key Points"))
        assertTrue(markdown.contains("## Executive Summary"))
        assertTrue(markdown.contains("## Overall Assessment"))
        assertTrue(markdown.contains("## Action Details"))
        assertTrue(markdown.contains("## Related Web Information"))
        assertTrue(markdown.contains("https://example.com/study"))
        assertTrue(markdown.contains("- Focus on the main idea."))
        assertEquals(
            "Study Notes Reviewer.md",
            suggestedReviewerFileName(summary.title)
        )
    }

    @Test
    fun linksReviewFindingsToDocumentPages() {
        val summary =
            summarizeDocumentText(
                title = "Report.pdf",
                rawText =
                    "The report identifies a critical budget risk and recommends immediate review. " +
                        "The team should approve the corrective action before the deadline. " +
                        "Additional evidence confirms the issue affects the project timeline.",
                pages = listOf(
                    DocumentPage(
                        pageNumber = 1,
                        text = "The report identifies a critical budget risk and recommends immediate review."
                    ),
                    DocumentPage(
                        pageNumber = 2,
                        text = "The team should approve the corrective action before the deadline."
                    )
                )
            )

        assertNotNull(summary)
        requireNotNull(summary)
        assertTrue(summary.evidence.isNotEmpty())
        assertTrue(summary.evidence.all { it.pageNumber in 1..2 })
        assertTrue(summary.evidence.all { it.sourceSentence.isNotBlank() })
        assertTrue(summary.evidence.all { it.confidenceScore in 60..98 })
    }

    @Test
    fun extractsActionOwnerDeadlineAndPriority() {
        val summary =
            summarizeDocumentText(
                title = "Action Report.txt",
                rawText =
                    "The project review identifies a budget risk. " +
                        "The action is assigned to Maria Santos and must be completed by Friday. " +
                        "The team should review the evidence before approval.",
                options = SummaryOptions(mode = ReviewMode.ACTIONS)
            )

        assertNotNull(summary)
        requireNotNull(summary)
        val action = summary.actionDetails.firstOrNull()
        assertNotNull(action)
        requireNotNull(action)
        assertEquals("Maria Santos", action.owner)
        assertTrue(action.deadline.orEmpty().contains("Friday", ignoreCase = true))
        assertEquals("High", action.priority)
    }

    @Test
    fun extractsNumericAndRelativeDeadlines() {
        val summary =
            summarizeDocumentText(
                title = "Deadlines.txt",
                rawText =
                    "The team must submit the report by 09/30/2026. " +
                        "The reviewer should complete approval by next Friday. " +
                        "The owner must archive the evidence before October 5, 2026."
            )

        assertNotNull(summary)
        requireNotNull(summary)
        val deadlines = summary.actionDetails.mapNotNull { it.deadline }
        assertTrue(deadlines.any { it.contains("09/30/2026") })
        assertTrue(deadlines.any { it.contains("next Friday", ignoreCase = true) })
        assertTrue(deadlines.any { it.contains("October 5, 2026", ignoreCase = true) })
    }

    @Test
    fun requestsOcrWhenPdfTextIsTooSparse() {
        val sparseText = "\u0000 \u0001 \u0002 \u0003 "

        assertTrue(shouldAttemptOcr(sparseText))
        assertFalse(
            shouldAttemptOcr(
                "AURA can review this PDF and highlight the main points for the reviewer. " +
                    "The extracted text contains enough words and readable content for a reliable review summary. " +
                    "It includes the findings, evidence, recommendations, deadlines, risks, and next steps needed for analysis."
            )
        )
    }
}
