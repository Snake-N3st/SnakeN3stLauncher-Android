package mc.snakenest.launcher.crypto;

import mc.snakenest.launcher.util.Hex;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE vector shared with the site (SigningStatementTest.php): a fixed seed, one (type, version, sha256, size)
 * tuple, and the public key, statement and signature that follow from them - cross-checked against Python's
 * {@code cryptography} library and PHP's libsodium. If either side drifts by one byte - a trailing newline, an
 * uppercase hex digit - every verification in the field fails at once while looking exactly like an attack.
 */
class SignedArtifactStatementTest {

    // Seed 000102...1f. Only the public half is used here; the seed is in the PHP test and Python cross-check.
    private static final String PUBLIC_KEY = "03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8";
    private static final String SHA256 = "e378b618ea1b20c47773f76f40aa0a8aefec499f3bb90b3a10c88e0389b4a18c";
    private static final String SIGNATURE = "19703f4adf05ff983f5139c26b3114b621b85604f826c3323cf3f6e29da41dfd"
            + "f3f3e31805232feed327c6bd8c5a163b1a2497153a2847197b95c5539d893f0f";

    private static byte[] shared() {
        return SignedArtifactStatement.build("bootstrap", "2.2.0", SHA256, 1234567);
    }

    @Test
    void theStatementIsExactlyTheSharedVectors() {
        assertEquals("sn3.artifact.v1\ntype=bootstrap\nversion=2.2.0\nsha256=" + SHA256 + "\nsize=1234567",
                new String(shared(), StandardCharsets.US_ASCII));
    }

    @Test
    void theSitesSignatureOfThatStatementVerifies() {
        assertTrue(Ed25519Verifier.verify(Hex.decode(PUBLIC_KEY), shared(), Hex.decode(SIGNATURE)));
    }

    @Test
    void noTrailingNewlineFiveLinesAndOnlyLineFeedSeparators() {
        String s = new String(SignedArtifactStatement.build("launcher", "1.0.0", SHA256, 5), StandardCharsets.US_ASCII);

        assertFalse(s.endsWith("\n"));
        assertFalse(s.contains("\r"));
        assertEquals(5, s.split("\n", -1).length);
    }

    @Test
    void everyFieldChangesTheStatementAndBreaksTheSignature() {
        byte[] base = shared();
        byte[][] variants = {
                SignedArtifactStatement.build("launcher", "2.2.0", SHA256, 1234567),
                SignedArtifactStatement.build("bootstrap", "2.2.1", SHA256, 1234567),
                SignedArtifactStatement.build("bootstrap", "2.2.0", "0".repeat(64), 1234567),
                SignedArtifactStatement.build("bootstrap", "2.2.0", SHA256, 1234568),
        };

        for (byte[] variant : variants) {
            assertFalse(java.util.Arrays.equals(base, variant));
            assertFalse(Ed25519Verifier.verify(Hex.decode(PUBLIC_KEY), variant, Hex.decode(SIGNATURE)),
                    "a signature must vouch for exactly one tuple");
        }
    }

    @Test
    void aBootstrapSignatureCannotBeReplayedAsALauncherOrADeb() {
        for (String other : new String[] {"launcher", "deb"}) {
            byte[] statement = SignedArtifactStatement.build(other, "2.2.0", SHA256, 1234567);
            assertFalse(Ed25519Verifier.verify(Hex.decode(PUBLIC_KEY), statement, Hex.decode(SIGNATURE)), other);
        }
    }

    @Test
    void offFormatInputIsRejectedNeverNormalised() {
        for (String type : new String[] {"", "Launcher", "installer", "portable", "launcher ", null}) {
            assertThrows(IllegalArgumentException.class, () -> SignedArtifactStatement.build(type, "1.0.0", SHA256, 5));
        }
        for (String version : new String[] {"", " 1.0", "1.0 ", "1.0\n", "1\n2", "-1", ".1", "1/2", "a".repeat(65), null}) {
            assertThrows(IllegalArgumentException.class, () -> SignedArtifactStatement.build("launcher", version, SHA256, 5), String.valueOf(version));
        }
        for (String sha : new String[] {"", SHA256.toUpperCase(), SHA256.substring(1), SHA256 + "0", "g".repeat(64), SHA256 + "\n", null}) {
            assertThrows(IllegalArgumentException.class, () -> SignedArtifactStatement.build("launcher", "1.0.0", sha, 5), String.valueOf(sha));
        }
        assertThrows(IllegalArgumentException.class, () -> SignedArtifactStatement.build("launcher", "1.0.0", SHA256, -1));
    }

    @Test
    void versionsTheSiteActuallyUsesAreAccepted() {
        for (String version : new String[] {"1.0", "2.0.0", "2.1.2", "9.9.9", "2.4.0-rc.1", "1.0+build5", "1.0~beta"}) {
            SignedArtifactStatement.build("bootstrap", version, SHA256, 1);
        }
    }

    @Test
    void theVerifierNeverThrowsAndSaysNoToMalformedInput() {
        byte[] key = Hex.decode(PUBLIC_KEY);
        byte[] sig = Hex.decode(SIGNATURE);
        byte[] message = shared();

        assertFalse(Ed25519Verifier.verify(null, message, sig));
        assertFalse(Ed25519Verifier.verify(key, null, sig));
        assertFalse(Ed25519Verifier.verify(key, message, null));
        assertFalse(Ed25519Verifier.verify(new byte[31], message, sig));
        assertFalse(Ed25519Verifier.verify(new byte[33], message, sig));
        assertFalse(Ed25519Verifier.verify(key, message, new byte[63]));
        assertFalse(Ed25519Verifier.verify(key, message, new byte[65]));
        assertFalse(Ed25519Verifier.verify(key, message, new byte[64]), "all-zero signature");
        assertFalse(Ed25519Verifier.verify(new byte[32], message, sig), "all-zero key");
        byte[] flipped = sig.clone();
        flipped[10] ^= 1;
        assertFalse(Ed25519Verifier.verify(key, message, flipped));
        byte[] otherKey = key.clone();
        otherKey[0] ^= 1;
        assertFalse(Ed25519Verifier.verify(otherKey, message, sig));
        assertNotEquals(null, message);
        assertArrayEquals(message, shared());
    }
}
