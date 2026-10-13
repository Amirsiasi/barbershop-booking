package at.barberbook.shop;

import jakarta.persistence.*;

import java.time.LocalTime;

@Entity
@Table(name = "opening_hours")
public class OpeningHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long shopId;

    /** ISO day of week: 1 = Monday ... 7 = Sunday. */
    private int dayOfWeek;
    private LocalTime openTime;
    private LocalTime closeTime;

    protected OpeningHours() {
    }

    public OpeningHours(Long shopId, int dayOfWeek, LocalTime openTime, LocalTime closeTime) {
        this.shopId = shopId;
        this.dayOfWeek = dayOfWeek;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public Long getId() { return id; }
    public Long getShopId() { return shopId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public LocalTime getOpenTime() { return openTime; }
    public LocalTime getCloseTime() { return closeTime; }
}
