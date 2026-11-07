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
    private static volatile boolean networkDisabled;
    private static final java.util.Set<HttpURLConnection> CONNECTIONS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static void setOffline(boolean offline) {
        networkDisabled = offline;
        if(offline) for(HttpURLConnection connection:CONNECTIONS) connection.disconnect();
    }
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
        load(context,imageUrl,baseUrl,MAX_BYTES,DECODE_DIMENSION,callback);
    }

    /** Article covers need more detail than toolbar icons, with their own bounded budget. */
    public static void loadCover(Context context, String imageUrl, String baseUrl, Consumer<Drawable> callback) {
        load(context,imageUrl,baseUrl,8*1024*1024,1024,callback);
    }

    private static void load(Context context, String imageUrl, String baseUrl, int maxBytes, int decodeDimension, Consumer<Drawable> callback) {
        Context application = context.getApplicationContext();
        if(SnakeRepository.get(application).offlineMode) { MAIN.post(()->callback.accept(null)); return; }
        Consumer<Bitmap> deliver = bitmap -> MAIN.post(() -> callback.accept(bitmap == null ? null
                : new BitmapDrawable(application.getResources(), bitmap)));
        final URI uri;
        try { uri = resolve(imageUrl, baseUrl); }
        catch (RuntimeException e) { deliver.accept(null); return; }
        String key = decodeDimension+":"+uri.toASCIIString();
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
                try { if(!SnakeRepository.get(application).offlineMode) bitmap = download(uri,maxBytes,decodeDimension); }
                catch (IOException | RuntimeException e) { /* Optional image: retain the placeholder. */ }
                finish(key, bitmap);
            });
        } catch (RejectedExecutionException e) { finish(key, null); }
    }

    /** Invalidate RAM content when catalogue metadata changes without changing the image URL. */
    public static void invalidate(String imageUrl, String baseUrl) {
        try {
            String key = resolve(imageUrl, baseUrl).toASCIIString();
            synchronized (LOCK) { CACHE.remove(DECODE_DIMENSION+":"+key); CACHE.remove("1024:"+key); }
        } catch (RuntimeException ignored) { }
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

    private static Bitmap download(URI start,int maxBytes,int decodeDimension) throws IOException {
        if(networkDisabled) return null;
        URI uri = start;
        for (int redirect = 0; redirect <= 3; redirect++) {
            if(networkDisabled) return null;
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            CONNECTIONS.add(connection);
            if(networkDisabled) { CONNECTIONS.remove(connection); connection.disconnect(); return null; }
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
                if (status != 200 || connection.getContentLengthLong() > maxBytes) return null;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (networkDisabled || Thread.currentThread().isInterrupted()) return null;
                        if (bytes.size() + count > maxBytes) return null;
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
                while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > decodeDimension)
                    options.inSampleSize *= 2;
                return BitmapFactory.decodeByteArray(encoded, 0, encoded.length, options);
            } finally { CONNECTIONS.remove(connection); connection.disconnect(); }
        }
        return null;
    }
}
