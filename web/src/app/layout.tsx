import "./globals.css";

// Root layout is intentionally minimal; the real <html lang>, metadata, header
// and footer live in app/[locale]/layout.tsx so they can be locale-aware.
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return children;
}
