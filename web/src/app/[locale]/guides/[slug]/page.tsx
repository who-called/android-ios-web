import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getDict } from "@/i18n/dictionaries";
import { alternatesFor, toDictLocale, urlLocales } from "@/i18n/locales";

export function generateStaticParams() {
  // Slugs are shared across locales; build all combinations.
  const slugs = getDict("fr").guides.items.map((g) => g.slug);
  const params: { locale: string; slug: string }[] = [];
  for (const locale of urlLocales) for (const slug of slugs) params.push({ locale, slug });
  return params;
}

function findGuide(locale: string, slug: string) {
  return getDict(toDictLocale(locale)).guides.items.find((g) => g.slug === slug);
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string; slug: string }>;
}): Promise<Metadata> {
  const { locale, slug } = await params;
  const g = findGuide(locale, slug);
  if (!g) return {};
  return {
    title: g.title,
    description: g.excerpt,
    alternates: alternatesFor(locale, `/guides/${slug}`),
  };
}

export default async function GuidePage({
  params,
}: {
  params: Promise<{ locale: string; slug: string }>;
}) {
  const { locale, slug } = await params;
  const g = findGuide(locale, slug);
  if (!g) notFound();
  const t = getDict(toDictLocale(locale));
  const base = `/${locale}`;

  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "Article",
    headline: g.title,
    description: g.excerpt,
  };

  return (
    <article className="mx-auto max-w-2xl px-4 py-12">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />
      <Link href={`${base}/guides`} className="text-sm text-night/60 hover:text-night">
        ← {t.guides.indexTitle}
      </Link>
      <h1 className="mt-3 text-3xl font-extrabold tracking-tight">{g.title}</h1>
      <div className="mt-6 space-y-4 text-lg leading-relaxed text-night/80">
        {g.body.map((p, i) => (
          <p key={i}>{p}</p>
        ))}
      </div>
      <div className="mt-10 rounded-2xl bg-night p-6 text-white">
        <p className="font-semibold">{t.cta.title}</p>
        <div className="mt-4 flex flex-wrap gap-3">
          <Link href={`${base}#telecharger`} className="rounded-xl bg-amber px-4 py-2 font-semibold text-night">
            {t.nav.download}
          </Link>
          <Link href={`${base}/verifier`} className="rounded-xl border border-white/30 px-4 py-2 font-semibold">
            {t.cta.verify}
          </Link>
        </div>
      </div>
    </article>
  );
}
