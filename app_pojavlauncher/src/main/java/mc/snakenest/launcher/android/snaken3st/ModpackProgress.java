package mc.snakenest.launcher.android.snaken3st;

import com.kdt.mcgui.ProgressLayout;
import mc.snakenest.launcher.android.R;
import mc.snakenest.launcher.android.progresskeeper.ProgressKeeper;
import mc.snakenest.launcher.modpack.SyncProgressListener;
import java.util.function.Consumer;

/** Download bytes and file application never overwrite each other's progress records. */
final class ModpackProgress implements SyncProgressListener, AutoCloseable {
    private final Consumer<String> applying;
    private boolean closed;
    ModpackProgress(Consumer<String> applying) { this.applying=applying; }
    @Override public synchronized void onDownloadProgress(long received,long total) {
        if(closed)return;
        int percent=total>0?(int)Math.max(0,Math.min(100,100.0*received/total)):0;
        ProgressKeeper.submitProgress(ProgressLayout.DOWNLOAD_SNAKE_MODPACK,percent,R.string.sn3_download_progress,
                received/1048576.0,total/1048576.0);
    }
    @Override public synchronized void onFileStarted(String path,long size) {
        if(closed)return;
        ProgressKeeper.submitProgress(ProgressLayout.APPLY_SNAKE_MODPACK,-1,R.string.sn3_apply_file,path);
        applying.accept(path);
    }
    @Override public void onFileDone(String path) { }
    @Override public synchronized void onDeleted(String path) {
        if(!closed)ProgressKeeper.submitProgress(ProgressLayout.APPLY_SNAKE_MODPACK,-1,R.string.sn3_apply_delete,path);
    }
    @Override public synchronized void close() {
        if(closed)return;
        closed=true;
        ProgressLayout.clearProgress(ProgressLayout.DOWNLOAD_SNAKE_MODPACK);
        ProgressLayout.clearProgress(ProgressLayout.APPLY_SNAKE_MODPACK);
    }
}
