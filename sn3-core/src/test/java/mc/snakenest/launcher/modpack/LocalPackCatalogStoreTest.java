package mc.snakenest.launcher.modpack;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalPackCatalogStoreTest {
    @TempDir Path root;
    private LocalPackCatalogStore store() { return new LocalPackCatalogStore(root.resolve("catalogue.json"),"https://snake-n3st.fr/"); }
    private ModpackSummary pack(String slug) { return new ModpackSummary(slug,"Pack "+slug,"Description",null,false,"1",2); }
    private ModpackManifest manifest() { return new ModpackManifest("1",null,null,2,"1.21.1","fabric","0.16",List.of(),null,null); }
    @Test void missingRemotePacksStayVisibleOnlyWhileActuallyInstalled() throws Exception {
        store().mergeInstalled(List.of(pack("old"),pack("live")),Set.of("old"));
        store().saveManifest("old",manifest());
        var merged=store().mergeInstalled(List.of(pack("live")),Set.of("old"));
        assertEquals(List.of("live","old"),merged.stream().map(ModpackSummary::slug).toList());
        assertEquals(Set.of("old"),store().removedSlugs(Set.of("old")));
        assertEquals(List.of("live"),store().mergeInstalled(List.of(pack("live")),Set.of()).stream().map(ModpackSummary::slug).toList());
    }
    @Test void loadingOfflineCannotTurnAnUnknownCatalogueIntoADeletion() throws Exception {
        store().rememberSummary(pack("local")); store().saveManifest("local",manifest());
        assertTrue(store().removedSlugs(Set.of("local")).isEmpty());
        store().saveRemote(List.of(pack("local"))); assertEquals(1,store().localSummaries(Set.of("local")).size());
        // The caller encountered a network error: it reads the cache and NEVER saves a false empty response.
        assertTrue(store().removedSlugs(Set.of("local")).isEmpty());
        store().saveRemote(List.of()); assertEquals(Set.of("local"),store().removedSlugs(Set.of("local")));
    }
    @Test void packsRestoredOnTheSiteLoseTheRemovedMarker() throws Exception {
        store().saveRemote(List.of()); assertEquals(Set.of("pack"),store().removedSlugs(Set.of("pack")));
        store().saveRemote(List.of(pack("pack"))); assertTrue(store().removedSlugs(Set.of("pack")).isEmpty());
    }
    @Test void oldInstallationsWithoutCachedMetadataAreStillListed() throws Exception {
        var local=store().localSummaries(Set.of("legacy"));
        assertEquals(1,local.size()); assertEquals("legacy",local.get(0).slug());
        assertTrue(store().loadManifest("legacy").isEmpty());
    }
    @Test void installedMetadataAndPerPlayerNamesSurviveRestartAndStayIsolatedBySite() throws Exception {
        store().saveManifest("pack",manifest()); store().cacheUsername(1,"Alice"); store().cacheUsername(2,"Bob");
        assertEquals(manifest(),store().loadManifest("pack").orElseThrow());
        assertEquals("Alice",store().loadUsername(1).orElseThrow()); assertEquals("Bob",store().loadUsername(2).orElseThrow());
        LocalPackCatalogStore other=new LocalPackCatalogStore(root.resolve("catalogue.json"),"https://other.example");
        assertTrue(other.loadUsername(1).isEmpty()); assertTrue(other.loadManifest("pack").isEmpty());
    }
    @Test void invalidIdentitiesCannotAlterTheCacheOrTraversePaths() throws Exception {
        store().rememberSummary(pack("safe"));
        assertThrows(IllegalArgumentException.class,()->store().saveManifest("../unsafe",manifest()));
        assertThrows(IllegalArgumentException.class,()->store().cacheUsername(1,"wrong name"));
        assertThrows(IllegalArgumentException.class,()->store().saveRemote(List.of(pack("safe"),pack("safe"))));
        assertEquals(List.of("safe"),store().loadSummaries().stream().map(ModpackSummary::slug).toList());
    }
    @Test void uninstallDropsTheLocalSnapshotWithoutHidingAnAvailableRemotePack() throws Exception {
        store().saveRemote(List.of(pack("pack"))); store().saveManifest("pack",manifest()); store().removeInstalled("pack");
        assertTrue(store().loadManifest("pack").isEmpty()); assertEquals(1,store().loadSummaries().size());
        assertTrue(store().localSummaries(Set.of()).isEmpty());
    }
}
