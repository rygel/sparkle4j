package io.github.rygel.sparkle4j;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class VersionComparatorTest {

    @Test
    @DisplayName("equal versions return false")
    void equalVersions() {
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.0"));
    }

    @Test
    @DisplayName("higher major is newer")
    void higherMajor() {
        assertTrue(VersionComparator.isNewer("2.0.0", "1.0.0"));
    }

    @Test
    @DisplayName("higher minor is newer")
    void higherMinor() {
        assertTrue(VersionComparator.isNewer("1.1.0", "1.0.0"));
    }

    @Test
    @DisplayName("higher patch is newer")
    void higherPatch() {
        assertTrue(VersionComparator.isNewer("1.0.1", "1.0.0"));
    }

    @Test
    @DisplayName("lower version is not newer")
    void lowerVersion() {
        assertFalse(VersionComparator.isNewer("0.9.0", "1.0.0"));
    }

    @Test
    @DisplayName("numeric comparison, not lexicographic")
    void numericComparison() {
        assertTrue(VersionComparator.isNewer("0.10.0", "0.9.0"));
    }

    @Test
    @DisplayName("pre-release is older than release")
    void preReleaseOlderThanRelease() {
        assertFalse(VersionComparator.isNewer("1.0.0-alpha", "1.0.0"));
    }

    @Test
    @DisplayName("release is newer than pre-release")
    void releaseNewerThanPreRelease() {
        assertTrue(VersionComparator.isNewer("1.0.0", "1.0.0-alpha"));
    }

    @Test
    @DisplayName("beta is newer than alpha (lexicographic)")
    void betaNewerThanAlpha() {
        assertTrue(VersionComparator.isNewer("1.0.0-beta", "1.0.0-alpha"));
    }

    @Test
    @DisplayName("missing patch defaults to zero")
    void missingPatch() {
        assertFalse(VersionComparator.isNewer("1.0", "1.0.0"));
    }

    @Test
    @DisplayName("double-digit segments compare correctly")
    void doubleDigitSegments() {
        assertTrue(VersionComparator.isNewer("1.12.0", "1.9.0"));
    }

    @Test
    @DisplayName("handles single segment version")
    void singleSegment() {
        assertTrue(VersionComparator.isNewer("2", "1"));
    }

    @Test
    @DisplayName("compare method works symmetrically")
    void compareSymmetry() {
        assertTrue(VersionComparator.INSTANCE.compare("2.0.0", "1.0.0") > 0);
        assertTrue(VersionComparator.INSTANCE.compare("1.0.0", "2.0.0") < 0);
        assertEquals(0, VersionComparator.INSTANCE.compare("1.0.0", "1.0.0"));
    }

    @Test
    @DisplayName("sorts list by version descending")
    void sortDescending() {
        var versions = Arrays.asList("0.5.0", "1.0.0", "0.7.0", "0.7.0-beta", "0.10.0");
        versions.sort(VersionComparator.INSTANCE.reversed());
        assertEquals(List.of("1.0.0", "0.10.0", "0.7.0", "0.7.0-beta", "0.5.0"), versions);
    }

    @Test
    @DisplayName("rc.2 is newer than rc.1")
    void rcOrdering() {
        assertTrue(VersionComparator.isNewer("1.0.0-rc.2", "1.0.0-rc.1"));
    }

    @Test
    @DisplayName("non-numeric version segment defaults to 0")
    void nonNumericSegmentDefaultsToZero() {
        // "abc" can't be parsed as int, should default to 0
        assertEquals(0, VersionComparator.INSTANCE.compare("abc", "0.0.0"));
    }

    @Test
    @DisplayName("version with leading/trailing whitespace is trimmed")
    void versionWithWhitespace() {
        assertEquals(0, VersionComparator.INSTANCE.compare("  1.0.0  ", "1.0.0"));
    }
}
