import { Router } from "express";
import { prisma } from "../db.js";
import { normalizePhone } from "../phone.js";
import { REPORT_CATEGORIES } from "../categories.js";
import { config } from "../config.js";
import { scoreFromReports, siaPrior } from "../scoring.js";
import { reportsWithWeights } from "../reportWeights.js";
import {
  confidenceLevel,
  findMatchingPattern,
  presentationSource,
} from "../lookupPresentation.js";

export const lookupRouter = Router();

const DAY = 86_400_000;

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

  const [number, patterns] = await Promise.all([
    prisma.number.findUnique({ where: { phone } }),
    prisma.pattern.findMany({
      select: { pattern: true, status: true, category: true, source: true, name: true },
    }),
  ]);
  const officialPattern = findMatchingPattern(
    phone,
    patterns.filter((pattern) => pattern.source === "arcep"),
  );

  // All reports for frequency (spam + legit — the chart shows total activity,
  // consistent with firstReportedAt/lastReportedAt which also cover all votes).
  const allReports = number
    ? await prisma.report.findMany({
        where: { phone },
        select: { createdAt: true, vote: true, category: true },
      })
    : [];

  // Recompute confidence at lookup time so time decay and device reputation are
  // reflected immediately. The materialized Number row remains the fast list source.
  let liveScore = null;
  if (number) {
    const [weightedReports, seed] = await Promise.all([
      reportsWithWeights(prisma, phone),
      prisma.siaSeed.findUnique({ where: { phone } }),
    ]);
    const prior = seed ? siaPrior(seed) : undefined;
    if (weightedReports.length > 0 || prior) {
      liveScore = scoreFromReports(weightedReports, { prior });
    }
  }

  // Spam reports only for the reason breakdown (category is null on legit).
  const spamReports = allReports.filter((r) => r.vote === "spam");

  const now = Date.now();
  const within = (days) =>
    allReports.filter((r) => now - new Date(r.createdAt).getTime() <= days * DAY).length;

  // Community "why did this number call?" breakdown over the spam reports.
  // Counts per canonical category (see categories.js) + the dominant reason
  // (topReason) for a plain-text headline like "le plus souvent : démarchage".
  const reasons = Object.fromEntries(REPORT_CATEGORIES.map((c) => [c, 0]));
  for (const r of spamReports) {
    const key = r.category && r.category in reasons ? r.category : "unknown";
    reasons[key] += 1;
  }

  // No individual spam reports (e.g. SIA-seeded aggregate) but we still have
  // a category + count on the Number row → synthesize a catch-all entry so
  // the category breakdown is never empty when we actually know something.
  if (spamReports.length === 0 && (number?.reportCountSpam ?? 0) > 0) {
    const cat = number.category && number.category in reasons ? number.category : "unknown";
    reasons[cat] = number.reportCountSpam;
  }

  const reasonTotal = Object.values(reasons).reduce((s, c) => s + c, 0);
  const ranked = Object.entries(reasons)
    .filter(([, count]) => count > 0)
    .sort((a, b) => b[1] - a[1]);
  const top = ranked[0];
  const topReason = top
    ? { category: top[0], count: top[1], share: Math.round((top[1] / reasonTotal) * 100) }
    : null;

  const totalReports = (number?.reportCountSpam ?? 0) + (number?.reportCountLegit ?? 0);
  const hasStatisticalEvidence = !!number && (totalReports > 0 || liveScore !== null);
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
    ? (liveScore?.status ?? number.status)
    : (officialPattern?.status ?? number?.status ?? "unknown");
  const spamScore = hasStatisticalEvidence
    ? (liveScore?.score ?? number.spamScore)
    : officialOnly
      ? (number?.spamScore ?? (status === "block" ? 100 : 70))
      : 0;
  const category = number?.category && number.category !== "unknown"
    ? number.category
    : (officialPattern?.category ?? "unknown");

  return res.json({
    phone,
    spamScore,
    status,
    confidence,
    confidenceLevel: confidenceLevel(confidence, officialOnly),
    source,
    category,
    reportCountSpam: number?.reportCountSpam ?? 0,
    reportCountLegit: number?.reportCountLegit ?? 0,
    frequency: {
      last24h: within(1),
      last7d: within(7),
      last30d: within(30),
      last1y: within(365),
    },
    reasons,
    topReason,
    firstReportedAt: allReports.length > 0 ? number?.firstReportedAt : null,
    lastReportedAt: allReports.length > 0 ? number?.lastReportedAt : null,
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
