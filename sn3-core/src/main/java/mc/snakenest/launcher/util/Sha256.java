package mc.snakenest.launcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 helpers shared by everything that needs to verify downloaded
 * content (modpack blobs, launcher releases) against a hash supplied by the
 * server, hex-encoded to match the API's convention.
 */
public final class Sha256 {

    private Sha256() {
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
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Guaranteed to exist on every conforming JDK implementation (JLS/JCA baseline algorithm).
            throw new AssertionError("SHA-256 not available", e);
        }
    }
}
