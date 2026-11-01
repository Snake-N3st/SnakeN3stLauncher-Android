package mc.snakenest.launcher.modpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Folds a pack's version of a file into the player's existing one, for the {@code MERGE} rule of
 * {@link FileMetas}.
 *
 * <p>The direction is deliberate and one-way: the pack's file carries <em>only</em> the settings it
 * needs to impose, so every key it declares wins, and every key it does not mention is left exactly
 * as the player had it. That is the whole point of the rule - a curator who needs to flip one
 * option should not have to flatten a player's controls, resolution and volume to do it.
 *
 * <p>Pure functions over bytes, no I/O: the caller reads and writes, which keeps this directly
 * testable and keeps the atomic-write behaviour in one place ({@code ModpackSyncEngine}).
 */
public final class FileMerger {

    /** Pretty-printed so a merged config stays readable if a player opens it afterwards. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private FileMerger() {
    }

    /**
     * @param existing the player's current file, or {@code null} when they have none yet
     * @param incoming the pack's version
     * @return the bytes to write
     */
    public static byte[] merge(FileMetaRule.MergeFormat format, byte[] existing, byte[] incoming) throws IOException {
        // Nothing to preserve: the pack's file is simply the new file. Also the path taken on a
        // first install, where every merge target is absent.
        if (existing == null || existing.length == 0) {
            return incoming;
        }

        return switch (format) {
            case PROPERTIES -> mergeProperties(existing, incoming);
            case JSON -> mergeJson(existing, incoming);
        };
    }

    /**
     * {@link Properties} rather than line-editing, so quoting and escapes are handled the way the
     * game itself will read them back. The cost is that comments and key order in the player's
     * file are not preserved - acceptable for a generated config, and the alternative is a
     * hand-rolled parser that would get escaping subtly wrong.
     */
    private static byte[] mergeProperties(byte[] existing, byte[] incoming) throws IOException {
        Properties merged = new Properties();

        try (var in = new ByteArrayInputStream(existing)) {
            merged.load(in);
        }

        Properties fromPack = new Properties();

        try (var in = new ByteArrayInputStream(incoming)) {
            fromPack.load(in);
        }

        // The pack's keys win; the player's other keys survive untouched.
        fromPack.forEach(merged::put);

        StringWriter writer = new StringWriter();
        merged.store(writer, "Merged by the SnakeN3st launcher - see .filesmetas");

        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Deep merge of objects. Arrays and scalars are replaced wholesale rather than combined: there
     * is no way to tell whether a curator meant "add to this list" or "this is now the list", and
     * silently appending would grow a player's config without bound across updates.
     */
    private static byte[] mergeJson(byte[] existing, byte[] incoming) throws IOException {
        try {
            JsonElement player = JsonParser.parseString(new String(existing, StandardCharsets.UTF_8));
            JsonElement pack = JsonParser.parseString(new String(incoming, StandardCharsets.UTF_8));

            // Only two objects can be merged key by key; anything else (a top-level array, a
            // scalar) has no shared structure to preserve, so the pack's version simply wins.
            if (!player.isJsonObject() || !pack.isJsonObject()) {
                return incoming;
            }

            JsonObject result = deepMerge(player.getAsJsonObject(), pack.getAsJsonObject());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(GSON.toJson(result).getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (RuntimeException e) {
            // A player's file that is no longer valid JSON has nothing worth preserving, and
            // refusing to update over it would leave the instance stuck.
            throw new IOException("Could not merge JSON: " + e.getMessage(), e);
        }
    }

    private static JsonObject deepMerge(JsonObject player, JsonObject pack) {
        JsonObject result = player.deepCopy();

        for (String key : pack.keySet()) {
            JsonElement packValue = pack.get(key);
            JsonElement playerValue = result.get(key);

            if (packValue.isJsonObject() && playerValue != null && playerValue.isJsonObject()) {
                result.add(key, deepMerge(playerValue.getAsJsonObject(), packValue.getAsJsonObject()));
            } else {
                result.add(key, packValue);
            }
        }

        return result;
    }
}
