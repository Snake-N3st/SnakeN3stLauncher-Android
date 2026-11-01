package mc.snakenest.launcher.modpack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.snakenest.launcher.util.Log;



import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Best-effort reader for a mod jar's own bundled metadata ({@code fabric.mod.json} /
 * {@code META-INF/mods.toml} / {@code META-INF/neoforge.mods.toml}) - used by
 * {@code ui.modpack.InstalledModsPage} to show a mod's real name/version/author regardless of
 * whether it's known to Modrinth (a manually-imported {@code .jar} might not be on Modrinth at
 * all, or a search result might never have been used to install it). Tries every known metadata
 * file name rather than picking one based on the modpack's declared loader - more resilient
 * against a jar for a different loader than expected, or a non-mod file that ended up in
 * {@code mods/} by mistake.
 */
public final class ModMetadataReader {

    private ModMetadataReader() {
    }

    public static InstalledModInfo read(Path jarFile) {
        try (ZipFile zip = new ZipFile(jarFile.toFile())) {
            InstalledModInfo fabric = readFabric(zip);
            if (fabric != null) {
                return fabric;
            }
            InstalledModInfo forge = readToml(zip, "META-INF/mods.toml");
            if (forge != null) {
                return forge;
            }
            InstalledModInfo neoforge = readToml(zip, "META-INF/neoforge.mods.toml");
            if (neoforge != null) {
                return neoforge;
            }
        } catch (Exception e) {
            Log.warn(ModMetadataReader.class, "Could not read metadata from " + jarFile.getFileName() + ": " + e.getMessage());
        }
        return InstalledModInfo.fallback(jarFile.getFileName().toString());
    }

    /**
     * Reads the mod's own bundled icon (see {@link InstalledModInfo#iconPath}) - a separate call
     * from {@link #read}, since decoding an image is heavier than scanning a small metadata file
     * and the UI only needs it once a row is actually about to be shown (same "load lazily, off
     * the EDT" reasoning as {@code ui.common.RemoteImages} for a Modrinth search result's icon).
     * Best-effort: returns {@code null} on literally any failure rather than throwing, same as
     * {@code RemoteImages.tryLoad}.
     */
    public static byte[] readIcon(Path jarFile, String iconPath) {
        if (iconPath == null) {
            return null;
        }
        try (ZipFile zip = new ZipFile(jarFile.toFile())) {
            ZipEntry entry = zip.getEntry(iconPath);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            Log.warn(ModMetadataReader.class, "Could not read icon " + iconPath + " from " + jarFile.getFileName() + ": " + e.getMessage());
            return null;
        }
    }

    private static InstalledModInfo readFabric(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("fabric.mod.json");
        if (entry == null) {
            return null;
        }
        JsonObject json;
        try (InputStream in = zip.getInputStream(entry)) {
            json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
        String name = optString(json, "name");
        String id = optString(json, "id");
        return new InstalledModInfo(name != null ? name : id, optString(json, "version"),
                firstAuthor(json.get("authors")), optString(json, "description"), fabricIconPath(json.get("icon")));
    }

    /** Per the Fabric mod.json spec, {@code icon} is either a single path string, or an object mapping size -> path - picks the largest size available in the latter case. */
    private static String fabricIconPath(JsonElement icon) {
        if (icon == null || icon.isJsonNull()) {
            return null;
        }
        if (icon.isJsonPrimitive()) {
            return icon.getAsString();
        }
        if (icon.isJsonObject()) {
            return icon.getAsJsonObject().entrySet().stream()
                    .max(Comparator.comparingInt(e -> parseIntOrZero(e.getKey())))
                    .map(Map.Entry::getValue)
                    .map(JsonElement::getAsString)
                    .orElse(null);
        }
        return null;
    }

    private static int parseIntOrZero(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String firstAuthor(JsonElement authors) {
        if (authors == null || !authors.isJsonArray() || authors.getAsJsonArray().isEmpty()) {
            return null;
        }
        JsonElement first = authors.getAsJsonArray().get(0);
        if (first.isJsonPrimitive()) {
            return first.getAsString();
        }
        if (first.isJsonObject() && first.getAsJsonObject().has("name")) {
            return first.getAsJsonObject().get("name").getAsString();
        }
        return null;
    }

    private static String optString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    /**
     * Not a real TOML parser - {@code mods.toml}'s {@code [[mods]]} table is, in every real file
     * seen, just single-line {@code key = "value"} pairs, so a per-field regex scan covers it
     * without pulling in a whole TOML library dependency for what's ultimately a best-effort
     * display feature. Takes the first match of each field, which is the {@code [[mods]]} block's
     * own value in practice (dependency tables using the same key names come later in the file).
     */
    private static InstalledModInfo readToml(ZipFile zip, String path) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) {
            return null;
        }
        String content;
        try (InputStream in = zip.getInputStream(entry)) {
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String modId = tomlField(content, "modId");
        String displayName = tomlField(content, "displayName");
        String version = tomlField(content, "version");
        String description = tomlField(content, "description");
        String authors = tomlField(content, "authors");
        // Unlike modId/version/displayName, logoFile is a top-level key (once per jar, "in the
        // root of the mod JAR" per the file's own comment), not inside [[mods]] - same tolerant
        // first-match scan still finds it correctly since there's only ever one.
        String logoFile = tomlField(content, "logoFile");
        String name = displayName != null ? displayName : modId;
        if (name == null) {
            return null;
        }
        // FML's own placeholder, substituted by the loader at runtime - meaningless to show as-is.
        if ("${file.jarVersion}".equals(version)) {
            version = null;
        }
        return new InstalledModInfo(name, version, authors, description, logoFile);
    }

    /**
     * The triple-quoted alternatives use {@link Pattern#DOTALL} so a multi-line
     * {@code description='''...\n...\n'''} (common for anything longer than one sentence) is
     * captured whole, not just the (near-empty) rest of its opening line; the single-quote
     * alternatives exclude newlines so they don't accidentally swallow the next several fields
     * if a value is unquoted or the regex otherwise fails to anchor on a real closing quote.
     */
    private static final Pattern TOML_STRING = Pattern.compile(
            "'''(.*?)'''|\"\"\"(.*?)\"\"\"|\"([^\"\\n]*)\"|'([^'\\n]*)'", Pattern.DOTALL);

    private static String tomlField(String content, String key) {
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*").matcher(content);
        if (!matcher.find()) {
            return null;
        }
        Matcher value = TOML_STRING.matcher(content);
        value.region(matcher.end(), content.length());
        if (!value.lookingAt()) {
            return null;
        }
        for (int i = 1; i <= value.groupCount(); i++) {
            if (value.group(i) != null) {
                return value.group(i).trim();
            }
        }
        return null;
    }
}
