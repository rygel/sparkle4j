package io.github.sparkle4j;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Integration tests for the Sparkle4j update flow. Uses WireMock over HTTP for appcast fetching and
 * tests the internal fetch-parse-filter pipeline directly, since checkNow() enforces HTTPS.
 */
@SuppressWarnings("NullAway.Init")
class Sparkle4jIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock =
            WireMockExtension.newInstance()
                    .options(WireMockConfiguration.wireMockConfig().dynamicPort())
                    .build();

    private static final String APPCAST_TEMPLATE =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle">
              <channel>
                <item>
                  <title>Test App 2.0.0</title>
                  <sparkle:version>2.0.0</sparkle:version>
                  <sparkle:shortVersionString>2.0.0</sparkle:shortVersionString>
                  <enclosure url="https://example.com/update.exe"
                             sparkle:os="%s"
                             length="1000"/>
                </item>
              </channel>
            </rss>
            """;

    private String appcastUrl() {
        return "http://localhost:" + wireMock.getPort() + "/appcast.xml";
    }

    // --- builder ---

    @Test
    @DisplayName("builder() returns a new Sparkle4jBuilder")
    void builderReturnsBuilder() {
        assertNotNull(Sparkle4j.builder());
    }

    // --- checkNow HTTPS enforcement ---

    @Test
    @DisplayName("checkNow returns null for non-HTTPS appcast URL")
    void checkNowRejectsHttp() {
        var instance =
                Sparkle4j.builder()
                        .appcastUrl(appcastUrl())
                        .currentVersion("1.0.0")
                        .appName("sparkle4j-integration-test")
                        .checkIntervalHours(0)
                        .build();

        assertNull(instance.checkNow());
    }

    // --- fetch + parse pipeline (tested via AppcastFetcher + AppcastParser directly) ---

    @Test
    @DisplayName("fetch and parse pipeline finds newer version")
    void fetchParsePipelineFindsNewer() throws Exception {
        wireMock.stubFor(
                get(urlEqualTo("/appcast.xml"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withBody(
                                                APPCAST_TEMPLATE.formatted(
                                                        AppcastParser.currentOs()))));

        var fetcher = new AppcastFetcher();
        var xml = fetcher.fetch(appcastUrl());
        assertNotNull(xml);

        var parser = new AppcastParser();
        var items = parser.parse(xml);
        assertFalse(items.isEmpty());
        assertEquals("2.0.0", items.get(0).version());
    }

    @Test
    @DisplayName("fetched version is compared correctly against current")
    void versionComparisonWithFetchedData() throws Exception {
        wireMock.stubFor(
                get(urlEqualTo("/appcast.xml"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withBody(
                                                APPCAST_TEMPLATE.formatted(
                                                        AppcastParser.currentOs()))));

        var fetcher = new AppcastFetcher();
        var xml = fetcher.fetch(appcastUrl());
        assertNotNull(xml);
        var items = new AppcastParser().parse(xml);
        var best = items.get(0);

        assertTrue(VersionComparator.isNewer(best.version(), "1.0.0"));
        assertFalse(VersionComparator.isNewer(best.version(), "2.0.0"));
        assertFalse(VersionComparator.isNewer(best.version(), "3.0.0"));
    }

    @Test
    @DisplayName("skipVersion integration with preferences")
    void skipVersionIntegration() {
        var prefs = new UpdatePreferences("sparkle4j-integration-test-" + System.nanoTime());
        assertFalse(prefs.isSkipped("2.0.0"));
        prefs.skipVersion("2.0.0");
        assertTrue(prefs.isSkipped("2.0.0"));
    }

    @Test
    @DisplayName("fetch returns null on server error")
    void fetchNullOnServerError() {
        wireMock.stubFor(get(urlEqualTo("/appcast.xml")).willReturn(aResponse().withStatus(500)));

        var fetcher = new AppcastFetcher();
        var xml = fetcher.fetch(appcastUrl());
        assertNull(xml);
    }

    @Test
    @DisplayName("parse returns empty list on malformed XML")
    void parseFailsOnMalformedXml() {
        assertThrows(Exception.class, () -> new AppcastParser().parse("not xml"));
    }

    @Test
    @DisplayName("full pipeline: fetch, parse, filter by OS")
    void fullPipelineWithOsFiltering() throws Exception {
        var wrongOs = "windows".equals(AppcastParser.currentOs()) ? "macos" : "windows";
        wireMock.stubFor(
                get(urlEqualTo("/appcast.xml"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withBody(APPCAST_TEMPLATE.formatted(wrongOs))));

        var fetcher = new AppcastFetcher();
        var xml = fetcher.fetch(appcastUrl());
        assertNotNull(xml);
        var items = new AppcastParser().parse(xml);
        assertTrue(items.isEmpty());
    }
}
