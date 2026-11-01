package mc.snakenest.launcher.modpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModBlacklistCacheStoreTest {

    private final ModBlacklistCacheStore store = new ModBlacklistCacheStore();

    @Test
    void loadReturnsEmptyWhenNeverSaved(@TempDir Path instanceDir) throws IOException {
        assertTrue(store.load(instanceDir).isEmpty());
    }

    @Test
    void saveThenLoadRoundTrips(@TempDir Path instanceDir) throws IOException {
        List<ModBlacklistEntry> entries = List.of(
                new ModBlacklistEntry("aa".repeat(32), "Cheat.jar", "Cheat client"),
                new ModBlacklistEntry("bb".repeat(32), "OtherBanned.jar", null)
        );

        store.save(instanceDir, entries);

        Optional<List<ModBlacklistEntry>> reloaded = store.load(instanceDir);
        assertTrue(reloaded.isPresent());
        assertEquals(entries, reloaded.get());
    }

    @Test
    void loadRejectsCorruptJson(@TempDir Path instanceDir) throws IOException {
        Files.writeString(instanceDir.resolve(".sn3-blacklist-cache.json"), "not json{{{");

        assertThrows(IOException.class, () -> store.load(instanceDir));
    }
}
