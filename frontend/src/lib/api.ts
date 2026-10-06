import type { FieldError } from './types';
import { requestId as newRequestId } from './ids';

const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? '';

/** An API failure in the backend's error format, plus network failures (status 0). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly requestId: string | undefined,
    readonly fieldErrors: FieldError[] = [],
    readonly retryAfterSeconds?: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  get isNetworkError() {
    return this.status === 0;
  }

  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((e) => e.field === field)?.message;
  }
}

export interface ApiResult<T> {
  data: T;
  /** True when the server answered from its idempotency store instead of executing again. */
  replayed: boolean;
  requestId?: string;
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH';
  body?: unknown;
  idempotencyKey?: string;
  signal?: AbortSignal;
}

let accessToken: string | null = null;
let onSessionExpired: (() => void) | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function setSessionExpiredHandler(handler: (() => void) | null) {
  onSessionExpired = handler;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<ApiResult<T>> {
  // We send our own request id so it can be shown even if the network drops before any response.
  const clientRequestId = newRequestId();
  const headers: Record<string, string> = { Accept: 'application/json', 'X-Request-ID': clientRequestId };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey;

  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Could not reach SecureBank. Check your connection and try again.',
      clientRequestId);
  }

  const serverRequestId = response.headers.get('X-Request-ID') ?? clientRequestId;
  const text = await response.text();
  let body: any = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = null;
    }
  }

  if (!response.ok) {
    if (response.status === 401 && accessToken && onSessionExpired) {
      onSessionExpired();
    }
    const retryAfter = response.headers.get('Retry-After');
    throw new ApiError(
      response.status,
      body?.error ?? `HTTP_${response.status}`,
      body?.message ?? (response.statusText || 'Request failed'),
      body?.requestId ?? serverRequestId,
      body?.fieldErrors ?? [],
      retryAfter ? Number(retryAfter) : undefined,
    );
  }

  return {
    data: body as T,
    replayed: response.headers.get('Idempotent-Replayed') === 'true',
    requestId: serverRequestId,
  };
}
