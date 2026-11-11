import { fr, type Dict } from "./dict/fr";
import { en } from "./dict/en";
import { es } from "./dict/es";
import { it } from "./dict/it";
import { pt } from "./dict/pt";
import { ru } from "./dict/ru";
import { pl } from "./dict/pl";
import { cs } from "./dict/cs";
import { de } from "./dict/de";
import { uk } from "./dict/uk";
import { id } from "./dict/id";
import { sv } from "./dict/sv";
import { nl } from "./dict/nl";
import { sk } from "./dict/sk";
import type { PartialDict } from "./dict/types";

export type { Dict };

/** Dictionary locales (language only). URL locales live in ./locales.ts. */
export type Locale =
  | "fr" | "en" | "es" | "it" | "pt" | "ru"
  | "pl" | "cs" | "de" | "uk" | "id" | "sv" | "nl" | "sk";
export const defaultLocale: Locale = "fr";

/** Complete dictionaries. */
const complete = { fr, en } satisfies Partial<Record<Locale, Dict>>;
/** Draft dictionaries — merged over English, the fallback language. */
export const partials: Record<Exclude<Locale, keyof typeof complete>, PartialDict> = {
  es, it, pt, ru,
  pl, cs, de, uk, id, sv, nl, sk,
};
export const locales = [...Object.keys(complete), ...Object.keys(partials)] as Locale[];

function isPlainObject(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

/** Deep-merge `over` onto `base`: objects key-by-key, everything else replaced. */
function merge<T>(base: T, over: unknown): T {
  if (!isPlainObject(base) || !isPlainObject(over)) return (over ?? base) as T;
  const out: Record<string, unknown> = { ...base };
  for (const [k, v] of Object.entries(over)) {
    if (v !== undefined) out[k] = merge((base as Record<string, unknown>)[k], v);
  }
  return out as T;
}

export const dictionaries: Record<Locale, Dict> = {
  ...complete,
  ...(Object.fromEntries(
    Object.entries(partials).map(([l, p]) => [l, merge(en, p)]),
  ) as Record<keyof typeof partials, Dict>),
};

export function getDict(locale: Locale): Dict {
  return dictionaries[locale] ?? dictionaries[defaultLocale];
}
