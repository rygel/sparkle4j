package io.github.sparkle4j

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignatureVerifierTest {

    @TempDir
    lateinit var tempDir: Path

    // Generate a real Ed25519 keypair for tests (Ed25519 needs no explicit initialize)
    private val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    private val publicKeyBase64: String =
        Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private fun sign(data: ByteArray): String {
        val sig = java.security.Signature.getInstance("Ed25519")
        sig.initSign(keyPair.private)
        sig.update(data)
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    @Test
    fun `verifies valid signature`() {
        val content = "hello sparkle4j".toByteArray()
        val file = tempDir.resolve("update.exe").also { Files.write(it, content) }
        val signature = sign(content)

        val verifier = SignatureVerifier(publicKeyBase64)
        assertTrue(verifier.verify(file, signature))
    }

    @Test
    fun `rejects signature for tampered file`() {
        val original = "original content".toByteArray()
        val signature = sign(original)

        val tampered = "tampered content".toByteArray()
        val file = tempDir.resolve("update.exe").also { Files.write(it, tampered) }

        val verifier = SignatureVerifier(publicKeyBase64)
        assertFalse(verifier.verify(file, signature))
    }

    @Test
    fun `skips verification and returns true when no public key configured`() {
        val file = tempDir.resolve("update.exe").also { Files.write(it, byteArrayOf(1, 2, 3)) }
        val verifier = SignatureVerifier(null)
        // Should warn and return true (verification disabled)
        assertTrue(verifier.verify(file, "any-signature"))
    }

    @Test
    fun `returns false for invalid base64 signature`() {
        val file = tempDir.resolve("update.exe").also { Files.write(it, byteArrayOf(1)) }
        val verifier = SignatureVerifier(publicKeyBase64)
        assertFalse(verifier.verify(file, "not-valid-base64!!!"))
    }

    @Test
    fun `returns false for invalid public key encoding`() {
        val content = "data".toByteArray()
        val file = tempDir.resolve("update.exe").also { Files.write(it, content) }
        val verifier = SignatureVerifier(Base64.getEncoder().encodeToString(byteArrayOf(0, 1, 2)))
        assertFalse(verifier.verify(file, sign(content)))
    }

    @Test
    fun `accepts raw 32-byte Ed25519 public key (Sparkle key format)`() {
        // Extract just the raw 32-byte key from the DER-encoded public key
        val derEncoded = keyPair.public.encoded // 44 bytes (12-byte header + 32-byte key)
        val rawKey32 = derEncoded.takeLast(32).toByteArray()

        val content = "test payload".toByteArray()
        val file = tempDir.resolve("update.exe").also { Files.write(it, content) }
        val signature = sign(content)

        val verifier = SignatureVerifier(Base64.getEncoder().encodeToString(rawKey32))
        assertTrue(verifier.verify(file, signature), "Raw 32-byte key should be accepted")
    }

    @Test
    fun `accepts DER-encoded X509 Ed25519 public key (Java keytool format)`() {
        // Standard Java-format public key (44-byte DER)
        val content = "test payload".toByteArray()
        val file = tempDir.resolve("update.exe").also { Files.write(it, content) }
        val signature = sign(content)

        val verifier = SignatureVerifier(publicKeyBase64) // publicKeyBase64 is DER-encoded (44 bytes)
        assertTrue(verifier.verify(file, signature), "DER X.509 key should be accepted")
    }
}
