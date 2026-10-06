import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowDownLeft, ArrowLeft, ArrowLeftRight, ArrowUpRight, ReceiptText } from 'lucide-react';
import { BalanceTrend, CashModal, TransactionRow, balanceHistory } from '@/components/money';
import { Button, buttonClass } from '@/components/ui/Button';
import { StatusBadge } from '@/components/ui/Badge';
import { CopyButton } from '@/components/ui/CopyButton';
import { EmptyState, ErrorPanel, Pagination, Skeleton } from '@/components/ui/Feedback';
import { useAccount, useStatement } from '@/hooks/queries';
import { api } from '@/lib/endpoints';
import type { ApiError } from '@/lib/api';
import { formatDate, groupAccount, money } from '@/lib/format';

const PAGE_SIZES = [10, 20, 50];
const TREND_POINTS = 50;

export function AccountDetailPage() {
  const { accountNumber = '' } = useParams();
  const account = useAccount(accountNumber);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const statement = useStatement(accountNumber, page, size);
  const trend = useQuery({
    queryKey: ['statement', accountNumber, 'trend'],
    queryFn: () => api.statement(accountNumber, 0, TREND_POINTS),
    enabled: account.isSuccess,
  });
  const [cash, setCash] = useState<'deposit' | 'withdraw' | null>(null);

  if (account.isError) {
    const err = account.error as ApiError;
    return (
      <div className="animate-fade-up">
        <BackLink />
        <ErrorPanel
          message={err.status === 403 ? 'This account belongs to another customer.' : err.status === 404 ? 'No account with this number exists.' : err.message}
          requestId={err.requestId}
        />
      </div>
    );
  }

  const a = account.data;
  const active = a?.status === 'ACTIVE';

  return (
    <div className="animate-fade-up">
      <BackLink />
      <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <section className="card p-6">
          {!a ? <Skeleton className="h-32" /> : (
            <>
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-lg font-semibold">{a.accountType === 'SAVINGS' ? 'Savings account' : 'Current account'}</h1>
                <StatusBadge status={a.status} />
              </div>
              <div className="mt-1 flex items-center gap-1 text-sm text-muted">
                <span className="font-mono tracking-wide">{groupAccount(a.accountNumber)}</span>
                <CopyButton value={a.accountNumber} label="Copy account number" />
                <span className="ml-2">Opened {formatDate(a.createdAt)}</span>
              </div>
              <p className="mt-6 text-sm text-muted">Available balance</p>
              <p className="mt-1 text-4xl font-bold tracking-tight tabular">{money(a.balance)}</p>
              {!active && (
                <p className="mt-4 rounded-xl bg-warning/10 p-3 text-sm text-warning">
                  This account is {a.status.toLowerCase()}. Deposits, withdrawals and transfers are disabled. Contact support to resolve it.
                </p>
              )}
              <div className="mt-6 flex flex-wrap gap-2">
                <Button variant="accent" onClick={() => setCash('deposit')} disabled={!active}>
                  <ArrowDownLeft className="h-4 w-4" /> Add money
                </Button>
                <Button variant="secondary" onClick={() => setCash('withdraw')} disabled={!active}>
                  <ArrowUpRight className="h-4 w-4" /> Withdraw
                </Button>
                {active && (
                  <Link to={`/transfer?from=${a.accountNumber}`} className={buttonClass('secondary')}>
                    <ArrowLeftRight className="h-4 w-4" /> Transfer
                  </Link>
                )}
              </div>
            </>
          )}
        </section>

        <section className="card p-6">
          <h2 className="font-semibold">Balance trend</h2>
          <p className="text-xs text-muted">Last {TREND_POINTS} successful movements</p>
          <div className="mt-4">
            {a && trend.data ? <BalanceTrend points={balanceHistory(Number(a.balance), trend.data.content)} /> : <Skeleton className="h-28" />}
          </div>
        </section>
      </div>

      <section className="card mt-6 overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-4">
          <div>
            <h2 className="font-semibold">Statement</h2>
            <p className="text-xs text-muted">Newest first. Failed attempts are shown so nothing is hidden from you.</p>
          </div>
          <label className="flex items-center gap-2 text-sm text-muted">
            Rows
            <select
              value={size}
              onChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}
              className="field h-9 w-auto py-1"
            >
              {PAGE_SIZES.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
        </div>
        {statement.isLoading ? (
          <div className="space-y-3 p-5">{[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-12" />)}</div>
        ) : statement.isError ? (
          <div className="p-5"><ErrorPanel message={(statement.error as ApiError).message} onRetry={() => statement.refetch()} /></div>
        ) : statement.data && statement.data.content.length > 0 ? (
          <>
            <ul className={`divide-y divide-border transition-opacity ${statement.isPlaceholderData ? 'opacity-60' : ''}`}>
              {statement.data.content.map((t) => <TransactionRow key={t.transactionReference} txn={t} />)}
            </ul>
            <Pagination page={statement.data} onChange={setPage} />
          </>
        ) : (
          <EmptyState icon={ReceiptText} title="No transactions yet" description="Add money to get started." />
        )}
      </section>

      {a && cash && <CashModal kind={cash} account={a} open onClose={() => setCash(null)} />}
    </div>
  );
}

function BackLink() {
  return (
    <Link to="/accounts" className="mb-6 inline-flex items-center gap-1.5 text-sm font-medium text-muted hover:text-ink">
      <ArrowLeft className="h-4 w-4" /> All accounts
    </Link>
  );
}
