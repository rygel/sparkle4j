package io.github.sparkle4j.apply;

import io.github.sparkle4j.UpdateItem;

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

            var args =
                    switch (item.installerType().toLowerCase(Locale.ROOT)) {
                        case "nsis" -> List.of(installerPath.toString(), "/S");
                        default -> List.of(installerPath.toString(), "/PASSIVE", "/NORESTART");
                    };

            log.info("Launching installer: " + args);
            new ProcessBuilder(args).start();
        } catch (IOException e) {
            log.severe("Failed to launch installer: " + e.getMessage());
        }
    }
}
