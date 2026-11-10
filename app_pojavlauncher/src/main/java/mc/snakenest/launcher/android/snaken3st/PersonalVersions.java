package mc.snakenest.launcher.android.snaken3st;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.kdt.mcgui.ProgressLayout;
import mc.snakenest.launcher.android.LauncherActivity;
import mc.snakenest.launcher.android.MainActivity;
import mc.snakenest.launcher.android.PojavProfile;
import mc.snakenest.launcher.android.R;
import mc.snakenest.launcher.android.Tools;
import mc.snakenest.launcher.android.fragments.ProfileEditorFragment;
import mc.snakenest.launcher.android.fragments.ProfileTypeSelectFragment;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;
import mc.snakenest.launcher.android.profiles.ProfileAdapter;
import mc.snakenest.launcher.android.tasks.AsyncMinecraftDownloader;
import mc.snakenest.launcher.android.tasks.MinecraftDownloader;
import mc.snakenest.launcher.android.value.MinecraftAccount;
import mc.snakenest.launcher.android.value.launcherprofiles.LauncherProfiles;
import mc.snakenest.launcher.android.value.launcherprofiles.MinecraftProfile;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Personal profiles reuse Amethyst's profile rows, editor, loaders and Java launcher. */
public final class PersonalVersions {
    private PersonalVersions() { }

    private static boolean available(LauncherActivity host) {
        if(!UnrestrictedMode.enabled(host)) return false;
        if(SnakeRepository.get(host).busy || ProgressLayout.hasProcesses() || Sn3GameBridge.isRunning(host)) {
            Toast.makeText(host, "Termine l’opération ou ferme le jeu avant de continuer.", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    public static void runJar(LauncherActivity host, boolean customArguments) {
        if(available(host)) Tools.installMod(host, customArguments);
    }

    public static void show(LauncherActivity host) {
        if(!available(host)) return;
        LauncherProfiles.load();
        ProfileAdapter nativeRows = new ProfileAdapter(null);
        List<Integer> positions = new ArrayList<>();
        for(int i=0;i<nativeRows.getCount();i++) {
            String id=(String)nativeRows.getItem(i);
            if(!SnakePackIcons.isManagedProfile(host,id,LauncherProfiles.mainProfileJson.profiles.get(id))) positions.add(i);
        }
        positions.sort(Comparator.comparing(index -> {
            MinecraftProfile profile=LauncherProfiles.mainProfileJson.profiles.get((String)nativeRows.getItem(index));
            return profile.name==null?"":profile.name;
        },String.CASE_INSENSITIVE_ORDER));
        BaseAdapter personalRows = new BaseAdapter() {
            @Override public int getCount() { return positions.size(); }
            @Override public Object getItem(int position) { return nativeRows.getItem(positions.get(position)); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position,View recycled,ViewGroup parent) {
                return nativeRows.getView(positions.get(position),recycled,parent);
            }
        };
        AlertDialog.Builder dialog=new AlertDialog.Builder(host).setTitle(R.string.sn3_personal_versions)
                .setNegativeButton("Fermer",null)
                .setPositiveButton("Ajouter une version",(d,w)->{
                    if(available(host)) Tools.swapFragment(host,ProfileTypeSelectFragment.class,ProfileTypeSelectFragment.TAG,null);
                });
        if(positions.isEmpty()) dialog.setMessage("Ajoute un profil pour choisir une version Minecraft ou un loader avec les outils Amethyst.");
        else dialog.setAdapter(personalRows,(d,position)->actions(host,(String)personalRows.getItem(position)));
        dialog.show();
    }

    private static MinecraftProfile personalProfile(LauncherActivity host,String id) throws IOException {
        LauncherProfiles.load();
        MinecraftProfile profile=LauncherProfiles.mainProfileJson.profiles.get(id);
        if(profile==null || SnakePackIcons.isManagedProfile(host,id,profile))
            throw new IOException("Ce profil n’est pas une version personnelle.");
        // A copied personal profile must not reuse a protected pack's private instance folder.
        File folder=Tools.getGameDirPath(profile).getCanonicalFile();
        File protectedRoot=new File(host.getNoBackupFilesDir(),"snake-n3st").getCanonicalFile();
        if(folder.toPath().startsWith(protectedRoot.toPath()))
            throw new IOException("Choisis un dossier personnel distinct des instances Snake N3st.");
        return profile;
    }

    private static void select(String id) {
        LauncherPreferences.DEFAULT_PREF.edit().putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,id).commit();
    }

    private static void actions(LauncherActivity host,String id) {
        if(!available(host)) return;
        try {
            MinecraftProfile profile=personalProfile(host,id);
            new AlertDialog.Builder(host).setTitle(profile.name==null||profile.name.isBlank()?profile.lastVersionId:profile.name)
                    .setItems(new String[]{"Jouer","Modifier","Ouvrir le dossier"},(d,action)->{
                        if(!available(host)) return;
                        if(action==0) launch(host,id);
                        else if(action==1) {
                            select(id);
                            Tools.swapFragment(host,ProfileEditorFragment.class,ProfileEditorFragment.TAG,null);
                        } else Tools.openPath(host,Tools.getGameDirPath(profile),false);
                    }).setNegativeButton("Retour",(d,w)->show(host)).show();
        } catch(Exception failure) { Tools.showError(host,failure); }
    }

    private static void launch(LauncherActivity host,String id) {
        SnakeRepository repo=SnakeRepository.get(host);
        repo.work("Préparation de la version personnelle…",()->{
            if(!UnrestrictedMode.enabled(host)) throw new IOException("Le mode débridé a été désactivé.");
            repo.requireSession();
            boolean offline=repo.offlineMode || !Tools.isOnline(host);
            MinecraftProfile profile=personalProfile(host,id);
            String version=AsyncMinecraftDownloader.normalizeVersionId(profile.lastVersionId);
            if(version==null || !version.matches("[A-Za-z0-9][A-Za-z0-9_.+ -]{0,199}")
                    || MinecraftProfile.LATEST_RELEASE.equals(version) || MinecraftProfile.LATEST_SNAPSHOT.equals(version))
                throw new IOException("Choisis une version installée ou attends le chargement de la liste Minecraft.");
            String alias="sn3-"+repo.requireSession().playerId();
            MinecraftAccount account=MinecraftAccount.load(alias);
            if(account==null) {
                String username=offline?repo.cachedUsername():repo.auth().fetchUsername(repo.requireSession());
                if(!username.matches("[A-Za-z0-9_]{1,16}")) throw new IOException("Pseudo Minecraft invalide.");
                account=new MinecraftAccount(); account.username=username;
                account.profileId=UUID.nameUUIDFromBytes(("OfflinePlayer:"+username).getBytes(StandardCharsets.UTF_8)).toString();
                new File(Tools.DIR_ACCOUNT_NEW).mkdirs();
                account.save(new File(Tools.DIR_ACCOUNT_NEW,alias+".json").toString());
                repo.catalog.cacheUsername(repo.requireSession().playerId(),username);
            }
            PojavProfile.setCurrentProfile(host,alias);
            Tools.switchDemo(false);
            select(id);
            if(offline) {
                MinecraftProfile resolved=new MinecraftProfile(profile); resolved.lastVersionId=version;
                Sn3GameBridge.requireLocalVersion(resolved);
            } else new MinecraftDownloader().installSnakeVersion(host,AsyncMinecraftDownloader.getListedVersion(version),version);
            if(Thread.currentThread().isInterrupted()) throw new InterruptedException();
            host.runOnUiThread(()->{
                if(host.isFinishing() || host.isDestroyed() || !UnrestrictedMode.enabled(host)) return;
                // Only the ordinary Amethyst handoff. No Snake token, seed or protected files.
                Intent game=new Intent(host,MainActivity.class).putExtra(MainActivity.INTENT_MINECRAFT_VERSION,version);
                game.putExtra("sn3.offline",offline);
                host.startActivity(game);
            });
        });
    }
}
