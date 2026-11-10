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
    public static final String ARGUMENTS = "sn3.jvm.arguments";
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
    private record PreparedLaunch(String version, ModpackSettings settings) { }

    /** Installs the selected pack and its game runtime without starting Minecraft. */
    static void install(LauncherActivity activity, SnakeRepository r, String slug) throws Exception {
        prepare(activity, r, slug, false);
    }

    static void repair(LauncherActivity activity, SnakeRepository r, String slug) throws Exception {
        prepare(activity, r, slug, true);
    }

    static void launch(LauncherActivity activity, SnakeRepository r, String slug) throws Exception {
        PreparedLaunch prepared = r.localOnly(slug) ? prepareLocal(activity, r, slug) : prepare(activity, r, slug, false);
        if (prepared == null) return;
        String version = prepared.version();
        ModpackSettings settings = prepared.settings();
        if (r.pinKey != null) {
            SensitiveFileVault.decryptAll(r.instance(slug), r.pinKey);
            try (var files = Files.walk(r.instance(slug))) {
                if(files.anyMatch(path -> path.getFileName().toString().endsWith(".sn3enc"))) {
                    r.sealAll();
                    throw new IOException("Un fichier protégé n’a pas pu être déchiffré. Le jeu n’a pas été lancé et les fichiers restent protégés.");
                }
            }
        }
        Intent game = new Intent(activity, MainActivity.class);
        game.putExtra("sn3.offline",r.offlineMode);
        game.putExtra(MainActivity.INTENT_MINECRAFT_VERSION, version);
        game.putExtra(SEED, r.session.key().seed()); game.putExtra(MEMORY, settings.memoryMb());
        game.putExtra(ARGUMENTS, SnakeJvmSettings.validateArguments(Objects.toString(settings.extraJvmArgs(), "")));
        if (r.pinKey != null) game.putExtra(PIN, r.pinKey.getEncoded());
        if (Thread.currentThread().isInterrupted()) { r.sealAll(); throw new InterruptedException(); }
        if (activity.isFinishing() || activity.isDestroyed()) { r.sealAll(); throw new IOException("La préparation est terminée. Rouvre le modpack pour jouer."); }
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) { r.work("Protection des fichiers…", r::sealAll); return; }
            activity.startActivity(game);
        });
    }
    private static PreparedLaunch prepare(LauncherActivity activity, SnakeRepository r, String slug, boolean repair) throws Exception {
        if (isRunning(activity)) throw new IllegalStateException("Un jeu est déjà en cours. Ferme-le avant de modifier une instance.");
        r.requireOnline(slug);
        ModpackManifest m = r.sync(slug, repair);
        r.status = "Vérification des mods…"; r.changed(); r.checkBlacklist(slug);
        String username = r.auth().fetchUsername(r.requireSession());
        if (!username.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalStateException("Pseudo Minecraft invalide.");
        r.catalog.cacheUsername(r.requireSession().playerId(), username);
        String version = prepareLoader(activity, r, m);
        if (version == null) return null;
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
        // Effective args travel in the game Intent, so inheriting globals never snapshots them here.
        profile.javaArgs = "";
        SnakePackIcons.applyToProfile(activity, slug, profile);
        LauncherProfiles.mainProfileJson.profiles.put(id, profile); LauncherProfiles.write();
        LauncherPreferences.DEFAULT_PREF.edit().putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, id).commit();
        MinecraftAccount account = new MinecraftAccount(); account.username = username;
        account.profileId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        new File(Tools.DIR_ACCOUNT_NEW).mkdirs();
        String accountFile = "sn3-" + r.session.playerId();
        account.save(new File(Tools.DIR_ACCOUNT_NEW, accountFile + ".json").toString());
        PojavProfile.setCurrentProfile(activity, accountFile);
        r.status = "Installation de Minecraft et du runtime…"; r.changed();
        mc.snakenest.launcher.android.progresskeeper.ProgressKeeper.submitProgress("sn3", -1,
                R.string.sn3_install_minecraft);
        new MinecraftDownloader().installSnakeVersion(activity, metadata, version);
        r.markInstalled(slug, version);
        r.catalog.saveManifest(slug, m);
        return new PreparedLaunch(version, settings);
    }

    /** No authentication, synchronization, metadata lookup or downloader is reachable here. */
    private static PreparedLaunch prepareLocal(LauncherActivity activity, SnakeRepository r, String slug) throws Exception {
        if (isRunning(activity)) throw new IOException("Un jeu est déjà en cours.");
        r.requireSession();
        if (r.protectionEnabled() && r.pinKey == null) throw new IOException("Déverrouille cet appareil avant de jouer hors connexion.");
        if (!r.isInstalled(slug)) throw new IOException("Installation locale incomplète. Reviens en ligne pour installer ou réparer ce modpack.");
        LocalInstallationFiles.requireInstance(r.instance(slug), new LocalManifestStore().load(r.instance(slug))
                .orElseThrow(() -> new IOException("Inventaire local manquant.")), r.modChoices(slug));
        MinecraftProfile profile = r.localProfile(slug);
        String version = profile.lastVersionId;
        requireLocalVersion(profile);
        // Existing accounts are reused verbatim, including the offline UUID used by worlds.
        String alias = "sn3-" + r.requireSession().playerId();
        MinecraftAccount account = MinecraftAccount.load(alias);
        if (account == null) {
            account = new MinecraftAccount(); account.username = r.cachedUsername();
            account.profileId = UUID.nameUUIDFromBytes(("OfflinePlayer:"+account.username).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            account.save(new File(Tools.DIR_ACCOUNT_NEW, alias+".json").toString());
        } else if (!account.isLocal() || !account.username.equals(r.cachedUsername())) throw new IOException("Compte Minecraft local incohérent. Reviens en ligne pour le réparer.");
        String id = UUID.nameUUIDFromBytes(("sn3:"+slug).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        LauncherProfiles.load();
        LauncherPreferences.DEFAULT_PREF.edit().putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,id).commit();
        PojavProfile.setCurrentProfile(activity,alias);
        return new PreparedLaunch(version,r.settings(slug));
    }
    /** Checks the selected version without authentication, synchronization or downloads. */
    static void requireLocalVersion(MinecraftProfile profile) throws IOException {
        String version = profile.lastVersionId;
        JMinecraftVersionList.Version info;
        try { info = Tools.getVersionInfo(version); }
        catch (RuntimeException e) { throw new IOException("Métadonnées Minecraft locales manquantes. Reviens en ligne pour réparer.", e); }
        requireLocalFile(Paths.get(Tools.getClientClasspath(version)), "fichier du jeu");
        if (info.libraries != null) for (var library : info.libraries) {
            if(library.name.startsWith("org.lwjgl") || library.downloads != null && library.downloads.artifact == null) continue;
            if (localLibraryApplies(library.rules)) LocalInstallationFiles.requireFile(Paths.get(Tools.DIR_HOME_LIBRARY),Tools.artifactToPath(library),false);
        }
        int javaVersion = info.javaVersion == null ? 8 : info.javaVersion.majorVersion;
        String runtime;
        try { runtime = Tools.pickRuntime(profile, javaVersion); }
        catch (RuntimeException e) { throw new IOException("Runtime Java local manquant. Reviens en ligne pour terminer l’installation.", e); }
        Path runtimeRoot = mc.snakenest.launcher.android.multirt.MultiRTUtils.getRuntimeHome(runtime).toPath();
        boolean hasVm;
        try (var files = Files.walk(runtimeRoot, 5)) { hasVm = files.anyMatch(path -> path.getFileName().toString().equals("libjvm.so") && Files.isRegularFile(path)); }
        if (!hasVm) throw new IOException("Runtime Java local incomplet. Reviens en ligne pour terminer l’installation.");
        if (info.assetIndex != null) {
            if(info.assetIndex.id==null || !info.assetIndex.id.matches("[A-Za-z0-9_.+-]+")) throw new IOException("Index local invalide.");
            Path index = Paths.get(Tools.ASSETS_PATH,"indexes",info.assetIndex.id+".json");
            requireLocalFile(index,"index des ressources");
            var objects = com.google.gson.JsonParser.parseString(Files.readString(index)).getAsJsonObject().getAsJsonObject("objects");
            for (var entry : objects.entrySet()) {
                String hash = entry.getValue().getAsJsonObject().get("hash").getAsString();
                if (!hash.matches("[a-fA-F0-9]{40}")) throw new IOException("Index de ressources local invalide.");
                requireLocalFile(Paths.get(Tools.ASSETS_PATH,"objects",hash.substring(0,2),hash),"ressource "+entry.getKey());
            }
        }
    }

    private static boolean localLibraryApplies(JMinecraftVersionList.Arguments.ArgValue.ArgRules[] rules) {
        if (rules != null) for (var rule : rules)
            if ("allow".equals(rule.action) && rule.os != null && "osx".equals(rule.os.name)) return false;
        return true;
    }
    private static void requireLocalFile(Path file, String label) throws IOException {
        if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)==0)
            throw new IOException("Fichier local manquant ("+label+"). Reviens en ligne pour réparer ; aucun téléchargement n’a été lancé.");
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
    /** A present empty extra deliberately overrides non-empty global or stale profile arguments. */
    public static String effectiveArguments(Activity activity, String legacyProfileArgs) {
        Intent intent=activity.getIntent();
        if(intent!=null && intent.hasExtra(ARGUMENTS))
            return SnakeJvmSettings.validateArguments(Objects.toString(intent.getStringExtra(ARGUMENTS),""));
        return Tools.isValidString(legacyProfileArgs)?legacyProfileArgs:LauncherPreferences.PREF_CUSTOM_JAVA_ARGS;
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
