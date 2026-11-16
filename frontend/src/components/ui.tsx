import { useEffect, useState, type InputHTMLAttributes, type ReactNode } from 'react';
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

/**
 * A client-side validation error tied to one field: shown under it, announced with it
 * (aria-describedby + aria-invalid), and focused so keyboard and screen-reader users land on it.
 * Put `onChange={clear}` on the <form> so the message goes away once the user edits anything.
 */
export function useFieldError() {
  const [fieldError, setFieldError] = useState<{ id: string; message: string } | null>(null);
  useEffect(() => {
    if (fieldError) document.getElementById(fieldError.id)?.focus();
  }, [fieldError]);
  return {
    fail: (id: string, message: string) => setFieldError({ id, message }),
    errorFor: (id: string) => (fieldError?.id === id ? fieldError.message : undefined),
    clear: () => setFieldError(null),
  };
}

/** Props that wire an input to its hint and error text. */
function describedBy(id: string, hint?: ReactNode, error?: string) {
  const ids = [hint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean).join(' ');
  return { 'aria-describedby': ids || undefined, 'aria-invalid': error ? true : undefined };
}

function FieldNotes({ id, hint, error }: { id: string; hint?: ReactNode; error?: string }) {
  return (
    <>
      {error && (
        <div id={`${id}-error`} className="field-error">
          {error}
        </div>
      )}
      {hint && (
        <div id={`${id}-hint`} className="hint">
          {hint}
        </div>
      )}
    </>
  );
}

export function TextField({
  id,
  label,
  value,
  onChange,
  hint,
  error,
  type = 'text',
  ...rest
}: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  hint?: ReactNode;
  error?: string;
  type?: 'text' | 'email';
} & Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'value' | 'onChange' | 'type'>) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input id={id} type={type} value={value} onChange={(e) => onChange(e.target.value)} {...describedBy(id, hint, error)} {...rest} />
      <FieldNotes id={id} hint={hint} error={error} />
    </div>
  );
}

export function PasswordField({
  id,
  label,
  value,
  onChange,
  autoComplete,
  hint,
  error,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  autoComplete: string;
  hint?: string;
  error?: string;
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
          {...describedBy(id, hint, error)}
        />
        <button type="button" className="ghost small" onClick={() => setShow((s) => !s)} aria-label={show ? 'Hide password' : 'Show password'}>
          {show ? 'Hide' : 'Show'}
        </button>
      </div>
      <FieldNotes id={id} hint={hint} error={error} />
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
