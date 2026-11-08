package mc.snakenest.launcher.modpack;

/**
 * What the mod manager knows locally about one file in an instance's {@code mods/} folder -
 * identified primarily by {@code hash} (SHA-1, Modrinth's own hashing algorithm, see
 * {@code util.Sha1}), so a rename doesn't lose the association. {@code modrinthProjectId}/
 * {@code modrinthVersionId} are {@code null} for a manually-imported {@code .jar} that has no known
 * Modrinth match.
 */
public record ModMeta(String hash, String modrinthProjectId, String modrinthVersionId) {
}
