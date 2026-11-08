package mc.snakenest.launcher.modrinth;

import com.google.gson.annotations.SerializedName;

/**
 * One entry of a {@link ModrinthVersion}'s {@code dependencies} - confirmed for real against
 * Iris's own version list (which really does depend on Sodium): {@code {"version_id": null,
 * "project_id": "AANobbMI", "file_name": null, "dependency_type": "required"}}. {@code
 * dependencyType} is one of {@code required}/{@code optional}/{@code incompatible}/{@code
 * embedded} per Modrinth's own docs - only {@code required} drives
 * {@code ui.modpack.ModManagerDialog}'s "install missing dependencies?" prompt.
 */
public record ModrinthDependency(
        @SerializedName("version_id") String versionId,
        @SerializedName("project_id") String projectId,
        @SerializedName("dependency_type") String dependencyType
) {
    public boolean isRequired() {
        return "required".equals(dependencyType);
    }
}
