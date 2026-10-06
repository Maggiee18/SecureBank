import type { ReactNode } from 'react';

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'brand' | 'accent';

const tones: Record<Tone, string> = {
  neutral: 'bg-surface-2 text-muted ring-border',
  success: 'bg-success/10 text-success ring-success/20',
  warning: 'bg-warning/10 text-warning ring-warning/25',
  danger: 'bg-danger/10 text-danger ring-danger/20',
  brand: 'bg-brand/10 text-brand ring-brand/20',
  accent: 'bg-accent/10 text-accent ring-accent/25',
};

export function Badge({ tone = 'neutral', children, dot = false }: { tone?: Tone; children: ReactNode; dot?: boolean }) {
  return (
    <span className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2 py-0.5 text-[11px] font-semibold ring-1 ring-inset ${tones[tone]}`}>
      {dot && <span className="h-1.5 w-1.5 rounded-full bg-current" />}
      {children}
    </span>
  );
}

export function StatusBadge({ status }: { status: string }) {
  const tone: Tone = status === 'ACTIVE' || status === 'SUCCESS'
    ? 'success'
    : status === 'BLOCKED' ? 'warning' : status === 'FAILED' || status === 'FAILURE' ? 'danger' : 'neutral';
  return <Badge tone={tone} dot>{status.charAt(0) + status.slice(1).toLowerCase()}</Badge>;
}
