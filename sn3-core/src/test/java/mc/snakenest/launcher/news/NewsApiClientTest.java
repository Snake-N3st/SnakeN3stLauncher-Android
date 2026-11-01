package mc.snakenest.launcher.news;

import com.sun.net.httpserver.HttpServer;
import mc.snakenest.launcher.net.HttpJsonClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NewsApiClientTest {

    // Real shape, copied from Azuriom core's PostResource.php on the local dev instance.
    private static final String SINGLE_POST_BODY = """
            {
              "id": 1,
              "title": "Ouverture du serveur",
              "description": "On est en ligne !",
              "slug": "ouverture-du-serveur",
              "url": "http://127.0.0.1/news/1-ouverture-du-serveur",
              "content": "<p>Bienvenue</p>",
              "author": {"id": 1, "name": "Admin"},
              "published_at": "2026-07-01T12:00:00+00:00",
              "image": null
            }
            """;

    private static final String LIST_BODY = "[" + SINGLE_POST_BODY + "]";

    private HttpServer server;
    private NewsApiClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/api/posts", exchange -> {
            if (!"/api/posts".equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] body = LIST_BODY.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/api/posts/1", exchange -> {
            byte[] body = SINGLE_POST_BODY.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/api/posts/999", exchange -> {
            byte[] body = "{\"message\":\"Not found\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = new NewsApiClient(new HttpJsonClient(), base);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void listPostsParsesEachField() throws Exception {
        List<Post> posts = client.listPosts();

        assertEquals(1, posts.size());
        Post post = posts.get(0);
        assertEquals(1, post.id());
        assertEquals("Ouverture du serveur", post.title());
        assertEquals("ouverture-du-serveur", post.slug());
        assertEquals("Admin", post.author().name());
        assertEquals("2026-07-01T12:00:00+00:00", post.publishedAt());
        assertNull(post.image());
    }

    @Test
    void getPostReturnsTheSinglePost() throws Exception {
        Post post = client.getPost(1);

        assertEquals("<p>Bienvenue</p>", post.content());
    }

    @Test
    void getPostThrowsOnUnknownId() {
        assertThrows(NewsApiException.class, () -> client.getPost(999));
    }
}
