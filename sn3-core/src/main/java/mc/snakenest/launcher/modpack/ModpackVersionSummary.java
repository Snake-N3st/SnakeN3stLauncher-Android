package mc.snakenest.launcher.modpack;

import com.google.gson.annotations.SerializedName;

/** One entry of {@code GET /api/modpacks/{slug}/versions} - for changelogs/rollback UI. */
public record ModpackVersionSummary(
        String version,
        String changelog,
        @SerializedName("total_size") long totalSize,
        @SerializedName("mc_version") String mcVersion,
        String loader,
        @SerializedName("loader_version") String loaderVersion,
        @SerializedName("created_at") String createdAt
) {
}
