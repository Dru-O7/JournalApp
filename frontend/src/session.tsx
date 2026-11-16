import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, setToken, setUnauthorizedHandler, type AuthResponse, type Me } from './api';
import { deriveKeys, generateDataKey, unwrapDataKey, wrapDataKey } from './crypto';

/**
 * Session state.
 *  - The API token lives in sessionStorage (this tab only) so a reload does not log you out.
 *  - The decrypted data key lives ONLY in memory as a non-extractable CryptoKey. After a reload the
 *    journal is "locked" and the password is needed again to unlock it. It is never written to storage.
 */
type Status = 'loading' | 'signed-out' | 'locked' | 'ready';

interface SessionValue {
  status: Status;
  me: Me | null;
  dataKey: CryptoKey | null;
  notice: string | null;
  /**
   * The token expired or was revoked while the journal was open. The page stays mounted (so unsaved
   * writing survives) and a password prompt is shown; see {@link SessionValue.reauthenticate}.
   */
  expired: boolean;
  reauthenticate(password: string): Promise<void>;
  login(username: string, password: string): Promise<void>;
  signup(input: { username: string; password: string; email: string; city: string; weeklyReport: boolean }): Promise<void>;
  unlock(password: string): Promise<void>;
  changeCredentials(currentPassword: string, newUsername: string, newPassword: string): Promise<void>;
  setMe(me: Me): void;
  logout(notice?: string): void;
}

const TOKEN_KEY = 'journal.token';
const SessionContext = createContext<SessionValue | null>(null);

function readStoredToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function storeToken(t: string | null) {
  setToken(t);
  try {
    if (t) sessionStorage.setItem(TOKEN_KEY, t);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage unavailable: session just won't survive reload */
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [me, setMe] = useState<Me | null>(null);
  const [dataKey, setDataKey] = useState<CryptoKey | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);
  const statusRef = useRef<Status>(status);
  statusRef.current = status;

  const logout = useCallback((message?: string) => {
    storeToken(null);
    setMe(null);
    setDataKey(null);
    setExpired(false);
    setNotice(message ?? null);
    setStatus('signed-out');
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      if (statusRef.current === 'ready') {
        // Keep the journal (and any unsaved writing) on screen and ask for the password again.
        // The stale token is dropped so the sign-in request is not rejected because of it.
        storeToken(null);
        setExpired(true);
      } else {
        logout('Your session has ended. Please sign in again.');
      }
    });
    const t = readStoredToken();
    if (!t) {
      setStatus('signed-out');
      return;
    }
    setToken(t);
    api
      .me()
      .then((m) => {
        setMe(m);
        setStatus('locked');
      })
      .catch(() => logout());
  }, [logout]);

  const accept = useCallback(async (res: AuthResponse, kek: CryptoKey) => {
    const key = await unwrapDataKey(res.user, kek);
    storeToken(res.token);
    setMe(res.user);
    setDataKey(key);
    setNotice(null);
    setStatus('ready');
  }, []);

  const login = useCallback(
    async (username: string, password: string) => {
      const { authKey, kek } = await deriveKeys(username, password);
      const res = await api.login(username, authKey);
      await accept(res, kek);
    },
    [accept],
  );

  const signup = useCallback<SessionValue['signup']>(
    async ({ username, password, email, city, weeklyReport }) => {
      const { authKey, kek } = await deriveKeys(username, password);
      const wrapped = await wrapDataKey(await generateDataKey(), kek);
      const res = await api.signup({
        username,
        authKey,
        ...wrapped,
        email: email.trim() || undefined,
        city: city.trim() || undefined,
        weeklyReport,
      });
      await accept(res, kek);
    },
    [accept],
  );

  const reauthenticate = useCallback(
    async (password: string) => {
      if (!me) throw new Error('Not signed in');
      const { authKey, kek } = await deriveKeys(me.username, password);
      const res = await api.login(me.username, authKey);
      await accept(res, kek);
      setExpired(false);
    },
    [me, accept],
  );

  const unlock = useCallback(
    async (password: string) => {
      if (!me) throw new Error('Not signed in');
      const { kek } = await deriveKeys(me.username, password);
      try {
        setDataKey(await unwrapDataKey(me, kek));
      } catch {
        throw new Error('That password does not unlock this journal.');
      }
      setStatus('ready');
    },
    [me],
  );

  const changeCredentials = useCallback(
    async (currentPassword: string, newUsername: string, newPassword: string) => {
      if (!me) throw new Error('Not signed in');
      const current = await deriveKeys(me.username, currentPassword);
      let exportable: CryptoKey;
      try {
        exportable = await unwrapDataKey(me, current.kek, true);
      } catch {
        throw new Error('Current password is incorrect.');
      }
      const next = await deriveKeys(newUsername, newPassword);
      const wrapped = await wrapDataKey(exportable, next.kek);
      const res = await api.changeCredentials({
        currentAuthKey: current.authKey,
        newUsername,
        newAuthKey: next.authKey,
        newWrappedKey: wrapped.wrappedKey,
        newWrappedKeyIv: wrapped.wrappedKeyIv,
      });
      await accept(res, next.kek);
    },
    [me, accept],
  );

  const value = useMemo<SessionValue>(
    () => ({ status, me, dataKey, notice, expired, reauthenticate, login, signup, unlock, changeCredentials, setMe, logout }),
    [status, me, dataKey, notice, expired, reauthenticate, login, signup, unlock, changeCredentials, logout],
  );
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error('useSession must be used inside SessionProvider');
  return ctx;
}
