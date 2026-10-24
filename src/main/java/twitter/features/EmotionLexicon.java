package twitter.features;

import java.util.*;

/**
 * Hand-crafted emotion lexicon with per-word scores for each of 6 emotions.
 * Each entry: word → double[6] scores for [joy, sadness, anger, fear, love, surprise]
 * Scores are in [0,1]. High score = strong signal for that emotion.
 */
public class EmotionLexicon {

    // [joy, sadness, anger, fear, love, surprise]
    private static final Map<String,double[]> LEXICON = new LinkedHashMap<>();

    static {
        // ── JOY ──────────────────────────────────────────────────
        add("happy",       1.0, 0.0, 0.0, 0.0, 0.1, 0.0);
        add("happiness",   1.0, 0.0, 0.0, 0.0, 0.1, 0.0);
        add("joyful",      1.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("excited",     0.9, 0.0, 0.0, 0.1, 0.0, 0.2);
        add("ecstatic",    1.0, 0.0, 0.0, 0.0, 0.1, 0.2);
        add("thrilled",    0.9, 0.0, 0.0, 0.0, 0.0, 0.1);
        add("delighted",   0.9, 0.0, 0.0, 0.0, 0.1, 0.1);
        add("pleased",     0.8, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("cheerful",    0.9, 0.0, 0.0, 0.0, 0.1, 0.0);
        add("content",     0.7, 0.0, 0.0, 0.0, 0.1, 0.0);
        add("blissful",    1.0, 0.0, 0.0, 0.0, 0.2, 0.0);
        add("elated",      1.0, 0.0, 0.0, 0.0, 0.0, 0.1);
        add("laughing",    0.8, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("smile",       0.8, 0.0, 0.0, 0.0, 0.3, 0.0);
        add("optimistic",  0.8, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("hopeful",     0.7, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("energetic",   0.8, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("wonderful",   0.9, 0.0, 0.0, 0.0, 0.2, 0.0);
        add("fantastic",   0.9, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("brilliant",   0.8, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("positive",    0.8, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("grateful",    0.7, 0.0, 0.0, 0.0, 0.3, 0.0);
        add("satisfied",   0.7, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("enjoyed",     0.8, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("peaceful",    0.6, 0.0, 0.0, 0.0, 0.2, 0.0);
        add("calm",        0.5, 0.0, 0.0, 0.0, 0.1, 0.0);
        add("proud",       0.8, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("inspired",    0.7, 0.0, 0.0, 0.0, 0.1, 0.0);
        add("motivated",   0.7, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("vital",       0.7, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("mellow",      0.5, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("fun",         0.8, 0.0, 0.0, 0.0, 0.0, 0.1);
        add("innocent",    0.6, 0.0, 0.0, 0.0, 0.2, 0.0);
        add("free",        0.6, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("liberated",   0.7, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("joyous",      1.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        add("adventurous", 0.6, 0.0, 0.0, 0.0, 0.0, 0.1);
        add("confident",   0.7, 0.0, 0.0, 0.0, 0.0, 0.0);

        // ── SADNESS ──────────────────────────────────────────────
        add("sad",         0.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        add("sadness",     0.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        add("ashamed",     0.0, 1.0, 0.1, 0.0, 0.0, 0.0);
        add("shame",       0.0, 0.9, 0.1, 0.0, 0.0, 0.0);
        add("devastated",  0.0, 1.0, 0.1, 0.0, 0.0, 0.0);
        add("miserable",   0.0, 1.0, 0.0, 0.1, 0.0, 0.0);
        add("lonely",      0.0, 1.0, 0.0, 0.1, 0.0, 0.0);
        add("grief",       0.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        add("mourning",    0.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        add("sorrow",      0.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        add("hopeless",    0.0, 1.0, 0.0, 0.1, 0.0, 0.0);
        add("crying",      0.0, 1.0, 0.1, 0.0, 0.0, 0.0);
        add("tears",       0.0, 0.9, 0.0, 0.1, 0.0, 0.0);
        add("weeping",     0.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        add("lethargic",   0.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        add("unwelcome",   0.0, 0.9, 0.0, 0.1, 0.0, 0.0);
        add("lost",        0.0, 0.8, 0.0, 0.2, 0.0, 0.0);
        add("defeated",    0.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        add("agitated",    0.0, 0.5, 0.4, 0.1, 0.0, 0.0);
        add("pain",        0.0, 0.8, 0.1, 0.1, 0.0, 0.0);
        add("empty",       0.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        add("dissatisfied",0.0, 0.6, 0.3, 0.0, 0.0, 0.0);
        add("disappointed",0.0, 0.9, 0.1, 0.0, 0.0, 0.0);
        add("regret",      0.0, 0.9, 0.0, 0.0, 0.0, 0.0);
        add("nostalgic",   0.1, 0.6, 0.0, 0.0, 0.3, 0.0);
        add("tragic",      0.0, 1.0, 0.0, 0.1, 0.0, 0.0);
        add("gloomy",      0.0, 1.0, 0.0, 0.1, 0.0, 0.0);
        add("depressed",   0.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        add("unhappy",     0.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        add("pathetic",    0.0, 0.8, 0.0, 0.0, 0.0, 0.0);
        add("rejected",    0.0, 0.9, 0.0, 0.0, 0.0, 0.0);
        add("agony",       0.0, 1.0, 0.0, 0.1, 0.0, 0.0);
        add("heartbroken", 0.0, 1.0, 0.0, 0.0, 0.1, 0.0);
        add("alone",       0.0, 0.8, 0.0, 0.1, 0.0, 0.0);
        add("ache",        0.0, 0.9, 0.0, 0.1, 0.0, 0.0);
        add("mess",        0.0, 0.7, 0.1, 0.1, 0.0, 0.0);
        add("uninspired",  0.0, 0.8, 0.0, 0.0, 0.0, 0.0);
        add("bleak",       0.0, 0.9, 0.0, 0.1, 0.0, 0.0);

        // ── ANGER ────────────────────────────────────────────────
        add("angry",       0.0, 0.0, 1.0, 0.0, 0.0, 0.0);
        add("furious",     0.0, 0.0, 1.0, 0.0, 0.0, 0.0);
        add("rage",        0.0, 0.0, 1.0, 0.0, 0.0, 0.0);
        add("frustrated",  0.0, 0.1, 0.9, 0.0, 0.0, 0.0);
        add("irritated",   0.0, 0.0, 0.9, 0.0, 0.0, 0.0);
        add("annoyed",     0.0, 0.1, 0.8, 0.0, 0.0, 0.0);
        add("bothered",    0.0, 0.1, 0.8, 0.0, 0.0, 0.0);
        add("offended",    0.0, 0.0, 0.9, 0.0, 0.0, 0.0);
        add("insulted",    0.0, 0.0, 1.0, 0.0, 0.0, 0.0);
        add("disgusted",   0.0, 0.0, 0.9, 0.0, 0.0, 0.0);
        add("outraged",    0.0, 0.0, 1.0, 0.0, 0.0, 0.0);
        add("infuriated",  0.0, 0.0, 1.0, 0.0, 0.0, 0.0);
        add("hostile",     0.0, 0.0, 0.9, 0.1, 0.0, 0.0);
        add("resentful",   0.0, 0.2, 0.8, 0.0, 0.0, 0.0);
        add("bitter",      0.0, 0.3, 0.7, 0.0, 0.0, 0.0);
        add("violent",     0.0, 0.0, 0.9, 0.1, 0.0, 0.0);
        add("hateful",     0.0, 0.1, 0.9, 0.0, 0.0, 0.0);
        add("dangerous",   0.0, 0.0, 0.8, 0.1, 0.0, 0.0);
        add("threatened",  0.0, 0.0, 0.6, 0.4, 0.0, 0.0);
        add("betrayed",    0.0, 0.3, 0.7, 0.0, 0.0, 0.0);
        add("jealousy",    0.0, 0.2, 0.7, 0.0, 0.1, 0.0);
        add("enraged",     0.0, 0.0, 1.0, 0.0, 0.0, 0.0);
        add("dissatisfied",0.0, 0.3, 0.7, 0.0, 0.0, 0.0);
        add("aggressive",  0.0, 0.0, 0.9, 0.1, 0.0, 0.0);
        add("cruel",       0.0, 0.1, 0.9, 0.0, 0.0, 0.0);

        // ── FEAR ─────────────────────────────────────────────────
        add("scared",      0.0, 0.0, 0.0, 1.0, 0.0, 0.0);
        add("frightened",  0.0, 0.0, 0.0, 1.0, 0.0, 0.0);
        add("afraid",      0.0, 0.0, 0.0, 1.0, 0.0, 0.0);
        add("fearful",     0.0, 0.0, 0.0, 1.0, 0.0, 0.0);
        add("terrified",   0.0, 0.0, 0.0, 1.0, 0.0, 0.0);
        add("horror",      0.0, 0.1, 0.0, 0.9, 0.0, 0.0);
        add("anxious",     0.0, 0.1, 0.0, 0.9, 0.0, 0.0);
        add("worried",     0.0, 0.2, 0.0, 0.8, 0.0, 0.0);
        add("nervous",     0.0, 0.1, 0.0, 0.9, 0.0, 0.0);
        add("panicked",    0.0, 0.0, 0.0, 1.0, 0.0, 0.0);
        add("vulnerable",  0.0, 0.2, 0.0, 0.8, 0.0, 0.0);
        add("uncertain",   0.0, 0.2, 0.0, 0.8, 0.0, 0.0);
        add("insecure",    0.0, 0.2, 0.0, 0.8, 0.0, 0.0);
        add("uncomfortable",0.0,0.1, 0.0, 0.7, 0.0, 0.0);
        add("uneasy",      0.0, 0.1, 0.0, 0.8, 0.0, 0.0);
        add("shaky",       0.0, 0.0, 0.0, 0.9, 0.0, 0.0);
        add("restless",    0.0, 0.1, 0.1, 0.8, 0.0, 0.0);
        add("apprehensive",0.0, 0.0, 0.0, 1.0, 0.0, 0.0);
        add("dread",       0.0, 0.1, 0.0, 0.9, 0.0, 0.0);
        add("timid",       0.0, 0.1, 0.0, 0.9, 0.0, 0.0);
        add("helpless",    0.0, 0.4, 0.0, 0.6, 0.0, 0.0);
        add("trembling",   0.0, 0.0, 0.0, 0.9, 0.0, 0.1);
        add("alarming",    0.0, 0.0, 0.0, 0.9, 0.0, 0.1);

        // ── LOVE ─────────────────────────────────────────────────
        add("love",        0.2, 0.0, 0.0, 0.0, 1.0, 0.0);
        add("beloved",     0.1, 0.0, 0.0, 0.0, 1.0, 0.0);
        add("adore",       0.1, 0.0, 0.0, 0.0, 1.0, 0.0);
        add("affection",   0.1, 0.0, 0.0, 0.0, 1.0, 0.0);
        add("fond",        0.2, 0.0, 0.0, 0.0, 0.8, 0.0);
        add("caring",      0.1, 0.0, 0.0, 0.0, 0.9, 0.0);
        add("tender",      0.1, 0.0, 0.0, 0.0, 0.9, 0.0);
        add("romantic",    0.1, 0.0, 0.0, 0.0, 1.0, 0.0);
        add("cherish",     0.1, 0.0, 0.0, 0.0, 1.0, 0.0);
        add("devoted",     0.1, 0.0, 0.0, 0.0, 0.9, 0.0);
        add("attached",    0.1, 0.0, 0.0, 0.0, 0.8, 0.0);
        add("intimate",    0.1, 0.0, 0.0, 0.0, 0.9, 0.0);
        add("warm",        0.2, 0.0, 0.0, 0.0, 0.8, 0.0);
        add("gentle",      0.2, 0.0, 0.0, 0.0, 0.8, 0.0);
        add("compassionate",0.1,0.0, 0.0, 0.0, 0.9, 0.0);
        add("empathetic",  0.1, 0.0, 0.0, 0.0, 0.8, 0.0);
        add("kind",        0.2, 0.0, 0.0, 0.0, 0.8, 0.0);
        add("generous",    0.2, 0.0, 0.0, 0.0, 0.8, 0.0);
        add("supportive",  0.2, 0.0, 0.0, 0.0, 0.8, 0.0);
        add("connected",   0.2, 0.0, 0.0, 0.0, 0.8, 0.0);
        add("miss",        0.0, 0.3, 0.0, 0.0, 0.7, 0.0);
        add("longing",     0.0, 0.2, 0.0, 0.0, 0.8, 0.0);
        add("sincere",     0.1, 0.0, 0.0, 0.0, 0.7, 0.0);

        // ── SURPRISE ─────────────────────────────────────────────
        add("surprised",   0.2, 0.0, 0.0, 0.1, 0.0, 1.0);
        add("astonished",  0.1, 0.0, 0.0, 0.0, 0.0, 1.0);
        add("amazed",      0.3, 0.0, 0.0, 0.0, 0.0, 1.0);
        add("shocked",     0.0, 0.1, 0.0, 0.1, 0.0, 1.0);
        add("stunned",     0.0, 0.1, 0.0, 0.1, 0.0, 1.0);
        add("startled",    0.0, 0.0, 0.0, 0.2, 0.0, 0.8);
        add("dazed",       0.0, 0.0, 0.0, 0.1, 0.0, 0.9);
        add("speechless",  0.0, 0.0, 0.0, 0.0, 0.0, 1.0);
        add("overwhelmed", 0.0, 0.2, 0.0, 0.2, 0.0, 0.8);
        add("impressed",   0.3, 0.0, 0.0, 0.0, 0.1, 0.8);
        add("weird",       0.0, 0.0, 0.0, 0.1, 0.0, 0.8);
        add("strange",     0.0, 0.0, 0.0, 0.1, 0.0, 0.8);
        add("unexpected",  0.0, 0.0, 0.0, 0.1, 0.0, 0.9);
        add("bizarre",     0.0, 0.0, 0.0, 0.1, 0.0, 0.8);
        add("peculiar",    0.0, 0.0, 0.0, 0.0, 0.0, 0.8);
    }

    private static void add(String w, double joy, double sad, double ang,
                             double fear, double love, double surp) {
        LEXICON.put(w, new double[]{joy, sad, ang, fear, love, surp});
    }

    /** Returns [6] lexicon score vector for a list of lemmas. Normalised. */
    public static double[] score(List<String> lemmas) {
        double[] scores = new double[6];
        for (String w : lemmas) {
            double[] entry = LEXICON.get(w);
            if (entry != null)
                for (int i=0;i<6;i++) scores[i] += entry[i];
        }
        // L1 normalise
        double sum = 0;
        for (double s : scores) sum += s;
        if (sum > 0) for (int i=0;i<6;i++) scores[i] /= sum;
        return scores;
    }

    /** Returns count of matching lexicon words for each class */
    public static double[] matchCount(List<String> lemmas) {
        double[] cnt = new double[6];
        for (String w : lemmas) {
            double[] e = LEXICON.get(w);
            if (e != null) for (int i=0;i<6;i++) if (e[i] > 0.5) cnt[i]++;
        }
        return cnt;
    }

    public static Map<String,double[]> getLexicon() { return LEXICON; }
    public static boolean contains(String w) { return LEXICON.containsKey(w); }
}
