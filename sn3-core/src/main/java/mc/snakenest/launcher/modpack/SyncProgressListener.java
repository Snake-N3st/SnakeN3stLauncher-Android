package mc.snakenest.launcher.modpack;

/** Progress callbacks for {@link ModpackSyncEngine#sync}. Invoked on the caller's own thread. */
public interface SyncProgressListener {

    void onFileStarted(String path, long size);

    void onFileDone(String path);

    void onDeleted(String path);
}
