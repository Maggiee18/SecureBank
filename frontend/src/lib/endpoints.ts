import { request } from './api';
import type {
  Account, AccountStatus, AccountType, AdminAccount, AuditLog, Balance, Customer, LoginResponse,
  MoneyOperationResult, Page, Transaction, TransferResult,
} from './types';

const data = async <T,>(promise: Promise<{ data: T }>) => (await promise).data;

const query = (params: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : '';
};

export const api = {
  login: (email: string, password: string) =>
    data(request<LoginResponse>('/api/v1/auth/login', { method: 'POST', body: { email, password } })),

  register: (body: { fullName: string; email: string; phone: string; password: string }) =>
    data(request<Customer>('/api/v1/auth/register', { method: 'POST', body })),

  me: () => data(request<Customer>('/api/v1/customers/me')),

  updateMe: (body: { fullName: string; phone: string }) =>
    data(request<Customer>('/api/v1/customers/me', { method: 'PUT', body })),

  accounts: () => data(request<Account[]>('/api/v1/accounts')),

  account: (number: string) => data(request<Account>(`/api/v1/accounts/${number}`)),

  balance: (number: string) => data(request<Balance>(`/api/v1/accounts/${number}/balance`)),

  openAccount: (accountType: AccountType) =>
    data(request<Account>('/api/v1/accounts', { method: 'POST', body: { accountType } })),

  deposit: (number: string, amount: string, description: string, idempotencyKey: string) =>
    request<MoneyOperationResult>(`/api/v1/accounts/${number}/deposit`,
      { method: 'POST', body: { amount, description: description || undefined }, idempotencyKey }),

  withdraw: (number: string, amount: string, description: string, idempotencyKey: string) =>
    request<MoneyOperationResult>(`/api/v1/accounts/${number}/withdraw`,
      { method: 'POST', body: { amount, description: description || undefined }, idempotencyKey }),

  transfer: (
    body: { sourceAccountNumber: string; destinationAccountNumber: string; amount: string; description?: string },
    idempotencyKey: string,
  ) => request<TransferResult>('/api/v1/transfers', { method: 'POST', body, idempotencyKey }),

  statement: (number: string, page: number, size: number, sort = 'createdAt,desc') =>
    data(request<Page<Transaction>>(`/api/v1/accounts/${number}/transactions${query({ page, size, sort })}`)),

  adminAccounts: (page: number, size: number, status?: AccountStatus) =>
    data(request<Page<AdminAccount>>(`/api/v1/admin/accounts${query({ page, size, status })}`)),

  changeAccountStatus: (number: string, status: AccountStatus, reason: string) =>
    data(request<Account>(`/api/v1/admin/accounts/${number}/status`, { method: 'PATCH', body: { status, reason } })),

  auditLogs: (page: number, size: number, customerId?: number) =>
    data(request<Page<AuditLog>>(`/api/v1/admin/audit-logs${query({ page, size, customerId })}`)),
};
