import Link from "next/link";
import { Suspense } from "react";
import { NumberTool } from "@/components/NumberTool";
import { StoreBadges } from "@/components/StoreBadges";
import {
  BellIcon,
  BlockIcon,
  CheckCircleIcon,
  DevicesIcon,
  GiftIcon,
  IncognitoIcon,
  ListIcon,
  LockIcon,
  PhoneSpamIcon,
  ShieldCheckIcon,
} from "@/components/Icons";
import { getDict } from "@/i18n/dictionaries";
import { toDictLocale } from "@/i18n/locales";

export default async function HomePage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = getDict(toDictLocale(locale));
  const base = `/${locale}`;

  return (
    <>
      {/* Hero */}
      <section className="border-b border-hair">
        <div className="mx-auto max-w-5xl px-4 pt-12 pb-8 sm:pt-20">
          <div className="grid items-center gap-10 lg:grid-cols-2">
            <div className="min-w-0">
              <span className="inline-flex items-center gap-2 rounded-full border border-emerald/30 bg-emerald/10 px-3 py-1 text-xs font-semibold text-emerald">
                <span className="h-2 w-2 rounded-full bg-emerald" />
                {t.hero.badge}
              </span>
              <h1 className="mt-4 text-4xl font-extrabold leading-tight tracking-tight sm:text-5xl">
                {t.hero.titleA}
                <span className="text-amber">{t.hero.titleB}</span>
              </h1>
              <p className="mt-4 text-lg text-night/70">{t.hero.subtitle}</p>
              <div id="telecharger" className="mt-6">
                <StoreBadges />
              </div>
              <p className="mt-3 text-sm text-night/60">{t.hero.privacy}</p>
            </div>
            <div className="min-w-0">
              <Suspense>
                <NumberTool dict={t} locale={locale} />
              </Suspense>
            </div>
          </div>
        </div>
      </section>

      {/* Trust stats */}
      <section className="border-b border-hair bg-night/[0.02]">
        <div className="mx-auto grid max-w-5xl grid-cols-2 gap-4 px-4 py-10 sm:grid-cols-4">
          <Stat icon={<ListIcon className="h-5 w-5" />} value="1 542+" label={t.stats.arcep} accent="text-amber" />
          <Stat icon={<IncognitoIcon className="h-5 w-5" />} value="100 %" label={t.stats.anon} accent="text-emerald" />
          <Stat icon={<GiftIcon className="h-5 w-5" />} value="0 €" label={t.stats.free} accent="text-night" />
          <Stat icon={<DevicesIcon className="h-5 w-5" />} value="Android · iOS" label={t.stats.multi} accent="text-night" />
        </div>
      </section>

      {/* Safe vs Blocked */}
      <section>
        <div className="mx-auto max-w-5xl px-4 py-14">
          <h2 className="text-center text-2xl font-bold">{t.compare.title}</h2>
          <p className="mx-auto mt-2 max-w-xl text-center text-night/70">{t.compare.subtitle}</p>
          <div className="mt-8 grid gap-5 sm:grid-cols-2">
            <div className="rounded-2xl border border-emerald/30 bg-emerald/[0.06] p-6">
              <div className="flex items-center gap-3">
                <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-emerald/15 text-emerald">
                  <CheckCircleIcon className="h-6 w-6" />
                </span>
                <div>
                  <div className="font-bold text-emerald">{t.compare.safeName}</div>
                  <div className="text-sm text-night/60">{t.compare.safeDesc}</div>
                </div>
              </div>
              <div className="mt-4 flex items-center justify-between rounded-xl border border-emerald/20 bg-white px-4 py-3">
                <span className="font-semibold">+33 1 23 45 67 89</span>
                <span className="inline-flex items-center gap-1 text-sm font-semibold text-emerald">
                  <ShieldCheckIcon className="h-4 w-4" /> {t.compare.safeBadge}
                </span>
              </div>
            </div>
            <div className="rounded-2xl border border-coral/30 bg-coral/[0.06] p-6">
              <div className="flex items-center gap-3">
                <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-coral/15 text-coral">
                  <PhoneSpamIcon className="h-6 w-6" />
                </span>
                <div>
                  <div className="font-bold text-coral">{t.compare.blockedName}</div>
                  <div className="text-sm text-night/60">{t.compare.blockedDesc}</div>
                </div>
              </div>
              <div className="mt-4 flex items-center justify-between rounded-xl border border-coral/20 bg-white px-4 py-3">
                <span className="font-semibold">+33 899 65 43 21</span>
                <span className="inline-flex items-center gap-1 text-sm font-semibold text-coral">
                  <BlockIcon className="h-4 w-4" /> {t.compare.blockedBadge}
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Features */}
      <section>
        <div className="mx-auto grid max-w-5xl gap-6 px-4 pb-14 sm:grid-cols-3">
          <Feature accent="coral" icon={<ShieldCheckIcon className="h-6 w-6" />} title={t.features.f1Title} body={t.features.f1Body} />
          <Feature accent="amber" icon={<BellIcon className="h-6 w-6" />} title={t.features.f2Title} body={t.features.f2Body} />
          <Feature accent="emerald" icon={<LockIcon className="h-6 w-6" />} title={t.features.f3Title} body={t.features.f3Body} />
        </div>
      </section>

      {/* How it works */}
      <section className="border-t border-hair">
        <div className="mx-auto max-w-5xl px-4 py-14">
          <h2 className="text-2xl font-bold">{t.how.title}</h2>
          <ol className="mt-6 grid gap-6 sm:grid-cols-3">
            <Step n={1} color="bg-night" title={t.how.s1Title} body={t.how.s1Body} />
            <Step n={2} color="bg-amber" title={t.how.s2Title} body={t.how.s2Body} />
            <Step n={3} color="bg-emerald" title={t.how.s3Title} body={t.how.s3Body} />
          </ol>
          <div className="mt-8">
            <StoreBadges />
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="border-t border-hair">
        <div className="mx-auto max-w-5xl px-4 py-12">
          <div className="rounded-3xl bg-night p-8 text-white sm:p-12">
            <h2 className="text-2xl font-bold sm:text-3xl">{t.cta.title}</h2>
            <p className="mt-2 max-w-xl text-white/70">{t.cta.subtitle}</p>
            <div className="mt-5 flex flex-wrap gap-3">
              <Link href={`${base}/verifier`} className="rounded-xl bg-amber px-5 py-3 font-semibold text-night transition hover:brightness-95">
                {t.cta.verify}
              </Link>
              <Link href={`${base}/signaler`} className="rounded-xl border border-white/30 px-5 py-3 font-semibold text-white transition hover:bg-white/10">
                {t.cta.report}
              </Link>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}

function Stat({ icon, value, label, accent }: { icon: React.ReactNode; value: string; label: string; accent: string }) {
  return (
    <div className="flex items-center gap-3 rounded-2xl border border-hair bg-white p-4">
      <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-night/[0.04] ${accent}`}>{icon}</span>
      <div className="min-w-0">
        <div className="truncate text-lg font-extrabold leading-tight">{value}</div>
        <div className="truncate text-sm text-night/60">{label}</div>
      </div>
    </div>
  );
}

const ACCENTS: Record<string, string> = {
  coral: "bg-coral/10 text-coral border-coral/20",
  amber: "bg-amber/10 text-amber border-amber/20",
  emerald: "bg-emerald/10 text-emerald border-emerald/20",
};

function Feature({ accent, icon, title, body }: { accent: keyof typeof ACCENTS; icon: React.ReactNode; title: string; body: string }) {
  return (
    <div className="rounded-2xl border border-hair bg-white p-6 transition hover:shadow-md">
      <div className={`flex h-12 w-12 items-center justify-center rounded-xl border ${ACCENTS[accent]}`}>{icon}</div>
      <h3 className="mt-4 font-bold">{title}</h3>
      <p className="mt-2 text-sm text-night/70">{body}</p>
    </div>
  );
}

function Step({ n, color, title, body }: { n: number; color: string; title: string; body: string }) {
  return (
    <li className="rounded-2xl border border-hair bg-white p-6">
      <span className={`flex h-9 w-9 items-center justify-center rounded-full text-sm font-bold text-white ${color}`}>{n}</span>
      <h3 className="mt-3 font-bold">{title}</h3>
      <p className="mt-1 text-sm text-night/70">{body}</p>
    </li>
  );
}
