"use client";

import { useState, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  lookupNumber,
  normalizePhone,
  reportNumber,
  webDeviceId,
} from "@/lib/api";
import { PhoneInput } from "@/components/PhoneInput";
import { defaultCountry, type Country } from "@/lib/countries";
import type { Dict } from "@/i18n/dictionaries";

type Mode = "lookup" | "report";

export function NumberTool({
  dict,
  locale,
  defaultMode = "lookup",
}: {
  dict: Dict;
  locale: string;
  defaultMode?: Mode;
}) {
  const t = dict.tool;
  const router = useRouter();
  const searchParams = useSearchParams();
  const CATEGORIES = [
    { api: "telemarketing", label: t.cats.telemarketing },
    { api: "scam", label: t.cats.scam },
    { api: "robocall", label: t.cats.robocall },
    { api: "silent", label: t.cats.silent },
    { api: "debt", label: t.cats.debt },
    { api: "survey", label: t.cats.survey },
    { api: "unknown", label: t.cats.unknown },
  ];
  const [mode, setMode] = useState<Mode>(defaultMode);
  // Pre-fill from ?phone= query param (e.g. arriving from a number page's
  // "Signaler ce numéro" button). PhoneInput auto-detects the country.
  const [phone, setPhone] = useState("");
  useEffect(() => {
    const p = searchParams.get("phone");
    if (p) setPhone("+" + p.replace(/^\+/, ""));
  }, [searchParams]);
  const [country, setCountry] = useState<Country>(defaultCountry);
  const [category, setCategory] = useState("telemarketing");
  const [vote, setVote] = useState<"spam" | "legit">("spam");
  const [loading, setLoading] = useState(false);
  const [lastReported, setLastReported] = useState<string | null>(null);
  const [message, setMessage] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  // Switching tabs clears the previous message so the old feedback doesn't
  // linger under the other mode.
  function switchMode(m: Mode) {
    if (m === mode) return;
    setMode(m);
    setMessage(null);
  }

  async function onLookup(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    const n = normalizePhone(phone, country.code);
    if (!n) {
      setMessage({ kind: "err", text: t.invalid });
      return;
    }
    setLoading(true);
    try {
      // Validate against the API, then send the user to the dedicated number
      // page (richer, SEO-friendly, indexable when eligible).
      await lookupNumber(n);
      router.push(`/${locale}/numero/${n}`);
    } catch {
      setMessage({ kind: "err", text: t.unavailable });
    } finally {
      setLoading(false);
    }
  }

  async function onReport(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    const n = normalizePhone(phone, country.code);
    if (!n) {
      setMessage({ kind: "err", text: t.invalid });
      return;
    }
    setLoading(true);
    try {
      await reportNumber({
        phone: n,
        deviceId: webDeviceId(),
        vote,
        category: vote === "spam" ? category : null,
      });
      // Playful nudge if they re-report the same number (clicking again is fine).
      setMessage({ kind: "ok", text: n === lastReported ? t.reportOkAgain : t.reportOk });
      setLastReported(n);
      setPhone("");
    } catch {
      setMessage({ kind: "err", text: t.reportErr });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="rounded-2xl border border-hair bg-white p-5 shadow-sm sm:p-6">
      <div className="mb-4 inline-flex rounded-lg border border-hair p-1 text-sm">
        <button
          onClick={() => switchMode("lookup")}
          className={`rounded-md px-4 py-1.5 font-medium transition ${
            mode === "lookup" ? "bg-night text-white" : "text-night/70"
          }`}
        >
          {t.verify}
        </button>
        <button
          onClick={() => switchMode("report")}
          className={`rounded-md px-4 py-1.5 font-medium transition ${
            mode === "report" ? "bg-night text-white" : "text-night/70"
          }`}
        >
          {t.report}
        </button>
      </div>

      <form onSubmit={mode === "lookup" ? onLookup : onReport} className="space-y-3">
        <PhoneInput
          value={phone}
          country={country}
          onValueChange={setPhone}
          onCountryChange={setCountry}
        />

        {mode === "report" && (
          <>
            <div className="flex gap-2">
              {(["spam", "legit"] as const).map((v) => (
                <button
                  key={v}
                  type="button"
                  onClick={() => setVote(v)}
                  className={`flex-1 rounded-xl border px-4 py-2 text-sm font-medium transition ${
                    vote === v ? "border-night bg-night/5" : "border-hair"
                  }`}
                >
                  {v === "spam" ? t.spam : t.legit}
                </button>
              ))}
            </div>
            {vote === "spam" && (
              <div className="flex flex-wrap gap-2">
                {CATEGORIES.map((c) => (
                  <button
                    key={c.api}
                    type="button"
                    onClick={() => setCategory(c.api)}
                    className={`rounded-full border px-3 py-1 text-sm transition ${
                      category === c.api ? "border-night bg-night/5" : "border-hair text-night/70"
                    }`}
                  >
                    {c.label}
                  </button>
                ))}
              </div>
            )}
          </>
        )}

        <button
          type="submit"
          disabled={loading}
          className="w-full rounded-xl bg-night px-4 py-3 font-semibold text-white transition hover:bg-night-dark disabled:opacity-60"
        >
          {loading
            ? t.wait
            : mode === "lookup"
              ? t.verifyBtn
              : t.reportBtn}
        </button>
      </form>

      <p className="mt-3 text-xs text-night/60">{t.anon}</p>

      {message && (
        <div
          className={`mt-4 rounded-xl border px-4 py-3 text-sm ${
            message.kind === "ok"
              ? "border-emerald/30 bg-emerald/5 text-night"
              : "border-coral/30 bg-coral/5 text-night"
          }`}
        >
          {message.text}
        </div>
      )}
    </div>
  );
}
