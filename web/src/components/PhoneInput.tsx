"use client";

import { useState, useRef, useEffect } from "react";
import { parsePhoneNumberFromString } from "libphonenumber-js";
import { countries, countryFromCode, defaultCountry, type Country } from "@/lib/countries";

/**
 * Phone input with a country/flag selector. The country drives parsing only;
 * the raw text + selected country are returned to the parent, which normalizes
 * to E.164 via libphonenumber-js. Lightweight, on-brand (no third-party CSS).
 */
export function PhoneInput({
  value,
  country,
  onValueChange,
  onCountryChange,
  placeholder,
  countryAriaLabel = "Choisir le pays",
  phoneAriaLabel = "Numéro de téléphone",
  searchPlaceholder = "Rechercher un pays…",
  emptyLabel = "Aucun pays trouvé.",
}: {
  value: string;
  country: Country;
  onValueChange: (v: string) => void;
  onCountryChange: (c: Country) => void;
  placeholder?: string;
  countryAriaLabel?: string;
  phoneAriaLabel?: string;
  searchPlaceholder?: string;
  emptyLabel?: string;
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function onClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, []);

  // Smart country detection: when an explicit international form is typed/pasted
  // (+34…, 0034…, +1…), switch the selector to the detected country — even if FR
  // was preselected — AND strip the dialing code from the field so the indicatif
  // isn't shown twice (chip + input). A bare national number is left untouched.
  useEffect(() => {
    const t = value.trim();
    if (!t.startsWith("+") && !t.startsWith("00")) return;
    // Normalize 00 → + so detection is independent of the current country's IDD.
    const intl = t.startsWith("00") ? "+" + t.slice(2) : t;
    const parsed = parsePhoneNumberFromString(intl, country.code);
    if (!parsed?.country) return;
    if (parsed.country !== country.code) onCountryChange(countryFromCode(parsed.country));
    // Replace the field with the national number (e.g. "914 80 00 10").
    const national = parsed.formatNational();
    if (national && national.replace(/\s/g, "") !== value.replace(/\s/g, "")) {
      onValueChange(national);
    }
  }, [value, country.code, onCountryChange, onValueChange]);

  const filtered = query
    ? countries.filter(
        (c) =>
          c.name.toLowerCase().includes(query.toLowerCase()) ||
          c.dial.includes(query.replace("+", "")),
      )
    : countries;

  return (
    <div ref={ref} className="relative flex w-full rounded-xl border border-hair focus-within:border-night">
      {/* Country selector */}
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-1.5 rounded-l-xl border-r border-hair px-3 py-3 text-sm hover:bg-night/[0.03]"
        aria-label={countryAriaLabel}
      >
        <span className="text-lg leading-none">{country.flag}</span>
        <span className="text-night/70">+{country.dial}</span>
        <svg viewBox="0 0 20 20" className="h-4 w-4 fill-night/40">
          <path d="M5.5 7.5L10 12l4.5-4.5z" />
        </svg>
      </button>

      {/* Number field */}
      <input
        type="tel"
        inputMode="tel"
        value={value}
        onChange={(e) => onValueChange(e.target.value)}
        placeholder={placeholder ?? "6 12 34 56 78"}
        className="w-0 min-w-0 flex-1 rounded-r-xl px-4 py-3 text-lg outline-none"
        aria-label={phoneAriaLabel}
      />

      {/* Dropdown */}
      {open && (
        <div className="absolute left-0 top-full z-20 mt-1 max-h-72 w-64 max-w-[calc(100vw-2.5rem)] overflow-auto rounded-xl border border-hair bg-white shadow-lg">
          <div className="sticky top-0 border-b border-hair bg-white p-2">
            <input
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={searchPlaceholder}
              className="w-full rounded-lg border border-hair px-3 py-2 text-sm outline-none focus:border-night"
            />
          </div>
          <ul>
            {filtered.map((c) => (
              <li key={c.code}>
                <button
                  type="button"
                  onClick={() => {
                    onCountryChange(c);
                    setOpen(false);
                    setQuery("");
                  }}
                  className={`flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-night/[0.04] ${
                    c.code === country.code ? "bg-night/[0.04] font-medium" : ""
                  }`}
                >
                  <span className="text-lg">{c.flag}</span>
                  <span className="flex-1">{c.name}</span>
                  <span className="text-night/50">+{c.dial}</span>
                </button>
              </li>
            ))}
            {filtered.length === 0 && (
              <li className="px-3 py-3 text-sm text-night/50">{emptyLabel}</li>
            )}
          </ul>
        </div>
      )}
    </div>
  );
}

export { defaultCountry };
