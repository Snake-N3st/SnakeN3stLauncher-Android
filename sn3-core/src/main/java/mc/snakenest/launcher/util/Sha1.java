package mc.snakenest.launcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-1 helper for identifying mod files against the Modrinth API, which hashes and matches its
 * files with SHA-1 (and SHA-512, unused here) rather than SHA-256 - see
 * {@code ModrinthApiClient#lookupByHashes}. Not used anywhere our own hashes are verified; that
 * stays {@link Sha256}.
 */
public final class Sha1 {

    private Sha1() {
    }

    public static String hex(byte[] data) {
        return Hex.encode(digest().digest(data));
    }

    /** Reads the stream to completion; does not close it. */
    public static String hex(InputStream in) throws IOException {
        MessageDigest digest = digest();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return Hex.encode(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            // Guaranteed to exist on every conforming JDK implementation (JLS/JCA baseline algorithm).
            throw new AssertionError("SHA-1 not available", e);
        }
    }
}
