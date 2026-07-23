# Who Called — Website

Marketing site + **verify / report a number** widget, wired to the Express backend.
**Next.js (App Router) · TypeScript · Tailwind.** Static-generated, SEO-ready.

## Run

```bash
cp .env.example .env.local        # set API + site URL
npm install
npm run dev                       # http://localhost:3000
npm run build && npm run start    # production
```

> The site runs on port 3000 by default — same as the backend. In local dev, run
> the backend on 3000 and the site on another port (`PORT=3001 npm run dev`), or
> point `NEXT_PUBLIC_API_BASE_URL` at your deployed API.

## Features

- **Hero + features + how-it-works** with store badges (Play / App Store links in `lib/config.ts`).
- **Verify a number** (`/verifier`) and **Report a number** (`/signaler`) — the same
  flow as the app, calling `GET /lookup/:phone` and `POST /reports` directly. Anonymous
  (random web `deviceId` in localStorage).
- **Legal pages** `/privacy` and `/policy` (fill the `[…]` placeholders).
- **SEO**: per-page `title`/`description`, OpenGraph + Twitter cards, canonical URLs,
  `sitemap.xml`, `robots.txt`, web manifest, SVG favicon.

## Backend link (CORS)

The browser widget calls the API cross-origin, so the Express backend allows the
site's origin via `CORS_ORIGINS` (see `backend/.env.example`). Add your production
domain there before going live.

## Config (single source of truth)

`src/lib/config.ts` — API base URL, site URL, store/repo/contact links.
Set `NEXT_PUBLIC_API_BASE_URL` and `NEXT_PUBLIC_SITE_URL` per environment.

## To finish before launch

- Replace `[DATE]`, `[NOM]`, `[ADRESSE]`, `[HÉBERGEUR]`, `[LICENCE]` in `/privacy` and `/policy`.
- Add real store URLs (app IDs) in `lib/config.ts`.
- Add `public/og.png` (1200×630), `public/apple-touch-icon.png`, `public/icon-512.png`.
