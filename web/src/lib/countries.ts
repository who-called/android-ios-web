import { getCountryCallingCode, type CountryCode } from "libphonenumber-js";

export type Country = {
  code: CountryCode; // ISO 3166-1 alpha-2, e.g. "FR"
  name: string; // localized display name (FR labels for now)
  dial: string; // calling code, e.g. "33"
  flag: string; // emoji flag
};

// FR-first, then the most relevant neighbours / common origins of spam calls.
// Extend freely — libphonenumber-js validates any country anyway.
export const countries: Country[] = [
  { code: "FR", name: "France", dial: "33", flag: "🇫🇷" },
  { code: "BE", name: "Belgique", dial: "32", flag: "🇧🇪" },
  { code: "CH", name: "Suisse", dial: "41", flag: "🇨🇭" },
  { code: "LU", name: "Luxembourg", dial: "352", flag: "🇱🇺" },
  { code: "MC", name: "Monaco", dial: "377", flag: "🇲🇨" },
  { code: "ES", name: "Espagne", dial: "34", flag: "🇪🇸" },
  { code: "IT", name: "Italie", dial: "39", flag: "🇮🇹" },
  { code: "DE", name: "Allemagne", dial: "49", flag: "🇩🇪" },
  { code: "GB", name: "Royaume-Uni", dial: "44", flag: "🇬🇧" },
  { code: "PT", name: "Portugal", dial: "351", flag: "🇵🇹" },
  { code: "NL", name: "Pays-Bas", dial: "31", flag: "🇳🇱" },
  { code: "US", name: "États-Unis", dial: "1", flag: "🇺🇸" },
  { code: "CA", name: "Canada", dial: "1", flag: "🇨🇦" },
  { code: "MA", name: "Maroc", dial: "212", flag: "🇲🇦" },
  { code: "DZ", name: "Algérie", dial: "213", flag: "🇩🇿" },
  { code: "TN", name: "Tunisie", dial: "216", flag: "🇹🇳" },
];

export const defaultCountry = countries[0]; // FR

export function findCountry(code: string): Country | undefined {
  return countries.find((c) => c.code === code);
}

// Flag emoji from an ISO 3166-1 alpha-2 code (regional indicator letters).
export function flagEmoji(code: string): string {
  if (!/^[A-Za-z]{2}$/.test(code)) return "🏳️";
  return String.fromCodePoint(
    ...[...code.toUpperCase()].map((c) => 0x1f1a5 + c.charCodeAt(0)),
  );
}

let regionNames: Intl.DisplayNames | null = null;
function countryName(code: CountryCode): string {
  try {
    regionNames ??= new Intl.DisplayNames(["fr"], { type: "region" });
    return regionNames.of(code) ?? code;
  } catch {
    return code;
  }
}

// Resolve a full Country for ANY detected ISO code: the curated entry if we have
// one, otherwise built from libphonenumber metadata + a computed flag — so
// auto-detection covers every country, not just the curated dropdown list.
export function countryFromCode(code: CountryCode): Country {
  const curated = findCountry(code);
  if (curated) return curated;
  let dial = "";
  try {
    dial = getCountryCallingCode(code);
  } catch {
    /* unknown calling code */
  }
  return { code, name: countryName(code), dial, flag: flagEmoji(code) };
}
