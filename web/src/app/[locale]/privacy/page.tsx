import type { Metadata } from "next";
import { getDict } from "@/i18n/dictionaries";
import { getPrivacy } from "@/i18n/legal";
import { alternatesFor, toDictLocale } from "@/i18n/locales";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const doc = getPrivacy(toDictLocale(locale));
  return {
    title: doc.title,
    description: getDict(toDictLocale(locale)).meta.homeDescription,
    alternates: alternatesFor(locale, "/privacy"),
    robots: { index: true, follow: true },
  };
}

export default async function PrivacyPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const lang = toDictLocale(locale);
  const doc = getPrivacy(lang);
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
