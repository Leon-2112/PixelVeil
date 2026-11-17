# PixelVeil — Java Image Steganography

PixelVeil is a Java Swing desktop application that hides a password-encrypted text message inside an image. The same app has **Encrypt** and **Decrypt** modes.

## Features

- Choose a custom image from your computer.
- Encrypt mode: enter a message and password, select where to save the output, and preview the saved image.
- Decrypt mode: open the encoded image, enter the same password, and reveal the message in the app.
- AES-256-GCM encryption, with a random salt and nonce and a PBKDF2-HMAC-SHA-256 key derivation step.
- Least-significant-bit (LSB) embedding in RGB channels.
- Uses only the Java standard library; no Maven dependencies or internet connection are needed.

## Requirements

- JDK 17 or newer (JDK 21 recommended).

## Run the prebuilt JAR

A prebuilt `PixelVeil.jar` is included. With JDK 17 or newer installed, launch it from a terminal with:

```bash
java -jar PixelVeil.jar
```

On Windows, you may also be able to double-click the JAR if `.jar` files are associated with Java. If not, use the command above or the `run.bat` script.

## Run on Windows

1. Install a JDK if you do not already have one.
2. Open this project folder in File Explorer.
3. Double-click `run.bat`, or open Command Prompt in this folder and run:

   ```bat
   run.bat
   ```

## Run on macOS / Linux

From this project folder, run:

```bash
chmod +x run.sh
./run.sh
```

Or compile and launch manually:

```bash
mkdir -p out
javac -d out src/main/java/com/pixelveil/*.java
java -cp out com.pixelveil.SteganographyApp
```

## How to use

1. Select **Encrypt**.
2. Click **Choose Image…**, then select a cover image. PNG, JPEG, BMP and GIF images can be opened.
3. Type the secret message and choose a password of at least 8 characters.
4. Click **Encrypt & Save PNG** and choose the destination. PixelVeil saves the encoded result as PNG and displays it in the result preview.
5. To recover it, choose **Decrypt**, open the saved PNG, enter the same password and click **Decrypt Message**.

## Important notes

- **Keep the encoded image in PNG format.** Do not convert it to JPEG, send it through apps that recompress images, resize it, or edit it. Lossy compression and pixel changes can corrupt the hidden message.
- The hidden data reduces the image's usable capacity. A larger image can hold a longer message; the app reports an error if the selected image is too small.
- AES-GCM protects the message's confidentiality and detects tampering. A wrong password or modified image will normally cause decryption to fail.
- LSB steganography is intended here as an educational project. It does not guarantee that an image cannot be analyzed for hidden data, and it is not a substitute for secure file-transfer practices.
- Use only images and content you have permission to process.

## Run the built-in smoke test

This checks that a message survives a PNG save/load cycle and that an incorrect password is rejected:

```bash
mkdir -p out
javac -encoding UTF-8 -d out src/main/java/com/pixelveil/*.java
javac -encoding UTF-8 -cp out -d out src/test/java/com/pixelveil/*.java
java -Djava.awt.headless=true -cp out com.pixelveil.SteganographyServiceSelfTest
```
