-- Vervang hieronder de waarde van gebruiker_id_var door het gewenste gebruiker-id
DO $$
DECLARE
  gebruiker_id_var bigint := :gebruiker_id; -- bijvoorbeeld 123
BEGIN
  -- 1. Verwijder betalingen van deze gebruiker
  DELETE FROM public.betaling WHERE gebruiker_id = gebruiker_id_var;

  -- 2. Verwijder saldo's die gekoppeld zijn aan periodes van deze gebruiker
  DELETE FROM public.saldo WHERE periode_id IN (
    SELECT id FROM public.periode WHERE gebruiker_id = gebruiker_id_var
  );

  -- 3. Verwijder saldo's die gekoppeld zijn aan rekeningen van deze gebruiker
  DELETE FROM public.saldo WHERE rekening_id IN (
    SELECT r.id FROM public.rekening r
    JOIN public.rekening_groep rg ON r.rekening_groep_id = rg.id
    WHERE rg.gebruiker_id = gebruiker_id_var
  );

  -- 4. Verwijder rekening_betaal_methoden van deze gebruiker
  DELETE FROM public.rekening_betaal_methoden WHERE rekening_id IN (
    SELECT r.id FROM public.rekening r
    JOIN public.rekening_groep rg ON r.rekening_groep_id = rg.id
    WHERE rg.gebruiker_id = gebruiker_id_var
  );

  -- 5. Verwijder rekeningen van deze gebruiker
  DELETE FROM public.rekening WHERE rekening_groep_id IN (
    SELECT id FROM public.rekening_groep WHERE gebruiker_id = gebruiker_id_var
  );

  -- 6. Verwijder rekeninggroepen van deze gebruiker
  DELETE FROM public.rekening_groep WHERE gebruiker_id = gebruiker_id_var;

  -- 7. Verwijder periodes van deze gebruiker
  DELETE FROM public.periode WHERE gebruiker_id = gebruiker_id_var;

  -- 8. Verwijder demo's van deze gebruiker
  DELETE FROM public.demo WHERE gebruiker_id = gebruiker_id_var;

  -- 9. Verwijder gebruiker_roles van deze gebruiker
  DELETE FROM public.gebruiker_roles WHERE gebruiker_id = gebruiker_id_var;

  -- 10. Verwijder gebruiker zelf
  DELETE FROM public.gebruiker WHERE id = gebruiker_id_var;
END $$;