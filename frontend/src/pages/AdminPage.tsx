import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, type AdminUser, type Page } from '../api';
import { deriveKeys, generateDataKey, passwordProblem, wrapDataKey } from '../crypto';
import { Alert, errorMessage, formatDate, PasswordField, useAction, useFieldError } from '../components/ui';

/** Admins manage accounts. They have no access to anyone's entries: there is no API for it and entries are ciphertext. */
export function AdminPage() {
  const [data, setData] = useState<Page<AdminUser> | null>(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback((p: number) => {
    api
      .adminUsers(p)
      .then((d) => {
        setData(d);
        setPage(p);
      })
      .catch((e) => setError(errorMessage(e)));
  }, []);

  useEffect(() => load(0), [load]);

  return (
    <>
      <h1>Admin</h1>
      <Alert kind="info">Admins can see account names and roles only. Journal entries are end-to-end encrypted and are never visible here.</Alert>
      <div className="grid-2">
        <div className="card">
          <div className="row">
            <h2 style={{ margin: 0 }}>Users</h2>
            <span className="spacer" />
            {data && <span className="muted small">{data.totalElements} total</span>}
          </div>
          <Alert>{error}</Alert>
          <div className="table-wrap" style={{ marginTop: '0.75rem' }}>
            <table>
              <thead>
                <tr>
                  <th>Username</th>
                  <th>Roles</th>
                  <th>Joined</th>
                </tr>
              </thead>
              <tbody>
                {data?.items.map((u) => (
                  <tr key={u.id}>
                    <td>{u.username}</td>
                    <td>
                      {u.roles.map((r) => (
                        <span key={r} className={`tag ${r === 'ADMIN' ? 'admin' : ''}`}>
                          {r.toLowerCase()}
                        </span>
                      ))}
                    </td>
                    <td className="muted">{formatDate(u.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {data && data.totalPages > 1 && (
            <div className="row" style={{ marginTop: '0.75rem' }}>
              <button disabled={page === 0} onClick={() => load(page - 1)}>
                Previous
              </button>
              <span className="muted small">
                Page {page + 1} of {data.totalPages}
              </span>
              <button disabled={page + 1 >= data.totalPages} onClick={() => load(page + 1)}>
                Next
              </button>
            </div>
          )}
        </div>
        <CreateAdminCard onCreated={() => load(0)} />
      </div>
    </>
  );
}

function CreateAdminCard({ onCreated }: { onCreated: () => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [ok, setOk] = useState<string | null>(null);
  const { busy, error, run } = useAction();
  const { fail, errorFor, clear } = useFieldError();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setOk(null);
    clear();
    const problem = passwordProblem(password, username.trim());
    if (problem) return fail('a-password', problem);
    run(async () => {
      const name = username.trim();
      const { authKey, kek } = await deriveKeys(name, password);
      const wrapped = await wrapDataKey(await generateDataKey(), kek);
      await api.adminCreate({ username: name, authKey, ...wrapped });
      setOk(`Admin "${name}" created. Share the password with them securely and ask them to change it.`);
      setUsername('');
      setPassword('');
      onCreated();
    });
  };

  return (
    <form className="card" onSubmit={submit} onChange={clear}>
      <h2>Create admin</h2>
      <Alert>{error}</Alert>
      <Alert kind="ok">{ok}</Alert>
      <div className="field">
        <label htmlFor="a-username">Username</label>
        <input id="a-username" type="text" value={username} pattern="[A-Za-z0-9_.\-]{3,32}" autoComplete="off" onChange={(e) => setUsername(e.target.value)} required />
      </div>
      <PasswordField id="a-password" label="Initial password" value={password} onChange={setPassword} autoComplete="new-password" hint="At least 10 characters." error={errorFor('a-password')} />
      <button className="primary" disabled={busy}>
        {busy ? 'Creating…' : 'Create admin'}
      </button>
    </form>
  );
}
