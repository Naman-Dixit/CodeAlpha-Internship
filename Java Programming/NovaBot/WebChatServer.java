import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Web interface: a tiny HTTP server (JDK built-in, no libraries) that serves a chat page
 * and a JSON API.   GET /  -> page,   POST /chat -> reply,   GET /suggestions -> quick questions
 */
public class WebChatServer {

    private static final Map<String, ChatBot.Session> SESSIONS = new ConcurrentHashMap<>();

    public static HttpServer start(ChatBot bot, int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);

        server.createContext("/", ex -> {
            if (!"/".equals(ex.getRequestURI().getPath())) {
                send(ex, 404, "text/plain; charset=utf-8", "Not found");
                return;
            }
            send(ex, 200, "text/html; charset=utf-8", PAGE);
        });

        server.createContext("/suggestions", ex -> {
            StringBuilder sb = new StringBuilder("[");
            String[] s = bot.suggestions();
            for (int i = 0; i < s.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(json(s[i]));
            }
            sb.append(']');
            send(ex, 200, "application/json; charset=utf-8", sb.toString());
        });

        server.createContext("/chat", ex -> {
            try {
                if (!"POST".equals(ex.getRequestMethod())) {
                    send(ex, 405, "text/plain; charset=utf-8", "Use POST");
                    return;
                }
                String sid = ex.getRequestHeaders().getFirst("X-Session");
                if (sid == null || sid.length() > 64) sid = "default";
                if (SESSIONS.size() > 1000) SESSIONS.clear();
                ChatBot.Session session = SESSIONS.computeIfAbsent(sid, k -> new ChatBot.Session());

                byte[] body = ex.getRequestBody().readNBytes(4000);
                String message = new String(body, StandardCharsets.UTF_8);
                ChatBot.Reply r = bot.reply(message, session);

                String out = "{\"reply\":" + json(r.text) + ",\"intent\":" + json(r.intent)
                        + ",\"confidence\":" + String.format(Locale.ROOT, "%.2f", r.confidence)
                        + ",\"exit\":" + r.exit + "}";
                send(ex, 200, "application/json; charset=utf-8", out);
            } catch (RuntimeException e) {
                send(ex, 500, "application/json; charset=utf-8", "{\"reply\":\"Internal error.\"}");
            }
        });

        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        return server;
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String json(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static final String PAGE = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>NovaBot - AI Chatbot</title>
<style>
  :root { --bg:#eef1f7; --card:#ffffff; --bot:#eceff5; --user:#2f6fed; --text:#1c2330; --muted:#6b7587; --line:#e1e5ee; }
  @media (prefers-color-scheme: dark) {
    :root { --bg:#0f1219; --card:#171b25; --bot:#232938; --user:#3b7cff; --text:#e8ebf2; --muted:#8d97ab; --line:#2a3142; }
  }
  * { box-sizing: border-box; }
  html, body { height: 100%; }
  body { margin:0; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; background:var(--bg); color:var(--text);
         display:flex; justify-content:center; align-items:center; }
  .app { width:min(760px,100%); height:min(820px,100%); background:var(--card); display:flex; flex-direction:column;
         border-radius:16px; overflow:hidden; box-shadow:0 12px 40px rgba(0,0,0,.14); }
  header { padding:14px 18px; border-bottom:1px solid var(--line); display:flex; align-items:center; gap:12px; }
  .avatar { width:38px; height:38px; border-radius:50%; background:var(--user); color:#fff; display:grid; place-items:center; font-weight:700; }
  header h1 { font-size:16px; margin:0; }
  header small { color:var(--muted); display:block; }
  header label { margin-left:auto; font-size:12px; color:var(--muted); display:flex; gap:6px; align-items:center; cursor:pointer; }
  #log { flex:1; overflow-y:auto; padding:18px; display:flex; flex-direction:column; gap:8px; }
  .msg { max-width:80%; padding:10px 14px; border-radius:16px; line-height:1.45; white-space:pre-wrap; word-wrap:break-word; }
  .bot { background:var(--bot); align-self:flex-start; border-bottom-left-radius:4px; }
  .user { background:var(--user); color:#fff; align-self:flex-end; border-bottom-right-radius:4px; }
  .meta { font-size:11px; color:var(--muted); align-self:flex-start; margin:-4px 0 4px 6px; }
  .typing { color:var(--muted); font-style:italic; }
  #chips { padding:0 18px 8px; display:flex; flex-wrap:wrap; gap:6px; }
  #chips button { border:1px solid var(--line); background:transparent; color:var(--text); border-radius:999px; padding:6px 12px; font-size:12px; cursor:pointer; }
  #chips button:hover { background:var(--bot); }
  form { display:flex; gap:8px; padding:12px 18px 18px; border-top:1px solid var(--line); }
  input[type=text] { flex:1; padding:12px 14px; border-radius:12px; border:1px solid var(--line); background:var(--bg); color:var(--text); font-size:15px; outline:none; }
  input[type=text]:focus { border-color:var(--user); }
  form button { border:0; background:var(--user); color:#fff; border-radius:12px; padding:0 20px; font-size:15px; cursor:pointer; }
</style>
</head>
<body>
<div class="app">
  <header>
    <div class="avatar">N</div>
    <div><h1>NovaBot</h1><small>AI assistant - NLP + machine learning, built in Java</small></div>
    <label><input type="checkbox" id="details"> AI details</label>
  </header>
  <div id="log" aria-live="polite"></div>
  <div id="chips"></div>
  <form id="form" autocomplete="off">
    <input type="text" id="msg" placeholder="Type your question..." autofocus>
    <button type="submit">Send</button>
  </form>
</div>
<script>
  const sid = Math.random().toString(36).slice(2) + Date.now().toString(36);
  const log = document.getElementById('log');
  const msg = document.getElementById('msg');
  const details = document.getElementById('details');

  function add(text, cls) {
    const d = document.createElement('div');
    d.className = 'msg ' + cls;
    d.textContent = text;
    log.appendChild(d);
    log.scrollTop = log.scrollHeight;
    return d;
  }
  function addMeta(text) {
    const d = document.createElement('div');
    d.className = 'meta';
    d.textContent = text;
    log.appendChild(d);
    log.scrollTop = log.scrollHeight;
  }

  async function send(text) {
    text = text.trim();
    if (!text) return;
    add(text, 'user');
    msg.value = '';
    const typing = add('NovaBot is typing...', 'bot typing');
    try {
      const res = await fetch('/chat', {
        method: 'POST',
        headers: { 'X-Session': sid, 'Content-Type': 'text/plain; charset=utf-8' },
        body: text
      });
      const data = await res.json();
      await new Promise(r => setTimeout(r, 350));
      typing.remove();
      add(data.reply, 'bot');
      if (details.checked) {
        addMeta('intent: ' + data.intent + '  |  confidence: ' + Math.round(data.confidence * 100) + '%');
      }
    } catch (e) {
      typing.remove();
      add('Connection problem - is the server still running?', 'bot');
    }
  }

  document.getElementById('form').addEventListener('submit', e => { e.preventDefault(); send(msg.value); });

  fetch('/suggestions').then(r => r.json()).then(list => {
    const box = document.getElementById('chips');
    list.forEach(s => {
      const b = document.createElement('button');
      b.type = 'button';
      b.textContent = s;
      b.onclick = () => send(s);
      box.appendChild(b);
    });
  });

  add("Hello! I'm NovaBot. Ask me about pricing, orders, refunds, accounts or support - or type 'help' to see all topics.", 'bot');
</script>
</body>
</html>
""";
}
