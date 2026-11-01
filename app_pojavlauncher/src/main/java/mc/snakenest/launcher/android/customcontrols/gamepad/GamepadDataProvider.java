package mc.snakenest.launcher.android.customcontrols.gamepad;

import mc.snakenest.launcher.android.GrabListener;

public interface GamepadDataProvider {
    GamepadMap getMenuMap();
    GamepadMap getGameMap();
    boolean isGrabbing();
    void attachGrabListener(GrabListener grabListener);
    void detachGrabListener(GrabListener grabListener);
}
