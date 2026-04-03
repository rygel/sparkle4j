package io.github.sparkle4j

/**
 * SemVer 2.0 comparator.
 *
 * - Numeric comparison per segment (not lexicographic).
 * - Pre-release qualifiers (-alpha, -beta, -rc.1) sort below the release version.
 */
object VersionComparator : Comparator<String> {

    /** Returns true if [candidate] is strictly newer than [current]. */
    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    override fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)

        for (i in 0..2) {
            val diff = pa.numbers[i] - pb.numbers[i]
            if (diff != 0) return diff
        }

        return when {
            pa.preRelease == null && pb.preRelease == null -> 0
            pa.preRelease == null -> 1 // release > pre-release
            pb.preRelease == null -> -1 // pre-release < release
            else -> pa.preRelease.compareTo(pb.preRelease)
        }
    }

    private data class Parsed(val numbers: List<Int>, val preRelease: String?)

    private fun parse(version: String): Parsed {
        val parts = version.trim().split("-", limit = 2)
        val segments = parts[0].split(".")
        val numbers = (0..2).map { i -> segments.getOrNull(i)?.toIntOrNull() ?: 0 }
        val preRelease = if (parts.size > 1) parts[1] else null
        return Parsed(numbers, preRelease)
    }
}
