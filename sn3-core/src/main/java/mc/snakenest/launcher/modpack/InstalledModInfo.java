package mc.snakenest.launcher.modpack;

/**
 * What {@code ModMetadataReader} could read from a mod jar's own bundled metadata file - the
 * source of truth for {@code ui.modpack.InstalledModsPage}, since it works for every installed
 * mod (including a manually-imported {@code .jar} Modrinth has never heard of), not just ones
 * this launcher itself downloaded. Any field can be {@code null} if the jar had no readable
 * metadata at all or just didn't set that particular field.
 *
 * @param iconPath path of the mod's own bundled icon *inside the jar* (e.g.
 *                 {@code "assets/fabric/icon.png"} or {@code "logo.png"}), {@code null} if the
 *                 jar declared none - not the decoded image itself, since reading the metadata
 *                 file is cheap but decoding an image is not; see
 *                 {@code ModMetadataReader#readIcon}, loaded lazily/async by the UI the same way
 *                 {@code ui.modpack.ModCardView} loads a Modrinth search result's icon.
 */
public record InstalledModInfo(String name, String version, String author, String description, String iconPath) {

    /** Used when no metadata file could be found/parsed at all - the filename is the only thing left to show. */
    public static InstalledModInfo fallback(String filename) {
        return new InstalledModInfo(filename, null, null, null, null);
    }
}
