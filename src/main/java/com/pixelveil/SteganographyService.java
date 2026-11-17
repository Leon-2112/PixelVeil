package com.pixelveil;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

public final class SteganographyService {
    private static final int SALT_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int VERSION = 1;
    private static final int PAYLOAD_OVERHEAD = 1 + SALT_LENGTH + NONCE_LENGTH + 16;
    private static final int LENGTH_HEADER_BYTES = Integer.BYTES;
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SteganographyService() { }

    public static long getMaxMessageBytes(BufferedImage image) {
        if (image == null) return 0L;
        long capacityBits = (long) image.getWidth() * image.getHeight() * 3L;
        return Math.max(0L, capacityBits / 8L - LENGTH_HEADER_BYTES - PAYLOAD_OVERHEAD);
    }

    public static BufferedImage hideMessage(BufferedImage coverImage, String message, char[] password)
            throws GeneralSecurityException {
        if (coverImage == null) throw new IllegalArgumentException("Choose an image first.");
        if (message == null || message.isEmpty()) throw new IllegalArgumentException("Enter a message to hide.");
        if (password == null || password.length == 0) throw new IllegalArgumentException("Enter a password.");

        byte[] plainText = message.getBytes(StandardCharsets.UTF_8);
        byte[] payload = null;
        byte[] packageBytes = null;
        try {
            long capacityBits = (long) coverImage.getWidth() * coverImage.getHeight() * 3L;
            long requiredBits = (long) (LENGTH_HEADER_BYTES + PAYLOAD_OVERHEAD + plainText.length) * 8L;
            if (requiredBits > capacityBits) {
                long maxBytes = getMaxMessageBytes(coverImage);
                throw new IllegalArgumentException(String.format(
                        "This image is too small for the message. It can hold about %d UTF-8 bytes; your message uses %d bytes. Choose a larger image or shorten the message.",
                        maxBytes, plainText.length));
            }

            payload = encrypt(plainText, password);
            packageBytes = ByteBuffer.allocate(LENGTH_HEADER_BYTES + payload.length)
                    .putInt(payload.length)
                    .put(payload)
                    .array();
            BufferedImage encoded = copyImage(coverImage);
            writeBytesToImage(encoded, packageBytes);
            return encoded;
        } finally {
            Arrays.fill(plainText, (byte) 0);
            if (payload != null) Arrays.fill(payload, (byte) 0);
            if (packageBytes != null) Arrays.fill(packageBytes, (byte) 0);
        }
    }

    public static String revealMessage(BufferedImage encodedImage, char[] password)
            throws GeneralSecurityException {
        if (encodedImage == null) throw new IllegalArgumentException("Choose an image first.");
        if (password == null || password.length == 0) throw new IllegalArgumentException("Enter the password used during encryption.");

        long capacityBits = (long) encodedImage.getWidth() * encodedImage.getHeight() * 3L;
        if (capacityBits < (long) LENGTH_HEADER_BYTES * 8L) {
            throw new IllegalArgumentException("This image is too small to contain a hidden message.");
        }

        byte[] header = readBytesFromImage(encodedImage, LENGTH_HEADER_BYTES);
        int payloadLength = ByteBuffer.wrap(header).getInt();
        Arrays.fill(header, (byte) 0);

        long maxPayload = (capacityBits / 8L) - LENGTH_HEADER_BYTES;
        if (payloadLength < PAYLOAD_OVERHEAD || (long) payloadLength > maxPayload) {
            throw new IllegalArgumentException("No valid PixelVeil message was found in this image.");
        }

        byte[] allBytes = readBytesFromImage(encodedImage, LENGTH_HEADER_BYTES + payloadLength);
        byte[] payload = Arrays.copyOfRange(allBytes, LENGTH_HEADER_BYTES, allBytes.length);
        Arrays.fill(allBytes, (byte) 0);

        byte[] plainText = null;
        try {
            plainText = decrypt(payload, password);
            return new String(plainText, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(payload, (byte) 0);
            if (plainText != null) {
                Arrays.fill(plainText, (byte) 0);
            }
        }
    }

    private static byte[] encrypt(byte[] plainText, char[] password) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_LENGTH];
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        byte[] encrypted = cipher.doFinal(plainText);

        return ByteBuffer.allocate(1 + SALT_LENGTH + NONCE_LENGTH + encrypted.length)
                .put((byte) VERSION)
                .put(salt)
                .put(nonce)
                .put(encrypted)
                .array();
    }

    private static byte[] decrypt(byte[] payload, char[] password) throws GeneralSecurityException {
        if (payload.length < PAYLOAD_OVERHEAD) {
            throw new IllegalArgumentException("No valid PixelVeil message was found in this image.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int version = Byte.toUnsignedInt(buffer.get());
        if (version != VERSION) {
            throw new IllegalArgumentException("No valid PixelVeil message was found in this image.");
        }

        byte[] salt = new byte[SALT_LENGTH];
        byte[] nonce = new byte[NONCE_LENGTH];
        byte[] ciphertext = new byte[buffer.remaining() - SALT_LENGTH - NONCE_LENGTH];
        buffer.get(salt);
        buffer.get(nonce);
        buffer.get(ciphertext);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return cipher.doFinal(ciphertext);
        } catch (BadPaddingException | javax.crypto.IllegalBlockSizeException ex) {
            throw new GeneralSecurityException(
                    "Decryption failed. The password may be incorrect, or the image may have been changed or may not contain a PixelVeil message.", ex);
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(ciphertext, (byte) 0);
        }
    }

    private static SecretKeySpec deriveKey(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256);
        try {
            byte[] keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            try {
                return new SecretKeySpec(keyBytes, "AES");
            } finally {
                Arrays.fill(keyBytes, (byte) 0);
            }
        } finally {
            spec.clearPassword();
        }
    }

    private static BufferedImage copyImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private static void writeBytesToImage(BufferedImage image, byte[] bytes) {
        long bitIndex = 0;
        int width = image.getWidth();
        for (byte value : bytes) {
            for (int shift = 7; shift >= 0; shift--) {
                int bit = (value >>> shift) & 1;
                long pixelIndex = bitIndex / 3L;
                int channel = (int) (bitIndex % 3L);
                int x = (int) (pixelIndex % width);
                int y = (int) (pixelIndex / width);
                int pixel = image.getRGB(x, y);
                int alpha = (pixel >>> 24) & 0xff;
                int red = (pixel >>> 16) & 0xff;
                int green = (pixel >>> 8) & 0xff;
                int blue = pixel & 0xff;
                if (channel == 0) red = (red & 0xfe) | bit;
                else if (channel == 1) green = (green & 0xfe) | bit;
                else blue = (blue & 0xfe) | bit;
                image.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
                bitIndex++;
            }
        }
    }

    private static byte[] readBytesFromImage(BufferedImage image, int length) {
        byte[] bytes = new byte[length];
        long bitIndex = 0;
        int width = image.getWidth();
        for (int byteIndex = 0; byteIndex < length; byteIndex++) {
            int value = 0;
            for (int i = 0; i < 8; i++) {
                long pixelIndex = bitIndex / 3L;
                int channel = (int) (bitIndex % 3L);
                int x = (int) (pixelIndex % width);
                int y = (int) (pixelIndex / width);
                int pixel = image.getRGB(x, y);
                int component = channel == 0 ? (pixel >>> 16) & 0xff
                        : channel == 1 ? (pixel >>> 8) & 0xff : pixel & 0xff;
                value = (value << 1) | (component & 1);
                bitIndex++;
            }
            bytes[byteIndex] = (byte) value;
        }
        return bytes;
    }
}
