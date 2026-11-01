package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.util.Hex;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/** Recognizes already installed bytes independently of a repository's encoded or renamed filename. */
public final class InstalledModMatcher {
    private InstalledModMatcher() { }

    public static Optional<Path> findIdentical(Path modsDirectory, String algorithm, String expectedHash,
                                                long expectedSize) throws IOException {
        int hashLength;
        if ("SHA-512".equals(algorithm)) hashLength = 128;
        else if ("SHA-1".equals(algorithm)) hashLength = 40;
        else throw new IllegalArgumentException("Unsupported mod hash algorithm");
        if (expectedSize < 0 || expectedHash == null || !expectedHash.matches("[0-9a-fA-F]{" + hashLength + "}"))
            throw new IOException("Invalid mod content fingerprint");
        checkInterrupted();
        if (!Files.isDirectory(modsDirectory, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        try (var entries = Files.list(modsDirectory)) {
            var candidates = entries.iterator();
            while (candidates.hasNext()) {
                checkInterrupted();
                Path candidate = candidates.next();
                if (!candidate.getFileName().toString().endsWith(".jar")
                        || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(candidate) != expectedSize) continue;
                MessageDigest digest;
                try { digest = MessageDigest.getInstance(algorithm); }
                catch (NoSuchAlgorithmException e) { throw new AssertionError("Required digest unavailable", e); }
                try (InputStream input = Files.newInputStream(candidate)) {
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        checkInterrupted();
                        digest.update(buffer, 0, count);
                    }
                }
                checkInterrupted();
                if (expectedHash.equalsIgnoreCase(Hex.encode(digest.digest()))) return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Mod content lookup cancelled");
    }
}
