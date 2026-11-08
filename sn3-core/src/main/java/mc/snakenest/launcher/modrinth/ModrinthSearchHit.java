package mc.snakenest.launcher.modrinth;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * One result of {@code GET /v2/search}. Already carries enough to sort compatible-first without a
 * separate per-project call: {@code versions} is every Minecraft version the project supports
 * across all its releases, and the loader shows up as one of {@code categories} (Modrinth doesn't
 * expose a separate "loaders" field on search hits, only on individual versions).
 */
public record ModrinthSearchHit(
        @SerializedName("project_id") String projectId,
        String slug,
        String title,
        String description,
        String author,
        @SerializedName("icon_url") String iconUrl,
        List<String> categories,
        List<String> versions,
        @SerializedName("client_side") String clientSide,
        @SerializedName("server_side") String serverSide,
        long downloads
) {
    public boolean isCompatible(String mcVersion, String loader) {
        boolean versionOk = mcVersion == null || versions.contains(mcVersion);
        boolean loaderOk = loader == null || categories.contains(loader);
        return versionOk && loaderOk;
    }
}
