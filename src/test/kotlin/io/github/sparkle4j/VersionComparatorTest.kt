package io.github.sparkle4j

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionComparatorTest {

    @Test fun `higher major is newer`() {
        assertTrue(VersionComparator.isNewer("2.0.0", "1.9.9"))
    }

    @Test fun `higher minor is newer`() {
        assertTrue(VersionComparator.isNewer("0.7.0", "0.6.0"))
    }

    @Test fun `higher patch is newer`() {
        assertTrue(VersionComparator.isNewer("1.0.1", "1.0.0"))
    }

    @Test fun `higher minor beats lower major`() {
        assertFalse(VersionComparator.isNewer("0.10.0", "1.0.0"))
    }

    @Test fun `double-digit minor segments`() {
        assertTrue(VersionComparator.isNewer("0.10.0", "0.9.0"))
    }

    @Test fun `same version is not newer`() {
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.0"))
    }

    @Test fun `older is not newer`() {
        assertFalse(VersionComparator.isNewer("0.6.0", "0.7.0"))
    }

    @Test fun `pre-release sorts below release`() {
        assertFalse(VersionComparator.isNewer("1.0.0-alpha", "1.0.0"))
        assertTrue(VersionComparator.isNewer("1.0.0", "1.0.0-alpha"))
    }

    @Test fun `beta sorts below release`() {
        assertFalse(VersionComparator.isNewer("1.0.0-beta", "1.0.0"))
    }

    @Test fun `rc sorts below release`() {
        assertFalse(VersionComparator.isNewer("1.0.0-rc.1", "1.0.0"))
    }

    @Test fun `pre-release newer than older release`() {
        assertTrue(VersionComparator.isNewer("1.1.0-alpha", "1.0.0"))
    }

    @Test fun `two pre-releases compared lexicographically`() {
        // alpha < beta alphabetically
        assertTrue(VersionComparator.isNewer("1.0.0-beta", "1.0.0-alpha"))
    }

    @Test fun `missing patch segment treated as 0`() {
        assertTrue(VersionComparator.isNewer("1.1", "1.0.0"))
        assertFalse(VersionComparator.isNewer("1.0", "1.0.0"))
    }

    @Test fun `compare returns correct sign`() {
        assertEquals(1, VersionComparator.compare("1.0.0", "0.9.0"))
        assertEquals(-1, VersionComparator.compare("0.9.0", "1.0.0"))
        assertEquals(0, VersionComparator.compare("1.0.0", "1.0.0"))
    }

    @Test fun `sort list of versions descending`() {
        val versions = listOf("0.9.0", "0.10.0", "1.0.0-rc.1", "1.0.0", "0.1.0")
        val sorted = versions.sortedWith(compareByDescending(VersionComparator) { it })
        assertEquals(listOf("1.0.0", "1.0.0-rc.1", "0.10.0", "0.9.0", "0.1.0"), sorted)
    }
}
