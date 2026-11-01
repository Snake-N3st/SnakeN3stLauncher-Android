package mc.snakenest.launcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * This launcher build's own version - the one shown in Parametres &gt; A propos, and the one that
 * should be published as the release version on the site so "what the player is running" and "what
 * the site says is latest" are the same string.
 *
 * <p>Read from {@code version.properties}, which Maven fills in from the {@code launcher} module's
 * own {@code <version>} at build time (see that pom's resource filtering). Deliberately not read
 * from the jar manifest's {@code Implementation-Version}: that only exists once the jar is
 * packaged, so running from an IDE or from {@code target/classes} would report nothing, and it also
 * depends on the shade plugin preserving the manifest. A filtered resource is present in both
 * cases and survives shading unchanged.
 *
 * <p>Falls back to {@value #UNKNOWN} rather than throwing - not knowing the version is a cosmetic
 * problem, never a reason to fail to start.
 */
public final class AppVersion {

    private static final String RESOURCE_PATH = "/version.properties";
    private static final String UNKNOWN = "dev";

    private static final String VERSION = read();

    private AppVersion() {
    }

    /** e.g. {@code "2.0.0"}, or {@code "dev"} if this build has no version baked in. */
    public static String current() {
        return VERSION;
    }

    private static String read() {
        try (InputStream in = AppVersion.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            // An unsubstituted "${project.version}" means the resource was read straight from the
            // source tree rather than a filtered build - treat that as "no version", not as a
            // version literally called "${project.version}".
            if (version == null || version.isBlank() || version.startsWith("${")) {
                return UNKNOWN;
            }
            return version.trim();
        } catch (IOException e) {
            Log.warn(AppVersion.class, "Could not read " + RESOURCE_PATH + ": " + e.getMessage());
            return UNKNOWN;
        }
    }
}
