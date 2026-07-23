import { Router } from "express";
import { prisma } from "../db.js";
import { normalizePhone } from "../phone.js";
import { REPORT_CATEGORIES } from "../categories.js";

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

  const number = await prisma.number.findUnique({ where: { phone } });

  if (!number) {
    return res.json({
      phone,
      spamScore: 0,
      status: "unknown",
      reportCountSpam: 0,
      reportCountLegit: 0,
      frequency: { last24h: 0, last7d: 0, last30d: 0, last1y: 0 },
      firstReportedAt: null,
      lastReportedAt: null,
    });
  }

  // All reports for frequency (spam + legit — the chart shows total activity,
  // consistent with firstReportedAt/lastReportedAt which also cover all votes).
  const allReports = await prisma.report.findMany({
    where: { phone },
    select: { createdAt: true, vote: true, category: true },
  });

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
  if (spamReports.length === 0 && number.reportCountSpam > 0) {
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

  // `source` is intentionally NOT exposed: SIA / community / ARCEP all surface
  // as one homogeneous "who-called" entry. Dates are shown only when we have
  // real timestamped reports — SIA seed has no period, so we don't fabricate one.
  const hasOwnReports = number.source === "community" || number.source === "mixed";
  return res.json({
    phone: number.phone,
    spamScore: number.spamScore,
    status: number.status,
    category: number.category,
    reportCountSpam: number.reportCountSpam,
    reportCountLegit: number.reportCountLegit,
    frequency: {
      last24h: within(1),
      last7d: within(7),
      last30d: within(30),
      last1y: within(365),
    },
    reasons,
    topReason,
    firstReportedAt: hasOwnReports ? number.firstReportedAt : null,
    lastReportedAt: hasOwnReports ? number.lastReportedAt : null,
  });
});
