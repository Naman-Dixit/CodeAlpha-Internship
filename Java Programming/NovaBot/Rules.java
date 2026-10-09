import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rule-based answers (pattern matching) that do not need machine learning. */
public final class Rules {

    private Rules() { }

    private static final Pattern NAME = Pattern.compile(
            "(?i)\\b(?:my name is|my name's|call me)\\s+([A-Za-z][A-Za-z-]{1,20})");
    private static final Set<String> NOT_NAMES = new HashSet<>(Arrays.asList(
            "not", "here", "unknown", "a", "the", "is", "bot", "novabot"));

    /** Returns an answer if a rule matches, otherwise null. */
    public static String apply(String raw, String norm, ChatBot.Session session) {

        // --- Remember the user's name ---
        Matcher m = NAME.matcher(raw);
        if (m.find()) {
            String name = capitalize(m.group(1));
            if (!NOT_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                session.userName = name;
                return "Nice to meet you, " + name + "! How can I help you today?";
            }
        }
        if (norm.matches(".*\\b(what is my name|who am i|do you know my name|do you remember my name)\\b.*")) {
            return session.userName != null
                    ? "Your name is " + session.userName + "."
                    : "I don't know your name yet. Tell me with: my name is ...";
        }

        // --- Time and date ---
        if (norm.matches(".*\\b(what is the time|what time is it|current time|time now|tell the time)\\b.*")) {
            return "The current time is " + LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")) + ".";
        }
        if (norm.matches(".*\\b(today s date|todays date|what is the date|what is today|what day is it|"
                + "current date|what day is today|what is the day)\\b.*")) {
            return "Today is " + LocalDate.now().format(
                    DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)) + ".";
        }

        // --- Maths ---
        String maths = math(raw);
        if (maths != null) return maths;

        return null;
    }

    // ---------- Calculator ----------
    private static String math(String raw) {
        String t = raw.toLowerCase(Locale.ROOT);
        t = t.replace("multiplied by", "*").replace("divided by", "/").replace("plus", "+")
                .replace("minus", "-").replace("times", "*").replace("power of", "^");
        t = t.replaceAll("what is|what's|whats|calculate|compute|how much is|solve|evaluate|equals|[=?]", " ");
        t = t.replace('x', '*').replace('\u00d7', '*').replace('\u00f7', '/').replaceAll("\\s+", "");

        if (!t.matches("[0-9+\\-*/().^%]+") || !t.matches(".*[0-9)][+\\-*/^%].*[0-9(].*")) return null;
        try {
            double r = new Parser(t).parse();
            if (Double.isNaN(r) || Double.isInfinite(r)) return "That result is undefined.";
            return "The answer is " + format(r) + ".";
        } catch (ArithmeticException e) {
            return "Division by zero is undefined.";
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String format(double r) {
        if (r == Math.rint(r) && Math.abs(r) < 1e15) return String.valueOf((long) r);
        return String.format(Locale.ROOT, "%.4f", r).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** Recursive-descent parser: + - * / % ^ and parentheses. */
    private static class Parser {
        private final String s;
        private int p = 0;

        Parser(String s) { this.s = s; }

        double parse() {
            double v = expr();
            if (p != s.length()) throw new IllegalStateException("Unexpected character");
            return v;
        }

        private boolean peek(char c) { return p < s.length() && s.charAt(p) == c; }

        private double expr() {
            double v = term();
            while (peek('+') || peek('-')) {
                char op = s.charAt(p++);
                double r = term();
                v = op == '+' ? v + r : v - r;
            }
            return v;
        }

        private double term() {
            double v = power();
            while (peek('*') || peek('/') || peek('%')) {
                char op = s.charAt(p++);
                double r = power();
                if ((op == '/' || op == '%') && r == 0) throw new ArithmeticException("zero");
                v = op == '*' ? v * r : op == '/' ? v / r : v % r;
            }
            return v;
        }

        private double power() {
            double base = unary();
            if (peek('^')) {
                p++;
                return Math.pow(base, power());
            }
            return base;
        }

        private double unary() {
            if (peek('-')) { p++; return -unary(); }
            if (peek('+')) { p++; return unary(); }
            return primary();
        }

        private double primary() {
            if (peek('(')) {
                p++;
                double v = expr();
                if (!peek(')')) throw new IllegalStateException("Missing )");
                p++;
                return v;
            }
            int start = p;
            while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.')) p++;
            if (start == p) throw new IllegalStateException("Number expected");
            return Double.parseDouble(s.substring(start, p));
        }
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
