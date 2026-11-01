package mc.snakenest.launcher.auth;

/**
 * Outcome of one {@code GET /api/launcher-auth/challenge/{token}} poll, per
 * the doc's 3-state contract: {@code 404} is terminal (expired/consumed),
 * {@code 403} means keep polling, {@code 200} delivers the key exactly once.
 */
public sealed interface PollResult {

    /** {@code 403}: the user hasn't confirmed in the browser yet. Keep polling. */
    record Pending() implements PollResult {
    }

    /** {@code 404}: unknown, expired, or already-consumed challenge. Stop polling. */
    record NotFound() implements PollResult {
    }

    /** {@code 200}: delivered exactly once. Store the key immediately. */
    record Success(String seedHex, long playerId) implements PollResult {
    }
}
