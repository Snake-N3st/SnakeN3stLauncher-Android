package mc.snakenest.launcher.modpack;

/**
 * {@code 404} with {@code code: "no_channel_version"}: the requested distribution channel (and
 * every channel it falls back to, including the site's default) has no version of this modpack
 * at all - e.g. the channel's only version was deleted, or {@code ModpackSettings#channel} was
 * hand-edited to a channel that no longer exists. Distinguished from a plain
 * {@link ModpackNotFoundException} so the caller can prompt the player to pick a different
 * channel instead of showing a generic "not found" error - see
 * {@code ui.modpack.ModpackDetailPage}.
 */
public final class ModpackNoChannelVersionException extends ModpackApiException {

    public ModpackNoChannelVersionException(String message) {
        super(message);
    }
}
