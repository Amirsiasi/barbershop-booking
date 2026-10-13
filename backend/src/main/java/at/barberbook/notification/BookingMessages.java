package at.barberbook.notification;

import at.barberbook.booking.Appointment;
import at.barberbook.booking.BookingProperties;
import at.barberbook.notification.NotificationSender.Message;
import at.barberbook.shop.Shop;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** German message texts for confirmations and reminders, plus the calendar file. */
@Component
public class BookingMessages {

    private static final Locale DE_AT = Locale.forLanguageTag("de-AT");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN);
    private static final DateTimeFormatter ICS = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final BookingProperties props;

    public BookingMessages(BookingProperties props) {
        this.props = props;
    }

    public Message confirmation(Appointment a, Shop shop) {
        String text = """
                Hallo %s,

                danke für deine Buchung! Dein Termin ist bestätigt:

                  Wann:     %s, %s Uhr
                  Was:      %s (%d Min.)
                  Bei:      %s
                  Wo:       %s, %s
                  Preis:    %s, Bezahlung im Shop

                Du kannst den Termin bis %d Stunden vorher online absagen:
                %s

                Im Anhang findest du den Termin für deinen Kalender.

                Bis bald!
                %s""".formatted(
                a.getCustomer().getFirstName(),
                date(a.getStartsAt(), shop), time(a.getStartsAt(), shop),
                a.getService().getName(), a.getService().getDurationMinutes(),
                a.getBarber().getName(),
                shop.getStreet(), shop.getZipCity(),
                euro(a.getPriceCents()),
                props.cancelCutoff().toHours(),
                manageLink(a),
                shop.getName());
        return message(a, shop, "Dein Termin am " + date(a.getStartsAt(), shop) + " um " + time(a.getStartsAt(), shop) + " Uhr",
                text, ics(a, shop));
    }

    public Message reminder(Appointment a, Shop shop) {
        String text = """
                Hallo %s,

                kleine Erinnerung an deinen Termin:
                %s, %s Uhr – %s bei %s
                %s, %s

                Du kannst nicht? Bitte sag kurz ab, dann kann jemand anderer den Termin nehmen:
                %s

                Bis bald!
                %s""".formatted(
                a.getCustomer().getFirstName(),
                date(a.getStartsAt(), shop), time(a.getStartsAt(), shop), a.getService().getName(), a.getBarber().getName(),
                shop.getStreet(), shop.getZipCity(),
                manageLink(a),
                shop.getName());
        return message(a, shop, "Erinnerung: " + date(a.getStartsAt(), shop) + " um " + time(a.getStartsAt(), shop) + " Uhr",
                text, null);
    }

    /** iCalendar file, so the customer can add the appointment to Google/Apple/Outlook calendar. */
    String ics(Appointment a, Shop shop) {
        String location = shop.getName() + ", " + shop.getStreet() + ", " + shop.getZipCity();
        return String.join("\r\n",
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//BarberBook//DE",
                "METHOD:PUBLISH",
                "BEGIN:VEVENT",
                "UID:" + a.getManageToken() + "@barberbook",
                "DTSTAMP:" + ICS.format(Instant.now()),
                "DTSTART:" + ICS.format(a.getStartsAt()),
                "DTEND:" + ICS.format(a.getEndsAt()),
                "SUMMARY:" + escape(a.getService().getName() + " bei " + a.getBarber().getName()),
                "LOCATION:" + escape(location),
                "DESCRIPTION:" + escape("Termin verwalten: " + manageLink(a)),
                "BEGIN:VALARM",
                "TRIGGER:-PT2H",
                "ACTION:DISPLAY",
                "DESCRIPTION:" + escape("Termin bei " + shop.getName()),
                "END:VALARM",
                "END:VEVENT",
                "END:VCALENDAR",
                "");
    }

    private Message message(Appointment a, Shop shop, String subject, String text, String ics) {
        return new Message(a.getCustomer().fullName(), a.getCustomer().getPhone(), a.getCustomer().getEmail(),
                shop.getName(), subject, text, ics);
    }

    private String manageLink(Appointment a) {
        return props.publicBaseUrl() + "/termin/" + a.getManageToken();
    }

    private static String date(Instant i, Shop shop) {
        return DATE.format(i.atZone(shop.zone()));
    }

    private static String time(Instant i, Shop shop) {
        return TIME.format(i.atZone(shop.zone()));
    }

    private static String euro(int cents) {
        NumberFormat f = NumberFormat.getCurrencyInstance(DE_AT);
        if (cents % 100 == 0) f.setMaximumFractionDigits(0);
        return f.format(cents / 100.0);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n");
    }
}
