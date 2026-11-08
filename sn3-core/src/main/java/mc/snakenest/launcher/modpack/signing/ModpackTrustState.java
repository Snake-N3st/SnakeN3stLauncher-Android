package mc.snakenest.launcher.modpack.signing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mc.snakenest.launcher.util.AtomicFiles;
import mc.snakenest.launcher.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers, per modpack, the highest certificate serial for which a <em>valid</em> signature has been seen -
 * the HSTS half of the modpack policy.
 *
 * <p>Two things follow from one number. A modpack that has a serial recorded has been seen signed, so a
 * manifest that arrives unsigned afterwards is a downgrade of the protection and is refused; before that the
 * warning exists only for the transition. And a certificate with a lower serial than the recorded one is
 * refused: that is what makes a compromised modpack key repairable, since the old key's certificate can no
 * longer be replayed once the new one has been seen.
 *
 * <p>A small JSON file in the data directory, written atomically. Missing or unreadable reads as "nothing seen
 * yet" - the safe direction for availability, and anyone who can edit the player's data directory can replace
 * the launcher outright. Never throws: failing to remember is logged, never a reason to refuse a manifest.
 */
public final class ModpackTrustState {

    public static final String FILE_NAME = "modpack-trust-state.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Data {
        /** slug -> highest certificate serial seen with a valid signature. */
        Map<String, Long> serials = new LinkedHashMap<>();
    }

    private final Path file;
    private final Data data;

    private ModpackTrustState(Path file, Data data) {
        this.file = file;
        this.data = data;
    }

    public static ModpackTrustState inMemory() {
        return new ModpackTrustState(null, new Data());
    }

    public static ModpackTrustState load(Path file) {
        Data data = new Data();

        if (Files.isRegularFile(file)) {
            try {
                Data parsed = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
                if (parsed != null && parsed.serials != null) {
                    data = parsed;
                }
            } catch (IOException | RuntimeException e) {
                Log.warn(ModpackTrustState.class, "Could not read " + file + ", treating it as empty: " + e.getMessage());
            }
        }

        return new ModpackTrustState(file, data);
    }

    public synchronized boolean hasSeenValid(String slug) {
        return data.serials.containsKey(slug);
    }

    /** The highest serial seen for {@code slug}, or 0 if none. */
    public synchronized long highestSerial(String slug) {
        Long serial = data.serials.get(slug);
        return serial == null ? 0 : serial;
    }

    public synchronized void recordValid(String slug, long serial) {
        Long known = data.serials.get(slug);
        if (known != null && known >= serial) {
            return;
        }

        data.serials.put(slug, serial);
        persist();
    }

    private void persist() {
        if (file == null) {
            return;
        }

        try {
            AtomicFiles.write(file, GSON.toJson(data).getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Log.warn(ModpackTrustState.class, "Could not remember that a valid signature was seen (" + file + "): " + e.getMessage());
        }
    }
}
