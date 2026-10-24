package twitter.classifiers;

import twitter.dataset.DataLoader;
import twitter.dataset.Tweet;
import twitter.features.EmotionLexicon;
import twitter.features.FeatureExtractor;
import java.util.*;

/**
 * ANN: Input → Dense(128,ReLU) → Dropout(0.3) → Dense(64,ReLU) → Dense(6,Softmax)
 * Mini-batch SGD + Adam-like moment updates + lexicon-guided output.
 */
public class ANN {

    private int     inputDim, h1=128, h2=64, outputDim;
    private double[][] W1,W2,W3;
    private double[]   b1,b2,b3;
    // Adam moments
    private double[][] mW1,mW2,mW3, vW1,vW2,vW3;
    private double[]   mb1,mb2,mb3, vb1,vb2,vb3;
    private double lr=0.001, beta1=0.9, beta2=0.999, eps=1e-8, dropout=0.3;
    private int epochs=12, batch=32;
    private Random rand=new Random(7);
    private int t=0;

    public ClassifierResult train(List<Tweet> train, List<Tweet> test,
                                   FeatureExtractor fe, String mode) {
        inputDim  = fe.buildVector(train.get(0), mode).length;
        outputDim = DataLoader.LABELS.length;
        initWeights();

        long t0 = System.currentTimeMillis();
        List<Integer> idx = new ArrayList<>();
        for (int i=0;i<train.size();i++) idx.add(i);

        for (int ep=0;ep<epochs;ep++) {
            Collections.shuffle(idx, rand);
            for (int s=0;s<train.size();s+=batch) {
                int e = Math.min(s+batch, train.size());
                double[][] gW1=new double[h1][inputDim], gW2=new double[h2][h1], gW3=new double[outputDim][h2];
                double[] gb1=new double[h1], gb2=new double[h2], gb3=new double[outputDim];

                for (int i=s;i<e;i++) {
                    Tweet tw = train.get(idx.get(i));
                    double[] x = fe.buildVector(tw, mode);
                    int label  = DataLoader.labelIndex(tw.getLabel());

                    // Forward
                    double[] a1 = relu(addB(mul(W1,x),b1));
                    double[] d1 = dropoutMask(a1, dropout);
                    double[] a2 = relu(addB(mul(W2,d1),b2));
                    double[] d2 = dropoutMask(a2, dropout);
                    double[] out= softmax(addB(mul(W3,d2),b3));

                    double[] dO = out.clone(); dO[label]-=1;

                    // Backprop W3
                    for (int c=0;c<outputDim;c++) { for(int j=0;j<h2;j++) gW3[c][j]+=dO[c]*d2[j]; gb3[c]+=dO[c]; }

                    double[] dA2=new double[h2];
                    for(int j=0;j<h2;j++) { for(int c=0;c<outputDim;c++) dA2[j]+=dO[c]*W3[c][j]; dA2[j]*=(a2[j]>0?1:0); }
                    for(int j=0;j<h2;j++) { for(int k=0;k<h1;k++) gW2[j][k]+=dA2[j]*d1[k]; gb2[j]+=dA2[j]; }

                    double[] dA1=new double[h1];
                    for(int k=0;k<h1;k++) { for(int j=0;j<h2;j++) dA1[k]+=dA2[j]*W2[j][k]; dA1[k]*=(a1[k]>0?1:0); }
                    for(int k=0;k<h1;k++) { for(int l=0;l<Math.min(x.length,inputDim);l++) gW1[k][l]+=dA1[k]*x[l]; gb1[k]+=dA1[k]; }
                }
                t++;
                int n=e-s;
                adamUpdate(W1,gW1,mW1,vW1,n); adamUpdate(b1,gb1,mb1,vb1,n);
                adamUpdate(W2,gW2,mW2,vW2,n); adamUpdate(b2,gb2,mb2,vb2,n);
                adamUpdate(W3,gW3,mW3,vW3,n); adamUpdate(b3,gb3,mb3,vb3,n);
            }
        }

        ClassifierResult res = new ClassifierResult();
        res.classifierName = "ANN (128-64-6)";
        res.featureMode    = mode;
        res.trainTimeMs    = System.currentTimeMillis()-t0;
        evaluate(test, fe, mode, res);
        return res;
    }

    public int predict(double[] x, List<String> lemmas) {
        double[] a1 = relu(addB(mul(W1,x),b1));
        double[] a2 = relu(addB(mul(W2,a1),b2));
        double[] out= softmax(addB(mul(W3,a2),b3));
        // Lexicon boost
        double[] lex=EmotionLexicon.score(lemmas);
        double[] cnt=EmotionLexicon.matchCount(lemmas);
        for(int c=0;c<outputDim;c++){out[c]+=lex[c]*0.4; out[c]+=cnt[c]*0.05;}
        int best=0; for(int i=1;i<outputDim;i++) if(out[i]>out[best]) best=i;
        return best;
    }

    private void evaluate(List<Tweet> test, FeatureExtractor fe, String mode, ClassifierResult res) {
        int[][] cm=new int[DataLoader.LABELS.length][DataLoader.LABELS.length];
        long ts=System.nanoTime();
        for(Tweet tw:test) cm[DataLoader.labelIndex(tw.getLabel())][predict(fe.buildVector(tw,mode),tw.getLemmas())]++;
        res.inferenceMs=(System.nanoTime()-ts)/1_000_000; res.confusionMatrix=cm; res.testSize=test.size();
        ClassifierResult.computeMetrics(res,cm);
    }

    private void initWeights(){
        W1=xavier(h1,inputDim); b1=new double[h1]; mW1=zero(h1,inputDim); vW1=zero(h1,inputDim); mb1=new double[h1]; vb1=new double[h1];
        W2=xavier(h2,h1);       b2=new double[h2]; mW2=zero(h2,h1);       vW2=zero(h2,h1);       mb2=new double[h2]; vb2=new double[h2];
        W3=xavier(outputDim,h2);b3=new double[outputDim]; mW3=zero(outputDim,h2); vW3=zero(outputDim,h2); mb3=new double[outputDim]; vb3=new double[outputDim];
    }
    private double[][] xavier(int r,int c){double s=Math.sqrt(2.0/c);double[][] M=new double[r][c];for(int i=0;i<r;i++) for(int j=0;j<c;j++) M[i][j]=rand.nextGaussian()*s;return M;}
    private double[][] zero(int r,int c){return new double[r][c];}
    private double[] relu(double[] v){double[] r=v.clone();for(int i=0;i<r.length;i++) r[i]=Math.max(0,r[i]);return r;}
    private double[] softmax(double[] v){double m=Arrays.stream(v).max().orElse(0);double[] e=new double[v.length];double s=0;for(int i=0;i<v.length;i++){e[i]=Math.exp(v[i]-m);s+=e[i];}for(int i=0;i<e.length;i++) e[i]/=s;return e;}
    private double[] mul(double[][] M,double[] v){double[] r=new double[M.length];for(int i=0;i<M.length;i++) for(int j=0;j<Math.min(v.length,M[i].length);j++) r[i]+=M[i][j]*v[j];return r;}
    private double[] addB(double[] v,double[] b){double[] r=v.clone();for(int i=0;i<r.length;i++) r[i]+=b[i];return r;}
    private double[] dropoutMask(double[] v,double rate){double[] r=v.clone();for(int i=0;i<r.length;i++) if(rand.nextDouble()<rate) r[i]=0;else r[i]/=(1-rate);return r;}
    private void adamUpdate(double[][] W,double[][] g,double[][] m,double[][] vv,int n){
        double a=lr*Math.sqrt(1-Math.pow(beta2,t))/(1-Math.pow(beta1,t));
        for(int i=0;i<W.length;i++) for(int j=0;j<W[i].length;j++){
            double gi=g[i][j]/n; m[i][j]=beta1*m[i][j]+(1-beta1)*gi; vv[i][j]=beta2*vv[i][j]+(1-beta2)*gi*gi;
            W[i][j]-=a*m[i][j]/(Math.sqrt(vv[i][j])+eps);
        }
    }
    private void adamUpdate(double[] b,double[] g,double[] m,double[] vv,int n){
        double a=lr*Math.sqrt(1-Math.pow(beta2,t))/(1-Math.pow(beta1,t));
        for(int i=0;i<b.length;i++){double gi=g[i]/n;m[i]=beta1*m[i]+(1-beta1)*gi;vv[i]=beta2*vv[i]+(1-beta2)*gi*gi;b[i]-=a*m[i]/(Math.sqrt(vv[i])+eps);}
    }
}
