-- Keep a sequence's topics (sequence_tags) and its concepts (tiered_task_sequence_concepts)
-- disjoint, and give topics an English label.
--
-- source = 'auto' rows were a copy of the sequence's concepts, written next to the concept links
-- at generation and re-classification time. Concepts are shown and filtered on their own, so
-- these rows only duplicated them under the topic heading. The application no longer writes
-- them; drop the existing ones and stop the source being valid.
--
-- topic_translations maps a canonical (German) tag slug to the English slug the semantic tagger
-- produced for it, so the library can show topics in the UI's language. It is keyed by name, not
-- by sequence, since a topic means the same thing on every sequence that carries it. Manual
-- topics have no entry and are shown as typed. Sequences tagged before this migration have none
-- either; the admin Bulk-Retagger's default filter picks them up.
--
-- Re-running is safe: every step is guarded.

DELETE FROM public.sequence_tags WHERE source = 'auto';

ALTER TABLE public.sequence_tags DROP CONSTRAINT IF EXISTS chk_sequence_tags_source;
ALTER TABLE public.sequence_tags
    ADD CONSTRAINT chk_sequence_tags_source CHECK (source IN ('llm', 'manual'));
ALTER TABLE public.sequence_tags ALTER COLUMN source SET DEFAULT 'manual';

CREATE TABLE IF NOT EXISTS public.topic_translations (
    tag_name   varchar(64) PRIMARY KEY,
    label_en   varchar(64) NOT NULL,
    created_at timestamp   NOT NULL DEFAULT now()
);
