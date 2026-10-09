import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The brain of NovaBot.
 *
 * For every message it works in this order:
 *   1. "learn: question => answer"  -> teaches the bot a new FAQ (online learning + retraining)
 *   2. pending clarification        -> handles yes/no to "Did you mean ...?"
 *   3. built-in commands            -> help, topics, stats, debug on/off, retrain
 *   4. rule-based answers           -> maths, time/date, remembering the user's name
 *   5. machine learning             -> NLP pipeline + TF-IDF similarity + Naive Bayes classifier
 *   6. fallback                     -> asks the user to rephrase
 */
public class ChatBot {

    // ---------- Public data types ----------
    /** Per-user conversation state (context memory). */
    public static class Session {
        public String userName;
        public String pendingTag;
        public String lastIntent;
        public boolean debug;
        public final List<String> history = new ArrayList<>();
    }

    public static class Reply {
        public final String text;
        public final String intent;
        public final double confidence;
        public final boolean exit;

        Reply(String text, String intent, double confidence, boolean exit) {
            this.text = text;
            this.intent = intent;
            this.confidence = confidence;
            this.exit = exit;
        }
    }

    public static class Prediction {
        public final String tag;
        public final double score;
        public final String bestExample;
        public final List<String> stems;

        Prediction(String tag, double score, String bestExample, List<String> stems) {
            this.tag = tag;
            this.score = score;
            this.bestExample = bestExample;
            this.stems = stems;
        }
    }

    // ---------- Settings ----------
    public static final double ACCEPT = 0.40;    // confident enough to answer (~90% precision when tested)
    public static final double CLARIFY = 0.25;   // ask "did you mean ...?"

    private static final Pattern TEACH = Pattern.compile(
            "^(?:learn|teach)\\s*:\\s*(.+?)\\s*=>\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Set<String> YES = new HashSet<>(Arrays.asList(
            "yes", "yeah", "yep", "yup", "sure", "correct", "right", "ok", "okay", "yes please", "exactly"));
    private static final Set<String> NO = new HashSet<>(Arrays.asList(
            "no", "nope", "nah", "not really", "wrong", "no thanks"));
    private static final Set<String> SMALLTALK = new HashSet<>(Arrays.asList(
            "greeting", "goodbye", "thanks", "acknowledge", "how_are_you", "bot_identity", "capabilities", "joke"));
    private static final String[] FALLBACKS = {
            "Sorry, I didn't understand that. Could you rephrase it? Type 'topics' to see what I can answer.",
            "I'm not sure about that one. Try asking in different words, or teach me with: learn: <question> => <answer>",
            "Hmm, I don't have an answer for that yet. Type 'topics' to see what I know, or teach me something new!"
    };
    private static final String[] SUGGESTIONS = {
            "What are your opening hours?", "How much does it cost?", "What is your refund policy?",
            "How do I reset my password?", "Track my order", "How can I pay?",
            "How can I contact you?", "Tell me a joke"
    };

    private static final File FAQ_FILE = new File("faq.txt");
    private static final File LEARNED_FILE = new File("learned_faq.txt");

    // ---------- State ----------
    private KnowledgeBase kb;
    private final NaiveBayes nb = new NaiveBayes();
    private final TfidfMatcher tfidf = new TfidfMatcher();
    private final boolean persist;
    private final Random rnd = new Random();

    /** Normal bot: built-in FAQs + faq.txt + learned_faq.txt (if present). */
    public ChatBot() {
        this.persist = true;
        loadKnowledge();
    }

    /** Bot with a custom knowledge base (used for evaluation). */
    public ChatBot(KnowledgeBase custom) {
        this.persist = false;
        this.kb = custom;
        train();
    }

    private void loadKnowledge() {
        kb = KnowledgeBase.withDefaults();
        kb.loadFile(FAQ_FILE);
        kb.loadFile(LEARNED_FILE);
        train();
    }

    private void train() {
        nb.train(kb);
        tfidf.train(kb);
    }

    public synchronized void retrain() {
        loadKnowledge();
    }

    public String[] suggestions() { return SUGGESTIONS.clone(); }

    public synchronized String stats() {
        return "Intents: " + kb.all().size() + " | training questions: " + kb.exampleCount()
                + " | vocabulary: " + kb.vocabulary().size() + " words | learned by users: " + kb.learnedCount();
    }

    // ---------- Main entry point ----------
    public synchronized Reply reply(String input, Session s) {
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) return new Reply("Please type a message and I'll do my best to help.", "empty", 0, false);
        if (raw.length() > 500) raw = raw.substring(0, 500);

        s.history.add("User: " + raw);
        Reply r = process(raw, s);
        s.history.add("Bot: " + r.text);
        s.lastIntent = r.intent;
        return r;
    }

    private Reply process(String raw, Session s) {
        String norm = NLP.normalize(raw);

        // 1. Teach command
        Matcher m = TEACH.matcher(raw);
        if (m.matches()) return new Reply(learn(m.group(1), m.group(2)), "learn", 1.0, false);

        // 2. Pending "did you mean ...?"
        if (s.pendingTag != null) {
            String tag = s.pendingTag;
            s.pendingTag = null;
            if (YES.contains(norm)) {
                KnowledgeBase.Intent in = kb.get(tag);
                if (in != null) return answer(in, s, 1.0, null);
            } else if (NO.contains(norm)) {
                return new Reply("No problem - could you rephrase your question?", "clarify_no", 1.0, false);
            }
        }

        // 3. Commands
        switch (norm) {
            case "help":
            case "topics":
            case "menu":
                return new Reply(helpText(), "help", 1.0, false);
            case "stats":
                return new Reply(stats(), "stats", 1.0, false);
            case "debug on":
                s.debug = true;
                return new Reply("Debug mode ON - I'll show intent, confidence and processed words.", "command", 1.0, false);
            case "debug off":
                s.debug = false;
                return new Reply("Debug mode OFF.", "command", 1.0, false);
            case "retrain":
                if (persist) {
                    retrain();
                    return new Reply("Models retrained. " + stats(), "command", 1.0, false);
                }
                break;
            default:
                break;
        }

        // 4. Rule-based answers
        String rule = Rules.apply(raw, norm, s);
        if (rule != null) return withDebug(new Reply(rule, "rule", 1.0, false), s, null);

        // 5. Machine learning (TF-IDF + Naive Bayes)
        Prediction p = classify(raw);
        if (p.tag != null && p.score >= ACCEPT) {
            return answer(kb.get(p.tag), s, p.score, p);
        }
        if (p.tag != null && p.score >= CLARIFY && !p.bestExample.isEmpty()) {
            s.pendingTag = p.tag;
            String text = "I'm not completely sure I understood. Did you mean: \"" + capitalize(p.bestExample)
                    + "?\" (yes / no)";
            return withDebug(new Reply(text, "clarify", p.score, false), s, p);
        }

        // 6. Fallback
        return withDebug(new Reply(FALLBACKS[rnd.nextInt(FALLBACKS.length)], "fallback", p.score, false), s, p);
    }

    private Reply answer(KnowledgeBase.Intent in, Session s, double conf, Prediction p) {
        String text = in.responses.get(rnd.nextInt(in.responses.size()));
        text = text.replace("{name}", s.userName == null ? "" : " " + s.userName);

        // Sentiment analysis: show empathy when the user sounds unhappy
        if (!SMALLTALK.contains(in.tag) && !text.startsWith("I'm sorry")
                && NLP.sentiment(NLP.tokens(s.history.isEmpty() ? "" : lastUserMessage(s))) < 0) {
            text = "I'm sorry to hear that. " + text;
        }
        return withDebug(new Reply(text, in.tag, conf, in.tag.equals("goodbye")), s, p);
    }

    private String lastUserMessage(Session s) {
        for (int i = s.history.size() - 1; i >= 0; i--) {
            if (s.history.get(i).startsWith("User: ")) return s.history.get(i).substring(6);
        }
        return "";
    }

    private Reply withDebug(Reply r, Session s, Prediction p) {
        if (!s.debug) return r;
        String info = "\n[debug] intent=" + r.intent + ", confidence=" + String.format(Locale.ROOT, "%.2f", r.confidence)
                + (p != null ? ", processed words=" + p.stems : "");
        return new Reply(r.text + info, r.intent, r.confidence, r.exit);
    }

    // ---------- Machine learning classification ----------
    /** Hybrid score = 0.6 * TF-IDF cosine similarity + 0.4 * Naive Bayes probability (scaled by word coverage). */
    public Prediction classify(String text) {
        List<String> stems = NLP.stems(NLP.correct(NLP.tokens(text), kb.vocabulary()));
        if (stems.isEmpty()) return new Prediction(null, 0, "", stems);

        Map<String, Double> nbProbs = nb.predict(NLP.features(stems));
        double coverage = nb.coverage(stems);
        TfidfMatcher.Result tr = tfidf.match(stems);

        String bestTag = null;
        double best = -1;
        for (Map.Entry<String, Double> e : nbProbs.entrySet()) {
            double score = 0.6 * tr.similarity.getOrDefault(e.getKey(), 0.0) + 0.4 * e.getValue() * coverage;
            if (score > best) {
                best = score;
                bestTag = e.getKey();
            }
        }
        if (bestTag == null) return new Prediction(null, 0, "", stems);
        return new Prediction(bestTag, best, tr.bestExample.getOrDefault(bestTag, ""), stems);
    }

    // ---------- Online learning ----------
    public synchronized String learn(String question, String answer) {
        String q = clean(question);
        String a = clean(answer);
        if (q.length() < 3 || a.length() < 2) {
            return "That's too short to learn. Use: learn: <question> => <answer>";
        }
        String tag = "learned_" + (kb.learnedCount() + 1);
        kb.add(tag, new String[]{q}, new String[]{a}, true);
        if (persist) {
            try {
                KnowledgeBase.appendLearned(LEARNED_FILE, tag, q, a);
            } catch (IOException e) {
                System.err.println("Could not save learned answer: " + e.getMessage());
            }
        }
        train();
        return "Thanks! I've learned a new answer. Try asking me: \"" + q + "\"";
    }

    private static String clean(String s) {
        return s.replace("||", " ").replace(";;", " ").replaceAll("\\s+", " ").trim();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String helpText() {
        StringBuilder sb = new StringBuilder("I can help with these topics:\n");
        for (KnowledgeBase.Intent in : kb.all()) {
            if (SMALLTALK.contains(in.tag) || in.tag.startsWith("learned_")) continue;
            sb.append("  - ").append(in.tag.replace('_', ' ')).append("\n");
        }
        if (kb.learnedCount() > 0) sb.append("  - plus ").append(kb.learnedCount()).append(" topic(s) you taught me\n");
        sb.append("\nI can also do maths (e.g. 12 * (3 + 4)), tell the time and date, and tell jokes.\n")
                .append("Commands: help, stats, debug on/off, retrain\n")
                .append("Teach me: learn: <question> => <answer>");
        return sb.toString();
    }

    // ---------- Evaluation (leave-one-out cross-validation) ----------
    /** Holds out each training question in turn, retrains, and checks whether it is still classified correctly. */
    public static void selfTest(PrintStream out) {
        KnowledgeBase full = KnowledgeBase.withDefaults();
        int total = 0, correct = 0, top1 = 0;
        List<String> misses = new ArrayList<>();
        for (KnowledgeBase.Intent in : full.all()) {
            if (in.examples.size() < 2) continue;
            for (int i = 0; i < in.examples.size(); i++) {
                ChatBot tmp = new ChatBot(full.copyWithout(in.tag, i));
                Prediction p = tmp.classify(in.examples.get(i));
                total++;
                if (in.tag.equals(p.tag)) top1++;
                if (in.tag.equals(p.tag) && p.score >= ACCEPT) {
                    correct++;
                } else {
                    misses.add("\"" + in.examples.get(i) + "\"  expected=" + in.tag + "  got=" + p.tag
                            + " (" + String.format(Locale.ROOT, "%.2f", p.score) + ")");
                }
            }
        }
        out.println("Leave-one-out evaluation (each training question is hidden, the bot retrained, then tested):");
        out.println("  Correct topic (top-1)          : " + top1 + "/" + total + " = "
                + String.format(Locale.ROOT, "%.1f", 100.0 * top1 / Math.max(1, total)) + "%");
        out.println("  Correct and confident (>= " + ACCEPT + ") : " + correct + "/" + total + " = "
                + String.format(Locale.ROOT, "%.1f", 100.0 * correct / Math.max(1, total)) + "%");
        for (String miss : misses) out.println("  miss: " + miss);
    }
}
