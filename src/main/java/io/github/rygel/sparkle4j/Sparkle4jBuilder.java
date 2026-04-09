package io.github.rygel.sparkle4j;

import org.jspecify.annotations.Nullable;

import java.awt.Component;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Predicate;

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
    private boolean allowUnsignedUpdates;
    private int checkIntervalHours = 24;
    private @Nullable Component parentComponent;
    private String appName = Sparkle4j.resolveAppName();
    private @Nullable Predicate<UpdateItem> onUpdateFound;
    private @Nullable Path macosAppPath;
    private @Nullable Function<UpdateItem, Downloader> downloaderFactory;

    Sparkle4jBuilder() {}

    /**
     * Sets the URL of the appcast XML feed. <strong>Must be HTTPS.</strong>
     *
     * @param url absolute HTTPS URL, e.g. {@code https://example.com/appcast.xml}
     */
    public Sparkle4jBuilder appcastUrl(String url) {
        this.appcastUrl = url;
        return this;
    }

    /**
     * Sets the currently running version of the host application. Used to determine whether a newer
     * version exists. Must be a SemVer 2.0 string (e.g. {@code "1.2.3"}).
     *
     * @param version the current app version
     */
    public Sparkle4jBuilder currentVersion(String version) {
        this.currentVersion = version;
        return this;
    }

    /**
     * Sets the Base64-encoded Ed25519 public key used to verify downloaded installer signatures.
     * Accepts both raw 32-byte keys (Sparkle {@code generate_keys} format) and DER-encoded X.509
     * keys (Java format). <strong>Strongly recommended for production use.</strong>
     *
     * @param key Base64-encoded Ed25519 public key
     */
    public Sparkle4jBuilder publicKey(String key) {
        this.publicKey = key;
        return this;
    }

    /**
     * Explicitly opts out of Ed25519 signature verification. Use only in development or when
     * distributing unsigned installers intentionally. <strong>Not recommended for production
     * use</strong> — without signature verification, downloaded installers cannot be authenticated.
     *
     * <p>If neither {@link #publicKey(String)} nor this method is called, {@link #build()} will
     * throw {@link IllegalArgumentException}.
     */
    public Sparkle4jBuilder allowUnsignedUpdates() {
        this.allowUnsignedUpdates = true;
        return this;
    }

    /**
     * Sets the minimum number of hours between automatic background checks. Set to {@code 0} to
     * check on every launch. Default: {@code 24}.
     *
     * @param hours minimum hours between checks; {@code 0} disables throttling
     */
    public Sparkle4jBuilder checkIntervalHours(int hours) {
        this.checkIntervalHours = hours;
        return this;
    }

    /**
     * Sets the Swing component used as the parent for update dialogs. The dialog is centered over
     * this component's window. If not set, the dialog is centered on screen.
     *
     * @param component a Swing component from the host application's window hierarchy
     */
    public Sparkle4jBuilder parentComponent(Component component) {
        this.parentComponent = component;
        return this;
    }

    /**
     * Sets the application name shown in update dialogs (e.g. "Update MyApp"). Defaults to the
     * JAR's {@code Implementation-Title} manifest attribute, falling back to {@code "Application"}.
     *
     * @param name human-readable application name
     */
    public Sparkle4jBuilder appName(String name) {
        this.appName = name;
        return this;
    }

    /**
     * Registers a callback invoked on the EDT when an update is found, before the built-in dialog
     * is shown. Return {@code true} to allow the built-in dialog; return {@code false} to suppress
     * it and handle the update UI yourself.
     *
     * @param handler predicate that receives the available {@link UpdateItem}; return {@code false}
     *     to suppress the default dialog
     */
    public Sparkle4jBuilder onUpdateFound(Predicate<UpdateItem> handler) {
        this.onUpdateFound = handler;
        return this;
    }

    /**
     * Sets the path to the current {@code .app} bundle on macOS. Used by the macOS updater to
     * locate the helper script. If not set, the path is auto-resolved by walking up from the JVM
     * executable.
     *
     * @param path absolute path to the current {@code .app} bundle
     */
    public Sparkle4jBuilder macosAppPath(Path path) {
        this.macosAppPath = path;
        return this;
    }

    /**
     * Sets a custom downloader factory. The factory is called with the selected {@link UpdateItem}
     * and must return a {@link Downloader} that performs the actual file transfer. Use this to add
     * custom HTTP headers, proxy settings, or authentication. If not set, the built-in {@link
     * UpdateDownloader} is used.
     *
     * @param factory a function that creates a {@link Downloader} for a given update item
     */
    public Sparkle4jBuilder downloader(Function<UpdateItem, Downloader> factory) {
        this.downloaderFactory = factory;
        return this;
    }

    /**
     * Builds and returns a configured {@link Sparkle4jInstance}.
     *
     * @throws IllegalArgumentException if appcastUrl or currentVersion is blank, or if neither
     *     {@link #publicKey(String)} nor {@link #allowUnsignedUpdates()} has been called
     */
    public Sparkle4jInstance build() {
        if (appcastUrl == null || appcastUrl.isBlank()) {
            throw new IllegalArgumentException("appcastUrl is required");
        }
        if (currentVersion == null || currentVersion.isBlank()) {
            throw new IllegalArgumentException("currentVersion is required");
        }
        if (publicKey == null && !allowUnsignedUpdates) {
            throw new IllegalArgumentException(
                    "publicKey is required for signature verification. "
                            + "Call allowUnsignedUpdates() to explicitly opt out (not recommended"
                            + " for production).");
        }
        return Sparkle4j.createInstance(
                new Sparkle4jConfig(
                        appcastUrl,
                        currentVersion,
                        publicKey,
                        allowUnsignedUpdates,
                        checkIntervalHours,
                        parentComponent,
                        appName,
                        onUpdateFound,
                        macosAppPath,
                        downloaderFactory));
    }
}
