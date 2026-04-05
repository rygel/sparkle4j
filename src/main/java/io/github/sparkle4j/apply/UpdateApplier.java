package io.github.sparkle4j.apply;

import io.github.sparkle4j.AppcastParser;
import io.github.sparkle4j.Sparkle4jConfig;
import io.github.sparkle4j.UpdateItem;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.logging.Logger;

/** Dispatches to the correct platform applier and resolves the current .app path on macOS. */
public final class UpdateApplier {

    private static final Logger log = Logger.getLogger(UpdateApplier.class.getName());

    private final Sparkle4jConfig config;

    public UpdateApplier(Sparkle4jConfig config) {
        this.config = config;
    }

    public void apply(UpdateItem item, Path tempFile) {
        switch (AppcastParser.currentOs()) {
            case "windows" -> new WindowsApplier().apply(item, tempFile);
            case "macos" -> new MacosApplier().apply(item, tempFile, resolveCurrentAppPath());
            default -> new LinuxApplier().apply(item, tempFile);
        }
    }

    private Path resolveCurrentAppPath() {
        if (config.macosAppPath() != null) return config.macosAppPath();

        var jvmExe = ProcessHandle.current().info().command().map(Path::of).orElse(null);

        var appBundle = jvmExe != null ? walkUpToAppBundle(jvmExe) : null;
        if (appBundle == null) {
            log.warning("Could not locate .app bundle — update may fail");
        }
        return appBundle != null ? appBundle : Path.of(".");
    }

    private static @Nullable Path walkUpToAppBundle(Path start) {
        var path = start;
        while (path != null) {
            var name = path.getFileName();
            if (name != null && name.toString().endsWith(".app")) return path;
            path = path.getParent();
        }
        return null;
    }
}
