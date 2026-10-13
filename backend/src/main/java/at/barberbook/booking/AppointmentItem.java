package at.barberbook.booking;

import at.barberbook.shop.ServiceOffering;
import jakarta.persistence.*;

/** One service inside an appointment, with name, duration and price frozen at booking time. */
@Entity
@Table(name = "appointment_item")
public class AppointmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    private Long serviceId;
    private int position;
    private String name;
    private int durationMinutes;
    private int priceCents;

    protected AppointmentItem() {
    }

    AppointmentItem(Appointment appointment, ServiceOffering service, int position) {
        this.appointment = appointment;
        this.serviceId = service.getId();
        this.position = position;
        this.name = service.getName();
        this.durationMinutes = service.getDurationMinutes();
        this.priceCents = service.getPriceCents();
    }

    public Long getId() { return id; }
    public Long getServiceId() { return serviceId; }
    public int getPosition() { return position; }
    public String getName() { return name; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getPriceCents() { return priceCents; }
}
