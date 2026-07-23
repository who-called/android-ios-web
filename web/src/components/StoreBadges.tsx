import { config } from "@/lib/config";

/** App store call-to-action buttons (text badges — no external image assets needed). */
export function StoreBadges({ className = "" }: { className?: string }) {
  return (
    <div className={`flex flex-wrap gap-3 ${className}`}>
      <div
        aria-label="Application iOS bientôt disponible"
        className="flex cursor-default items-center gap-2 rounded-xl bg-night/65 px-5 py-3 text-white"
      >
        <AppleIcon />
        <span>
          <span className="block text-[11px] opacity-80">Bientôt sur</span>
          <span className="block text-sm font-semibold leading-tight">App Store</span>
        </span>
      </div>
      <a
        href={config.links.playStore}
        className="flex items-center gap-2 rounded-xl border border-hair bg-white px-5 py-3 text-night transition hover:border-night/30"
      >
        <PlayIcon />
        <span>
          <span className="block text-[11px] opacity-70">Disponible sur</span>
          <span className="block text-sm font-semibold leading-tight">Google Play</span>
        </span>
      </a>
    </div>
  );
}

function AppleIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-6 w-6 fill-current" aria-hidden="true">
      <path d="M16.4 12.9c0-2 1.6-2.9 1.7-3-1-1.4-2.4-1.6-2.9-1.6-1.2-.1-2.4.7-3 .7-.6 0-1.6-.7-2.6-.7-1.3 0-2.6.8-3.3 2-1.4 2.4-.4 6 1 8 .7 1 1.5 2 2.5 2 1 0 1.4-.6 2.6-.6s1.5.6 2.6.6 1.7-.9 2.4-1.9c.5-.7.7-1.1 1-1.9-2.6-1-2.5-3.1-2.5-3.1zM14.6 6.3c.5-.7.9-1.6.8-2.5-.8 0-1.7.5-2.3 1.2-.5.6-.9 1.5-.8 2.4.9.1 1.7-.4 2.3-1.1z" />
    </svg>
  );
}

function PlayIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-6 w-6" aria-hidden="true">
      <path d="M3.6 2.3 13 12 3.6 21.7c-.4-.2-.6-.6-.6-1.1V3.4c0-.5.2-.9.6-1.1z" fill="#1B2A4A" />
      <path d="M16.3 9.1 13 12l3.3 2.9 3.4-2c.7-.4.7-1.4 0-1.8l-3.4-2z" fill="#F59E0B" />
      <path d="M13 12 3.6 2.3c.1 0 .3 0 .4.1L16.3 9.1z" fill="#10B981" />
      <path d="M13 12 4 21.6c.1.1.3.1.4 0l11.9-6.7z" fill="#EF4444" />
    </svg>
  );
}
