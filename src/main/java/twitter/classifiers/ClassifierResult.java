package twitter.classifiers;

public class ClassifierResult {
    public String classifierName;
    public String featureMode;
    public double accuracy, precision, recall, f1;
    public int    testSize;
    public long   trainTimeMs, inferenceMs;
    public int[][] confusionMatrix;

    public void print() {
        System.out.printf("  %-28s %-18s acc=%-8.2f prec=%-8.2f rec=%-8.2f f1=%-8.2f t=%dms%n",
            classifierName, featureMode, accuracy, precision, recall, f1, inferenceMs);
    }

    /** Macro-averaged metrics from confusion matrix */
    public static void computeMetrics(ClassifierResult res, int[][] cm) {
        int n=cm.length, total=0, correct=0;
        double pSum=0, rSum=0, f1Sum=0;
        for (int c=0;c<n;c++) {
            int tp=cm[c][c], fp=0, fn=0;
            for (int j=0;j<n;j++) {
                if (c==j) { total+=cm[c][j]; correct+=cm[c][j]; }
                else { total+=cm[c][j]; fp+=cm[j][c]; fn+=cm[c][j]; }
            }
            double p=(tp+fp)>0?(double)tp/(tp+fp):0;
            double r=(tp+fn)>0?(double)tp/(tp+fn):0;
            double f=(p+r)>0?2*p*r/(p+r):0;
            pSum+=p; rSum+=r; f1Sum+=f;
        }
        res.accuracy  = total>0?(double)correct/total*100:0;
        res.precision = pSum/n*100;
        res.recall    = rSum/n*100;
        res.f1        = f1Sum/n*100;
    }
}
