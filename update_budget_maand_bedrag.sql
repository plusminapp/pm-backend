-- SQL script to update budgetMaandBedrag = 0 where periode.periodeStartDatum = periode.periodeEindDatum
-- This targets periods where the start date equals the end date (single-day periods)

UPDATE saldo 
SET budget_maand_bedrag = 0 
WHERE periode_id IN (
    SELECT id 
    FROM periode 
    WHERE periode_start_datum = periode_eind_datum
);

-- Optional: Display affected records before running the update
-- Uncomment the following query to see which records would be affected:
/*
SELECT 
    s.id as saldo_id,
    s.budget_maand_bedrag,
    p.id as periode_id,
    p.periode_start_datum,
    p.periode_eind_datum,
    p.periode_status
FROM saldo s
JOIN periode p ON s.periode_id = p.id
WHERE p.periode_start_datum = p.periode_eind_datum;
*/
