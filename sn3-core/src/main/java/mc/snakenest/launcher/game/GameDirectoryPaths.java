package mc.snakenest.launcher.game;

import java.io.File;

/** Resolve an explicitly selected instance without rebasing an absolute private-storage path. */
public final class GameDirectoryPaths {
    private GameDirectoryPaths() { }
    public static File resolve(String storageRoot, String configured) {
        File selected = new File(configured);
        return selected.isAbsolute() ? selected : new File(storageRoot, configured);
    }
}
