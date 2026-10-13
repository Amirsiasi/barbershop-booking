package at.barberbook.notification;

import at.barberbook.booking.Appointment;
import at.barberbook.booking.BookingProperties;
import at.barberbook.notification.NotificationSender.Message;
import at.barberbook.shop.Shop;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** German message texts for confirmations and reminders. */
@Component
public class BookingMessages {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN);

    private final BookingProperties props;

    public BookingMessages(BookingProperties props) {
        this.props = props;
    }

    public Message confirmation(Appointment a, Shop shop) {
        String text = """
                Hallo %s,

                dein Termin ist bestätigt:
                %s um %s Uhr – %s bei %s
                %s, %s

                Termin verschieben oder absagen: %s

                Bis bald!
                %s""".formatted(
                a.getCustomer().getFirstName(),
                date(a, shop), time(a, shop), a.getService().getName(), a.getBarber().getName(),
                shop.getStreet(), shop.getZipCity(),
                manageLink(a),
                shop.getName());
        return message(a, "Terminbestätigung – " + shop.getName(), text);
    }

    public Message reminder(Appointment a, Shop shop) {
        String text = """
                Hallo %s, kleine Erinnerung an deinen Termin am %s um %s Uhr, %s bei %s im %s.
                Du kannst nicht? Bitte sag kurz ab: %s""".formatted(
                a.getCustomer().getFirstName(),
                date(a, shop), time(a, shop), a.getService().getName(), a.getBarber().getName(), shop.getName(),
                manageLink(a));
        return message(a, "Erinnerung an deinen Termin", text);
    }

    private Message message(Appointment a, String subject, String text) {
        return new Message(a.getCustomer().fullName(), a.getCustomer().getPhone(), a.getCustomer().getEmail(), subject, text);
    }

    private String manageLink(Appointment a) {
        return props.publicBaseUrl() + "/termin/" + a.getManageToken();
    }

    private static String date(Appointment a, Shop shop) {
        return DATE.format(a.getStartsAt().atZone(shop.zone()));
    }

    private static String time(Appointment a, Shop shop) {
        return TIME.format(a.getStartsAt().atZone(shop.zone()));
    }
}
