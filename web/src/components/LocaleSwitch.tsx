"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { urlLocales, type UrlLocale } from "@/i18n/locales";

const FLAGS: Record<UrlLocale, string> = { "fr-FR": "🇫🇷", "en-US": "🇬🇧" };
const SHORT: Record<UrlLocale, string> = { "fr-FR": "FR", "en-US": "EN" };

/** Swaps the locale segment of the current path, keeping the rest. */
export function LocaleSwitch({ current }: { current: UrlLocale }) {
  const pathname = usePathname() || `/${current}`;

  function pathFor(target: UrlLocale): string {
    const parts = pathname.split("/");
    // parts[0] = "", parts[1] = locale
    if (urlLocales.includes(parts[1] as UrlLocale)) {
      parts[1] = target;
      return parts.join("/") || `/${target}`;
    }
    return `/${target}`;
  }

  return (
    <div className="flex items-center gap-1 rounded-lg border border-hair p-0.5">
      {urlLocales.map((l) => (
        <Link
          key={l}
          href={pathFor(l)}
          aria-label={SHORT[l]}
          className={`flex items-center gap-1 rounded-md px-2 py-1 text-xs font-semibold transition ${
            l === current ? "bg-night text-white" : "text-night/60 hover:text-night"
          }`}
        >
          <span className="hidden sm:inline">{FLAGS[l]}</span>
          <span>{SHORT[l]}</span>
        </Link>
      ))}
    </div>
  );
}
