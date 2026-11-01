package mc.snakenest.launcher.crypto;

/** Checked on purpose: callers (the auth flow) must handle a storage failure explicitly. */
public final class KeyStorageException extends Exception {

    public KeyStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public KeyStorageException(String message) {
        super(message);
    }
}
