package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.auth.SignedParams;
import mc.snakenest.launcher.auth.SignedRequestSigner;
import mc.snakenest.launcher.net.HttpJsonClient;
import mc.snakenest.launcher.net.JsonResponse;
import mc.snakenest.launcher.net.RawResponse;
import mc.snakenest.launcher.modpack.signing.ManifestVerdict;
import mc.snakenest.launcher.modpack.signing.ModpackManifestVerifier;
import mc.snakenest.launcher.net.Uris;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Client for the {@code modpacks} plugin's endpoints. Every call requires
 * both proof of possession of the player's key (same signature recipe as
 * {@code auth.SignedRequestSigner}) <b>and</b> the launcher's own
 * {@code client_id} - see {@code LAUNCHER_INTEGRATION.md} section 7.
 */
public final class ModpackApiClient {

    private final HttpJsonClient http;
    private final URI baseUrl;
    private final ModpackManifestVerifier verifier;

    public ModpackApiClient(HttpJsonClient http, URI baseUrl) {
        this(http, baseUrl, ModpackManifestVerifier.disabled());
    }

    /** @param verifier checks every manifest this client returns; see {@link ModpackManifestVerifier} for the policy */
    public ModpackApiClient(HttpJsonClient http, URI baseUrl, ModpackManifestVerifier verifier) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.verifier = verifier;
    }

    public List<ModpackSummary> listModpacks(PlayerSession session) throws IOException, InterruptedException, ModpackApiException {
        JsonResponse response = get("/api/modpacks", Map.of(), session);
        requireSuccess(response, "listing modpacks");
        return response.as(ModpackListResponse.class).modpacks();
    }

    /**
     * @param version exact version string, or {@code null} to resolve through {@code channel} instead
     * @param channel distribution channel to resolve "latest" through (see
     *                {@code ModpackSettings#channel}); {@code null} means the site's default
     *                channel. Ignored server-side when {@code version} is non-null - pinning an
     *                exact version by name is channel-irrelevant.
     */
    public ModpackManifest getManifest(String slug, String version, String channel, PlayerSession session)
            throws IOException, InterruptedException, ModpackApiException {
        Map<String, String> extra = new java.util.LinkedHashMap<>();
        if (version != null) {
            extra.put("version", version);
        } else if (channel != null) {
            extra.put("channel", channel);
        }
        JsonResponse response = get("/api/modpacks/" + slug + "/manifest", extra, session);
        if (response.statusCode() == 404 && isNoChannelVersion(response)) {
            throw new ModpackNoChannelVersionException(
                    "Aucune version disponible sur le canal sélectionné pour \"" + slug + "\" - choisis un autre canal via \"Gérer\".");
        }
        requireSuccess(response, "fetching the manifest of " + slug);
        ModpackManifest manifest = response.as(ModpackManifest.class);

        // The one place a manifest enters the launcher, so the one place it is checked: a refused manifest never
        // reaches the sync engine, and nothing is downloaded or installed from it.
        ManifestVerdict verdict = verifier.verify(slug, version, manifest);
        if (!verdict.accepted()) {
            throw new ModpackSignatureException("La signature du modpack \"" + slug + "\" est refusée (" + verdict.reason()
                    + "). Rien n'a été installé.");
        }
        return manifest;
    }

    public List<ModpackVersionSummary> listVersions(String slug, PlayerSession session)
            throws IOException, InterruptedException, ModpackApiException {
        JsonResponse response = get("/api/modpacks/" + slug + "/versions", Map.of(), session);
        requireSuccess(response, "listing versions of " + slug);
        return response.as(VersionListResponse.class).versions();
    }

    /**
     * Global entries plus this modpack's own, already merged server-side - see
     * {@code LauncherApp#checkModBlacklist}, called right before every launch.
     */
    public List<ModBlacklistEntry> getBlacklist(String slug, PlayerSession session)
            throws IOException, InterruptedException, ModpackApiException {
        JsonResponse response = get("/api/modpacks/" + slug + "/blacklist", Map.of(), session);
        requireSuccess(response, "fetching the mod blacklist of " + slug);
        return response.as(BlacklistResponse.class).entries();
    }

    /**
     * Streams the raw content of one blob. The caller must check
     * {@link RawResponse#isSuccess()} and close the response.
     */
    public RawResponse downloadBlob(String slug, String hash, PlayerSession session) throws IOException, InterruptedException {
        String path = "/api/modpacks/" + slug + "/blob/" + hash;
        SignedParams signed = SignedRequestSigner.sign(session, "GET", path);
        URI uri = Uris.withQuery(baseUrl.resolve(path), signed.toQueryParams());
        return http.getRaw(uri);
    }

    private JsonResponse get(String path, Map<String, String> extraParams, PlayerSession session)
            throws IOException, InterruptedException {
        SignedParams signed = SignedRequestSigner.sign(session, "GET", path);
        Map<String, String> params = signed.toQueryParams();
        params.putAll(extraParams);
        URI uri = Uris.withQuery(baseUrl.resolve(path), params);
        return http.get(uri);
    }

    private void requireSuccess(JsonResponse response, String action) throws ModpackApiException {
        if (response.isSuccess()) {
            return;
        }
        switch (response.statusCode()) {
            case 403 -> throw new ModpackAccessDeniedException("Access denied " + action);
            case 404 -> throw new ModpackNotFoundException("Not found: " + action);
            default -> throw new ModpackApiException("Unexpected status " + response.statusCode() + " " + action);
        }
    }

    /** Distinguishes "the channel has nothing to resolve" from a plain unknown-slug/version 404 - see ModpackNoChannelVersionException. */
    private boolean isNoChannelVersion(JsonResponse response) {
        try {
            return "no_channel_version".equals(response.as(ErrorCodeResponse.class).code());
        } catch (com.google.gson.JsonSyntaxException e) {
            return false;
        }
    }

    private record ErrorCodeResponse(String code) {
    }

    private record ModpackListResponse(List<ModpackSummary> modpacks) {
    }

    private record VersionListResponse(List<ModpackVersionSummary> versions) {
    }

    private record BlacklistResponse(List<ModBlacklistEntry> entries) {
    }
}
