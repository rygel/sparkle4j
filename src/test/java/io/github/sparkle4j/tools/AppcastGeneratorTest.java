package io.github.sparkle4j.tools;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.List;

@SuppressWarnings("NullAway.Init")
class AppcastGeneratorTest {

    @TempDir Path tempDir;

    private static Object invokeStatic(String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = AppcastGenerator.class.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // --- sha256Hex ---

    @Test
    @DisplayName("sha256Hex produces correct hash for known input")
    void sha256HexKnownInput() throws Exception {
        var data = "hello".getBytes(StandardCharsets.UTF_8);
        var result =
                (String) invokeStatic("sha256Hex", new Class<?>[] {byte[].class}, (Object) data);

        // Known SHA-256 of "hello"
        var expected = MessageDigest.getInstance("SHA-256").digest(data);
        var sb = new StringBuilder();
        for (byte b : expected) sb.append(String.format("%02x", b));
        assertEquals(sb.toString(), result);
    }

    @Test
    @DisplayName("sha256Hex returns 64-character hex string")
    void sha256HexLength() {
        var result =
                (String)
                        invokeStatic(
                                "sha256Hex",
                                new Class<?>[] {byte[].class},
                                (Object) "test".getBytes(StandardCharsets.UTF_8));
        assertEquals(64, result.length());
        assertTrue(result.matches("[0-9a-f]+"));
    }

    // --- sign ---

    @Test
    @DisplayName("sign produces valid Base64 signature")
    void signProducesBase64() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var data = "test data".getBytes(StandardCharsets.UTF_8);

        var signature =
                (String)
                        invokeStatic(
                                "sign",
                                new Class<?>[] {byte[].class, PrivateKey.class},
                                data,
                                keyPair.getPrivate());

        assertNotNull(signature);
        assertDoesNotThrow(() -> Base64.getDecoder().decode(signature));
    }

    @Test
    @DisplayName("sign and verify round-trip")
    void signVerifyRoundTrip() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var data = "test data".getBytes(StandardCharsets.UTF_8);

        var signature =
                (String)
                        invokeStatic(
                                "sign",
                                new Class<?>[] {byte[].class, PrivateKey.class},
                                data,
                                keyPair.getPrivate());

        var sig = java.security.Signature.getInstance("Ed25519");
        sig.initVerify(keyPair.getPublic());
        sig.update(data);
        assertTrue(sig.verify(Base64.getDecoder().decode(signature)));
    }

    // --- mimeType ---

    @Test
    @DisplayName("mimeType returns correct types")
    void mimeTypes() {
        assertEquals(
                "application/octet-stream",
                invokeStatic("mimeType", new Class<?>[] {String.class}, "exe"));
        assertEquals(
                "application/zip", invokeStatic("mimeType", new Class<?>[] {String.class}, "zip"));
        assertEquals(
                "application/vnd.debian.binary-package",
                invokeStatic("mimeType", new Class<?>[] {String.class}, "deb"));
        assertEquals(
                "application/x-rpm",
                invokeStatic("mimeType", new Class<?>[] {String.class}, "rpm"));
        assertEquals(
                "application/octet-stream",
                invokeStatic("mimeType", new Class<?>[] {String.class}, "unknown"));
    }

    // --- minimalRssSkeleton ---

    @Test
    @DisplayName("minimalRssSkeleton produces valid RSS structure")
    void minimalRssSkeletonValid() {
        var result =
                (String) invokeStatic("minimalRssSkeleton", new Class<?>[] {String.class}, "MyApp");
        assertTrue(result.contains("<rss version=\"2.0\""));
        assertTrue(result.contains("xmlns:sparkle="));
        assertTrue(result.contains("<title>MyApp</title>"));
        assertTrue(result.contains("<channel>"));
        assertTrue(result.contains("</channel>"));
        assertTrue(result.contains("</rss>"));
    }

    // --- insertItem ---

    @Test
    @DisplayName("insertItem prepends before existing items")
    void insertItemPrepends() {
        var existing =
                """
                <rss><channel>
                    <item><title>Old</title></item>
                </channel></rss>""";
        var newItem = "<item><title>New</title></item>";

        var result =
                (String)
                        invokeStatic(
                                "insertItem",
                                new Class<?>[] {String.class, String.class},
                                existing,
                                newItem);

        assertTrue(result.indexOf("New") < result.indexOf("Old"));
    }

    @Test
    @DisplayName("insertItem inserts into empty channel")
    void insertItemEmptyChannel() {
        var existing =
                """
                <rss><channel>
                  <title>App</title>
                </channel></rss>""";
        var newItem = "<item><title>First</title></item>";

        var result =
                (String)
                        invokeStatic(
                                "insertItem",
                                new Class<?>[] {String.class, String.class},
                                existing,
                                newItem);

        assertTrue(result.contains("<item><title>First</title></item>"));
        assertTrue(result.contains("</channel>"));
    }

    // --- buildItem ---

    @Test
    @DisplayName("buildItem includes release notes link when provided")
    void buildItemWithReleaseNotes() {
        var result =
                (String)
                        invokeStatic(
                                "buildItem",
                                new Class<?>[] {
                                    String.class,
                                    String.class,
                                    String.class,
                                    String.class,
                                    String.class
                                },
                                "MyApp",
                                "1.0.0",
                                "Mon, 01 Jan 2024",
                                "https://example.com/notes",
                                "<enclosure/>");

        assertTrue(result.contains("sparkle:releaseNotesLink"));
        assertTrue(result.contains("https://example.com/notes"));
    }

    @SuppressWarnings("NullAway")
    @Test
    @DisplayName("buildItem omits release notes link when null")
    void buildItemWithoutReleaseNotes() {
        var result =
                (String)
                        invokeStatic(
                                "buildItem",
                                new Class<?>[] {
                                    String.class,
                                    String.class,
                                    String.class,
                                    String.class,
                                    String.class
                                },
                                "MyApp",
                                "1.0.0",
                                "Mon, 01 Jan 2024",
                                (String) null,
                                "<enclosure/>");

        assertFalse(result.contains("releaseNotesLink"));
        assertTrue(result.contains("<title>MyApp 1.0.0</title>"));
        assertTrue(result.contains("<sparkle:version>1.0.0</sparkle:version>"));
    }

    // --- loadPrivateKey ---

    @Test
    @DisplayName("loadPrivateKey round-trips with generated key")
    void loadPrivateKeyRoundTrip() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var pkcs8Bytes = keyPair.getPrivate().getEncoded();
        var base64 = Base64.getEncoder().encodeToString(pkcs8Bytes);

        var loaded = invokeStatic("loadPrivateKey", new Class<?>[] {String.class}, base64);

        assertNotNull(loaded);
        assertEquals("EdDSA", ((PrivateKey) loaded).getAlgorithm());
    }

    @Test
    @DisplayName("loadPrivateKey throws on invalid base64")
    void loadPrivateKeyInvalidBase64() {
        assertThrows(
                AssertionError.class,
                () ->
                        invokeStatic(
                                "loadPrivateKey", new Class<?>[] {String.class}, "not-base64!!!"));
    }

    // --- requireEnv ---

    @Test
    @DisplayName("requireEnv throws on missing environment variable")
    void requireEnvThrowsMissing() {
        assertThrows(
                AssertionError.class,
                () ->
                        invokeStatic(
                                "requireEnv",
                                new Class<?>[] {String.class},
                                "SPARKLE4J_NONEXISTENT_VAR_" + System.nanoTime()));
    }

    // --- insertItem preserves multiple existing items ---

    @Test
    @DisplayName("insertItem preserves all existing items when prepending")
    void insertItemPreservesAll() {
        var existing =
                """
                <rss><channel>
                    <item><title>v1.0</title></item>
                    <item><title>v0.9</title></item>
                </channel></rss>""";
        var newItem = "<item><title>v1.1</title></item>";

        var result =
                (String)
                        invokeStatic(
                                "insertItem",
                                new Class<?>[] {String.class, String.class},
                                existing,
                                newItem);

        assertTrue(result.contains("v1.1"));
        assertTrue(result.contains("v1.0"));
        assertTrue(result.contains("v0.9"));
        assertTrue(result.indexOf("v1.1") < result.indexOf("v1.0"));
    }

    // --- buildEnclosures ---

    @SuppressWarnings("unchecked")
    private Object createInstaller(Path path, String os, String ext) {
        try {
            Class<?> installerClass =
                    Class.forName("io.github.sparkle4j.tools.AppcastGenerator$Installer");
            Constructor<?> ctor =
                    installerClass.getDeclaredConstructor(Path.class, String.class, String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(path, os, ext);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("buildEnclosures generates enclosure XML with signature and hash")
    void buildEnclosuresGeneratesXml() throws Exception {
        var installer = tempDir.resolve("app-setup.exe");
        Files.write(installer, "fake installer bytes".getBytes(StandardCharsets.UTF_8));

        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var installerObj = createInstaller(installer, "windows", "exe");

        Method method =
                AppcastGenerator.class.getDeclaredMethod(
                        "buildEnclosures", List.class, String.class, PrivateKey.class);
        method.setAccessible(true);

        var result =
                (String)
                        method.invoke(
                                null,
                                List.of(installerObj),
                                "https://dl.example.com",
                                keyPair.getPrivate());

        assertTrue(result.contains("url=\"https://dl.example.com/app-setup.exe\""));
        assertTrue(result.contains("sparkle:os=\"windows\""));
        assertTrue(result.contains("sparkle:edSignature="));
        assertTrue(result.contains("sparkle:sha256="));
        assertTrue(result.contains("type=\"application/octet-stream\""));
        assertTrue(result.contains("length=\""));
    }

    @Test
    @DisplayName("buildEnclosures handles multiple installers")
    void buildEnclosuresMultiple() throws Exception {
        var winInstaller = tempDir.resolve("app.exe");
        var macInstaller = tempDir.resolve("app.zip");
        Files.write(winInstaller, "win".getBytes(StandardCharsets.UTF_8));
        Files.write(macInstaller, "mac".getBytes(StandardCharsets.UTF_8));

        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();

        Method method =
                AppcastGenerator.class.getDeclaredMethod(
                        "buildEnclosures", List.class, String.class, PrivateKey.class);
        method.setAccessible(true);

        var result =
                (String)
                        method.invoke(
                                null,
                                List.of(
                                        createInstaller(winInstaller, "windows", "exe"),
                                        createInstaller(macInstaller, "macos", "zip")),
                                "https://dl.example.com",
                                keyPair.getPrivate());

        assertTrue(result.contains("sparkle:os=\"windows\""));
        assertTrue(result.contains("sparkle:os=\"macos\""));
        assertTrue(result.contains("type=\"application/octet-stream\""));
        assertTrue(result.contains("type=\"application/zip\""));
    }
}
