package mc.snakenest.launcher.auth;

import com.google.gson.annotations.SerializedName;

/**
 * One distribution channel, as listed by {@code GET /api/launcher-auth/channels} - see
 * {@code Api\ChannelListController} in the sibling {@code SnakeN3stLogin} repo. Used to populate
 * the channel picker in {@code ui.settings.SettingsPage} (the launcher's own channel) and
 * {@code ui.modpack.ModpackSettingsDialog} (a modpack's channel) - the same list serves both,
 * channels aren't scoped to one resource kind.
 *
 * @param restricted whether this channel requires the player to be eligible (a specific role) -
 *                   shown as a hint in the picker; the actual enforcement happens server-side
 *                   when a release/version is actually fetched, this is never the security
 *                   boundary itself.
 */
public record ChannelInfo(String name, String parent, @SerializedName("is_default") boolean isDefault, boolean restricted) {
}
