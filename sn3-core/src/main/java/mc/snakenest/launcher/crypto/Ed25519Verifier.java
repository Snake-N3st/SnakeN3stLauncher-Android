package mc.snakenest.launcher.crypto;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;

/**
 * Checks an Ed25519 signature against a public key. Verification only: a separate class rather than a method
 * on {@link Ed25519KeyPair}, whose whole contract is "I hold a seed" - a verifier holds nothing secret and
 * must not be reachable through something that does.
 *
 * <p>Returns {@code false} for any malformed input (wrong key length, wrong signature length, a point that is
 * not on the curve) and never throws: a verifier that can be made to throw is a verifier a caller wraps in a
 * {@code catch} that happens to swallow the wrong thing. Mirrors {@code Ed25519::verify()} in the site's
 * plugin, which the signatures come from.
 *
 * <p>Known property of the eddsa 0.3.0 library: it does not enforce the canonical {@code S} of RFC 8032, so a
 * signature has a second, malleated form that also verifies. Harmless here - the signed statement is what
 * authenticates, nobody keys anything on the signature bytes themselves - but written down so it is not
 * rediscovered.
 */
public final class Ed25519Verifier {

    private static final EdDSANamedCurveSpec CURVE = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);

    public static final int PUBLIC_KEY_BYTES = 32;
    public static final int SIGNATURE_BYTES = 64;

    private Ed25519Verifier() {
    }

    public static boolean verify(byte[] publicKey, byte[] message, byte[] signature) {
        if (publicKey == null || message == null || signature == null
                || publicKey.length != PUBLIC_KEY_BYTES || signature.length != SIGNATURE_BYTES) {
            return false;
        }

        try {
            EdDSAPublicKey key = new EdDSAPublicKey(new EdDSAPublicKeySpec(publicKey, CURVE));
            EdDSAEngine engine = new EdDSAEngine();
            engine.initVerify(key);
            engine.update(message);
            return engine.verify(signature);
        } catch (Exception | LinkageError e) {
            return false;
        }
    }
}
