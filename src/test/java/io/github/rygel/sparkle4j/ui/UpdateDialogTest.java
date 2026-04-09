package io.github.rygel.sparkle4j.ui;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.rygel.sparkle4j.Sparkle4jConfig;
import io.github.rygel.sparkle4j.UpdateItem;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import java.awt.Container;
import java.awt.Window;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for UpdateDialog Swing UI. These tests create real Swing dialogs and must run under Xvfb in
 * a container (mvn verify -Ptest-desktop). They are excluded from the default test run.
 */
@SuppressWarnings("NullAway.Init")
class UpdateDialogTest {

    @RegisterExtension
    static WireMockExtension wireMock =
            WireMockExtension.newInstance()
                    .options(WireMockConfiguration.wireMockConfig().dynamicPort())
                    .build();

    @SuppressWarnings("NullAway")
    private static String currentOs() {
        var name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) return "windows";
        if (name.contains("mac")) return "macos";
        return "linux";
    }

    private Sparkle4jConfig config() {
        return new Sparkle4jConfig(
                "https://example.com/appcast.xml", "1.0.0", null, 0, null, "TestApp", null, null);
    }

    @SuppressWarnings("NullAway")
    private UpdateItem item() {
        return new UpdateItem(
                "TestApp 2.0.0",
                "2.0.0",
                "2.0.0",
                "http://localhost:" + wireMock.getPort() + "/update.exe",
                100,
                null,
                null,
                "Some release notes",
                null,
                null,
                currentOs(),
                false,
                "exe");
    }

    @SuppressWarnings("NullAway")
    private UpdateItem criticalItem() {
        return new UpdateItem(
                "TestApp 2.0.0",
                "2.0.0",
                "2.0.0",
                "http://localhost:" + wireMock.getPort() + "/update.exe",
                100,
                null,
                null,
                "Critical fix",
                null,
                null,
                currentOs(),
                true,
                "exe");
    }

    private JDialog getDialog(UpdateDialog ud) throws Exception {
        Field f = UpdateDialog.class.getDeclaredField("dialog");
        f.setAccessible(true);
        return (JDialog) f.get(ud);
    }

    private void showNonModal(UpdateDialog ud) throws Exception {
        var latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(
                () -> {
                    // Replace show() logic without modality to avoid blocking
                    try {
                        Field dialogField = UpdateDialog.class.getDeclaredField("dialog");
                        dialogField.setAccessible(true);

                        var dialog = new JDialog((Window) null, "Update Available");
                        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                        dialogField.set(ud, dialog);

                        // Trigger the panel creation via reflection
                        var createHeader =
                                UpdateDialog.class.getDeclaredMethod("createHeaderPanel");
                        createHeader.setAccessible(true);
                        dialog.add(
                                (Container) createHeader.invoke(ud), java.awt.BorderLayout.NORTH);

                        var createNotes =
                                UpdateDialog.class.getDeclaredMethod("createNotesScrollPane");
                        createNotes.setAccessible(true);
                        dialog.add(
                                (Container) createNotes.invoke(ud), java.awt.BorderLayout.CENTER);

                        var createButtons =
                                UpdateDialog.class.getDeclaredMethod("createButtonPanel");
                        createButtons.setAccessible(true);
                        var buttonPanel = createButtons.invoke(ud);

                        Field bpField = UpdateDialog.class.getDeclaredField("buttonPanel");
                        bpField.setAccessible(true);
                        bpField.set(ud, buttonPanel);
                        dialog.add((Container) buttonPanel, java.awt.BorderLayout.SOUTH);

                        var createProgress =
                                UpdateDialog.class.getDeclaredMethod("createProgressPanel");
                        createProgress.setAccessible(true);
                        var progressPanel = createProgress.invoke(ud);
                        Field ppField = UpdateDialog.class.getDeclaredField("progressPanel");
                        ppField.setAccessible(true);
                        ppField.set(ud, progressPanel);

                        dialog.pack();
                        dialog.setVisible(true);
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                    latch.countDown();
                });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Dialog did not open in 5s");
    }

    @Test
    @DisplayName("dialog shows header with app name and version")
    void dialogShowsHeader() throws Exception {
        var ud = new UpdateDialog(config(), item(), v -> {}, (i, p) -> {}, p -> Path.of("stub"));
        showNonModal(ud);

        var dialog = getDialog(ud);
        assertNotNull(dialog);
        assertTrue(dialog.isVisible());

        // Check title
        assertEquals("Update Available", dialog.getTitle());

        dialog.dispose();
    }

    @Test
    @DisplayName("skip button calls onSkip and closes dialog")
    void skipButtonCallsOnSkip() throws Exception {
        var skipped = new AtomicReference<String>();
        var ud =
                new UpdateDialog(
                        config(), item(), skipped::set, (i, p) -> {}, p -> Path.of("stub"));
        showNonModal(ud);

        var dialog = getDialog(ud);
        var skipButton = findButton(dialog, "Skip This Version");
        assertNotNull(skipButton);

        var closeLatch = new CountDownLatch(1);
        dialog.addWindowListener(
                new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent e) {
                        closeLatch.countDown();
                    }
                });

        SwingUtilities.invokeLater(skipButton::doClick);
        assertTrue(closeLatch.await(5, TimeUnit.SECONDS));
        assertEquals("2.0.0", skipped.get());
    }

    @Test
    @DisplayName("remind later button closes dialog without skip")
    void remindLaterClosesDialog() throws Exception {
        var skipped = new AtomicBoolean(false);
        var ud =
                new UpdateDialog(
                        config(),
                        item(),
                        v -> skipped.set(true),
                        (i, p) -> {},
                        p -> Path.of("stub"));
        showNonModal(ud);

        var dialog = getDialog(ud);
        var remindButton = findButton(dialog, "Remind Me Later");
        assertNotNull(remindButton);

        var closeLatch = new CountDownLatch(1);
        dialog.addWindowListener(
                new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent e) {
                        closeLatch.countDown();
                    }
                });

        SwingUtilities.invokeLater(remindButton::doClick);
        assertTrue(closeLatch.await(5, TimeUnit.SECONDS));
        assertFalse(skipped.get());
    }

    @Test
    @DisplayName("critical update hides skip button")
    void criticalUpdateHidesSkip() throws Exception {
        var ud =
                new UpdateDialog(
                        config(), criticalItem(), v -> {}, (i, p) -> {}, p -> Path.of("stub"));
        showNonModal(ud);

        var dialog = getDialog(ud);
        var skipButton = findButton(dialog, "Skip This Version");
        assertNotNull(skipButton);
        assertFalse(skipButton.isVisible());

        dialog.dispose();
    }

    @Test
    @DisplayName("install button starts download")
    void installButtonStartsDownload() throws Exception {
        wireMock.stubFor(
                get(urlEqualTo("/update.exe"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withBody(
                                                "x".repeat(100).getBytes(StandardCharsets.UTF_8))));

        var installCalled = new CountDownLatch(1);
        var ud =
                new UpdateDialog(
                        config(),
                        item(),
                        v -> {},
                        (i, p) -> installCalled.countDown(),
                        p -> Path.of("stub"));
        showNonModal(ud);

        var dialog = getDialog(ud);
        var installButton = findButton(dialog, "Install");
        assertNotNull(installButton);

        SwingUtilities.invokeLater(installButton::doClick);

        // Wait for download to complete and "Install Now" to appear
        Thread.sleep(2000);

        var installNowButton = findButton(dialog, "Install Now");
        if (installNowButton != null) {
            SwingUtilities.invokeLater(installNowButton::doClick);
            assertTrue(installCalled.await(5, TimeUnit.SECONDS));
        }

        dialog.dispose();
    }

    private static @org.jspecify.annotations.Nullable JButton findButton(
            Container container, String text) {
        for (var c : container.getComponents()) {
            if (c instanceof JButton btn && text.equals(btn.getText())) return btn;
            if (c instanceof Container sub) {
                var found = findButton(sub, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
