package at.barberbook.booking;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response bodies of the public booking API. Entities are never sent to the client directly. */
public final class BookingDtos {

    private BookingDtos() {
    }

    public record SlotView(Instant start, Instant end, List<Long> barberIds) {
    }

    public record AvailabilityView(LocalDate date, List<SlotView> slots) {
    }

    public record BookingRequest(
            @NotNull(message = "Bitte wähle eine Leistung.") Long serviceId,
            Long barberId, // null = egal, irgendein freier Barber
            @NotNull(message = "Bitte wähle eine Uhrzeit.") Instant start,
            @NotBlank(message = "Vorname fehlt.") @Size(max = 50) String firstName,
            @NotBlank(message = "Nachname fehlt.") @Size(max = 50) String lastName,
            @NotBlank(message = "Telefonnummer fehlt.")
            @Pattern(regexp = "^[+0-9 ()/-]{6,25}$", message = "Bitte gib eine gültige Telefonnummer ein.") String phone,
            @Email(message = "Bitte gib eine gültige E-Mail-Adresse ein.") @Size(max = 100) String email,
            @Size(max = 300) String note,
            @AssertTrue(message = "Bitte akzeptiere die Datenschutzerklärung.") boolean privacyAccepted) {
    }

    public record AppointmentView(
            Long id,
            UUID manageToken,
            String status,
            Instant start,
            Instant end,
            String serviceName,
            int durationMinutes,
            int priceCents,
            String barberName,
            String customerFirstName,
            String shopName,
            String shopAddress,
            String timeZone,
            boolean cancellable,
            Instant cancelDeadline) {
    }
}
