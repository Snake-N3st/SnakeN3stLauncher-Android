package mc.snakenest.launcher.android.snaken3st;

import android.app.*;
import android.content.*;
import mc.snakenest.launcher.android.*;
import mc.snakenest.launcher.android.LauncherActivity;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;
import mc.snakenest.launcher.android.tasks.*;
import mc.snakenest.launcher.android.modloaders.*;
import mc.snakenest.launcher.android.value.*;
import mc.snakenest.launcher.android.value.launcherprofiles.*;
import mc.snakenest.launcher.android.extra.*;
import mc.snakenest.launcher.android.utils.DownloadUtils;
import mc.snakenest.launcher.modpack.*;
import mc.snakenest.launcher.net.RawResponse;
import mc.snakenest.launcher.util.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** Android VM handoff. Secrets travel only in the explicit, non-exported game Intent. */
public final class Sn3GameBridge {
    private static final String SEED = "sn3.session.seed";
    private static final String PIN = "sn3.session.pinKey";
    private static final String MEMORY = "sn3.memory";
    private Sn3GameBridge() { }
    public static boolean hasSession(Context c) { return SnakeRepository.get(c).session != null; }
    public static boolean isRunning(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes == null) return false;
        for (ActivityManager.RunningAppProcessInfo info : processes)
            if ((c.getPackageName() + ":game").equals(info.processName)) return true;
        return false;
    }
    static void launch(LauncherActivity activity, SnakeRepository r, String slug) throws Exception {
        if (isRunning(activity)) throw new IllegalStateException("Un jeu est déjà en cours. Ferme-le avant de modifier une instance.");
        ModpackManifest m = r.sync(slug, false);
        r.status = "Vérification des mods…"; r.changed(); r.checkBlacklist(slug);
        String username = r.auth().fetchUsername(r.requireSession());
        if (!username.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalStateException("Pseudo Minecraft invalide.");
        String version = prepareLoader(activity, r, m);
        if (version == null) return;
        String json = DownloadUtils.downloadString(LauncherPreferences.PREF_VERSION_REPOS);
        JMinecraftVersionList list = Tools.GLOBAL_GSON.fromJson(json, JMinecraftVersionList.class);
        ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, list);
        JMinecraftVersionList.Version metadata = null;
        for (JMinecraftVersionList.Version v : list.versions) if (version.equals(v.id)) metadata = v;
        ModpackSettings settings = r.settings(slug);
        String id = UUID.nameUUIDFromBytes(("sn3:" + slug).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        LauncherProfiles.load();
        MinecraftProfile existing = LauncherProfiles.mainProfileJson.profiles.get(id);
        MinecraftProfile profile = existing == null ? new MinecraftProfile() : new MinecraftProfile(existing);
        profile.name = "Snake N3st — " + slug;
        profile.lastVersionId = version; profile.gameDir = r.instance(slug).toString();
        profile.javaArgs = settings.extraJvmArgs() == null ? "" : settings.extraJvmArgs();
        if (profile.javaArgs.contains("sn3.token")) throw new IllegalArgumentException("sn3.token est géré par le launcher.");
        LauncherProfiles.mainProfileJson.profiles.put(id, profile); LauncherProfiles.write();
        LauncherPreferences.DEFAULT_PREF.edit().putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, id).commit();
        MinecraftAccount account = new MinecraftAccount(); account.username = username;
        account.profileId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        new File(Tools.DIR_ACCOUNT_NEW).mkdirs();
        String accountFile = "sn3-" + r.session.playerId();
        account.save(new File(Tools.DIR_ACCOUNT_NEW, accountFile + ".json").toString());
        PojavProfile.setCurrentProfile(activity, accountFile);
        r.status = "Installation de Minecraft et du runtime…"; r.changed();
        new MinecraftDownloader().installSnakeVersion(activity, metadata, version);
        if (r.pinKey != null) SensitiveFileVault.decryptAll(r.instance(slug), r.pinKey);
        Intent game = new Intent(activity, MainActivity.class);
        game.putExtra(MainActivity.INTENT_MINECRAFT_VERSION, version);
        game.putExtra(SEED, r.session.key().seed()); game.putExtra(MEMORY, settings.memoryMb());
        if (r.pinKey != null) game.putExtra(PIN, r.pinKey.getEncoded());
        if (Thread.currentThread().isInterrupted()) { r.sealAll(); throw new InterruptedException(); }
        if (activity.isFinishing() || activity.isDestroyed()) { r.sealAll(); throw new IOException("La préparation est terminée. Rouvre le modpack pour jouer."); }
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) { r.work("Protection des fichiers…", r::sealAll); return; }
            activity.startActivity(game);
        });
    }
    private static String prepareLoader(LauncherActivity activity, SnakeRepository r, ModpackManifest m) throws Exception {
        if (!m.mcVersion().matches("[A-Za-z0-9_.+-]+")) throw new IOException("Version Minecraft invalide");
        String loader = m.loader().toLowerCase(Locale.ROOT);
        if ("vanilla".equals(loader)) return m.mcVersion();
        if (m.loaderVersion() == null || !m.loaderVersion().matches("[A-Za-z0-9_.+-]+")) throw new IOException("Version du loader invalide");
        if ("fabric".equals(loader) || "quilt".equals(loader)) {
            FabriclikeUtils utils = "fabric".equals(loader) ? FabriclikeUtils.FABRIC_UTILS : FabriclikeUtils.QUILT_UTILS;
            String json = DownloadUtils.downloadString(utils.createJsonDownloadUrl(m.mcVersion(), m.loaderVersion()));
            String id = com.google.gson.JsonParser.parseString(json).getAsJsonObject().get("id").getAsString();
            if (!id.matches("[A-Za-z0-9_.+-]+")) throw new IOException("Identifiant du loader invalide");
            AtomicFiles.write(Paths.get(Tools.DIR_HOME_VERSION, id, id + ".json"), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return id;
        }
        String id;
        String url;
        if ("forge".equals(loader)) {
            id = m.mcVersion() + "-forge-" + m.loaderVersion();
            url = ForgeUtils.getInstallerUrl(m.mcVersion() + "-" + m.loaderVersion());
        } else if ("neoforge".equals(loader)) {
            id = "neoforge-" + m.loaderVersion();
            url = "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + m.loaderVersion() + "/neoforge-" + m.loaderVersion() + "-installer.jar";
        } else throw new IOException("Loader non pris en charge : " + loader);
        Path profile = Paths.get(Tools.DIR_HOME_VERSION, id, id + ".json");
        if (Files.isRegularFile(profile)) return id;
        File jar = new File(Tools.DIR_CACHE, "sn3-" + loader + "-installer.jar");
        try (RawResponse response = r.http.getRaw(URI.create(url))) {
            if (!response.isSuccess()) throw new IOException("Téléchargement du loader refusé : " + response.statusCode());
            AtomicFiles.writeVerifying(jar.toPath(), response.body());
        }
        Intent intent = new Intent(activity, JavaGUILauncherActivity.class);
        if ("neoforge".equals(loader)) intent.putExtra("javaArgs", "-jar " + jar.getAbsolutePath() + " --install-client").putExtra("openLogOutput", true);
        else ForgeUtils.addAutoInstallArgs(intent, jar, id);
        activity.runOnUiThread(() -> { activity.startActivity(intent); new android.app.AlertDialog.Builder(activity).setTitle("Installation du loader").setMessage("Termine l’installation puis touche Jouer à nouveau.").setPositiveButton("OK",null).show(); });
        return null;
    }
    public static void applyMemory(Activity activity) {
        int memory = activity.getIntent().getIntExtra(MEMORY, 0);
        if (memory > 0) LauncherPreferences.PREF_RAM_ALLOCATION = Math.max(256, Math.min(memory, Tools.getTotalDeviceMemory(activity)));
    }
    public static void addToken(Activity activity, File directory, List<String> args) {
        String privateRoot = activity.getNoBackupFilesDir().toPath().resolve("snake-n3st/instances").toString() + File.separator;
        if (directory == null || !directory.getAbsolutePath().startsWith(privateRoot)) return;
        byte[] seed = activity.getIntent().getByteArrayExtra(SEED);
        if (seed == null || seed.length != 32) throw new SecurityException("Relance ce modpack depuis Snake N3st pour te connecter.");
        args.removeIf(a -> a.startsWith("-Dsn3.token="));
        args.add(1, "-Dsn3.token=" + Hex.encode(seed));
        Arrays.fill(seed, (byte) 0); activity.getIntent().removeExtra(SEED);
    }
    public static void sealAfterExit(Activity activity, File directory) {
        byte[] key = activity.getIntent().getByteArrayExtra(PIN);
        if (key == null || directory == null) return;
        try { SensitiveFileVault.encryptAll(directory.toPath(), new SecretKeySpec(key, "AES")); }
        finally { Arrays.fill(key, (byte) 0); activity.getIntent().removeExtra(PIN); }
    }
}
