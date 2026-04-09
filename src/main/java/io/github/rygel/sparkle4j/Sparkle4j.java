package io.github.rygel.sparkle4j;

/**
 * Entry point for the sparkle4j update library.
 *
 * <pre>{@code
 * Sparkle4j.builder()
 *     .appcastUrl("https://example.com/appcast.xml")
 *     .currentVersion("0.5.0")
 *     .parentComponent(mainWindow)
 *     .build()
 *     .checkInBackground();
 * }</pre>
 */
public final class Sparkle4j {

    private Sparkle4j() {}

    /**
     * Creates a new builder for configuring a sparkle4j instance.
     *
     * @return a new {@link Sparkle4jBuilder}
     */
    public static Sparkle4jBuilder builder() {
        return new Sparkle4jBuilder();
    }

    /** Package-private factory used by the builder. */
    static Sparkle4jInstance createInstance(Sparkle4jConfig config) {
        return new Sparkle4jInstanceImpl(config);
    }

    static String resolveAppName() {
        var pkg = Sparkle4jConfig.class.getPackage();
        var title = pkg != null ? pkg.getImplementationTitle() : null;
        return title != null ? title : "Application";
    }
}
