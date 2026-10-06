import { useState, type FormEvent } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Eye, EyeOff, Info, Lock, Mail } from 'lucide-react';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { Button } from '@/components/ui/Button';
import { Field } from '@/components/ui/Field';
import { ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';

export function LoginPage() {
  const { login, notice, clearNotice } = useAuth();
  const location = useLocation();
  const registeredEmail = (location.state as { email?: string } | null)?.email ?? '';
  const [email, setEmail] = useState(registeredEmail);
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    clearNotice();
    setSubmitting(true);
    try {
      // PublicOnly redirects as soon as the session exists.
      await login(email, password);
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, 'UNKNOWN', String(e), undefined));
    } finally {
      setSubmitting(false);
    }
  }

  const locked = error?.code === 'TOO_MANY_LOGIN_ATTEMPTS';

  return (
    <AuthLayout title="Welcome back" subtitle="Sign in to manage your accounts and transfers.">
      {(notice || registeredEmail) && !error && (
        <div className="mb-6 flex gap-2.5 rounded-xl border border-brand/20 bg-brand/5 p-3.5 text-sm">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-brand" />
          <span>{notice ?? 'Your profile is ready. Sign in with the password you just created.'}</span>
        </div>
      )}
      {error && (
        <div role="alert" className="mb-6 rounded-xl border border-danger/25 bg-danger/5 p-3.5 text-sm">
          <p className="font-semibold text-danger">{locked ? 'Sign in temporarily locked' : 'Could not sign you in'}</p>
          <p className="mt-0.5 text-muted">
            {locked && error.retryAfterSeconds
              ? `Too many failed attempts. Try again in about ${Math.ceil(error.retryAfterSeconds / 60)} minutes.`
              : error.message}
          </p>
        </div>
      )}
      <form onSubmit={onSubmit} className="space-y-5" noValidate>
        <Field
          label="Email"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          leading={<Mail className="h-4 w-4" />}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <Field
          label="Password"
          type={showPassword ? 'text' : 'password'}
          autoComplete="current-password"
          placeholder="Your password"
          leading={<Lock className="h-4 w-4" />}
          trailing={
            <button type="button" onClick={() => setShowPassword((s) => !s)} className="rounded-lg p-1.5 text-muted hover:text-ink"
              aria-label={showPassword ? 'Hide password' : 'Show password'}>
              {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          }
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        <Button type="submit" size="lg" className="w-full" loading={submitting} disabled={!email || !password}>
          Sign in
        </Button>
      </form>
      <p className="mt-8 text-center text-sm text-muted">
        New to SecureBank?{' '}
        <Link to="/register" className="font-semibold text-brand hover:underline">Create your profile</Link>
      </p>
    </AuthLayout>
  );
}
