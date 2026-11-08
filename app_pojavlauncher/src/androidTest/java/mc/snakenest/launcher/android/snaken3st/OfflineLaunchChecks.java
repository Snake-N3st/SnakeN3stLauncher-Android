package mc.snakenest.launcher.android.snaken3st;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import mc.snakenest.launcher.android.LauncherActivity;
import mc.snakenest.launcher.android.Tools;
import mc.snakenest.launcher.android.PojavProfile;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;
import mc.snakenest.launcher.android.value.MinecraftAccount;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Reads the real installation under a process-local network refusal. Never launches the game. */
public final class OfflineLaunchChecks {
    private final Instrumentation instrumentation;
    public OfflineLaunchChecks(Instrumentation instrumentation) { this.instrumentation=instrumentation; }
    public void run() {
        Bundle result=new Bundle(); AtomicInteger requests=new AtomicInteger();
        ProxySelector previousProxy=ProxySelector.getDefault();
        LauncherActivity activity=null; SnakeRepository repo=null;
        Path catalogFile=null; byte[] catalogBackup=null, seed=null;
        boolean hadCatalog=false, restoreCatalog=false, oldOffline=false, oldAvailable=false, oldRejected=false;
        mc.snakenest.launcher.auth.PlayerSession oldSession=null;
        java.util.List<mc.snakenest.launcher.modpack.ModpackSummary> oldPacks=null;
        Set<String> oldRemoved=null;
        SharedPreferences profilePrefs=null; Map<String,?> profileBackup=null;
        SharedPreferences launcherPrefs=null; String previousProfile=null;
        int passed=0;
        try {
            Context target=instrumentation.getTargetContext();
            Tools.initStorageConstants(target); LauncherPreferences.loadPreferences(target);
            repo=SnakeRepository.get(target);
            if(repo.busy) throw new Blocked("Une opération est déjà en cours.");
            if(repo.protectionEnabled()) throw new Blocked("Protection activée : déverrouillage manuel requis, aucun code n’est lu par ce test.");
            oldOffline=repo.offlineMode; oldAvailable=repo.offlineAvailable; oldRejected=repo.sessionRejected;
            oldSession=repo.session; oldPacks=repo.packs; oldRemoved=repo.removedPacks;
            repo.session=repo.store.load(repo.site(),repo.client(),null);
            if(repo.session==null) throw new Blocked("Aucune session locale disponible.");
            seed=repo.session.key().seed().clone();
            if(MinecraftAccount.load("sn3-"+repo.session.playerId())==null) throw new Blocked("Compte Minecraft local absent : test sans création de compte.");
            // Establish offline before opening the home, so its icon/updater hooks cannot fetch.
            repo.offlineMode=true; RemoteIconLoader.setOffline(true);
            ProxySelector.setDefault(new ProxySelector() {
                @Override public List<Proxy> select(URI uri) {
                    if(uri!=null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                        requests.incrementAndGet(); throw new NetworkRefused();
                    }
                    return List.of(Proxy.NO_PROXY);
                }
                @Override public void connectFailed(URI uri,SocketAddress address,IOException failure) { }
            });
            profilePrefs=PojavProfile.getPrefs(target); profileBackup=new HashMap<>(profilePrefs.getAll());
            launcherPrefs=LauncherPreferences.DEFAULT_PREF;
            previousProfile=launcherPrefs.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,null);
            SnakeRepository tested=repo;
            instrumentation.runOnMainSync(tested::goOffline);
            Set<String> installed=repo.installedSlugs();
            if(installed.isEmpty()) throw new Blocked("Aucun modpack installé localement.");
            String slug=installed.stream().filter(tested::isInstalled).sorted().findFirst()
                    .orElseThrow(() -> new Blocked("Modpack local présent, mais installation incomplète : réparation en ligne requise."));
            check(repo.packs.stream().anyMatch(pack->pack.slug().equals(slug)),"local-catalog"); passed++;
            Intent intent=new Intent(target,LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=(LauncherActivity)instrumentation.startActivitySync(intent); instrumentation.waitForIdleSync();
            Method prepare=Sn3GameBridge.class.getDeclaredMethod("prepareLocal",LauncherActivity.class,SnakeRepository.class,String.class);
            prepare.setAccessible(true);
            Object prepared;
            try { prepared=prepare.invoke(null,activity,repo,slug); }
            catch(InvocationTargetException failure) { throw new AssertionError("Préparation locale refusée : "+failure.getCause().getClass().getSimpleName()+": "+failure.getCause().getMessage()); }
            check(prepared!=null,"prepare-local"); passed++;
            check(Arrays.equals(seed,repo.session.key().seed()),"session-key-preserved"); passed++;
            catalogFile=repo.dirs.root().resolve("local-pack-catalog.json").toAbsolutePath().normalize();
            if(!catalogFile.getParent().equals(repo.dirs.root().toAbsolutePath().normalize()) || Files.isSymbolicLink(catalogFile)) throw new AssertionError("Catalogue hors du dossier privé attendu.");
            hadCatalog=Files.exists(catalogFile,LinkOption.NOFOLLOW_LINKS);
            if(hadCatalog) catalogBackup=Files.readAllBytes(catalogFile);
            restoreCatalog=true;
            repo.catalog.saveRemote(List.of()); repo.restoreLocalCatalog();
            check(repo.removedPacks.contains(slug) && repo.localOnly(slug) && repo.packs.stream().anyMatch(pack->pack.slug().equals(slug)),"removed-pack-local"); passed++;
            check(requests.get()==0,"zero-http-requests");
            result.putBoolean("success",true);
        } catch(Blocked blocked) {
            result.putBoolean("blocked",true); result.putString("message",blocked.getMessage());
        } catch(Throwable failure) {
            result.putBoolean("success",false);
            result.putString("message",failure.getClass().getSimpleName()+": "+Objects.toString(failure.getMessage(),""));
        } finally {
            try {
                if(activity!=null) { LauncherActivity finished=activity; instrumentation.runOnMainSync(finished::finish); instrumentation.waitForIdleSync(); }
                if(restoreCatalog) { if(hadCatalog) Files.write(catalogFile,catalogBackup); else Files.deleteIfExists(catalogFile); }
                if(profilePrefs!=null && profileBackup!=null) restorePrefs(profilePrefs,profileBackup);
                if(launcherPrefs!=null) {
                    var edit=launcherPrefs.edit(); if(previousProfile==null) edit.remove(LauncherPreferences.PREF_KEY_CURRENT_PROFILE); else edit.putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,previousProfile); edit.commit();
                }
                if(repo!=null && oldPacks!=null) {
                    repo.session=oldSession; repo.packs=oldPacks; repo.removedPacks=oldRemoved;
                    repo.offlineMode=oldOffline; repo.offlineAvailable=oldAvailable; repo.sessionRejected=oldRejected;
                    RemoteIconLoader.setOffline(oldOffline);
                }
            } catch(Throwable cleanup) { result.putBoolean("success",false); result.putString("cleanup",cleanup.getClass().getSimpleName()); }
            finally { ProxySelector.setDefault(previousProxy); if(seed!=null) Arrays.fill(seed,(byte)0); }
            result.putInt("checks",passed); result.putInt("httpRequests",requests.get());
            instrumentation.finish(result.getBoolean("success",false)?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
    private void check(boolean condition,String name) {
        if(!condition) throw new AssertionError(name);
        Bundle status=new Bundle(); status.putString("case",name); status.putBoolean("passed",true); instrumentation.sendStatus(0,status);
    }
    private static void restorePrefs(SharedPreferences preferences,Map<String,?> saved) {
        var edit=preferences.edit().clear();
        for(var entry:saved.entrySet()) {
            Object value=entry.getValue(); String key=entry.getKey();
            if(value instanceof String) edit.putString(key,(String)value);
            else if(value instanceof Boolean) edit.putBoolean(key,(Boolean)value);
            else if(value instanceof Integer) edit.putInt(key,(Integer)value);
            else if(value instanceof Long) edit.putLong(key,(Long)value);
            else if(value instanceof Float) edit.putFloat(key,(Float)value);
            else if(value instanceof Set<?>) { Set<String> strings=new HashSet<>(); for(Object item:(Set<?>)value) strings.add((String)item); edit.putStringSet(key,strings); }
        }
        edit.commit();
    }
    private static final class Blocked extends Exception { Blocked(String message) { super(message); } }
    private static final class NetworkRefused extends RuntimeException { NetworkRefused() { super("HTTP(S) interdit par le test hors connexion",new IOException("Network disabled for this process only")); } }
}
