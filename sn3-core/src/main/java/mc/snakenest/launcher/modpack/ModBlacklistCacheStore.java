package mc.snakenest.launcher.modpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import mc.snakenest.launcher.util.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Persists the last-successfully-fetched {@link ModBlacklistEntry} list for an instance, so a
 * launch can still be checked against it if the site is unreachable right when the player hits
 * "Demarrer" - see {@code LauncherApp#checkModBlacklist}. Never a substitute for a fresh fetch when
 * one succeeds; purely a fallback, and an honest one: if there's no cache yet either (first-ever
 * launch offline), the check has nothing to compare against and lets the launch through rather than
 * blocking everything on a single network hiccup.
 */
public final class ModBlacklistCacheStore {

    private static final String FILE_NAME = ".sn3-blacklist-cache.json";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public Optional<List<ModBlacklistEntry>> load(Path instanceDir) throws IOException {
        Path file = instanceDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<ModBlacklistEntry> entries = GSON.fromJson(json, new TypeToken<List<ModBlacklistEntry>>() {
            }.getType());
            return Optional.ofNullable(entries);
        } catch (JsonSyntaxException e) {
            throw new IOException("Corrupt local blacklist cache at " + file, e);
        }
    }

    public void save(Path instanceDir, List<ModBlacklistEntry> entries) throws IOException {
        Path file = instanceDir.resolve(FILE_NAME);
        AtomicFiles.write(file, GSON.toJson(entries).getBytes(StandardCharsets.UTF_8));
    }
}
