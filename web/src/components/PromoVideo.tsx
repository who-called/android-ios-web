"use client";

import { useEffect, useRef, useState } from "react";

/**
 * Promo film (store/motion). Two encodes — 16:9 from `sm` up, 9:16 on phones —
 * each `preload="none"` so only the one actually displayed is ever fetched.
 * Plays muted while ≥50% on screen; the sound button restarts it with audio.
 * Honors prefers-reduced-motion: no autoplay, native controls instead.
 */
export function PromoVideo({
  lang,
  label,
  soundOnLabel,
  soundOffLabel,
}: {
  lang: "fr" | "en";
  label: string;
  soundOnLabel: string;
  soundOffLabel: string;
}) {
  const wide = useRef<HTMLVideoElement>(null);
  const tall = useRef<HTMLVideoElement>(null);
  const [muted, setMuted] = useState(true);
  const [reducedMotion, setReducedMotion] = useState(false);

  // The encode the current breakpoint shows (the other one is display:none).
  const visible = () => [wide.current, tall.current].find((v) => v && v.offsetParent !== null) ?? null;

  useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    setReducedMotion(reduce);
    if (reduce) return;

    const io = new IntersectionObserver(
      ([entry]) => {
        const v = visible();
        if (!v) return;
        if (entry.isIntersecting) v.play().catch(() => {});
        else v.pause();
      },
      { threshold: 0.5 },
    );
    if (wide.current?.parentElement) io.observe(wide.current.parentElement);
    return () => io.disconnect();
  }, []);

  function toggleSound() {
    const v = visible();
    if (!v) return;
    const next = !muted;
    v.muted = next;
    if (!next) v.currentTime = 0;
    v.play().catch(() => {});
    setMuted(next);
  }

  const src = (fmt: "16x9" | "9x16") => `/video/who-called-${lang}-${fmt}.mp4`;
  const poster = (fmt: "16x9" | "9x16") => `/video/who-called-${lang}-${fmt}.jpg`;
  const common = {
    muted: true,
    loop: true,
    playsInline: true,
    preload: "none" as const,
    controls: reducedMotion,
    "aria-label": label,
  };

  return (
    <div className="relative mx-auto overflow-hidden rounded-3xl bg-night-dark shadow-2xl shadow-night/30 ring-1 ring-white/10 max-sm:max-w-[min(100%,calc(80svh*9/16))]">
      <video ref={wide} {...common} poster={poster("16x9")} className="hidden aspect-video w-full sm:block">
        <source src={src("16x9")} type="video/mp4" />
      </video>
      <video ref={tall} {...common} poster={poster("9x16")} className="block aspect-[9/16] w-full sm:hidden">
        <source src={src("9x16")} type="video/mp4" />
      </video>
      {!reducedMotion && (
        <button
          type="button"
          onClick={toggleSound}
          aria-pressed={!muted}
          className="absolute bottom-3 right-3 flex items-center gap-2 rounded-full bg-black/55 px-4 py-2 text-sm font-semibold text-white backdrop-blur transition hover:bg-black/70 sm:bottom-5 sm:right-5"
        >
          {muted ? <SpeakerOffIcon /> : <SpeakerOnIcon />}
          {muted ? soundOnLabel : soundOffLabel}
        </button>
      )}
    </div>
  );
}

function SpeakerOffIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M11 5L6 9H3v6h3l5 4z" fill="currentColor" />
      <path d="M22 9l-6 6M16 9l6 6" />
    </svg>
  );
}

function SpeakerOnIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M11 5L6 9H3v6h3l5 4z" fill="currentColor" />
      <path d="M15.5 8.5a5 5 0 010 7M18.5 5.5a9 9 0 010 13" />
    </svg>
  );
}
