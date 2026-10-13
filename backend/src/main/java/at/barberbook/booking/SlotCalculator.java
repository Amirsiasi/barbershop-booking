package at.barberbook.booking;

import java.time.*;
import java.util.*;

/**
 * Pure availability logic - no database, no Spring - so it is easy to unit test.
 *
 * <p>For one day it walks through the opening hours in steps (e.g. every 15 min) and
 * offers a start time if at least one barber is free for the whole service duration.
 * Wall-clock times are interpreted in the shop's time zone, so daylight-saving changes
 * are handled by java.time, and everything returned is an {@link Instant} (UTC).
 */
public final class SlotCalculator {

    /** A period in which a barber is busy (appointment or absence). End is exclusive. */
    public record Busy(long barberId, Instant start, Instant end) {
        boolean overlaps(Instant s, Instant e) {
            return start.isBefore(e) && end.isAfter(s);
        }
    }

    /** One bookable start time and which barbers could take it. */
    public record Slot(Instant start, Instant end, List<Long> barberIds) {
    }

    private SlotCalculator() {
    }

    public static List<Slot> calculate(LocalDate date,
                                       ZoneId zone,
                                       LocalTime open,
                                       LocalTime close,
                                       Duration serviceDuration,
                                       List<Long> barberIds,
                                       Collection<Busy> busy,
                                       Instant earliestStart,
                                       int stepMinutes) {
        if (barberIds.isEmpty() || !close.isAfter(open)) return List.of();

        Map<Long, List<Busy>> busyByBarber = new HashMap<>();
        for (Busy b : busy) busyByBarber.computeIfAbsent(b.barberId(), k -> new ArrayList<>()).add(b);

        Instant dayClose = ZonedDateTime.of(date, close, zone).toInstant();
        List<Slot> slots = new ArrayList<>();

        // count minutes from opening time instead of adding to LocalTime, which would wrap at midnight
        long openMinutes = Duration.between(open, close).toMinutes();
        for (long m = 0; m < openMinutes; m += stepMinutes) {
            Instant start = ZonedDateTime.of(date, open.plusMinutes(m), zone).toInstant();
            Instant end = start.plus(serviceDuration);

            if (end.isAfter(dayClose)) break;            // service would run past closing time
            if (start.isBefore(earliestStart)) continue; // in the past or too short notice

            List<Long> free = barberIds.stream()
                    .filter(id -> busyByBarber.getOrDefault(id, List.of()).stream().noneMatch(b -> b.overlaps(start, end)))
                    .toList();
            if (!free.isEmpty()) slots.add(new Slot(start, end, free));
        }
        return slots;
    }
}
