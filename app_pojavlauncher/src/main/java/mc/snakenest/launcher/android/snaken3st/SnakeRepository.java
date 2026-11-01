package mc.snakenest.launcher.android.snaken3st;

import android.content.*;
import android.os.*;
import mc.snakenest.launcher.android.BuildConfig;
import mc.snakenest.launcher.auth.*;
import mc.snakenest.launcher.crypto.*;
import mc.snakenest.launcher.modpack.*;
import mc.snakenest.launcher.modpack.signing.*;
import mc.snakenest.launcher.net.*;
import mc.snakenest.launcher.news.*;
import mc.snakenest.launcher.util.*;
import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Process-owned operations survive activity rotation; callbacks never retain an Activity. */
final class SnakeRepository {
    private static SnakeRepository instance;
    static synchronized SnakeRepository get(Context c) { if (instance == null) instance = new SnakeRepository(c.getApplicationContext()); return instance; }
    final Context context;
    final SharedPreferences prefs;
    final AppDirs dirs;
    final AndroidSessionStore store;
    final PinProtection pin;
    final HttpJsonClient http = new HttpJsonClient();
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    final Handler main = new Handler(Looper.getMainLooper());
    volatile Runnable observer;
    private final java.util.concurrent.CopyOnWriteArraySet<Runnable> screenObservers = new java.util.concurrent.CopyOnWriteArraySet<>();
    volatile Future<?> task;
    volatile Thread worker;
    volatile boolean cancellationRequested;
    volatile String status = "Prêt";
    volatile String error;
    volatile PlayerSession session;
    volatile boolean sessionRejected;
    volatile PlayerInfo player;
    volatile SecretKey pinKey;
    volatile List<ModpackSummary> packs = List.of();
    volatile List<Post> posts = List.of();
    volatile String loginUrl;
    volatile boolean busy;
    private SnakeRepository(Context c) {
        context = c; prefs = c.getSharedPreferences("sn3", Context.MODE_PRIVATE);
        dirs = new AppDirs(c.getNoBackupFilesDir().toPath().resolve("snake-n3st"));
        store = new AndroidSessionStore(c); pin = new PinProtection(dirs);
    }
    String site() { return BuildConfig.SN3_BASE_URL; }
    String client() { return BuildConfig.SN3_CLIENT_ID; }
    LauncherAuthApiClient auth() { return new LauncherAuthApiClient(http, URI.create(site())); }
    ModpackApiClient api() {
        ModpackSigningKeys roots = ModpackSigningKeys.fromProperties(k -> BuildConfig.SN3_ROOT_KEYS);
        return new ModpackApiClient(http, URI.create(site()), new ModpackManifestVerifier(roots,
                ModpackTrustState.load(dirs.root().resolve(ModpackTrustState.FILE_NAME))));
    }
    void addScreenObserver(Runnable observer) { screenObservers.add(observer); }
    void removeScreenObserver(Runnable observer) { screenObservers.remove(observer); }
    void changed() { main.post(() -> {
        Runnable current = observer; if (current != null) current.run();
        for (Runnable listener : screenObservers) if (listener != current) listener.run();
    }); }
    interface Work { void run() throws Exception; }
    synchronized void work(String label, Work work) {
        if (busy) return;
        busy = true; cancellationRequested = false; status = label; error = null; changed();
        mc.snakenest.launcher.android.progresskeeper.ProgressKeeper.submitProgress("sn3", 0, mc.snakenest.launcher.android.R.string.newdl_starting);
        mc.snakenest.launcher.android.services.ProgressService.startService(context);
        task = executor.submit(() -> {
            worker = Thread.currentThread();
            try {
                if (cancellationRequested) throw new InterruptedException();
                work.run(); if (Thread.currentThread().isInterrupted()) throw new InterruptedException(); status = "Prêt";
            }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); status = "Annulé"; }
            catch (Exception e) { error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); status = "Échec"; }
            finally {
                worker = null; Thread.interrupted(); busy = false; loginUrl = null;
                mc.snakenest.launcher.android.progresskeeper.ProgressKeeper.submitProgress("sn3", -1, -1);
                changed();
            }
        });
    }
    void cancel() { cancellationRequested = true; Thread t = worker; if (t != null) t.interrupt(); }
    void unlock(char[] code) { work("Déverrouillage…", () -> {
        try { SecretKey key = pin.isEnabled() ? pin.deriveExisting(code) : null;
            if (key != null) {
                byte[] check = SecretBox.decrypt(key, Files.readAllBytes(dirs.secure().resolve("pin-check.bin")));
                if (!Arrays.equals(check, new byte[]{83,78,51})) throw new IllegalStateException("PIN incorrect");
            }
            PlayerSession restored = store.load(site(), client(), key);
            pinKey = key; session = restored;
            sealAll(); if (session != null) refresh();
        } finally { Arrays.fill(code, '\0'); }
    }); }
    void login() { work("Connexion en cours…", () -> {
        if (pin.isEnabled() && pinKey == null) throw new IllegalStateException("Déverrouille le PIN avant de te connecter.");
        if (client().isBlank()) throw new IllegalStateException("Ce build ne définit pas sn3.clientId. Recompile avec -Psn3.clientId=…");
        String challenge = auth().requestChallenge(client());
        loginUrl = auth().loginUrl(challenge).toString(); changed();
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(11);
        while (System.nanoTime() < deadline) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            PollResult result = auth().pollChallenge(challenge);
            if (result instanceof PollResult.Success success) {
                PlayerSession received = new PlayerSession(Ed25519KeyPair.fromSeedHex(success.seedHex()), success.playerId(), client());
                store.save(received, site(), pinKey); session = received; refresh(); return;
            }
            if (result instanceof PollResult.NotFound) throw new IllegalStateException("La demande de connexion a expiré.");
            Thread.sleep(2500);
        }
        throw new IllegalStateException("Délai de connexion dépassé.");
    }); }
    void refresh() throws Exception {
        try { player = auth().fetchPlayerInfo(requireSession()); }
        catch (LauncherApiException e) {
            if (e.statusCode() == 401 || e.statusCode() == 403) {
                sessionRejected = true; player = null; packs = List.of();
                throw new java.io.IOException("Session à revalider. Vérifie l’horloge de cet appareil ou reconnecte-toi. La clé locale est conservée.", e);
            }
            throw e;
        }
        sessionRejected = false;
        packs = api().listModpacks(session);
    }
    PlayerSession requireSession() { if (session == null) throw new IllegalStateException("Connecte-toi d’abord."); return session; }
    void logout() { work("Déconnexion…", () -> {
        boolean revoked = true;
        if (session != null) try { auth().revokeKey(session); } catch (Exception e) { revoked = false; }
        sealAll(); store.delete(); session = null; sessionRejected = false; player = null; packs = List.of();
        if (!revoked) throw new java.io.IOException("Session locale supprimée. La révocation distante a échoué : vérifie tes clés sur le site.");
    }); }
    void sealAll() throws Exception {
        if (Sn3GameBridge.isRunning(context) || pinKey == null || !Files.isDirectory(dirs.instances())) return;
        try (var children = Files.list(dirs.instances())) {
            children.filter(Files::isDirectory).forEach(path -> SensitiveFileVault.encryptAll(path, pinKey));
        }
    }
    void beginInstallation(String slug) {
        requireSlug(slug);
        if (!prefs.edit().putBoolean("installing." + slug, true).commit())
            throw new IllegalStateException("Impossible de mémoriser la préparation du modpack.");
    }
    void markInstalled(String slug, String version) {
        requireSlug(slug);
        if (version == null || !version.matches("[A-Za-z0-9_.+-]+")) throw new IllegalArgumentException("Version du jeu invalide");
        if (!prefs.edit().putString("installed." + slug, version).remove("installing." + slug).commit())
            throw new IllegalStateException("Impossible de mémoriser l’installation terminée.");
    }
    boolean isInstalled(String slug) {
        requireSlug(slug);
        if (prefs.getBoolean("installing." + slug, false)) return false;
        Path directory = instance(slug);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) return false;
        try {
            if (new LocalManifestStore().load(directory).isEmpty()) return false;
            // Read a snapshot rather than reloading/mutating Amethyst's global profile map
            // while its background installer may be updating it.
            Path profilesFile = Paths.get(mc.snakenest.launcher.android.Tools.GAME_PROFILES_FILE);
            if (!Files.isRegularFile(profilesFile)) return false;
            var profiles = mc.snakenest.launcher.android.Tools.GLOBAL_GSON.fromJson(Files.readString(profilesFile),
                    mc.snakenest.launcher.android.value.launcherprofiles.MinecraftLauncherProfiles.class);
            String id = UUID.nameUUIDFromBytes(("sn3:" + slug).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            var profile = profiles == null || profiles.profiles == null ? null : profiles.profiles.get(id);
            if (profile == null || profile.lastVersionId == null || !profile.lastVersionId.matches("[A-Za-z0-9_.+-]+")) return false;
            String completed = prefs.getString("installed." + slug, null);
            if (completed != null && !completed.equals(profile.lastVersionId)) return false;
            Path versions = Paths.get(mc.snakenest.launcher.android.Tools.DIR_HOME_VERSION);
            Path json = versions.resolve(profile.lastVersionId).resolve(profile.lastVersionId + ".json");
            Path jar = versions.resolve(profile.lastVersionId).resolve(profile.lastVersionId + ".jar");
            return Files.isRegularFile(json) && Files.size(json) > 0 && Files.isRegularFile(jar) && Files.size(jar) > 0;
        } catch (Exception exception) { return false; }
    }
    void uninstall(String slug) throws Exception {
        requireSlug(slug);
        if (Sn3GameBridge.isRunning(context)) throw new IllegalStateException("Ferme Minecraft avant de désinstaller un modpack.");
        // Leave a failed/interrupted uninstall visibly incomplete, even if its old manifest
        // survives until the last traversal step. Retrying Installer then repairs it.
        beginInstallation(slug);
        InstanceUninstaller.delete(dirs.instances(), slug);
        InstanceUninstaller.delete(dirs.root().resolve("mod-overrides"), slug);
        Path settings = dirs.modpackSettingsFile(slug);
        if (Files.isSymbolicLink(settings.getParent())) throw new java.io.IOException("Dossier de réglages non sûr : lien symbolique.");
        Files.deleteIfExists(settings);
        String id = UUID.nameUUIDFromBytes(("sn3:" + slug).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        mc.snakenest.launcher.android.value.launcherprofiles.LauncherProfiles.load();
        var profileStore = mc.snakenest.launcher.android.value.launcherprofiles.LauncherProfiles.mainProfileJson;
        profileStore.profiles.remove(id);
        if (profileStore.profiles.isEmpty()) profileStore.profiles.put(UUID.randomUUID().toString(),
                mc.snakenest.launcher.android.value.launcherprofiles.MinecraftProfile.getDefaultProfile());
        mc.snakenest.launcher.android.value.launcherprofiles.LauncherProfiles.write();
        var launcherPrefs = mc.snakenest.launcher.android.prefs.LauncherPreferences.DEFAULT_PREF;
        String current = launcherPrefs.getString(mc.snakenest.launcher.android.prefs.LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        if (id.equals(current) || !profileStore.profiles.containsKey(current)) launcherPrefs.edit()
                .putString(mc.snakenest.launcher.android.prefs.LauncherPreferences.PREF_KEY_CURRENT_PROFILE,
                        profileStore.profiles.keySet().iterator().next()).commit();
        SharedPreferences.Editor edit = prefs.edit().remove("version." + slug).remove("installed." + slug).remove("installing." + slug);
        if (slug.equals(prefs.getString("selected-pack", null))) edit.remove("selected-pack");
        for (String key : prefs.getAll().keySet()) if (key.startsWith("mod." + slug + ".")) edit.remove(key);
        if (!edit.commit()) throw new java.io.IOException("Les préférences du modpack n’ont pas pu être nettoyées.");
    }

    ModOverrides modOverrides(String slug) throws java.io.IOException {
        requireSlug(slug); return new ModOverrides(dirs.root().resolve("mod-overrides").resolve(slug));
    }
    Map<String, ModOverrides.Choice> modChoices(String slug) throws java.io.IOException { return modOverrides(slug).choices(); }
    void changeMod(String slug, Path file, boolean remove) throws Exception {
        requireSlug(slug);
        if (Sn3GameBridge.isRunning(context)) throw new IllegalStateException("Ferme Minecraft avant de modifier les mods.");
        if (busy && Thread.currentThread() != worker) throw new IllegalStateException("Une opération est déjà en cours.");
        Path directory = instance(slug).resolve("mods").toAbsolutePath().normalize();
        Path target = file.toAbsolutePath().normalize();
        if (!directory.equals(target.getParent())) throw new java.io.IOException("Le mod n’appartient pas à cette instance.");
        String name = target.getFileName().toString(); String base = ModOverrides.canonicalFilename(name);
        ModpackManifest manifest = manifest(slug);
        boolean managed = manifest.files().stream().anyMatch(item -> item.path().equals("mods/"+base));
        modOverrides(slug).change(instance(slug),name,remove,managed);
        if (remove) {
            var edit = prefs.edit();
            for (var pref : prefs.getAll().entrySet()) if (pref.getKey().startsWith("mod."+slug+".") && base.equals(pref.getValue())) edit.remove(pref.getKey());
            edit.apply();
        }
    }

    ModpackSettings settings(String slug) { return new ModpackSettingsStore(dirs).load(slug, ModpackSettings.defaults()); }
    ModpackManifest manifest(String slug) throws Exception {
        requireSlug(slug); ModpackSettings s = settings(slug);
        return api().getManifest(slug, prefs.getString("version." + slug, null), s.channel(), requireSession());
    }
    Path instance(String slug) { requireSlug(slug); return dirs.instance(slug); }
    static void requireSlug(String slug) { if (slug == null || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw new IllegalArgumentException("Identifiant de modpack invalide"); }
    ModpackManifest sync(String slug, boolean repair) throws Exception {
        ModpackManifest m = manifest(slug); Path dir = instance(slug);
        ModpackSettingsStore settings = new ModpackSettingsStore(dirs);
        if (!Files.exists(dirs.modpackSettingsFile(slug))) settings.save(slug, ModpackSettings.defaults(m.defaultMemoryMb(), m.defaultJvmArgs()));
        if (pinKey != null) SensitiveFileVault.decryptAll(dir, pinKey);
        try {
            if (repair) {
                modOverrides(slug).resetManaged(dir);
                new LocalManifestStore().clear(dir);
            }
            new ModpackSyncEngine(new ModpackFileDownloader(api(), http), new LocalManifestStore())
                    .sync(slug, dir, m, requireSession(), new SyncProgressListener() {
                        private volatile int percent;
                        public void onFileStarted(String path, long size) {
                            status = "Application : " + path;
                            mc.snakenest.launcher.android.progresskeeper.ProgressKeeper.submitProgress("sn3", percent,
                                    mc.snakenest.launcher.android.R.string.sn3_apply_file, path);
                            changed();
                        }
                        public void onDownloadProgress(long receivedBytes, long totalBytes) {
                            percent = totalBytes > 0 ? (int) Math.min(99, 99.0 * receivedBytes / totalBytes) : 0;
                            mc.snakenest.launcher.android.progresskeeper.ProgressKeeper.submitProgress("sn3", percent,
                                    mc.snakenest.launcher.android.R.string.sn3_download_progress,
                                    receivedBytes / 1048576.0, totalBytes / 1048576.0);
                        }
                        public void onFileDone(String path) { }
                        public void onDeleted(String path) { }
                    }, false, modOverrides(slug).ignoredModPaths());
            // The server manifest stays intact/signed. Reapply explicit local mod choices
            // only after its verified sync, and before any game/blacklist check.
            modOverrides(slug).apply(dir);
            mc.snakenest.launcher.android.progresskeeper.ProgressKeeper.submitProgress("sn3", 100,
                    mc.snakenest.launcher.android.R.string.sn3_apply_done);
        } finally { if (pinKey != null) SensitiveFileVault.encryptAll(dir, pinKey); }
        return m;
    }
    void checkBlacklist(String slug) throws Exception {
        List<ModBlacklistEntry> entries = api().getBlacklist(slug, requireSession());
        Path mods = instance(slug).resolve("mods"); if (!Files.isDirectory(mods)) return;
        try (var files = Files.list(mods)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String hash; try (var in = Files.newInputStream(file)) { hash = Sha256.hex(in); }
                for (ModBlacklistEntry entry : entries) if (hash.equalsIgnoreCase(entry.hash()))
                    throw new IllegalStateException("Mod interdit : " + file.getFileName() + " — " + entry.reason());
            }
        }
    }
    void disablePin() { work("Suppression du PIN…", () -> {
        requireSession(); if (pinKey == null) throw new IllegalStateException("Déverrouille d’abord le compte.");
        if (Files.isDirectory(dirs.instances())) try (var children = Files.list(dirs.instances())) {
            for (Path dir : children.filter(Files::isDirectory).toList()) {
                SensitiveFileVault.decryptAll(dir, pinKey);
                try (var files = Files.walk(dir)) {
                    if (files.anyMatch(f -> f.getFileName().toString().endsWith(".sn3enc")))
                        throw new IllegalStateException("Un fichier protégé ne peut pas être déchiffré. Le PIN est conservé.");
                }
            }
        }
        store.save(session, site(), null); pin.disable();
        if (pin.isEnabled()) { store.save(session, site(), pinKey); throw new java.io.IOException("Le PIN n’a pas pu être désactivé."); }
        Files.deleteIfExists(dirs.secure().resolve("pin-check.bin")); pinKey = null;
    }); }
    void setPin(char[] code) { work("Protection du compte…", () -> {
        try {
            requireSession(); if (pin.isEnabled()) throw new IllegalStateException("Le PIN est déjà activé.");
            SecretKey key = pin.enable(code);
            try {
                AtomicFiles.write(dirs.secure().resolve("pin-check.bin"), SecretBox.encrypt(key, new byte[]{83,78,51}));
                store.save(session, site(), key);
            } catch (Exception e) { pin.disable(); Files.deleteIfExists(dirs.secure().resolve("pin-check.bin")); throw e; }
            pinKey = key; sealAll();
        } finally { Arrays.fill(code, '\0'); }
    }); }
}
