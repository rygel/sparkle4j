package io.github.sparkle4j;

import org.jspecify.annotations.Nullable;

import java.awt.Component;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Fluent builder for configuring a {@link Sparkle4jInstance}.
 *
 * <pre>{@code
 * Sparkle4j.builder()
 *     .appcastUrl("https://example.com/appcast.xml")
 *     .currentVersion("0.5.0")
 *     .publicKey("BASE64_KEY")
 *     .parentComponent(mainWindow)
 *     .build()
 *     .checkInBackground();
 * }</pre>
 */
public final class Sparkle4jBuilder {

    private String appcastUrl = "";
    private String currentVersion = "";
    private @Nullable String publicKey;
    private int checkIntervalHours = 24;
    private @Nullable Component parentComponent;
    private String appName = Sparkle4j.resolveAppName();
    private @Nullable Function<UpdateItem, Boolean> onUpdateFound;
    private @Nullable Path macosAppPath;

    Sparkle4jBuilder() {}

    public Sparkle4jBuilder appcastUrl(String url) {
        this.appcastUrl = url;
        return this;
    }

    public Sparkle4jBuilder currentVersion(String version) {
        this.currentVersion = version;
        return this;
    }

    public Sparkle4jBuilder publicKey(String key) {
        this.publicKey = key;
        return this;
    }

    public Sparkle4jBuilder checkIntervalHours(int hours) {
        this.checkIntervalHours = hours;
        return this;
    }

    public Sparkle4jBuilder parentComponent(Component component) {
        this.parentComponent = component;
        return this;
    }

    public Sparkle4jBuilder appName(String name) {
        this.appName = name;
        return this;
    }

    public Sparkle4jBuilder onUpdateFound(Function<UpdateItem, Boolean> handler) {
        this.onUpdateFound = handler;
        return this;
    }

    public Sparkle4jBuilder macosAppPath(Path path) {
        this.macosAppPath = path;
        return this;
    }

    /**
     * Builds and returns a configured {@link Sparkle4jInstance}.
     *
     * @throws IllegalArgumentException if appcastUrl or currentVersion is blank
     */
    public Sparkle4jInstance build() {
        if (appcastUrl == null || appcastUrl.isBlank()) {
            throw new IllegalArgumentException("appcastUrl is required");
        }
        if (currentVersion == null || currentVersion.isBlank()) {
            throw new IllegalArgumentException("currentVersion is required");
        }
        return Sparkle4j.createInstance(
                new Sparkle4jConfig(
                        appcastUrl,
                        currentVersion,
                        publicKey,
                        checkIntervalHours,
                        parentComponent,
                        appName,
                        onUpdateFound,
                        macosAppPath));
    }
}
