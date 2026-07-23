import { Router } from "express";
import { prisma } from "../db.js";
import { config } from "../config.js";

export const statsRouter = Router();

/**
 * GET /api/v1/stats
 *
 * Public "reassurance" stats for the apps (à la Saracroche's homepage banner):
 * a big "numéros couverts" figure, the live community count, the number of
 * ARCEP block ranges, and the freshness of the data ("dernière mise à jour").
 *
 * Honesty note: `coveredNumbers` is deliberately labelled "couverts" in the UI.
 * It sums an owner-set baseline (covered ranges/ecosystem) + the numbers covered
 * by ARCEP wildcard ranges + the real community count. We never claim these are
 * all individually "signalés" — the community figure is exposed separately.
 */
statsRouter.get("/", async (req, res) => {
  // Optional country scope (E.164 dial code) → also return that country's counts.
  const country = String(req.query.country ?? "").replace(/[^0-9]/g, "");

  const [communityCount, arcepPatternCount, lastNumber, lastPattern] =
    await Promise.all([
      prisma.number.count(),
      prisma.pattern.count(),
      prisma.number.findFirst({
        orderBy: { updatedAt: "desc" },
        select: { updatedAt: true },
      }),
      prisma.pattern.findFirst({
        orderBy: { updatedAt: "desc" },
        select: { updatedAt: true },
      }),
    ]);

  // Numbers covered by ARCEP wildcard ranges (each pattern covers a block).
  const arcepCovered = arcepPatternCount * config.stats.numbersPerArcepPattern;

  // Big "couverts" headline = baseline + ARCEP ranges + live community numbers.
  const coveredNumbers =
    config.stats.baseCoveredOffset + arcepCovered + communityCount;

  // Most recent data change across either table → "dernière mise à jour".
  const dates = [lastNumber?.updatedAt, lastPattern?.updatedAt].filter(Boolean);
  const lastUpdate =
    dates.length > 0
      ? new Date(Math.max(...dates.map((d) => new Date(d).getTime()))).toISOString()
      : null;

  // Per-country counts (what a country-scoped device actually relies on).
  let countryNumbers = null;
  let countryArcepPatternCount = null;
  if (country) {
    [countryNumbers, countryArcepPatternCount] = await Promise.all([
      prisma.number.count({ where: { phone: { startsWith: country } } }),
      prisma.pattern.count({ where: { pattern: { startsWith: country } } }),
    ]);
  }

  return res.json({
    serverTime: new Date().toISOString(),
    coveredNumbers, // big reassurance figure ("numéros couverts")
    communityCount, // honest count of crowdsourced numbers in the DB
    arcepPatternCount, // number of ARCEP block ranges
    lastUpdate, // ISO-8601 of the freshest data, or null if DB empty
    country: country || null, // echoed dial code, or null if global
    countryNumbers, // numbers for that country (null if no country)
    countryArcepPatternCount, // ARCEP ranges for that country (null if none)
  });
});

/**
 * GET /api/v1/stats/community
 * The "community shield": honest crowdsourced-action figures.
 *  - total  : all community reports ever (the shared shield)
 *  - week   : reports in the last 7 days (momentum + weekly goal)
 *  - goal   : soft weekly target (last week ×1.2, min 500, rounded) so the bar
 *             shows meaningful progress without being always full/empty.
 */
statsRouter.get("/community", async (_req, res) => {
  const now = Date.now();
  const weekAgo = new Date(now - 7 * 86_400_000);
  const twoWeeksAgo = new Date(now - 14 * 86_400_000);
  const [total, week, prevWeek] = await Promise.all([
    prisma.report.count(),
    prisma.report.count({ where: { createdAt: { gte: weekAgo } } }),
    prisma.report.count({ where: { createdAt: { gte: twoWeeksAgo, lt: weekAgo } } }),
  ]);
  const goal = Math.max(500, Math.ceil((prevWeek * 1.2 || week * 1.5 || 500) / 100) * 100);
  return res.json({ serverTime: new Date().toISOString(), total, week, goal });
});
