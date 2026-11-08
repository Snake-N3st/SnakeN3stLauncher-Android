package mc.snakenest.launcher.android.authenticator.listener;

/** Called when a step is started, guaranteed to be in the UI Thread */
public interface ProgressListener {
    /** */
    void onLoginProgress(int step);
}