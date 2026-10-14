# BarberBook – Online-Terminbuchung für Barbershops

[![CI](https://github.com/Amirsiasi/barbershop-booking/actions/workflows/ci.yml/badge.svg)](https://github.com/Amirsiasi/barbershop-booking/actions/workflows/ci.yml)

Ein eigenes Buchungssystem statt teurer Plattform-Gebühren. Erster Kunde: **Mr. Artist Barbershop**, Ottakringer Straße 18, 1170 Wien.

Entstanden aus meinem Projekt [customer-crud-spring-react](https://github.com/Amirsiasi/customer-crud-spring-react): Gleicher Stack (Spring Boot + React), gleiche Fehlerbehandlung, aber mit echter Fachlogik.

![Startseite](docs/screenshots/hero.png)

## Was es kann

**Für Kunden** (ohne Konto)
- Eigene Buchungsseite `/buchen` in 4 Schritten mit Brotkrumen-Navigation: **Leistungen › Barber › Zeit › Bestätigen** (Zurück-Pfeil und Browser-Zurück funktionieren)
- **Mehrere Leistungen in einem Termin** (z. B. Schnitt + Bart): Dauer und Preis werden addiert, gesucht wird ein Zeitfenster für alles zusammen
- Kategorie-Leiste zum Springen, Leistungskarten mit +/✓, rechts eine mitlaufende Zusammenfassung mit Shop-Foto, Bewertung, Adresse und Gesamtpreis (am Handy als Leiste unten)
- Es werden nur wirklich freie Zeiten angezeigt (Öffnungszeiten, Dauer der Leistung, bestehende Termine, Pausen)
- Bestätigung mit Link zum Verwalten: Termin ansehen, in den Kalender speichern (.ics), bis 2 Stunden vorher selbst absagen
- Bestätigungs-E-Mail mit Kalender-Anhang (.ics) und Erinnerungs-E-Mail 24 Stunden vorher (weniger No-Shows)
- Funktioniert am Handy, Live-Status „Jetzt geöffnet bis 19:00 Uhr“

**Per WhatsApp** (Phase 1: Ablauf fertig, Test über den Simulator im Admin)
- Kunde schreibt z. B. „Amir 11:30 15.10“ oder „morgen 17 Uhr bei Sami“. Name, Tag, Uhrzeit, Barber und Leistung werden aus dem Text erkannt.
- Fehlt etwas, fragt das System nach (Leistung als nummerierte Liste, Uhrzeit, Name). Mehrere Leistungen gehen mit „1 und 4“.
- Ist die Zeit frei, wird der Termin als **Anfrage** reserviert, und niemand anderer kann ihn online buchen. Ist sie belegt, schlägt das System die nächsten freien Zeiten vor.
- Der Barber **bestätigt oder lehnt ab**, und der Kunde bekommt sofort eine Antwort. Unbestätigte Anfragen werden nach 2 Stunden automatisch freigegeben.

**Für den Shop** (`/admin`, persönliche Logins mit zwei Rollen)
- **Inhaber:** alles, inklusive Team, Logins und Preise. **Barber:** sieht den ganzen Kalender (zum Aushelfen), ändert aber nur eigene Termine, eigene Anfragen und eigene blockierte Zeiten. Die Regeln prüft das Backend bei jeder Anfrage.
- Der Inhaber legt im Tab Team pro Barber einen Login mit Startpasswort an. Beim ersten Login muss der Barber ein eigenes Passwort festlegen. Passwörter werden nur als BCrypt-Hash gespeichert.
- Wird ein Barber entfernt, wird sein Login automatisch gesperrt.
- Der Login aus der `.env` (`ADMIN_USERNAME` / `ADMIN_PASSWORD`) bleibt als Notfall-Zugang mit Inhaber-Rechten.
- Kalender mit Zeitraster: **Tag** (eine Spalte pro Barber), **3 Tage** und **Woche** (eine Spalte pro Tag), jeder Barber in einer eigenen Farbe, rote Linie für „jetzt“, geschlossene Zeiten schraffiert, Filter auf einzelne Barber
- **Termine selbst eintragen** (Anruf, Kunde im Shop): in eine freie Stelle klicken oder „Neu“ → „Termin eintragen“, auch mit mehreren Leistungen. Doppelbuchungen und blockierte Zeiten werden abgelehnt.
- Termine als „erschienen“, „nicht erschienen“ oder „storniert“ markieren, Telefonnummer direkt anrufen
- Zeit blockieren (Pause, Urlaub, Arzttermin) – in der Zeit kann niemand online buchen
- Kennzahlen des Tages: Termine, erwarteter Umsatz, Stornos
- **Anfragen:** offene WhatsApp-Anfragen mit Zähler im Menü, Bestätigen/Ablehnen, WhatsApp-Simulator mit allen Unterhaltungen
- **Team:** Barber hinzufügen, bearbeiten und entfernen. Mit kommenden Terminen geht Entfernen nicht (erst umbuchen oder stornieren), mit alten Terminen wird der Barber nur ausgeblendet und kann wieder aktiviert werden.
- **Leistungen & Preise:** Preise, Dauer, Namen und Beschreibungen ändern, neue Leistungen anlegen, Leistungen ausblenden. Bereits gebuchte Termine behalten ihren Preis.
- **Statistik** (nur Inhaber): Umsatz, Termine, Ø pro Termin, Neukunden, Storno- und No-Show-Quote, jeweils mit Vergleich zum Zeitraum davor. Diagramme pro **Woche** (12), **Monat** (12) oder **Jahr** (5), zurückblättern, Filter pro Barber, Umsatz pro Barber, beliebteste Leistungen, woher die Termine kommen (online, WhatsApp, eingetragen), alles auch als Tabelle.
- **Shop-Auftritt:** Instagram, WhatsApp, E-Mail und Telefon für den Footer eintragen (leere Felder werden nicht angezeigt). Foto vom Shop hochladen (wird im Browser verkleinert, nur JPG/PNG/WebP) und die Bewertung (z. B. aus Google) eintragen. Beides erscheint in der Zusammenfassung der Buchungsseite.

![Buchung](docs/screenshots/booking.png)
![Admin-Kalender](docs/screenshots/admin.png)

## Technik

| Teil | Technologie |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Data JPA, Spring Security, Bean Validation |
| Datenbank | PostgreSQL 16, Flyway-Migrationen |
| Frontend | React 18, Vite, React Router, eigenes CSS (keine UI-Bibliothek) |
| Betrieb | Docker Compose: Postgres + Backend + nginx (liefert Frontend aus und leitet `/api` weiter) |
| Tests | JUnit 5, AssertJ, MockMvc, Testcontainers |

### Die wichtigsten Entscheidungen

**Doppelbuchungen verhindert die Datenbank, nicht der Code.** In [`V1__schema.sql`](backend/src/main/resources/db/migration/V1__schema.sql) gibt es einen PostgreSQL *Exclusion Constraint*: Für denselben Barber dürfen sich zwei aktive Termine zeitlich nicht überschneiden. Auch wenn 20 Leute in derselben Millisekunde auf „Buchen“ drücken, bekommt genau einer den Termin – das prüft ein eigener Test. Bei „egal welcher Barber“ versucht das Backend dann automatisch den nächsten freien.

**Zeiten immer in UTC speichern.** Die Datenbank speichert `TIMESTAMPTZ`, die API liefert UTC. Umgerechnet auf `Europe/Vienna` wird nur bei der Berechnung der Slots und bei der Anzeige. Damit stimmt auch der Tag der Zeitumstellung.

**Slot-Berechnung ohne Datenbank testbar.** [`SlotCalculator`](backend/src/main/java/at/barberbook/booking/SlotCalculator.java) ist eine reine Funktion ohne Spring – daher einfache, schnelle Unit-Tests.

**Mehrere Shops von Anfang an.** Jede Tabelle hat eine `shop_id`. Mr. Artist ist Shop Nr. 1; ein zweiter Shop ist ein neuer Datensatz, kein neues Projekt.

**Benachrichtigungen austauschbar.** Hinter dem Interface `NotificationSender` stecken zwei Umsetzungen: `LoggingNotificationSender` (Standard, schreibt nur ins Log) und `EmailNotificationSender` (SMTP, wird automatisch aktiv, sobald `SPRING_MAIL_HOST` gesetzt ist). Die Bestätigung wird im Hintergrund verschickt (`@Async`), damit der Kunde nach dem Klick auf „Buchen“ nicht auf den Mailserver wartet. Ein Fehler beim Mailversand macht die Buchung nie ungültig. SMS wäre eine dritte Klasse – sonst ändert sich nichts.

## Starten

### Mit Docker (am einfachsten)

```bash
cp .env.example .env        # Passwörter darin ändern
docker compose up --build
```

- Buchungsseite: http://localhost:5173
- Admin: http://localhost:5173/admin (Zugangsdaten aus `.env`)

### Ohne Docker (zum Entwickeln)

PostgreSQL lokal anlegen:

```sql
CREATE ROLE barberbook LOGIN PASSWORD 'barberbook';
CREATE DATABASE barberbook OWNER barberbook;
```

Backend (Flyway legt Tabellen und Demo-Daten beim Start an):

```bash
cd backend
ADMIN_PASSWORD=geheim mvn spring-boot:run
```

Frontend (leitet `/api` an Port 8080 weiter):

```bash
cd frontend
npm install
npm run dev
```

### E-Mails verschicken

Ohne Einstellungen werden Bestätigungen und Erinnerungen nur ins Log geschrieben. Für echten Versand diese Umgebungsvariablen setzen (in IntelliJ unter *Environment variables*, bei Docker in `.env`):

| Variable | Brevo | Gmail (zum Testen) |
|---|---|---|
| `SPRING_MAIL_HOST` | `smtp-relay.brevo.com` | `smtp.gmail.com` |
| `SPRING_MAIL_PORT` | `587` | `587` |
| `SPRING_MAIL_USERNAME` | SMTP-Login aus Brevo → *SMTP & API* | deine Gmail-Adresse |
| `SPRING_MAIL_PASSWORD` | SMTP-Schlüssel aus Brevo | App-Passwort (braucht 2-Faktor-Anmeldung) |
| `MAIL_FROM` | bei Brevo bestätigte Absenderadresse | deine Gmail-Adresse |
| `MAIL_REPLY_TO` | optional, z. B. die Adresse des Shops | optional |

Im Log steht dann beim Start `E-mail sending enabled` und nach jeder Buchung `E-mail "…" sent to …`. Kunden ohne E-Mail-Adresse werden übersprungen.

Für den Livebetrieb: eine eigene Domain als Absender (z. B. `termine@mrartist.at`) mit SPF/DKIM-Einträgen beim Anbieter, sonst landen Mails oft im Spam.

### Tests

```bash
cd backend
mvn test
```

Die Integrationstests brauchen ein echtes PostgreSQL. Läuft Docker, startet Testcontainers es automatisch. Ohne Docker eine leere Test-Datenbank angeben:

```bash
TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/barberbook_test mvn test
```

Ohne beides werden nur die Unit-Tests ausgeführt.

Bei jedem Push auf GitHub laufen über **GitHub Actions** ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) automatisch alle Backend-Tests inklusive der Integrationstests gegen PostgreSQL, der Frontend-Build und der Build beider Docker-Images.

## API

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/api/shops/{slug}` | Shop, Öffnungszeiten, Barber, Preisliste |
| GET | `/api/shops/{slug}/photo` | Shop-Foto |
| GET | `/api/shops/{slug}/availability/days?serviceId=1&serviceId=4&barberId&from&days` | Freie Termine pro Tag (Datumsleiste), `serviceId` mehrfach für mehrere Leistungen |
| GET | `/api/shops/{slug}/availability?serviceId&barberId&date` | Freie Uhrzeiten an einem Tag |
| POST | `/api/shops/{slug}/appointments` | Termin buchen mit `serviceIds: [..]` (409 wenn gerade vergeben) |
| GET | `/api/appointments/{token}` | Termin über den Link ansehen |
| POST | `/api/appointments/{token}/cancel` | Termin absagen (422 wenn zu spät) |
| GET | `/api/admin/day?date` · `/api/admin/range?from&days` | Tagesansicht / bis zu 7 Tage (Login) |
| POST | `/api/admin/appointments` | Termin eintragen (Barber nur für sich selbst) |
| PATCH | `/api/admin/appointments/{id}` | Status ändern (Login) |
| POST / DELETE | `/api/admin/absences` | Zeit blockieren / freigeben (Login) |
| GET / POST | `/api/admin/services` | Preisliste inkl. ausgeblendeter Leistungen / neue Leistung (Login) |
| PUT | `/api/admin/services/{id}` | Preis, Dauer, Name ändern oder ausblenden (Login) |
| GET | `/api/admin/stats?unit=week\|month\|year&end&barberId` | Statistik (Inhaber) |
| GET · PUT `/rating` · PUT `/contact` · PUT/DELETE `/photo` | `/api/admin/shop` | Shop-Foto, Bewertung, Kontakt & Social Media (Inhaber) |
| GET | `/api/admin/me` · POST `/api/admin/me/password` | Eigenes Konto, Passwort ändern (Login) |
| GET / POST | `/api/admin/staff` | Logins des Teams / Login anlegen (Inhaber) |
| PUT / DELETE | `/api/admin/staff/{id}` · POST `/{id}/reset-password` | Rolle, sperren, löschen, Startpasswort neu setzen (Inhaber) |
| GET / POST | `/api/admin/barbers` | Team inkl. Ehemalige / Barber hinzufügen (Login) |
| PUT / DELETE | `/api/admin/barbers/{id}` | Barber bearbeiten / entfernen (Login) |
| GET | `/api/admin/requests` | Offene Anfragen (Login) |
| POST | `/api/admin/appointments/{id}/confirm` · `/reject` | Anfrage bestätigen / ablehnen, Kunde wird benachrichtigt (Login) |
| POST | `/api/admin/chat/simulate` | Chat-Nachricht wie von WhatsApp verarbeiten (Login) |
| GET | `/api/admin/chat/conversations` · `/messages?phone=` | Unterhaltungen lesen (Login) |

Fehler kommen immer im gleichen Format (`ApiError` mit `validationErrors` pro Feld) – übernommen aus customer-crud-spring-react.

## Projektstruktur

```
backend/src/main/java/at/barberbook/
  shop/          Shop, Öffnungszeiten, Barber, Leistungen, Abwesenheiten
  booking/       Kunden, Termine, SlotCalculator, AvailabilityService, BookingService
  admin/         Admin-API und Zugriffsregeln (Spring Security)
  staff/         Persönliche Logins, Rollen, aktueller Benutzer, Passwortwechsel-Pflicht
  notification/  Bestätigung und Erinnerung per E-Mail (ReminderJob alle 5 Minuten)
  chat/          WhatsApp-Anfragen: MessageParser, Gesprächsablauf, Ablauf unbestätigter Anfragen
  common/        ApiError, GlobalExceptionHandler
backend/src/main/resources/db/migration/
  V1__schema.sql            Tabellen + Exclusion Constraint
  V2__seed_mr_artist.sql    Daten von Mr. Artist
frontend/src/
  pages/         Buchung, Termin verwalten, Admin
  components/    Hero, Ticket, Shop-Infos
  lib/           API-Client, Zeitzonen-Helfer, .ics-Export
```

## Vor dem Livegang

- [ ] Preise, Dauer der Leistungen, Telefonnummer und Arbeitszeiten der Barber mit dem Shop abstimmen (Daten stammen aus dem öffentlichen Treatwell-Profil, Stand Oktober 2026). Die Gesichtsbehandlung fehlt noch, weil die Dauer unbekannt ist.
- [ ] Impressum und Datenschutzerklärung schreiben (`/impressum`, `/datenschutz` sind Platzhalter)
- [ ] E-Mail-Anbieter mit eigener Absender-Domain einrichten (SPF/DKIM); SMS optional als weiterer `NotificationSender`
- [ ] Hosting in der EU (DSGVO), HTTPS, Backups der Datenbank
- [ ] Starkes Notfall-Passwort in der `.env`; im Alltag die persönlichen Logins nutzen

## WhatsApp: Phase 2

Phase 1 enthält den kompletten Ablauf. Für echte WhatsApp-Nachrichten fehlen nur noch zwei kleine Teile:

1. **Webhook** `POST /api/webhooks/whatsapp`: nimmt Nachrichten von der WhatsApp Cloud API (Meta) entgegen und ruft `ChatBookingService.handleIncoming(...)` auf – dieselbe Methode wie der Simulator.
2. **`WhatsAppCloudGateway`**: eine weitere Umsetzung von `ChatGateway` mit `@Primary`, die Antworten über die Cloud API verschickt.

Voraussetzungen: Meta-Business-Konto, eine für die WhatsApp Business Platform registrierte Nummer und eine öffentliche HTTPS-Adresse für den Webhook.

## Ideen für später

- Warteliste: frei werdende Termine automatisch anbieten
- Arbeitszeiten pro Barber statt nur Shop-Öffnungszeiten
- Anzahlung bei Neukunden über Stripe (gegen No-Shows)
- Google-Bewertung nach dem Termin anfragen
- Self-Service-Registrierung für weitere Shops (SaaS)
