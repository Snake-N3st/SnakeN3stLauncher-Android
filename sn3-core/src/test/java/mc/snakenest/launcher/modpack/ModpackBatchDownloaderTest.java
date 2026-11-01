package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.net.RawResponse;
import mc.snakenest.launcher.util.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModpackBatchDownloaderTest {
    @TempDir Path cache;
    private final ModpackApiClient api = mock(ModpackApiClient.class);
    private final ModpackBatchDownloader downloader = new ModpackBatchDownloader(api);

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static ManifestFile file(String content) {
        return new ManifestFile(content, Sha256.hex(bytes(content)), bytes(content).length, null);
    }
    private static byte[] frame(List<ManifestFile> files) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes("SN3B1\n"));
        for (ManifestFile file : files) {
            out.write(bytes(file.hash() + " " + file.size() + "\n"));
            out.write(bytes(file.path()));
        }
        return out.toByteArray();
    }
    private void respond(byte[] content) throws Exception {
        when(api.downloadBlobs(eq("pack"), anyList(), isNull()))
                .thenReturn(new RawResponse(200, new ByteArrayInputStream(content)));
    }

    @Test void deduplicatesAndSkipsExternalFilesWithoutClosingBatchBetweenEntries() throws Exception {
        ManifestFile a = file("alpha"), b = file("beta");
        respond(frame(List.of(a, b)));
        downloader.fetch("pack", List.of(a, a, new ManifestFile("external", "invalid", -1, "https://example.test"), b), null, cache);
        verify(api).downloadBlobs("pack", List.of(a.hash(), b.hash()), null);
        assertEquals("alpha", Files.readString(cache.resolve(a.hash())));
        assertEquals("beta", Files.readString(cache.resolve(b.hash())));
    }

    @Test void chunksAt64Hashes() throws Exception {
        List<ManifestFile> files = new ArrayList<>();
        for (int i = 0; i < 65; i++) files.add(file("blob" + i));
        when(api.downloadBlobs(eq("pack"), anyList(), isNull())).thenAnswer(call -> {
            List<String> hashes = call.getArgument(1);
            return new RawResponse(200, new ByteArrayInputStream(frame(files.stream().filter(f -> hashes.contains(f.hash())).toList())));
        });
        downloader.fetch("pack", files, null, cache);
        verify(api).downloadBlobs("pack", files.subList(0, 64).stream().map(ManifestFile::hash).toList(), null);
        verify(api).downloadBlobs("pack", List.of(files.get(64).hash()), null);
        try (var entries = Files.list(cache)) { assertEquals(65, entries.count()); }
    }

    @Test void rejectsCorruptHashWithoutPublishingFile() throws Exception {
        ManifestFile a = file("alpha");
        respond(bytes("SN3B1\n" + a.hash() + " 5\nwrong"));
        assertThrows(IOException.class, () -> downloader.fetch("pack", List.of(a), null, cache));
        assertFalse(Files.exists(cache.resolve(a.hash())));
    }

    @Test void rejectsTruncatedContent() throws Exception {
        ManifestFile a = file("alpha");
        respond(bytes("SN3B1\n" + a.hash() + " 5\nalp"));
        assertThrows(IOException.class, () -> downloader.fetch("pack", List.of(a), null, cache));
        assertFalse(Files.exists(cache.resolve(a.hash())));
    }

    @Test void rejectsFramingErrors() throws Exception {
        ManifestFile a = file("alpha"), b = file("beta");
        List<byte[]> invalid = List.of(bytes("WRONG\n"), bytes("SN3B1\n"),
                bytes("SN3B1\n" + b.hash() + " 4\nbeta"),
                bytes("SN3B1\n" + a.hash() + " 4\nalph"),
                bytes("SN3B1\n" + "x".repeat(200) + "\n"),
                bytes("SN3B1\n" + a.hash() + " 5\nalphaextra"));
        for (byte[] body : invalid) {
            respond(body);
            assertThrows(IOException.class, () -> downloader.fetch("pack", List.of(a), null, cache));
        }
    }

    @Test void rejectsDuplicateAndMissingEntries() throws Exception {
        ManifestFile a = file("alpha"), b = file("beta");
        respond(frame(List.of(a, a)));
        assertThrows(IOException.class, () -> downloader.fetch("pack", List.of(a, b), null, cache));
        respond(frame(List.of(a)));
        assertThrows(IOException.class, () -> downloader.fetch("pack", List.of(a, b), null, cache));
    }

    @Test void refusesHttpFailuresWithoutSingleBlobFallback() throws Exception {
        for (int status : new int[]{403, 404, 405, 419}) {
            when(api.downloadBlobs(eq("pack"), anyList(), isNull()))
                    .thenReturn(new RawResponse(status, new ByteArrayInputStream(new byte[0])));
            IOException error = assertThrows(IOException.class, () -> downloader.fetch("pack", List.of(file("alpha")), null, cache));
            if (status == 404 || status == 405) assertTrue(error.getMessage().contains("mis à jour"));
        }
        verify(api, never()).downloadBlob(anyString(), anyString(), any());
    }

    @Test void cancellationStopsBeforeRequest() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> downloader.fetch("pack", List.of(file("alpha")), null, cache));
            verifyNoInteractions(api);
        } finally { Thread.interrupted(); }
    }
}
