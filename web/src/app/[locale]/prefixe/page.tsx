import type { Metadata } from "next";
import Link from "next/link";
import { fetchPrefixes } from "@/lib/api";
import { getDict } from "@/i18n/dictionaries";
import { alternatesFor, toDictLocale } from "@/i18n/locales";

export const revalidate = 600;

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const t = getDict(toDictLocale(locale)).prefix;
  return {
    title: t.indexTitle,
    description: t.indexDesc,
    alternates: alternatesFor(locale, "/prefixe"),
  };
}

export default async function PrefixIndexPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  const t = getDict(toDictLocale(locale)).prefix;
  const prefixes = await fetchPrefixes();
  const base = `/${locale}/prefixe`;

  return (
    <section className="mx-auto max-w-3xl px-4 py-12">
      <h1 className="text-3xl font-extrabold tracking-tight">{t.indexTitle}</h1>
      <p className="mt-2 text-night/70">{t.indexDesc}</p>
      <div className="mt-8 grid grid-cols-2 gap-3 sm:grid-cols-3">
        {prefixes.map((p) => (
          <Link
            key={p.intl}
            href={`${base}/${p.intl}`}
            className="rounded-xl border border-hair px-4 py-3 text-center font-semibold transition hover:border-night/30 hover:shadow-sm"
          >
            {p.display}
          </Link>
        ))}
      </div>
      {prefixes.length === 0 && (
        <p className="mt-6 text-night/60">—</p>
      )}
    </section>
  );
}
