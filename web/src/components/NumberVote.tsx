"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { reportNumber, webDeviceId } from "@/lib/api";

type Vote = "spam" | "legit";

export function NumberVote({
  phone,
  locale,
  title,
  note,
  spamLabel,
  legitLabel,
  savedMessage,
  errorMessage,
}: {
  phone: string;
  locale: string;
  title: string;
  note: string;
  spamLabel: string;
  legitLabel: string;
  savedMessage: string;
  errorMessage: string;
}) {
  const router = useRouter();
  const storageKey = `wc_vote_${phone}`;
  const [selected, setSelected] = useState<Vote | null>(null);
  const [loading, setLoading] = useState<Vote | null>(null);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    const stored = localStorage.getItem(storageKey);
    if (stored === "spam" || stored === "legit") setSelected(stored);
  }, [storageKey]);

  async function vote(next: Vote) {
    setLoading(next);
    setMessage(null);
    try {
      await reportNumber({
        phone,
        deviceId: webDeviceId(),
        vote: next,
        category: next === "spam" ? "unknown" : null,
        locale,
      });
      localStorage.setItem(storageKey, next);
      setSelected(next);
      setMessage({ ok: true, text: savedMessage });
      router.refresh();
    } catch {
      setMessage({ ok: false, text: errorMessage });
    } finally {
      setLoading(null);
    }
  }

  return (
    <section className="rounded-2xl border border-hair p-5">
      <h2 className="font-bold">{title}</h2>
      <p className="mt-1 text-sm text-night/55">{note}</p>
      <div className="mt-4 grid grid-cols-2 gap-2">
        <button
          type="button"
          disabled={loading !== null}
          onClick={() => vote("spam")}
          className={`rounded-xl border px-3 py-2.5 text-sm font-semibold transition disabled:opacity-60 ${
            selected === "spam"
              ? "border-coral bg-coral text-white"
              : "border-coral/30 bg-coral/5 text-coral"
          }`}
        >
          {loading === "spam" ? "…" : spamLabel}
        </button>
        <button
          type="button"
          disabled={loading !== null}
          onClick={() => vote("legit")}
          className={`rounded-xl border px-3 py-2.5 text-sm font-semibold transition disabled:opacity-60 ${
            selected === "legit"
              ? "border-emerald bg-emerald text-white"
              : "border-emerald/30 bg-emerald/5 text-emerald"
          }`}
        >
          {loading === "legit" ? "…" : legitLabel}
        </button>
      </div>
      {message && (
        <p className={`mt-3 text-sm ${message.ok ? "text-emerald" : "text-coral"}`}>
          {message.text}
        </p>
      )}
    </section>
  );
}
