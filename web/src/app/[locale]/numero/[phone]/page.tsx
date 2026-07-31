import type { Metadata } from "next";
import Link from "next/link";
import { parsePhoneNumberFromString } from "libphonenumber-js";
import { fetchIndexable, fetchSeoNumbers, lookupNumber, type LookupResult, type ReasonCategory } from "@/lib/api";
import { countryFromCode } from "@/lib/countries";
import { config } from "@/lib/config";
import { getDict } from "@/i18n/dictionaries";
import { alternatesFor, toDictLocale, urlLocales } from "@/i18n/locales";
import { ShieldIcon, CheckCircleIcon, BlockIcon, BellIcon, ListIcon } from "@/components/Icons";
import { NumberVote } from "@/components/NumberVote";
import { NumberContactActions } from "@/components/NumberContactActions";

// Pre-render the quality pages at build; others render on-demand (and noindex).
export async function generateStaticParams() {
  const numbers = await fetchSeoNumbers(2000);
  const params: { locale: string; phone: string }[] = [];
  for (const l of urlLocales) {
    for (const n of numbers) params.push({ locale: l, phone: n.phone });
  }
  return params;
}

function fmt(phone: string) {
  return `+${phone}`;
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string; phone: string }>;
}): Promise<Metadata> {
  const { locale, phone } = await params;
  const t = getDict(toDictLocale(locale)).seo;
  const { indexable } = await fetchIndexable(phone);
  const display = fmt(phone);
  return {
    title: t.numberTitle.replace("{phone}", display),
    description: t.numberDesc.replace("{phone}", display),
    alternates: alternatesFor(locale, `/numero/${phone}`),
    robots: indexable ? { index: true, follow: true } : { index: false, follow: true },
  };
}

export default async function NumberPage({
  params,
}: {
  params: Promise<{ locale: string; phone: string }>;
}) {
  const { locale, phone } = await params;
  const lang = toDictLocale(locale);
  const d = getDict(lang);
  const t = d.seo;
  const labels = d.tool.labels;
  const cats = d.tool.cats;
  const catsList = ["telemarketing", "scam", "robocall", "silent", "debt", "survey", "unknown"] as const;
  const base = `/${locale}`;

  const [{ isArcep: seoIsArcep, number }, lookup] = await Promise.all([
    fetchIndexable(phone),
    lookupNumber(phone).catch(() => null),
  ]);

  const parsed = parsePhoneNumberFromString("+" + phone);
  const country = parsed?.country ? countryFromCode(parsed.country) : null;
  const intlFormat = parsed?.formatInternational() ?? `+${phone}`;
  const nationalFormat = parsed?.formatNational() ?? phone;
  const e164 = parsed?.number ?? `+${phone}`;
  const display = intlFormat;

  const dateLocale = locale === "fr-FR" ? "fr-FR" : "en-US";
  const fmtDate = (iso: string | null | undefined) => {
    if (!iso) return t.never;
    try {
      return new Intl.DateTimeFormat(dateLocale, {
        day: "numeric",
        month: "short",
        year: "numeric",
      }).format(new Date(iso));
    } catch {
      return t.never;
    }
  };

  const status = (lookup?.status ?? number?.status ?? "unknown") as "block" | "warn" | "allow" | "unknown";
  const source = lookup?.source ?? (seoIsArcep ? "arcep" : number ? "community" : "none");
  const isArcep = source === "arcep" || source === "mixed" || seoIsArcep;
  const hasCommunityData =
    source === "community" ||
    source === "mixed" ||
    (lookup?.reportCountSpam ?? number?.reportCountSpam ?? 0) > 0 ||
    (lookup?.reportCountLegit ?? number?.reportCountLegit ?? 0) > 0;
  const hasDetail = lookup !== null || number !== null;
  const blocked = status === "block";
  const accentBar = {
    block: "bg-coral",
    warn: "bg-amber",
    allow: "bg-emerald",
    unknown: "bg-night/30",
  }[status];
  const statusColor = {
    block: "text-coral",
    warn: "text-amber",
    allow: "text-emerald",
    unknown: "text-blue",
  }[status];
  const pill = {
    block: "bg-coral/10 text-coral",
    warn: "bg-amber/10 text-amber",
    allow: "bg-emerald/10 text-emerald",
    unknown: "bg-night/[0.06] text-night/70",
  }[status];
  const StatusIcon =
    blocked ? BlockIcon : status === "warn" ? BellIcon : status === "allow" ? CheckCircleIcon : ShieldIcon;

  const spamCount = lookup?.reportCountSpam ?? number?.reportCountSpam ?? 0;
  const legitCount = lookup?.reportCountLegit ?? number?.reportCountLegit ?? 0;
  const confidenceLevel = lookup?.confidenceLevel ?? (isArcep ? "official" : "none");
  const confidence = lookup?.confidence;

  const freq = lookup?.frequency;
  const chartBuckets = freq
    ? [
        { label: t.period24h, value: freq.last24h },
        { label: t.period7d, value: freq.last7d },
        { label: t.period30d, value: freq.last30d },
        { label: t.period1y, value: freq.last1y },
      ]
    : [];
  const chartMax = Math.max(...chartBuckets.map((b) => b.value), 1);
  const hasChartData = chartBuckets.some((b) => b.value > 0);

  const reasons: Record<ReasonCategory, number> = lookup?.reasons ?? {
    telemarketing: 0, scam: 0, robocall: 0, silent: 0, debt: 0, survey: 0, unknown: 0,
  };
  const catEntries = catsList
    .filter((c) => reasons[c] > 0)
    .map((c) => ({ key: c, label: cats[c], count: reasons[c] }))
    .sort((a, b) => b.count - a.count);
  const catTotal = catEntries.reduce((s, e) => s + e.count, 0);

  const hasDates = !!lookup && (!!lookup.firstReportedAt || !!lookup.lastReportedAt);

  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: [
      {
        "@type": "Question",
        name: t.whoCalls.replace("{phone}", display),
        acceptedAnswer: {
          "@type": "Answer",
          text: isArcep
            ? t.arcepNote
            : number
              ? `${t.communityNote} (${spamCount} ${t.spamReports.toLowerCase()}, ${legitCount} ${t.legitReports.toLowerCase()}).`
              : t.noData,
        },
      },
    ],
  };

  return (
    <article className="mx-auto max-w-5xl px-4 py-8 sm:py-12">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />

      <h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
        {t.whoCalls.replace("{phone}", display)}
      </h1>

      {hasDetail ? (
        <div className="mt-6 space-y-4">
          {/* Hero card — status accent bar + number, score, counts */}
          <div className="flex overflow-hidden rounded-2xl border border-hair">
            <div className={`w-1.5 shrink-0 ${accentBar}`} />
            <div className="flex-1 p-5 sm:p-6">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  {country && <span className="text-3xl">{country.flag}</span>}
                  <span className="text-xl font-bold tracking-tight sm:text-2xl">{display}</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-semibold ${pill}`}>
                    <StatusIcon className="h-4 w-4" /> {labels[status]}
                  </span>
                  {isArcep && (
                  <span className="inline-flex items-center gap-1.5 rounded-full border border-amber/30 bg-amber/10 px-3 py-1 text-sm font-semibold text-amber">
                    <ListIcon className="h-4 w-4" /> {t.arcepBadge}
                  </span>
                  )}
                </div>
              </div>

              <div className="mt-5 flex flex-wrap items-end gap-x-8 gap-y-4">
                <div>
                  <div className={`text-4xl font-extrabold leading-none sm:text-5xl ${statusColor}`}>
                    {d.tool.riskLevels[status]}
                  </div>
                  <div className="mt-1 text-xs font-medium uppercase tracking-wide text-night/40">
                    {d.tool.risk}
                  </div>
                </div>
                {(spamCount > 0 || legitCount > 0) && (
                  <div className="text-sm text-night/60">
                    <span className="font-semibold text-coral">{spamCount}</span>
                    {" "}{t.spamReports.toLowerCase()}
                    <span className="mx-1.5 text-night/30">·</span>
                    <span className="font-semibold text-emerald">{legitCount}</span>
                    {" "}{t.legitReports.toLowerCase()}
                  </div>
                )}
              </div>

              <p className="mt-4 text-sm text-night/60">
                {source === "mixed"
                  ? `${t.arcepNote} ${t.communityNote}`
                  : isArcep
                    ? t.arcepNote
                    : hasCommunityData
                      ? t.communityNote
                      : t.noData}
              </p>
              {isArcep && (
                <p className="mt-2 text-sm">
                  <a
                    href={config.links.arcepSource}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="font-semibold text-night/70 underline hover:text-night"
                  >
                    {t.arcepSource}
                  </a>
                </p>
              )}
            </div>
          </div>

          <NumberContactActions
            phone={phone}
            risky={status === "block" || status === "warn"}
            title={t.contactNumber}
            callLabel={t.callNumber}
            smsLabel={t.smsNumber}
            warning={t.riskyContactWarning}
          />

          <div className="grid gap-4 sm:grid-cols-2">
            <section className="rounded-2xl border border-hair p-5">
              <h2 className="text-xs font-semibold uppercase tracking-wide text-night/40">
                {t.reports}
              </h2>
              <div className="mt-3 flex gap-8">
                <div>
                  <div className="text-2xl font-bold text-coral">{spamCount}</div>
                  <div className="text-xs text-night/50">{t.spamReports}</div>
                </div>
                <div>
                  <div className="text-2xl font-bold text-emerald">{legitCount}</div>
                  <div className="text-xs text-night/50">{t.legitReports}</div>
                </div>
              </div>
              <p className="mt-3 text-xs leading-relaxed text-night/50">{t.rawVotesNote}</p>
            </section>

            <section className="rounded-2xl border border-hair p-5">
              <h2 className="text-xs font-semibold uppercase tracking-wide text-night/40">
                {t.confidence}
              </h2>
              <p className="mt-3 text-xl font-bold">
                {t.confidenceLevels[confidenceLevel]}
                {typeof confidence === "number" && confidenceLevel !== "none" ? ` · ${confidence} %` : ""}
              </p>
              <p className="mt-2 text-sm leading-relaxed text-night/55">
                {t.confidenceNotes[confidenceLevel]}
              </p>
            </section>
          </div>

          {/* Detail grid — country/formats | categories */}
          {!isArcep && (
            <div className="grid gap-4 sm:grid-cols-2">
              {/* Country + formats */}
              <div className="rounded-2xl border border-hair p-5">
                <h2 className="text-xs font-semibold uppercase tracking-wide text-night/40">
                  {country ? t.country : t.formats}
                </h2>
                {country && (
                  <div className="mt-2 flex items-center gap-2">
                    <span className="text-2xl">{country.flag}</span>
                    <span className="text-lg font-bold">{country.name}</span>
                  </div>
                )}
                <div className={`mt-3 space-y-1.5 border-t border-hair ${country ? "pt-3" : ""}`}>
                  <FormatRow label={t.formatIntl} value={intlFormat} />
                  <FormatRow label={t.formatNational} value={nationalFormat} />
                  <FormatRow label={t.formatE164} value={e164} />
                </div>
              </div>

              {/* Categories */}
              {catEntries.length > 0 ? (
                <div className="rounded-2xl border border-hair p-5">
                  <h2 className="text-xs font-semibold uppercase tracking-wide text-night/40">
                    {t.reportCategories}
                  </h2>
                  <div className="mt-3 space-y-3">
                    {catEntries.map((e) => (
                      <div key={e.key}>
                        <div className="flex items-center justify-between text-sm">
                          <span className="font-medium">{e.label}</span>
                          <span className="tabular-nums text-night/50">{e.count}</span>
                        </div>
                        <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-night/[0.06]">
                          <div
                            className="h-full rounded-full bg-coral/60"
                            style={{ width: `${(e.count / catTotal) * 100}%` }}
                          />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="rounded-2xl border border-hair p-5">
                  <h2 className="text-xs font-semibold uppercase tracking-wide text-night/40">
                    {t.reportCategories}
                  </h2>
                  <p className="mt-3 text-sm text-night/40">{t.categoriesNa}</p>
                </div>
              )}
            </div>
          )}

          {/* Activity timeline — dates + chart */}
          {!isArcep && (hasDates || hasChartData) && (
            <div className="rounded-2xl border border-hair p-5 sm:p-6">
              <h2 className="text-xs font-semibold uppercase tracking-wide text-night/40">
                {t.history}
              </h2>

              {/* Date timeline */}
              {hasDates && lookup && (
                <div className="mt-4 flex items-center gap-4">
                  <div className="text-center">
                    <div className="text-[0.7rem] uppercase tracking-wide text-night/40">
                      {t.firstReport}
                    </div>
                    <div className="text-sm font-bold">{fmtDate(lookup.firstReportedAt)}</div>
                  </div>
                  <div className="flex-1 border-t border-dashed border-hair" />
                  <div className="text-center">
                    <div className="text-[0.7rem] uppercase tracking-wide text-night/40">
                      {t.lastReport}
                    </div>
                    <div className="text-sm font-bold">{fmtDate(lookup.lastReportedAt)}</div>
                  </div>
                </div>
              )}

              {/* Frequency chart */}
              {hasChartData && (
                <div className="mt-5">
                  <div className="flex items-end gap-2 sm:gap-3" style={{ height: "6rem" }}>
                    {chartBuckets.map((b, i) => (
                      <div key={i} className="flex flex-1 flex-col items-center gap-1.5">
                        <span className="text-xs font-bold tabular-nums text-night/60">{b.value}</span>
                        <div className="flex w-full flex-1 items-end">
                          <div
                            className={`w-full rounded-t-md transition-all ${
                              b.value > 0 ? "bg-coral/50" : "bg-night/[0.05]"
                            }`}
                            style={{
                              height: `${Math.max((b.value / chartMax) * 100, b.value > 0 ? 8 : 3)}%`,
                            }}
                          />
                        </div>
                        <span className="whitespace-nowrap text-[0.65rem] text-night/40">{b.label}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          <NumberVote
            phone={phone}
            locale={lang}
            title={t.yourOpinion}
            note={t.opinionNote}
            spamLabel={t.spamReports}
            legitLabel={t.legitReports}
            savedMessage={t.voteSaved}
            errorMessage={t.voteError}
          />

          {/* Advice CTA */}
          <section className="rounded-2xl bg-night p-5 text-white sm:p-6">
            <h2 className="text-lg font-bold">{t.advice}</h2>
            <p className="mt-1.5 text-sm text-white/70">{t.adviceBody}</p>
            <div className="mt-4 flex flex-wrap gap-3">
              <Link
                href={`${base}#telecharger`}
                className="rounded-xl bg-amber px-5 py-2.5 text-sm font-bold text-night transition hover:brightness-95"
              >
                {t.blockCta}
              </Link>
              <Link
                href={`${base}/signaler?phone=${phone}`}
                className="rounded-xl border border-white/25 px-5 py-2.5 text-sm font-bold text-white transition hover:bg-white/10"
              >
                {t.reportCta}
              </Link>
            </div>
          </section>
        </div>
      ) : (
        <div className="mt-6 rounded-2xl border border-hair p-6 text-center">
          <p className="text-night/60">{t.noData}</p>
          <Link
            href={`${base}/signaler?phone=${phone}`}
            className="mt-4 inline-block rounded-xl bg-night px-5 py-3 font-semibold text-white transition hover:bg-night-dark"
          >
            {t.reportCta}
          </Link>
        </div>
      )}
    </article>
  );
}

function FormatRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-2 text-sm">
      <span className="text-night/40">{label}</span>
      <span className="font-medium tabular-nums">{value}</span>
    </div>
  );
}

// Allow on-demand rendering of pages not in generateStaticParams.
export const dynamicParams = true;
