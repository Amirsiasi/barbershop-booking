package at.barberbook.shop;

import jakarta.persistence.*;

/** A bookable service such as "Haarschnitt & Bartschnitt" (named ServiceOffering to avoid clashing with @Service). */
@Entity
@Table(name = "service_offering")
public class ServiceOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long shopId;
    private String category;
    private String name;
    private String description;
    private int durationMinutes;
    private int priceCents;
    private boolean active = true;
    private int sortOrder;

    protected ServiceOffering() {
    }

    public ServiceOffering(Long shopId, String category, String name, int durationMinutes, int priceCents) {
        this.shopId = shopId;
        this.category = category;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.priceCents = priceCents;
    }

    public Long getId() { return id; }
    public Long getShopId() { return shopId; }
    public String getCategory() { return category; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getPriceCents() { return priceCents; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
}
