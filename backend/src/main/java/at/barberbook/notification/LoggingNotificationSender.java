package at.barberbook.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Development sender: writes the message to the log instead of sending it. */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(Message m) {
        log.info("""

                ---------- NACHRICHT (nur Log, kein echter Versand) ----------
                An:      {} | Tel. {} | E-Mail {}
                Betreff: {}
                {}
                -------------------------------------------------------------""",
                m.toName(), m.phone(), m.email() == null ? "-" : m.email(), m.subject(), m.text());
    }
}
