package mc.snakenest.launcher.android.fragments;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.widget.ExpandableListAdapter;

import androidx.annotation.NonNull;

import mc.snakenest.launcher.android.JavaGUILauncherActivity;
import mc.snakenest.launcher.android.R;
import mc.snakenest.launcher.android.Tools;
import mc.snakenest.launcher.android.modloaders.ForgeDownloadTask;
import mc.snakenest.launcher.android.modloaders.ForgeUtils;
import mc.snakenest.launcher.android.modloaders.ForgeVersionListAdapter;
import mc.snakenest.launcher.android.modloaders.ModloaderListenerProxy;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ForgeInstallFragment extends ModVersionListFragment<List<String>> {
    public static final String TAG = "ForgeInstallFragment";
    public ForgeInstallFragment() {
        super(TAG);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public int getTitleText() {
        return R.string.forge_dl_select_version;
    }

    @Override
    public int getNoDataMsg() {
        return R.string.forge_dl_no_installer;
    }

    @Override
    public List<String> loadVersionList() throws IOException {
        return ForgeUtils.downloadForgeVersions();
    }

    @Override
    public ExpandableListAdapter createAdapter(List<String> versionList, LayoutInflater layoutInflater) {
        return new ForgeVersionListAdapter(versionList, layoutInflater);
    }

    @Override
    public Runnable createDownloadTask(Object selectedVersion, ModloaderListenerProxy listenerProxy) {
        return new ForgeDownloadTask(listenerProxy, (String) selectedVersion);
    }

    @Override
    public void onDownloadFinished(Context context, File downloadedFile) {
        Intent modInstallerStartIntent = new Intent(context, JavaGUILauncherActivity.class);
        ForgeUtils.addAutoInstallArgs(modInstallerStartIntent, downloadedFile, true);
        context.startActivity(modInstallerStartIntent);
    }
}
