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
    volatile Future<?> task;
    volatile Thread worker;
    volatile boolean cancellationRequested;
    volatile String status = "Prêt";
    volatile String error;
    volatile PlayerSession session;
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
    void changed() { main.post(() -> { Runnable r = observer; if (r != null) r.run(); }); }
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
    void refresh() throws Exception { player = auth().fetchPlayerInfo(requireSession()); packs = api().listModpacks(session); }
    PlayerSession requireSession() { if (session == null) throw new IllegalStateException("Connecte-toi d’abord."); return session; }
    void logout() { work("Déconnexion…", () -> {
        boolean revoked = true;
        if (session != null) try { auth().revokeKey(session); } catch (Exception e) { revoked = false; }
        sealAll(); store.delete(); session = null; player = null; packs = List.of();
        if (!revoked) throw new java.io.IOException("Session locale supprimée. La révocation distante a échoué : vérifie tes clés sur le site.");
    }); }
    void sealAll() throws Exception {
        if (Sn3GameBridge.isRunning(context) || pinKey == null || !Files.isDirectory(dirs.instances())) return;
        try (var children = Files.list(dirs.instances())) {
            children.filter(Files::isDirectory).forEach(path -> SensitiveFileVault.encryptAll(path, pinKey));
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
            if (repair) new LocalManifestStore().clear(dir);
            new ModpackSyncEngine(new ModpackFileDownloader(api(), http), new LocalManifestStore())
                    .sync(slug, dir, m, requireSession(), new SyncProgressListener() {
                        public void onFileStarted(String path, long size) { status = "Téléchargement : " + path; changed(); }
                        public void onFileDone(String path) { }
                        public void onDeleted(String path) { }
                    });
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
