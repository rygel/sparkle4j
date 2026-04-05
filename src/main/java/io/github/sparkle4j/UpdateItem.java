package io.github.sparkle4j;

import org.jspecify.annotations.Nullable;

/**
 * A single update entry from an appcast feed, scoped to the current platform.
 *
 * @param version Machine-readable version used for comparison (from {@code sparkle:version})
 * @param shortVersionString Human-readable version for display (from {@code
 *     sparkle:shortVersionString}), falls back to {@code version} when absent
 * @param edSignature Base64-encoded Ed25519 signature of the download file
 * @param releaseNotesUrl URL to an external release notes page (Markdown or HTML)
 * @param inlineDescription Inline release notes from {@code <description>} (fallback when
 *     releaseNotesUrl is absent)
 * @param minimumJavaVersion Minimum Java major version required (custom {@code
 *     sparkle:minimumJavaVersion} element)
 * @param isCriticalUpdate When true the "Skip This Version" button is hidden (set by {@code
 *     <sparkle:criticalUpdate/>})
 * @param installerType Windows installer type: "inno" (default) or "nsis"
 */
public record UpdateItem(
        String title,
        String version,
        String shortVersionString,
        String url,
        long length,
        @Nullable String edSignature,
        @Nullable String releaseNotesUrl,
        @Nullable String inlineDescription,
        @Nullable Integer minimumJavaVersion,
        @Nullable String pubDate,
        String os,
        boolean isCriticalUpdate,
        String installerType) {}
