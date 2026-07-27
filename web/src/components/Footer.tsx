import Link from "next/link";
import { config } from "@/lib/config";
import { getDict } from "@/i18n/dictionaries";
import { toDictLocale, type UrlLocale } from "@/i18n/locales";

export function Footer({ locale }: { locale: UrlLocale }) {
  const t = getDict(toDictLocale(locale));
  const base = `/${locale}`;
  return (
    <footer className="border-t border-hair">
      <div className="mx-auto flex max-w-5xl flex-col gap-4 px-4 py-8 text-sm text-night/70 sm:flex-row sm:items-center sm:justify-between">
        <p>© {new Date().getFullYear()} Who Called — {t.footer.rights}</p>
        <nav className="flex flex-wrap gap-4">
          <Link href={`${base}/verifier`} className="hover:text-night">{t.footer.verify}</Link>
          <Link href={`${base}/signaler`} className="hover:text-night">{t.footer.report}</Link>
          <Link href={`${base}/tendances`} className="hover:text-night">{t.nav.trends}</Link>
          <Link href={`${base}/guides`} className="hover:text-night">{t.nav.guides}</Link>
          <Link href={`${base}/prefixe`} className="hover:text-night">{t.nav.prefixes}</Link>
          <Link href={`${base}/privacy`} className="hover:text-night">{t.footer.privacy}</Link>
          <Link href={`${base}/policy`} className="hover:text-night">{t.footer.policy}</Link>
          <Link href={`${base}/support`} className="hover:text-night">{t.footer.support}</Link>
          <Link href={`${base}/data-deletion`} className="hover:text-night">{t.footer.deleteData}</Link>
          <a href={config.links.repo} className="hover:text-night">{t.footer.source}</a>
          <a href={`mailto:${config.links.contact}`} className="hover:text-night">{t.footer.contact}</a>
        </nav>
      </div>
      {/* Government-info compliance: official source link + non-affiliation disclaimer. */}
      <div className="mx-auto max-w-5xl px-4 pb-8 text-xs text-night/50">
        <p>
          {t.footer.disclaimer}{" "}
          <a
            href={config.links.arcepSource}
            target="_blank"
            rel="noopener noreferrer"
            className="underline hover:text-night"
          >
            {t.footer.arcepSource}
          </a>
        </p>
      </div>
    </footer>
  );
}
