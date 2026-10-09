import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

/** Desktop GUI (Java Swing) for NovaBot with chat bubbles, quick-reply buttons and a "teach the bot" dialog. */
public class ChatGUI extends JFrame {

    private final ChatBot bot;
    private final ChatBot.Session session = new ChatBot.Session();

    private final JTextPane chat = new JTextPane();
    private final StyledDocument doc = chat.getStyledDocument();
    private final JTextField input = new JTextField();
    private final JLabel status = new JLabel(" ");
    private final StringBuilder transcript = new StringBuilder();
    private boolean showDetails = false;

    private final SimpleAttributeSet leftAlign = new SimpleAttributeSet();
    private final SimpleAttributeSet rightAlign = new SimpleAttributeSet();
    private final Style userStyle;
    private final Style botStyle;
    private final Style labelStyle;
    private final Style detailStyle;

    public ChatGUI(ChatBot bot) {
        super("NovaBot - AI Chatbot");
        this.bot = bot;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));

        // ----- text styles -----
        StyleConstants.setAlignment(leftAlign, StyleConstants.ALIGN_LEFT);
        StyleConstants.setAlignment(rightAlign, StyleConstants.ALIGN_RIGHT);

        userStyle = chat.addStyle("user", null);
        StyleConstants.setFontFamily(userStyle, "SansSerif");
        StyleConstants.setFontSize(userStyle, 14);
        StyleConstants.setForeground(userStyle, Color.WHITE);
        StyleConstants.setBackground(userStyle, new Color(0x2F6FED));

        botStyle = chat.addStyle("bot", null);
        StyleConstants.setFontFamily(botStyle, "SansSerif");
        StyleConstants.setFontSize(botStyle, 14);
        StyleConstants.setForeground(botStyle, new Color(0x1C2330));
        StyleConstants.setBackground(botStyle, new Color(0xE6EAF2));

        labelStyle = chat.addStyle("label", null);
        StyleConstants.setFontFamily(labelStyle, "SansSerif");
        StyleConstants.setFontSize(labelStyle, 11);
        StyleConstants.setBold(labelStyle, true);
        StyleConstants.setForeground(labelStyle, Color.GRAY);

        detailStyle = chat.addStyle("detail", null);
        StyleConstants.setFontFamily(detailStyle, "SansSerif");
        StyleConstants.setFontSize(detailStyle, 11);
        StyleConstants.setItalic(detailStyle, true);
        StyleConstants.setForeground(detailStyle, Color.GRAY);

        chat.setEditable(false);
        chat.setMargin(new Insets(10, 12, 10, 12));
        add(new JScrollPane(chat), BorderLayout.CENTER);

        // ----- bottom: quick replies + input -----
        JPanel quick = new JPanel(new GridLayout(2, 4, 6, 6));
        for (String s : bot.suggestions()) {
            JButton b = new JButton(s);
            b.setFont(b.getFont().deriveFont(11f));
            b.setMargin(new Insets(3, 2, 3, 2));
            b.setFocusable(false);
            b.addActionListener(e -> send(s));
            quick.add(b);
        }

        JButton sendBtn = new JButton("Send");
        sendBtn.addActionListener(e -> send(input.getText()));
        input.addActionListener(e -> send(input.getText()));
        input.setFont(input.getFont().deriveFont(14f));

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.add(input, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);

        status.setFont(status.getFont().deriveFont(Font.ITALIC, 11f));
        status.setForeground(Color.GRAY);

        JPanel bottom = new JPanel(new BorderLayout(4, 6));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));
        bottom.add(quick, BorderLayout.NORTH);
        JPanel inputBlock = new JPanel(new BorderLayout(0, 2));
        inputBlock.add(status, BorderLayout.NORTH);
        inputBlock.add(inputRow, BorderLayout.CENTER);
        bottom.add(inputBlock, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        setJMenuBar(buildMenu());

        addMessage("NovaBot", "Hello! I'm NovaBot, the assistant for NovaTech Solutions. Ask me about pricing, "
                + "orders, refunds, accounts or support - or type 'help' to see all topics.", false);

        setSize(1020, 660);
        setLocationRelativeTo(null);
        SwingUtilities.invokeLater(input::requestFocusInWindow);
    }

    // ---------- Menu ----------
    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem save = new JMenuItem("Save Transcript...");
        save.addActionListener(e -> saveTranscript());
        JMenuItem clear = new JMenuItem("Clear Chat");
        clear.addActionListener(e -> {
            chat.setText("");
            transcript.setLength(0);
        });
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> System.exit(0));
        file.add(save);
        file.add(clear);
        file.addSeparator();
        file.add(exit);

        JMenu botMenu = new JMenu("Bot");
        JMenuItem teach = new JMenuItem("Teach the Bot...");
        teach.addActionListener(e -> teachDialog());
        JMenuItem retrain = new JMenuItem("Retrain Models");
        retrain.addActionListener(e -> send("retrain"));
        JMenuItem stats = new JMenuItem("Show Statistics");
        stats.addActionListener(e -> send("stats"));
        JCheckBoxMenuItem details = new JCheckBoxMenuItem("Show AI details (intent / confidence)");
        details.addActionListener(e -> showDetails = details.isSelected());
        botMenu.add(teach);
        botMenu.add(retrain);
        botMenu.add(stats);
        botMenu.addSeparator();
        botMenu.add(details);

        JMenu help = new JMenu("Help");
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "NovaBot - Java AI chatbot\n\nNLP: normalization, tokenization, spell-correction, stop-words,\n"
                        + "synonyms, stemming, sentiment analysis\nML: TF-IDF cosine similarity + Naive Bayes classifier\n"
                        + "Rules: calculator, time/date, name memory\nOnline learning: teach it new answers",
                "About NovaBot", JOptionPane.INFORMATION_MESSAGE));
        help.add(about);

        bar.add(file);
        bar.add(botMenu);
        bar.add(help);
        return bar;
    }

    // ---------- Chatting ----------
    private void send(String text) {
        text = text.trim();
        if (text.isEmpty()) return;
        addMessage("You", text, true);
        input.setText("");

        final ChatBot.Reply r = bot.reply(text, session);
        status.setText("NovaBot is typing...");
        int delay = 350 + Math.min(r.text.length() * 4, 900);
        javax.swing.Timer timer = new javax.swing.Timer(delay, e -> {
            status.setText(" ");
            addMessage("NovaBot", r.text, false);
            if (showDetails) addDetails(r);
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void addMessage(String who, String text, boolean isUser) {
        try {
            int start = doc.getLength();
            doc.insertString(start, who + "\n", labelStyle);
            doc.setParagraphAttributes(start, 1, isUser ? rightAlign : leftAlign, false);
            int bodyStart = doc.getLength();
            doc.insertString(bodyStart, " " + text.replace("\n", "\n ") + " \n\n", isUser ? userStyle : botStyle);
            doc.setParagraphAttributes(bodyStart, doc.getLength() - bodyStart, isUser ? rightAlign : leftAlign, false);
            chat.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {
            // cannot happen: we only append at the end of the document
        }
        transcript.append(who).append(": ").append(text).append("\n");
    }

    private void addDetails(ChatBot.Reply r) {
        try {
            int start = doc.getLength();
            doc.insertString(start, String.format(Locale.ROOT, "[intent: %s | confidence: %.0f%%]\n\n",
                    r.intent, r.confidence * 100), detailStyle);
            doc.setParagraphAttributes(start, doc.getLength() - start, leftAlign, false);
            chat.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {
            // ignore
        }
    }

    // ---------- Dialogs ----------
    private void teachDialog() {
        JTextField q = new JTextField(30);
        JTextField a = new JTextField(30);
        JPanel p = new JPanel(new GridLayout(4, 1, 4, 4));
        p.add(new JLabel("Question users might ask:"));
        p.add(q);
        p.add(new JLabel("Answer the bot should give:"));
        p.add(a);
        int ok = JOptionPane.showConfirmDialog(this, p, "Teach the Bot", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) {
            String msg = bot.learn(q.getText(), a.getText());
            addMessage("NovaBot", msg, false);
        }
    }

    private void saveTranscript() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("chat_transcript.txt"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter fw = new FileWriter(chooser.getSelectedFile())) {
                fw.write(transcript.toString());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
