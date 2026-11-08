package mc.snakenest.launcher.modpack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static mc.snakenest.launcher.modpack.FileMetaRule.MergeFormat.JSON;
import static mc.snakenest.launcher.modpack.FileMetaRule.MergeFormat.PROPERTIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class FileMergerTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static Properties propertiesOf(byte[] data) throws Exception {
        Properties p = new Properties();
        try (var in = new ByteArrayInputStream(data)) {
            p.load(in);
        }
        return p;
    }

    private static JsonObject jsonOf(byte[] data) {
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    // --- the point of the whole rule: the player keeps what the pack did not mention -----------

    @Test
    void propertiesMergeKeepsSettingsThePackDoesNotMention() throws Exception {
        byte[] player = bytes("fov=90\nvolume=0.3\nlang=fr\n");
        byte[] pack = bytes("fov=70\n");

        Properties merged = propertiesOf(FileMerger.merge(PROPERTIES, player, pack));

        assertEquals("70", merged.getProperty("fov"), "the pack's value must win");
        assertEquals("0.3", merged.getProperty("volume"), "the player's other settings must survive");
        assertEquals("fr", merged.getProperty("lang"));
    }

    @Test
    void propertiesMergeAddsKeysThePlayerDoesNotHaveYet() throws Exception {
        Properties merged = propertiesOf(FileMerger.merge(PROPERTIES, bytes("a=1\n"), bytes("b=2\n")));

        assertEquals("1", merged.getProperty("a"));
        assertEquals("2", merged.getProperty("b"));
    }

    @Test
    void jsonMergeKeepsSettingsThePackDoesNotMention() throws Exception {
        byte[] player = bytes("{\"fov\":90,\"volume\":0.3,\"keys\":{\"jump\":\"SPACE\",\"crouch\":\"SHIFT\"}}");
        byte[] pack = bytes("{\"fov\":70,\"keys\":{\"jump\":\"J\"}}");

        JsonObject merged = jsonOf(FileMerger.merge(JSON, player, pack));

        assertEquals(70, merged.get("fov").getAsInt());
        assertEquals(0.3, merged.get("volume").getAsDouble(), 1e-9);
        assertEquals("J", merged.getAsJsonObject("keys").get("jump").getAsString(), "nested value replaced");
        assertEquals("SHIFT", merged.getAsJsonObject("keys").get("crouch").getAsString(),
                "a nested key the pack did not mention must survive");
    }

    /**
     * Arrays are replaced rather than concatenated: there is no way to tell "add to this list"
     * from "this is now the list", and appending would grow a config on every single update.
     */
    @Test
    void jsonMergeReplacesArraysWholesale() throws Exception {
        JsonObject merged = jsonOf(FileMerger.merge(JSON,
                bytes("{\"servers\":[\"a\",\"b\",\"c\"]}"),
                bytes("{\"servers\":[\"x\"]}")));

        assertEquals(1, merged.getAsJsonArray("servers").size());
        assertEquals("x", merged.getAsJsonArray("servers").get(0).getAsString());
    }

    // --- degenerate inputs ----------------------------------------------------------------------

    @Test
    void anAbsentPlayerFileMeansTheIncomingFileIsWrittenAsIs() throws Exception {
        byte[] pack = bytes("{\"a\":1}");

        assertArrayEquals(pack, FileMerger.merge(JSON, null, pack));
        assertArrayEquals(pack, FileMerger.merge(JSON, new byte[0], pack));
        assertArrayEquals(pack, FileMerger.merge(PROPERTIES, null, pack));
    }

    @Test
    void nonObjectJsonCannotBeMergedSoThePackWins() throws Exception {
        byte[] pack = bytes("{\"a\":1}");

        assertArrayEquals(pack, FileMerger.merge(JSON, bytes("[1,2,3]"), pack));
        assertArrayEquals(pack, FileMerger.merge(JSON, bytes("\"scalar\""), pack));
    }

    @Test
    void unreadablePlayerJsonIsReportedRatherThanSilentlyDiscarded() {
        assertThrows(java.io.IOException.class,
                () -> FileMerger.merge(JSON, bytes("{ this is not json"), bytes("{\"a\":1}")));
    }
}
