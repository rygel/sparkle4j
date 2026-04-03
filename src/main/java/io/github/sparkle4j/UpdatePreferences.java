package io.github.sparkle4j;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * Persists skipped versions and last-check timestamp using the JDK Preferences API.
 * Node path: /io/github/sparkle4j/&lt;sanitized-app-name&gt;
 */
final class UpdatePreferences {

    private static final String KEY_LAST_CHECK = "lastCheckTimestamp";
    private static final String KEY_SKIPPED_VERSIONS = "skippedVersions";

    private final Preferences prefs;

    UpdatePreferences(String appName) {
        var sanitized = appName.toLowerCase().replaceAll("[^a-z0-9]", "-");
        this.prefs = Preferences.userRoot().node("/io/github/sparkle4j/" + sanitized);
    }

    Instant getLastCheckTimestamp() {
        long epoch = prefs.getLong(KEY_LAST_CHECK, -1L);
        return epoch >= 0 ? Instant.ofEpochSecond(epoch) : null;
    }

    void setLastCheckTimestamp(Instant value) {
        prefs.putLong(KEY_LAST_CHECK, value != null ? value.getEpochSecond() : -1L);
    }

    boolean isSkipped(String version) {
        return skippedVersions().contains(version);
    }

    void skipVersion(String version) {
        var versions = new LinkedHashSet<>(skippedVersions());
        versions.add(version);
        prefs.put(KEY_SKIPPED_VERSIONS, String.join(",", versions));
    }

    private Set<String> skippedVersions() {
        var raw = prefs.get(KEY_SKIPPED_VERSIONS, "");
        return Arrays.stream(raw.split(","))
            .filter(s -> !s.isBlank())
            .collect(Collectors.toSet());
    }
}
