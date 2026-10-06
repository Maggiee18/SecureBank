const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });
const compactInr = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  notation: 'compact',
  maximumFractionDigits: 1,
});

export const money = (value: number | string) => inr.format(Number(value));
export const compactMoney = (value: number) => compactInr.format(value);

export function maskAccount(accountNumber: string): string {
  return accountNumber.length <= 4 ? accountNumber : `•••• ${accountNumber.slice(-4)}`;
}

/** 502133557148 -> 5021 3355 7148, easier to read and compare. */
export function groupAccount(accountNumber: string): string {
  return accountNumber.replace(/(\d{4})(?=\d)/g, '$1 ');
}

const dateTime = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });

export const formatDateTime = (iso: string) => dateTime.format(new Date(iso));
export const formatDate = (iso: string) => dateOnly.format(new Date(iso));

export function relativeTime(iso: string): string {
  const seconds = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 45) return 'just now';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return formatDate(iso);
}

export const titleCase = (value: string) =>
  value.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
