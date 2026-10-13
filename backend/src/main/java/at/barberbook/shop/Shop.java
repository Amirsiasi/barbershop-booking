package at.barberbook.shop;

import jakarta.persistence.*;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "shop")
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String slug;
    private String name;
    private String tagline;
    private String street;
    private String zipCity;
    private String phone;
    private String email;
    private String timeZone;
    private String highlights;

    protected Shop() {
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getTagline() { return tagline; }
    public String getStreet() { return street; }
    public String getZipCity() { return zipCity; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getTimeZone() { return timeZone; }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }

    public List<String> highlightList() {
        if (highlights == null || highlights.isBlank()) return List.of();
        return Arrays.stream(highlights.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
