import { useState, type ReactNode } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import {
  ArrowLeftRight, Building2, LayoutDashboard, LogOut, Menu, Moon, ScrollText, ShieldCheck, Sun, User, Wallet, X,
  type LucideIcon,
} from 'lucide-react';
import { useAuth } from '@/lib/auth';
import { useTheme } from '@/lib/theme';
import { useMe } from '@/hooks/queries';
import { Logo } from '@/components/ui/Logo';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
}

const customerNav: NavItem[] = [
  { to: '/', label: 'Overview', icon: LayoutDashboard },
  { to: '/accounts', label: 'Accounts', icon: Wallet },
  { to: '/transfer', label: 'Transfer', icon: ArrowLeftRight },
  { to: '/profile', label: 'Profile', icon: User },
];

const adminNav: NavItem[] = [
  { to: '/admin/accounts', label: 'All accounts', icon: Building2 },
  { to: '/admin/audit', label: 'Audit log', icon: ScrollText },
];

function NavLinks({ items, onNavigate }: { items: NavItem[]; onNavigate?: () => void }) {
  return (
    <>
      {items.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={to === '/'}
          onClick={onNavigate}
          className={({ isActive }) =>
            `group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${
              isActive ? 'bg-brand text-brand-fg shadow-sm' : 'text-muted hover:bg-surface-2 hover:text-ink'
            }`}
        >
          <Icon className="h-[18px] w-[18px]" />
          {label}
        </NavLink>
      ))}
    </>
  );
}

function initials(name?: string, email?: string) {
  const source = name?.trim() || email || '?';
  const parts = source.split(/\s+/);
  return (parts.length > 1 ? parts[0][0] + parts[parts.length - 1][0] : source.slice(0, 2)).toUpperCase();
}

export function AppShell() {
  const { user, logout } = useAuth();
  const { theme, toggle } = useTheme();
  const { data: me } = useMe();
  const [mobileOpen, setMobileOpen] = useState(false);
  const isAdmin = user?.role === 'ADMIN';

  const sidebar = (onNavigate?: () => void) => (
    <nav className="flex flex-1 flex-col gap-1">
      <NavLinks items={customerNav} onNavigate={onNavigate} />
      {isAdmin && (
        <>
          <p className="mb-1 mt-6 flex items-center gap-1.5 px-3 text-[11px] font-semibold uppercase tracking-wider text-muted">
            <ShieldCheck className="h-3.5 w-3.5" /> Operations
          </p>
          <NavLinks items={adminNav} onNavigate={onNavigate} />
        </>
      )}
    </nav>
  );

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[260px_1fr]">
      {/* Desktop sidebar */}
      <aside className="sticky top-0 hidden h-screen flex-col border-r border-border bg-surface px-4 py-6 lg:flex">
        <div className="mb-8 px-2"><Logo /></div>
        {sidebar()}
        <div className="mt-6 rounded-xl border border-border bg-surface-2 p-3 text-xs text-muted">
          <p className="font-semibold text-ink">Demo environment</p>
          <p className="mt-0.5">Balances are test money. Never use real credentials.</p>
        </div>
      </aside>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-slate-950/50" onClick={() => setMobileOpen(false)} />
          <aside className="absolute inset-y-0 left-0 flex w-72 animate-fade-up flex-col bg-surface px-4 py-6">
            <div className="mb-8 flex items-center justify-between px-2">
              <Logo />
              <button onClick={() => setMobileOpen(false)} aria-label="Close menu" className="text-muted"><X className="h-5 w-5" /></button>
            </div>
            {sidebar(() => setMobileOpen(false))}
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-col">
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-border bg-bg/80 px-4 backdrop-blur sm:px-6 lg:px-10">
          <button className="rounded-lg p-2 text-muted hover:bg-surface-2 lg:hidden" onClick={() => setMobileOpen(true)} aria-label="Open menu">
            <Menu className="h-5 w-5" />
          </button>
          <div className="lg:hidden"><Logo compact /></div>
          <div className="flex-1" />
          <button
            onClick={toggle}
            className="rounded-xl p-2.5 text-muted transition hover:bg-surface-2 hover:text-ink"
            aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          >
            {theme === 'dark' ? <Sun className="h-[18px] w-[18px]" /> : <Moon className="h-[18px] w-[18px]" />}
          </button>
          <div className="flex items-center gap-3 rounded-xl py-1 pl-1 pr-1 sm:pr-3">
            <div className="grid h-9 w-9 place-items-center rounded-full bg-gradient-to-br from-blue-800 to-emerald-500 text-xs font-bold text-white">
              {initials(me?.fullName, user?.email)}
            </div>
            <div className="hidden leading-tight sm:block">
              <p className="text-sm font-semibold">{me?.fullName ?? '…'}</p>
              <p className="text-xs text-muted">{isAdmin ? 'Operations admin' : 'Personal banking'}</p>
            </div>
          </div>
          <button
            onClick={() => logout()}
            className="inline-flex items-center gap-2 rounded-xl px-3 py-2 text-sm font-medium text-muted transition hover:bg-surface-2 hover:text-ink"
          >
            <LogOut className="h-4 w-4" /> <span className="hidden sm:inline">Sign out</span>
          </button>
        </header>

        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6 lg:px-10">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-2xl font-bold tracking-tight sm:text-[28px]">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
