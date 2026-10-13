-- Demo data: Mr. Artist Barbershop, Ottakringer Straße 18, 1170 Wien.
-- Taken from the shop's public Treatwell listing (October 2026).
-- Before going live: confirm prices, durations, phone and e-mail with the owner.

INSERT INTO shop (slug, name, tagline, street, zip_city, time_zone, highlights)
VALUES ('mr-artist', 'Mr. Artist Barbershop',
        'Haarschnitte und Bartpflege in Hernals – entspannt, freundlich, präzise.',
        'Ottakringer Straße 18', '1170 Wien', 'Europe/Vienna',
        E'Kopfmassage bei jeder Behandlung\nGratis Getränke & WLAN\nWir sprechen Deutsch, Französisch und Arabisch\nHaustiere willkommen\nBezahlung im Shop – nur bar\nStraßenbahn: Hernalser Gürtel, wenige Schritte');

INSERT INTO opening_hours (shop_id, day_of_week, open_time, close_time)
SELECT s.id, d.dow, d.open_t, d.close_t
FROM shop s,
     (VALUES (1, TIME '10:00', TIME '19:00'),
             (2, TIME '10:00', TIME '19:00'),
             (3, TIME '10:00', TIME '19:00'),
             (4, TIME '10:00', TIME '19:00'),
             (5, TIME '10:00', TIME '19:00'),
             (6, TIME '10:00', TIME '18:00')) AS d (dow, open_t, close_t)
WHERE s.slug = 'mr-artist';

INSERT INTO barber (shop_id, name, bio, sort_order)
SELECT s.id, b.name, b.bio, b.sort_order
FROM shop s,
     (VALUES ('Reza', 'Klassische Schnitte und Bartpflege – über 300 Fünf-Sterne-Bewertungen.', 1),
             ('Sami', 'Moderne Herrenschnitte, Fades, Bartstyling und kurze Damenschnitte.', 2)) AS b (name, bio, sort_order)
WHERE s.slug = 'mr-artist';

INSERT INTO service_offering (shop_id, category, name, description, duration_minutes, price_cents, sort_order)
SELECT s.id, v.category, v.name, v.description, v.duration, v.price, v.sort_order
FROM shop s,
     (VALUES ('Herren', 'Schneiden & Föhnen', 'Der Klassiker – Schnitt nach Wunsch, fertig gestylt.', 35, 2800, 1),
             ('Herren', 'Waschen, Schneiden & Föhnen – kurze Haare', 'Inklusive Haarwäsche.', 40, 3100, 2),
             ('Herren', 'Waschen, Schneiden & Föhnen – lange Haare', 'Inklusive Haarwäsche.', 50, 3300, 3),
             ('Herren', 'Bart kürzen & stylen', 'Konturen, Länge und Pflege für deinen Bart.', 30, 1900, 4),
             ('Herren', 'Haarschnitt & Bartschnitt', 'Kopf und Bart in einem Termin.', 70, 4700, 5),
             ('Herren', 'Voll-Service', 'Das komplette Programm – Haare, Bart und Pflege.', 90, 5500, 6),
             ('Kinder & Jugendliche', 'Haarschnitt bis 16 Jahre', NULL, 35, 2400, 7),
             ('Gesicht', 'Augenbrauen zupfen', NULL, 10, 1000, 8)) AS v (category, name, description, duration, price, sort_order)
WHERE s.slug = 'mr-artist';
