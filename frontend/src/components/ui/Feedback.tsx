import type { ReactNode } from 'react';
import { ChevronLeft, ChevronRight, LoaderCircle, type LucideIcon } from 'lucide-react';
import type { Page } from '@/lib/types';

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-10 text-sm text-muted" role="status">
      <LoaderCircle className="h-4 w-4 animate-spin" /> {label}
    </div>
  );
}

export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded-lg bg-surface-2 ${className}`} />;
}

export function EmptyState({ icon: Icon, title, description, action }: {
  icon: LucideIcon; title: string; description?: string; action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center px-6 py-12 text-center">
      <div className="mb-4 grid h-12 w-12 place-items-center rounded-2xl bg-brand/10 text-brand">
        <Icon className="h-6 w-6" />
      </div>
      <p className="font-semibold">{title}</p>
      {description && <p className="mt-1 max-w-sm text-sm text-muted">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function Pagination<T>({ page, onChange }: { page: Page<T>; onChange: (page: number) => void }) {
  if (page.totalElements === 0) return null;
  const from = page.page * page.size + 1;
  const to = from + page.content.length - 1;
  return (
    <div className="flex items-center justify-between gap-4 border-t border-border px-5 py-3 text-sm">
      <p className="text-muted tabular">
        {from}–{to} of {page.totalElements}
      </p>
      <div className="flex items-center gap-1">
        <button
          onClick={() => onChange(page.page - 1)}
          disabled={page.first}
          className="inline-flex h-8 items-center gap-1 rounded-lg px-2.5 text-muted transition hover:bg-surface-2 hover:text-ink disabled:opacity-40"
        >
          <ChevronLeft className="h-4 w-4" /> Prev
        </button>
        <span className="px-2 text-xs text-muted tabular">Page {page.page + 1} of {Math.max(page.totalPages, 1)}</span>
        <button
          onClick={() => onChange(page.page + 1)}
          disabled={page.last}
          className="inline-flex h-8 items-center gap-1 rounded-lg px-2.5 text-muted transition hover:bg-surface-2 hover:text-ink disabled:opacity-40"
        >
          Next <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

export function ErrorPanel({ message, requestId, onRetry }: { message: string; requestId?: string; onRetry?: () => void }) {
  return (
    <div className="card flex flex-col items-center gap-2 p-8 text-center">
      <p className="font-semibold">We couldn't load this</p>
      <p className="text-sm text-muted">{message}</p>
      {requestId && <p className="text-xs text-muted">Support ref <code className="font-mono">{requestId}</code></p>}
      {onRetry && <button onClick={onRetry} className="mt-2 text-sm font-semibold text-brand hover:underline">Try again</button>}
    </div>
  );
}
