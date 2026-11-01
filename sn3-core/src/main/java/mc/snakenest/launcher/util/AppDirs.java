package mc.snakenest.launcher.util;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the launcher's single data root and the clean, fixed layout of
 * subdirectories beneath it. Every other class that needs a path on disk
 * goes through here rather than building one by hand, so the layout only
 * needs to change in one place.
 *
 * <p>Root, by OS (unless overridden - see below):
 * <ul>
 *   <li>Linux: {@code $HOME/.local/share/snake-n3st}</li>
 *   <li>macOS: {@code ~/Library/Application Support/snake-n3st}</li>
 *   <li>Windows: {@code %APPDATA%\snake-n3st}</li>
 * </ul>
 *
 * <p>The folder name {@code snake-n3st} above is the default <b>app id</b>. The {@code sn3.appId} system
 * property replaces it (only it: the OS-specific parent stays), so a site can ship a launcher whose data
 * lives beside, not inside, another site's - typically a test site and the production one installed on the
 * same machine. The site bakes it into {@code jvm.prop} (see {@code BundledJvmProperties}) from the package
 * id it is configured with, and only when that id is not the default, so an existing install keeps finding
 * its data. The value becomes a directory name, so anything that could be a path is refused - see
 * {@link #resolveAppId}.
 *
 * <p>The {@code -Dsn3.dataDir=...} JVM system property overrides all of the above with an
 * arbitrary directory, read by both {@code bootstrap} and {@code launcher} (both go through this
 * same class) - mainly to keep a test/dev instance's data (config, keys, cached jars, installed
 * modpacks) completely separate from a real one on the same machine, without them ever
 * interfering with each other.
 */
public final class AppDirs {

    /** The folder name used when no {@code sn3.appId} is set - and what every pre-existing install uses. */
    static final String DEFAULT_APP_ID = "snake-n3st";

    private final Path root;

    public AppDirs() {
        this(resolveRoot());
    }

    /** Points the whole tree at an arbitrary directory - mainly useful for tests. */
    public AppDirs(Path root) {
        this.root = root;
    }

    static Path resolveRoot() {
        return resolveRootForTesting(System.getProperty("sn3.dataDir"),
                System.getProperty("os.name", ""), System.getProperty("user.home"), System.getenv("APPDATA"),
                System.getProperty("sn3.appId"));
    }

    /**
     * The folder name to use for {@code appId}: the value itself if it is a plain name made of lowercase
     * letters, digits and {@code + . -} (what a Debian package id is), the default otherwise. Anything
     * else - a path separator, {@code ..}, a blank - must never reach {@code Path.resolve}, where it could
     * point the launcher's data (keys included) somewhere it was never meant to live.
     */
    static String resolveAppId(String appId) {
        if (appId == null) {
            return DEFAULT_APP_ID;
        }
        // Not trimmed: a stray newline or space is a reason to refuse, not to quietly clean up.
        return appId.matches("[a-z0-9][a-z0-9+.-]*") ? appId : DEFAULT_APP_ID;
    }

    /** Same as the five-argument form, with the default app id - kept so existing callers and tests read as before. */
    static Path resolveRootForTesting(String dataDirOverride, String osName, String home, String windowsAppData) {
        return resolveRootForTesting(dataDirOverride, osName, home, windowsAppData, null);
    }

    /** Package-visible, pure version of {@link #resolveRoot()} - exercised directly by tests. */
    static Path resolveRootForTesting(String dataDirOverride, String osName, String home, String windowsAppData,
                                      String appId) {
        if (dataDirOverride != null && !dataDirOverride.isBlank()) {
            String dRoot = System.getProperty("sn3.dataRoot","");
            return switch (dRoot) {
                case "jar" -> {
                    try {
                        // Récupère le dossier contenant le JAR en cours d'exécution
                        String pathToJar = AppDirs.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .getPath();

                        String jarDirectory = new File(pathToJar).getParentFile().getPath();
                        jarDirectory = URLDecoder.decode(jarDirectory, StandardCharsets.UTF_8);

                        yield Paths.get(jarDirectory, dataDirOverride);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                case "home" -> Paths.get(System.getProperty("user.home"), dataDirOverride);
                default -> Paths.get(dataDirOverride);
            };
        }

        String os = osName.toLowerCase();
        String folder = resolveAppId(appId);

        if (os.contains("win")) {
            Path base = (windowsAppData != null && !windowsAppData.isBlank())
                    ? Paths.get(windowsAppData)
                    : Paths.get(home, "AppData", "Roaming");
            return base.resolve(folder);
        }

        if (os.contains("mac") || os.contains("darwin")) {
            return Paths.get(home, "Library", "Application Support", folder);
        }

        return Paths.get(home, ".local", "share", folder);
    }

    public Path root() {
        return root;
    }

    public Path cache() {
        return root.resolve("cache");
    }

    /** Where the bootstrap caches downloaded launcher jars. */
    public Path cacheLauncher() {
        return cache().resolve("launcher");
    }

    public Path instances() {
        return root.resolve("instances");
    }

    /** Full, independent game directory for one modpack. */
    public Path instance(String modpackSlug) {
        return instances().resolve(modpackSlug);
    }

    /** Encrypted private key + its local wrapping key, tightened permissions. */
    public Path secure() {
        return root.resolve("secure");
    }

    public Path logs() {
        return root.resolve("logs");
    }

    public Path configFile() {
        return root.resolve("config.json");
    }

    /**
     * Per-modpack launcher settings (memory allocation, custom JVM args) -
     * deliberately outside {@link #instance}, so a "Reparer"/reinstall pass
     * over the instance directory can never wipe it.
     */
    public Path modpackSettingsFile(String modpackSlug) {
        return root.resolve("modpack-settings").resolve(modpackSlug + ".json");
    }

    /**
     * Shared pool of vanilla asset objects (see {@code game.assets.SharedAssetsLinker} in the
     * launcher module), mirroring Mojang's own content-addressed {@code assets/objects/xx/xxxx...}
     * layout - naturally collision-free and reusable across every modpack/Minecraft version, since
     * Mojang already names these files by their own hash. Only used when the "Stockage des
     * ressources vanilla" setting is set to share within the launcher (as opposed to per-modpack,
     * or the official Minecraft launcher's own folder).
     */
    public Path sharedAssetsPool() {
        return root.resolve("shared").resolve("assets");
    }

    /**
     * Shared pool of vanilla + mod-loader library jars (and the vanilla client jar itself),
     * mirroring the same relative paths Minecraft/FlowUpdater already use - these already encode
     * group/artifact/version (or the Minecraft version, for the client jar), so this is naturally
     * collision-free the same way {@link #sharedAssetsPool()} is.
     */
    public Path sharedLibrariesPool() {
        return root.resolve("shared").resolve("libraries");
    }
}
