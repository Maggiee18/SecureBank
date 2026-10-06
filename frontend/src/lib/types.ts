// Shapes of the SecureBank API responses (see the backend dto package).

export type Role = 'CUSTOMER' | 'ADMIN';
export type AccountType = 'SAVINGS' | 'CURRENT';
export type AccountStatus = 'ACTIVE' | 'BLOCKED' | 'CLOSED';

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  customerId: number;
  role: Role;
}

export interface Customer {
  id: number;
  fullName: string;
  email: string;
  phone: string;
  role: Role;
  createdAt: string;
}

export interface Account {
  accountNumber: string;
  accountType: AccountType;
  status: AccountStatus;
  balance: number;
  currency: string;
  createdAt: string;
}

export interface Balance {
  accountNumber: string;
  balance: number;
  currency: string;
  asOf: string;
}

export interface MoneyOperationResult {
  transactionReference: string;
  type: 'DEPOSIT' | 'WITHDRAWAL';
  status: string;
  accountNumber: string;
  amount: number;
  balanceAfter: number;
  description?: string;
  createdAt: string;
}

export interface TransferResult {
  transactionReference: string;
  status: string;
  sourceAccountNumber: string;
  destinationAccountNumber: string;
  amount: number;
  sourceBalanceAfter: number;
  description?: string;
  createdAt: string;
}

export interface Transaction {
  transactionReference: string;
  type: 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER';
  direction: 'DEBIT' | 'CREDIT';
  status: 'SUCCESS' | 'FAILED';
  amount: number;
  counterpartyAccount?: string;
  description?: string;
  failureReason?: string;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  sort?: string;
}

export interface AdminAccount {
  accountNumber: string;
  accountType: AccountType;
  status: AccountStatus;
  balance: number;
  currency: string;
  ownerId: number;
  ownerName: string;
  ownerEmail: string;
  createdAt: string;
}

export interface AuditLog {
  id: number;
  customerId?: number;
  action: string;
  resource: string;
  resourceId?: string;
  status: 'SUCCESS' | 'FAILURE';
  description?: string;
  ipAddress?: string;
  requestId?: string;
  createdAt: string;
}

export interface FieldError {
  field: string;
  message: string;
}
