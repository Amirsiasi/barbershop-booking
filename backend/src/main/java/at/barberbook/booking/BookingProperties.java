package at.barberbook.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Booking rules, configurable in application.yml under "app.booking".
 *
 * @param slotStepMinutes  grid for offered start times (15 = 10:00, 10:15, 10:30 ...)
 * @param minLeadTime      how far in advance a slot must be booked (no "in 5 minutes" bookings)
 * @param maxDaysAhead     booking horizon
 * @param cancelCutoff     customers can cancel online until this long before the appointment
 * @param reminderBefore   when the reminder is sent
 * @param publicBaseUrl    base URL of the website, used for links in messages
 */
@ConfigurationProperties("app.booking")
public record BookingProperties(
        @DefaultValue("15") int slotStepMinutes,
        @DefaultValue("30m") Duration minLeadTime,
        @DefaultValue("60") int maxDaysAhead,
        @DefaultValue("2h") Duration cancelCutoff,
        @DefaultValue("24h") Duration reminderBefore,
        @DefaultValue("http://localhost:5173") String publicBaseUrl) {
}
