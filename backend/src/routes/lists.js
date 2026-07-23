import { Router } from "express";
import { prisma } from "../db.js";
import { rateLimit } from "../middleware/rateLimit.js";
import { issueListToken, verifyListToken, LIST_TOKEN_TTL_S } from "../listToken.js";

export const listsRouter = Router();

/**
 * GET /api/v1/lists/access
 *
 * Rate-limited gate issuing a short-lived HMAC token required by GET /lists.
 * The download URL is therefore "provisoire" : shared, hotlinked or replayed
 * after the TTL, it is dead. All list traffic funnels through this gate.
 */
listsRouter.get(
  "/access",
  rateLimit({ windowMs: 3_600_000, max: 30 }),
  (_req, res) => {
    const { token } = issueListToken();
    return res.json({ token, expiresIn: LIST_TOKEN_TTL_S });
  },
);

/**
 * GET /api/v1/lists?token=<jwtish>&since=<ISO8601>&limit=<n>&country=<dialCode>
 *
 * Delta-sync endpoint for apps (requires a valid short-lived `token`, see
 * /lists/access):
 *   - First launch (warm-up): omit `since` → full list of block+warn numbers + patterns.
 *   - Periodic update (every N): pass last sync timestamp → only changed entries.
 *   - `country`: E.164 dialing code (e.g. "33") to scope the list to one country
 *     so a device caches only what it needs. Omit or "all" → worldwide.
 *
 * Returns both exact `numbers` (community + ARCEP exacts) and wildcard `patterns`
 * (ARCEP/operator ranges, e.g. "33899######"). Apps cache results locally.
 */
listsRouter.get("/", rateLimit({ windowMs: 3_600_000, max: 1200 }), async (req, res) => {
  if (!verifyListToken(String(req.query.token ?? ""))) {
    return res.status(401).json({ error: "invalid_or_expired_token" });
  }
  const since = req.query.since ? new Date(String(req.query.since)) : null;
  const sinceValid = since && !isNaN(since.getTime());
  const limit = Math.min(parseInt(String(req.query.limit ?? "5000"), 10), 50000);

  // Country scope: digits only; "all"/empty → no filter. `phone` is E.164
  // without '+', so a dial code prefix (LIKE "33%") selects that country.
  const rawCountry = String(req.query.country ?? "").replace(/[^0-9]/g, "");
  const countryFilter = rawCountry ? { phone: { startsWith: rawCountry } } : {};
  const patternFilter = rawCountry ? { pattern: { startsWith: rawCountry } } : {};

  const numbers = await prisma.number.findMany({
    where: {
      status: { in: ["block", "warn"] },
      ...countryFilter,
      ...(sinceValid ? { updatedAt: { gt: since } } : {}),
    },
    // `source` is deliberately omitted: apps see one homogeneous list.
    select: {
      phone: true,
      status: true,
      spamScore: true,
      category: true,
      updatedAt: true,
    },
    orderBy: { updatedAt: "asc" },
    take: limit,
  });

  const patterns = await prisma.pattern.findMany({
    where: {
      ...patternFilter,
      ...(sinceValid ? { updatedAt: { gt: since } } : {}),
    },
    select: {
      pattern: true,
      status: true,
      category: true,
      source: true,
      name: true,
      updatedAt: true,
    },
    orderBy: { updatedAt: "asc" },
    take: limit,
  });

  // Phones to drop from the local cache (healed / deleted) since last sync.
  // On a full sync (no `since`) the client starts clean, so none are needed.
  const deleted = sinceValid
    ? (
        await prisma.numberTombstone.findMany({
          where: { removedAt: { gt: since }, ...countryFilter },
          select: { phone: true },
          orderBy: { removedAt: "asc" },
          take: limit,
        })
      ).map((t) => t.phone)
    : [];

  return res.json({
    serverTime: new Date().toISOString(),
    count: numbers.length,
    next: numbers.length === limit ? numbers.at(-1)?.updatedAt : null,
    numbers,
    patterns,
    deleted,
  });
});
