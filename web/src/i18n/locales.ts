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

/**
 * Canonical + hreflang cluster for a page. Every locale variant lists all
 * alternates, and x-default points to fr-FR (the site's default locale) so
 * Google consolidates duplicates onto fr-FR instead of picking another lang.
 */
export function alternatesFor(locale: string, path = "") {
  const languages: Record<string, string> = {};
  for (const l of urlLocales) languages[l] = `/${l}${path}`;
  languages["x-default"] = `/${defaultUrlLocale}${path}`;
  return { canonical: `/${locale}${path}`, languages };
}
