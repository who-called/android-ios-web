/**
 * Tiny fixed-window in-memory counters, keyed by an arbitrary string (the
 * client IP in practice).
 *
 * Deliberately simple (single-instance API, no Redis): the goal is to bound
 * abuse cheaply — mass scraping, score spam, sybil floods — not to be
 * millisecond-accurate under bursts. Behind the reverse proxy `req.ip` honors
 * X-Forwarded-For thanks to `trust proxy` (see server.js).
 */

/**
 * A reusable counter: `hit(key)` returns true while the key is within budget
 * for the current window, false once it exceeded `max`.
 */
export function fixedWindowCounter({ windowMs, max }) {
  const hits = new Map(); // key -> { count, resetAt }

  // Periodic sweep so the map never grows unbounded.
  const sweeper = setInterval(() => {
    const now = Date.now();
    for (const [k, v] of hits) if (v.resetAt <= now) hits.delete(k);
  }, windowMs);
  sweeper.unref?.();

  return {
    hit(key) {
      const now = Date.now();
      let h = hits.get(key);
      if (!h || h.resetAt <= now) {
        h = { count: 0, resetAt: now + windowMs };
        hits.set(key, h);
      }
      h.count += 1;
      return { allowed: h.count <= max, retryAfterSec: Math.ceil((h.resetAt - now) / 1000) };
    },
  };
}

/** Express middleware: per-IP fixed-window limit → 429 with Retry-After. */
export function rateLimit({ windowMs, max }) {
  const counter = fixedWindowCounter({ windowMs, max });

  return (req, res, next) => {
    const { allowed, retryAfterSec } = counter.hit(req.ip ?? "unknown");
    if (!allowed) {
      res.setHeader("Retry-After", String(retryAfterSec));
      return res.status(429).json({ error: "rate_limited" });
    }
    return next();
  };
}
