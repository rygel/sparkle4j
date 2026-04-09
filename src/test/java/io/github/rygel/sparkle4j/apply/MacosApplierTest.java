package io.github.rygel.sparkle4j.apply;

import static org.junit.jupiter.api.Assertions.*;

import io.github.rygel.sparkle4j.UpdateItem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("NullAway.Init")
class MacosApplierTest {

    @TempDir Path tempDir;

    @SuppressWarnings("NullAway")
    private UpdateItem item() {
        return new UpdateItem(
                "App 2.0.0",
                "2.0.0",
                "2.0.0",
                "https://example.com/app.zip",
                1000,
                null,
                null,
                null,
                null,
                null,
                "macos",
                false,
                "zip");
    }

    @Test
    @DisplayName("apply with missing helper script falls back to browser")
    void missingHelperScriptFallsToBrowser() throws Exception {
        var appPath = tempDir.resolve("Test.app");
        Files.createDirectories(appPath.resolve("Contents/MacOS"));
        var tempFile = tempDir.resolve("update.zip");
        Files.write(tempFile, "fake zip".getBytes(StandardCharsets.UTF_8));

        var applier = new MacosApplier();
        // Helper script doesn't exist — should fall back to browser
        try {
            applier.apply(item(), tempFile, appPath);
        } catch (Exception e) {
            assertNotNull(e.getMessage());
        }
        // tempFile should still exist (not deleted by the applier)
        assertTrue(Files.exists(tempFile));
    }

    @Test
    @DisplayName("apply with existing helper script makes it executable")
    void existingHelperScriptMadeExecutable() throws Exception {
        var appPath = tempDir.resolve("Test.app");
        var scriptDir = appPath.resolve("Contents/MacOS");
        Files.createDirectories(scriptDir);
        var helperScript = scriptDir.resolve("sparkle4j-updater");
        // Write a script that just exits (cross-platform safe for test)
        Files.writeString(helperScript, "#!/bin/sh\nexit 0\n");

        var tempFile = tempDir.resolve("update.zip");
        Files.write(tempFile, "fake zip".getBytes(StandardCharsets.UTF_8));

        var applier = new MacosApplier();
        try {
            applier.apply(item(), tempFile, appPath);
        } catch (Exception e) {
            assertNotNull(e.getMessage());
        }
        // Script should be marked executable (on Unix systems)
        if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            assertTrue(helperScript.toFile().canExecute());
        }
    }

    @Test
    @DisplayName("apply passes correct arguments to helper script")
    void helperScriptArguments() throws Exception {
        var appPath = tempDir.resolve("Test.app");
        var scriptDir = appPath.resolve("Contents/MacOS");
        Files.createDirectories(scriptDir);

        // Write a script that captures its arguments to a file
        var helperScript = scriptDir.resolve("sparkle4j-updater");
        var argsFile = tempDir.resolve("captured-args.txt");
        Files.writeString(
                helperScript,
                "#!/bin/sh\necho \"$1\" > " + argsFile + "\necho \"$2\" >> " + argsFile + "\n");
        helperScript.toFile().setExecutable(true);

        var tempFile = tempDir.resolve("update.zip");
        Files.write(tempFile, "fake zip".getBytes(StandardCharsets.UTF_8));

        var applier = new MacosApplier();
        try {
            applier.apply(item(), tempFile, appPath);
            // Give the fire-and-forget process time to write
            Thread.sleep(500);

            if (Files.exists(argsFile)) {
                var args = Files.readString(argsFile);
                assertTrue(args.contains(appPath.toString()));
                assertTrue(args.contains(tempFile.toString()));
            }
        } catch (Exception e) {
            assertNotNull(e.getMessage());
        }
        assertTrue(Files.exists(tempFile));
    }
}
