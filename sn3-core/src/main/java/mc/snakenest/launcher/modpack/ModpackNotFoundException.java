package mc.snakenest.launcher.modpack;

/** {@code 404}: unknown modpack slug, version, or blob hash. */
public final class ModpackNotFoundException extends ModpackApiException {

    public ModpackNotFoundException(String message) {
        super(message);
    }
}
