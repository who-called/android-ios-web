export const config = {
  apiBaseUrl:
    process.env.NEXT_PUBLIC_API_BASE_URL ?? "https://api.who-called.com/api/v1",
  siteUrl: process.env.NEXT_PUBLIC_SITE_URL ?? "https://www.who-called.com",
  // Store + social links (single source of truth).
  links: {
    playStore: "https://play.google.com/store/apps/details?id=com.whocalled.android",
    appStore: "https://apps.apple.com/app/who-called/id000000000",
    repo: "https://github.com/who-called/android-ios-web",
    contact: "contact@who-called.com",
    privacy: "/privacy",
    policy: "/policy",
  },
};
