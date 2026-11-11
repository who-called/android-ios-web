"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { isUrlLocale, localeInfo, publishedLocales, type UrlLocale } from "@/i18n/locales";

/**
 * Language picker. Shows published locales (plus the current one when a
 * reviewer is browsing a draft locale). Swaps the locale segment of the
 * current path, keeping the rest. Native <details> → no JS state, works
 * without hydration, scales past 2 languages.
 */
export function LocaleSwitch({ current }: { current: UrlLocale }) {
  const pathname = usePathname() || `/${current}`;
  const options: UrlLocale[] = publishedLocales.includes(current)
    ? publishedLocales
    : [...publishedLocales, current];
  const cur = localeInfo(current);

  function pathFor(target: UrlLocale): string {
    const parts = pathname.split("/");
    // parts[0] = "", parts[1] = locale
    if (isUrlLocale(parts[1])) {
      parts[1] = target;
      return parts.join("/") || `/${target}`;
    }
    return `/${target}`;
  }

  return (
    <details className="group relative">
      <summary
        aria-label={cur.label}
        className="flex cursor-pointer list-none items-center gap-1 rounded-lg border border-hair px-2 py-1 text-xs font-semibold text-night/80 transition hover:text-night [&::-webkit-details-marker]:hidden"
      >
        <span className="hidden sm:inline">{cur.flag}</span>
        <span>{cur.short}</span>
        <span aria-hidden className="text-night/40 transition group-open:rotate-180">▾</span>
      </summary>
      <ul className="absolute right-0 z-50 mt-1 min-w-[9rem] overflow-hidden rounded-lg border border-hair bg-white py-1 shadow-lg">
        {options.map((l) => {
          const info = localeInfo(l);
          return (
            <li key={l}>
              <Link
                href={pathFor(l)}
                hrefLang={l}
                lang={l}
                aria-current={l === current ? "true" : undefined}
                // The header persists across client navigations: close the menu.
                onClick={(e) => e.currentTarget.closest("details")?.removeAttribute("open")}
                className={`flex items-center gap-2 px-3 py-1.5 text-sm transition ${
                  l === current ? "bg-night text-white" : "text-night/80 hover:bg-night/[0.05]"
                }`}
              >
                <span>{info.flag}</span>
                <span>{info.label}</span>
                {!info.published && <span className="ml-auto text-[0.65rem] uppercase opacity-60">draft</span>}
              </Link>
            </li>
          );
        })}
      </ul>
    </details>
  );
}
