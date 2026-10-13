package at.barberbook.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings for chat requests, under "app.chat" in application.yml.
 *
 * @param requestHold    how long an unconfirmed request keeps the slot reserved
 * @param sessionTimeout after this silence a half-finished conversation starts over
 */
@ConfigurationProperties("app.chat")
public record ChatProperties(
        @DefaultValue("2h") Duration requestHold,
        @DefaultValue("2h") Duration sessionTimeout) {
}
