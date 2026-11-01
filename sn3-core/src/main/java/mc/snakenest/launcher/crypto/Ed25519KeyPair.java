package mc.snakenest.launcher.crypto;

import mc.snakenest.launcher.util.Hex;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;

import java.security.InvalidKeyException;
import java.security.SignatureException;

/**
 * An Ed25519 (RFC 8032) key pair derived from a raw 32-byte seed - the same
 * format the site issues ({@code privateKey} in the device-flow response)
 * and the same format the in-game mod reads from {@code -Dsn3.token}.
 *
 * <p>Deliberately uses {@code net.i2p.crypto:eddsa} rather than the JDK's
 * built-in {@code java.security} Ed25519 support: the JDK can sign with a
 * raw-seed private key, but exposes no portable, documented way to derive
 * the corresponding raw public key from just that seed. This exact library
 * is already used (and cross-validated against the JDK's own verifier and
 * against PHP's {@code sodium_crypto_sign_seed_keypair}) by the in-game mod
 * in this same project - see {@code mod/common/.../auth/Ed25519Signer.java}.
 *
 * <p>Instances never log or expose the seed via {@link #toString()} - only
 * the public key, which is not secret.
 */
public final class Ed25519KeyPair {

    private static final EdDSANamedCurveSpec CURVE_SPEC = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);

    private final byte[] seed;
    private final EdDSAPrivateKey privateKey;
    private final byte[] publicKey;

    private Ed25519KeyPair(byte[] seed, EdDSAPrivateKey privateKey, byte[] publicKey) {
        this.seed = seed;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    /**
     * @param seed32 the raw 32-byte Ed25519 seed
     * @throws IllegalArgumentException if {@code seed32} isn't exactly 32 bytes
     */
    public static Ed25519KeyPair fromSeed(byte[] seed32) {
        if (seed32.length != 32) {
            throw new IllegalArgumentException("Ed25519 seed must be 32 bytes, got " + seed32.length);
        }
        EdDSAPrivateKeySpec privSpec = new EdDSAPrivateKeySpec(seed32, CURVE_SPEC);
        EdDSAPrivateKey privateKey = new EdDSAPrivateKey(privSpec);
        EdDSAPublicKeySpec pubSpec = new EdDSAPublicKeySpec(privSpec.getA(), CURVE_SPEC);
        byte[] publicKey = new EdDSAPublicKey(pubSpec).getAbyte();
        return new Ed25519KeyPair(seed32.clone(), privateKey, publicKey);
    }

    public static Ed25519KeyPair fromSeedHex(String seedHex) {
        return fromSeed(Hex.decode(seedHex));
    }

    /** The raw 32-byte seed. Treat as a secret - callers must not log it. */
    public byte[] seed() {
        return seed.clone();
    }

    public String seedHex() {
        return Hex.encode(seed);
    }

    /** The raw 32-byte public key. Not secret. */
    public byte[] publicKey() {
        return publicKey.clone();
    }

    public String publicKeyHex() {
        return Hex.encode(publicKey);
    }

    public byte[] sign(byte[] message) {
        try {
            EdDSAEngine engine = new EdDSAEngine();
            engine.initSign(privateKey);
            engine.update(message);
            return engine.sign();
        } catch (InvalidKeyException | SignatureException e) {
            throw new IllegalStateException("Ed25519 signing failed", e);
        }
    }

    public String signHex(byte[] message) {
        return Hex.encode(sign(message));
    }

    /** Deliberately omits the seed - only the public key is safe to print/log. */
    @Override
    public String toString() {
        return "Ed25519KeyPair[publicKey=" + publicKeyHex() + "]";
    }
}
