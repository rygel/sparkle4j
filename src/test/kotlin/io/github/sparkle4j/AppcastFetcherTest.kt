package io.github.sparkle4j

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppcastFetcherTest {

    @RegisterExtension
    val wireMock: WireMockExtension = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build()

    private val fetcher = AppcastFetcher()

    private fun url(path: String) = "http://localhost:${wireMock.port}$path"

    @Test
    fun `returns body on 200`() {
        wireMock.stubFor(
            get(urlEqualTo("/appcast.xml"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/xml")
                    .withBody("<rss/>"))
        )

        val result = fetcher.fetch(url("/appcast.xml"))
        assertEquals("<rss/>", result)
    }

    @Test
    fun `sends If-None-Match on second request and returns null on 304`() {
        val etag = "\"abc123\""

        wireMock.stubFor(
            get(urlEqualTo("/appcast.xml"))
                .withHeader("If-None-Match", absent())
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("ETag", etag)
                    .withBody("<rss/>"))
        )
        wireMock.stubFor(
            get(urlEqualTo("/appcast.xml"))
                .withHeader("If-None-Match", equalTo(etag))
                .willReturn(aResponse().withStatus(304))
        )

        val first = fetcher.fetch(url("/appcast.xml"))
        assertEquals("<rss/>", first)

        // Second call should hit 304 and return the cached body
        val second = fetcher.fetch(url("/appcast.xml"))
        assertEquals("<rss/>", second)

        wireMock.verify(2, getRequestedFor(urlEqualTo("/appcast.xml")))
    }

    @Test
    fun `sends If-Modified-Since when ETag absent`() {
        val lastModified = "Wed, 01 Jan 2026 00:00:00 GMT"

        wireMock.stubFor(
            get(urlEqualTo("/appcast.xml"))
                .withHeader("If-Modified-Since", absent())
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Last-Modified", lastModified)
                    .withBody("<rss/>"))
        )
        wireMock.stubFor(
            get(urlEqualTo("/appcast.xml"))
                .withHeader("If-Modified-Since", equalTo(lastModified))
                .willReturn(aResponse().withStatus(304))
        )

        fetcher.fetch(url("/appcast.xml"))
        val second = fetcher.fetch(url("/appcast.xml"))
        assertEquals("<rss/>", second) // cached body returned on 304
    }

    @Test
    fun `returns null on unexpected HTTP status`() {
        wireMock.stubFor(
            get(urlEqualTo("/appcast.xml"))
                .willReturn(aResponse().withStatus(500))
        )

        val result = fetcher.fetch(url("/appcast.xml"))
        assertNull(result)
    }

    @Test
    fun `returns null on 404`() {
        wireMock.stubFor(
            get(urlEqualTo("/appcast.xml"))
                .willReturn(aResponse().withStatus(404))
        )

        assertNull(fetcher.fetch(url("/appcast.xml")))
    }

    @Test
    fun `returns null when server is unreachable`() {
        // Port 1 is almost certainly not listening
        val result = fetcher.fetch("http://localhost:1/appcast.xml")
        assertNull(result)
    }

    @Test
    fun `follows redirect`() {
        wireMock.stubFor(
            get(urlEqualTo("/old-appcast.xml"))
                .willReturn(permanentRedirect(url("/appcast.xml")))
        )
        wireMock.stubFor(
            get(urlEqualTo("/appcast.xml"))
                .willReturn(aResponse().withStatus(200).withBody("<rss/>"))
        )

        assertEquals("<rss/>", fetcher.fetch(url("/old-appcast.xml")))
    }
}
