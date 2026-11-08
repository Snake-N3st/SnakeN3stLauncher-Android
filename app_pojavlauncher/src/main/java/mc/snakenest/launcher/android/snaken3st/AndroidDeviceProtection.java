package mc.snakenest.launcher.android.snaken3st;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.AtomicFiles;

/**
 * Android-authenticated wrapping of the session/file-vault key. The wrapped key may be an
 * existing PIN-derived key, allowing migration without changing any already encrypted file.
 * Only the wrapping key lives in AndroidKeyStore; it cannot be exported or used without a
 * recent strong biometric or device-credential authentication. The unwrapped vault key stays
 * in memory only for the launcher's unlocked session, as the former PIN-derived key did.
 */
final class AndroidDeviceProtection {
    static final String ALIAS="sn3-device-protection-v1";
    static final int AUTH_WINDOW_SECONDS=30;
    private static final byte[] HEADER={'S','N','3','D','E','V',1,0};
    private static final int IV_BYTES=12,KEY_BYTES=32,TAG_BYTES=16;
    private final Path file;
    private final String alias;

    AndroidDeviceProtection(AppDirs dirs){this(dirs.secure().resolve("device-lock.bin"),ALIAS);}
    /** Fixture-only constructor: callers must use an isolated path and unique key alias. */
    AndroidDeviceProtection(Path file,String alias){this.file=file;this.alias=alias;}

    /** A corrupt, symbolic or non-regular entry still means protected: never silently downgrade. */
    boolean enabled(){return Files.exists(file,LinkOption.NOFOLLOW_LINKS);}

    /** Generate before showing the system prompt, so that prompt authorizes the resulting key. */
    synchronized void prepare()throws Exception {
        safePath();KeyStore store=keyStore();
        if(enabled()) {
            if(!store.containsAlias(alias))throw new IOException("La clé Android de cette protection est indisponible. Les données protégées sont conservées.");
            wrappingKey(store);return;
        }
        // Only an orphan with no protected blob may be replaced. Never regenerate a lost key
        // for an existing blob: that would mask invalidation and strand protected data.
        if(store.containsAlias(alias))store.deleteEntry(alias);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        KeyGenParameterSpec.Builder spec=new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(false);
        if(Build.VERSION.SDK_INT>=30)spec.setUserAuthenticationParameters(AUTH_WINDOW_SECONDS,
            KeyProperties.AUTH_BIOMETRIC_STRONG|KeyProperties.AUTH_DEVICE_CREDENTIAL);
        else spec.setUserAuthenticationValidityDurationSeconds(AUTH_WINDOW_SECONDS);
        generator.init(spec.build());generator.generateKey();
    }

    /** Authentication is enforced by KeyStore at Cipher.init/doFinal, not by a UI callback flag. */
    synchronized void wrap(SecretKey vaultKey)throws Exception {
        safePath();byte[] encoded=vaultKey.getEncoded();
        if(encoded==null)throw new GeneralSecurityException("Clé de coffre non exportable");
        try {
            if(!"AES".equalsIgnoreCase(vaultKey.getAlgorithm())||encoded.length!=KEY_BYTES)throw new GeneralSecurityException("Clé de coffre invalide");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,wrappingKey(keyStore()));cipher.updateAAD(HEADER);
            byte[] encrypted=cipher.doFinal(encoded),iv=cipher.getIV();
            if(iv.length!=IV_BYTES)throw new GeneralSecurityException("Initialisation du chiffrement invalide");
            byte[] blob=new byte[HEADER.length+IV_BYTES+encrypted.length];
            System.arraycopy(HEADER,0,blob,0,HEADER.length);System.arraycopy(iv,0,blob,HEADER.length,IV_BYTES);
            System.arraycopy(encrypted,0,blob,HEADER.length+IV_BYTES,encrypted.length);
            AtomicFiles.write(file,blob);
        }finally{Arrays.fill(encoded,(byte)0);}
    }

    synchronized SecretKey unwrap()throws Exception {
        safePath();
        int expected=HEADER.length+IV_BYTES+KEY_BYTES+TAG_BYTES;
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)!=expected)throw new IOException("Protection Android absente ou endommagée. Les données protégées sont conservées.");
        byte[] blob=Files.readAllBytes(file);
        if(!MessageDigest.isEqual(HEADER,Arrays.copyOf(blob,HEADER.length)))throw new IOException("Format de protection Android inconnu.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,wrappingKey(keyStore()),new GCMParameterSpec(128,Arrays.copyOfRange(blob,HEADER.length,HEADER.length+IV_BYTES)));
        cipher.updateAAD(HEADER);byte[] raw=cipher.doFinal(blob,HEADER.length+IV_BYTES,KEY_BYTES+TAG_BYTES);
        try{if(raw.length!=KEY_BYTES)throw new GeneralSecurityException("Clé de coffre invalide");return new SecretKeySpec(raw,"AES");}
        finally{Arrays.fill(raw,(byte)0);}
    }

    /** Call only after the caller has safely saved an unprotected session and decrypted files. */
    synchronized void clear()throws Exception {
        safePath();Files.deleteIfExists(file);
        // Once the blob is gone, an orphaned non-exportable wrapping key cannot expose the
        // vault key. Do not report a failed disable merely because Keystore cleanup failed.
        try{KeyStore store=keyStore();if(store.containsAlias(alias))store.deleteEntry(alias);}catch(Exception ignored){}
    }
    static SecretKey newVaultKey(){byte[] raw=new byte[KEY_BYTES];new SecureRandom().nextBytes(raw);try{return new SecretKeySpec(raw,"AES");}finally{Arrays.fill(raw,(byte)0);}}
    private void safePath()throws IOException{if(Files.isSymbolicLink(file)||Files.isSymbolicLink(file.getParent()))throw new IOException("Emplacement de protection Android non sûr.");}
    private static KeyStore keyStore()throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);return store;}
    private SecretKey wrappingKey(KeyStore store)throws Exception {
        if(!store.containsAlias(alias))throw new IOException("La clé Android de cette protection est indisponible. Les données protégées sont conservées.");
        Key key=store.getKey(alias,null);if(!(key instanceof SecretKey))throw new GeneralSecurityException("Clé Android invalide");return (SecretKey)key;
    }
}
