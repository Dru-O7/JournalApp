import { useState, type FormEvent } from 'react';
import { api } from '../api';
import { deriveKeys, passwordProblem } from '../crypto';
import { useSession } from '../session';
import { Alert, PasswordField, useAction } from '../components/ui';

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
  const { busy, error, setError, run } = useAction();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setOk(null);
    if (weeklyReport && !email.trim()) return setError('The weekly mood email needs an email address.');
    const turningOff = me?.weeklyReport && !weeklyReport;
    run(async () => {
      setMe(await api.updateProfile({ email, city, weeklyReport }));
      setOk(turningOff ? 'Saved. Moods previously shared for the weekly email were deleted.' : 'Saved.');
    });
  };

  return (
    <form className="card" onSubmit={submit}>
      <h2>Profile</h2>
      <p className="muted small">Stored encrypted. Used only to send your weekly email and show local weather.</p>
      <Alert>{error}</Alert>
      <Alert kind="ok">{ok}</Alert>
      <div className="field">
        <label htmlFor="p-email">Email</label>
        <input id="p-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      <div className="field">
        <label htmlFor="p-city">City</label>
        <input id="p-city" type="text" value={city} maxLength={100} onChange={(e) => setCity(e.target.value)} />
      </div>
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
  const { busy, error, setError, run } = useAction();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setOk(false);
    const problem = passwordProblem(next, username.trim());
    if (problem) return setError(problem);
    if (next !== confirmNext) return setError('New passwords do not match.');
    run(async () => {
      await changeCredentials(current, username.trim(), next);
      setCurrent('');
      setNext('');
      setConfirmNext('');
      setOk(true);
    });
  };

  return (
    <form className="card" onSubmit={submit}>
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
      <PasswordField id="c-new" label="New password" value={next} onChange={setNext} autoComplete="new-password" hint="Re-enter your current password here to change only the username." />
      <PasswordField id="c-confirm" label="Confirm new password" value={confirmNext} onChange={setConfirmNext} autoComplete="new-password" />
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
  const { busy, error, run } = useAction();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!me || !confirm('Delete your account and every entry? This cannot be undone.')) return;
    run(async () => {
      const { authKey } = await deriveKeys(me.username, password);
      await api.deleteAccount(authKey);
      logout('Your account and all entries were deleted.');
    });
  };

  return (
    <form className="card" onSubmit={submit}>
      <h2>Delete account</h2>
      <p className="muted small">Permanently deletes your account and all of your entries from the server.</p>
      <Alert>{error}</Alert>
      <PasswordField id="d-password" label="Password" value={password} onChange={setPassword} autoComplete="current-password" />
      <button className="danger" disabled={busy}>
        {busy ? 'Deleting…' : 'Delete everything'}
      </button>
    </form>
  );
}
