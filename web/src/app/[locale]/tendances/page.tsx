import type { Metadata } from "next";
import Link from "next/link";
import { fetchTrending, type TrendingNumber } from "@/lib/api";
import { getDict } from "@/i18n/dictionaries";
import { alternatesFor, toDictLocale } from "@/i18n/locales";
import { BlockIcon, BellIcon } from "@/components/Icons";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const t = getDict(toDictLocale(locale)).trends;
  return {
    title: t.metaTitle,
    description: t.metaDesc,
    alternates: alternatesFor(locale, "/tendances"),
  };
}

export default async function TendancesPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  const lang = toDictLocale(locale);
  const dict = getDict(lang);
  const t = dict.trends;
  const cats = dict.tool.cats;
  const numbers = await fetchTrending(7, 25);
  const base = `/${locale}`;

  return (
    <section className="mx-auto max-w-2xl px-4 py-12">
      <h1 className="text-3xl font-extrabold tracking-tight">{t.title}</h1>
      <p className="mt-2 text-night/70">{t.subtitle}</p>

      {numbers.length === 0 ? (
        <p className="mt-8 rounded-xl border border-hair bg-white px-4 py-6 text-center text-night/60">
          {t.empty}
        </p>
      ) : (
        <ol className="mt-6 space-y-3">
          {numbers.map((n, i) => (
            <TrendingRow
              key={n.phone}
              n={n}
              rank={i + 1}
              base={base}
              t={t}
              cats={cats}
            />
          ))}
        </ol>
      )}

      <p className="mt-6 text-xs text-night/50">{t.updated}</p>
    </section>
  );
}

function TrendingRow({
  n,
  rank,
  base,
  t,
  cats,
}: {
  n: TrendingNumber;
  rank: number;
  base: string;
  t: ReturnType<typeof getDict>["trends"];
  cats: ReturnType<typeof getDict>["tool"]["cats"];
}) {
  const blocked = n.status === "block";
  const color = blocked ? "text-coral" : n.status === "warn" ? "text-amber" : "text-night/70";
  const Icon = blocked ? BlockIcon : BellIcon;
  const reason =
    n.topReason && n.topReason.category !== "unknown"
      ? cats[n.topReason.category as keyof typeof cats]
      : null;

  return (
    <li className="flex items-center gap-4 rounded-xl border border-hair bg-white px-4 py-3 shadow-sm">
      <span className="w-6 shrink-0 text-center text-lg font-bold text-night/40">{rank}</span>
      <div className="min-w-0 flex-1">
        <Link
          href={`${base}/numero/${n.phone}`}
          className="font-semibold underline-offset-2 hover:underline"
        >
          +{n.phone}
        </Link>
        <div className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm text-night/60">
          <span className={`inline-flex items-center gap-1 font-medium ${color}`}>
            <Icon className="h-3.5 w-3.5" />
            {reason ?? cats.unknown}
          </span>
          <span aria-hidden>·</span>
          <span>
            {n.reportCount} {t.reportsCol}
          </span>
          {n.last24h > 0 && (
            <>
              <span aria-hidden>·</span>
              <span className="text-coral">{t.last24h.replace("{n}", String(n.last24h))}</span>
            </>
          )}
        </div>
      </div>
      <span className={`shrink-0 text-2xl font-extrabold ${blocked ? "text-coral" : "text-amber"}`}>
        {n.spamScore}
      </span>
    </li>
  );
}
