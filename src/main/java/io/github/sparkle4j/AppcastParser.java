package io.github.sparkle4j;

import org.jspecify.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Parses an RSS 2.0 + Sparkle-namespace appcast XML into a list of {@link UpdateItem}s.
 *
 * <p>Only items that have an {@code <enclosure>} matching the current OS are included. Items are
 * returned sorted by version descending (highest first).
 */
public final class AppcastParser {

    private static final Logger log = Logger.getLogger(AppcastParser.class.getName());
    private static final String SPARKLE_NS = "http://www.andymatuschak.org/xml-namespaces/sparkle";

    List<UpdateItem> parse(String xml)
            throws SAXException, IOException, ParserConfigurationException {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        try {
            var doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            var currentOs = currentOs();
            int currentJvmMajor = Runtime.version().feature();

            var result = new ArrayList<UpdateItem>();
            NodeList itemNodes = doc.getElementsByTagName("item");

            for (int i = 0; i < itemNodes.getLength(); i++) {
                if (itemNodes.item(i) instanceof Element item) {
                    var parsed = parseItem(item, currentOs, currentJvmMajor);
                    if (parsed != null) result.add(parsed);
                }
            }

            result.sort((a, b) -> VersionComparator.INSTANCE.compare(b.version(), a.version()));
            return result;
        } catch (ParserConfigurationException e) {
            throw new IOException("XML parser configuration error", e);
        }
    }

    private @Nullable UpdateItem parseItem(Element item, String currentOs, int currentJvmMajor) {
        var itemVersion = sparkleElement(item, "version");
        var itemShortVersion = sparkleElement(item, "shortVersionString");

        var minJavaStr = sparkleElement(item, "minimumJavaVersion");
        Integer minJava = minJavaStr != null ? parseIntOrNull(minJavaStr) : null;
        if (minJava != null && currentJvmMajor < minJava) return null;

        var title = firstElementText(item, "title");
        if (title == null) title = "";
        var pubDate = firstElementText(item, "pubDate");
        var releaseNotesUrl = sparkleElement(item, "releaseNotesLink");
        var inlineDescription = firstElementText(item, "description");
        boolean isCritical =
                item.getElementsByTagNameNS(SPARKLE_NS, "criticalUpdate").getLength() > 0;

        NodeList enclosures = item.getElementsByTagName("enclosure");
        for (int j = 0; j < enclosures.getLength(); j++) {
            if (enclosures.item(j) instanceof Element enc) {
                var result =
                        parseEnclosure(
                                enc,
                                currentOs,
                                itemVersion,
                                itemShortVersion,
                                releaseNotesUrl,
                                inlineDescription,
                                title,
                                pubDate,
                                minJava,
                                isCritical);
                if (result != null) return result;
            }
        }
        return null;
    }

    private @Nullable UpdateItem parseEnclosure(
            Element enc,
            String currentOs,
            @Nullable String itemVersion,
            @Nullable String itemShortVersion,
            @Nullable String releaseNotesUrl,
            @Nullable String inlineDescription,
            String title,
            @Nullable String pubDate,
            @Nullable Integer minJava,
            boolean isCritical) {
        var os = enc.getAttributeNS(SPARKLE_NS, "os").toLowerCase(Locale.ROOT);
        if (!os.equals(currentOs)) return null;

        var url = enc.getAttribute("url");
        if (url.isBlank()) return null;
        if (!url.startsWith("https://")) {
            log.warning("Rejecting non-HTTPS enclosure URL: " + url);
            return null;
        }

        var version = nonBlankOrNull(enc.getAttributeNS(SPARKLE_NS, "version"));
        if (version == null) version = itemVersion;
        if (version == null) return null;

        var shortVersion = nonBlankOrNull(enc.getAttributeNS(SPARKLE_NS, "shortVersionString"));
        if (shortVersion == null) shortVersion = itemShortVersion;
        if (shortVersion == null) shortVersion = version;

        long length = parseLongOrZero(enc.getAttribute("length"));
        var edSig = nonBlankOrNull(enc.getAttributeNS(SPARKLE_NS, "edSignature"));
        var installerType = nonBlankOrNull(enc.getAttributeNS(SPARKLE_NS, "installerType"));
        if (installerType == null) installerType = "inno";

        return new UpdateItem(
                title,
                version,
                shortVersion,
                url,
                length,
                edSig,
                releaseNotesUrl,
                releaseNotesUrl == null ? inlineDescription : null,
                minJava,
                pubDate,
                os,
                isCritical,
                installerType);
    }

    private static @Nullable String sparkleElement(Element el, String localName) {
        var nodes = el.getElementsByTagNameNS(SPARKLE_NS, localName);
        if (nodes.getLength() == 0) return null;
        var text = nodes.item(0).getTextContent();
        return text != null && !text.isBlank() ? text.trim() : null;
    }

    private static @Nullable String firstElementText(Element el, String tagName) {
        var nodes = el.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        var text = nodes.item(0).getTextContent();
        return text != null && !text.isBlank() ? text.trim() : null;
    }

    private static @Nullable String nonBlankOrNull(@Nullable String s) {
        return s != null && !s.isBlank() ? s : null;
    }

    private static @Nullable Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long parseLongOrZero(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static String currentOs() {
        var name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) return "windows";
        if (name.contains("mac")) return "macos";
        return "linux";
    }
}
