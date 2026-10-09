import java.util.Scanner;

/** Console (terminal) interface for NovaBot. */
public class ConsoleChat {

    public static void run(ChatBot bot) {
        Scanner sc = new Scanner(System.in);
        ChatBot.Session session = new ChatBot.Session();

        System.out.println("==============================================");
        System.out.println("  NovaBot - AI Chatbot (console)");
        System.out.println("  " + bot.stats());
        System.out.println("  Type 'help' for topics, 'bye' to exit.");
        System.out.println("==============================================");
        System.out.println("Bot : Hello! I'm NovaBot. How can I help you today?");

        while (true) {
            System.out.print("You : ");
            if (!sc.hasNextLine()) break;
            String line = sc.nextLine();
            ChatBot.Reply r = bot.reply(line, session);
            System.out.println("Bot : " + r.text.replace("\n", "\n      "));
            if (r.exit) break;
        }
    }
}
