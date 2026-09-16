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
                keywords = listOf("study", "reviewer")
            )

        val markdown = formatReviewerMarkdown(summary)

        assertTrue(markdown.contains("# Study Notes.docx"))
        assertTrue(markdown.contains("## Key Points"))
        assertTrue(markdown.contains("## Executive Summary"))
        assertTrue(markdown.contains("## Overall Assessment"))
        assertTrue(markdown.contains("- Focus on the main idea."))
        assertEquals(
            "Study Notes Reviewer.md",
            suggestedReviewerFileName(summary.title)
        )
    }

    @Test
    fun requestsOcrWhenPdfTextIsTooSparse() {
        val sparseText = "\u0000 \u0001 \u0002 \u0003 "

        assertTrue(shouldAttemptOcr(sparseText))
        assertFalse(
            shouldAttemptOcr(
                "AURA can review the PDF and highlight the main points for the reviewer."
            )
        )
    }
}
