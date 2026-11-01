package mc.snakenest.launcher.android.authenticator.listener;

import mc.snakenest.launcher.android.value.MinecraftAccount;

/** Called when the login is done and the account received. guaranteed to be on the UI Thread */
public interface DoneListener {
    void onLoginDone(MinecraftAccount account);
}
