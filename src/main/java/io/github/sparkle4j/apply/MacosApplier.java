package io.github.sparkle4j.apply;

import io.github.sparkle4j.UpdateItem;

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
            log.severe("Helper script not found at " + helperScript + " — falling back to browser download");
            openBrowser(item.url());
            return;
        }

        try {
            helperScript.toFile().setExecutable(true);

            new ProcessBuilder(
                helperScript.toString(),
                currentAppPath.toString(),
                tempFile.toString(),
                String.valueOf(ProcessHandle.current().pid())
            ).start(); // fire and forget

            System.exit(0);
        } catch (IOException e) {
            log.severe("Failed to launch helper script: " + e.getMessage());
            openBrowser(item.url());
        }
    }

    private static void openBrowser(String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException e) {
            log.warning("Could not open browser: " + e.getMessage());
        } catch (UnsupportedOperationException e) {
            log.warning("Desktop browsing not supported: " + e.getMessage());
        }
    }
}
