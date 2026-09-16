package com.marksilla.auraagent

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
    fun search(query: String, limit: Int = 5): List<WebSearchResult> {
        return searchDetailed(query, limit).results
    }

    fun searchDetailed(query: String, limit: Int = 5): WebSearchResponse {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return WebSearchResponse(emptyList())
        }

        return try {
            val imageSearch = isImageSearchQuery(cleanQuery)
            val regularResults =
                if (imageSearch) {
                    emptyList()
                } else {
                    parseSearchResults(fetch(cleanQuery), limit)
                }
            val imageResults =
                if (imageSearch || includeRelatedImages) {
                    runCatching {
                        parseImageSearchResults(
                            fetchImages(cleanQuery),
                            limit
                        )
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            WebSearchResponse(
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
        } catch (_: Exception) {
            WebSearchResponse(
                results = emptyList(),
                error = "The web search request failed"
            )
        }
    }
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

        val keyPoints =
            response.results
                .mapNotNull { result ->
                    result.snippet
                        .trim()
                        .takeIf { it.isNotBlank() }
                }
                .distinct()
                .take(5)

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
    val sentences =
        results
            .flatMap { result ->
                result.snippet
                    .split(Regex("(?<=[.!?])\\s+"))
                    .map { it.trim() }
            }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)

    return sentences
        .joinToString(" ")
        .ifBlank { "The search returned results, but no summary text was available." }
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
