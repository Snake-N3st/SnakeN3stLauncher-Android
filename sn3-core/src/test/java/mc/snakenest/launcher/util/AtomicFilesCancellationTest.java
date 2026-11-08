package mc.snakenest.launcher.util;

import java.nio.file.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtomicFilesCancellationTest {
    @Test void interruptedDownloadNeverReplacesExistingFile(@TempDir Path dir) throws Exception {
        Path target=dir.resolve("mod.jar"); Files.writeString(target,"existing");
        byte[] replacement="replacement".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedIOException.class,()->AtomicFiles.writeVerified(target,new ByteArrayInputStream(replacement),Sha256.hex(replacement))); }
        finally { Thread.interrupted(); }
        assertEquals("existing",Files.readString(target));
        try(var files=Files.list(dir)) { assertEquals(1,files.count()); }
    }
}
