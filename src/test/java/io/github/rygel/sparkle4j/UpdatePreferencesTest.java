package io.github.rygel.sparkle4j;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

class UpdatePreferencesTest {

    private static final String APP_NAME = "sparkle4j-test-prefs-" + System.nanoTime();

    private UpdatePreferences prefs;

    @BeforeEach
    void setUp() {
        prefs = new UpdatePreferences(APP_NAME);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        var sanitized = APP_NAME.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "-");
        var node = Preferences.userRoot().node("/io/github/sparkle4j/" + sanitized);
        node.removeNode();
    }

    @Test
    @DisplayName("getLastCheckTimestamp returns null when unset")
    void lastCheckTimestampNullWhenUnset() {
        assertNull(prefs.getLastCheckTimestamp());
    }

    @Test
    @DisplayName("setLastCheckTimestamp persists and reads back")
    void lastCheckTimestampRoundTrip() {
        var now = Instant.now();
        prefs.setLastCheckTimestamp(now);

        var retrieved = prefs.getLastCheckTimestamp();
        assertNotNull(retrieved);
        assertEquals(now.getEpochSecond(), retrieved.getEpochSecond());
    }

    @Test
    @DisplayName("isSkipped returns false for unknown version")
    void isSkippedFalseForUnknown() {
        assertFalse(prefs.isSkipped("1.0.0"));
    }

    @Test
    @DisplayName("skipVersion persists and isSkipped returns true")
    void skipVersionMakesItSkipped() {
        prefs.skipVersion("2.0.0");
        assertTrue(prefs.isSkipped("2.0.0"));
    }

    @Test
    @DisplayName("multiple skipped versions accumulate")
    void multipleSkippedVersions() {
        prefs.skipVersion("1.0.0");
        prefs.skipVersion("2.0.0");
        prefs.skipVersion("3.0.0");

        assertTrue(prefs.isSkipped("1.0.0"));
        assertTrue(prefs.isSkipped("2.0.0"));
        assertTrue(prefs.isSkipped("3.0.0"));
        assertFalse(prefs.isSkipped("4.0.0"));
    }

    @Test
    @DisplayName("skipping same version twice does not duplicate")
    void skipSameVersionTwice() {
        prefs.skipVersion("1.0.0");
        prefs.skipVersion("1.0.0");
        assertTrue(prefs.isSkipped("1.0.0"));
    }

    @Test
    @DisplayName("preferences survive re-instantiation")
    void preferencesPersistedAcrossInstances() {
        prefs.skipVersion("5.0.0");
        prefs.setLastCheckTimestamp(Instant.ofEpochSecond(1000));

        var prefs2 = new UpdatePreferences(APP_NAME);
        assertTrue(prefs2.isSkipped("5.0.0"));
        assertNotNull(prefs2.getLastCheckTimestamp());
        assertEquals(1000, prefs2.getLastCheckTimestamp().getEpochSecond());
    }
}
