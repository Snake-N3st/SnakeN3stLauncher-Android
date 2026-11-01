package mc.snakenest.launcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Write-then-rename helper reused by every class that persists something to
 * disk (config, local manifests, the encrypted key, downloaded files): the
 * temporary file is created in the destination's own directory so the final
 * {@link Files#move} is same-filesystem and therefore atomic, which avoids
 * ever leaving a half-written file at the real path if the process dies
 * mid-write.
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    public static void write(Path target, byte[] content) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
        try {
            Files.write(tmp, content);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Consumes and closes {@code source}. On a successful return, the SHA-256
     * of what was written is returned so callers that need to verify content
     * against an expected hash don't have to re-read the file. Note that the
     * move to {@code target} always happens here, before the caller gets a
     * chance to check the hash - use {@link #writeVerified} instead when the
     * content must not be committed to {@code target} on a mismatch (e.g.
     * anything downloaded from the network).
     */
    public static String writeVerifying(Path target, InputStream source) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
        try {
            String hash = hashWhileWriting(tmp, source);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return hash;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Like {@link #writeVerifying}, but only moves the content into place if
     * its SHA-256 matches {@code expectedHashHex}; otherwise the temp file is
     * discarded and {@link HashVerificationException} is thrown, leaving
     * {@code target} untouched. This is the one to use for anything fetched
     * over the network and checked against a manifest/release hash.
     */
    public static void writeVerified(Path target, InputStream source, String expectedHashHex) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
        try {
            String actualHash = hashWhileWriting(tmp, source);
            if (!actualHash.equalsIgnoreCase(expectedHashHex)) {
                throw new HashVerificationException(target.getFileName().toString(), expectedHashHex, actualHash);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String hashWhileWriting(Path tmp, InputStream source) throws IOException {
        try (source; var out = Files.newOutputStream(tmp)) {
            var digest = new java.security.DigestOutputStream(out, java.security.MessageDigest.getInstance("SHA-256"));
            byte[] buffer = new byte[65536]; int count;
            while ((count = source.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Download cancelled");
                digest.write(buffer, 0, count);
            }
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Download cancelled");
            return Hex.encode(digest.getMessageDigest().digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 not available", e);
        }
    }
}
