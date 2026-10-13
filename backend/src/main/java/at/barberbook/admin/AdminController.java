package at.barberbook.admin;

import at.barberbook.booking.Appointment;
import at.barberbook.booking.AppointmentRepository;
import at.barberbook.booking.AppointmentStatus;
import at.barberbook.chat.ChatBookingService;
import at.barberbook.common.Exceptions.BusinessRuleException;
import at.barberbook.common.Exceptions.NotFoundException;
import at.barberbook.shop.*;
import at.barberbook.staff.CurrentStaff;
import at.barberbook.staff.StaffPrincipal;
import at.barberbook.staff.StaffUser;
import at.barberbook.staff.StaffUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Back office for the team: day calendar, status changes, requests, blocked times, own account.
 * Everyone sees the whole calendar (to help each other out); barbers change only their own appointments.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record Me(String username, String role, Long barberId, String barberName, boolean mustChangePassword,
                     boolean emergencyLogin, String shopSlug, String shopName, String timeZone, List<BarberRef> barbers) {
    }

    public record BarberRef(Long id, String name) {
    }

    public record AdminAppointment(Long id, Instant start, Instant end, String status, Long barberId, String barberName,
                                   String serviceName, int priceCents, String customerName, String phone, String email,
                                   String note, String source, Instant pendingUntil) {
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

    public record PasswordChange(
            @NotBlank(message = "Bitte das aktuelle Passwort eingeben.") String currentPassword,
            @NotBlank(message = "Bitte ein neues Passwort eingeben.")
            @Size(min = 8, max = 72, message = "Das neue Passwort braucht mindestens 8 Zeichen.") String newPassword) {
    }

    private static final String ONLY_OWN = "Das kann nur der zuständige Barber oder der Inhaber.";

    private final CurrentStaff current;
    private final BarberRepository barbers;
    private final AppointmentRepository appointments;
    private final BarberAbsenceRepository absences;
    private final ChatBookingService chat;
    private final StaffUserRepository staffUsers;
    private final PasswordEncoder encoder;

    public AdminController(CurrentStaff current, BarberRepository barbers, AppointmentRepository appointments,
                           BarberAbsenceRepository absences, ChatBookingService chat,
                           StaffUserRepository staffUsers, PasswordEncoder encoder) {
        this.current = current;
        this.barbers = barbers;
        this.appointments = appointments;
        this.absences = absences;
        this.chat = chat;
        this.staffUsers = staffUsers;
        this.encoder = encoder;
    }

    // ---- own account ---------------------------------------------------------------------------

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public Me me() {
        StaffPrincipal p = current.get();
        Shop shop = current.shop();
        String barberName = p.barberId() == null ? null : barbers.findById(p.barberId()).map(Barber::getName).orElse(null);
        return new Me(p.username(), p.role().name(), p.barberId(), barberName, p.mustChangePassword(),
                p.isEmergencyLogin(), shop.getSlug(), shop.getName(), shop.getTimeZone(), barberRefs(shop));
    }

    @PostMapping("/me/password")
    @Transactional
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChange req) {
        StaffPrincipal p = current.get();
        if (p.isEmergencyLogin()) {
            throw new BusinessRuleException("Das Passwort des Notfall-Zugangs wird in der .env (ADMIN_PASSWORD) geändert.");
        }
        StaffUser user = staffUsers.findById(p.staffId()).orElseThrow(() -> new NotFoundException("Benutzer nicht gefunden."));
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Das aktuelle Passwort stimmt nicht.");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw new BusinessRuleException("Das neue Passwort muss sich vom alten unterscheiden.");
        }
        user.changePassword(encoder.encode(req.newPassword()));
        return ResponseEntity.noContent().build();
    }

    // ---- calendar ------------------------------------------------------------------------------

    @GetMapping("/day")
    @Transactional(readOnly = true)
    public DayView day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Shop shop = current.shop();
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
        Appointment a = appointment(id);
        current.requireMayActFor(a.getBarber().getId(), ONLY_OWN);
        if (a.getStatus() == AppointmentStatus.PENDING) {
            // a request: the customer must be told about the decision
            if (change.status() == AppointmentStatus.CONFIRMED) return toAdmin(chat.confirm(a.getShopId(), id));
            if (change.status() == AppointmentStatus.CANCELLED) return toAdmin(chat.reject(a.getShopId(), id));
            throw new BusinessRuleException("Eine Anfrage kann nur bestätigt oder abgelehnt werden.");
        }
        if (change.status() == AppointmentStatus.PENDING) {
            throw new BusinessRuleException("Ein Termin kann nicht zurück auf „angefragt“ gesetzt werden.");
        }
        if (a.getStatus() == AppointmentStatus.CANCELLED && change.status().blocksTime()) {
            // re-activating could collide with a newer booking; the DB constraint would reject it anyway
            throw new BusinessRuleException("Ein stornierter Termin kann nicht reaktiviert werden. Bitte neu buchen.");
        }
        a.changeStatus(change.status());
        appointments.flush();
        return toAdmin(a);
    }

    // ---- requests ------------------------------------------------------------------------------

    /** Open requests (e.g. from WhatsApp), soonest first. Barbers see the requests for themselves. */
    @GetMapping("/requests")
    @Transactional(readOnly = true)
    public List<AdminAppointment> requests() {
        return appointments.findPending(current.shop().getId()).stream()
                .filter(a -> current.mayActFor(a.getBarber().getId()))
                .map(AdminController::toAdmin)
                .toList();
    }

    @PostMapping("/appointments/{id}/confirm")
    public AdminAppointment confirm(@PathVariable Long id) {
        Appointment a = appointment(id);
        current.requireMayActFor(a.getBarber().getId(), ONLY_OWN);
        return toAdmin(chat.confirm(a.getShopId(), id));
    }

    @PostMapping("/appointments/{id}/reject")
    public AdminAppointment reject(@PathVariable Long id) {
        Appointment a = appointment(id);
        current.requireMayActFor(a.getBarber().getId(), ONLY_OWN);
        return toAdmin(chat.reject(a.getShopId(), id));
    }

    // ---- blocked times -------------------------------------------------------------------------

    @PostMapping("/absences")
    @Transactional
    public ResponseEntity<AbsenceView> block(@Valid @RequestBody AbsenceRequest req) {
        if (!req.end().isAfter(req.start())) throw new BusinessRuleException("Ende muss nach dem Beginn liegen.");
        barbers.findById(req.barberId())
                .filter(b -> b.getShopId().equals(current.shop().getId()))
                .orElseThrow(() -> new NotFoundException("Barber nicht gefunden."));
        current.requireMayActFor(req.barberId(), "Du kannst nur deine eigene Zeit blockieren.");
        BarberAbsence saved = absences.save(new BarberAbsence(req.barberId(), req.start(), req.end(), req.reason()));
        return ResponseEntity.status(201).body(new AbsenceView(saved.getId(), saved.getBarberId(),
                saved.getStartsAt(), saved.getEndsAt(), saved.getReason()));
    }

    @DeleteMapping("/absences/{id}")
    @Transactional
    public ResponseEntity<Void> unblock(@PathVariable Long id) {
        Long shopId = current.shop().getId();
        BarberAbsence a = absences.findById(id)
                .filter(x -> barbers.findById(x.getBarberId()).map(b -> b.getShopId().equals(shopId)).orElse(false))
                .orElseThrow(() -> new NotFoundException("Blocker nicht gefunden."));
        current.requireMayActFor(a.getBarberId(), "Du kannst nur deine eigenen Blocker entfernen.");
        absences.delete(a);
        return ResponseEntity.noContent().build();
    }

    // ---- helpers -------------------------------------------------------------------------------

    private Appointment appointment(Long id) {
        Long shopId = current.shop().getId();
        return appointments.findWithDetails(id)
                .filter(x -> x.getShopId().equals(shopId))
                .orElseThrow(() -> new NotFoundException("Termin nicht gefunden."));
    }

    private List<BarberRef> barberRefs(Shop shop) {
        return barbers.findByShopIdAndActiveTrueOrderBySortOrder(shop.getId()).stream()
                .map(b -> new BarberRef(b.getId(), b.getName())).toList();
    }

    static AdminAppointment toAdmin(Appointment a) {
        return new AdminAppointment(a.getId(), a.getStartsAt(), a.getEndsAt(), a.getStatus().name(),
                a.getBarber().getId(), a.getBarber().getName(), a.getServiceLabel(), a.getPriceCents(),
                a.getCustomer().fullName().trim(), a.getCustomer().getPhone(), a.getCustomer().getEmail(), a.getNote(),
                a.getSource().name(), a.getPendingUntil());
    }
}
