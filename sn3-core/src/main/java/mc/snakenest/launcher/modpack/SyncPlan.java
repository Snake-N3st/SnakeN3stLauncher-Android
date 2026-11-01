package mc.snakenest.launcher.modpack;

import java.util.List;

/**
 * What a sync has to do to reach the target manifest - see {@link ManifestDiffer}.
 *
 * @param toDownload files written straight to disk, replacing whatever is there
 * @param toMerge    files folded into the player's existing copy (see {@link FileMerger})
 * @param toDelete   paths the pack no longer ships, excluding anything the player owns
 */
public record SyncPlan(List<ManifestFile> toDownload, List<MergeAction> toMerge, List<String> toDelete) {

    /** Total number of files this plan will fetch - downloads and merges alike. */
    public int fetchCount() {
        return toDownload.size() + toMerge.size();
    }
}
