package mc.snakenest.launcher.modpack.signing;

import mc.snakenest.launcher.crypto.Ed25519Verifier;
import mc.snakenest.launcher.modpack.ModpackManifest;
import mc.snakenest.launcher.util.Hex;
import mc.snakenest.launcher.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * Checks a modpack manifest against the pinned root key: certificate first (the root says this key is this
 * modpack's), then the version signature (that key says this manifest is this version of it).
 *
 * <p>Policy, in the order it is applied:
 * <ol>
 *   <li>no root pinned - accept, nothing to check;</li>
 *   <li>no signature, never seen one for this modpack - accept with a warning (the transition);</li>
 *   <li>no signature, but one was seen - refuse: someone removed it, or the site lost it;</li>
 *   <li>a signature that is malformed, not by a certified key, for another modpack or version, or over other
 *       content than the manifest in hand - refuse;</li>
 *   <li>a certificate with a lower serial than one already seen - refuse;</li>
 *   <li>otherwise valid, and the serial is remembered.</li>
 * </ol>
 *
 * <p>Everything the signature covers is rebuilt from the manifest fields and the slug that was asked for. The
 * {@code requestedVersion}, when the caller pinned one by name, must match the manifest's own: a signed
 * manifest of <em>another</em> version is genuine, but it is not what was asked for.
 *
 * <p>Never throws on hostile input: anything off-format is a refusal.
 */
public final class ModpackManifestVerifier {

    private static final Pattern HEX_KEY = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern HEX_SIGNATURE = Pattern.compile("[0-9a-f]{128}");

    private final ModpackSigningKeys keys;
    private final ModpackTrustState trust;

    public ModpackManifestVerifier(ModpackSigningKeys keys, ModpackTrustState trust) {
        this.keys = keys;
        this.trust = trust;
    }

    /** Verifies nothing: for callers that run without pins (development, tests). */
    public static ModpackManifestVerifier disabled() {
        return new ModpackManifestVerifier(ModpackSigningKeys.none(), ModpackTrustState.inMemory());
    }

    /** @param requestedVersion the exact version the caller asked for, or {@code null} when resolved through a channel */
    public ManifestVerdict verify(String slug, String requestedVersion, ModpackManifest manifest) {
        if (!keys.isConfigured()) {
            return ManifestVerdict.notConfigured();
        }

        ManifestVerdict verdict;
        try {
            verdict = check(slug, requestedVersion, manifest);
        } catch (RuntimeException e) {
            verdict = ManifestVerdict.refused("the signature could not be checked: " + e.getMessage());
        }

        switch (verdict.status()) {
            case UNSIGNED -> Log.warn(ModpackManifestVerifier.class, "Modpack \"" + slug + "\" is not signed: " + verdict.reason());
            case REFUSED -> Log.error(ModpackManifestVerifier.class, "Modpack \"" + slug + "\" refused: " + verdict.reason(), null);
            default -> { }
        }
        return verdict;
    }

    private ManifestVerdict check(String slug, String requestedVersion, ModpackManifest manifest) {
        ModpackManifest.Signature signature = manifest.signature();
        ModpackManifest.Certificate certificate = manifest.certificate();

        if (signature == null && certificate == null) {
            return trust.hasSeenValid(slug)
                    ? ManifestVerdict.refused("it was signed before and this manifest has no signature")
                    : ManifestVerdict.unsigned("no signature has been seen for it yet");
        }
        if (signature == null || certificate == null) {
            return ManifestVerdict.refused("the signature or its certificate is missing");
        }

        if (!"ed25519".equals(signature.alg()) || signature.value() == null || !HEX_SIGNATURE.matcher(signature.value()).matches()) {
            return ManifestVerdict.refused("the signature is malformed");
        }
        if (certificate.publicKey() == null || !HEX_KEY.matcher(certificate.publicKey()).matches()
                || certificate.signature() == null || !HEX_SIGNATURE.matcher(certificate.signature()).matches()
                || certificate.serial() < 1 || !slug.equals(certificate.modpack())) {
            return ManifestVerdict.refused("the certificate is malformed or names another modpack");
        }

        byte[] modpackKey = Hex.decode(certificate.publicKey());
        byte[] certificateText = ModpackManifestStatements.certificate(slug, modpackKey, certificate.serial());
        byte[] certificateSignature = Hex.decode(certificate.signature());

        boolean certified = false;
        for (byte[] root : keys.roots()) {
            if (Ed25519Verifier.verify(root, certificateText, certificateSignature)) {
                certified = true;
                break;
            }
        }
        if (!certified) {
            return ManifestVerdict.refused("the certificate was not signed by a pinned root key");
        }

        long known = trust.highestSerial(slug);
        if (certificate.serial() < known) {
            return ManifestVerdict.refused("its certificate (serial " + certificate.serial()
                    + ") is older than one already seen (serial " + known + ")");
        }

        byte[] manifestText = ModpackManifestStatements.manifest(slug, manifest.version(), manifest.mcVersion(), manifest.loader(),
                manifest.loaderVersion(), manifest.defaultJvmArgs(), manifest.files());
        byte[] versionText = ModpackManifestStatements.version(slug, manifest.version(), manifest.mcVersion(), manifest.loader(),
                sha256Hex(manifestText));

        if (!Ed25519Verifier.verify(modpackKey, versionText, Hex.decode(signature.value()))) {
            return ManifestVerdict.refused("the signature does not match this manifest");
        }

        if (requestedVersion != null && !requestedVersion.equals(manifest.version())) {
            return ManifestVerdict.refused("the signed manifest is for version \"" + manifest.version()
                    + "\", not the \"" + requestedVersion + "\" that was asked for");
        }

        trust.recordValid(slug, certificate.serial());
        return ManifestVerdict.valid();
    }

    private static String sha256Hex(byte[] data) {
        try {
            return Hex.encode(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
