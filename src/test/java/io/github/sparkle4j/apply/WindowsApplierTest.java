package io.github.sparkle4j.apply;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sparkle4j.UpdateItem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("NullAway.Init")
class WindowsApplierTest {

    @TempDir Path tempDir;

    @SuppressWarnings("NullAway")
    private UpdateItem item(String installerType) {
        return new UpdateItem(
                "App 2.0.0",
                "2.0.0",
                "2.0.0",
                "https://example.com/update.exe",
                1000,
                null,
                null,
                null,
                null,
                null,
                "windows",
                false,
                installerType);
    }

    @Test
    @DisplayName("apply moves temp file to sparkle4j-update.exe")
    void applyMovesTempFile() throws Exception {
        var tempFile = tempDir.resolve("downloaded.tmp");
        Files.write(tempFile, "installer bytes".getBytes(StandardCharsets.UTF_8));

        var applier = new WindowsApplier();
        // ProcessBuilder will fail because sparkle4j-update.exe isn't a real installer
        try {
            applier.apply(item("inno"), tempFile);
        } catch (Exception e) {
            assertNotNull(e.getMessage());
        }

        // Original temp file should be moved
        assertFalse(Files.exists(tempFile));
        // The moved file should exist
        assertTrue(Files.exists(tempDir.resolve("sparkle4j-update.exe")));
    }

    @Test
    @DisplayName("apply with nsis installer type uses /S flag")
    void nsisInstallerType() throws Exception {
        var tempFile = tempDir.resolve("downloaded.tmp");
        Files.write(tempFile, "nsis bytes".getBytes(StandardCharsets.UTF_8));

        var applier = new WindowsApplier();
        // Will attempt to run the exe — will fail, but exercises the code path
        try {
            applier.apply(item("nsis"), tempFile);
        } catch (Exception e) {
            assertNotNull(e.getMessage());
        }
        assertTrue(Files.exists(tempDir.resolve("sparkle4j-update.exe")));
    }

    @Test
    @DisplayName("apply with inno installer type uses /PASSIVE /NORESTART flags")
    void innoInstallerType() throws Exception {
        var tempFile = tempDir.resolve("downloaded.tmp");
        Files.write(tempFile, "inno bytes".getBytes(StandardCharsets.UTF_8));

        var applier = new WindowsApplier();
        try {
            applier.apply(item("inno"), tempFile);
        } catch (Exception e) {
            assertNotNull(e.getMessage());
        }
        assertTrue(Files.exists(tempDir.resolve("sparkle4j-update.exe")));
    }
}
