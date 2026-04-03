package io.github.sparkle4j.ui

import java.util.logging.Logger
import javax.swing.JEditorPane
import javax.swing.SwingUtilities

/**
 * Renders release notes from a URL in a JEditorPane.
 *
 * - HTML URLs: rendered as-is via JEditorPane's text/html support.
 * - Markdown URLs (.md or content-sniffed): converted to HTML.
 *   Uses flexmark-java if on the classpath; otherwise falls back to a minimal built-in converter.
 * - No URL: shows "No release notes available."
 * - Network failure: shows "Could not load release notes." — never blocks the dialog.
 */
internal class ReleaseNotesPanel(
    private val releaseNotesUrl: String?,
    private val inlineDescription: String? = null,
) : JEditorPane() {

    private val log = Logger.getLogger(ReleaseNotesPanel::class.java.name)

    init {
        isEditable = false
        contentType = "text/html"
        putClientProperty(HONOR_DISPLAY_PROPERTIES, true)
        loadContentAsync()
    }

    private fun loadContentAsync() {
        if (releaseNotesUrl == null) {
            if (inlineDescription != null) {
                text = when {
                    looksLikeMarkdown(inlineDescription) -> markdownToHtml(inlineDescription)
                    inlineDescription.trimStart().startsWith("<") -> inlineDescription
                    else -> "<html><body><p>${inlineDescription.htmlEscape()}</p></body></html>"
                }
            } else {
                text = NO_NOTES_HTML
            }
            return
        }

        Thread {
            val html = try {
                val content = java.net.URI.create(releaseNotesUrl).toURL()
                    .openStream().bufferedReader().readText()
                if (releaseNotesUrl.endsWith(".md", ignoreCase = true) || looksLikeMarkdown(content)) {
                    markdownToHtml(content)
                } else {
                    content
                }
            } catch (e: Exception) {
                log.warning("Could not load release notes from $releaseNotesUrl: ${e.message}")
                LOAD_FAILED_HTML
            }
            SwingUtilities.invokeLater { text = html }
        }.also { it.isDaemon = true; it.name = "sparkle4j-release-notes"; it.start() }
    }

    private fun looksLikeMarkdown(content: String): Boolean {
        val first = content.trimStart()
        return first.startsWith("#") || first.startsWith("- ") || first.startsWith("* ")
    }

    private fun markdownToHtml(markdown: String): String {
        return try {
            flexmarkConvert(markdown)
        } catch (_: ClassNotFoundException) {
            minimalMarkdownToHtml(markdown)
        }
    }

    /** Reflective call to flexmark-java so it remains an optional runtime dependency. */
    private fun flexmarkConvert(markdown: String): String {
        val optionsClass = Class.forName("com.vladsch.flexmark.util.data.MutableDataSet")
        val options = optionsClass.getDeclaredConstructor().newInstance()

        val parserClass = Class.forName("com.vladsch.flexmark.parser.Parser")
        val parserBuilder = parserClass.getMethod("builder", optionsClass).invoke(null, options)
        val parser = parserBuilder.javaClass.getMethod("build").invoke(parserBuilder)
        val document = parser.javaClass.getMethod("parse", String::class.java).invoke(parser, markdown)

        val rendererClass = Class.forName("com.vladsch.flexmark.html.HtmlRenderer")
        val rendererBuilder = rendererClass.getMethod("builder", optionsClass).invoke(null, options)
        val renderer = rendererBuilder.javaClass.getMethod("build").invoke(rendererBuilder)
        val nodeClass = Class.forName("com.vladsch.flexmark.util.ast.Node")
        val html = renderer.javaClass.getMethod("render", nodeClass).invoke(renderer, document) as String

        return "<html><body style='font-family:sans-serif;padding:8px'>$html</body></html>"
    }

    /** CommonMark subset: headings, bold, lists, code blocks, paragraphs. */
    private fun minimalMarkdownToHtml(markdown: String): String {
        val sb = StringBuilder("<html><body style='font-family:sans-serif;padding:8px'>")
        val lines = markdown.lines()
        var inCode = false

        for (line in lines) {
            when {
                line.startsWith("```") -> {
                    inCode = !inCode
                    if (inCode) sb.append("<pre><code>") else sb.append("</code></pre>")
                }
                inCode -> sb.append(line.htmlEscape()).append('\n')
                line.startsWith("### ") -> sb.append("<h3>${line.drop(4).inlineFormat()}</h3>")
                line.startsWith("## ")  -> sb.append("<h2>${line.drop(3).inlineFormat()}</h2>")
                line.startsWith("# ")   -> sb.append("<h1>${line.drop(2).inlineFormat()}</h1>")
                line.startsWith("- ") || line.startsWith("* ") -> sb.append("<li>${line.drop(2).inlineFormat()}</li>")
                line.isBlank()          -> sb.append("<br>")
                else                    -> sb.append("<p>${line.inlineFormat()}</p>")
            }
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    private fun String.htmlEscape(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun String.inlineFormat(): String {
        var s = htmlEscape()
        // [text](url) — links
        s = Regex("""\[(.+?)\]\((.+?)\)""").replace(s) { "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>" }
        // **bold**
        s = Regex("""\*\*(.+?)\*\*""").replace(s) { "<b>${it.groupValues[1]}</b>" }
        // `code`
        s = Regex("""`(.+?)`""").replace(s) { "<code>${it.groupValues[1]}</code>" }
        return s
    }

    private companion object {
        const val NO_NOTES_HTML = "<html><body><p>No release notes available.</p></body></html>"
        const val LOAD_FAILED_HTML = "<html><body><p>Could not load release notes.</p></body></html>"
    }
}
