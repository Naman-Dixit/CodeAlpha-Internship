# NovaBot - AI Chatbot in Java

A complete chatbot with **NLP**, **machine learning + rule-based logic**, **FAQ training**, and **three interfaces**
(console, desktop GUI, web). No external libraries - only the JDK.

**Requirements:** JDK 17 or newer.

## Run it

```
javac *.java
java Main            # menu: choose console / GUI / web / both / self-test
java Main console    # terminal chat
java Main gui        # Swing desktop window
java Main web        # browser chat at http://localhost:8080   (java Main web 9000 for another port)
java Main all        # GUI + web together
java Main test       # sample conversation + machine-learning evaluation
```

## How it works

Every message goes through these steps (in `ChatBot.java`):

1. `learn: question => answer` teaches the bot a new FAQ instantly (saved to `learned_faq.txt`, models retrain).
2. Yes/no replies to a "Did you mean ...?" question.
3. Commands: `help`, `topics`, `stats`, `debug on/off`, `retrain`.
4. **Rule-based** answers (`Rules.java`): calculator (`12 * (3 + 4)`), time/date, remembering your name.
5. **Machine learning** (the main FAQ engine) - hybrid of two models:
   * **TF-IDF + cosine similarity** (`TfidfMatcher.java`) - finds the closest known question.
   * **Naive Bayes classifier** (`NaiveBayes.java`) - predicts the topic from words and word pairs.
   * Final score = 0.6 x TF-IDF similarity + 0.4 x Naive Bayes probability.
     - score >= 0.40: answer.  0.25 - 0.40: ask "Did you mean ...?".  Below that: ask to rephrase.
6. Fallback message with hints.

**NLP techniques** (`NLP.java`): normalization and contraction expansion, tokenization, stop-word removal,
synonym mapping, a suffix-stripping stemmer, spell correction (edit distance against the training vocabulary),
unigram + bigram features, and lexicon-based sentiment analysis (the bot shows empathy when you sound unhappy).

**Context memory:** remembers your name and pending clarifications per conversation (separate per browser tab on the web).

## Training the bot on FAQs

* Built-in training data: 30 topics, ~465 example questions (`KnowledgeBase.java`).
* Add your own without touching code - edit `faq.txt` (one topic per line):
  ```
  tag||question 1;;question 2;;question 3||answer 1;;answer 2
  ```
  then type `retrain` in the chat (or restart).
* Teach it while chatting: `learn: what is your favourite colour => Blue!`  (GUI: *Bot > Teach the Bot...*)
* In answers, `{name}` is replaced by the user's name when known.

## Accuracy (measured with `java Main test`)

Leave-one-out test: each training question is hidden, the bot is retrained, then asked that question.
Result: the correct topic is ranked first for about **80%** of questions it has never seen worded that way, and
answers given at or above the 0.40 threshold are correct about **90%** of the time. Unrelated questions
(weather, cooking, sports) score below the threshold and are not answered. Accuracy improves as you add more
example questions per topic.

## Files

| File | Purpose |
|---|---|
| `Main.java` | Launcher / menu |
| `ChatBot.java` | Brain: routing, hybrid ML scoring, context, online learning, evaluation |
| `NLP.java` | Text processing pipeline |
| `KnowledgeBase.java` | FAQ training data + file loading |
| `NaiveBayes.java`, `TfidfMatcher.java` | The two ML models |
| `Rules.java` | Rule-based answers (calculator, time, name) |
| `ConsoleChat.java`, `ChatGUI.java`, `WebChatServer.java` | The three interfaces |
| `faq.txt` | Your own training data (sample included) |

The web server listens on `localhost` only. Endpoints: `GET /` (page), `POST /chat` (plain-text body, JSON reply), `GET /suggestions`.
