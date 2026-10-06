import { useState, type FormEvent } from 'react';
import { ScrollText, Search, X } from 'lucide-react';
import { PageHeader } from '@/components/layout/AppShell';
import { Badge, StatusBadge } from '@/components/ui/Badge';
import { CopyButton } from '@/components/ui/CopyButton';
import { EmptyState, ErrorPanel, Pagination, Skeleton } from '@/components/ui/Feedback';
import { useAuditLogs } from '@/hooks/queries';
import type { ApiError } from '@/lib/api';
import { formatDateTime, titleCase } from '@/lib/format';

const ACTION_TONE: Record<string, 'brand' | 'accent' | 'warning' | 'neutral'> = {
  TRANSFER: 'brand', DEPOSIT: 'accent', WITHDRAWAL: 'accent', LOGIN_FAILED: 'warning', ACCOUNT_STATUS_CHANGED: 'warning',
};

export function AuditLogPage() {
  const [page, setPage] = useState(0);
  const [customerInput, setCustomerInput] = useState('');
  const [customerId, setCustomerId] = useState<number | undefined>();
  const logs = useAuditLogs(page, customerId);

  function applyFilter(event: FormEvent) {
    event.preventDefault();
    const id = Number(customerInput);
    setCustomerId(customerInput && Number.isInteger(id) && id > 0 ? id : undefined);
    setPage(0);
  }

  return (
    <div className="animate-fade-up">
      <PageHeader title="Audit log" subtitle="Every login, money movement and admin action, with the request id to trace it in the logs." />

      <div className="card overflow-hidden">
        <form onSubmit={applyFilter} className="flex flex-wrap items-center gap-2 border-b border-border p-4">
          <div className="relative w-full sm:w-64">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
            <input className="field h-10 pl-9" inputMode="numeric" placeholder="Customer id" value={customerInput}
              onChange={(e) => setCustomerInput(e.target.value.replace(/\D/g, ''))} aria-label="Filter by customer id" />
          </div>
          <button type="submit" className="h-10 rounded-xl bg-brand px-4 text-sm font-semibold text-brand-fg">Filter</button>
          {customerId !== undefined && (
            <button type="button" onClick={() => { setCustomerId(undefined); setCustomerInput(''); setPage(0); }}
              className="inline-flex h-10 items-center gap-1 rounded-xl px-3 text-sm text-muted hover:bg-surface-2">
              <X className="h-4 w-4" /> Customer {customerId}
            </button>
          )}
        </form>

        {logs.isLoading ? (
          <div className="space-y-3 p-5">{[0, 1, 2, 3, 4, 5].map((i) => <Skeleton key={i} className="h-10" />)}</div>
        ) : logs.isError ? (
          <div className="p-5"><ErrorPanel message={(logs.error as ApiError).message} onRetry={() => logs.refetch()} /></div>
        ) : !logs.data || logs.data.content.length === 0 ? (
          <EmptyState icon={ScrollText} title="No audit events" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-surface-2/60 text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th className="px-5 py-3 font-semibold">When</th>
                  <th className="px-5 py-3 font-semibold">Action</th>
                  <th className="px-5 py-3 font-semibold">Outcome</th>
                  <th className="px-5 py-3 font-semibold">Customer</th>
                  <th className="px-5 py-3 font-semibold">Details</th>
                  <th className="px-5 py-3 font-semibold">Request id</th>
                </tr>
              </thead>
              <tbody className={`divide-y divide-border ${logs.isPlaceholderData ? 'opacity-60' : ''}`}>
                {logs.data.content.map((log) => (
                  <tr key={log.id} className="align-top transition hover:bg-surface-2/50">
                    <td className="whitespace-nowrap px-5 py-3 text-muted tabular">{formatDateTime(log.createdAt)}</td>
                    <td className="px-5 py-3"><Badge tone={ACTION_TONE[log.action] ?? 'neutral'}>{titleCase(log.action)}</Badge></td>
                    <td className="px-5 py-3"><StatusBadge status={log.status} /></td>
                    <td className="px-5 py-3">
                      {log.customerId
                        ? <button className="font-mono text-brand hover:underline" onClick={() => {
                            setCustomerId(log.customerId); setCustomerInput(String(log.customerId)); setPage(0);
                          }}>{log.customerId}</button>
                        : <span className="text-muted">unknown</span>}
                    </td>
                    <td className="max-w-[280px] px-5 py-3">
                      <p className="truncate" title={log.description}>{log.description}</p>
                      <p className="text-xs text-muted">{log.resource}{log.resourceId ? ` ${log.resourceId}` : ''}{log.ipAddress ? ` · ${log.ipAddress}` : ''}</p>
                    </td>
                    <td className="whitespace-nowrap px-5 py-3">
                      {log.requestId && (
                        <span className="inline-flex items-center gap-1 font-mono text-xs text-muted">
                          {log.requestId.length > 14 ? `${log.requestId.slice(0, 13)}…` : log.requestId}
                          <CopyButton value={log.requestId} label="Copy request id" />
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {logs.data && <Pagination page={logs.data} onChange={setPage} />}
      </div>
    </div>
  );
}
