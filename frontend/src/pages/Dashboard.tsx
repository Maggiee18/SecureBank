import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQueries } from '@tanstack/react-query';
import { ArrowLeftRight, ArrowRight, Plus, Sparkles, Wallet } from 'lucide-react';
import { PageHeader } from '@/components/layout/AppShell';
import { AccountCard, OpenAccountModal, TransactionRow } from '@/components/money';
import { Button, buttonClass } from '@/components/ui/Button';
import { EmptyState, ErrorPanel, Skeleton } from '@/components/ui/Feedback';
import { useAccounts, useMe } from '@/hooks/queries';
import { api } from '@/lib/endpoints';
import { ApiError } from '@/lib/api';
import { money } from '@/lib/format';

const RECENT_PER_ACCOUNT = 5;

function greeting() {
  const hour = new Date().getHours();
  return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
}

export function DashboardPage() {
  const { data: me } = useMe();
  const accounts = useAccounts();
  const [opening, setOpening] = useState(false);
  const list = accounts.data ?? [];

  // There is no cross-account feed endpoint, so the first statement page of each account is merged here.
  const recent = useQueries({
    queries: list.map((a) => ({
      queryKey: ['recent', a.accountNumber],
      queryFn: () => api.statement(a.accountNumber, 0, RECENT_PER_ACCOUNT),
    })),
  });

  const activity = useMemo(() => recent
    .flatMap((q, i) => (q.data?.content ?? []).map((txn) => ({ txn, accountNumber: list[i]?.accountNumber })))
    .sort((a, b) => b.txn.createdAt.localeCompare(a.txn.createdAt))
    .slice(0, 7), [recent, list]);

  const total = list.filter((a) => a.status !== 'CLOSED').reduce((sum, a) => sum + Number(a.balance), 0);
  const firstName = me?.fullName.split(' ')[0];

  if (accounts.isError) {
    const err = accounts.error as ApiError;
    return <ErrorPanel message={err.message} requestId={err.requestId} onRetry={() => accounts.refetch()} />;
  }

  return (
    <div className="animate-fade-up">
      <PageHeader
        title={`${greeting()}${firstName ? `, ${firstName}` : ''}`}
        subtitle="Here's where your money stands today."
        actions={<>
          <Button variant="secondary" onClick={() => setOpening(true)}><Plus className="h-4 w-4" /> Open account</Button>
          <Link to="/transfer" className={buttonClass()}><ArrowLeftRight className="h-4 w-4" /> Send money</Link>
        </>}
      />

      <section className="hero-gradient relative mb-8 overflow-hidden rounded-2xl p-6 text-white shadow-lift sm:p-8">
        <div className="absolute -right-16 -top-20 h-64 w-64 rounded-full border border-white/10" />
        <div className="absolute -right-4 -top-8 h-40 w-40 rounded-full border border-white/10" />
        <p className="relative text-sm font-medium text-blue-100/80">Total balance</p>
        {accounts.isLoading
          ? <Skeleton className="relative mt-3 h-10 w-56 bg-white/15" />
          : <p className="relative mt-2 text-4xl font-bold tracking-tight tabular sm:text-5xl">{money(total)}</p>}
        <div className="relative mt-6 flex flex-wrap gap-x-8 gap-y-2 text-sm text-blue-100/85">
          <span><strong className="text-white">{list.length}</strong> account{list.length === 1 ? '' : 's'}</span>
          <span><strong className="text-white">{list.filter((a) => a.status === 'ACTIVE').length}</strong> active</span>
          <span className="inline-flex items-center gap-1.5"><Sparkles className="h-4 w-4 text-emerald-300" /> Transfers protected against duplicates</span>
        </div>
      </section>

      <div className="grid gap-8 lg:grid-cols-[1fr_1.15fr]">
        <section>
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-semibold">Your accounts</h2>
            <Link to="/accounts" className="inline-flex items-center gap-1 text-sm font-semibold text-brand hover:underline">
              Manage <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
          {accounts.isLoading ? (
            <div className="grid gap-4"><Skeleton className="h-40" /><Skeleton className="h-40" /></div>
          ) : list.length === 0 ? (
            <div className="card">
              <EmptyState icon={Wallet} title="No accounts yet" description="Open a savings or current account to start banking."
                action={<Button onClick={() => setOpening(true)}><Plus className="h-4 w-4" /> Open your first account</Button>} />
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
              {list.map((a) => <AccountCard key={a.accountNumber} account={a} compact />)}
            </div>
          )}
        </section>

        <section className="card overflow-hidden">
          <div className="flex items-center justify-between border-b border-border px-5 py-4">
            <h2 className="font-semibold">Recent activity</h2>
            <span className="text-xs text-muted">Across all accounts</span>
          </div>
          {recent.some((q) => q.isLoading) ? (
            <div className="space-y-3 p-5">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-12" />)}</div>
          ) : activity.length === 0 ? (
            <EmptyState icon={ArrowLeftRight} title="No transactions yet"
              description="Deposits, withdrawals and transfers will show up here." />
          ) : (
            <ul className="divide-y divide-border">
              {activity.map(({ txn, accountNumber }) => (
                <TransactionRow key={txn.transactionReference} txn={txn} accountNumber={accountNumber} />
              ))}
            </ul>
          )}
        </section>
      </div>

      <OpenAccountModal open={opening} onClose={() => setOpening(false)} />
    </div>
  );
}
