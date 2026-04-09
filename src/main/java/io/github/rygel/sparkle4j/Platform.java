package io.github.rygel.sparkle4j;

import java.util.Locale;

/** Utility for detecting the current operating system. */
public final class Platform {

    private Platform() {}

    /**
     * Returns a normalized OS identifier: {@code "windows"}, {@code "macos"}, or {@code "linux"}.
     */
    public static String currentOs() {
        var name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) return "windows";
        if (name.contains("mac")) return "macos";
        return "linux";
    }
}
