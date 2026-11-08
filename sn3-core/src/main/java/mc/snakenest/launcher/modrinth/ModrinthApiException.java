package mc.snakenest.launcher.modrinth;

/** Thrown when a Modrinth API call fails - unreachable, unexpected status, or malformed body. */
public class ModrinthApiException extends Exception {
    public ModrinthApiException(String message) {
        super(message);
    }

    public ModrinthApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
