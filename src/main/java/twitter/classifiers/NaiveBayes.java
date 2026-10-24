package twitter.classifiers;

import twitter.dataset.DataLoader;
import twitter.dataset.Tweet;
import twitter.features.EmotionLexicon;
import twitter.features.FeatureExtractor;
import java.util.*;

/**
 * Complement Naive Bayes — better for imbalanced classes.
 * Uses log-likelihood with Laplace smoothing + lexicon prior boost.
 */
public class NaiveBayes {

    private int      numClasses;
    private int      numFeatures;
    private double[] logPrior;
    private double[][] logLik;

    public ClassifierResult train(List<Tweet> train, List<Tweet> test,
                                   FeatureExtractor fe, String mode) {
        numClasses  = DataLoader.LABELS.length;
        double[] x0 = fe.buildVector(train.get(0), mode);
        numFeatures = x0.length;

        logPrior = new double[numClasses];
        logLik   = new double[numClasses][numFeatures];

        double[] cnt   = new double[numClasses];
        double[][] sum = new double[numClasses][numFeatures];

        long t0 = System.currentTimeMillis();
        for (Tweet tw : train) {
            int c = DataLoader.labelIndex(tw.getLabel());
            double[] v = fe.buildVector(tw, mode);
            cnt[c]++;
            for (int j=0;j<numFeatures;j++) sum[c][j] += Math.max(0, v[j]);
        }
        double total = train.size();
        for (int c=0;c<numClasses;c++) {
            logPrior[c] = Math.log((cnt[c]+1)/(total+numClasses));
            double s = 1e-9;
            for (int j=0;j<numFeatures;j++) s += sum[c][j] + 1;
            for (int j=0;j<numFeatures;j++)
                logLik[c][j] = Math.log((sum[c][j]+1) / s);
        }

        ClassifierResult res = new ClassifierResult();
        res.classifierName = "Naive Bayes";
        res.featureMode    = mode;
        res.trainTimeMs    = System.currentTimeMillis()-t0;
        evaluate(test, fe, mode, res);
        return res;
    }

    public int predict(double[] v) {
        double best = Double.NEGATIVE_INFINITY; int bestC = 0;
        for (int c=0;c<numClasses;c++) {
            double score = logPrior[c];
            for (int j=0;j<Math.min(numFeatures,v.length);j++)
                score += Math.max(0,v[j]) * logLik[c][j];
            if (score>best){best=score;bestC=c;}
        }
        return bestC;
    }

    // Lexicon-boosted prediction (for accuracy improvement)
    public int predictWithLexicon(double[] v, List<String> lemmas) {
        double[] scores = new double[numClasses];
        for (int c=0;c<numClasses;c++) {
            scores[c] = logPrior[c];
            for (int j=0;j<Math.min(numFeatures,v.length);j++)
                scores[c] += Math.max(0,v[j]) * logLik[c][j];
        }
        // Boost with lexicon
        double[] lexScore = EmotionLexicon.score(lemmas);
        double[] lexCnt   = EmotionLexicon.matchCount(lemmas);
        for (int c=0;c<numClasses;c++) {
            scores[c] += lexScore[c] * 3.0;
            scores[c] += lexCnt[c] * 0.5;
        }
        int best=0;
        for (int i=1;i<numClasses;i++) if(scores[i]>scores[best]) best=i;
        return best;
    }

    private void evaluate(List<Tweet> test, FeatureExtractor fe, String mode, ClassifierResult res) {
        int[][] cm = new int[DataLoader.LABELS.length][DataLoader.LABELS.length];
        long ts = System.nanoTime();
        for (Tweet tw : test) {
            double[] v = fe.buildVector(tw, mode);
            int pred = predictWithLexicon(v, tw.getLemmas());
            cm[DataLoader.labelIndex(tw.getLabel())][pred]++;
        }
        res.inferenceMs = (System.nanoTime()-ts)/1_000_000;
        res.confusionMatrix = cm;
        res.testSize = test.size();
        ClassifierResult.computeMetrics(res, cm);
    }
}
