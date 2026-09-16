package com.marksilla.auraagent

import kotlin.math.ceil

data class DocumentSummary(
    val title: String,
    val wordCount: Int,
    val readingTimeMinutes: Int,
    val keyPoints: List<String>,
    val reviewerNotes: List<String>,
    val actionItems: List<String>,
    val keywords: List<String>
)

fun summarizeDocumentText(
    title: String,
    rawText: String
): DocumentSummary? {
    val text =
        rawText
            .replace(Regex("[\\p{Cntrl}&&[^\\n\\t]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    val words =
        Regex("[A-Za-z][A-Za-z0-9'-]*")
            .findAll(text)
            .map {
                it.value.lowercase().trim('\'', '-')
            }
            .filter {
                it.length > 1
            }
            .toList()

    if (words.size < 35 || !hasReadableText(text)) {
        return null
    }

    val sentences =
        splitSentences(text)
            .filter {
                it.length >= 24
            }
            .take(80)

    if (sentences.isEmpty()) {
        return null
    }

    val frequencies =
        words
            .filterNot {
                it in stopWords
            }
            .groupingBy {
                it
            }
            .eachCount()

    val ranked =
        sentences
            .mapIndexed { index, sentence ->
                ScoredSentence(
                    index = index,
                    text = sentence,
                    score = scoreSentence(
                        sentence = sentence,
                        frequencies = frequencies
                    )
                )
            }
            .sortedByDescending {
                it.score
            }

    val keyPoints =
        ranked
            .take(5)
            .sortedBy {
                it.index
            }
            .map {
                it.text.compactSentence()
            }

    val reviewerNotes =
        sentences
            .filter {
                reviewerSignals.any { signal ->
                    it.contains(signal, ignoreCase = true)
                }
            }
            .take(4)
            .map {
                it.compactSentence()
            }
            .ifEmpty {
                keyPoints.take(3)
            }

    val actionItems =
        sentences
            .filter {
                actionSignals.any { signal ->
                    it.contains(signal, ignoreCase = true)
                }
            }
            .take(4)
            .map {
                it.compactSentence()
            }

    val keywords =
        frequencies
            .entries
            .sortedByDescending {
                it.value
            }
            .take(8)
            .map {
                it.key
            }

    return DocumentSummary(
        title = title.ifBlank {
            "Document"
        },
        wordCount = words.size,
        readingTimeMinutes =
            ceil(words.size / 220.0)
                .toInt()
                .coerceAtLeast(1),
        keyPoints = keyPoints,
        reviewerNotes = reviewerNotes,
        actionItems = actionItems,
        keywords = keywords
    )
}

private data class ScoredSentence(
    val index: Int,
    val text: String,
    val score: Double
)

private fun splitSentences(text: String): List<String> =
    text
        .replace(Regex("\\s+"), " ")
        .split(Regex("(?<=[.!?])\\s+|\\n+"))
        .map {
            it.trim()
        }
        .filter {
            it.isNotBlank()
        }

private fun scoreSentence(
    sentence: String,
    frequencies: Map<String, Int>
): Double {
    val sentenceWords =
        Regex("[A-Za-z][A-Za-z0-9'-]*")
            .findAll(sentence)
            .map {
                it.value.lowercase()
            }
            .filterNot {
                it in stopWords
            }
            .toList()

    if (sentenceWords.isEmpty()) {
        return 0.0
    }

    val frequencyScore =
        sentenceWords.sumOf {
            frequencies[it] ?: 0
        }.toDouble() / sentenceWords.size

    val signalBonus =
        if (
            reviewerSignals.any {
                sentence.contains(it, ignoreCase = true)
            }
        ) {
            1.5
        } else {
            0.0
        }

    val lengthPenalty =
        when {
            sentence.length < 45 -> 0.78
            sentence.length > 260 -> 0.72
            else -> 1.0
        }

    return (frequencyScore + signalBonus) * lengthPenalty
}

private fun String.compactSentence(): String {
    val clean =
        replace(Regex("\\s+"), " ")
            .trim()

    return if (clean.length <= 220) {
        clean
    } else {
        clean.take(217).trimEnd() + "..."
    }
}

private fun hasReadableText(text: String): Boolean {
    val sample = text.take(2000)

    if (sample.isBlank()) {
        return false
    }

    val letters =
        sample.count {
            it.isLetter()
        }

    val controls =
        sample.count {
            it.code in 0..8 || it.code in 14..31
        }

    return letters >= 80 && controls < 12
}

private val reviewerSignals =
    setOf(
        "important",
        "risk",
        "issue",
        "problem",
        "challenge",
        "require",
        "required",
        "recommend",
        "recommendation",
        "conclusion",
        "deadline",
        "result",
        "findings",
        "evidence",
        "limitation",
        "summary"
    )

private val actionSignals =
    setOf(
        "must",
        "should",
        "need to",
        "needs to",
        "required",
        "deadline",
        "submit",
        "review",
        "approve",
        "revise",
        "complete",
        "follow up",
        "action"
    )

private val stopWords =
    setOf(
        "about",
        "after",
        "again",
        "also",
        "and",
        "ang",
        "are",
        "because",
        "been",
        "before",
        "but",
        "can",
        "could",
        "did",
        "does",
        "for",
        "from",
        "had",
        "has",
        "have",
        "her",
        "his",
        "into",
        "its",
        "may",
        "mga",
        "not",
        "nang",
        "ng",
        "our",
        "out",
        "she",
        "should",
        "that",
        "the",
        "their",
        "there",
        "these",
        "they",
        "this",
        "those",
        "through",
        "was",
        "were",
        "with",
        "would",
        "you",
        "your",
        "yung"
    )
