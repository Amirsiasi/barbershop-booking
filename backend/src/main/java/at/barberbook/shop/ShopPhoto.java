package at.barberbook.shop;

import jakarta.persistence.*;

import java.time.Instant;

/** The picture of the shop shown on the booking page. One per shop, kept apart from {@link Shop} on purpose. */
@Entity
@Table(name = "shop_photo")
public class ShopPhoto {

    @Id
    private Long shopId;

    private String contentType;

    @Column(name = "data")
    private byte[] data;

    private Instant updatedAt;

    protected ShopPhoto() {
    }

    public ShopPhoto(Long shopId) {
        this.shopId = shopId;
    }

    public void replace(String contentType, byte[] data, Instant now) {
        this.contentType = contentType;
        this.data = data;
        this.updatedAt = now;
    }

    public Long getShopId() { return shopId; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public Instant getUpdatedAt() { return updatedAt; }
}
