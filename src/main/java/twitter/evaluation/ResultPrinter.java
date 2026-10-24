package twitter.evaluation;

import twitter.classifiers.ClassifierResult;
import twitter.dataset.DataLoader;
import java.util.*;

public class ResultPrinter {

    public void printAllResults(List<ClassifierResult> results) {
        header("ALL CLASSIFIER RESULTS - COMPLETE EVALUATION");

        // Master table
        System.out.println("[Master Results - All Classifiers * All Feature Modes]");
        System.out.println("  " + "-".repeat(110));
        System.out.printf("  %-28s %-18s %-10s %-11s %-10s %-10s %-10s%n",
            "Classifier","Feature Mode","Accuracy%","Precision%","Recall%","F1%","Time(ms)");
        System.out.println("  " + "-".repeat(110));
        for (ClassifierResult r : results) r.print();

        // Per classifier
        System.out.println("[Results Grouped by Classifier]");
        Map<String,List<ClassifierResult>> byClf = new LinkedHashMap<>();
        for (ClassifierResult r : results)
            byClf.computeIfAbsent(r.classifierName, k->new ArrayList<>()).add(r);
        for (Map.Entry<String,List<ClassifierResult>> e : byClf.entrySet()) {
            System.out.println("\n- " + e.getKey() + "---------------------");
            System.out.printf("  %-22s %-10s %-11s %-10s %-10s%n","Feature Mode","Accuracy%","Precision%","Recall%","F1%");
            System.out.println("  " + "-".repeat(65));
            for (ClassifierResult r : e.getValue())
                System.out.printf("  %-22s %-10.2f %-11.2f %-10.2f %-10.2f%n",
                    r.featureMode,r.accuracy,r.precision,r.recall,r.f1);
        }

        // Feature combination tables
        combo(results,"lemma+tfidf","Lemmas + TF-IDF  ×  All Classifiers");
        combo(results,"lemma+coco", "Lemmas + CoCo    ×  All Classifiers");
        combo(results,"tfidf",      "TF-IDF only      ×  All Classifiers");
        combo(results,"coco",       "CoCo only        ×  All Classifiers");
        combo(results,"bigram",     "Bigram           ×  All Classifiers");
        combo(results,"ngram",      "N-gram (Trigram) ×  All Classifiers");

        // Best model per-class
        ClassifierResult best = results.stream().max(Comparator.comparingDouble(r->r.f1)).orElse(results.get(0));
        perClass(best);
        confMatrix(best);
        featureComparison(results);
        bestSummary(results);
    }

    private void combo(List<ClassifierResult> results, String mode, String title) {
        System.out.println("-" + title + "-");
        System.out.println("-".repeat(75));
        System.out.printf("  %-28s %-10s %-11s %-10s %-10s%n","Classifier","Accuracy%","Precision%","Recall%","F1%");
        System.out.println("  " + "-".repeat(75));
        boolean found=false;
        for (ClassifierResult r : results) if (r.featureMode.equals(mode)) { r.print(); found=true; }
        if (!found) System.out.println("  (no results for: "+mode+")");
        results.stream().filter(r->r.featureMode.equals(mode))
            .max(Comparator.comparingDouble(r->r.f1))
            .ifPresent(r->System.out.printf("%nBest: %-26s Acc=%.2f%%  F1=%.2f%%%n",r.classifierName,r.accuracy,r.f1));
    }

    private void perClass(ClassifierResult res) {
        System.out.println("[Per-Class Metrics - Best Model: "+res.classifierName+" ("+res.featureMode+")]");
        System.out.println("-".repeat(65));
        System.out.printf("  %-14s %-10s %-10s %-10s %-10s%n","Emotion","Prec%","Recall%","F1%","Support");
        System.out.println("-".repeat(65));
        int[][] cm=res.confusionMatrix;
        String[] lbl=DataLoader.LABELS;
        for (int c=0;c<lbl.length;c++){
            int tp=cm[c][c],fp=0,fn=0;
            for(int j=0;j<lbl.length;j++) if(j!=c){fp+=cm[j][c];fn+=cm[c][j];}
            int sup=tp+fn;
            double p=(tp+fp)>0?(double)tp/(tp+fp)*100:0;
            double r=(tp+fn)>0?(double)tp/(tp+fn)*100:0;
            double f1=(p+r)>0?2*p*r/(p+r):0;
            System.out.printf("  %-14s %-10.2f %-10.2f %-10.2f %-10d%n",lbl[c],p,r,f1,sup);
        }
    }

    private void confMatrix(ClassifierResult res) {
        System.out.println("[Confusion Matrix - "+res.classifierName+" ("+res.featureMode+")]");
        String[] lbl=DataLoader.LABELS; int[][] cm=res.confusionMatrix;
        System.out.print("Actual\\Pred   ");
        for(String l:lbl) System.out.printf("%-12s",l);
        System.out.println(); System.out.println("-".repeat(90));
        for(int i=0;i<lbl.length;i++){
            System.out.printf("  %-14s",lbl[i]);
            for(int j=0;j<lbl.length;j++) System.out.printf("%-12s",(i==j?"["+cm[i][j]+"]":String.valueOf(cm[i][j])));
            System.out.println();
        }
    }

    private void featureComparison(List<ClassifierResult> results) {
        System.out.println("[Feature Mode Comparison - Avg Across All Classifiers]");
        System.out.println("  " + "-".repeat(68));
        System.out.printf("  %-22s %-10s %-10s %-10s %-10s%n","Feature Mode","Avg Acc%","Avg Prec%","Avg Rec%","Avg F1%");
        System.out.println("  " + "-".repeat(68));
        Map<String,double[]> s=new LinkedHashMap<>(); Map<String,Integer> n=new LinkedHashMap<>();
        for(ClassifierResult r:results){s.computeIfAbsent(r.featureMode,k->new double[4]);n.merge(r.featureMode,1,Integer::sum);double[] x=s.get(r.featureMode);x[0]+=r.accuracy;x[1]+=r.precision;x[2]+=r.recall;x[3]+=r.f1;}
        for(Map.Entry<String,double[]> e:s.entrySet()){int c=n.get(e.getKey());double[]v=e.getValue();System.out.printf("  %-22s %-10.2f %-10.2f %-10.2f %-10.2f%n",e.getKey(),v[0]/c,v[1]/c,v[2]/c,v[3]/c);}
    }

    private void bestSummary(List<ClassifierResult> results) {
        System.out.println("-".repeat(70));
        System.out.println("  BEST MODEL SUMMARY");
        System.out.println("-".repeat(70));
        ClassifierResult best=results.stream().max(Comparator.comparingDouble(r->r.f1)).orElse(results.get(0));
        System.out.printf("  Best Classifier : %s%n",  best.classifierName);
        System.out.printf("  Feature Mode    : %s%n",  best.featureMode);
        System.out.printf("  Accuracy        : %.2f%%%n",best.accuracy);
        System.out.printf("  Precision       : %.2f%%%n",best.precision);
        System.out.printf("  Recall          : %.2f%%%n",best.recall);
        System.out.printf("  F1-Score        : %.2f%%%n",best.f1);
        System.out.printf("  Inference Time  : %d ms%n", best.inferenceMs);
        System.out.printf("  Test Samples    : %d%n",    best.testSize);
        System.out.println("-".repeat(70));
        System.out.println("Top 5 Models by Accuracy:");
        results.stream().sorted((a,b)->Double.compare(b.accuracy,a.accuracy)).limit(5)
            .forEach(r->System.out.printf("  %-28s %-18s Acc=%.2f%%  F1=%.2f%%%n",
                r.classifierName,r.featureMode,r.accuracy,r.f1));
    }

    private void header(String t) {
        System.out.println("-".repeat(68)+"-");
        System.out.println("|"+String.format("%-66s",t)+"|");
        System.out.println("-".repeat(68)+"-");
    }
}
