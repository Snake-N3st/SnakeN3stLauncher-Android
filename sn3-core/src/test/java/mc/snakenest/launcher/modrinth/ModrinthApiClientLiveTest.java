package mc.snakenest.launcher.modrinth;

import mc.snakenest.launcher.net.HttpJsonClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hits the real Modrinth API to catch Gson field-mapping mistakes that a mocked test can't -
 * disabled by default (network-dependent, not run in CI), run manually with
 * {@code -Dtest=ModrinthApiClientLiveTest -Dsurefire.failIfNoSpecifiedTests=false} after
 * removing {@code @Disabled} temporarily.
 */
@Disabled("network-dependent, manual verification only")
class ModrinthApiClientLiveTest {

    private final ModrinthApiClient client = new ModrinthApiClient(new HttpJsonClient());

    @Test
    void searchReturnsParsedHits() throws Exception {
        List<ModrinthSearchHit> hits = client.search("jei", 5);
        assertFalse(hits.isEmpty());
        ModrinthSearchHit jei = hits.stream().filter(h -> h.projectId().equals("u6dRKJwZ")).findFirst().orElseThrow();
        assertTrue(jei.categories().contains("forge"));
        assertTrue(jei.versions().contains("1.20.1"));
        assertTrue(jei.isCompatible("1.20.1", "forge"));
        assertFalse(jei.isCompatible("1.0", "forge"));
    }

    @Test
    void versionsReturnsCompatibleFiles() throws Exception {
        List<ModrinthVersion> versions = client.versions("u6dRKJwZ");
        assertFalse(versions.isEmpty());
        ModrinthVersion v = versions.stream().filter(x -> x.id().equals("hVXQAD0D")).findFirst().orElseThrow();
        assertEquals("15.49.0.188", v.versionNumber());
        assertTrue(v.isCompatible("1.20.1", "forge"));
        assertEquals("21f781835beac0d638d8a27049e93c56d166b3dc", v.primaryFile().orElseThrow().sha1());
    }

    @Test
    void lookupByHashesResolvesKnownHash() throws Exception {
        Map<String, ModrinthVersion> found = client.lookupByHashes(
                List.of("21f781835beac0d638d8a27049e93c56d166b3dc"));
        assertEquals(1, found.size());
        assertEquals("hVXQAD0D", found.get("21f781835beac0d638d8a27049e93c56d166b3dc").id());

        Map<String, ModrinthVersion> notFound = client.lookupByHashes(List.of("00".repeat(20)));
        assertTrue(notFound.isEmpty());
    }

    @Test
    void versionDependenciesAreParsed() throws Exception {
        // Iris really does depend on Sodium - confirmed for real before writing ModrinthDependency.
        List<ModrinthVersion> versions = client.versions("YL57xq9U");
        ModrinthVersion withSodiumDep = versions.stream()
                .filter(v -> v.requiredDependencyProjectIds().contains("AANobbMI"))
                .findFirst().orElseThrow();
        assertTrue(withSodiumDep.requiredDependencyProjectIds().contains("AANobbMI"));
    }

    @Test
    void getProjectReturnsTitle() throws Exception {
        ModrinthProjectSummary sodium = client.getProject("AANobbMI");
        assertEquals("Sodium", sodium.title());
        assertEquals("sodium", sodium.slug());
    }
}
