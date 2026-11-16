import { useState, type FormEvent } from 'react';
import { useSession } from '../session';
import { Alert, LockIcon, PasswordField, useAction, usePageTitle } from '../components/ui';

/** Shown after a page reload: the session is valid but the data key is no longer in memory. */
export function UnlockPage() {
  const { me, unlock, logout } = useSession();
  const [password, setPassword] = useState('');
  const { busy, error, run } = useAction();
  usePageTitle('Journal locked');

  const submit = (e: FormEvent) => {
    e.preventDefault();
    run(() => unlock(password));
  };

  return (
    <main className="narrow">
      <div className="card">
        <h1 className="row">
          <LockIcon size={24} /> Journal locked
        </h1>
        <p className="muted small">
          Signed in as <strong>{me?.username}</strong>. Your key is never stored on this device, so enter your password to unlock.
        </p>
        <Alert>{error}</Alert>
        <form onSubmit={submit}>
          <input type="text" autoComplete="username" value={me?.username ?? ''} readOnly hidden />
          <PasswordField id="unlock-password" label="Password" value={password} onChange={setPassword} autoComplete="current-password" />
          <div className="row">
            <button className="primary" disabled={busy}>
              {busy ? 'Unlocking…' : 'Unlock'}
            </button>
            <span className="spacer" />
            <button type="button" className="ghost" onClick={() => logout()}>
              Sign out
            </button>
          </div>
        </form>
      </div>
    </main>
  );
}
