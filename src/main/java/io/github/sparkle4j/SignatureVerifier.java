package io.github.sparkle4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Verifies Ed25519 signatures on downloaded files.
 *
 * <p>Requires Java 15+ (EdDSA available in java.security since JEP 339).
 * If the public key is null, verification is skipped with a warning.
 *
 * <p>Accepts both raw 32-byte Ed25519 keys (Sparkle format) and
 * DER-encoded X.509 SubjectPublicKeyInfo (44 bytes, standard Java format).
 */
final class SignatureVerifier {

    private static final Logger log = Logger.getLogger(SignatureVerifier.class.getName());

    // DER header for Ed25519 SubjectPublicKeyInfo (RFC 8410): 12 bytes + 32-byte key = 44 bytes total
    private static final byte[] ED25519_DER_HEADER = {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private final String publicKeyBase64;

    SignatureVerifier(String publicKeyBase64) {
        this.publicKeyBase64 = publicKeyBase64;
    }

    /**
     * Returns true if the file's signature is valid (or verification is disabled).
     * Returns false if the signature is invalid or an error occurs.
     */
    boolean verify(Path file, String signatureBase64) {
        if (publicKeyBase64 == null) {
            log.warning("No public key configured — Ed25519 signature verification skipped");
            return true;
        }

        try {
            var rawKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
            var signatureBytes = Base64.getDecoder().decode(signatureBase64);
            var fileBytes = Files.readAllBytes(file);

            var keyFactory = KeyFactory.getInstance("Ed25519");
            var publicKey = keyFactory.generatePublic(toX509KeySpec(rawKeyBytes));

            var sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(fileBytes);
            return sig.verify(signatureBytes);
        } catch (GeneralSecurityException | IllegalArgumentException | IOException e) {
            log.severe("Signature verification error: " + e.getMessage());
            return false;
        }
    }

    private static X509EncodedKeySpec toX509KeySpec(byte[] keyBytes) {
        return switch (keyBytes.length) {
            case 32 -> {
                // raw key → wrap in DER
                var der = new byte[44];
                System.arraycopy(ED25519_DER_HEADER, 0, der, 0, 12);
                System.arraycopy(keyBytes, 0, der, 12, 32);
                yield new X509EncodedKeySpec(der);
            }
            default -> new X509EncodedKeySpec(keyBytes); // already DER-encoded or try as-is
        };
    }
}
