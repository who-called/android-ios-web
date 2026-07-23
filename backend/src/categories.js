/**
 * Canonical spam-reason categories — SINGLE SOURCE OF TRUTH.
 *
 * The API validates against this set and the scoring job maps SIA's 18 raw
 * category ids onto it. Front-ends (web / Android / iOS) only carry these same
 * keys + their localized labels — they must never invent new ones.
 *
 * Curated (data-driven) set: covers >98% of real reports without the friction
 * of SIA's full 18-category taxonomy. "unknown" = system default / "Autre".
 */
export const REPORT_CATEGORIES = [
  "telemarketing", // Démarchage
  "scam", // Arnaque
  "robocall", // Appel automatisé
  "silent", // Appel silencieux
  "debt", // Recouvrement
  "survey", // Sondage / caritatif / politique
  "unknown", // Autre / non précisé
];

// SIA enum `f` (category_id) → our canonical key. Identity ids (Finance/Company/
// Service) are not spam reasons → "unknown" (their status comes from scoring).
const SIA_ID_TO_CATEGORY = {
  0: "unknown", // NONE
  1: "telemarketing", // TELEMARKETER
  2: "debt", // DEBT_COLLECTOR
  3: "silent", // SILENT_CALL
  4: "telemarketing", // NUISANCE_CALL
  5: "telemarketing", // UNSOLICITED_CALL
  6: "telemarketing", // CALL_CENTRE
  7: "unknown", // FAX_MACHINE
  8: "survey", // NON_PROFIT
  9: "survey", // POLITICAL
  10: "scam", // SCAM
  11: "unknown", // PRANK
  12: "unknown", // SMS
  13: "survey", // SURVEY
  14: "unknown", // OTHER
  15: "unknown", // FINANCE_SERVICE (identity)
  16: "unknown", // COMPANY (identity)
  17: "unknown", // SERVICE (identity)
  18: "robocall", // ROBOCALL
};

/** Map a SIA category id to our canonical category key. */
export function categoryFromSiaId(id) {
  return SIA_ID_TO_CATEGORY[id] ?? "unknown";
}

/** Whether a string is one of our canonical report categories. */
export function isReportCategory(c) {
  return REPORT_CATEGORIES.includes(c);
}
