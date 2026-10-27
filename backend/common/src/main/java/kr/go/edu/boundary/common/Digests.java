package kr.go.edu.boundary.common;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Digests {
    private Digests() { }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static String sha256(String data) {
        return sha256(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Fields(String... values) {
        int size = 0;
        byte[][] parts = new byte[values.length][];
        for (int index = 0; index < values.length; index++) {
            parts[index] = values[index].getBytes(StandardCharsets.UTF_8);
            size += Integer.BYTES + parts[index].length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (byte[] part : parts) {
            buffer.putInt(part.length).put(part);
        }
        return sha256(buffer.array());
    }
}

