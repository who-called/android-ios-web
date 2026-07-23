FROM node:22-alpine

RUN corepack enable

WORKDIR /app

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
# pnpm 11 exits non-zero when dependency build scripts are "ignored" (Prisma).
# Skip them here; we run `prisma generate` explicitly right after.
RUN pnpm install --frozen-lockfile --prod=false --ignore-scripts

COPY prisma ./prisma
RUN pnpm prisma generate

COPY src ./src

ENV NODE_ENV=production
EXPOSE 3000

# Apply migrations on boot, then start the API.
CMD ["sh", "-c", "pnpm prisma migrate deploy && node src/server.js"]

# redeploy: trigger main pipeline

# redeploy: ship category:null fix

# redeploy: ship prefix grouping
