import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.util.Scanner;

/**
 * NovaBot launcher.
 *
 *   java Main              -> shows a menu
 *   java Main console      -> terminal chat
 *   java Main gui          -> Swing desktop window
 *   java Main web [port]   -> browser chat at http://localhost:8080
 *   java Main all [port]   -> GUI window + web server together
 *   java Main test         -> runs sample conversations and a machine-learning evaluation
 */
public class Main {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0].toLowerCase() : null;
        int port = 8080;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port, using 8080.");
            }
        }

        if (mode == null) mode = askMode();

        if (mode.equals("test")) {
            runTest();
            return;
        }

        ChatBot bot = new ChatBot();
        System.out.println("NovaBot trained. " + bot.stats());

        switch (mode) {
            case "gui":
                startGui(bot);
                break;
            case "web":
                startWeb(bot, port);
                break;
            case "all":
                startWeb(bot, port);
                startGui(bot);
                break;
            default:
                ConsoleChat.run(bot);
        }
    }

    private static String askMode() {
        System.out.println("==============================================");
        System.out.println("  NovaBot - Java AI Chatbot");
        System.out.println("==============================================");
        System.out.println("  1) Console chat");
        System.out.println("  2) Desktop GUI (Swing)");
        System.out.println("  3) Web interface (browser)");
        System.out.println("  4) GUI + Web together");
        System.out.println("  5) Run self-test / ML evaluation");
        System.out.print("Choose an option [1-5]: ");
        Scanner sc = new Scanner(System.in);
        String c = sc.hasNextLine() ? sc.nextLine().trim() : "1";
        switch (c) {
            case "2": return "gui";
            case "3": return "web";
            case "4": return "all";
            case "5": return "test";
            default: return "console";
        }
    }

    private static void startGui(ChatBot bot) {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("No display available for the GUI - starting console chat instead.");
            ConsoleChat.run(bot);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // use default look and feel
            }
            new ChatGUI(bot).setVisible(true);
        });
    }

    private static void startWeb(ChatBot bot, int port) throws Exception {
        WebChatServer.start(bot, port);
        System.out.println("Web chat running at  http://localhost:" + port + "   (press Ctrl+C to stop)");
    }

    private static void runTest() {
        ChatBot bot = new ChatBot();
        System.out.println(bot.stats());
        System.out.println();

        String[] samples = {
                "hi", "Hello there!", "wat are your opning hours", "I forgot my pasword",
                "can I get my money back", "do u accept paypal", "how long will shipping take",
                "is there a student discount", "my app keeps crashing", "tell me a joke",
                "5 + 3 * 2", "what is (12 + 8) / 4", "what time is it", "my name is aarav",
                "what is my name", "this service is terrible and I want a refund",
                "what is the capital of france", "how do I bake a cake", "who won the cricket match",
                "what is the weather today", "asdfgh", "bye"
        };
        ChatBot.Session session = new ChatBot.Session();
        session.debug = true;
        for (String s : samples) {
            ChatBot.Reply r = bot.reply(s, session);
            System.out.println("You : " + s);
            System.out.println("Bot : " + r.text.replace("\n", "\n      "));
            System.out.println();
        }

        ChatBot.selfTest(System.out);
    }
}
