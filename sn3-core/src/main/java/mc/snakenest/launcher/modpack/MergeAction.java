package mc.snakenest.launcher.modpack;

/**
 * A file the pack wants to change without flattening the player's copy - see
 * {@link FileMetaRule.Kind#MERGE}. Carried separately from a plain download in {@link SyncPlan}
 * because the two are applied differently: a download replaces the file, a merge reads what is
 * already there first.
 */
public record MergeAction(ManifestFile file, FileMetaRule.MergeFormat format) {
}
