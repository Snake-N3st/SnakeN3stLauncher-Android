package mc.snakenest.launcher.crypto;

import mc.snakenest.launcher.util.Hex;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seed/public key/signature triple below was generated and verified
 * independently with Python's `cryptography` library (a different Ed25519
 * implementation from the one under test here), the same style of
 * cross-check already used for this exact library elsewhere in the project
 * (see mod/README.md). Seed is the trivial sequence 0x00..0x1f so it's easy
 * to eyeball, not because it's a weak/special key.
 */
class Ed25519KeyPairTest {

    private static final String SEED_HEX = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
    private static final String EXPECTED_PUBLIC_KEY_HEX = "03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8";
    private static final String MESSAGE = "hello ed25519";
    private static final String EXPECTED_SIGNATURE_HEX =
            "23ab925ca3bc11175a57181ba660de2baa6a5ee6725e317228910ba674e464fa72527fed056177fa4303c6f16f5dd73dbf715affb1e0b0b819e7a258d95c9c03";

    @Test
    void derivesThePublicKeyMatchingAnIndependentImplementation() {
        Ed25519KeyPair keyPair = Ed25519KeyPair.fromSeedHex(SEED_HEX);

        assertEquals(EXPECTED_PUBLIC_KEY_HEX, keyPair.publicKeyHex());
    }

    @Test
    void producesASignatureMatchingAnIndependentImplementation() {
        Ed25519KeyPair keyPair = Ed25519KeyPair.fromSeedHex(SEED_HEX);

        byte[] signature = keyPair.sign(MESSAGE.getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(Hex.decode(EXPECTED_SIGNATURE_HEX), signature);
    }

    @Test
    void rejectsASeedThatIsNot32Bytes() {
        assertThrows(IllegalArgumentException.class, () -> Ed25519KeyPair.fromSeed(new byte[16]));
    }

    @Test
    void toStringNeverContainsTheSeed() {
        Ed25519KeyPair keyPair = Ed25519KeyPair.fromSeedHex(SEED_HEX);

        String text = keyPair.toString();

        assertFalse(text.contains(SEED_HEX));
        assertTrue(text.contains(keyPair.publicKeyHex()));
    }
}
