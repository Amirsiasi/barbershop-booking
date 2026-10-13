package at.barberbook.admin;

import at.barberbook.booking.Appointment;
import at.barberbook.booking.AppointmentRepository;
import at.barberbook.booking.AppointmentStatus;
import at.barberbook.common.Exceptions.BusinessRuleException;
import at.barberbook.common.Exceptions.NotFoundException;
import at.barberbook.shop.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Back office for the shop owner: day calendar, status changes, blocking time. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record Me(String username, String shopSlug, String shopName, String timeZone, List<BarberRef> barbers) {
    }

    public record BarberRef(Long id, String name) {
    }

    public record AdminAppointment(Long id, Instant start, Instant end, String status, Long barberId, String barberName,
                                   String serviceName, int priceCents, String customerName, String phone, String email,
                                   String note) {
    }

    public record AbsenceView(Long id, Long barberId, Instant start, Instant end, String reason) {
    }

    public record DayView(LocalDate date, List<AdminAppointment> appointments, List<AbsenceView> absences) {
    }

    public record StatusChange(@NotNull AppointmentStatus status) {
    }

    public record AbsenceRequest(@NotNull Long barberId, @NotNull Instant start, @NotNull Instant end,
                                 @Size(max = 120) String reason) {
    }

    private final AdminProperties props;
    private final ShopRepository shops;
    private final BarberRepository barbers;
    private final AppointmentRepository appointments;
    private final BarberAbsenceRepository absences;

    public AdminController(AdminProperties props, ShopRepository shops, BarberRepository barbers,
                           AppointmentRepository appointments, BarberAbsenceRepository absences) {
        this.props = props;
        this.shops = shops;
        this.barbers = barbers;
        this.appointments = appointments;
        this.absences = absences;
    }

    @GetMapping("/me")
    public Me me(Authentication auth) {
        Shop shop = shop();
        return new Me(auth.getName(), shop.getSlug(), shop.getName(), shop.getTimeZone(), barberRefs(shop));
    }

    @GetMapping("/day")
    @Transactional(readOnly = true)
    public DayView day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Shop shop = shop();
        ZoneId zone = shop.zone();
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();

        List<AdminAppointment> appts = appointments.findForShop(shop.getId(), from, to).stream()
                .map(AdminController::toAdmin).toList();
        List<Long> barberIds = barberRefs(shop).stream().map(BarberRef::id).toList();
        List<AbsenceView> abs = barberIds.isEmpty() ? List.of() : absences.findOverlapping(barberIds, from, to).stream()
                .map(a -> new AbsenceView(a.getId(), a.getBarberId(), a.getStartsAt(), a.getEndsAt(), a.getReason()))
                .toList();
        return new DayView(date, appts, abs);
    }

    @PatchMapping("/appointments/{id}")
    @Transactional
    public AdminAppointment changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChange change) {
        Appointment a = appointments.findById(id)
                .filter(x -> x.getShopId().equals(shop().getId()))
                .orElseThrow(() -> new NotFoundException("Termin nicht gefunden."));
        if (a.getStatus() == AppointmentStatus.CANCELLED && change.status().blocksTime()) {
            // re-activating could collide with a newer booking; the DB constraint would reject it anyway
            throw new BusinessRuleException("Ein stornierter Termin kann nicht reaktiviert werden. Bitte neu buchen.");
        }
        a.changeStatus(change.status());
        appointments.flush();
        return toAdmin(a);
    }

    @PostMapping("/absences")
    @Transactional
    public ResponseEntity<AbsenceView> block(@Valid @RequestBody AbsenceRequest req) {
        if (!req.end().isAfter(req.start())) throw new BusinessRuleException("Ende muss nach dem Beginn liegen.");
        barbers.findById(req.barberId())
                .filter(b -> b.getShopId().equals(shop().getId()))
                .orElseThrow(() -> new NotFoundException("Barber nicht gefunden."));
        BarberAbsence saved = absences.save(new BarberAbsence(req.barberId(), req.start(), req.end(), req.reason()));
        return ResponseEntity.status(201).body(new AbsenceView(saved.getId(), saved.getBarberId(),
                saved.getStartsAt(), saved.getEndsAt(), saved.getReason()));
    }

    @DeleteMapping("/absences/{id}")
    @Transactional
    public ResponseEntity<Void> unblock(@PathVariable Long id) {
        Long shopId = shop().getId();
        BarberAbsence a = absences.findById(id)
                .filter(x -> barbers.findById(x.getBarberId()).map(b -> b.getShopId().equals(shopId)).orElse(false))
                .orElseThrow(() -> new NotFoundException("Blocker nicht gefunden."));
        absences.delete(a);
        return ResponseEntity.noContent().build();
    }

    private Shop shop() {
        return shops.findBySlug(props.shopSlug()).orElseThrow(() -> new NotFoundException("Shop nicht gefunden."));
    }

    private List<BarberRef> barberRefs(Shop shop) {
        return barbers.findByShopIdAndActiveTrueOrderBySortOrder(shop.getId()).stream()
                .map(b -> new BarberRef(b.getId(), b.getName())).toList();
    }

    private static AdminAppointment toAdmin(Appointment a) {
        return new AdminAppointment(a.getId(), a.getStartsAt(), a.getEndsAt(), a.getStatus().name(),
                a.getBarber().getId(), a.getBarber().getName(), a.getService().getName(), a.getPriceCents(),
                a.getCustomer().fullName(), a.getCustomer().getPhone(), a.getCustomer().getEmail(), a.getNote());
    }
}
