import { useEffect, useId, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ArrowDownLeft, ArrowUpRight, CircleAlert, IndianRupee, Landmark, PiggyBank, ShieldCheck } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Field } from '@/components/ui/Field';
import { Modal } from '@/components/ui/Modal';
import { StatusBadge } from '@/components/ui/Badge';
import { ApiError } from '@/lib/api';
import { api } from '@/lib/endpoints';
import { groupAccount, maskAccount, money, relativeTime, formatDateTime } from '@/lib/format';
import { uuid } from '@/lib/ids';
import { useToast } from '@/lib/toast';
import { useRefreshMoney } from '@/hooks/queries';
import type { Account, AccountType, Transaction } from '@/lib/types';

/** Accepts what a person types (1,000.5) and returns the API form (1000.50), or null if not a valid amount. */
export function parseAmount(raw: string): string | null {
  const cleaned = raw.replace(/,/g, '').trim();
  if (!/^\d{1,15}(\.\d{1,2})?$/.test(cleaned)) return null;
  if (Number(cleaned) <= 0) return null;
  return Number(cleaned).toFixed(2);
}

export function AmountField({ value, onChange, error, label = 'Amount', hint }: {
  value: string; onChange: (value: string) => void; error?: string; label?: string; hint?: string;
}) {
  return (
    <Field
      label={label}
      inputMode="decimal"
      placeholder="0.00"
      leading={<IndianRupee className="h-4 w-4" />}
      value={value}
      onChange={(e) => onChange(e.target.value.replace(/[^\d.,]/g, ''))}
      error={error}
      hint={hint}
      className="[&_input]:text-lg [&_input]:font-semibold [&_input]:tabular"
    />
  );
}

export function AccountCard({ account, compact = false }: { account: Account; compact?: boolean }) {
  const Icon = account.accountType === 'SAVINGS' ? PiggyBank : Landmark;
  const inactive = account.status !== 'ACTIVE';
  return (
    <Link
      to={`/accounts/${account.accountNumber}`}
      className={`group relative block overflow-hidden rounded-2xl p-5 text-white shadow-card transition hover:-translate-y-0.5 hover:shadow-lift ${
        inactive ? 'bg-gradient-to-br from-slate-600 to-slate-800' : 'hero-gradient'
      }`}
    >
      <div className="absolute -right-10 -top-12 h-40 w-40 rounded-full border border-white/10" />
      <div className="absolute -right-2 -top-4 h-24 w-24 rounded-full border border-white/10" />
      <div className="relative flex items-start justify-between">
        <span className="inline-flex items-center gap-2 text-sm font-medium text-white/85">
          <Icon className="h-4 w-4" /> {account.accountType === 'SAVINGS' ? 'Savings' : 'Current'}
        </span>
        {inactive
          ? <span className="rounded-full bg-white/15 px-2 py-0.5 text-[11px] font-semibold">{account.status}</span>
          : <ShieldCheck className="h-4 w-4 text-emerald-300" />}
      </div>
      <p className={`relative font-bold tracking-tight tabular ${compact ? 'mt-6 text-2xl' : 'mt-8 text-[28px]'}`}>
        {money(account.balance)}
      </p>
      <p className="relative mt-1 font-mono text-sm tracking-widest text-white/70">{groupAccount(account.accountNumber)}</p>
    </Link>
  );
}

export function TransactionRow({ txn, accountNumber }: { txn: Transaction; accountNumber?: string }) {
  const credit = txn.direction === 'CREDIT';
  const failed = txn.status === 'FAILED';
  const title = txn.type === 'TRANSFER'
    ? credit ? `From ${txn.counterpartyAccount ?? 'account'}` : `To ${txn.counterpartyAccount ?? 'account'}`
    : txn.type === 'DEPOSIT' ? 'Deposit' : 'Withdrawal';
  return (
    <li className="flex items-center gap-4 px-5 py-3.5">
      <span className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl ${
        failed ? 'bg-danger/10 text-danger' : credit ? 'bg-success/10 text-success' : 'bg-surface-2 text-ink'
      }`}>
        {failed ? <CircleAlert className="h-[18px] w-[18px]" /> : credit
          ? <ArrowDownLeft className="h-[18px] w-[18px]" /> : <ArrowUpRight className="h-[18px] w-[18px]" />}
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold">{txn.description || title}</p>
        <p className="mt-0.5 truncate text-xs text-muted" title={formatDateTime(txn.createdAt)}>
          {txn.description ? `${title} · ` : ''}{relativeTime(txn.createdAt)}
          {accountNumber && <> · {maskAccount(accountNumber)}</>}
          <span className="hidden font-mono sm:inline"> · {txn.transactionReference}</span>
        </p>
        {failed && txn.failureReason && (
          <p className="mt-0.5 truncate text-xs text-danger">{txn.failureReason.replace(/^[A-Z_]+: /, '')}</p>
        )}
      </div>
      <div className="text-right">
        <p className={`text-sm font-semibold tabular ${failed ? 'text-muted line-through' : credit ? 'text-success' : ''}`}>
          {credit ? '+' : '−'}{money(txn.amount)}
        </p>
        {failed && <div className="mt-1"><StatusBadge status="FAILED" /></div>}
      </div>
    </li>
  );
}

/** Rebuilds the balance before each statement line, newest to oldest, to draw a trend. */
export function balanceHistory(currentBalance: number, transactions: Transaction[]) {
  const points = [{ at: new Date().toISOString(), balance: currentBalance }];
  let balance = currentBalance;
  for (const txn of transactions) {
    if (txn.status !== 'SUCCESS') continue;
    balance = txn.direction === 'CREDIT' ? balance - Number(txn.amount) : balance + Number(txn.amount);
    points.push({ at: txn.createdAt, balance });
  }
  return points.reverse();
}

export function BalanceTrend({ points }: { points: { at: string; balance: number }[] }) {
  const gradientId = useId();
  const path = useMemo(() => {
    if (points.length < 2) return null;
    const width = 600, height = 120, pad = 8;
    const values = points.map((p) => p.balance);
    const min = Math.min(...values), max = Math.max(...values);
    const span = max - min || 1;
    const xy = points.map((p, i) => [
      (i / (points.length - 1)) * width,
      pad + (1 - (p.balance - min) / span) * (height - pad * 2),
    ]);
    const line = xy.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
    return { line, area: `${line} L${width} ${height} L0 ${height} Z` };
  }, [points]);

  if (!path) {
    return <p className="py-8 text-center text-sm text-muted">Your balance trend appears after a few transactions.</p>;
  }
  return (
    <svg viewBox="0 0 600 120" preserveAspectRatio="none" className="h-28 w-full" role="img"
      aria-label="Balance trend over recent transactions">
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="rgb(var(--accent))" stopOpacity="0.28" />
          <stop offset="100%" stopColor="rgb(var(--accent))" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={path.area} fill={`url(#${gradientId})`} />
      <path d={path.line} fill="none" stroke="rgb(var(--accent))" strokeWidth="2.5" vectorEffect="non-scaling-stroke"
        strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  );
}

type CashKind = 'deposit' | 'withdraw';

export function CashModal({ kind, account, open, onClose }: {
  kind: CashKind; account: Account; open: boolean; onClose: () => void;
}) {
  const toast = useToast();
  const refresh = useRefreshMoney();
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  // One key per intended operation: a double click or a retry after a timeout reuses it.
  const [idempotencyKey, setIdempotencyKey] = useState(uuid);

  useEffect(() => {
    if (open) {
      setAmount('');
      setNote('');
      setError(undefined);
      setIdempotencyKey(uuid());
    }
  }, [open]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const parsed = parseAmount(amount);
    if (!parsed) {
      setError('Enter an amount above zero with at most 2 decimals');
      return;
    }
    if (kind === 'withdraw' && Number(parsed) > Number(account.balance)) {
      setError(`You can withdraw up to ${money(account.balance)}`);
      return;
    }
    setBusy(true);
    try {
      const call = kind === 'deposit' ? api.deposit : api.withdraw;
      const result = await call(account.accountNumber, parsed, note.trim(), idempotencyKey);
      await refresh();
      toast.success(
        kind === 'deposit' ? `${money(parsed)} deposited` : `${money(parsed)} withdrawn`,
        `New balance ${money(result.data.balanceAfter)} · ${result.data.transactionReference}`,
      );
      onClose();
    } catch (e) {
      if (e instanceof ApiError && e.fieldError('amount')) setError(e.fieldError('amount'));
      else toast.error(e, kind === 'deposit' ? 'Deposit failed' : 'Withdrawal failed');
      if (e instanceof ApiError && !e.isNetworkError) setIdempotencyKey(uuid());
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={kind === 'deposit' ? 'Add money' : 'Withdraw money'}
      description={`${account.accountType === 'SAVINGS' ? 'Savings' : 'Current'} ${maskAccount(account.accountNumber)} · Balance ${money(account.balance)}`}
    >
      <form onSubmit={submit} className="space-y-4">
        <AmountField value={amount} onChange={(v) => { setAmount(v); setError(undefined); }} error={error} />
        <Field label="Note (optional)" placeholder={kind === 'deposit' ? 'Salary, cash deposit…' : 'ATM, rent…'}
          maxLength={140} value={note} onChange={(e) => setNote(e.target.value)} />
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" variant={kind === 'deposit' ? 'accent' : 'primary'} loading={busy}>
            {kind === 'deposit' ? 'Deposit' : 'Withdraw'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export function OpenAccountModal({ open, onClose, onOpened }: {
  open: boolean; onClose: () => void; onOpened?: (account: Account) => void;
}) {
  const toast = useToast();
  const refresh = useRefreshMoney();
  const [type, setType] = useState<AccountType>('SAVINGS');
  const [busy, setBusy] = useState(false);

  const options: { value: AccountType; title: string; text: string; icon: typeof PiggyBank }[] = [
    { value: 'SAVINGS', title: 'Savings', text: 'For keeping and growing money.', icon: PiggyBank },
    { value: 'CURRENT', title: 'Current', text: 'For frequent payments and business use.', icon: Landmark },
  ];

  async function submit() {
    setBusy(true);
    try {
      const account = await api.openAccount(type);
      await refresh();
      toast.success('Account opened', `${type === 'SAVINGS' ? 'Savings' : 'Current'} account ${groupAccount(account.accountNumber)}`);
      onOpened?.(account);
      onClose();
    } catch (e) {
      toast.error(e, 'Could not open the account');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Open a new account" description="Your account number is generated instantly."
      footer={<>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button onClick={submit} loading={busy}>Open account</Button>
      </>}>
      <div className="grid gap-3 sm:grid-cols-2" role="radiogroup" aria-label="Account type">
        {options.map(({ value, title, text, icon: Icon }) => (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={type === value}
            onClick={() => setType(value)}
            className={`rounded-xl border p-4 text-left transition ${
              type === value ? 'border-brand bg-brand/5 ring-4 ring-brand/10' : 'border-border hover:bg-surface-2'
            }`}
          >
            <Icon className={`h-5 w-5 ${type === value ? 'text-brand' : 'text-muted'}`} />
            <p className="mt-3 font-semibold">{title}</p>
            <p className="mt-0.5 text-xs text-muted">{text}</p>
          </button>
        ))}
      </div>
    </Modal>
  );
}
