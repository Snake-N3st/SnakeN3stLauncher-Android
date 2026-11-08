package mc.snakenest.launcher.auth;

/** States of the device-flow login, in the order a successful run passes through them. */
public enum DeviceAuthState {
    IDLE,
    REQUESTING_CHALLENGE,
    AWAITING_USER_CONFIRMATION,
    POLLING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
