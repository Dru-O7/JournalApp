import { describe, expect, it } from 'vitest';
import { buildGrid, localIso, summarize } from './activity';

const today = new Date(2026, 10, 18, 15); // Wed 18 Nov 2026, 15:00 local

describe('activity heatmap', () => {
  it('ends with the week containing today, Sunday first', () => {
    const g = buildGrid(new Set(), today);
    expect(g.weeks).toHaveLength(53);
    const last = g.weeks[52];
    expect(last[0].date).toBe('2026-11-15'); // Sunday
    expect(last[3].date).toBe('2026-11-18');
    expect(last[3].future).toBe(false);
    expect(last[4].future).toBe(true);
    expect(g.weeks[0][0].date).toBe('2025-11-16');
  });

  it('has 371 consecutive days without gaps or repeats', () => {
    const dates = buildGrid(new Set(), today).weeks.flat().map((c) => c.date);
    expect(new Set(dates).size).toBe(371);
    for (let i = 1; i < dates.length; i++) {
      const prev = new Date(`${dates[i - 1]}T12:00:00`);
      prev.setDate(prev.getDate() + 1);
      expect(localIso(prev)).toBe(dates[i]);
    }
  });

  it('marks written days and labels months without a cramped first label', () => {
    const g = buildGrid(new Set(['2026-11-18', '2026-03-01']), today);
    expect(g.weeks[52][3].written).toBe(true);
    expect(g.weeks[52][2].written).toBe(false);
    expect(g.weeks.flat().find((c) => c.date === '2026-03-01')?.written).toBe(true);
    expect(g.months.length).toBeGreaterThanOrEqual(12);
    expect(g.months[1].week - g.months[0].week).toBeGreaterThanOrEqual(3);
  });

  it('summarizes days written and the current streak', () => {
    const days = new Set(['2026-11-15', '2026-11-16', '2026-11-17', '2026-11-10', '2027-01-01']);
    // today (18th) is not written yet: the streak still counts 15-17; future days are ignored
    expect(summarize(buildGrid(days, today))).toEqual({ daysWritten: 4, streak: 3 });
    days.add('2026-11-18');
    expect(summarize(buildGrid(days, today)).streak).toBe(4);
    days.delete('2026-11-17');
    expect(summarize(buildGrid(days, today)).streak).toBe(1);
  });
});
