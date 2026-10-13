package at.barberbook.notification;

/**
 * Sends a message to a customer. The default implementation only logs.
 * To go live, add e.g. a BrevoEmailSender or TwilioSmsSender implementing this
 * interface and annotate it with {@code @Primary} - nothing else has to change.
 */
public interface NotificationSender {

    void send(Message message);

    record Message(String toName, String phone, String email, String subject, String text) {
    }
}
