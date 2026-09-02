export const config = {
  port: parseInt(process.env.PORT ?? "3000", 10),
  scoring: {
    blockThreshold: parseInt(process.env.SCORE_BLOCK_THRESHOLD ?? "85", 10),
    warnThreshold: parseInt(process.env.SCORE_WARN_THRESHOLD ?? "60", 10),
    minReportsForBlock: parseFloat(process.env.MIN_REPORTS_FOR_BLOCK ?? "5"),

    // Time decay: a report's weight halves every N days.
    halfLifeDays: parseFloat(process.env.SCORE_HALF_LIFE_DAYS ?? "30"),

    // Velocity: >= threshold spam reports within the window → small score boost.
    velocityWindowDays: parseFloat(process.env.SCORE_VELOCITY_WINDOW_DAYS ?? "1"),
    velocityThreshold: parseInt(process.env.SCORE_VELOCITY_THRESHOLD ?? "5", 10),
    velocityBoost: parseInt(process.env.SCORE_VELOCITY_BOOST ?? "10", 10),

    // Reputation (anti-poisoning): how much agreeing/disagreeing with consensus
    // moves a device's weight, and the minimum weighted volume before consensus counts.
    reputationStep: parseFloat(process.env.REPUTATION_STEP ?? "0.1"),
    consensusMinWeight: parseFloat(process.env.CONSENSUS_MIN_WEIGHT ?? "3"),

    // Uncontradicted spam evidence needed before a number is shown as "warn"
    // (below the warn score band). 1.5 ≈ two fresh devices at default
    // reputation: a single anonymous report is displayed as "1 signalement"
    // but no longer flags someone's number for every user.
    minWeightForWarn: parseFloat(process.env.MIN_WEIGHT_FOR_WARN ?? "1.5"),
  },
  // SIA seed harmonization: imported aggregates enter scoring as a decaying
  // prior. `trust` (α) discounts external data vs our own; `halfLifeDays` makes
  // the prior fade slower than fresh reports so it bootstraps then yields to
  // live community data. `maxCount` caps a single byte-saturated count (255).
  sia: {
    trust: parseFloat(process.env.SIA_TRUST ?? "0.6"),
    halfLifeDays: parseFloat(process.env.SIA_HALF_LIFE_DAYS ?? "180"),
    neutralWeight: parseFloat(process.env.SIA_NEUTRAL_WEIGHT ?? "0.3"),
    maxCount: parseInt(process.env.SIA_MAX_COUNT ?? "255", 10),
  },
  antiAbuse: {
    maxReportsPerDevicePerDay: parseInt(
      process.env.MAX_REPORTS_PER_DEVICE_PER_DAY ?? "50",
      10
    ),
    // Brand-new deviceIds one IP may register per day (anti-Sybil). Known
    // devices are exempt, so shared NATs only feel it on fresh installs.
    maxNewDevicesPerIpPerDay: parseInt(process.env.MAX_NEW_DEVICES_PER_IP_PER_DAY ?? "20", 10),
  },
  privacy: {
    // Bearer token required by DELETE /privacy/number/:phone (third-party
    // erasure, handled by the operator after an email request). Unset = the
    // endpoint is disabled: an open one let spammers purge their own history.
    adminToken: process.env.PRIVACY_ADMIN_TOKEN ?? "",
  },
  lists: {
    // Hourly per-IP budgets for the delta-sync endpoints.
    //
    // These MUST comfortably exceed one full sync: a worldwide/US scope is
    // millions of rows, so a client paginating at 5 000 rows issues ~1 000
    // requests for a single sync. The old 1 200 cap was consumed by one sync,
    // and since the limiter keys on req.ip, every device behind one NAT (a
    // household, or a whole carrier behind CGNAT) shares that budget — the
    // second device to sync got a 429 halfway through.
    //
    // They are not the anti-scraping control and were never able to be: one
    // valid token already allows 50 000 rows per request. The short-lived token
    // gate (see listToken.js) is what bounds bulk access.
    accessMaxPerHour: parseInt(process.env.LIST_ACCESS_MAX_PER_HOUR ?? "120", 10),
    pageMaxPerHour: parseInt(process.env.LIST_PAGE_MAX_PER_HOUR ?? "20000", 10),
  },
  // Comma-separated list of web origins allowed to call the API (the website).
  // Apps don't need CORS; this is for the browser widget on who-called.com.
  corsOrigins: (process.env.CORS_ORIGINS ?? "http://localhost:3000,https://www.who-called.com")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean),
  // ARCEP / operator block list (Saracroche-compatible JSON). Change the URL in .env
  // if you mirror your own JSON.
  arcep: {
    listUrl:
      process.env.ARCEP_LIST_URL ??
      "https://saracroche.org/api/v1/lists/french-list-arcep-operators",
  },
  // RGPD data minimization: raw reports older than this are purged. Numbers with
  // no remaining reports are removed too.
  retention: {
    reportDays: parseInt(process.env.REPORT_RETENTION_DAYS ?? "365", 10),
  },
  // Public "reassurance" stats shown in the apps (à la Saracroche). The ARCEP
  // block patterns each cover a whole ######-range (10000 numbers), so the
  // community DB sits on top of a large baseline of covered numbers. The base
  // offset lets us surface a meaningful "couverts" figure from day one — it is
  // labelled "numéros couverts" (not "signalés") to stay honest/defensible.
  stats: {
    // Numbers covered by each ARCEP wildcard pattern (one trailing '#' = 10x).
    numbersPerArcepPattern: parseInt(
      process.env.STATS_NUMBERS_PER_ARCEP_PATTERN ?? "10000",
      10
    ),
    // Assumed baseline of covered numbers (ARCEP ranges + ecosystem). Owner-set.
    baseCoveredOffset: parseInt(process.env.STATS_BASE_COVERED_OFFSET ?? "500000", 10),
  },
};
