/**
 * SEO indexability rules (RGPD-safe long-tail strategy), shared by the /seo
 * routes and by /trending so every surface agrees on which numbers get an
 * indexable page.
 *
 * Two independent sources of eligibility:
 *   1. ARCEP — FR ONLY. Official French telemarketer/operator prefixes
 *      (phones starting with "33"). These are FR-specific by nature.
 *   2. Community reports — COUNTRY-AGNOSTIC. Any number with >= N reports is
 *      eligible regardless of country (same rule for FR, BE, US, …).
 *
 * In all cases we exclude personal mobiles to avoid publishing pages about
 * individuals (FR 06/07 → 336/337). Other countries' mobiles are reported via
 * community signals only and stay subject to the same report threshold.
 */
export const MIN_REPORTS_FOR_INDEX = parseInt(process.env.SEO_MIN_REPORTS ?? "3", 10);

/** Exclude French personal mobiles (336…, 337…) from public indexed pages. */
export function isPersonalMobileFR(phone) {
  return /^33[67]/.test(phone);
}

/** ARCEP eligibility is FR-only (phone in the French country code). */
export function isArcepEligible(n) {
  return n.source === "arcep" && n.phone.startsWith("33");
}

/**
 * Whether a Number row deserves an indexable page.
 * `n` needs { phone, source, reportCountSpam }; null/undefined → false.
 */
export function isIndexable(n) {
  if (!n) return false;
  if (isPersonalMobileFR(n.phone)) return false;
  return isArcepEligible(n) || (n.reportCountSpam ?? 0) >= MIN_REPORTS_FOR_INDEX;
}
