package twitter.dataset;

import java.util.*;

public class Tweet {
    private final int    id;
    private final String rawText;
    private final String label;

    private String       cleanText = "";
    private List<String> tokens    = new ArrayList<>();
    private List<String> lemmas    = new ArrayList<>();

    private double[] featureVector;

    public Tweet(int id, String rawText, String label) {
        this.id      = id;
        this.rawText = rawText.trim();
        this.label   = label.trim();
    }

    public int    getId()      { return id; }
    public String getRawText() { return rawText; }
    public String getLabel()   { return label; }

    public String       getCleanText()            { return cleanText; }
    public void         setCleanText(String t)    { this.cleanText = t; }
    public List<String> getTokens()               { return tokens; }
    public void         setTokens(List<String> t) { this.tokens = t; }
    public List<String> getLemmas()               { return lemmas; }
    public void         setLemmas(List<String> l) { this.lemmas = l; }

    public double[] getFeatureVector()           { return featureVector; }
    public void     setFeatureVector(double[] v) { this.featureVector = v; }

    @Override public String toString() {
        return String.format("[%4d] %-10s | \"%s\"", id, label,
            rawText.length() > 65 ? rawText.substring(0,65)+"..." : rawText);
    }
}
