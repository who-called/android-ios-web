import { scoreFromReports, siaPrior, displayedReportCounts } from "./scoring.js";
import { reportsWithWeights } from "./reportWeights.js";
import { resolveCategory } from "./jobs/scoreJob.js";

/**
 * Recompute ONE number's materialized row from all its evidence (raw reports
 * with device reputation + SIA prior) and persist it.
 *
 * Shared by every path that changes a number's reports outside the batch score
 * job (new vote, RGPD device wipe, retention purge) so none of them leaves a
 * stale `status`/`spamScore` behind for up to six hours — a purged number used
 * to keep its "block" until the next job run.
 *
 * `updateMany` so a phone whose `numbers` row is already gone is a no-op.
 *
 * @param {import("@prisma/client").Prisma.TransactionClient | import("@prisma/client").PrismaClient} client
 * @param {string} phone
 * @param {{ now?: number }} [opts]
 * @returns {Promise<{phone:string, spamScore:number, status:string, spam:number, legit:number}>}
 */
export async function rescoreNumber(client, phone, { now = Date.now() } = {}) {
  const reports = await reportsWithWeights(client, phone);
  const seed = await client.siaSeed.findUnique({ where: { phone } });
  const prior = seed ? siaPrior(seed, { now }) : undefined;
  const scored = scoreFromReports(reports, { now, prior });
  const counts = displayedReportCounts(scored, seed); // option B

  await client.number.updateMany({
    where: { phone },
    data: {
      reportCountSpam: counts.spam,
      reportCountLegit: counts.legit,
      spamScore: scored.score,
      status: scored.status,
      category: resolveCategory(reports, seed),
      source: seed ? "mixed" : "community",
    },
  });

  return { phone, spamScore: scored.score, status: scored.status, ...counts };
}
