package mc.snakenest.launcher.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-256-GCM of one byte array, with the random IV prepended to the ciphertext so a single
 * opaque blob is all that ever needs storing. Shared by everything encrypted at rest in this
 * launcher (the player's Ed25519 seed in {@link EncryptedFileKeyStorage}, and the sensitive
 * modpack files listed in a {@code .cryptlist}) so the mode/IV-length/tag-length choices exist
 * in exactly one place rather than being re-picked per call site.
 *
 * <p>GCM is authenticated encryption, which is also what makes "was that the right PIN?"
 * answerable at all: {@link #decrypt} throws on a wrong key because the tag doesn't verify, so
 * no separate password-verifier file (an extra thing to keep in sync, and an extra oracle) is
 * needed - see {@code launcher.crypto.PinProtection} (a different module, not linkable from here).
 */
public final class SecretBox {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretBox() {
    }

    /** @return {@code iv || ciphertext+tag} */
    public static byte[] encrypt(SecretKey key, byte[] plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] out = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
        return out;
    }

    /**
     * @param blob exactly what {@link #encrypt} returned
     * @throws GeneralSecurityException on a wrong key, a corrupted/tampered blob, or one too
     *                                  short to even hold an IV - deliberately not distinguished,
     *                                  since a caller can't act differently on any of them
     */
    public static byte[] decrypt(SecretKey key, byte[] blob) throws GeneralSecurityException {
        if (blob.length <= IV_LENGTH) {
            throw new GeneralSecurityException("Encrypted blob is truncated");
        }
        byte[] iv = Arrays.copyOfRange(blob, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(blob, IV_LENGTH, blob.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return cipher.doFinal(ciphertext);
    }
}
