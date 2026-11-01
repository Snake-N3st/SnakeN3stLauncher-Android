package mc.snakenest.launcher.news;

import com.google.gson.reflect.TypeToken;
import mc.snakenest.launcher.net.HttpJsonClient;
import mc.snakenest.launcher.net.JsonResponse;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Client for Azuriom core's public news feed ({@code /api/posts}) - not a
 * custom plugin, and unauthenticated (no signature/client_id needed, it's
 * public site content).
 */
public final class NewsApiClient {

    private final HttpJsonClient http;
    private final URI baseUrl;

    public NewsApiClient(HttpJsonClient http, URI baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
    }

    public List<Post> listPosts() throws IOException, InterruptedException, NewsApiException {
        JsonResponse response = http.get(baseUrl.resolve("/api/posts"));

        if (!response.isSuccess()) {
            throw new NewsApiException("Unexpected status listing posts: " + response.statusCode());
        }
        return response.as(new TypeToken<List<Post>>() {
        });
    }

    public Post getPost(long id) throws IOException, InterruptedException, NewsApiException {
        JsonResponse response = http.get(baseUrl.resolve("/api/posts/" + id));

        if (response.statusCode() == 404) {
            throw new NewsApiException("Unknown post: " + id);
        }
        if (!response.isSuccess()) {
            throw new NewsApiException("Unexpected status fetching post " + id + ": " + response.statusCode());
        }
        return response.as(Post.class);
    }
}
