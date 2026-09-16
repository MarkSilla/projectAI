package com.marksilla.auraagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchManagerTest {
    @Test
    fun detectsRequestsThatNeedCurrentWebInformation() {
        assertTrue(shouldSearchWeb("What's the latest news today?"))
        assertTrue(shouldSearchWeb("Find information about Gordon College"))
        assertTrue(shouldSearchWeb("What is the weather today?"))
    }

    @Test
    fun leavesLocalRequestsOffline() {
        assertTrue(!shouldSearchWeb("What is 25 times 8?"))
        assertTrue(!shouldSearchWeb("Open Messenger"))
        assertTrue(!shouldSearchWeb("What is RAM?"))
    }

    @Test
    fun parsesStructuredSearchResults() {
        val html =
            """
            <div class="result">
              <a class="result__a" href="https://example.com/news">Latest News</a>
              <a class="result__snippet">Important update &amp; details.</a>
            </div>
            """.trimIndent()

        val results = parseSearchResults(html)

        assertEquals(1, results.size)
        assertEquals("Latest News", results[0].title)
        assertEquals("https://example.com/news", results[0].url)
        assertEquals("Important update & details.", results[0].snippet)
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
