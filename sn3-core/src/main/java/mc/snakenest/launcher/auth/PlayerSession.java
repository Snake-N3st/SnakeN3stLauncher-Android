package mc.snakenest.launcher.auth;

import mc.snakenest.launcher.crypto.Ed25519KeyPair;

/**
 * The authenticated player, bundled with the launcher's own client id -
 * exactly the triple every signed request on this API needs. Introduced to
 * avoid passing the same 3 parameters through every API client method.
 */
public record PlayerSession(Ed25519KeyPair key, long playerId, String clientId) {
}
