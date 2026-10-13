package at.barberbook.booking;

import at.barberbook.shop.Barber;
import at.barberbook.shop.ServiceOffering;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /** The first (main) service. The complete list is {@link #items}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private ServiceOffering service;

    // small list, needed almost everywhere an appointment is shown (also outside transactions) -> eager,
    // loaded in batches so a calendar day with 30 appointments costs one extra query, not 30
    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position")
    @org.hibernate.annotations.BatchSize(size = 100)
    private List<AppointmentItem> items = new ArrayList<>();

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
        this(shopId, barber, List.of(service), customer, startsAt, note, now);
    }

    /** One appointment for several services, done one after another by the same barber. */
    public Appointment(Long shopId, Barber barber, List<ServiceOffering> services, Customer customer,
                       Instant startsAt, String note, Instant now) {
        if (services.isEmpty()) throw new IllegalArgumentException("at least one service");
        this.shopId = shopId;
        this.barber = barber;
        this.service = services.get(0);
        this.customer = customer;
        this.startsAt = startsAt;
        for (int i = 0; i < services.size(); i++) items.add(new AppointmentItem(this, services.get(i), i));
        this.endsAt = startsAt.plusSeconds(totalMinutes(services) * 60L);
        this.priceCents = services.stream().mapToInt(ServiceOffering::getPriceCents).sum();
        this.note = note;
        this.manageToken = UUID.randomUUID();
        this.createdAt = now;
    }

    /** A request that reserves the slot but still needs the barber's confirmation (e.g. from WhatsApp). */
    public static Appointment request(Long shopId, Barber barber, List<ServiceOffering> services, Customer customer,
                                      Instant startsAt, String note, Instant now, BookingSource source,
                                      Instant pendingUntil) {
        Appointment a = new Appointment(shopId, barber, services, customer, startsAt, note, now);
        a.status = AppointmentStatus.PENDING;
        a.source = source;
        a.pendingUntil = pendingUntil;
        return a;
    }

    /** Entered by the team (phone call, walk-in) instead of booked online. */
    void markEnteredByStaff() {
        this.source = BookingSource.ADMIN;
    }

    public void changeStatus(AppointmentStatus newStatus) {
        this.status = newStatus;
        if (newStatus != AppointmentStatus.PENDING) this.pendingUntil = null;
    }

    public static int totalMinutes(List<ServiceOffering> services) {
        return services.stream().mapToInt(ServiceOffering::getDurationMinutes).sum();
    }

    /** "Haarschnitt + Bart trimmen" - for e-mails, chat messages and the admin calendar. */
    public String getServiceLabel() {
        if (items.isEmpty()) return service.getName();
        return items.stream().map(AppointmentItem::getName).collect(Collectors.joining(" + "));
    }

    public int getDurationMinutes() {
        return (int) java.time.Duration.between(startsAt, endsAt).toMinutes();
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
    public List<AppointmentItem> getItems() { return items; }
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
