import { useState } from 'react';
import { Plus, Wallet } from 'lucide-react';
import { PageHeader } from '@/components/layout/AppShell';
import { AccountCard, OpenAccountModal } from '@/components/money';
import { Button } from '@/components/ui/Button';
import { EmptyState, ErrorPanel, Skeleton } from '@/components/ui/Feedback';
import { useAccounts } from '@/hooks/queries';
import type { ApiError } from '@/lib/api';

export function AccountsPage() {
  const accounts = useAccounts();
  const [opening, setOpening] = useState(false);

  return (
    <div className="animate-fade-up">
      <PageHeader
        title="Accounts"
        subtitle="Open a new account or pick one to see its statement."
        actions={<Button onClick={() => setOpening(true)}><Plus className="h-4 w-4" /> Open account</Button>}
      />
      {accounts.isError ? (
        <ErrorPanel message={(accounts.error as ApiError).message} requestId={(accounts.error as ApiError).requestId}
          onRetry={() => accounts.refetch()} />
      ) : accounts.isLoading ? (
        <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-44" />)}</div>
      ) : accounts.data?.length === 0 ? (
        <div className="card">
          <EmptyState icon={Wallet} title="You don't have an account yet"
            description="Savings for keeping money, current for frequent payments. You can open both."
            action={<Button onClick={() => setOpening(true)}><Plus className="h-4 w-4" /> Open your first account</Button>} />
        </div>
      ) : (
        <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
          {accounts.data?.map((a) => <AccountCard key={a.accountNumber} account={a} />)}
          <button
            onClick={() => setOpening(true)}
            className="flex min-h-[176px] flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-border text-sm font-semibold text-muted transition hover:border-brand/40 hover:text-brand"
          >
            <Plus className="h-5 w-5" /> Open another account
          </button>
        </div>
      )}
      <OpenAccountModal open={opening} onClose={() => setOpening(false)} />
    </div>
  );
}
