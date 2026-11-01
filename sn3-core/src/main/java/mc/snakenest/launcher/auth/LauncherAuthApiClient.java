package mc.snakenest.launcher.auth;

import mc.snakenest.launcher.net.HttpJsonClient;
import mc.snakenest.launcher.net.JsonResponse;
import mc.snakenest.launcher.net.Uris;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Client for the {@code launcher-auth} plugin's endpoints: the device-flow
 * challenge (obtain/poll) and the three signed player-info lookups. See
 * {@code LAUNCHER_INTEGRATION.md} sections 1-3 and 6 in the sibling
 * {@code SnakeN3stLogin} repo for the authoritative contract.
 */
public final class LauncherAuthApiClient {

    private final HttpJsonClient http;
    private final URI baseUrl;

    public LauncherAuthApiClient(HttpJsonClient http, URI baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
    }

    /** @return the challenge token to show the user's browser and to poll */
    public String requestChallenge(String clientId) throws IOException, InterruptedException, LauncherApiException {
        JsonResponse response = http.postJson(baseUrl.resolve("/api/launcher-auth/challenge"), Map.of("client_id", clientId));

        if (response.statusCode() == 404) {
            throw new LauncherApiException("Unknown client_id", response.statusCode());
        }
        if (!response.isSuccess()) {
            throw new LauncherApiException("Unexpected status requesting a challenge: " + response.statusCode(), response.statusCode());
        }
        return response.as(ChallengeResponse.class).challenge();
    }

    public URI loginUrl(String challengeToken) {
        return Uris.withQuery(baseUrl.resolve("/launcher-login"), Map.of("challenge", challengeToken));
    }

    /** Unsigned - a channel's name/restriction/default status isn't sensitive, see ChannelInfo's own docblock. */
    public List<ChannelInfo> fetchChannels() throws IOException, InterruptedException, LauncherApiException {
        JsonResponse response = http.get(baseUrl.resolve("/api/launcher-auth/channels"));

        if (!response.isSuccess()) {
            throw new LauncherApiException("Unexpected status fetching channels: " + response.statusCode(), response.statusCode());
        }
        return response.as(ChannelsResponse.class).channels();
    }

    /** Unsigned - there's no player key yet at this point (used to brand the login window). */
    public ClientInfo fetchClientInfo(String clientId) throws IOException, InterruptedException, LauncherApiException {
        URI uri = Uris.withQuery(baseUrl.resolve("/api/launcher-auth/client"), Map.of("client_id", clientId));
        JsonResponse response = http.get(uri);

        if (response.statusCode() == 404) {
            throw new LauncherApiException("Unknown client_id", response.statusCode());
        }
        if (!response.isSuccess()) {
            throw new LauncherApiException("Unexpected status fetching client info: " + response.statusCode(), response.statusCode());
        }
        return response.as(ClientInfo.class);
    }

    public PollResult pollChallenge(String challengeToken) throws IOException, InterruptedException, LauncherApiException {
        JsonResponse response = http.get(baseUrl.resolve("/api/launcher-auth/challenge/" + challengeToken));

        return switch (response.statusCode()) {
            case 200 -> {
                PollSuccessResponse body = response.as(PollSuccessResponse.class);
                yield new PollResult.Success(body.privateKey(), body.playerId());
            }
            case 403 -> new PollResult.Pending();
            case 404 -> new PollResult.NotFound();
            default -> throw new LauncherApiException("Unexpected status polling the challenge: " + response.statusCode(), response.statusCode());
        };
    }

    public String fetchUsername(PlayerSession session) throws IOException, InterruptedException, LauncherApiException {
        return fetchOne("/api/launcher-auth/player/username", session, UsernameResponse.class).username();
    }

    /**
     * Everything the account popover needs (username/role/email/avatar) in a
     * single signed request - meant to be called once at login and once at
     * startup, never every time the popover is opened.
     */
    public PlayerInfo fetchPlayerInfo(PlayerSession session) throws IOException, InterruptedException, LauncherApiException {
        return fetchOne("/api/launcher-auth/player/info", session, PlayerInfo.class);
    }

    /**
     * Deletes this key server-side, so a copy that leaked before logout (e.g. exfiltrated from
     * disk) stops being accepted afterward. Called by {@code LauncherApp#logout} before the
     * local key file is deleted - best-effort (a failure here shouldn't block logging out
     * locally), but always attempted, since skipping it would leave a stolen key valid forever.
     */
    public void revokeKey(PlayerSession session) throws IOException, InterruptedException, LauncherApiException {
        String path = "/api/launcher-auth/player/revoke";
        SignedParams signed = SignedRequestSigner.sign(session, "POST", path);
        URI uri = Uris.withQuery(baseUrl.resolve(path), signed.toQueryParams());
        JsonResponse response = http.postJson(uri, Map.of());

        if (!response.isSuccess()) {
            throw new LauncherApiException("Key revocation rejected (status " + response.statusCode() + ")", response.statusCode());
        }
    }

    private <T> T fetchOne(String path, PlayerSession session, Class<T> type)
            throws IOException, InterruptedException, LauncherApiException {
        SignedParams signed = SignedRequestSigner.sign(session, "GET", path);
        URI uri = Uris.withQuery(baseUrl.resolve(path), signed.toQueryParams());
        JsonResponse response = http.get(uri);

        if (!response.isSuccess()) {
            throw new LauncherApiException("Player info request rejected (status " + response.statusCode() + ")", response.statusCode());
        }
        return response.as(type);
    }

    private record ChallengeResponse(String challenge) {
    }

    private record PollSuccessResponse(String privateKey, long playerId) {
    }

    private record UsernameResponse(String username) {
    }

    private record ChannelsResponse(List<ChannelInfo> channels) {
    }
}
