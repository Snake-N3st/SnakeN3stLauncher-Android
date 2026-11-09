package mc.snakenest.launcher.modpack;

import com.github.luben.zstd.ZstdInputStreamNoFinalizer;
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
    private static final int MAX_HEADER = 128;
    private final ModpackApiClient api;

    public ModpackBatchDownloader(ModpackApiClient api) {
        this.api = api;
    }

    public void fetch(String slug, List<ManifestFile> files, PlayerSession session, Path cacheDir)
            throws IOException, InterruptedException {
        fetch(slug, files, session, cacheDir, ignored -> { });
    }

    public void fetch(String slug, List<ManifestFile> files, PlayerSession session, Path cacheDir,
                      java.util.function.LongConsumer onBytes) throws IOException, InterruptedException {
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
                String magic = readLine(stream);
                boolean encoded = "SN3B2".equals(magic);
                if (!encoded && !"SN3B1".equals(magic)) throw new IOException("Invalid blob batch magic");
                boolean usedOriginalFallback = false;
                for (String hash : batch) {
                    checkInterrupted();
                    String header = readLine(stream);
                    String[] parts = header.split(" ", -1);
                    if (parts.length != (encoded ? 4 : 2) || !parts[0].equals(hash) || !parts[1].matches("[0-9]+")) {
                        throw new IOException("Unexpected blob batch header for " + hash);
                    }
                    long size;
                    try { size = Long.parseLong(parts[1]); }
                    catch (NumberFormatException e) { throw new IOException("Invalid blob batch size", e); }
                    if (size != unique.get(hash).size()) throw new IOException("Blob batch size differs from manifest for " + hash);
                    long wireSize = size;
                    String encoding = "identity";
                    if (encoded) {
                        if (!parts[2].matches("[0-9]+")) throw new IOException("Invalid wire size");
                        try { wireSize = Long.parseLong(parts[2]); }
                        catch (NumberFormatException e) { throw new IOException("Invalid wire size", e); }
                        encoding = parts[3];
                    }
                    if ("identity".equals(encoding)) {
                        if (wireSize != size) throw new IOException("Identity size mismatch");
                        AtomicFiles.writeVerified(cacheDir.resolve(hash), new BlobStream(stream, size, onBytes), hash);
                    } else if ("zstd".equals(encoding)) {
                        if (wireSize <= 0 || wireSize > size) throw new IOException("Invalid compressed size");
                        BlobStream wire = new BlobStream(stream, wireSize, ignored -> { });
                        long[] credited = {0};
                        try (ZstdInputStreamNoFinalizer decoder = new ZstdInputStreamNoFinalizer(wire)) {
                            decoder.setLongMax(20); // At most a 1 MiB history window, matching the worker.
                            AtomicFiles.writeVerified(cacheDir.resolve(hash), new DecodedStream(decoder, size, count -> { credited[0] += count; onBytes.accept(count); }), hash);
                        } catch (IOException | LinkageError e) {
                            checkInterrupted();
                            // A corrupt representation must not prevent installing the original.
                            // Abandon this batch: decoder buffering may have consumed an unknown
                            // portion of the frame. Fetch only this and remaining entries raw.
                            response.close();
                            for (int index = batch.indexOf(hash); index < batch.size(); index++) {
                                String originalHash = batch.get(index);
                                downloadOriginal(slug, originalHash, session, unique.get(originalHash).size(),
                                        cacheDir, onBytes, index == batch.indexOf(hash) ? credited[0] : 0);
                            }
                            usedOriginalFallback = true;
                            break;
                        }
                    } else {
                        throw new IOException("Unsupported blob encoding: " + encoding);
                    }
                }
                checkInterrupted();
                if (!usedOriginalFallback && stream.read() != -1) throw new IOException("Extra content in blob batch response");
            }
        }
    }

    private void downloadOriginal(String slug, String hash, PlayerSession session, long size,
                                  Path cacheDir, java.util.function.LongConsumer onBytes, long credited)
            throws IOException, InterruptedException {
        try (RawResponse original = api.downloadBlob(slug, hash, session)) {
            if (!original.isSuccess()) throw new IOException("Original blob download failed (HTTP " + original.statusCode() + ")");
            long[] received = {0};
            AtomicFiles.writeVerified(cacheDir.resolve(hash), new DecodedStream(original.body(), size, count -> {
                long before = Math.max(received[0], credited);
                received[0] += count;
                long delta = Math.max(received[0], credited) - before;
                if (delta > 0) onBytes.accept(delta);
            }), hash);
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

    /** Bound expanded output and check decoder EOF before AtomicFiles can publish it. */
    private static final class DecodedStream extends InputStream {
        private final InputStream source;
        private long remaining;
        private final java.util.function.LongConsumer onBytes;
        DecodedStream(InputStream source, long size, java.util.function.LongConsumer onBytes) {
            this.source = source; this.remaining = size; this.onBytes = onBytes;
        }
        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
        }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            checkInterrupted();
            if (length == 0) return 0;
            if (remaining == 0) {
                if (source.read() != -1) throw new IOException("Expanded blob exceeds manifest size");
                return -1;
            }
            int count = source.read(buffer, offset, (int) Math.min(remaining, length));
            if (count == -1) throw new EOFException("Expanded blob shorter than manifest size");
            remaining -= count;
            if (count > 0) onBytes.accept(count);
            return count;
        }
    }

    /** Closing a verified entry must leave the enclosing batch stream open. */
    private static final class BlobStream extends InputStream {
        private final InputStream source;
        private long remaining;
        private final java.util.function.LongConsumer onBytes;
        BlobStream(InputStream source, long size, java.util.function.LongConsumer onBytes) {
            this.source = source; this.remaining = size; this.onBytes = onBytes;
        }
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
            if (count > 0) onBytes.accept(count);
            return count;
        }
        @Override public void close() { }
    }
}
