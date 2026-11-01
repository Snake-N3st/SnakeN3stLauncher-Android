package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.net.RawResponse;
import mc.snakenest.launcher.util.AtomicFiles;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Downloads the site's framed blob stream without buffering blob contents in memory. */
public final class ModpackBatchDownloader {
    private static final int BATCH_SIZE = 64;
    private static final int MAX_HEADER = 96;
    private final ModpackApiClient api;

    public ModpackBatchDownloader(ModpackApiClient api) {
        this.api = api;
    }

    public void fetch(String slug, List<ManifestFile> files, PlayerSession session, Path cacheDir)
            throws IOException, InterruptedException {
        Map<String, ManifestFile> unique = new LinkedHashMap<>();
        for (ManifestFile file : files) {
            checkInterrupted();
            if (file.url() != null) continue;
            if (file.hash() == null || !file.hash().matches("[0-9a-fA-F]{64}") || file.size() < 0) {
                throw new IOException("Invalid manifest blob hash or size");
            }
            String hash = file.hash().toLowerCase(Locale.ROOT);
            ManifestFile previous = unique.putIfAbsent(hash, file);
            if (previous != null && previous.size() != file.size()) {
                throw new IOException("Conflicting manifest sizes for blob " + hash);
            }
        }
        List<String> hashes = new ArrayList<>(unique.keySet());
        for (int start = 0; start < hashes.size(); start += BATCH_SIZE) {
            checkInterrupted();
            List<String> batch = new ArrayList<>(hashes.subList(start, Math.min(start + BATCH_SIZE, hashes.size())));
            try (RawResponse response = api.downloadBlobs(slug, batch, session)) {
                if (response.statusCode() == 404 || response.statusCode() == 405) {
                    throw new IOException("Le site doit être mis à jour : endpoint de téléchargement groupé indisponible (HTTP "
                            + response.statusCode() + ")");
                }
                if (!response.isSuccess()) {
                    throw new IOException("Blob batch download failed (HTTP " + response.statusCode() + ")");
                }
                InputStream stream = new BufferedInputStream(response.body());
                if (!"SN3B1".equals(readLine(stream))) throw new IOException("Invalid blob batch magic");
                for (String hash : batch) {
                    checkInterrupted();
                    String header = readLine(stream);
                    String[] parts = header.split(" ", -1);
                    if (parts.length != 2 || !parts[0].equals(hash) || !parts[1].matches("[0-9]+")) {
                        throw new IOException("Unexpected blob batch header for " + hash);
                    }
                    long size;
                    try { size = Long.parseLong(parts[1]); }
                    catch (NumberFormatException e) { throw new IOException("Invalid blob batch size", e); }
                    if (size != unique.get(hash).size()) throw new IOException("Blob batch size differs from manifest for " + hash);
                    AtomicFiles.writeVerified(cacheDir.resolve(hash), new BlobStream(stream, size), hash);
                }
                checkInterrupted();
                if (stream.read() != -1) throw new IOException("Extra content in blob batch response");
            }
        }
    }

    private static String readLine(InputStream stream) throws IOException {
        StringBuilder line = new StringBuilder();
        while (true) {
            checkInterrupted();
            int value = stream.read();
            if (value == -1) throw new EOFException("Truncated blob batch header");
            if (value == '\n') return line.toString();
            if (value < 32 || value > 126 || line.length() >= MAX_HEADER) {
                throw new IOException("Invalid or overlong blob batch header");
            }
            line.append((char) value);
        }
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Blob batch download cancelled");
    }

    /** Closing a verified entry must leave the enclosing batch stream open. */
    private static final class BlobStream extends InputStream {
        private final InputStream source;
        private long remaining;
        BlobStream(InputStream source, long size) { this.source = source; this.remaining = size; }
        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
        }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            checkInterrupted();
            if (length == 0) return 0;
            if (remaining == 0) return -1;
            int count = source.read(buffer, offset, (int) Math.min(remaining, length));
            if (count == -1) throw new EOFException("Truncated blob batch content");
            remaining -= count;
            return count;
        }
        @Override public void close() { }
    }
}
