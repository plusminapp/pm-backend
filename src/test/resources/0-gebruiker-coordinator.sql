-- Geef de 1ste gebruiker de rol ROLE_COORDINATOR om andere gebruikers toe te kunnen voegen
-- de (1ste) gebruiker wordt automatisch (zonder rol) aangemaakt bij het inloggen door die gebruiker

-- Selecteer het eerste record uit de gebruiker-tabel (op volgorde van id)
WITH eerste_gebruiker AS (
  SELECT id
  FROM public.gebruiker
  ORDER BY id
  LIMIT 1
)
-- Voeg een record toe aan gebruiker_roles met ROLE_COORDINATOR
INSERT INTO public.gebruiker_roles (gebruiker_id, roles)
SELECT id, 'ROLE_COORDINATOR'
FROM eerste_gebruiker;