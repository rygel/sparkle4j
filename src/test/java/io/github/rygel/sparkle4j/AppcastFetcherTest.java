package io.github.rygel.sparkle4j;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AppcastFetcherTest {

    @RegisterExtension
    static WireMockExtension wireMock =
            WireMockExtension.newInstance()
                    .options(WireMockConfiguration.wireMockConfig().dynamicPort())
                    .build();

    private final AppcastFetcher fetcher = new AppcastFetcher();

    private String url(String path) {
        return "http://localhost:" + wireMock.getPort() + path;
    }

    @Test
    @DisplayName("returns body on 200")
    void returnsBodyOn200() {
        wireMock.stubFor(
                get(urlEqualTo("/appcast.xml"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/xml")
                                        .withBody("<rss/>")));

        assertEquals("<rss/>", fetcher.fetch(url("/appcast.xml")));
    }

    @Test
    @DisplayName("sends If-None-Match on second request and returns cached body on 304")
    void etagCaching() {
        var etag = "\"abc123\"";

        wireMock.stubFor(
                get(urlEqualTo("/appcast.xml"))
                        .withHeader("If-None-Match", absent())
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("ETag", etag)
                                        .withBody("<rss/>")));

        wireMock.stubFor(
                get(urlEqualTo("/appcast.xml"))
                        .withHeader("If-None-Match", equalTo(etag))
                        .willReturn(aResponse().withStatus(304)));

        assertEquals("<rss/>", fetcher.fetch(url("/appcast.xml")));
        assertEquals("<rss/>", fetcher.fetch(url("/appcast.xml")));

        wireMock.verify(2, getRequestedFor(urlEqualTo("/appcast.xml")));
    }

    @Test
    @DisplayName("sends If-Modified-Since when ETag absent")
    void lastModifiedCaching() {
        var lastModified = "Wed, 01 Jan 2026 00:00:00 GMT";

        wireMock.stubFor(
                get(urlEqualTo("/appcast.xml"))
                        .withHeader("If-Modified-Since", absent())
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Last-Modified", lastModified)
                                        .withBody("<rss/>")));

        wireMock.stubFor(
                get(urlEqualTo("/appcast.xml"))
                        .withHeader("If-Modified-Since", equalTo(lastModified))
                        .willReturn(aResponse().withStatus(304)));

        fetcher.fetch(url("/appcast.xml"));
        assertEquals("<rss/>", fetcher.fetch(url("/appcast.xml")));
    }

    @Test
    @DisplayName("returns null on unexpected HTTP status")
    void returnsNullOnError() {
        wireMock.stubFor(get(urlEqualTo("/appcast.xml")).willReturn(aResponse().withStatus(500)));

        assertNull(fetcher.fetch(url("/appcast.xml")));
    }

    @Test
    @DisplayName("returns null on 404")
    void returnsNullOn404() {
        wireMock.stubFor(get(urlEqualTo("/appcast.xml")).willReturn(aResponse().withStatus(404)));

        assertNull(fetcher.fetch(url("/appcast.xml")));
    }

    @Test
    @DisplayName("returns null when server is unreachable")
    void returnsNullOnUnreachable() {
        assertNull(fetcher.fetch("http://localhost:1/appcast.xml"));
    }

    @Test
    @DisplayName("follows redirect")
    void followsRedirect() {
        wireMock.stubFor(
                get(urlEqualTo("/old-appcast.xml"))
                        .willReturn(permanentRedirect(url("/appcast.xml"))));
        wireMock.stubFor(
                get(urlEqualTo("/appcast.xml"))
                        .willReturn(aResponse().withStatus(200).withBody("<rss/>")));

        assertEquals("<rss/>", fetcher.fetch(url("/old-appcast.xml")));
    }

    @Test
    @DisplayName("304 without prior cache returns null")
    void returns304WithoutCacheReturnsNull() {
        // Server returns 304 on first request (no prior cache entry)
        wireMock.stubFor(get(urlEqualTo("/appcast.xml")).willReturn(aResponse().withStatus(304)));

        // New fetcher with empty cache
        var freshFetcher = new AppcastFetcher();
        assertNull(freshFetcher.fetch(url("/appcast.xml")));
    }

    @Test
    @DisplayName("caches response without ETag or Last-Modified")
    void cachesWithoutHeaders() {
        wireMock.stubFor(
                get(urlEqualTo("/appcast.xml"))
                        .willReturn(aResponse().withStatus(200).withBody("<rss>data</rss>")));

        assertEquals("<rss>data</rss>", fetcher.fetch(url("/appcast.xml")));
        // Second fetch should still work (cache exists but no conditional headers)
        assertEquals("<rss>data</rss>", fetcher.fetch(url("/appcast.xml")));
    }
}
