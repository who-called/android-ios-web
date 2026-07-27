import type { Metadata } from "next";
import { Suspense } from "react";
import { NumberTool } from "@/components/NumberTool";
import { getDict } from "@/i18n/dictionaries";
import { alternatesFor, toDictLocale } from "@/i18n/locales";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const t = getDict(toDictLocale(locale));
  return {
    title: t.meta.verifyTitle,
    description: t.meta.verifyDescription,
    alternates: alternatesFor(locale, "/verifier"),
  };
}

export default async function VerifierPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = getDict(toDictLocale(locale));
  return (
    <section className="mx-auto max-w-2xl px-4 py-12">
      <h1 className="text-3xl font-extrabold tracking-tight">{t.meta.verifyTitle}</h1>
      <p className="mt-2 text-night/70">{t.meta.verifyDescription}</p>
      <div className="mt-6">
        <Suspense>
          <NumberTool dict={t} locale={locale} defaultMode="lookup" />
        </Suspense>
      </div>
    </section>
  );
}
