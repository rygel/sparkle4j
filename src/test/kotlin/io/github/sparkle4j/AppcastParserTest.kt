package io.github.sparkle4j

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppcastParserTest {

    private val parser = AppcastParser()

    private fun xml(resource: String): String = AppcastParserTest::class.java.classLoader
        .getResourceAsStream(resource)!!.bufferedReader().readText()

    // --- happy path ---

    @Test
    fun `parses valid feed and returns highest version first`() {
        val items = withOs("windows") { parser.parse(xml("appcast-valid.xml")) }
        assertTrue(items.isNotEmpty(), "Expected at least one item")
        // Machine-readable version (build number) used for sorting
        assertEquals("47391", items.first().version)
        // Human-readable version shown in UI
        assertEquals("0.7.0", items.first().shortVersionString)
    }

    @Test
    fun `picks correct enclosure for windows`() {
        val items = withOs("windows") { parser.parse(xml("appcast-valid.xml")) }
        val item = items.first()
        assertEquals("windows", item.os)
        assertTrue(item.url.contains("windows"), "Expected windows URL")
        assertEquals(185_000_000L, item.length)
        assertEquals("AAAA", item.edSignature)
    }

    @Test
    fun `reads criticalUpdate element`() {
        val items = withOs("windows") { parser.parse(xml("appcast-valid.xml")) }
        assertTrue(items.first().isCriticalUpdate, "0.7.0 should be marked as critical")
    }

    @Test
    fun `picks correct enclosure for linux`() {
        val items = withOs("linux") { parser.parse(xml("appcast-valid.xml")) }
        val item = items.first()
        assertEquals("linux", item.os)
        assertTrue(item.url.endsWith(".deb"))
    }

    @Test
    fun `populates optional fields`() {
        val items = withOs("windows") { parser.parse(xml("appcast-valid.xml")) }
        val item = items.first()
        assertEquals("https://example.com/changelog", item.releaseNotesUrl)
        assertNotNull(item.pubDate)
    }

    // --- filtering ---

    @Test
    fun `skips items without enclosure for current OS`() {
        // appcast-valid.xml has no macos 0_6_0 enclosure — only one macos item
        val items = withOs("macos") { parser.parse(xml("appcast-valid.xml")) }
        assertEquals(1, items.size)
        assertEquals("0.7.0", items.first().shortVersionString)
    }

    @Test
    fun `skips items whose minimum java version exceeds current JVM`() {
        val items = withOs("windows") { parser.parse(xml("appcast-min-java.xml")) }
        assertTrue(items.isEmpty(), "Item requiring Java 999 should be filtered out")
    }

    @Test
    fun `rejects non-HTTPS enclosure URL`() {
        val feedWithHttp = xml("appcast-valid.xml")
            .replace(
                "https://example.com/releases/testapp-0.7.0-windows.exe",
                "http://example.com/releases/testapp-0.7.0-windows.exe",
            )
        val items = withOs("windows") { parser.parse(feedWithHttp) }
        // 0.7.0 has no valid windows enclosure now; 0.6.0 still has one
        assertTrue(items.none { it.version == "0.7.0" }, "0.7.0 should be rejected (http URL)")
    }

    @Test
    fun `falls back to inline description when no releaseNotesLink`() {
        val items = withOs("windows") { parser.parse(xml("appcast-inline-description.xml")) }
        val item = items.first()
        assertNull(item.releaseNotesUrl)
        assertTrue(item.inlineDescription?.contains("Fixed a crash") == true)
    }

    @Test
    fun `shortVersionString falls back to version when absent`() {
        val feed = xml("appcast-valid.xml")
            .replace("<sparkle:shortVersionString>0.7.0</sparkle:shortVersionString>", "")
        val items = withOs("windows") { parser.parse(feed) }
        // shortVersionString should equal version when the element is absent
        assertEquals(items.first().version, items.first().shortVersionString)
    }

    // --- error cases ---

    @Test
    fun `throws on malformed XML`() {
        assertThrows<Exception> { parser.parse("<not valid xml") }
    }

    @Test
    fun `returns empty list for feed with no items`() {
        val emptyFeed = """<?xml version="1.0"?>
            <rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle">
              <channel/>
            </rss>"""
        val items = withOs("windows") { parser.parse(emptyFeed) }
        assertTrue(items.isEmpty())
    }

    @Test
    fun `ignores items missing sparkle version element`() {
        val feed = """<?xml version="1.0"?>
            <rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle">
              <channel>
                <item>
                  <title>No version</title>
                  <enclosure url="https://example.com/x.exe" sparkle:os="windows" length="100" type="application/octet-stream"/>
                </item>
              </channel>
            </rss>"""
        val items = withOs("windows") { parser.parse(feed) }
        assertTrue(items.isEmpty())
    }

    // --- helpers ---

    private fun <T> withOs(os: String, block: () -> T): T {
        val original = System.getProperty("os.name")
        System.setProperty(
            "os.name",
            when (os) {
                "windows" -> "Windows 10"
                "macos" -> "Mac OS X"
                else -> "Linux"
            },
        )
        return try {
            block()
        } finally {
            System.setProperty("os.name", original)
        }
    }
}
