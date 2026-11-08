package mc.snakenest.launcher.modpack;

/** {@code 403}: the modpack is restricted and the player's role doesn't allow it. */
public final class ModpackAccessDeniedException extends ModpackApiException {

    public ModpackAccessDeniedException(String message) {
        super(message);
    }
}
