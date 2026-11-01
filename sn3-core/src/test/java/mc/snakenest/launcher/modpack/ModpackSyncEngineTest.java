package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.crypto.Ed25519KeyPair;
import mc.snakenest.launcher.net.HttpJsonClient;
import mc.snakenest.launcher.net.RawResponse;
import mc.snakenest.launcher.util.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModpackSyncEngineTest {

    private static final String SEED_HEX = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

    @Mock
    private ModpackApiClient api;

    @Mock
    private HttpJsonClient http;

    private final PlayerSession session = new PlayerSession(Ed25519KeyPair.fromSeedHex(SEED_HEX), 1L, "client-1");
    private final List<String> events = new ArrayList<>();

    private final SyncProgressListener listener = new SyncProgressListener() {
        @Override
        public void onFileStarted(String path, long size) {
            events.add("started:" + path);
        }

        @Override
        public void onFileDone(String path) {
            events.add("done:" + path);
        }

        @Override
        public void onDeleted(String path) {
            events.add("deleted:" + path);
        }
    };

    private ModpackSyncEngine engine() {
        return new ModpackSyncEngine(new ModpackFileDownloader(api, http), new LocalManifestStore());
    }

    private static RawResponse rawResponseOf(String content) {
        return new RawResponse(200, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void freshSyncDownloadsEveryFileAndRecordsTheManifest(@TempDir Path instanceDir) throws Exception {
        String content = "mod-a-content";
        String hash = Sha256.hex(content.getBytes(StandardCharsets.UTF_8));
        when(api.downloadBlobs("aventure", List.of(hash), session))
                .thenReturn(rawResponseOf("SN3B1\n" + hash + " " + content.length() + "\n" + content));

        ModpackManifest manifest = new ModpackManifest("1.0.0", null, null, content.length(), "1.20.4", "forge", "49.0.30",
                List.of(new ManifestFile("mods/a.jar", Sha256.hex(content.getBytes(StandardCharsets.UTF_8)), content.length(), null)),
                null, ModpackSettings.DEFAULT_MEMORY_MB);

        engine().sync("aventure", instanceDir, manifest, session, listener);

        assertEquals(content, Files.readString(instanceDir.resolve("mods/a.jar")));
        assertTrue(events.contains("started:mods/a.jar"));
        assertTrue(events.contains("done:mods/a.jar"));

        var stored = new LocalManifestStore().load(instanceDir);
        assertTrue(stored.isPresent());
        assertEquals("1.0.0", stored.get().version());
    }

    @Test
    void resyncingAnUnchangedManifestDownloadsNothing(@TempDir Path instanceDir) throws Exception {
        String content = "mod-a-content";
        String hash = Sha256.hex(content.getBytes(StandardCharsets.UTF_8));
        ManifestFile file = new ManifestFile("mods/a.jar", hash, content.length(), null);
        ModpackManifest manifest = new ModpackManifest("1.0.0", null, null, content.length(), "1.20.4", "vanilla", null, List.of(file),
                null, ModpackSettings.DEFAULT_MEMORY_MB);

        Files.createDirectories(instanceDir.resolve("mods"));
        Files.writeString(instanceDir.resolve("mods/a.jar"), content);
        new LocalManifestStore().save(instanceDir, StoredManifest.of(manifest));

        engine().sync("aventure", instanceDir, manifest, session, listener);

        assertTrue(events.isEmpty());
    }

    @Test
    void removedFileIsDeletedAndItsNowEmptyDirectoryIsPruned(@TempDir Path instanceDir) throws Exception {
        Files.createDirectories(instanceDir.resolve("mods/extra"));
        Files.writeString(instanceDir.resolve("mods/extra/old.jar"), "old");
        StoredManifest previous = new StoredManifest("0.9.0", java.util.Map.of("mods/extra/old.jar", Sha256.hex("old".getBytes(StandardCharsets.UTF_8))));
        new LocalManifestStore().save(instanceDir, previous);

        ModpackManifest manifest = new ModpackManifest("1.0.0", null, null, 0, "1.20.4", "vanilla", null, List.of(),
                null, ModpackSettings.DEFAULT_MEMORY_MB);

        engine().sync("aventure", instanceDir, manifest, session, listener);

        assertFalse(Files.exists(instanceDir.resolve("mods/extra/old.jar")));
        assertFalse(Files.exists(instanceDir.resolve("mods/extra")));
        assertTrue(events.contains("deleted:mods/extra/old.jar"));
    }

    @Test
    void aFileWithAUrlIsFetchedFromItsOriginServerNotFromTheBlobApi(@TempDir Path instanceDir) throws Exception {
        String content = "jar-bytes";
        String hash = Sha256.hex(content.getBytes(StandardCharsets.UTF_8));
        when(http.getRaw(java.net.URI.create("https://cdn.example.com/voicechat.jar")))
                .thenReturn(rawResponseOf(content));

        ModpackManifest manifest = new ModpackManifest("1.0.0", null, null, content.length(), "1.20.4", "fabric", null,
                List.of(new ManifestFile("mods/voicechat.jar", hash, content.length(), "https://cdn.example.com/voicechat.jar")),
                null, ModpackSettings.DEFAULT_MEMORY_MB);

        engine().sync("aventure", instanceDir, manifest, session, listener);

        assertEquals(content, Files.readString(instanceDir.resolve("mods/voicechat.jar")));
        org.mockito.Mockito.verifyNoInteractions(api);
    }
}
