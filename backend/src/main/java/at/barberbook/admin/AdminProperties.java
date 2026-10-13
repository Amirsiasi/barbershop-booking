package at.barberbook.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Admin login for the shop owner. Set ADMIN_USERNAME / ADMIN_PASSWORD as environment variables -
 * never commit a real password. One admin per installation for now; for many shops this becomes
 * a "staff_user" table with a shop_id.
 */
@ConfigurationProperties("app.admin")
public record AdminProperties(
        @DefaultValue("admin") String username,
        String password,
        @DefaultValue("mr-artist") String shopSlug) {
}
