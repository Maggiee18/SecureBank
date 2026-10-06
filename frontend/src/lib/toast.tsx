import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { CircleAlert, CircleCheck, X } from 'lucide-react';
import { ApiError } from './api';
import { CopyButton } from '@/components/ui/CopyButton';

interface Toast {
  id: number;
  tone: 'success' | 'error';
  title: string;
  description?: string;
  requestId?: string;
}

interface ToastApi {
  success: (title: string, description?: string) => void;
  error: (error: unknown, title?: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);
let nextId = 1;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const dismiss = useCallback((id: number) => setToasts((all) => all.filter((t) => t.id !== id)), []);

  const push = useCallback((toast: Omit<Toast, 'id'>) => {
    const id = nextId++;
    setToasts((all) => [...all.slice(-3), { ...toast, id }]);
    window.setTimeout(() => dismiss(id), toast.tone === 'error' ? 9000 : 4500);
  }, [dismiss]);

  const api: ToastApi = {
    success: (title, description) => push({ tone: 'success', title, description }),
    error: (error, title) => {
      if (error instanceof ApiError) {
        push({ tone: 'error', title: title ?? 'Request failed', description: error.message, requestId: error.requestId });
      } else {
        push({ tone: 'error', title: title ?? 'Something went wrong', description: String((error as Error)?.message ?? error) });
      }
    },
  };

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-[min(92vw,380px)] flex-col gap-2" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className="card pointer-events-auto flex animate-fade-up gap-3 p-4 shadow-lift">
            {t.tone === 'success'
              ? <CircleCheck className="mt-0.5 h-5 w-5 shrink-0 text-success" />
              : <CircleAlert className="mt-0.5 h-5 w-5 shrink-0 text-danger" />}
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold">{t.title}</p>
              {t.description && <p className="mt-0.5 text-sm text-muted">{t.description}</p>}
              {t.requestId && (
                <div className="mt-2 flex items-center gap-1.5 text-xs text-muted">
                  <span>Support ref</span>
                  <code className="rounded bg-surface-2 px-1.5 py-0.5 font-mono text-[11px] text-ink">{t.requestId}</code>
                  <CopyButton value={t.requestId} label="Copy support reference" />
                </div>
              )}
            </div>
            <button onClick={() => dismiss(t.id)} className="text-muted hover:text-ink" aria-label="Dismiss">
              <X className="h-4 w-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast must be used inside ToastProvider');
  return context;
}
