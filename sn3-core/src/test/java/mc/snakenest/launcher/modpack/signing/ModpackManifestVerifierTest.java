package mc.snakenest.launcher.modpack.signing;

import mc.snakenest.launcher.crypto.Ed25519KeyPair;
import mc.snakenest.launcher.modpack.ManifestFile;
import mc.snakenest.launcher.modpack.ModpackManifest;
import mc.snakenest.launcher.modpack.signing.ManifestVerdict.Status;
import mc.snakenest.launcher.util.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The policy, against signatures made by real Ed25519 keys: the chain root -> modpack key -> version, the HSTS
 * rule per modpack, the certificate serial, and everything a server (or whoever sits in front of it) could try.
 */
class ModpackManifestVerifierTest {

    private static final String SLUG = "demo-pack";
    private static final Ed25519KeyPair ROOT = key(1);
    private static final Ed25519KeyPair OTHER_ROOT = key(9);
    private static final Ed25519KeyPair MODPACK = key(2);
    private static final Ed25519KeyPair NEW_MODPACK = key(3);

    private static Ed25519KeyPair key(int n) {
        byte[] seed = new byte[32];
        seed[0] = (byte) n;
        seed[31] = (byte) (n * 5);
        return Ed25519KeyPair.fromSeed(seed);
    }

    private static List<ManifestFile> files() {
        return List.of(new ManifestFile("mods/a.jar", "a".repeat(64), 10, null), new ManifestFile("config/b.toml", "b".repeat(64), 5, "https://e.org/x"));
    }

    private static ModpackManifest unsigned(String version) {
        return new ModpackManifest(version, "release", "log", 15, "1.21.1", "neoforge", "21.1.77", files(), "-Xmx3G", 4096);
    }

    private static String sha256(byte[] data) throws Exception {
        return Hex.encode(MessageDigest.getInstance("SHA-256").digest(data));
    }

    /** What the site would serve: certified by {@code root}, signed by {@code modpackKey}. */
    private static ModpackManifest signed(String version, Ed25519KeyPair root, Ed25519KeyPair modpackKey, long serial) throws Exception {
        ModpackManifest m = unsigned(version);
        byte[] text = ModpackManifestStatements.manifest(SLUG, version, m.mcVersion(), m.loader(), m.loaderVersion(), m.defaultJvmArgs(), m.files());
        byte[] statement = ModpackManifestStatements.version(SLUG, version, m.mcVersion(), m.loader(), sha256(text));
        String certSig = root.signHex(ModpackManifestStatements.certificate(SLUG, modpackKey.publicKey(), serial));

        return new ModpackManifest(m.version(), m.channel(), m.changelog(), m.totalSize(), m.mcVersion(), m.loader(), m.loaderVersion(),
                m.files(), m.defaultJvmArgs(), m.rawDefaultMemoryMb(),
                new ModpackManifest.Signature("ed25519", modpackKey.signHex(statement)),
                new ModpackManifest.Certificate(SLUG, modpackKey.publicKeyHex(), serial, certSig));
    }

    private static ModpackManifestVerifier verifier(ModpackTrustState trust, Ed25519KeyPair... roots) {
        return new ModpackManifestVerifier(ModpackSigningKeys.of(java.util.Arrays.stream(roots).map(Ed25519KeyPair::publicKey).toList()), trust);
    }

    private static Status status(ModpackManifestVerifier v, String requested, ModpackManifest m) {
        return v.verify(SLUG, requested, m).status();
    }

    @Test
    void aFullyValidChainIsAccepted() throws Exception {
        assertEquals(Status.VALID, status(verifier(ModpackTrustState.inMemory(), ROOT), null, signed("1.0", ROOT, MODPACK, 1)));
    }

    @Test
    void withNoPinnedRootNothingIsCheckedNotEvenABrokenSignature() throws Exception {
        ModpackManifest broken = signed("1.0", OTHER_ROOT, MODPACK, 1);
        assertEquals(Status.NOT_CONFIGURED, status(verifier(ModpackTrustState.inMemory()), null, broken));
        assertEquals(Status.NOT_CONFIGURED, status(ModpackManifestVerifier.disabled(), null, unsigned("1.0")));
    }

    @Test
    void unsignedIsAWarningOnlyUntilASignatureHasBeenSeen() throws Exception {
        ModpackTrustState trust = ModpackTrustState.inMemory();
        ModpackManifestVerifier v = verifier(trust, ROOT);

        assertEquals(Status.UNSIGNED, status(v, null, unsigned("1.0")));
        assertTrue(v.verify(SLUG, null, unsigned("1.0")).accepted());

        assertEquals(Status.VALID, status(v, null, signed("1.1", ROOT, MODPACK, 1)));
        assertEquals(Status.REFUSED, status(v, null, unsigned("1.2")), "a signature that disappears is a downgrade");
        assertFalse(v.verify(SLUG, null, unsigned("1.2")).accepted());
    }

    @Test
    void theRuleIsPerModpackNotGlobal() throws Exception {
        ModpackTrustState trust = ModpackTrustState.inMemory();
        ModpackManifestVerifier v = verifier(trust, ROOT);
        assertEquals(Status.VALID, status(v, null, signed("1.0", ROOT, MODPACK, 1)));
        assertEquals(Status.UNSIGNED, v.verify("another-pack", null, unsigned("1.0")).status(),
                "another modpack that was never signed keeps the transition behaviour");
    }

    @Test
    void aSignatureFromAKeyThatTheRootDidNotCertifyIsRefused() throws Exception {
        assertEquals(Status.REFUSED, status(verifier(ModpackTrustState.inMemory(), ROOT), null, signed("1.0", OTHER_ROOT, MODPACK, 1)));
    }

    @Test
    void aSecondPinnedRootIsEnoughForRotation() throws Exception {
        assertEquals(Status.VALID, status(verifier(ModpackTrustState.inMemory(), OTHER_ROOT, ROOT), null, signed("1.0", ROOT, MODPACK, 1)));
    }

    @Test
    void tamperingWithAnythingTheSignatureCoversIsRefused() throws Exception {
        ModpackManifest ok = signed("1.0", ROOT, MODPACK, 1);
        ModpackManifestVerifier v = verifier(ModpackTrustState.inMemory(), ROOT);

        List<ManifestFile> changed = List.of(new ManifestFile("mods/a.jar", "0".repeat(64), 10, null), ok.files().get(1));
        assertEquals(Status.REFUSED, status(v, null, with(ok, ok.version(), ok.mcVersion(), ok.loader(), ok.loaderVersion(), "-javaagent:evil.jar", ok.files())));
        assertEquals(Status.REFUSED, status(v, null, with(ok, ok.version(), ok.mcVersion(), ok.loader(), ok.loaderVersion(), ok.defaultJvmArgs(), changed)));
        assertEquals(Status.REFUSED, status(v, null, with(ok, ok.version(), ok.mcVersion(), ok.loader(), "99", ok.defaultJvmArgs(), ok.files())));
        assertEquals(Status.REFUSED, status(v, null, with(ok, ok.version(), ok.mcVersion(), "forge", ok.loaderVersion(), ok.defaultJvmArgs(), ok.files())));
        assertEquals(Status.REFUSED, status(v, null, with(ok, "9.9", ok.mcVersion(), ok.loader(), ok.loaderVersion(), ok.defaultJvmArgs(), ok.files())));

        java.util.ArrayList<ManifestFile> more = new java.util.ArrayList<>(ok.files());
        more.add(new ManifestFile("mods/evil.jar", "c".repeat(64), 1, null));
        assertEquals(Status.REFUSED, status(v, null, with(ok, ok.version(), ok.mcVersion(), ok.loader(), ok.loaderVersion(), ok.defaultJvmArgs(), more)));
        assertEquals(Status.REFUSED, status(v, null, with(ok, ok.version(), ok.mcVersion(), ok.loader(), ok.loaderVersion(), ok.defaultJvmArgs(), ok.files().subList(0, 1))));

        List<ManifestFile> otherUrl = List.of(ok.files().get(0), new ManifestFile("config/b.toml", "b".repeat(64), 5, "https://evil.example/x"));
        assertEquals(Status.REFUSED, status(v, null, with(ok, ok.version(), ok.mcVersion(), ok.loader(), ok.loaderVersion(), ok.defaultJvmArgs(), otherUrl)));
    }

    @Test
    void fieldsTheSignatureDeliberatelyDoesNotCoverMayChange() throws Exception {
        ModpackManifest ok = signed("1.0", ROOT, MODPACK, 1);
        ModpackManifest edited = new ModpackManifest(ok.version(), "testing", "a new changelog", 999, ok.mcVersion(), ok.loader(), ok.loaderVersion(),
                ok.files(), ok.defaultJvmArgs(), 8192, ok.signature(), ok.certificate());
        assertEquals(Status.VALID, status(verifier(ModpackTrustState.inMemory(), ROOT), null, edited));
    }

    @Test
    void theOrderOfTheFileListIsIrrelevant() throws Exception {
        ModpackManifest ok = signed("1.0", ROOT, MODPACK, 1);
        assertEquals(Status.VALID, status(verifier(ModpackTrustState.inMemory(), ROOT), null,
                with(ok, ok.version(), ok.mcVersion(), ok.loader(), ok.loaderVersion(), ok.defaultJvmArgs(), List.of(ok.files().get(1), ok.files().get(0)))));
    }

    @Test
    void aCertificateForAnotherModpackCannotBeReplayedHere() throws Exception {
        ModpackManifest ok = signed("1.0", ROOT, MODPACK, 1);
        ModpackManifest.Certificate c = ok.certificate();
        ModpackManifest relabelled = withCert(ok, new ModpackManifest.Certificate("other-pack", c.publicKey(), c.serial(), c.signature()));
        assertEquals(Status.REFUSED, status(verifier(ModpackTrustState.inMemory(), ROOT), null, relabelled));
        // and asked for under a different slug than it names
        assertEquals(Status.REFUSED, verifier(ModpackTrustState.inMemory(), ROOT).verify("other-pack", null, ok).status());
    }

    @Test
    void aSwappedKeyInTheCertificateIsRefused() throws Exception {
        ModpackManifest ok = signed("1.0", ROOT, MODPACK, 1);
        ModpackManifest.Certificate c = ok.certificate();
        assertEquals(Status.REFUSED, status(verifier(ModpackTrustState.inMemory(), ROOT), null,
                withCert(ok, new ModpackManifest.Certificate(SLUG, NEW_MODPACK.publicKeyHex(), c.serial(), c.signature()))));
    }

    @Test
    void anOlderCertificateSerialThanOneAlreadySeenIsRefused() throws Exception {
        ModpackTrustState trust = ModpackTrustState.inMemory();
        ModpackManifestVerifier v = verifier(trust, ROOT);

        assertEquals(Status.VALID, status(v, null, signed("1.0", ROOT, MODPACK, 1)));
        assertEquals(Status.VALID, status(v, null, signed("1.1", ROOT, NEW_MODPACK, 2)), "the repaired key, higher serial");
        assertEquals(2, trust.highestSerial(SLUG));
        assertEquals(Status.REFUSED, status(v, null, signed("1.2", ROOT, MODPACK, 1)), "the old, possibly compromised key can no longer be replayed");
        assertEquals(Status.VALID, status(v, null, signed("1.3", ROOT, NEW_MODPACK, 2)), "the same serial keeps working");
    }

    @Test
    void aSignedManifestOfAnotherVersionIsNotWhatWasAskedFor() throws Exception {
        ModpackManifestVerifier v = verifier(ModpackTrustState.inMemory(), ROOT);
        assertEquals(Status.VALID, status(v, "1.0", signed("1.0", ROOT, MODPACK, 1)));
        assertEquals(Status.REFUSED, status(v, "2.0", signed("1.0", ROOT, MODPACK, 1)), "a genuine old manifest replayed for a pinned newer version");
        assertEquals(Status.VALID, status(v, null, signed("1.0", ROOT, MODPACK, 1)), "through a channel there is nothing to compare");
    }

    @Test
    void malformedSignatureMaterialIsRefusedNeverAnException() throws Exception {
        ModpackManifest ok = signed("1.0", ROOT, MODPACK, 1);
        ModpackManifestVerifier v = verifier(ModpackTrustState.inMemory(), ROOT);

        assertEquals(Status.REFUSED, status(v, null, withSig(ok, new ModpackManifest.Signature("rsa", ok.signature().value()))));
        assertEquals(Status.REFUSED, status(v, null, withSig(ok, new ModpackManifest.Signature("ed25519", "abcd"))));
        assertEquals(Status.REFUSED, status(v, null, withSig(ok, new ModpackManifest.Signature("ed25519", ok.signature().value().toUpperCase()))));
        assertEquals(Status.REFUSED, status(v, null, withSig(ok, new ModpackManifest.Signature("ed25519", null))));
        assertEquals(Status.REFUSED, status(v, null, withSig(ok, null)), "a certificate without a signature");
        assertEquals(Status.REFUSED, status(v, null, withCert(ok, null)), "a signature without a certificate");
        ModpackManifest.Certificate c = ok.certificate();
        assertEquals(Status.REFUSED, status(v, null, withCert(ok, new ModpackManifest.Certificate(SLUG, "zz", c.serial(), c.signature()))));
        assertEquals(Status.REFUSED, status(v, null, withCert(ok, new ModpackManifest.Certificate(SLUG, c.publicKey(), 0, c.signature()))));
        assertEquals(Status.REFUSED, status(v, null, withCert(ok, new ModpackManifest.Certificate(SLUG, c.publicKey(), c.serial(), null))));
        assertEquals(Status.REFUSED, status(v, null, with(ok, "", ok.mcVersion(), ok.loader(), ok.loaderVersion(), ok.defaultJvmArgs(), ok.files())), "an empty version is off-format");
        assertEquals(Status.REFUSED, status(v, null, with(ok, ok.version(), ok.mcVersion(), ok.loader(), ok.loaderVersion(), ok.defaultJvmArgs(),
                List.of(new ManifestFile("x", "NOT-HEX", 1, null)))));
    }

    @Test
    void theTrustStateSurvivesARestartAndIsTolerantOfAGarbageFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(ModpackTrustState.FILE_NAME);

        ModpackManifestVerifier first = verifier(ModpackTrustState.load(file), ROOT);
        assertEquals(Status.VALID, status(first, null, signed("1.0", ROOT, MODPACK, 3)));

        ModpackManifestVerifier restarted = verifier(ModpackTrustState.load(file), ROOT);
        assertEquals(Status.REFUSED, status(restarted, null, unsigned("1.1")), "remembered across runs");
        assertEquals(Status.REFUSED, status(restarted, null, signed("1.1", ROOT, MODPACK, 2)));

        java.nio.file.Files.writeString(file, "{ not json");
        assertEquals(Status.UNSIGNED, status(verifier(ModpackTrustState.load(file), ROOT), null, unsigned("1.1")), "unreadable reads as nothing seen, and does not throw");
    }

    @Test
    void theRootKeysPropertyIsParsedStrictlyAndToleratesATypo() {
        String good = Hex.encode(ROOT.publicKey());
        assertEquals(1, ModpackSigningKeys.fromProperties(n -> good).roots().size());
        assertEquals(2, ModpackSigningKeys.fromProperties(n -> good + " , " + Hex.encode(OTHER_ROOT.publicKey())).roots().size());
        assertEquals(1, ModpackSigningKeys.fromProperties(n -> good + ",nonsense").roots().size(), "a bad entry is skipped");
        assertFalse(ModpackSigningKeys.fromProperties(n -> "nonsense").isConfigured(), "all bad = unconfigured, not failing closed");
        assertFalse(ModpackSigningKeys.fromProperties(n -> null).isConfigured());
        assertFalse(ModpackSigningKeys.fromProperties(n -> good.toUpperCase()).isConfigured(), "uppercase is not the format");
    }

    private static ModpackManifest with(ModpackManifest m, String version, String mc, String loader, String loaderVersion, String jvm, List<ManifestFile> files) {
        return new ModpackManifest(version, m.channel(), m.changelog(), m.totalSize(), mc, loader, loaderVersion, files, jvm, m.rawDefaultMemoryMb(),
                m.signature(), m.certificate());
    }

    private static ModpackManifest withCert(ModpackManifest m, ModpackManifest.Certificate c) {
        return new ModpackManifest(m.version(), m.channel(), m.changelog(), m.totalSize(), m.mcVersion(), m.loader(), m.loaderVersion(), m.files(),
                m.defaultJvmArgs(), m.rawDefaultMemoryMb(), m.signature(), c);
    }

    private static ModpackManifest withSig(ModpackManifest m, ModpackManifest.Signature s) {
        return new ModpackManifest(m.version(), m.channel(), m.changelog(), m.totalSize(), m.mcVersion(), m.loader(), m.loaderVersion(), m.files(),
                m.defaultJvmArgs(), m.rawDefaultMemoryMb(), s, m.certificate());
    }
}
