package io.github.rygel.sparkle4j;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;

@SuppressWarnings("NullAway.Init")
class SignatureVerifierTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("valid signature returns true")
    void validSignature() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var content = "test content".getBytes(StandardCharsets.UTF_8);
        var file = tempDir.resolve("test-file");
        Files.write(file, content);

        var sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(content);
        var signatureBase64 = Base64.getEncoder().encodeToString(sig.sign());

        // Use DER-encoded public key (44 bytes)
        var publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        var verifier = new SignatureVerifier(publicKeyBase64);
        assertTrue(verifier.verify(file, signatureBase64));
    }

    @Test
    @DisplayName("tampered file returns false")
    void tamperedFile() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var content = "test content".getBytes(StandardCharsets.UTF_8);
        var file = tempDir.resolve("test-file");
        Files.write(file, "tampered content".getBytes(StandardCharsets.UTF_8));

        var sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(content);
        var signatureBase64 = Base64.getEncoder().encodeToString(sig.sign());

        var publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        var verifier = new SignatureVerifier(publicKeyBase64);
        assertFalse(verifier.verify(file, signatureBase64));
    }

    @Test
    @DisplayName("invalid base64 key returns false")
    void invalidBase64Key() throws Exception {
        var file = tempDir.resolve("test-file");
        Files.write(file, "content".getBytes(StandardCharsets.UTF_8));

        var verifier = new SignatureVerifier("not-valid-base64!!!");
        assertFalse(verifier.verify(file, Base64.getEncoder().encodeToString(new byte[64])));
    }

    @Test
    @DisplayName("invalid key format returns false")
    void invalidKeyFormat() throws Exception {
        var file = tempDir.resolve("test-file");
        Files.write(file, "content".getBytes(StandardCharsets.UTF_8));

        var verifier = new SignatureVerifier(Base64.getEncoder().encodeToString(new byte[16]));
        assertFalse(verifier.verify(file, Base64.getEncoder().encodeToString(new byte[64])));
    }

    @Test
    @DisplayName("raw 32-byte key format works")
    void raw32ByteKey() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var content = "test content".getBytes(StandardCharsets.UTF_8);
        var file = tempDir.resolve("test-file");
        Files.write(file, content);

        var sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(content);
        var signatureBase64 = Base64.getEncoder().encodeToString(sig.sign());

        // Extract raw 32-byte key from DER-encoded public key (last 32 bytes of 44-byte encoding)
        var derEncoded = keyPair.getPublic().getEncoded();
        var rawKey = Arrays.copyOfRange(derEncoded, 12, 44);
        var publicKeyBase64 = Base64.getEncoder().encodeToString(rawKey);

        var verifier = new SignatureVerifier(publicKeyBase64);
        assertTrue(verifier.verify(file, signatureBase64));
    }

    @Test
    @DisplayName("DER-encoded 44-byte key format works")
    void derEncodedKey() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var content = "another test".getBytes(StandardCharsets.UTF_8);
        var file = tempDir.resolve("test-file");
        Files.write(file, content);

        var sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(content);
        var signatureBase64 = Base64.getEncoder().encodeToString(sig.sign());

        // Use full 44-byte DER-encoded key
        var publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        var verifier = new SignatureVerifier(publicKeyBase64);
        assertTrue(verifier.verify(file, signatureBase64));
    }
}
