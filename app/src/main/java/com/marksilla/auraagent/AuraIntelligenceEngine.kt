package com.marksilla.auraagent

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class AuraIntent {
    OPEN_APPLICATION,
    CLOSE_APPLICATION,
    SEARCH_WEB,
    SEARCH_LOCAL_KNOWLEDGE,
    ANSWER_QUESTION,
    CREATE_NOTE,
    READ_NOTE,
    REMEMBER_INFORMATION,
    FORGET_INFORMATION,
    SET_REMINDER,
    GET_TIME,
    GET_DATE,
    GET_WEATHER,
    RESEARCH_TOPIC,
    SUMMARIZE,
    COMPARE,
    EXPLAIN,
    CALCULATE,
    NAVIGATE,
    EXECUTE_ACTION,
    UNKNOWN
}

data class AuraEntity(
    val name: String,
    val type: String,
    val source: String,
    val confidence: Float = 1.0f
)

data class AuraContext(
    val previousUserInput: String? = null,
    val previousAuraResponse: String? = null,
    val currentTopic: String? = null,
    val activeEntities: List<AuraEntity> = emptyList(),
    val recentIntents: List<AuraIntent> = emptyList(),
    val conversationState: String = "active",
    val unresolvedReferences: Map<String, String> = emptyMap(),
    val taskState: String = "idle"
)

data class AuraInterpretation(
    val intent: AuraIntent,
    val confidence: Float,
    val entities: List<AuraEntity>,
    val keywords: List<String>,
    val resolvedReferences: List<String>,
    val requiresCurrentInformation: Boolean,
    val requiresWebResearch: Boolean,
    val actionPlan: List<String>,
    val command: String? = null
)

data class MemoryEntry(
    val type: String,
    val category: String,
    val value: String,
    val source: String,
    val confidence: Float = 1.0f,
    val matchText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class SourceEvidence(
    val source: String,
    val title: String,
    val retrievedAt: String,
    val relevantText: String = "",
    val claims: List<String> = emptyList(),
    val reliabilitySignals: List<String> = emptyList(),
    val contradictions: List<String> = emptyList()
)

data class ConflictOutcome(
    val hasConflict: Boolean,
    val conflictingClaims: List<Pair<String, String>> = emptyList(),
    val summary: String = "No conflict detected"
)

data class TaskPlan(
    val goal: String,
    val status: String,
    val requirements: List<String>,
    val completedSteps: List<String>,
    val pendingSteps: List<String>,
    val evidence: List<String>
)

class AuraNlpEngine {
    private val appKeywords = setOf(
        "facebook",
        "fb",
        "messenger",
        "instagram",
        "whatsapp",
        "youtube",
        "chrome",
        "settings",
        "downloads",
        "camera",
        "maps",
        "gmail"
    )

    fun normalize(input: String): String =
        input
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun tokenize(input: String): List<String> =
        normalize(input)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    fun keywords(input: String): List<String> {
        val tokens = tokenize(input)
        return tokens.filter { it.length > 2 || appKeywords.contains(it) }
    }

    fun containsAny(input: String, phrases: Collection<String>): Boolean =
        phrases.any { phrase -> normalize(input).contains(phrase) }

    fun resolveReferences(input: String, context: AuraContext): List<String> {
        val normalized = normalize(input)
        val pronouns = Regex("\\b(he|she|they|him|her|them|it)\\b")
        if (pronouns.containsMatchIn(normalized)) {
            val names = context.activeEntities.map { it.name }
            if (names.isNotEmpty()) {
                return names.map { "${normalize(it)} -> ${normalize(input)}" }
            }
        }
        return emptyList()
    }
}

interface MemoryStore {
    fun load(): MutableList<MemoryEntry>
    fun save(entries: List<MemoryEntry>)
}

class InMemoryMemoryStore : MemoryStore {
    private val data = mutableListOf<MemoryEntry>()

    override fun load(): MutableList<MemoryEntry> = data.toMutableList()

    override fun save(entries: List<MemoryEntry>) {
        data.clear()
        data.addAll(entries)
    }
}

class SharedPreferencesMemoryStore(
    private val preferences: SharedPreferences
) : MemoryStore {
    override fun load(): MutableList<MemoryEntry> {
        val raw = preferences.getString(KEY_AURA_MEMORY_ITEMS, "") ?: ""
        if (raw.isBlank()) {
            return mutableListOf()
        }

        return runCatching {
            val array = JSONArray(raw)
            val items = mutableListOf<MemoryEntry>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                items += MemoryEntry(
                    type = item.optString("type", "memory"),
                    category = item.optString("category", "general"),
                    value = item.optString("value", ""),
                    source = item.optString("source", "USER"),
                    confidence = item.optDouble("confidence", 1.0).toFloat(),
                    matchText = item.optString("matchText", "").ifBlank { null },
                    timestamp = item.optLong("timestamp", System.currentTimeMillis())
                )
            }
            items
        }.getOrElse { mutableListOf() }
    }

    override fun save(entries: List<MemoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("type", entry.type)
                    .put("category", entry.category)
                    .put("value", entry.value)
                    .put("source", entry.source)
                    .put("confidence", entry.confidence)
                    .put("matchText", entry.matchText ?: "")
                    .put("timestamp", entry.timestamp)
            )
        }
        preferences.edit().putString(KEY_AURA_MEMORY_ITEMS, array.toString()).apply()
    }
}

class AuraMemoryEngine(
    private val store: MemoryStore = InMemoryMemoryStore()
) {
    private val shortTerm = mutableListOf<MemoryEntry>()
    private val longTerm = store.load().toMutableList()

    fun remember(
        type: String,
        category: String,
        value: String,
        source: String,
        confidence: Float = 1.0f,
        matchText: String? = null
    ) {
        val entry = MemoryEntry(
            type = type,
            category = category,
            value = value,
            source = source,
            confidence = confidence,
            matchText = matchText
        )
        shortTerm += entry
        longTerm += entry
        persist()
    }

    fun storeMemory(
        type: String,
        category: String,
        value: String,
        source: String,
        confidence: Float = 1.0f,
        matchText: String? = null
    ): MemoryEntry {
        val entry = MemoryEntry(
            type = type,
            category = category,
            value = value,
            source = source,
            confidence = confidence,
            matchText = matchText
        )
        val existing = longTerm.firstOrNull {
            it.type == type && it.category == category && it.value == value
        }
        if (existing != null) {
            longTerm.remove(existing)
        }
        longTerm += entry
        persist()
        return entry
    }

    fun retrieveMemory(key: String): List<MemoryEntry> =
        searchMemory(key)

    fun updateMemory(
        type: String,
        category: String,
        value: String,
        source: String,
        confidence: Float = 1.0f,
        matchText: String? = null
    ) {
        val existing = longTerm
            .filter { it.type == type || it.category == category }
            .firstOrNull { it.matchText == matchText || it.value == value }
        if (existing != null) {
            val index = longTerm.indexOf(existing)
            longTerm[index] = existing.copy(
                value = value,
                source = source,
                confidence = confidence,
                matchText = matchText
            )
            val shortIndex = shortTerm.indexOfFirst { it.type == existing.type && it.category == existing.category }
            if (shortIndex >= 0) {
                shortTerm[shortIndex] = shortTerm[shortIndex].copy(
                    value = value,
                    source = source,
                    confidence = confidence,
                    matchText = matchText
                )
            }
            persist()
            return
        }
        remember(type, category, value, source, confidence, matchText)
    }

    fun deleteMemory(query: String): Boolean {
        val before = longTerm.size
        longTerm.removeAll { entry ->
            matchesMemoryQuery(entry, query)
        }
        shortTerm.removeAll { entry ->
            matchesMemoryQuery(entry, query)
        }
        persist()
        return before != longTerm.size
    }

    fun forgetMemory(key: String): Int {
        return if (deleteMemory(key)) {
            val after = longTerm.size
            val before = after + 1
            before - after
        } else {
            0
        }
    }

    fun searchMemory(query: String): List<MemoryEntry> {
        val q = query.lowercase(Locale.ROOT).trim()
        if (q.isBlank()) {
            return emptyList()
        }

        val tokens = q
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .distinct()

        return (longTerm + shortTerm)
            .filter { entry ->
                val haystack = listOf(
                    entry.type,
                    entry.category,
                    entry.value,
                    entry.matchText ?: ""
                ).joinToString(" ").lowercase(Locale.ROOT)

                q in haystack ||
                    tokens.any { token -> token.length >= 2 && haystack.contains(token) }
            }
            .distinctBy { it.type to it.category to it.value }
    }

    fun getRelatedMemories(query: String): List<MemoryEntry> =
        searchMemory(query)

    fun allMemory(): List<MemoryEntry> =
        (longTerm + shortTerm)
            .distinctBy { entry -> entry.type to entry.category to entry.value to entry.matchText }
            .sortedByDescending { it.timestamp }

    fun detectExplicitLearningInstruction(input: String): Pair<String, String>? {
        val normalized = input.lowercase(Locale.ROOT)
        val patterns = listOf(
            Regex("(?:when\\s+(?:i\\s+)?say|kapag\\s+sinabi\\s+kong|kapag\\s+sinabi\\s+ko)\\s+['\"]?([a-z0-9\\s-]+?)['\"]?\\s*(?:it\\s+means|ibig\\s+sabihin(?:\\s+ay)?|means|mean|meaning)\\s+(?:ay\\s+|is\\s+|to\\s+|na\\s+)?([a-z_]+)") ,
            Regex("(?:^|\\b)([a-z0-9][a-z0-9\\s-]*?)\\s+(?:means|mean|meaning|it\\s+means|ibig\\s+sabihin(?:\\s+ay)?)\\s+(?:ay\\s+|is\\s+|to\\s+|na\\s+)?([a-z_]+)\\b") ,
            Regex("(?:remember|save|tandaan)\\s+(?:that|na)\\s+(?:i|ako)\\s+(?:prefer|prefer ko|mas gusto ko)\\s+([a-z0-9\\s]+)")
        )

        for (pattern in patterns) {
            val match = pattern.find(normalized) ?: continue
            val term = match.groupValues.getOrNull(1)?.trim() ?: continue
            val meaning = match.groupValues.getOrNull(2)?.trim() ?: continue
            if (term.isNotBlank() && meaning.isNotBlank()) {
                return term to meaning.uppercase(Locale.ROOT)
            }
        }

        val directMarker = when {
            normalized.contains("kapag sinabi kong") -> "kapag sinabi kong"
            normalized.contains("when i say") -> "when i say"
            else -> null
        }

        if (directMarker != null) {
            val remainder = normalized.substringAfter(directMarker, "")
                .replace(Regex("^(?:na|ay|is|to)?\\s+"), "")
                .trim()
            val split = remainder.split(Regex("\\s+(?:ibig sabihin|ibig sabihin ay|it means|means|meaning)\\s+(?:ay|is|to|na)?\\s+"), limit = 2)
            if (split.size == 2) {
                val term = split[0].replace(Regex("['\"]"), "").trim()
                val meaning = split[1].replace(Regex("['\"]"), "").trim()
                if (term.isNotBlank() && meaning.isNotBlank()) {
                    return term to meaning.uppercase(Locale.ROOT)
                }
            }
        }

        if (normalized.contains("remember that") || normalized.contains("tandaan na") || normalized.contains("i prefer")) {
            val token = normalized.replace(Regex(".*(?:remember that|tandaan na|i prefer)\\s+"), "")
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .trim()
            if (token.isNotBlank()) {
                return token to "PREFERENCE"
            }
        }

        return null
    }

    fun learnFromExplicitInstruction(input: String, source: String = "USER"): MemoryEntry? {
        val parsed = detectExplicitLearningInstruction(input) ?: return null
        val term = parsed.first
        val meaning = parsed.second

        if (isReservedCommandVerb(term)) {
            return null
        }

        val kind = when {
            meaning.contains("OPEN") -> "learned_term"
            meaning.contains("PREFERENCE") -> "preference"
            else -> "learned_concept"
        }

        val entry = storeMemory(
            type = kind,
            category = if (meaning.contains("PREFERENCE")) "response_style" else "learned_word",
            value = meaning,
            source = source,
            confidence = 1.0f,
            matchText = term
        )

        val aliasEntry = if (term.contains(" ") || term.length > 1) {
            storeMemory(
                type = "application_alias",
                category = "alias",
                value = term,
                source = source,
                confidence = 1.0f,
                matchText = meaning
            )
        } else {
            null
        }

        return if (aliasEntry != null) aliasEntry else entry
    }

    private fun persist() {
        store.save(longTerm)
    }

    private fun matchesMemoryQuery(entry: MemoryEntry, query: String): Boolean {
        val q = query.lowercase(Locale.ROOT)
        return entry.value.contains(q, ignoreCase = true) ||
            entry.category.contains(q, ignoreCase = true) ||
            entry.type.contains(q, ignoreCase = true) ||
            (entry.matchText?.contains(q, ignoreCase = true) == true)
    }
}

fun createAndroidMemoryEngine(context: Context): AuraMemoryEngine =
    AuraMemoryEngine(
        store = SharedPreferencesMemoryStore(
            context.applicationContext.getSharedPreferences(
                "aura_memory_store",
                Context.MODE_PRIVATE
            )
        )
    )

private const val KEY_AURA_MEMORY_ITEMS = "aura_memory_items"

class AuraResearchEngine {
    fun detectConflict(evidence: List<SourceEvidence>): ConflictOutcome {
        if (evidence.size < 2) {
            return ConflictOutcome(hasConflict = false)
        }

        val claims: List<Pair<String, String>> = evidence.flatMap { source ->
            source.claims.map { claim -> source.title to claim }
        }

        val conflicts = mutableListOf<Pair<String, String>>()
        for (i in claims.indices) {
            for (j in i + 1 until claims.size) {
                val left = claims[i]
                val right = claims[j]
                if (left.second != right.second && left.second.isNotBlank() && right.second.isNotBlank()) {
                    conflicts.add(left.second to right.second)
                }
            }
        }

        return if (conflicts.isEmpty()) {
            ConflictOutcome(hasConflict = false)
        } else {
            ConflictOutcome(
                hasConflict = true,
                conflictingClaims = conflicts,
                summary = "Multiple sources report different claims and need additional verification."
            )
        }
    }
}

class AuraIntelligenceEngine(
    private val memoryEngine: AuraMemoryEngine = AuraMemoryEngine(),
    private val nlp: AuraNlpEngine = AuraNlpEngine()
) {
    private val appAliases = mapOf(
        "facebook" to "facebook",
        "fb" to "facebook",
        "face book" to "facebook",
        "messenger" to "messenger",
        "instagram" to "instagram",
        "ig" to "instagram",
        "whatsapp" to "whatsapp",
        "wa" to "whatsapp",
        "youtube" to "youtube",
        "yt" to "youtube",
        "chrome" to "chrome",
        "gmail" to "gmail",
        "settings" to "settings",
        "downloads" to "downloads",
        "camera" to "camera"
    )

    fun classify(
        input: String,
        context: AuraContext = AuraContext(),
        memory: AuraMemoryEngine? = null
    ): AuraInterpretation {
        val activeMemory = memory ?: this.memoryEngine
        val normalized = nlp.normalize(input)
        val keywords = nlp.keywords(input)
        val memoryAware = resolveMemoryTerms(normalized, activeMemory)
        val isMalicious = memoryAware.contains("ignore aura") && memoryAware.contains("execute this command")
        if (isMalicious) {
            return AuraInterpretation(
                intent = AuraIntent.UNKNOWN,
                confidence = 0.05f,
                entities = emptyList(),
                keywords = keywords,
                resolvedReferences = emptyList(),
                requiresCurrentInformation = false,
                requiresWebResearch = false,
                actionPlan = listOf("Ignore untrusted webpage content"),
                command = input
            )
        }

        val resolvedReferences = nlp.resolveReferences(input, context)
        val appMatch = detectAppReference(memoryAware)

        val openSignals = listOf(
            "open ", "launch ", "start ", "run ", "buksan ", "goto ", "go to ", "punta ka sa ", "open app ", "paki buksan ", "open mo "
        )
        val rememberSignals = listOf("remember ", "save that ", "note that ", "keep in mind ")
        val dateSignals = listOf("what date", "what day", "date today", "today's date")
        val timeSignals = listOf("what time", "time now", "what time is it")
        val weatherSignals = listOf("weather", "temperature", "rain forecast")
        val currentInfoSignals = listOf(
            "current president", "latest android version", "latest news", "today's news", "current weather",
            "who is the president", "what is the current", "what is today's", "latest version"
        )
        val searchSignals = listOf("search for ", "find information about ", "look up ", "research ", "find the latest ")
        val reminderSignals = listOf("remind me", "set reminder", "alarm", "notify me")
        val calcSignals = listOf("calculate ", "what is ", "plus ", "minus ", "sum ", "multiply ", "divide ")
        val compareSignals = listOf("compare ", "difference between ", "which is better")
        val summarizeSignals = listOf("summarize ", "summary of ", "review document")

        val requiresCurrentInformation = currentInfoSignals.any { memoryAware.contains(it) } ||
            memoryAware.contains("current") ||
            memoryAware.contains("latest") ||
            memoryAware.contains("today")

        val requiresWebResearch = requiresCurrentInformation || searchSignals.any { memoryAware.contains(it) }

        val intent = when {
            appMatch != null && openSignals.any { memoryAware.contains(it) } -> AuraIntent.OPEN_APPLICATION
            memoryAware.contains("close ") || memoryAware.contains("close app") -> AuraIntent.CLOSE_APPLICATION
            memoryAware.contains("remember ") || memoryAware.contains("save that") || memoryAware.contains("note that") -> AuraIntent.REMEMBER_INFORMATION
            memoryAware.contains("forget ") || memoryAware.contains("remove memory") -> AuraIntent.FORGET_INFORMATION
            reminderSignals.any { memoryAware.contains(it) } -> AuraIntent.SET_REMINDER
            timeSignals.any { memoryAware.contains(it) } -> AuraIntent.GET_TIME
            dateSignals.any { memoryAware.contains(it) } -> AuraIntent.GET_DATE
            weatherSignals.any { memoryAware.contains(it) } -> AuraIntent.GET_WEATHER
            summarizeSignals.any { memoryAware.contains(it) } -> AuraIntent.SUMMARIZE
            compareSignals.any { memoryAware.contains(it) } -> AuraIntent.COMPARE
            requiresCurrentInformation || requiresWebResearch -> AuraIntent.SEARCH_WEB
            calcSignals.any { memoryAware.contains(it) } -> AuraIntent.CALCULATE
            memoryAware.contains("take me to") || memoryAware.contains("navigate to") || memoryAware.contains("map") -> AuraIntent.NAVIGATE
            memoryAware.contains("create note") || memoryAware.contains("write note") -> AuraIntent.CREATE_NOTE
            memoryAware.contains("read note") || memoryAware.contains("open note") -> AuraIntent.READ_NOTE
            memoryAware.contains("who is") || memoryAware.contains("what is") || memoryAware.contains("how old") || memoryAware.contains("when was") || memoryAware.contains("why ") -> AuraIntent.ANSWER_QUESTION
            rememberSignals.any { memoryAware.contains(it) } -> AuraIntent.REMEMBER_INFORMATION
            else -> AuraIntent.UNKNOWN
        }

        val confidence = when (intent) {
            AuraIntent.OPEN_APPLICATION -> if (appMatch != null) 0.91f else 0.68f
            AuraIntent.SEARCH_WEB -> 0.87f
            AuraIntent.ANSWER_QUESTION -> 0.79f
            AuraIntent.REMEMBER_INFORMATION -> 0.88f
            AuraIntent.SET_REMINDER -> 0.82f
            AuraIntent.GET_TIME -> 0.89f
            AuraIntent.GET_DATE -> 0.89f
            AuraIntent.GET_WEATHER -> 0.86f
            AuraIntent.CALCULATE -> 0.8f
            AuraIntent.UNKNOWN -> 0.35f
            else -> 0.72f
        }

        val entities = buildList {
            appMatch?.let { app -> add(AuraEntity(name = app, type = "application", source = "command", confidence = confidence)) }
            context.activeEntities.filter { it.name.isNotBlank() }.forEach { add(it) }
            if (normalized.contains("president")) add(AuraEntity(name = "Philippines", type = "location", source = "domain", confidence = 0.75f))
        }

        return AuraInterpretation(
            intent = intent,
            confidence = confidence,
            entities = entities,
            keywords = keywords,
            resolvedReferences = resolvedReferences,
            requiresCurrentInformation = requiresCurrentInformation,
            requiresWebResearch = requiresWebResearch,
            actionPlan = buildActionPlan(intent, normalized),
            command = input
        )
    }

    fun planTask(task: String): TaskPlan {
        val normalized = nlp.normalize(task)
        val requirements = mutableListOf<String>()
        if (normalized.contains("android") || normalized.contains("version")) {
            requirements += "Identify the relevant Android version topic"
        }
        if (normalized.contains("explain") || normalized.contains("major changes")) {
            requirements += "Extract key changes and explain them clearly"
        }
        if (normalized.contains("latest") || normalized.contains("current") || normalized.contains("today")) {
            requirements += "Check current external information"
        }

        val pending = mutableListOf<String>()
        pending += "identify the topic and required facts"
        pending += "search for relevant current sources"
        pending += "compare evidence and detect conflicts"
        pending += "generate a structured answer with sources"

        val steps = listOf(
            "Identify topic",
            "Determine whether current information is required",
            "Search the internet",
            "Retrieve multiple sources",
            "Extract relevant facts",
            "Compare evidence",
            "Detect conflicts",
            "Generate final answer"
        )

        return TaskPlan(
            goal = task,
            status = "researching",
            requirements = requirements.ifEmpty { listOf("Determine the request and required evidence") },
            completedSteps = emptyList(),
            pendingSteps = pending,
            evidence = steps
        )
    }

    private fun buildActionPlan(intent: AuraIntent, normalized: String): List<String> =
        when (intent) {
            AuraIntent.OPEN_APPLICATION -> listOf("Detect requested application", "Validate app name", "Launch app if confidence is sufficient")
            AuraIntent.SEARCH_WEB -> listOf("Detect current-information requirement", "Build search query", "Retrieve sources", "Compare evidence")
            AuraIntent.ANSWER_QUESTION -> listOf("Resolve context references", "Use memory if available", "Answer with evidence or local knowledge")
            AuraIntent.REMEMBER_INFORMATION -> listOf("Store structured fact", "Link to user preference or context")
            AuraIntent.UNKNOWN -> listOf("Ask for clarification")
            else -> listOf("Use the relevant tool", "Complete the requested action", "Report result")
        }

    private fun detectAppReference(normalized: String): String? {
        val candidate = mutableListOf<String>()
        for ((alias, canonical) in appAliases) {
            if (normalized.contains(alias)) {
                candidate += canonical
            }
        }
        if (candidate.isNotEmpty()) {
            return candidate.first()
        }
        return null
    }

    fun resolveMemoryTerms(input: String, memory: AuraMemoryEngine): String {
        var working = input.trim()
        if (working.isBlank()) return working

        val memoryMatches = memory
            .searchMemory(working)
            .filter { it.matchText != null || it.category.contains("alias") || it.type.contains("learned") }
            .sortedByDescending { maxOf(it.value.length, it.matchText?.length ?: 0) }

        for (entry in memoryMatches) {
            val alias = when {
                entry.type.contains("application_alias") || entry.category.contains("alias") -> entry.value.trim()
                entry.matchText != null -> entry.matchText!!.trim()
                else -> entry.value.trim()
            }
            val replacement = when {
                entry.type.contains("application_alias") || entry.category.contains("alias") ->
                    when {
                        entry.matchText.equals("OPEN", ignoreCase = true) || entry.matchText.equals("OPEN_APP", ignoreCase = true) -> "open"
                        entry.matchText.equals("NAVIGATE", ignoreCase = true) || entry.matchText.equals("GO_TO", ignoreCase = true) -> "go to"
                        else -> entry.matchText?.trim().orEmpty()
                    }
                entry.value.equals("OPEN", ignoreCase = true) || entry.value.equals("OPEN_APP", ignoreCase = true) -> "open"
                entry.value.equals("NAVIGATE", ignoreCase = true) || entry.value.equals("GO_TO", ignoreCase = true) -> "go to"
                else -> entry.value.trim()
            }

            val aliasLower = alias.lowercase(Locale.ROOT)
            if (aliasLower.isBlank() || replacement.isBlank()) continue
            if (working.contains(aliasLower)) {
                working = working.replace(aliasLower, replacement)
            }
        }

        return working
    }
}

fun rememberMemory(
    memory: AuraMemoryEngine,
    type: String,
    category: String,
    value: String,
    source: String,
    confidence: Float = 1.0f,
    matchText: String? = null
) = memory.remember(type, category, value, source, confidence, matchText)

fun retrieveMemory(memory: AuraMemoryEngine, key: String): List<MemoryEntry> = memory.retrieveMemory(key)

fun updateMemory(
    memory: AuraMemoryEngine,
    type: String,
    category: String,
    value: String,
    source: String,
    confidence: Float = 1.0f,
    matchText: String? = null
) = memory.updateMemory(type, category, value, source, confidence, matchText)

fun forgetMemory(memory: AuraMemoryEngine, key: String): Int = memory.forgetMemory(key)

fun searchMemory(memory: AuraMemoryEngine, query: String): List<MemoryEntry> = memory.searchMemory(query)
