package mc.snakenest.launcher.modpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModsMetaStoreTest {

    private final ModsMetaStore store = new ModsMetaStore();

    @Test
    void loadReturnsEmptyMapWhenNeverSaved(@TempDir Path instanceDir) throws IOException {
        assertTrue(store.load(instanceDir).isEmpty());
    }

    @Test
    void saveThenLoadRoundTrips(@TempDir Path instanceDir) throws IOException {
        Map<String, ModMeta> meta = Map.of(
                "jei-1.20.1-forge-15.49.0.188.jar", new ModMeta("aa".repeat(20), "u6dRKJwZ", "hVXQAD0D"),
                "MyImportedMod.jar", new ModMeta("bb".repeat(20), null, null)
        );

        store.save(instanceDir, meta);

        assertEquals(meta, store.load(instanceDir));
    }

    @Test
    void loadRejectsCorruptJson(@TempDir Path instanceDir) throws IOException {
        Files.writeString(instanceDir.resolve("mods-meta.json"), "not json{{{");

        assertThrows(IOException.class, () -> store.load(instanceDir));
    }
}
