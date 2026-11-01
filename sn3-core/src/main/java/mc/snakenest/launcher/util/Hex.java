package mc.snakenest.launcher.util;

/**
 * Lower-case hex codec. Used for keys, signatures and hashes throughout the
 * launcher - kept in one place since a hand-rolled parser is exactly the
 * kind of small utility that's easy to get subtly wrong on malformed input.
 */
public final class Hex {

    private static final char[] DIGITS = "0123456789abcdef".toCharArray();

    private Hex() {
    }

    public static String encode(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = DIGITS[v >>> 4];
            out[i * 2 + 1] = DIGITS[v & 0x0F];
        }
        return new String(out);
    }

    /**
     * @throws IllegalArgumentException if {@code hex} has an odd length or contains
     *                                  a non-hex character
     */
    public static byte[] decode(String hex) {
        if ((hex.length() % 2) != 0) {
            throw new IllegalArgumentException("Odd-length hex string");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(2 * i), 16);
            int lo = Character.digit(hex.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex character at index " + (2 * i));
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
