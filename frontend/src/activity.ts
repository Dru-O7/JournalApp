/** Pure helpers for the writing-activity heatmap (kept separate so they can be unit-tested). */

export const WEEKS = 53;

export interface Cell {
  date: string; // yyyy-MM-dd, local time
  written: boolean;
  future: boolean;
}

export interface Grid {
  /** Columns of 7 days, Sunday first; the last column contains today. */
  weeks: Cell[][];
  /** Month labels and the week (column) they start at. */
  months: { week: number; label: string }[];
}

export function localIso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export function buildGrid(written: Set<string>, today: Date, weeks = WEEKS): Grid {
  // Dates are built from y/m/d at noon so daylight-saving shifts never skip or repeat a day.
  const y = today.getFullYear();
  const m = today.getMonth();
  const firstDay = today.getDate() - today.getDay() - (weeks - 1) * 7;
  const todayIso = localIso(today);
  const monthFmt = new Intl.DateTimeFormat(undefined, { month: 'short' });

  const cols: Cell[][] = [];
  const months: Grid['months'] = [];
  let prevMonth = -1;
  for (let w = 0; w < weeks; w++) {
    const col: Cell[] = [];
    for (let d = 0; d < 7; d++) {
      const iso = localIso(new Date(y, m, firstDay + w * 7 + d, 12));
      col.push({ date: iso, written: written.has(iso), future: iso > todayIso });
    }
    const sunday = new Date(y, m, firstDay + w * 7, 12);
    if (sunday.getMonth() !== prevMonth) {
      months.push({ week: w, label: monthFmt.format(sunday) });
      prevMonth = sunday.getMonth();
    }
    cols.push(col);
  }
  // A partial first month would collide with the next label.
  if (months.length > 1 && months[1].week - months[0].week < 3) months.shift();
  return { weeks: cols, months };
}

export interface Summary {
  daysWritten: number;
  /** Consecutive days with an entry, ending today (or yesterday, if today has none yet). */
  streak: number;
}

export function summarize(grid: Grid): Summary {
  const days = grid.weeks.flat().filter((c) => !c.future);
  const daysWritten = days.filter((c) => c.written).length;
  let i = days.length - 1;
  if (i >= 0 && !days[i].written) i--; // today isn't over yet
  let streak = 0;
  while (i >= 0 && days[i].written) {
    streak++;
    i--;
  }
  return { daysWritten, streak };
}
