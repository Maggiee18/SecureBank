import type { ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { useAuth } from '@/lib/auth';
import { AccountDetailPage } from '@/pages/AccountDetail';
import { AccountsPage } from '@/pages/Accounts';
import { AdminAccountsPage } from '@/pages/admin/AdminAccounts';
import { AuditLogPage } from '@/pages/admin/AuditLog';
import { DashboardPage } from '@/pages/Dashboard';
import { LoginPage } from '@/pages/Login';
import { NotFoundPage } from '@/pages/NotFound';
import { ProfilePage } from '@/pages/Profile';
import { RegisterPage } from '@/pages/Register';
import { TransferPage } from '@/pages/Transfer';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, signedOutByUser } = useAuth();
  const location = useLocation();
  if (!user) {
    return <Navigate to="/login" replace state={signedOutByUser ? undefined : { from: location.pathname + location.search }} />;
  }
  return <>{children}</>;
}

/** UI guard only. The API enforces ROLE_ADMIN itself, so hiding screens is a convenience, not security. */
function RequireAdmin({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return user?.role === 'ADMIN' ? <>{children}</> : <Navigate to="/" replace />;
}

/** Once signed in, go back to the page that asked for a login, or to the role's home page. */
function PublicOnly({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <>{children}</>;
  const from = (location.state as { from?: string } | null)?.from;
  return <Navigate to={from ?? (user.role === 'ADMIN' ? '/admin/accounts' : '/')} replace />;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<PublicOnly><LoginPage /></PublicOnly>} />
      <Route path="/register" element={<PublicOnly><RegisterPage /></PublicOnly>} />
      <Route element={<RequireAuth><AppShell /></RequireAuth>}>
        <Route index element={<DashboardPage />} />
        <Route path="accounts" element={<AccountsPage />} />
        <Route path="accounts/:accountNumber" element={<AccountDetailPage />} />
        <Route path="transfer" element={<TransferPage />} />
        <Route path="profile" element={<ProfilePage />} />
        <Route path="admin/accounts" element={<RequireAdmin><AdminAccountsPage /></RequireAdmin>} />
        <Route path="admin/audit" element={<RequireAdmin><AuditLogPage /></RequireAdmin>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
