package mc.snakenest.launcher.auth;

import com.google.gson.annotations.SerializedName;

/**
 * A launcher client's public branding: name, optional logo URL, and an
 * optional Discord Rich Presence application ID. Fetched before any login
 * happens (unsigned endpoint, see {@code LauncherAuthApiClient#fetchClientInfo})
 * so the login window itself can show the right name/logo.
 *
 * @param discordAppId the Discord "Application ID" configured for this
 *                     client in {@code /admin/launcher-auth} - {@code null}
 *                     if unset, in which case Discord Rich Presence is
 *                     simply not enabled (see {@code discord.DiscordPresenceService}).
 * @param bugReportsEnabled whether this client is allowed to submit crash/bug reports at all.
 *                     Boxed rather than primitive on purpose: {@code null} means "this site is
 *                     older than the field", which has to read as enabled rather than as the
 *                     {@code false} a primitive would silently default to.
 */
public record ClientInfo(String name, String image, @SerializedName("discord_app_id") String discordAppId,
                         @SerializedName("bug_reports_enabled") Boolean bugReportsEnabled) {

    /**
     * Whether to offer bug reporting in the UI at all. The site enforces this independently (a
     * disabled client gets a 403 from {@code /api/launcher-auth/bug-reports} whatever the launcher
     * does), so this only decides whether the player is shown a button that would actually work -
     * it is never the security boundary itself.
     */
    public boolean bugReportsAllowed() {
        return bugReportsEnabled == null || bugReportsEnabled;
    }
}
