import type { Metadata } from "next";
import Link from "next/link";
import { fetchPrefix, fetchPrefixes } from "@/lib/api";
import { config } from "@/lib/config";
import { getDict } from "@/i18n/dictionaries";
import { alternatesFor, toDictLocale, urlLocales } from "@/i18n/locales";

export const revalidate = 3600;
export const dynamicParams = true;

export async function generateStaticParams() {
  const prefixes = await fetchPrefixes();
  const params: { locale: string; intl: string }[] = [];
  for (const l of urlLocales) for (const p of prefixes) params.push({ locale: l, intl: p.intl });
  return params;
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string; intl: string }>;
}): Promise<Metadata> {
  const { locale, intl } = await params;
  const t = getDict(toDictLocale(locale)).prefix;
  const { exists, prefix } = await fetchPrefix(intl);
  const display = prefix?.display ?? `+${intl}`;
  return {
    title: t.title.replace("{display}", display),
    description: t.desc.replace("{display}", display),
    alternates: alternatesFor(locale, `/prefixe/${intl}`),
    // Index only real ARCEP prefixes (aggregate, no personal data → RGPD-safe).
    robots: exists ? { index: true, follow: true } : { index: false, follow: true },
  };
}

export default async function PrefixPage({
  params,
}: {
  params: Promise<{ locale: string; intl: string }>;
}) {
  const { locale, intl } = await params;
  const t = getDict(toDictLocale(locale)).prefix;
  const { prefix, numbers } = await fetchPrefix(intl);
  const display = prefix?.display ?? `+${intl}`;
  const base = `/${locale}`;

  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: [
      {
        "@type": "Question",
        name: t.title.replace("{display}", display),
        acceptedAnswer: { "@type": "Answer", text: t.arcepNote.replace("{display}", display) },
      },
    ],
  };

  return (
    <article className="mx-auto max-w-2xl px-4 py-12">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, "\\u003c") }} />
      <Link href={`${base}/prefixe`} className="text-sm text-night/60 hover:text-night">
        ← {t.allPrefixes}
      </Link>
      <h1 className="mt-3 text-3xl font-extrabold tracking-tight">
        {t.heading.replace("{display}", display)}
      </h1>
      <div className="mt-4 rounded-2xl border border-amber/30 bg-amber/[0.06] p-5">
        <p className="text-night/80">{t.arcepNote.replace("{display}", display)}</p>
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
      </div>

      <section className="mt-8">
        <h2 className="text-xl font-bold">{t.reportedNumbers}</h2>
        {numbers.length === 0 ? (
          <p className="mt-2 text-night/60">{t.noNumbers}</p>
        ) : (
          <ul className="mt-4 divide-y divide-hair rounded-2xl border border-hair">
            {numbers.map((n) => (
              <li key={n.phone}>
                <Link
                  href={`${base}/numero/${n.phone}`}
                  className="flex items-center justify-between px-4 py-3 hover:bg-night/[0.03]"
                >
                  <span className="font-semibold">+{n.phone}</span>
                  <span className="text-sm text-night/60">
                    {n.reportCountSpam} · {t.seeNumber} →
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </article>
  );
}
