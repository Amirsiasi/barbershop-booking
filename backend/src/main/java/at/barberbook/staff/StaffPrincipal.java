package at.barberbook.staff;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The logged-in team member, available in every admin request.
 *
 * @param staffId  id of the staff_user row, or {@code null} for the emergency login from the environment
 * @param barberId the barber this login belongs to, or {@code null} (emergency login)
 */
public record StaffPrincipal(Long staffId, Long shopId, Long barberId, StaffRole role, String username,
                             String passwordHash, boolean active, boolean mustChangePassword)
        implements UserDetails {

    public boolean isOwner() {
        return role == StaffRole.OWNER;
    }

    public boolean isEmergencyLogin() {
        return staffId == null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isEnabled() { return active; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
