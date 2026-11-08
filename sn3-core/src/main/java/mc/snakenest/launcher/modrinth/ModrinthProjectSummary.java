package mc.snakenest.launcher.modrinth;

/**
 * Just enough of {@code GET /v2/project/{id}} to show a missing dependency's real name in
 * {@code ui.modpack.ModManagerDialog}'s "install missing dependencies?" prompt - a
 * {@link ModrinthDependency} only carries the project id, not a human-readable name.
 */
public record ModrinthProjectSummary(String id, String slug, String title, String description,
        @com.google.gson.annotations.SerializedName("icon_url") String iconUrl) {
    public ModrinthProjectSummary(String id,String slug,String title){this(id,slug,title,null,null);}
}
