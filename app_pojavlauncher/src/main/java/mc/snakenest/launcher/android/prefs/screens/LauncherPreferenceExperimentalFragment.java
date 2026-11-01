package mc.snakenest.launcher.android.prefs.screens;

import android.os.Bundle;

import mc.snakenest.launcher.android.R;

public class LauncherPreferenceExperimentalFragment extends LauncherPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_experimental);
    }
}
