package mc.snakenest.launcher.auth;

/**
 * The authenticated player's account details, fetched once (at login and at
 * every startup) via {@code LauncherAuthApiClient#fetchPlayerInfo} rather
 * than on-demand when the account popover is opened - see
 * {@code LAUNCHER_INTEGRATION.md} section 6.
 */
public record PlayerInfo(String username, String role, String email, String avatar) {
}
