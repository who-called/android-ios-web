import type { MetadataRoute } from "next";
import { config } from "@/lib/config";
import { urlLocales } from "@/i18n/locales";
import { fetchSeoNumbers, fetchPrefixes } from "@/lib/api";

export const revalidate = 3600;

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const now = new Date();
  const staticPaths = ["", "/verifier", "/signaler", "/tendances", "/guides", "/prefixe", "/privacy", "/policy", "/support", "/data-deletion"];
  const entries: MetadataRoute.Sitemap = [];

  // Static pages, per locale.
  for (const locale of urlLocales) {
    for (const path of staticPaths) {
      const fresh = path === "" || path === "/tendances";
      entries.push({
        url: `${config.siteUrl}/${locale}${path}`,
        lastModified: now,
        changeFrequency: fresh ? "weekly" : "monthly",
        priority: path === "" ? 1 : path === "/tendances" ? 0.7 : 0.6,
      });
    }
  }

  // Aggregate ARCEP prefix pages (FR) — RGPD-safe, no personal data.
  const prefixes = await fetchPrefixes();
  for (const locale of urlLocales) {
    for (const p of prefixes) {
      entries.push({
        url: `${config.siteUrl}/${locale}/prefixe/${p.intl}`,
        lastModified: now,
        changeFrequency: "weekly",
        priority: 0.6,
      });
    }
  }

  // Quality number pages only (ARCEP or well-reported, non-personal).
  const numbers = await fetchSeoNumbers(5000);
  for (const locale of urlLocales) {
    for (const n of numbers) {
      entries.push({
        url: `${config.siteUrl}/${locale}/numero/${n.phone}`,
        lastModified: now,
        changeFrequency: "weekly",
        priority: 0.5,
      });
    }
  }

  return entries;
}
