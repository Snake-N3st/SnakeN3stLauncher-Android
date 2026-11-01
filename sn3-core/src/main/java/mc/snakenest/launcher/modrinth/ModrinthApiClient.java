package mc.snakenest.launcher.modrinth;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import mc.snakenest.launcher.net.HttpJsonClient;
import mc.snakenest.launcher.net.JsonResponse;
import mc.snakenest.launcher.net.Uris;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for Modrinth's public v2 API (<a href="https://docs.modrinth.com/api/">docs</a>), used by
 * the mod manager to search/install/identify mods - see {@code ui.modpack.ModManagerDialog}. Public
 * read endpoints, no auth token or request signature needed, unlike {@code ModpackApiClient}.
 *
 * <p>Response shapes below were confirmed against the real API (not just the docs) before writing
 * these DTOs - in particular that loader filtering uses the {@code categories:<loader>} facet (not
 * {@code loaders:<loader>}), that {@code /project/{id}/version} is singular, and that
 * {@code /version_files} defaults its {@code algorithm} to {@code sha1} and returns a
 * {@code Map<hash, ModrinthVersion>} (absent keys for unknown hashes, never a 404).
 */
public final class ModrinthApiClient {

    private static final URI BASE = URI.create("https://api.modrinth.com/v2/");
    private static final String MOD_FACET = "[[\"project_type:mod\"]]";

    private final HttpJsonClient http;

    public ModrinthApiClient(HttpJsonClient http) {
        this.http = http;
    }

    public List<ModrinthSearchHit> search(String query, int limit) throws IOException, InterruptedException, ModrinthApiException {
        return search(query, limit, "relevance");
    }

    /**
     * The mod manager's default view before the player has typed anything - an empty query
     * ranked by {@code index=downloads} instead of Modrinth's usual relevance ranking, which is
     * meaningless with no search text (verified for real: an empty {@code query} plus
     * {@code index=downloads} returns Fabric API first, not an arbitrary/empty result).
     */
    public List<ModrinthSearchHit> browsePopular(int limit) throws IOException, InterruptedException, ModrinthApiException {
        return search("", limit, "downloads");
    }

    private List<ModrinthSearchHit> search(String query, int limit, String index) throws IOException, InterruptedException, ModrinthApiException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", query);
        params.put("limit", String.valueOf(limit));
        params.put("index", index);
        params.put("facets", MOD_FACET);
        URI uri = Uris.withQuery(BASE.resolve("search"), params);
        JsonResponse response = http.get(uri);
        requireSuccess(response, "searching Modrinth for \"" + query + "\"");
        return response.as(SearchResponse.class).hits();
    }

    /** Every published version of a project, most recent first, as Modrinth returns them. */
    public List<ModrinthVersion> versions(String projectId) throws IOException, InterruptedException, ModrinthApiException {
        URI uri = BASE.resolve("project/" + projectId + "/version");
        JsonResponse response = http.get(uri);
        requireSuccess(response, "listing versions of Modrinth project " + projectId);
        return response.as(new TypeToken<List<ModrinthVersion>>() {
        });
    }

    /** Just enough to show a missing dependency's real name in a prompt instead of a bare project id. */
    public ModrinthProjectSummary getProject(String projectId) throws IOException, InterruptedException, ModrinthApiException {
        URI uri = BASE.resolve("project/" + projectId);
        JsonResponse response = http.get(uri);
        requireSuccess(response, "fetching Modrinth project " + projectId);
        return response.as(ModrinthProjectSummary.class);
    }

    /**
     * Identifies already-installed mods by their file hash, so the mod manager can tell which
     * Modrinth project/version a file in {@code mods/} corresponds to without guessing from its
     * name. Hashes not known to Modrinth are simply absent from the result map.
     *
     * @param sha1Hashes hex-encoded SHA-1 hashes - see {@code util.Sha1}, not {@code Sha256}
     */
    public Map<String, ModrinthVersion> lookupByHashes(List<String> sha1Hashes) throws IOException, InterruptedException, ModrinthApiException {
        if (sha1Hashes.isEmpty()) {
            return Map.of();
        }
        URI uri = BASE.resolve("version_files");
        JsonResponse response = http.postJson(uri, new HashLookupRequest(sha1Hashes, "sha1"));
        requireSuccess(response, "looking up " + sha1Hashes.size() + " mod hash(es) on Modrinth");
        return response.as(new TypeToken<Map<String, ModrinthVersion>>() {
        });
    }

    private void requireSuccess(JsonResponse response, String action) throws ModrinthApiException {
        if (!response.isSuccess()) {
            throw new ModrinthApiException("Modrinth error " + response.statusCode() + " while " + action);
        }
    }

    private record HashLookupRequest(List<String> hashes, String algorithm) {
    }

    private record SearchResponse(
            List<ModrinthSearchHit> hits,
            int offset,
            int limit,
            @SerializedName("total_hits") int totalHits
    ) {
    }
}
