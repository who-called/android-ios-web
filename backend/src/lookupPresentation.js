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
