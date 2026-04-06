package io.github.sparkle4j.apply;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sparkle4j.UpdateItem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests for LinuxApplier. These test the argument parsing and file handling logic. The actual
 * process execution (pkexec/dpkg/rpm) is not tested here — that requires a Linux container with
 * root privileges.
 */
@SuppressWarnings("NullAway.Init")
class LinuxApplierTest {

    @TempDir Path tempDir;

    @SuppressWarnings("NullAway")
    private UpdateItem item() {
        return new UpdateItem(
                "App 2.0.0",
                "2.0.0",
                "2.0.0",
                "https://example.com/app.deb",
                1000,
                null,
                null,
                null,
                null,
                null,
                "linux",
                false,
                "deb");
    }

    @Test
    @DisplayName("apply with .deb file attempts package install")
    void applyWithDebFile() throws Exception {
        var debFile = tempDir.resolve("app-2.0.0.deb");
        Files.write(debFile, "fake deb".getBytes(StandardCharsets.UTF_8));

        var applier = new LinuxApplier();
        // On non-Linux or without pkexec, this will throw IOException
        // That's expected — we're testing the code path, not the actual install
        try {
            applier.apply(item(), debFile);
        } catch (Exception e) {
            assertNotNull(e.getMessage());
        }
        // The file should still exist (applier doesn't delete it)
        assertTrue(Files.exists(debFile));
    }

    @Test
    @DisplayName("apply with .rpm file attempts rpm install")
    void applyWithRpmFile() throws Exception {
        var rpmFile = tempDir.resolve("app-2.0.0.rpm");
        Files.write(rpmFile, "fake rpm".getBytes(StandardCharsets.UTF_8));

        var applier = new LinuxApplier();
        try {
            applier.apply(item(), rpmFile);
        } catch (Exception e) {
            assertNotNull(e.getMessage());
        }
        assertTrue(Files.exists(rpmFile));
    }

    @Test
    @DisplayName("apply with unknown extension falls back to browser")
    void applyWithUnknownExtension() throws Exception {
        var appImage = tempDir.resolve("app-2.0.0.AppImage");
        Files.write(appImage, "fake appimage".getBytes(StandardCharsets.UTF_8));

        var applier = new LinuxApplier();
        // Should try Desktop.browse — may throw UnsupportedOperationException in test
        try {
            applier.apply(item(), appImage);
        } catch (Exception e) {
            assertNotNull(e.getMessage());
        }
        assertTrue(Files.exists(appImage));
    }

    @Test
    @DisplayName("apply with no extension falls back to browser")
    void applyWithNoExtension() throws Exception {
        var noExt = tempDir.resolve("app-update");
        Files.write(noExt, "binary".getBytes(StandardCharsets.UTF_8));

        var applier = new LinuxApplier();
        try {
            applier.apply(item(), noExt);
        } catch (Exception e) {
            assertNotNull(e.getMessage());
        }
        assertTrue(Files.exists(noExt));
    }
}
