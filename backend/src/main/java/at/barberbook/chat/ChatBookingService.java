package at.barberbook.chat;

import at.barberbook.booking.*;
import at.barberbook.booking.SlotCalculator.Slot;
import at.barberbook.chat.ChatMessage.Direction;
import at.barberbook.chat.ChatSession.State;
import at.barberbook.chat.MessageParser.Command;
import at.barberbook.chat.MessageParser.Parsed;
import at.barberbook.common.Exceptions.BusinessRuleException;
import at.barberbook.common.Exceptions.NotFoundException;
import at.barberbook.notification.AsyncNotifier;
import at.barberbook.notification.BookingMessages;
import at.barberbook.shop.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Booking by chat (WhatsApp): understands the request, asks for what is missing, reserves the slot as
 * PENDING and tells the customer once the barber has confirmed or rejected it.
 *
 * <pre>
 *  Kunde:  Amir 11:30 15.10
 *  Shop:   Welche Leistung möchtest du? 1 Schneiden &amp; Föhnen …
 *  Kunde:  1
 *  Shop:   Danke Amir! Deine Anfrage ist eingegangen … Der Barber bestätigt sie in Kürze.
 *  (Barber klickt "Bestätigen")
 *  Shop:   ✅ Bestätigt! …
 * </pre>
 */
@Service
public class ChatBookingService {

    private static final Logger log = LoggerFactory.getLogger(ChatBookingService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d.M.", Locale.GERMAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN);

    public record ChatMessageView(Long id, String direction, String text, Instant at) {
    }

    public record ConversationView(String phone, String name, String lastText, String lastDirection, Instant lastAt) {
    }

    private final ShopRepository shops;
    private final BarberRepository barbers;
    private final ServiceOfferingRepository services;
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository chatMessages;
    private final AppointmentRepository appointments;
    private final AvailabilityService availability;
    private final BookingService booking;
    private final ChatGateway gateway;
    private final AsyncNotifier notifier;
    private final BookingMessages mailTexts;
    private final BookingProperties bookingProps;
    private final ChatProperties props;
    private final Clock clock;

    public ChatBookingService(ShopRepository shops, BarberRepository barbers, ServiceOfferingRepository services,
                              ChatSessionRepository sessions, ChatMessageRepository chatMessages,
                              AppointmentRepository appointments, AvailabilityService availability,
                              BookingService booking, ChatGateway gateway, AsyncNotifier notifier,
                              BookingMessages mailTexts, BookingProperties bookingProps, ChatProperties props,
                              Clock clock) {
        this.shops = shops;
        this.barbers = barbers;
        this.services = services;
        this.sessions = sessions;
        this.chatMessages = chatMessages;
        this.appointments = appointments;
        this.availability = availability;
        this.booking = booking;
        this.gateway = gateway;
        this.notifier = notifier;
        this.mailTexts = mailTexts;
        this.bookingProps = bookingProps;
        this.props = props;
        this.clock = clock;
    }

    // ============================================================================================
    // incoming messages
    // ============================================================================================

    /**
     * Handles one incoming chat message and returns the replies that were sent.
     *
     * @param profileName the name shown in the sender's WhatsApp profile, if known (used when the
     *                    message itself contains no name)
     */
    @Transactional
    public List<String> handleIncoming(String shopSlug, String rawPhone, String profileName, String text) {
        Shop shop = booking.shop(shopSlug);
        String phone = BookingService.normalizePhone(rawPhone);
        Instant now = clock.instant();
        chatMessages.save(new ChatMessage(shop.getId(), phone, Direction.IN, text, now));

        ChatSession session = sessions.findByShopIdAndPhone(shop.getId(), phone)
                .orElseGet(() -> new ChatSession(shop.getId(), phone, now));
        if (session.getState() != State.IDLE && session.getUpdatedAt().plus(props.sessionTimeout()).isBefore(now)) {
            session.reset(); // half-finished conversation from hours ago - start over
        }

        List<Barber> barberList = barbers.findByShopIdAndActiveTrueOrderBySortOrder(shop.getId());
        List<ServiceOffering> serviceList = services.findByShopIdAndActiveTrueOrderBySortOrder(shop.getId());
        LocalDate today = LocalDate.now(clock.withZone(shop.zone()));
        Parsed parsed = MessageParser.parse(text, today,
                barberList.stream().map(Barber::getName).toList(),
                serviceList.stream().map(ServiceOffering::getName).toList());

        String reply;
        List<Integer> numbers = session.getState() == State.AWAITING_SERVICE ? MessageParser.numberList(text) : List.of();
        if (numbers.size() > 1) {
            // "1 und 3" / "1, 3" / "1+3" -> several services in one appointment
            reply = chooseServices(session, numbers, serviceList);
            if (reply == null) reply = advance(shop, session, MessageParser.nothing(), text, profileName, barberList, serviceList, now);
        } else if (parsed.command() == Command.CANCEL) {
            session.reset();
            reply = "Alles klar, abgebrochen. Schreib einfach wieder, wenn du einen Termin möchtest.";
        } else if (parsed.command() == Command.HELP) {
            reply = help(shop);
        } else {
            reply = advance(shop, session, parsed, text, profileName, barberList, serviceList, now);
        }

        session.touch(now);
        sessions.save(session);
        send(shop, phone, reply, now);
        return List.of(reply);
    }

    /** Merges what the message says into the conversation and decides the next step. */
    private String advance(Shop shop, ChatSession s, Parsed p, String text, String profileName,
                           List<Barber> barberList, List<ServiceOffering> serviceList, Instant now) {
        // 1) a plain number answers the open question
        if (p.choice() != null) {
            if (s.getState() == State.AWAITING_SERVICE) {
                int index = p.choice() - 1;
                if (index < 0 || index >= serviceList.size()) {
                    return "Bitte antworte mit einer Zahl von 1 bis " + serviceList.size() + ".\n\n" + serviceQuestion(serviceList);
                }
                s.setServiceId(serviceList.get(index).getId());
            } else if ((s.getState() == State.AWAITING_TIME || s.getState() == State.AWAITING_DATETIME) && p.choice() <= 23) {
                s.setRequestedTime(LocalTime.of(p.choice(), 0)); // "14" -> 14:00
            } else if (s.getState() == State.IDLE) {
                return help(shop);
            }
        }

        // 2) an answer to "Wie ist dein Name?"
        if (s.getState() == State.AWAITING_NAME && p.name() == null && p.date() == null
                && p.time() == null && p.choice() == null) {
            String typed = cleanName(text);
            if (typed != null) s.setCustomerName(typed);
        }

        // 3) everything else the message contains
        if (p.name() != null) s.setCustomerName(p.name());
        if (p.date() != null) s.setRequestedDate(p.date());
        if (p.time() != null) s.setRequestedTime(p.time());
        if (p.serviceName() != null) {
            serviceList.stream().filter(x -> x.getName().equals(p.serviceName())).findFirst()
                    .ifPresent(x -> s.setServiceId(x.getId()));
        }
        if (p.barberName() != null) {
            barberList.stream().filter(b -> b.getName().equals(p.barberName())).findFirst()
                    .ifPresent(b -> s.setBarberId(b.getId()));
        }
        if (p.anyBarber()) s.setBarberId(null);

        boolean knowsSomething = s.getRequestedDate() != null || s.getRequestedTime() != null || s.getServiceId() != null;
        if (!knowsSomething) {
            s.setState(State.IDLE);
            if (p.name() != null) return "Hallo " + p.name().split(" ")[0] + "! " + help(shop);
            return help(shop);
        }

        // 4) ask for what is still missing - one question at a time
        if (s.getRequestedDate() == null || s.getRequestedTime() == null) {
            s.setState(State.AWAITING_DATETIME);
            if (s.getRequestedDate() != null) {
                return "Um wie viel Uhr am " + DAY.format(s.getRequestedDate()) + "? Zum Beispiel: 11:30";
            }
            if (s.getRequestedTime() != null) {
                return "An welchem Tag um " + TIME.format(s.getRequestedTime()) + " Uhr? Zum Beispiel: 15.10 oder morgen";
            }
            return "Für welchen Tag und welche Uhrzeit? Zum Beispiel: 15.10 11:30";
        }

        Map<Long, ServiceOffering> byId = serviceList.stream().collect(Collectors.toMap(ServiceOffering::getId, x -> x));
        List<ServiceOffering> chosen = s.getServiceIds().stream().map(byId::get).filter(Objects::nonNull).toList();
        if (chosen.isEmpty()) {
            s.setState(State.AWAITING_SERVICE);
            return serviceQuestion(serviceList);
        }

        if (s.getCustomerName() == null) s.setCustomerName(cleanName(profileName));
        if (s.getCustomerName() == null) {
            s.setState(State.AWAITING_NAME);
            return "Fast geschafft! Wie ist dein Name?";
        }

        // 5) everything known: try to reserve the slot
        ZonedDateTime start = ZonedDateTime.of(s.getRequestedDate(), s.getRequestedTime(), shop.zone());
        if (!start.toInstant().isAfter(now)) {
            s.setRequestedTime(null);
            s.setState(State.AWAITING_DATETIME);
            return "Dieser Zeitpunkt liegt schon in der Vergangenheit. Welcher Tag und welche Uhrzeit passt dir?";
        }

        String[] names = s.getCustomerName().split("\\s+", 2);
        Instant holdUntil = now.plus(props.requestHold());
        Instant pendingUntil = holdUntil.isBefore(start.toInstant()) ? holdUntil : start.toInstant();

        Optional<Appointment> reserved = booking.reserveRequest(shop, chosen, s.getBarberId(), start.toInstant(),
                names[0], names.length > 1 ? names[1] : "", s.getPhone(), BookingSource.WHATSAPP, pendingUntil);

        if (reserved.isPresent()) {
            Appointment a = reserved.get();
            s.reset();
            return "Danke " + names[0] + "! Deine Anfrage ist eingegangen:\n"
                    + when(a.getStartsAt(), shop) + " – " + a.getServiceLabel() + " bei " + a.getBarber().getName() + ".\n"
                    + "Der Barber bestätigt sie in Kürze, du bekommst dann hier eine Nachricht.";
        }
        return suggestAlternatives(shop, s, chosen, start);
    }

    /** The wished time is taken: offer the closest free times that day, or the next day with free times. */
    private String suggestAlternatives(Shop shop, ChatSession s, List<ServiceOffering> service, ZonedDateTime wished) {
        String wishedText = "Um " + TIME.format(wished) + " Uhr ist am " + DAY.format(wished);
        s.setRequestedTime(null);
        s.setState(State.AWAITING_TIME);

        List<Slot> sameDay = availability.slots(shop, service, s.getBarberId(), wished.toLocalDate());
        if (!sameDay.isEmpty()) {
            List<String> closest = sameDay.stream()
                    .sorted(Comparator.comparingLong(slot -> Math.abs(Duration.between(wished.toInstant(), slot.start()).toMinutes())))
                    .limit(3)
                    .sorted(Comparator.comparing(Slot::start))
                    .map(slot -> TIME.format(slot.start().atZone(shop.zone())))
                    .toList();
            return wishedText + " leider schon besetzt. Frei wären: " + list(closest)
                    + " Uhr.\nAntworte einfach mit der Uhrzeit.";
        }

        for (int i = 1; i <= 14; i++) {
            LocalDate day = wished.toLocalDate().plusDays(i);
            List<Slot> slots = availability.slots(shop, service, s.getBarberId(), day);
            if (!slots.isEmpty()) {
                s.setRequestedDate(day);
                List<String> first = slots.stream().limit(3)
                        .map(slot -> TIME.format(slot.start().atZone(shop.zone()))).toList();
                return "Am " + DAY.format(wished) + " ist leider nichts mehr frei. Am " + DAY.format(day)
                        + " ginge: " + list(first) + " Uhr.\nAntworte einfach mit der Uhrzeit.";
            }
        }
        s.reset();
        return "In den nächsten zwei Wochen ist leider nichts mehr frei. Bitte ruf uns direkt an.";
    }

    // ============================================================================================
    // barber decisions and expiry
    // ============================================================================================

    @Transactional
    public Appointment confirm(Long shopId, Long appointmentId) {
        Appointment a = pending(shopId, appointmentId);
        a.changeStatus(AppointmentStatus.CONFIRMED);
        Shop shop = shops.findById(a.getShopId()).orElseThrow();
        if (a.getSource() == BookingSource.WHATSAPP) {
            send(shop, a.getCustomer().getPhone(), "✅ Bestätigt! Bis bald, " + a.getCustomer().getFirstName() + ".\n"
                    + when(a.getStartsAt(), shop) + " – " + a.getServiceLabel() + " bei " + a.getBarber().getName() + "\n"
                    + shop.getName() + ", " + shop.getStreet() + ", " + shop.getZipCity() + "\n"
                    + "Absagen kannst du hier: " + manageLink(a), clock.instant());
        }
        notifier.send(mailTexts.confirmation(a, shop)); // e-mail as well, if we know the address
        log.info("Request {} confirmed", a.getId());
        return a;
    }

    @Transactional
    public Appointment reject(Long shopId, Long appointmentId) {
        Appointment a = pending(shopId, appointmentId);
        a.changeStatus(AppointmentStatus.CANCELLED);
        Shop shop = shops.findById(a.getShopId()).orElseThrow();
        if (a.getSource() == BookingSource.WHATSAPP) {
            send(shop, a.getCustomer().getPhone(), "Leider können wir dir " + when(a.getStartsAt(), shop)
                    + " nicht anbieten. Schreib uns gern eine andere Uhrzeit, zum Beispiel „morgen 15:00“.", clock.instant());
        }
        log.info("Request {} rejected", a.getId());
        return a;
    }

    /** Releases requests the barber did not confirm in time, so the slot can be booked again. */
    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT1M")
    @Transactional
    public void expireRequests() {
        Instant now = clock.instant();
        for (Appointment a : appointments.findExpiredRequests(now)) {
            a.changeStatus(AppointmentStatus.CANCELLED);
            Shop shop = shops.findById(a.getShopId()).orElseThrow();
            if (a.getSource() == BookingSource.WHATSAPP) {
                send(shop, a.getCustomer().getPhone(), "Deine Anfrage für " + when(a.getStartsAt(), shop)
                        + " konnte leider nicht rechtzeitig bestätigt werden, der Termin ist wieder frei. "
                        + "Schreib uns gern eine neue Uhrzeit oder ruf an.", now);
            }
            log.info("Request {} expired", a.getId());
        }
    }

    // ============================================================================================
    // reading conversations (admin)
    // ============================================================================================

    @Transactional(readOnly = true)
    public List<ConversationView> conversations(Long shopId) {
        Map<String, ChatMessage> latest = new LinkedHashMap<>();
        for (ChatMessage m : chatMessages.findByShopIdOrderByCreatedAtDescIdDesc(shopId, PageRequest.of(0, 500))) {
            latest.putIfAbsent(m.getPhone(), m);
        }
        return latest.values().stream()
                .map(m -> new ConversationView(m.getPhone(),
                        sessions.findByShopIdAndPhone(shopId, m.getPhone()).map(ChatSession::getCustomerName).orElse(null),
                        m.getText(), m.getDirection().name(), m.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageView> messages(Long shopId, String rawPhone) {
        String phone = BookingService.normalizePhone(rawPhone);
        return chatMessages.findByShopIdAndPhoneOrderByCreatedAtAscIdAsc(shopId, phone).stream()
                .map(m -> new ChatMessageView(m.getId(), m.getDirection().name(), m.getText(), m.getCreatedAt()))
                .toList();
    }

    // ============================================================================================
    // helpers
    // ============================================================================================

    private Appointment pending(Long shopId, Long id) {
        Appointment a = appointments.findWithDetails(id)
                .filter(x -> x.getShopId().equals(shopId))
                .orElseThrow(() -> new NotFoundException("Termin nicht gefunden."));
        if (a.getStatus() != AppointmentStatus.PENDING) {
            throw new BusinessRuleException("Diese Anfrage wurde bereits bearbeitet.");
        }
        return a;
    }

    private void send(Shop shop, String phone, String text, Instant now) {
        chatMessages.save(new ChatMessage(shop.getId(), phone, Direction.OUT, text, now));
        try {
            gateway.send(shop, phone, text);
        } catch (RuntimeException ex) {
            log.warn("Chat message to {} could not be delivered: {}", phone, ex.getMessage());
        }
    }

    private String help(Shop shop) {
        return "Hier kannst du einen Termin beim " + shop.getName() + " anfragen. "
                + "Schreib einfach deinen Namen, den Tag und die Uhrzeit, zum Beispiel:\n"
                + "„Amir 15.10 11:30“ oder „morgen 17 Uhr bei Sami“.";
    }

    /** Stores several numbered choices; returns an error reply if a number is out of range, else null. */
    private static String chooseServices(ChatSession s, List<Integer> numbers, List<ServiceOffering> serviceList) {
        List<Long> ids = new ArrayList<>();
        for (int n : numbers) {
            if (n < 1 || n > serviceList.size()) {
                return "Bitte antworte mit Zahlen von 1 bis " + serviceList.size() + ".\n\n" + serviceQuestion(serviceList);
            }
            Long id = serviceList.get(n - 1).getId();
            if (!ids.contains(id)) ids.add(id);
        }
        if (ids.size() > 6) return "Bitte wähle höchstens 6 Leistungen.";
        s.setServiceIds(ids);
        return null;
    }

    private static String serviceQuestion(List<ServiceOffering> serviceList) {
        StringBuilder sb = new StringBuilder("Welche Leistung möchtest du? Antworte mit der Nummer"
                + " (mehrere gehen auch, z. B. „1 und 3“):");
        for (int i = 0; i < serviceList.size(); i++) {
            ServiceOffering x = serviceList.get(i);
            sb.append("\n").append(i + 1).append("  ").append(x.getName())
                    .append(" (").append(x.getDurationMinutes()).append(" Min., ")
                    .append(euro(x.getPriceCents())).append(")");
        }
        return sb.toString();
    }

    private String when(Instant start, Shop shop) {
        ZonedDateTime z = start.atZone(shop.zone());
        return DAY.format(z) + " um " + TIME.format(z) + " Uhr";
    }

    private String manageLink(Appointment a) {
        return bookingProps.publicBaseUrl() + "/termin/" + a.getManageToken();
    }

    private static String list(List<String> items) {
        if (items.size() == 1) return items.get(0);
        return String.join(", ", items.subList(0, items.size() - 1)) + " oder " + items.get(items.size() - 1);
    }

    private static String euro(int cents) {
        return cents % 100 == 0 ? (cents / 100) + " €" : String.format(Locale.GERMAN, "%.2f €", cents / 100.0);
    }

    /** "  amir   siasi " -> "Amir Siasi"; null if it does not look like a name. */
    static String cleanName(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replaceAll("[^\\p{L}\\s'-]", "").replaceAll("\\s+", " ");
        if (s.length() < 2 || s.length() > 50) return null;
        return Arrays.stream(s.split(" "))
                .map(w -> w.substring(0, 1).toUpperCase(Locale.GERMAN) + w.substring(1))
                .collect(Collectors.joining(" "));
    }
}
