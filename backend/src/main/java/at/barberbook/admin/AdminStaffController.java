package at.barberbook.admin;

import at.barberbook.common.Exceptions.BusinessRuleException;
import at.barberbook.common.Exceptions.ConflictException;
import at.barberbook.common.Exceptions.NotFoundException;
import at.barberbook.shop.Barber;
import at.barberbook.shop.BarberRepository;
import at.barberbook.staff.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Personal logins for the team (owner only). Every login belongs to one barber. The owner sets a start
 * password; the barber must choose an own one at the first login. Passwords are stored as BCrypt hashes only.
 */
@RestController
@RequestMapping("/api/admin/staff")
public class AdminStaffController {

    public record StaffView(Long id, String username, String role, Long barberId, String barberName,
                            boolean active, boolean mustChangePassword) {
    }

    public record NewLogin(
            @NotNull(message = "Bitte einen Barber wählen.") Long barberId,
            @NotBlank(message = "Benutzername fehlt.")
            @Pattern(regexp = "^[a-zA-Z0-9._-]{3,40}$",
                    message = "3–40 Zeichen: Buchstaben ohne Umlaute, Ziffern, Punkt, Minus oder Unterstrich.") String username,
            @NotBlank(message = "Startpasswort fehlt.")
            @Size(min = 8, max = 72, message = "Das Startpasswort braucht mindestens 8 Zeichen.") String password,
            @NotNull(message = "Bitte eine Rolle wählen.") StaffRole role) {
    }

    public record LoginChange(@NotNull StaffRole role, boolean active) {
    }

    public record PasswordReset(
            @NotBlank(message = "Neues Startpasswort fehlt.")
            @Size(min = 8, max = 72, message = "Das Startpasswort braucht mindestens 8 Zeichen.") String password) {
    }

    private final CurrentStaff current;
    private final StaffUserRepository users;
    private final BarberRepository barbers;
    private final StaffUserDetailsService userDetails;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AdminStaffController(CurrentStaff current, StaffUserRepository users, BarberRepository barbers,
                                StaffUserDetailsService userDetails, PasswordEncoder encoder, Clock clock) {
        this.current = current;
        this.users = users;
        this.barbers = barbers;
        this.userDetails = userDetails;
        this.encoder = encoder;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<StaffView> list() {
        return users.findByShopIdOrderByUsername(current.shop().getId()).stream().map(this::toView).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<StaffView> create(@Valid @RequestBody NewLogin req) {
        Long shopId = current.shop().getId();
        Barber barber = barbers.findById(req.barberId())
                .filter(b -> b.getShopId().equals(shopId) && b.isActive())
                .orElseThrow(() -> new NotFoundException("Barber nicht gefunden."));
        if (users.findByBarberId(barber.getId()).isPresent()) {
            throw new ConflictException(barber.getName() + " hat schon einen Login.");
        }
        String username = req.username().trim().toLowerCase(Locale.ROOT);
        if (users.findByUsername(username).isPresent() || userDetails.isEmergencyUsername(username)) {
            throw new ConflictException("Der Benutzername „" + username + "“ ist schon vergeben.");
        }
        StaffUser saved = users.save(new StaffUser(shopId, barber.getId(), username,
                encoder.encode(req.password()), req.role(), clock.instant()));
        return ResponseEntity.created(URI.create("/api/admin/staff/" + saved.getId())).body(toView(saved));
    }

    @PutMapping("/{id}")
    @Transactional
    public StaffView update(@PathVariable Long id, @Valid @RequestBody LoginChange req) {
        StaffUser u = user(id);
        if (isMe(u) && (req.role() != StaffRole.OWNER || !req.active())) {
            throw new BusinessRuleException("Du kannst dich nicht selbst sperren oder herabstufen.");
        }
        if (req.active() && u.getBarberId() != null
                && !barbers.findById(u.getBarberId()).map(Barber::isActive).orElse(false)) {
            throw new BusinessRuleException("Der Barber ist nicht mehr im Team. Bitte ihn zuerst wieder aktivieren.");
        }
        u.setRole(req.role());
        u.setActive(req.active());
        return toView(u);
    }

    @PostMapping("/{id}/reset-password")
    @Transactional
    public StaffView resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordReset req) {
        StaffUser u = user(id);
        if (isMe(u)) {
            throw new BusinessRuleException("Dein eigenes Passwort änderst du über „Passwort ändern“.");
        }
        u.resetPassword(encoder.encode(req.password()));
        return toView(u);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        StaffUser u = user(id);
        if (isMe(u)) throw new BusinessRuleException("Du kannst deinen eigenen Login nicht löschen.");
        users.delete(u);
        return ResponseEntity.noContent().build();
    }

    private boolean isMe(StaffUser u) {
        return Objects.equals(current.get().staffId(), u.getId());
    }

    private StaffUser user(Long id) {
        Long shopId = current.shop().getId();
        return users.findById(id).filter(u -> u.getShopId().equals(shopId))
                .orElseThrow(() -> new NotFoundException("Login nicht gefunden."));
    }

    private StaffView toView(StaffUser u) {
        String barberName = u.getBarberId() == null ? null
                : barbers.findById(u.getBarberId()).map(Barber::getName).orElse(null);
        return new StaffView(u.getId(), u.getUsername(), u.getRole().name(), u.getBarberId(), barberName,
                u.isActive(), u.isMustChangePassword());
    }
}
