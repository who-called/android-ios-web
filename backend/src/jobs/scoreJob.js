/**
 * Batch re-scoring / materialization job.
 *
 * Produces the unified `numbers` row (what users see) from ALL evidence:
 *   - our raw `reports`  (time decay + device reputation)
 *   - `sia_seed`         (external aggregates → decaying prior, see siaPrior)
 *   - ARCEP `patterns`   (applied as a lookup-time overlay elsewhere)
 *
 * Provenance is kept in `numbers.source` (sia | community | mixed) for internal
 * use only — the API never exposes it, so SIA-sourced risk looks like ours.
 *
 * Displayed counts use "option B": SIA pos/neg are folded into the shown report
 * counts so the DB never looks empty at launch.
 *
 *   - `runScoreJob()` is the reusable entry point (used by the scheduler).
 *   - Run directly (`npm run score`) for a one-shot execution.
 */
import { fileURLToPath } from "node:url";
import { prisma } from "../db.js";
import { config } from "../config.js";
import { scoreFromReports, siaPrior } from "../scoring.js";
import { reportsWithWeights } from "../reportWeights.js";
import { categoryFromSiaId } from "../categories.js";

const cap = (n) => Math.min(Math.max(n ?? 0, 0), config.sia.maxCount);

/** Upsert a batch of materialized rows into `numbers` in one statement. */
async function flushNumbers(rows) {
  if (rows.length === 0) return;
  const values = [];
  const params = [];
  rows.forEach((r, i) => {
    const b = i * 7;
    values.push(`($${b + 1},$${b + 2},$${b + 3},$${b + 4},$${b + 5},$${b + 6},$${b + 7},now(),now())`);
    params.push(r.phone, r.spamScore, r.category, r.source, r.status, r.spam, r.legit);
  });
  // first_reported_at keeps its existing value on conflict; updated_at is set
  // here because the raw insert bypasses Prisma's @updatedAt.
  await prisma.$executeRawUnsafe(
    `INSERT INTO numbers
       (phone, spam_score, category, source, status, report_count_spam, report_count_legit, last_reported_at, updated_at)
     VALUES ${values.join(",")}
     ON CONFLICT (phone) DO UPDATE SET
       spam_score = EXCLUDED.spam_score,
       category = EXCLUDED.category,
       source = EXCLUDED.source,
       status = EXCLUDED.status,
       report_count_spam = EXCLUDED.report_count_spam,
       report_count_legit = EXCLUDED.report_count_legit,
       updated_at = now()`,
    ...params
  );
}

/** Recompute / materialize all numbers. Returns the count written. */
export async function runScoreJob({ now = Date.now(), batchSize = 2000 } = {}) {
  let written = 0;

  // Snapshot the phones currently in the apps' interest (block/warn) so we can
  // tombstone any that get downgraded or deleted this run.
  const beforeRows = await prisma.number.findMany({
    where: { status: { in: ["block", "warn"] } },
    select: { phone: true },
  });
  const wasActive = new Set(beforeRows.map((r) => r.phone));

  // Phones that have at least one of our own reports → full path.
  const reportRows = await prisma.report.findMany({
    distinct: ["phone"],
    select: { phone: true },
  });
  const reportPhones = new Set(reportRows.map((r) => r.phone));

  let batch = [];
  const push = async (row) => {
    batch.push(row);
    if (batch.length >= batchSize) {
      await flushNumbers(batch);
      written += batch.length;
      batch = [];
    }
  };

  // 1) Report-backed numbers (community, possibly merged with a SIA prior).
  for (const phone of reportPhones) {
    const reports = await reportsWithWeights(prisma, phone);
    const seed = await prisma.siaSeed.findUnique({ where: { phone } });
    const prior = seed ? siaPrior(seed, { now }) : undefined;
    const scored = scoreFromReports(reports, { now, prior });
    await push({
      phone,
      spamScore: scored.score,
      status: scored.status,
      category: resolveCategory(reports, seed),
      source: seed ? "mixed" : "community",
      spam: scored.spam + (seed ? cap(seed.neg) : 0), // option B
      legit: scored.legit + (seed ? cap(seed.pos) : 0),
    });
  }

  // 2) Seed-only numbers (no reports yet): score from the prior alone. Paginated
  //    by phone cursor so 11M rows stream without loading them all at once.
  let cursor = undefined;
  for (;;) {
    const page = await prisma.siaSeed.findMany({
      take: 10_000,
      ...(cursor ? { skip: 1, cursor: { phone: cursor } } : {}),
      orderBy: { phone: "asc" },
      select: { phone: true, pos: true, neg: true, neu: true, categoryId: true, importedAt: true },
    });
    if (page.length === 0) break;
    cursor = page[page.length - 1].phone;
    for (const seed of page) {
      if (reportPhones.has(seed.phone)) continue; // already done in pass 1
      const prior = siaPrior(seed, { now });
      const scored = scoreFromReports([], { now, prior });
      await push({
        phone: seed.phone,
        spamScore: scored.score,
        status: scored.status,
        category: categoryFromSiaId(seed.categoryId),
        source: "sia",
        spam: cap(seed.neg),
        legit: cap(seed.pos),
      });
    }
  }

  await flushNumbers(batch);
  written += batch.length;

  await reconcileTombstones(wasActive, now);
  return written;
}

/**
 * Remove numbers with no supporting evidence (seed gone AND no reports), and
 * record tombstones for every phone that left the apps' block/warn interest
 * (deleted OR downgraded to allow/unknown) so clients drop them from cache.
 */
async function reconcileTombstones(wasActive, now) {
  // 1) Delete orphans: in numbers, but no seed and no reports anymore.
  await prisma.$executeRaw`
    DELETE FROM numbers n
    WHERE NOT EXISTS (SELECT 1 FROM sia_seed s WHERE s.phone = n.phone)
      AND NOT EXISTS (SELECT 1 FROM reports r WHERE r.phone = n.phone)`;

  // 2) Phones still active (block/warn) after the run.
  const afterRows = await prisma.number.findMany({
    where: { status: { in: ["block", "warn"] } },
    select: { phone: true },
  });
  const stillActive = new Set(afterRows.map((r) => r.phone));

  // 3) Tombstone everything that was active and no longer is.
  const removed = [...wasActive].filter((p) => !stillActive.has(p));
  const at = new Date(now);
  for (let i = 0; i < removed.length; i += 5000) {
    await prisma.numberTombstone.createMany({
      data: removed.slice(i, i + 5000).map((phone) => ({ phone, removedAt: at })),
      skipDuplicates: true,
    });
  }

  // 4) Re-arm: a phone that became active again must not keep a stale tombstone,
  //    else a client syncing from an old cursor would delete then re-add it.
  await prisma.$executeRaw`
    DELETE FROM number_tombstones t
    WHERE EXISTS (
      SELECT 1 FROM numbers n
      WHERE n.phone = t.phone AND n.status IN ('block','warn'))`;
}

/** ARCEP overlay handled at lookup; here: our reports' majority category, else SIA's. */
function resolveCategory(reports, seed) {
  const counts = {};
  for (const r of reports) {
    if (r.category) counts[r.category] = (counts[r.category] ?? 0) + 1;
  }
  const top = Object.entries(counts).sort((a, b) => b[1] - a[1])[0];
  return top?.[0] ?? (seed ? categoryFromSiaId(seed.categoryId) : "unknown");
}

// One-shot execution when invoked directly (e.g. `node src/jobs/scoreJob.js`).
const isMain = process.argv[1] === fileURLToPath(import.meta.url);
if (isMain) {
  runScoreJob()
    .then((n) => {
      console.log(`Materialized ${n} numbers.`);
      return prisma.$disconnect();
    })
    .catch(async (e) => {
      console.error(e);
      await prisma.$disconnect();
      process.exit(1);
    });
}
