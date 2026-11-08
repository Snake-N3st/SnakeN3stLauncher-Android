package mc.snakenest.launcher.net;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * A raw (non-JSON) HTTP response body, used for streaming downloads (modpack
 * blobs, the launcher release jar) where the caller wants to hash/write the
 * bytes as they arrive rather than buffer the whole response in memory.
 */
public final class RawResponse implements Closeable {

    private final int statusCode;
    private final InputStream body;

    public RawResponse(int statusCode, InputStream body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /** Caller is responsible for consuming and for closing this response. */
    public InputStream body() {
        return body;
    }

    @Override
    public void close() throws IOException {
        body.close();
    }
}
