package at.barberbook.staff;

import at.barberbook.common.Exceptions.ForbiddenException;
import at.barberbook.common.Exceptions.NotFoundException;
import at.barberbook.shop.Shop;
import at.barberbook.shop.ShopRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Who is logged in, and the checks that depend on it. Used by all admin controllers. */
@Component
public class CurrentStaff {

    private final ShopRepository shops;

    public CurrentStaff(ShopRepository shops) {
        this.shops = shops;
    }

    public StaffPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof StaffPrincipal p) return p;
        throw new ForbiddenException("Nicht angemeldet.");
    }

    /** The shop the logged-in person works for - every admin query is limited to it. */
    public Shop shop() {
        return shops.findById(get().shopId()).orElseThrow(() -> new NotFoundException("Shop nicht gefunden."));
    }

    public boolean isOwner() {
        return get().isOwner();
    }

    /** Owners may do it for everyone, barbers only for themselves. */
    public boolean mayActFor(Long barberId) {
        StaffPrincipal p = get();
        return p.isOwner() || (p.barberId() != null && Objects.equals(p.barberId(), barberId));
    }

    public void requireMayActFor(Long barberId, String message) {
        if (!mayActFor(barberId)) throw new ForbiddenException(message);
    }
}
