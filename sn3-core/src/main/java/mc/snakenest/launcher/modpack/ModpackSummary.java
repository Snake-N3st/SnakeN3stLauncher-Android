package mc.snakenest.launcher.modpack;

import com.google.gson.annotations.SerializedName;

/** One entry of {@code GET /api/modpacks} - mirrors ModpackListController::index(). */
public record ModpackSummary(
        String slug,
        String name,
        String description,
        String image,
        boolean restricted,
        @SerializedName("latest_version") String latestVersion,
        @SerializedName("total_size") long totalSize
) {
}
