import type { Metadata } from "next";

/** French personal mobiles (06/07 → 336/337): never indexed, no API needed. */
export function isPersonalMobileFR(phone: string): boolean {
  return /^33[67]/.test(phone);
}

/**
 * Robots directive for API-driven pages (numero, prefixe).
 *
 * - API answered → index or noindex as it says.
 * - API unreachable (`known` false) → return undefined so the page inherits
 *   the layout's `index, follow`. Emitting noindex on a transient failure
 *   de-indexes legitimate pages (GSC flagged /prefixe/33262 this way).
 * - `forceNoindex` lets callers apply local hard rules (personal mobiles)
 *   regardless of API availability.
 */
export function robotsFor(
  known: boolean,
  indexable: boolean,
  forceNoindex = false,
): Metadata["robots"] | undefined {
  if (forceNoindex) return { index: false, follow: true };
  if (!known) return undefined;
  return indexable ? { index: true, follow: true } : { index: false, follow: true };
}
