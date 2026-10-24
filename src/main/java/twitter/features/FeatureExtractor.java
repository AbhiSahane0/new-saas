package twitter.features;

import twitter.dataset.Tweet;
import twitter.preprocessing.Preprocessor;
import java.util.*;

/**
 * Builds a rich feature vector combining:
 *  1. TF-IDF (top 400 terms, full vocabulary)
 *  2. Lemma unigrams (binary presence)
 *  3. Bigrams (top 200)
 *  4. N-grams / trigrams (top 150)
 *  5. Co-occurrence scores (CoCo, top 300 vocab)
 *  6. Emotion Lexicon scores (6 dims)
 *  7. Lexicon match counts (6 dims)
 *  8. NLP meta-features (12 dims)
 *  9. Class-conditional word presence (6×20 = 120 dims)
 * Total: ~1200+ dims → fed to all classifiers
 */
public class FeatureExtractor {

    private static final int TFIDF_VOCAB   = 400;
    private static final int BIGRAM_VOCAB  = 200;
    private static final int NGRAM_VOCAB   = 150;
    private static final int COCO_VOCAB    = 300;
    private static final int COCO_WINDOW   = 2;
    private static final int TOP_PER_CLASS = 20; // top words per class for binary features

    private List<String> tfidfVocab  = new ArrayList<>();
    private List<String> bigramVocab = new ArrayList<>();
    private List<String> ngramVocab  = new ArrayList<>();
    private List<String> cocoVocab   = new ArrayList<>();
    private List<String> classTopWords = new ArrayList<>(); // 6*TOP_PER_CLASS

    private Map<String,Double>              idfMap   = new HashMap<>();
    private Map<String,Map<String,Integer>> coMatrix = new HashMap<>();

    private int featureDim = 0;

    // ─── Build phase (on training set only) ─────────────────────
    public void build(List<Tweet> trainSet, String[] labels) {

        // IDF
        Map<String,Integer> df = new HashMap<>(), cf = new HashMap<>();
        for (Tweet t : trainSet) {
            Set<String> seen = new HashSet<>(t.getLemmas());
            for (String w : seen) df.merge(w,1,Integer::sum);
            for (String w : t.getLemmas()) cf.merge(w,1,Integer::sum);
        }
        int N = trainSet.size();
        for (Map.Entry<String,Integer> e : df.entrySet())
            idfMap.put(e.getKey(), Math.log((N+1.0)/(e.getValue()+1)) + 1);

        // TF-IDF vocab (top by TF*IDF potential)
        cf.entrySet().stream()
            .sorted((a,b)->b.getValue()-a.getValue())
            .limit(TFIDF_VOCAB)
            .forEach(e->tfidfVocab.add(e.getKey()));

        // Co-occurrence vocab (same top-N for CoCo)
        cocoVocab.addAll(tfidfVocab.subList(0, Math.min(COCO_VOCAB, tfidfVocab.size())));

        // Bigrams
        Map<String,Integer> bCnt = new HashMap<>();
        for (Tweet t : trainSet) {
            List<String> l = t.getLemmas();
            for (int i=0;i<l.size()-1;i++) bCnt.merge(l.get(i)+"_"+l.get(i+1),1,Integer::sum);
        }
        bCnt.entrySet().stream().sorted((a,b)->b.getValue()-a.getValue())
            .limit(BIGRAM_VOCAB).forEach(e->bigramVocab.add(e.getKey()));

        // N-grams (trigrams)
        Map<String,Integer> nCnt = new HashMap<>();
        for (Tweet t : trainSet) {
            List<String> l = t.getLemmas();
            for (int i=0;i<l.size()-2;i++) nCnt.merge(l.get(i)+"_"+l.get(i+1)+"_"+l.get(i+2),1,Integer::sum);
        }
        nCnt.entrySet().stream().sorted((a,b)->b.getValue()-a.getValue())
            .limit(NGRAM_VOCAB).forEach(e->ngramVocab.add(e.getKey()));

        // Co-occurrence matrix
        for (Tweet t : trainSet) {
            List<String> l = t.getLemmas();
            for (int i=0;i<l.size();i++) {
                String w = l.get(i);
                coMatrix.putIfAbsent(w, new HashMap<>());
                for (int j=Math.max(0,i-COCO_WINDOW); j<=Math.min(l.size()-1,i+COCO_WINDOW); j++) {
                    if (i!=j) coMatrix.get(w).merge(l.get(j),1,Integer::sum);
                }
            }
        }

        // Top words per class (for binary class-conditional features)
        Map<String, Map<String,Integer>> classCF = new LinkedHashMap<>();
        for (String lb : labels) classCF.put(lb, new HashMap<>());
        for (Tweet t : trainSet)
            for (String w : t.getLemmas()) classCF.get(t.getLabel()).merge(w,1,Integer::sum);
        for (String lb : labels) {
            classCF.get(lb).entrySet().stream()
                .sorted((a,b)->b.getValue()-a.getValue())
                .limit(TOP_PER_CLASS)
                .forEach(e->classTopWords.add(e.getKey()));
        }

        featureDim = tfidfVocab.size()   // TF-IDF
                   + tfidfVocab.size()   // Lemma binary
                   + bigramVocab.size()  // Bigrams
                   + ngramVocab.size()   // N-grams
                   + cocoVocab.size()    // CoCo
                   + 6 + 6              // Lexicon scores + counts
                   + 12                 // NLP meta
                   + classTopWords.size(); // Class-conditional binary
    }

    // ─── Extract all features for one tweet ──────────────────────
    public double[] extract(Tweet t) {
        List<String> lem = t.getLemmas();
        List<String> tok = t.getTokens();
        String raw = t.getRawText().toLowerCase();

        // TF map
        Map<String,Integer> tf = new HashMap<>();
        for (String w : lem) tf.merge(w,1,Integer::sum);
        int total = Math.max(1, lem.size());

        // Bigram map
        Map<String,Integer> bg = new HashMap<>();
        for (int i=0;i<lem.size()-1;i++) bg.merge(lem.get(i)+"_"+lem.get(i+1),1,Integer::sum);

        // N-gram map
        Map<String,Integer> ng = new HashMap<>();
        for (int i=0;i<lem.size()-2;i++) ng.merge(lem.get(i)+"_"+lem.get(i+1)+"_"+lem.get(i+2),1,Integer::sum);

        List<Double> v = new ArrayList<>();

        // 1. TF-IDF
        for (String w : tfidfVocab) {
            double tfv = (double)tf.getOrDefault(w,0)/total;
            v.add(tfv * idfMap.getOrDefault(w,1.0));
        }

        // 2. Lemma binary presence
        Set<String> lemSet = new HashSet<>(lem);
        for (String w : tfidfVocab) v.add(lemSet.contains(w) ? 1.0 : 0.0);

        // 3. Bigrams
        for (String b : bigramVocab) v.add((double)bg.getOrDefault(b,0));

        // 4. N-grams
        for (String n : ngramVocab) v.add((double)ng.getOrDefault(n,0));

        // 5. CoCo — weighted co-occurrence
        for (String w : cocoVocab) {
            double score = 0;
            Map<String,Integer> nbrs = coMatrix.getOrDefault(w, Collections.emptyMap());
            for (String tw : lem) score += nbrs.getOrDefault(tw,0);
            v.add(score);
        }
        // L2-normalise CoCo block
        int cocoStart = tfidfVocab.size()*2 + bigramVocab.size() + ngramVocab.size();
        double cocoNorm = 1e-9;
        for (int i=cocoStart; i<cocoStart+cocoVocab.size(); i++) cocoNorm += v.get(i)*v.get(i);
        cocoNorm = Math.sqrt(cocoNorm);
        for (int i=cocoStart; i<cocoStart+cocoVocab.size(); i++) v.set(i, v.get(i)/cocoNorm);

        // 6. Emotion lexicon scores (6)
        double[] lexScores = EmotionLexicon.score(lem);
        for (double s : lexScores) v.add(s);

        // 7. Lexicon match counts (6)
        double[] lexCounts = EmotionLexicon.matchCount(lem);
        for (double s : lexCounts) v.add(s);

        // 8. NLP meta-features (12)
        v.add((double)tok.size());                                                     // num tokens
        v.add((double)lem.size());                                                     // num lemmas
        v.add(tok.stream().mapToInt(String::length).average().orElse(0));              // avg token len
        v.add(tok.size()>0?(double)new HashSet<>(tok).size()/tok.size():0);            // type ratio
        v.add((double)raw.chars().filter(c->c=='!').count());                          // exclamations
        v.add((double)raw.chars().filter(c->c=='?').count());                          // questions
        v.add((double)raw.chars().filter(Character::isUpperCase).count());             // caps count
        v.add((double)lem.stream().filter(EmotionLexicon::contains).count()); // lexicon hits
        v.add((double)lem.stream().filter(w->w.length()>6).count()); // long words
        v.add(raw.contains("not") || raw.contains("never") || raw.contains("no") ? 1.0 : 0.0); // negation
        v.add((double)lem.size() / Math.max(1, tok.size()));                           // compression ratio
        v.add((double)lem.stream().filter(w->EMOTION_SUFFIX(w)).count());              // emotion suffixes

        // 9. Class-conditional binary (classTopWords.size())
        for (String w : classTopWords) v.add(lemSet.contains(w) ? 1.0 : 0.0);

        // Convert to array
        double[] arr = new double[v.size()];
        for (int i=0;i<arr.length;i++) arr[i] = v.get(i);
        return arr;
    }

    private static boolean EMOTION_SUFFIX(String w) {
        return w.endsWith("ful") || w.endsWith("less") || w.endsWith("ness") ||
               w.endsWith("ed")  || w.endsWith("ing")  || w.endsWith("ous") ||
               w.endsWith("ive") || w.endsWith("ic");
    }

    // ─── Build separate TF-IDF, CoCo vectors for named modes ─────
    /** Returns feature vector for a specific mode (for classifier comparison output) */
    public double[] buildVector(Tweet t, String mode) {
        switch (mode) {
            case "tfidf":       return tfidfOnly(t);
            case "coco":        return cocoOnly(t);
            case "bigram":      return bigramOnly(t);
            case "ngram":       return ngramOnly(t);
            case "lemma+tfidf": return lemmaAndTfidf(t);
            case "lemma+coco":  return lemmaAndCoco(t);
            default:            return extract(t); // full
        }
    }

    private double[] tfidfOnly(Tweet t) {
        Map<String,Integer> tf = new HashMap<>();
        for (String w : t.getLemmas()) tf.merge(w,1,Integer::sum);
        int n = Math.max(1,t.getLemmas().size());
        List<Double> v = new ArrayList<>();
        for (String w : tfidfVocab) v.add((double)tf.getOrDefault(w,0)/n * idfMap.getOrDefault(w,1.0));
        appendNLP(v,t);
        return toArray(v);
    }

    private double[] cocoOnly(Tweet t) {
        List<Double> v = new ArrayList<>();
        for (String w : cocoVocab) {
            double s=0; Map<String,Integer> nbrs=coMatrix.getOrDefault(w,Collections.emptyMap());
            for (String tw : t.getLemmas()) s+=nbrs.getOrDefault(tw,0);
            v.add(s);
        }
        l2normalise(v,0,cocoVocab.size());
        appendNLP(v,t);
        appendLexicon(v,t);
        return toArray(v);
    }

    private double[] bigramOnly(Tweet t) {
        Map<String,Integer> bg = new HashMap<>();
        List<String> l = t.getLemmas();
        for (int i=0;i<l.size()-1;i++) bg.merge(l.get(i)+"_"+l.get(i+1),1,Integer::sum);
        List<Double> v = new ArrayList<>();
        for (String b : bigramVocab) v.add((double)bg.getOrDefault(b,0));
        appendNLP(v,t);
        appendLexicon(v,t);
        return toArray(v);
    }

    private double[] ngramOnly(Tweet t) {
        Map<String,Integer> ng = new HashMap<>();
        List<String> l = t.getLemmas();
        for (int i=0;i<l.size()-2;i++) ng.merge(l.get(i)+"_"+l.get(i+1)+"_"+l.get(i+2),1,Integer::sum);
        List<Double> v = new ArrayList<>();
        for (String n : ngramVocab) v.add((double)ng.getOrDefault(n,0));
        appendNLP(v,t);
        appendLexicon(v,t);
        return toArray(v);
    }

    private double[] lemmaAndTfidf(Tweet t) {
        // TF-IDF + binary lemma presence + lexicon + NLP
        Map<String,Integer> tf = new HashMap<>();
        for (String w : t.getLemmas()) tf.merge(w,1,Integer::sum);
        int n = Math.max(1,t.getLemmas().size());
        Set<String> lemSet = new HashSet<>(t.getLemmas());
        List<Double> v = new ArrayList<>();
        for (String w : tfidfVocab) v.add((double)tf.getOrDefault(w,0)/n * idfMap.getOrDefault(w,1.0));
        for (String w : tfidfVocab) v.add(lemSet.contains(w)?1.0:0.0);
        appendLexicon(v,t);
        appendNLP(v,t);
        return toArray(v);
    }

    private double[] lemmaAndCoco(Tweet t) {
        Set<String> lemSet = new HashSet<>(t.getLemmas());
        List<Double> v = new ArrayList<>();
        for (String w : tfidfVocab) v.add(lemSet.contains(w)?1.0:0.0);
        for (String w : cocoVocab) {
            double s=0; Map<String,Integer> nbrs=coMatrix.getOrDefault(w,Collections.emptyMap());
            for (String tw : t.getLemmas()) s+=nbrs.getOrDefault(tw,0);
            v.add(s);
        }
        l2normalise(v,tfidfVocab.size(),tfidfVocab.size()+cocoVocab.size());
        appendLexicon(v,t);
        appendNLP(v,t);
        return toArray(v);
    }

    private void appendNLP(List<Double> v, Tweet t) {
        List<String> tok = t.getTokens();
        List<String> lem = t.getLemmas();
        String raw = t.getRawText().toLowerCase();
        v.add((double)tok.size());
        v.add((double)lem.size());
        v.add(tok.stream().mapToInt(String::length).average().orElse(0));
        v.add(tok.size()>0?(double)new HashSet<>(tok).size()/tok.size():0);
        v.add((double)raw.chars().filter(c->c=='!').count());
        v.add((double)raw.chars().filter(c->c=='?').count());
        v.add(raw.contains("not")||raw.contains("never")||raw.contains("no")?1.0:0.0);
        v.add((double)lem.stream().filter(EmotionLexicon::contains).count());
    }

    private void appendLexicon(List<Double> v, Tweet t) {
        for (double s : EmotionLexicon.score(t.getLemmas())) v.add(s);
        for (double s : EmotionLexicon.matchCount(t.getLemmas())) v.add(s);
    }

    private void l2normalise(List<Double> v, int start, int end) {
        double norm=1e-9;
        for (int i=start;i<end;i++) norm+=v.get(i)*v.get(i);
        norm=Math.sqrt(norm);
        for (int i=start;i<end;i++) v.set(i,v.get(i)/norm);
    }

    private double[] toArray(List<Double> v) {
        double[] a=new double[v.size()]; for(int i=0;i<a.length;i++) a[i]=v.get(i); return a;
    }

    // ─── Feature summary ────────────────────────────────────────
    public void printFeatureSummary(List<Tweet> tweets) {
        System.out.println("-".repeat(68) + "-");
        System.out.println("||ALL EXTRACTED FEATURES SUMMARY                 ||");
        System.out.println("-".repeat(68) + "-");
        System.out.printf("%n  1. LEMMAS%n");
        System.out.printf("     Unique lemmas in corpus   : %d%n", idfMap.size());
        System.out.printf("     TF-IDF vocabulary size    : %d terms%n", tfidfVocab.size());
        System.out.printf("     Sample: %s%n", String.join(", ",tfidfVocab.subList(0,Math.min(12,tfidfVocab.size()))));
        System.out.printf("%n  2. NLP META-FEATURES (12 per tweet)%n");
        System.out.println("     num_tokens, num_lemmas, avg_word_len, type_ratio,");
        System.out.println("     exclamations, questions, negation, lexicon_hits,");
        System.out.println("     long_words, caps_count, compression_ratio, emotion_suffixes");
        System.out.printf("%n  3. BIGRAMS%n");
        System.out.printf("     Vocabulary : %d unique bigrams%n", bigramVocab.size());
        System.out.printf("     Sample: %s%n", String.join(", ",bigramVocab.subList(0,Math.min(6,bigramVocab.size()))));
        System.out.printf("%n  4. N-GRAMS (trigrams)%n");
        System.out.printf("     Vocabulary : %d unique trigrams%n", ngramVocab.size());
        System.out.printf("     Sample: %s%n", String.join(", ",ngramVocab.subList(0,Math.min(4,ngramVocab.size()))));
        System.out.printf("%n  5. TF-IDF%n");
        System.out.printf("     Vector dim : %d | IDF computed on %d documents%n",
            tfidfVocab.size(), tweets.size());
        System.out.printf("%n  6. CO-OCCURRENCE / CoCo (window=%d)%n", COCO_WINDOW);
        System.out.printf("     Context vocab : %d | Matrix entries: %d%n",
            cocoVocab.size(), coMatrix.size());
        System.out.printf("%n  7. EMOTION LEXICON FEATURES (12 dims)%n");
        System.out.printf("     Lexicon entries   : %d emotion-labelled words%n",
            EmotionLexicon.getLexicon().size());
        System.out.println("     6 normalised emotion scores + 6 match-count features");
        System.out.printf("%n  8. CLASS-CONDITIONAL BINARY FEATURES%n");
        System.out.printf("     Top %d words per class × 6 classes = %d binary dims%n",
            TOP_PER_CLASS, classTopWords.size());
        System.out.printf("%n  [Feature Vector Dimensions per Mode]%n");
        System.out.println("  " + "-".repeat(45));
        System.out.printf("  %-22s %d%n","TF-IDF only:",    tfidfVocab.size()+8);
        System.out.printf("  %-22s %d%n","CoCo only:",      cocoVocab.size()+8+12);
        System.out.printf("  %-22s %d%n","Bigram only:",    bigramVocab.size()+8+12);
        System.out.printf("  %-22s %d%n","N-gram only:",    ngramVocab.size()+8+12);
        System.out.printf("  %-22s %d%n","Lemma+TF-IDF:",   tfidfVocab.size()*2+12+8);
        System.out.printf("  %-22s %d%n","Lemma+CoCo:",     tfidfVocab.size()+cocoVocab.size()+12+8);
        System.out.printf("  %-22s %d%n","Full (all modes):",featureDim);
    }

    public int getFeatureDim() { return featureDim; }
    public List<String> getTfidfVocab()  { return tfidfVocab; }
    public List<String> getBigramVocab() { return bigramVocab; }
    public List<String> getNgramVocab()  { return ngramVocab; }
    public List<String> getCocoVocab()   { return cocoVocab; }
    public Map<String,Double> getIdfMap(){ return idfMap; }
}
