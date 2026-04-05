package io.github.sparkle4j.tools;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.util.Base64;

class AppcastGeneratorTest {

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
}
