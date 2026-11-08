package mc.snakenest.launcher.crypto;

import mc.snakenest.launcher.util.AppDirs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinProtectionTest {

    @Test
    void isNotEnabledUntilAPinIsSet(@TempDir Path root) throws Exception {
        PinProtection pin = new PinProtection(new AppDirs(root));

        assertFalse(pin.isEnabled());

        pin.enable("123456".toCharArray());

        assertTrue(pin.isEnabled());
    }

    @Test
    void disableRemovesTheMetadata(@TempDir Path root) throws Exception {
        PinProtection pin = new PinProtection(new AppDirs(root));
        pin.enable("123456".toCharArray());

        pin.disable();

        assertFalse(pin.isEnabled());
    }

    @Test
    void theSamePinDerivesTheSameKeyAcrossRestarts(@TempDir Path root) throws Exception {
        AppDirs dirs = new AppDirs(root);
        SecretKey atSetup = new PinProtection(dirs).enable("4242".toCharArray());

        // A brand new instance, as if the launcher had been restarted - only the on-disk salt links them.
        SecretKey afterRestart = new PinProtection(dirs).deriveExisting("4242".toCharArray());

        assertArrayEquals(atSetup.getEncoded(), afterRestart.getEncoded());
    }

    @Test
    void aDifferentPinDerivesADifferentKey(@TempDir Path root) throws Exception {
        PinProtection pin = new PinProtection(new AppDirs(root));
        SecretKey right = pin.enable("4242".toCharArray());

        SecretKey wrong = pin.deriveExisting("9999".toCharArray());

        // Deriving a wrong PIN deliberately succeeds - only decrypting reveals it (see the class Javadoc).
        assertNotEquals(java.util.Arrays.toString(right.getEncoded()), java.util.Arrays.toString(wrong.getEncoded()));
    }

    @Test
    void twoVaultsWithTheSamePinGetDifferentKeys(@TempDir Path first, @TempDir Path second) throws Exception {
        // i.e. the salt really is random per vault, so one cracked PIN doesn't yield a reusable key.
        SecretKey a = new PinProtection(new AppDirs(first)).enable("123456".toCharArray());
        SecretKey b = new PinProtection(new AppDirs(second)).enable("123456".toCharArray());

        assertNotEquals(java.util.Arrays.toString(a.getEncoded()), java.util.Arrays.toString(b.getEncoded()));
    }

    @Test
    void rejectsAPinThatIsNotDigitsOrIsTheWrongLength() {
        assertTrue(PinProtection.isValidPin("1234".toCharArray()));
        assertTrue(PinProtection.isValidPin("123456789012".toCharArray()));

        assertFalse(PinProtection.isValidPin(null));
        assertFalse(PinProtection.isValidPin("123".toCharArray()), "too short");
        assertFalse(PinProtection.isValidPin("1234567890123".toCharArray()), "too long");
        assertFalse(PinProtection.isValidPin("12a4".toCharArray()), "not digits");
        assertFalse(PinProtection.isValidPin("12 4".toCharArray()), "not digits");
        assertFalse(PinProtection.isValidPin("".toCharArray()));
    }

    @Test
    void enableRefusesAnInvalidPin(@TempDir Path root) {
        PinProtection pin = new PinProtection(new AppDirs(root));

        assertThrows(KeyStorageException.class, () -> pin.enable("12".toCharArray()));
        assertFalse(pin.isEnabled(), "a rejected PIN must not leave the vault half-enabled");
    }

    @Test
    void deriveExistingFailsWhenNoPinIsSet(@TempDir Path root) {
        PinProtection pin = new PinProtection(new AppDirs(root));

        assertThrows(KeyStorageException.class, () -> pin.deriveExisting("1234".toCharArray()));
    }

    @Test
    void deriveExistingFailsOnCorruptMetadata(@TempDir Path root) throws Exception {
        AppDirs dirs = new AppDirs(root);
        PinProtection pin = new PinProtection(dirs);
        pin.enable("1234".toCharArray());
        Files.writeString(dirs.secure().resolve("pin.json"), "{ not json at all");

        assertThrows(KeyStorageException.class, () -> pin.deriveExisting("1234".toCharArray()));
    }

    @Test
    void theMetadataFileNeverContainsThePin(@TempDir Path root) throws Exception {
        AppDirs dirs = new AppDirs(root);
        new PinProtection(dirs).enable("867530".toCharArray());

        String metadata = Files.readString(dirs.secure().resolve("pin.json"));

        assertFalse(metadata.contains("867530"), "the PIN itself must never be written to disk");
    }

    /**
     * The actual end-to-end flow the unlock dialog relies on, combining this class with
     * {@code common}'s {@link EncryptedFileKeyStorage} - each has its own isolated tests
     * elsewhere (this file; {@code common}'s own EncryptedFileKeyStorageTest, which uses a
     * directly-constructed key instead of a real PIN since it can't depend on this class), this
     * is the one place both are exercised together.
     */
    @Test
    void aWrongPinFailsToDecryptATokenEncryptedWithTheRightOne(@TempDir Path root) throws Exception {
        AppDirs dirs = new AppDirs(root);
        PinProtection pin = new PinProtection(dirs);
        SecretKey rightKey = pin.enable("123456".toCharArray());
        Ed25519KeyPair seed = Ed25519KeyPair.fromSeedHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        EncryptedFileKeyStorage.withPin(dirs, rightKey).save(seed);

        SecretKey wrongKey = pin.deriveExisting("999999".toCharArray());

        // This exact failure (GCM tag doesn't verify) is how the unlock dialog detects a bad PIN.
        assertThrows(KeyStorageException.class, () -> EncryptedFileKeyStorage.withPin(dirs, wrongKey).load());
    }
}
