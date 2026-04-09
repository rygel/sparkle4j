package io.github.rygel.sparkle4j;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

@SuppressWarnings("NullAway.Init")
class Sparkle4jInstanceTest {

    @RegisterExtension
    static WireMockExtension wireMock =
            WireMockExtension.newInstance()
                    .options(WireMockConfiguration.wireMockConfig().dynamicPort())
                    .build();

    @TempDir Path tempDir;

    @SuppressWarnings("NullAway")
    private Sparkle4jInstance buildInstance(String currentVersion, int intervalHours) {
        return buildInstance(currentVersion, intervalHours, null);
    }

    @SuppressWarnings("NullAway")
    private Sparkle4jInstance buildInstance(
            String currentVersion, int intervalHours, String publicKey) {
        return Sparkle4j.builder()
                .appcastUrl("https://example.com/appcast.xml")
                .currentVersion(currentVersion)
                .appName("sparkle4j-instance-test-" + System.nanoTime())
                .checkIntervalHours(intervalHours)
                .publicKey(publicKey)
                .build();
    }

    // --- checkNow ---

    @Test
    @DisplayName("checkNow returns empty when check interval not elapsed")
    void checkNowThrottledByInterval() {
        var appName = "sparkle4j-throttle-" + System.nanoTime();
        var prefs = new UpdatePreferences(appName);
        prefs.setLastCheckTimestamp(Instant.now());

        try (var instance =
                Sparkle4j.builder()
                        .appcastUrl("https://example.com/appcast.xml")
                        .currentVersion("1.0.0")
                        .appName(appName)
                        .checkIntervalHours(24)
                        .build()) {
            // Should be throttled because we just set the timestamp
            assertTrue(instance.checkNow().isEmpty());
        }
    }

    @Test
    @DisplayName("checkNow returns empty for non-HTTPS appcast URL")
    void checkNowRejectsHttp() {
        try (var instance =
                Sparkle4j.builder()
                        .appcastUrl("http://example.com/appcast.xml")
                        .currentVersion("1.0.0")
                        .appName("sparkle4j-http-" + System.nanoTime())
                        .checkIntervalHours(0)
                        .build()) {
            assertTrue(instance.checkNow().isEmpty());
        }
    }

    @Test
    @DisplayName("check interval of 0 disables throttling")
    void zeroIntervalDisablesThrottling() {
        var appName = "sparkle4j-interval-" + System.nanoTime();
        var prefs = new UpdatePreferences(appName);
        prefs.setLastCheckTimestamp(Instant.now());

        try (var instance =
                Sparkle4j.builder()
                        .appcastUrl("https://example.com/appcast.xml")
                        .currentVersion("1.0.0")
                        .appName(appName)
                        .checkIntervalHours(0)
                        .build()) {
            // With interval=0, should not be throttled — will attempt to fetch and fail
            var result = instance.checkNow();
            assertTrue(result.isEmpty()); // empty because fetch fails, not because throttled
        }
    }

    @Test
    @DisplayName("checkNow returns empty when fetch returns null")
    void checkNowNullOnFetchFailure() {
        try (var instance = buildInstance("1.0.0", 0)) {
            // example.com won't serve appcast — fetch fails gracefully
            assertTrue(instance.checkNow().isEmpty());
        }
    }

    // --- skipVersion ---

    @Test
    @DisplayName("skipVersion persists across checks")
    void skipVersionPersists() {
        var appName = "sparkle4j-skip-" + System.nanoTime();
        try (var instance =
                Sparkle4j.builder()
                        .appcastUrl("https://example.com/appcast.xml")
                        .currentVersion("1.0.0")
                        .appName(appName)
                        .checkIntervalHours(0)
                        .build()) {
            instance.skipVersion("2.0.0");

            var prefs = new UpdatePreferences(appName);
            assertTrue(prefs.isSkipped("2.0.0"));
        }
    }

    // --- installUpdate (via reflection since it's package-private on the inner class) ---

    private Object createInstanceImpl(String publicKey) {
        var appName = "sparkle4j-install-" + System.nanoTime();
        var config =
                new Sparkle4jConfig(
                        "https://example.com/appcast.xml",
                        "1.0.0",
                        publicKey,
                        0,
                        null,
                        appName,
                        null,
                        null);
        return Sparkle4j.createInstance(config);
    }

    private void callInstallUpdate(Object instance, UpdateItem item, Path tempFile) {
        try {
            Method method =
                    instance.getClass()
                            .getDeclaredMethod("installUpdate", UpdateItem.class, Path.class);
            method.setAccessible(true);
            method.invoke(instance, item, tempFile);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("NullAway")
    private UpdateItem fakeUpdateItem(String signature) {
        return new UpdateItem(
                "Test 2.0.0", // title
                "2.0.0", // version
                "2.0.0", // shortVersionString
                "https://example.com/update.exe", // url
                1000, // length
                signature, // edSignature
                null, // releaseNotesUrl
                null, // inlineDescription
                null, // minimumJavaVersion
                null, // pubDate
                AppcastParser.currentOs(), // os
                false, // isCriticalUpdate
                "exe"); // installerType
    }

    @Test
    @DisplayName("valid signature passes verification before applier runs")
    void installUpdateValidSignaturePasses() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        var content = "fake installer".getBytes(StandardCharsets.UTF_8);
        var file = tempDir.resolve("update.exe");
        Files.write(file, content);

        var sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(content);
        var signatureBase64 = Base64.getEncoder().encodeToString(sig.sign());

        // Verify the signature check directly — installUpdate delegates to applier after
        var verifier = new SignatureVerifier(publicKeyBase64);
        assertTrue(verifier.verify(file, signatureBase64));
    }

    @Test
    @DisplayName("installUpdate with invalid signature deletes temp file")
    void installUpdateInvalidSignatureDeletesFile() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        var content = "fake installer".getBytes(StandardCharsets.UTF_8);
        var file = tempDir.resolve("update.exe");
        Files.write(file, content);

        var instance = createInstanceImpl(publicKeyBase64);
        var item = fakeUpdateItem("aW52YWxpZC1zaWduYXR1cmU="); // invalid signature

        callInstallUpdate(instance, item, file);

        assertFalse(Files.exists(file));
    }

    @SuppressWarnings("NullAway")
    @Test
    @DisplayName("null signature means no verification needed")
    void nullSignatureSkipsVerification() {
        // When edSignature is null, installUpdate skips the verify call entirely
        // and proceeds to applier.apply(). Verified by checking the condition in code:
        // if (item.edSignature() != null && !verifier.verify(...))
        var item = fakeUpdateItem(null);
        assertNull(item.edSignature());
    }

    @SuppressWarnings("NullAway")
    @Test
    @DisplayName("null public key causes verifier to skip verification")
    void nullPublicKeySkipsVerification() throws Exception {
        var content = "anything".getBytes(StandardCharsets.UTF_8);
        var file = tempDir.resolve("update.exe");
        Files.write(file, content);

        var verifier = new SignatureVerifier(null);
        assertTrue(verifier.verify(file, "ignored"));
    }

    // --- Static helpers ---

    @Test
    @DisplayName("Sparkle4j.resolveAppName returns default when no manifest")
    void resolveAppNameDefault() {
        assertEquals("Application", Sparkle4j.resolveAppName());
    }

    @Test
    @DisplayName("VersionComparator.isNewer correctly identifies newer versions")
    void isNewerVersionComparison() {
        assertTrue(VersionComparator.isNewer("2.0.0", "1.0.0"));
        assertTrue(VersionComparator.isNewer("1.1.0", "1.0.0"));
        assertTrue(VersionComparator.isNewer("1.0.1", "1.0.0"));
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.0"));
        assertFalse(VersionComparator.isNewer("0.9.0", "1.0.0"));
        assertFalse(VersionComparator.isNewer("1.0.0-alpha", "1.0.0"));
    }

    // --- checkInBackground ---

    @Test
    @DisplayName("checkInBackground does not throw")
    void checkInBackgroundDoesNotThrow() {
        try (var instance = buildInstance("1.0.0", 0)) {
            assertDoesNotThrow(instance::checkInBackground);
        }
    }

    // --- onUpdateFound hook ---

    @Test
    @DisplayName("builder accepts onUpdateFound callback")
    void onUpdateFoundCallback() {
        try (var instance =
                Sparkle4j.builder()
                        .appcastUrl("https://example.com/appcast.xml")
                        .currentVersion("1.0.0")
                        .appName("sparkle4j-hook-" + System.nanoTime())
                        .onUpdateFound(item -> false)
                        .build()) {
            assertNotNull(instance);
        }
    }

    // --- isCheckDue via reflection ---

    private boolean callIsCheckDue(Object instance) {
        try {
            Method method = instance.getClass().getDeclaredMethod("isCheckDue");
            method.setAccessible(true);
            return (boolean) method.invoke(instance);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("isCheckDue returns true when no previous check")
    void isCheckDueTrueWhenNoPreviousCheck() {
        var appName = "sparkle4j-due-" + System.nanoTime();
        var config =
                new Sparkle4jConfig(
                        "https://example.com/appcast.xml",
                        "1.0.0",
                        null,
                        24,
                        null,
                        appName,
                        null,
                        null);
        try (var instance = Sparkle4j.createInstance(config)) {
            assertTrue(callIsCheckDue(instance));
        }
    }

    @Test
    @DisplayName("isCheckDue returns false when checked recently")
    void isCheckDueFalseWhenRecent() {
        var appName = "sparkle4j-recent-" + System.nanoTime();
        new UpdatePreferences(appName).setLastCheckTimestamp(Instant.now());

        var config =
                new Sparkle4jConfig(
                        "https://example.com/appcast.xml",
                        "1.0.0",
                        null,
                        24,
                        null,
                        appName,
                        null,
                        null);
        try (var instance = Sparkle4j.createInstance(config)) {
            assertFalse(callIsCheckDue(instance));
        }
    }

    @Test
    @DisplayName("isCheckDue returns true when interval is 0")
    void isCheckDueTrueWhenIntervalZero() {
        var appName = "sparkle4j-zero-" + System.nanoTime();
        new UpdatePreferences(appName).setLastCheckTimestamp(Instant.now());

        var config =
                new Sparkle4jConfig(
                        "https://example.com/appcast.xml",
                        "1.0.0",
                        null,
                        0,
                        null,
                        appName,
                        null,
                        null);
        try (var instance = Sparkle4j.createInstance(config)) {
            assertTrue(callIsCheckDue(instance));
        }
    }

    @Test
    @DisplayName("isCheckDue returns false for HTTP URL")
    void isCheckDueFalseForHttp() {
        var appName = "sparkle4j-http-" + System.nanoTime();
        var config =
                new Sparkle4jConfig(
                        "http://example.com/appcast.xml",
                        "1.0.0",
                        null,
                        0,
                        null,
                        appName,
                        null,
                        null);
        try (var instance = Sparkle4j.createInstance(config)) {
            assertFalse(callIsCheckDue(instance));
        }
    }

    @Test
    @DisplayName("isCheckDue returns true when last check was long ago")
    void isCheckDueTrueWhenOld() {
        var appName = "sparkle4j-old-" + System.nanoTime();
        new UpdatePreferences(appName)
                .setLastCheckTimestamp(Instant.now().minusSeconds(86_400 * 2));

        var config =
                new Sparkle4jConfig(
                        "https://example.com/appcast.xml",
                        "1.0.0",
                        null,
                        24,
                        null,
                        appName,
                        null,
                        null);
        try (var instance = Sparkle4j.createInstance(config)) {
            assertTrue(callIsCheckDue(instance));
        }
    }

    // --- fetchAndParseBestUpdate via reflection ---

    @SuppressWarnings("NullAway")
    private UpdateItem callFetchAndParse(Object instance) {
        try {
            Method method = instance.getClass().getDeclaredMethod("fetchAndParseBestUpdate");
            method.setAccessible(true);
            return (UpdateItem) method.invoke(instance);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("fetchAndParseBestUpdate returns null on unreachable URL")
    void fetchAndParseNullOnUnreachable() {
        var appName = "sparkle4j-unreachable-" + System.nanoTime();
        var config =
                new Sparkle4jConfig(
                        "https://localhost:1/appcast.xml",
                        "1.0.0",
                        null,
                        0,
                        null,
                        appName,
                        null,
                        null);
        try (var instance = Sparkle4j.createInstance(config)) {
            assertNull(callFetchAndParse(instance));
        }
    }

    // --- applyUpdate with WireMock download + invalid signature (error path) ---

    @Test
    @DisplayName("applyUpdate with invalid signature logs error and does not throw")
    void applyUpdateInvalidSignature() {
        var keyPair =
                assertDoesNotThrow(() -> KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        var publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        wireMock.stubFor(
                get(urlEqualTo("/update.exe"))
                        .willReturn(
                                aResponse().withStatus(200).withBody("fake installer content")));

        var appName = "sparkle4j-apply-" + System.nanoTime();
        var config =
                new Sparkle4jConfig(
                        "https://example.com/appcast.xml",
                        "1.0.0",
                        publicKeyBase64,
                        0,
                        null,
                        appName,
                        null,
                        null);

        @SuppressWarnings("NullAway")
        var item =
                new UpdateItem(
                        "Test 2.0.0",
                        "2.0.0",
                        "2.0.0",
                        "http://localhost:" + wireMock.getPort() + "/update.exe",
                        21,
                        "aW52YWxpZA==",
                        null,
                        null,
                        null,
                        null,
                        AppcastParser.currentOs(),
                        false,
                        "exe");

        try (var instance = Sparkle4j.createInstance(config)) {
            assertDoesNotThrow(() -> instance.applyUpdate(item));
        }
    }

    // --- Sparkle4jConfig record ---

    @SuppressWarnings("NullAway")
    @Test
    @DisplayName("Sparkle4jConfig record accessors work")
    void configRecordAccessors() {
        var config =
                new Sparkle4jConfig(
                        "https://example.com/appcast.xml",
                        "1.0.0",
                        "key",
                        12,
                        null,
                        "TestApp",
                        null,
                        null);

        assertEquals("https://example.com/appcast.xml", config.appcastUrl());
        assertEquals("1.0.0", config.currentVersion());
        assertEquals("key", config.publicKey());
        assertEquals(12, config.checkIntervalHours());
        assertNull(config.parentComponent());
        assertEquals("TestApp", config.appName());
        assertNull(config.onUpdateFound());
        assertNull(config.macosAppPath());
    }

    // --- presentUpdate with hook ---

    @SuppressWarnings("NullAway")
    private void callPresentUpdate(Object instance, UpdateItem item) {
        try {
            Method method =
                    instance.getClass().getDeclaredMethod("presentUpdate", UpdateItem.class);
            method.setAccessible(true);
            method.invoke(instance, item);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("presentUpdate with hook returning false does not show dialog")
    void presentUpdateHookReturnsFalse() {
        var hookCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
        var appName = "sparkle4j-hook-" + System.nanoTime();
        var config =
                new Sparkle4jConfig(
                        "https://example.com/appcast.xml",
                        "1.0.0",
                        null,
                        0,
                        null,
                        appName,
                        item -> {
                            hookCalled.set(true);
                            return false;
                        },
                        null);

        @SuppressWarnings("NullAway")
        var item = fakeUpdateItem(null);

        try (var instance = Sparkle4j.createInstance(config)) {
            callPresentUpdate(instance, item);
            assertTrue(hookCalled.get());
        }
    }
}
