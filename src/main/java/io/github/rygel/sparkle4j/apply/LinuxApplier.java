package io.github.rygel.sparkle4j.apply;

import io.github.rygel.sparkle4j.UpdateItem;

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
                // command is always one of: ["pkexec","dpkg","-i",<path>] or
                // ["pkexec","rpm","-U",<path>]. The path is a locally generated temp file —
                // not derived from appcast XML. No shell is involved (ProcessBuilder list form).
                // Not a command injection risk. // NOSEMGREP
                int exitCode =
                        new ProcessBuilder(command).inheritIO().start().waitFor(); // NOSEMGREP
                if (exitCode != 0) {
                    log.warning("Package manager exited with code " + exitCode);
                }
            } catch (IOException | InterruptedException e) {
                log.severe("Package installation failed: " + e.getMessage());
            }
            return;
        }

        // Tier 2: AppImage or unknown format — open browser
        log.info("Unknown installer format (" + ext + ") — opening browser for " + item.url());
        var url = item.url();
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
