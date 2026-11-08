package mc.snakenest.launcher.android.snaken3st;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import com.google.gson.Gson;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.crypto.*;
import mc.snakenest.launcher.util.AtomicFiles;

/** Non-exportable Android Keystore wrapping key; token never enters preferences or profiles. */
final class AndroidSessionStore {
    private static final String ALIAS = "sn3-session-v1";
    private final Path file;
    private final Gson gson = new Gson();
    private record Stored(long playerId, String clientId, String site, String seed) { }
    AndroidSessionStore(Context context) { file = context.getNoBackupFilesDir().toPath().resolve("sn3-session.bin"); }
    boolean exists() { return Files.isRegularFile(file); }
    private SecretKey wrappingKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            return gen.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }
    void save(PlayerSession session, String site, SecretKey pinKey) throws Exception {
        byte[] data = gson.toJson(new Stored(session.playerId(), session.clientId(), site, session.key().seedHex()))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (pinKey != null) data = SecretBox.encrypt(pinKey, data);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, wrappingKey());
        byte[] encrypted = c.doFinal(data); byte[] blob = new byte[12 + encrypted.length];
        System.arraycopy(c.getIV(), 0, blob, 0, 12); System.arraycopy(encrypted, 0, blob, 12, encrypted.length);
        AtomicFiles.write(file, blob); Arrays.fill(data, (byte) 0);
    }
    PlayerSession load(String site,String client,SecretKey pinKey)throws Exception{return load(site,client,pinKey,false);}
    /** Only after authenticated AndroidDeviceProtection.unwrap: recover interrupted activation. */
    PlayerSession loadAfterDeviceAuthentication(String site,String client,SecretKey key)throws Exception{return load(site,client,key,true);}
    private PlayerSession load(String site, String client, SecretKey pinKey,boolean recoverActivation) throws Exception {
        if (!exists()) return null;
        byte[] blob = Files.readAllBytes(file);
        if (blob.length < 28) throw new java.io.IOException("Session endommagée");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, wrappingKey(), new GCMParameterSpec(128, Arrays.copyOf(blob, 12)));
        byte[] data = c.doFinal(blob, 12, blob.length - 12);
        if(pinKey!=null)try{data=SecretBox.decrypt(pinKey,data);}
        catch(java.security.GeneralSecurityException failure){if(!recoverActivation)throw failure;
            // Only a valid outer-authenticated, site/client-matching plaintext session can recover.
            // A protected session with a wrong key still fails JSON/schema validation below.
        }
        Stored stored;
        try{stored=gson.fromJson(new String(data,java.nio.charset.StandardCharsets.UTF_8),Stored.class);}
        catch(RuntimeException invalid){throw new java.io.IOException("Session invalide",invalid);}
        finally{Arrays.fill(data,(byte)0);}
        if(stored==null||stored.seed()==null||stored.playerId()<=0)throw new java.io.IOException("Session invalide");
        if (!site.equals(stored.site()) || !client.equals(stored.clientId())) throw new java.io.IOException("La session appartient à un autre site/client");
        return new PlayerSession(Ed25519KeyPair.fromSeedHex(stored.seed()), stored.playerId(), client);
    }
    void delete() throws Exception { Files.deleteIfExists(file); }
}
