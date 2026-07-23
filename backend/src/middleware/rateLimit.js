/**
 * Tiny fixed-window in-memory rate limiter, keyed by client IP.
 *
 * Deliberately simple (single-instance API, no Redis): the goal is to bound
 * abuse cheaply — mass scraping, score spam, sybil floods — not to be
 * millisecond-accurate under bursts. Behind the reverse proxy `req.ip` honors
 * X-Forwarded-For thanks to `trust proxy` (see server.js).
 */
export function rateLimit({ windowMs, max }) {
  const hits = new Map(); // ip -> { count, resetAt }

  // Periodic sweep so the map never grows unbounded.
  const sweeper = setInterval(() => {
    const now = Date.now();
    for (const [k, v] of hits) if (v.resetAt <= now) hits.delete(k);
  }, windowMs);
  sweeper.unref?.();

  return (req, res, next) => {
    const key = req.ip ?? "unknown";
    const now = Date.now();
    let h = hits.get(key);
    if (!h || h.resetAt <= now) {
      h = { count: 0, resetAt: now + windowMs };
      hits.set(key, h);
    }
    h.count += 1;
    if (h.count > max) {
      res.setHeader("Retry-After", String(Math.ceil((h.resetAt - now) / 1000)));
      return res.status(429).json({ error: "rate_limited" });
    }
    return next();
  };
}
