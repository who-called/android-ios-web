import { Router } from "express";
import { prisma } from "../db.js";
import { normalizePhone } from "../phone.js";
import { REPORT_CATEGORIES, categoryFromSiaId } from "../categories.js";
import { config } from "../config.js";
import { capSiaCount, scoreFromReports, siaPrior, displayedReportCounts } from "../scoring.js";
import { reportsWithWeights } from "../reportWeights.js";
import {
  confidenceLevel,
  evidenceTimeline,
  findMatchingPattern,
  presentationSource,
  reasonBreakdown,
} from "../lookupPresentation.js";

export const lookupRouter = Router();

/**
 * GET /api/v1/lookup/:phone
 *
 * Enriched lookup for the detail screen: score/status/category/source plus
 * report counts and frequency over rolling windows (24h / 7d / 30d / 1y).
 */
lookupRouter.get("/:phone", async (req, res) => {
  const phone = normalizePhone(req.params.phone);
  if (!phone) {
    return res.status(400).json({ error: "invalid_phone" });
  }
  const deviceId =
    typeof req.query.deviceId === "string" && req.query.deviceId.length >= 8
      ? req.query.deviceId
      : null;

  const [number, patterns, userReport, seed] = await Promise.all([
    prisma.number.findUnique({ where: { phone } }),
    prisma.pattern.findMany({
      select: { pattern: true, status: true, category: true, source: true, name: true },
    }),
    deviceId
      ? prisma.report.findFirst({
          where: { phone, deviceId },
          select: { vote: true },
        })
      : null,
    prisma.siaSeed.findUnique({ where: { phone } }),
  ]);
  const officialPattern = findMatchingPattern(
    phone,
    patterns.filter((pattern) => pattern.source === "arcep"),
  );

  // All reports for frequency (spam + legit — the chart shows total activity,
  // consistent with firstReportedAt/lastReportedAt which also cover all votes).
  const allReports =
    number || seed
      ? await prisma.report.findMany({
          where: { phone },
          select: { createdAt: true, vote: true, category: true },
        })
      : [];

  // Recompute confidence at lookup time so time decay and device reputation are
  // reflected immediately. The materialized Number row remains the fast list source.
  const prior = seed ? siaPrior(seed) : undefined;
  let liveScore = null;
  if (number || seed) {
    const weightedReports = await reportsWithWeights(prisma, phone);
    if (weightedReports.length > 0 || prior) {
      liveScore = scoreFromReports(weightedReports, { prior });
    }
  }

  // Spam reports only for the reason breakdown (category is null on legit).
  const spamReports = allReports.filter((r) => r.vote === "spam");

  const now = Date.now();

  // Displayed counts are derived here from live evidence (our reports + the SIA
  // aggregate) instead of read back from `numbers` — the materialized row is a
  // cache that any write path could leave momentarily SIA-less, which is exactly
  // how a seed-backed history used to collapse to "0 / 1" after a single vote
  // from the web or the app.
  const counts = displayedReportCounts(
    { spam: spamReports.length, legit: allReports.length - spamReports.length },
    seed,
  );
  const { frequency, firstReportedAt, lastReportedAt } = evidenceTimeline(allReports, seed, now);

  // `sia_seed.category` carries the Go ingester's own vocabulary (it still says
  // "legit"/"other"); categories.js is the single source of truth, so always
  // re-derive from the raw SIA id.
  const seedCategory = seed ? categoryFromSiaId(seed.categoryId) : null;

  // Community "why did this number call?" breakdown over the spam evidence.
  // Counts per canonical category (see categories.js) + the dominant reason
  // (topReason) for a plain-text headline like "le plus souvent : démarchage".
  const reasons = reasonBreakdown(
    REPORT_CATEGORIES,
    spamReports,
    seed ? capSiaCount(seed.neg) : 0,
    seedCategory,
  );

  const reasonTotal = Object.values(reasons).reduce((s, c) => s + c, 0);
  const ranked = Object.entries(reasons)
    .filter(([, count]) => count > 0)
    .sort((a, b) => b[1] - a[1]);
  const top = ranked[0];
  const topReason = top
    ? { category: top[0], count: top[1], share: Math.round((top[1] / reasonTotal) * 100) }
    : null;

  const totalReports = counts.spam + counts.legit;
  const hasStatisticalEvidence = !!(number || seed) && (totalReports > 0 || liveScore !== null);
  const hasOfficialPattern = officialPattern !== null || number?.source === "arcep";
  const source = presentationSource({ hasStatisticalEvidence, hasOfficialPattern });
  const officialOnly = hasOfficialPattern && !hasStatisticalEvidence;
  const fallbackConfidence = Math.min(
    100,
    Math.round((totalReports / config.scoring.minReportsForBlock) * 100),
  );
  const confidence = officialOnly
    ? null
    : hasStatisticalEvidence
      ? (liveScore?.confidence ?? fallbackConfidence)
      : 0;
  const status = hasStatisticalEvidence
    ? (liveScore?.status ?? number?.status ?? "unknown")
    : (officialPattern?.status ?? number?.status ?? "unknown");
  const spamScore = hasStatisticalEvidence
    ? (liveScore?.score ?? number?.spamScore ?? 0)
    : officialOnly
      ? (number?.spamScore ?? (status === "block" ? 100 : 70))
      : 0;
  const category = [number?.category, seedCategory, officialPattern?.category]
    .find((c) => c && c !== "unknown") ?? "unknown";

  return res.json({
    phone,
    spamScore,
    status,
    confidence,
    confidenceLevel: confidenceLevel(confidence, officialOnly),
    source,
    category,
    reportCountSpam: counts.spam,
    reportCountLegit: counts.legit,
    userVote: userReport?.vote ?? null,
    frequency,
    reasons,
    topReason,
    firstReportedAt,
    lastReportedAt,
    officialPattern: officialPattern
      ? {
          pattern: officialPattern.pattern,
          status: officialPattern.status,
          category: officialPattern.category,
          name: officialPattern.name,
        }
      : null,
  });
});
