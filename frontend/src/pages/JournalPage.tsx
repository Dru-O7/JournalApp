import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, ApiError, type EntryRecord, type Greeting } from '../api';
import { decryptEntry, encryptEntry, SENTIMENTS, type EntryPlaintext, type Sentiment } from '../crypto';
import { useSession } from '../session';
import { Alert, errorMessage, formatDate, LockIcon, moodLabel, useAction } from '../components/ui';

interface Entry {
  id: string;
  version: number;
  createdAt: string;
  updatedAt: string;
  plain: EntryPlaintext | null; // null = could not be decrypted
}

const NEW = 'new';

export function JournalPage() {
  const { dataKey } = useSession();
  const [entries, setEntries] = useState<Entry[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  // Remount the editor only when the user opens something else, not when a new entry gets its id on save.
  const [editorKey, setEditorKey] = useState(0);
  const open = (id: string | null) => {
    setSelected(id);
    setEditorKey((k) => k + 1);
  };

  const decryptAll = useCallback(
    (records: EntryRecord[]) =>
      Promise.all(
        records.map(async (r): Promise<Entry> => {
          let plain: EntryPlaintext | null = null;
          try {
            plain = await decryptEntry(dataKey!, r.id, r.ciphertext, r.iv);
          } catch {
            /* tampered or foreign ciphertext: shown as unreadable, never silently trusted */
          }
          return { id: r.id, version: r.version, createdAt: r.createdAt, updatedAt: r.updatedAt, plain };
        }),
      ),
    [dataKey],
  );

  const load = useCallback(
    async (p: number) => {
      setLoading(true);
      setLoadError(null);
      try {
        const res = await api.listEntries(p);
        const decrypted = await decryptAll(res.items);
        setEntries((prev) => (p === 0 ? decrypted : [...prev, ...decrypted.filter((d) => !prev.some((x) => x.id === d.id))]));
        setPage(p);
        setHasMore(p + 1 < res.totalPages);
      } catch (e) {
        setLoadError(errorMessage(e));
      } finally {
        setLoading(false);
      }
    },
    [decryptAll],
  );

  useEffect(() => {
    load(0);
  }, [load]);

  const current = selected && selected !== NEW ? entries.find((e) => e.id === selected) ?? null : null;

  const onSaved = (saved: Entry) => {
    setEntries((prev) => {
      const rest = prev.filter((e) => e.id !== saved.id);
      return [saved, ...rest].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    });
    setSelected(saved.id);
  };

  const onDeleted = (id: string) => {
    setEntries((prev) => prev.filter((e) => e.id !== id));
    open(null);
  };

  return (
    <>
      <GreetingBanner />
      <div className={`journal ${selected ? 'editing' : ''}`}>
        <aside className="card entry-list" aria-label="Entries">
          <div className="row" style={{ padding: '0.25rem 0.5rem 0.5rem' }}>
            <h2 style={{ margin: 0 }}>Entries</h2>
            <span className="spacer" />
            <button className="primary" onClick={() => open(NEW)}>
              New entry
            </button>
          </div>
          <Alert>{loadError}</Alert>
          {!loading && entries.length === 0 && !loadError && <div className="empty">No entries yet. Write your first one.</div>}
          <ul>
            {entries.map((e) => (
              <li key={e.id}>
                <button className={`entry-item ${selected === e.id ? 'selected' : ''}`} onClick={() => open(e.id)}>
                  <span className="t">{e.plain ? e.plain.title || 'Untitled' : '⚠ Unreadable entry'}</span>
                  <span className="m">
                    {formatDate(e.createdAt)}
                    {e.plain?.sentiment && <span className="mood">{moodLabel(e.plain.sentiment)}</span>}
                  </span>
                </button>
              </li>
            ))}
          </ul>
          {loading && <div className="muted small" style={{ padding: '0.75rem' }}>Decrypting…</div>}
          {hasMore && !loading && (
            <button className="ghost full" onClick={() => load(page + 1)}>
              Load older entries
            </button>
          )}
        </aside>

        <section>
          {selected === null && (
            <div className="card empty">
              <LockIcon size={28} />
              <p style={{ marginTop: '0.75rem' }}>Select an entry or start a new one.</p>
              <p className="small">Entries are encrypted on this device before they are saved.</p>
            </div>
          )}
          {selected !== null && (selected === NEW || current) && (
            <Editor key={editorKey} entry={current} onSaved={onSaved} onDeleted={onDeleted} onClose={() => open(null)} />
          )}
        </section>
      </div>
    </>
  );
}

function GreetingBanner() {
  const [g, setG] = useState<Greeting | null>(null);
  useEffect(() => {
    api.greeting().then(setG).catch(() => setG(null));
  }, []);
  if (!g) return null;
  return (
    <div className="greeting">
      <h1>{g.message}</h1>
      {g.feelsLike != null && (
        <span className="muted">
          Feels like {g.feelsLike}°{g.weatherDescription ? ` · ${g.weatherDescription}` : ''}
        </span>
      )}
      {g.quote && (
        <p className="quote" style={{ flexBasis: '100%', margin: 0 }}>
          “{g.quote}”{g.quoteAuthor ? ` — ${g.quoteAuthor}` : ''}
        </p>
      )}
    </div>
  );
}

function Editor({
  entry,
  onSaved,
  onDeleted,
  onClose,
}: {
  entry: Entry | null;
  onSaved: (e: Entry) => void;
  onDeleted: (id: string) => void;
  onClose: () => void;
}) {
  const { dataKey, me } = useSession();
  const unreadable = entry !== null && entry.plain === null;
  const [title, setTitle] = useState(entry?.plain?.title ?? '');
  const [content, setContent] = useState(entry?.plain?.content ?? '');
  const [sentiment, setSentiment] = useState<Sentiment | null>(entry?.plain?.sentiment ?? null);
  const [saved, setSaved] = useState(false);
  const { busy, error, setError, run } = useAction();

  const dirty =
    entry === null ? title !== '' || content !== '' || sentiment !== null
      : title !== (entry.plain?.title ?? '') || content !== (entry.plain?.content ?? '') || sentiment !== (entry.plain?.sentiment ?? null);

  const save = (e: FormEvent) => {
    e.preventDefault();
    if (!title.trim() && !content.trim()) return setError('Write something first.');
    run(async () => {
      const id = entry?.id ?? crypto.randomUUID();
      const plain = { title: title.trim(), content, sentiment };
      const { ciphertext, iv } = await encryptEntry(dataKey!, id, plain);
      // The mood is only shared with the server if the user opted in to the weekly email.
      const reportSentiment = me?.weeklyReport ? sentiment : null;
      try {
        const rec = entry
          ? await api.updateEntry(id, { ciphertext, iv, reportSentiment, version: entry.version })
          : await api.createEntry({ id, ciphertext, iv, reportSentiment });
        onSaved({ id: rec.id, version: rec.version, createdAt: rec.createdAt, updatedAt: rec.updatedAt, plain });
        setSaved(true);
      } catch (err) {
        if (err instanceof ApiError && err.status === 409)
          throw new Error('This entry was changed in another tab or device. Copy your text, reload the page and try again.');
        throw err;
      }
    });
  };

  const remove = () => {
    if (!entry || !confirm('Delete this entry permanently?')) return;
    run(async () => {
      await api.deleteEntry(entry.id);
      onDeleted(entry.id);
    });
  };

  return (
    <form className="card stack" onSubmit={save}>
      <div className="row">
        <span className="lock-note">
          <LockIcon size={14} /> End-to-end encrypted
        </span>
        <span className="spacer" />
        {entry && <span className="muted small">Created {formatDate(entry.createdAt)}</span>}
        <button type="button" className="ghost" onClick={onClose} aria-label="Close editor">
          ✕
        </button>
      </div>
      {unreadable ? (
        <Alert>
          This entry could not be decrypted. It may have been modified on the server. Its contents are not shown for your safety.
        </Alert>
      ) : (
        <>
          <Alert>{error}</Alert>
          {saved && !dirty && <Alert kind="ok">Saved.</Alert>}
          <input
            className="title-input"
            type="text"
            placeholder="Title"
            aria-label="Title"
            value={title}
            maxLength={200}
            onChange={(e) => {
              setTitle(e.target.value);
              setSaved(false);
            }}
          />
          <textarea
            placeholder="What's on your mind?"
            aria-label="Entry"
            value={content}
            maxLength={100_000}
            onChange={(e) => {
              setContent(e.target.value);
              setSaved(false);
            }}
          />
          <div>
            <div className="small muted" style={{ marginBottom: '0.4rem' }}>
              Mood{me?.weeklyReport ? ' (included in your weekly email)' : ''}
            </div>
            <div className="moods" role="group" aria-label="Mood">
              {SENTIMENTS.map((s) => (
                <button
                  type="button"
                  key={s}
                  aria-pressed={sentiment === s}
                  onClick={() => {
                    setSentiment(sentiment === s ? null : s);
                    setSaved(false);
                  }}
                >
                  {moodLabel(s)}
                </button>
              ))}
            </div>
          </div>
        </>
      )}
      <div className="row">
        {!unreadable && (
          <button className="primary" disabled={busy || !dirty}>
            {busy ? 'Encrypting…' : 'Save'}
          </button>
        )}
        <span className="spacer" />
        {entry && (
          <button type="button" className="danger" onClick={remove} disabled={busy}>
            Delete
          </button>
        )}
      </div>
    </form>
  );
}
