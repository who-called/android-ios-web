type IconProps = { className?: string };

const base = "stroke-current fill-none";

/** Shield (protection). */
export function ShieldIcon({ className = "h-6 w-6" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={`${base} ${className}`} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3l7 2.5v5C19 16 16 19.5 12 21 8 19.5 5 16 5 10.5v-5z" />
    </svg>
  );
}

/** Shield with check (protected / safe). */
export function ShieldCheckIcon({ className = "h-6 w-6" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={`${base} ${className}`} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3l7 2.5v5C19 16 16 19.5 12 21 8 19.5 5 16 5 10.5v-5z" />
      <path d="M9 12l2 2 4-4" />
    </svg>
  );
}

/** Blocked / no-call (danger). */
export function BlockIcon({ className = "h-6 w-6" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={`${base} ${className}`} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9" />
      <path d="M5.6 5.6l12.8 12.8" />
    </svg>
  );
}

/** Bell / alert (warn). */
export function BellIcon({ className = "h-6 w-6" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={`${base} ${className}`} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M6 9a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6z" />
      <path d="M10 20a2 2 0 0 0 4 0" />
    </svg>
  );
}

/** Lock (privacy). */
export function LockIcon({ className = "h-6 w-6" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={`${base} ${className}`} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <rect x="5" y="11" width="14" height="9" rx="2" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" />
      <path d="M12 15v2" />
    </svg>
  );
}

/** Phone with slash (spam call). */
export function PhoneSpamIcon({ className = "h-6 w-6" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={`${base} ${className}`} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M5 4h3l2 5-2 1a11 11 0 0 0 5 5l1-2 5 2v3a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z" />
      <path d="M4 3l16 16" />
    </svg>
  );
}

/** Check circle (safe / legit). */
export function CheckCircleIcon({ className = "h-6 w-6" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={`${base} ${className}`} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9" />
      <path d="M8.5 12.5l2.5 2.5 4.5-5" />
    </svg>
  );
}

/** List / official registry (ARCEP). */
export function ListIcon({ className = "h-6 w-6" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={`${base} ${className}`} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M8 6h12M8 12h12M8 18h12" />
      <circle cx="4" cy="6" r="1" />
      <circle cx="4" cy="12" r="1" />
      <circle cx="4" cy="18" r="1" />
    </svg>
  );
}

/** Incognito / anonymous. */
export function IncognitoIcon({ className = "h-6 w-6" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={`${base} ${className}`} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 11l1.8-5A2 2 0 0 1 7.7 4.6h8.6a2 2 0 0 1 1.9 1.4L20 11" />
      <path d="M3 11h18" />
      <circle cx="7.5" cy="15" r="2.5" />
      <circle cx="16.5" cy="15" r="2.5" />
      <path d="M10 14.5c1.2-.6 2.8-.6 4 0" />
    </svg>
  );
}

/** Gift / free. */
export function GiftIcon({ className = "h-6 w-6" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={`${base} ${className}`} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 11h16v9H4z" />
      <path d="M3 7h18v4H3zM12 7v13" />
      <path d="M12 7C12 5 10.5 4 9 4S6 5 7 7zM12 7c0-2 1.5-3 3-3s3 1 2 3z" />
    </svg>
  );
}

/** Devices (cross-platform). */
export function DevicesIcon({ className = "h-6 w-6" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" className={`${base} ${className}`} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="5" width="13" height="9" rx="1.5" />
      <path d="M2 17h13" />
      <rect x="17" y="9" width="5" height="11" rx="1.2" />
    </svg>
  );
}
