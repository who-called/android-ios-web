import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { config } from "@/lib/config";
import { Header } from "@/components/Header";
import { Footer } from "@/components/Footer";
import { getDict } from "@/i18n/dictionaries";
import { isUrlLocale, toDictLocale, ogLocale, urlLocales, type UrlLocale } from "@/i18n/locales";

export function generateStaticParams() {
  return urlLocales.map((locale) => ({ locale }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  if (!isUrlLocale(locale)) return {};
  const t = getDict(toDictLocale(locale));

  // hreflang alternates for SEO.
  const languages: Record<string, string> = {};
  for (const l of urlLocales) languages[l] = `${config.siteUrl}/${l}`;

  return {
    metadataBase: new URL(config.siteUrl),
    title: { default: t.meta.homeTitle, template: "%s — Who Called" },
    description: t.meta.homeDescription,
    applicationName: "Who Called",
    alternates: { canonical: `/${locale}`, languages },
    openGraph: {
      type: "website",
      locale: ogLocale(locale),
      url: `${config.siteUrl}/${locale}`,
      siteName: "Who Called",
      title: t.meta.homeTitle,
      description: t.meta.homeDescription,
      images: [{ url: "/og.png", width: 1200, height: 630, alt: "Who Called" }],
    },
    twitter: { card: "summary_large_image", images: ["/og.png"] },
    icons: { icon: "/favicon.svg", apple: "/apple-touch-icon.png" },
    robots: { index: true, follow: true },
  };
}

export default async function LocaleLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isUrlLocale(locale)) notFound();
  const lang = toDictLocale(locale as UrlLocale);

  return (
    <html lang={lang}>
      <body className="flex min-h-screen flex-col">
        <Header locale={locale as UrlLocale} />
        <main className="flex-1 overflow-x-hidden">{children}</main>
        <Footer locale={locale as UrlLocale} />
      </body>
    </html>
  );
}
