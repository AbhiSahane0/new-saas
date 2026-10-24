package tsaas;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import twitter.classifiers.*;
import twitter.dataset.*;
import twitter.evaluation.ResultPrinter;
import twitter.features.FeatureExtractor;
import twitter.preprocessing.Preprocessor;

/** Headless copy of final_code.runProcess() steps 1-6, used only to record the desktop baseline. */
public class Baseline {
    static final String[] MODES = {"tfidf","coco","bigram","ngram","lemma+tfidf","lemma+coco"};

    public static void main(String[] a) throws Exception {
        List<ClassifierResult> results = new ArrayList<>();
        List<Tweet> all = new DataLoader().load(a[0]);
        new Preprocessor().preprocess(all);
        Collections.shuffle(all, new Random(42));
        int trSz = (int)(all.size() * 0.80);
        List<Tweet> train = all.subList(0, trSz), test = all.subList(trSz, all.size());
        FeatureExtractor fe = new FeatureExtractor();
        fe.build(train, DataLoader.LABELS);
        fe.printFeatureSummary(all);
        for (String m : MODES) {
            results.add(new NaiveBayes().train(train, test, fe, m));
            results.add(new SVM().train(train, test, fe, m));
            results.add(new ANN().train(train, test, fe, m));
            results.add(new HySimNet().train(train, test, fe, m));
            results.add(new RNN_LSTM_N().train(train, test, fe, m));
        }
        new ResultPrinter().printAllResults(results);
        try (PrintWriter w = new PrintWriter(a[1], "UTF-8")) {
            w.println("classifier,mode,accuracy,precision,recall,f1,inferenceMs,confusion");
            for (ClassifierResult r : results)
                w.printf("%s,%s,%.2f,%.2f,%.2f,%.2f,%d,%s%n", r.classifierName, r.featureMode,
                    r.accuracy, r.precision, r.recall, r.f1, r.inferenceMs, Arrays.deepToString(r.confusionMatrix).replace(",", ""));
        }
    }
}
