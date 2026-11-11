import type { MetadataRoute } from "next";
import { config } from "@/lib/config";
import { defaultUrlLocale, fallbackUrlLocale, publishedLocales } from "@/i18n/locales";
import { fetchSeoNumbers, fetchPrefixes } from "@/lib/api";

export const revalidate = 3600;

/** hreflang alternates for a path — mirrors the on-page <link rel=alternate>. */
function langAlternates(path: string): Record<string, string> {
  const languages: Record<string, string> = {};
  for (const l of publishedLocales) languages[l] = `${config.siteUrl}/${l}${path}`;
  languages["x-default"] = `${config.siteUrl}/${fallbackUrlLocale}${path}`;
  return languages;
}

// One sitemap per published locale (/sitemap/<locale>.xml) — a single file
// would exceed Google's 50 000-URL limit. /sitemap.xml is the index
// (app/sitemap.xml/route.ts). Draft locales are deliberately absent.
export async function generateSitemaps() {
  return publishedLocales.map((id) => ({ id }));
}

export default async function sitemap(props: {
  id: string | Promise<string>;
}): Promise<MetadataRoute.Sitemap> {
  const id = await props.id;
  const locales = publishedLocales.filter((l) => l === id);
  const now = new Date();
  const staticPaths = ["", "/verifier", "/signaler", "/tendances", "/guides", "/prefixe", "/privacy", "/policy", "/support", "/data-deletion"];
  const entries: MetadataRoute.Sitemap = [];

  // Static pages, per locale.
  for (const locale of locales) {
    for (const path of staticPaths) {
      const fresh = path === "" || path === "/tendances";
      entries.push({
        url: `${config.siteUrl}/${locale}${path}`,
        lastModified: now,
        changeFrequency: fresh ? "weekly" : "monthly",
        priority: path === "" ? 1 : path === "/tendances" ? 0.7 : 0.6,
        alternates: { languages: langAlternates(path) },
      });
    }
  }

  // Aggregate ARCEP prefix pages (FR) — RGPD-safe, no personal data.
  const prefixes = await fetchPrefixes();
  for (const locale of locales) {
    for (const p of prefixes) {
      entries.push({
        url: `${config.siteUrl}/${locale}/prefixe/${p.intl}`,
        lastModified: now,
        changeFrequency: "weekly",
        priority: locale === defaultUrlLocale ? 0.6 : 0.4,
        alternates: { languages: langAlternates(`/prefixe/${p.intl}`) },
      });
    }
  }

  // Quality number pages only (ARCEP or well-reported, non-personal).
  const numbers = await fetchSeoNumbers(5000);
  for (const locale of locales) {
    for (const n of numbers) {
      entries.push({
        url: `${config.siteUrl}/${locale}/numero/${n.phone}`,
        lastModified: now,
        changeFrequency: "weekly",
        priority: locale === defaultUrlLocale ? 0.5 : 0.3,
        alternates: { languages: langAlternates(`/numero/${n.phone}`) },
      });
    }
  }

  return entries;
}
