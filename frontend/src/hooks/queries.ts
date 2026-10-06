import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/endpoints';
import type { AccountStatus } from '@/lib/types';

export const keys = {
  me: ['me'] as const,
  accounts: ['accounts'] as const,
  account: (number: string) => ['account', number] as const,
  statement: (number: string, page: number, size: number) => ['statement', number, page, size] as const,
  adminAccounts: (page: number, status?: AccountStatus) => ['admin', 'accounts', page, status ?? 'ALL'] as const,
  auditLogs: (page: number, customerId?: number) => ['admin', 'audit', page, customerId ?? 'ALL'] as const,
};

export const useMe = () => useQuery({ queryKey: keys.me, queryFn: api.me, staleTime: 60_000 });

export const useAccounts = () => useQuery({ queryKey: keys.accounts, queryFn: api.accounts });

export const useAccount = (number: string) =>
  useQuery({ queryKey: keys.account(number), queryFn: () => api.account(number) });

export const useStatement = (number: string, page: number, size = 10) =>
  useQuery({
    queryKey: keys.statement(number, page, size),
    queryFn: () => api.statement(number, page, size),
    placeholderData: keepPreviousData,
  });

export const useAdminAccounts = (page: number, status?: AccountStatus) =>
  useQuery({
    queryKey: keys.adminAccounts(page, status),
    queryFn: () => api.adminAccounts(page, 15, status),
    placeholderData: keepPreviousData,
  });

export const useAuditLogs = (page: number, customerId?: number) =>
  useQuery({
    queryKey: keys.auditLogs(page, customerId),
    queryFn: () => api.auditLogs(page, 20, customerId),
    placeholderData: keepPreviousData,
  });

/** After money moves, balances and statements everywhere are stale. */
export function useRefreshMoney() {
  const client = useQueryClient();
  return () => Promise.all([
    client.invalidateQueries({ queryKey: keys.accounts }),
    client.invalidateQueries({ queryKey: ['account'] }),
    client.invalidateQueries({ queryKey: ['statement'] }),
    client.invalidateQueries({ queryKey: ['recent'] }),
  ]);
}
