-- Reverses migration/V27__topics_without_concepts.sql.
--
-- The source = 'auto' rows V27 deleted are not restored: they were derived from the concept
-- links, and a backend from before V27 only writes them on generation or concept
-- re-classification. Run the admin Bulk Concept Re-Classifier after rolling back the code to
-- bring them back. All English topic labels are lost.
--
-- Idempotent -- safe to run against a database V27 never ran against.

DROP TABLE IF EXISTS public.topic_translations;

ALTER TABLE public.sequence_tags DROP CONSTRAINT IF EXISTS chk_sequence_tags_source;
ALTER TABLE public.sequence_tags
    ADD CONSTRAINT chk_sequence_tags_source CHECK (source IN ('auto', 'llm', 'manual'));
ALTER TABLE public.sequence_tags ALTER COLUMN source SET DEFAULT 'auto';

DELETE FROM flyway_schema_history WHERE version='27';
