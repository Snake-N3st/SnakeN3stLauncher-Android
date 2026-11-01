package mc.snakenest.launcher.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Sha256Test {

    // Well-known test vectors (verified against `sha256sum`).
    private static final String EMPTY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String HELLO_HASH = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @Test
    void emptyByteArray() {
        assertEquals(EMPTY_HASH, Sha256.hex(new byte[0]));
    }

    @Test
    void knownVector() {
        assertEquals(HELLO_HASH, Sha256.hex("hello".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void streamMatchesByteArray() throws IOException {
        byte[] data = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
        String fromBytes = Sha256.hex(data);
        String fromStream = Sha256.hex(new ByteArrayInputStream(data));
        assertEquals(fromBytes, fromStream);
    }
}
