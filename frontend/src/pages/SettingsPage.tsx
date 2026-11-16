import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api } from '../api';
import { deriveKeys, passwordProblem, unwrapDataKey } from '../crypto';
import { useSession } from '../session';
import { Alert, PasswordField, TextField, useAction, useFieldError } from '../components/ui';

export function SettingsPage() {
  return (
    <>
      <h1>Settings</h1>
      <div className="grid-2" style={{ marginTop: '1rem' }}>
        <ProfileCard />
        <CredentialsCard />
        <SessionsCard />
        <DeleteCard />
      </div>
    </>
  );
}

function ProfileCard() {
  const { me, setMe } = useSession();
  const [email, setEmail] = useState(me?.email ?? '');
  const [city, setCity] = useState(me?.city ?? '');
  const [weeklyReport, setWeeklyReport] = useState(me?.weeklyReport ?? false);
  const [ok, setOk] = useState<string | null>(null);
  const { busy, error, run } = useAction();
  const { fail, errorFor, clear } = useFieldError();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setOk(null);
    clear();
    if (weeklyReport && !email.trim()) return fail('p-email', 'Add an email address for the weekly mood email, or turn it off below.');
    const turningOff = me?.weeklyReport && !weeklyReport;
    run(async () => {
      setMe(await api.updateProfile({ email, city, weeklyReport }));
      setOk(turningOff ? 'Saved. Moods previously shared for the weekly email were deleted.' : 'Saved.');
    });
  };

  return (
    <form className="card" onSubmit={submit} onChange={clear}>
      <h2>Profile</h2>
      <p className="muted small">Stored encrypted. Used only to send your weekly email and show local weather.</p>
      <Alert>{error}</Alert>
      <Alert kind="ok">{ok}</Alert>
      <TextField id="p-email" type="email" label="Email" value={email} onChange={setEmail} autoComplete="email" error={errorFor('p-email')} />
      <TextField id="p-city" label="City" value={city} onChange={setCity} maxLength={100} />
      <div className="field">
        <label className="check">
          <input type="checkbox" checked={weeklyReport} onChange={(e) => setWeeklyReport(e.target.checked)} />
          <span>
            Weekly mood email
            <span className="hint" style={{ display: 'block' }}>
              Shares only the mood you pick per entry, never your writing. Turning this off deletes those moods from the server.
            </span>
          </span>
        </label>
      </div>
      <button className="primary" disabled={busy}>
        {busy ? 'Saving…' : 'Save profile'}
      </button>
    </form>
  );
}

function CredentialsCard() {
  const { me, changeCredentials } = useSession();
  const [current, setCurrent] = useState('');
  const [username, setUsername] = useState(me?.username ?? '');
  const [next, setNext] = useState('');
  const [confirmNext, setConfirmNext] = useState('');
  const [ok, setOk] = useState(false);
  const { busy, error, run } = useAction();
  const { fail, errorFor, clear } = useFieldError();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setOk(false);
    clear();
    const problem = passwordProblem(next, username.trim());
    if (problem) return fail('c-new', problem);
    if (next !== confirmNext) return fail('c-confirm', 'New passwords do not match.');
    run(async () => {
      await changeCredentials(current, username.trim(), next);
      setCurrent('');
      setNext('');
      setConfirmNext('');
      setOk(true);
    });
  };

  return (
    <form className="card" onSubmit={submit} onChange={clear}>
      <h2>Username &amp; password</h2>
      <p className="muted small">
        Your journal key is re-encrypted with the new password on this device. Other devices are signed out.
      </p>
      <Alert>{error}</Alert>
      {ok && <Alert kind="ok">Updated. You're still signed in here.</Alert>}
      <PasswordField id="c-current" label="Current password" value={current} onChange={setCurrent} autoComplete="current-password" />
      <div className="field">
        <label htmlFor="c-username">Username</label>
        <input
          id="c-username"
          type="text"
          value={username}
          autoComplete="username"
          pattern="[A-Za-z0-9_.\-]{3,32}"
          onChange={(e) => setUsername(e.target.value)}
          required
        />
      </div>
      <PasswordField id="c-new" label="New password" value={next} onChange={setNext} autoComplete="new-password" hint="Re-enter your current password here to change only the username." error={errorFor('c-new')} />
      <PasswordField id="c-confirm" label="Confirm new password" value={confirmNext} onChange={setConfirmNext} autoComplete="new-password" error={errorFor('c-confirm')} />
      <button className="primary" disabled={busy}>
        {busy ? 'Re-encrypting…' : 'Update'}
      </button>
    </form>
  );
}

function SessionsCard() {
  const { logout } = useSession();
  const { busy, error, run } = useAction();
  return (
    <div className="card">
      <h2>Sessions</h2>
      <p className="muted small">Sign out on every device, including this one. Useful if you used a shared computer.</p>
      <Alert>{error}</Alert>
      <button
        disabled={busy}
        onClick={() =>
          run(async () => {
            await api.logoutEverywhere();
            logout('Signed out on all devices.');
          })
        }
      >
        Sign out everywhere
      </button>
    </div>
  );
}

function DeleteCard() {
  const { me, logout } = useSession();
  const [password, setPassword] = useState('');
  // Step 2 holds the auth key derived (and checked) in step 1, so the password is not re-typed.
  const [authKey, setAuthKey] = useState<string | null>(null);
  const [typed, setTyped] = useState('');
  const { busy, error, setError, run } = useAction();
  const { fail, errorFor, clear } = useFieldError();
  const confirmRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (authKey) confirmRef.current?.focus();
  }, [authKey]);

  const checkPassword = (e: FormEvent) => {
    e.preventDefault();
    clear();
    if (!me) return;
    run(async () => {
      const keys = await deriveKeys(me.username, password);
      try {
        // Same check as unlocking: only the right password unwraps this account's key.
        await unwrapDataKey(me, keys.kek);
      } catch {
        return fail('d-password', 'That password is not correct.');
      }
      setAuthKey(keys.authKey);
    });
  };

  const cancel = () => {
    setAuthKey(null);
    setTyped('');
    setPassword('');
    setError(null);
  };

  const remove = (e: FormEvent) => {
    e.preventDefault();
    if (!me || !authKey || typed !== me.username) return;
    run(async () => {
      await api.deleteAccount(authKey);
      logout('Your account and all entries were deleted.');
    });
  };

  if (authKey && me)
    return (
      <form className="card danger-zone" onSubmit={remove}>
        <h2>Delete account</h2>
        <p className="small">
          This permanently deletes <strong>{me.username}</strong> and every journal entry from the server. It cannot be undone.
        </p>
        <Alert>{error}</Alert>
        <div className="field">
          <label htmlFor="d-confirm">
            Type <strong>{me.username}</strong> to confirm
          </label>
          <input ref={confirmRef} id="d-confirm" type="text" autoComplete="off" spellCheck={false} value={typed} onChange={(e) => setTyped(e.target.value)} />
        </div>
        <div className="row">
          <button className="danger" disabled={busy || typed !== me.username}>
            {busy ? 'Deleting…' : `Delete ${me.username} forever`}
          </button>
          <button type="button" className="ghost" onClick={cancel}>
            Cancel
          </button>
        </div>
      </form>
    );

  return (
    <form className="card" onSubmit={checkPassword} onChange={clear}>
      <h2>Delete account</h2>
      <p className="muted small">Permanently deletes your account and all of your entries from the server.</p>
      <Alert>{error}</Alert>
      <PasswordField id="d-password" label="Password" value={password} onChange={setPassword} autoComplete="current-password" error={errorFor('d-password')} />
      <button className="danger" disabled={busy}>
        {busy ? 'Checking…' : 'Continue to delete'}
      </button>
    </form>
  );
}
