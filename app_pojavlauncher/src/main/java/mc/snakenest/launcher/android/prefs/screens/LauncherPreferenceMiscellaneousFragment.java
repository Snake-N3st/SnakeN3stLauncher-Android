package mc.snakenest.launcher.android.prefs.screens;

import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.preference.Preference;

import mc.snakenest.launcher.android.R;
import mc.snakenest.launcher.android.Tools;
import mc.snakenest.launcher.android.utils.GLInfoUtils;

public class LauncherPreferenceMiscellaneousFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_misc);
        Preference driverPreference = requirePreference("zinkPreferSystemDriver");
        PackageManager packageManager = driverPreference.getContext().getPackageManager();
        boolean supportsTurnip = Tools.checkVulkanSupport(packageManager) && GLInfoUtils.getGlInfo().isAdreno();
        driverPreference.setVisible(supportsTurnip);
    }
}
