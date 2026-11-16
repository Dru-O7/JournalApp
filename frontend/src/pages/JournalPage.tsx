import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { useBlocker } from 'react-router-dom';
import { api, ApiError, type EntryRecord, type Greeting } from '../api';
import { decryptEntry, encryptEntry, SENTIMENTS, type EntryPlaintext, type Sentiment } from '../crypto';
import { useSession } from '../session';
import { Alert, errorMessage, LockIcon, moodLabel, useAction } from '../components/ui';
import { ActivityHeatmap } from '../components/ActivityHeatmap';
import { localIso } from '../activity';
import { confirmDiscard, DISCARD_PROMPT, setUnsaved } from '../unsaved';

/**
 * A daily journal: one entry per day. Today's entry can be edited until midnight; every earlier
 * day is read-only, and entries cannot be deleted. The server enforces the same rules.
 */
interface Entry {
  id: string;
  version: number;
  entryDate: string; // yyyy-MM-dd
  locksAt: string;
  plain: EntryPlaintext | null; // null = could not be decrypted
}

const NEW = 'new';
const dayFmt = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });

function formatDay(entryDate: string): string {
  return entryDate === localIso(new Date()) ? 'Today' : dayFmt.format(new Date(`${entryDate}T12:00:00`));
}

function isEditable(e: Entry): boolean {
  return Date.now() < Date.parse(e.locksAt);
}

function toEntry(r: EntryRecord, plain: EntryPlaintext | null): Entry {
  return { id: r.id, version: r.version, entryDate: r.entryDate, locksAt: r.locksAt, plain };
}

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
  const [activityKey, setActivityKey] = useState(0); // refetch the heatmap after writing
  // Where keyboard focus goes back to when the open entry is closed.
  const returnFocusTo = useRef<string | null>(null);
  const open = (id: string | null) => {
    if (id === selected || !confirmDiscard()) return;
    if (id === null) returnFocusTo.current = selected;
    setSelected(id);
    setEditorKey((k) => k + 1);
  };
  useEffect(() => {
    if (selected !== null || returnFocusTo.current === null) return;
    const id = returnFocusTo.current;
    returnFocusTo.current = null;
    const target =
      document.querySelector<HTMLElement>(`[data-entry-id="${CSS.escape(id)}"]`) ??
      document.querySelector<HTMLElement>('[data-write-today]');
    target?.focus();
  }, [selected]);

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
          return toEntry(r, plain);
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

  const todayIso = localIso(new Date());
  const todays = entries.find((e) => e.entryDate === todayIso) ?? null;
  const current = selected && selected !== NEW ? entries.find((e) => e.id === selected) ?? null : null;

  const onSaved = (saved: Entry) => {
    setEntries((prev) => [saved, ...prev.filter((e) => e.id !== saved.id)].sort((a, b) => b.entryDate.localeCompare(a.entryDate)));
    setSelected(saved.id);
    setActivityKey((k) => k + 1);
  };

  // The server says today's entry already exists (another tab or device) or the day just ended.
  const onStale = async () => {
    await load(0);
    setActivityKey((k) => k + 1);
  };

  return (
    <>
      <GreetingBanner />
      <div className={selected ? 'editing-mobile-hide' : undefined}>
        <ActivityHeatmap refreshKey={activityKey} />
      </div>
      <div className={`journal ${selected ? 'editing' : ''}`}>
        <aside className="card entry-list" aria-label="Entries">
          <div className="row" style={{ padding: '0.25rem 0.5rem 0.5rem' }}>
            <h2 style={{ margin: 0 }}>Days</h2>
            <span className="spacer" />
            {!loading &&
              (todays ? (
                <button className="ghost" data-write-today onClick={() => open(todays.id)}>
                  Today's entry
                </button>
              ) : (
                <button className="primary" data-write-today onClick={() => open(NEW)}>
                  Write today's entry
                </button>
              ))}
          </div>
          <Alert>{loadError}</Alert>
          {!loading && entries.length === 0 && !loadError && (
            <div className="empty">Nothing yet. One entry a day: write today's.</div>
          )}
          <ul>
            {entries.map((e) => (
              <li key={e.id}>
                <button
                  className={`entry-item ${selected === e.id ? 'selected' : ''}`}
                  data-entry-id={e.id}
                  aria-current={selected === e.id ? 'true' : undefined}
                  onClick={() => open(e.id)}
                >
                  <span className="t">{e.plain ? e.plain.title || 'Untitled' : '⚠ Unreadable entry'}</span>
                  <span className="m">
                    {formatDay(e.entryDate)}
                    {!isEditable(e) && <LockIcon size={12} />}
                    {e.plain?.sentiment && <span className="mood">{moodLabel(e.plain.sentiment)}</span>}
                  </span>
                </button>
              </li>
            ))}
          </ul>
          {loading && <div className="muted small" style={{ padding: '0.75rem' }}>Decrypting…</div>}
          {hasMore && !loading && (
            <button className="ghost full" onClick={() => load(page + 1)}>
              Load earlier days
            </button>
          )}
        </aside>

        <section>
          {selected === null && (
            <div className="card empty">
              <LockIcon size={28} />
              <p style={{ marginTop: '0.75rem' }}>{todays ? 'Pick a day to read it.' : "Pick a day to read it, or write today's entry."}</p>
              <p className="small">One entry a day. You can edit it until midnight; after that it's kept exactly as written.</p>
            </div>
          )}
          {selected !== null && current && (!isEditable(current) || current.plain === null) && (
            <ReadOnlyEntry key={editorKey} entry={current} onClose={() => open(null)} />
          )}
          {selected !== null && (selected === NEW || (current && isEditable(current) && current.plain !== null)) && (
            <Editor key={editorKey} entry={current} onSaved={onSaved} onStale={onStale} onClose={() => open(null)} />
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

function ReadOnlyEntry({ entry, onClose }: { entry: Entry; onClose: () => void }) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => headingRef.current?.focus(), []);
  return (
    <article className="card stack">
      <div className="row">
        <span className="lock-note">
          <LockIcon size={14} /> {formatDay(entry.entryDate)} · read-only
        </span>
        <span className="spacer" />
        <button type="button" className="ghost" onClick={onClose} aria-label="Close entry">
          ✕
        </button>
      </div>
      {entry.plain === null ? (
        <>
          <h2 className="entry-title" tabIndex={-1} ref={headingRef}>
            Unreadable entry
          </h2>
          <Alert>
            This entry could not be decrypted. It may have been modified on the server. Its contents are not shown for your safety.
          </Alert>
        </>
      ) : (
        <>
          <h2 className="entry-title" tabIndex={-1} ref={headingRef}>
            {entry.plain.title || 'Untitled'}
          </h2>
          {entry.plain.sentiment && <span className="mood">{moodLabel(entry.plain.sentiment)}</span>}
          <div className="entry-read">{entry.plain.content || <span className="muted">No text.</span>}</div>
          <p className="muted small" style={{ margin: 0 }}>
            This day has ended, so its entry is kept exactly as written.
          </p>
        </>
      )}
    </article>
  );
}

function Editor({
  entry,
  onSaved,
  onStale,
  onClose,
}: {
  entry: Entry | null; // null = writing today's entry
  onSaved: (e: Entry) => void;
  onStale: () => void;
  onClose: () => void;
}) {
  const { dataKey, me } = useSession();
  const [title, setTitle] = useState(entry?.plain?.title ?? '');
  const [content, setContent] = useState(entry?.plain?.content ?? '');
  const [sentiment, setSentiment] = useState<Sentiment | null>(entry?.plain?.sentiment ?? null);
  const [saved, setSaved] = useState(false);
  const { busy, error, setError, run } = useAction();
  const titleRef = useRef<HTMLInputElement>(null);
  const contentRef = useRef<HTMLTextAreaElement>(null);

  // Opening the editor moves focus into it: the title for a new entry, the end of the text otherwise.
  useEffect(() => {
    if (!entry) return titleRef.current?.focus();
    const t = contentRef.current;
    if (t) {
      t.focus();
      t.setSelectionRange(t.value.length, t.value.length);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only when the editor is opened
  }, []);

  const dirty =
    entry === null ? title !== '' || content !== '' || sentiment !== null
      : title !== (entry.plain?.title ?? '') || content !== (entry.plain?.content ?? '') || sentiment !== (entry.plain?.sentiment ?? null);

  // Guard unsaved writing against in-app navigation (incl. Back), tab close/reload, and day switches.
  useEffect(() => {
    setUnsaved(dirty);
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);
  useEffect(() => () => setUnsaved(false), []);
  const blocker = useBlocker(({ currentLocation, nextLocation }) => dirty && currentLocation.pathname !== nextLocation.pathname);
  useEffect(() => {
    if (blocker.state !== 'blocked') return;
    if (window.confirm(DISCARD_PROMPT)) blocker.proceed();
    else blocker.reset();
  }, [blocker]);

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
          : await api.createEntry({ id, timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC', ciphertext, iv, reportSentiment });
        onSaved(toEntry(rec, plain));
        setSaved(true);
      } catch (err) {
        if (err instanceof ApiError && err.status === 401)
          throw new Error('Not saved yet: your session ended. Sign in again, then press Save. Your text is still here.');
        if (err instanceof ApiError && err.status === 409 && !entry) {
          onStale();
          throw new Error("You've already written today's entry (maybe in another tab). Copy your text; it's been reloaded below.");
        }
        if (err instanceof ApiError && err.status === 409)
          throw new Error('This entry was changed in another tab or device. Copy your text, reload the page and try again.');
        if (err instanceof ApiError && err.status === 403) {
          onStale();
          throw new Error("This day has ended, so the entry is now locked. Copy anything you'd like to keep.");
        }
        throw err;
      }
    });
  };

  return (
    <form className="card stack" onSubmit={save}>
      <div className="row">
        <span className="lock-note">
          <LockIcon size={14} /> End-to-end encrypted
        </span>
        <span className="spacer" />
        <span className="muted small">{entry ? formatDay(entry.entryDate) : 'Today'}</span>
        <button type="button" className="ghost" onClick={onClose} aria-label="Close editor">
          ✕
        </button>
      </div>
      <Alert>{error}</Alert>
      {saved && !dirty && <Alert kind="ok">Saved. You can keep editing until midnight.</Alert>}
      <input
        ref={titleRef}
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
        ref={contentRef}
        placeholder="How was today?"
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
      <div className="row">
        <button className="primary" disabled={busy || !dirty}>
          {busy ? 'Encrypting…' : 'Save'}
        </button>
        <span className="spacer" />
        <span className="muted small">Editable until midnight, then locked for good.</span>
      </div>
    </form>
  );
}
