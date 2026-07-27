export const config = {
  apiBaseUrl:
    process.env.NEXT_PUBLIC_API_BASE_URL ?? "https://api.who-called.com/api/v1",
  siteUrl: process.env.NEXT_PUBLIC_SITE_URL ?? "https://www.who-called.com",
  // Store + social links (single source of truth).
  links: {
    playStore: "https://play.google.com/store/apps/details?id=com.devfi.whocalled",
    repo: "https://github.com/who-called/android-ios-web",
    contact: "contact@who-called.com",
    privacy: "/privacy",
    policy: "/policy",
    // Official government source for the telemarketing prefixes (store policies:
    // government info requires a visible link to the original source).
    arcepSource:
      "https://www.arcep.fr/la-regulation/grands-dossiers-thematiques-transverses/la-numerotation.html",
  },
};
