import type { Metadata } from "next";
import { getDataDeletion } from "@/i18n/legal";
import { alternatesFor, toDictLocale } from "@/i18n/locales";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const doc = getDataDeletion(toDictLocale(locale));
  return {
    title: doc.title,
    description: doc.intro,
    alternates: alternatesFor(locale, "/data-deletion"),
    robots: { index: true, follow: true },
  };
}

export default async function DataDeletionPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const lang = toDictLocale(locale);
  const doc = getDataDeletion(lang);
  const updatedLabel = lang === "en" ? "Last updated" : "Dernière mise à jour";

  return (
    <article className="mx-auto max-w-3xl px-4 py-12">
      <h1 className="text-3xl font-extrabold">{doc.title}</h1>
      <p className="mt-2 text-sm text-night/60">
        {updatedLabel} : {doc.updated}
      </p>
      {doc.intro && <p className="mt-6 text-night/80">{doc.intro}</p>}
      {doc.sections.map((s) => (
        <section key={s.title} className="mt-6">
          <h2 className="text-xl font-bold">{s.title}</h2>
          <p className="mt-2 text-night/80">{s.body}</p>
        </section>
      ))}
    </article>
  );
}
