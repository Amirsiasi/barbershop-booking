package at.barberbook.shop;

import jakarta.persistence.*;

import java.time.Instant;

/** A blocked period for one barber: break, holiday, doctor's appointment ... */
@Entity
@Table(name = "barber_absence")
public class BarberAbsence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long barberId;
    private Instant startsAt;
    private Instant endsAt;
    private String reason;

    protected BarberAbsence() {
    }

    public BarberAbsence(Long barberId, Instant startsAt, Instant endsAt, String reason) {
        this.barberId = barberId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Long getBarberId() { return barberId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getReason() { return reason; }
}
