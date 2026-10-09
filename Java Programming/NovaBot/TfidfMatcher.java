import java.util.*;

/**
 * TF-IDF vectorizer with cosine similarity.
 * Every training question becomes a weighted vector; a user query is compared with all of them.
 * Words the model has never seen count against the similarity, so unrelated questions score low.
 */
public class TfidfMatcher {

    public static class Result {
        /** Best cosine similarity per intent (0..1). */
        public final Map<String, Double> similarity = new HashMap<>();
        /** The closest training question per intent. */
        public final Map<String, String> bestExample = new HashMap<>();
    }

    private static class Doc {
        final String tag;
        final String text;
        final Map<String, Double> vec;

        Doc(String tag, String text, Map<String, Double> vec) {
            this.tag = tag;
            this.text = text;
            this.vec = vec;
        }
    }

    private final List<Doc> docs = new ArrayList<>();
    private final Map<String, Double> idf = new HashMap<>();
    private double idfMax = 1;

    public void train(KnowledgeBase kb) {
        docs.clear();
        idf.clear();

        List<String> tags = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<List<String>> stemLists = new ArrayList<>();
        // Document frequency is counted per INTENT: a word found in many topics is a weak signal.
        Map<String, Set<String>> termIntents = new HashMap<>();
        int intentCount = 0;

        for (KnowledgeBase.Intent in : kb.all()) {
            if (!in.examples.isEmpty()) intentCount++;
            for (String ex : in.examples) {
                List<String> st = NLP.stems(NLP.tokens(ex));
                if (st.isEmpty()) continue;
                tags.add(in.tag);
                texts.add(ex);
                stemLists.add(st);
                for (String t : st) termIntents.computeIfAbsent(t, k -> new HashSet<>()).add(in.tag);
            }
        }

        int n = tags.size();
        for (Map.Entry<String, Set<String>> e : termIntents.entrySet()) {
            double base = Math.log((intentCount + 1.0) / (e.getValue().size() + 1.0)) + 1.0;
            idf.put(e.getKey(), base * base);          // squared idf = sharper focus on rare, topic-specific words
        }
        double maxBase = Math.log(intentCount + 1.0) + 1.0;
        idfMax = maxBase * maxBase;

        for (int i = 0; i < n; i++) {
            double[] norm = new double[1];
            Map<String, Double> v = vector(stemLists.get(i), norm);
            if (norm[0] > 0) for (Map.Entry<String, Double> e : v.entrySet()) e.setValue(e.getValue() / norm[0]);
            docs.add(new Doc(tags.get(i), texts.get(i), v));
        }
    }

    /** Builds the weighted vector; unknown terms only add to the norm (they lower similarity). */
    private Map<String, Double> vector(List<String> stems, double[] normOut) {
        Map<String, Integer> tf = new HashMap<>();
        for (String s : stems) tf.merge(s, 1, Integer::sum);

        Map<String, Double> v = new HashMap<>();
        double normSq = 0;
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            Double w = idf.get(e.getKey());
            double weight = (w == null ? idfMax : w) * (1 + Math.log(e.getValue()));
            normSq += weight * weight;
            if (w != null) v.put(e.getKey(), weight);
        }
        normOut[0] = Math.sqrt(normSq);
        return v;
    }

    public Result match(List<String> stems) {
        Result r = new Result();
        double[] qn = new double[1];
        Map<String, Double> q = vector(stems, qn);
        if (qn[0] == 0) return r;

        for (Doc d : docs) {
            double dot = 0;
            for (Map.Entry<String, Double> e : q.entrySet()) {
                Double dv = d.vec.get(e.getKey());
                if (dv != null) dot += e.getValue() * dv;
            }
            double sim = dot / qn[0];
            if (sim > r.similarity.getOrDefault(d.tag, 0.0)) {
                r.similarity.put(d.tag, sim);
                r.bestExample.put(d.tag, d.text);
            }
        }
        return r;
    }
}
