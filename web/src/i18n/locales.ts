import type { Locale } from "./dictionaries";

/**
 * Locale registry — single source of truth for every locale the site knows.
 *
 * Adding a language = one entry here + one dictionary file in ./dict/.
 *
 * `published: false` ("draft") locales are routable (so translators can review
 * /<locale>/…) but stay invisible to users and search engines: not in the
 * language switcher, sitemap or hreflang, never picked by Accept-Language
 * detection, and served with `noindex`. Flip to `true` once the dictionary is
 * fully translated (`pnpm i18n:check`) — otherwise Google would index pages
 * whose text silently falls back to English.
 */
export const localeRegistry = [
  { url: "fr-FR", dict: "fr", label: "Français", short: "FR", flag: "🇫🇷", published: true },
  { url: "en-US", dict: "en", label: "English", short: "EN", flag: "🇬🇧", published: true },
  // Lot 1 — ~85 % of the numbers database (see ru/it/pt/es share by dial code).
  { url: "es-ES", dict: "es", label: "Español", short: "ES", flag: "🇪🇸", published: true },
  { url: "it-IT", dict: "it", label: "Italiano", short: "IT", flag: "🇮🇹", published: true },
  { url: "pt-BR", dict: "pt", label: "Português", short: "PT", flag: "🇧🇷", published: true },
  { url: "ru-RU", dict: "ru", label: "Русский", short: "RU", flag: "🇷🇺", published: true },
  // Lot 2 — PL, CZ, DE/AT/CH, UA (~97 % cumulated).
  { url: "pl-PL", dict: "pl", label: "Polski", short: "PL", flag: "🇵🇱", published: true },
  { url: "cs-CZ", dict: "cs", label: "Čeština", short: "CS", flag: "🇨🇿", published: true },
  { url: "de-DE", dict: "de", label: "Deutsch", short: "DE", flag: "🇩🇪", published: true },
  { url: "uk-UA", dict: "uk", label: "Українська", short: "UK", flag: "🇺🇦", published: true },
  // Lot 3 — long tail (~98.8 % cumulated).
  { url: "id-ID", dict: "id", label: "Bahasa Indonesia", short: "ID", flag: "🇮🇩", published: true },
  { url: "sv-SE", dict: "sv", label: "Svenska", short: "SV", flag: "🇸🇪", published: true },
  { url: "nl-NL", dict: "nl", label: "Nederlands", short: "NL", flag: "🇳🇱", published: true },
  { url: "sk-SK", dict: "sk", label: "Slovenčina", short: "SK", flag: "🇸🇰", published: true },
] as const satisfies readonly {
  url: string;
  dict: Locale;
  label: string;
  short: string;
  flag: string;
  published: boolean;
}[];

export type LocaleInfo = (typeof localeRegistry)[number];

/** URL locale segments (BCP-47 with region), e.g. "fr-FR". */
export const urlLocales = localeRegistry.map((l) => l.url);
export type UrlLocale = LocaleInfo["url"];

/** Locales shown to users and search engines. */
export const publishedLocales: UrlLocale[] = localeRegistry
  .filter((l) => l.published)
  .map((l) => l.url);

export const defaultUrlLocale: UrlLocale = "fr-FR";

export function isUrlLocale(seg: string): seg is UrlLocale {
  return (urlLocales as readonly string[]).includes(seg);
}

export function localeInfo(url: string): LocaleInfo {
  return localeRegistry.find((l) => l.url === url) ?? localeRegistry[0];
}

export function isPublished(url: string): boolean {
  return localeInfo(url).published && isUrlLocale(url);
}

/** Map a URL locale (fr-FR) to a dictionary locale (fr). */
export function toDictLocale(url: string): Locale {
  return localeInfo(url).dict;
}

/** OpenGraph locale string, e.g. fr_FR. */
export function ogLocale(url: UrlLocale): string {
  return url.replace("-", "_");
}

/**
 * Locale for visitors we can't match (unsupported browser language, bots
 * without Accept-Language) — also the hreflang x-default.
 */
export const fallbackUrlLocale: UrlLocale = "en-US";

/**
 * Best published locale for an Accept-Language header, or null if none of the
 * visitor's languages is supported.
 * Exact tag match first (pt-BR), then language match (pt-PT → pt-BR), by q order.
 */
export function negotiateLocale(acceptLanguage: string | null | undefined): UrlLocale | null {
  const wanted = (acceptLanguage ?? "")
    .split(",")
    .map((part, i) => {
      const [tag, ...params] = part.trim().split(";");
      const q = params.find((p) => p.trim().startsWith("q="));
      return { tag: tag.trim().toLowerCase(), q: q ? Number(q.trim().slice(2)) || 0 : 1, i };
    })
    .filter((w) => w.tag && w.tag !== "*" && w.q > 0)
    .sort((a, b) => b.q - a.q || a.i - b.i);

  for (const { tag } of wanted) {
    const exact = publishedLocales.find((l) => l.toLowerCase() === tag);
    if (exact) return exact;
    const lang = tag.split("-")[0];
    const byLang = publishedLocales.find((l) => l.toLowerCase().split("-")[0] === lang);
    if (byLang) return byLang;
  }
  return null;
}

/**
 * Dial-code prefix → locale of the country the number belongs to. Longest
 * prefix wins. Only countries whose main language we publish; others → null.
 */
const dialLocales: Record<string, UrlLocale> = {
  // French: France + overseas, Monaco, Luxembourg, Belgium, Maghreb
  "33": "fr-FR", "262": "fr-FR", "508": "fr-FR", "590": "fr-FR", "594": "fr-FR", "596": "fr-FR",
  "681": "fr-FR", "687": "fr-FR", "689": "fr-FR", "377": "fr-FR", "352": "fr-FR", "32": "fr-FR",
  "212": "fr-FR", "213": "fr-FR", "216": "fr-FR",
  // English
  "1": "en-US", "44": "en-US", "61": "en-US", "64": "en-US", "353": "en-US", "27": "en-US",
  // Spanish: Spain + Latin America
  "34": "es-ES", "52": "es-ES", "54": "es-ES", "56": "es-ES", "57": "es-ES", "51": "es-ES",
  "58": "es-ES", "53": "es-ES", "591": "es-ES", "593": "es-ES", "595": "es-ES", "598": "es-ES",
  "502": "es-ES", "503": "es-ES", "504": "es-ES", "505": "es-ES", "506": "es-ES", "507": "es-ES",
  // Italian: Italy, San Marino
  "39": "it-IT", "378": "it-IT",
  // Portuguese: Brazil, Portugal
  "55": "pt-BR", "351": "pt-BR",
  // Russian: Russia/Kazakhstan (+7), Belarus
  "7": "ru-RU", "375": "ru-RU",
  // Lot 2/3
  "48": "pl-PL", "420": "cs-CZ", "421": "sk-SK", "380": "uk-UA",
  "49": "de-DE", "43": "de-DE", "41": "de-DE", "423": "de-DE",
  "62": "id-ID", "46": "sv-SE", "31": "nl-NL", "597": "nl-NL",
};

export function localeForPhone(digits: string): UrlLocale | null {
  for (let len = 3; len >= 1; len--) {
    const hit = dialLocales[digits.slice(0, len)];
    if (hit && publishedLocales.includes(hit)) return hit;
  }
  return null;
}

/**
 * Canonical + hreflang cluster for a page. Every locale variant lists all
 * published alternates (drafts stay out), and x-default points to the fallback
 * locale — the one a visitor with no supported language is redirected to.
 */
export function alternatesFor(locale: string, path = "") {
  const languages: Record<string, string> = {};
  for (const l of publishedLocales) languages[l] = `/${l}${path}`;
  languages["x-default"] = `/${fallbackUrlLocale}${path}`;
  return { canonical: `/${locale}${path}`, languages };
}
