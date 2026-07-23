import crypto from "node:crypto";

/**
 * Short-lived HMAC tokens gating the list download (`GET /lists`).
 *
 * Pattern "URL provisoire" : the app first asks `GET /lists/access` (strictly
 * rate-limited) for a token, then paginates `GET /lists?token=…`. A shared or
 * decompiled URL dies after LIST_TOKEN_TTL_S. The signing secret lives ONLY
 * server-side (env), so open-sourcing the clients reveals nothing.
 *
 * Without LIST_TOKEN_SECRET a random secret is generated at boot — fine for a
 * single instance (tokens just die on restart, TTL is short anyway).
 */
if (!process.env.LIST_TOKEN_SECRET && process.env.NODE_ENV === "production") {
  // Boot-time visibility: tokens will still work, but die on every restart.
  console.warn(
    "LIST_TOKEN_SECRET is not set — using an ephemeral secret (list tokens invalidated on each restart).",
  );
}

const secret = process.env.LIST_TOKEN_SECRET
  ? Buffer.from(process.env.LIST_TOKEN_SECRET, "utf8")
  : crypto.randomBytes(32);

export const LIST_TOKEN_TTL_S = parseInt(process.env.LIST_TOKEN_TTL_S ?? "900", 10);

const sign = (payload) =>
  crypto.createHmac("sha256", secret).update(payload).digest("base64url");

/** Returns `{ token, exp }` — token = "<expEpochSeconds>.<hmac>". */
export function issueListToken(now = Date.now()) {
  const exp = Math.floor(now / 1000) + LIST_TOKEN_TTL_S;
  return { token: `${exp}.${sign(String(exp))}`, exp };
}

export function verifyListToken(token, now = Date.now()) {
  if (typeof token !== "string" || token.length === 0 || token.length > 128) return false;
  const dot = token.indexOf(".");
  if (dot <= 0) return false;
  const expStr = token.slice(0, dot);
  const sig = token.slice(dot + 1);
  const exp = Number(expStr);
  if (!Number.isInteger(exp) || exp * 1000 < now) return false;
  const a = Buffer.from(sig);
  const b = Buffer.from(sign(expStr));
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}
