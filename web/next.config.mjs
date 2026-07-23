/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  // Slim, self-contained server build for the Docker image.
  output: "standalone",
  // We don't use next/image optimization → no sharp needed at runtime.
  images: { unoptimized: true },
  // Trim what "Collecting build traces" (@vercel/nft) has to scan/copy into the
  // standalone output. These are build/dev-only and never loaded by the server
  // at runtime — excluding them lowers the tracing step's memory + time, which
  // is what was OOM-killing the self-hosted buildkit daemon.
  outputFileTracingExcludes: {
    "*": [
      "node_modules/.pnpm/@swc+core*/**",
      "node_modules/.pnpm/@esbuild*/**",
      "node_modules/.pnpm/esbuild*/**",
      "node_modules/.pnpm/@next+swc*/**",
      "node_modules/.pnpm/typescript*/**",
      "node_modules/.pnpm/sharp*/**",
      "node_modules/.pnpm/caniuse-lite*/**",
    ],
  },
};

export default nextConfig;
