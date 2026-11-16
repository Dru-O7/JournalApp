import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useSession } from '../session';
import { Alert, LockIcon, PasswordField, useAction } from './ui';

/**
 * Shown when the session token expires or is revoked while the journal is open. The page underneath
 * stays mounted, so anything not yet saved is kept; after signing in again the user can save it.
 */
export function ReauthDialog() {
  const { me, reauthenticate, logout } = useSession();
  const ref = useRef<HTMLDialogElement>(null);
  const [password, setPassword] = useState('');
  const { busy, error, run } = useAction();

  useEffect(() => {
    const d = ref.current;
    if (d && !d.open) d.showModal();
  }, []);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    run(() => reauthenticate(password));
  };

  return (
    // Escape must not dismiss it: without a token nothing can be saved.
    <dialog ref={ref} className="modal card" aria-labelledby="reauth-title" onCancel={(e) => e.preventDefault()}>
      <form onSubmit={submit}>
        <h2 id="reauth-title" className="row">
          <LockIcon size={20} /> Your session ended
        </h2>
        <p className="muted small">
          For your security you were signed out. Enter the password for <strong>{me?.username}</strong> to continue. Anything you
          haven't saved is still on the page.
        </p>
        <Alert>{error}</Alert>
        <input type="text" autoComplete="username" value={me?.username ?? ''} readOnly hidden />
        <PasswordField id="reauth-password" label="Password" value={password} onChange={setPassword} autoComplete="current-password" />
        <div className="row">
          <button className="primary" disabled={busy}>
            {busy ? 'Signing in…' : 'Continue'}
          </button>
          <span className="spacer" />
          <button type="button" className="ghost" onClick={() => logout()}>
            Sign out and discard
          </button>
        </div>
      </form>
    </dialog>
  );
}
