import { useState, type ReactNode } from 'react';
import { ApiError } from '../api';

export function LockIcon({ size = 18 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <rect x="4" y="11" width="16" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </svg>
  );
}

export function Alert({ kind = 'error', children }: { kind?: 'error' | 'ok' | 'info'; children: ReactNode }) {
  if (!children) return null;
  return (
    <div className={`alert ${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    const fields = Object.entries(e.fieldErrors);
    return fields.length ? fields.map(([k, v]) => `${k}: ${v}`).join('; ') : e.message;
  }
  if (e instanceof Error) return e.message;
  return 'Something went wrong.';
}

/** Runs an async action, tracking busy state and turning failures into a message. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const run = async (fn: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return { busy, error, setError, run };
}

export function PasswordField({
  id,
  label,
  value,
  onChange,
  autoComplete,
  hint,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  autoComplete: string;
  hint?: string;
}) {
  const [show, setShow] = useState(false);
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="row" style={{ flexWrap: 'nowrap', gap: '0.4rem' }}>
        <input
          id={id}
          type={show ? 'text' : 'password'}
          value={value}
          autoComplete={autoComplete}
          onChange={(e) => onChange(e.target.value)}
          required
        />
        <button type="button" className="ghost small" onClick={() => setShow((s) => !s)} aria-label={show ? 'Hide password' : 'Show password'}>
          {show ? 'Hide' : 'Show'}
        </button>
      </div>
      {hint && <div className="hint">{hint}</div>}
    </div>
  );
}

const MOOD_LABEL: Record<string, string> = { HAPPY: '😊 Happy', SAD: '😔 Sad', ANGRY: '😠 Angry', ANXIOUS: '😟 Anxious' };
export function moodLabel(m: string | null): string {
  return m ? (MOOD_LABEL[m] ?? m) : '';
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}
