package mc.snakenest.launcher.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

/**
 * Produces the exact signature every player-info and modpacks endpoint expects: a signature over
 * {@code "{METHOD}:{path}:{timestamp}"} (UTF-8, colons included, path without its leading slash -
 * see VerifyLauncherSignature in the sibling SnakeN3stLogin repo), using the current Unix time in
 * seconds. Binding the signature to the exact method+path (rather than a single message shared by
 * every endpoint, as before) means a signature captured for one endpoint can never be replayed
 * against a different one; the server also enforces each signature is single-use. Still: never
 * cache or reuse a {@link SignedParams} across requests - this must be recomputed fresh every
 * time, or an expired/already-consumed signature gets sent for no reason.
 */
public final class SignedRequestSigner {

    private SignedRequestSigner() {
    }

    /** @param path the exact path passed to {@code baseUrl.resolve(path)} for this request - leading slash optional, stripped if present. */
    public static SignedParams sign(PlayerSession session, String method, String path) {
        long timestamp = Instant.now().getEpochSecond();
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        String message = method.toUpperCase(Locale.ROOT) + ":" + normalizedPath + ":" + timestamp;
        String signature = session.key().signHex(message.getBytes(StandardCharsets.UTF_8));
        return new SignedParams(session.playerId(), session.key().publicKeyHex(), timestamp, signature, session.clientId());
    }
}
