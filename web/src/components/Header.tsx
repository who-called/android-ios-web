import Link from "next/link";
import { Logo } from "./Logo";
import { LocaleSwitch } from "./LocaleSwitch";
import { getDict } from "@/i18n/dictionaries";
import { toDictLocale, type UrlLocale } from "@/i18n/locales";

export function Header({ locale }: { locale: UrlLocale }) {
  const t = getDict(toDictLocale(locale));
  const base = `/${locale}`;
  return (
    <header className="sticky top-0 z-50 border-b border-hair bg-white/90 backdrop-blur">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
        <Link href={base} className="flex items-center gap-2">
          <Logo className="h-7 w-7" />
          <span className="text-lg font-bold tracking-tight">Who Called</span>
        </Link>
        <nav className="flex items-center gap-2 text-sm font-medium sm:gap-4">
          <Link href={`${base}/verifier`} className="hidden hover:text-night/70 sm:inline">
            {t.nav.verify}
          </Link>
          <Link href={`${base}/signaler`} className="hidden hover:text-night/70 sm:inline">
            {t.nav.report}
          </Link>
          <Link href={`${base}/tendances`} className="hidden hover:text-night/70 sm:inline">
            {t.nav.trends}
          </Link>
          <Link href={`${base}/guides`} className="hidden hover:text-night/70 sm:inline">
            {t.nav.guides}
          </Link>
          <Link
            href={`${base}#telecharger`}
            className="rounded-lg bg-night px-3 py-1.5 text-white hover:bg-night-dark"
          >
            {t.nav.download}
          </Link>
          <LocaleSwitch current={locale} />
        </nav>
      </div>
    </header>
  );
}
