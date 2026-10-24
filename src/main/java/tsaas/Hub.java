package tsaas;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.sse.SseClient;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fan-out of server-sent events to every connected browser. */
public class Hub {
    public static final ObjectMapper JSON = new ObjectMapper();
    private final List<SseClient> clients = new CopyOnWriteArrayList<>();

    public void add(SseClient c)    { clients.add(c); }
    public void remove(SseClient c) { clients.remove(c); }

    public void send(String event, Object data) {
        String json;
        try { json = data instanceof String s ? JSON.writeValueAsString(s) : JSON.writeValueAsString(data); }
        catch (Exception e) { return; }
        for (SseClient c : clients) sendTo(c, event, json);
    }

    public static void sendTo(SseClient c, String event, String json) {
        synchronized (c) {
            try { c.sendEvent(event, json); } catch (Exception ignored) {}
        }
    }
}
