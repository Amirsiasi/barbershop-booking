package at.barberbook.booking;

import at.barberbook.shop.Barber;
import at.barberbook.shop.ServiceOffering;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long shopId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barber_id")
    private Barber barber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private ServiceOffering service;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private Instant startsAt;
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    private AppointmentStatus status = AppointmentStatus.CONFIRMED;

    /** Secret token in the "manage your booking" link - customers need no account. */
    private UUID manageToken;

    private String note;
    private int priceCents;
    private Instant reminderSentAt;
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private BookingSource source = BookingSource.ONLINE;

    /** Only for PENDING requests: after this moment the slot is released if the barber has not confirmed. */
    private Instant pendingUntil;

    protected Appointment() {
    }

    public Appointment(Long shopId, Barber barber, ServiceOffering service, Customer customer,
                       Instant startsAt, String note, Instant now) {
        this.shopId = shopId;
        this.barber = barber;
        this.service = service;
        this.customer = customer;
        this.startsAt = startsAt;
        this.endsAt = startsAt.plusSeconds(service.getDurationMinutes() * 60L);
        this.priceCents = service.getPriceCents();
        this.note = note;
        this.manageToken = UUID.randomUUID();
        this.createdAt = now;
    }

    /** A request that reserves the slot but still needs the barber's confirmation (e.g. from WhatsApp). */
    public static Appointment request(Long shopId, Barber barber, ServiceOffering service, Customer customer,
                                      Instant startsAt, String note, Instant now, BookingSource source,
                                      Instant pendingUntil) {
        Appointment a = new Appointment(shopId, barber, service, customer, startsAt, note, now);
        a.status = AppointmentStatus.PENDING;
        a.source = source;
        a.pendingUntil = pendingUntil;
        return a;
    }

    public void changeStatus(AppointmentStatus newStatus) {
        this.status = newStatus;
        if (newStatus != AppointmentStatus.PENDING) this.pendingUntil = null;
    }

    public void markReminderSent(Instant when) {
        this.reminderSentAt = when;
    }

    public BookingSource getSource() { return source; }
    public Instant getPendingUntil() { return pendingUntil; }
    public Long getId() { return id; }
    public Long getShopId() { return shopId; }
    public Barber getBarber() { return barber; }
    public ServiceOffering getService() { return service; }
    public Customer getCustomer() { return customer; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public AppointmentStatus getStatus() { return status; }
    public UUID getManageToken() { return manageToken; }
    public String getNote() { return note; }
    public int getPriceCents() { return priceCents; }
    public Instant getReminderSentAt() { return reminderSentAt; }
    public Instant getCreatedAt() { return createdAt; }
}
