package mc.snakenest.launcher.modpack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestDifferTest {

    /** No .filesmetas rules in these cases, so the predicate only matters for the two legacy paths. */
    private static final java.util.function.Predicate<String> PRESENT = path -> true;
    private static final java.util.function.Predicate<String> ABSENT = path -> false;

    private static ModpackManifest manifest(ManifestFile... files) {
        return new ModpackManifest("1.0.0", null, null, 0, "1.20.4", "vanilla", null, List.of(files), null, ModpackSettings.DEFAULT_MEMORY_MB);
    }

    @Test
    void freshInstallDownloadsEverythingAndDeletesNothing() {
        ModpackManifest target = manifest(
                new ManifestFile("mods/a.jar", "hash-a", 10, null),
                new ManifestFile("mods/b.jar", "hash-b", 20, null)
        );

        SyncPlan plan = ManifestDiffer.diff(Optional.empty(), target, FileMetas.empty(), PRESENT, false);

        assertEquals(2, plan.toDownload().size());
        assertTrue(plan.toDelete().isEmpty());
    }

    @Test
    void identicalManifestProducesAnEmptyPlan() {
        ManifestFile file = new ManifestFile("mods/a.jar", "hash-a", 10, null);
        ModpackManifest target = manifest(file);
        StoredManifest previous = new StoredManifest("1.0.0", Map.of("mods/a.jar", "hash-a"));

        SyncPlan plan = ManifestDiffer.diff(Optional.of(previous), target, FileMetas.empty(), PRESENT, false);

        assertTrue(plan.toDownload().isEmpty());
        assertTrue(plan.toDelete().isEmpty());
    }

    @Test
    void onlyTheChangedFileIsRedownloaded() {
        ModpackManifest target = manifest(
                new ManifestFile("mods/a.jar", "hash-a-v2", 10, null),
                new ManifestFile("mods/b.jar", "hash-b", 20, null)
        );
        StoredManifest previous = new StoredManifest("1.0.0", Map.of(
                "mods/a.jar", "hash-a-v1",
                "mods/b.jar", "hash-b"
        ));

        SyncPlan plan = ManifestDiffer.diff(Optional.of(previous), target, FileMetas.empty(), PRESENT, false);

        assertEquals(List.of(new ManifestFile("mods/a.jar", "hash-a-v2", 10, null)), plan.toDownload());
        assertTrue(plan.toDelete().isEmpty());
    }

    @Test
    void fileRemovedInNewVersionIsDeleted() {
        ModpackManifest target = manifest(new ManifestFile("mods/a.jar", "hash-a", 10, null));
        StoredManifest previous = new StoredManifest("1.0.0", Map.of(
                "mods/a.jar", "hash-a",
                "mods/removed.jar", "hash-removed"
        ));

        SyncPlan plan = ManifestDiffer.diff(Optional.of(previous), target, FileMetas.empty(), PRESENT, false);

        assertTrue(plan.toDownload().isEmpty());
        assertEquals(List.of("mods/removed.jar"), plan.toDelete());
    }

    @Test
    void renamedFileIsDownloadedAtTheNewPathAndDeletedAtTheOldOne() {
        ModpackManifest target = manifest(new ManifestFile("mods/new-name.jar", "hash-a", 10, null));
        StoredManifest previous = new StoredManifest("1.0.0", Map.of("mods/old-name.jar", "hash-a"));

        SyncPlan plan = ManifestDiffer.diff(Optional.of(previous), target, FileMetas.empty(), PRESENT, false);

        assertEquals(List.of(new ManifestFile("mods/new-name.jar", "hash-a", 10, null)), plan.toDownload());
        assertEquals(List.of("mods/old-name.jar"), plan.toDelete());
    }

    @Test
    void protectedFileIsSeededOnFreshInstall() {
        ModpackManifest target = manifest(new ManifestFile("options.txt", "hash-a", 10, null));

        SyncPlan plan = ManifestDiffer.diff(Optional.empty(), target, FileMetas.empty(), ABSENT, false);

        assertEquals(List.of(new ManifestFile("options.txt", "hash-a", 10, null)), plan.toDownload());
    }

    @Test
    void protectedFileIsNeverRedownloadedOnceTrackedEvenIfItsHashChangesUpstream() {
        ModpackManifest target = manifest(new ManifestFile("options.txt", "hash-a-v2", 10, null));
        StoredManifest previous = new StoredManifest("1.0.0", Map.of("options.txt", "hash-a-v1"));

        SyncPlan plan = ManifestDiffer.diff(Optional.of(previous), target, FileMetas.empty(), PRESENT, false);

        assertTrue(plan.toDownload().isEmpty());
    }

    @Test
    void protectedFileIsNeverDeletedEvenIfDroppedFromTheTargetManifest() {
        ModpackManifest target = manifest();
        StoredManifest previous = new StoredManifest("1.0.0", Map.of("servers.dat", "hash-a"));

        SyncPlan plan = ManifestDiffer.diff(Optional.of(previous), target, FileMetas.empty(), PRESENT, false);

        assertTrue(plan.toDelete().isEmpty());
    }

    /**
     * Deliberately the opposite of what this asserted before: a repair used to re-seed player-owned
     * files, silently destroying a server list. It now leaves them alone unless the player asks -
     * see the test below and {@code LauncherApp#repairModpack}.
     */
    @Test
    void repairAloneNoLongerOverwritesAPlayerOwnedFile() {
        ModpackManifest target = manifest(new ManifestFile("servers.dat", "hash-a-v2", 10, null));

        // "Réparer" clears the locally-recorded manifest before diffing (see
        // LocalManifestStore#clear) - simulated here the same way, Optional.empty().
        SyncPlan plan = ManifestDiffer.diff(Optional.empty(), target, FileMetas.empty(), PRESENT, false);

        assertTrue(plan.toDownload().isEmpty());
    }

    @Test
    void repairRestoresAPlayerOwnedFileWhenThePlayerOptsIn() {
        ModpackManifest target = manifest(new ManifestFile("servers.dat", "hash-a-v2", 10, null));

        SyncPlan plan = ManifestDiffer.diff(Optional.empty(), target, FileMetas.empty(), PRESENT, true);

        assertEquals(List.of(new ManifestFile("servers.dat", "hash-a-v2", 10, null)), plan.toDownload());
    }
}
