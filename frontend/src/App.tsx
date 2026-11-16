import { Navigate, NavLink, Route, Routes, Link } from 'react-router-dom';
import { useSession } from './session';
import { LoginPage, SignupPage } from './pages/AuthPages';
import { UnlockPage } from './pages/UnlockPage';
import { JournalPage } from './pages/JournalPage';
import { SettingsPage } from './pages/SettingsPage';
import { AdminPage } from './pages/AdminPage';
import { LockIcon } from './components/ui';
import { ReauthDialog } from './components/ReauthDialog';
import { confirmDiscard } from './unsaved';

export default function App() {
  const { status, me, logout, expired } = useSession();

  if (status === 'loading') return <div className="center">Loading…</div>;

  if (status === 'signed-out') {
    return (
      <Routes>
        <Route path="/signup" element={<SignupPage />} />
        <Route path="*" element={<LoginPage />} />
      </Routes>
    );
  }

  if (status === 'locked') return <UnlockPage />;

  const isAdmin = me?.roles.includes('ADMIN') ?? false;
  return (
    <>
      <header className="topbar">
        <Link to="/" className="brand">
          <LockIcon /> Journal
        </Link>
        <nav className="nav" aria-label="Main">
          <NavLink to="/" end>
            Entries
          </NavLink>
          <NavLink to="/settings">Settings</NavLink>
          {isAdmin && <NavLink to="/admin">Admin</NavLink>}
          <button className="ghost" onClick={() => confirmDiscard() && logout()}>
            Sign out
          </button>
        </nav>
      </header>
      <main className="main">
        <Routes>
          <Route path="/" element={<JournalPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/admin" element={isAdmin ? <AdminPage /> : <Navigate to="/" replace />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      {expired && <ReauthDialog />}
    </>
  );
}
