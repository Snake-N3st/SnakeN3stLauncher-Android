package mc.snakenest.launcher.util;

import java.io.IOException;

/**
 * Downloaded content's SHA-256 didn't match what the caller expected. Thrown
 * by {@link AtomicFiles#writeVerified} before the content is ever moved to
 * its final path - the temp file is discarded, never left half-verified at
 * the real destination.
 */
public final class HashVerificationException extends IOException {

    public HashVerificationException(String subject, String expectedHash, String actualHash) {
        super("Hash mismatch for " + subject + ": expected " + expectedHash + " but got " + actualHash);
    }
}
