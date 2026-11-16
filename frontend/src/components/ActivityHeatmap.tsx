import { useEffect, useLayoutEffect, useMemo, useRef, useState, type PointerEvent } from 'react';
import { api } from '../api';
import { buildGrid, localIso, summarize, type Cell } from '../activity';

const WEEKDAY_LABELS: [number, string][] = [
  [1, 'Mon'],
  [3, 'Wed'],
  [5, 'Fri'],
];
const tipDate = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });

/**
 * GitHub-style calendar of the days you wrote on over the last year (one entry per day).
 * The server returns only the dates; entry contents are never involved.
 */
export function ActivityHeatmap({ refreshKey }: { refreshKey: number }) {
  const [days, setDays] = useState<Set<string> | null>(null);
  const [tip, setTip] = useState<{ x: number; y: number; text: string } | null>(null);
  const cardRef = useRef<HTMLDivElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .entryActivity()
      .then((a) => !cancelled && setDays(new Set(a.days)))
      .catch(() => !cancelled && setDays(null));
    return () => {
      cancelled = true;
    };
  }, [refreshKey]);

  const todayKey = localIso(new Date());
  // eslint-disable-next-line react-hooks/exhaustive-deps -- todayKey rebuilds the grid after midnight
  const grid = useMemo(() => buildGrid(days ?? new Set(), new Date()), [days, todayKey]);
  const stats = useMemo(() => summarize(grid), [grid]);

  // Narrow screens scroll the calendar sideways; start at the most recent weeks, and stay there when
  // the container resizes (rotation, window resize, the calendar being re-shown after editing).
  useLayoutEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollLeft = el.scrollWidth;
    const ro = new ResizeObserver(() => {
      el.scrollLeft = el.scrollWidth;
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, [days]);

  if (!days) return null;

  const show = (e: PointerEvent<HTMLDivElement>, c: Cell) => {
    const card = cardRef.current?.getBoundingClientRect();
    if (!card) return;
    const r = e.currentTarget.getBoundingClientRect();
    const x = Math.min(Math.max(r.left + r.width / 2 - card.left, 90), card.width - 90);
    const when = tipDate.format(new Date(`${c.date}T12:00:00`));
    setTip({ x, y: r.top - card.top, text: `${c.written ? 'Written' : 'No entry'} · ${when}` });
  };

  const summary =
    stats.daysWritten === 0
      ? 'No days written in the last year yet'
      : `${stats.daysWritten} ${stats.daysWritten === 1 ? 'day' : 'days'} written in the last year`;

  return (
    <section className="card activity" ref={cardRef} aria-label="Writing activity">
      <div className="activity-head">
        <h2>Your year</h2>
        <span className="muted small">{summary}</span>
        {stats.streak > 1 && <span className="streak small">{stats.streak}-day streak</span>}
      </div>
      <div className="hm-scroll" ref={scrollRef}>
        <div className="hm" role="img" aria-label={`Calendar of days written. ${summary}.`} onPointerLeave={() => setTip(null)}>
          {grid.months.map((mo) => (
            <span key={`${mo.week}-${mo.label}`} className="hm-month" style={{ gridColumn: mo.week + 2 }} aria-hidden>
              {mo.label}
            </span>
          ))}
          {WEEKDAY_LABELS.map(([row, label]) => (
            <span key={label} className="hm-wd" style={{ gridRow: row + 2 }} aria-hidden>
              {label}
            </span>
          ))}
          {grid.weeks.map((week, w) =>
            week.map((c, d) => (
              <div
                key={c.date}
                className={`hm-cell${c.written ? ' on' : ''}${c.future ? ' future' : ''}`}
                style={{ gridColumn: w + 2, gridRow: d + 2 }}
                onPointerEnter={c.future ? () => setTip(null) : (e) => show(e, c)}
              />
            )),
          )}
        </div>
      </div>
      <div className="hm-legend" aria-hidden>
        <i />
        <span>No entry</span>
        <i className="on" />
        <span>Written</span>
      </div>
      {tip && (
        <div className="hm-tip" style={{ left: tip.x, top: tip.y }} role="status">
          {tip.text}
        </div>
      )}
    </section>
  );
}
