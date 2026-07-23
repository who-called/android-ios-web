# who-called — backend

Crowdsourced call-reporting + scoring API. **Express.js + Prisma + PostgreSQL.**

## Quick start (local, Docker)

Uses **pnpm** (Prisma build scripts are allowed via `pnpm-workspace.yaml` →
`onlyBuiltDependencies`).

```bash
cp .env.example .env
docker compose up -d db        # Postgres on :5432
pnpm install
pnpm prisma migrate dev        # create tables
pnpm db:seed                   # optional: a few FR sample numbers
pnpm dev                       # API on :3000 (auto-reload)
```

Switch to your real Postgres later by editing `DATABASE_URL` in `.env`.
Same DB is used across both app environments.

Full stack in Docker (API + DB + periodic scoring):

```bash
docker compose up --build
# Test the scheduler quickly with a short interval:
SCORE_INTERVAL_MS=10000 docker compose up --build scoring
```

## Endpoints (`/api/v1`)

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/reports` | Submit anonymous vote `{phone, deviceId, vote: spam\|legit, category?, locale?}` |
| `GET`  | `/lookup/:phone` | Current score/status for one number (live UX) |
| `GET`  | `/lists?since=<ISO>&limit=<n>` | Delta sync of `block`+`warn` numbers (warm-up: omit `since`) |
| `GET`  | `/health` | Health check |

Phone numbers are normalized to **E.164 without `+`** (e.g. `33612345678`). FR-first
(`0X…` → `33X…`), but international input is accepted.

## Scoring (`src/scoring.js`)

`scoreFromReports(reports)` computes the score from **raw reports**, each weighted by:
- **Time decay** — half-life 30d, so old reports fade (numbers can recover).
- **Device reputation** — 0.1..2.0; low-trust devices count less.

```
weight = decay(age, halfLife) × reputation
score  = weightedRatio(spam/total) × confidence(weightedTotal/min) × 100 + velocityBoost
```
- **Velocity** — ≥ `SCORE_VELOCITY_THRESHOLD` spam reports within the window → `+SCORE_VELOCITY_BOOST` (robocall in progress).
- **Confidence** — few votes ⇒ cautious score.

Status (thresholds in `.env`):
- `score ≥ 85` & weighted volume ≥ `MIN_REPORTS_FOR_BLOCK` → **block**
- `60 ≤ score < 85` → **warn**
- else → **allow / unknown**

**Anti-poisoning** — `updateReputation()` erodes a device's weight when it votes
against a strong consensus, so attackers spamming bad votes lose influence.

Batch re-scoring: `pnpm score` (one-shot) — recomputes from raw reports,
applying decay over time. In Docker, the **`scoring`** service runs the scheduler
(`src/jobs/scheduler.js`) on a loop every `SCORE_INTERVAL_MS` (default 6h).
Tests: `pnpm test` (`test/scoring.test.js`).

## Anti-abuse

Per-device daily rate limit (`MAX_REPORTS_PER_DEVICE_PER_DAY`) via the `devices` table.
`deviceId` is an anonymous UUID — no PII (RGPD).
