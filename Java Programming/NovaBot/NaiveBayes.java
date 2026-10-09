import java.util.*;

/**
 * Multinomial Naive Bayes text classifier with Laplace smoothing.
 * Trained on the example questions of every intent in the KnowledgeBase.
 */
public class NaiveBayes {

    private final Map<String, Integer> docCount = new HashMap<>();
    private final Map<String, Map<String, Integer>> wordCount = new HashMap<>();
    private final Map<String, Integer> totalWords = new HashMap<>();
    private final Set<String> vocab = new HashSet<>();
    private int totalDocs;

    public void train(KnowledgeBase kb) {
        docCount.clear();
        wordCount.clear();
        totalWords.clear();
        vocab.clear();
        totalDocs = 0;

        for (KnowledgeBase.Intent in : kb.all()) {
            for (String example : in.examples) {
                List<String> f = NLP.features(NLP.stems(NLP.tokens(example)));
                if (f.isEmpty()) continue;
                docCount.merge(in.tag, 1, Integer::sum);
                totalDocs++;
                Map<String, Integer> wc = wordCount.computeIfAbsent(in.tag, k -> new HashMap<>());
                for (String w : f) {
                    wc.merge(w, 1, Integer::sum);
                    totalWords.merge(in.tag, 1, Integer::sum);
                    vocab.add(w);
                }
            }
        }
    }

    /** Returns P(intent | features) for every intent (probabilities sum to 1). */
    public Map<String, Double> predict(List<String> features) {
        Map<String, Double> probs = new LinkedHashMap<>();
        if (docCount.isEmpty()) return probs;

        List<String> known = new ArrayList<>();
        for (String f : features) if (vocab.contains(f)) known.add(f);

        int v = vocab.size();
        Map<String, Double> logProb = new LinkedHashMap<>();
        double max = -Double.MAX_VALUE;
        for (String tag : docCount.keySet()) {
            double lp = Math.log((double) docCount.get(tag) / totalDocs);
            Map<String, Integer> wc = wordCount.get(tag);
            int total = totalWords.getOrDefault(tag, 0);
            for (String w : known) {
                lp += Math.log((wc.getOrDefault(w, 0) + 1.0) / (total + v));
            }
            logProb.put(tag, lp);
            max = Math.max(max, lp);
        }

        double sum = 0;
        for (Map.Entry<String, Double> e : logProb.entrySet()) {
            double p = Math.exp(e.getValue() - max);
            probs.put(e.getKey(), p);
            sum += p;
        }
        for (Map.Entry<String, Double> e : probs.entrySet()) e.setValue(e.getValue() / sum);
        return probs;
    }

    /** Fraction of the (unigram) stems that the model has seen during training. */
    public double coverage(List<String> stems) {
        if (stems.isEmpty()) return 0;
        int known = 0;
        for (String s : stems) if (vocab.contains(s)) known++;
        return (double) known / stems.size();
    }
}
