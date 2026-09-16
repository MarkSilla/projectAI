package com.marksilla.auraagent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node

data class DocumentPage(
    val pageNumber: Int,
    val text: String
)

data class DocumentText(
    val text: String,
    val sourceType: String,
    val pages: List<DocumentPage> = emptyList()
)

fun readDocumentTextFromUri(
    context: Context,
    uri: Uri,
    displayName: String,
    maxChars: Int = 250_000
): DocumentText {
    val mimeType =
        context.contentResolver
            .getType(uri)
            .orEmpty()
            .lowercase(Locale.US)

    val lowerName =
        displayName.lowercase(Locale.US)

    return when {
        mimeType == "application/pdf" ||
            lowerName.endsWith(".pdf") ->
            readPdfDocument(
                context = context,
                uri = uri,
                maxChars = maxChars
            )

        mimeType ==
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            lowerName.endsWith(".docx") ->
            readDocxDocument(
                context = context,
                uri = uri,
                maxChars = maxChars
            )

        else ->
            readPlainDocument(
                context = context,
                uri = uri,
                maxChars = maxChars
            )
    }
}

fun extractDocxText(
    inputStream: InputStream,
    maxChars: Int = 250_000
): String {
    val builder = StringBuilder()

    ZipInputStream(inputStream).use { zip ->
        while (builder.length < maxChars) {
            val entry = zip.nextEntry ?: break

            if (
                !entry.isDirectory &&
                entry.name.startsWith("word/") &&
                entry.name.endsWith(".xml") &&
                wordTextEntryNames.any {
                    entry.name == it || entry.name.startsWith(it)
                }
            ) {
                val bytes = zip.readEntryBytes(maxChars)
                val entryText = extractWordXmlText(bytes)

                if (entryText.isNotBlank()) {
                    if (builder.isNotEmpty()) {
                        builder.append("\n\n")
                    }

                    builder.append(entryText)
                }
            }

            zip.closeEntry()
        }
    }

    return builder
        .toString()
        .take(maxChars)
}

fun formatReviewerMarkdown(summary: DocumentSummary): String =
    buildString {
        appendLine("# ${summary.title}")
        appendLine()
        appendLine("- Words: ${summary.wordCount}")
        appendLine("- Reading time: ${summary.readingTimeMinutes} min")
        appendLine()

        if (summary.executiveSummary.isNotBlank()) {
            appendLine("## Executive Summary")
            appendLine(summary.executiveSummary)
            appendLine()
        }

        appendLine("## Overall Assessment")
        appendLine(summary.overallAssessment)
        appendLine("- Confidence: ${summary.confidenceScore}/100")
        appendLine()

        if (summary.evidence.isNotEmpty()) {
            appendLine("## Evidence")
            summary.evidence.forEach { evidence ->
                appendLine("- Page ${evidence.pageNumber} (${evidence.confidenceScore}% confidence)")
                appendLine("  - Source: ${evidence.sourceSentence}")
            }
            appendLine()
        }

        appendLine("## Key Points")
        appendMarkdownBullets(summary.keyPoints)

        if (summary.riskFlags.isNotEmpty()) {
            appendLine()
            appendLine("## Risk Flags")
            appendMarkdownBullets(summary.riskFlags)
        }

        appendLine()
        appendLine("## Reviewer Notes")
        appendMarkdownBullets(summary.reviewerNotes)

        if (summary.actionItems.isNotEmpty()) {
            appendLine()
            appendLine("## Action Items")
            appendMarkdownBullets(summary.actionItems)
        }

        if (summary.actionDetails.isNotEmpty()) {
            appendLine()
            appendLine("## Action Details")
            summary.actionDetails.forEach { action ->
                appendLine("- ${action.action}")
                appendLine("  - Priority: ${action.priority}")
                action.owner?.let { appendLine("  - Owner: $it") }
                action.deadline?.let { appendLine("  - Deadline: $it") }
            }
        }

        if (summary.recommendations.isNotEmpty()) {
            appendLine()
            appendLine("## Recommendations")
            appendMarkdownBullets(summary.recommendations)
        }

        if (summary.keywords.isNotEmpty()) {
            appendLine()
            appendLine("## Keywords")
            appendLine(summary.keywords.joinToString(", "))
        }
    }

fun suggestedReviewerFileName(title: String): String {
    val base =
        title
            .substringBeforeLast('.')
            .replace(
                Regex("[^A-Za-z0-9 _-]"),
                " "
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
            .ifBlank {
                "AURA Reviewer"
            }
            .take(64)
            .trim()

    return "$base Reviewer.md"
}

private fun readPdfDocument(
    context: Context,
    uri: Uri,
    maxChars: Int
): DocumentText {
    PDFBoxResourceLoader.init(
        context.applicationContext
    )

    val text =
        context.contentResolver
            .openInputStream(uri)
            ?.use { input ->
                PDDocument.load(input).use { document ->
                    PDFTextStripper()
                        .getText(document)
                        .take(maxChars)
                }
            }
            .orEmpty()

    val pages =
        if (shouldAttemptOcr(text)) {
            emptyList()
        } else {
            readPdfPages(
                context = context,
                uri = uri,
                maxChars = maxChars
            )
        }

    val finalText =
        if (shouldAttemptOcr(text)) {
            val ocrText =
                runCatching {
                    readPdfWithOcr(
                        context = context,
                        uri = uri,
                        maxChars = maxChars
                    )
                }.getOrElse {
                    ""
                }

            if (ocrText.isNotBlank()) {
                ocrText
            } else {
                text
            }
        } else {
            text
        }

    return DocumentText(
        text = finalText,
        sourceType = "PDF",
        pages = pages
    )
}

private fun readPdfPages(
    context: Context,
    uri: Uri,
    maxChars: Int
): List<DocumentPage> {
    PDFBoxResourceLoader.init(context.applicationContext)

    return context.contentResolver
        .openInputStream(uri)
        ?.use { input ->
            PDDocument.load(input).use { document ->
                val stripper = PDFTextStripper()
                var remaining = maxChars

                (0 until document.numberOfPages).mapNotNull { index ->
                    if (remaining <= 0) {
                        return@mapNotNull null
                    }

                    stripper.startPage = index + 1
                    stripper.endPage = index + 1
                    val pageText = stripper.getText(document).trim()
                    val limitedText = pageText.take(remaining)
                    remaining -= limitedText.length

                    if (limitedText.isBlank()) {
                        null
                    } else {
                        DocumentPage(
                            pageNumber = index + 1,
                            text = limitedText
                        )
                    }
                }
            }
        }
        .orEmpty()
}

private fun readPdfWithOcr(
    context: Context,
    uri: Uri,
    maxChars: Int
): String {
    val descriptor =
        context.contentResolver
            .openFileDescriptor(uri, "r")
            ?: return ""

    val recognizer =
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

    return descriptor.use { parcelFileDescriptor ->
        PdfRenderer(parcelFileDescriptor).use { renderer ->
            buildString {
                for (pageIndex in 0 until renderer.pageCount) {
                    val page =
                        renderer.openPage(pageIndex)

                    val bitmap =
                        Bitmap.createBitmap(
                            page.width,
                            page.height,
                            Bitmap.Config.ARGB_8888
                        )

                    try {
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )

                        val image =
                            InputImage.fromBitmap(
                                bitmap,
                                0
                            )

                        val result =
                            Tasks.await(
                                recognizer.process(image)
                            )

                        val detectedText =
                            result.text.trim()

                        if (detectedText.isNotBlank()) {
                            if (isNotEmpty()) {
                                append("\n\n")
                            }

                            append(detectedText)
                        }
                    } finally {
                        page.close()
                        bitmap.recycle()
                    }

                    if (length >= maxChars) {
                        break
                    }
                }
            }.take(maxChars)
        }
    }
}

private fun readDocxDocument(
    context: Context,
    uri: Uri,
    maxChars: Int
): DocumentText {
    val text =
        context.contentResolver
            .openInputStream(uri)
            ?.use { input ->
                extractDocxText(
                    inputStream = input,
                    maxChars = maxChars
                )
            }
            .orEmpty()

    return DocumentText(
        text = text,
        sourceType = "DOCX"
    )
}

private fun readPlainDocument(
    context: Context,
    uri: Uri,
    maxChars: Int
): DocumentText {
    val builder = StringBuilder()

    context.contentResolver
        .openInputStream(uri)
        ?.bufferedReader()
        ?.use { reader ->
            val buffer = CharArray(4096)

            while (builder.length < maxChars) {
                val read = reader.read(buffer)

                if (read <= 0) {
                    break
                }

                val remaining =
                    maxChars - builder.length

                builder.append(
                    buffer,
                    0,
                    minOf(read, remaining)
                )
            }
        }

    return DocumentText(
        text = builder.toString(),
        sourceType = "Text"
    )
}

private fun ZipInputStream.readEntryBytes(
    maxChars: Int
): ByteArray {
    val output =
        ByteArrayOutputStream()

    val buffer =
        ByteArray(4096)

    var total = 0

    while (total < maxChars * 4) {
        val read = read(buffer)

        if (read <= 0) {
            break
        }

        val remaining =
            maxChars * 4 - total

        output.write(
            buffer,
            0,
            minOf(read, remaining)
        )

        total += read
    }

    return output.toByteArray()
}

private fun extractWordXmlText(
    bytes: ByteArray
): String {
    if (bytes.isEmpty()) {
        return ""
    }

    val factory =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                isNamespaceAware = true

                runCatching {
                    setFeature(
                        XMLConstants.FEATURE_SECURE_PROCESSING,
                        true
                    )
                }

                runCatching {
                    setFeature(
                        "http://apache.org/xml/features/disallow-doctype-decl",
                        true
                    )
                }

                runCatching {
                    setFeature(
                        "http://xml.org/sax/features/external-general-entities",
                        false
                    )
                }

                runCatching {
                    setFeature(
                        "http://xml.org/sax/features/external-parameter-entities",
                        false
                    )
                }
            }

    return runCatching {
        val document =
            factory
                .newDocumentBuilder()
                .parse(
                    ByteArrayInputStream(bytes)
                )

        val builder =
            StringBuilder()

        appendWordNodeText(
            node = document.documentElement,
            builder = builder
        )

        builder
            .toString()
            .replace(
                Regex("[ \\t]+"),
                " "
            )
            .replace(
                Regex("\\n{3,}"),
                "\n\n"
            )
            .trim()
    }.getOrDefault("")
}

private fun appendWordNodeText(
    node: Node,
    builder: StringBuilder
) {
    val name =
        node.localName
            ?: node.nodeName.substringAfter(':')

    when (name) {
        "t" -> {
            builder.append(node.textContent)
            return
        }

        "tab" -> {
            builder.append('\t')
            return
        }

        "br",
        "cr" -> {
            builder.append('\n')
            return
        }
    }

    val children =
        node.childNodes

    for (index in 0 until children.length) {
        appendWordNodeText(
            node = children.item(index),
            builder = builder
        )
    }

    if (name == "p") {
        builder.append('\n')
    }
}

private fun StringBuilder.appendMarkdownBullets(
    items: List<String>
) {
    if (items.isEmpty()) {
        return
    }

    items.forEach { item ->
        appendLine("- $item")
    }
}

private val wordTextEntryNames =
    listOf(
        "word/document.xml",
        "word/header",
        "word/footer",
        "word/footnotes.xml",
        "word/endnotes.xml"
    )
