package mc.snakenest.launcher.android;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.Keep;
import mc.snakenest.launcher.android.snaken3st.GameCrashActivity;

/** Stable native/JNI entry point; the report itself lives in the launcher process. */
@Keep
public class ExitActivity extends GameCrashActivity {
    private static final java.util.concurrent.atomic.AtomicBoolean opened=new java.util.concurrent.atomic.AtomicBoolean();
    public static void showExitMessage(Context context,int code,boolean isSignal) {
        if(code==0&&!isSignal||!opened.compareAndSet(false,true))return;
        try {
        context.startActivity(new Intent(context,ExitActivity.class).putExtra("code",code).putExtra("isSignal",isSignal)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }catch(RuntimeException failure){opened.set(false);/* Never prevent termination of the game process. */}
    }
}
