package com.marksilla.auraagent

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

internal data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
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

internal fun shouldSearchWeb(input: String): Boolean {
    val normalized =
        input
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    if (normalized.isBlank()) {
        return false
    }

    val webIntentPhrases =
        listOf(
            "latest",
            "today",
            "current",
            "recent",
            "right now",
            "this week",
            "this month",
            "what happened",
            "news",
            "weather",
            "search",
            "look up",
            "find information",
            "find out",
            "online",
            "who is the current",
            "what is the latest",
            "what time does"
        )

    return webIntentPhrases.any { phrase ->
        normalized.contains(phrase)
    }
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

            if (title.isBlank() || url.isBlank()) {
                null
            } else {
                WebSearchResult(
                    title = title,
                    url = url,
                    snippet = snippet
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
            appendLine("Source: ${result.url}")
        }
    }.trim()
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
