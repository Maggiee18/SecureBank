import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { setAccessToken, setSessionExpiredHandler } from './api';
import { api } from './endpoints';
import { read, write } from './storage';
import type { Role } from './types';

/**
 * The JWT lives in sessionStorage: it survives a page refresh but not closing the tab.
 * A production bank would prefer an httpOnly cookie set by a backend-for-frontend so
 * page scripts can never read the token.
 */
const TOKEN_KEY = 'securebank.token';

export interface SessionUser {
  id: number;
  email: string;
  role: Role;
  expiresAt: number;
}

interface AuthState {
  user: SessionUser | null;
  notice: string | null;
  /** True after the person clicked Sign out, so we don't send the next login back to their old page. */
  signedOutByUser: boolean;
  login: (email: string, password: string) => Promise<SessionUser>;
  logout: (notice?: string) => void;
  clearNotice: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

function decode(token: string): SessionUser | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    const user = { id: Number(payload.sub), email: payload.email, role: payload.role, expiresAt: payload.exp * 1000 };
    return user.expiresAt > Date.now() ? user : null;
  } catch {
    return null;
  }
}

function restore(): { token: string; user: SessionUser } | null {
  const token = read('session', TOKEN_KEY);
  const user = token ? decode(token) : null;
  if (token && user) {
    setAccessToken(token);
    return { token, user };
  }
  return null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<SessionUser | null>(() => restore()?.user ?? null);
  const [notice, setNotice] = useState<string | null>(null);
  const [signedOutByUser, setSignedOutByUser] = useState(false);

  const logout = useCallback((message?: string) => {
    setAccessToken(null);
    write('session', TOKEN_KEY, null);
    setUser(null);
    queryClient.clear();
    setSignedOutByUser(!message);
    if (message) setNotice(message);
  }, [queryClient]);

  const login = useCallback(async (email: string, password: string) => {
    const response = await api.login(email, password);
    const session = decode(response.accessToken);
    if (!session) throw new Error('Received an invalid session token');
    setAccessToken(response.accessToken);
    write('session', TOKEN_KEY, response.accessToken);
    setNotice(null);
    setSignedOutByUser(false);
    setUser(session);
    return session;
  }, []);

  useEffect(() => {
    setSessionExpiredHandler(() => logout('Your session has ended. Please sign in again.'));
    return () => setSessionExpiredHandler(null);
  }, [logout]);

  // Sign out exactly when the token expires instead of waiting for a failed request.
  useEffect(() => {
    if (!user) return;
    const timer = window.setTimeout(() => logout('Your session expired after 30 minutes. Please sign in again.'),
      Math.max(0, user.expiresAt - Date.now()));
    return () => window.clearTimeout(timer);
  }, [user, logout]);

  const value = useMemo<AuthState>(() => ({
    user, notice, signedOutByUser, login, logout, clearNotice: () => setNotice(null),
  }), [user, notice, signedOutByUser, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
