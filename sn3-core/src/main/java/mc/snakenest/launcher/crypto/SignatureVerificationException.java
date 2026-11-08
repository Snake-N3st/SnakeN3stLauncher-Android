package mc.snakenest.launcher.crypto;

import java.io.IOException;

/**
 * A release's signature could not be accepted: it is present but wrong, or it is absent where one has been
 * seen before. Never thrown for a signature that is merely missing the first time - that is a warning, not an
 * error, see {@code bootstrap}'s {@code ArtifactVerifier}.
 *
 * <p>An {@link IOException} on purpose: it can cross every existing {@code throws IOException} signature on
 * the download path without touching one of them, and a caller that already treats an I/O failure as "do not
 * use what was just fetched" does the right thing without knowing this type exists.
 */
public final class SignatureVerificationException extends IOException {

    public SignatureVerificationException(String message) {
        super(message);
    }
}
