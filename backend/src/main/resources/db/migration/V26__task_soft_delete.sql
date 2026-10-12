-- Let an owner delete a tier from a sequence without losing what students did on it.
--
-- learning_events, task_submissions, hint_usage, task_originals and task_change_analyses all
-- reference tasks.id ON DELETE CASCADE (V1, V8), so a real DELETE would erase every attempt,
-- hint and research event recorded against the tier. Deleting must only take the tier out of
-- where it is *offered*: the sequence page, the solve list, class progress, search and copies.
--
-- deleted_at marks that, the same way V23 does for sequences. Loading the tasks of a sequence
-- asks "deleted_at IS NULL"; loading one task by id, which is what research and event logging
-- do, does not.
--
-- Null for every existing row. Re-running is safe: the ADD is guarded.

ALTER TABLE public.tasks
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;
