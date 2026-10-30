-- Reverses migration/V29__class_worksheet_position.sql. The order lives only in this
-- column, so dropping it loses nothing that existed before V29; reads go back to
-- ordering by worksheet name, as PostgreClassDao did beforehand.

ALTER TABLE public.class_worksheets DROP COLUMN IF EXISTS position;

DELETE FROM flyway_schema_history WHERE version='29';
