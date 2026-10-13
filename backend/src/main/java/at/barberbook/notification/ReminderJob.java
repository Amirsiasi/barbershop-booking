package at.barberbook.notification;

import at.barberbook.booking.Appointment;
import at.barberbook.booking.AppointmentRepository;
import at.barberbook.booking.BookingProperties;
import at.barberbook.shop.Shop;
import at.barberbook.shop.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Every few minutes: send a reminder to everyone whose appointment is within the reminder window. */
@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final AppointmentRepository appointments;
    private final ShopRepository shops;
    private final NotificationSender sender;
    private final BookingMessages messages;
    private final BookingProperties props;
    private final Clock clock;

    public ReminderJob(AppointmentRepository appointments, ShopRepository shops, NotificationSender sender,
                       BookingMessages messages, BookingProperties props, Clock clock) {
        this.appointments = appointments;
        this.shops = shops;
        this.sender = sender;
        this.messages = messages;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
    @Transactional
    public void sendDueReminders() {
        Instant now = clock.instant();
        List<Appointment> due = appointments.findDueForReminder(now, now.plus(props.reminderBefore()));
        Map<Long, Shop> shopCache = new HashMap<>();
        for (Appointment a : due) {
            Shop shop = shopCache.computeIfAbsent(a.getShopId(), id -> shops.findById(id).orElseThrow());
            try {
                sender.send(messages.reminder(a, shop));
                a.markReminderSent(now);
            } catch (RuntimeException ex) {
                // one failing message must not stop the others; it is retried on the next run
                log.warn("Reminder for appointment {} failed", a.getId(), ex);
            }
        }
    }
}
