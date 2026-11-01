package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.util.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;

class InstalledModMatcherTest {
    @TempDir Path mods;
    private static final byte[] CONTENT = "same Fabric API bytes".getBytes(StandardCharsets.UTF_8);
    private static String hash(byte[] content, String algorithm) throws Exception {
        return Hex.encode(MessageDigest.getInstance(algorithm).digest(content));
    }

    @Test void recognizesEncodedManagedFilenameByContentInsteadOfDownloadingPlusNamedDuplicate() throws Exception {
        Path managed = Files.write(mods.resolve("fabric-api-0.141.6%2B1.21.11.jar"), CONTENT);
        assertEquals(managed, InstalledModMatcher.findIdentical(mods, "SHA-512", hash(CONTENT, "SHA-512"), CONTENT.length).orElseThrow());
        assertFalse(Files.exists(mods.resolve("fabric-api-0.141.6+1.21.11.jar")));
    }

    @Test void preservesRealUpdatesEvenWithSameFilenameOrSameSize() throws Exception {
        byte[] newer = CONTENT.clone(); newer[0]++;
        Files.write(mods.resolve("fabric-api.jar"), CONTENT);
        assertTrue(InstalledModMatcher.findIdentical(mods, "SHA-512", hash(newer, "SHA-512"), newer.length).isEmpty());
        assertTrue(InstalledModMatcher.findIdentical(mods, "SHA-512", hash(CONTENT, "SHA-512"), CONTENT.length + 1).isEmpty());
    }

    @Test void supportsSha1FallbackAndUppercaseHex() throws Exception {
        Path installed = Files.write(mods.resolve("renamed.jar"), CONTENT);
        assertEquals(installed, InstalledModMatcher.findIdentical(mods, "SHA-1", hash(CONTENT, "SHA-1").toUpperCase(java.util.Locale.ROOT), CONTENT.length).orElseThrow());
    }

    @Test void ignoresDisabledFilesDirectoriesAndSymlinks() throws Exception {
        Files.write(mods.resolve("fabric-api.jar.disabled"), CONTENT);
        Files.createDirectory(mods.resolve("directory.jar"));
        Path outside = Files.write(mods.resolve("outside-data"), CONTENT);
        Files.createSymbolicLink(mods.resolve("linked.jar"), outside);
        assertTrue(InstalledModMatcher.findIdentical(mods, "SHA-512", hash(CONTENT, "SHA-512"), CONTENT.length).isEmpty());
    }

    @Test void missingDirectoryIsEmptyButMalformedHashesFail() throws Exception {
        assertTrue(InstalledModMatcher.findIdentical(mods.resolve("missing"), "SHA-512", hash(CONTENT, "SHA-512"), CONTENT.length).isEmpty());
        assertThrows(IOException.class, () -> InstalledModMatcher.findIdentical(mods, "SHA-512", "bad", CONTENT.length));
    }

    @Test void cancellationStopsBeforeScanning() throws Exception {
        String expected = hash(CONTENT, "SHA-512");
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedIOException.class, () -> InstalledModMatcher.findIdentical(mods, "SHA-512", expected, CONTENT.length)); }
        finally { Thread.interrupted(); }
    }
}
