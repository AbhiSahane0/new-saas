package twitter.preprocessing;

import twitter.dataset.Tweet;
import java.util.*;

public class Preprocessor {

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
        "i","me","my","myself","we","our","ours","ourselves","you","your","yours",
        "yourself","he","him","his","himself","she","her","hers","herself","it","its",
        "itself","they","them","their","theirs","themselves","what","which","who","whom",
        "this","that","these","those","am","is","are","was","were","be","been","being",
        "have","has","had","having","do","does","did","doing","a","an","the","and","but",
        "if","or","because","as","until","while","of","at","by","for","with","about",
        "against","between","into","through","during","before","after","above","below",
        "to","from","up","down","in","out","on","off","over","under","again","further",
        "then","once","here","there","when","where","why","how","all","both","each",
        "few","more","most","other","some","such","no","nor","not","only","own","same",
        "so","than","too","very","s","t","can","will","just","don","should","now","d",
        "ll","m","o","re","ve","y","ain","aren","couldn","didn","doesn","hadn","hasn",
        "haven","isn","ma","mightn","mustn","needn","shan","shouldn","wasn","weren",
        "won","wouldn","also","get","got","go","would","could","one","even","back",
        "way","well","still","feel","feeling","felt","make","think","know","want",
        "need","come","take","like","look","time","good","new","first","last","long",
        "great","little","right","big","old","high","way","day","man","hand","part",
        "place","case","week","company","different","group","problem","fact","http",
        "www","href","rel","bookmark","april","class","url","fn","com"
    ));

    // Comprehensive emotion-aware lemma dictionary
    private static final Map<String,String> LEMMA = new HashMap<>();
    static {
        // Sadness cluster
        put("ashamed","ashamed"); put("shame","shame"); put("shameful","shame");
        put("devastated","devastated"); put("devastating","devastated"); put("devastate","devastated");
        put("pathetic","pathetic"); put("rejected","rejected"); put("rejection","rejected");
        put("heartbroken","heartbroken"); put("depressed","depressed"); put("depression","depressed");
        put("miserable","miserable"); put("misery","miserable"); put("miserably","miserable");
        put("lonely","lonely"); put("loneliness","lonely"); put("alone","alone");
        put("grief","grief"); put("grieving","grief"); put("grieve","grief");
        put("mourning","mourning"); put("mourn","mourning"); put("mournful","mourning");
        put("sorrow","sorrow"); put("sorrowful","sorrow"); put("sorrowfully","sorrow");
        put("hopeless","hopeless"); put("hopelessness","hopeless");
        put("unhappy","unhappy"); put("unhappiness","unhappy");
        put("unfortunate","unfortunate"); put("unfortunately","unfortunate");
        put("crying","crying"); put("cried","crying"); put("cries","crying"); put("cry","crying");
        put("tears","tears"); put("tearful","tears"); put("weeping","weeping"); put("wept","weeping");
        put("lethargic","lethargic"); put("lethargy","lethargic");
        put("unwelcome","unwelcome"); put("lost","lost"); put("defeated","defeated");
        put("agitated","agitated"); put("pain","pain"); put("painful","pain"); put("ache","ache");
        put("heartache","ache"); put("empty","empty"); put("emptiness","empty");
        put("dissatisfied","dissatisfied"); put("dissatisfy","dissatisfied");
        put("disappointed","disappointed"); put("disappointment","disappointed");
        put("regret","regret"); put("regretful","regret"); put("regrets","regret");
        put("nostalgic","nostalgic"); put("nostalgia","nostalgic");
        put("tragic","tragic"); put("tragedy","tragic");
        put("gloomy","gloomy"); put("gloom","gloomy"); put("bleak","bleak");

        // Joy cluster
        put("happy","happy"); put("happiness","happy"); put("happily","happy");
        put("happier","happy"); put("happiest","happy");
        put("joyful","joyful"); put("joy","joyful"); put("joyous","joyful"); put("joyfully","joyful");
        put("excited","excited"); put("exciting","excited"); put("excite","excited");
        put("elated","elated"); put("elation","elated");
        put("ecstatic","ecstatic"); put("ecstasy","ecstatic"); put("ecstatically","ecstatic");
        put("thrilled","thrilled"); put("thrill","thrilled"); put("thrilling","thrilled");
        put("delighted","delighted"); put("delight","delighted"); put("delightful","delighted");
        put("cheerful","cheerful"); put("cheer","cheerful"); put("cheered","cheerful");
        put("pleased","pleased"); put("pleasure","pleased"); put("pleasant","pleasant");
        put("satisfied","satisfied"); put("satisfaction","satisfied");
        put("grateful","grateful"); put("gratitude","grateful"); put("thankful","thankful");
        put("content","content"); put("contented","content"); put("contentment","content");
        put("optimistic","optimistic"); put("optimism","optimistic");
        put("hopeful","hopeful"); put("hope","hopeful"); put("hoping","hopeful");
        put("energetic","energetic"); put("energy","energetic"); put("vital","vital");
        put("positive","positive"); put("wonderful","wonderful"); put("amazing","amazing");
        put("fantastic","fantastic"); put("great","great"); put("brilliant","brilliant");
        put("laughing","laughing"); put("laugh","laughing"); put("laughter","laughing");
        put("smile","smile"); put("smiling","smile"); put("smiled","smile"); put("smiles","smile");
        put("fun","fun"); put("funny","funny"); put("enjoyed","enjoyed"); put("enjoy","enjoyed");
        put("enjoyment","enjoyed"); put("blissful","blissful"); put("bliss","blissful");
        put("proud","proud"); put("pride","proud"); put("proudly","proud");
        put("confident","confident"); put("confidence","confident");
        put("mellow","mellow"); put("peaceful","peaceful"); put("peace","peaceful");
        put("calm","calm"); put("relaxed","relaxed"); put("comfortable","comfortable");
        put("innocent","innocent"); put("free","free"); put("liberated","liberated");
        put("passionate","passionate"); put("passion","passionate");
        put("inspired","inspired"); put("inspire","inspired"); put("inspiration","inspired");
        put("motivated","motivated"); put("motivation","motivated");

        // Anger cluster
        put("angry","angry"); put("anger","angry"); put("angrily","angry"); put("angered","angry");
        put("furious","furious"); put("fury","furious"); put("furiously","furious");
        put("rage","rage"); put("raging","rage"); put("enraged","rage");
        put("frustrated","frustrated"); put("frustration","frustrated"); put("frustrating","frustrated");
        put("irritated","irritated"); put("irritation","irritated"); put("irritating","irritated");
        put("annoyed","annoyed"); put("annoyance","annoyed"); put("annoying","annoyed");
        put("bothered","bothered"); put("bother","bothered");
        put("offended","offended"); put("offensive","offended"); put("offend","offended");
        put("insulted","insulted"); put("insult","insulted"); put("insulting","insulted");
        put("disgusted","disgusted"); put("disgust","disgusted"); put("disgusting","disgusted");
        put("outraged","outraged"); put("outrage","outraged");
        put("infuriated","infuriated"); put("infuriate","infuriated");
        put("hostile","hostile"); put("hostility","hostile");
        put("resent","resentful"); put("resentful","resentful"); put("resentment","resentful");
        put("bitter","bitter"); put("bitterness","bitter"); put("bitterly","bitter");
        put("violent","violent"); put("violence","violent");
        put("hateful","hateful"); put("hate","hateful"); put("hated","hateful"); put("hating","hateful");
        put("dangerous","dangerous"); put("threaten","threatened"); put("threatened","threatened");
        put("betrayed","betrayed"); put("betrayal","betrayed");

        // Fear cluster
        put("scared","scared"); put("scare","scared"); put("scaring","scared"); put("scary","scared");
        put("frightened","frightened"); put("fright","frightened"); put("frighten","frightened");
        put("afraid","afraid");
        put("fearful","fearful"); put("fear","fearful"); put("feared","fearful");
        put("terrified","terrified"); put("terrify","terrified"); put("terrifying","terrified");
        put("horror","horror"); put("horrified","horror"); put("horrifying","horror");
        put("anxious","anxious"); put("anxiety","anxious"); put("anxiously","anxious");
        put("worried","worried"); put("worry","worried"); put("worrying","worried"); put("worries","worried");
        put("nervous","nervous"); put("nervousness","nervous"); put("nervously","nervous");
        put("panicked","panicked"); put("panic","panicked"); put("panicking","panicked");
        put("vulnerable","vulnerable"); put("vulnerability","vulnerable");
        put("uncertain","uncertain"); put("uncertainty","uncertain"); put("unsure","unsure");
        put("insecure","insecure"); put("insecurity","insecure");
        put("uncomfortable","uncomfortable"); put("uneasy","uneasy"); put("unease","uneasy");
        put("shaky","shaky"); put("trembling","trembling"); put("tremble","trembling");
        put("restless","restless"); put("restlessness","restless");
        put("apprehensive","apprehensive"); put("apprehension","apprehensive");
        put("dread","dread"); put("dreading","dread"); put("dreaded","dread");
        put("timid","timid"); put("helpless","helpless"); put("helplessness","helpless");

        // Love cluster
        put("love","love"); put("loved","love"); put("loving","love"); put("loves","love");
        put("beloved","beloved"); put("adore","adore"); put("adored","adore"); put("adoring","adore");
        put("affection","affection"); put("affectionate","affection"); put("fond","fond"); put("fondness","fond");
        put("caring","caring"); put("care","caring"); put("cared","caring");
        put("tender","tender"); put("tenderness","tender"); put("tenderly","tender");
        put("romantic","romantic"); put("romance","romantic");
        put("cherish","cherish"); put("cherished","cherish"); put("cherishing","cherish");
        put("devoted","devoted"); put("devotion","devoted"); put("devote","devoted");
        put("attached","attached"); put("attachment","attached");
        put("intimate","intimate"); put("intimacy","intimate");
        put("warm","warm"); put("warmth","warm"); put("warmly","warm");
        put("gentle","gentle"); put("gently","gentle"); put("gentleness","gentle");
        put("compassionate","compassionate"); put("compassion","compassionate");
        put("empathy","empathetic"); put("empathetic","empathetic");
        put("kind","kind"); put("kindness","kind"); put("kindly","kind");
        put("generous","generous"); put("generosity","generous");
        put("supportive","supportive"); put("support","supportive");
        put("connected","connected"); put("connection","connected");
        put("longing","longing"); put("miss","miss"); put("missing","miss");
        put("sincere","sincere"); put("sincerely","sincere"); put("sincerity","sincere");
        put("passionate","passionate");

        // Surprise cluster
        put("surprised","surprised"); put("surprise","surprised"); put("surprising","surprised");
        put("astonished","astonished"); put("astonish","astonished"); put("astonishing","astonished");
        put("amazed","amazed"); put("amaze","amazed"); put("amazing","amazed");
        put("shocked","shocked"); put("shock","shocked"); put("shocking","shocked");
        put("stunned","stunned"); put("stun","stunned"); put("stunning","stunned");
        put("startled","startled"); put("startle","startled");
        put("dazed","dazed"); put("daze","dazed");
        put("speechless","speechless");
        put("overwhelmed","overwhelmed"); put("overwhelm","overwhelmed"); put("overwhelming","overwhelmed");
        put("impressed","impressed"); put("impress","impressed"); put("impressive","impressed");
        put("weird","weird"); put("strange","strange"); put("strangely","strange");
        put("unexpected","unexpected"); put("unexpectedly","unexpected");
        put("bizarre","bizarre"); put("peculiar","peculiar");
    }

    private static void put(String k, String v) { LEMMA.put(k, v); }

    public void preprocess(List<Tweet> tweets) {
        for (Tweet t : tweets) {
            String clean = clean(t.getRawText());
            t.setCleanText(clean);
            List<String> tok = tokenize(clean);
            t.setTokens(tok);
            t.setLemmas(lemmatize(removeStopwords(tok)));
        }
    }

    public String clean(String text) {
        text = text.toLowerCase();
        text = text.replaceAll("https?://\\S+", " ");
        text = text.replaceAll("@\\w+", " ");
        text = text.replaceAll("[^a-z\\s]", " ");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    public List<String> tokenize(String text) {
        List<String> t = new ArrayList<>();
        for (String w : text.split("\\s+")) if (!w.isEmpty()) t.add(w);
        return t;
    }

    public List<String> removeStopwords(List<String> tokens) {
        List<String> r = new ArrayList<>();
        for (String w : tokens)
            if (!STOPWORDS.contains(w) && w.length() > 2) r.add(w);
        return r;
    }

    public List<String> lemmatize(List<String> tokens) {
        List<String> r = new ArrayList<>();
        for (String w : tokens) r.add(LEMMA.getOrDefault(w, stem(w)));
        return r;
    }

    private String stem(String w) {
        if (w.length() < 4) return w;
        if (w.endsWith("ness"))  return w.substring(0, w.length()-4);
        if (w.endsWith("ment"))  return w.substring(0, w.length()-4);
        if (w.endsWith("ful"))   return w.substring(0, w.length()-3);
        if (w.endsWith("ous"))   return w.substring(0, w.length()-3);
        if (w.endsWith("ive"))   return w.substring(0, w.length()-3);
        if (w.endsWith("ize"))   return w.substring(0, w.length()-3);
        if (w.endsWith("ise"))   return w.substring(0, w.length()-3);
        if (w.endsWith("ation")) return w.substring(0, w.length()-5) + "ate";
        if (w.endsWith("ing") && w.length() > 6) return w.substring(0, w.length()-3);
        if (w.endsWith("ies"))   return w.substring(0, w.length()-3) + "y";
        if (w.endsWith("ed") && w.length() > 5)  return w.substring(0, w.length()-2);
        if (w.endsWith("ly") && w.length() > 5)  return w.substring(0, w.length()-2);
        if (w.endsWith("er") && w.length() > 5)  return w.substring(0, w.length()-2);
        if (w.endsWith("es") && w.length() > 4)  return w.substring(0, w.length()-2);
        if (w.endsWith("s")  && w.length() > 4)  return w.substring(0, w.length()-1);
        return w;
    }

    public static Set<String> getStopwords() { return STOPWORDS; }
    public static Map<String,String> getLemmaDict() { return LEMMA; }
}
