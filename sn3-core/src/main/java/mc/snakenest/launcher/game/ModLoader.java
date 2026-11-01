package mc.snakenest.launcher.game;

/**
 * A modpack version's mod loader, as declared by the site's
 * {@code mc_version}/{@code loader}/{@code loader_version} manifest fields.
 * One enum, shared between manifest parsing ({@code modpack}) and game
 * installation/launch ({@code game}), so the four accepted values are never
 * spelled out as raw strings in more than one place.
 */
public enum ModLoader {
    VANILLA,
    FORGE,
    FABRIC,
    NEOFORGE,
    /** Forward-compatible fallback for a value this launcher build doesn't recognize yet. */
    UNKNOWN;

    public static ModLoader fromApiValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return switch (value.toLowerCase()) {
            case "vanilla" -> VANILLA;
            case "forge" -> FORGE;
            case "fabric" -> FABRIC;
            case "neoforge" -> NEOFORGE;
            default -> UNKNOWN;
        };
    }
}
