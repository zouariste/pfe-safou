-- Reverses migration/V28__lowercase_programming_language.sql -- partially.
-- The constraint can be dropped, but the original casing of the rows V28
-- lowercased was not recorded, so it cannot be restored. Lowercase is the
-- canonical spelling anyway (SupportedLanguage), so nothing depends on it.

ALTER TABLE tiered_task_sequences
    DROP CONSTRAINT IF EXISTS tiered_task_sequences_programming_language_lowercase_check;

DELETE FROM flyway_schema_history WHERE version='28';
