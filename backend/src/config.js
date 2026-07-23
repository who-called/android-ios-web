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
