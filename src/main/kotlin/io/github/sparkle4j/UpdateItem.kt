package io.github.sparkle4j

/**
 * A single update entry from an appcast feed, scoped to the current platform.
 *
 * Field mapping to Sparkle appcast elements:
 * - [version] ← `sparkle:version` (machine-readable build number)
 * - [shortVersionString] ← `sparkle:shortVersionString` (human-readable, e.g. "3.2.1")
 * - [isCriticalUpdate] ← `<sparkle:criticalUpdate/>` — when true, the user cannot skip this version
 */
data class UpdateItem(
    val title: String,
    /** Machine-readable version used for comparison (e.g. "12345" or "0.7.0"). */
    val version: String,
    /**
     * Human-readable version string for display (e.g. "3.2.1").
     * Falls back to [version] when the appcast does not include `sparkle:shortVersionString`.
     */
    val shortVersionString: String,
    val url: String,
    val length: Long,
    val edSignature: String?,
    /** URL to an external release notes page (Markdown or HTML). */
    val releaseNotesUrl: String?,
    /** Inline release notes from the `<description>` element (fallback when [releaseNotesUrl] is absent). */
    val inlineDescription: String? = null,
    /**
     * Minimum Java major version required to run this update.
     * Read from the custom `sparkle:minimumJavaVersion` element (not `sparkle:minimumSystemVersion`,
     * which is a macOS version string and must remain untouched for Sparkle feed sharing).
     */
    val minimumJavaVersion: Int?,
    val pubDate: String?,
    val os: String,
    /** When true the "Skip This Version" button is hidden. Set by `<sparkle:criticalUpdate/>`. */
    val isCriticalUpdate: Boolean = false,
    /** Installer type on Windows: "inno" (default) or "nsis". Custom sparkle4j attribute. */
    val installerType: String = "inno",
)
