---
title: TSaaS Twitter Emotion Analysis
emoji: 📊
colorFrom: blue
colorTo: purple
sdk: docker
app_port: 7860
pinned: false
---

# TSaaS — Twitter Emotion Analysis (web port)

Web version of the NetBeans/Swing project *Design and Analysis of Cloud Deployed Intelligent Analysis on Real Time
Twitter Data (TSaaS)*. The Java logic (`twitter.dataset`, `preprocessing`, `features`, `classifiers`, `evaluation`) is
copied **unchanged**; only `twitter.ui` was replaced by a Javalin server (`src/main/java/tsaas`) and an HTML/CSS/JS UI
(`src/main/resources/public`). A **Live Bluesky** tab was added.

## Run locally
Requirements: Java 17, Maven 3.9.

```bash
mvn -q install:install-file -Dfile=libs/org.deeplearning4j.jar -DgroupId=local.phd -DartifactId=deeplearning4j-evalmetric -Dversion=1.0 -Dpackaging=jar
mvn -q -DskipTests package
java -jar target/tsaas.jar        # http://localhost:7860  (login admin / admin)
```
On start-up the server loads the bundled `dataset.txt`, runs the 6 steps once and connects to Bluesky Jetstream.
Press **Run** to repeat; **Browse Dataset** uploads a `.txt` file (`text;label`, validated with `DataLoader` rules).

## Environment variables
| Var | Default | Meaning |
|---|---|---|
| `PORT` | `7860` | HTTP port |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75` | JVM options (Docker) |

Login is fixed to `admin` / `admin`. Nothing is stored server-side: Live judgments and exports live in the browser tab.

## Docker
```bash
docker build -t tsaas .
docker run -p 7860:7860 tsaas
```

## Deploy / redeploy (Render free, Docker)
1. Push this folder to a GitHub repo (`git init && git add . && git commit -m init && git push`).
2. On render.com: **New → Blueprint** (or **Web Service**), pick the repo; `render.yaml` sets Docker runtime, free plan,
   health check `/api/health`.
3. Redeploy = push to the repo (auto-deploy) or **Manual Deploy** in the dashboard.

Free-tier notes: the service sleeps after ~15 min without traffic (the Bluesky stream stops and the models are
re-trained, ~25 s, on the next visit). The image uses ~200 MB RAM, within the 512 MB limit.
Hugging Face Docker Spaces need a paid PRO plan; the front-matter above lets the same repo run there too.

## Verification against the desktop app
`baseline/` holds two headless runs of the original pipeline. All 30 rows (accuracy, precision, recall, F1, confusion
matrix) are identical in the web app; only `Time (ms)` differs because it is wall-clock inference time.
Side-by-side screenshots: `docs/screenshots/compare/`.

## Known differences
- `Time (ms)` is machine/run dependent (also in the desktop app).
- RNN-LSTM's reported accuracy/precision/recall/F1 come from `EvaluateMetric` preset values in the original code
  (kept as is); its live predictions come from the really trained network.
- Console log is decoded as UTF-8 (Swing showed mojibake for `█`).
- Complete dialog button reads "✔  Back" as in the Java source.
- Screenshots of the Swing app were taken on macOS (Aqua look & feel, no Segoe UI), so fonts/buttons differ slightly.
