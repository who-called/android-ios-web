import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // Who Called brand — midnight blue + intent accents. No violet, no gradients.
        night: {
          DEFAULT: "#1B2A4A",
          dark: "#131F38",
        },
        amber: { DEFAULT: "#F59E0B" },
        emerald: { DEFAULT: "#10B981" },
        coral: { DEFAULT: "#EF4444" },
        hair: "#E6E8F0", // subtle hairline border
      },
      fontFamily: {
        sans: ["system-ui", "-apple-system", "Segoe UI", "Roboto", "sans-serif"],
      },
    },
  },
  plugins: [],
};

export default config;
