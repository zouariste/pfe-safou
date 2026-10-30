-- Manual ordering for the worksheets assigned to a class.
--
-- Until now every read of `class_worksheets` ordered by worksheet name, so a newly assigned
-- worksheet landed wherever its name happened to sort and a teacher had no way to put the
-- worksheets in the order the class works through them.
--
-- `position` mirrors `worksheets_sequence.position` (V18): a plain integer per (class_id,
-- worksheet_id) row. Assigning a worksheet sets it to one past the class's current maximum (0
-- for the first), so "assign" always appends. Reordering rewrites every row's position for that
-- class. No uniqueness constraint on (class_id, position), for the same reason as in V18; reads
-- break ties by assigned_at and worksheet_id.
--
-- Backfill orders existing assignments by when they were made, oldest first, which is the new
-- default order. Rows without an assigned_at sort first: the column has defaulted to now()
-- since V1, so a missing value can only be older than any stamped one.
--
-- Re-running is safe: the ADD is guarded, the backfill only touches rows still missing a
-- position, and SET NOT NULL is a no-op once every row already satisfies it.

ALTER TABLE public.class_worksheets
    ADD COLUMN IF NOT EXISTS position integer;

UPDATE public.class_worksheets cw
SET position = ordered.rn - 1
FROM (
    SELECT class_id, worksheet_id,
           ROW_NUMBER() OVER (
               PARTITION BY class_id ORDER BY assigned_at NULLS FIRST, worksheet_id
           ) AS rn
    FROM public.class_worksheets
) ordered
WHERE cw.class_id = ordered.class_id
  AND cw.worksheet_id = ordered.worksheet_id
  AND cw.position IS NULL;

ALTER TABLE public.class_worksheets
    ALTER COLUMN position SET NOT NULL;
