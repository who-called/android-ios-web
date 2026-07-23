import type { Locale } from "./dictionaries";

/** URL locale segments (BCP-47 with region) and their dictionary mapping. */
export const urlLocales = ["fr-FR", "en-US"] as const;
export type UrlLocale = (typeof urlLocales)[number];

export const defaultUrlLocale: UrlLocale = "fr-FR";

/** Map a URL locale (fr-FR) to a dictionary locale (fr). */
export function toDictLocale(url: string): Locale {
  return url.toLowerCase().startsWith("en") ? "en" : "fr";
}

/** OpenGraph locale string, e.g. fr_FR. */
export function ogLocale(url: UrlLocale): string {
  return url.replace("-", "_");
}

export function isUrlLocale(seg: string): seg is UrlLocale {
  return (urlLocales as readonly string[]).includes(seg);
}
