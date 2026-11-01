package mc.snakenest.launcher.crypto;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.AtomicFiles;
import mc.snakenest.launcher.util.Log;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The optional PIN code: turns a short numeric code into the AES key that protects the player's
 * token at rest (and any {@code .cryptlist} modpack files), instead of keeping that key in a file
 * right next to what it decrypts.
 *
 * <p><b>What this actually buys, and what it doesn't.</b> Without a PIN, {@code
 * EncryptedFileKeyStorage} stores its wrapping key in {@code secure/wrap.key} beside the encrypted
 * token - so anything that can read one file can read both, and a "token grabber" that copies the
 * {@code secure/} folder wins. With a PIN enabled, {@code wrap.key} is <b>deleted</b> and the key
 * exists only as a PBKDF2 derivation of a code that's never written to disk, so copying the folder
 * yields nothing directly usable.
 *
 * <p>It is <b>not</b> a defense against an attacker who both grabs the files and is willing to
 * brute-force them: a numeric PIN has very little entropy (10^4 for 4 digits), so an offline
 * attack is entirely feasible for a determined one. {@link #ITERATIONS} (OWASP's current PBKDF2-
 * HMAC-SHA256 recommendation, ~0.6s per guess on a typical desktop) is what makes that cost real
 * rather than instant, and is the honest ceiling of this feature: it raises the bar against the
 * commodity "copy the folder and replay the token" grabber this is aimed at, not against targeted
 * cracking. Nor does it protect the token *while the game runs* - it's passed to the game as
 * {@code -Dsn3.token=...} either way (see {@code game.LaunchRequest}).
 *
 * <p>The salt and iteration count live in {@code secure/pin.json} in the clear, which is normal
 * (neither is secret) and is what lets {@link #ITERATIONS} be raised later without stranding
 * already-encrypted data.
 */
public final class PinProtection {

    /** OWASP's 2023 recommendation for PBKDF2-HMAC-SHA256. Persisted per-vault, so raising it later doesn't break existing files. */
    private static final int ITERATIONS = 600_000;

    private static final int SALT_LENGTH_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;

    /** Short enough to stay a "PIN", long enough that a player who wants 8-10 digits can have them. */
    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 12;

    private static final Gson GSON = new Gson();

    private final Path metadataFile;
    private final SecureRandom random = new SecureRandom();

    public PinProtection(AppDirs dirs) {
        this.metadataFile = dirs.secure().resolve("pin.json");
    }

    /** Whether the player has turned the PIN on - i.e. whether the launcher must ask for it at startup. */
    public boolean isEnabled() {
        return Files.isRegularFile(metadataFile);
    }

    /** Digits only, {@value #MIN_LENGTH}-{@value #MAX_LENGTH} of them - enforced here rather than only in the dialog, so every caller gets the same rule. */
    public static boolean isValidPin(char[] pin) {
        if (pin == null || pin.length < MIN_LENGTH || pin.length > MAX_LENGTH) {
            return false;
        }
        for (char c : pin) {
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a fresh salt and derives the key for a brand new PIN. The caller is responsible for
     * then re-encrypting whatever the old key protected (see {@code KeyStorage#save}) - this class
     * deliberately doesn't touch the token itself.
     */
    public SecretKey enable(char[] pin) throws KeyStorageException {
        if (!isValidPin(pin)) {
            throw new KeyStorageException("A PIN must be " + MIN_LENGTH + " to " + MAX_LENGTH + " digits");
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        random.nextBytes(salt);

        try {
            SecretKey key = derive(pin, salt, ITERATIONS);
            Metadata metadata = new Metadata(Base64.getEncoder().encodeToString(salt), ITERATIONS);
            AtomicFiles.write(metadataFile, GSON.toJson(metadata).getBytes(StandardCharsets.UTF_8));
            return key;
        } catch (IOException | GeneralSecurityException e) {
            throw new KeyStorageException("Could not enable the PIN", e);
        }
    }

    /**
     * Derives the key for an existing PIN. Succeeding here does <b>not</b> mean the PIN was
     * correct - a wrong PIN derives a different key just as happily, and only fails later when
     * {@link SecretBox#decrypt} can't authenticate the token (see this class's own Javadoc for why
     * that's deliberate).
     *
     * @throws KeyStorageException if the PIN isn't enabled at all, or its metadata is unreadable
     */
    public SecretKey deriveExisting(char[] pin) throws KeyStorageException {
        Metadata metadata = readMetadata();
        try {
            return derive(pin, Base64.getDecoder().decode(metadata.salt), metadata.iterations);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new KeyStorageException("Could not derive the key from the PIN", e);
        }
    }

    /** Removes the PIN metadata - the caller must re-encrypt whatever it protected under a non-PIN key first. */
    public void disable() {
        try {
            Files.deleteIfExists(metadataFile);
        } catch (IOException e) {
            Log.warn(PinProtection.class, "Could not remove " + metadataFile.getFileName() + ": " + e.getMessage());
        }
    }

    private Metadata readMetadata() throws KeyStorageException {
        try {
            Metadata metadata = GSON.fromJson(Files.readString(metadataFile, StandardCharsets.UTF_8), Metadata.class);
            if (metadata == null || metadata.salt == null || metadata.salt.isBlank() || metadata.iterations <= 0) {
                throw new KeyStorageException("PIN metadata is missing or malformed");
            }
            return metadata;
        } catch (IOException | JsonSyntaxException e) {
            throw new KeyStorageException("Could not read the PIN metadata", e);
        }
    }

    private static SecretKey derive(char[] pin, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, iterations, KEY_LENGTH_BITS);
        try {
            byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(derived, "AES");
        } finally {
            // Clears the copy PBEKeySpec made of the PIN; the caller's own array is its own
            // responsibility (see PinUnlockDialog, which zeroes it after use).
            spec.clearPassword();
        }
    }

    /** Neither field is secret - see this class's Javadoc. */
    private record Metadata(String salt, int iterations) {
    }
}
