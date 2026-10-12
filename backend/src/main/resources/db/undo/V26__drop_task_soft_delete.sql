-- Reverses migration/V26__task_soft_delete.sql.
--
-- Every tier an owner deleted reappears in its sequence, and may then share a tier_level with
-- the tier that moved into its place: the marker is the only record that it was deleted.
-- Application code filters on deleted_at, so a backend at V26 or later will fail against a
-- database this has run against. Roll the code back with the schema.
--
-- Idempotent -- safe to run against a database that never had the column.

ALTER TABLE public.tasks DROP COLUMN IF EXISTS deleted_at;

DELETE FROM flyway_schema_history WHERE version='26';
