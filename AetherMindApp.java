import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.text.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AetherMindApp extends JFrame {

    private static final String LOGIN_USER = "DICKSON";
    private static final int PASSWORD_ITERATIONS = 310_000;
    private static final String WINDOWS_VOICE_SCRIPT =
        "$ErrorActionPreference = 'Stop'\n" +
        "try {\n" +
        "  Add-Type -AssemblyName System.Speech\n" +
        "  $recognizer = New-Object System.Speech.Recognition.SpeechRecognitionEngine\n" +
        "  try {\n" +
        "    $choices = New-Object System.Speech.Recognition.Choices\n" +
        "    $choices.Add('__VOICE_UNLOCK_PHRASE__')\n" +
        "    $builder = New-Object System.Speech.Recognition.GrammarBuilder\n" +
        "    $builder.Append($choices)\n" +
        "    $grammar = New-Object -TypeName System.Speech.Recognition.Grammar -ArgumentList (,$builder)\n" +
        "    $recognizer.LoadGrammar($grammar)\n" +
        "    $recognizer.SetInputToDefaultAudioDevice()\n" +
        "    $result = $recognizer.Recognize([TimeSpan]::FromSeconds(15))\n" +
        "    if ($null -ne $result) { [Console]::Out.WriteLine('__AETHER_VOICE__' + $result.Text) }\n" +
        "    else { [Console]::Out.WriteLine('__AETHER_VOICE__NO_MATCH') }\n" +
        "  } finally { $recognizer.Dispose() }\n" +
        "} catch {\n" +
        "  [Console]::Error.WriteLine($_.Exception.Message)\n" +
        "  exit 2\n" +
        "}";
    private static final String WINDOWS_HELLO_SCRIPT =
        "$ErrorActionPreference = 'Stop'\n" +
        "try {\n" +
        "  Add-Type -AssemblyName System.Runtime.WindowsRuntime\n" +
        "  $verifier = [Windows.Security.Credentials.UI.UserConsentVerifier, Windows.Security.Credentials.UI, ContentType=WindowsRuntime]\n" +
        "  $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1\n" +
        "  if ($null -eq $asTask) { throw 'Windows Runtime async support is unavailable.' }\n" +
        "  $availabilityOperation = $verifier::CheckAvailabilityAsync()\n" +
        "  $availabilityTask = $asTask.MakeGenericMethod([Windows.Security.Credentials.UI.UserConsentVerifierAvailability]).Invoke($null, @($availabilityOperation))\n" +
        "  $availabilityTask.Wait()\n" +
        "  $result = $availabilityTask.Result.ToString()\n" +
        "  if ($result -eq 'Available') {\n" +
        "    $verificationOperation = $verifier::RequestVerificationAsync('Unlock AetherMind')\n" +
        "    $verificationTask = $asTask.MakeGenericMethod([Windows.Security.Credentials.UI.UserConsentVerificationResult]).Invoke($null, @($verificationOperation))\n" +
        "    $verificationTask.Wait()\n" +
        "    $result = $verificationTask.Result.ToString()\n" +
        "  }\n" +
        "  [Console]::Out.WriteLine('__AETHER_HELLO__' + $result)\n" +
        "} catch {\n" +
        "  [Console]::Error.WriteLine($_.Exception.Message)\n" +
        "  exit 2\n" +
        "}";

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final Color BG_DARK  = new Color(13,  13,  20);
    private static final Color BG_PANEL = new Color(20,  20,  32);
    private static final Color BG_INPUT = new Color(28,  28,  42);
    private static final Color CYAN     = new Color(0,   230, 255);
    private static final Color GREEN    = new Color(0,   255, 140);
    private static final Color YELLOW   = new Color(255, 220, 50);
    private static final Color WHITE    = new Color(220, 220, 230);
    private static final Color GRAY     = new Color(100, 100, 130);
    private static final Icon AETHER_AVATAR = new SpongeAvatarIcon();

    private final AetherBrain brain = new AetherBrain();
    private final String userName;
    private boolean restoring;

    // ── GUI Components ────────────────────────────────────────────────────────
    private JTextPane  chatPane;
    private StyledDocument doc;
    private JTextField inputField;
    private JButton    pictureButton;
    private JButton    sendButton;
    private JLabel     statusLabel;

    // ── Constructors ─────────────────────────────────────────────────────────
    public AetherMindApp() {
        this(LOGIN_USER);
    }

    public AetherMindApp(String userName) {
        this.userName = sanitizeUserName(userName);
        buildUI();
        showBoot();
        restoreHistory();
    }

    // ── Build the window ──────────────────────────────────────────────────────
    private void buildUI() {
        setTitle("AI ARHERMIND v0.9 — Local AI Core");
        setSize(800, 620);
        setMinimumSize(new Dimension(520, 400));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        // ── Header ────────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        header.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, new Color(30, 30, 50)),
            new EmptyBorder(12, 20, 12, 20)
        ));

        JLabel title = new JLabel("⬡  AI ARHERMIND");
        title.setFont(new Font("Consolas", Font.BOLD, 22));
        title.setForeground(CYAN);

        JLabel subtitle = new JLabel("v0.9  ·  Local knowledge  ·  Optional online search");
        subtitle.setFont(new Font("Consolas", Font.PLAIN, 11));
        subtitle.setForeground(GRAY);

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setBackground(BG_PANEL);
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(subtitle);

        statusLabel = new JLabel("● ONLINE");
        statusLabel.setFont(new Font("Consolas", Font.BOLD, 12));
        statusLabel.setForeground(GREEN);

        header.add(titleBox,     BorderLayout.WEST);
        header.add(statusLabel,  BorderLayout.EAST);

        // ── Chat pane ─────────────────────────────────────────────────────────
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(BG_DARK);
        chatPane.setBorder(new EmptyBorder(12, 14, 12, 14));
        doc = chatPane.getStyledDocument();

        JScrollPane scroll = new JScrollPane(chatPane);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(30, 30, 55), 1));
        scroll.getVerticalScrollBar().setBackground(BG_PANEL);

        // ── Input bar ─────────────────────────────────────────────────────────
        inputField = new JTextField();
        inputField.setBackground(BG_INPUT);
        inputField.setForeground(WHITE);
        inputField.setCaretColor(CYAN);
        inputField.setFont(new Font("Consolas", Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 150, 180), 1),
            new EmptyBorder(8, 12, 8, 12)
        ));

        pictureButton = new JButton("ADD PICTURE");
        pictureButton.setUI(new BasicButtonUI());
        pictureButton.setOpaque(true);
        pictureButton.setContentAreaFilled(true);
        pictureButton.setBorderPainted(false);
        pictureButton.setBackground(BG_INPUT);
        pictureButton.setForeground(CYAN);
        pictureButton.setFont(new Font("Consolas", Font.BOLD, 11));
        pictureButton.setFocusPainted(false);
        pictureButton.setBorder(new CompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 150, 180), 1),
            new EmptyBorder(8, 10, 8, 10)
        ));
        pictureButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        sendButton = new JButton("SEND  ▶");
        sendButton.setUI(new BasicButtonUI());
        sendButton.setOpaque(true);
        sendButton.setContentAreaFilled(true);
        sendButton.setBorderPainted(false);
        sendButton.setBackground(CYAN);
        sendButton.setForeground(BG_DARK);
        sendButton.setFont(new Font("Consolas", Font.BOLD, 13));
        sendButton.setFocusPainted(false);
        sendButton.setBorder(new EmptyBorder(8, 20, 8, 20));
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendButton.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { sendButton.setBackground(new Color(0, 200, 230)); }
            public void mouseExited (MouseEvent e) { sendButton.setBackground(CYAN); }
        });

        JPanel inputRow = new JPanel(new BorderLayout(10, 0));
        inputRow.setBackground(BG_PANEL);
        inputRow.setBorder(new EmptyBorder(10, 14, 6, 14));
        inputRow.add(inputField, BorderLayout.CENTER);
        JPanel inputActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        inputActions.setBackground(BG_PANEL);
        inputActions.add(pictureButton);
        inputActions.add(sendButton);
        inputRow.add(inputActions, BorderLayout.EAST);

        JLabel hint = new JLabel("  Try: hello · joke · java · life · weather · hack · exit");
        hint.setFont(new Font("Consolas", Font.PLAIN, 10));
        hint.setForeground(GRAY);
        hint.setBackground(BG_PANEL);
        hint.setOpaque(true);
        hint.setBorder(new EmptyBorder(0, 14, 8, 0));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(BG_PANEL);
        bottom.setBorder(new MatteBorder(1, 0, 0, 0, new Color(30, 30, 50)));
        bottom.add(inputRow, BorderLayout.CENTER);
        bottom.add(hint,     BorderLayout.SOUTH);

        // ── Assemble ──────────────────────────────────────────────────────────
        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(scroll,  BorderLayout.CENTER);
        add(bottom,  BorderLayout.SOUTH);

        // ── Wire actions ──────────────────────────────────────────────────────
        ActionListener onSend = e -> handleSend();
        sendButton.addActionListener(onSend);
        inputField.addActionListener(onSend);
        pictureButton.addActionListener(e -> choosePicture());

        setVisible(true);
        inputField.requestFocusInWindow();
    }

    // ── Boot greeting ─────────────────────────────────────────────────────────
    private void showBoot() {
        appendText("  ╔══════════════════════════════════════╗\n", CYAN,   15, true);
        appendText("  ║      AI ARHERMIND v0.9 - Local AI    ║\n", CYAN,   15, true);
        appendText("  ╚══════════════════════════════════════╝\n", CYAN,   15, true);
        appendText("\n  Local knowledge with online search for unmatched questions.\n\n", YELLOW, 12, false);
        appendText("  Initializing neural pathways...\n",  GRAY,  12, false);
        appendText("  Loading knowledge base...\n",        GRAY,  12, false);
        appendText("  Consciousness online for " + userName + ".\n\n", GREEN, 13, true);
        appendText("  ─────────────────────────────────────────\n\n", GRAY, 11, false);
    }

    // ── Handle user message ───────────────────────────────────────────────────
    private void handleSend() {
        String raw = inputField.getText().trim();
        if (raw.isEmpty()) return;

        inputField.setText("");
        setInputEnabled(false);

        // Print user line
        appendText("  " + userName + "  ›  ", GREEN, 13, true);
        appendText(raw + "\n\n", WHITE, 13, false);

        saveTurn("U", raw);

        if (AetherBrain.isExit(raw)) {
            appendAetherLabel();
            typewriterAppend(AetherBrain.GOODBYE + "\n\n", WHITE, 13, () -> {
                saveTurn("A", AetherBrain.GOODBYE);
                statusLabel.setText("● OFFLINE");
                statusLabel.setForeground(GRAY);
            });
            return;
        }

        statusLabel.setText("● THINKING...");
        statusLabel.setForeground(YELLOW);

        String reply = brain.respondLocally(raw);
        if (reply == null) {
            statusLabel.setText("● SEARCHING WEB...");
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    return brain.webSearchAndBrowse(raw, 3);
                }

                @Override
                protected void done() {
                    String onlineReply;
                    try {
                        onlineReply = get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        onlineReply = "[ONLINE SEARCH ERROR] Search was interrupted.";
                    } catch (java.util.concurrent.ExecutionException e) {
                        Throwable cause = e.getCause();
                        onlineReply = "[ONLINE SEARCH ERROR] "
                                + (cause == null ? e.getMessage() : cause.getMessage());
                    }
                    showReply(onlineReply);
                }
            }.execute();
            return;
        }

        showReply(reply);
    }

    private void showReply(String reply) {
        appendAetherLabel();
        typewriterAppend(reply + "\n\n", WHITE, 13, () -> {
            saveTurn("A", reply);
            statusLabel.setText("● ONLINE");
            statusLabel.setForeground(GREEN);
            setInputEnabled(true);
            inputField.requestFocusInWindow();
        });
    }

    private void appendAetherLabel() {
        SimpleAttributeSet avatarAttributes = new SimpleAttributeSet();
        StyleConstants.setIcon(avatarAttributes, AETHER_AVATAR);
        try {
            doc.insertString(doc.getLength(), " ", avatarAttributes);
        } catch (BadLocationException ignored) {
        }
        appendText("  Aether  ›  ", CYAN, 13, true);
    }

    // ── Typewriter animation ──────────────────────────────────────────────────
    private void typewriterAppend(String text, Color color, int size, Runnable onDone) {
        final int[] i = {0};
        Timer t = new Timer(16, null);
        t.addActionListener(e -> {
            if (i[0] < text.length()) {
                appendText(String.valueOf(text.charAt(i[0])), color, size, false);
                scrollToBottom();
                i[0]++;
            } else {
                t.stop();
                if (onDone != null) onDone.run();
            }
        });
        t.start();
    }

    // ── Append styled text ────────────────────────────────────────────────────
    private void appendText(String text, Color color, int size, boolean bold) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, color);
        StyleConstants.setFontFamily(a, "Consolas");
        StyleConstants.setFontSize(a, size);
        StyleConstants.setBold(a, bold);
        try { doc.insertString(doc.getLength(), text, a); }
        catch (BadLocationException ignored) {}
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> chatPane.setCaretPosition(doc.getLength()));
    }

    private void setInputEnabled(boolean enabled) {
        inputField.setEnabled(enabled);
        pictureButton.setEnabled(enabled);
        sendButton.setEnabled(enabled);
    }

    private void choosePicture() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Insert picture into chat");
        chooser.setFileFilter(new FileNameExtensionFilter(
            "Image files",
            ImageIO.getReaderFileSuffixes()
        ));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Path imagePath = chooser.getSelectedFile().toPath();
        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image == null) {
                throw new IOException("The selected file is not a supported image.");
            }
            appendPicture(imagePath.getFileName().toString(), image);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this,
                "Could not insert picture: " + e.getMessage(),
                "Picture error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void appendPicture(String fileName, BufferedImage image) {
        final int maxWidth = 480;
        final int maxHeight = 320;
        double scale = Math.min(
            1.0,
            Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight())
        );
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage preview = image;
        if (width != image.getWidth() || height != image.getHeight()) {
            preview = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = preview.createGraphics();
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );
            graphics.drawImage(image, 0, 0, width, height, null);
            graphics.dispose();
        }

        appendText("  " + userName + "  ›  Picture: " + fileName + "\n", GREEN, 13, true);
        SimpleAttributeSet imageAttributes = new SimpleAttributeSet();
        StyleConstants.setIcon(imageAttributes, new ImageIcon(preview));
        try {
            doc.insertString(doc.getLength(), " ", imageAttributes);
            appendText("\n\n", WHITE, 13, false);
        } catch (BadLocationException e) {
            JOptionPane.showMessageDialog(
                this,
                "Could not insert the picture into the chat.",
                "Picture error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        scrollToBottom();
    }

    private Path historyFile() {
        return Path.of(System.getProperty("user.home"), ".aethermind", "history.txt");
    }

    private Path memoryFile() {
        return Path.of(System.getProperty("user.home"), ".aethermind", "memory.txt");
    }

    private void saveTurn(String who, String text) {
        if (restoring) return;
        try {
            Path file = historyFile();
            Files.createDirectories(file.getParent());
            Files.writeString(
                file,
                who + " " + text.replace("\n", "\\n") + "\n",
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
            );
            Path mem = memoryFile();
            StringBuilder sb = new StringBuilder();
            for (String m : brain.memorySnapshot()) sb.append(m.replace("\n", " ")).append('\n');
            Files.writeString(mem, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private void restoreHistory() {
        Path file = historyFile();
        if (!Files.isRegularFile(file)) return;
        restoring = true;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.length() < 3) continue;
                String who = line.substring(0, 1);
                String text = line.substring(2).replace("\\n", "\n");
                if ("U".equals(who)) {
                    appendText("  " + userName + "  ›  ", GREEN, 13, true);
                    appendText(text + "\n\n", WHITE, 13, false);
                } else if ("A".equals(who)) {
                    appendAetherLabel();
                    appendText(text + "\n\n", WHITE, 13, false);
                }
            }
            Path mem = memoryFile();
            if (Files.isRegularFile(mem)) {
                brain.restoreMemory(Files.readAllLines(mem, StandardCharsets.UTF_8));
            }
            scrollToBottom();
        } catch (Exception ignored) {
        } finally {
            restoring = false;
        }
    }

    public String getUserName() {
        return userName;
    }

    private static String sanitizeUserName(String raw) {
        if (raw == null) return "Creator";
        String value = raw.trim();
        return value.isEmpty() ? "Creator" : value;
    }

    private static boolean authenticate(String userName, char[] password)
            throws GeneralSecurityException, IOException {
        if (!LOGIN_USER.equalsIgnoreCase(userName.trim())) return false;

        Properties credentials = loadLoginConfiguration();
        String encodedSalt = credentials.getProperty("password.salt");
        String encodedHash = credentials.getProperty("password.hash");
        if (encodedSalt == null || encodedSalt.isBlank()
                || encodedHash == null || encodedHash.isBlank()) {
            throw new IOException(
                "Password sign-in configuration must contain password.salt and password.hash."
            );
        }

        byte[] salt;
        byte[] expected;
        try {
            salt = java.util.Base64.getDecoder().decode(encodedSalt);
            expected = java.util.Base64.getDecoder().decode(encodedHash);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Password sign-in configuration is invalid.", e);
        }
        PBEKeySpec spec = new PBEKeySpec(
            password,
            salt,
            PASSWORD_ITERATIONS,
            256
        );
        try {
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(expected, actual);
        } finally {
            spec.clearPassword();
            java.util.Arrays.fill(salt, (byte) 0);
            java.util.Arrays.fill(expected, (byte) 0);
        }
    }

    private static Properties loadLoginConfiguration() throws IOException {
        Path credentialsFile = Path.of(
            System.getProperty("user.home"),
            ".aethermind",
            "login.properties"
        );
        if (!Files.isRegularFile(credentialsFile)) {
            throw new IOException(
                "Local login configuration is missing: " + credentialsFile + "."
            );
        }
        Properties credentials = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(
                credentialsFile,
                StandardCharsets.UTF_8
        )) {
            credentials.load(reader);
        }
        return credentials;
    }

    private static void showLogin() {
        while (true) {
            JTextField userField = new JTextField(LOGIN_USER);
            JPasswordField passwordField = new JPasswordField();
            JPanel panel = new JPanel(new GridLayout(0, 1, 10, 8));
            panel.setBorder(new EmptyBorder(12, 12, 12, 12));
            panel.add(new JLabel(isWindows()
                ? "Unlock with Windows Hello or sign in with your password."
                : "Sign in with your password."));
            panel.add(new JLabel("User name"));
            panel.add(userField);
            panel.add(new JLabel("Password"));
            panel.add(passwordField);

            String[] options = isWindows()
                ? new String[] {"Unlock with Windows Hello", "Unlock with voice phrase",
                    "Sign in with password", "Cancel"}
                : new String[] {"Sign in with password", "Cancel"};
            int choice = JOptionPane.showOptionDialog(
                null,
                panel,
                "AI ARHERMIND Login",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
            );
            if (choice < 0 || choice == options.length - 1) return;
            if (isWindows() && choice == 0) {
                if (!LOGIN_USER.equalsIgnoreCase(userField.getText().trim())) {
                    JOptionPane.showMessageDialog(
                        null,
                        "The user name is incorrect.",
                        "Login failed",
                        JOptionPane.ERROR_MESSAGE
                    );
                    continue;
                }
                authenticateWithWindowsHello();
                return;
            }
            if (isWindows() && choice == 1) {
                if (!LOGIN_USER.equalsIgnoreCase(userField.getText().trim())) {
                    JOptionPane.showMessageDialog(
                        null,
                        "The user name is incorrect.",
                        "Login failed",
                        JOptionPane.ERROR_MESSAGE
                    );
                    continue;
                }
                int consent = JOptionPane.showConfirmDialog(
                    null,
                    "Say your configured voice phrase when prompted. Windows listens for up to 15 seconds.\n\n"
                        + "This checks the spoken words, not who is speaking. Anyone who knows "
                        + "the phrase could unlock the app.",
                    "Voice phrase unlock",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );
                if (consent == JOptionPane.OK_OPTION) {
                    authenticateWithVoicePhrase();
                    return;
                }
                continue;
            }

            char[] password = passwordField.getPassword();
            try {
                if (authenticate(userField.getText(), password)) {
                    new AetherMindApp(LOGIN_USER);
                    return;
                }
                JOptionPane.showMessageDialog(
                    null,
                    "The user name or password is incorrect.",
                    "Login failed",
                    JOptionPane.ERROR_MESSAGE
                );
            } catch (GeneralSecurityException | IOException e) {
                JOptionPane.showMessageDialog(
                    null,
                    "Could not verify the login credentials: " + e.getMessage(),
                    "Login error",
                    JOptionPane.ERROR_MESSAGE
                );
                continue;
            } finally {
                java.util.Arrays.fill(password, '\0');
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
            .startsWith("windows");
    }

    private static void authenticateWithVoicePhrase() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws IOException, InterruptedException {
                String phrase = loadLoginConfiguration().getProperty("voice.phrase", "").trim();
                if (phrase.isEmpty()) {
                    throw new IOException(
                        "Voice unlock is not configured in ~/.aethermind/login.properties."
                    );
                }
                String voiceScript = WINDOWS_VOICE_SCRIPT.replace(
                    "__VOICE_UNLOCK_PHRASE__",
                    phrase.replace("'", "''")
                );
                String encodedScript = java.util.Base64.getEncoder().encodeToString(
                    voiceScript.getBytes(java.nio.charset.StandardCharsets.UTF_16LE)
                );
                Process process = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-WindowStyle",
                    "Hidden",
                    "-EncodedCommand",
                    encodedScript
                ).redirectErrorStream(true).start();
                boolean completed;
                try {
                    completed = process.waitFor(25, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                    throw e;
                }
                if (!completed) {
                    process.destroyForcibly();
                    throw new IOException("Voice recognition timed out. Check microphone access and try again.");
                }
                String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
                );
                if (process.exitValue() != 0) {
                    throw new IOException("Windows speech recognition failed: " + output.trim());
                }
                for (String line : output.split("\\R")) {
                    if (line.startsWith("__AETHER_VOICE__")) {
                        return line.substring("__AETHER_VOICE__".length()).trim();
                    }
                }
                throw new IOException("Windows speech recognition returned no result.");
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    String normalized = result.toLowerCase(java.util.Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", " ")
                        .trim();
                    String configuredPhrase = loadLoginConfiguration()
                        .getProperty("voice.phrase", "")
                        .toLowerCase(java.util.Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", " ")
                        .trim();
                    if (configuredPhrase.isEmpty()) {
                        throw new IOException(
                            "Voice unlock is not configured in ~/.aethermind/login.properties."
                        );
                    }
                    if (configuredPhrase.equals(normalized)) {
                        new AetherMindApp(LOGIN_USER);
                        return;
                    }
                    JOptionPane.showMessageDialog(
                        null,
                        "The voice phrase was not recognized. Try again or use Windows Hello or your password.",
                        "Voice unlock failed",
                        JOptionPane.ERROR_MESSAGE
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    JOptionPane.showMessageDialog(
                        null,
                        "Voice recognition was interrupted.",
                        "Voice unlock error",
                        JOptionPane.ERROR_MESSAGE
                    );
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(
                        null,
                        "Could not load voice unlock configuration: " + e.getMessage(),
                        "Voice unlock error",
                        JOptionPane.ERROR_MESSAGE
                    );
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    JOptionPane.showMessageDialog(
                        null,
                        "Could not unlock with voice: "
                            + (cause == null ? e.getMessage() : cause.getMessage()),
                        "Voice unlock error",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
                showLogin();
            }
        }.execute();
    }

    private static void authenticateWithWindowsHello() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws IOException, InterruptedException {
                String encodedScript = java.util.Base64.getEncoder().encodeToString(
                    WINDOWS_HELLO_SCRIPT.getBytes(java.nio.charset.StandardCharsets.UTF_16LE)
                );
                Process process = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-WindowStyle",
                    "Hidden",
                    "-EncodedCommand",
                    encodedScript
                ).redirectErrorStream(true).start();
                boolean completed;
                try {
                    completed = process.waitFor(90, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                    throw e;
                }
                if (!completed) {
                    process.destroyForcibly();
                    throw new IOException("Windows Hello verification timed out.");
                }
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    throw new IOException("Windows Hello could not run: " + output.trim());
                }
                for (String line : output.split("\\R")) {
                    if (line.startsWith("__AETHER_HELLO__")) {
                        return line.substring("__AETHER_HELLO__".length()).trim();
                    }
                }
                throw new IOException("Windows Hello returned no authentication result.");
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    if ("Verified".equals(result)) {
                        new AetherMindApp(LOGIN_USER);
                        return;
                    }
                    JOptionPane.showMessageDialog(
                        null,
                        windowsHelloResultMessage(result),
                        "Windows Hello",
                        JOptionPane.ERROR_MESSAGE
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    JOptionPane.showMessageDialog(
                        null,
                        "Windows Hello was interrupted.",
                        "Windows Hello",
                        JOptionPane.ERROR_MESSAGE
                    );
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    JOptionPane.showMessageDialog(
                        null,
                        "Could not verify with Windows Hello: "
                            + (cause == null ? e.getMessage() : cause.getMessage()),
                        "Windows Hello",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
                showLogin();
            }
        }.execute();
    }

    private static String windowsHelloResultMessage(String result) {
        switch (result) {
            case "DeviceNotPresent":
                return "This device does not have Windows Hello biometric hardware.";
            case "NotConfiguredForUser":
                return "Set up face recognition or another Windows Hello sign-in method in Windows Settings.";
            case "DisabledByPolicy":
                return "Windows Hello is disabled by your device administrator.";
            case "DeviceBusy":
                return "Windows Hello is busy. Try again.";
            case "Canceled":
                return "Windows Hello sign-in was canceled.";
            case "RetriesExhausted":
                return "Too many unsuccessful attempts. Try again later.";
            case "Verified":
                return "Windows Hello verified successfully.";
            default:
                return "Windows Hello could not verify this sign-in (" + result + ").";
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(AetherMindApp::showLogin);
    }

    private static final class SpongeAvatarIcon implements Icon {
        private static final int SIZE = 24;

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(x, y);

            g.setColor(new Color(255, 218, 74));
            g.fillRoundRect(1, 1, 21, 21, 7, 7);
            g.setColor(new Color(197, 151, 48));
            g.setStroke(new BasicStroke(1.2f));
            g.drawRoundRect(1, 1, 21, 21, 7, 7);

            g.setColor(new Color(194, 151, 50));
            g.fillOval(4, 5, 2, 2);
            g.fillOval(17, 4, 2, 2);
            g.fillOval(4, 16, 2, 2);
            g.fillOval(18, 16, 2, 2);

            g.setColor(Color.WHITE);
            g.fillOval(6, 6, 5, 6);
            g.fillOval(13, 6, 5, 6);
            g.setColor(new Color(34, 139, 198));
            g.fillOval(8, 8, 3, 4);
            g.fillOval(14, 8, 3, 4);
            g.setColor(new Color(24, 35, 49));
            g.fillOval(9, 9, 2, 3);
            g.fillOval(15, 9, 2, 3);

            g.setColor(new Color(96, 58, 35));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(7, 11, 10, 7, 5, 170);
            g.dispose();
        }
    }
}
