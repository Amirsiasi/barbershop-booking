package at.barberbook.notification;

/**
 * Sends a message to a customer.
 *
 * <ul>
 *   <li>{@link LoggingNotificationSender}: default, only writes to the log (development).</li>
 *   <li>{@link EmailNotificationSender}: real e-mails via SMTP, active as soon as SPRING_MAIL_HOST is set.</li>
 * </ul>
 * An SMS sender (Twilio, ...) would be one more implementation of this interface.
 */
public interface NotificationSender {

    void send(Message message);

    /**
     * @param senderName  shown as the e-mail sender, e.g. "Mr. Artist Barbershop"
     * @param calendarIcs optional iCalendar file attached to the e-mail (null = no attachment)
     */
    record Message(String toName, String phone, String email, String senderName,
                   String subject, String text, String calendarIcs) {
    }
}
