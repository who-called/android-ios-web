# --- Build stage ---
FROM node:22-alpine AS build
RUN corepack enable
WORKDIR /app

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
# pnpm 11 exits non-zero when a dependency build script is "ignored" (sharp).
# We don't need sharp's native binary (no next/image optimization), so install
# without running build scripts.
RUN pnpm install --frozen-lockfile --ignore-scripts

COPY . .
# NEXT_PUBLIC_* are baked at build time (set via build-args in CI).
ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_SITE_URL
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_SITE_URL=$NEXT_PUBLIC_SITE_URL
# No build-time telemetry network call.
ENV NEXT_TELEMETRY_DISABLED=1
# Cap the Node heap so the build (esp. "Collecting build traces" / @vercel/nft,
# which is memory-hungry over 300+ pages) stays within budget and isn't killed
# by the host OOM-killer on the shared self-hosted runner. Overridable via
# build-arg NODE_BUILD_MEMORY if the runner has more/less RAM.
ARG NODE_BUILD_MEMORY=2048
ENV NODE_OPTIONS=--max-old-space-size=$NODE_BUILD_MEMORY
RUN pnpm build

# --- Runtime stage (standalone) ---
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000

COPY --from=build /app/public ./public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static

EXPOSE 3000
CMD ["node", "server.js"]

# redeploy: trigger main pipeline
