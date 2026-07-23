/**
 * Long-running scheduler for the batch re-scoring job.
 *
 * Runs `runScoreJob` once at startup, then every SCORE_INTERVAL_MS.
 * Used by the `scoring` service in docker-compose so scores keep fading with
 * time decay even without new reports. Crash-safe: a failed run is logged and
 * the loop continues.
 */
import { prisma } from "../db.js";
import { runScoreJob } from "./scoreJob.js";
import { runRetention } from "./retention.js";

const INTERVAL_MS = parseInt(
  process.env.SCORE_INTERVAL_MS ?? String(6 * 60 * 60 * 1000), // default 6h
  10
);

// Run the RGPD purge once per day (every Nth scoring tick).
const RETENTION_EVERY = Math.max(1, Math.round(86_400_000 / INTERVAL_MS));
let tickCount = 0;

let stopping = false;

async function tick() {
  if (stopping) return;
  const startedAt = Date.now();
  try {
    const updated = await runScoreJob();
    console.log(
      `[scheduler] re-scored ${updated} numbers in ${Date.now() - startedAt}ms`
    );
    if (tickCount % RETENTION_EVERY === 0) {
      const r = await runRetention();
      console.log(`[scheduler] retention: purged ${r.purged}, removed ${r.removed}`);
    }
    tickCount += 1;
  } catch (err) {
    console.error("[scheduler] run failed:", err);
  }
}

async function main() {
  console.log(`[scheduler] starting; interval=${INTERVAL_MS}ms`);
  await tick();
  const timer = setInterval(tick, INTERVAL_MS);

  const shutdown = async (signal) => {
    console.log(`[scheduler] received ${signal}, shutting down`);
    stopping = true;
    clearInterval(timer);
    await prisma.$disconnect();
    process.exit(0);
  };
  process.on("SIGTERM", () => shutdown("SIGTERM"));
  process.on("SIGINT", () => shutdown("SIGINT"));
}

main();
