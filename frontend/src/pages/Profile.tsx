import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Mail, Phone, ShieldCheck, User } from 'lucide-react';
import { PageHeader } from '@/components/layout/AppShell';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Feedback';
import { Field } from '@/components/ui/Field';
import { keys, useMe } from '@/hooks/queries';
import { ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { api } from '@/lib/endpoints';
import { formatDate, formatDateTime } from '@/lib/format';
import { useToast } from '@/lib/toast';

export function ProfilePage() {
  const { data: me, isLoading } = useMe();
  const { user } = useAuth();
  const client = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({ fullName: '', phone: '' });
  const [error, setError] = useState<ApiError | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (me) setForm({ fullName: me.fullName, phone: me.phone });
  }, [me]);

  const dirty = me && (form.fullName !== me.fullName || form.phone !== me.phone);

  async function save(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const updated = await api.updateMe({ fullName: form.fullName.trim(), phone: form.phone });
      client.setQueryData(keys.me, updated);
      toast.success('Profile updated');
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors.length) setError(e);
      else toast.error(e, 'Could not update profile');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl animate-fade-up">
      <PageHeader title="Profile" subtitle="Your personal details and session." />
      <div className="grid gap-6 md:grid-cols-[1.3fr_1fr]">
        <form onSubmit={save} className="card space-y-5 p-6" noValidate>
          <h2 className="font-semibold">Personal details</h2>
          {isLoading || !me ? <Skeleton className="h-48" /> : (
            <>
              <Field label="Full name" leading={<User className="h-4 w-4" />} value={form.fullName}
                error={error?.fieldError('fullName')}
                onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))} />
              <Field label="Mobile number" inputMode="numeric" leading={<Phone className="h-4 w-4" />} value={form.phone}
                error={error?.fieldError('phone')}
                onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value.replace(/\D/g, '').slice(0, 10) }))} />
              <Field label="Email" leading={<Mail className="h-4 w-4" />} value={me.email} disabled
                hint="Email is your sign in identity and cannot be changed here." />
              <div className="flex justify-end">
                <Button type="submit" loading={saving} disabled={!dirty}>Save changes</Button>
              </div>
            </>
          )}
        </form>

        <aside className="card h-fit space-y-4 p-6 text-sm">
          <h2 className="flex items-center gap-2 font-semibold"><ShieldCheck className="h-4 w-4 text-success" /> Security</h2>
          <InfoRow label="Role"><Badge tone={me?.role === 'ADMIN' ? 'brand' : 'neutral'}>{me?.role ?? '…'}</Badge></InfoRow>
          <InfoRow label="Customer id"><span className="font-mono">{me?.id ?? '…'}</span></InfoRow>
          <InfoRow label="Member since">{me ? formatDate(me.createdAt) : '…'}</InfoRow>
          <InfoRow label="Session ends">{user ? formatDateTime(new Date(user.expiresAt).toISOString()) : '…'}</InfoRow>
          <p className="rounded-xl bg-surface-2 p-3 text-xs text-muted">
            Sessions are signed JWTs that expire automatically. Five wrong passwords lock sign in for 15 minutes.
          </p>
        </aside>
      </div>
    </div>
  );
}

function InfoRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-muted">{label}</span>
      <span className="font-medium">{children}</span>
    </div>
  );
}
