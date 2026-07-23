import type { Metadata } from "next";
import { Suspense } from "react";
import { NumberTool } from "@/components/NumberTool";
import { getDict } from "@/i18n/dictionaries";
import { toDictLocale } from "@/i18n/locales";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const t = getDict(toDictLocale(locale));
  return {
    title: t.meta.reportTitle,
    description: t.meta.reportDescription,
    alternates: { canonical: `/${locale}/signaler` },
  };
}

export default async function SignalerPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = getDict(toDictLocale(locale));
  return (
    <section className="mx-auto max-w-2xl px-4 py-12">
      <h1 className="text-3xl font-extrabold tracking-tight">{t.meta.reportTitle}</h1>
      <p className="mt-2 text-night/70">{t.meta.reportDescription}</p>
      <div className="mt-6">
        <Suspense>
          <NumberTool dict={t} locale={locale} defaultMode="report" />
        </Suspense>
      </div>
    </section>
  );
}
