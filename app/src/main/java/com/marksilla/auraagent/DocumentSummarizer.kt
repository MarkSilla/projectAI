package com.marksilla.auraagent

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.sqrt


data class DocumentSummary(
    val title: String,
    val wordCount: Int,
    val readingTimeMinutes: Int,
    val keyPoints: List<String>,
    val reviewerNotes: List<String>,
    val actionItems: List<String>,
    val keywords: List<String>,
    val executiveSummary: String = "",
    val riskFlags: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val overallAssessment: String = "Needs review",
    val confidenceScore: Int = 75,
    val evidence: List<ReviewEvidence> = emptyList(),
    val actionDetails: List<ReviewAction> = emptyList()
)

data class ReviewEvidence(
    val pageNumber: Int,
        val excerpt: String,
        val sourceSentence: String = excerpt,
        val confidenceScore: Int = 70
)

data class ReviewAction(
    val action: String,
    val owner: String? = null,
    val deadline: String? = null,
    val priority: String = "Medium"
)

data class SummaryOptions(
    val maxKeyPoints: Int? = null,
    val maxReviewerNotes: Int? = null,
    val maxActionItems: Int? = null,
    val maxKeywords: Int = 8,
    val readingWordsPerMinute: Int = 220,
    val adaptive: Boolean = true,
    val mode: ReviewMode = ReviewMode.GENERAL
)

enum class ReviewMode(
    val label: String
) {
    GENERAL("General"),
    EXECUTIVE("Executive"),
    RISKS("Risks"),
    ACTIONS("Actions"),
    STUDY("Study")
}

private data class ScoredSentence(
    val index: Int,
    val text: String,
    val score: Double
)

private data class DocumentProfile(
    val type: DocumentType,
    val hasSections: Boolean,
    val sentenceCount: Int,
    val averageSentenceLength: Double
)

private enum class DocumentType {
    ACADEMIC,
    REPORT,
    PROPOSAL,
    MEETING_NOTES,
    INSTRUCTION,
    GENERAL
}

fun summarizeDocumentText(
    title: String,
    rawText: String,
    options: SummaryOptions = SummaryOptions(),
    pages: List<DocumentPage> = emptyList()
): DocumentSummary? {

    val text = normalizeText(rawText)

    if (!hasReadableText(text)) {
        return null
    }

    val words = extractWords(text)

    if (words.size < 35) {
        return null
    }

    val sentences =
        splitSentences(text)
            .filter { it.length >= 20 }
            .take(600)

    if (sentences.isEmpty()) {
        return null
    }

    val focusedSentences =
        focusSentences(
            sentences = sentences,
            mode = options.mode
        )

    val profile =
        detectDocumentProfile(
            text = text,
            sentences = focusedSentences
        )

    val frequencies =
        words
            .filterNot { it in stopWords }
            .filter { it.length >= 3 }
            .groupingBy { it }
            .eachCount()

    val maxKeyPoints =
        options.maxKeyPoints
            ?: if (options.adaptive) {
                adaptiveKeyPointCount(words.size, focusedSentences.size)
            } else {
                5
            }

    val maxReviewerNotes =
        options.maxReviewerNotes
            ?: if (options.adaptive) {
                adaptiveReviewerNoteCount(focusedSentences.size)
            } else {
                4
            }

    val maxActionItems =
        options.maxActionItems
            ?: if (options.adaptive) {
                adaptiveActionItemCount(focusedSentences.size)
            } else {
                4
            }

    val ranked =
        focusedSentences
            .mapIndexed { index, sentence ->
                ScoredSentence(
                    index = index,
                    text = sentence,
                    score =
                        scoreSentence(
                            sentence = sentence,
                            index = index,
                            totalSentences = focusedSentences.size,
                            frequencies = frequencies,
                            profile = profile
                        )
                )
            }
            .sortedByDescending { it.score }

    val keyPoints =
        selectDiverseSentences(
            ranked = ranked,
            limit = maxKeyPoints
        )
            .sortedBy { it.index }
            .map { it.text.compactSentence() }

    val reviewerNotes =
        buildReviewerNotes(
            sentences = focusedSentences,
            ranked = ranked,
            limit = maxReviewerNotes,
            documentType = profile.type
        )

    val actionItems =
        buildActionItems(
            sentences = focusedSentences,
            limit = maxActionItems,
            documentType = profile.type
        )

    val keywords =
        extractDynamicKeywords(
            frequencies = frequencies,
            sentences = focusedSentences,
            maxKeywords = options.maxKeywords
        )

    val executiveSummary =
        buildExecutiveSummary(
            keyPoints = keyPoints,
            reviewerNotes = reviewerNotes,
            actionItems = actionItems
        )

    val riskFlags =
        buildRiskFlags(
            sentences = focusedSentences,
            ranked = ranked,
            limit = 4
        )

    val recommendations =
        buildRecommendations(
            actionItems = actionItems,
            reviewerNotes = reviewerNotes,
            limit = 4
        )

    val overallAssessment =
        buildOverallAssessment(
            riskFlags = riskFlags,
            actionItems = actionItems,
            keyPoints = keyPoints
        )

    val confidenceScore =
        calculateConfidenceScore(
            wordCount = words.size,
            sentenceCount = sentences.size,
            riskCount = riskFlags.size,
            actionCount = actionItems.size
        )

    val evidence =
        buildEvidence(
            items = (riskFlags + actionItems + keyPoints).distinct(),
            pages = pages,
            limit = 6
        )

    val actionDetails =
        buildActionDetails(
            sentences = sentences,
            limit = maxActionItems
        )

    return DocumentSummary(
        title =
            title
                .trim()
                .ifBlank {
                    "Document"
                },

        wordCount = words.size,

        readingTimeMinutes =
            ceil(
                words.size /
                    options.readingWordsPerMinute
                        .coerceAtLeast(100)
                        .toDouble()
            )
                .toInt()
                .coerceAtLeast(1),

        keyPoints = keyPoints,

        reviewerNotes = reviewerNotes,

        actionItems = actionItems,

        keywords = keywords,

        executiveSummary = executiveSummary,

        riskFlags = riskFlags,

        recommendations = recommendations,

        overallAssessment = overallAssessment,

        confidenceScore = confidenceScore,

        evidence = evidence,

        actionDetails = actionDetails
    )
}

private fun buildEvidence(
    items: List<String>,
    pages: List<DocumentPage>,
    limit: Int
): List<ReviewEvidence> {
    if (pages.isEmpty()) {
        return emptyList()
    }

    return items
        .mapNotNull { item ->
            val itemWords =
                extractWords(item)
                    .filterNot { it in stopWords }
                    .toSet()

            pages
                .map { page ->
                    val pageWords = extractWords(page.text).toSet()
                    page to itemWords.intersect(pageWords).size
                }
                .maxByOrNull { it.second }
                ?.takeIf { it.second > 0 }
                ?.let { (page, overlap) ->
                    ReviewEvidence(
                        pageNumber = page.pageNumber,
                        excerpt = page.text.compactSentence(),
                        sourceSentence = findSourceSentence(
                            item = item,
                            pageText = page.text
                        ),
                        confidenceScore =
                            (60 + overlap * 8).coerceAtMost(98)
                    )
                }
        }
        .distinctBy { "${it.pageNumber}:${normalizeForComparison(it.excerpt)}" }
        .take(limit)
}

private fun findSourceSentence(
    item: String,
    pageText: String
): String {
        val itemWords =
            extractWords(item)
                .filterNot { it in stopWords }
                .toSet()

        return splitSentences(pageText)
            .maxByOrNull { sentence ->
                itemWords.intersect(extractWords(sentence).toSet()).size
            }
            ?.compactSentence()
            ?: pageText.compactSentence()
    }

private fun buildActionDetails(
    sentences: List<String>,
    limit: Int
): List<ReviewAction> {
    return sentences
        .filter { sentence ->
            actionSignals.any { signal ->
                sentence.contains(signal, ignoreCase = true)
            }
        }
        .distinctBy { normalizeForComparison(it) }
        .take(limit)
        .map { sentence ->
            val owner =
                Regex(
                    "(?:assigned to|owner is|responsible for|responsible:?)\\s+([A-Z][A-Za-z]+(?:\\s+[A-Z][A-Za-z]+)*)"
                )
                    .find(sentence)
                    ?.groupValues
                    ?.getOrNull(1)

            val deadline = extractDeadline(sentence)

            val priority =
                when {
                    listOf("urgent", "critical", "asap", "immediately", "deadline")
                        .any { sentence.contains(it, ignoreCase = true) } -> "High"

                    listOf("should", "recommend", "review", "follow up")
                        .any { sentence.contains(it, ignoreCase = true) } -> "Medium"

                    else -> "Low"
                }

            ReviewAction(
                action = sentence.compactSentence(),
                owner = owner,
                deadline = deadline,
                priority = priority
            )
        }
}

/* =========================================================
   TEXT NORMALIZATION
   ========================================================= */

private fun normalizeText(rawText: String): String =
    rawText
        .replace(
            Regex("[\\p{Cntrl}&&[^\\n\\t]]"),
            " "
        )
        .replace(
            Regex("[ \\t]+"),
            " "
        )
        .replace(
            Regex("\\n{3,}"),
            "\n\n"
        )
        .trim()

private fun focusSentences(
    sentences: List<String>,
    mode: ReviewMode
): List<String> {
    val focused =
        when (mode) {
            ReviewMode.GENERAL -> sentences

            ReviewMode.EXECUTIVE ->
                (sentences.take(80) + sentences.takeLast(40)).distinct()

            ReviewMode.RISKS ->
                sentences.filter { sentence ->
                    reviewerSignals.any {
                        sentence.contains(it, ignoreCase = true)
                    }
                }

            ReviewMode.ACTIONS ->
                sentences.filter { sentence ->
                    actionSignals.any {
                        sentence.contains(it, ignoreCase = true)
                    }
                }

            ReviewMode.STUDY ->
                sentences.filter { sentence ->
                    academicSignals.any {
                        sentence.contains(it, ignoreCase = true)
                    }
                }
        }

    return if (focused.size >= 3) {
        focused
    } else {
        sentences
    }
}

/* =========================================================
   WORD EXTRACTION
   ========================================================= */

private fun extractWords(text: String): List<String> =
    Regex("[A-Za-z][A-Za-z0-9'-]*")
        .findAll(text)
        .map {
            it.value
                .lowercase()
                .trim('\'', '-')
        }
        .filter {
            it.length > 1
        }
        .toList()

/* =========================================================
   SENTENCE SPLITTING
   ========================================================= */

private fun splitSentences(text: String): List<String> =
    text
        .replace(
            Regex("[ \\t]+"),
            " "
        )
        .split(
            Regex("(?<=[.!?])\\s+|\\n+")
        )
        .map {
            it
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim()
        }
        .filter {
            it.isNotBlank()
        }

/* =========================================================
   DOCUMENT PROFILE
   ========================================================= */

private fun detectDocumentProfile(
    text: String,
    sentences: List<String>
): DocumentProfile {

    val lower = text.lowercase()

    val academicScore =
        countMatches(
            lower,
            academicSignals
        )

    val reportScore =
        countMatches(
            lower,
            reportSignals
        )

    val proposalScore =
        countMatches(
            lower,
            proposalSignals
        )

    val meetingScore =
        countMatches(
            lower,
            meetingSignals
        )

    val instructionScore =
        countMatches(
            lower,
            instructionSignals
        )

    val type =
        when {
            academicScore >= reportScore &&
                academicScore >= proposalScore &&
                academicScore >= meetingScore &&
                academicScore >= instructionScore &&
                academicScore >= 3 ->
                DocumentType.ACADEMIC

            reportScore >= proposalScore &&
                reportScore >= meetingScore &&
                reportScore >= instructionScore &&
                reportScore >= 3 ->
                DocumentType.REPORT

            proposalScore >= meetingScore &&
                proposalScore >= instructionScore &&
                proposalScore >= 3 ->
                DocumentType.PROPOSAL

            meetingScore >= instructionScore &&
                meetingScore >= 2 ->
                DocumentType.MEETING_NOTES

            instructionScore >= 2 ->
                DocumentType.INSTRUCTION

            else ->
                DocumentType.GENERAL
        }

    val averageLength =
        if (sentences.isEmpty()) {
            0.0
        } else {
            sentences
                .map { extractWords(it).size }
                .average()
        }

    return DocumentProfile(
        type = type,
        hasSections = detectSections(text),
        sentenceCount = sentences.size,
        averageSentenceLength = averageLength
    )
}

private fun countMatches(
    text: String,
    signals: Set<String>
): Int =
    signals.count {
        Regex(
            "\\b${Regex.escape(it)}\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
    }

private fun detectSections(text: String): Boolean {
    val lines =
        text
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

    return lines.count { line ->
        line.length in 3..100 &&
            (
                line.matches(
                    Regex(
                        "^[A-Z][A-Z0-9\\s:&/-]{2,}$"
                    )
                ) ||
                    line.matches(
                        Regex(
                            "^\\d+(\\.\\d+)*[.)]?\\s+.+"
                        )
                    )
            )
    } >= 2
}

/* =========================================================
   ADAPTIVE COUNTS
   ========================================================= */

private fun adaptiveKeyPointCount(
    wordCount: Int,
    sentenceCount: Int
): Int =
    when {
        wordCount < 150 -> 2
        wordCount < 400 -> 3
        wordCount < 900 -> 4
        wordCount < 1800 -> 5
        wordCount < 3500 -> 6
        sentenceCount > 100 -> 8
        else -> 7
    }

private fun adaptiveReviewerNoteCount(
    sentenceCount: Int
): Int =
    when {
        sentenceCount < 8 -> 2
        sentenceCount < 20 -> 3
        sentenceCount < 50 -> 4
        sentenceCount < 100 -> 5
        else -> 6
    }

private fun adaptiveActionItemCount(
    sentenceCount: Int
): Int =
    when {
        sentenceCount < 10 -> 2
        sentenceCount < 30 -> 3
        sentenceCount < 80 -> 4
        else -> 6
    }

/* =========================================================
   SENTENCE SCORING
   ========================================================= */

private fun scoreSentence(
    sentence: String,
    index: Int,
    totalSentences: Int,
    frequencies: Map<String, Int>,
    profile: DocumentProfile
): Double {

    val sentenceWords =
        extractWords(sentence)
            .filterNot {
                it in stopWords
            }

    if (sentenceWords.isEmpty()) {
        return 0.0
    }

    /*
     * 1. Frequency score
     */

    val frequencyScore =
        sentenceWords
            .sumOf {
                frequencies[it] ?: 0
            }
            .toDouble() /
            sentenceWords.size

    /*
     * 2. TF-IDF-like rarity bonus
     *
     * Words that occur often but not everywhere
     * become more valuable.
     */

    val rarityScore =
        sentenceWords
            .distinct()
            .sumOf { word ->

                val frequency =
                    frequencies[word] ?: 1

                1.0 /
                    sqrt(
                        frequency.toDouble()
                    )
            }

    /*
     * 3. Position score
     *
     * Important information is frequently found
     * near the beginning or end of documents.
     */

    val positionRatio =
        if (totalSentences <= 1) {
            0.5
        } else {
            index.toDouble() /
                (totalSentences - 1)
        }

    val positionBonus =
        when {
            index <= 1 -> 1.4
            positionRatio >= 0.85 -> 1.3
            positionRatio in 0.15..0.75 -> 0.5
            else -> 0.2
        }

    /*
     * 4. Signal bonus
     */

    val signalBonus =
        calculateSignalBonus(
            sentence = sentence,
            documentType = profile.type
        )

    /*
     * 5. Number / data bonus
     */

    val numberBonus =
        if (
            Regex(
                "\\b\\d+(?:\\.\\d+)?%?\\b"
            ).containsMatchIn(sentence)
        ) {
            0.7
        } else {
            0.0
        }

    /*
     * 6. Length optimization
     */

    val wordCount =
        sentenceWords.size

    val lengthScore =
        when {
            wordCount < 5 -> 0.45
            wordCount < 8 -> 0.75
            wordCount in 8..35 -> 1.15
            wordCount in 36..55 -> 1.0
            wordCount in 56..75 -> 0.8
            else -> 0.6
        }

    /*
     * 7. Heading / section-like sentence
     */

    val headingBonus =
        if (
            sentence.matches(
                Regex(
                    "^[A-Z][A-Za-z0-9\\s:&/-]{3,80}:?$"
                )
            )
        ) {
            0.5
        } else {
            0.0
        }

    /*
     * 8. Document-specific bonus
     */

    val typeBonus =
        when (profile.type) {

            DocumentType.ACADEMIC ->
                if (
                    academicImportantSignals.any {
                        sentence.contains(
                            it,
                            ignoreCase = true
                        )
                    }
                ) 1.0 else 0.0

            DocumentType.REPORT ->
                if (
                    reportImportantSignals.any {
                        sentence.contains(
                            it,
                            ignoreCase = true
                        )
                    }
                ) 1.0 else 0.0

            DocumentType.PROPOSAL ->
                if (
                    proposalImportantSignals.any {
                        sentence.contains(
                            it,
                            ignoreCase = true
                        )
                    }
                ) 1.0 else 0.0

            DocumentType.MEETING_NOTES ->
                if (
                    meetingImportantSignals.any {
                        sentence.contains(
                            it,
                            ignoreCase = true
                        )
                    }
                ) 1.0 else 0.0

            DocumentType.INSTRUCTION ->
                if (
                    instructionImportantSignals.any {
                        sentence.contains(
                            it,
                            ignoreCase = true
                        )
                    }
                ) 1.0 else 0.0

            DocumentType.GENERAL ->
                0.0
        }

    return (
        frequencyScore +
            rarityScore * 0.8 +
            positionBonus +
            signalBonus +
            numberBonus +
            headingBonus +
            typeBonus
        ) * lengthScore
}

/* =========================================================
   SIGNAL SCORING
   ========================================================= */

private fun calculateSignalBonus(
    sentence: String,
    documentType: DocumentType
): Double {

    var score = 0.0

    if (
        reviewerSignals.any {
            sentence.contains(
                it,
                ignoreCase = true
            )
        }
    ) {
        score += 1.5
    }

    if (
        actionSignals.any {
            sentence.contains(
                it,
                ignoreCase = true
            )
        }
    ) {
        score += 1.2
    }

    if (
        conclusionSignals.any {
            sentence.contains(
                it,
                ignoreCase = true
            )
        }
    ) {
        score += 1.0
    }

    if (
        questionSignals.any {
            sentence.contains(
                it,
                ignoreCase = true
            )
        }
    ) {
        score += 0.5
    }

    return score
}

/* =========================================================
   DIVERSE SENTENCE SELECTION
   ========================================================= */

private fun selectDiverseSentences(
    ranked: List<ScoredSentence>,
    limit: Int
): List<ScoredSentence> {

    val selected =
        mutableListOf<ScoredSentence>()

    for (candidate in ranked) {

        if (selected.size >= limit) {
            break
        }

        val tooSimilar =
            selected.any { existing ->

                sentenceSimilarity(
                    existing.text,
                    candidate.text
                ) >= 0.65
            }

        if (!tooSimilar) {
            selected += candidate
        }
    }

    return selected
}

private fun sentenceSimilarity(
    first: String,
    second: String
): Double {

    val firstWords =
        extractWords(first)
            .filterNot { it in stopWords }
            .toSet()

    val secondWords =
        extractWords(second)
            .filterNot { it in stopWords }
            .toSet()

    if (
        firstWords.isEmpty() ||
        secondWords.isEmpty()
    ) {
        return 0.0
    }

    val intersection =
        firstWords
            .intersect(secondWords)
            .size

    val union =
        firstWords
            .union(secondWords)
            .size

    return if (union == 0) {
        0.0
    } else {
        intersection.toDouble() / union
    }
}

/* =========================================================
   REVIEWER NOTES
   ========================================================= */

private fun buildReviewerNotes(
    sentences: List<String>,
    ranked: List<ScoredSentence>,
    limit: Int,
    documentType: DocumentType
): List<String> {

    val signals =
        when (documentType) {

            DocumentType.ACADEMIC ->
                academicReviewerSignals

            DocumentType.REPORT ->
                reportReviewerSignals

            DocumentType.PROPOSAL ->
                proposalReviewerSignals

            DocumentType.MEETING_NOTES ->
                meetingReviewerSignals

            DocumentType.INSTRUCTION ->
                instructionReviewerSignals

            DocumentType.GENERAL ->
                reviewerSignals
        }

    val signalMatches =
        sentences
            .filter { sentence ->
                signals.any {
                    sentence.contains(
                        it,
                        ignoreCase = true
                    )
                }
            }

    val selected =
        selectDiverseSentences(
            ranked =
                signalMatches
                    .mapIndexed { index, sentence ->
                        ScoredSentence(
                            index = index,
                            text = sentence,
                            score =
                                calculateSignalBonus(
                                    sentence,
                                    documentType
                                )
                        )
                    }
                    .sortedByDescending {
                        it.score
                    },
            limit = limit
        )

    return if (selected.isNotEmpty()) {
        selected
            .map {
                it.text.compactSentence()
            }
    } else {
        ranked
            .take(limit)
            .map {
                it.text.compactSentence()
            }
    }
}

/* =========================================================
   ACTION ITEMS
   ========================================================= */

private fun buildActionItems(
    sentences: List<String>,
    limit: Int,
    documentType: DocumentType
): List<String> {

    val matches =
        sentences
            .filter { sentence ->

                actionSignals.any {
                    sentence.contains(
                        it,
                        ignoreCase = true
                    )
                } ||

                    when (documentType) {

                        DocumentType.ACADEMIC ->
                            academicActionSignals.any {
                                sentence.contains(
                                    it,
                                    ignoreCase = true
                                )
                            }

                        DocumentType.REPORT ->
                            reportActionSignals.any {
                                sentence.contains(
                                    it,
                                    ignoreCase = true
                                )
                            }

                        DocumentType.PROPOSAL ->
                            proposalActionSignals.any {
                                sentence.contains(
                                    it,
                                    ignoreCase = true
                                )
                            }

                        DocumentType.MEETING_NOTES ->
                            meetingActionSignals.any {
                                sentence.contains(
                                    it,
                                    ignoreCase = true
                                )
                            }

                        DocumentType.INSTRUCTION ->
                            instructionActionSignals.any {
                                sentence.contains(
                                    it,
                                    ignoreCase = true
                                )
                            }

                        DocumentType.GENERAL ->
                            false
                    }
            }

    return matches
        .distinctBy {
            normalizeForComparison(it)
        }

    private fun extractDeadline(sentence: String): String? {
        val patterns =
            listOf(
                "(?:by|before|on|deadline(?: is|:)?)\\s+((?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)(?:\\s+morning|\\s+afternoon|\\s+evening)?)",
                "(?:by|before|on|deadline(?: is|:)?)\\s+((?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{1,2}(?:,?\\s+\\d{4})?)",
                "(?:by|before|on|deadline(?: is|:)?)\\s+(\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?)",
                "(?:by|before|on|deadline(?: is|:)?)\\s+(next\\s+(?:week|month|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday))"
            )

        return patterns
            .asSequence()
            .map { Regex(it, RegexOption.IGNORE_CASE).find(sentence) }
            .filterNotNull()
            .mapNotNull { it.groupValues.getOrNull(1)?.trim() }
            .firstOrNull()
    }
        .take(limit)
        .map {
            it.compactSentence()
        }
}

private fun buildExecutiveSummary(
    keyPoints: List<String>,
    reviewerNotes: List<String>,
    actionItems: List<String>
): String {
    val lead =
        keyPoints
            .firstOrNull()
            ?.compactSentence()
            ?: reviewerNotes
                .firstOrNull()
                ?.compactSentence()
            ?: "The document presents a clear review of the main topic and next steps."

    val note =
        when {
            actionItems.isNotEmpty() ->
                "The review highlights immediate action items and follow-up priorities."

            reviewerNotes.isNotEmpty() ->
                "The review focuses on key findings, constraints, and important context."

            else ->
                "The document is structured around a concise set of practical conclusions."
        }

    return "$lead $note"
}

private fun buildRiskFlags(
    sentences: List<String>,
    ranked: List<ScoredSentence>,
    limit: Int
): List<String> {
    val riskWords =
        setOf(
            "risk",
            "issue",
            "problem",
            "challenge",
            "concern",
            "limitation",
            "failure",
            "deadline",
            "delay",
            "warning",
            "vulnerability"
        )

    val matches =
        sentences
            .filter { sentence ->
                riskWords.any {
                    sentence.contains(it, ignoreCase = true)
                }
            }
            .distinctBy { normalizeForComparison(it) }
            .take(limit)
            .map { it.compactSentence() }

    return if (matches.isNotEmpty()) {
        matches
    } else {
        ranked
            .take(limit)
            .map { it.text.compactSentence() }
    }
}

private fun buildRecommendations(
    actionItems: List<String>,
    reviewerNotes: List<String>,
    limit: Int
): List<String> {
    val direct =
        actionItems
            .ifEmpty {
                reviewerNotes
            }
            .take(limit)

    return direct
        .map { it.compactSentence() }
        .distinctBy { normalizeForComparison(it) }
        .take(limit)
}

private fun buildOverallAssessment(
    riskFlags: List<String>,
    actionItems: List<String>,
    keyPoints: List<String>
): String {
    return when {
        riskFlags.size >= 3 && actionItems.isNotEmpty() ->
            "High-priority review: significant risks and follow-up actions are identified."

        riskFlags.isNotEmpty() ->
            "Moderate review: some issues need attention before final approval."

        actionItems.isNotEmpty() && keyPoints.size >= 3 ->
            "Positive review: the document is clear, actionable, and well structured."

        keyPoints.isNotEmpty() ->
            "Stable review: the main findings are coherent and easy to follow."

        else ->
            "Needs review: the document is not yet clear enough for confident decisions."
    }
}

private fun calculateConfidenceScore(
    wordCount: Int,
    sentenceCount: Int,
    riskCount: Int,
    actionCount: Int
): Int {
    var score = 72

    score += when {
        wordCount >= 600 -> 16
        wordCount >= 250 -> 10
        wordCount >= 120 -> 6
        else -> 2
    }

    score += when {
        sentenceCount >= 25 -> 8
        sentenceCount >= 12 -> 5
        else -> 2
    }

    score -= riskCount * 5
    score += actionCount * 3

    return score.coerceIn(35, 98)
}

/* =========================================================
   KEYWORD EXTRACTION
   ========================================================= */

private fun extractDynamicKeywords(
    frequencies: Map<String, Int>,
    sentences: List<String>,
    maxKeywords: Int
): List<String> {

    val sentenceCount = sentences.size

    val scored =
        frequencies
            .map { (word, frequency) ->

                val documentFrequency =
                    sentences.count { sentence ->
                        word in extractWords(sentence)
                    }

                val tf =
                    ln(
                        1.0 +
                            frequency.toDouble()
                    )

                val idf =
                    ln(
                        (
                            sentenceCount + 1
                        ).toDouble() /
                            (
                                documentFrequency + 1
                            )
                    ) + 1.0

                val lengthBonus =
                    when {
                        word.length >= 8 -> 1.15
                        word.length >= 6 -> 1.05
                        else -> 1.0
                    }

                val score =
                    tf *
                        idf *
                        lengthBonus

                word to score
            }
            .sortedByDescending {
                it.second
            }

    return scored
        .take(maxKeywords)
        .map {
            it.first
        }
}

/* =========================================================
   TEXT QUALITY
   ========================================================= */

private fun hasReadableText(
    text: String
): Boolean {

    val sample =
        text.take(3000)

    if (sample.isBlank()) {
        return false
    }

    val letters =
        sample.count {
            it.isLetter()
        }

    val digits =
        sample.count {
            it.isDigit()
        }

    val controls =
        sample.count {
            it.code in 0..8 ||
                it.code in 14..31
        }

    val words =
        extractWords(sample).size

    return (
        letters >= 80 &&
            words >= 25 &&
            controls < 12
        ) ||
        (
            letters >= 50 &&
                digits >= 10 &&
                words >= 20
        )
}

/* =========================================================
   OCR DETECTION
   ========================================================= */

fun shouldAttemptOcr(
    rawText: String
): Boolean {

    val normalized =
        rawText
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()

    if (normalized.isBlank()) {
        return true
    }

    val letters =
        normalized.count {
            it.isLetter()
        }

    val words =
        Regex("[A-Za-z]{3,}")
            .findAll(normalized)
            .count()

    val averageWordLength =
        if (words == 0) {
            0.0
        } else {
            letters.toDouble() / words
        }

    return (
        words < 35 ||
            letters < 80 ||
            averageWordLength < 2.2
        )
}

/* =========================================================
   STRING HELPERS
   ========================================================= */

private fun String.compactSentence(): String {

    val clean =
        replace(
            Regex("\\s+"),
            " "
        )
            .trim()

    return when {
        clean.length <= 220 ->
            clean

        else ->
            clean
                .take(217)
                .trimEnd() +
                "..."
    }
}

private fun normalizeForComparison(
    text: String
): String =
    text
        .lowercase()
        .replace(
            Regex("[^a-z0-9\\s]"),
            ""
        )
        .replace(
            Regex("\\s+"),
            " "
        )
        .trim()

/* =========================================================
   SIGNAL SETS
   ========================================================= */

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
        "results",
        "finding",
        "findings",
        "evidence",
        "limitation",
        "limitations",
        "summary",
        "significant",
        "impact",
        "concern",
        "failure",
        "success"
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
        "follow-up",
        "action",
        "implement",
        "prepare",
        "update",
        "create",
        "develop",
        "conduct",
        "ensure"
    )

private val conclusionSignals =
    setOf(
        "in conclusion",
        "to conclude",
        "overall",
        "therefore",
        "thus",
        "finally",
        "in summary",
        "ultimately",
        "the study shows",
        "the results show",
        "the findings show"
    )

private val questionSignals =
    setOf(
        "why",
        "how",
        "whether",
        "what",
        "question",
        "objective",
        "purpose",
        "goal"
    )

/* =========================================================
   DOCUMENT TYPE SIGNALS
   ========================================================= */

private val academicSignals =
    setOf(
        "research",
        "study",
        "methodology",
        "literature",
        "researcher",
        "hypothesis",
        "variables",
        "respondents",
        "participants",
        "data",
        "analysis",
        "findings",
        "discussion",
        "conclusion",
        "abstract",
        "references"
    )

private val reportSignals =
    setOf(
        "report",
        "findings",
        "results",
        "incident",
        "issue",
        "analysis",
        "recommendation",
        "observation",
        "summary",
        "status",
        "performance",
        "evaluation"
    )

private val proposalSignals =
    setOf(
        "proposal",
        "proposed",
        "project",
        "budget",
        "objective",
        "timeline",
        "implementation",
        "solution",
        "resources",
        "scope",
        "deliverables",
        "recommendation"
    )

private val meetingSignals =
    setOf(
        "meeting",
        "agenda",
        "attendees",
        "discussion",
        "minutes",
        "decision",
        "assigned",
        "follow-up",
        "action item",
        "next meeting",
        "deadline"
    )

private val instructionSignals =
    setOf(
        "step",
        "steps",
        "instructions",
        "procedure",
        "follow",
        "click",
        "select",
        "enter",
        "install",
        "configure",
        "complete",
        "submit"
    )

/* =========================================================
   DOCUMENT-SPECIFIC IMPORTANT SIGNALS
   ========================================================= */

private val academicImportantSignals =
    setOf(
        "research question",
        "hypothesis",
        "methodology",
        "sample",
        "results",
        "findings",
        "significant",
        "limitation",
        "conclusion"
    )

private val reportImportantSignals =
    setOf(
        "incident",
        "finding",
        "result",
        "risk",
        "issue",
        "impact",
        "recommendation",
        "status",
        "performance"
    )

private val proposalImportantSignals =
    setOf(
        "objective",
        "proposed",
        "solution",
        "budget",
        "timeline",
        "scope",
        "deliverable",
        "implementation"
    )

private val meetingImportantSignals =
    setOf(
        "decision",
        "assigned",
        "action item",
        "deadline",
        "follow-up",
        "agreement",
        "discussion"
    )

private val instructionImportantSignals =
    setOf(
        "step",
        "required",
        "warning",
        "important",
        "click",
        "select",
        "enter",
        "complete"
    )

/* =========================================================
   REVIEWER SIGNALS BY DOCUMENT TYPE
   ========================================================= */

private val academicReviewerSignals =
    setOf(
        "limitation",
        "finding",
        "result",
        "evidence",
        "methodology",
        "significant",
        "conclusion",
        "recommendation",
        "research gap"
    )

private val reportReviewerSignals =
    setOf(
        "risk",
        "issue",
        "problem",
        "incident",
        "finding",
        "impact",
        "failure",
        "recommendation"
    )

private val proposalReviewerSignals =
    setOf(
        "risk",
        "budget",
        "scope",
        "timeline",
        "resource",
        "objective",
        "challenge",
        "recommendation"
    )

private val meetingReviewerSignals =
    setOf(
        "decision",
        "issue",
        "concern",
        "deadline",
        "assigned",
        "agreement",
        "discussion"
    )

private val instructionReviewerSignals =
    setOf(
        "warning",
        "important",
        "required",
        "must",
        "note",
        "caution"
    )

/* =========================================================
   ACTION SIGNALS BY DOCUMENT TYPE
   ========================================================= */

private val academicActionSignals =
    setOf(
        "conduct",
        "analyze",
        "collect",
        "revise",
        "submit",
        "evaluate",
        "investigate"
    )

private val reportActionSignals =
    setOf(
        "resolve",
        "investigate",
        "monitor",
        "review",
        "correct",
        "follow up"
    )

private val proposalActionSignals =
    setOf(
        "implement",
        "develop",
        "prepare",
        "approve",
        "allocate",
        "schedule",
        "evaluate"
    )

private val meetingActionSignals =
    setOf(
        "assigned",
        "will",
        "must",
        "follow up",
        "complete",
        "prepare",
        "schedule"
    )

private val instructionActionSignals =
    setOf(
        "click",
        "select",
        "enter",
        "install",
        "configure",
        "complete",
        "submit"
    )

/* =========================================================
   STOP WORDS
   ========================================================= */

private val stopWords =
    setOf(
        "about",
        "above",
        "after",
        "again",
        "against",
        "also",
        "and",
        "ang",
        "are",
        "because",
        "been",
        "before",
        "being",
        "below",
        "between",
        "both",
        "but",
        "can",
        "could",
        "did",
        "does",
        "doing",
        "during",
        "each",
        "few",
        "for",
        "from",
        "further",
        "had",
        "has",
        "have",
        "having",
        "her",
        "here",
        "hers",
        "him",
        "his",
        "how",
        "into",
        "its",
        "itself",
        "may",
        "mga",
        "more",
        "most",
        "must",
        "nang",
        "ng",
        "not",
        "now",
        "of",
        "off",
        "on",
        "once",
        "only",
        "or",
        "other",
        "our",
        "ours",
        "out",
        "over",
        "same",
        "she",
        "should",
        "so",
        "some",
        "such",
        "than",
        "that",
        "the",
        "their",
        "theirs",
        "them",
        "then",
        "there",
        "these",
        "they",
        "this",
        "those",
        "through",
        "to",
        "too",
        "under",
        "until",
        "up",
        "very",
        "was",
        "were",
        "what",
        "when",
        "where",
        "which",
        "while",
        "who",
        "whom",
        "why",
        "will",
        "with",
        "would",
        "you",
        "your",
        "yours",
        "yung"
    )

