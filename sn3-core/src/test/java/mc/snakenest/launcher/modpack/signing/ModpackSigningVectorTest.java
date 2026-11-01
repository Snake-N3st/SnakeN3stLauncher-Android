package mc.snakenest.launcher.modpack.signing;

import mc.snakenest.launcher.crypto.Ed25519Verifier;
import mc.snakenest.launcher.modpack.ManifestFile;
import mc.snakenest.launcher.util.Hex;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vector shared with the site's {@code ModpackSigningFormatsTest.php}: the PHP side produced these bytes
 * and signatures from a fixed seed, and this side must rebuild the same bytes and accept the same signatures.
 * If one of them changes the format, exactly one of the two tests fails - that is what this is for.
 */
class ModpackSigningVectorTest {

    static final String SLUG = "demo-pack";
    static final String MODPACK_PUB = "03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8";
    static final String ROOT_PUB = "29acbae141bccaf0b22e1a94d34d0bc7361e526d0bfe12c89794bc9322966dd7";
    static final String MANIFEST_SHA256 = "f90af58c105b356de6ca14ebbdde6f257b4617800436838ba266e4f1f571ec23";
    static final String VERSION_SIG = "c3fec07c681ded0028ec90a0628364721a1fd49099bbb7d143c62c96c90781ce79e9e4efa0696dc19a4ce0d5ed53ecb340f36490e2d9fc0f424b4340b4302b08";
    static final String CERT_SIG = "160b980960b0f8251b7c54bfbc521bcc0d9bfdc11c2e9f18dd4e89f4b9530592b3e5038673c696a634ca76840833d61d3ca8535f646cb7eb3588ea74e6b33d03";

    static final String CANONICAL = String.join("\n",
            "sn3.modpack-manifest.v1",
            "modpack=demo-pack",
            "version=312e322e30",
            "mc_version=312e32312e31",
            "loader=6e656f666f726765",
            "loader_version=32312e312e3737",
            "jvm_args=2d586d783347202d44666f6f3dc3a9",
            "file=636f6e6669672f622e746f6d6c 3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d 10 68747470733a2f2f6578616d706c652e6f72672f782e6a6172",
            "file=6d6f64732f612e6a6172 ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb 1234 -",
            "file=c3a92fc3bc2066696c652e747874 2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6 0 -");

    static List<ManifestFile> files() {
        return List.of(
                new ManifestFile("mods/a.jar", "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb", 1234, null),
                new ManifestFile("config/b.toml", "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d", 10, "https://example.org/x.jar"),
                new ManifestFile("é/ü file.txt", "2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6", 0, null));
    }

    static byte[] canonical() {
        return ModpackManifestStatements.manifest(SLUG, "1.2.0", "1.21.1", "neoforge", "21.1.77", "-Xmx3G -Dfoo=é", files());
    }

    @Test
    void theCanonicalTextIsByteIdenticalToTheSites() {
        assertEquals(CANONICAL, new String(canonical(), StandardCharsets.US_ASCII));
    }

    @Test
    void theManifestDigestIsTheSites() throws Exception {
        assertEquals(MANIFEST_SHA256, Hex.encode(MessageDigest.getInstance("SHA-256").digest(canonical())));
    }

    @Test
    void theOrderFilesAreListedInDoesNotMatter() {
        List<ManifestFile> reversed = new java.util.ArrayList<>(files());
        java.util.Collections.reverse(reversed);
        assertArrayEquals(canonical(), ModpackManifestStatements.manifest(SLUG, "1.2.0", "1.21.1", "neoforge", "21.1.77", "-Xmx3G -Dfoo=é", reversed));
    }

    @Test
    void theSitesVersionSignatureVerifiesOverOurVersionStatement() {
        byte[] statement = ModpackManifestStatements.version(SLUG, "1.2.0", "1.21.1", "neoforge", MANIFEST_SHA256);
        assertTrue(Ed25519Verifier.verify(Hex.decode(MODPACK_PUB), statement, Hex.decode(VERSION_SIG)));
    }

    @Test
    void theSitesCertificateVerifiesOverOurCertificateStatement() {
        byte[] statement = ModpackManifestStatements.certificate(SLUG, Hex.decode(MODPACK_PUB), 1);
        assertTrue(Ed25519Verifier.verify(Hex.decode(ROOT_PUB), statement, Hex.decode(CERT_SIG)));
    }

    @Test
    void anyChangeToWhatIsSignedBreaksTheSignatures() {
        assertTrue(!Ed25519Verifier.verify(Hex.decode(MODPACK_PUB), ModpackManifestStatements.version(SLUG, "1.2.1", "1.21.1", "neoforge", MANIFEST_SHA256), Hex.decode(VERSION_SIG)));
        assertTrue(!Ed25519Verifier.verify(Hex.decode(ROOT_PUB), ModpackManifestStatements.certificate("other-pack", Hex.decode(MODPACK_PUB), 1), Hex.decode(CERT_SIG)));
        assertTrue(!Ed25519Verifier.verify(Hex.decode(ROOT_PUB), ModpackManifestStatements.certificate(SLUG, Hex.decode(MODPACK_PUB), 2), Hex.decode(CERT_SIG)));
    }

    @Test
    void offFormatInputIsRejectedNotNormalised() {
        assertThrows(IllegalArgumentException.class, () -> ModpackManifestStatements.manifest("Demo", "1", "1", "forge", null, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ModpackManifestStatements.manifest(SLUG, "", "1", "forge", null, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ModpackManifestStatements.manifest(SLUG, "1", "1", "forge", null, null,
                List.of(new ManifestFile("a", "ABCDEF".repeat(11).substring(0, 64), 1, null))));
        assertThrows(IllegalArgumentException.class, () -> ModpackManifestStatements.manifest(SLUG, "1", "1", "forge", null, null,
                List.of(new ManifestFile("", "a".repeat(64), 1, null))));
        assertThrows(IllegalArgumentException.class, () -> ModpackManifestStatements.certificate(SLUG, new byte[31], 1));
        assertThrows(IllegalArgumentException.class, () -> ModpackManifestStatements.certificate(SLUG, new byte[32], 0));
    }

    /**
     * The wire shape: the manifest as the site serves it (snake_case, nested signature and certificate), parsed
     * by the same Gson the client uses, verified by the real verifier against the PHP-made signatures.
     */
    @Test
    void theSitesJsonShapeParsesAndVerifiesEndToEnd() {
        String json = """
                {"version":"1.2.0","channel":"release","changelog":"x","total_size":1244,"mc_version":"1.21.1","loader":"neoforge",
                 "loader_version":"21.1.77",
                 "files":[{"path":"mods/a.jar","hash":"ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb","size":1234,"url":null},
                          {"path":"config/b.toml","hash":"3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d","size":10,"url":"https://example.org/x.jar"},
                          {"path":"é/ü file.txt","hash":"2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6","size":0,"url":null}],
                 "default_jvm_args":"-Xmx3G -Dfoo=é","default_memory_mb":3072,
                 "signature":{"alg":"ed25519","value":"%s"},
                 "certificate":{"modpack":"demo-pack","public_key":"%s","serial":1,"signature":"%s"}}
                """.formatted(VERSION_SIG, MODPACK_PUB, CERT_SIG);

        mc.snakenest.launcher.modpack.ModpackManifest manifest = new com.google.gson.Gson().fromJson(json, mc.snakenest.launcher.modpack.ModpackManifest.class);

        ModpackManifestVerifier verifier = new ModpackManifestVerifier(ModpackSigningKeys.of(List.of(Hex.decode(ROOT_PUB))), ModpackTrustState.inMemory());
        assertEquals(ManifestVerdict.Status.VALID, verifier.verify(SLUG, "1.2.0", manifest).status());
    }

    @Test
    void anUnsignedJsonManifestParsesWithNullSignatureFields() {
        mc.snakenest.launcher.modpack.ModpackManifest manifest = new com.google.gson.Gson().fromJson(
                "{\"version\":\"1\",\"mc_version\":\"1.21.1\",\"loader\":\"forge\",\"files\":[]}", mc.snakenest.launcher.modpack.ModpackManifest.class);
        assertEquals(null, manifest.signature());
        assertEquals(null, manifest.certificate());
    }
}
