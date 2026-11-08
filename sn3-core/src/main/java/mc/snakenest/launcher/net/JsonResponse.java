package mc.snakenest.launcher.net;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

/**
 * An HTTP response whose body is (expected to be) JSON. Deliberately keeps
 * the raw body untyped rather than forcing one response type per endpoint:
 * this API returns a different JSON shape per status code on the same
 * endpoint (e.g. the challenge poll returns a key on 200 but a
 * {@code {"message": ...}} on 403/404), so callers decide what shape to
 * parse based on {@link #statusCode()} themselves.
 */
public final class JsonResponse {

    private final int statusCode;
    private final String rawBody;
    private final Gson gson;

    JsonResponse(int statusCode, String rawBody, Gson gson) {
        this.statusCode = statusCode;
        this.rawBody = rawBody;
        this.gson = gson;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    public String rawBody() {
        return rawBody;
    }

    /** @throws JsonSyntaxException if the body isn't valid JSON matching {@code type} */
    public <T> T as(Class<T> type) {
        return gson.fromJson(rawBody, type);
    }

    /** For generic types such as {@code List<Post>} that a bare {@link Class} can't express. */
    public <T> T as(TypeToken<T> type) {
        return gson.fromJson(rawBody, type.getType());
    }
}
