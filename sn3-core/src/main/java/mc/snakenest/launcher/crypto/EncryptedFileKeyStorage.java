package mc.snakenest.launcher.crypto;

import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.AtomicFiles;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * Stores the player's Ed25519 seed encrypted at rest (AES-256-GCM, via {@link SecretBox}) under
 * {@link AppDirs#secure()}, in one of two modes:
 *
 * <ul>
 *   <li><b>no PIN</b> ({@link #EncryptedFileKeyStorage(AppDirs)}) - the encryption key is
 *   generated once and kept in {@code wrap.key} right next to the encrypted seed, both
 *   permission-restricted via {@link SecureFilePermissions}. Convenient (nothing to type at
 *   startup) but, by construction, anything able to read one file can read the other.
 *   <li><b>PIN</b> ({@link #withPin}) - the key is derived from the player's PIN instead
 *   ({@code launcher.crypto.PinProtection}, a different module, not linkable from here) and
 *   {@code wrap.key} is deleted, so no key material is left on disk at all. Only ever used from
 *   {@code launcher} - {@code bootstrap} never prompts for a PIN, staying minimal (a PIN-protected
 *   vault just reads as "no usable key" to it).
 * </ul>
 *
 * See {@link SecureFilePermissions}'s Javadoc for what the permission tightening does and doesn't
 * protect against in either mode.
 */
public final class EncryptedFileKeyStorage implements KeyStorage {

    private static final int AES_KEY_LENGTH_BYTES = 32;

    private final Path wrappingKeyFile;
    private final Path encryptedKeyFile;
    /** {@code null} means "no PIN" - fall back to the {@link #wrappingKeyFile} on disk. */
    private final SecretKey pinKey;
    private final SecureRandom random = new SecureRandom();

    public EncryptedFileKeyStorage(AppDirs dirs) {
        this(dirs, null);
    }

    /** @param pinKey already derived from the player's PIN by {@code launcher.crypto.PinProtection} - never the PIN itself */
    public static EncryptedFileKeyStorage withPin(AppDirs dirs, SecretKey pinKey) {
        return new EncryptedFileKeyStorage(dirs, pinKey);
    }

    private EncryptedFileKeyStorage(AppDirs dirs, SecretKey pinKey) {
        this.wrappingKeyFile = dirs.secure().resolve("wrap.key");
        this.encryptedKeyFile = dirs.secure().resolve("key.bin");
        this.pinKey = pinKey;
    }

    @Override
    public void save(Ed25519KeyPair keyPair) throws KeyStorageException {
        try {
            byte[] blob = SecretBox.encrypt(encryptionKey(), keyPair.seed());
            AtomicFiles.write(encryptedKeyFile, blob);
            SecureFilePermissions.restrictToOwner(encryptedKeyFile);

            // A PIN-protected vault must never leave a usable wrapping key behind - that file
            // existing at all would defeat the entire point of the PIN (see PinProtection).
            if (pinKey != null) {
                Files.deleteIfExists(wrappingKeyFile);
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new KeyStorageException("Could not save the encrypted key", e);
        }
    }

    /**
     * @return empty when nothing is stored yet
     * @throws KeyStorageException on a wrong PIN, among other failures - a wrong PIN derives a
     *                             different key, whose GCM tag then fails to authenticate (see
     *                             {@link SecretBox}), which is exactly how the unlock dialog
     *                             detects it
     */
    @Override
    public Optional<Ed25519KeyPair> load() throws KeyStorageException {
        if (!Files.isRegularFile(encryptedKeyFile)) {
            return Optional.empty();
        }
        if (pinKey == null && !Files.isRegularFile(wrappingKeyFile)) {
            return Optional.empty();
        }
        try {
            byte[] seed = SecretBox.decrypt(encryptionKey(), Files.readAllBytes(encryptedKeyFile));
            return Optional.of(Ed25519KeyPair.fromSeed(seed));
        } catch (IOException | GeneralSecurityException e) {
            throw new KeyStorageException("Could not read the encrypted key", e);
        }
    }

    @Override
    public void delete() {
        try {
            Files.deleteIfExists(encryptedKeyFile);
        } catch (IOException e) {
            // Best-effort: a leftover encrypted file with no valid session is harmless, not a
            // correctness issue - logout should not fail just because the disk was uncooperative.
        }
    }

    private SecretKey encryptionKey() throws IOException {
        return pinKey != null ? pinKey : loadOrCreateWrappingKey();
    }

    private SecretKey loadOrCreateWrappingKey() throws IOException {
        if (Files.isRegularFile(wrappingKeyFile)) {
            return new SecretKeySpec(Files.readAllBytes(wrappingKeyFile), "AES");
        }
        byte[] key = new byte[AES_KEY_LENGTH_BYTES];
        random.nextBytes(key);
        AtomicFiles.write(wrappingKeyFile, key);
        SecureFilePermissions.restrictToOwner(wrappingKeyFile);
        return new SecretKeySpec(key, "AES");
    }
}
