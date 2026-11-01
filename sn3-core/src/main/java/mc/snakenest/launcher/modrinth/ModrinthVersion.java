package mc.snakenest.launcher.modrinth;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Optional;

/**
 * One published version of a Modrinth project, as returned by both
 * {@code GET /v2/project/{id}/version} (a list) and {@code POST /v2/version_files} (a
 * {@code Map<hash, ModrinthVersion>}) - same shape either way.
 */
public record ModrinthVersion(
        String id,
        @SerializedName("project_id") String projectId,
        @SerializedName("version_number") String versionNumber,
        String name,
        @SerializedName("game_versions") List<String> gameVersions,
        List<String> loaders,
        @SerializedName("version_type") String versionType,
        List<ModrinthVersionFile> files,
        List<ModrinthDependency> dependencies
) {
    public boolean isCompatible(String mcVersion, String loader) {
        boolean versionOk = mcVersion == null || gameVersions.contains(mcVersion);
        boolean loaderOk = loader == null || loaders.contains(loader);
        return versionOk && loaderOk;
    }

    /** The file to actually download - {@code primary}, or the first one if none is marked. */
    public Optional<ModrinthVersionFile> primaryFile() {
        return files.stream().filter(ModrinthVersionFile::primary).findFirst()
                .or(() -> files.stream().findFirst());
    }

    /** Every {@code required} dependency's project id - see {@code ModManagerDialog}'s "install missing dependencies?" prompt. */
    public List<String> requiredDependencyProjectIds() {
        return dependencies == null ? List.of() : dependencies.stream()
                .filter(ModrinthDependency::isRequired)
                .map(ModrinthDependency::projectId)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
