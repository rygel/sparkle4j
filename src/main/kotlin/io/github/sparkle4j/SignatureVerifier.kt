package io.github.sparkle4j

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.logging.Logger

/**
 * Verifies Ed25519 signatures on downloaded files.
 *
 * Requires Java 15+ (EdDSA available in java.security since JEP 339).
 * If [publicKeyBase64] is null, verification is skipped with a warning.
 */
internal class SignatureVerifier(private val publicKeyBase64: String?) {

    private val log = Logger.getLogger(SignatureVerifier::class.java.name)

    /**
     * Returns true if the file's signature is valid (or verification is disabled).
     * Returns false if the signature is invalid or an error occurs.
     *
     * Accepts both formats for the public key:
     * - **Raw 32-byte Ed25519 key** (as produced by Sparkle's `generate_keys` tool and
     *   `openssl pkey -outform DER | tail -c 32 | base64`)
     * - **DER-encoded X.509 SubjectPublicKeyInfo** (44 bytes, standard Java format, as
     *   produced by `openssl pkey -pubout -outform DER | base64`)
     */
    fun verify(file: Path, signatureBase64: String): Boolean {
        if (publicKeyBase64 == null) {
            log.warning("No public key configured — Ed25519 signature verification skipped")
            return true
        }

        return try {
            val rawKeyBytes = Base64.getDecoder().decode(publicKeyBase64)
            val signatureBytes = Base64.getDecoder().decode(signatureBase64)
            val fileBytes = Files.readAllBytes(file)

            val keyFactory = KeyFactory.getInstance("Ed25519")
            val publicKey = keyFactory.generatePublic(toX509KeySpec(rawKeyBytes))

            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(publicKey)
            sig.update(fileBytes)
            sig.verify(signatureBytes)
        } catch (e: Exception) {
            log.severe("Signature verification error: ${e.message}")
            false
        }
    }

    private companion object {
        // DER header for Ed25519 SubjectPublicKeyInfo (RFC 8410): 12 bytes + 32-byte key = 44 bytes total
        val ED25519_DER_HEADER = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
        )

        fun toX509KeySpec(keyBytes: ByteArray): X509EncodedKeySpec = when (keyBytes.size) {
            32   -> X509EncodedKeySpec(ED25519_DER_HEADER + keyBytes) // raw key → wrap in DER
            44   -> X509EncodedKeySpec(keyBytes)                       // already DER-encoded
            else -> X509EncodedKeySpec(keyBytes)                       // try as-is; will throw if wrong
        }
    }
}
