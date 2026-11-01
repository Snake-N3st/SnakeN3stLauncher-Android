package mc.snakenest.launcher.bugreport;

import mc.snakenest.launcher.net.HttpJsonClient;
import mc.snakenest.launcher.net.JsonResponse;
import mc.snakenest.launcher.util.AppVersion;
import mc.snakenest.launcher.util.Log;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Submits a report to the site plugin's {@code POST /api/launcher-auth/bug-reports} (see
 * {@code LAUNCHER_INTEGRATION.md} in the sibling {@code SnakeN3stLogin} repo). Unauthenticated -
 * a launcher crash can happen before login, so this can't depend on a signed session the way
 * {@link mc.snakenest.launcher.auth.LauncherAuthApiClient} does.
 *
 * <h2>Hard rule</h2>
 * Every method here is best-effort and never throws. A crash reporter that can itself throw
 * (network down, server error, bad JSON) would turn "the launcher had a problem" into "the
 * launcher had two problems" - callers only ever get a {@code boolean} back.
 */
public final class BugReportApiClient {

    // Mirrors the column sizes in the site plugin's bug_reports migration - truncating here
    // avoids a silently-dropped report on a 422 the player never sees.
    private static final int MAX_SUMMARY = 255;
    private static final int MAX_DESCRIPTION = 4000;
    private static final int MAX_DETAILS = 65_535;
    private static final int MAX_LAUNCHER_VERSION = 50;
    private static final int MAX_SYSTEM_INFO = 255;
    private static final int MAX_MODPACK_SLUG = 255;

    private final HttpJsonClient http;
    private final URI baseUrl;
    private final String clientId;

    public BugReportApiClient(HttpJsonClient http, URI baseUrl, String clientId) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.clientId = clientId;
    }

    /**
     * @param description free-text player explanation, may be {@code null}/blank
     * @param details      technical payload (stack trace, crash-report content), may be {@code null}/blank
     * @param modpackSlug  the modpack running at the time, or {@code null} when not applicable
     * @return whether the server accepted it - {@code false} covers both "couldn't reach the server" and "server rejected it"
     */
    public boolean submit(BugReportType type, String summary, String description, String details, String modpackSlug) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("client_id", clientId);
            body.put("type", type.wireValue());
            body.put("summary", truncate(summary, MAX_SUMMARY));
            putIfNotBlank(body, "description", truncate(description, MAX_DESCRIPTION));
            putIfNotBlank(body, "details", truncate(details, MAX_DETAILS));
            putIfNotBlank(body, "launcher_version", truncate(AppVersion.current(), MAX_LAUNCHER_VERSION));
            putIfNotBlank(body, "system_info", truncate(SystemInfo.describe(), MAX_SYSTEM_INFO));
            putIfNotBlank(body, "modpack_slug", truncate(modpackSlug, MAX_MODPACK_SLUG));

            JsonResponse response = http.postJson(baseUrl.resolve("/api/launcher-auth/bug-reports"), body);
            if (!response.isSuccess()) {
                Log.warn(BugReportApiClient.class, "Bug report rejected (status " + response.statusCode() + ")");
                return false;
            }
            return true;
        } catch (Exception e) {
            // Deliberately broad: this must never propagate, whatever goes wrong.
            Log.warn(BugReportApiClient.class, "Could not submit bug report: " + e.getMessage());
            return false;
        }
    }

    private static void putIfNotBlank(Map<String, Object> body, String key, String value) {
        if (value != null && !value.isBlank()) {
            body.put(key, value);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
