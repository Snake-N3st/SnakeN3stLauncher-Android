package mc.snakenest.launcher.modpack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers how {@link FileMetas} rules bend the plain hash diff. */
class ManifestDifferRulesTest {

    private static ManifestFile file(String path, String hash) {
        return new ManifestFile(path, hash, 10, null);
    }

    private static ModpackManifest manifestOf(ManifestFile... files) {
        return new ModpackManifest("1.0", null, "", 0, "1.21.1", "vanilla", null, List.of(files), null, null);
    }

    private static Optional<StoredManifest> applied(ManifestFile... files) {
        return Optional.of(StoredManifest.of(manifestOf(files)));
    }

    /** Every path is considered present on disk. */
    private static final Predicate<String> ALL_PRESENT = path -> true;
    private static final Predicate<String> NONE_PRESENT = path -> false;

    private static Set<String> paths(List<ManifestFile> files) {
        return files.stream().map(ManifestFile::path).collect(java.util.stream.Collectors.toSet());
    }

    // --- OVERRIDE -------------------------------------------------------------------------------

    /**
     * The point of OVERRIDE: an unchanged hash would normally mean "skip", which would leave a
     * locally edited copy in place forever.
     */
    @Test
    void overrideRedownloadsEvenWhenTheHashIsUnchanged() {
        ManifestFile pinned = file("config/pack.cfg", "same");
        SyncPlan plan = ManifestDiffer.diff(applied(pinned), manifestOf(pinned),
                FileMetas.parse("config/pack.cfg = OVERRIDE"), ALL_PRESENT, false);

        assertEquals(Set.of("config/pack.cfg"), paths(plan.toDownload()));
    }

    @Test
    void withoutARuleAnUnchangedFileIsSkipped() {
        ManifestFile pinned = file("config/pack.cfg", "same");
        SyncPlan plan = ManifestDiffer.diff(applied(pinned), manifestOf(pinned),
                FileMetas.empty(), ALL_PRESENT, false);

        assertTrue(plan.toDownload().isEmpty());
    }

    // --- DEFAULT --------------------------------------------------------------------------------

    @Test
    void defaultSeedsWhenAbsentAndNeverAgain() {
        ManifestFile servers = file("servers.dat", "v1");

        SyncPlan seeding = ManifestDiffer.diff(Optional.empty(), manifestOf(servers),
                FileMetas.parse("servers.dat = DEFAULT"), NONE_PRESENT, false);
        assertEquals(Set.of("servers.dat"), paths(seeding.toDownload()), "absent: must be seeded");

        SyncPlan later = ManifestDiffer.diff(Optional.empty(), manifestOf(file("servers.dat", "v2")),
                FileMetas.parse("servers.dat = DEFAULT"), ALL_PRESENT, false);
        assertTrue(later.toDownload().isEmpty(), "present: must never be overwritten, even on a new hash");
    }

    /**
     * The case that made "existing on disk" the test rather than "tracked in the manifest": a
     * repair clears the recorded manifest, so a manifest-based check would restore the file and
     * destroy the player's server list - the exact behaviour this rule forbids.
     */
    @Test
    void defaultSurvivesARepairThatClearedTheRecordedManifest() {
        SyncPlan plan = ManifestDiffer.diff(Optional.empty(), manifestOf(file("servers.dat", "v1")),
                FileMetas.parse("servers.dat = DEFAULT"), ALL_PRESENT, false);

        assertTrue(plan.toDownload().isEmpty());
    }

    @Test
    void defaultIsRestoredOnlyWhenThePlayerAsksForIt() {
        SyncPlan plan = ManifestDiffer.diff(Optional.empty(), manifestOf(file("servers.dat", "v1")),
                FileMetas.parse("servers.dat = DEFAULT"), ALL_PRESENT, true);

        assertEquals(Set.of("servers.dat"), paths(plan.toDownload()));
    }

    // --- MERGE ----------------------------------------------------------------------------------

    @Test
    void mergeProducesAMergeActionRatherThanAnOverwrite() {
        SyncPlan plan = ManifestDiffer.diff(applied(file("config/c.json", "old")),
                manifestOf(file("config/c.json", "new")),
                FileMetas.parse("config/c.json = MERGE JSON"), ALL_PRESENT, false);

        assertTrue(plan.toDownload().isEmpty(), "must not be overwritten");
        assertEquals(1, plan.toMerge().size());
        assertEquals(FileMetaRule.MergeFormat.JSON, plan.toMerge().get(0).format());
    }

    @Test
    void mergeIsAPlainWriteWhenThePlayerHasNoCopyYet() {
        SyncPlan plan = ManifestDiffer.diff(Optional.empty(), manifestOf(file("config/c.json", "new")),
                FileMetas.parse("config/c.json = MERGE JSON"), NONE_PRESENT, false);

        assertEquals(Set.of("config/c.json"), paths(plan.toDownload()));
        assertTrue(plan.toMerge().isEmpty());
    }

    @Test
    void mergeDoesNothingWhenThePackFileIsUnchanged() {
        ManifestFile same = file("config/c.json", "same");
        SyncPlan plan = ManifestDiffer.diff(applied(same), manifestOf(same),
                FileMetas.parse("config/c.json = MERGE JSON"), ALL_PRESENT, false);

        assertTrue(plan.toDownload().isEmpty());
        assertTrue(plan.toMerge().isEmpty());
    }

    // --- deletion --------------------------------------------------------------------------------

    @Test
    void playerOwnedFilesAreNeverDeletedWhenThePackStopsShippingThem() {
        SyncPlan plan = ManifestDiffer.diff(
                applied(file("servers.dat", "v1"), file("config/c.json", "v1"), file("mods/old.jar", "v1")),
                manifestOf(),
                FileMetas.parse("servers.dat = DEFAULT\nconfig/c.json = MERGE JSON"), ALL_PRESENT, false);

        assertEquals(List.of("mods/old.jar"), plan.toDelete());
    }

    // --- backwards compatibility ------------------------------------------------------------------

    /**
     * Packs published before the convention existed rely on these two paths being protected; they
     * must keep behaving identically when no .filesmetas is shipped.
     */
    @Test
    void theTwoLegacyPathsStayProtectedWithoutAnyRules() {
        SyncPlan plan = ManifestDiffer.diff(Optional.empty(),
                manifestOf(file("options.txt", "v1"), file("servers.dat", "v1")),
                FileMetas.empty(), ALL_PRESENT, false);

        assertTrue(plan.toDownload().isEmpty());
    }

    @Test
    void anExplicitRuleWinsOverTheLegacyProtection() {
        SyncPlan plan = ManifestDiffer.diff(Optional.empty(),
                manifestOf(file("options.txt", "v1")),
                FileMetas.parse("options.txt = OVERRIDE"), ALL_PRESENT, false);

        assertEquals(Set.of("options.txt"), paths(plan.toDownload()));
        assertFalse(plan.toDownload().isEmpty());
    }
}
