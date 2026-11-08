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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists, per instance, which Modrinth project/version each file in {@code mods/} came from -
 * keyed by filename. Stored next to {@code mods/} (in the instance dir itself), not inside it, so
 * it's never mistaken for a mod or shipped by accident. Used by {@code ui.modpack.ModManagerDialog}
 * to know which of an installed mod's versions is current, and to offer "changer de version"
 * without re-querying Modrinth by hash every time the dialog opens.
 */
public final class ModsMetaStore {

    private static final String FILE_NAME = "mods-meta.json";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public Map<String, ModMeta> load(Path instanceDir) throws IOException {
        Path file = instanceDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, ModMeta> meta = GSON.fromJson(json, new TypeToken<Map<String, ModMeta>>() {
            }.getType());
            return meta != null ? meta : new LinkedHashMap<>();
        } catch (JsonSyntaxException e) {
            throw new IOException("Corrupt mods metadata at " + file, e);
        }
    }

    public void save(Path instanceDir, Map<String, ModMeta> meta) throws IOException {
        Path file = instanceDir.resolve(FILE_NAME);
        AtomicFiles.write(file, GSON.toJson(meta).getBytes(StandardCharsets.UTF_8));
    }
}
