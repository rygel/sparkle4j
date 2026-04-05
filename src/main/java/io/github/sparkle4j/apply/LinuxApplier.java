package io.github.sparkle4j.apply;

import io.github.sparkle4j.UpdateItem;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

final class LinuxApplier {

    private static final Logger log = Logger.getLogger(LinuxApplier.class.getName());

    void apply(UpdateItem item, Path tempFile) {
        var fileNamePart = tempFile.getFileName();
        var fileName = fileNamePart != null ? fileNamePart.toString() : tempFile.toString();
        var dot = fileName.lastIndexOf('.');
        var ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";

        List<String> command =
                switch (ext) {
                    case "deb" -> List.of("pkexec", "dpkg", "-i", tempFile.toString());
                    case "rpm" -> List.of("pkexec", "rpm", "-U", tempFile.toString());
                    default -> null;
                };

        if (command != null) {
            try {
                log.info("Installing package via pkexec: " + command);
                int exitCode = new ProcessBuilder(command).inheritIO().start().waitFor();
                if (exitCode != 0) {
                    log.warning("Package manager exited with code " + exitCode);
                }
                System.exit(0);
            } catch (IOException | InterruptedException e) {
                log.severe("Package installation failed: " + e.getMessage());
            }
            return;
        }

        // Tier 2: AppImage or unknown format — open browser
        log.info("Unknown installer format (" + ext + ") — opening browser for " + item.url());
        try {
            Desktop.getDesktop().browse(URI.create(item.url()));
        } catch (IOException e) {
            log.warning("Could not open browser: " + e.getMessage());
        } catch (UnsupportedOperationException e) {
            log.warning("Desktop browsing not supported: " + e.getMessage());
        }
    }
}
