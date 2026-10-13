package at.barberbook.chat;

import jakarta.persistence.*;

import java.time.Instant;

/** One chat message, incoming (customer) or outgoing (shop). */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    public enum Direction { IN, OUT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long shopId;
    private String phone;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    private String text;
    private Instant createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(Long shopId, String phone, Direction direction, String text, Instant createdAt) {
        this.shopId = shopId;
        this.phone = phone;
        this.direction = direction;
        this.text = text;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getShopId() { return shopId; }
    public String getPhone() { return phone; }
    public Direction getDirection() { return direction; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
