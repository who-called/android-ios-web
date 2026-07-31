import { capSiaCount } from "./scoring.js";

const DAY = 86_400_000;

/** Match a normalized E.164 number against trailing-# wildcard patterns. */
export function findMatchingPattern(phone, patterns) {
  return patterns
    .filter((entry) => {
      const pattern = entry.pattern;
      if (!pattern || pattern.length !== phone.length) return false;
      for (let i = 0; i < pattern.length; i += 1) {
        if (pattern[i] !== "#" && pattern[i] !== phone[i]) return false;
      }
      return true;
    })
    .sort((a, b) => fixedLength(b.pattern) - fixedLength(a.pattern))[0] ?? null;
}

export function presentationSource({ hasStatisticalEvidence, hasOfficialPattern }) {
  if (hasOfficialPattern && hasStatisticalEvidence) return "mixed";
  if (hasOfficialPattern) return "arcep";
  if (hasStatisticalEvidence) return "community";
  return "none";
}

/**
 * Activity timeline shown on the detail screens, derived from ALL evidence:
 * our raw reports (real per-vote timestamps) + the SIA seed aggregate.
 *
 * SIA has no per-vote timestamps, so its whole aggregate is attributed to
 * `importedAt` — the same synthetic age the scoring prior decays from. Without
 * this, a seed-backed number looks like it has no history at all (empty chart,
 * no dates) even though it carries dozens of external votes.
 *
 * Derived live from the reports rather than from `numbers.first/lastReportedAt`
 * so a mid-flight materialization never shows a truncated history.
 *
 * @param {Array<{createdAt:Date|string}>} reports  our raw reports for the phone
 * @param {{pos:number, neg:number, importedAt:Date|string}|null} seed
 */
export function evidenceTimeline(reports, seed, now = Date.now()) {
  const siaVotes = seed ? capSiaCount(seed.neg) + capSiaCount(seed.pos) : 0;
  const siaAt = siaVotes > 0 ? new Date(seed.importedAt).getTime() : null;
  const times = reports.map((r) => new Date(r.createdAt).getTime());
  if (siaAt !== null) times.push(siaAt);

  const within = (days) => {
    const cutoff = days * DAY;
    const own = reports.filter((r) => now - new Date(r.createdAt).getTime() <= cutoff).length;
    const sia = siaAt !== null && now - siaAt <= cutoff ? siaVotes : 0;
    return own + sia;
  };

  return {
    frequency: {
      last24h: within(1),
      last7d: within(7),
      last30d: within(30),
      last1y: within(365),
    },
    firstReportedAt: times.length > 0 ? new Date(Math.min(...times)) : null,
    lastReportedAt: times.length > 0 ? new Date(Math.max(...times)) : null,
  };
}

/**
 * Reason ("why did it call?") breakdown over the spam evidence. Counts our own
 * spam reports by their category, then folds the SIA negative aggregate into the
 * seed's single category so the breakdown always adds up to the displayed spam
 * count instead of silently dropping the external votes.
 *
 * `seedCategory` must already be canonical — resolve it with
 * `categoryFromSiaId(seed.categoryId)` rather than reading `sia_seed.category`,
 * whose strings come from the Go ingester's own older vocabulary.
 *
 * @param {string[]} categories       canonical category list
 * @param {Array<{category?:string|null}>} spamReports  our spam reports only
 * @param {number} seedSpam           SIA negative votes (already capped)
 * @param {string|null} seedCategory  canonical category for those votes
 */
export function reasonBreakdown(categories, spamReports, seedSpam = 0, seedCategory = null) {
  const reasons = Object.fromEntries(categories.map((c) => [c, 0]));
  const bump = (category, by) => {
    const key = category && category in reasons ? category : "unknown";
    reasons[key] += by;
  };
  for (const r of spamReports) bump(r.category, 1);
  if (seedSpam > 0) bump(seedCategory, seedSpam);
  return reasons;
}

/** Stable confidence labels shared by web, Android and iOS. */
export function confidenceLevel(confidence, officialOnly = false) {
  if (officialOnly) return "official";
  if (!confidence) return "none";
  if (confidence < 60) return "low";
  if (confidence < 100) return "medium";
  return "high";
}

function fixedLength(pattern) {
  return pattern.replace(/#+$/, "").length;
}
