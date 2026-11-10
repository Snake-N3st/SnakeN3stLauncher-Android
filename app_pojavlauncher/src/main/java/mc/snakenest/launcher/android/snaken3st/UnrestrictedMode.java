package mc.snakenest.launcher.android.snaken3st;

import android.content.Context;
import androidx.preference.PreferenceManager;

/** Opt-in access to Amethyst's personal profiles and Java tools. */
public final class UnrestrictedMode {
    private UnrestrictedMode() { }
    public static boolean enabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("sn3_unrestricted_launcher", false);
    }
}
