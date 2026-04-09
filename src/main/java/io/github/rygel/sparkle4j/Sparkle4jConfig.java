package io.github.rygel.sparkle4j;

import org.jspecify.annotations.Nullable;

import java.awt.Component;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Immutable configuration for a sparkle4j instance.
 *
 * @param appcastUrl Appcast XML feed URL. Must be HTTPS.
 * @param currentVersion Running version of the host app. Semver string.
 * @param publicKey Base64-encoded Ed25519 public key. Null only when allowUnsignedUpdates is true.
 * @param allowUnsignedUpdates When true, signature verification is skipped. Must be explicitly
 *     opted into via {@link Sparkle4jBuilder#allowUnsignedUpdates()}.
 * @param checkIntervalHours Minimum hours between checks. 0 = check every launch. Default: 24.
 * @param parentComponent Swing parent for dialogs. Null = dialog centered on screen.
 * @param appName App name shown in dialogs.
 * @param onUpdateFound Called on the EDT when an update is found, before the built-in dialog.
 *     Return false to suppress the built-in dialog and handle UI yourself.
 * @param macosAppPath macOS-only: path to the current .app bundle. Auto-resolved if null.
 */
public record Sparkle4jConfig(
        String appcastUrl,
        String currentVersion,
        @Nullable String publicKey,
        boolean allowUnsignedUpdates,
        int checkIntervalHours,
        @Nullable Component parentComponent,
        String appName,
        @Nullable Predicate<UpdateItem> onUpdateFound,
        @Nullable Path macosAppPath) {}
