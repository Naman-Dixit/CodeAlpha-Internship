import java.util.*;

/**
 * Natural Language Processing toolkit (no external libraries).
 *
 * Pipeline:  raw text -> normalize -> tokenize -> spell-correct -> remove stop-words
 *            -> synonym mapping -> stemming -> features (unigrams + bigrams)
 */
public final class NLP {

    private NLP() { }

    // ---------- Resources ----------
    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "a", "an", "the", "is", "are", "am", "was", "were", "be", "to", "of", "in", "on", "at",
            "for", "and", "or", "it", "this", "that", "i", "me", "my", "please", "tell", "about",
            "with", "there", "so", "if", "as", "by", "will", "would", "could", "should", "does",
            "did", "have", "has", "had", "just", "some", "any"));

    private static final String[][] CONTRACTIONS = {
            {"what's", "what is"}, {"that's", "that is"}, {"it's", "it is"}, {"i'm", "i am"},
            {"don't", "do not"}, {"doesn't", "does not"}, {"can't", "cannot"}, {"won't", "will not"},
            {"isn't", "is not"}, {"i've", "i have"}, {"you're", "you are"}, {"how's", "how is"},
            {"who's", "who is"}, {"where's", "where is"}, {"i'd", "i would"}, {"i'll", "i will"},
            {"didn't", "did not"}, {"wasn't", "was not"}, {"let's", "let us"}, {"whats", "what is"}
    };

    /** Lexical normalization: different words with the same meaning share one token. */
    private static final Map<String, String> SYN = new HashMap<>();

    static {
        String[][] pairs = {
                {"hi", "hello"}, {"hey", "hello"}, {"hiya", "hello"}, {"howdy", "hello"}, {"greetings", "hello"},
                {"goodbye", "bye"}, {"cya", "bye"}, {"farewell", "bye"}, {"quit", "bye"}, {"exit", "bye"},
                {"thx", "thanks"}, {"thank", "thanks"}, {"thankyou", "thanks"},
                {"cost", "price"}, {"costs", "price"}, {"fee", "price"}, {"fees", "price"},
                {"pricing", "price"}, {"prices", "price"}, {"charges", "price"}, {"expensive", "price"},
                {"delivery", "ship"}, {"deliveries", "ship"}, {"deliver", "ship"}, {"delivered", "ship"},
                {"shipping", "ship"}, {"shipped", "ship"}, {"shipment", "ship"}, {"dispatch", "ship"},
                {"reimburse", "refund"}, {"reimbursement", "refund"}, {"refunds", "refund"}, {"refunded", "refund"},
                {"buy", "purchase"}, {"bought", "purchase"}, {"purchased", "purchase"}, {"purchasing", "purchase"},
                {"login", "signin"}, {"logon", "signin"}, {"log", "signin"},
                {"glitch", "bug"}, {"glitches", "bug"}, {"bugs", "bug"}, {"crashes", "crash"}, {"crashing", "crash"},
                {"jokes", "joke"}, {"funny", "joke"}, {"laugh", "joke"},
                {"timings", "hour"}, {"timing", "hour"}, {"hrs", "hour"},
                {"coupon", "discount"}, {"promo", "discount"}, {"offers", "discount"}, {"voucher", "discount"},
                {"email", "mail"}, {"mails", "mail"}
        };
        for (String[] p : pairs) SYN.put(p[0], p[1]);
    }

    private static final Set<String> POSITIVE = new HashSet<>(Arrays.asList(
            "great", "awesome", "excellent", "good", "love", "amazing", "wonderful", "fantastic",
            "happy", "perfect", "nice", "helpful", "cool"));
    private static final Set<String> NEGATIVE = new HashSet<>(Arrays.asList(
            "angry", "terrible", "worst", "hate", "bad", "awful", "frustrated", "annoyed", "annoying",
            "disappointed", "horrible", "useless", "pathetic", "upset", "unhappy", "furious",
            "ridiculous", "sucks", "poor"));

    // ---------- Step 1: normalize ----------
    public static String normalize(String text) {
        if (text == null) return "";
        String t = text.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        for (String[] c : CONTRACTIONS) t = t.replace(c[0], c[1]);
        t = t.replaceAll("[^a-z0-9\\s]", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    // ---------- Step 2: tokenize ----------
    public static List<String> tokens(String text) {
        String n = normalize(text);
        if (n.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(n.split(" ")));
    }

    // ---------- Step 3: spell correction (edit distance against known vocabulary) ----------
    public static List<String> correct(List<String> tokens, Set<String> vocab) {
        List<String> out = new ArrayList<>();
        for (String t : tokens) {
            // Be conservative: short words (<= 5 letters), known words, numbers and synonyms are never "corrected".
            if (t.length() <= 5 || hasDigit(t) || vocab.contains(t) || STOP.contains(t) || SYN.containsKey(t)) {
                out.add(t);
                continue;
            }
            int maxDist = t.length() <= 8 ? 1 : 2;
            String best = t;
            int bestDist = maxDist + 1;
            for (String v : vocab) {
                // typos rarely change the first letter, which avoids turning real words into other words
                if (v.length() <= 3 || v.charAt(0) != t.charAt(0) || Math.abs(v.length() - t.length()) > maxDist) continue;
                int d = levenshtein(t, v);
                if (d < bestDist) {
                    bestDist = d;
                    best = v;
                }
            }
            out.add(best);
        }
        return out;
    }

    public static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    // ---------- Step 4: stop-word removal + synonym mapping + stemming ----------
    public static List<String> stems(List<String> tokens) {
        List<String> out = new ArrayList<>();
        for (String t : tokens) {
            if (STOP.contains(t)) continue;
            out.add(stem(t));
        }
        return out;
    }

    /** A small suffix-stripping stemmer (Porter-style, simplified). */
    public static String stem(String word) {
        String w = SYN.getOrDefault(word, word);
        if (w.length() <= 3 || hasDigit(w)) return w;
        String r = w;
        if (w.endsWith("sses")) r = w.substring(0, w.length() - 2);
        else if (w.endsWith("ies") && w.length() > 4) r = w.substring(0, w.length() - 3) + "y";
        else if (w.endsWith("ing") && w.length() > 5) r = undouble(w.substring(0, w.length() - 3));
        else if (w.endsWith("ed") && w.length() > 4) r = undouble(w.substring(0, w.length() - 2));
        else if (w.endsWith("ly") && w.length() > 4) r = w.substring(0, w.length() - 2);
        else if (w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is"))
            r = w.substring(0, w.length() - 1);
        if (r.length() > 4 && r.endsWith("e")) r = r.substring(0, r.length() - 1);
        return r;
    }

    private static String undouble(String w) {
        int n = w.length();
        if (n >= 2 && w.charAt(n - 1) == w.charAt(n - 2) && "lsz".indexOf(w.charAt(n - 1)) < 0) {
            return w.substring(0, n - 1);
        }
        return w;
    }

    // ---------- Step 5: features (unigrams + bigrams) ----------
    public static List<String> features(List<String> stems) {
        List<String> f = new ArrayList<>(stems);
        for (int i = 0; i + 1 < stems.size(); i++) f.add(stems.get(i) + "_" + stems.get(i + 1));
        return f;
    }

    // ---------- Sentiment (lexicon based) ----------
    public static int sentiment(List<String> tokens) {
        int score = 0;
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            boolean negated = i > 0 && (tokens.get(i - 1).equals("not") || tokens.get(i - 1).equals("never"));
            if (POSITIVE.contains(t)) score += negated ? -1 : 1;
            if (NEGATIVE.contains(t)) score += negated ? 1 : -1;
        }
        return score;
    }

    private static boolean hasDigit(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) return true;
        return false;
    }
}
