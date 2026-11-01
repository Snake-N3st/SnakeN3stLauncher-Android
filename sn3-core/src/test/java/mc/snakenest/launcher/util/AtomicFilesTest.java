package mc.snakenest.launcher.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtomicFilesTest {

    @Test
    void writeCreatesParentDirsAndContent(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("nested/dir/file.txt");

        AtomicFiles.write(target, "hello".getBytes(StandardCharsets.UTF_8));

        assertEquals("hello", Files.readString(target));
    }

    @Test
    void writeOverwritesExistingFile(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("file.txt");
        AtomicFiles.write(target, "first".getBytes(StandardCharsets.UTF_8));
        AtomicFiles.write(target, "second".getBytes(StandardCharsets.UTF_8));

        assertEquals("second", Files.readString(target));
    }

    @Test
    void writeLeavesNoTempFileBehind(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("file.txt");
        AtomicFiles.write(target, "data".getBytes(StandardCharsets.UTF_8));

        try (var entries = Files.list(tempDir)) {
            assertEquals(1, entries.count());
        }
    }

    @Test
    void writeVerifyingReturnsMatchingHash(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("blob.bin");
        byte[] content = "content-to-verify".getBytes(StandardCharsets.UTF_8);

        String hash = AtomicFiles.writeVerifying(target, new ByteArrayInputStream(content));

        assertEquals(Sha256.hex(content), hash);
        assertEquals("content-to-verify", Files.readString(target));
    }

    @Test
    void writeVerifiedCommitsWhenHashMatches(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("blob.bin");
        byte[] content = "good-content".getBytes(StandardCharsets.UTF_8);

        AtomicFiles.writeVerified(target, new ByteArrayInputStream(content), Sha256.hex(content));

        assertEquals("good-content", Files.readString(target));
    }

    @Test
    void writeVerifiedNeverCommitsOnHashMismatch(@TempDir Path tempDir) {
        Path target = tempDir.resolve("blob.bin");
        byte[] content = "tampered-content".getBytes(StandardCharsets.UTF_8);

        assertThrows(HashVerificationException.class,
                () -> AtomicFiles.writeVerified(target, new ByteArrayInputStream(content), "0".repeat(64)));

        assertFalse(Files.exists(target));
    }
}
