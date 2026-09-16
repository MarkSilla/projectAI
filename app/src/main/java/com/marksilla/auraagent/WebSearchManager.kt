package com.marksilla.auraagent

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

internal data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val imageUrl: String? = null
)

internal data class WebSearchResponse(
    val results: List<WebSearchResult>,
    val error: String? = null
)

internal class WebSearchManager(
    private val fetchImages: (String) -> String = ::fetchImageSearchPage,
    private val includeRelatedImages: Boolean = false,
    private val fetch: (String) -> String = ::fetchSearchPage
) {
    private data class CachedResponse(
        val timestamp: Long,
        val response: WebSearchResponse
    )

    private val cache = mutableMapOf<String, CachedResponse>()

    fun search(query: String, limit: Int = 5): List<WebSearchResult> {
        return searchDetailed(query, limit).results
    }

    fun clearCache() {
        synchronized(cache) {
            cache.clear()
        }
    }

    fun searchDetailed(query: String, limit: Int = 5): WebSearchResponse {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return WebSearchResponse(emptyList())
        }
        getCached(cleanQuery, limit)?.let { return it }

        return try {
            val imageSearch = isImageSearchQuery(cleanQuery)
            val regularResults =
                if (imageSearch) {
                    emptyList()
                } else {
                    filterVideoResults(
                        results = rankSearchResults(
                            results = parseSearchResults(
                                fetchWithRetry(fetch, cleanQuery),
                                maxOf(limit, 10)
                            ),
                            query = cleanQuery,
                            limit = maxOf(limit, 10)
                        ),
                        videoSearch = isVideoSearchQuery(cleanQuery),
                        limit = limit
                    )
                }
            val imageResults =
                if (imageSearch || includeRelatedImages) {
                    runCatching {
                        parseImageSearchResults(
                            fetchWithRetry(fetchImages, cleanQuery),
                            limit
                        )
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            val response = WebSearchResponse(
                results =
                    if (imageSearch) {
                        imageResults
                    } else if (includeRelatedImages) {
                        attachRelatedImages(
                            results = regularResults,
                            images = imageResults,
                            limit = limit
                        )
                    } else {
                        regularResults
                    }
            )
            cacheResponse(cleanQuery, limit, response)
            response
        } catch (_: Exception) {
            WebSearchResponse(
                results = emptyList(),
                error = "The web search request failed"
            )
        }
    }

    suspend fun searchDetailedParallel(
        query: String,
        limit: Int = 5
    ): WebSearchResponse = coroutineScope {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@coroutineScope WebSearchResponse(emptyList())
        }
        getCached(cleanQuery, limit)?.let { return@coroutineScope it }

        val imageSearch = isImageSearchQuery(cleanQuery)
        val regularResults: Deferred<List<WebSearchResult>>? =
            if (imageSearch) {
                null
            } else {
                async(Dispatchers.IO) {
                    try {
                        filterVideoResults(
                            results = rankSearchResults(
                                results = parseSearchResults(
                                    fetchWithRetry(fetch, cleanQuery),
                                    maxOf(limit, 10)
                                ),
                                query = cleanQuery,
                                limit = maxOf(limit, 10)
                            ),
                            videoSearch = isVideoSearchQuery(cleanQuery),
                            limit = limit
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
        val imageResults =
            if (imageSearch || includeRelatedImages) {
                async(Dispatchers.IO) {
                    try {
                        parseImageSearchResults(
                            fetchWithRetry(fetchImages, cleanQuery),
                            limit
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            } else {
                null
            }

        val parsedRegularResults = regularResults?.await().orEmpty()
        ensureActive()
        val parsedImageResults = imageResults?.await().orEmpty()
        ensureActive()
        val results =
            when {
                imageSearch -> parsedImageResults
                includeRelatedImages ->
                    attachRelatedImages(
                        results = parsedRegularResults,
                        images = parsedImageResults,
                        limit = limit
                    )

                else -> parsedRegularResults
            }

        val response = WebSearchResponse(results = results)
        cacheResponse(cleanQuery, limit, response)
        response
    }

    private fun cacheKey(query: String, limit: Int): String =
        "${query.lowercase()}|$limit|$includeRelatedImages"

    private fun getCached(query: String, limit: Int): WebSearchResponse? {
        val key = cacheKey(query, limit)
        val cached = synchronized(cache) { cache[key] } ?: return null
        return if (System.currentTimeMillis() - cached.timestamp < 300_000L) {
            cached.response
        } else {
            synchronized(cache) { cache.remove(key) }
            null
        }
    }

    private fun cacheResponse(
        query: String,
        limit: Int,
        response: WebSearchResponse
    ) {
        if (response.error != null || response.results.isEmpty()) {
            return
        }
        synchronized(cache) {
            if (cache.size >= 20) {
                cache.remove(cache.keys.first())
            }
            cache[cacheKey(query, limit)] =
                CachedResponse(
                    timestamp = System.currentTimeMillis(),
                    response = response
                )
        }
    }
}

private fun fetchWithRetry(
    fetch: (String) -> String,
    query: String,
    attempts: Int = 2
): String {
    var lastError: Exception? = null
    repeat(attempts.coerceAtLeast(1)) { attempt ->
        try {
            return fetch(query)
        } catch (error: Exception) {
            lastError = error
            if (attempt + 1 < attempts) {
                Thread.sleep(250L)
            }
        }
    }
    throw lastError ?: IOException("The web search request failed")
}

private fun attachRelatedImages(
    results: List<WebSearchResult>,
    images: List<WebSearchResult>,
    limit: Int
): List<WebSearchResult> {
    if (images.isEmpty()) {
        return results
    }

    return results
        .mapIndexed { index, result ->
            result.copy(
                imageUrl = result.imageUrl ?: images.getOrNull(index)?.imageUrl
            )
        }
        .ifEmpty { images }
        .take(limit)
}

internal fun extractWebSearchQuery(input: String): String? {
    val match =
        Regex(
            "^@web\\b([\\s\\S]*)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(input.trim())

    val rawQuery =
        match
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()

    if (rawQuery.isNullOrBlank()) {
        return null
    }

    if (rawQuery.startsWith("{")) {
        if (!rawQuery.endsWith("}")) {
            return null
        }

        return rawQuery
            .removePrefix("{")
            .removeSuffix("}")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    return rawQuery
}

internal fun isWebSearchCommand(input: String): Boolean {
    return input.trim().matches(
        Regex("@web(?:\\s|\\{|$).*", RegexOption.IGNORE_CASE)
    )
}

internal fun shouldSearchWeb(input: String): Boolean {
    return extractWebSearchQuery(input) != null
}

internal fun isVideoSearchQuery(query: String): Boolean {
    val normalized = query.lowercase()
    return listOf(
        "video",
        "videos",
        "youtube",
        "watch",
        "tutorial",
        "documentary",
        "livestream",
        "live stream"
    ).any { normalized.contains(it) }
}

internal fun isImageSearchQuery(query: String): Boolean {
    val normalized = query.lowercase()
    return listOf(
        "image",
        "images",
        "picture",
        "pictures",
        "photo",
        "photos",
        "illustration",
        "wallpaper"
    ).any { normalized.contains(it) }
}

private fun filterVideoResults(
    results: List<WebSearchResult>,
    videoSearch: Boolean,
    limit: Int
): List<WebSearchResult> {
    if (!videoSearch) {
        return results.take(limit)
    }

    return results
        .filter { result ->
            val source = "${result.url} ${result.title} ${result.snippet}".lowercase()
            listOf("youtube", "youtu.be", "vimeo", "video", "watch", "stream")
                .any { source.contains(it) }
        }
        .take(limit)
}

internal fun parseSearchResults(
    html: String,
    limit: Int = 5
): List<WebSearchResult> {
    if (html.isBlank() || limit <= 0) {
        return emptyList()
    }

    val resultPattern =
        Regex(
            "<a[^>]*class=\\\"result__a\\\"[^>]*href=\\\"([^\\\"]+)\\\"[^>]*>(.*?)</a>\\s*.*?<a[^>]*class=\\\"result__snippet[^>]*>(.*?)</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

    return resultPattern
        .findAll(html)
        .mapNotNull { match ->
            val url = normalizeSearchResultUrl(match.groupValues[1])
            val title = decodeHtml(stripHtml(match.groupValues[2])).trim()
            val snippet = decodeHtml(stripHtml(match.groupValues[3])).trim()
            val imageUrl =
                extractImageUrlNearResult(
                    html = html,
                    startIndex = match.range.first,
                    endIndex = match.range.last
                )

            if (title.isBlank() || url.isBlank()) {
                null
            } else {
                WebSearchResult(
                    title = title,
                    url = url,
                    snippet = snippet,
                    imageUrl = imageUrl
                )
            }
        }
        .distinctBy { it.url }
        .take(limit)
        .toList()
}

internal fun rankSearchResults(
    results: List<WebSearchResult>,
    query: String,
    limit: Int = 5
): List<WebSearchResult> {
    val queryWords =
        query
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()

    return results
        .distinctBy { it.url }
        .sortedByDescending { result ->
            val host =
                runCatching { URL(result.url).host.lowercase() }
                    .getOrDefault("")
            val text = "${result.title} ${result.snippet}".lowercase()
            val relevance = queryWords.count { text.contains(it) }
            val trustedDomain =
                when {
                    host.endsWith(".gov") || host.endsWith(".gov.ph") -> 5
                    host.endsWith(".edu") || host.endsWith(".edu.ph") -> 5
                    host.endsWith(".org") -> 3
                    else -> 0
                }
            trustedDomain + relevance
        }
        .take(limit.coerceAtLeast(0))
}

internal fun parseImageSearchResults(
    json: String,
    limit: Int = 5
): List<WebSearchResult> {
    if (json.isBlank() || limit <= 0) {
        return emptyList()
    }

    return Regex("\\{[^{}]*}")
        .findAll(json)
        .mapNotNull { match ->
            val item = match.value
            val imageUrl = extractJsonField(item, "image") ?: return@mapNotNull null
            val sourceUrl = extractJsonField(item, "url") ?: imageUrl
            val thumbnailUrl = extractJsonField(item, "thumbnail")
            val title =
                extractJsonField(item, "title")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Image result"

            WebSearchResult(
                title = title,
                url = normalizeSearchResultUrl(sourceUrl),
                snippet = "Image result for your search.",
                imageUrl = thumbnailUrl ?: imageUrl
            )
        }
        .distinctBy { it.imageUrl }
        .take(limit)
        .toList()
}

private fun extractJsonField(jsonObject: String, field: String): String? {
    return Regex(
        "\\\"${Regex.escape(field)}\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\""
    ).find(jsonObject)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::decodeJsonString)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun decodeJsonString(value: String): String {
    return value
        .replace("\\\\/", "/")
        .replace("\\\\\"", "\"")
        .replace("\\\\\\", "\\")
}

internal fun formatWebSearchReply(response: WebSearchResponse): String {
    if (response.error != null) {
        return "I couldn't access the internet right now."
    }

    if (response.results.isEmpty()) {
        return "I searched the web but couldn't find relevant results."
    }

    return buildString {
        appendLine("Summary")
        appendLine(buildWebSearchSummary(response.results))

        val keyPoints = buildWebSearchKeyPoints(response.results)

        if (keyPoints.isNotEmpty()) {
            appendLine()
            appendLine("Key points")
            keyPoints.forEach { point ->
                appendLine("- $point")
            }
        }
    }.trim()
}

internal fun buildWebSearchSummary(results: List<WebSearchResult>): String {
    val sentences = selectInformativeWebSentences(results)

    return sentences
        .joinToString(" ")
        .ifBlank { "The search returned results, but no summary text was available." }
}

private fun selectInformativeWebSentences(
    results: List<WebSearchResult>
): List<String> {
    val candidates =
        results
            .flatMap { result ->
                result.snippet
                    .split(Regex("(?<=[.!?])\\s+|\\n+"))
                    .map { it.trim() }
            }
            .filter { sentence ->
                sentence.length >= 25 &&
                    !isUrlLikeText(sentence)
            }
            .distinctBy(::normalizeWebText)

    if (candidates.isEmpty()) {
        return emptyList()
    }

    val frequencies =
        candidates
            .flatMap { sentence ->
                normalizeWebText(sentence)
                    .split(" ")
                    .filter { it.length >= 4 }
            }
            .groupingBy { it }
            .eachCount()

    return candidates
        .sortedByDescending { sentence ->
            normalizeWebText(sentence)
                .split(" ")
                .sumOf { word -> frequencies[word] ?: 0 }
                .toDouble() / sentence.length.coerceAtLeast(1)
        }
        .take(3)
        .sortedBy { candidates.indexOf(it) }
}

private fun normalizeWebText(value: String): String {
    return value
        .lowercase()
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun buildWebSearchKeyPoints(
    results: List<WebSearchResult>
): List<String> {
    return results
        .flatMap { result ->
            result.snippet
                .split(Regex("(?<=[.!?])\\s+|\\n+"))
                .map { it.trim() }
        }
        .filter { it.length >= 25 && !isUrlLikeText(it) }
        .distinctBy(::normalizeWebText)
        .take(5)
}

internal fun isUrlLikeText(value: String): Boolean {
    return value.trim().matches(
        Regex("^(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)
    )
}

private fun extractImageUrl(html: String): String? {
    return Regex(
        "<img[^>]+(?:data-src|src)=\"(https?://[^\"]+)\"",
        RegexOption.IGNORE_CASE
    ).find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun extractImageUrlNearResult(
    html: String,
    startIndex: Int,
    endIndex: Int
): String? {
    val containerEnd =
        html.indexOf(
            "</div>",
            startIndex = endIndex,
            ignoreCase = true
        )
    val end =
        if (containerEnd >= 0) {
            containerEnd + "</div>".length
        } else {
            endIndex + 4_000
        }.coerceAtMost(html.length)

    return extractImageUrl(
        html.substring(startIndex, end)
    )
}

internal fun normalizeSearchResultUrl(rawUrl: String): String {
    val decoded = decodeHtml(rawUrl).trim()
    val normalized =
        when {
            decoded.startsWith("//") -> "https:$decoded"
            decoded.startsWith("/") -> "https://html.duckduckgo.com$decoded"
            else -> decoded
        }
    val encodedTarget =
        Regex("[?&]uddg=([^&]+)", RegexOption.IGNORE_CASE)
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)

    return encodedTarget
        ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        ?: normalized
}

private fun fetchSearchPage(query: String): String {
    val encodedQuery =
        URLEncoder.encode(query, StandardCharsets.UTF_8.name())
    val connection =
        (URL("https://html.duckduckgo.com/html/?q=$encodedQuery").openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", "AURA-Agent/1.0")
                setRequestProperty("Accept", "text/html")
            }

    return try {
        if (connection.responseCode !in 200..299) {
            throw IOException("Search returned HTTP ${connection.responseCode}")
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun fetchImageSearchPage(query: String): String {
    val encodedQuery =
        URLEncoder.encode(query, StandardCharsets.UTF_8.name())
    val connection =
        (URL("https://duckduckgo.com/i.js?o=json&q=$encodedQuery").openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", "AURA-Agent/1.0")
                setRequestProperty("Referer", "https://duckduckgo.com/")
                setRequestProperty("Accept", "application/json")
            }

    return try {
        if (connection.responseCode !in 200..299) {
            throw IOException("Image search returned HTTP ${connection.responseCode}")
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun stripHtml(value: String): String =
    value
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun decodeHtml(value: String): String =
    value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
        }
