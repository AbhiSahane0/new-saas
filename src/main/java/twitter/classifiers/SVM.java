package twitter.classifiers;

import twitter.dataset.DataLoader;
import twitter.dataset.Tweet;
import twitter.features.EmotionLexicon;
import twitter.features.FeatureExtractor;
import java.util.*;

/**
 * Linear SVM One-vs-Rest with SGD, cosine normalisation, lexicon-guided prediction.
 */
public class SVM {

    private int      numClasses, numFeatures;
    private double[][] W, bias1d;
    private double   C=1.0, lr=0.01;
    private int      epochs=15;
    private Random   rand=new Random(42);

    public ClassifierResult train(List<Tweet> train, List<Tweet> test,
                                   FeatureExtractor fe, String mode) {
        numClasses  = DataLoader.LABELS.length;
        numFeatures = fe.buildVector(train.get(0), mode).length;
        W           = new double[numClasses][numFeatures];
        bias1d      = new double[numClasses][1];

        long t0 = System.currentTimeMillis();
        List<Integer> idx = new ArrayList<>();
        for (int i=0;i<train.size();i++) idx.add(i);

        for (int ep=0;ep<epochs;ep++) {
            Collections.shuffle(idx, rand);
            double eta = lr / (1 + 0.005*ep);
            for (int i : idx) {
                Tweet tw = train.get(i);
                double[] x = l2norm(fe.buildVector(tw, mode));
                int actual  = DataLoader.labelIndex(tw.getLabel());
                for (int c=0;c<numClasses;c++) {
                    int y = (c==actual)?1:-1;
                    double score = dot(W[c],x)+bias1d[c][0];
                    if (y*score < 1) {
                        for (int j=0;j<numFeatures;j++)
                            W[c][j] = (1-eta*0.0001)*W[c][j] + eta*C*y*x[j];
                        bias1d[c][0] += eta*C*y;
                    } else {
                        for (int j=0;j<numFeatures;j++) W[c][j]*=(1-eta*0.0001);
                    }
                }
            }
        }

        ClassifierResult res = new ClassifierResult();
        res.classifierName = "SVM (Linear OvR)";
        res.featureMode    = mode;
        res.trainTimeMs    = System.currentTimeMillis()-t0;
        evaluate(test, fe, mode, res);
        return res;
    }

    public int predict(double[] x, List<String> lemmas) {
        double[] xn = l2norm(x);
        double[] scores = new double[numClasses];
        for (int c=0;c<numClasses;c++) scores[c] = dot(W[c],xn) + bias1d[c][0];
        // Lexicon boost
        double[] lex = EmotionLexicon.score(lemmas);
        double[] cnt = EmotionLexicon.matchCount(lemmas);
        for (int c=0;c<numClasses;c++) { scores[c]+=lex[c]*2.5; scores[c]+=cnt[c]*0.4; }
        int best=0; for(int i=1;i<numClasses;i++) if(scores[i]>scores[best]) best=i;
        return best;
    }

    private void evaluate(List<Tweet> test, FeatureExtractor fe, String mode, ClassifierResult res) {
        int[][] cm = new int[DataLoader.LABELS.length][DataLoader.LABELS.length];
        long ts = System.nanoTime();
        for (Tweet tw : test)
            cm[DataLoader.labelIndex(tw.getLabel())][predict(fe.buildVector(tw,mode), tw.getLemmas())]++;
        res.inferenceMs = (System.nanoTime()-ts)/1_000_000;
        res.confusionMatrix = cm; res.testSize = test.size();
        ClassifierResult.computeMetrics(res, cm);
    }

    private double dot(double[] a, double[] b) {
        double s=0; int l=Math.min(a.length,b.length); for(int i=0;i<l;i++) s+=a[i]*b[i]; return s;
    }
    private double[] l2norm(double[] v) {
        double n=1e-9; for(double x:v) n+=x*x; n=Math.sqrt(n);
        double[] r=new double[v.length]; for(int i=0;i<v.length;i++) r[i]=v[i]/n; return r;
    }
}
