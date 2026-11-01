package mc.snakenest.launcher.auth;

/** Checked on purpose: callers must decide how to surface an API failure to the user. */
public final class LauncherApiException extends Exception {

    /** No real HTTP status behind this failure (e.g. a network/parsing error). */
    public static final int NO_STATUS = -1;

    private final int statusCode;

    public LauncherApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public LauncherApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = NO_STATUS;
    }

    /** The HTTP status code that caused this failure, or {@link #NO_STATUS} if there wasn't one. */
    public int statusCode() {
        return statusCode;
    }
}
