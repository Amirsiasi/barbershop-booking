package at.barberbook.booking;

import at.barberbook.booking.SlotCalculator.Busy;
import at.barberbook.booking.SlotCalculator.Slot;
import at.barberbook.common.Exceptions.NotFoundException;
import at.barberbook.shop.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Loads opening hours, barbers, appointments and absences and lets {@link SlotCalculator} do the maths. */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    /** Summary of one day for the date picker. */
    public record DaySummary(LocalDate date, boolean open, int freeSlots) {
    }

    private final OpeningHoursRepository openingHours;
    private final BarberRepository barbers;
    private final AppointmentRepository appointments;
    private final BarberAbsenceRepository absences;
    private final BookingProperties props;
    private final Clock clock;

    public AvailabilityService(OpeningHoursRepository openingHours, BarberRepository barbers,
                               AppointmentRepository appointments, BarberAbsenceRepository absences,
                               BookingProperties props, Clock clock) {
        this.openingHours = openingHours;
        this.barbers = barbers;
        this.appointments = appointments;
        this.absences = absences;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Free start times on {@code date} for {@code service}.
     *
     * @param barberId a specific barber, or {@code null} for "egal / any barber"
     */
    public List<Slot> slots(Shop shop, ServiceOffering service, Long barberId, LocalDate date) {
        ZoneId zone = shop.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (date.isBefore(today) || date.isAfter(today.plusDays(props.maxDaysAhead()))) return List.of();

        Optional<OpeningHours> hours = openingHours.findByShopIdAndDayOfWeek(shop.getId(), date.getDayOfWeek().getValue());
        if (hours.isEmpty()) return List.of(); // closed, e.g. Sunday

        List<Long> barberIds = barberIdsFor(shop, barberId);
        if (barberIds.isEmpty()) return List.of();

        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();

        List<Busy> busy = new ArrayList<>();
        Stream.concat(
                appointments.findBlocking(barberIds, dayStart, dayEnd).stream()
                        .map(a -> new Busy(a.getBarber().getId(), a.getStartsAt(), a.getEndsAt())),
                absences.findOverlapping(barberIds, dayStart, dayEnd).stream()
                        .map(a -> new Busy(a.getBarberId(), a.getStartsAt(), a.getEndsAt()))
        ).forEach(busy::add);

        Instant earliest = clock.instant().plus(props.minLeadTime());

        return SlotCalculator.calculate(date, zone, hours.get().getOpenTime(), hours.get().getCloseTime(),
                Duration.ofMinutes(service.getDurationMinutes()), barberIds, busy, earliest, props.slotStepMinutes());
    }

    /** Which of the next {@code days} days are open and how many slots are free - for the date strip. */
    public List<DaySummary> days(Shop shop, ServiceOffering service, Long barberId, LocalDate from, int days) {
        List<DaySummary> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = from.plusDays(i);
            boolean open = openingHours.findByShopIdAndDayOfWeek(shop.getId(), d.getDayOfWeek().getValue()).isPresent();
            int free = open ? slots(shop, service, barberId, d).size() : 0;
            result.add(new DaySummary(d, open, free));
        }
        return result;
    }

    private List<Long> barberIdsFor(Shop shop, Long barberId) {
        List<Barber> active = barbers.findByShopIdAndActiveTrueOrderBySortOrder(shop.getId());
        if (barberId == null) return active.stream().map(Barber::getId).toList();
        return active.stream()
                .filter(b -> b.getId().equals(barberId))
                .map(Barber::getId)
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new NotFoundException("Barber nicht gefunden."));
    }
}
