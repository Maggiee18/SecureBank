import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Ban, Building2, Lock, Search, Unlock } from 'lucide-react';
import { PageHeader } from '@/components/layout/AppShell';
import { StatusBadge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { CopyButton } from '@/components/ui/CopyButton';
import { EmptyState, ErrorPanel, Pagination, Skeleton } from '@/components/ui/Feedback';
import { Field } from '@/components/ui/Field';
import { Modal } from '@/components/ui/Modal';
import { useAdminAccounts } from '@/hooks/queries';
import type { ApiError } from '@/lib/api';
import { api } from '@/lib/endpoints';
import { formatDate, groupAccount, money } from '@/lib/format';
import { useToast } from '@/lib/toast';
import type { AccountStatus, AdminAccount } from '@/lib/types';

const FILTERS: { value?: AccountStatus; label: string }[] = [
  { label: 'All' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'BLOCKED', label: 'Blocked' },
  { value: 'CLOSED', label: 'Closed' },
];

interface PendingChange {
  account: AdminAccount;
  status: AccountStatus;
}

export function AdminAccountsPage() {
  const [status, setStatus] = useState<AccountStatus | undefined>();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [pending, setPending] = useState<PendingChange | null>(null);
  const accounts = useAdminAccounts(page, status);

  const rows = (accounts.data?.content ?? []).filter((a) => {
    const term = search.trim().toLowerCase();
    return !term || a.accountNumber.includes(term.replace(/\s/g, '')) || a.ownerName.toLowerCase().includes(term)
      || a.ownerEmail.toLowerCase().includes(term);
  });

  return (
    <div className="animate-fade-up">
      <PageHeader title="All accounts" subtitle="Block suspicious accounts, unblock after verification, close empty ones." />

      <div className="card overflow-hidden">
        <div className="flex flex-col gap-3 border-b border-border p-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex rounded-xl bg-surface-2 p-1" role="tablist" aria-label="Filter by status">
            {FILTERS.map((f) => (
              <button key={f.label} role="tab" aria-selected={status === f.value}
                onClick={() => { setStatus(f.value); setPage(0); }}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium transition ${
                  status === f.value ? 'bg-surface text-ink shadow-sm' : 'text-muted hover:text-ink'
                }`}>
                {f.label}
              </button>
            ))}
          </div>
          <div className="relative sm:w-72">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
            <input className="field h-10 pl-9" placeholder="Filter this page by name, email, number"
              value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Filter accounts" />
          </div>
        </div>

        {accounts.isLoading ? (
          <div className="space-y-3 p-5">{[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-12" />)}</div>
        ) : accounts.isError ? (
          <div className="p-5"><ErrorPanel message={(accounts.error as ApiError).message} onRetry={() => accounts.refetch()} /></div>
        ) : rows.length === 0 ? (
          <EmptyState icon={Building2} title="No accounts match" description="Try another status or clear the filter." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-surface-2/60 text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th className="px-5 py-3 font-semibold">Account</th>
                  <th className="px-5 py-3 font-semibold">Owner</th>
                  <th className="px-5 py-3 text-right font-semibold">Balance</th>
                  <th className="px-5 py-3 font-semibold">Status</th>
                  <th className="px-5 py-3 font-semibold">Opened</th>
                  <th className="px-5 py-3 text-right font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody className={`divide-y divide-border ${accounts.isPlaceholderData ? 'opacity-60' : ''}`}>
                {rows.map((a) => (
                  <tr key={a.accountNumber} className="transition hover:bg-surface-2/50">
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-1 font-mono text-[13px]">
                        {groupAccount(a.accountNumber)} <CopyButton value={a.accountNumber} label="Copy account number" />
                      </div>
                      <p className="text-xs text-muted">{a.accountType === 'SAVINGS' ? 'Savings' : 'Current'}</p>
                    </td>
                    <td className="px-5 py-3.5">
                      <p className="font-medium">{a.ownerName}</p>
                      <p className="text-xs text-muted">{a.ownerEmail} · id {a.ownerId}</p>
                    </td>
                    <td className="px-5 py-3.5 text-right font-semibold tabular">{money(a.balance)}</td>
                    <td className="px-5 py-3.5"><StatusBadge status={a.status} /></td>
                    <td className="whitespace-nowrap px-5 py-3.5 text-muted">{formatDate(a.createdAt)}</td>
                    <td className="px-5 py-3.5">
                      <div className="flex justify-end gap-1.5">
                        {a.status === 'ACTIVE' && (
                          <Button size="sm" variant="secondary" onClick={() => setPending({ account: a, status: 'BLOCKED' })}>
                            <Lock className="h-3.5 w-3.5" /> Block
                          </Button>
                        )}
                        {a.status === 'BLOCKED' && (
                          <Button size="sm" variant="secondary" onClick={() => setPending({ account: a, status: 'ACTIVE' })}>
                            <Unlock className="h-3.5 w-3.5" /> Unblock
                          </Button>
                        )}
                        {a.status !== 'CLOSED' && (
                          <Button size="sm" variant="ghost" onClick={() => setPending({ account: a, status: 'CLOSED' })}
                            title={Number(a.balance) !== 0 ? 'Only zero balance accounts can be closed' : 'Close account'}
                            disabled={Number(a.balance) !== 0}>
                            <Ban className="h-3.5 w-3.5" /> Close
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {accounts.data && <Pagination page={accounts.data} onChange={setPage} />}
      </div>

      {pending && <StatusChangeModal change={pending} onClose={() => setPending(null)} />}
    </div>
  );
}

const VERB: Record<AccountStatus, string> = { ACTIVE: 'Unblock', BLOCKED: 'Block', CLOSED: 'Close' };
const DONE: Record<AccountStatus, string> = { ACTIVE: 'unblocked', BLOCKED: 'blocked', CLOSED: 'closed' };

function StatusChangeModal({ change, onClose }: { change: PendingChange; onClose: () => void }) {
  const toast = useToast();
  const client = useQueryClient();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const verb = VERB[change.status];

  async function confirm() {
    setBusy(true);
    try {
      await api.changeAccountStatus(change.account.accountNumber, change.status, reason.trim());
      await client.invalidateQueries({ queryKey: ['admin'] });
      toast.success(`Account ${DONE[change.status]}`, `${groupAccount(change.account.accountNumber)} · reason recorded in the audit log`);
      onClose();
    } catch (e) {
      toast.error(e, `Could not ${verb.toLowerCase()} the account`);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open onClose={onClose} title={`${verb} account?`}
      description={`${groupAccount(change.account.accountNumber)} owned by ${change.account.ownerName}`}
      footer={<>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button variant={change.status === 'ACTIVE' ? 'accent' : 'danger'} onClick={confirm} loading={busy}
          disabled={reason.trim().length < 3}>{verb}</Button>
      </>}>
      {change.status === 'CLOSED' && (
        <p className="mb-4 rounded-xl bg-danger/10 p-3 text-sm text-danger">Closing is permanent. A closed account cannot be reopened.</p>
      )}
      {change.status === 'BLOCKED' && (
        <p className="mb-4 rounded-xl bg-warning/10 p-3 text-sm text-warning">The customer will not be able to deposit, withdraw or transfer until it is unblocked.</p>
      )}
      <Field label="Reason" placeholder="Customer reported a lost card" maxLength={200} value={reason}
        onChange={(e) => setReason(e.target.value)} hint="Stored in the audit log with your admin id." />
    </Modal>
  );
}
