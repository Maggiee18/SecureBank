// Browser storage can throw (private mode, blocked site data), so every access is guarded.

export function read(storage: 'local' | 'session', key: string): string | null {
  try {
    return (storage === 'local' ? window.localStorage : window.sessionStorage).getItem(key);
  } catch {
    return null;
  }
}

export function write(storage: 'local' | 'session', key: string, value: string | null) {
  try {
    const target = storage === 'local' ? window.localStorage : window.sessionStorage;
    if (value === null) target.removeItem(key);
    else target.setItem(key, value);
  } catch {
    // Not persisted; the app still works for this tab.
  }
}
