# Deployment — Who Called (OVH MKS)

Monorepo `github.com/who-called/android-ios-web`. One repo, GitHub Actions per
folder (`paths:` triggers), images on GHCR, deployed to the shared OVH managed
Kubernetes cluster via the self-hosted runner VM.

> Production migrated from the Hetzner k3s box to OVH MKS (July 2026). The old
> cluster is scaled to zero and kept only as a cold copy.

## Components

| Piece | Where | Domain |
|-------|-------|--------|
| API (Express) | `backend/` → `whocalled-api` | `api.who-called.com` |
| Website (Next.js) | `web/` → `whocalled-web` | `www.who-called.com` (apex redirects) |
| Database | shared `sides-postgres` (CNPG, `db-prod`) | DB `whocalled`, owner `whocalled_app` |

DNS: `www` / apex / `api` → the OVH ingress LoadBalancer IP.

## Workflows (`.github/workflows/`)

| Workflow | Trigger | Does |
|----------|---------|------|
| `deploy-infra-ovh.yaml` | manual / infra changes | namespace, GHCR pull secret, API secret, configmap, services, ingress |
| `deploy-api-ovh.yaml` | push `backend/**` | build+push API image, sync `LIST_TOKEN_SECRET`, deploy |
| `deploy-web-ovh.yaml` | push `web/**` | build+push web image, deploy |
| `deploy-updater-ovh.yml` | manual / updater changes | SIA pull + cron + warmup publish |

All jobs run on the self-hosted runner VM labelled `[self-hosted, ovh]`
(pattern shared with the `st` repo: runner on an OVH VM, scoped `github-ci`
ServiceAccount kubeconfig — no cluster-admin on the box).

## GitHub repo secrets (Settings → Secrets and variables → Actions)

| Secret | Value |
|--------|-------|
| `DATABASE_URL` | `postgresql://whocalled_app:<PASSWORD>@sides-postgres-rw.db-prod:5432/whocalled?sslmode=require` |
| `LIST_TOKEN_SECRET` | random string (`openssl rand -hex 32`) — signs the short-lived `/lists` download tokens |
| `GHCR_PULL_TOKEN` | GitHub PAT (classic) with `read:packages` — lets the cluster pull the GHCR images |

`GITHUB_TOKEN` is automatic (used to push images during the build job).

## Notes

- API runs `prisma migrate deploy` on boot (schema applied automatically).
- The scoring scheduler + retention run in-process via `SCORE_INTERVAL_MS`.
- `NEXT_PUBLIC_*` for the site are baked at image build (build-args in the web
  workflow) → `api.who-called.com` + `www.who-called.com`.
- ARCEP list is **FR only** (`ARCEP_LIST_URL` in the API configmap).
