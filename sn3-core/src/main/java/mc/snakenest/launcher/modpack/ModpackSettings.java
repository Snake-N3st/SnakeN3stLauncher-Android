package mc.snakenest.launcher.modpack;

/**
 * Per-modpack launcher preferences the player can tune from the "Gerer"
 * action ({@code ui.modpack.ModpackDetailPage}): how much memory to give the
 * JVM, any extra JVM arguments, and which distribution channel to follow for
 * this modpack's own version updates. Persisted by {@link ModpackSettingsStore},
 * one small JSON file per modpack slug.
 *
 * @param memoryMb     heap size passed as {@code -Xmx<memoryMb>M}
 * @param extraJvmArgs raw, space-separated extra JVM args appended after the
 *                     memory flag and the launcher's own {@code -Dsn3.token};
 *                     empty string (not null) when unset
 * @param channel      the distribution channel this modpack instance follows, sent as {@code
 *                     ?channel=} when fetching this modpack's manifest - {@code null} means the
 *                     site's default channel. Independent of {@code config.LauncherConfig#channel}
 *                     (the launcher's own channel) - each modpack instance picks its own, see
 *                     {@code ui.modpack.ModpackSettingsDialog}.
 * @param javaPath     the {@code java} binary to run this modpack with; {@code null}/blank means
 *                     "follow the launcher-wide default", which itself may mean the launcher's own
 *                     JVM - see {@code game.JavaRuntimes}. Null on every settings file written
 *                     before this field existed, which is exactly the value that preserves the old
 *                     behaviour, so no migration is needed.
 */
public record ModpackSettings(int memoryMb, String extraJvmArgs, String channel, String javaPath) {

    /** Fallback when a modpack doesn't declare its own {@code ModpackManifest#defaultMemoryMb}. */
    public static final int DEFAULT_MEMORY_MB = 2048;

    public static ModpackSettings defaults() {
        return new ModpackSettings(DEFAULT_MEMORY_MB, "", null, null);
    }

    /**
     * The curator's recommended starting point for a modpack the player has never customized
     * settings for yet - see {@code ModpackManifest#defaultMemoryMb}/{@code #defaultJvmArgs}.
     * {@code extraJvmArgs} may be {@code null} (never set by the curator); normalized to "" to
     * match {@link #extraJvmArgs()}'s "empty string, never null" contract.
     */
    public static ModpackSettings defaults(int memoryMb, String extraJvmArgs) {
        return new ModpackSettings(memoryMb, extraJvmArgs != null ? extraJvmArgs : "", null, null);
    }
}
