package at.barberbook.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Sends real e-mails via SMTP (Brevo, Mailjet, Gmail, ...).
 * Only created when SPRING_MAIL_HOST is set; then it replaces the logging sender.
 * Customers without an e-mail address are skipped (the phone number is for a future SMS sender).
 */
@Component
@Primary
// active only if SPRING_MAIL_HOST has a value (an empty variable, e.g. from docker-compose, counts as "off")
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${spring.mail.host:}')")
public class EmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String replyTo;

    public EmailNotificationSender(JavaMailSender mailSender,
                                   @Value("${app.mail.from}") String fromAddress,
                                   @Value("${app.mail.reply-to:}") String replyTo) {
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException("Set app.mail.from (env MAIL_FROM) to the sender address verified at your mail provider.");
        }
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.replyTo = replyTo;
        log.info("E-mail sending enabled, sender: {}", fromAddress);
    }

    @Override
    public void send(Message m) {
        if (m.email() == null || m.email().isBlank()) {
            log.info("No e-mail address for {}, skipping \"{}\"", m.toName(), m.subject());
            return;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            boolean multipart = m.calendarIcs() != null;
            MimeMessageHelper mail = new MimeMessageHelper(mime, multipart, StandardCharsets.UTF_8.name());
            mail.setFrom(fromAddress, m.senderName());
            if (!replyTo.isBlank()) mail.setReplyTo(replyTo);
            mail.setTo(m.email());
            mail.setSubject(m.subject());
            mail.setText(m.text(), false);
            if (multipart) {
                mail.addAttachment("termin.ics",
                        new ByteArrayResource(m.calendarIcs().getBytes(StandardCharsets.UTF_8)),
                        "text/calendar; charset=UTF-8; method=PUBLISH");
            }
            mailSender.send(mime);
            log.info("E-mail \"{}\" sent to {}", m.subject(), m.email());
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("Could not build e-mail", e);
        } catch (MailException e) {
            // rethrown so the caller can log it and the reminder job retries on its next run
            throw new IllegalStateException("Could not send e-mail to " + m.email() + ": " + e.getMessage(), e);
        }
    }
}
