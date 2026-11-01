package mc.snakenest.launcher.modpack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure diff logic (no I/O): decides which files need downloading, merging or deleting to bring an
 * instance directory from its previously-applied manifest to a target one. Diffing is by
 * <b>hash</b>, never by version string or path alone - a file whose content didn't change is never
 * re-downloaded, even across version bumps that touched other files.
 *
 * <p>{@link FileMetas} overrides that per path, because hash-diffing cannot express intent: see
 * each {@link FileMetaRule.Kind} for what it means and why.
 */
public final class ManifestDiffer {

    /**
     * Treated as {@code DEFAULT} when - and only when - a pack ships no {@code .filesmetas} rule
     * of its own for them.
     *
     * <p>These two were hardcoded here long before the convention existed, for a real reason: a
     * curator's uploaded zip is usually a snapshot of their own instance and routinely contains
     * them simply because they ran the game to build the pack. Without the exception, an unrelated
     * update that happened to catch a fresh copy of the curator's {@code options.txt} would
     * overwrite every player's settings on their very next launch, since a sync runs before every
     * launch and not just on explicit updates. Kept so that packs predating {@code .filesmetas}
     * behave exactly as they did; a pack that declares its own rule for these paths wins.
     */
    private static final Set<String> IMPLICITLY_PLAYER_OWNED = Set.of("options.txt", "servers.dat");

    private ManifestDiffer() {
    }

    /**
     * @param previous       the manifest last applied to this instance, if any
     * @param target         the manifest being moved to
     * @param metas          the pack's rules, {@link FileMetas#empty()} when it ships none
     * @param existsOnDisk   whether a given relative path is currently present in the instance -
     *                       a predicate rather than a directory, to keep this class I/O-free and
     *                       directly testable
     * @param resetPlayerFiles {@code true} only when the player explicitly asked a repair to also
     *                       restore their own files (see {@code LauncherApp#repairModpack}) - it
     *                       is the one thing that overrides {@code DEFAULT}
     */
    public static SyncPlan diff(Optional<StoredManifest> previous, ModpackManifest target,
                                FileMetas metas, Predicate<String> existsOnDisk, boolean resetPlayerFiles) {
        Map<String, String> previousPathToHash = previous.map(StoredManifest::pathToHash).orElseGet(Map::of);

        List<ManifestFile> toDownload = new ArrayList<>();
        List<MergeAction> toMerge = new ArrayList<>();

        for (ManifestFile file : target.files()) {
            FileMetaRule rule = ruleFor(metas, file.path());
            boolean contentChanged = !file.hash().equals(previousPathToHash.get(file.path()));

            if (rule == null) {
                if (contentChanged) {
                    toDownload.add(file);
                }
                continue;
            }

            switch (rule.kind()) {
                // Rewritten every time, precisely so a locally edited copy cannot drift: whether
                // the hash moved upstream is irrelevant.
                case OVERRIDE -> toDownload.add(file);

                // Seeded once. "Already there" is judged from the disk, not from the recorded
                // manifest: a repair clears that manifest, and judging by it would make every
                // repair silently restore the file - which is exactly what this rule forbids.
                case DEFAULT -> {
                    if (resetPlayerFiles || !existsOnDisk.test(file.path())) {
                        toDownload.add(file);
                    }
                }

                // Nothing to fold in when the player has no copy, so that case is a plain write;
                // the merger short-circuits it too, this just avoids reading a file that isn't there.
                case MERGE -> {
                    if (!existsOnDisk.test(file.path()) || resetPlayerFiles) {
                        toDownload.add(file);
                    } else if (contentChanged) {
                        toMerge.add(new MergeAction(file, rule.mergeFormat()));
                    }
                }
            }
        }

        Set<String> targetPaths = new HashSet<>();
        for (ManifestFile file : target.files()) {
            targetPaths.add(file.path());
        }

        List<String> toDelete = new ArrayList<>();
        for (String previousPath : previousPathToHash.keySet()) {
            // A pack dropping a player-owned file means it stops seeding it, not that the player's
            // copy should be destroyed.
            FileMetaRule previousRule = ruleFor(metas, previousPath);

            if (previousRule != null && previousRule.protectsExistingFile()) {
                continue;
            }

            if (previousRule == null && IMPLICITLY_PLAYER_OWNED.contains(previousPath)) {
                continue;
            }

            if (!targetPaths.contains(previousPath)) {
                toDelete.add(previousPath);
            }
        }

        return new SyncPlan(toDownload, toMerge, toDelete);
    }

    /**
     * Every path a sync would refuse to overwrite - the pack's {@code DEFAULT}/{@code MERGE} rules
     * plus the two legacy paths it did not override.
     *
     * <p>Used to tell a player exactly which of their files a repair would restore, before asking
     * whether they want that (see {@code LauncherApp#repairModpack}). Pure, like the rest of this
     * class: the caller decides which of these actually exist on disk.
     */
    public static Set<String> playerOwnedPaths(FileMetas metas) {
        Set<String> owned = new java.util.LinkedHashSet<>(metas.playerOwnedPaths());

        for (String legacy : IMPLICITLY_PLAYER_OWNED) {
            if (metas.ruleFor(legacy).isEmpty()) {
                owned.add(legacy);
            }
        }

        return owned;
    }

    /** The pack's own rule, falling back to the legacy implicit one for the two seeded paths. */
    private static FileMetaRule ruleFor(FileMetas metas, String path) {
        return metas.ruleFor(path).orElseGet(() -> IMPLICITLY_PLAYER_OWNED.contains(path)
                ? new FileMetaRule(FileMetaRule.Kind.DEFAULT, null)
                : null);
    }
}
