package at.barberbook;

import at.barberbook.booking.BookingDtos.BookingRequest;
import at.barberbook.booking.BookingService;
import at.barberbook.common.Exceptions.ConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests against a real PostgreSQL (the exclusion constraint cannot be tested with H2).
 * Uses Testcontainers if Docker is running, or TEST_DATABASE_URL / TEST_DATABASE_USER / TEST_DATABASE_PASSWORD.
 * Skipped automatically when neither is available.
 */
@SpringBootTest(properties = "app.admin.password=test-secret")
@AutoConfigureMockMvc
@EnabledIf("databaseAvailable")
class BookingIntegrationTest {

    /** Monday, 7 January 2030, 08:00 in Vienna - the shop opens at 10:00. */
    static final Instant NOW = Instant.parse("2030-01-07T07:00:00Z");
    static final ZoneId VIENNA = ZoneId.of("Europe/Vienna");
    static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

    static PostgreSQLContainer<?> container;

    static boolean databaseAvailable() {
        return System.getenv("TEST_DATABASE_URL") != null || DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        String url = System.getenv("TEST_DATABASE_URL");
        if (url != null) {
            r.add("spring.datasource.url", () -> url);
            r.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "barberbook"));
            r.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "barberbook"));
            return;
        }
        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
        }
        r.add("spring.datasource.url", container::getJdbcUrl);
        r.add("spring.datasource.username", container::getUsername);
        r.add("spring.datasource.password", container::getPassword);
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingService booking;

    long reza;
    long sami;
    long haircut; // "Schneiden & Föhnen", 35 min

    @BeforeEach
    void cleanAndLookup() {
        jdbc.execute("TRUNCATE appointment, customer, barber_absence RESTART IDENTITY CASCADE");
        reza = jdbc.queryForObject("select id from barber where name = 'Reza'", Long.class);
        sami = jdbc.queryForObject("select id from barber where name = 'Sami'", Long.class);
        haircut = jdbc.queryForObject("select id from service_offering where name = 'Schneiden & Föhnen'", Long.class);
    }

    static Instant vienna(int hour, int minute) {
        return ZonedDateTime.of(MONDAY, LocalTime.of(hour, minute), VIENNA).toInstant();
    }

    BookingRequest request(Long barberId, Instant start, String phone) {
        return new BookingRequest(haircut, barberId, start, "Max", "Muster", phone, "max@example.com", null, true);
    }

    @Test
    void shop_page_contains_seed_data() throws Exception {
        mvc.perform(get("/api/shops/mr-artist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mr. Artist Barbershop"))
                .andExpect(jsonPath("$.barbers.length()").value(2))
                .andExpect(jsonPath("$.services.length()").value(8))
                .andExpect(jsonPath("$.openingHours.length()").value(6));
    }

    @Test
    void availability_for_a_monday() throws Exception {
        mvc.perform(get("/api/shops/mr-artist/availability")
                        .param("serviceId", String.valueOf(haircut)).param("date", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].start").value(vienna(10, 0).toString()))
                .andExpect(jsonPath("$.slots[0].barberIds.length()").value(2))
                .andExpect(jsonPath("$.slots.length()").value(34));
    }

    @Test
    void sunday_is_closed() throws Exception {
        mvc.perform(get("/api/shops/mr-artist/availability")
                        .param("serviceId", String.valueOf(haircut)).param("date", "2030-01-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(0));
    }

    @Test
    void book_then_same_slot_is_rejected_and_any_barber_falls_back_to_the_other() throws Exception {
        String body = json.writeValueAsString(request(reza, vienna(10, 0), "0660 1111111"));
        mvc.perform(post("/api/shops/mr-artist/appointments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.barberName").value("Reza"))
                .andExpect(jsonPath("$.priceCents").value(2800));

        mvc.perform(post("/api/shops/mr-artist/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request(reza, vienna(10, 15), "0660 2222222"))))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/shops/mr-artist/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request(null, vienna(10, 0), "0660 3333333"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.barberName").value("Sami"));
    }

    @Test
    void invalid_input_returns_field_errors() throws Exception {
        String body = """
                {"serviceId": %d, "start": "%s", "firstName": "", "lastName": "M", "phone": "abc", "privacyAccepted": false}
                """.formatted(haircut, vienna(12, 0));
        mvc.perform(post("/api/shops/mr-artist/appointments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.length()").value(3));
    }

    @Test
    void twenty_people_clicking_book_at_the_same_moment_get_exactly_one_appointment() throws Exception {
        int attempts = 20;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            String phone = "0660 50000" + String.format("%02d", i);
            futures.add(pool.submit(() -> {
                go.await();
                try {
                    booking.book("mr-artist", request(reza, vienna(11, 0), phone));
                    ok.incrementAndGet();
                } catch (ConflictException e) {
                    conflict.incrementAndGet();
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(ok.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(attempts - 1);
        assertThat(jdbc.queryForObject("select count(*) from appointment", Integer.class)).isEqualTo(1);
    }

    @Test
    void customer_can_cancel_and_the_slot_becomes_free_again() throws Exception {
        String created = mvc.perform(post("/api/shops/mr-artist/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request(reza, vienna(15, 0), "0660 4444444"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(created).get("manageToken").asText();

        mvc.perform(post("/api/appointments/" + token + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(post("/api/shops/mr-artist/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request(reza, vienna(15, 0), "0660 5555555"))))
                .andExpect(status().isCreated());
    }

    @Test
    void blocked_time_is_not_bookable() throws Exception {
        String absence = """
                {"barberId": %d, "start": "%s", "end": "%s", "reason": "Mittagspause"}
                """.formatted(sami, vienna(13, 0), vienna(14, 0));
        mvc.perform(post("/api/admin/absences").with(httpBasic("admin", "test-secret"))
                        .contentType(MediaType.APPLICATION_JSON).content(absence))
                .andExpect(status().isCreated());

        String res = mvc.perform(get("/api/shops/mr-artist/availability")
                        .param("serviceId", String.valueOf(haircut)).param("barberId", String.valueOf(sami))
                        .param("date", MONDAY.toString()))
                .andReturn().getResponse().getContentAsString();
        List<String> starts = new ArrayList<>();
        for (JsonNode s : json.readTree(res).get("slots")) starts.add(s.get("start").asText());

        assertThat(starts).doesNotContain(vienna(13, 0).toString(), vienna(12, 30).toString())
                .contains(vienna(12, 15).toString(), vienna(14, 0).toString());
    }

    @Test
    void admin_area_requires_login() throws Exception {
        mvc.perform(get("/api/admin/day").param("date", MONDAY.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));

        mvc.perform(get("/api/admin/day").param("date", MONDAY.toString()).with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/day").param("date", MONDAY.toString()).with(httpBasic("admin", "test-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointments").isArray());
    }
}
