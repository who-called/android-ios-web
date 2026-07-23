import type { Metadata } from "next";
import { getPolicy } from "@/i18n/legal";
import { toDictLocale } from "@/i18n/locales";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const doc = getPolicy(toDictLocale(locale));
  return {
    title: doc.title,
    alternates: { canonical: `/${locale}/policy` },
  };
}

export default async function PolicyPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const lang = toDictLocale(locale);
  const doc = getPolicy(lang);
  const updatedLabel = lang === "en" ? "Last updated" : "Dernière mise à jour";

  return (
    <article className="mx-auto max-w-3xl px-4 py-12">
      <h1 className="text-3xl font-extrabold">{doc.title}</h1>
      <p className="mt-2 text-sm text-night/60">
        {updatedLabel} : {doc.updated}
      </p>
      {doc.sections.map((s) => (
        <section key={s.title} className="mt-6">
          <h2 className="text-xl font-bold">{s.title}</h2>
          <p className="mt-2 text-night/80">{s.body}</p>
        </section>
      ))}
    </article>
  );
}
