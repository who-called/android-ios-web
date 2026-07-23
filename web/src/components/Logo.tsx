export function Logo({ className = "h-8 w-8" }: { className?: string }) {
  // Shield + check, on brand. Inline SVG so it scales crisply everywhere.
  return (
    <svg viewBox="0 0 48 48" className={className} aria-hidden="true">
      <path
        d="M24 4l16 6v12c0 11-7 18-16 22C15 40 8 33 8 22V10z"
        fill="#1B2A4A"
      />
      <path
        d="M21.5 28.5L17 24l-2.5 2.5 7 7 14-14L33 3l.5 0.5z"
        fill="none"
      />
      <path
        d="M21.2 29.6l-5.2-5.2 2.6-2.6 2.6 2.6 9.4-9.4 2.6 2.6z"
        fill="#FFFFFF"
      />
    </svg>
  );
}
