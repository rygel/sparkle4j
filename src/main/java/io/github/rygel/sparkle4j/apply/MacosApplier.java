package io.github.rygel.sparkle4j.apply;

import io.github.rygel.sparkle4j.UpdateItem;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

final class MacosApplier {

    private static final Logger log = Logger.getLogger(MacosApplier.class.getName());

    void apply(UpdateItem item, Path tempFile, Path currentAppPath) {
        var helperScript = currentAppPath.resolve("Contents/MacOS/sparkle4j-updater");

        if (!Files.exists(helperScript)) {
            log.severe(
                    "Helper script not found at "
                            + helperScript
                            + " — falling back to browser download");
            openBrowser(item.url());
            return;
        }

        try {
            helperScript.toFile().setExecutable(true);

            // helperScript is resolved from the current .app bundle path (process introspection
            // or explicit config), not from appcast XML. tempFile is a locally generated path
            // inside the system temp directory. No shell is used — ProcessBuilder takes a list.
            // Not a command injection risk. // NOSEMGREP
            new ProcessBuilder(
                            helperScript.toString(),
                            currentAppPath.toString(),
                            tempFile.toString(),
                            String.valueOf(ProcessHandle.current().pid()))
                    .start(); // fire and forget — application should exit after this returns
        } catch (IOException e) {
            log.severe("Failed to launch helper script: " + e.getMessage());
            openBrowser(item.url());
        }
    }

    private static void openBrowser(String url) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            log.severe("Refusing to open browser for non-HTTP URL from appcast: " + url);
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException e) {
            log.warning("Could not open browser: " + e.getMessage());
        } catch (UnsupportedOperationException e) {
            log.warning("Desktop browsing not supported: " + e.getMessage());
        }
    }
}
