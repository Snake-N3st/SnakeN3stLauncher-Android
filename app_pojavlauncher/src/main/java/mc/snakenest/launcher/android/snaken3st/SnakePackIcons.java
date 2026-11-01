package mc.snakenest.launcher.android.snaken3st;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.snakenest.launcher.android.Tools;
import mc.snakenest.launcher.android.profiles.ProfileIconCache;
import mc.snakenest.launcher.android.value.launcherprofiles.LauncherProfiles;
import mc.snakenest.launcher.android.value.launcherprofiles.MinecraftLauncherProfiles;
import mc.snakenest.launcher.android.value.launcherprofiles.MinecraftProfile;
import mc.snakenest.launcher.modpack.ModpackSummary;
import mc.snakenest.launcher.util.AtomicFiles;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Native Amethyst data-URI icons, including a catalogue cache beside launcher_profiles.json. */
public final class SnakePackIcons {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Entry> ICONS = new HashMap<>();
    private static boolean loaded;
    private record Entry(String source, String icon) { }
    private SnakePackIcons() { }

    private static String key(String slug) {
        SnakeRepository.requireSlug(slug);
        return UUID.nameUUIDFromBytes(("sn3:" + slug).getBytes(StandardCharsets.UTF_8)).toString();
    }
    private static Path cacheFile() {
        return Paths.get(Tools.GAME_PROFILES_FILE).resolveSibling("launcher_sn3_pack_icons.json");
    }
    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path file = cacheFile();
            if (!Files.isRegularFile(file) || Files.size(file) > 16 * 1024 * 1024) return;
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (var item : json.entrySet()) {
                try {
                    JsonObject entry = item.getValue().getAsJsonObject();
                    String source = entry.get("source").getAsString();
                    String icon = entry.has("icon") ? entry.get("icon").getAsString() : null;
                    if (icon == null || icon.startsWith("data:image/") && icon.length() <= 1024 * 1024)
                        ICONS.put(item.getKey(), new Entry(source, icon));
                } catch (RuntimeException ignored) { }
            }
        } catch (Exception ignored) { /* A failed optional cache must never stop the launcher. */ }
    }
    private static void persistCache() {
        JsonObject json = new JsonObject();
        for (var item : ICONS.entrySet()) {
            JsonObject entry = new JsonObject(); entry.addProperty("source", item.getValue().source());
            if (item.getValue().icon() != null) entry.addProperty("icon", item.getValue().icon());
            json.add(item.getKey(), entry);
        }
        try { AtomicFiles.write(cacheFile(), json.toString().getBytes(StandardCharsets.UTF_8)); }
        catch (Exception ignored) { }
    }
    private static String source(ModpackSummary pack, String origin) {
        if (pack.image() == null || pack.image().isBlank()) return "removed";
        try {
            URI url = URI.create(origin).resolve(pack.image().trim());
            if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null || url.getRawUserInfo() != null)
                return "removed";
            return url.toASCIIString() + "\n" + Objects.toString(pack.latestVersion(), "");
        } catch (RuntimeException ignored) { return "removed"; }
    }

    /** Cached results are delivered immediately on main; unchanged cached sources need no network. */
    public static void load(Context context, ModpackSummary pack, String origin, Consumer<Drawable> callback) {
        Context app = context.getApplicationContext();
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> load(app, pack, origin, callback)); return;
        }
        String id = key(pack.slug()), source = source(pack, origin);
        final Entry entry;
        synchronized (SnakePackIcons.class) {
            ensureLoaded();
            Entry previous = ICONS.get(id);
            String icon = null;
            if (previous != null && previous.source().equals(source)) icon = previous.icon();
            entry = new Entry(source, icon);
            if (!entry.equals(previous)) {
                ICONS.put(id, entry); persistCache(); ProfileIconCache.dropIcon(id);
                writeProfileIcon(id, icon);
                // A changed version may reuse the same URL; discard the remote RAM cache too.
                if (previous != null && !"removed".equals(source)) RemoteIconLoader.invalidate(pack.image(), origin);
            }
        }
        if (entry.icon() != null) {
            try { callback.accept(ProfileIconCache.fetchIcon(app.getResources(), id, entry.icon())); return; }
            catch (RuntimeException ignored) { ProfileIconCache.dropIcon(id); }
        }
        if ("removed".equals(source)) { callback.accept(null); return; }
        RemoteIconLoader.load(app, pack.image(), origin, drawable -> {
            synchronized (SnakePackIcons.class) {
                Entry current = ICONS.get(id);
                if (current == null || !current.source().equals(source)) return; // Outdated download after catalogue refresh.
                String encoded = encode(drawable);
                if (encoded != null) {
                    ICONS.put(id, new Entry(source, encoded)); persistCache();
                    ProfileIconCache.dropIcon(id); writeProfileIcon(id, encoded);
                    callback.accept(ProfileIconCache.fetchIcon(app.getResources(), id, encoded));
                } else callback.accept(null);
            }
        });
    }

    /** Warms/invalidate icons after catalogue refresh, without creating game profiles. */
    public static void refresh(Context context, List<ModpackSummary> packs, String origin) {
        for (ModpackSummary pack : List.copyOf(packs)) load(context, pack, origin, ignored -> { });
    }

    /** Called when creating/updating a real Snake profile; never creates a catalogue stub. */
    public static synchronized void applyToProfile(Context context, String slug, MinecraftProfile profile) {
        ensureLoaded(); Entry entry = ICONS.get(key(slug));
        if (entry != null) profile.icon = entry.icon();
    }

    /** Catalogue identity, with a strictly bounded instance-directory fallback for offline profiles. */
    public static synchronized boolean isManagedProfile(Context context, String profileKey, MinecraftProfile profile) {
        if (profileKey == null) return false;
        ensureLoaded();
        if (ICONS.containsKey(profileKey)) return true;
        for (ModpackSummary pack : SnakeRepository.get(context).packs) if (key(pack.slug()).equals(profileKey)) return true;
        if (profile == null || profile.gameDir == null) return false;
        try {
            Path root = context.getNoBackupFilesDir().toPath().resolve("snake-n3st/instances").toAbsolutePath().normalize();
            Path directory = Paths.get(profile.gameDir).toAbsolutePath().normalize();
            if (!root.equals(directory.getParent())) return false;
            return key(directory.getFileName().toString()).equals(profileKey);
        } catch (RuntimeException ignored) { return false; }
    }

    private static String encode(Drawable drawable) {
        if (!(drawable instanceof BitmapDrawable)) return null;
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        if (bitmap == null || bitmap.getWidth() > 512 || bitmap.getHeight() > 512) return null;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes) || bytes.size() > 700 * 1024) return null;
        return "data:image/png;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
    }
    private static MinecraftLauncherProfiles readProfiles() throws Exception {
        Path file = Paths.get(Tools.GAME_PROFILES_FILE);
        return Files.isRegularFile(file) ? Tools.GLOBAL_GSON.fromJson(Files.readString(file), MinecraftLauncherProfiles.class) : null;
    }
    private static void writeProfileIcon(String id, String icon) {
        // Patch a fresh disk snapshot, avoiding a stale global profile-map rewrite.
        synchronized (LauncherProfiles.class) {
            try {
                MinecraftLauncherProfiles disk = readProfiles();
                if (disk == null || disk.profiles == null || !disk.profiles.containsKey(id)) return;
                MinecraftProfile profile = disk.profiles.get(id);
                if (!Objects.equals(profile.icon, icon)) {
                    profile.icon = icon;
                    AtomicFiles.write(Paths.get(Tools.GAME_PROFILES_FILE), disk.toJson().getBytes(StandardCharsets.UTF_8));
                }
                if (LauncherProfiles.mainProfileJson != null && LauncherProfiles.mainProfileJson.profiles != null) {
                    MinecraftProfile live = LauncherProfiles.mainProfileJson.profiles.get(id);
                    if (live != null) live.icon = icon;
                }
            } catch (Exception ignored) { }
        }
    }
}
