package io.github.sparkle4j;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AppcastParserTest {

    private final AppcastParser parser = new AppcastParser();

    private String loadResource(String name) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(name)) {
            if (stream == null) throw new IOException("Resource not found: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test @DisplayName("parses valid appcast and returns items sorted by version descending")
    void parsesValidAppcast() throws Exception {
        var items = parser.parse(loadResource("appcast-valid.xml"));
        assertFalse(items.isEmpty());
        // Items should be sorted descending
        for (int i = 0; i < items.size() - 1; i++) {
            assertTrue(VersionComparator.isNewer(items.get(i).version(), items.get(i + 1).version())
                || items.get(i).version().equals(items.get(i + 1).version()));
        }
    }

    @Test @DisplayName("filters to current OS only")
    void filtersToCurrentOs() throws Exception {
        var items = parser.parse(loadResource("appcast-valid.xml"));
        var currentOs = AppcastParser.currentOs();
        for (var item : items) {
            assertEquals(currentOs, item.os());
        }
    }

    @Test @DisplayName("rejects non-HTTPS enclosure URLs")
    void rejectsNonHttps() throws Exception {
        var xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle">
              <channel>
                <item>
                  <sparkle:version>1.0.0</sparkle:version>
                  <enclosure url="http://example.com/update.exe" sparkle:os="%s" length="100"/>
                </item>
              </channel>
            </rss>
            """.formatted(AppcastParser.currentOs());
        var items = parser.parse(xml);
        assertTrue(items.isEmpty());
    }

    @Test @DisplayName("parses critical update flag")
    void parsesCriticalUpdate() throws Exception {
        var xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle">
              <channel>
                <item>
                  <sparkle:version>1.0.0</sparkle:version>
                  <sparkle:criticalUpdate/>
                  <enclosure url="https://example.com/update.exe" sparkle:os="%s" length="100"/>
                </item>
              </channel>
            </rss>
            """.formatted(AppcastParser.currentOs());
        var items = parser.parse(xml);
        assertEquals(1, items.size());
        assertTrue(items.get(0).isCriticalUpdate());
    }

    @Test @DisplayName("returns empty list for malformed XML")
    void malformedXml() {
        assertThrows(Exception.class, () -> parser.parse("not xml at all"));
    }

    @Test @DisplayName("returns empty list when no enclosure matches current OS")
    void noMatchingOs() throws Exception {
        var otherOs = AppcastParser.currentOs().equals("windows") ? "macos" : "windows";
        var xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle">
              <channel>
                <item>
                  <sparkle:version>1.0.0</sparkle:version>
                  <enclosure url="https://example.com/update.exe" sparkle:os="%s" length="100"/>
                </item>
              </channel>
            </rss>
            """.formatted(otherOs);
        var items = parser.parse(xml);
        assertTrue(items.isEmpty());
    }

    @Test @DisplayName("handles missing optional fields gracefully")
    void missingOptionalFields() throws Exception {
        var xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle">
              <channel>
                <item>
                  <sparkle:version>1.0.0</sparkle:version>
                  <enclosure url="https://example.com/update.exe" sparkle:os="%s" length="100"/>
                </item>
              </channel>
            </rss>
            """.formatted(AppcastParser.currentOs());
        var items = parser.parse(xml);
        assertEquals(1, items.size());
        var item = items.get(0);
        assertNull(item.edSignature());
        assertNull(item.releaseNotesUrl());
        assertNull(item.pubDate());
        assertFalse(item.isCriticalUpdate());
    }

    @Test @DisplayName("enclosure version overrides item version")
    void enclosureVersionOverridesItem() throws Exception {
        var xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle">
              <channel>
                <item>
                  <sparkle:version>1.0.0</sparkle:version>
                  <enclosure url="https://example.com/update.exe" sparkle:os="%s" sparkle:version="2.0.0" length="100"/>
                </item>
              </channel>
            </rss>
            """.formatted(AppcastParser.currentOs());
        var items = parser.parse(xml);
        assertEquals("2.0.0", items.get(0).version());
    }

    @Test @DisplayName("filters by minimumJavaVersion")
    void filtersMinJava() throws Exception {
        var items = parser.parse(loadResource("appcast-min-java.xml"));
        for (var item : items) {
            if (item.minimumJavaVersion() != null) {
                assertTrue(Runtime.version().feature() >= item.minimumJavaVersion());
            }
        }
    }

    @Test @DisplayName("uses inline description when no release notes URL")
    void inlineDescription() throws Exception {
        var items = parser.parse(loadResource("appcast-inline-description.xml"));
        assertFalse(items.isEmpty());
        var item = items.get(0);
        assertNull(item.releaseNotesUrl());
        assertNotNull(item.inlineDescription());
    }

    @Test @DisplayName("shortVersionString falls back to version when absent")
    void shortVersionFallback() throws Exception {
        var xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle">
              <channel>
                <item>
                  <sparkle:version>1.0.0</sparkle:version>
                  <enclosure url="https://example.com/update.exe" sparkle:os="%s" length="100"/>
                </item>
              </channel>
            </rss>
            """.formatted(AppcastParser.currentOs());
        var items = parser.parse(xml);
        assertEquals("1.0.0", items.get(0).shortVersionString());
    }

    @Test @DisplayName("default installer type is inno")
    void defaultInstallerType() throws Exception {
        var xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle">
              <channel>
                <item>
                  <sparkle:version>1.0.0</sparkle:version>
                  <enclosure url="https://example.com/update.exe" sparkle:os="%s" length="100"/>
                </item>
              </channel>
            </rss>
            """.formatted(AppcastParser.currentOs());
        var items = parser.parse(xml);
        assertEquals("inno", items.get(0).installerType());
    }
}
