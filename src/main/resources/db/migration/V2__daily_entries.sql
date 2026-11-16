-- Daily journal: one entry per user per calendar day. An entry can be edited until its day ends
-- in the writer's time zone (locks_at); after that it is read-only. Entries cannot be deleted.
ALTER TABLE journal_entries
    ADD COLUMN entry_date date,
    ADD COLUMN locks_at   timestamptz;

-- Entries written before this rule: date them by UTC, lock them, and keep the earliest per day.
UPDATE journal_entries SET entry_date = (created_at AT TIME ZONE 'UTC')::date, locks_at = created_at;
DELETE FROM journal_entries e
    USING journal_entries f
    WHERE e.user_id = f.user_id AND e.entry_date = f.entry_date AND (e.created_at, e.id) > (f.created_at, f.id);

ALTER TABLE journal_entries
    ALTER COLUMN entry_date SET NOT NULL,
    ALTER COLUMN locks_at SET NOT NULL,
    ADD CONSTRAINT journal_entries_one_per_day UNIQUE (user_id, entry_date);
