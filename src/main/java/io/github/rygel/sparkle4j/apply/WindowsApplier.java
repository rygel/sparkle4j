package io.github.rygel.sparkle4j.apply;

import io.github.rygel.sparkle4j.UpdateItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

final class WindowsApplier {

    private static final Logger log = Logger.getLogger(WindowsApplier.class.getName());

    void apply(UpdateItem item, Path tempFile) {
        try {
            var parent = tempFile.toAbsolutePath().getParent();
            if (parent == null) {
                log.severe("Cannot resolve parent directory for temp file: " + tempFile);
                return;
            }
            var installerPath = parent.resolve("sparkle4j-update.exe");
            Files.move(tempFile, installerPath, StandardCopyOption.REPLACE_EXISTING);

            // installerPath is always tempDir + "sparkle4j-update.exe" (hardcoded name, not
            // user-controlled). All args are either this fixed path or compile-time string
            // literals. No shell is involved — ProcessBuilder takes a list, not a command
            // string. Not a command injection risk. // NOSEMGREP
            var args =
                    switch (item.installerType().toLowerCase(Locale.ROOT)) {
                        case "nsis" -> List.of(installerPath.toString(), "/S");
                        default -> List.of(installerPath.toString(), "/PASSIVE", "/NORESTART");
                    };

            log.info("Launching installer: " + args);
            new ProcessBuilder(args).start(); // NOSEMGREP
        } catch (IOException e) {
            log.severe("Failed to launch installer: " + e.getMessage());
        }
    }
}
