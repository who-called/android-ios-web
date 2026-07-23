import express from "express";
import helmet from "helmet";
import morgan from "morgan";
import { config } from "./config.js";
import { rateLimit } from "./middleware/rateLimit.js";
import { reportsRouter } from "./routes/reports.js";
import { lookupRouter } from "./routes/lookup.js";
import { trendingRouter } from "./routes/trending.js";
import { listsRouter } from "./routes/lists.js";
import { privacyRouter } from "./routes/privacy.js";
import { seoRouter } from "./routes/seo.js";
import { statsRouter } from "./routes/stats.js";
import { gameRouter } from "./routes/game.js";

const app = express();

// Behind the reverse proxy: honor X-Forwarded-For so req.ip (rate limiting)
// sees the real client IP. TRUST_PROXY=0 for bare exposure.
app.set("trust proxy", parseInt(process.env.TRUST_PROXY ?? "1", 10));

app.use(helmet());
app.use(express.json());
app.use(morgan("tiny"));

// CORS for the website's browser widget (lookup/report). Allowlist from env.
app.use((req, res, next) => {
  const origin = req.headers.origin;
  if (origin && config.corsOrigins.includes(origin)) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
    res.setHeader("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept");
  }
  if (req.method === "OPTIONS") return res.sendStatus(204);
  next();
});

// Serialize BigInt (Report.id) safely in JSON responses.
app.set("json replacer", (_key, value) =>
  typeof value === "bigint" ? value.toString() : value
);

app.get("/health", (_req, res) => res.json({ ok: true }));
app.get("/healthz", (_req, res) => res.json({ ok: true }));

// Per-IP rate limits — generous for honest clients, fatal for mass abuse.
// (/lists carries its own limits: strict on /access, generous on pages.)
app.use("/api/v1/reports", rateLimit({ windowMs: 3_600_000, max: 60 }), reportsRouter);
app.use("/api/v1/lookup", rateLimit({ windowMs: 3_600_000, max: 300 }), lookupRouter);
app.use("/api/v1/trending", rateLimit({ windowMs: 3_600_000, max: 120 }), trendingRouter);
app.use("/api/v1/lists", listsRouter);
app.use("/api/v1/privacy", rateLimit({ windowMs: 3_600_000, max: 30 }), privacyRouter);
app.use("/api/v1/seo", seoRouter);
app.use("/api/v1/stats", rateLimit({ windowMs: 3_600_000, max: 240 }), statsRouter);
app.use("/api/v1/game", rateLimit({ windowMs: 3_600_000, max: 240 }), gameRouter);

app.use((_req, res) => res.status(404).json({ error: "not_found" }));

// eslint-disable-next-line no-unused-vars
app.use((err, _req, res, _next) => {
  console.error(err);
  res.status(500).json({ error: "internal_error" });
});

app.listen(config.port, () => {
  console.log(`who-called API listening on :${config.port}`);
});
