import { Router } from "express";
import { prisma } from "../db.js";
import { config } from "../config.js";
import { MIN_REPORTS_FOR_INDEX, isPersonalMobileFR, isArcepEligible, isIndexable } from "../seoEligibility.js";

export const seoRouter = Router();

/**
 * GET /api/v1/seo/numbers?limit=&offset=
 * Returns the numbers eligible for an indexable page (quality + non-personal).
 */
seoRouter.get("/numbers", async (req, res) => {
  const limit = Math.min(parseInt(String(req.query.limit ?? "1000"), 10), 5000);
  const offset = Math.max(parseInt(String(req.query.offset ?? "0"), 10), 0);

  const rows = await prisma.number.findMany({
    where: {
      OR: [
        { source: "arcep" },
        { reportCountSpam: { gte: MIN_REPORTS_FOR_INDEX } },
      ],
    },
    select: {
      phone: true,
      spamScore: true,
      status: true,
      category: true,
      source: true,
      reportCountSpam: true,
      reportCountLegit: true,
      updatedAt: true,
    },
    orderBy: { updatedAt: "desc" },
    take: limit,
    skip: offset,
  });

  const numbers = rows.filter(isIndexable);

  res.json({
    serverTime: new Date().toISOString(),
    minReports: MIN_REPORTS_FOR_INDEX,
    count: numbers.length,
    numbers,
  });
});

/**
 * Derive a SHORT human prefix from an ARCEP wildcard pattern, grouped on the
 * national 4-digit lead (the meaningful telemarketing prefix), e.g.
 *   "33162######"  → { intl: "33162", national: "0162", display: "01 62" }
 *   "338991234##"  → { intl: "33899", national: "0899", display: "08 99" }
 * FR-only (patterns start with "33"). Grouping keeps page count sane and SEO-
 * relevant (people search "0162", "0899" — not 6-digit series).
 */
function prefixFromPattern(pattern) {
  const fixed = pattern.replace(/#+$/, "");
  if (!fixed.startsWith("33") || fixed.length < 5) return null; // ARCEP = FR only
  const lead = fixed.slice(2, 5); // 3 digits after country code → e.g. "162", "899"
  const intl = "33" + lead; // "33162"
  const national = "0" + lead; // "0162"
  const display = `0${lead[0]} ${lead.slice(1)}`; // "01 62"
  return { intl, national, display };
}

/**
 * GET /api/v1/seo/prefixes
 * The official French (ARCEP) prefixes, derived from the wildcard patterns,
 * with per-prefix aggregates (how many community numbers fall under it).
 * These power aggregate prefix pages (à la annuaire inversé), in ADDITION to
 * the per-number pages.
 */
seoRouter.get("/prefixes", async (_req, res) => {
  const patterns = await prisma.pattern.findMany({
    where: { source: "arcep" },
    select: { pattern: true, status: true, category: true, name: true },
  });

  // Group by derived prefix (several patterns may map to the same display).
  const byIntl = new Map();
  for (const p of patterns) {
    const pre = prefixFromPattern(p.pattern);
    if (!pre) continue;
    if (!byIntl.has(pre.intl)) {
      byIntl.set(pre.intl, {
        ...pre,
        status: p.status,
        category: p.category,
        name: p.name,
        patternCount: 0,
      });
    }
    byIntl.get(pre.intl).patternCount += 1;
  }

  const prefixes = [...byIntl.values()].sort((a, b) => a.intl.localeCompare(b.intl));
  res.json({ serverTime: new Date().toISOString(), count: prefixes.length, prefixes });
});

/**
 * GET /api/v1/seo/prefix/:intl   (intl = digits, e.g. "33899")
 * One prefix's detail + the indexable community numbers under it.
 */
seoRouter.get("/prefix/:intl", async (req, res) => {
  const intl = String(req.params.intl).replace(/\D/g, "");
  if (!intl.startsWith("33") || intl.length < 3) {
    return res.status(400).json({ error: "invalid_prefix" });
  }

  const pattern = await prisma.pattern.findFirst({
    where: { source: "arcep", pattern: { startsWith: intl } },
    select: { pattern: true, status: true, category: true, name: true },
  });

  // Quality numbers under this prefix (well-reported, non-personal).
  const numbers = await prisma.number.findMany({
    where: {
      phone: { startsWith: intl },
      reportCountSpam: { gte: MIN_REPORTS_FOR_INDEX },
    },
    select: { phone: true, spamScore: true, status: true, category: true, reportCountSpam: true },
    orderBy: { reportCountSpam: "desc" },
    take: 100,
  });

  const pre = pattern ? prefixFromPattern(pattern.pattern) : null;
  res.json({
    intl,
    exists: !!pattern,
    prefix: pre ? { ...pre, status: pattern.status, category: pattern.category, name: pattern.name } : null,
    numbers: numbers.filter((n) => !isPersonalMobileFR(n.phone)),
  });
});

/**
 * GET /api/v1/seo/indexable/:phone
 * Tells the page renderer whether THIS number should be indexed (robots) and
 * returns its data. Always safe to call; pages call it to decide noindex.
 */
seoRouter.get("/indexable/:phone", async (req, res) => {
  const phone = String(req.params.phone).replace(/\D/g, "");
  if (!phone) return res.status(400).json({ error: "invalid_phone" });

  const number = await prisma.number.findUnique({ where: { phone } });

  const eligible = isIndexable(number);

  res.json({
    phone,
    indexable: eligible,
    isArcep: !!number && isArcepEligible(number),
    number: number
      ? {
          phone: number.phone,
          spamScore: number.spamScore,
          status: number.status,
          category: number.category,
          source: number.source,
          reportCountSpam: number.reportCountSpam,
          reportCountLegit: number.reportCountLegit,
        }
      : null,
  });
});
