package mc.snakenest.launcher.auth;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The 5 query parameters every authenticated call to this API needs:
 * proof of possession of the player's Ed25519 key
 * ({@code playerId}/{@code publicKey}/{@code timestamp}/{@code signature})
 * plus the launcher's own {@code client_id}.
 */
public record SignedParams(long playerId, String publicKeyHex, long timestamp, String signatureHex, String clientId) {

    public Map<String, String> toQueryParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("playerId", Long.toString(playerId));
        params.put("publicKey", publicKeyHex);
        params.put("timestamp", Long.toString(timestamp));
        params.put("signature", signatureHex);
        params.put("client_id", clientId);
        return params;
    }
}
