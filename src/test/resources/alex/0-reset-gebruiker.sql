CREATE OR REPLACE PROCEDURE reset_gebruiker_data(IN gebruikerId bigint)
LANGUAGE plpgsql
AS $$
BEGIN
  DELETE FROM public.betaling WHERE gebruiker_id = gebruikerId;
  DELETE FROM public.saldo WHERE periode_id IN (
    SELECT id FROM public.periode WHERE gebruiker_id = gebruikerId
  );
  DELETE FROM public.saldo WHERE rekening_id IN (
    SELECT r.id FROM public.rekening r
    JOIN public.rekening_groep rg ON r.rekening_groep_id = rg.id
    WHERE rg.gebruiker_id = gebruikerId
  );
  DELETE FROM public.rekening_betaal_methoden WHERE rekening_id IN (
    SELECT r.id FROM public.rekening r
    JOIN public.rekening_groep rg ON r.rekening_groep_id = rg.id
    WHERE rg.gebruiker_id = gebruikerId
  );
  DELETE FROM public.rekening WHERE rekening_groep_id IN (
    SELECT id FROM public.rekening_groep WHERE gebruiker_id = gebruikerId
  );
  DELETE FROM public.rekening_groep WHERE gebruiker_id = gebruikerId;
END;
$$;
