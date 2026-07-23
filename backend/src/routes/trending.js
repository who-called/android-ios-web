import { Router } from "express";
import { prisma } from "../db.js";

export const trendingRouter = Router();

const DAY = 86_400_000;

/**
 * GET /api/v1/trending?window=7&limit=20
 *
 * "Numéros qui montent" — fresh spam campaigns ranked by recent velocity,
 * surfacing numbers before they become widely known. Powers the web Trends
 * page (great organic SEO: people search "0X XX qui m'a appelé") and the
 * mobile Trends screen.
 *
 * Ranking = spam reports inside the window, weighted toward the most recent
 * 24h so a number ramping up today beats a stale one. We also return the
 * dominant reason per number for a plain-text "ce que c'est" headline.
 */
trendingRouter.get("/", async (req, res) => {
  const windowDays = Math.min(
    Math.max(parseInt(String(req.query.window ?? "7"), 10) || 7, 1),
    30
  );
  const limit = Math.min(
    Math.max(parseInt(String(req.query.limit ?? "20"), 10) || 20, 1),
    50
  );

  const now = Date.now();
  const windowStart = new Date(now - windowDays * DAY);

  // Pull recent spam reports only; aggregate in memory (the window is small and
  // bounded). category is null on legit votes, so they're naturally excluded.
  const reports = await prisma.report.findMany({
    where: { vote: "spam", createdAt: { gte: windowStart } },
    select: { phone: true, category: true, createdAt: true },
  });

  // Aggregate per phone: total in-window count, last-24h count, reason tally.
  const agg = new Map();
  for (const r of reports) {
    let entry = agg.get(r.phone);
    if (!entry) {
      entry = {
        phone: r.phone,
        count: 0,
        last24h: 0,
        reasons: { telemarketing: 0, scam: 0, robocall: 0, unknown: 0 },
      };
      agg.set(r.phone, entry);
    }
    entry.count += 1;
    if (now - new Date(r.createdAt).getTime() <= DAY) entry.last24h += 1;
    const key = r.category && r.category in entry.reasons ? r.category : "unknown";
    entry.reasons[key] += 1;
  }

  if (agg.size === 0) {
    return res.json({ serverTime: new Date().toISOString(), window: windowDays, count: 0, numbers: [] });
  }

  // Velocity score: recent activity matters more. Last-24h hits count double.
  const ranked = [...agg.values()]
    .map((e) => ({ ...e, velocity: e.count + e.last24h }))
    .sort((a, b) => b.velocity - a.velocity || b.count - a.count)
    .slice(0, limit);

  // Enrich with the number's current status/score/category in one query.
  const numbers = await prisma.number.findMany({
    where: { phone: { in: ranked.map((e) => e.phone) } },
    select: { phone: true, spamScore: true, status: true, category: true, source: true },
  });
  const byPhone = new Map(numbers.map((n) => [n.phone, n]));

  const result = ranked.map((e) => {
    const n = byPhone.get(e.phone);
    const top = Object.entries(e.reasons)
      .filter(([, c]) => c > 0)
      .sort((a, b) => b[1] - a[1])[0];
    return {
      phone: e.phone,
      reportCount: e.count,
      last24h: e.last24h,
      velocity: e.velocity,
      spamScore: n?.spamScore ?? 0,
      status: n?.status ?? "unknown",
      category: n?.category ?? "unknown",
      source: n?.source ?? "community",
      topReason: top
        ? { category: top[0], count: top[1], share: Math.round((top[1] / e.count) * 100) }
        : null,
    };
  });

  return res.json({
    serverTime: new Date().toISOString(),
    window: windowDays,
    count: result.length,
    numbers: result,
  });
});
