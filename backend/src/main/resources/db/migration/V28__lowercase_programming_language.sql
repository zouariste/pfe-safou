-- ════════════════════════════════════════════════════════════════════
-- V28__lowercase_programming_language.sql
-- One spelling rule for programming languages: trimmed, lowercase
-- ════════════════════════════════════════════════════════════════════
--
-- SupportedLanguage has always declared lowercase as the canonical spelling,
-- and LLM-generated sequences are stored that way. The manual task creator,
-- though, takes the language as free text, so "Java" and "java" both reached
-- tiered_task_sequences.programming_language -- and the library's language
-- facet (GROUP BY programming_language) listed them as two entries.
--
-- PostgreSequenceDAO.save now normalises before writing; this folds the rows
-- written before that and adds a constraint as a safety net, as V14 did for
-- concept names. No uniqueness is involved, so lowercasing cannot collide.
--
-- VERIFY afterwards (must return 0 rows):
--   SELECT DISTINCT programming_language FROM tiered_task_sequences
--    WHERE programming_language <> lower(btrim(programming_language));
-- ════════════════════════════════════════════════════════════════════

UPDATE tiered_task_sequences
   SET programming_language = lower(btrim(programming_language))
 WHERE programming_language <> lower(btrim(programming_language));

ALTER TABLE tiered_task_sequences
    ADD CONSTRAINT tiered_task_sequences_programming_language_lowercase_check
    CHECK (programming_language = lower(btrim(programming_language)));

COMMENT ON CONSTRAINT tiered_task_sequences_programming_language_lowercase_check ON tiered_task_sequences IS
    'Programming languages are trimmed and lowercase. PostgreSequenceDAO normalises on insert; this catches anything that does not.';
