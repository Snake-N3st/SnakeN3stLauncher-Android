package mc.snakenest.launcher.modpack;

import java.nio.file.Path;

/**
 * Resolves a manifest-supplied relative path against an instance directory
 * defensively. {@code path} comes straight from the server response and is
 * about to be written to or deleted from disk - without this check, a
 * malicious or compromised server response containing {@code ../../..}
 * segments could write or delete files outside the instance directory
 * entirely. The server is presumed trusted (it's the operator's own
 * Azuriom instance), but this costs nothing and is the same defense the
 * server itself already applies to the {@code hash} path segment in
 * {@code ModpackBlobController} - worth doing here too, in depth.
 */
final class InstancePaths {

    private InstancePaths() {
    }

    /** @throws PathTraversalException if the resolved path would escape {@code instanceDir} */
    static Path resolveSafely(Path instanceDir, String relativePath) throws PathTraversalException {
        Path resolved = instanceDir.resolve(relativePath).normalize();
        Path normalizedRoot = instanceDir.normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new PathTraversalException(relativePath);
        }
        return resolved;
    }
}
