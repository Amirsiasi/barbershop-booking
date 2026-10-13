package at.barberbook.staff;

import at.barberbook.admin.AdminProperties;
import at.barberbook.shop.ShopRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Finds the person trying to log in: first the emergency owner login from the environment
 * (ADMIN_USERNAME / ADMIN_PASSWORD), then the personal logins in the database.
 */
@Service
public class StaffUserDetailsService implements UserDetailsService {

    private final AdminProperties props;
    private final ShopRepository shops;
    private final StaffUserRepository users;
    private final String emergencyHash;

    public StaffUserDetailsService(AdminProperties props, ShopRepository shops, StaffUserRepository users,
                                   PasswordEncoder encoder) {
        if (props.password() == null || props.password().isBlank()) {
            throw new IllegalStateException("Set app.admin.password (env ADMIN_PASSWORD) before starting the app.");
        }
        this.props = props;
        this.shops = shops;
        this.users = users;
        this.emergencyHash = encoder.encode(props.password());
    }

    @Override
    public StaffPrincipal loadUserByUsername(String username) {
        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);

        if (name.equals(props.username().toLowerCase(Locale.ROOT))) {
            Long shopId = shops.findBySlug(props.shopSlug())
                    .orElseThrow(() -> new UsernameNotFoundException("Shop of the emergency login not found"))
                    .getId();
            return new StaffPrincipal(null, shopId, null, StaffRole.OWNER, props.username(), emergencyHash, true, false);
        }

        StaffUser u = users.findByUsername(name).orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return new StaffPrincipal(u.getId(), u.getShopId(), u.getBarberId(), u.getRole(), u.getUsername(),
                u.getPasswordHash(), u.isActive(), u.isMustChangePassword());
    }

    /** Usernames must not collide with the emergency login. */
    public boolean isEmergencyUsername(String username) {
        return props.username().equalsIgnoreCase(username.trim());
    }
}
