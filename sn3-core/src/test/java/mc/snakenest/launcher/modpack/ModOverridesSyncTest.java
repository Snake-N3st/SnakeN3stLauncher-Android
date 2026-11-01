package mc.snakenest.launcher.modpack;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModOverridesSyncTest {
    @TempDir Path root;
    @Test void localChoicesSkipDownloadsAndDoNotPretendAnUpstreamUpdateWasApplied() throws Exception {
        Path instance=Files.createDirectory(root.resolve("instance"));
        ModpackFileDownloader source=mock(ModpackFileDownloader.class); ModpackFileDownloader cached=mock(ModpackFileDownloader.class);
        when(source.withBlobCache(any(),any())).thenReturn(cached);
        LocalManifestStore store=new LocalManifestStore();
        String old="0".repeat(64), newer="1".repeat(64);
        store.save(instance,new StoredManifest("old",Map.of("mods/official.jar",old)));
        ModpackManifest target=new ModpackManifest("new",null,null,3,"1.21.1","fabric","0.16",List.of(new ManifestFile("mods/official.jar",newer,3,null)),null,null);
        SyncProgressListener listener=mock(SyncProgressListener.class);
        new ModpackSyncEngine(source,store).sync("pack",instance,target,null,listener,false,Set.of("mods/official.jar"));
        verify(cached).prefetch(eq("pack"),eq(List.of()),isNull());
        verify(cached,never()).download(any(),any(),any(),any());
        assertEquals(old,store.load(instance).orElseThrow().pathToHash().get("mods/official.jar"));
        assertEquals(1,ManifestDiffer.diff(store.load(instance),target,FileMetas.empty(),path->false,false).toDownload().size());
    }
}
