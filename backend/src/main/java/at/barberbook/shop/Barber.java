package at.barberbook.shop;

import jakarta.persistence.*;

@Entity
@Table(name = "barber")
public class Barber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long shopId;
    private String name;
    private String bio;
    private boolean active = true;
    private int sortOrder;

    protected Barber() {
    }

    public Barber(Long shopId, String name) {
        this.shopId = shopId;
        this.name = name;
    }

    public void update(String name, String bio, boolean active, int sortOrder) {
        this.name = name;
        this.bio = bio;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    /** Hidden from customers and the calendar, but kept so past appointments still show who did them. */
    public void deactivate() {
        this.active = false;
    }

    public Long getId() { return id; }
    public Long getShopId() { return shopId; }
    public String getName() { return name; }
    public String getBio() { return bio; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
}
