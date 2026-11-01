package mc.snakenest.launcher.android.modloaders.modpacks.api;

import android.content.Context;
import android.content.Intent;

import mc.snakenest.launcher.android.LauncherActivity;
import mc.snakenest.launcher.android.R;
import mc.snakenest.launcher.android.Tools;
import mc.snakenest.launcher.android.modloaders.ModloaderDownloadListener;
import mc.snakenest.launcher.android.modloaders.modpacks.ModloaderInstallTracker;
import mc.snakenest.launcher.android.utils.NotificationUtils;

import java.io.File;

public class NotificationDownloadListener implements ModloaderDownloadListener {
    private final Context mContext;
    private final Intent mIntent;
    private final ModLoader mModLoader;
    
    public NotificationDownloadListener(Context context, ModLoader modLoader) {
        mModLoader = modLoader;
        mContext = context.getApplicationContext();
        mIntent = new Intent(mContext, LauncherActivity.class);
    }

    @Override
    public void onDownloadFinished(File downloadedFile) {
        if(mModLoader.requiresGuiInstallation()) {
            ModloaderInstallTracker.saveModLoader(mContext, mModLoader, downloadedFile);
            sendIntentNotification(R.string.modpack_install_notification_success);
        }
    }

    @Override
    public void onDataNotAvailable() {
        sendEmptyNotification(R.string.modpack_install_notification_data_not_available);
    }

    @Override
    public void onDownloadError(Exception e) {
        Tools.showErrorRemote(mContext, R.string.modpack_install_modloader_download_failed, e);
    }

    private void sendIntentNotification(int localeString) {
        Tools.runOnUiThread(() -> NotificationUtils.sendBasicNotification(mContext,
                R.string.modpack_install_notification_title,
                localeString,
                mIntent,
                NotificationUtils.PENDINGINTENT_CODE_DOWNLOAD_SERVICE,
                NotificationUtils.NOTIFICATION_ID_DOWNLOAD_LISTENER
        ));
    }

    private void sendEmptyNotification(int localeString) {
        Tools.runOnUiThread(()->NotificationUtils.sendBasicNotification(mContext,
                R.string.modpack_install_notification_title,
                localeString,
                mIntent,
                NotificationUtils.PENDINGINTENT_CODE_DOWNLOAD_SERVICE,
                NotificationUtils.NOTIFICATION_ID_DOWNLOAD_LISTENER
        ));
    }
}
