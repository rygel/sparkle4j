package io.github.sparkle4j

import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.logging.Logger
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses an RSS 2.0 + Sparkle-namespace appcast XML into a list of [UpdateItem]s.
 *
 * Only items that have an `<enclosure>` matching the current OS are included.
 * Items are returned sorted by version descending (highest first).
 */
internal class AppcastParser {

    private val log = Logger.getLogger(AppcastParser::class.java.name)

    fun parse(xml: String): List<UpdateItem> {
        val factory = DocumentBuilderFactory.newInstance().also { it.isNamespaceAware = true }
        val doc = factory.newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))

        val currentOs = currentOs()
        val currentJvmMajor = Runtime.version().feature()

        val result = mutableListOf<UpdateItem>()
        val itemNodes: NodeList = doc.getElementsByTagName("item")

        for (i in 0 until itemNodes.length) {
            val item = itemNodes.item(i) as? Element ?: continue

            // Item-level version fields (may be overridden by enclosure-level attributes below)
            val itemVersion = item.sparkleElement("version")
            val itemShortVersion = item.sparkleElement("shortVersionString")

            // sparkle:minimumJavaVersion is a sparkle4j-specific custom element.
            // We deliberately do NOT read sparkle:minimumSystemVersion here because that element
            // carries a macOS version string in real Sparkle feeds and must remain untouched.
            val minJava = item.sparkleElement("minimumJavaVersion")?.toIntOrNull()
            if (minJava != null && currentJvmMajor < minJava) continue

            val title = item.firstElementText("title") ?: ""
            val pubDate = item.firstElementText("pubDate")
            val releaseNotesUrl = item.sparkleElement("releaseNotesLink")?.takeIf { it.isNotBlank() }

            // Inline description as fallback release notes (plain text / HTML)
            val inlineDescription = item.firstElementText("description")

            // sparkle:criticalUpdate — bare element presence means critical
            val isCritical = item.getElementsByTagNameNS(SPARKLE_NS, "criticalUpdate").length > 0

            val enclosures: NodeList = item.getElementsByTagName("enclosure")
            for (j in 0 until enclosures.length) {
                val enc = enclosures.item(j) as? Element ?: continue
                val os = enc.getAttributeNS(SPARKLE_NS, "os").lowercase()
                if (os != currentOs) continue

                val url = enc.getAttribute("url") ?: continue
                if (url.isBlank()) continue
                if (!url.startsWith("https://")) {
                    log.warning("Rejecting non-HTTPS enclosure URL: $url")
                    continue
                }

                // Enclosure-level version overrides item-level (Sparkle precedence)
                val version = enc.getAttributeNS(SPARKLE_NS, "version").takeIf { it.isNotBlank() }
                    ?: itemVersion ?: continue
                val shortVersion = enc.getAttributeNS(SPARKLE_NS, "shortVersionString").takeIf { it.isNotBlank() }
                    ?: itemShortVersion
                    ?: version

                val length = enc.getAttribute("length").toLongOrNull() ?: 0L
                val edSig = enc.getAttributeNS(SPARKLE_NS, "edSignature").takeIf { it.isNotBlank() }
                val installerType = enc.getAttributeNS(SPARKLE_NS, "installerType")
                    .takeIf { it.isNotBlank() } ?: "inno"

                result.add(
                    UpdateItem(
                        title = title,
                        version = version,
                        shortVersionString = shortVersion,
                        url = url,
                        length = length,
                        edSignature = edSig,
                        releaseNotesUrl = releaseNotesUrl,
                        inlineDescription = if (releaseNotesUrl == null) inlineDescription else null,
                        minimumJavaVersion = minJava,
                        pubDate = pubDate,
                        os = os,
                        isCriticalUpdate = isCritical,
                        installerType = installerType,
                    )
                )
                break // one enclosure per item per OS
            }
        }

        result.sortWith(compareByDescending(VersionComparator) { it.version })
        return result
    }

    private fun Element.sparkleElement(localName: String): String? =
        getElementsByTagNameNS(SPARKLE_NS, localName)
            .takeIf { it.length > 0 }
            ?.item(0)?.textContent?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun Element.firstElementText(tagName: String): String? =
        getElementsByTagName(tagName)
            .takeIf { it.length > 0 }
            ?.item(0)?.textContent?.trim()
            ?.takeIf { it.isNotBlank() }

    companion object {
        private const val SPARKLE_NS = "http://www.andymatuschak.org/xml-namespaces/sparkle"

        fun currentOs(): String {
            val name = System.getProperty("os.name", "").lowercase()
            return when {
                name.contains("win") -> "windows"
                name.contains("mac") -> "macos"
                else -> "linux"
            }
        }
    }
}
