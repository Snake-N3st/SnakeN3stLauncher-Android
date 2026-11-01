package mc.snakenest.launcher.modpack;

/**
 * One entry of {@code GET /api/modpacks/{slug}/blacklist} - global entries and this modpack's own
 * already merged server-side (see the site's {@code Modpack::effectiveBlacklist()}). {@code hash}
 * (SHA-256, hex, same convention as {@link ManifestFile#hash()}) is the real matching key -
 * {@code filename}/{@code reason} are shown to the player if this entry blocks a launch, never used
 * for matching.
 */
public record ModBlacklistEntry(String hash, String filename, String reason) {
}
