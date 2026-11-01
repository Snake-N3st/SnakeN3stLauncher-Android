package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.net.HttpJsonClient;
import mc.snakenest.launcher.net.RawResponse;
import mc.snakenest.launcher.util.AtomicFiles;
import mc.snakenest.launcher.util.HashVerificationException;
import mc.snakenest.launcher.util.Sha256;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Downloads one modpack file, verifying its content before it's ever
 * written at its final path: streamed to a temp file while hashing, then
 * only moved into place if the SHA-256 matches what the manifest promised
 * (protects against a truncated or corrupted transfer - the doc explicitly
 * calls this out as mandatory, not optional). {@link ManifestFile#url()},
 * when set, redirects the fetch to a third-party origin server instead of
 * this site's own blob storage - the hash is still this site's own (computed
 * once at import time), so the same verification applies either way.
 */
public final class ModpackFileDownloader {

    private final ModpackApiClient api;
    private final HttpJsonClient http;
    private final Path blobCache;

    public ModpackFileDownloader(ModpackApiClient api, HttpJsonClient http) {
        this(api, http, null);
    }

    private ModpackFileDownloader(ModpackApiClient api, HttpJsonClient http, Path blobCache) {
        this.api = api; this.http = http; this.blobCache = blobCache;
    }

    ModpackFileDownloader withBlobCache(Path cache) { return new ModpackFileDownloader(api, http, cache); }

    void prefetch(String slug, java.util.List<ManifestFile> files, PlayerSession session)
            throws IOException, InterruptedException {
        new ModpackBatchDownloader(api).fetch(slug, files, session, blobCache);
    }

    public void download(String slug, ManifestFile file, Path instanceDir, PlayerSession session)
            throws IOException, InterruptedException, ModpackApiException {
        Path target = InstancePaths.resolveSafely(instanceDir, file.path());

        try (RawResponse response = fetch(slug, file, session)) {
            if (response.statusCode() == 404) {
                throw new ModpackNotFoundException("Unknown blob " + file.hash() + " for " + slug);
            }
            if (response.statusCode() == 403) {
                throw new ModpackAccessDeniedException("Access denied downloading " + file.path());
            }
            if (!response.isSuccess()) {
                throw new ModpackApiException("Unexpected status " + response.statusCode() + " downloading " + file.path());
            }

            AtomicFiles.writeVerified(target, response.body(), file.hash());
        }
    }

    /**
     * The same fetch and the same verification, but handing the bytes back instead of writing
     * them - for a {@code MERGE} file, which has to be folded into the player's existing copy
     * before anything lands on disk (see {@code FileMerger}).
     *
     * <p>Held in memory rather than streamed to a temp file: a merge target is a config, kilobytes
     * at most, and the merge itself needs the whole thing anyway.
     */
    public byte[] fetchBytes(String slug, ManifestFile file, PlayerSession session)
            throws IOException, InterruptedException, ModpackApiException {
        try (RawResponse response = fetch(slug, file, session)) {
            if (response.statusCode() == 404) {
                throw new ModpackNotFoundException("Unknown blob " + file.hash() + " for " + slug);
            }
            if (response.statusCode() == 403) {
                throw new ModpackAccessDeniedException("Access denied downloading " + file.path());
            }
            if (!response.isSuccess()) {
                throw new ModpackApiException("Unexpected status " + response.statusCode() + " downloading " + file.path());
            }

            byte[] content = response.body().readAllBytes();
            String actual = Sha256.hex(content);

            if (!actual.equalsIgnoreCase(file.hash())) {
                throw new HashVerificationException(file.path(), file.hash(), actual);
            }

            return content;
        }
    }

    private RawResponse fetch(String slug, ManifestFile file, PlayerSession session) throws IOException, InterruptedException {
        if (file.url() != null) {
            return http.getRaw(URI.create(file.url()));
        }
        if (blobCache != null) {
            Path cached = blobCache.resolve(file.hash().toLowerCase(java.util.Locale.ROOT));
            if (!java.nio.file.Files.isRegularFile(cached)) throw new IOException("Missing verified batch blob");
            return new RawResponse(200, java.nio.file.Files.newInputStream(cached));
        }
        return api.downloadBlob(slug, file.hash(), session);
    }
}
