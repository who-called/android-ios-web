import { NextRequest, NextResponse } from "next/server";
import {
  fallbackUrlLocale,
  isPublished,
  localeForPhone,
  negotiateLocale,
  urlLocales,
} from "@/i18n/locales";

const PUBLIC_FILE = /\.(.*)$/;

/**
 * Locale routing: every page lives under /<locale>/... (e.g. /fr-FR/verifier).
 * Requests without a locale prefix are redirected to, in order:
 *   1. the best published Accept-Language match (drafts are never auto-selected);
 *   2. on /numero/<phone> and /prefixe/<intl>, the language of the number's
 *      country (a +34 number → es-ES);
 *   3. English (en-US).
 */
export function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;

  // Skip Next internals, API, and static files.
  if (
    pathname.startsWith("/_next") ||
    pathname.startsWith("/api") ||
    pathname === "/healthz" ||
    pathname === "/sitemap.xml" ||
    pathname === "/robots.txt" ||
    pathname === "/manifest.webmanifest" ||
    PUBLIC_FILE.test(pathname)
  ) {
    return;
  }

  // Already localized?
  const current = urlLocales.find(
    (l) => pathname === `/${l}` || pathname.startsWith(`/${l}/`),
  );
  if (current) {
    // Draft locales: reachable for review, but never indexed — a header covers
    // every page, whatever robots value the page's own metadata sets.
    if (!isPublished(current)) {
      const res = NextResponse.next();
      res.headers.set("X-Robots-Tag", "noindex, nofollow");
      return res;
    }
    return;
  }

  const phone = /^\/(?:numero|prefixe)\/\+?(\d+)/.exec(pathname)?.[1];
  const locale =
    negotiateLocale(req.headers.get("accept-language")) ??
    (phone ? localeForPhone(phone) : null) ??
    fallbackUrlLocale;

  const url = req.nextUrl.clone();
  url.pathname = `/${locale}${pathname === "/" ? "" : pathname}`;
  // 308 permanent: lets Google consolidate "/" → "/<locale>" instead of
  // reporting a lingering temporary redirect (default is 307).
  return NextResponse.redirect(url, 308);
}

export const config = {
  matcher: ["/((?!_next|api|.*\\..*).*)"],
};
