package io.github.sparkle4j.ui;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@SuppressWarnings("NullAway.Init")
class ReleaseNotesPanelTest {

    @RegisterExtension
    static WireMockExtension wireMock =
            WireMockExtension.newInstance()
                    .options(WireMockConfiguration.wireMockConfig().dynamicPort())
                    .build();

    // Use reflection to test package-private static methods
    private static String invoke(String methodName, String arg) {
        try {
            Method method = ReleaseNotesPanel.class.getDeclaredMethod(methodName, String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, arg);
        } catch (InvocationTargetException e) {
            throw new AssertionError(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean invokeLooksLikeMarkdown(String content) {
        try {
            Method method =
                    ReleaseNotesPanel.class.getDeclaredMethod("looksLikeMarkdown", String.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, content);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static String invokeRenderInlineContent(String arg) {
        try {
            Method method =
                    ReleaseNotesPanel.class.getDeclaredMethod("renderInlineContent", String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, (Object) arg);
        } catch (InvocationTargetException e) {
            throw new AssertionError(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // --- htmlEscape ---

    @Test
    @DisplayName("htmlEscape escapes ampersand, less-than, greater-than")
    void htmlEscapeSpecialChars() {
        var result = invoke("htmlEscape", "a < b & c > d");
        assertEquals("a &lt; b &amp; c &gt; d", result);
    }

    @Test
    @DisplayName("htmlEscape passes plain text through")
    void htmlEscapePlainText() {
        assertEquals("hello world", invoke("htmlEscape", "hello world"));
    }

    // --- inlineFormat ---

    @Test
    @DisplayName("inlineFormat converts bold markdown")
    void inlineFormatBold() {
        var result = invoke("inlineFormat", "this is **bold** text");
        assertTrue(result.contains("<b>bold</b>"));
    }

    @Test
    @DisplayName("inlineFormat converts inline code")
    void inlineFormatCode() {
        var result = invoke("inlineFormat", "use `code` here");
        assertTrue(result.contains("<code>code</code>"));
    }

    @Test
    @DisplayName("inlineFormat converts links")
    void inlineFormatLinks() {
        var result = invoke("inlineFormat", "click [here](https://example.com)");
        assertTrue(result.contains("<a href=\"https://example.com\">here</a>"));
    }

    @Test
    @DisplayName("inlineFormat escapes HTML before formatting")
    void inlineFormatEscapesFirst() {
        var result = invoke("inlineFormat", "<script> **bold**");
        assertTrue(result.contains("&lt;script&gt;"));
        assertTrue(result.contains("<b>bold</b>"));
    }

    // --- looksLikeMarkdown ---

    @Test
    @DisplayName("looksLikeMarkdown detects heading")
    void looksLikeMarkdownHeading() {
        assertTrue(invokeLooksLikeMarkdown("# Title"));
    }

    @Test
    @DisplayName("looksLikeMarkdown detects dash list")
    void looksLikeMarkdownDashList() {
        assertTrue(invokeLooksLikeMarkdown("- item"));
    }

    @Test
    @DisplayName("looksLikeMarkdown detects star list")
    void looksLikeMarkdownStarList() {
        assertTrue(invokeLooksLikeMarkdown("* item"));
    }

    @Test
    @DisplayName("looksLikeMarkdown returns false for plain text")
    void looksLikeMarkdownPlainText() {
        assertFalse(invokeLooksLikeMarkdown("just plain text"));
    }

    @Test
    @DisplayName("looksLikeMarkdown ignores leading whitespace")
    void looksLikeMarkdownIgnoresWhitespace() {
        assertTrue(invokeLooksLikeMarkdown("   # Title with spaces"));
    }

    // --- minimalMarkdownToHtml ---

    @Test
    @DisplayName("minimalMarkdownToHtml converts h1 heading")
    void markdownH1() {
        var result = invoke("minimalMarkdownToHtml", "# Title");
        assertTrue(result.contains("<h1>Title</h1>"));
    }

    @Test
    @DisplayName("minimalMarkdownToHtml converts h2 heading")
    void markdownH2() {
        var result = invoke("minimalMarkdownToHtml", "## Subtitle");
        assertTrue(result.contains("<h2>Subtitle</h2>"));
    }

    @Test
    @DisplayName("minimalMarkdownToHtml converts h3 heading")
    void markdownH3() {
        var result = invoke("minimalMarkdownToHtml", "### Section");
        assertTrue(result.contains("<h3>Section</h3>"));
    }

    @Test
    @DisplayName("minimalMarkdownToHtml converts list items")
    void markdownListItems() {
        var result = invoke("minimalMarkdownToHtml", "- first\n* second");
        assertTrue(result.contains("<li>first</li>"));
        assertTrue(result.contains("<li>second</li>"));
    }

    @Test
    @DisplayName("minimalMarkdownToHtml converts code blocks")
    void markdownCodeBlocks() {
        var result = invoke("minimalMarkdownToHtml", "```\nvar x = 1;\n```");
        assertTrue(result.contains("<pre><code>"));
        assertTrue(result.contains("var x = 1;"));
        assertTrue(result.contains("</code></pre>"));
    }

    @Test
    @DisplayName("minimalMarkdownToHtml converts blank lines to br")
    void markdownBlankLines() {
        var result = invoke("minimalMarkdownToHtml", "line1\n\nline2");
        assertTrue(result.contains("<br>"));
    }

    @Test
    @DisplayName("minimalMarkdownToHtml wraps plain text in paragraphs")
    void markdownParagraphs() {
        var result = invoke("minimalMarkdownToHtml", "just text");
        assertTrue(result.contains("<p>just text</p>"));
    }

    @Test
    @DisplayName("minimalMarkdownToHtml escapes HTML inside code blocks")
    void markdownCodeBlockEscapes() {
        var result = invoke("minimalMarkdownToHtml", "```\n<script>alert('xss')</script>\n```");
        assertTrue(result.contains("&lt;script&gt;"));
        assertFalse(result.contains("<script>"));
    }

    // --- renderInlineContent ---

    @SuppressWarnings("NullAway")
    @Test
    @DisplayName("renderInlineContent returns no-notes message for null")
    void renderInlineContentNull() {
        var result = invokeRenderInlineContent(null);
        assertTrue(result.contains("No release notes available."));
    }

    @Test
    @DisplayName("renderInlineContent escapes plain text")
    void renderInlineContentPlainText() {
        var result = invokeRenderInlineContent("simple text <b>not bold</b>");
        assertTrue(result.contains("&lt;b&gt;"));
    }

    @Test
    @DisplayName("renderInlineContent passes through HTML")
    void renderInlineContentHtml() {
        var html = "<html><body>Hello</body></html>";
        assertEquals(html, invokeRenderInlineContent(html));
    }

    @Test
    @DisplayName("renderInlineContent converts markdown")
    void renderInlineContentMarkdown() {
        var result = invokeRenderInlineContent("# Release Notes");
        assertTrue(result.contains("<h1>Release Notes</h1>"));
    }

    // --- bodyStyle ---

    @Test
    @DisplayName("bodyStyle returns CSS with color and background-color")
    void bodyStyleContainsColors() {
        try {
            Method method = ReleaseNotesPanel.class.getDeclaredMethod("bodyStyle");
            method.setAccessible(true);
            var style = (String) method.invoke(null);
            assertTrue(style.contains("font-family:sans-serif"));
            assertTrue(style.contains("padding:8px"));
            assertTrue(style.contains("color:"));
            assertTrue(style.contains("background-color:"));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // --- colorToHex ---

    @Test
    @DisplayName("colorToHex converts Color to hex string")
    void colorToHexConversion() {
        try {
            Method method = ReleaseNotesPanel.class.getDeclaredMethod("colorToHex", Color.class);
            method.setAccessible(true);
            assertEquals("#FF0000", method.invoke(null, Color.RED));
            assertEquals("#00FF00", method.invoke(null, Color.GREEN));
            assertEquals("#0000FF", method.invoke(null, Color.BLUE));
            assertEquals("#000000", method.invoke(null, Color.BLACK));
            assertEquals("#FFFFFF", method.invoke(null, Color.WHITE));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // --- wrapHtml ---

    @Test
    @DisplayName("wrapHtml wraps content in styled body")
    void wrapHtmlProducesStyledBody() {
        var result = invoke("wrapHtml", "Hello");
        assertTrue(result.startsWith("<html><body style='"));
        assertTrue(result.contains("<p>Hello</p>"));
        assertTrue(result.endsWith("</body></html>"));
    }

    // --- fetchRemoteContent ---

    private static String invokeFetchRemoteContent(String url) {
        try {
            Method method =
                    ReleaseNotesPanel.class.getDeclaredMethod("fetchRemoteContent", String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, url);
        } catch (InvocationTargetException e) {
            throw new AssertionError(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("fetchRemoteContent returns HTML content as-is")
    void fetchRemoteHtmlContent() {
        wireMock.stubFor(
                get(urlEqualTo("/notes.html"))
                        .willReturn(aResponse().withStatus(200).withBody("<h1>Notes</h1>")));

        var result =
                invokeFetchRemoteContent("http://localhost:" + wireMock.getPort() + "/notes.html");
        assertEquals("<h1>Notes</h1>", result);
    }

    @Test
    @DisplayName("fetchRemoteContent converts markdown URL to HTML")
    void fetchRemoteMarkdownContent() {
        wireMock.stubFor(
                get(urlEqualTo("/notes.md"))
                        .willReturn(aResponse().withStatus(200).withBody("# Release Notes")));

        var result =
                invokeFetchRemoteContent("http://localhost:" + wireMock.getPort() + "/notes.md");
        assertTrue(result.contains("<h1>Release Notes</h1>"));
    }

    @Test
    @DisplayName("fetchRemoteContent returns error message on HTTP failure")
    void fetchRemoteContentHttpError() {
        wireMock.stubFor(get(urlEqualTo("/notes.html")).willReturn(aResponse().withStatus(500)));

        var result =
                invokeFetchRemoteContent("http://localhost:" + wireMock.getPort() + "/notes.html");
        assertTrue(result.contains("Could not load release notes."));
    }

    @Test
    @DisplayName("fetchRemoteContent returns error message on invalid URL")
    void fetchRemoteContentInvalidUrl() {
        var result = invokeFetchRemoteContent("not a valid url at all");
        assertTrue(result.contains("Could not load release notes."));
    }

    @Test
    @DisplayName("fetchRemoteContent detects markdown by content even without .md extension")
    void fetchRemoteContentDetectsMarkdownByContent() {
        wireMock.stubFor(
                get(urlEqualTo("/notes"))
                        .willReturn(
                                aResponse().withStatus(200).withBody("# Changelog\n- Fixed bug")));

        var result = invokeFetchRemoteContent("http://localhost:" + wireMock.getPort() + "/notes");
        assertTrue(result.contains("<h1>Changelog</h1>"));
        assertTrue(result.contains("<li>Fixed bug</li>"));
    }

    // --- constructor (creates actual Swing component) ---

    @SuppressWarnings("NullAway")
    @Test
    @DisplayName("constructor with null URL and null description shows no-notes")
    void constructorNullUrlNullDescription() {
        var panel = new ReleaseNotesPanel(null, null);
        assertNotNull(panel);
        var text = panel.getText();
        assertTrue(text.contains("No release notes available"));
    }

    @SuppressWarnings("NullAway")
    @Test
    @DisplayName("constructor with null URL and plain text description")
    void constructorNullUrlPlainText() {
        var panel = new ReleaseNotesPanel(null, "Simple update notes");
        var text = panel.getText();
        assertTrue(text.contains("Simple update notes"));
    }

    @SuppressWarnings("NullAway")
    @Test
    @DisplayName("constructor with null URL and markdown description")
    void constructorNullUrlMarkdown() {
        var panel = new ReleaseNotesPanel(null, "# Release Notes\n- Bug fix");
        var text = panel.getText();
        assertTrue(text.contains("Release Notes"));
    }

    @SuppressWarnings("NullAway")
    @Test
    @DisplayName("constructor with null URL and HTML description")
    void constructorNullUrlHtml() {
        var panel = new ReleaseNotesPanel(null, "<html><body>HTML notes</body></html>");
        assertNotNull(panel);
    }

    private static void waitForContent(ReleaseNotesPanel panel, String expected)
            throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            if (panel.getText().contains(expected)) return;
            Thread.sleep(100);
        }
        assertTrue(panel.getText().contains(expected), "Expected content not found after 2s");
    }

    @Test
    @DisplayName("constructor with remote URL loads content asynchronously")
    void constructorWithUrl() throws InterruptedException {
        wireMock.stubFor(
                get(urlEqualTo("/release-notes.html"))
                        .willReturn(aResponse().withStatus(200).withBody("<h1>Remote Notes</h1>")));

        var url = "http://localhost:" + wireMock.getPort() + "/release-notes.html";
        var panel = new ReleaseNotesPanel(url, null);
        assertNotNull(panel);
        waitForContent(panel, "Remote Notes");
    }

    @SuppressWarnings("NullAway")
    @Test
    @DisplayName("constructor with remote markdown URL converts to HTML")
    void constructorWithMarkdownUrl() throws InterruptedException {
        wireMock.stubFor(
                get(urlEqualTo("/notes.md"))
                        .willReturn(aResponse().withStatus(200).withBody("# Changelog")));

        var url = "http://localhost:" + wireMock.getPort() + "/notes.md";
        var panel = new ReleaseNotesPanel(url, null);
        assertNotNull(panel);
        waitForContent(panel, "Changelog");
    }
}
