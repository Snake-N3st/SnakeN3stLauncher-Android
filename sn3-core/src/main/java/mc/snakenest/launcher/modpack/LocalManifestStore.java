package mc.snakenest.launcher.modpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import mc.snakenest.launcher.util.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Persists the last-applied {@link StoredManifest} of an instance directory as a small JSON file. */
public final class LocalManifestStore {

    private static final String FILE_NAME = ".sn3-manifest.json";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public Optional<StoredManifest> load(Path instanceDir) throws IOException {
        Path file = instanceDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return Optional.ofNullable(GSON.fromJson(json, StoredManifest.class));
        } catch (JsonSyntaxException e) {
            throw new IOException("Corrupt local manifest at " + file, e);
        }
    }

    public void save(Path instanceDir, StoredManifest manifest) throws IOException {
        Path file = instanceDir.resolve(FILE_NAME);
        AtomicFiles.write(file, GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Forgets the last-applied manifest, so the next {@code ModpackSyncEngine#sync}
     * treats every file as needing a fresh download+verify - the "Reparer" action's
     * whole mechanism (see {@code ui.modpack.ModpackDetailPage}).
     */
    public void clear(Path instanceDir) throws IOException {
        Files.deleteIfExists(instanceDir.resolve(FILE_NAME));
    }
}
