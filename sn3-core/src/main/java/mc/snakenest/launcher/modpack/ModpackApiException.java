package mc.snakenest.launcher.modpack;

/** Checked on purpose: callers must decide how to surface a modpack API failure. */
public class ModpackApiException extends Exception {

    public ModpackApiException(String message) {
        super(message);
    }
}
