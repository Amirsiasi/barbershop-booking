package at.barberbook.admin;

import at.barberbook.staff.CurrentStaff;
import at.barberbook.common.Exceptions.NotFoundException;
import at.barberbook.shop.ServiceOffering;
import at.barberbook.shop.ServiceOfferingRepository;
import at.barberbook.shop.Shop;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Price list management for the shop owner: change prices and durations, add services, hide services.
 * Services are never deleted, because past appointments reference them - they are set inactive instead.
 */
@RestController
@RequestMapping("/api/admin/services")
public class AdminServicesController {

    public record AdminServiceView(Long id, String category, String name, String description,
                                   int durationMinutes, int priceCents, boolean active, int sortOrder) {
    }

    public record ServiceForm(
            @NotBlank(message = "Kategorie fehlt.") @Size(max = 80) String category,
            @NotBlank(message = "Name fehlt.") @Size(max = 120) String name,
            @Size(max = 300, message = "Beschreibung ist zu lang (max. 300 Zeichen).") String description,
            @NotNull(message = "Dauer fehlt.")
            @Min(value = 5, message = "Mindestens 5 Minuten.")
            @Max(value = 480, message = "Höchstens 8 Stunden.") Integer durationMinutes,
            @NotNull(message = "Preis fehlt.")
            @Min(value = 0, message = "Der Preis kann nicht negativ sein.")
            @Max(value = 100_000, message = "Höchstens 1.000 €.") Integer priceCents,
            boolean active,
            Integer sortOrder) {
    }

    private final CurrentStaff current;
    private final ServiceOfferingRepository services;

    public AdminServicesController(CurrentStaff current, ServiceOfferingRepository services) {
        this.current = current;
        this.services = services;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminServiceView> list() {
        return services.findByShopIdOrderBySortOrderAscIdAsc(shop().getId()).stream()
                .map(AdminServicesController::toView)
                .toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AdminServiceView> create(@Valid @RequestBody ServiceForm form) {
        Shop shop = shop();
        int nextSort = services.findByShopIdOrderBySortOrderAscIdAsc(shop.getId()).stream()
                .mapToInt(ServiceOffering::getSortOrder).max().orElse(0) + 1;
        ServiceOffering s = new ServiceOffering(shop.getId(), form.category().trim(), form.name().trim(),
                form.durationMinutes(), form.priceCents());
        s.update(form.category().trim(), form.name().trim(), blankToNull(form.description()),
                form.durationMinutes(), form.priceCents(), form.active(),
                form.sortOrder() != null ? form.sortOrder() : nextSort);
        ServiceOffering saved = services.save(s);
        return ResponseEntity.created(URI.create("/api/admin/services/" + saved.getId())).body(toView(saved));
    }

    @PutMapping("/{id}")
    @Transactional
    public AdminServiceView update(@PathVariable Long id, @Valid @RequestBody ServiceForm form) {
        ServiceOffering s = services.findById(id)
                .filter(x -> x.getShopId().equals(shop().getId()))
                .orElseThrow(() -> new NotFoundException("Leistung nicht gefunden."));
        s.update(form.category().trim(), form.name().trim(), blankToNull(form.description()),
                form.durationMinutes(), form.priceCents(), form.active(),
                form.sortOrder() != null ? form.sortOrder() : s.getSortOrder());
        return toView(s);
    }

    private Shop shop() {
        return current.shop();
    }

    private static AdminServiceView toView(ServiceOffering s) {
        return new AdminServiceView(s.getId(), s.getCategory(), s.getName(), s.getDescription(),
                s.getDurationMinutes(), s.getPriceCents(), s.isActive(), s.getSortOrder());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
