import { NextRequest, NextResponse } from "next/server";
import { urlLocales, defaultUrlLocale } from "@/i18n/locales";

const PUBLIC_FILE = /\.(.*)$/;

/**
 * Locale routing: every page lives under /<locale>/... (e.g. /fr-FR/verifier).
 * Requests without a locale prefix are redirected — to the Accept-Language
 * match if available, otherwise to the default (fr-FR).
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
  const hasLocale = urlLocales.some(
    (l) => pathname === `/${l}` || pathname.startsWith(`/${l}/`),
  );
  if (hasLocale) return;

  // Detect from Accept-Language, default to fr-FR.
  const accept = req.headers.get("accept-language") ?? "";
  const locale = accept.toLowerCase().startsWith("en") ? "en-US" : defaultUrlLocale;

  const url = req.nextUrl.clone();
  url.pathname = `/${locale}${pathname === "/" ? "" : pathname}`;
  return NextResponse.redirect(url);
}

export const config = {
  matcher: ["/((?!_next|api|.*\\..*).*)"],
};
