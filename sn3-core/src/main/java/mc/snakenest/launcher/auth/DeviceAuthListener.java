package mc.snakenest.launcher.auth;

/**
 * Callbacks for {@link DeviceAuthService}. Invoked from the service's own
 * background thread, never the Swing EDT - UI code implementing this must
 * hop back with {@code SwingUtilities.invokeLater} itself.
 */
public interface DeviceAuthListener {

    void onStateChanged(DeviceAuthState state);

    /** The login URL couldn't be opened automatically (e.g. no registered browser handler). */
    void onBrowserOpenFailed(String loginUrl);

    void onSucceeded(long playerId);

    void onFailed(String reasonMessage);
}
