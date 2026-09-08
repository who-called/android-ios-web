import { config } from "./config";
import { parsePhoneNumberFromString, type CountryCode } from "libphonenumber-js";

export type ReasonCategory =
  | "telemarketing"
  | "scam"
  | "robocall"
  | "silent"
  | "debt"
  | "survey"
  | "unknown";

/** API error carrying the HTTP status so callers can show dedicated messages. */
export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

export type TopReason = {
  category: ReasonCategory;
  count: number;
  share: number; // 0..100 — share of spam reports
};

export type LookupResult = {
  phone: string;
  spamScore: number;
  status: "block" | "warn" | "allow" | "unknown";
  confidence?: number | null;
  confidenceLevel?: "none" | "low" | "medium" | "high" | "official";
  category?: string | null;
  source?: "none" | "community" | "arcep" | "mixed";
  reportCountSpam?: number;
  reportCountLegit?: number;
  userVote?: "spam" | "legit" | null;
  frequency?: { last24h: number; last7d: number; last30d: number; last1y: number };
  reasons?: Record<ReasonCategory, number>;
  topReason?: TopReason | null;
  firstReportedAt?: string | null;
  lastReportedAt?: string | null;
  officialPattern?: {
    pattern: string;
    status: string;
    category: string;
    name?: string | null;
  } | null;
};

export type TrendingNumber = {
  phone: string;
  reportCount: number;
  last24h: number;
  velocity: number;
  spamScore: number;
  status: "block" | "warn" | "allow" | "unknown";
  category: string;
  source: string;
  /** Same rule as /seo/indexable; absent on older backends. */
  indexable?: boolean;
  topReason?: TopReason | null;
};

/**
 * Normalize to E.164 digits WITHOUT '+' (e.g. "33612345678"), using
 * libphonenumber-js with the selected country as the default region.
 * We always store/look up the international form — the country only helps
 * parse local input. Returns null if the number isn't valid.
 */
export function normalizePhone(raw: string, country: CountryCode = "FR"): string | null {
  if (!raw) return null;
  const parsed = parsePhoneNumberFromString(raw, country);
  if (!parsed || !parsed.isValid()) return null;
  return parsed.number.replace(/^\+/, ""); // E.164 without '+'
}

export async function lookupNumber(phone: string): Promise<LookupResult> {
  const res = await fetch(`${config.apiBaseUrl}/lookup/${encodeURIComponent(phone)}`, {
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  if (!res.ok) throw new Error(`lookup failed: ${res.status}`);
  return res.json();
}

export async function reportNumber(input: {
  phone: string;
  deviceId: string;
  vote: "spam" | "legit";
  category?: string | null;
  locale?: string;
}): Promise<void> {
  const res = await fetch(`${config.apiBaseUrl}/reports`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...input, locale: input.locale ?? "fr" }),
  });
  if (!res.ok) throw new ApiError(`report failed: ${res.status}`, res.status);
}

/** Numbers ramping up right now ("ça monte"), ranked by recent velocity. */
export async function fetchTrending(
  windowDays = 7,
  limit = 20,
): Promise<TrendingNumber[]> {
  try {
    const res = await fetch(
      `${config.apiBaseUrl}/trending?window=${windowDays}&limit=${limit}`,
      { next: { revalidate: 600 } }, // 10 min: fresh enough, SEO/ISR-friendly
    );
    if (!res.ok) return [];
    return (await res.json()).numbers ?? [];
  } catch {
    return [];
  }
}

export type SeoNumber = {
  phone: string;
  spamScore: number;
  status: string;
  category: string;
  source: string;
  reportCountSpam: number;
  reportCountLegit: number;
};

/** Numbers eligible for an indexable page (quality + non-personal). */
export async function fetchSeoNumbers(limit = 2000): Promise<SeoNumber[]> {
  try {
    const res = await fetch(`${config.apiBaseUrl}/seo/numbers?limit=${limit}`, {
      // Revalidate periodically (ISR-friendly) instead of no-store.
      next: { revalidate: 3600 },
    });
    if (!res.ok) return [];
    const data = await res.json();
    return data.numbers ?? [];
  } catch {
    return [];
  }
}

/**
 * Per-number indexability + data, used by the page to decide noindex.
 * `ok: false` means the API could not answer (network error / 5xx) — the
 * page must then NOT emit noindex, or a transient outage during an ISR
 * revalidation would de-index a legitimate page for an hour (seen in GSC).
 */
export async function fetchIndexable(
  phone: string,
): Promise<{ ok: boolean; indexable: boolean; isArcep: boolean; number: SeoNumber | null }> {
  const unknown = { ok: false, indexable: false, isArcep: false, number: null };
  try {
    const res = await fetch(
      `${config.apiBaseUrl}/seo/indexable/${encodeURIComponent(phone)}`,
      { next: { revalidate: 3600 } },
    );
    if (res.status >= 500) return unknown;
    if (!res.ok) return { ...unknown, ok: true }; // 4xx: invalid phone → not indexable
    return { ok: true, ...(await res.json()) };
  } catch {
    return unknown;
  }
}

export type SeoPrefix = {
  intl: string; // "33899"
  national: string; // "0899"
  display: string; // "+33 899"
  status: string;
  category: string;
  name: string | null;
  patternCount: number;
};

/** All official FR (ARCEP) prefixes for aggregate pages. */
export async function fetchPrefixes(): Promise<SeoPrefix[]> {
  try {
    const res = await fetch(`${config.apiBaseUrl}/seo/prefixes`, { next: { revalidate: 86400 } });
    if (!res.ok) return [];
    return (await res.json()).prefixes ?? [];
  } catch {
    return [];
  }
}

/**
 * One prefix detail + the quality numbers under it.
 * `ok: false` = API unreachable (see fetchIndexable) → page must not noindex.
 */
export async function fetchPrefix(intl: string): Promise<{
  ok: boolean;
  exists: boolean;
  prefix: SeoPrefix | null;
  numbers: SeoNumber[];
}> {
  const unknown = { ok: false, exists: false, prefix: null, numbers: [] };
  try {
    const res = await fetch(`${config.apiBaseUrl}/seo/prefix/${encodeURIComponent(intl)}`, {
      next: { revalidate: 3600 },
    });
    if (res.status >= 500) return unknown;
    if (!res.ok) return { ...unknown, ok: true }; // 4xx: invalid prefix → not indexable
    return { ok: true, ...(await res.json()) };
  } catch {
    return unknown;
  }
}

/** Anonymous web device id (localStorage). No PII. */
let memoryDeviceId: string | null = null;

export function webDeviceId(): string {
  if (typeof window === "undefined") return "web-anon";
  const key = "wc_device_id";
  try {
    let id = localStorage.getItem(key);
    if (!id) {
      id = "web-" + crypto.randomUUID();
      localStorage.setItem(key, id);
    }
    return id;
  } catch {
    // localStorage blocked (Safari "block all cookies", private/embedded
    // contexts) — fall back to a per-session in-memory id.
    if (!memoryDeviceId) memoryDeviceId = "web-" + crypto.randomUUID();
    return memoryDeviceId;
  }
}
