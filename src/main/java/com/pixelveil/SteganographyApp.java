package com.pixelveil;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public final class SteganographyApp extends JFrame {
    private static final Color APP_BG = new Color(14, 19, 32);
    private static final Color PANEL_BG = new Color(24, 32, 50);
    private static final Color PANEL_EDGE = new Color(52, 66, 91);
    private static final Color TEXT = new Color(238, 243, 250);
    private static final Color MUTED = new Color(160, 174, 196);
    private static final Color ACCENT = new Color(94, 174, 255);
    private static final Color ACCENT_DARK = new Color(28, 103, 185);

    private final JToggleButton encryptMode = new JToggleButton("Encrypt");
    private final JToggleButton decryptMode = new JToggleButton("Decrypt");
    private final JButton chooseImageButton = new JButton("Choose Image…");
    private final JButton runButton = new JButton("Encrypt & Save PNG");
    private final JButton copyButton = new JButton("Copy Message");
    private final JCheckBox showPasswordBox = new JCheckBox("Show");
    private final JTextArea messageArea = new JTextArea(4, 36);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JLabel messageLabel = new JLabel("Message to hide");
    private final JLabel statusLabel = new JLabel("Choose an image to begin.");
    private final JLabel resultNoteLabel = new JLabel("Lossless PNG output preserves the hidden bits.");
    private final ImagePreviewPanel sourcePreview = new ImagePreviewPanel("COVER IMAGE (SOURCE)");
    private final ImagePreviewPanel resultPreview = new ImagePreviewPanel("ENCRYPTED IMAGE PREVIEW");

    private final char defaultEchoChar;
    private BufferedImage sourceImage;
    private File sourceFile;
    private BufferedImage lastEncryptedImage;
    private File lastEncryptedFile;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }
        javax.swing.SwingUtilities.invokeLater(() -> new SteganographyApp().setVisible(true));
    }

    private SteganographyApp() {
        super("PixelVeil — Image Steganography");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(940, 690));
        setSize(1180, 840);
        setLocationRelativeTo(null);
        getContentPane().setBackground(APP_BG);
        setLayout(new BorderLayout(16, 16));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

        defaultEchoChar = passwordField.getEchoChar();

        add(buildHeader(), BorderLayout.NORTH);
        add(buildWorkspace(), BorderLayout.CENTER);
        add(buildControls(), BorderLayout.SOUTH);
        styleControls();
        configureModes();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 8));
        header.setOpaque(false);
        JPanel titles = new JPanel(new GridLayout(0, 1, 0, 3));
        titles.setOpaque(false);
        JLabel title = new JLabel("PixelVeil");
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        title.setForeground(TEXT);
        JLabel subtitle = new JLabel("Hide an encrypted message inside an ordinary-looking image.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);
        titles.add(title);
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);

        JPanel modePanel = new JPanel(new GridLayout(1, 2, 6, 0));
        modePanel.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        group.add(encryptMode);
        group.add(decryptMode);
        encryptMode.setSelected(true);
        modePanel.add(encryptMode);
        modePanel.add(decryptMode);
        header.add(modePanel, BorderLayout.EAST);
        encryptMode.addActionListener(e -> configureModes());
        decryptMode.addActionListener(e -> configureModes());
        return header;
    }

    private JSplitPane buildWorkspace() {
        JPanel sourcePanel = new JPanel(new BorderLayout(0, 8));
        sourcePanel.setBackground(PANEL_BG);
        sourcePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PANEL_EDGE), BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        sourcePanel.add(sourcePreview, BorderLayout.CENTER);
        sourcePanel.add(chooseImageButton, BorderLayout.SOUTH);

        JPanel resultPanel = new JPanel(new BorderLayout(0, 8));
        resultPanel.setBackground(PANEL_BG);
        resultPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PANEL_EDGE), BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        resultPanel.add(resultPreview, BorderLayout.CENTER);
        resultNoteLabel.setForeground(MUTED);
        resultNoteLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        resultNoteLabel.setHorizontalAlignment(SwingConstants.CENTER);
        resultPanel.add(resultNoteLabel, BorderLayout.SOUTH);

        chooseImageButton.addActionListener(e -> chooseSourceImage());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourcePanel, resultPanel);
        split.setResizeWeight(0.5);
        split.setContinuousLayout(true);
        split.setDividerSize(8);
        split.setBorder(null);
        split.setOpaque(false);
        split.setPreferredSize(new Dimension(1000, 400));
        return split;
    }

    private JPanel buildControls() {
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBackground(PANEL_BG);
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PANEL_EDGE), BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 5, 4, 5);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        JLabel passwordLabel = new JLabel("Password");
        passwordLabel.setForeground(TEXT);
        controls.add(passwordLabel, c);

        c.gridx = 1;
        c.weightx = 1;
        JPanel passPanel = new JPanel(new BorderLayout(8, 0));
        passPanel.setOpaque(false);
        passPanel.add(passwordField, BorderLayout.CENTER);
        passPanel.add(showPasswordBox, BorderLayout.EAST);
        controls.add(passPanel, c);

        c.gridx = 2;
        c.weightx = 0;
        JLabel passwordHint = new JLabel("Min 8 characters; used to encrypt/decrypt");
        passwordHint.setForeground(MUTED);
        passwordHint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        controls.add(passwordHint, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        controls.add(messageLabel, c);

        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        c.anchor = GridBagConstraints.CENTER;
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(messageArea);
        scroll.setBorder(BorderFactory.createLineBorder(PANEL_EDGE));
        scroll.getViewport().setBackground(new Color(13, 18, 30));
        controls.add(scroll, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        statusLabel.setForeground(MUTED);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        controls.add(statusLabel, c);

        c.gridx = 2;
        c.gridwidth = 1;
        c.weightx = 0;
        c.anchor = GridBagConstraints.EAST;
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(copyButton);
        actionPanel.add(runButton);
        controls.add(actionPanel, c);

        runButton.addActionListener(e -> runAction());
        copyButton.addActionListener(e -> copyRecoveredMessage());
        showPasswordBox.addActionListener(e -> toggleShowPassword());

        return controls;
    }

    private void styleControls() {
        styleButton(chooseImageButton, false);
        styleButton(runButton, true);
        styleButton(copyButton, false);

        for (JToggleButton toggle : new JToggleButton[]{encryptMode, decryptMode}) {
            toggle.setUI(new BasicToggleButtonUI());
            toggle.setFocusPainted(false);
            toggle.setFont(new Font("SansSerif", Font.BOLD, 13));
            toggle.setForeground(TEXT);
            toggle.setBackground(PANEL_BG);
            toggle.setOpaque(true);
            toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            toggle.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(PANEL_EDGE),
                    BorderFactory.createEmptyBorder(9, 18, 9, 18)));
        }

        for (JTextField field : new JTextField[]{passwordField}) {
            field.setBackground(new Color(13, 18, 30));
            field.setForeground(TEXT);
            field.setCaretColor(TEXT);
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(PANEL_EDGE),
                    BorderFactory.createEmptyBorder(7, 8, 7, 8)));
        }

        messageArea.setBackground(new Color(13, 18, 30));
        messageArea.setForeground(TEXT);
        messageArea.setDisabledTextColor(TEXT);
        messageArea.setCaretColor(TEXT);
        messageArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        messageArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        messageArea.setSelectedTextColor(Color.WHITE);
        messageArea.setSelectionColor(ACCENT_DARK);

        showPasswordBox.setOpaque(false);
        showPasswordBox.setForeground(MUTED);
        showPasswordBox.setFont(new Font("SansSerif", Font.PLAIN, 12));
        showPasswordBox.setFocusPainted(false);
        showPasswordBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleButton(JButton button, boolean primary) {
        button.setUI(new BasicButtonUI());
        button.setFocusPainted(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setForeground(primary ? Color.WHITE : TEXT);
        button.setBackground(primary ? ACCENT_DARK : new Color(37, 49, 72));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? ACCENT : PANEL_EDGE),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void toggleShowPassword() {
        if (showPasswordBox.isSelected()) {
            passwordField.setEchoChar((char) 0);
        } else {
            passwordField.setEchoChar(defaultEchoChar);
        }
    }

    private void copyRecoveredMessage() {
        String text = messageArea.getText();
        if (text != null && !text.isEmpty()) {
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            statusLabel.setText("Message copied to clipboard (" + text.length() + " chars).");
        }
    }

    private void configureModes() {
        boolean encrypting = encryptMode.isSelected();

        encryptMode.setBackground(encrypting ? ACCENT_DARK : PANEL_BG);
        encryptMode.setForeground(encrypting ? Color.WHITE : MUTED);
        encryptMode.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(encrypting ? ACCENT : PANEL_EDGE),
                BorderFactory.createEmptyBorder(9, 18, 9, 18)));

        decryptMode.setBackground(encrypting ? PANEL_BG : ACCENT_DARK);
        decryptMode.setForeground(encrypting ? MUTED : Color.WHITE);
        decryptMode.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(encrypting ? PANEL_EDGE : ACCENT),
                BorderFactory.createEmptyBorder(9, 18, 9, 18)));

        messageLabel.setText(encrypting ? "Message to hide" : "Recovered message");
        messageArea.setEditable(encrypting);
        messageArea.setToolTipText(encrypting ? "Type the secret message you want to hide." : "The decrypted message will appear here.");
        runButton.setText(encrypting ? "Encrypt & Save PNG" : "Decrypt Message");
        copyButton.setVisible(!encrypting);
        copyButton.setEnabled(!encrypting && !messageArea.getText().isEmpty());

        sourcePreview.setTitle(encrypting ? "COVER IMAGE (SOURCE)" : "ENCODED IMAGE (SOURCE)");
        resultPreview.setTitle(encrypting ? "ENCRYPTED IMAGE PREVIEW" : "DECODED IMAGE CONFIRMATION");
        resultNoteLabel.setText(encrypting ? "Lossless PNG output preserves the hidden bits."
                : "Only lossless PNG images with PixelVeil data can be decrypted.");

        if (encrypting) {
            if (sourceImage != null && sourceFile != null && sourceFile != lastEncryptedFile) {
                long cap = SteganographyService.getMaxMessageBytes(sourceImage);
                statusLabel.setText("Loaded " + sourceFile.getName() + " — capacity: ~" + formatBytes(cap) + ".");
            } else {
                statusLabel.setText("Choose a cover image, enter a message and password.");
            }
        } else {
            if (lastEncryptedFile != null && lastEncryptedImage != null && lastEncryptedFile.exists()) {
                sourceImage = lastEncryptedImage;
                sourceFile = lastEncryptedFile;
                sourcePreview.setImage(sourceImage);
                resultPreview.setImage(null);
                messageArea.setText("");
                statusLabel.setText("Loaded recently encrypted image: " + lastEncryptedFile.getName() + ". Click Decrypt Message to verify.");
            } else if (sourceImage != null) {
                statusLabel.setText("Image loaded: " + (sourceFile != null ? sourceFile.getName() : "image") + ". Enter password and decrypt.");
            } else {
                statusLabel.setText("Choose the PNG that contains the hidden message.");
            }
        }
    }

    private void chooseSourceImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(encryptMode.isSelected() ? "Choose a cover image" : "Choose an encoded image");
        chooser.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPEG, BMP, GIF)", "png", "jpg", "jpeg", "bmp", "gif"));
        if (sourceFile != null && sourceFile.getParentFile() != null) {
            chooser.setCurrentDirectory(sourceFile.getParentFile());
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File selected = chooser.getSelectedFile();
        if (selected == null) return;
        try {
            BufferedImage loaded = ImageIO.read(selected);
            if (loaded == null) throw new IOException("The selected file is not a supported image.");
            sourceImage = loaded;
            sourceFile = selected;
            sourcePreview.setImage(loaded);
            resultPreview.setImage(null);
            if (encryptMode.isSelected()) {
                long cap = SteganographyService.getMaxMessageBytes(loaded);
                statusLabel.setText("Loaded " + selected.getName() + " (" + loaded.getWidth() + " × " + loaded.getHeight() + ") — capacity: ~" + formatBytes(cap) + ".");
            } else {
                statusLabel.setText("Loaded " + selected.getName() + " (" + loaded.getWidth() + " × " + loaded.getHeight() + "). Enter password and decrypt.");
            }
        } catch (Exception ex) {
            showError("Could not open image", ex.getMessage());
        }
    }

    private void runAction() {
        if (sourceImage == null) {
            showError("Image required", "Choose an image first.");
            return;
        }
        char[] password = passwordField.getPassword();
        if (password.length == 0) {
            showError("Password required", "Enter a password.");
            return;
        }
        if (encryptMode.isSelected()) {
            String message = messageArea.getText();
            if (message == null || message.isEmpty()) {
                Arrays.fill(password, '\0');
                showError("Message required", "Type the message you want to hide.");
                return;
            }
            if (password.length < 8) {
                Arrays.fill(password, '\0');
                showError("Use a stronger password", "For encryption, use a password with at least 8 characters.");
                return;
            }

            File destination = chooseSaveDestination();
            if (destination == null) {
                Arrays.fill(password, '\0');
                statusLabel.setText("Save cancelled. The image was not encrypted.");
                return;
            }

            setBusy(true, "Encrypting message and saving PNG…");
            new SwingWorker<BufferedImage, Void>() {
                @Override protected BufferedImage doInBackground() throws Exception {
                    BufferedImage encoded = SteganographyService.hideMessage(sourceImage, message, password);
                    if (!ImageIO.write(encoded, "png", destination)) {
                        throw new IOException("No PNG writer is available in this Java runtime.");
                    }
                    return encoded;
                }
                @Override protected void done() {
                    Arrays.fill(password, '\0');
                    setBusy(false, "");
                    try {
                        BufferedImage encoded = get();
                        lastEncryptedFile = destination;
                        lastEncryptedImage = encoded;
                        resultPreview.setImage(encoded);
                        statusLabel.setText("Saved encrypted image: " + destination.getAbsolutePath()
                                + " (" + formatBytes(destination.length()) + ").");
                        JOptionPane.showMessageDialog(SteganographyApp.this,
                                "Your encrypted message was hidden and saved as a PNG:\n\n" + destination.getAbsolutePath(),
                                "Encryption Complete", JOptionPane.INFORMATION_MESSAGE);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        showError("Encryption interrupted", "The operation was interrupted.");
                    } catch (ExecutionException ex) {
                        showError("Encryption failed", rootMessage(ex));
                    }
                }
            }.execute();
        } else {
            setBusy(true, "Reading hidden data and decrypting…");
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() throws Exception {
                    return SteganographyService.revealMessage(sourceImage, password);
                }
                @Override protected void done() {
                    Arrays.fill(password, '\0');
                    setBusy(false, "");
                    try {
                        String recovered = get();
                        messageArea.setText(recovered);
                        resultPreview.setImage(sourceImage);
                        copyButton.setEnabled(!recovered.isEmpty());
                        statusLabel.setText("Message decrypted successfully (" + recovered.length() + " characters).");
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        showError("Decryption interrupted", "The operation was interrupted.");
                    } catch (ExecutionException ex) {
                        showError("Decryption failed", rootMessage(ex));
                    }
                }
            }.execute();
        }
    }

    private File chooseSaveDestination() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save encrypted image");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG image (*.png)", "png"));
        if (sourceFile != null && sourceFile.getParentFile() != null) {
            chooser.setCurrentDirectory(sourceFile.getParentFile());
            String name = sourceFile.getName();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            chooser.setSelectedFile(new File(sourceFile.getParentFile(), base + "-encrypted.png"));
        } else {
            chooser.setSelectedFile(new File("pixelveil-encrypted.png"));
        }

        while (true) {
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return null;
            }
            File selected = chooser.getSelectedFile();
            if (selected == null) {
                return null;
            }
            String name = selected.getName();
            if (!name.toLowerCase().endsWith(".png")) {
                int dot = name.lastIndexOf('.');
                if (dot > 0) name = name.substring(0, dot);
                selected = selected.toPath().resolveSibling(name + ".png").toFile();
            }
            if (selected.exists()) {
                int answer = JOptionPane.showConfirmDialog(this,
                        "The file \"" + selected.getName() + "\" already exists.\nDo you want to replace it?",
                        "Confirm Overwrite",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (answer == JOptionPane.YES_OPTION) {
                    return selected;
                }
            } else {
                return selected;
            }
        }
    }

    private void setBusy(boolean busy, String status) {
        chooseImageButton.setEnabled(!busy);
        runButton.setEnabled(!busy);
        encryptMode.setEnabled(!busy);
        decryptMode.setEnabled(!busy);
        passwordField.setEnabled(!busy);
        messageArea.setEnabled(!busy);
        copyButton.setEnabled(!busy && !encryptMode.isSelected() && !messageArea.getText().isEmpty());
        if (!status.isEmpty()) statusLabel.setText(status);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String rootMessage(Exception ex) {
        Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.toString() : message;
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message == null ? "Unknown error" : message, title, JOptionPane.ERROR_MESSAGE);
        statusLabel.setText(title + ".");
    }

    private static final class ImagePreviewPanel extends JPanel {
        private BufferedImage image;
        private String title;

        private ImagePreviewPanel(String title) {
            this.title = title;
            setBackground(PANEL_BG);
            setOpaque(true);
            setPreferredSize(new Dimension(450, 300));
        }

        void setTitle(String title) {
            this.title = title;
            repaint();
        }

        void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setColor(MUTED);
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                g2.drawString(title, 8, 18);

                int top = 26;
                int availableWidth = width - 16;
                int availableHeight = height - top - 12;

                if (availableWidth <= 0 || availableHeight <= 0) return;

                if (image == null) {
                    g2.setColor(new Color(33, 43, 63));
                    g2.fillRoundRect(8, top, availableWidth, availableHeight, 12, 12);
                    g2.setColor(MUTED);
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
                    String text = "Your image preview will appear here";
                    int textWidth = g2.getFontMetrics().stringWidth(text);
                    g2.drawString(text, (width - textWidth) / 2, top + availableHeight / 2 + 5);
                } else {
                    double scale = Math.min((double) availableWidth / image.getWidth(), (double) availableHeight / image.getHeight());
                    int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
                    int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
                    int x = 8 + (availableWidth - w) / 2;
                    int y = top + (availableHeight - h) / 2;

                    g2.setColor(new Color(10, 14, 24));
                    g2.fillRoundRect(x - 3, y - 3, w + 6, h + 6, 8, 8);
                    g2.drawImage(image, x, y, w, h, null);

                    g2.setColor(new Color(20, 26, 40, 200));
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                    String dimensions = image.getWidth() + " × " + image.getHeight();
                    int dimWidth = g2.getFontMetrics().stringWidth(dimensions);
                    g2.fillRoundRect(width - dimWidth - 18, height - 20, dimWidth + 10, 16, 6, 6);
                    g2.setColor(TEXT);
                    g2.drawString(dimensions, width - dimWidth - 13, height - 8);
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
