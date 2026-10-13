package at.barberbook.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Sends the booking confirmation in the background, so the customer does not wait
 * for the SMTP server (1-3 seconds) after clicking "Buchen". A failed e-mail never
 * fails the booking - the appointment is valid either way.
 */
@Component
public class AsyncNotifier {

    private static final Logger log = LoggerFactory.getLogger(AsyncNotifier.class);

    private final NotificationSender sender;

    public AsyncNotifier(NotificationSender sender) {
        this.sender = sender;
    }

    @Async
    public void send(NotificationSender.Message message) {
        try {
            sender.send(message);
        } catch (RuntimeException ex) {
            log.warn("Notification \"{}\" to {} failed: {}", message.subject(), message.toName(), ex.getMessage());
        }
    }
}
