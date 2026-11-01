package mc.snakenest.launcher.modpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.AtomicFiles;
import mc.snakenest.launcher.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads/saves one modpack's {@link ModpackSettings} as JSON at {@link AppDirs#modpackSettingsFile}. */
public final class ModpackSettingsStore {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final AppDirs dirs;

    public ModpackSettingsStore(AppDirs dirs) {
        this.dirs = dirs;
    }

    /**
     * Returns {@code fallback} if nothing was ever saved, or the file is unreadable - the player's
     * own saved choice, once they have one, always wins over {@code fallback} regardless of what
     * it is (so a curator changing a modpack's declared defaults later never overrides a setting
     * the player already customized).
     *
     * @param fallback typically {@code ModpackSettings.defaults(manifest.defaultMemoryMb(),
     *                 manifest.defaultJvmArgs())} - the modpack's curator-recommended starting
     *                 point, or plain {@link ModpackSettings#defaults()} where no manifest is
     *                 available yet
     */
    public ModpackSettings load(String modpackSlug, ModpackSettings fallback) {
        Path file = dirs.modpackSettingsFile(modpackSlug);
        if (!Files.isRegularFile(file)) {
            return fallback;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            ModpackSettings settings = GSON.fromJson(json, ModpackSettings.class);
            return settings != null ? settings : fallback;
        } catch (IOException | JsonSyntaxException e) {
            Log.warn(ModpackSettingsStore.class, "Could not read settings for " + modpackSlug + ", falling back to defaults: " + e.getMessage());
            return fallback;
        }
    }

    public void save(String modpackSlug, ModpackSettings settings) {
        try {
            AtomicFiles.write(dirs.modpackSettingsFile(modpackSlug), GSON.toJson(settings).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.warn(ModpackSettingsStore.class, "Could not save settings for " + modpackSlug + ": " + e.getMessage());
        }
    }
}
