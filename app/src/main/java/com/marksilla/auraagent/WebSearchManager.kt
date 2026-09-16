package com.marksilla.auraagent

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
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
            WebSearchResponse(
                results = parseSearchResults(fetch(cleanQuery), limit)
            )
        } catch (_: Exception) {
            WebSearchResponse(
                results = emptyList(),
                error = "The web search request failed"
            )
        }
    }
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
            val url = decodeHtml(match.groupValues[1]).trim()
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

internal fun formatWebSearchReply(response: WebSearchResponse): String {
    if (response.error != null) {
        return "I couldn't access the internet right now."
    }

    if (response.results.isEmpty()) {
        return "I searched the web but couldn't find relevant results."
    }

    return buildString {
        appendLine("Here's what I found on the web:")
        response.results.forEachIndexed { index, result ->
            appendLine()
            appendLine("${index + 1}. ${result.title}")
            if (result.snippet.isNotBlank()) {
                appendLine(result.snippet)
            }
        }
    }.trim()
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
