package io.github.sparkle4j

import java.time.Instant
import java.util.prefs.Preferences

/**
 * Persists skipped versions and last-check timestamp using the JDK Preferences API.
 * Node path: /io/github/sparkle4j/<sanitized-app-name>
 */
internal class UpdatePreferences(appName: String) {

    private val prefs: Preferences = Preferences.userRoot()
        .node("/io/github/sparkle4j/${appName.lowercase().replace(Regex("[^a-z0-9]"), "-")}")

    var lastCheckTimestamp: Instant?
        get() {
            val epoch = prefs.getLong(KEY_LAST_CHECK, -1L)
            return if (epoch >= 0) Instant.ofEpochSecond(epoch) else null
        }
        set(value) {
            prefs.putLong(KEY_LAST_CHECK, value?.epochSecond ?: -1L)
        }

    fun isSkipped(version: String): Boolean = skippedVersions().contains(version)

    fun skipVersion(version: String) {
        val versions = skippedVersions().toMutableSet()
        versions.add(version)
        prefs.put(KEY_SKIPPED_VERSIONS, versions.joinToString(","))
    }

    private fun skippedVersions(): Set<String> = prefs.get(KEY_SKIPPED_VERSIONS, "")
        .split(",")
        .filter { it.isNotBlank() }
        .toSet()

    private companion object {
        const val KEY_LAST_CHECK = "lastCheckTimestamp"
        const val KEY_SKIPPED_VERSIONS = "skippedVersions"
    }
}
