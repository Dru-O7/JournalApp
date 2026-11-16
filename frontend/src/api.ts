import type { Sentiment } from './crypto';

export interface Me {
  id: string;
  username: string;
  roles: string[];
  email: string | null;
  city: string | null;
  weeklyReport: boolean;
  wrappedKey: string;
  wrappedKeyIv: string;
  kdfVersion: number;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: Me;
}

export interface EntryRecord {
  id: string;
  ciphertext: string;
  iv: string;
  encVersion: number;
  version: number;
  /** The day this entry belongs to (yyyy-MM-dd). One entry per day. */
  entryDate: string;
  /** When it becomes read-only: the end of `entryDate` in the writer's time zone. */
  locksAt: string;
  editable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Activity {
  /** Days written on in the last year (yyyy-MM-dd), oldest first. */
  days: string[];
}

export interface Greeting {
  message: string;
  feelsLike: number | null;
  weatherDescription: string | null;
  quote: string | null;
  quoteAuthor: string | null;
}

export interface AdminUser {
  id: string;
  username: string;
  roles: string[];
  createdAt: string;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

let token: string | null = null;
let onUnauthorized: () => void = () => {};

export function setToken(t: string | null) {
  token = t;
}

export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = `Bearer ${token}`;

  let res: Response;
  try {
    res = await fetch(`/api/v1${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'omit',
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection.');
  }

  if (res.status === 401 && token) {
    onUnauthorized();
  }
  if (!res.ok) {
    let detail = res.statusText || 'Request failed';
    let errors: Record<string, string> = {};
    try {
      const pd = await res.json();
      detail = pd.detail ?? detail;
      errors = pd.errors ?? {};
    } catch {
      /* not JSON */
    }
    if (res.status === 429) detail = 'Too many attempts. Please wait a few minutes and try again.';
    throw new ApiError(res.status, detail, errors);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const api = {
  signup: (body: {
    username: string;
    authKey: string;
    wrappedKey: string;
    wrappedKeyIv: string;
    email?: string;
    city?: string;
    weeklyReport: boolean;
  }) => request<AuthResponse>('POST', '/auth/signup', body),
  login: (username: string, authKey: string) => request<AuthResponse>('POST', '/auth/login', { username, authKey }),

  me: () => request<Me>('GET', '/me'),
  updateProfile: (body: { email?: string; city?: string; weeklyReport?: boolean }) => request<Me>('PATCH', '/me', body),
  changeCredentials: (body: {
    currentAuthKey: string;
    newUsername: string;
    newAuthKey: string;
    newWrappedKey: string;
    newWrappedKeyIv: string;
  }) => request<AuthResponse>('PUT', '/me/credentials', body),
  logoutEverywhere: () => request<void>('POST', '/me/logout-everywhere'),
  deleteAccount: (authKey: string) => request<void>('DELETE', '/me', { authKey }),
  greeting: () => request<Greeting>('GET', '/me/greeting'),

  entryActivity: () => request<Activity>('GET', '/entries/activity'),
  listEntries: (page: number, size = 20) => request<Page<EntryRecord>>('GET', `/entries?page=${page}&size=${size}`),
  /** Creates today's entry; `timeZone` decides which day "today" is. 409 if today's entry exists. */
  createEntry: (body: { id: string; timeZone: string; ciphertext: string; iv: string; reportSentiment: Sentiment | null }) =>
    request<EntryRecord>('POST', '/entries', body),
  updateEntry: (id: string, body: { ciphertext: string; iv: string; reportSentiment: Sentiment | null; version: number }) =>
    request<EntryRecord>('PUT', `/entries/${encodeURIComponent(id)}`, body),

  adminUsers: (page: number) => request<Page<AdminUser>>('GET', `/admin/users?page=${page}&size=50`),
  adminCreate: (body: { username: string; authKey: string; wrappedKey: string; wrappedKeyIv: string }) =>
    request<AdminUser>('POST', '/admin/users', body),
};
