CREATE OR REPLACE PROCEDURE delete_administratie(IN administratieId bigint)
LANGUAGE plpgsql
AS $$
BEGIN
  DELETE FROM public.betaling WHERE administratie_id = administratieId;
  DELETE FROM public.saldo WHERE periode_id IN (
    SELECT id FROM public.periode WHERE administratie_id = administratieId
  );
  DELETE FROM public.saldo WHERE rekening_id IN (
    SELECT r.id FROM public.rekening r
    JOIN public.rekening_groep rg ON r.rekening_groep_id = rg.id
    WHERE rg.administratie_id = administratieId
  );
  DELETE FROM public.rekening_betaal_methoden WHERE rekening_id IN (
    SELECT r.id FROM public.rekening r
    JOIN public.rekening_groep rg ON r.rekening_groep_id = rg.id
    WHERE rg.administratie_id = administratieId
  );
  DELETE FROM public.rekening WHERE rekening_groep_id IN (
    SELECT id FROM public.rekening_groep WHERE administratie_id = administratieId
  );
  DELETE FROM public.rekening_groep WHERE administratie_id = administratieId;
  DELETE FROM public.gebruiker_administratie WHERE administratie_id = administratieId;
  DELETE FROM public.periode WHERE administratie_id = administratieId;
  DELETE FROM public.administratie WHERE id = administratieId;
END;
$$;

-- CALL delete_administratie(440);