package io.github.rygel.sparkle4j;

import io.github.rygel.sparkle4j.apply.UpdateApplier;
import io.github.rygel.sparkle4j.ui.UpdateDialog;

import org.jspecify.annotations.Nullable;
import org.xml.sax.SAXException;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Entry point for the sparkle4j update library.
 *
 * <pre>{@code
 * Sparkle4j.builder()
 *     .appcastUrl("https://example.com/appcast.xml")
 *     .currentVersion("0.5.0")
 *     .parentComponent(mainWindow)
 *     .build()
 *     .checkInBackground();
 * }</pre>
 */
public final class Sparkle4j {

    private Sparkle4j() {}

    /** Creates a new builder for configuring a sparkle4j instance. */
    public static Sparkle4jBuilder builder() {
        return new Sparkle4jBuilder();
    }

    /** Package-private factory used by the builder. */
    static Sparkle4jInstance createInstance(Sparkle4jConfig config) {
        return new Sparkle4jInstanceImpl(config);
    }

    static String resolveAppName() {
        var pkg = Sparkle4jConfig.class.getPackage();
        var title = pkg != null ? pkg.getImplementationTitle() : null;
        return title != null ? title : "Application";
    }

    // ---------------------------------------------------------------------------
    // Implementation
    // ---------------------------------------------------------------------------

    private static final class Sparkle4jInstanceImpl implements Sparkle4jInstance {

        private static final Logger log = Logger.getLogger(Sparkle4jInstanceImpl.class.getName());

        private final Sparkle4jConfig config;
        private final UpdatePreferences prefs;
        private final AppcastFetcher fetcher;
        private final AppcastParser parser;
        private final @Nullable SignatureVerifier verifier;
        private final UpdateApplier applier;

        private final java.util.concurrent.ExecutorService executor =
                Executors.newSingleThreadExecutor(
                        r -> {
                            var t = new Thread(r, "sparkle4j-checker");
                            t.setDaemon(true);
                            return t;
                        });

        Sparkle4jInstanceImpl(Sparkle4jConfig config) {
            this.config = config;
            this.prefs = new UpdatePreferences(config.appName());
            this.fetcher = new AppcastFetcher();
            this.parser = new AppcastParser();
            var key = config.publicKey();
            this.verifier = key != null ? new SignatureVerifier(key) : null;
            this.applier = new UpdateApplier(config);
        }

        @Override
        public void checkInBackground() {
            executor.submit(
                    () -> {
                        try {
                            checkNow()
                                    .ifPresent(
                                            item ->
                                                    SwingUtilities.invokeLater(
                                                            () -> presentUpdate(item)));
                        } catch (RuntimeException e) {
                            log.warning(
                                    "Unexpected error during background update check: "
                                            + e.getMessage());
                        }
                    });
        }

        @Override
        public Optional<UpdateItem> checkNow() {
            if (!isCheckDue()) {
                return Optional.empty();
            }
            var best = fetchAndParseBestUpdate();
            if (best == null) {
                return Optional.empty();
            }
            if (prefs.isSkipped(best.version())) {
                return Optional.empty();
            }
            if (!VersionComparator.isNewer(best.version(), config.currentVersion())) {
                return Optional.empty();
            }
            return Optional.of(best);
        }

        @Override
        public void applyUpdate(UpdateItem item) {
            try {
                var tempFile =
                        new UpdateDownloader().download(item.url(), item.length(), (a, b) -> {});
                installUpdate(item, tempFile);
            } catch (IOException e) {
                log.severe("Download failed in applyUpdate: " + e.getMessage());
                showErrorDialog("Download failed. Try again later.", "Download Error");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warning("Download interrupted in applyUpdate");
            }
        }

        @Override
        public void skipVersion(String version) {
            prefs.skipVersion(version);
        }

        @Override
        public void close() {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        void installUpdate(UpdateItem item, java.nio.file.Path tempFile) {
            if (config.publicKey() != null) {
                // Key configured — signature must be present and valid.
                if (item.edSignature() == null) {
                    log.severe(
                            "Update "
                                    + item.version()
                                    + " has no Ed25519 signature but a public key is configured"
                                    + " — aborting update");
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {
                    }
                    showErrorDialog(
                            "The update could not be verified (no signature provided). "
                                    + "It has been discarded for your safety.",
                            "Verification Failed");
                    return;
                }
                if (!java.util.Objects.requireNonNull(verifier)
                        .verify(tempFile, item.edSignature())) {
                    log.severe(
                            "Signature verification failed for "
                                    + item.version()
                                    + " — aborting update");
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {
                    }
                    showErrorDialog(
                            "The update could not be verified. It has been discarded for your"
                                    + " safety.",
                            "Verification Failed");
                    return;
                }
            } else {
                // allowUnsignedUpdates — warn and proceed.
                log.warning(
                        "Installing update "
                                + item.version()
                                + " without signature verification (allowUnsignedUpdates is set)");
            }
            applier.apply(item, tempFile);
        }

        private boolean isCheckDue() {
            if (config.checkIntervalHours() > 0) {
                var last = prefs.getLastCheckTimestamp();
                if (last != null
                        && Duration.between(last, Instant.now()).toHours()
                                < config.checkIntervalHours()) {
                    return false;
                }
            }
            if (!config.appcastUrl().startsWith("https://")) {
                log.severe("Appcast URL must be HTTPS: " + config.appcastUrl());
                return false;
            }
            return true;
        }

        private @Nullable UpdateItem fetchAndParseBestUpdate() {
            String xml;
            try {
                xml = fetcher.fetch(config.appcastUrl());
            } catch (RuntimeException e) {
                log.warning("Failed to fetch appcast: " + e.getMessage());
                return null;
            }
            if (xml == null) return null;

            prefs.setLastCheckTimestamp(Instant.now());

            try {
                var items = parser.parse(xml);
                return items.isEmpty() ? null : items.get(0);
            } catch (RuntimeException
                    | SAXException
                    | IOException
                    | ParserConfigurationException e) {
                log.severe("Failed to parse appcast: " + e.getMessage());
                return null;
            }
        }

        private void presentUpdate(UpdateItem item) {
            var hook = config.onUpdateFound();
            if (hook != null && !hook.test(item)) {
                return;
            }

            new UpdateDialog(
                            config,
                            item,
                            this::skipVersion,
                            this::installUpdate,
                            progress ->
                                    new UpdateDownloader()
                                            .download(item.url(), item.length(), progress))
                    .show();
        }

        private void showErrorDialog(String message, String title) {
            SwingUtilities.invokeLater(
                    () ->
                            JOptionPane.showMessageDialog(
                                    config.parentComponent(),
                                    message,
                                    title,
                                    JOptionPane.ERROR_MESSAGE));
        }
    }
}
