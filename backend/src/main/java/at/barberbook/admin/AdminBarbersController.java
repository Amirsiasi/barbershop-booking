package at.barberbook.admin;

import at.barberbook.staff.CurrentStaff;
import at.barberbook.staff.StaffUserRepository;
import at.barberbook.booking.AppointmentRepository;
import at.barberbook.chat.ChatSessionRepository;
import at.barberbook.common.Exceptions.BusinessRuleException;
import at.barberbook.common.Exceptions.ConflictException;
import at.barberbook.common.Exceptions.NotFoundException;
import at.barberbook.shop.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Clock;
import java.util.List;

/**
 * Team management: add, edit and remove barbers.
 *
 * <p>Removing follows three rules so no customer is left without a barber and history stays correct:
 * <ol>
 *   <li>upcoming (requested or confirmed) appointments → refused, they must be moved or cancelled first</li>
 *   <li>only past appointments → the barber is hidden (inactive), past appointments keep his name</li>
 *   <li>no appointments at all → really deleted</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/admin/barbers")
public class AdminBarbersController {

    public record AdminBarberView(Long id, String name, String bio, boolean active, int sortOrder, long upcomingAppointments) {
    }

    public record BarberForm(
            @NotBlank(message = "Name fehlt.") @Size(max = 80, message = "Name ist zu lang.") String name,
            @Size(max = 300, message = "Beschreibung ist zu lang (max. 300 Zeichen).") String bio,
            boolean active,
            Integer sortOrder) {
    }

    public record RemovalResult(String result, String message) {
    }

    private final CurrentStaff current;
    private final BarberRepository barbers;
    private final AppointmentRepository appointments;
    private final ChatSessionRepository chatSessions;
    private final StaffUserRepository staffUsers;
    private final Clock clock;

    public AdminBarbersController(CurrentStaff current, BarberRepository barbers,
                                  AppointmentRepository appointments,
                                  ChatSessionRepository chatSessions, StaffUserRepository staffUsers, Clock clock) {
        this.current = current;
        this.barbers = barbers;
        this.appointments = appointments;
        this.chatSessions = chatSessions;
        this.staffUsers = staffUsers;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminBarberView> list() {
        return barbers.findByShopIdOrderBySortOrderAscIdAsc(shop().getId()).stream().map(this::toView).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AdminBarberView> create(@Valid @RequestBody BarberForm form) {
        Shop shop = shop();
        String name = form.name().trim();
        requireUniqueName(shop, name, null);
        int nextSort = barbers.findByShopIdOrderBySortOrderAscIdAsc(shop.getId()).stream()
                .mapToInt(Barber::getSortOrder).max().orElse(0) + 1;
        Barber b = new Barber(shop.getId(), name);
        b.update(name, blankToNull(form.bio()), form.active(), form.sortOrder() != null ? form.sortOrder() : nextSort);
        Barber saved = barbers.save(b);
        return ResponseEntity.created(URI.create("/api/admin/barbers/" + saved.getId())).body(toView(saved));
    }

    @PutMapping("/{id}")
    @Transactional
    public AdminBarberView update(@PathVariable Long id, @Valid @RequestBody BarberForm form) {
        Barber b = barber(id);
        String name = form.name().trim();
        requireUniqueName(shop(), name, id);
        if (b.isActive() && !form.active()) {
            requireNoUpcoming(b);
            disableLogin(b);
        }
        b.update(name, blankToNull(form.bio()), form.active(), form.sortOrder() != null ? form.sortOrder() : b.getSortOrder());
        return toView(b);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public RemovalResult remove(@PathVariable Long id) {
        Barber b = barber(id);
        requireNoUpcoming(b);
        disableLogin(b);
        if (appointments.existsByBarberId(id)) {
            b.deactivate();
            return new RemovalResult("DEACTIVATED", b.getName() + " wurde entfernt. Alte Termine bleiben in der Historie erhalten.");
        }
        chatSessions.forgetBarber(id);
        // blocked times (barber_absence) are removed by the database: ON DELETE CASCADE
        barbers.delete(b);
        return new RemovalResult("DELETED", b.getName() + " wurde gelöscht.");
    }

    // ---- rules ---------------------------------------------------------------------------------

    /** Someone who left the team must not be able to log in any more. */
    private void disableLogin(Barber b) {
        staffUsers.findByBarberId(b.getId()).ifPresent(u -> u.setActive(false));
    }

    private void requireNoUpcoming(Barber b) {
        long upcoming = appointments.countUpcoming(b.getId(), clock.instant());
        if (upcoming > 0) {
            throw new BusinessRuleException(b.getName() + " hat noch " + upcoming
                    + (upcoming == 1 ? " kommenden Termin" : " kommende Termine")
                    + ". Bitte diese zuerst stornieren oder einem anderen Barber geben.");
        }
    }

    /** Two active barbers with the same name would confuse customers and the WhatsApp parser. */
    private void requireUniqueName(Shop shop, String name, Long ownId) {
        boolean taken = barbers.findByShopIdAndActiveTrueOrderBySortOrder(shop.getId()).stream()
                .anyMatch(x -> !x.getId().equals(ownId) && x.getName().trim().equalsIgnoreCase(name));
        if (taken) throw new ConflictException("Es gibt schon einen Barber namens " + name + ".");
    }

    private Barber barber(Long id) {
        return barbers.findById(id)
                .filter(b -> b.getShopId().equals(shop().getId()))
                .orElseThrow(() -> new NotFoundException("Barber nicht gefunden."));
    }

    private Shop shop() {
        return current.shop();
    }

    private AdminBarberView toView(Barber b) {
        return new AdminBarberView(b.getId(), b.getName(), b.getBio(), b.isActive(), b.getSortOrder(),
                appointments.countUpcoming(b.getId(), clock.instant()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
