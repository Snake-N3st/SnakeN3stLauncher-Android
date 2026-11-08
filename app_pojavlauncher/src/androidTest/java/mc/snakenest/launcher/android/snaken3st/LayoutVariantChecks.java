package mc.snakenest.launcher.android.snaken3st;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import mc.snakenest.launcher.android.LauncherActivity;
import mc.snakenest.launcher.android.R;

/** Inflate the real resource variants without changing the user's orientation preferences. */
final class LayoutVariantChecks {
    static int run(Instrumentation test)throws Exception {
        var target=test.getTargetContext();
        int theme=target.getPackageManager().getActivityInfo(new ComponentName(target,LauncherActivity.class),0).getThemeResource();
        int[] passed={0};
        for(int orientation:new int[]{Configuration.ORIENTATION_PORTRAIT,Configuration.ORIENTATION_LANDSCAPE}) {
            Configuration config=new Configuration(target.getResources().getConfiguration());config.orientation=orientation;
            var context=new ContextThemeWrapper(target.createConfigurationContext(config),theme);
            test.runOnMainSync(()->{
                var home=LayoutInflater.from(context).inflate(R.layout.fragment_launcher,null,false);
                if(!(home.findViewById(R.id.mc_version_spinner) instanceof com.kdt.mcgui.mcModpackSpinner))
                    throw new AssertionError("Home must use the Snake modpack picker in every orientation");
                passed[0]++;
                var shell=LayoutInflater.from(context).inflate(R.layout.activity_pojav_launcher,null,false);
                if(!(shell.findViewById(R.id.sn3_account_button) instanceof SnakeAccountView))
                    throw new AssertionError("Every orientation must contain the Snake account toolbar");
                passed[0]++;
            });
        }
        return passed[0];
    }
}
