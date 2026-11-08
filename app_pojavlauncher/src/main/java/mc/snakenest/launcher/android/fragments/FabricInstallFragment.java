package mc.snakenest.launcher.android.fragments;

import mc.snakenest.launcher.android.modloaders.FabriclikeUtils;
import mc.snakenest.launcher.android.modloaders.ModloaderListenerProxy;

public class FabricInstallFragment extends FabriclikeInstallFragment {

    public static final String TAG = "FabricInstallFragment";

    public FabricInstallFragment() {
        super(FabriclikeUtils.FABRIC_UTILS, TAG);
    }
}
