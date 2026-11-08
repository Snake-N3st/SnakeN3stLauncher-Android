package mc.snakenest.launcher.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HexTest {

    @Test
    void encodesLowercase() {
        assertEquals("00ff7a", Hex.encode(new byte[]{0x00, (byte) 0xFF, 0x7A}));
    }

    @Test
    void roundTrips() {
        byte[] original = {1, 2, 3, (byte) 255, 0, 127};
        assertArrayEquals(original, Hex.decode(Hex.encode(original)));
    }

    @Test
    void rejectsOddLength() {
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("abc"));
    }

    @Test
    void rejectsNonHexCharacters() {
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("zz"));
    }
}
