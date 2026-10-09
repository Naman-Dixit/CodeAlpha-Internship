import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Stores the bot's training data: intents (topics), each with example questions and answers.
 *
 * File format for faq.txt / learned_faq.txt (one intent per line):
 *     tag||question 1;;question 2;;question 3||answer 1;;answer 2
 */
public class KnowledgeBase {

    public static class Intent {
        public final String tag;
        public final List<String> examples = new ArrayList<>();
        public final List<String> responses = new ArrayList<>();
        public boolean learned;

        Intent(String tag) {
            this.tag = tag;
        }
    }

    private final Map<String, Intent> intents = new LinkedHashMap<>();
    private Set<String> vocabCache;

    public Collection<Intent> all() { return intents.values(); }

    public Intent get(String tag) { return intents.get(tag); }

    public int exampleCount() {
        int n = 0;
        for (Intent in : intents.values()) n += in.examples.size();
        return n;
    }

    public int learnedCount() {
        int n = 0;
        for (Intent in : intents.values()) if (in.learned) n++;
        return n;
    }

    public Intent add(String tag, String[] examples, String[] responses, boolean learned) {
        Intent in = intents.computeIfAbsent(tag, Intent::new);
        in.learned = in.learned || learned;
        for (String e : examples) if (!e.trim().isEmpty()) in.examples.add(e.trim());
        for (String r : responses) if (!r.trim().isEmpty()) in.responses.add(r.trim());
        vocabCache = null;
        return in;
    }

    /** All normalized words seen in training questions (used for spell correction). */
    public Set<String> vocabulary() {
        if (vocabCache == null) {
            Set<String> v = new HashSet<>();
            for (Intent in : intents.values()) for (String e : in.examples) v.addAll(NLP.tokens(e));
            vocabCache = v;
        }
        return vocabCache;
    }

    public KnowledgeBase copyWithout(String tag, int exampleIndex) {
        KnowledgeBase kb = new KnowledgeBase();
        for (Intent in : intents.values()) {
            Intent c = kb.intents.computeIfAbsent(in.tag, Intent::new);
            for (int i = 0; i < in.examples.size(); i++) {
                if (!(in.tag.equals(tag) && i == exampleIndex)) c.examples.add(in.examples.get(i));
            }
            c.responses.addAll(in.responses);
        }
        return kb;
    }

    // ---------- File I/O ----------
    public int loadFile(File f) {
        if (f == null || !f.isFile()) return 0;
        int loaded = 0;
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|\\|");
                if (parts.length < 3 || parts[0].trim().isEmpty()) continue;
                String tag = parts[0].trim();
                String[] ex = parts[1].split(";;");
                String[] re = parts[2].split(";;");
                if (parts[1].trim().isEmpty() || parts[2].trim().isEmpty()) continue;
                add(tag, ex, re, tag.startsWith("learned_"));
                loaded++;
            }
        } catch (IOException e) {
            System.err.println("Could not read " + f.getName() + ": " + e.getMessage());
        }
        return loaded;
    }

    public static void appendLearned(File f, String tag, String question, String answer) throws IOException {
        String line = tag + "||" + question + "||" + answer + System.lineSeparator();
        Files.write(f.toPath(), line.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    // ---------- Built-in training data ----------
    private void def(String tag, String[] examples, String[] responses) {
        add(tag, examples, responses, false);
    }

    /** Adds extra example questions (paraphrases) to an existing intent. */
    private void more(String tag, String... examples) {
        add(tag, examples, new String[0], false);
    }

    public static KnowledgeBase withDefaults() {
        KnowledgeBase kb = new KnowledgeBase();

        // ===== Small talk =====
        kb.def("greeting",
                new String[]{"hello", "hi", "hi there", "hey", "hey there", "good morning", "good afternoon",
                        "good evening", "howdy", "greetings", "hello bot", "hi bot"},
                new String[]{"Hello{name}! I'm NovaBot, the virtual assistant for NovaTech Solutions. How can I help you today?",
                        "Hi{name}! Ask me about our plans, orders, billing or technical support."});

        kb.def("goodbye",
                new String[]{"bye", "goodbye", "see you later", "see you", "talk to you later", "i have to go",
                        "quit", "exit", "farewell", "good night", "bye bye"},
                new String[]{"Goodbye{name}! Have a wonderful day.", "Bye{name}! Come back any time you need help."});

        kb.def("thanks",
                new String[]{"thanks", "thank you", "thanks a lot", "thank you so much", "many thanks",
                        "i appreciate it", "that was helpful", "great thanks", "thanks for your help"},
                new String[]{"You're welcome{name}! Is there anything else I can help with?",
                        "Happy to help{name}! Let me know if you have more questions."});

        kb.def("acknowledge",
                new String[]{"ok", "okay", "got it", "alright", "cool", "fine", "understood", "i see", "sounds good"},
                new String[]{"Great! Let me know if there is anything else you would like to know.",
                        "Okay! I'm here if you need anything else."});

        kb.def("how_are_you",
                new String[]{"how are you", "how are you doing", "how is it going", "how do you do",
                        "how is your day", "are you okay", "what is up", "how have you been"},
                new String[]{"I'm just a program, but I'm running smoothly! How can I help you?",
                        "Doing great, thanks for asking! What can I do for you?"});

        kb.def("bot_identity",
                new String[]{"who are you", "what is your name", "what are you", "are you a robot", "are you human",
                        "are you a bot", "tell me about yourself", "introduce yourself", "who made you",
                        "who created you"},
                new String[]{"I'm NovaBot, an AI chatbot written in Java. I understand questions with NLP "
                        + "(tokenization, stemming, spell-correction, TF-IDF) and a Naive Bayes classifier trained on FAQ data."});

        kb.def("capabilities",
                new String[]{"what can you do", "how can you help me", "what can you help me with",
                        "what topics do you know", "what do you know", "what can i ask you",
                        "what questions can you answer", "help me"},
                new String[]{"I can answer questions about our company, pricing, payments, refunds, shipping, order tracking, "
                        + "accounts, passwords, warranty, privacy, careers and technical support. I can also do quick maths, "
                        + "tell the time and tell jokes. Type 'topics' for the full list, or teach me with: "
                        + "learn: question => answer"});

        kb.def("joke",
                new String[]{"tell me a joke", "make me laugh", "say something funny", "do you know any jokes",
                        "joke", "i want to hear a joke", "another joke"},
                new String[]{"Why do Java developers wear glasses? Because they don't C#.",
                        "There are only 10 kinds of people: those who understand binary and those who don't.",
                        "A SQL query walks into a bar, walks up to two tables and asks: 'Can I join you?'",
                        "Why did the programmer quit his job? He didn't get arrays."});

        // ===== FAQ topics =====
        kb.def("company_info",
                new String[]{"what is novatech", "tell me about your company", "what does novatech do",
                        "what do you sell", "what products do you offer", "what services do you provide",
                        "about novatech", "what is your company about", "what do you offer"},
                new String[]{"NovaTech Solutions builds cloud software and hardware accessories: NovaCloud (hosting), "
                        + "NovaDesk (helpdesk) and NovaPay (payments). We serve over 20,000 customers worldwide."});

        kb.def("business_hours",
                new String[]{"what are your opening hours", "when are you open", "what time do you open",
                        "what time do you close", "business hours", "are you open on weekends",
                        "working hours", "support timings", "are you open on sunday"},
                new String[]{"Our support team is available Monday to Friday, 9:00 AM - 6:00 PM (IST), and Saturday "
                        + "10:00 AM - 2:00 PM. I'm available 24/7!"});

        kb.def("contact",
                new String[]{"how can i contact you", "what is your phone number", "customer support number",
                        "what is your email address", "how do i reach support", "contact details",
                        "talk to a human agent", "speak to a representative", "i want to call customer care"},
                new String[]{"You can reach us at support@novatech.example, call +91-1800-000-000 (toll free), "
                        + "or use live chat on our website."});

        kb.def("location",
                new String[]{"where are you located", "what is your address", "where is your office",
                        "office location", "where is your headquarters", "where can i find your store"},
                new String[]{"Our head office is at 12 Tech Park Road, Bengaluru, India. We also have offices in Pune and Hyderabad."});

        kb.def("pricing",
                new String[]{"how much does it cost", "what are your prices", "pricing plans",
                        "how much is the subscription", "is there a free plan", "what is the price of the premium plan",
                        "do you have a free trial", "subscription cost", "how much is the pro plan"},
                new String[]{"We offer three plans: Free ($0), Pro ($12/month) and Enterprise (custom pricing). "
                        + "All paid plans include a 14-day free trial."});

        kb.def("discount",
                new String[]{"do you have any discounts", "student discount", "coupon code", "promo code",
                        "are there any offers", "do you have a voucher", "how can i get a cheaper price"},
                new String[]{"Students and non-profits get 40% off Pro. Use code WELCOME10 for 10% off your first order."});

        kb.def("payment_methods",
                new String[]{"what payment methods do you accept", "can i pay by credit card", "do you accept upi",
                        "how can i pay", "do you accept paypal", "payment options", "can i pay with debit card",
                        "is net banking available"},
                new String[]{"We accept credit/debit cards, UPI, net banking and PayPal. All payments are encrypted and processed securely."});

        kb.def("refund",
                new String[]{"what is your refund policy", "how do i get a refund", "i want my money back",
                        "can i cancel and get a refund", "return policy", "how long does a refund take",
                        "refund request", "i want to return my purchase"},
                new String[]{"You can request a full refund within 30 days of purchase from Account > Billing > Request Refund. "
                        + "Refunds are processed in 5-7 business days."});

        kb.def("shipping",
                new String[]{"how long does delivery take", "shipping charges", "do you ship internationally",
                        "delivery time", "shipping cost", "how much is shipping", "do you offer express delivery",
                        "which countries do you deliver to"},
                new String[]{"Standard delivery takes 3-5 business days and express delivery takes 1-2 business days. "
                        + "Shipping is free on orders over $50, and we ship to more than 40 countries."});

        kb.def("order_tracking",
                new String[]{"where is my order", "track my order", "order status", "how can i track my package",
                        "what is my tracking number", "has my order shipped", "when will my order arrive",
                        "my parcel has not arrived"},
                new String[]{"You can track your order under My Account > Orders, or use the tracking link in your confirmation "
                        + "email. If it has been more than 7 days, contact support with your order number."});

        kb.def("password_reset",
                new String[]{"i forgot my password", "how do i reset my password", "reset password",
                        "change my password", "i cannot log in", "unable to login", "i cannot access my account",
                        "my account is locked"},
                new String[]{"Click 'Forgot password' on the login page, enter your email and follow the link we send you. "
                        + "The link is valid for 30 minutes. If your account is locked, please contact support."});

        kb.def("account_create",
                new String[]{"how do i create an account", "how to sign up", "register a new account",
                        "open an account", "how can i register", "sign up for novatech", "i want to join"},
                new String[]{"Click 'Sign Up' on our website, enter your email and a password, and verify your email address. "
                        + "It takes less than two minutes."});

        kb.def("account_delete",
                new String[]{"how do i delete my account", "close my account", "cancel my subscription",
                        "how to unsubscribe", "deactivate my account", "cancel my plan", "i want to stop my membership"},
                new String[]{"Go to Settings > Account > Cancel Subscription or Delete Account. Your data is removed within 30 days. "
                        + "You can export your data first from Settings > Data."});

        kb.def("tech_support",
                new String[]{"my app is not working", "the software keeps crashing", "i am facing a technical issue",
                        "i found a bug", "the website is not loading", "something is broken", "i get an error message",
                        "i need technical support", "the app is slow"},
                new String[]{"I'm sorry you're facing a technical issue. Please restart the app and clear the cache. If the problem "
                        + "continues, open a ticket at support.novatech.example with a screenshot and we'll reply within 24 hours."});

        kb.def("warranty",
                new String[]{"do you offer a warranty", "what is the warranty period", "is there a guarantee",
                        "how long is the warranty", "warranty on hardware", "is my device covered"},
                new String[]{"All hardware comes with a 1-year limited warranty. Software plans include a 99.9% uptime guarantee."});

        kb.def("security_privacy",
                new String[]{"is my data safe", "what is your privacy policy", "do you share my data",
                        "how do you protect my information", "is it secure", "data security", "are you gdpr compliant"},
                new String[]{"We use end-to-end encryption, never sell your data and comply with GDPR. "
                        + "See the full Privacy Policy on our website."});

        kb.def("careers",
                new String[]{"are you hiring", "job openings", "how can i apply for a job", "careers at novatech",
                        "internship opportunities", "i want to work with you", "do you have vacancies"},
                new String[]{"We're always looking for talented people! Visit careers.novatech.example to see openings. "
                        + "We also run a summer internship program."});

        kb.def("features",
                new String[]{"what features do you have", "what does the pro plan include", "does it support integrations",
                        "can i integrate with slack", "do you have a mobile app", "is there an api",
                        "what is included in the plan"},
                new String[]{"Pro includes unlimited projects, team collaboration, integrations (Slack, Google Drive, GitHub), "
                        + "a REST API and mobile apps for Android and iOS."});

        kb.def("feedback_complaint",
                new String[]{"i want to file a complaint", "i am not happy with the service", "i want to give feedback",
                        "this is terrible service", "i want to speak to a manager", "i want to submit a suggestion"},
                new String[]{"I'm sorry to hear that. Please share your feedback at feedback.novatech.example or email "
                        + "support@novatech.example - a manager will respond within 2 working days."});

        // ===== More paraphrases: more varied training questions make the models more accurate =====
        kb.more("greeting", "hello there", "hey bot", "good day", "hello nova", "hi nova", "hiya", "hello novabot");
        kb.more("goodbye", "see you soon", "i am leaving now", "talk later", "catch you later",
                "that is all for now", "have a nice day", "bye for now", "i am done");
        kb.more("thanks", "thanks for the help", "thank you very much", "much appreciated", "i am grateful",
                "you have been helpful", "cheers", "thanks bot", "that helps");
        kb.more("acknowledge", "alright then", "ok great", "oh okay", "makes sense", "nice", "good", "perfect",
                "sure thing", "i understand");
        kb.more("how_are_you", "how are things", "how is life", "how do you feel today", "hope you are well",
                "are you doing well", "how is everything", "how are you today");
        kb.more("bot_identity", "what should i call you", "do you have a name", "are you real", "are you an ai",
                "are you artificial intelligence", "what is your purpose", "who built you", "who programmed you",
                "what is novabot");
        kb.more("capabilities", "what are you able to do", "what do you help with", "what kind of questions can i ask",
                "show me what you can do", "list your skills", "what are your abilities", "can you help me");
        kb.more("joke", "tell me something funny", "got any jokes", "make a joke", "crack a joke", "i need a laugh",
                "tell me another joke", "say a joke");
        kb.more("company_info", "tell me about novatech solutions", "what kind of company are you",
                "what does your business do", "describe your company", "what is novacloud", "what is novadesk",
                "what is novapay", "what products do you sell");
        kb.more("business_hours", "when does support open", "when do you close", "what are your working days",
                "are you open today", "what are your office hours", "is customer care open on saturday",
                "support hours", "until what time are you open");
        kb.more("contact", "how do i get in touch", "give me your phone number", "what is your contact number",
                "i need your email", "how can i email support", "customer care number", "i want to talk to someone",
                "connect me to a person", "is there a helpline", "support email address");
        kb.more("location", "where is your office located", "what is your office address", "where are you based",
                "where is your head office", "in which city are you", "do you have an office in pune",
                "give me your address", "where can i visit you");
        kb.more("pricing", "what is the price", "how much do you charge", "what does it cost",
                "what are the subscription fees", "how much is the premium plan", "is it free",
                "what is the cost of the enterprise plan", "do you offer a free version", "tell me your plans",
                "is there a trial period", "how expensive is it", "plan prices");
        kb.more("discount", "any student offers", "do you offer student pricing", "is there a discount code",
                "how do i use a coupon", "are there any promotions", "do you have a sale", "any special offers",
                "i have a promo code", "can i get a discount");
        kb.more("payment_methods", "which cards do you accept", "do you take visa", "can i pay with google pay",
                "do you accept mastercard", "how do i pay for my order", "what are the accepted payment modes",
                "can i pay using upi", "is cash on delivery available", "can i pay in installments");
        kb.more("refund", "i want a refund", "how do i return a product", "can i get my money returned",
                "what is the money back guarantee", "refund policy", "how many days do i have to return",
                "will i get my money back", "how do i request a refund", "how long will my refund take",
                "can i return my order");
        kb.more("shipping", "how many days for delivery", "how fast is delivery", "what are the delivery charges",
                "is shipping free", "do you deliver abroad", "do you ship worldwide", "what is the delivery time",
                "can you send it internationally", "express shipping options");
        kb.more("order_tracking", "where is my package", "track my parcel", "what is the status of my order",
                "i want to track my shipment", "has my parcel been sent", "my order has not arrived yet",
                "how do i find my tracking number", "can i see my order status", "where is my delivery");
        kb.more("password_reset", "forgot password", "i lost my password", "how can i recover my password",
                "i cannot sign in", "reset my login password", "unable to log into my account",
                "my password is not working", "how to change my password", "i am locked out of my account",
                "password recovery");
        kb.more("account_create", "create a new account", "how do i register", "i want to sign up",
                "how can i create an account", "registration process", "how to open a new account",
                "where do i sign up", "make a new profile", "how do i join novatech");
        kb.more("account_delete", "delete my account", "how can i close my account", "i want to cancel my subscription",
                "how do i unsubscribe from novatech", "how to deactivate account", "stop my subscription",
                "terminate my account", "remove my account permanently", "cancel my membership");
        kb.more("tech_support", "the app crashed", "my software is not working", "i have a technical problem",
                "website not loading", "the app is not opening", "it keeps freezing", "i am getting an error",
                "i need help with a bug", "the system is down", "something went wrong",
                "the page is not responding");
        kb.more("warranty", "what does the warranty cover", "how long is the guarantee",
                "is there a warranty on devices", "do you provide warranty", "warranty period",
                "how many years of warranty", "what is your guarantee policy");
        kb.more("security_privacy", "is my information secure", "how is my data protected", "do you sell my data",
                "what is your data policy", "is my data encrypted", "privacy policy", "are my details safe",
                "do you comply with gdpr", "how secure is my account");
        kb.more("careers", "do you have job openings", "how do i apply for a job", "are there any vacancies",
                "i want to apply for an internship", "job opportunities", "can i work at novatech",
                "do you recruit freshers", "how can i join your team");
        kb.more("features", "what are the features of the pro plan", "does it have a mobile app",
                "do you support slack integration", "is there a rest api", "what is included in pro",
                "can i connect it with github", "what can the premium plan do", "does novatech support integrations");
        kb.more("feedback_complaint", "i have a complaint", "i am unhappy with your service",
                "i want to give you feedback", "your service is bad", "i want to talk to a manager",
                "i am not satisfied", "i want to report a problem with your service", "how do i submit feedback",
                "i would like to make a suggestion");

        return kb;
    }
}
