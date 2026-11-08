package mc.snakenest.launcher.modpack;

import java.util.Map;

/**
 * The manifest last successfully applied to an instance directory, persisted
 * locally so a later sync can diff against it. {@code version} is kept only
 * for display (changelog/version number in the UI) - diffing itself is
 * always done by {@code pathToHash}, never by comparing version strings.
 */
public record StoredManifest(String version, Map<String, String> pathToHash) {

    public static StoredManifest of(ModpackManifest manifest) {
        Map<String, String> pathToHash = new java.util.LinkedHashMap<>();
        for (ManifestFile file : manifest.files()) {
            pathToHash.put(file.path(), file.hash());
        }
        return new StoredManifest(manifest.version(), pathToHash);
    }
}
