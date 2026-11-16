import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useSession } from '../session';
import { passwordProblem } from '../crypto';
import { Alert, LockIcon, PasswordField, TextField, useAction, useFieldError } from '../components/ui';

export function LoginPage() {
  const { login, notice } = useSession();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const { busy, error, run } = useAction();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    run(() => login(username.trim(), password));
  };

  return (
    <div className="narrow">
      <div className="card">
        <h1 className="row">
          <LockIcon size={24} /> Sign in
        </h1>
        <p className="muted small">Your entries are decrypted on this device after you sign in.</p>
        <Alert kind="info">{notice}</Alert>
        <Alert>{error}</Alert>
        <form onSubmit={submit}>
          <div className="field">
            <label htmlFor="username">Username</label>
            <input id="username" type="text" value={username} autoComplete="username" onChange={(e) => setUsername(e.target.value)} required />
          </div>
          <PasswordField id="password" label="Password" value={password} onChange={setPassword} autoComplete="current-password" />
          <button className="primary full" disabled={busy}>
            {busy ? 'Unlocking…' : 'Sign in'}
          </button>
        </form>
        <p className="small" style={{ marginTop: '1rem' }}>
          New here? <Link to="/signup">Create a journal</Link>
        </p>
      </div>
    </div>
  );
}

export function SignupPage() {
  const { signup } = useSession();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [email, setEmail] = useState('');
  const [city, setCity] = useState('');
  const [weeklyReport, setWeeklyReport] = useState(false);
  const [understood, setUnderstood] = useState(false);
  const { busy, error, run } = useAction();
  const { fail, errorFor, clear } = useFieldError();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    clear();
    const problem = passwordProblem(password, username.trim());
    if (problem) return fail('su-password', problem);
    if (password !== confirm) return fail('su-confirm', 'Passwords do not match.');
    if (weeklyReport && !email.trim()) return fail('su-email', 'Add an email address for the weekly mood email, or turn it off below.');
    run(() => signup({ username: username.trim(), password, email, city, weeklyReport }));
  };

  return (
    <div className="narrow">
      <div className="card">
        <h1>Create your journal</h1>
        <p className="muted small">
          Everything you write is encrypted in your browser with a key derived from your password. Nobody else, including
          the people who run this service, can read it.
        </p>
        <Alert>{error}</Alert>
        <form onSubmit={submit} onChange={clear}>
          <div className="field">
            <label htmlFor="su-username">Username</label>
            <input
              id="su-username"
              type="text"
              value={username}
              autoComplete="username"
              pattern="[A-Za-z0-9_.\-]{3,32}"
              title="3–32 characters: letters, digits, _ . -"
              aria-describedby="su-username-hint"
              onChange={(e) => setUsername(e.target.value)}
              required
            />
            <div id="su-username-hint" className="hint">
              3–32 characters: letters, digits, _ . -
            </div>
          </div>
          <PasswordField
            id="su-password"
            label="Password"
            value={password}
            onChange={setPassword}
            autoComplete="new-password"
            hint="At least 10 characters, not containing your username. A short sentence works well."
            error={errorFor('su-password')}
          />
          <PasswordField id="su-confirm" label="Confirm password" value={confirm} onChange={setConfirm} autoComplete="new-password" error={errorFor('su-confirm')} />
          <TextField id="su-email" type="email" label="Email (optional)" value={email} onChange={setEmail} autoComplete="email" error={errorFor('su-email')} />
          <TextField
            id="su-city"
            label="City for the weather greeting (optional)"
            value={city}
            onChange={setCity}
            maxLength={100}
            hint="Email and city are stored encrypted; the server uses them only for email and weather."
          />
          <div className="field">
            <label className="check">
              <input type="checkbox" checked={weeklyReport} onChange={(e) => setWeeklyReport(e.target.checked)} />
              <span>
                Send me a weekly mood summary by email.
                <span className="hint" style={{ display: 'block' }}>
                  To do this the server needs the mood you pick for each entry (never your writing). You can turn it off any time and those moods are deleted.
                </span>
              </span>
            </label>
          </div>
          <div className="field">
            <label className="check">
              <input type="checkbox" checked={understood} onChange={(e) => setUnderstood(e.target.checked)} required />
              <span>I understand that if I forget my password, my entries cannot be recovered.</span>
            </label>
          </div>
          <button className="primary full" disabled={busy || !understood}>
            {busy ? 'Creating keys…' : 'Create journal'}
          </button>
        </form>
        <p className="small" style={{ marginTop: '1rem' }}>
          Already have one? <Link to="/">Sign in</Link>
        </p>
      </div>
    </div>
  );
}
