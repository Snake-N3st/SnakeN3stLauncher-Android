package mc.snakenest.launcher.android.snaken3st;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Bounded public-image loader. No cookies, credentials, disk cache or URL logging. */
public final class RemoteIconLoader {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final int DECODE_DIMENSION = 256;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();
    private static final Map<String, List<Consumer<Bitmap>>> PENDING = new HashMap<>();
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(
            2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(24), runnable -> {
                Thread thread = new Thread(() -> {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                    runnable.run();
                }, "sn3-public-icon");
                thread.setDaemon(true);
                return thread;
            });

    private RemoteIconLoader() { }

    /** Always invokes callback on the main thread, with null when unavailable or rejected. */
    public static void load(Context context, String imageUrl, String baseUrl, Consumer<Drawable> callback) {
        Context application = context.getApplicationContext();
        Consumer<Bitmap> deliver = bitmap -> MAIN.post(() -> callback.accept(bitmap == null ? null
                : new BitmapDrawable(application.getResources(), bitmap)));
        final URI uri;
        try { uri = resolve(imageUrl, baseUrl); }
        catch (RuntimeException e) { deliver.accept(null); return; }
        String key = uri.toASCIIString();
        synchronized (LOCK) {
            Bitmap cached = CACHE.get(key);
            if (cached != null) { deliver.accept(cached); return; }
            List<Consumer<Bitmap>> waiting = PENDING.get(key);
            if (waiting != null) { waiting.add(deliver); return; }
            waiting = new ArrayList<>(); waiting.add(deliver); PENDING.put(key, waiting);
        }
        try {
            WORKERS.execute(() -> {
                Bitmap bitmap = null;
                try { bitmap = download(uri); }
                catch (IOException | RuntimeException e) { /* Optional image: retain the placeholder. */ }
                finish(key, bitmap);
            });
        } catch (RejectedExecutionException e) { finish(key, null); }
    }

    private static URI resolve(String imageUrl, String baseUrl) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.length() > 2048)
            throw new IllegalArgumentException("Image unavailable");
        URI requested = URI.create(imageUrl.trim());
        URI uri = requested.isAbsolute() ? requested : URI.create(baseUrl).resolve(requested);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.toASCIIString().length() > 2048) throw new IllegalArgumentException("HTTPS image required");
        return uri;
    }

    private static void finish(String key, Bitmap bitmap) {
        List<Consumer<Bitmap>> callbacks;
        synchronized (LOCK) {
            if (bitmap != null) CACHE.put(key, bitmap);
            callbacks = PENDING.remove(key);
        }
        if (callbacks != null) for (Consumer<Bitmap> callback : callbacks) callback.accept(bitmap);
    }

    private static Bitmap download(URI start) throws IOException {
        URI uri = start;
        for (int redirect = 0; redirect <= 3; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "image/*");
            connection.setRequestProperty("User-Agent", "SnakeN3stLauncher-Android");
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || redirect == 3) return null;
                    uri = resolve(location, uri.toString());
                    continue;
                }
                if (status != 200 || connection.getContentLengthLong() > MAX_BYTES) return null;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) return null;
                        if (bytes.size() + count > MAX_BYTES) return null;
                        bytes.write(buffer, 0, count);
                    }
                }
                byte[] encoded = bytes.toByteArray();
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(encoded, 0, encoded.length, bounds);
                if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > MAX_DIMENSION
                        || bounds.outHeight > MAX_DIMENSION) return null;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > DECODE_DIMENSION)
                    options.inSampleSize *= 2;
                return BitmapFactory.decodeByteArray(encoded, 0, encoded.length, options);
            } finally { connection.disconnect(); }
        }
        return null;
    }
}
