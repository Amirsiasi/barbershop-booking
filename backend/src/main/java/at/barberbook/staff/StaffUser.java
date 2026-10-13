package at.barberbook.staff;

import jakarta.persistence.*;

import java.time.Instant;

/** A personal login of a team member. The password is only stored as a BCrypt hash. */
@Entity
@Table(name = "staff_user")
public class StaffUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long shopId;
    private Long barberId;
    private String username;
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private StaffRole role;

    private boolean active = true;
    private boolean mustChangePassword = true;
    private Instant createdAt;

    protected StaffUser() {
    }

    public StaffUser(Long shopId, Long barberId, String username, String passwordHash, StaffRole role, Instant now) {
        this.shopId = shopId;
        this.barberId = barberId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = now;
    }

    /** Set by the owner: the user has to choose an own password at the next login. */
    public void resetPassword(String newHash) {
        this.passwordHash = newHash;
        this.mustChangePassword = true;
    }

    /** Chosen by the user himself. */
    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.mustChangePassword = false;
    }

    public void setRole(StaffRole role) { this.role = role; }
    public void setActive(boolean active) { this.active = active; }

    public Long getId() { return id; }
    public Long getShopId() { return shopId; }
    public Long getBarberId() { return barberId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public StaffRole getRole() { return role; }
    public boolean isActive() { return active; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public Instant getCreatedAt() { return createdAt; }
}
