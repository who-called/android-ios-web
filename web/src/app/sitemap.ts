import type { MetadataRoute } from "next";
import { config } from "@/lib/config";
import { urlLocales, defaultUrlLocale } from "@/i18n/locales";
import { fetchSeoNumbers, fetchPrefixes } from "@/lib/api";

export const revalidate = 3600;

/** hreflang alternates for a path — mirrors the on-page <link rel=alternate>. */
function langAlternates(path: string): Record<string, string> {
  const languages: Record<string, string> = {};
  for (const l of urlLocales) languages[l] = `${config.siteUrl}/${l}${path}`;
  languages["x-default"] = `${config.siteUrl}/${defaultUrlLocale}${path}`;
  return languages;
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const now = new Date();
  const staticPaths = ["", "/verifier", "/signaler", "/tendances", "/guides", "/prefixe", "/privacy", "/policy", "/support", "/data-deletion"];
  const entries: MetadataRoute.Sitemap = [];

  // Static pages, per locale.
  for (const path of staticPaths) {
    const fresh = path === "" || path === "/tendances";
    const languages = langAlternates(path);
    for (const locale of urlLocales) {
      entries.push({
        url: `${config.siteUrl}/${locale}${path}`,
        lastModified: now,
        changeFrequency: fresh ? "weekly" : "monthly",
        priority: path === "" ? 1 : path === "/tendances" ? 0.7 : 0.6,
        alternates: { languages },
      });
    }
  }

  // Aggregate ARCEP prefix pages (FR) — RGPD-safe, no personal data.
  const prefixes = await fetchPrefixes();
  for (const p of prefixes) {
    const languages = langAlternates(`/prefixe/${p.intl}`);
    for (const locale of urlLocales) {
      entries.push({
        url: `${config.siteUrl}/${locale}/prefixe/${p.intl}`,
        lastModified: now,
        changeFrequency: "weekly",
        priority: locale === defaultUrlLocale ? 0.6 : 0.4,
        alternates: { languages },
      });
    }
  }

  // Quality number pages only (ARCEP or well-reported, non-personal).
  const numbers = await fetchSeoNumbers(5000);
  for (const n of numbers) {
    const languages = langAlternates(`/numero/${n.phone}`);
    for (const locale of urlLocales) {
      entries.push({
        url: `${config.siteUrl}/${locale}/numero/${n.phone}`,
        lastModified: now,
        changeFrequency: "weekly",
        priority: locale === defaultUrlLocale ? 0.5 : 0.3,
        alternates: { languages },
      });
    }
  }

  return entries;
}
