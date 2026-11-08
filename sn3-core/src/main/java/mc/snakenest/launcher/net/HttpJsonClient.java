package mc.snakenest.launcher.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Android-compatible transport. Signed queries are never logged or redirected. */
public final class HttpJsonClient {
    private final Gson gson;
    public HttpJsonClient() { this(new GsonBuilder().disableHtmlEscaping().create()); }
    public HttpJsonClient(Gson gson) { this.gson = gson; }
    public JsonResponse get(URI uri) throws IOException, InterruptedException { return json(uri, "GET", null); }
    public JsonResponse postJson(URI uri, Object body) throws IOException, InterruptedException {
        return json(uri, "POST", gson.toJson(body).getBytes(StandardCharsets.UTF_8));
    }
    private HttpURLConnection open(URI uri, String method) throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) throw new IOException("Unsupported protocol");
        HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", "SnakeN3stLauncher-Android/0.1");
        return c;
    }
    private JsonResponse json(URI uri, String method, byte[] body) throws IOException, InterruptedException {
        HttpURLConnection c = open(uri, method);
        try {
            c.setRequestProperty("Accept", "application/json");
            if (body != null) {
                c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = c.getOutputStream()) { out.write(body); }
            }
            int status = c.getResponseCode();
            InputStream stream = status >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = "";
            if (stream != null) try (InputStream in = stream) { text = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
            return new JsonResponse(status, text, gson);
        } finally { c.disconnect(); }
    }
    public RawResponse getRaw(URI uri) throws IOException, InterruptedException {
        return raw(uri, 0);
    }
    private RawResponse raw(URI uri, int redirects) throws IOException, InterruptedException {
        HttpURLConnection c = open(uri, "GET");
        try {
            int status = c.getResponseCode();
            // Only unsigned external downloads may follow a redirect, always over HTTPS.
            if (status >= 300 && status < 400 && uri.getRawQuery() == null && redirects < 5) {
                String location = c.getHeaderField("Location");
                if (location != null) {
                    URI next = uri.resolve(location);
                    if (!"https".equals(next.getScheme())) throw new IOException("Insecure download redirect");
                    c.disconnect(); return raw(next, redirects + 1);
                }
            }
            InputStream stream = status >= 400 ? c.getErrorStream() : c.getInputStream();
            return new RawResponse(status, new FilterInputStream(stream == null ? new ByteArrayInputStream(new byte[0]) : stream) {
                @Override public void close() throws IOException { try { super.close(); } finally { c.disconnect(); } }
            });
        } catch (IOException | RuntimeException e) { c.disconnect(); throw e; }
    }
}
