"use client";

import type { MouseEvent } from "react";

export function NumberContactActions({
  phone,
  risky,
  title,
  callLabel,
  smsLabel,
  warning,
}: {
  phone: string;
  risky: boolean;
  title: string;
  callLabel: string;
  smsLabel: string;
  warning: string;
}) {
  function guard(event: MouseEvent<HTMLAnchorElement>) {
    if (risky && !window.confirm(warning)) event.preventDefault();
  }

  return (
    <section className="rounded-2xl border border-hair p-5">
      <h2 className="font-bold">{title}</h2>
      <div className="mt-3 grid grid-cols-2 gap-2">
        <a
          href={`tel:+${phone}`}
          onClick={guard}
          className="rounded-xl border border-hair px-4 py-2.5 text-center text-sm font-semibold transition hover:bg-night/[0.04]"
        >
          {callLabel}
        </a>
        <a
          href={`sms:+${phone}`}
          onClick={guard}
          className="rounded-xl border border-hair px-4 py-2.5 text-center text-sm font-semibold transition hover:bg-night/[0.04]"
        >
          {smsLabel}
        </a>
      </div>
    </section>
  );
}
