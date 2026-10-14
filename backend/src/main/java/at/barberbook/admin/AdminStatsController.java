package at.barberbook.admin;

import at.barberbook.common.Exceptions.BusinessRuleException;
import at.barberbook.shop.Barber;
import at.barberbook.shop.BarberRepository;
import at.barberbook.shop.Shop;
import at.barberbook.staff.CurrentStaff;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Figures for the owner: appointments and revenue per week, month or year, per barber, top services,
 * where bookings come from, and the same numbers for the period before (for "+12 %").
 *
 * <p>Counting rules, so the numbers mean the same everywhere:
 * <ul>
 *   <li><b>Termine</b> = confirmed + completed + no-show (everything that was really booked)</li>
 *   <li><b>Umsatz</b> = price of confirmed + completed (no-shows and cancellations bring no money; future
 *       confirmed appointments count as expected revenue)</li>
 *   <li>open requests (PENDING) are not counted yet</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    public enum Unit { WEEK, MONTH, YEAR }

    public record Bucket(LocalDate start, int appointments, long revenueCents, int cancelled, int noShows) {
    }

    public record Totals(int appointments, long revenueCents, int cancelled, int noShows, int newCustomers,
                         long averageCents) {
    }

    public record BarberStat(Long barberId, String name, int appointments, long revenueCents) {
    }

    public record ServiceStat(String name, int count, long revenueCents) {
    }

    public record SourceStat(String source, int appointments) {
    }

    public record StatsView(Unit unit, LocalDate from, LocalDate to, Long barberId, List<Bucket> buckets,
                            Totals totals, Totals previous, List<BarberStat> barbers, List<ServiceStat> services,
                            List<SourceStat> sources) {
    }

    private static final Map<Unit, Integer> BUCKETS = Map.of(Unit.WEEK, 12, Unit.MONTH, 12, Unit.YEAR, 5);
    private static final String BOOKED = "('CONFIRMED','COMPLETED','NO_SHOW')";
    private static final String PAID = "('CONFIRMED','COMPLETED')";

    private final CurrentStaff current;
    private final BarberRepository barbers;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public AdminStatsController(CurrentStaff current, BarberRepository barbers, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.current = current;
        this.barbers = barbers;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * @param unit     week, month or year
     * @param end      a day inside the last period shown (default: today) - for paging back in time
     * @param barberId only this barber (optional)
     */
    @GetMapping
    @Transactional(readOnly = true)
    public StatsView stats(@RequestParam(defaultValue = "MONTH") String unit,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                           @RequestParam(required = false) Long barberId) {
        Unit u = parseUnit(unit);
        Shop shop = current.shop();
        ZoneId zone = shop.zone();
        LocalDate last = periodStart(u, end != null ? end : LocalDate.now(clock.withZone(zone)));
        int count = BUCKETS.get(u);
        LocalDate from = plus(u, last, -(count - 1));
        LocalDate to = plus(u, last, 1); // exclusive
        LocalDate prevFrom = plus(u, from, -count);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("shop", shop.getId())
                .addValue("tz", shop.getTimeZone())
                .addValue("unit", u.name().toLowerCase())
                .addValue("from", ts(from, zone))
                .addValue("to", ts(to, zone))
                .addValue("prevFrom", ts(prevFrom, zone))
                .addValue("barber", barberId);
        String barberFilter = barberId == null ? "" : " and a.barber_id = :barber";

        // one row per period / status / source / barber - everything else is added up here
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select (date_trunc(:unit, a.starts_at at time zone :tz))::date as bucket,
                       a.status, a.source, a.barber_id, count(*) as n, coalesce(sum(a.price_cents), 0) as cents
                from appointment a
                where a.shop_id = :shop and a.starts_at >= :from and a.starts_at < :to""" + barberFilter + """

                group by 1, 2, 3, 4""", p);

        Map<LocalDate, int[]> counts = new LinkedHashMap<>();   // booked, cancelled, noShows
        Map<LocalDate, long[]> money = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            counts.put(plus(u, from, i), new int[3]);
            money.put(plus(u, from, i), new long[1]);
        }
        Map<Long, long[]> perBarber = new LinkedHashMap<>();    // appointments, revenue
        Map<String, Integer> perSource = new TreeMap<>();

        for (Map<String, Object> r : rows) {
            LocalDate bucket = ((java.sql.Date) r.get("bucket")).toLocalDate();
            String status = (String) r.get("status");
            int n = ((Number) r.get("n")).intValue();
            long cents = ((Number) r.get("cents")).longValue();
            int[] c = counts.get(bucket);
            if (c == null) continue;
            boolean booked = status.equals("CONFIRMED") || status.equals("COMPLETED") || status.equals("NO_SHOW");
            boolean paid = status.equals("CONFIRMED") || status.equals("COMPLETED");
            if (booked) c[0] += n;
            if (status.equals("CANCELLED")) c[1] += n;
            if (status.equals("NO_SHOW")) c[2] += n;
            if (paid) money.get(bucket)[0] += cents;
            if (booked) {
                long[] b = perBarber.computeIfAbsent(((Number) r.get("barber_id")).longValue(), k -> new long[2]);
                b[0] += n;
                if (paid) b[1] += cents;
                perSource.merge((String) r.get("source"), n, Integer::sum);
            }
        }

        List<Bucket> buckets = new ArrayList<>();
        int booked = 0, cancelled = 0, noShows = 0;
        long revenue = 0;
        for (LocalDate d : counts.keySet()) {
            int[] c = counts.get(d);
            long cents = money.get(d)[0];
            buckets.add(new Bucket(d, c[0], cents, c[1], c[2]));
            booked += c[0]; cancelled += c[1]; noShows += c[2]; revenue += cents;
        }
        Totals totals = new Totals(booked, revenue, cancelled, noShows,
                newCustomers(p, "from", "to", barberFilter), average(revenue, booked - noShows));
        Totals previous = totals(p, barberFilter);

        Map<Long, String> names = new HashMap<>();
        barbers.findByShopIdOrderBySortOrderAscIdAsc(shop.getId()).forEach(b -> names.put(b.getId(), b.getName()));
        List<BarberStat> barberStats = new ArrayList<>();
        // every active barber appears (also with 0), plus former barbers that still have figures
        barbers.findByShopIdOrderBySortOrderAscIdAsc(shop.getId()).stream()
                .filter(b -> b.isActive() || perBarber.containsKey(b.getId()))
                .filter(b -> barberId == null || b.getId().equals(barberId))
                .map(Barber::getId)
                .forEach(id -> {
                    long[] v = perBarber.getOrDefault(id, new long[2]);
                    barberStats.add(new BarberStat(id, names.get(id), (int) v[0], v[1]));
                });

        List<ServiceStat> services = jdbc.query("""
                select i.name, count(*) as n, coalesce(sum(i.price_cents), 0) as cents
                from appointment_item i join appointment a on a.id = i.appointment_id
                where a.shop_id = :shop and a.starts_at >= :from and a.starts_at < :to
                  and a.status in""" + " " + PAID + barberFilter + """

                group by i.name
                order by n desc, cents desc
                limit 8""", p, (rs, i) -> new ServiceStat(rs.getString("name"), rs.getInt("n"), rs.getLong("cents")));

        List<SourceStat> sources = perSource.entrySet().stream()
                .map(e -> new SourceStat(e.getKey(), e.getValue())).toList();

        return new StatsView(u, from, to.minusDays(1), barberId, buckets, totals, previous, barberStats, services, sources);
    }

    // ---- helpers -------------------------------------------------------------------------------------

    private Totals totals(MapSqlParameterSource p, String barberFilter) {
        Map<String, Object> r = jdbc.queryForMap("""
                select count(*) filter (where a.status in""" + " " + BOOKED + """
                ) as booked,
                       count(*) filter (where a.status = 'CANCELLED') as cancelled,
                       count(*) filter (where a.status = 'NO_SHOW') as no_shows,
                       coalesce(sum(a.price_cents) filter (where a.status in""" + " " + PAID + """
                ), 0) as cents
                from appointment a
                where a.shop_id = :shop and a.starts_at >= :prevFrom and a.starts_at < :from""" + barberFilter, p);
        int booked = ((Number) r.get("booked")).intValue();
        int noShows = ((Number) r.get("no_shows")).intValue();
        long cents = ((Number) r.get("cents")).longValue();
        return new Totals(booked, cents, ((Number) r.get("cancelled")).intValue(), noShows,
                newCustomers(p, "prevFrom", "from", barberFilter), average(cents, booked - noShows));
    }

    /** Customers whose very first booked appointment in this shop falls into the period. */
    private int newCustomers(MapSqlParameterSource p, String fromParam, String toParam, String barberFilter) {
        Integer n = jdbc.queryForObject("""
                select count(*) from (
                    select a.customer_id, min(a.starts_at) as first_visit
                    from appointment a
                    where a.shop_id = :shop and a.status in""" + " " + BOOKED + """

                    group by a.customer_id
                ) f
                where f.first_visit >= :%s and f.first_visit < :%s
                  and exists (select 1 from appointment a where a.customer_id = f.customer_id
                              and a.starts_at = f.first_visit%s)""".formatted(fromParam, toParam, barberFilter), p, Integer.class);
        return n == null ? 0 : n;
    }

    private static long average(long cents, int paidAppointments) {
        return paidAppointments <= 0 ? 0 : Math.round((double) cents / paidAppointments);
    }

    private static Unit parseUnit(String raw) {
        try {
            return Unit.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Zeitraum muss week, month oder year sein.");
        }
    }

    static LocalDate periodStart(Unit u, LocalDate d) {
        return switch (u) {
            case WEEK -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> d.withDayOfMonth(1);
            case YEAR -> d.withDayOfYear(1);
        };
    }

    static LocalDate plus(Unit u, LocalDate d, int n) {
        return switch (u) {
            case WEEK -> d.plusWeeks(n);
            case MONTH -> d.plusMonths(n);
            case YEAR -> d.plusYears(n);
        };
    }

    /** OffsetDateTime is sent as timestamptz, so the comparison is exact whatever the DB session zone is. */
    private static OffsetDateTime ts(LocalDate d, ZoneId zone) {
        return d.atStartOfDay(zone).toOffsetDateTime();
    }
}
