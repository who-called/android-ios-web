import type { Metadata } from "next";
import Link from "next/link";
import { getDict } from "@/i18n/dictionaries";
import { toDictLocale } from "@/i18n/locales";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const t = getDict(toDictLocale(locale)).guides;
  return {
    title: t.indexTitle,
    description: t.indexDesc,
    alternates: { canonical: `/${locale}/guides` },
  };
}

export default async function GuidesPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = getDict(toDictLocale(locale)).guides;
  const base = `/${locale}/guides`;

  return (
    <section className="mx-auto max-w-3xl px-4 py-12">
      <h1 className="text-3xl font-extrabold tracking-tight">{t.indexTitle}</h1>
      <p className="mt-2 text-night/70">{t.indexDesc}</p>
      <div className="mt-8 grid gap-4">
        {t.items.map((g) => (
          <Link
            key={g.slug}
            href={`${base}/${g.slug}`}
            className="rounded-2xl border border-hair p-6 transition hover:shadow-md"
          >
            <h2 className="text-xl font-bold">{g.title}</h2>
            <p className="mt-2 text-night/70">{g.excerpt}</p>
            <span className="mt-3 inline-block text-sm font-semibold text-night">{t.readMore} →</span>
          </Link>
        ))}
      </div>
    </section>
  );
}
