package tsaas;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import java.util.*;

public class Main {
    static final String USER = "admin", PASS = "admin";

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7860"));
        Hub hub = new Hub();
        Pipeline pipeline = new Pipeline(hub);
        pipeline.installConsole();
        Live live = new Live(hub, pipeline);

        Javalin app = Javalin.create(cfg -> {
            cfg.staticFiles.add("/public");
            cfg.http.maxRequestSize = 20_000_000L;
            cfg.jetty.modifyServletContextHandler(h -> h.getSessionHandler().setMaxInactiveInterval(7 * 24 * 3600));
        });

        app.before("/api/*", ctx -> {
            String p = ctx.path();
            if (p.equals("/api/login") || p.equals("/api/health")) return;
            if (ctx.sessionAttribute("user") == null) { ctx.status(401).json(Map.of("message", "Not logged in")); ctx.skipRemainingHandlers(); }
        });

        app.get("/api/health", ctx -> ctx.result("ok"));
        app.get("/api/me", ctx -> ctx.json(Map.of("user", String.valueOf(ctx.sessionAttribute("user")))));
        app.post("/api/login", ctx -> {
            JsonNode b = Hub.JSON.readTree(ctx.body());
            String u = b.path("username").asText(""), p = b.path("password").asText("");
            if (u.equals(USER) && p.equals(PASS)) {
                ctx.sessionAttribute("user", u);
                ctx.json(Map.of("ok", true, "message", "Login Successful!"));
            } else {
                ctx.status(401).json(Map.of("ok", false, "message", "Invalid Username or Password!"));
            }
        });
        app.post("/api/logout", ctx -> { ctx.req().getSession().invalidate(); ctx.json(Map.of("ok", true)); });

        app.post("/api/run", ctx -> {
            if (!pipeline.start()) ctx.status(409).json(Map.of("message", "A run is already in progress."));
            else ctx.json(Map.of("ok", true));
        });

        app.post("/api/dataset", ctx -> {
            UploadedFile f = ctx.uploadedFile("file");
            try {
                if (f == null) throw new IllegalArgumentException("No file received.");
                if (!f.filename().toLowerCase().endsWith(".txt")) throw new IllegalArgumentException("Only .txt files are accepted.");
                int n = pipeline.setDataset(f.filename(), f.content().readAllBytes());
                ctx.json(Map.of("ok", true, "name", f.filename(), "tweets", n));
            } catch (IllegalStateException e) {
                ctx.status(409).json(Map.of("message", e.getMessage()));
            } catch (Exception e) {
                ctx.status(400).json(Map.of("message", String.valueOf(e.getMessage())));
            }
        });

        app.post("/api/live/config", ctx -> {
            JsonNode b = Hub.JSON.readTree(ctx.body());
            live.configure(b.has("keyword") ? b.get("keyword").asText() : null,
                           b.has("paused") ? b.get("paused").asBoolean() : null);
            ctx.json(live.state());
        });

        app.sse("/api/events", client -> {
            client.keepAlive();
            client.onClose(() -> hub.remove(client));
            try {
                Hub.sendTo(client, "snapshot", Hub.JSON.writeValueAsString(pipeline.snapshot()));
                Hub.sendTo(client, "live", Hub.JSON.writeValueAsString(live.state()));
            } catch (Exception e) { System.err.println(e); }
            hub.add(client);
        });

        app.start("0.0.0.0", port);
        live.start();
    }
}
