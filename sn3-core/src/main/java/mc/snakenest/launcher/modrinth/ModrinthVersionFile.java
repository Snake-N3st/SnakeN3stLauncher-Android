package mc.snakenest.launcher.modrinth;

import java.util.Map;

/**
 * One downloadable file of a {@link ModrinthVersion}. {@code hashes} keys are algorithm names
 * ({@code "sha1"}/{@code "sha512"}) - matching against a local mod always uses {@code sha1}, see
 * {@code Sha1} and {@code ModrinthApiClient#lookupByHashes}.
 */
public record ModrinthVersionFile(
        Map<String, String> hashes,
        String url,
        String filename,
        boolean primary,
        long size
) {
    public String sha1() {
        return hashes.get("sha1");
    }
}
