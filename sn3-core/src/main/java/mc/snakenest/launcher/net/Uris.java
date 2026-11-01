package mc.snakenest.launcher.net;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds request URIs with properly encoded query parameters. */
public final class Uris {

    private Uris() {
    }

    public static URI withQuery(URI base, Map<String, String> params) {
        if (params.isEmpty()) {
            return base;
        }
        String query = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        return URI.create(base + "?" + query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
