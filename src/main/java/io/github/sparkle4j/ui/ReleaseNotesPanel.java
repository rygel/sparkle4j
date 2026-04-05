package io.github.sparkle4j.ui;

import org.jspecify.annotations.Nullable;

import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import java.awt.Color;
import java.io.IOException;
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
 *   <li>Markdown URLs (.md or content-sniffed): converted to HTML. Uses flexmark-java if on the
 *       classpath; otherwise falls back to a minimal built-in converter.
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
    private static final Pattern CODE_PATTERN = Pattern.compile("`(.+?)`");

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
        try {
            var content =
                    new String(
                            URI.create(releaseNotesUrl).toURL().openStream().readAllBytes(),
                            StandardCharsets.UTF_8);
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
        return first.startsWith("#") || first.startsWith("- ") || first.startsWith("* ");
    }

    private static String markdownToHtml(String markdown) {
        try {
            return flexmarkConvert(markdown);
        } catch (ReflectiveOperationException e) {
            return minimalMarkdownToHtml(markdown);
        }
    }

    /** Reflective call to flexmark-java so it remains an optional runtime dependency. */
    private static String flexmarkConvert(String markdown) throws ReflectiveOperationException {
        var optionsClass = Class.forName("com.vladsch.flexmark.util.data.MutableDataSet");
        var options = optionsClass.getDeclaredConstructor().newInstance();

        var parserClass = Class.forName("com.vladsch.flexmark.parser.Parser");
        var parserBuilder = parserClass.getMethod("builder", optionsClass).invoke(null, options);
        var parser = parserBuilder.getClass().getMethod("build").invoke(parserBuilder);
        var document = parser.getClass().getMethod("parse", String.class).invoke(parser, markdown);

        var rendererClass = Class.forName("com.vladsch.flexmark.html.HtmlRenderer");
        var rendererBuilder =
                rendererClass.getMethod("builder", optionsClass).invoke(null, options);
        var renderer = rendererBuilder.getClass().getMethod("build").invoke(rendererBuilder);
        var nodeClass = Class.forName("com.vladsch.flexmark.util.ast.Node");
        var html =
                (String)
                        renderer.getClass()
                                .getMethod("render", nodeClass)
                                .invoke(renderer, document);

        return "<html><body style='" + bodyStyle() + "'>" + html + "</body></html>";
    }

    /** CommonMark subset: headings, bold, lists, code blocks, links, paragraphs. */
    private static String minimalMarkdownToHtml(String markdown) {
        var sb = new StringBuilder("<html><body style='" + bodyStyle() + "'>");
        var lines = markdown.split("\n", -1);
        boolean inCode = false;

        for (var line : lines) {
            if (line.startsWith("```")) {
                inCode = !inCode;
                sb.append(inCode ? "<pre><code>" : "</code></pre>");
            } else if (inCode) {
                sb.append(htmlEscape(line)).append('\n');
            } else if (line.startsWith("### ")) {
                sb.append("<h3>").append(inlineFormat(line.substring(4))).append("</h3>");
            } else if (line.startsWith("## ")) {
                sb.append("<h2>").append(inlineFormat(line.substring(3))).append("</h2>");
            } else if (line.startsWith("# ")) {
                sb.append("<h1>").append(inlineFormat(line.substring(2))).append("</h1>");
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                sb.append("<li>").append(inlineFormat(line.substring(2))).append("</li>");
            } else if (line.isBlank()) {
                sb.append("<br>");
            } else {
                sb.append("<p>").append(inlineFormat(line)).append("</p>");
            }
        }

        sb.append("</body></html>");
        return sb.toString();
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
        escaped = CODE_PATTERN.matcher(escaped).replaceAll("<code>$1</code>");
        return escaped;
    }
}
