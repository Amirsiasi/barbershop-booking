package at.barberbook.booking;

import at.barberbook.booking.AvailabilityService.DaySummary;
import at.barberbook.booking.BookingDtos.AppointmentView;
import at.barberbook.booking.BookingDtos.AvailabilityView;
import at.barberbook.booking.BookingDtos.BookingRequest;
import at.barberbook.booking.BookingDtos.SlotView;
import at.barberbook.common.Exceptions.NotFoundException;
import at.barberbook.shop.ServiceOffering;
import at.barberbook.shop.ServiceOfferingRepository;
import at.barberbook.shop.Shop;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Public API used by the booking website. No login: customers identify themselves via the manage link. */
@RestController
@RequestMapping("/api")
@Validated
public class BookingController {

    private final BookingService booking;
    private final AvailabilityService availability;
    private final ServiceOfferingRepository services;

    public BookingController(BookingService booking, AvailabilityService availability, ServiceOfferingRepository services) {
        this.booking = booking;
        this.availability = availability;
        this.services = services;
    }

    /** Free start times for one day. */
    @GetMapping("/shops/{slug}/availability")
    public AvailabilityView availability(@PathVariable String slug,
                                         @RequestParam Long serviceId,
                                         @RequestParam(required = false) Long barberId,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Shop shop = booking.shop(slug);
        List<SlotView> slots = availability.slots(shop, service(shop, serviceId), barberId, date).stream()
                .map(s -> new SlotView(s.start(), s.end(), s.barberIds()))
                .toList();
        return new AvailabilityView(date, slots);
    }

    /** Overview of the next days (open? how many free slots?) for the date picker. */
    @GetMapping("/shops/{slug}/availability/days")
    public List<DaySummary> days(@PathVariable String slug,
                                 @RequestParam Long serviceId,
                                 @RequestParam(required = false) Long barberId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(defaultValue = "14") @Min(1) @Max(31) int days) {
        Shop shop = booking.shop(slug);
        return availability.days(shop, service(shop, serviceId), barberId, from, days);
    }

    @PostMapping("/shops/{slug}/appointments")
    public ResponseEntity<AppointmentView> book(@PathVariable String slug, @Valid @RequestBody BookingRequest req) {
        AppointmentView created = booking.book(slug, req);
        return ResponseEntity.created(URI.create("/api/appointments/" + created.manageToken())).body(created);
    }

    @GetMapping("/appointments/{token}")
    public AppointmentView appointment(@PathVariable UUID token) {
        return booking.findByToken(token);
    }

    @PostMapping("/appointments/{token}/cancel")
    public AppointmentView cancel(@PathVariable UUID token) {
        return booking.cancel(token);
    }

    private ServiceOffering service(Shop shop, Long serviceId) {
        return services.findById(serviceId)
                .filter(s -> s.getShopId().equals(shop.getId()) && s.isActive())
                .orElseThrow(() -> new NotFoundException("Leistung nicht gefunden."));
    }
}
