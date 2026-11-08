package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.net.HttpJsonClient;
import mc.snakenest.launcher.net.RawResponse;
import mc.snakenest.launcher.util.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DownloadProgressTest {
    @TempDir Path root;
    private final ModpackApiClient api = mock(ModpackApiClient.class);
    private final HttpJsonClient http = mock(HttpJsonClient.class);
    private static final byte[] CONTENT = new byte[512 * 1024];
    private static String hash() { return Sha256.hex(CONTENT); }
    private static ManifestFile file(String path, String url) { return new ManifestFile(path, hash(), CONTENT.length, url); }
    private static byte[] frame() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(("SN3B1\n" + hash() + " " + CONTENT.length + "\n").getBytes(StandardCharsets.US_ASCII));
        output.write(CONTENT);
        return output.toByteArray();
    }
    private static RawResponse response(byte[] bytes) { return new RawResponse(200, new ByteArrayInputStream(bytes)); }
    private static ModpackManifest manifest(List<ManifestFile> files) {
        return new ModpackManifest("1", null, null, 0, "1.20.4", "vanilla", null, files, null, ModpackSettings.DEFAULT_MEMORY_MB);
    }

    @Test void payloadCallbacksOccurWithinLargeBlobAndCacheReadsDoNotCount() throws Exception {
        when(api.downloadBlobs("pack", List.of(hash()), null)).thenReturn(response(frame()));
        AtomicLong received = new AtomicLong();
        List<Long> increments = new ArrayList<>();
        ModpackFileDownloader downloader = new ModpackFileDownloader(api, http).withBlobCache(root.resolve("cache"), n -> {
            received.addAndGet(n); increments.add(n);
        });
        downloader.prefetch("pack", List.of(file("a", null), file("b", null)), null);
        assertEquals(CONTENT.length, received.get());
        assertTrue(increments.size() > 1);
        downloader.download("pack", file("a", null), root.resolve("instance"), null);
        downloader.fetchBytes("pack", file("b", null), null);
        assertEquals(CONTENT.length, received.get());
    }

    @Test void totalDeduplicatesInternalHashesButCountsEachExternalActionAndMonotonicProgress() throws Exception {
        when(api.downloadBlobs("pack", List.of(hash()), null)).thenReturn(response(frame()));
        when(http.getRaw(URI.create("https://example.test/blob"))).thenAnswer(call -> response(CONTENT));
        List<long[]> updates = Collections.synchronizedList(new ArrayList<>());
        SyncProgressListener listener = new SyncProgressListener() {
            public void onFileStarted(String path, long size) { }
            public void onFileDone(String path) { }
            public void onDeleted(String path) { }
            public void onDownloadProgress(long received, long total) { updates.add(new long[]{received, total}); }
        };
        new ModpackSyncEngine(new ModpackFileDownloader(api, http), new LocalManifestStore()).sync("pack", root.resolve("instance"),
                manifest(List.of(file("a", null), file("b", null), file("c", "https://example.test/blob"), file("d", "https://example.test/blob"))), null, listener);
        assertEquals(0, updates.get(0)[0]);
        long total = 3L * CONTENT.length;
        long previous = 0;
        for (long[] update : updates) {
            assertEquals(total, update[1]);
            assertTrue(update[0] >= previous);
            previous = update[0];
        }
        assertEquals(total, previous);
        verify(http, times(2)).getRaw(URI.create("https://example.test/blob"));
    }

    @Test void throttledProgressUpdatesDuringSlowLargeBlobAndFinishesOnlyAfterVerification() throws Exception {
        InputStream slow = new ByteArrayInputStream(frame()) {
            @Override public synchronized int read(byte[] buffer, int offset, int length) {
                try { Thread.sleep(110); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return super.read(buffer, offset, Math.min(length, 65536));
            }
        };
        when(api.downloadBlobs("pack", List.of(hash()), null)).thenReturn(new RawResponse(200, slow));
        List<Long> updates = new ArrayList<>();
        SyncProgressListener listener = new SyncProgressListener() {
            public void onFileStarted(String path, long size) { }
            public void onFileDone(String path) { }
            public void onDeleted(String path) { }
            public void onDownloadProgress(long received, long total) { updates.add(received); }
        };
        new ModpackSyncEngine(new ModpackFileDownloader(api, http), new LocalManifestStore()).sync("pack", root.resolve("instance"), manifest(List.of(file("a", null))), null, listener);
        assertTrue(updates.size() > 3);
        assertEquals((long) CONTENT.length, updates.get(updates.size() - 1));
        assertTrue(updates.subList(0, updates.size() - 1).stream().allMatch(n -> n < CONTENT.length));
    }

    @Test void invalidFinalBlobNeverReportsCompletion() throws Exception {
        byte[] corrupt = frame(); corrupt[corrupt.length - 1] = 1;
        when(api.downloadBlobs("pack", List.of(hash()), null)).thenReturn(response(corrupt));
        List<Long> updates = new ArrayList<>();
        SyncProgressListener listener = new SyncProgressListener() {
            public void onFileStarted(String path, long size) { }
            public void onFileDone(String path) { }
            public void onDeleted(String path) { }
            public void onDownloadProgress(long received, long total) { updates.add(received); }
        };
        assertThrows(IOException.class, () -> new ModpackSyncEngine(new ModpackFileDownloader(api, http), new LocalManifestStore())
                .sync("pack", root.resolve("instance"), manifest(List.of(file("a", null))), null, listener));
        assertTrue(updates.stream().allMatch(n -> n < CONTENT.length));
    }
}
