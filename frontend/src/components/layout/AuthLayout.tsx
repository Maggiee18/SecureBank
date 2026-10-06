import type { ReactNode } from 'react';
import { Fingerprint, Repeat2, ScrollText } from 'lucide-react';
import { Logo } from '@/components/ui/Logo';

const points = [
  { icon: Repeat2, title: 'No double charges', text: 'Every transfer carries an idempotency key, so a retry never moves money twice.' },
  { icon: Fingerprint, title: 'Signed sessions', text: 'Short lived JWT sessions with BCrypt protected passwords and login lockout.' },
  { icon: ScrollText, title: 'Full audit trail', text: 'Every login, transfer and failure is recorded with a traceable request id.' },
];

export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      <section className="hero-gradient relative hidden overflow-hidden p-12 text-white lg:flex lg:flex-col">
        <div className="absolute -right-24 -top-24 h-96 w-96 rounded-full border border-white/10" />
        <div className="absolute -right-10 -top-10 h-64 w-64 rounded-full border border-white/10" />
        <Logo light />
        <div className="my-auto max-w-md">
          <p className="mb-4 inline-flex rounded-full bg-white/10 px-3 py-1 text-xs font-medium text-emerald-200 ring-1 ring-white/15">
            Digital banking, built carefully
          </p>
          <h2 className="text-4xl font-bold leading-tight tracking-tight">
            Money that moves exactly once.
          </h2>
          <p className="mt-4 text-[15px] leading-relaxed text-blue-100/80">
            Open accounts, move money and track every rupee, on an API designed around atomic transfers,
            concurrency safety and traceability.
          </p>
          <ul className="mt-10 space-y-5">
            {points.map(({ icon: Icon, title: heading, text }) => (
              <li key={heading} className="flex gap-4">
                <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-white/10 ring-1 ring-white/15">
                  <Icon className="h-5 w-5 text-emerald-300" />
                </span>
                <span>
                  <span className="block font-semibold">{heading}</span>
                  <span className="text-sm text-blue-100/75">{text}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
        <p className="text-xs text-blue-100/60">Portfolio demo. All balances are test money.</p>
      </section>

      <section className="flex items-center justify-center px-5 py-12 sm:px-10">
        <div className="w-full max-w-[400px] animate-fade-up">
          <div className="mb-10 lg:hidden"><Logo /></div>
          <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
          <p className="mb-8 mt-1.5 text-sm text-muted">{subtitle}</p>
          {children}
        </div>
      </section>
    </div>
  );
}
