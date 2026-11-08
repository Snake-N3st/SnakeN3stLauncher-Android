package mc.snakenest.launcher.modpack;

/**
 * Thrown by {@code LauncherApp#launchGame} when one or more files under {@code mods/} match a
 * blacklisted hash (see {@link ModBlacklistEntry}) - the game is never actually started. Message is
 * already player-facing (lists the blocked file(s) and their reason), same convention as every
 * other exception {@code doInstallAndLaunch} catches and shows directly.
 */
public class ModBlacklistedException extends Exception {
    public ModBlacklistedException(String message) {
        super(message);
    }
}
