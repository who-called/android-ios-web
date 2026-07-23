#!/usr/bin/env sh
# who-called periodic DB update.
#
# Keeps the server-side database fresh:
#   1. dbforge pull   — fetch new SIA deltas (resumes from sia_sync; no re-fetch)
#   2. score          — re-materialize the unified `numbers` table (decay, tombstones)
#   3. retention      — RGPD purge of stale raw reports
#
# The warm-up snapshot is NOT generated here — it is a build/release step
# (see `dbforge snapshot --prefix 33`) because the embedded warm-up is frozen
# per app release.
#
# Requires: DATABASE_URL, `dbforge` on PATH, and the backend at /app.
set -eu

echo "[db-update] 1/3 SIA pull"
# SIA pull is best-effort: a third-party outage must not block re-scoring.
dbforge pull --dsn "$DATABASE_URL" "${PULL_ARGS:-}" || echo "[db-update] pull failed, continuing"

echo "[db-update] 2/3 re-score"
node src/jobs/scoreJob.js

echo "[db-update] 3/3 retention purge"
node src/jobs/retention.js

echo "[db-update] done"
