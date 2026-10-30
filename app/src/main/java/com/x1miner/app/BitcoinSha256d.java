package com.x1miner.app;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Offline Bitcoin SHA-256d learning/validation helper.
 *
 * This class hashes exactly one supplied 80-byte block header. It does not
 * search nonces, connect to pools, submit shares, or perform mining.
 */
public final class BitcoinSha256d {
    public static final String GENESIS_HEADER_HEX =
            "01000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "3ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a"
            + "29ab5f49"
            + "ffff001d"
            + "1dac2b7c";

    /** Conventional human-readable hash of Bitcoin's genesis block. */
    public static final String GENESIS_BLOCK_HASH =
            "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f";

    private BitcoinSha256d() { }

    /**
     * Calculates SHA256(SHA256(header)) for one Bitcoin block header.
     *
     * @param headerHex exactly 160 hexadecimal characters (80 bytes)
     * @return conventional displayed block hash (digest bytes reversed)
     */
    public static String hashHeaderHex(String headerHex) throws NoSuchAlgorithmException {
        byte[] header = decodeHex(headerHex);
        if (header.length != 80) {
            throw new IllegalArgumentException("O cabeçalho Bitcoin precisa ter exatamente 80 bytes.");
        }

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] firstHash = sha256.digest(header);
        byte[] secondHash = sha256.digest(firstHash);
        reverse(secondHash);
        return encodeHex(secondHash);
    }

    /** Returns true only when the Bitcoin genesis-header test vector matches. */
    public static boolean verifyGenesisVector() throws NoSuchAlgorithmException {
        return GENESIS_BLOCK_HASH.equals(hashHeaderHex(GENESIS_HEADER_HEX));
    }

    private static byte[] decodeHex(String value) {
        if (value == null || (value.length() & 1) != 0) {
            throw new IllegalArgumentException("Cabeçalho hexadecimal inválido.");
        }
        byte[] output = new byte[value.length() / 2];
        for (int i = 0; i < output.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Cabeçalho contém caractere hexadecimal inválido.");
            }
            output[i] = (byte) ((high << 4) | low);
        }
        return output;
    }

    private static String encodeHex(byte[] bytes) {
        final char[] digits = "0123456789abcdef".toCharArray();
        char[] output = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            output[i * 2] = digits[value >>> 4];
            output[i * 2 + 1] = digits[value & 0x0f];
        }
        return new String(output);
    }

    private static void reverse(byte[] bytes) {
        for (int left = 0, right = bytes.length - 1; left < right; left++, right--) {
            byte temp = bytes[left];
            bytes[left] = bytes[right];
            bytes[right] = temp;
        }
    }
}
