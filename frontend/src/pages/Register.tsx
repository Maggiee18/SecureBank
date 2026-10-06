import { useMemo, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Check, Lock, Mail, Phone, User } from 'lucide-react';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { Button } from '@/components/ui/Button';
import { Field } from '@/components/ui/Field';
import { ApiError } from '@/lib/api';
import { api } from '@/lib/endpoints';

// Mirrors the backend's RegisterRequest rules so people see problems before submitting.
const passwordRules = [
  { label: '8 to 72 characters', test: (p: string) => p.length >= 8 && p.length <= 72 },
  { label: 'Upper and lower case letters', test: (p: string) => /[a-z]/.test(p) && /[A-Z]/.test(p) },
  { label: 'At least one number', test: (p: string) => /\d/.test(p) },
  { label: 'At least one symbol', test: (p: string) => /[^A-Za-z0-9]/.test(p) },
];

export function RegisterPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '' });
  const [error, setError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const passed = useMemo(() => passwordRules.map((rule) => rule.test(form.password)), [form.password]);
  const strength = passed.filter(Boolean).length;
  const update = (field: keyof typeof form) => (e: ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [field]: field === 'phone' ? e.target.value.replace(/\D/g, '').slice(0, 10) : e.target.value }));

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.register(form);
      navigate('/login', { state: { email: form.email.trim().toLowerCase() } });
    } catch (e) {
      setError(e instanceof ApiError ? e : null);
    } finally {
      setSubmitting(false);
    }
  }

  const fieldError = (name: string) => error?.fieldError(name);
  const generalError = error && error.fieldErrors.length === 0 ? error.message : null;

  return (
    <AuthLayout title="Create your profile" subtitle="It takes a minute. You can open accounts right after.">
      {generalError && (
        <div role="alert" className="mb-6 rounded-xl border border-danger/25 bg-danger/5 p-3.5 text-sm text-danger">
          {generalError}
        </div>
      )}
      <form onSubmit={onSubmit} className="space-y-5" noValidate>
        <Field label="Full name" autoComplete="name" placeholder="Maggie Rao" leading={<User className="h-4 w-4" />}
          value={form.fullName} onChange={update('fullName')} error={fieldError('fullName')} />
        <Field label="Email" type="email" autoComplete="email" placeholder="you@example.com" leading={<Mail className="h-4 w-4" />}
          value={form.email} onChange={update('email')} error={fieldError('email')} />
        <Field label="Mobile number" inputMode="numeric" autoComplete="tel-national" placeholder="9876543210"
          leading={<Phone className="h-4 w-4" />} value={form.phone} onChange={update('phone')} error={fieldError('phone')}
          hint="10 digit Indian mobile number" />
        <div>
          <Field label="Password" type="password" autoComplete="new-password" placeholder="Create a strong password"
            leading={<Lock className="h-4 w-4" />} value={form.password} onChange={update('password')}
            error={fieldError('password')} />
          <div className="mt-2.5 flex gap-1.5" aria-hidden="true">
            {[0, 1, 2, 3].map((i) => (
              <span key={i} className={`h-1 flex-1 rounded-full transition ${
                i < strength ? (strength === 4 ? 'bg-success' : strength >= 2 ? 'bg-warning' : 'bg-danger') : 'bg-surface-2'
              }`} />
            ))}
          </div>
          <ul className="mt-3 grid grid-cols-2 gap-x-3 gap-y-1.5">
            {passwordRules.map((rule, i) => (
              <li key={rule.label} className={`flex items-center gap-1.5 text-xs ${passed[i] ? 'text-success' : 'text-muted'}`}>
                <Check className={`h-3.5 w-3.5 ${passed[i] ? 'opacity-100' : 'opacity-30'}`} /> {rule.label}
              </li>
            ))}
          </ul>
        </div>
        <Button type="submit" size="lg" className="w-full" loading={submitting}
          disabled={!form.fullName || !form.email || form.phone.length !== 10 || strength < 4}>
          Create profile
        </Button>
      </form>
      <p className="mt-8 text-center text-sm text-muted">
        Already banking with us? <Link to="/login" className="font-semibold text-brand hover:underline">Sign in</Link>
      </p>
    </AuthLayout>
  );
}
