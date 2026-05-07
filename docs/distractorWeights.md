CREATE OR REPLACE VIEW public.vw_task_distractor_weights AS
SELECT 
    id AS task_id,
    -- Extrahiere den Typ aus dem JSON und weise Gewicht zu
    CASE (distractor_plan->>'type')
        WHEN 'LOGIC_FLIP' THEN 3.0
        WHEN 'OFF_BY_ONE' THEN 2.5
        WHEN 'VARIABLE_MISMATCH' THEN 2.0
        WHEN 'BOUNDARY_IDENTITY' THEN 2.0
        WHEN 'STRUCTURAL_ILLOGICAL' THEN 1.5
        WHEN 'REDUNDANT_ZOMBIE' THEN 1.0
        ELSE 0.0 -- Falls keine Distraktoren da sind
    END AS raw_distractor_weight
FROM public.tasks;