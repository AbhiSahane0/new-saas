package tsaas;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import twitter.dataset.DataLoader;

/** Bluesky Jetstream consumer: English posts, optional keyword filter, ~1 post/sec to the UI, auto-reconnect. */
public class Live {
    static final String URL = "wss://jetstream2.us-east.bsky.network/subscribe?wantedCollections=app.bsky.feed.post";
    static final long MIN_GAP_MS = 1000;

    private final Hub hub;
    private final Pipeline pipeline;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService emitter = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2),
        r -> { Thread t = new Thread(r, "live-emit"); t.setDaemon(true); return t; }, new ThreadPoolExecutor.DiscardPolicy());
    private final Map<String,String> handles = Collections.synchronizedMap(new LinkedHashMap<>() {
        protected boolean removeEldestEntry(Map.Entry<String,String> e) { return size() > 3000; }
    });
    private final Deque<Long> emitted = new ConcurrentLinkedDeque<>();

    volatile String keyword = "";
    volatile boolean paused = true;      // stream starts only when the user presses Start
    volatile boolean started = false;
    volatile boolean connected = false;
    private volatile long cursor = 0;
    private volatile long lastEmit = 0;
    private final AtomicLong seen = new AtomicLong();

    public Live(Hub hub, Pipeline pipeline) { this.hub = hub; this.pipeline = pipeline; }

    public void start() {
        Thread t = new Thread(this::loop, "jetstream"); t.setDaemon(true); t.start();
        ScheduledExecutorService s = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread x = new Thread(r, "live-status"); x.setDaemon(true); return x; });
        s.scheduleAtFixedRate(() -> hub.send("live", state()), 2, 3, TimeUnit.SECONDS);
    }

    public Map<String,Object> state() {
        long cut = System.currentTimeMillis() - 60_000;
        while (!emitted.isEmpty() && emitted.peekFirst() < cut) emitted.pollFirst();
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("connected", connected); m.put("paused", paused); m.put("started", started); m.put("keyword", keyword);
        m.put("ppm", emitted.size()); m.put("ready", pipeline.models != null);
        return m;
    }

    public void configure(String kw, Boolean p) {
        if (kw != null) keyword = kw.trim();
        if (p != null) { paused = p; if (!p) started = true; }
        hub.send("live", state());
    }

    private void loop() {
        long backoff = 1000;
        while (true) {
            CompletableFuture<Void> closed = new CompletableFuture<>();
            long t0 = System.currentTimeMillis();
            try {
                String u = URL + (cursor > 0 ? "&cursor=" + (cursor - 5_000_000L) : "");
                WebSocket ws = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15))
                    .buildAsync(URI.create(u), new WebSocket.Listener() {
                        final StringBuilder sb = new StringBuilder();
                        @Override public void onOpen(WebSocket w) { connected = true; hub.send("live", state()); w.request(1); }
                        @Override public CompletionStage<?> onText(WebSocket w, CharSequence d, boolean last) {
                            sb.append(d);
                            if (last) { String msg = sb.toString(); sb.setLength(0); try { handle(msg); } catch (Exception ignored) {} }
                            w.request(1);
                            return null;
                        }
                        @Override public CompletionStage<?> onClose(WebSocket w, int code, String why) { closed.complete(null); return null; }
                        @Override public void onError(WebSocket w, Throwable e) { closed.complete(null); }
                    }).get(20, TimeUnit.SECONDS);
                closed.get();
            } catch (Exception e) {
                System.err.println("[live] connect error: " + e);
            }
            connected = false;
            hub.send("live", state());
            if (System.currentTimeMillis() - t0 > 30_000) backoff = 1000;
            try { Thread.sleep(backoff); } catch (InterruptedException ie) { return; }
            backoff = Math.min(backoff * 2, 30_000);
        }
    }

    private void handle(String msg) throws Exception {
        JsonNode n = Hub.JSON.readTree(msg);
        if (n.has("time_us")) cursor = n.get("time_us").asLong();
        if (paused) return;
        if (!"commit".equals(n.path("kind").asText())) return;
        JsonNode c = n.path("commit");
        if (!"create".equals(c.path("operation").asText()) || !"app.bsky.feed.post".equals(c.path("collection").asText())) return;
        JsonNode rec = c.path("record");
        boolean en = false;
        for (JsonNode l : rec.path("langs")) if (l.asText().toLowerCase().startsWith("en")) { en = true; break; }
        if (!en) return;
        String text = rec.path("text").asText("").trim();
        if (text.isEmpty()) return;
        String kw = keyword;
        if (!kw.isEmpty()) {
            String low = text.toLowerCase();
            boolean hit = false;
            for (String k : kw.toLowerCase().split("[,\\s]+")) if (!k.isEmpty() && low.contains(k)) { hit = true; break; }
            if (!hit) return;
        }
        long now = System.currentTimeMillis();
        if (now - lastEmit < MIN_GAP_MS) return;
        lastEmit = now;
        String did = n.path("did").asText(), rkey = c.path("rkey").asText();
        String created = rec.path("createdAt").asText("");
        emitter.execute(() -> emit(did, rkey, text, created));
    }

    private void emit(String did, String rkey, String text, String created) {
        try {
            String handle = handleOf(did);
            int[] preds = pipeline.predictAll(text);
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", did + "/" + rkey);
            m.put("uri", "at://" + did + "/app.bsky.feed.post/" + rkey);
            m.put("url", "https://bsky.app/profile/" + did + "/post/" + rkey);
            m.put("author", handle); m.put("text", text);
            m.put("time", created.isEmpty() ? java.time.Instant.now().toString() : created);
            m.put("preds", preds);
            emitted.addLast(System.currentTimeMillis());
            seen.incrementAndGet();
            hub.send("post", m);
        } catch (Exception e) { System.err.println("[live] emit error: " + e); }
    }

    private String handleOf(String did) {
        String h = handles.get(did);
        if (h != null) return h;
        try {
            HttpRequest rq = HttpRequest.newBuilder(URI.create(
                "https://public.api.bsky.app/xrpc/app.bsky.actor.getProfile?actor=" + did))
                .timeout(Duration.ofSeconds(3)).build();
            HttpResponse<String> rs = http.send(rq, HttpResponse.BodyHandlers.ofString());
            if (rs.statusCode() == 200) h = Hub.JSON.readTree(rs.body()).path("handle").asText(null);
        } catch (Exception ignored) {}
        if (h == null) return did;
        handles.put(did, h);
        return h;
    }

    static String[] labels() { return DataLoader.LABELS; }
}
