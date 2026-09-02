/**
 * RGPD retention / purge job (run via cron / `pnpm purge`).
 *
 * Deletes raw reports older than REPORT_RETENTION_DAYS, recomputes affected
 * numbers, and removes community numbers that no longer have any reports.
 * ARCEP-sourced numbers are kept (official list, not personal reports).
 */
import { fileURLToPath } from "node:url";
import { prisma } from "../db.js";
import { config } from "../config.js";
import { rescoreNumber } from "../rescore.js";

export async function runRetention(now = Date.now()) {
  const cutoff = new Date(now - config.retention.reportDays * 86_400_000);

  // Which numbers are affected (have old reports)?
  const oldReports = await prisma.report.findMany({
    where: { createdAt: { lt: cutoff } },
    select: { phone: true },
  });
  const phones = [...new Set(oldReports.map((r) => r.phone))];

  const purged = await prisma.report.deleteMany({
    where: { createdAt: { lt: cutoff } },
  });

  let rescored = 0;
  let removed = 0;
  for (const phone of phones) {
    const spam = await prisma.report.count({ where: { phone, vote: "spam" } });
    const legit = await prisma.report.count({ where: { phone, vote: "legit" } });
    const seed = await prisma.siaSeed.findUnique({ where: { phone } });

    if (spam + legit === 0 && !seed) {
      // No reports left → drop the community number (keep ARCEP ones).
      const res = await prisma.number.deleteMany({
        where: { phone, source: "community" },
      });
      removed += res.count;
    } else {
      // Full re-score: purging the reports that held a number in block/warn
      // must release it now, not leave a stale status until the next job.
      await rescoreNumber(prisma, phone, { now });
      rescored += 1;
    }
  }

  console.log(
    `Retention: purged ${purged.count} reports, rescored ${rescored}, removed ${removed} numbers.`
  );
  return { purged: purged.count, rescored, removed };
}

const isMain = process.argv[1] === fileURLToPath(import.meta.url);
if (isMain) {
  runRetention()
    .then(() => prisma.$disconnect())
    .catch(async (e) => {
      console.error(e);
      await prisma.$disconnect();
      process.exit(1);
    });
}
