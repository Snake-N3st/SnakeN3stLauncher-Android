package mc.snakenest.launcher.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The exact bytes the site signs when it publishes an artifact, rebuilt here from values the client already
 * has - mirror of {@code SigningStatement.php}, and kept byte-exact with it by a shared test vector.
 *
 * <pre>
 * sn3.artifact.v1
 * type=bootstrap
 * version=2.2.0
 * sha256=bb0ebd16...
 * size=1234567
 * </pre>
 *
 * Lines joined with {@code \n}, <b>no trailing newline</b>, US-ASCII, digest lowercase, size a plain decimal.
 *
 * <p><b>The client builds this; the server never sends it.</b> Verifying a message the server chose would
 * defeat the point: the statement is reconstructed from the type, version, digest and size this client
 * actually intends to use, so a signature only ever vouches for exactly those.
 *
 * <p><b>Rejects rather than normalises.</b> An uppercase digest or a version with a newline in it throws,
 * because "fixing" it would build a statement the site never signed and turn a server bug into a
 * verification failure that looks like an attack - or worse, turn an attacker's formatting trick into a
 * statement that verifies. Callers treat the exception as an invalid signature.
 *
 * <p>{@code v1} is a promise: once a client ships that verifies it, the site must emit it forever.
 */
public final class SignedArtifactStatement {

    public static final String FORMAT = "sn3.artifact.v1";

    public static final String TYPE_LAUNCHER = "launcher";
    public static final String TYPE_BOOTSTRAP = "bootstrap";
    public static final String TYPE_DEB = "deb";

    private static final Set<String> TYPES = Set.of(TYPE_LAUNCHER, TYPE_BOOTSTRAP, TYPE_DEB);

    /** No whitespace and no newline: a version is one line of the statement. */
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+~-]{0,63}");

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private SignedArtifactStatement() {
    }

    /** @throws IllegalArgumentException for anything off-format - see the class comment for why */
    public static byte[] build(String type, String version, String sha256, long size) {
        if (type == null || !TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown artifact type: " + type);
        }
        if (version == null || !VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("A signed version must be 1-64 characters of [A-Za-z0-9._+~-].");
        }
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("The digest must be 64 lowercase hex characters.");
        }
        if (size < 0) {
            throw new IllegalArgumentException("A size cannot be negative.");
        }

        String statement = FORMAT + "\ntype=" + type + "\nversion=" + version + "\nsha256=" + sha256 + "\nsize=" + size;
        return statement.getBytes(StandardCharsets.US_ASCII);
    }
}
