package at.barberbook.shop;

import at.barberbook.common.Exceptions.NotFoundException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/** Public shop page data: info, opening hours, barbers and price list - everything the booking page needs in one call. */
@RestController
@RequestMapping("/api/shops")
public class ShopController {

    public record HoursView(int dayOfWeek, LocalTime open, LocalTime close) {
    }

    public record BarberView(Long id, String name, String bio) {
    }

    public record ServiceView(Long id, String category, String name, String description, int durationMinutes, int priceCents) {
    }

    public record ShopView(String slug, String name, String tagline, String street, String zipCity, String phone,
                           String email, String timeZone, List<String> highlights, List<HoursView> openingHours,
                           List<BarberView> barbers, List<ServiceView> services) {
    }

    private final ShopRepository shops;
    private final OpeningHoursRepository hours;
    private final BarberRepository barbers;
    private final ServiceOfferingRepository services;

    public ShopController(ShopRepository shops, OpeningHoursRepository hours, BarberRepository barbers,
                          ServiceOfferingRepository services) {
        this.shops = shops;
        this.hours = hours;
        this.barbers = barbers;
        this.services = services;
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    public ResponseEntity<ShopView> shop(@PathVariable String slug) {
        Shop s = shops.findBySlug(slug).orElseThrow(() -> new NotFoundException("Shop nicht gefunden."));
        ShopView view = new ShopView(s.getSlug(), s.getName(), s.getTagline(), s.getStreet(), s.getZipCity(),
                s.getPhone(), s.getEmail(), s.getTimeZone(), s.highlightList(),
                hours.findByShopIdOrderByDayOfWeek(s.getId()).stream()
                        .map(h -> new HoursView(h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime())).toList(),
                barbers.findByShopIdAndActiveTrueOrderBySortOrder(s.getId()).stream()
                        .map(b -> new BarberView(b.getId(), b.getName(), b.getBio())).toList(),
                services.findByShopIdAndActiveTrueOrderBySortOrder(s.getId()).stream()
                        .map(v -> new ServiceView(v.getId(), v.getCategory(), v.getName(), v.getDescription(),
                                v.getDurationMinutes(), v.getPriceCents())).toList());
        // short browser cache: price changes from the admin area show up within half a minute
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic()).body(view);
    }
}
