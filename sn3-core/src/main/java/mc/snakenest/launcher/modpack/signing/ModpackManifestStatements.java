package mc.snakenest.launcher.modpack.signing;

import mc.snakenest.launcher.modpack.ManifestFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The three texts of the modpack signature chain, rebuilt here from values the launcher already holds -
 * mirror of {@code ManifestCanonical.php} and {@code ModpackStatements.php}, kept byte-exact with them by a
 * shared test vector.
 *
 * <p><b>The server never sends a text to verify.</b> Verifying a message the server chose would defeat the
 * point; every text is built from the slug this launcher asked for and the manifest fields it is about to use,
 * so a signature only ever vouches for exactly those.
 *
 * <p><b>Rejects rather than normalises</b>, like {@code SignedArtifactStatement}: an input the site could not
 * have signed throws {@link IllegalArgumentException}, which callers treat as an invalid signature.
 *
 * <p>Text fields are hex-encoded UTF-8 so no value can contain a separator; the file lines are sorted as
 * strings (all ASCII), which makes the order the server lists files in irrelevant.
 */
public final class ModpackManifestStatements {

    public static final String MANIFEST_FORMAT = "sn3.modpack-manifest.v1";
    public static final String VERSION_FORMAT = "sn3.modpack.v1";
    public static final String CERTIFICATE_FORMAT = "sn3.modpack-cert.v1";

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private ModpackManifestStatements() {
    }

    /** The canonical text of a manifest, the thing whose SHA-256 the version signature covers. */
    public static byte[] manifest(String slug, String version, String mcVersion, String loader, String loaderVersion,
                                  String jvmArgs, List<ManifestFile> files) {
        requireSlug(slug);
        requireNonEmpty(version, mcVersion, loader);

        List<String> fileLines = new ArrayList<>();
        for (ManifestFile file : files == null ? List.<ManifestFile>of() : files) {
            if (file == null || file.path() == null || file.path().isEmpty()) {
                throw new IllegalArgumentException("A file has no path.");
            }
            if (file.hash() == null || !SHA256.matcher(file.hash()).matches()) {
                throw new IllegalArgumentException("A file digest must be 64 lowercase hex characters.");
            }
            if (file.size() < 0) {
                throw new IllegalArgumentException("A file size cannot be negative.");
            }
            String url = file.url() == null || file.url().isEmpty() ? "-" : hex(file.url());
            fileLines.add("file=" + hex(file.path()) + " " + file.hash() + " " + file.size() + " " + url);
        }
        Collections.sort(fileLines);

        List<String> lines = new ArrayList<>();
        lines.add(MANIFEST_FORMAT);
        lines.add("modpack=" + slug);
        lines.add("version=" + hex(version));
        lines.add("mc_version=" + hex(mcVersion));
        lines.add("loader=" + hex(loader));
        lines.add("loader_version=" + hex(loaderVersion == null ? "" : loaderVersion));
        lines.add("jvm_args=" + hex(jvmArgs == null ? "" : jvmArgs));
        lines.addAll(fileLines);

        return String.join("\n", lines).getBytes(StandardCharsets.US_ASCII);
    }

    /** What a modpack's own key signs for one version. */
    public static byte[] version(String slug, String version, String mcVersion, String loader, String manifestSha256) {
        requireSlug(slug);
        requireNonEmpty(version, mcVersion, loader);
        if (manifestSha256 == null || !SHA256.matcher(manifestSha256).matches()) {
            throw new IllegalArgumentException("The manifest digest must be 64 lowercase hex characters.");
        }

        return String.join("\n", VERSION_FORMAT, "modpack=" + slug, "version=" + hex(version),
                "mc_version=" + hex(mcVersion), "loader=" + hex(loader), "manifest_sha256=" + manifestSha256)
                .getBytes(StandardCharsets.US_ASCII);
    }

    /** What the root key signs to say "this is the key of this modpack". */
    public static byte[] certificate(String slug, byte[] publicKey, long serial) {
        requireSlug(slug);
        if (publicKey == null || publicKey.length != 32) {
            throw new IllegalArgumentException("An Ed25519 public key is 32 bytes.");
        }
        if (serial < 1) {
            throw new IllegalArgumentException("A certificate serial starts at 1.");
        }

        return String.join("\n", CERTIFICATE_FORMAT, "modpack=" + slug, "pubkey=" + mc.snakenest.launcher.util.Hex.encode(publicKey),
                "serial=" + serial).getBytes(StandardCharsets.US_ASCII);
    }

    private static String hex(String value) {
        return mc.snakenest.launcher.util.Hex.encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void requireSlug(String slug) {
        if (slug == null || !SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("A signed modpack slug must be lowercase letters, digits and single hyphens.");
        }
    }

    private static void requireNonEmpty(String version, String mcVersion, String loader) {
        if (version == null || version.isEmpty() || mcVersion == null || mcVersion.isEmpty() || loader == null || loader.isEmpty()) {
            throw new IllegalArgumentException("Version, Minecraft version and loader cannot be empty.");
        }
    }
}
