package at.barberbook.chat;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** What we already know about a chat partner's booking wish while we ask follow-up questions. */
@Entity
@Table(name = "chat_session")
public class ChatSession {

    public enum State {
        IDLE,
        AWAITING_DATETIME,
        AWAITING_SERVICE,
        AWAITING_NAME,
        AWAITING_TIME
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long shopId;
    private String phone;

    @Enumerated(EnumType.STRING)
    private State state = State.IDLE;

    private String customerName;
    private LocalDate requestedDate;
    private LocalTime requestedTime;
    private Long serviceId;
    /** Further services after the first one, comma separated ("4,7"). */
    private String extraServiceIds;
    private Long barberId;
    private Instant updatedAt;

    protected ChatSession() {
    }

    public ChatSession(Long shopId, String phone, Instant now) {
        this.shopId = shopId;
        this.phone = phone;
        this.updatedAt = now;
    }

    /** Forget the booking wish but keep the name - the person is still the same. */
    public void reset() {
        state = State.IDLE;
        requestedDate = null;
        requestedTime = null;
        serviceId = null;
        extraServiceIds = null;
        barberId = null;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getShopId() { return shopId; }
    public String getPhone() { return phone; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public LocalDate getRequestedDate() { return requestedDate; }
    public void setRequestedDate(LocalDate requestedDate) { this.requestedDate = requestedDate; }
    public LocalTime getRequestedTime() { return requestedTime; }
    public void setRequestedTime(LocalTime requestedTime) { this.requestedTime = requestedTime; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
        this.extraServiceIds = null;
    }

    /** All chosen services in order; empty if none yet. */
    public java.util.List<Long> getServiceIds() {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        if (serviceId == null) return ids;
        ids.add(serviceId);
        if (extraServiceIds != null && !extraServiceIds.isBlank()) {
            for (String part : extraServiceIds.split(",")) ids.add(Long.valueOf(part.trim()));
        }
        return ids;
    }

    public void setServiceIds(java.util.List<Long> ids) {
        this.serviceId = ids.isEmpty() ? null : ids.get(0);
        this.extraServiceIds = ids.size() > 1
                ? ids.subList(1, ids.size()).stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))
                : null;
    }
    public Long getBarberId() { return barberId; }
    public void setBarberId(Long barberId) { this.barberId = barberId; }
    public Instant getUpdatedAt() { return updatedAt; }
}
