/**
 * Normalize a phone number to E.164 digits without '+' (e.g. "33899123456").
 * FR-first but accepts already-international input.
 *
 * @param {string} raw
 * @param {string} [defaultCountryPrefix="33"]
 * @returns {string|null}
 */
export function normalizePhone(raw, defaultCountryPrefix = "33") {
  if (!raw) return null;

  let s = String(raw).replace(/[\s().-]/g, "");

  if (s.startsWith("+")) {
    s = s.slice(1);
  } else if (s.startsWith("00")) {
    s = s.slice(2);
  } else if (s.startsWith("0")) {
    // National FR format: 0X XX XX XX XX → 33X...
    s = defaultCountryPrefix + s.slice(1);
  }

  if (!/^\d{6,15}$/.test(s)) return null;
  return s;
}
