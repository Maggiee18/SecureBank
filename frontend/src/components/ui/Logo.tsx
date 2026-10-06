import { useId } from 'react';

export function Logo({ compact = false, light = false }: { compact?: boolean; light?: boolean }) {
  // A unique gradient id per instance: a duplicate id inside a hidden sidebar would blank out the visible logo.
  const gradient = `sb-logo-${useId().replace(/:/g, '')}`;
  return (
    <div className="flex items-center gap-2.5">
      <svg viewBox="0 0 64 64" className="h-8 w-8 shrink-0" aria-hidden="true">
        <defs>
          <linearGradient id={gradient} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#1e3a8a" />
            <stop offset="1" stopColor="#10b981" />
          </linearGradient>
        </defs>
        <rect width="64" height="64" rx="16" fill={`url(#${gradient})`} />
        <path d="M32 13 17 19v11c0 10 6.4 18.4 15 21 8.6-2.6 15-11 15-21V19z" fill="none" stroke="#fff" strokeWidth="4" strokeLinejoin="round" />
        <path d="m25 32 5 5 10-11" fill="none" stroke="#fff" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      {!compact && (
        <span className={`text-[17px] font-bold tracking-tight ${light ? 'text-white' : 'text-ink'}`}>
          Secure<span className={light ? 'text-emerald-300' : 'text-accent'}>Bank</span>
        </span>
      )}
    </div>
  );
}
