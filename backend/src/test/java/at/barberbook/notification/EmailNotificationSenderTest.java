package at.barberbook.notification;

import at.barberbook.notification.NotificationSender.Message;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailNotificationSenderTest {

    private JavaMailSender mailSender;
    private EmailNotificationSender sender;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        sender = new EmailNotificationSender(mailSender, "termine@example.at", "shop@example.at");
    }

    private static Message message(String email, String ics) {
        return new Message("Max Muster", "+436601234567", email, "Mr. Artist Barbershop",
                "Dein Termin am Mittwoch", "Hallo Max, dein Termin ist bestätigt.", ics);
    }

    @Test
    void sends_confirmation_with_sender_name_and_calendar_attachment() throws Exception {
        sender.send(message("max@example.com", "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage mail = sent.getValue();
        mail.saveChanges();

        InternetAddress from = (InternetAddress) mail.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("termine@example.at");
        assertThat(from.getPersonal()).isEqualTo("Mr. Artist Barbershop");
        assertThat(mail.getAllRecipients()).extracting(Address::toString).containsExactly("max@example.com");
        assertThat(mail.getReplyTo()).extracting(Address::toString).containsExactly("shop@example.at");
        assertThat(mail.getSubject()).isEqualTo("Dein Termin am Mittwoch");

        Multipart parts = (Multipart) mail.getContent();
        boolean hasIcs = false;
        for (int i = 0; i < parts.getCount(); i++) {
            BodyPart part = parts.getBodyPart(i);
            if ("termin.ics".equals(part.getFileName())) hasIcs = true;
        }
        assertThat(hasIcs).isTrue();
    }

    @Test
    void customers_without_email_are_skipped() {
        sender.send(message(null, null));
        sender.send(message("  ", null));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void provider_errors_are_reported_to_the_caller() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(message("max@example.com", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max@example.com");
    }

    @Test
    void refuses_to_start_without_sender_address() {
        assertThatThrownBy(() -> new EmailNotificationSender(mailSender, "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_FROM");
    }
}
