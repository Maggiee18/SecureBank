import { useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  ArrowDown, ArrowLeft, BadgeCheck, CircleCheck, KeyRound, RefreshCw, Repeat2, ShieldCheck, TriangleAlert, Wallet,
} from 'lucide-react';
import { PageHeader } from '@/components/layout/AppShell';
import { AmountField, parseAmount } from '@/components/money';
import { Badge } from '@/components/ui/Badge';
import { Button, buttonClass } from '@/components/ui/Button';
import { CopyButton } from '@/components/ui/CopyButton';
import { EmptyState, Skeleton } from '@/components/ui/Feedback';
import { Field } from '@/components/ui/Field';
import { useAccounts, useRefreshMoney } from '@/hooks/queries';
import { ApiError } from '@/lib/api';
import { api } from '@/lib/endpoints';
import { formatDateTime, groupAccount, maskAccount, money } from '@/lib/format';
import { uuid } from '@/lib/ids';
import { useToast } from '@/lib/toast';
import type { TransferResult } from '@/lib/types';

type Step = 'form' | 'review' | 'done';

interface Draft {
  from: string;
  to: string;
  amount: string;
  note: string;
}

const STEPS: { id: Step; label: string }[] = [
  { id: 'form', label: 'Details' },
  { id: 'review', label: 'Review' },
  { id: 'done', label: 'Done' },
];

export function TransferPage() {
  const [params] = useSearchParams();
  const accounts = useAccounts();
  const refresh = useRefreshMoney();
  const toast = useToast();
  const active = useMemo(() => (accounts.data ?? []).filter((a) => a.status === 'ACTIVE'), [accounts.data]);

  const [step, setStep] = useState<Step>('form');
  const [draft, setDraft] = useState<Draft>({ from: params.get('from') ?? '', to: '', amount: '', note: '' });
  const [errors, setErrors] = useState<Partial<Record<keyof Draft, string>>>({});
  // One key per intended transfer. It is created when the customer reviews, and reused on every retry.
  const [idempotencyKey, setIdempotencyKey] = useState('');
  const [sending, setSending] = useState(false);
  const [uncertain, setUncertain] = useState<ApiError | null>(null);
  const [receipt, setReceipt] = useState<{ result: TransferResult; requestId?: string } | null>(null);
  const [replay, setReplay] = useState<{ replayed: boolean; reference: string } | null>(null);

  const source = active.find((a) => a.accountNumber === draft.from) ?? active[0];
  const fromNumber = draft.from || source?.accountNumber || '';
  const ownOthers = active.filter((a) => a.accountNumber !== fromNumber);

  function validate(): string | null {
    const next: typeof errors = {};
    const amount = parseAmount(draft.amount);
    if (!fromNumber) next.from = 'Choose the account to pay from';
    if (!/^\d{12}$/.test(draft.to)) next.to = 'Enter the 12 digit account number';
    else if (draft.to === fromNumber) next.to = 'Choose a different account from the one you are paying from';
    if (!amount) next.amount = 'Enter an amount above zero with at most 2 decimals';
    else if (source && Number(amount) > Number(source.balance)) next.amount = `Exceeds your balance of ${money(source.balance)}`;
    setErrors(next);
    return Object.keys(next).length ? null : amount;
  }

  function review(event: FormEvent) {
    event.preventDefault();
    const amount = validate();
    if (!amount) return;
    setDraft((d) => ({ ...d, from: fromNumber, amount }));
    setIdempotencyKey(uuid());
    setUncertain(null);
    setStep('review');
  }

  const body = () => ({
    sourceAccountNumber: draft.from,
    destinationAccountNumber: draft.to,
    amount: draft.amount,
    description: draft.note.trim() || undefined,
  });

  async function send() {
    setSending(true);
    setUncertain(null);
    try {
      const response = await api.transfer(body(), idempotencyKey);
      setReceipt({ result: response.data, requestId: response.requestId });
      setReplay(null);
      setStep('done');
      await refresh();
    } catch (e) {
      const error = e instanceof ApiError ? e : new ApiError(0, 'UNKNOWN', String(e), undefined);
      if (error.isNetworkError || error.status >= 500 || error.code === 'IDEMPOTENCY_REQUEST_IN_PROGRESS'
          || error.code === 'CONCURRENT_UPDATE') {
        // Outcome unknown or retryable: keep the same key so a retry can never pay twice.
        setUncertain(error);
      } else {
        toast.error(error, 'Transfer not completed');
        if (error.fieldError('destinationAccountNumber') || error.code === 'RESOURCE_NOT_FOUND'
            || error.code === 'INVALID_TRANSACTION') {
          setErrors({ to: error.status === 404 ? 'No account exists with this number' : error.message });
        }
        setStep('form');
      }
    } finally {
      setSending(false);
    }
  }

  /** Demo of idempotency: the exact same request and key again. The server must not move money twice. */
  async function resendSameRequest() {
    setSending(true);
    try {
      const response = await api.transfer(body(), idempotencyKey);
      setReplay({ replayed: response.replayed, reference: response.data.transactionReference });
      await refresh();
    } catch (e) {
      toast.error(e, 'Resend failed');
    } finally {
      setSending(false);
    }
  }

  function startOver() {
    setDraft({ from: draft.from, to: '', amount: '', note: '' });
    setErrors({});
    setReceipt(null);
    setReplay(null);
    setStep('form');
  }

  if (accounts.isLoading) return <Skeleton className="h-96" />;

  if (active.length === 0) {
    return (
      <div className="animate-fade-up">
        <PageHeader title="Send money" />
        <div className="card">
          <EmptyState icon={Wallet} title="You need an active account first"
            description="Open an account and add money, then you can send transfers."
            action={<Link to="/accounts" className={buttonClass()}>Go to accounts</Link>} />
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl animate-fade-up">
      <PageHeader title="Send money" subtitle="Transfers are instant and protected against accidental duplicates." />

      <ol className="mb-6 flex items-center gap-2 text-sm" aria-label="Progress">
        {STEPS.map((s, i) => {
          const index = STEPS.findIndex((x) => x.id === step);
          const state = i < index ? 'done' : i === index ? 'current' : 'todo';
          return (
            <li key={s.id} className="flex flex-1 items-center gap-2">
              <span className={`grid h-7 w-7 place-items-center rounded-full text-xs font-bold ${
                state === 'todo' ? 'bg-surface-2 text-muted' : state === 'current' ? 'bg-brand text-brand-fg' : 'bg-success text-white'
              }`}>{state === 'done' ? '✓' : i + 1}</span>
              <span className={state === 'todo' ? 'text-muted' : 'font-semibold'}>{s.label}</span>
              {i < STEPS.length - 1 && <span className="mx-1 h-px flex-1 bg-border" />}
            </li>
          );
        })}
      </ol>

      {step === 'form' && (
        <form onSubmit={review} className="card space-y-6 p-6" noValidate>
          <div>
            <span className="label">From</span>
            <div className="grid gap-2 sm:grid-cols-2" role="radiogroup" aria-label="Pay from">
              {active.map((a) => {
                const selected = a.accountNumber === fromNumber;
                return (
                  <button key={a.accountNumber} type="button" role="radio" aria-checked={selected}
                    onClick={() => setDraft((d) => ({ ...d, from: a.accountNumber }))}
                    className={`rounded-xl border p-3.5 text-left transition ${
                      selected ? 'border-brand bg-brand/5 ring-4 ring-brand/10' : 'border-border hover:bg-surface-2'
                    }`}>
                    <p className="text-xs text-muted">{a.accountType === 'SAVINGS' ? 'Savings' : 'Current'} · {maskAccount(a.accountNumber)}</p>
                    <p className="mt-1 font-semibold tabular">{money(a.balance)}</p>
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <Field label="To account number" inputMode="numeric" placeholder="12 digit account number" maxLength={12}
              className="[&_input]:font-mono [&_input]:tracking-widest"
              value={draft.to} error={errors.to}
              onChange={(e) => { setDraft((d) => ({ ...d, to: e.target.value.replace(/\D/g, '').slice(0, 12) })); setErrors((x) => ({ ...x, to: undefined })); }} />
            {ownOthers.length > 0 && (
              <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
                <span className="text-muted">Your accounts:</span>
                {ownOthers.map((a) => (
                  <button key={a.accountNumber} type="button" onClick={() => setDraft((d) => ({ ...d, to: a.accountNumber }))}
                    className="rounded-full border border-border px-2.5 py-1 font-medium hover:border-brand/40 hover:text-brand">
                    {a.accountType === 'SAVINGS' ? 'Savings' : 'Current'} {maskAccount(a.accountNumber)}
                  </button>
                ))}
              </div>
            )}
          </div>

          <AmountField value={draft.amount} error={errors.amount}
            hint={source ? `Available ${money(source.balance)}` : undefined}
            onChange={(v) => { setDraft((d) => ({ ...d, amount: v })); setErrors((x) => ({ ...x, amount: undefined })); }} />

          <Field label="Note (optional)" placeholder="Rent for October" maxLength={140}
            value={draft.note} onChange={(e) => setDraft((d) => ({ ...d, note: e.target.value }))} />

          <Button type="submit" size="lg" className="w-full">Review transfer</Button>
        </form>
      )}

      {step === 'review' && (
        <div className="card overflow-hidden">
          <div className="p-6">
            <p className="text-sm text-muted">You're sending</p>
            <p className="mt-1 text-4xl font-bold tracking-tight tabular">{money(draft.amount)}</p>
            <div className="mt-6 space-y-2">
              <Party label="From" value={`${source?.accountType === 'SAVINGS' ? 'Savings' : 'Current'} ${groupAccount(draft.from)}`} />
              <div className="flex justify-center"><ArrowDown className="h-4 w-4 text-muted" /></div>
              <Party label="To" value={groupAccount(draft.to)} />
            </div>
            {draft.note.trim() && <p className="mt-4 text-sm text-muted">Note: <span className="text-ink">{draft.note.trim()}</span></p>}
          </div>

          <div className="flex items-start gap-3 border-t border-border bg-surface-2/60 px-6 py-4 text-xs text-muted">
            <KeyRound className="mt-0.5 h-4 w-4 shrink-0 text-brand" />
            <p>
              Protected by Idempotency-Key <code className="font-mono text-ink">{idempotencyKey.slice(0, 8)}…</code>.
              If the connection drops, retrying sends the same key, so you can never be charged twice.
            </p>
          </div>

          {uncertain && (
            <div role="alert" className="flex gap-3 border-t border-warning/30 bg-warning/10 px-6 py-4 text-sm">
              <TriangleAlert className="mt-0.5 h-5 w-5 shrink-0 text-warning" />
              <div>
                <p className="font-semibold">
                  {uncertain.code === 'CONCURRENT_UPDATE' ? 'Your account was busy, nothing was sent' : 'We could not confirm this transfer'}
                </p>
                <p className="mt-0.5 text-muted">
                  {uncertain.code === 'CONCURRENT_UPDATE'
                    ? 'Another payment updated the account at the same moment. Retrying is safe.'
                    : 'Retrying is safe: it reuses the same key, so if the first attempt went through you will just see its receipt.'}
                </p>
                {uncertain.requestId && <p className="mt-1 text-xs text-muted">Support ref <code className="font-mono">{uncertain.requestId}</code></p>}
              </div>
            </div>
          )}

          <div className="flex gap-2 border-t border-border p-4">
            <Button variant="ghost" onClick={() => setStep('form')} disabled={sending}><ArrowLeft className="h-4 w-4" /> Edit</Button>
            <div className="flex-1" />
            <Button size="lg" onClick={send} loading={sending}>
              {uncertain ? <><RefreshCw className="h-4 w-4" /> Retry safely</> : <><ShieldCheck className="h-4 w-4" /> Confirm and send</>}
            </Button>
          </div>
        </div>
      )}

      {step === 'done' && receipt && (
        <div className="card animate-pop overflow-hidden">
          <div className="flex flex-col items-center px-6 pb-6 pt-8 text-center">
            <span className="grid h-14 w-14 place-items-center rounded-full bg-success/10 text-success">
              <CircleCheck className="h-8 w-8" />
            </span>
            <p className="mt-4 text-sm text-muted">Transfer successful</p>
            <p className="mt-1 text-4xl font-bold tracking-tight tabular">{money(receipt.result.amount)}</p>
            <p className="mt-1 text-sm text-muted">to {groupAccount(receipt.result.destinationAccountNumber)}</p>
          </div>
          <dl className="divide-y divide-border border-y border-border text-sm">
            <Row label="Reference">
              <span className="font-mono">{receipt.result.transactionReference}</span>
              <CopyButton value={receipt.result.transactionReference} label="Copy reference" />
            </Row>
            <Row label="From">{groupAccount(receipt.result.sourceAccountNumber)}</Row>
            <Row label="Balance after"><span className="tabular">{money(receipt.result.sourceBalanceAfter)}</span></Row>
            <Row label="Date">{formatDateTime(receipt.result.createdAt)}</Row>
            {receipt.result.description && <Row label="Note">{receipt.result.description}</Row>}
            {receipt.requestId && <Row label="Request id"><span className="font-mono text-xs">{receipt.requestId}</span></Row>}
          </dl>

          <div className="space-y-3 bg-surface-2/60 p-5">
            <div className="flex items-start gap-3">
              <Repeat2 className="mt-0.5 h-5 w-5 shrink-0 text-brand" />
              <div className="text-sm">
                <p className="font-semibold">See duplicate protection in action</p>
                <p className="mt-0.5 text-muted">Send this exact request again with the same Idempotency-Key, like a double tap or a retry after a timeout.</p>
              </div>
            </div>
            <Button variant="secondary" size="sm" onClick={resendSameRequest} loading={sending}>Send the same request again</Button>
            {replay && (
              <div className="flex animate-fade-up items-start gap-2.5 rounded-xl border border-success/25 bg-success/10 p-3 text-sm">
                <BadgeCheck className="mt-0.5 h-4 w-4 shrink-0 text-success" />
                <div>
                  <p className="font-semibold">
                    {replay.replayed ? 'Replayed, no money moved' : 'Executed again'}
                    {replay.replayed && <span className="ml-2 align-middle"><Badge tone="success">Idempotent-Replayed: true</Badge></span>}
                  </p>
                  <p className="mt-0.5 text-muted">
                    The server returned the original receipt <code className="font-mono">{replay.reference}</code>. Your balance is unchanged.
                  </p>
                </div>
              </div>
            )}
          </div>

          <div className="flex flex-wrap gap-2 border-t border-border p-4">
            <Link to={`/accounts/${receipt.result.sourceAccountNumber}`} className={buttonClass('secondary')}>View statement</Link>
            <div className="flex-1" />
            <Button onClick={startOver}>New transfer</Button>
          </div>
        </div>
      )}
    </div>
  );
}

function Party({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between rounded-xl border border-border px-4 py-3">
      <span className="text-sm text-muted">{label}</span>
      <span className="font-mono text-sm font-semibold tracking-wide">{value}</span>
    </div>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4 px-6 py-3">
      <dt className="text-muted">{label}</dt>
      <dd className="flex items-center gap-1 text-right font-medium">{children}</dd>
    </div>
  );
}
