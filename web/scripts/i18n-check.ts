/**
 * i18n coverage report: lists, per draft locale, the dictionary keys that still
 * fall back to English. Exit code 1 if a *published* locale is incomplete.
 *
 *   pnpm i18n:check            # summary
 *   pnpm i18n:check --missing  # also print every missing key
 */
import { fr } from "../src/i18n/dict/fr";
import { partials } from "../src/i18n/dictionaries";
import { localeRegistry } from "../src/i18n/locales";

function leaves(obj: unknown, prefix = ""): string[] {
  if (Array.isArray(obj) || typeof obj !== "object" || obj === null) return [prefix];
  return Object.entries(obj).flatMap(([k, v]) => leaves(v, prefix ? `${prefix}.${k}` : k));
}

function has(obj: unknown, path: string): boolean {
  let cur: unknown = obj;
  for (const k of path.split(".")) {
    if (typeof cur !== "object" || cur === null || !(k in cur)) return false;
    cur = (cur as Record<string, unknown>)[k];
  }
  return cur !== undefined;
}

const all = leaves(fr);
const showMissing = process.argv.includes("--missing");
let failed = false;

for (const [dict, partial] of Object.entries(partials)) {
  const missing = all.filter((k) => !has(partial, k));
  const pct = Math.round((100 * (all.length - missing.length)) / all.length);
  const info = localeRegistry.find((l) => l.dict === dict);
  const published = info?.published ?? false;
  console.log(`${dict.padEnd(3)} ${String(pct).padStart(3)} %  ${missing.length} missing  ${published ? "PUBLISHED" : "draft"}`);
  if (showMissing) for (const k of missing) console.log(`      - ${k}`);
  if (published && missing.length) failed = true;
}

if (failed) {
  console.error("\nA published locale has untranslated keys — translate them or set published: false.");
  process.exit(1);
}
