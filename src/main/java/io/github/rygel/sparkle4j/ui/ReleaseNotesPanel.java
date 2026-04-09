package io.github.rygel.sparkle4j.ui;

import org.jspecify.annotations.Nullable;

import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import java.awt.Color;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Renders release notes from a URL in a JEditorPane.
 *
 * <ul>
 *   <li>HTML URLs: rendered as-is via JEditorPane's text/html support.
 *   <li>Markdown URLs (.md or content-sniffed): converted to HTML via a built-in CommonMark subset
 *       converter supporting headings, bold, code, links, lists, and code blocks.
 *   <li>No URL: shows "No release notes available."
 *   <li>Network failure: shows "Could not load release notes." — never blocks the dialog.
 * </ul>
 */
final class ReleaseNotesPanel extends JEditorPane {

    private static final long serialVersionUID = 1L;
    private static final Logger log = Logger.getLogger(ReleaseNotesPanel.class.getName());
    private static final String NO_NOTES_MSG = "No release notes available.";
    private static final String LOAD_FAILED_MSG = "Could not load release notes.";

    private static final Pattern LINK_PATTERN = Pattern.compile("\\[(.+?)]\\((.+?)\\)");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_STAR_PATTERN =
            Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
    private static final Pattern ITALIC_UNDER_PATTERN =
            Pattern.compile("(?<!_)_(?!_)([^_]+?)(?<!_)_(?!_)");
    private static final Pattern STRIKE_PATTERN = Pattern.compile("~~(.+?)~~");
    private static final Pattern CODE_PATTERN = Pattern.compile("`(.+?)`");
    private static final Pattern HR_PATTERN = Pattern.compile("^(---|\\*\\*\\*|___)$");
    private static final Pattern OL_PATTERN = Pattern.compile("^\\d+\\.\\s(.*)$");

    ReleaseNotesPanel(@Nullable String releaseNotesUrl, @Nullable String inlineDescription) {
        setEditable(false);
        setContentType("text/html");
        putClientProperty(HONOR_DISPLAY_PROPERTIES, true);
        loadContentAsync(releaseNotesUrl, inlineDescription);
    }

    private void loadContentAsync(
            @Nullable String releaseNotesUrl, @Nullable String inlineDescription) {
        if (releaseNotesUrl == null) {
            setText(renderInlineContent(inlineDescription));
            return;
        }

        var thread =
                new Thread(
                        () -> {
                            var html = fetchRemoteContent(releaseNotesUrl);
                            SwingUtilities.invokeLater(() -> setText(html));
                        });
        thread.setDaemon(true);
        thread.setName("sparkle4j-release-notes");
        thread.start();
    }

    private static String renderInlineContent(@Nullable String inlineDescription) {
        if (inlineDescription == null) {
            return wrapHtml(NO_NOTES_MSG);
        }
        if (looksLikeMarkdown(inlineDescription)) {
            return markdownToHtml(inlineDescription);
        }
        if (inlineDescription.trim().startsWith("<")) {
            return inlineDescription;
        }
        return wrapHtml(htmlEscape(inlineDescription));
    }

    private static String fetchRemoteContent(String releaseNotesUrl) {
        if (!releaseNotesUrl.startsWith("https://") && !releaseNotesUrl.startsWith("http://")) {
            log.warning("Rejecting release notes URL with non-HTTP(S) scheme: " + releaseNotesUrl);
            return wrapHtml(LOAD_FAILED_MSG);
        }
        try {
            var conn = (HttpURLConnection) URI.create(releaseNotesUrl).toURL().openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "text/html,text/plain,text/markdown,*/*");
            String content;
            try (var stream = conn.getInputStream()) {
                content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (releaseNotesUrl.toLowerCase(Locale.ROOT).endsWith(".md")
                    || looksLikeMarkdown(content)) {
                return markdownToHtml(content);
            }
            return content;
        } catch (IOException e) {
            log.warning(
                    "Could not load release notes from " + releaseNotesUrl + ": " + e.getMessage());
            return wrapHtml(LOAD_FAILED_MSG);
        } catch (IllegalArgumentException e) {
            log.warning("Invalid release notes URL " + releaseNotesUrl + ": " + e.getMessage());
            return wrapHtml(LOAD_FAILED_MSG);
        }
    }

    private static String wrapHtml(String bodyContent) {
        return "<html><body style='" + bodyStyle() + "'><p>" + bodyContent + "</p></body></html>";
    }

    private static boolean looksLikeMarkdown(String content) {
        var first = content.stripLeading();
        return first.startsWith("#")
                || first.startsWith("- ")
                || first.startsWith("* ")
                || first.startsWith("> ")
                || OL_PATTERN.matcher(first.split("\n", 2)[0]).matches();
    }

    private static String markdownToHtml(String markdown) {
        return minimalMarkdownToHtml(markdown);
    }

    private record MdState(boolean inCode, boolean inUl, boolean inOl) {
        static final MdState NONE = new MdState(false, false, false);
    }

    /**
     * CommonMark subset: headings, bold, italic, strikethrough, lists, blockquotes, code blocks,
     * links, paragraphs.
     */
    private static String minimalMarkdownToHtml(String markdown) {
        var sb = new StringBuilder("<html><body style='" + bodyStyle() + "'>");
        var state = MdState.NONE;
        for (var line : markdown.split("\n", -1)) {
            state = processLine(line, sb, state);
        }
        closeList(sb, state.inUl(), state.inOl());
        sb.append("</body></html>");
        return sb.toString();
    }

    private static MdState processLine(String line, StringBuilder sb, MdState s) {
        if (line.startsWith("```")) {
            var nowInCode = !s.inCode();
            if (nowInCode) closeList(sb, s.inUl(), s.inOl());
            sb.append(nowInCode ? "<pre><code>" : "</code></pre>");
            return new MdState(nowInCode, nowInCode && s.inUl(), nowInCode && s.inOl());
        }
        if (s.inCode()) {
            sb.append(htmlEscape(line)).append('\n');
            return s;
        }
        return processBlockLine(line, sb, s);
    }

    private static MdState processBlockLine(String line, StringBuilder sb, MdState s) {
        if (line.startsWith("### ")) {
            return closeListAppend(sb, s, "<h3>" + inlineFormat(line.substring(4)) + "</h3>");
        } else if (line.startsWith("## ")) {
            return closeListAppend(sb, s, "<h2>" + inlineFormat(line.substring(3)) + "</h2>");
        } else if (line.startsWith("# ")) {
            return closeListAppend(sb, s, "<h1>" + inlineFormat(line.substring(2)) + "</h1>");
        } else if (line.startsWith("- ") || line.startsWith("* ")) {
            if (s.inOl()) sb.append("</ol>");
            if (!s.inUl()) sb.append("<ul>");
            sb.append("<li>").append(inlineFormat(line.substring(2))).append("</li>");
            return new MdState(false, true, false);
        } else {
            return processOtherLine(line, sb, s);
        }
    }

    private static MdState processOtherLine(String line, StringBuilder sb, MdState s) {
        var olMatcher = OL_PATTERN.matcher(line);
        if (olMatcher.matches()) {
            if (s.inUl()) sb.append("</ul>");
            if (!s.inOl()) sb.append("<ol>");
            sb.append("<li>").append(inlineFormat(olMatcher.group(1))).append("</li>");
            return new MdState(false, false, true);
        } else if (line.startsWith("> ")) {
            return closeListAppend(
                    sb, s, "<blockquote>" + inlineFormat(line.substring(2)) + "</blockquote>");
        } else if (HR_PATTERN.matcher(line).matches()) {
            return closeListAppend(sb, s, "<hr>");
        } else if (line.isBlank()) {
            return closeListAppend(sb, s, "<br>");
        } else {
            return closeListAppend(sb, s, "<p>" + inlineFormat(line) + "</p>");
        }
    }

    private static MdState closeListAppend(StringBuilder sb, MdState s, String html) {
        closeList(sb, s.inUl(), s.inOl());
        sb.append(html);
        return MdState.NONE;
    }

    private static void closeList(StringBuilder sb, boolean inUl, boolean inOl) {
        if (inUl) sb.append("</ul>");
        if (inOl) sb.append("</ol>");
    }

    private static String bodyStyle() {
        Color fg = UIManager.getColor("EditorPane.foreground");
        Color bg = UIManager.getColor("EditorPane.background");
        String fgHex = fg != null ? colorToHex(fg) : "#000000";
        String bgHex = bg != null ? colorToHex(bg) : "#FFFFFF";
        return String.format(
                "font-family:sans-serif;padding:8px;color:%s;background-color:%s", fgHex, bgHex);
    }

    private static String colorToHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String inlineFormat(String s) {
        var escaped = htmlEscape(s);
        escaped = LINK_PATTERN.matcher(escaped).replaceAll("<a href=\"$2\">$1</a>");
        escaped = BOLD_PATTERN.matcher(escaped).replaceAll("<b>$1</b>");
        escaped = ITALIC_STAR_PATTERN.matcher(escaped).replaceAll("<i>$1</i>");
        escaped = ITALIC_UNDER_PATTERN.matcher(escaped).replaceAll("<i>$1</i>");
        escaped = STRIKE_PATTERN.matcher(escaped).replaceAll("<s>$1</s>");
        escaped = CODE_PATTERN.matcher(escaped).replaceAll("<code>$1</code>");
        return escaped;
    }
}
