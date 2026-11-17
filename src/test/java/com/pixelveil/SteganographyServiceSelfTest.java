package com.pixelveil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

public final class SteganographyServiceSelfTest {
    public static void main(String[] args) throws Exception {
        testBasicRoundTrip();
        testTransparentImage();
        testTamperingDetection();
        testMaxCapacity();
        testUnencodedImageRejection();
        testInputValidation();
        System.out.println("ALL PixelVeil tests passed successfully!");
    }

    private static void testBasicRoundTrip() throws Exception {
        BufferedImage cover = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < cover.getHeight(); y++) {
            for (int x = 0; x < cover.getWidth(); x++) {
                int r = (x * 3 + y) & 255;
                int g = (y * 5 + x) & 255;
                int b = (x + y * 2) & 255;
                cover.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        String expected = "PixelVeil test: Hello, नमस्कार 🌻\nSecond line with special characters: €$%^&*()!";
        char[] password = "SelfTestPassword123".toCharArray();
        BufferedImage encoded = SteganographyService.hideMessage(cover, expected, password);
        File png = File.createTempFile("pixelveil-basic-test", ".png");
        try {
            if (!ImageIO.write(encoded, "png", png)) throw new AssertionError("PNG writer unavailable");
            BufferedImage reopened = ImageIO.read(png);
            String actual = SteganographyService.revealMessage(reopened, password);
            if (!expected.equals(actual)) throw new AssertionError("Message changed during PNG round-trip");

            try {
                SteganographyService.revealMessage(reopened, "IncorrectPassword123".toCharArray());
                throw new AssertionError("Incorrect password was accepted");
            } catch (GeneralSecurityException expectedFailure) {
            }
            System.out.println("Basic round-trip and wrong-password rejection passed.");
        } finally {
            if (!png.delete()) png.deleteOnExit();
        }
    }

    private static void testTransparentImage() throws Exception {
        BufferedImage cover = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < cover.getHeight(); y++) {
            for (int x = 0; x < cover.getWidth(); x++) {
                int a = (x < 50) ? 0 : 255;
                int r = (x * 7) & 255;
                int g = (y * 11) & 255;
                int b = 128;
                cover.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        String expected = "Testing message in transparent PNG!";
        char[] password = "TransparentPass1".toCharArray();
        BufferedImage encoded = SteganographyService.hideMessage(cover, expected, password);
        File png = File.createTempFile("pixelveil-trans-test", ".png");
        try {
            if (!ImageIO.write(encoded, "png", png)) throw new AssertionError("PNG writer unavailable");
            BufferedImage reopened = ImageIO.read(png);
            String actual = SteganographyService.revealMessage(reopened, password);
            if (!expected.equals(actual)) throw new AssertionError("Transparent image decode failed");
            System.out.println("Transparent image round-trip passed.");
        } finally {
            if (!png.delete()) png.deleteOnExit();
        }
    }

    private static void testTamperingDetection() throws Exception {
        BufferedImage cover = new BufferedImage(150, 150, BufferedImage.TYPE_INT_RGB);
        String expected = "Message to be tampered with a longer text to span across pixels.";
        char[] password = "TamperPassword123".toCharArray();
        BufferedImage encoded = SteganographyService.hideMessage(cover, expected, password);

        int rgbPayload = encoded.getRGB(95, 0);
        encoded.setRGB(95, 0, rgbPayload ^ 1);
        try {
            SteganographyService.revealMessage(encoded, password);
            throw new AssertionError("Tampered ciphertext should have failed authentication");
        } catch (GeneralSecurityException expectedFailure) {
        }

        encoded.setRGB(95, 0, rgbPayload);
        int rgbHeader = encoded.getRGB(1, 0);
        encoded.setRGB(1, 0, rgbHeader ^ 1);
        try {
            SteganographyService.revealMessage(encoded, password);
            throw new AssertionError("Tampered header should have been rejected");
        } catch (Exception expectedFailure) {
        }

        System.out.println("Tampering detection passed.");
    }

    private static void testMaxCapacity() throws Exception {
        BufferedImage cover = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        long maxBytes = SteganographyService.getMaxMessageBytes(cover);
        if (maxBytes <= 0) throw new AssertionError("Max bytes should be positive for 50x50 image");

        byte[] bytes = new byte[(int) maxBytes];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) ('A' + (i % 26));
        String maxMessage = new String(bytes, StandardCharsets.UTF_8);

        char[] password = "CapacityPassword1".toCharArray();
        BufferedImage encoded = SteganographyService.hideMessage(cover, maxMessage, password);
        String revealed = SteganographyService.revealMessage(encoded, password);
        if (!maxMessage.equals(revealed)) throw new AssertionError("Max capacity message corrupted");

        String overMessage = maxMessage + "X";
        try {
            SteganographyService.hideMessage(cover, overMessage, password);
            throw new AssertionError("Over-capacity message should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.out.println("Capacity boundary check passed.");
        }
    }

    private static void testUnencodedImageRejection() {
        BufferedImage cover = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        char[] password = "SomePassword123".toCharArray();
        try {
            SteganographyService.revealMessage(cover, password);
            throw new AssertionError("Unencoded image should be rejected");
        } catch (IllegalArgumentException expected) {
            System.out.println("Raw unencoded image rejection passed: " + expected.getMessage());
        } catch (Exception ex) {
            throw new AssertionError("Expected IllegalArgumentException but got: " + ex);
        }
    }

    private static void testInputValidation() throws Exception {
        char[] pass = "Password123".toCharArray();
        BufferedImage img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);

        try {
            SteganographyService.hideMessage(null, "Test", pass);
            throw new AssertionError("Null image should be rejected");
        } catch (IllegalArgumentException expected) {}

        try {
            SteganographyService.hideMessage(img, "", pass);
            throw new AssertionError("Empty message should be rejected");
        } catch (IllegalArgumentException expected) {}

        try {
            SteganographyService.hideMessage(img, "Test", null);
            throw new AssertionError("Null password should be rejected");
        } catch (IllegalArgumentException expected) {}

        try {
            SteganographyService.revealMessage(null, pass);
            throw new AssertionError("Null image in reveal should be rejected");
        } catch (Exception expected) {}

        try {
            SteganographyService.revealMessage(img, new char[0]);
            throw new AssertionError("Empty password in reveal should be rejected");
        } catch (Exception expected) {}

        System.out.println("Input validation checks passed.");
    }
}
