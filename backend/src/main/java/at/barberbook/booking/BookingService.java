package at.barberbook.booking;

import at.barberbook.booking.BookingDtos.AppointmentView;
import at.barberbook.booking.BookingDtos.BookingRequest;
import at.barberbook.booking.SlotCalculator.Slot;
import at.barberbook.common.Exceptions.BusinessRuleException;
import at.barberbook.common.Exceptions.ConflictException;
import at.barberbook.common.Exceptions.NotFoundException;
import at.barberbook.notification.BookingMessages;
import at.barberbook.notification.NotificationSender;
import at.barberbook.shop.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final String SLOT_TAKEN = "Dieser Termin wurde gerade vergeben. Bitte wähle eine andere Uhrzeit.";

    private final ShopRepository shops;
    private final BarberRepository barbers;
    private final ServiceOfferingRepository services;
    private final CustomerRepository customers;
    private final AppointmentRepository appointments;
    private final AvailabilityService availability;
    private final NotificationSender sender;
    private final BookingMessages messages;
    private final BookingProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    public BookingService(ShopRepository shops, BarberRepository barbers, ServiceOfferingRepository services,
                          CustomerRepository customers, AppointmentRepository appointments,
                          AvailabilityService availability, NotificationSender sender, BookingMessages messages,
                          BookingProperties props, Clock clock, PlatformTransactionManager txManager) {
        this.shops = shops;
        this.barbers = barbers;
        this.services = services;
        this.customers = customers;
        this.appointments = appointments;
        this.availability = availability;
        this.sender = sender;
        this.messages = messages;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Books an appointment.
     *
     * <ol>
     *   <li>Re-check availability on the server (never trust the client).</li>
     *   <li>Try to insert for each free barber. If two people book the same slot at the same time,
     *       the PostgreSQL exclusion constraint rejects the second insert; with "any barber" we then
     *       simply try the next free one.</li>
     * </ol>
     */
    public AppointmentView book(String shopSlug, BookingRequest req) {
        Shop shop = shop(shopSlug);
        ServiceOffering service = services.findById(req.serviceId())
                .filter(s -> s.getShopId().equals(shop.getId()) && s.isActive())
                .orElseThrow(() -> new NotFoundException("Leistung nicht gefunden."));

        LocalDate day = req.start().atZone(shop.zone()).toLocalDate();
        Slot slot = availability.slots(shop, service, req.barberId(), day).stream()
                .filter(s -> s.start().equals(req.start()))
                .findFirst()
                .orElseThrow(() -> new ConflictException(SLOT_TAKEN));

        String phone = normalizePhone(req.phone());
        List<Long> candidates = req.barberId() != null ? List.of(req.barberId()) : slot.barberIds();

        for (Long barberId : candidates) {
            try {
                Appointment saved = tx.execute(status -> {
                    Customer customer = customers.findByShopIdAndPhone(shop.getId(), phone)
                            .orElseGet(() -> new Customer(shop.getId(), req.firstName().trim(), req.lastName().trim(), phone, blankToNull(req.email())));
                    customer.updateContact(req.firstName().trim(), req.lastName().trim(), blankToNull(req.email()));
                    customers.save(customer);

                    Barber barber = barbers.getReferenceById(barberId);
                    Appointment a = new Appointment(shop.getId(), barber, service, customer, req.start(),
                            blankToNull(req.note()), clock.instant());
                    return appointments.saveAndFlush(a);
                });
                Appointment full = appointments.findByToken(saved.getManageToken()).orElseThrow();
                sendSafely(messages.confirmation(full, shop));
                log.info("Booked appointment {} for barber {} at {}", full.getId(), barberId, full.getStartsAt());
                return toView(full, shop);
            } catch (DataIntegrityViolationException ex) {
                log.info("Slot {} for barber {} was taken concurrently, trying next", req.start(), barberId);
            }
        }
        throw new ConflictException(SLOT_TAKEN);
    }

    public AppointmentView findByToken(UUID token) {
        Appointment a = appointments.findByToken(token).orElseThrow(() -> new NotFoundException("Termin nicht gefunden."));
        return toView(a, shops.findById(a.getShopId()).orElseThrow());
    }

    public AppointmentView cancel(UUID token) {
        AppointmentView result = tx.execute(status -> {
            Appointment a = appointments.findByToken(token).orElseThrow(() -> new NotFoundException("Termin nicht gefunden."));
            Shop shop = shops.findById(a.getShopId()).orElseThrow();
            if (a.getStatus() == AppointmentStatus.CANCELLED) return toView(a, shop); // idempotent
            if (a.getStatus() != AppointmentStatus.CONFIRMED) {
                throw new BusinessRuleException("Dieser Termin kann nicht mehr storniert werden.");
            }
            if (!clock.instant().isBefore(cancelDeadline(a))) {
                throw new BusinessRuleException("Online-Stornierung ist nur bis "
                        + props.cancelCutoff().toHours() + " Stunden vor dem Termin möglich. Bitte ruf im Shop an.");
            }
            a.changeStatus(AppointmentStatus.CANCELLED);
            return toView(a, shop);
        });
        log.info("Appointment {} cancelled by customer", result.id());
        return result;
    }

    // ---- helpers -------------------------------------------------------------------------

    Shop shop(String slug) {
        return shops.findBySlug(slug).orElseThrow(() -> new NotFoundException("Shop nicht gefunden."));
    }

    private Instant cancelDeadline(Appointment a) {
        return a.getStartsAt().minus(props.cancelCutoff());
    }

    private AppointmentView toView(Appointment a, Shop shop) {
        Instant deadline = cancelDeadline(a);
        boolean cancellable = a.getStatus() == AppointmentStatus.CONFIRMED && clock.instant().isBefore(deadline);
        return new AppointmentView(a.getId(), a.getManageToken(), a.getStatus().name(), a.getStartsAt(), a.getEndsAt(),
                a.getService().getName(), a.getService().getDurationMinutes(), a.getPriceCents(),
                a.getBarber().getName(), a.getCustomer().getFirstName(),
                shop.getName(), shop.getStreet() + ", " + shop.getZipCity(), shop.getTimeZone(),
                cancellable, deadline);
    }

    private void sendSafely(NotificationSender.Message m) {
        try {
            sender.send(m);
        } catch (RuntimeException ex) {
            // the booking is valid even if the e-mail/SMS provider is down
            log.warn("Could not send confirmation", ex);
        }
    }

    /** "0660 123 45 67" -> "+436601234567", "0049 ..." -> "+49...". Austria is the default country. */
    static String normalizePhone(String raw) {
        String p = raw.replaceAll("[^+0-9]", "");
        if (p.startsWith("00")) p = "+" + p.substring(2);
        else if (p.startsWith("0")) p = "+43" + p.substring(1);
        else if (!p.startsWith("+")) p = "+43" + p;
        return p;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
