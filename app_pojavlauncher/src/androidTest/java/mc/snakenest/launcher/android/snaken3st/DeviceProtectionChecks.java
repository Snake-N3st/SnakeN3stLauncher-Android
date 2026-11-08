package mc.snakenest.launcher.android.snaken3st;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import java.io.IOException;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;

/** Isolated Keystore policy checks: never prompts or modifies the real session/device lock. */
final class DeviceProtectionChecks {
    static void run(Instrumentation test) {
        Bundle result=new Bundle();Path root=null;String alias="sn3-device-fixture-"+UUID.randomUUID();int passed=0;
        try {
            Context target=test.getTargetContext();KeyguardManager keyguard=(KeyguardManager)target.getSystemService(Context.KEYGUARD_SERVICE);
            if(keyguard==null||!keyguard.isDeviceSecure()){result.putBoolean("blocked",true);result.putString("message","Configurer le verrouillage Android pour vérifier les clés authentifiées.");return;}
            root=Files.createTempDirectory(target.getCacheDir().toPath(),"sn3-device-protection-fixture-");Path file=root.resolve("device-lock.bin");
            AndroidDeviceProtection protection=new AndroidDeviceProtection(file,alias);
            check(!protection.enabled());passed++;
            byte[] random=AndroidDeviceProtection.newVaultKey().getEncoded();check(random.length==32&&!Arrays.equals(random,new byte[32]));Arrays.fill(random,(byte)0);passed++;
            protection.prepare();KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);SecretKey wrapping=(SecretKey)store.getKey(alias,null);
            check(wrapping!=null&&wrapping.getEncoded()==null);passed++;
            KeyInfo info=(KeyInfo)SecretKeyFactory.getInstance(wrapping.getAlgorithm(),"AndroidKeyStore").getKeySpec(wrapping,KeyInfo.class);
            check(info.isUserAuthenticationRequired()&&info.getUserAuthenticationValidityDurationSeconds()==AndroidDeviceProtection.AUTH_WINDOW_SECONDS);passed++;
            if(Build.VERSION.SDK_INT>=30)check(info.getUserAuthenticationType()==(KeyProperties.AUTH_BIOMETRIC_STRONG|KeyProperties.AUTH_DEVICE_CREDENTIAL));
            else check(info.getUserAuthenticationValidityDurationSeconds()>0);passed++;
            boolean missing=false;try{protection.unwrap();}catch(IOException expected){missing=true;}check(missing);passed++;
            byte[] corrupt={1,2,3};Files.write(file,corrupt);check(protection.enabled());boolean rejected=false;try{protection.unwrap();}catch(IOException expected){rejected=true;}check(rejected&&Arrays.equals(corrupt,Files.readAllBytes(file)));passed++;
            store.deleteEntry(alias);boolean lost=false;try{protection.prepare();}catch(IOException expected){lost=true;}check(lost&&!store.containsAlias(alias)&&Arrays.equals(corrupt,Files.readAllBytes(file)));passed++;
            protection.clear();check(!protection.enabled()&&!store.containsAlias(alias));passed++;
            // Fixture session bytes only. The normal session wrapping alias may be used by the
            // existing store, but its real file is never read, rewritten or deleted.
            Path sessionRoot=root.resolve("session");Files.createDirectories(sessionRoot);
            Context isolated=new android.content.ContextWrapper(target){@Override public java.io.File getNoBackupFilesDir(){return sessionRoot.toFile();}};
            AndroidSessionStore sessionStore=new AndroidSessionStore(isolated);
            var session=new mc.snakenest.launcher.auth.PlayerSession(mc.snakenest.launcher.crypto.Ed25519KeyPair.fromSeed(new byte[32]),123456,"fixture-client");
            SecretKey vault=AndroidDeviceProtection.newVaultKey(),wrong=AndroidDeviceProtection.newVaultKey();
            sessionStore.save(session,"https://fixture.invalid",null);
            boolean standardRejected=false;try{sessionStore.load("https://fixture.invalid","fixture-client",vault);}catch(Exception expected){standardRejected=true;}check(standardRejected);passed++;
            var recovered=sessionStore.loadAfterDeviceAuthentication("https://fixture.invalid","fixture-client",vault);
            sessionStore.save(recovered,"https://fixture.invalid",vault);
            check(recovered.playerId()==session.playerId()&&sessionStore.load("https://fixture.invalid","fixture-client",vault).playerId()==session.playerId());passed++;
            sessionStore.save(session,"https://fixture.invalid",vault);
            var loaded=sessionStore.loadAfterDeviceAuthentication("https://fixture.invalid","fixture-client",vault);
            check(loaded.playerId()==session.playerId()&&loaded.key().seedHex().equals(session.key().seedHex()));passed++;
            boolean wrongRejected=false;try{sessionStore.loadAfterDeviceAuthentication("https://fixture.invalid","fixture-client",wrong);}catch(Exception expected){wrongRejected=true;}check(wrongRejected);passed++;
            boolean plaintextRejected=false;try{sessionStore.load("https://fixture.invalid","fixture-client",null);}catch(Exception expected){plaintextRejected=true;}check(plaintextRejected);passed++;
            result.putBoolean("success",true);
        }catch(Throwable failure){result.putBoolean("success",false);result.putString("message",failure.getClass().getSimpleName()+": "+failure.getMessage());}
        finally {
            try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(alias))store.deleteEntry(alias);if(root!=null)try(var files=Files.walk(root)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}}
            catch(Exception failure){result.putBoolean("success",false);result.putString("cleanup",failure.getClass().getSimpleName());}
            result.putInt("checks",passed);result.putInt("httpRequests",0);test.finish(result.getBoolean("success")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
    private static void check(boolean value){if(!value)throw new AssertionError("Device protection policy check failed");}
}
