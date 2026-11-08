package mc.snakenest.launcher.modpack.signing;

/**
 * What {@link ModpackManifestVerifier} concluded.
 *
 * <ul>
 *   <li>{@link Status#NOT_CONFIGURED} - no root key is pinned: nothing can be checked, accept.</li>
 *   <li>{@link Status#VALID} - the chain verifies.</li>
 *   <li>{@link Status#UNSIGNED} - no signature, and this modpack has never been seen signed: the transition.
 *       Accepted with a warning - the project rule is to warn rather than block when a signature cannot be
 *       checked.</li>
 *   <li>{@link Status#REFUSED} - a signature that does not verify, one that disappeared after being seen, or a
 *       certificate older than one already seen. Not accepted.</li>
 * </ul>
 */
public record ManifestVerdict(Status status, String reason) {

    public enum Status { NOT_CONFIGURED, VALID, UNSIGNED, REFUSED }

    static ManifestVerdict notConfigured() {
        return new ManifestVerdict(Status.NOT_CONFIGURED, "no modpack root key is pinned");
    }

    static ManifestVerdict valid() {
        return new ManifestVerdict(Status.VALID, "signature verified");
    }

    static ManifestVerdict unsigned(String reason) {
        return new ManifestVerdict(Status.UNSIGNED, reason);
    }

    static ManifestVerdict refused(String reason) {
        return new ManifestVerdict(Status.REFUSED, reason);
    }

    public boolean accepted() {
        return status != Status.REFUSED;
    }
}
