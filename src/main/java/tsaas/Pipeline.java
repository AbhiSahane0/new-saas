package tsaas;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import twitter.classifiers.*;
import twitter.dataset.*;
import twitter.evaluation.ResultPrinter;
import twitter.features.FeatureExtractor;
import twitter.preprocessing.Preprocessor;

/**
 * Server-side counterpart of final_code.runProcess(): same six steps, same status / progress strings,
 * same System.out lines. Keeps the trained models in memory for live prediction.
 */
public class Pipeline {
    public static final String[] MODES = {"tfidf","coco","bigram","ngram","lemma+tfidf","lemma+coco"};
    public static final String[] CNAMES = {"Naive Bayes","SVM (Linear OvR)","ANN (128-64-6)","HySimNet","RNN-LSTM"};
    static final double TRAIN_R = 0.80;

    public interface Predictor { int predict(double[] v, List<String> lemmas); }

    private final Hub hub;
    private final Object lock = new Object();

    // state mirrored to the browser
    volatile boolean running = false;
    String datasetName = "dataset.txt";
    Path datasetPath;
    String status = "Ready — load a dataset and press Run";
    String barMsg = "Ready — press Run";
    int barPct = 0;
    final List<String> log = Collections.synchronizedList(new ArrayList<>());
    Map<String,Object> cards = new LinkedHashMap<>();
    Map<String,Object> pie = null;
    Map<String,Object> view = null;     // results view-model (step 6)

    // live prediction state
    volatile Predictor[][] models;      // [classifier][mode]
    volatile FeatureExtractor fe;

    public Pipeline(Hub hub) throws IOException {
        this.hub = hub;
        Path dir = Files.createTempDirectory("tsaas-ds");
        datasetPath = dir.resolve("dataset.txt");
        try (InputStream in = Pipeline.class.getResourceAsStream("/dataset.txt")) {
            Files.copy(in, datasetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        cards.put("total", "—"); cards.put("train", "—"); cards.put("test", "—");
        cards.put("bestAcc", "—"); cards.put("bestF1", "—"); cards.put("bestModel", "—");
    }

    // ── System.out capture (what the Swing app does in runProcess) ───────────
    public void installConsole() {
        final PrintStream orig = System.out;
        OutputStream os = new OutputStream() {
            final ByteArrayOutputStream buf = new ByteArrayOutputStream();
            @Override public synchronized void write(int b) {
                buf.write(b);
                if (b == '\n') {
                    String line = buf.toString(StandardCharsets.UTF_8);
                    buf.reset();
                    orig.print(line);
                    synchronized (log) { log.add(line); if (log.size() > 20000) log.remove(0); }
                    hub.send("log", line);
                }
            }
        };
        System.setOut(new PrintStream(os, true, StandardCharsets.UTF_8));
    }

    private void setStatus(String msg) { status = msg; hub.send("status", msg); }
    private void masterBar(String msg, int pct) {
        barMsg = msg; barPct = pct;
        hub.send("bar", Map.of("msg", msg, "pct", pct));
    }
    // KPI cards and the emotion chart are held back until the run has finished
    private final Map<String,Object> pendingCards = new LinkedHashMap<>();
    private Map<String,Object> pendingPie;
    private void setCard(String k, String v) { pendingCards.put(k, v); }

    public Map<String,Object> snapshot() {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("dataset", datasetName);
        m.put("running", running);
        m.put("status", status);
        m.put("bar", Map.of("msg", barMsg, "pct", barPct));
        m.put("cards", cards);
        m.put("pie", pie);
        m.put("results", view);
        synchronized (log) { m.put("log", new ArrayList<>(log)); }
        return m;
    }

    // ── Dataset upload (browse) ──────────────────────────────────────────────
    /** Validates with DataLoader's own rules; returns number of tweets loaded. */
    public int setDataset(String name, byte[] data) throws IOException {
        synchronized (lock) {
            if (running) throw new IllegalStateException("A run is in progress.");
            Path dir = Files.createTempDirectory("tsaas-ds");
            Path p = dir.resolve("dataset.txt");
            Files.write(p, data);
            PrintStream saved = System.out;
            int n;
            try {
                System.setOut(new PrintStream(OutputStream.nullOutputStream()));
                n = new DataLoader().load(p.toString()).size();
            } finally { System.setOut(saved); }
            if (n == 0) throw new IllegalArgumentException(
                "No valid lines found. Expected one tweet per line as  text;label  with label in "
                + String.join(", ", DataLoader.LABELS) + ".");
            datasetPath = p;
            datasetName = name;
            setStatus("Dataset selected: " + name);
            hub.send("dataset", name);
            return n;
        }
    }

    // ── Run ──────────────────────────────────────────────────────────────────
    public boolean start() {
        synchronized (lock) {
            if (running) return false;
            running = true;
        }
        Thread t = new Thread(this::runProcess, "pipeline");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private void runProcess() {
        hub.send("running", true);
        List<ClassifierResult> results = new ArrayList<>();
        synchronized (log) { log.clear(); }
        view = null; pie = null; pendingCards.clear(); pendingPie = null;
        for (String k : new ArrayList<>(cards.keySet())) cards.put(k, "—");
        barMsg = "Starting…"; barPct = 0;
        hub.send("reset", "");
        hub.send("bar", Map.of("msg", "Starting…", "pct", 0));
        try {
            // Step 1
            setStatus("Step 1 — Loading dataset…");
            masterBar("Step 1/6  Load Dataset", 5);
            DataLoader loader = new DataLoader();
            List<Tweet> allTweets = loader.load(datasetPath.toString());
            setCard("total", String.valueOf(allTweets.size()));
            setPie(allTweets);
            masterBar("Step 1/6  Done", 12);

            // Step 2
            setStatus("Step 2 — Pre-processing tweets…");
            masterBar("Step 2/6  Pre-processing", 14);
            System.out.println("\nclean - tokenise - stopword removal - lemmatise ...");
            new Preprocessor().preprocess(allTweets);
            masterBar("Step 2/6  Done", 22);

            // Step 3
            setStatus("Step 3 — Extracting features…");
            masterBar("Step 3/6  Feature Extraction", 24);
            Collections.shuffle(allTweets, new Random(42));
            int trSz = (int)(allTweets.size() * TRAIN_R);
            List<Tweet> trainTweets = allTweets.subList(0, trSz);
            List<Tweet> testTweets  = allTweets.subList(trSz, allTweets.size());
            System.out.printf("%n  Train: %d | Test: %d | Split: 80/20%%%n%n",
                trainTweets.size(), testTweets.size());
            FeatureExtractor featureExtractor = new FeatureExtractor();
            featureExtractor.build(trainTweets, DataLoader.LABELS);
            featureExtractor.printFeatureSummary(allTweets);
            setCard("train", String.valueOf(trainTweets.size()));
            setCard("test", String.valueOf(testTweets.size()));
            masterBar("Step 3/6  Done", 32);

            // Step 4
            setStatus("Step 4 — Training classifiers…");
            int total = MODES.length * 5, done = 0;
            Predictor[][] pm = new Predictor[5][MODES.length];
            for (int mi = 0; mi < MODES.length; mi++) {
                String mode = MODES[mi];
                System.out.println("Feature Mode: [" + mode.toUpperCase() + "]");

                System.out.print("  Naive Bayes    ... ");
                NaiveBayes nbm = new NaiveBayes();
                ClassifierResult nb = nbm.train(trainTweets, testTweets, featureExtractor, mode);
                pm[0][mi] = nbm::predictWithLexicon;
                results.add(nb); done++;
                System.out.printf("Acc=%.2f%% F1=%.2f%%%n", nb.accuracy, nb.f1);
                masterBar("Training " + mode.toUpperCase() + "…", 32 + done*38/total);

                System.out.print("  SVM            ... ");
                SVM svmm = new SVM();
                ClassifierResult svm = svmm.train(trainTweets, testTweets, featureExtractor, mode);
                pm[1][mi] = svmm::predict;
                results.add(svm); done++;
                System.out.printf("Acc=%.2f%% F1=%.2f%%%n", svm.accuracy, svm.f1);
                masterBar("Training " + mode.toUpperCase() + "…", 32 + done*38/total);

                System.out.print("  ANN            ... ");
                ANN annm = new ANN();
                ClassifierResult ann = annm.train(trainTweets, testTweets, featureExtractor, mode);
                pm[2][mi] = annm::predict;
                results.add(ann); done++;
                System.out.printf("Acc=%.2f%% F1=%.2f%%%n", ann.accuracy, ann.f1);
                masterBar("Training " + mode.toUpperCase() + "…", 32 + done*38/total);

                System.out.print("  HySimNet       ... ");
                HySimNet hym = new HySimNet();
                ClassifierResult hy = hym.train(trainTweets, testTweets, featureExtractor, mode);
                pm[3][mi] = hym::predict;
                results.add(hy); done++;
                System.out.printf("Acc=%.2f%% F1=%.2f%%%n", hy.accuracy, hy.f1);
                masterBar("Training " + mode.toUpperCase() + "…", 32 + done*38/total);

                System.out.print("  RNN-LSTM       ... ");
                RNN_LSTM_N lstmm = new RNN_LSTM_N();
                ClassifierResult lstm = lstmm.train(trainTweets, testTweets, featureExtractor, mode);
                pm[4][mi] = lstmm::predict;
                results.add(lstm); done++;
                System.out.printf("Acc=%.2f%% F1=%.2f%%%n", lstm.accuracy, lstm.f1);
                masterBar("Training " + mode.toUpperCase() + "…", 32 + done*38/total);
            }

            // Step 5
            setStatus("Step 5 — Evaluating models…");
            masterBar("Step 5/6  Evaluation", 72);
            new ResultPrinter().printAllResults(results);
            masterBar("Step 5/6  Done", 88);

            // Step 6
            setStatus("Step 6 — Populating results…");
            masterBar("Step 6/6  Generating Report", 92);
            populateResults(results);
            Thread.sleep(300);
            masterBar("Complete!", 100);

            models = pm;
            fe = featureExtractor;
            cards.putAll(pendingCards); pie = pendingPie;
            hub.send("cards", cards); hub.send("pie", pie);
            ClassifierResult best = best(results);
            barMsg = "Complete ✓";
            hub.send("bar", Map.of("msg", "Complete ✓", "pct", 100));
            setStatus("Complete — " + results.size() + " experiments across 5 classifiers x 6 feature modes.");
            running = false;
            hub.send("running", false);
            hub.send("done", Map.of("name", best.classifierName, "mode", best.featureMode,
                "acc", String.format("%.2f%%", best.accuracy), "f1", String.format("%.2f%%", best.f1),
                "n", results.size()));
        } catch (Exception e) {
            e.printStackTrace();
            setStatus("Error: " + e.getMessage());
            barMsg = "Error";
            hub.send("bar", Map.of("msg", "Error", "pct", barPct));
            running = false;
            hub.send("running", false);
            hub.send("runerror", e.getMessage() == null ? "Unknown error occurred." : e.getMessage());
        } finally {
            running = false;
        }
    }

    static ClassifierResult best(List<ClassifierResult> results) {
        return results.stream().max(Comparator.comparingDouble(r -> r.accuracy)).orElse(results.get(0));
    }

    private void setPie(List<Tweet> tweets) {
        String[] lbls = DataLoader.LABELS;
        int[] counts = new int[lbls.length];
        for (Tweet t : tweets) counts[DataLoader.labelIndex(t.getLabel())]++;
        double total = tweets.size();
        List<Map<String,Object>> legend = new ArrayList<>();
        for (int i = 0; i < lbls.length; i++)
            legend.add(Map.of("label", lbls[i], "count", counts[i],
                "pct", String.format("%.1f", counts[i]/total*100)));
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("total", tweets.size()); m.put("legend", legend);
        pendingPie = m;
    }

    /** Mirrors final_code.populateResults(): all numbers are formatted here with String.format. */
    private void populateResults(List<ClassifierResult> results) {
        List<Map<String,Object>> rows = new ArrayList<>();
        for (ClassifierResult r : results) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("c", r.classifierName); row.put("m", r.featureMode);
            row.put("acc", String.format("%.2f", r.accuracy));
            row.put("prec", String.format("%.2f", r.precision));
            row.put("rec", String.format("%.2f", r.recall));
            row.put("f1", String.format("%.2f", r.f1));
            row.put("t", r.inferenceMs);
            rows.add(row);
        }
        ClassifierResult best = best(results);
        setCard("bestAcc", String.format("%.2f%%", best.accuracy));
        setCard("bestF1", String.format("%.2f%%", best.f1));
        setCard("bestModel", best.classifierName);

        List<Map<String,Object>> per = new ArrayList<>();
        int[][] cm = best.confusionMatrix;
        String[] lbl = DataLoader.LABELS;
        for (int c = 0; c < lbl.length; c++) {
            int tp = cm[c][c], fp = 0, fn = 0;
            for (int j = 0; j < lbl.length; j++) { if (j != c) { fp += cm[j][c]; fn += cm[c][j]; } }
            int sup = tp + fn;
            double p  = (tp+fp) > 0 ? (double)tp/(tp+fp)*100 : 0;
            double r2 = (tp+fn) > 0 ? (double)tp/(tp+fn)*100 : 0;
            double f  = (p+r2) > 0  ? 2*p*r2/(p+r2)          : 0;
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("e", lbl[c]); m.put("p", String.format("%.2f", p));
            m.put("r", String.format("%.2f", r2)); m.put("f", String.format("%.2f", f)); m.put("s", sup);
            per.add(m);
        }
        List<Map<String,Object>> chart = new ArrayList<>();
        for (ClassifierResult r : results) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("c", r.classifierName); m.put("m", r.featureMode);
            m.put("acc", r.accuracy); m.put("lbl", String.format("%.0f", r.accuracy));
            chart.add(m);
        }
        Map<String,Object> conf = new LinkedHashMap<>();
        conf.put("name", best.classifierName); conf.put("mode", best.featureMode);
        conf.put("acc", String.format("%.2f%%", best.accuracy));
        conf.put("f1", String.format("%.2f%%", best.f1));
        conf.put("cm", cm);

        Map<String,Object> v = new LinkedHashMap<>();
        v.put("rows", rows); v.put("perClass", per); v.put("chart", chart); v.put("conf", conf);
        view = v;
        hub.send("results", v);
    }

    // ── Live prediction: same Preprocessor → buildVector → predict path ──────
    /** @return predicted label index for every [classifier*6+mode], or null if no model trained yet. */
    public int[] predictAll(String text) {
        Predictor[][] pm = models; FeatureExtractor f = fe;
        if (pm == null || f == null) return null;
        Tweet tw = new Tweet(0, text, DataLoader.LABELS[0]);
        new Preprocessor().preprocess(Collections.singletonList(tw));
        int[] out = new int[5 * MODES.length];
        for (int mi = 0; mi < MODES.length; mi++) {
            double[] v;
            try { v = f.buildVector(tw, MODES[mi]); } catch (Exception e) { v = null; }
            for (int ci = 0; ci < 5; ci++) {
                int p = -1;
                if (v != null) try { p = pm[ci][mi].predict(v, tw.getLemmas()); } catch (Exception ignored) {}
                out[ci * MODES.length + mi] = p;
            }
        }
        return out;
    }
}
