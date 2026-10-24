package twitter.dataset;

import java.io.*;
import java.util.*;

public class DataLoader {

    public static final String[] LABELS = {"joy","sadness","anger","fear","love","surprise"};

    public List<Tweet> load(String path) throws IOException {
        List<Tweet> tweets = new ArrayList<>();
        int id = 1, skipped = 0;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) { skipped++; continue; }
                int sep = line.lastIndexOf(';');
                if (sep < 1) { skipped++; continue; }
                String text  = line.substring(0, sep).trim();
                String label = line.substring(sep+1).trim().toLowerCase();
                if (text.isEmpty() || !isValid(label)) { skipped++; continue; }
                tweets.add(new Tweet(id++, text, label));
            }
        }
        printSummary(tweets, path, skipped);
        return tweets;
    }

    private boolean isValid(String l) {
        for (String x : LABELS) if (x.equals(l)) return true;
        return false;
    }

    private void printSummary(List<Tweet> tweets, String path, int skipped) {
        //System.out.println("\n" + "╔" + "═".repeat(68) + "╗");
        System.out.println("DATA UPLOAD FROM CSV/TXT — SUMMARY");
       // System.out.println("╚" + "═".repeat(68) + "╝");
        System.out.println("  Source      : " + path);
        System.out.println("  Format      : text;label (semicolon-separated)");
        System.out.printf ("  Total loaded: %d tweets | Skipped: %d%n%n", tweets.size(), skipped);

        Map<String,Integer> dist = new LinkedHashMap<>();
        for (String l : LABELS) dist.put(l, 0);
        for (Tweet t : tweets) dist.merge(t.getLabel(), 1, Integer::sum);

        System.out.println("  [Class Distribution]");
        System.out.println("  " + "-".repeat(55));
        System.out.printf("  %-12s %-8s %-8s %s%n","Label","Count","Pct%","Bar");
        System.out.println("  " + "-".repeat(55));
        for (Map.Entry<String,Integer> e : dist.entrySet()) {
            double pct = (double)e.getValue()/tweets.size()*100;
            System.out.printf("  %-12s %-8d %-8.2f %s%n",
                e.getKey(), e.getValue(), pct,
                "█".repeat((int)(pct/2)));
        }
        System.out.println("\n  [Sample Records (first 5 per class)]");
        System.out.println("  " + "-".repeat(80));
        Map<String,Integer> shown = new HashMap<>();
        for (Tweet t : tweets) {
            int c = shown.getOrDefault(t.getLabel(), 0);
            if (c >= 5) continue;
            System.out.printf("  [%4d] %-10s | %s%n", t.getId(), t.getLabel(),
                t.getRawText().length()>65 ? t.getRawText().substring(0,65)+"..." : t.getRawText());
            shown.put(t.getLabel(), c+1);
        }
    }

    public static int labelIndex(String label) {
        for (int i=0; i<LABELS.length; i++) if (LABELS[i].equals(label)) return i;
        return 0;
    }
}
