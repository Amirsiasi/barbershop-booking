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

    public void changeStatus(AppointmentStatus newStatus) {
        this.status = newStatus;
    }

    public void markReminderSent(Instant when) {
        this.reminderSentAt = when;
    }

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
