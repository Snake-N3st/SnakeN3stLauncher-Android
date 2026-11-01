package mc.snakenest.launcher.android.fragments;

import mc.snakenest.launcher.android.modloaders.FabriclikeUtils;
import mc.snakenest.launcher.android.modloaders.ModloaderListenerProxy;

public class QuiltInstallFragment extends FabriclikeInstallFragment {

    public static final String TAG = "QuiltInstallFragment";
    private static ModloaderListenerProxy sTaskProxy;

    public QuiltInstallFragment() {
        super(FabriclikeUtils.QUILT_UTILS, TAG);
    }
}
