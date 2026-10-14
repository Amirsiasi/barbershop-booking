package at.barberbook;

import at.barberbook.booking.BookingDtos.BookingRequest;
import at.barberbook.booking.BookingService;
import at.barberbook.chat.ChatBookingService;
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

import java.sql.Timestamp;
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
    @Autowired ChatBookingService chat;

    long reza;
    long sami;
    long haircut; // "Schneiden & Föhnen", 35 min, 28 €
    long beard;   // "Bart kürzen & stylen", 30 min, 19 €

    @BeforeEach
    void cleanAndLookup() {
        jdbc.execute("TRUNCATE appointment, appointment_item, customer, barber_absence, chat_message, chat_session, staff_user, shop_photo RESTART IDENTITY CASCADE");
        jdbc.update("update shop set rating = null, review_count = null");
        reza = jdbc.queryForObject("select id from barber where name = 'Reza'", Long.class);
        sami = jdbc.queryForObject("select id from barber where name = 'Sami'", Long.class);
        haircut = jdbc.queryForObject("select id from service_offering where name = 'Schneiden & Föhnen'", Long.class);
        beard = jdbc.queryForObject("select id from service_offering where name = 'Bart kürzen & stylen'", Long.class);
    }

    static Instant vienna(int hour, int minute) {
        return ZonedDateTime.of(MONDAY, LocalTime.of(hour, minute), VIENNA).toInstant();
    }

    BookingRequest request(Long barberId, Instant start, String phone) {
        return new BookingRequest(List.of(haircut), barberId, start, "Max", "Muster", phone, "max@example.com", null, true);
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
                {"serviceIds": [%d], "start": "%s", "firstName": "", "lastName": "M", "phone": "abc", "privacyAccepted": false}
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

    // ---- admin price list ----------------------------------------------------------------------

    String serviceJson(String name, int duration, int priceCents, boolean active) {
        return """
                {"category": "Herren", "name": "%s", "description": null,
                 "durationMinutes": %d, "priceCents": %d, "active": %b}
                """.formatted(name, duration, priceCents, active);
    }

    @Test
    void new_price_applies_to_new_bookings_but_not_to_existing_ones() throws Exception {
        try {
            String first = mvc.perform(post("/api/shops/mr-artist/appointments").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(request(reza, vienna(10, 0), "0660 6000001"))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String token = json.readTree(first).get("manageToken").asText();

            mvc.perform(put("/api/admin/services/" + haircut).with(httpBasic("admin", "test-secret"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(serviceJson("Schneiden & Föhnen", 35, 3000, true)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.priceCents").value(3000));

            mvc.perform(get("/api/shops/mr-artist"))
                    .andExpect(jsonPath("$.services[?(@.id == " + haircut + ")].priceCents").value(3000));

            // the appointment booked before the change keeps its price
            mvc.perform(get("/api/appointments/" + token))
                    .andExpect(jsonPath("$.priceCents").value(2800));

            mvc.perform(post("/api/shops/mr-artist/appointments").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(request(sami, vienna(10, 0), "0660 6000002"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.priceCents").value(3000));
        } finally {
            jdbc.update("update service_offering set price_cents = 2800 where id = ?", haircut);
        }
    }

    @Test
    void hidden_services_are_listed_for_the_admin_but_not_bookable() throws Exception {
        String created = mvc.perform(post("/api/admin/services").with(httpBasic("admin", "test-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceJson("Kopfmassage extra", 15, 1200, false)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        mvc.perform(get("/api/admin/services").with(httpBasic("admin", "test-secret")))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].active").value(false));

        mvc.perform(get("/api/shops/mr-artist"))
                .andExpect(jsonPath("$.services[?(@.id == " + id + ")]").isEmpty());

        mvc.perform(get("/api/shops/mr-artist/availability")
                        .param("serviceId", String.valueOf(id)).param("date", MONDAY.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalid_price_list_entries_are_rejected() throws Exception {
        mvc.perform(post("/api/admin/services").with(httpBasic("admin", "test-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceJson("", 2, -100, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.length()").value(3));

        mvc.perform(get("/api/admin/services"))
                .andExpect(status().isUnauthorized());
    }

    // ---- WhatsApp requests (phase 1, via the same service the webhook will use) ------------------

    String say(String phone, String text) {
        return chat.handleIncoming("mr-artist", phone, null, text).get(0);
    }

    List<String> freeStarts(long barberId) throws Exception {
        String res = mvc.perform(get("/api/shops/mr-artist/availability")
                        .param("serviceId", String.valueOf(haircut)).param("barberId", String.valueOf(barberId))
                        .param("date", MONDAY.toString()))
                .andReturn().getResponse().getContentAsString();
        List<String> starts = new ArrayList<>();
        for (JsonNode s : json.readTree(res).get("slots")) starts.add(s.get("start").asText());
        return starts;
    }

    @Test
    void whatsapp_request_asks_for_the_service_reserves_the_slot_and_the_barber_confirms() throws Exception {
        String first = say("0664 1000001", "Amir 11:30 07.01");
        assertThat(first).contains("Welche Leistung").contains("1  Schneiden & Föhnen");

        String second = say("0664 1000001", "1");
        assertThat(second).contains("Danke Amir").contains("11:30");

        Long id = jdbc.queryForObject("select id from appointment where status = 'PENDING' and source = 'WHATSAPP'", Long.class);
        Long barberId = jdbc.queryForObject("select barber_id from appointment where id = ?", Long.class, id);
        assertThat(freeStarts(barberId)).doesNotContain(vienna(11, 30).toString()); // slot is held

        mvc.perform(get("/api/admin/requests").with(httpBasic("admin", "test-secret")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].source").value("WHATSAPP"))
                .andExpect(jsonPath("$[0].customerName").value("Amir"));

        mvc.perform(post("/api/admin/appointments/" + id + "/confirm").with(httpBasic("admin", "test-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        List<ChatBookingService.ChatMessageView> conversation = chat.messages(1L, "0664 1000001");
        assertThat(conversation).hasSize(5);
        assertThat(conversation.get(4).text()).startsWith("✅ Bestätigt!").contains("/termin/");

        // deciding twice is not possible
        mvc.perform(post("/api/admin/appointments/" + id + "/reject").with(httpBasic("admin", "test-secret")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void whatsapp_offers_the_closest_free_times_when_the_wish_is_taken() throws Exception {
        mvc.perform(post("/api/shops/mr-artist/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request(reza, vienna(11, 30), "0660 7000001"))))
                .andExpect(status().isCreated());

        String reply = say("0664 1000002", "Max 11:30 07.01 bei Reza Schneiden & Föhnen");
        assertThat(reply).contains("besetzt").contains("10:30, 10:45 oder 12:15");

        String booked = say("0664 1000002", "12:15");
        assertThat(booked).contains("Danke Max").contains("12:15").contains("Reza");
    }

    @Test
    void rejected_and_expired_requests_free_the_slot_again() throws Exception {
        say("0664 1000003", "Lena 15:00 07.01 Schneiden & Föhnen");
        Long rejected = jdbc.queryForObject("select id from appointment where status = 'PENDING'", Long.class);

        mvc.perform(post("/api/admin/appointments/" + rejected + "/reject").with(httpBasic("admin", "test-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(chat.messages(1L, "0664 1000003")).last()
                .extracting(ChatBookingService.ChatMessageView::text).asString().startsWith("Leider");

        say("0664 1000004", "Tom 16:00 07.01 Schneiden & Föhnen");
        Long expiring = jdbc.queryForObject("select id from appointment where status = 'PENDING'", Long.class);
        jdbc.update("update appointment set pending_until = ? where id = ?", Timestamp.from(NOW.minusSeconds(60)), expiring);

        chat.expireRequests();

        assertThat(jdbc.queryForObject("select status from appointment where id = ?", String.class, expiring))
                .isEqualTo("CANCELLED");
        assertThat(freeStarts(reza)).contains(vienna(15, 0).toString(), vienna(16, 0).toString());
    }

    @Test
    void unclear_messages_get_a_short_explanation() {
        assertThat(say("0664 1000005", "Hallo")).contains("Schreib einfach deinen Namen, den Tag und die Uhrzeit");
        assertThat(say("0664 1000005", "morgen")).contains("Um wie viel Uhr");
        assertThat(say("0664 1000005", "abbrechen")).contains("abgebrochen");
    }

    // ---- team management -----------------------------------------------------------------------

    long createBarber(String name) throws Exception {
        String created = mvc.perform(post("/api/admin/barbers").with(httpBasic("admin", "test-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "bio": "Neu im Team", "active": true}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(created).get("id").asLong();
    }

    @Test
    void a_new_barber_without_appointments_is_bookable_and_can_be_deleted() throws Exception {
        long id = createBarber("Karim");
        try {
            mvc.perform(get("/api/shops/mr-artist"))
                    .andExpect(jsonPath("$.barbers[?(@.name == 'Karim')]").isNotEmpty());
            assertThat(freeStarts(id)).contains(vienna(10, 0).toString());

            mvc.perform(delete("/api/admin/barbers/" + id).with(httpBasic("admin", "test-secret")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("DELETED"));

            assertThat(jdbc.queryForObject("select count(*) from barber where id = ?", Integer.class, id)).isZero();
        } finally {
            jdbc.update("update barber set active = false where id = ?", id);
        }
    }

    @Test
    void a_barber_with_upcoming_appointments_cannot_be_removed_and_with_history_is_only_hidden() throws Exception {
        long id = createBarber("Yusuf");
        try {
            String booked = mvc.perform(post("/api/shops/mr-artist/appointments").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(request(id, vienna(11, 0), "0660 8000001"))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            mvc.perform(delete("/api/admin/barbers/" + id).with(httpBasic("admin", "test-secret")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1 kommenden Termin")));

            mvc.perform(post("/api/appointments/" + json.readTree(booked).get("manageToken").asText() + "/cancel"))
                    .andExpect(status().isOk());

            mvc.perform(delete("/api/admin/barbers/" + id).with(httpBasic("admin", "test-secret")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("DEACTIVATED"));

            mvc.perform(get("/api/shops/mr-artist"))
                    .andExpect(jsonPath("$.barbers[?(@.name == 'Yusuf')]").isEmpty());
            mvc.perform(get("/api/admin/barbers").with(httpBasic("admin", "test-secret")))
                    .andExpect(jsonPath("$[?(@.name == 'Yusuf')].active").value(false));
        } finally {
            jdbc.update("update barber set active = false where id = ?", id);
        }
    }

    @Test
    void two_active_barbers_cannot_have_the_same_name() throws Exception {
        mvc.perform(post("/api/admin/barbers").with(httpBasic("admin", "test-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"reza\", \"active\": true}"))
                .andExpect(status().isConflict());
    }

    // ---- personal logins and roles -------------------------------------------------------------

    long createLogin(long barberId, String username, String password, String role, boolean passwordAlreadyChanged) throws Exception {
        String created = mvc.perform(post("/api/admin/staff").with(httpBasic("admin", "test-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"barberId": %d, "username": "%s", "password": "%s", "role": "%s"}
                                """.formatted(barberId, username, password, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
        if (passwordAlreadyChanged) jdbc.update("update staff_user set must_change_password = false where id = ?", id);
        return id;
    }

    long bookOnline(long barberId, int hour, String phone) throws Exception {
        String res = mvc.perform(post("/api/shops/mr-artist/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request(barberId, vienna(hour, 0), phone))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    @Test
    void a_new_login_must_choose_an_own_password_first() throws Exception {
        createLogin(sami, "Sami.K", "start1234", "BARBER", false);

        mvc.perform(get("/api/admin/me").with(httpBasic("sami.k", "start1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("BARBER"))
                .andExpect(jsonPath("$.barberName").value("Sami"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        mvc.perform(get("/api/admin/day").param("date", MONDAY.toString()).with(httpBasic("sami.k", "start1234")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("eigenes Passwort")));

        mvc.perform(post("/api/admin/me/password").with(httpBasic("sami.k", "start1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"falsch\", \"newPassword\": \"neu-geheim-1\"}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/admin/me/password").with(httpBasic("sami.k", "start1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"start1234\", \"newPassword\": \"neu-geheim-1\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/day").param("date", MONDAY.toString()).with(httpBasic("sami.k", "start1234")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/day").param("date", MONDAY.toString()).with(httpBasic("sami.k", "neu-geheim-1")))
                .andExpect(status().isOk());
    }

    @Test
    void a_barber_sees_the_whole_calendar_but_changes_only_his_own_things() throws Exception {
        createLogin(sami, "sami", "start1234", "BARBER", true);
        long rezasAppointment = bookOnline(reza, 10, "0660 9000001");
        long samisAppointment = bookOnline(sami, 11, "0660 9000002");

        mvc.perform(get("/api/admin/day").param("date", MONDAY.toString()).with(httpBasic("sami", "start1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointments.length()").value(2));

        mvc.perform(patch("/api/admin/appointments/" + rezasAppointment).with(httpBasic("sami", "start1234"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"COMPLETED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/appointments/" + samisAppointment).with(httpBasic("sami", "start1234"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"COMPLETED\"}"))
                .andExpect(status().isOk());

        String absence = "{\"barberId\": %d, \"start\": \"%s\", \"end\": \"%s\", \"reason\": \"Pause\"}";
        mvc.perform(post("/api/admin/absences").with(httpBasic("sami", "start1234")).contentType(MediaType.APPLICATION_JSON)
                        .content(absence.formatted(reza, vienna(13, 0), vienna(14, 0))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/absences").with(httpBasic("sami", "start1234")).contentType(MediaType.APPLICATION_JSON)
                        .content(absence.formatted(sami, vienna(13, 0), vienna(14, 0))))
                .andExpect(status().isCreated());

        // owner-only areas
        mvc.perform(get("/api/admin/services").with(httpBasic("sami", "start1234"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/staff").with(httpBasic("sami", "start1234"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/chat/conversations").with(httpBasic("sami", "start1234"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/barbers").with(httpBasic("sami", "start1234"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"X\", \"active\": true}"))
                .andExpect(status().isForbidden());

        // a WhatsApp request for Reza is not Sami's to decide
        say("0664 2000001", "Ali 15:00 07.01 bei Reza Schneiden & Föhnen");
        Long request = jdbc.queryForObject("select id from appointment where status = 'PENDING'", Long.class);
        mvc.perform(get("/api/admin/requests").with(httpBasic("sami", "start1234")))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/admin/appointments/" + request + "/confirm").with(httpBasic("sami", "start1234")))
                .andExpect(status().isForbidden());
    }

    @Test
    void removing_a_barber_locks_his_login() throws Exception {
        long leon = createBarber("Leon");
        try {
            createLogin(leon, "leon", "start1234", "BARBER", true);
            mvc.perform(get("/api/admin/me").with(httpBasic("leon", "start1234"))).andExpect(status().isOk());

            mvc.perform(delete("/api/admin/barbers/" + leon).with(httpBasic("admin", "test-secret")))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/admin/me").with(httpBasic("leon", "start1234"))).andExpect(status().isUnauthorized());
        } finally {
            jdbc.update("update barber set active = false where id = ?", leon);
        }
    }

    @Test
    void usernames_are_unique_and_an_owner_cannot_lock_himself_out() throws Exception {
        long rezasLogin = createLogin(reza, "reza", "start1234", "OWNER", true);

        mvc.perform(post("/api/admin/staff").with(httpBasic("admin", "test-secret")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barberId\": %d, \"username\": \"REZA\", \"password\": \"start1234\", \"role\": \"BARBER\"}".formatted(sami)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/staff").with(httpBasic("admin", "test-secret")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barberId\": %d, \"username\": \"admin\", \"password\": \"start1234\", \"role\": \"BARBER\"}".formatted(sami)))
                .andExpect(status().isConflict());

        // Reza as owner may manage the team ...
        mvc.perform(get("/api/admin/staff").with(httpBasic("reza", "start1234"))).andExpect(status().isOk());
        // ... but not demote himself
        mvc.perform(put("/api/admin/staff/" + rezasLogin).with(httpBasic("reza", "start1234"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"BARBER\", \"active\": true}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- several services in one appointment -----------------------------------------------------

    @Test
    void several_services_are_booked_back_to_back_with_summed_duration_and_price() throws Exception {
        BookingRequest req = new BookingRequest(List.of(haircut, beard), sami, vienna(10, 0), "Max", "Muster",
                "0660 4444444", null, null, true);
        mvc.perform(post("/api/shops/mr-artist/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceName").value("Schneiden & Föhnen + Bart kürzen & stylen"))
                .andExpect(jsonPath("$.services.length()").value(2))
                .andExpect(jsonPath("$.services[1].priceCents").value(1900))
                .andExpect(jsonPath("$.durationMinutes").value(65))
                .andExpect(jsonPath("$.priceCents").value(4700))
                .andExpect(jsonPath("$.end").value(vienna(11, 5).toString()));

        assertThat(jdbc.queryForObject("select count(*) from appointment_item", Integer.class)).isEqualTo(2);

        // 10:00-11:05 is taken for Sami, the next haircut on the 15-minute grid starts at 11:15
        List<String> free = freeStarts(sami);
        assertThat(free).doesNotContain(vienna(10, 30).toString(), vienna(11, 0).toString())
                .contains(vienna(11, 15).toString());

        // 65 minutes do not fit after 17:55 (closing 19:00)
        String res = mvc.perform(get("/api/shops/mr-artist/availability")
                        .param("serviceId", String.valueOf(haircut)).param("serviceId", String.valueOf(beard))
                        .param("barberId", String.valueOf(sami)).param("date", MONDAY.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> both = new ArrayList<>();
        for (JsonNode slot : json.readTree(res).get("slots")) both.add(slot.get("start").asText());
        assertThat(both).contains(vienna(17, 45).toString()).doesNotContain(vienna(18, 0).toString());
    }

    @Test
    void unknown_services_are_rejected() throws Exception {
        BookingRequest req = new BookingRequest(List.of(haircut, 999_999L), null, vienna(12, 0), "Max", "Muster",
                "0660 4444445", null, null, true);
        mvc.perform(post("/api/shops/mr-artist/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void whatsapp_customers_can_choose_several_services_by_number() throws Exception {
        assertThat(say("0664 3000001", "Amir 11:30 07.01")).contains("mehrere gehen auch");
        String reply = say("0664 3000001", "1 und 4");
        assertThat(reply).contains("Anfrage ist eingegangen").contains("Schneiden & Föhnen + Bart kürzen & stylen");
        Integer minutes = jdbc.queryForObject(
                "select (extract(epoch from ends_at - starts_at) / 60)::int from appointment where status = 'PENDING'", Integer.class);
        assertThat(minutes).isEqualTo(65);
    }

    // ---- shop photo and rating -------------------------------------------------------------------

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    @Test
    void the_owner_uploads_a_shop_photo_and_sets_the_rating() throws Exception {
        mvc.perform(get("/api/shops/mr-artist")).andExpect(jsonPath("$.photoUrl").value(org.hamcrest.Matchers.nullValue()));

        String res = mvc.perform(put("/api/admin/shop/photo").with(httpBasic("admin", "test-secret"))
                        .contentType("image/png").content(PNG))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = json.readTree(res).get("photoUrl").asText();
        assertThat(url).startsWith("/api/shops/mr-artist/photo?v=");

        mvc.perform(get("/api/shops/mr-artist")).andExpect(jsonPath("$.photoUrl").value(url));
        mvc.perform(get("/api/shops/mr-artist/photo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));

        // only real pictures - an SVG/HTML file must never be served from our domain
        mvc.perform(put("/api/admin/shop/photo").with(httpBasic("admin", "test-secret"))
                        .contentType("image/png").content("<svg onload=alert(1)></svg>".getBytes()))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(put("/api/admin/shop/rating").with(httpBasic("admin", "test-secret"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\": 4.8, \"reviewCount\": 127}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/shops/mr-artist"))
                .andExpect(jsonPath("$.rating").value(4.8))
                .andExpect(jsonPath("$.reviewCount").value(127));
        mvc.perform(put("/api/admin/shop/rating").with(httpBasic("admin", "test-secret"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\": 5.5, \"reviewCount\": 1}"))
                .andExpect(status().isBadRequest());

        // barbers may not change it
        createLogin(sami, "sami", "start1234", "BARBER", true);
        mvc.perform(put("/api/admin/shop/photo").with(httpBasic("sami", "start1234"))
                        .contentType("image/png").content(PNG))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/admin/shop/photo").with(httpBasic("admin", "test-secret"))).andExpect(status().isOk());
        mvc.perform(get("/api/shops/mr-artist/photo")).andExpect(status().isNotFound());
    }

    // ---- appointments entered by the team (phone call, walk-in) ----------------------------------

    @Test
    void the_team_enters_appointments_itself_without_double_booking() throws Exception {
        String body = """
                {"barberId": %d, "serviceIds": [%d, %d], "start": "%s", "firstName": "Karim", "phone": "0660 5555555"}
                """;
        mvc.perform(post("/api/admin/appointments").with(httpBasic("admin", "test-secret"))
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(sami, haircut, beard, vienna(10, 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("ADMIN"))
                .andExpect(jsonPath("$.serviceName").value("Schneiden & Föhnen + Bart kürzen & stylen"))
                .andExpect(jsonPath("$.end").value(vienna(11, 10).toString()));

        // overlaps the first one
        mvc.perform(post("/api/admin/appointments").with(httpBasic("admin", "test-secret"))
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(sami, haircut, beard, vienna(10, 30))))
                .andExpect(status().isConflict());

        // a barber enters appointments only for himself
        createLogin(sami, "sami", "start1234", "BARBER", true);
        mvc.perform(post("/api/admin/appointments").with(httpBasic("sami", "start1234"))
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(reza, haircut, beard, vienna(12, 0))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/appointments").with(httpBasic("sami", "start1234"))
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(sami, haircut, beard, vienna(12, 0))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/admin/range").param("from", MONDAY.toString()).param("days", "3")
                        .with(httpBasic("admin", "test-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointments.length()").value(2));
    }

    // ---- statistics ------------------------------------------------------------------------------

    @Test
    void statistics_per_week_month_and_barber() throws Exception {
        bookOnline(sami, 10, "0660 7000001");
        long second = bookOnline(reza, 11, "0660 7000002");
        bookOnline(reza, 12, "0660 7000003");
        jdbc.update("update appointment set status = 'CANCELLED' where id = ?", second);

        mvc.perform(get("/api/admin/stats").param("unit", "week").with(httpBasic("admin", "test-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets.length()").value(12))
                .andExpect(jsonPath("$.buckets[11].start").value("2030-01-07"))
                .andExpect(jsonPath("$.buckets[11].appointments").value(2))
                .andExpect(jsonPath("$.buckets[11].revenueCents").value(5600))
                .andExpect(jsonPath("$.buckets[11].cancelled").value(1))
                .andExpect(jsonPath("$.totals.newCustomers").value(2))
                .andExpect(jsonPath("$.totals.averageCents").value(2800))
                .andExpect(jsonPath("$.previous.appointments").value(0))
                .andExpect(jsonPath("$.services[0].name").value("Schneiden & Föhnen"));

        mvc.perform(get("/api/admin/stats").param("unit", "month").param("barberId", String.valueOf(sami))
                        .with(httpBasic("admin", "test-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets[11].start").value("2030-01-01"))
                .andExpect(jsonPath("$.totals.appointments").value(1))
                .andExpect(jsonPath("$.barbers.length()").value(1));

        createLogin(sami, "sami", "start1234", "BARBER", true);
        mvc.perform(get("/api/admin/stats").with(httpBasic("sami", "start1234"))).andExpect(status().isForbidden());
    }
}
