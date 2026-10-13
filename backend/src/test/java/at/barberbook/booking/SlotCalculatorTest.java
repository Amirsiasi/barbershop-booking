package at.barberbook.booking;

import at.barberbook.booking.SlotCalculator.Busy;
import at.barberbook.booking.SlotCalculator.Slot;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlotCalculatorTest {

    private static final ZoneId VIENNA = ZoneId.of("Europe/Vienna");
    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);
    private static final LocalTime OPEN = LocalTime.of(10, 0);
    private static final LocalTime CLOSE = LocalTime.of(19, 0);

    private static Instant at(LocalDate d, int h, int m) {
        return ZonedDateTime.of(d, LocalTime.of(h, m), VIENNA).toInstant();
    }

    private static List<Slot> calc(Duration duration, List<Long> barbers, List<Busy> busy, Instant earliest) {
        return SlotCalculator.calculate(MONDAY, VIENNA, OPEN, CLOSE, duration, barbers, busy, earliest, 15);
    }

    @Test
    void offers_every_15_minutes_and_the_last_slot_ends_before_closing() {
        List<Slot> slots = calc(Duration.ofMinutes(35), List.of(1L), List.of(), Instant.MIN);

        assertThat(slots.get(0).start()).isEqualTo(at(MONDAY, 10, 0));
        assertThat(slots.get(slots.size() - 1).start()).isEqualTo(at(MONDAY, 18, 15)); // 18:15 + 35 min = 18:50
        assertThat(slots).hasSize(34);
    }

    @Test
    void skips_slots_that_overlap_an_existing_appointment() {
        Busy appointment = new Busy(1L, at(MONDAY, 11, 0), at(MONDAY, 11, 35));

        List<Instant> starts = calc(Duration.ofMinutes(30), List.of(1L), List.of(appointment), Instant.MIN)
                .stream().map(Slot::start).toList();

        assertThat(starts).contains(at(MONDAY, 10, 30))       // 10:30-11:00 ends exactly when the other starts
                .doesNotContain(at(MONDAY, 10, 45), at(MONDAY, 11, 0), at(MONDAY, 11, 15), at(MONDAY, 11, 30))
                .contains(at(MONDAY, 11, 45));
    }

    @Test
    void any_barber_mode_lists_who_is_free() {
        Busy rezaBusy = new Busy(1L, at(MONDAY, 10, 0), at(MONDAY, 12, 0));

        Slot first = calc(Duration.ofMinutes(30), List.of(1L, 2L), List.of(rezaBusy), Instant.MIN).get(0);

        assertThat(first.start()).isEqualTo(at(MONDAY, 10, 0));
        assertThat(first.barberIds()).containsExactly(2L);
    }

    @Test
    void respects_lead_time() {
        List<Slot> slots = calc(Duration.ofMinutes(30), List.of(1L), List.of(), at(MONDAY, 14, 5));

        assertThat(slots.get(0).start()).isEqualTo(at(MONDAY, 14, 15));
    }

    @Test
    void handles_the_day_daylight_saving_starts() {
        LocalDate dstDay = LocalDate.of(2030, 3, 31); // clocks jump 02:00 -> 03:00 in Vienna
        List<Slot> slots = SlotCalculator.calculate(dstDay, VIENNA, OPEN, CLOSE, Duration.ofMinutes(30),
                List.of(1L), List.of(), Instant.MIN, 15);

        // 10:00 Vienna summer time is 08:00 UTC
        assertThat(slots.get(0).start()).isEqualTo(Instant.parse("2030-03-31T08:00:00Z"));
    }

    @Test
    void no_barbers_means_no_slots() {
        assertThat(calc(Duration.ofMinutes(30), List.of(), List.of(), Instant.MIN)).isEmpty();
    }
}
