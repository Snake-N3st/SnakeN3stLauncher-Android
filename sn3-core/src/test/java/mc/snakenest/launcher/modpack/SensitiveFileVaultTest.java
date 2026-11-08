package mc.snakenest.launcher.modpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real files on disk, since what this class is for is precisely what ends up stored there. */
class SensitiveFileVaultTest {

    private static SecretKey key() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void doesNothingWithoutACryptList(@TempDir Path instance) throws Exception {
        write(instance.resolve("config").resolve("secret.json"), "top-secret");

        assertEquals(0, SensitiveFileVault.encryptAll(instance, key()));
        assertEquals("top-secret", Files.readString(instance.resolve("config").resolve("secret.json")));
    }

    @Test
    void encryptsAListedFileAndRemovesThePlaintext(@TempDir Path instance) throws Exception {
        write(instance.resolve(".cryptlist"), "config/secret.json\n");
        Path secret = instance.resolve("config").resolve("secret.json");
        write(secret, "top-secret");

        assertEquals(1, SensitiveFileVault.encryptAll(instance, key()));

        assertFalse(Files.exists(secret), "the plaintext must be gone");
        Path encrypted = instance.resolve("config").resolve("secret.json.sn3enc");
        assertTrue(Files.exists(encrypted));
        String raw = Files.readString(encrypted, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("top-secret"), "the secret must not be readable in the encrypted file");
    }

    @Test
    void leavesUnlistedFilesAlone(@TempDir Path instance) throws Exception {
        write(instance.resolve(".cryptlist"), "config/secret.json\n");
        write(instance.resolve("config").resolve("secret.json"), "top-secret");
        Path ordinary = instance.resolve("config").resolve("public.json");
        write(ordinary, "nothing-special");

        SensitiveFileVault.encryptAll(instance, key());

        assertTrue(Files.exists(ordinary));
        assertEquals("nothing-special", Files.readString(ordinary));
    }

    @Test
    void supportsGlobsAndIgnoresCommentsAndBlankLines(@TempDir Path instance) throws Exception {
        write(instance.resolve(".cryptlist"), """
                # everything secret below
                config/*.secret

                """);
        write(instance.resolve("config").resolve("a.secret"), "aaa");
        write(instance.resolve("config").resolve("b.secret"), "bbb");
        write(instance.resolve("config").resolve("c.txt"), "ccc");

        assertEquals(2, SensitiveFileVault.encryptAll(instance, key()));

        assertTrue(Files.exists(instance.resolve("config").resolve("a.secret.sn3enc")));
        assertTrue(Files.exists(instance.resolve("config").resolve("b.secret.sn3enc")));
        assertEquals("ccc", Files.readString(instance.resolve("config").resolve("c.txt")));
    }

    @Test
    void decryptRestoresTheExactOriginalContent(@TempDir Path instance) throws Exception {
        SecretKey key = key();
        write(instance.resolve(".cryptlist"), "config/secret.json\n");
        Path secret = instance.resolve("config").resolve("secret.json");
        write(secret, "{\"apiKey\": \"very-secret-value\"}");

        SensitiveFileVault.encryptAll(instance, key);
        assertEquals(1, SensitiveFileVault.decryptAll(instance, key));

        assertEquals("{\"apiKey\": \"very-secret-value\"}", Files.readString(secret));
        assertFalse(Files.exists(instance.resolve("config").resolve("secret.json.sn3enc")));
    }

    @Test
    void encryptIsIdempotentAndNeverDoubleEncrypts(@TempDir Path instance) throws Exception {
        SecretKey key = key();
        write(instance.resolve(".cryptlist"), "config/secret.json\n");
        write(instance.resolve("config").resolve("secret.json"), "top-secret");

        assertEquals(1, SensitiveFileVault.encryptAll(instance, key));
        // Nothing left in plaintext, so a second pass (e.g. the startup re-seal) has no work to do.
        assertEquals(0, SensitiveFileVault.encryptAll(instance, key));

        SensitiveFileVault.decryptAll(instance, key);
        assertEquals("top-secret", Files.readString(instance.resolve("config").resolve("secret.json")));
    }

    @Test
    void decryptWithTheWrongKeyLeavesTheEncryptedFileInPlace(@TempDir Path instance) throws Exception {
        write(instance.resolve(".cryptlist"), "config/secret.json\n");
        write(instance.resolve("config").resolve("secret.json"), "top-secret");
        SensitiveFileVault.encryptAll(instance, key());

        // A wrong key must not silently destroy the ciphertext - the right PIN still has to work after.
        assertEquals(0, SensitiveFileVault.decryptAll(instance, key()));
        assertTrue(Files.exists(instance.resolve("config").resolve("secret.json.sn3enc")));
        assertFalse(Files.exists(instance.resolve("config").resolve("secret.json")));
    }

    @Test
    void wipeDeletesEncryptedFilesWithoutTouchingTheRest(@TempDir Path instance) throws Exception {
        write(instance.resolve(".cryptlist"), "config/secret.json\n");
        write(instance.resolve("config").resolve("secret.json"), "top-secret");
        Path ordinary = instance.resolve("config").resolve("public.json");
        write(ordinary, "nothing-special");
        SensitiveFileVault.encryptAll(instance, key());

        assertEquals(1, SensitiveFileVault.wipeEncrypted(instance));

        assertFalse(Files.exists(instance.resolve("config").resolve("secret.json.sn3enc")));
        assertTrue(Files.exists(ordinary), "an unrelated file must survive the wipe");
        assertTrue(Files.exists(instance.resolve(".cryptlist")), "the list itself is not a secret");
    }

    @Test
    void aMalformedPatternIsIgnoredWithoutBreakingTheOthers(@TempDir Path instance) throws Exception {
        write(instance.resolve(".cryptlist"), "config/[unclosed\nconfig/secret.json\n");
        write(instance.resolve("config").resolve("secret.json"), "top-secret");

        assertEquals(1, SensitiveFileVault.encryptAll(instance, key()));
        assertTrue(Files.exists(instance.resolve("config").resolve("secret.json.sn3enc")));
    }

    @Test
    void aPatternCannotReachOutsideTheInstance(@TempDir Path parent) throws Exception {
        Path instance = parent.resolve("instance");
        Path outside = parent.resolve("outside-secret.txt");
        write(outside, "must-not-be-touched");
        write(instance.resolve(".cryptlist"), "../outside-secret.txt\n../../**\n");

        SensitiveFileVault.encryptAll(instance, key());

        assertTrue(Files.exists(outside), "a pattern is only ever matched against files inside the instance");
        assertEquals("must-not-be-touched", Files.readString(outside));
    }

    @Test
    void doesNothingWhenTheInstanceDoesNotExistYet(@TempDir Path parent) throws Exception {
        Path missing = parent.resolve("never-installed");

        assertEquals(0, SensitiveFileVault.encryptAll(missing, key()));
        assertEquals(0, SensitiveFileVault.decryptAll(missing, key()));
        assertEquals(0, SensitiveFileVault.wipeEncrypted(missing));
    }
}
