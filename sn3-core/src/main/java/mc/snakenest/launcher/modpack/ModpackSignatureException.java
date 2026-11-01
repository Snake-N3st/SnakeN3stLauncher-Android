package mc.snakenest.launcher.modpack;

/**
 * A manifest whose signature was checked and refused - see {@code ModpackManifestVerifier} for what counts.
 * A {@link ModpackApiException} so every existing caller already shows it like any other failure to fetch a
 * manifest, and nothing is installed from it.
 */
public class ModpackSignatureException extends ModpackApiException {

    public ModpackSignatureException(String message) {
        super(message);
    }
}
