package com.marksilla.auraagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchManagerTest {
    @Test
    fun extractsExplicitWebSearchQuery() {
        assertEquals(
            "what is RAM?",
            extractWebSearchQuery("@web {what is RAM?}")
        )
        assertEquals(
            "what is RAM?",
            extractWebSearchQuery("@web what is RAM?")
        )
        assertEquals(
            "latest Android news",
            extractWebSearchQuery("  @WEB { latest Android news } ")
        )
    }

    @Test
    fun explicitWebSearchUsesOnlyTextInsideBraces() {
        var receivedQuery: String? = null
        val manager =
            WebSearchManager {
                receivedQuery = it
                "<html><body>No results</body></html>"
            }

        manager.searchDetailed(extractWebSearchQuery("@web {what is RAM?}")!!)

        assertEquals("what is RAM?", receivedQuery)
    }

    @Test
    fun onlyExplicitWebCommandsTriggerSearch() {
        assertTrue(shouldSearchWeb("@web {What is RAM?}"))
        assertTrue(shouldSearchWeb("@web What is RAM?"))
        assertTrue(isWebSearchCommand("@web {}"))
        assertTrue(isWebSearchCommand("  @WEB {what is RAM?}"))
        assertTrue(!isWebSearchCommand("@website {what is RAM?}"))
        assertTrue(!shouldSearchWeb("What's the latest news today?"))
        assertTrue(!shouldSearchWeb("Find information about Gordon College"))
        assertTrue(!shouldSearchWeb("What is the weather today?"))
        assertTrue(!shouldSearchWeb("What is 25 times 8?"))
        assertTrue(!shouldSearchWeb("Open Messenger"))
        assertTrue(!shouldSearchWeb("What is RAM?"))
        assertTrue(extractWebSearchQuery("@web {}") == null)
        assertTrue(extractWebSearchQuery("@web") == null)
    }

    @Test
    fun detectsVideoSearchQueries() {
        assertTrue(isVideoSearchQuery("videos about Android development"))
        assertTrue(isVideoSearchQuery("YouTube tutorial for Kotlin"))
        assertTrue(!isVideoSearchQuery("what is RAM?"))
        assertTrue(isImageSearchQuery("images of Manila"))
        assertTrue(!isImageSearchQuery("what is RAM?"))
    }

    @Test
    fun parsesImageSearchResults() {
        val json =
            """
            {"title":"Manila skyline","image":"https://images.example/sky.jpg","thumbnail":"https://images.example/thumb.jpg","url":"https://example.com/manila"}
            """.trimIndent()

        val results = parseImageSearchResults(json)

        assertEquals(1, results.size)
        assertEquals("Manila skyline", results[0].title)
        assertEquals("https://images.example/thumb.jpg", results[0].imageUrl)
        assertEquals("https://example.com/manila", results[0].url)
    }

    @Test
    fun attachesRelatedImageToNormalWebResults() {
        val manager =
            WebSearchManager(
                fetch = {
                    """
                    <div class="result">
                      <a class="result__a" href="https://example.com/ram">RAM guide</a>
                      <a class="result__snippet">RAM stores active data.</a>
                    </div>
                    """.trimIndent()
                },
                fetchImages = {
                    """
                    {"title":"RAM image","image":"https://images.example/ram.jpg","thumbnail":"https://images.example/ram-thumb.jpg","url":"https://example.com/ram-image"}
                    """.trimIndent()
                },
                includeRelatedImages = true
            )

        val response = manager.searchDetailed("what is RAM?")

        assertEquals(
            "https://images.example/ram-thumb.jpg",
            response.results.single().imageUrl
        )
    }

    @Test
    fun parsesStructuredSearchResults() {
        val html =
            """
            <div class="result">
              <a class="result__a" href="https://example.com/news">Latest News</a>
              <a class="result__snippet">Important update &amp; details.</a>
                            <img src="https://example.com/news.jpg" />
            </div>
            """.trimIndent()

        val results = parseSearchResults(html)

        assertEquals(1, results.size)
        assertEquals("Latest News", results[0].title)
        assertEquals("https://example.com/news", results[0].url)
        assertEquals("Important update & details.", results[0].snippet)
        assertEquals("https://example.com/news.jpg", results[0].imageUrl)
    }

    @Test
    fun ranksTrustedAndRelevantSourcesFirst() {
        val ranked =
            rankSearchResults(
                results = listOf(
                    WebSearchResult(
                        title = "General page",
                        url = "https://example.com/ram",
                        snippet = "About unrelated hardware."
                    ),
                    WebSearchResult(
                        title = "RAM guide",
                        url = "https://www.nasa.gov/ram-guide",
                        snippet = "RAM stores active data for applications."
                    )
                ),
                query = "what is RAM?"
            )

        assertEquals("https://www.nasa.gov/ram-guide", ranked.first().url)
    }

    @Test
    fun normalizesRedirectAndProtocolRelativeSourceLinks() {
        assertEquals(
            "https://example.com/article",
            normalizeSearchResultUrl(
                "//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Farticle"
            )
        )
        assertEquals(
            "https://example.com/article",
            normalizeSearchResultUrl("https://example.com/article")
        )
    }

    @Test
    fun handlesEmptyResults() {
        val manager = WebSearchManager { "<html><body>No results</body></html>" }

        val response = manager.searchDetailed("nothing")

        assertTrue(response.results.isEmpty())
        assertEquals(null, response.error)
        assertTrue(formatWebSearchReply(response).contains("couldn't find", ignoreCase = true))
    }

    @Test
    fun cachesSuccessfulSearchesUntilCacheIsCleared() {
        var fetchCount = 0
        val manager =
            WebSearchManager(
                fetch = {
                    fetchCount += 1
                    """
                    <div class="result">
                      <a class="result__a" href="https://example.com/ram">RAM guide</a>
                      <a class="result__snippet">RAM stores active data for apps.</a>
                    </div>
                    """.trimIndent()
                }
            )

        manager.searchDetailed("what is RAM?")
        manager.searchDetailed("what is RAM?")
        assertEquals(1, fetchCount)

        manager.clearCache()
        manager.searchDetailed("what is RAM?")
        assertEquals(2, fetchCount)
    }

    @Test
    fun formatsSummaryAndKeyPointsFromSearchResults() {
        val response =
            WebSearchResponse(
                results = listOf(
                    WebSearchResult(
                        title = "RAM guide",
                        url = "https://example.com/ram",
                        snippet = "RAM temporarily stores active data. It helps apps multitask."
                    ),
                    WebSearchResult(
                        title = "Memory basics",
                        url = "https://example.com/memory",
                        snippet = "More RAM can improve multitasking."
                    )
                )
            )

        val reply = formatWebSearchReply(response)

        assertTrue(reply.contains("Summary"))
        assertTrue(reply.contains("RAM temporarily stores active data."))
        assertTrue(reply.contains("Key points"))
        assertTrue(reply.contains("More RAM can improve multitasking."))
    }

    @Test
    fun summaryRemovesDuplicateAndShortSearchFragments() {
        val results =
            listOf(
                WebSearchResult(
                    title = "RAM guide",
                    url = "https://example.com/one",
                    snippet = "RAM temporarily stores active data."
                ),
                WebSearchResult(
                    title = "Memory basics",
                    url = "https://example.com/two",
                    snippet = "RAM temporarily stores active data. More RAM improves multitasking across apps."
                )
            )

        val summary = buildWebSearchSummary(results)

        assertTrue(summary.contains("RAM temporarily stores active data."))
        assertTrue(summary.contains("More RAM improves multitasking across apps."))
        assertTrue(summary.split("RAM temporarily stores active data.").size - 1 == 1)
    }

    @Test
    fun derivesKeyPointsFromTheSameSelectedEvidence() {
        val results =
            listOf(
                WebSearchResult(
                    title = "RAM guide",
                    url = "https://example.com/one",
                    snippet = "RAM temporarily stores active data for current apps."
                ),
                WebSearchResult(
                    title = "Memory basics",
                    url = "https://example.com/two",
                    snippet = "More RAM improves multitasking across apps. That is why larger memory helps performance."
                ),
                WebSearchResult(
                    title = "System overview",
                    url = "https://example.com/three",
                    snippet = "Operating systems use virtual memory to manage background tasks."
                )
            )

        val summary = buildWebSearchSummary(results)
        val keyPoints = buildWebSearchKeyPoints(results)

        assertTrue(summary.contains("RAM temporarily stores active data for current apps."))
        assertTrue(summary.contains("More RAM improves multitasking across apps."))
        assertTrue(keyPoints.any { it.contains("RAM temporarily stores active data for current apps") })
        assertTrue(keyPoints.any { it.contains("More RAM improves multitasking across apps") })
        assertTrue(!keyPoints.any { it.contains("Operating systems use virtual memory to manage background tasks") })
    }

    @Test
    fun identifiesUrlOnlyDisplayText() {
        assertTrue(isUrlLikeText("https://example.com/article"))
        assertTrue(!isUrlLikeText("RAM guide"))
    }

    @Test
    fun handlesNetworkFailure() {
        val manager = WebSearchManager { throw java.io.IOException("offline") }

        val response = manager.searchDetailed("latest news")

        assertTrue(response.results.isEmpty())
        assertTrue(response.error != null)
        assertTrue(formatWebSearchReply(response).contains("couldn't access", ignoreCase = true))
    }

    @Test
    fun ignoresMalformedResultMarkup() {
        val html =
            """
            <a class="result__a">Missing URL</a>
            <a class="result__snippet">Incomplete result</a>
            """.trimIndent()

        assertTrue(parseSearchResults(html).isEmpty())
    }
}
