package mc.snakenest.launcher.modpack;

/** Progress callbacks for {@link ModpackSyncEngine#sync}. Callbacks can run on download worker threads. */
public interface SyncProgressListener {

    /** Actual network payload bytes; shared blobs count once and local cache reads count zero. */
    default void onDownloadProgress(long receivedBytes, long totalBytes) { }

    void onFileStarted(String path, long size);

    void onFileDone(String path);

    void onDeleted(String path);
}
