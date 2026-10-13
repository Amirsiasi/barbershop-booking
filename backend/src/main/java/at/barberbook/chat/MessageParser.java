package at.barberbook.chat;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Understands short booking messages like they are typed on a phone:
 * <pre>
 *   Amir 11:30 15.10
 *   Hallo, morgen 17 Uhr bei Sami?
 *   Max Muster Donnerstag 9.30 Bart kürzen &amp; stylen
 * </pre>
 * Pure function without Spring or a database, so it is easy to unit test.
 * Everything it cannot recognise stays {@code null} and the conversation asks for it.
 */
public final class MessageParser {

    public enum Command { NONE, HELP, CANCEL }

    /**
     * @param choice      the message is just a number ("2"), e.g. an answer to "Welche Leistung?"
     * @param serviceName the name of a service that appears in the message, if any
     */
    public record Parsed(String name, LocalDate date, LocalTime time, String barberName, boolean anyBarber,
                         Integer choice, String serviceName, Command command) {
    }

    private static final Pattern CLOCK = Pattern.compile("(?<![\\d.:])(\\d{1,2}):(\\d{2})(?![\\d])");
    private static final Pattern HOUR_UHR = Pattern.compile("(?<![\\d.:])(\\d{1,2})(?:[.:](\\d{2}))?\\s*(?:uhr|h)\\b");
    private static final Pattern DOTTED = Pattern.compile("(?<![\\d.:])(\\d{1,2})\\.(\\d{1,2})(\\.)?(\\d{2,4})?(?![\\d:])");
    private static final Pattern ONLY_NUMBER = Pattern.compile("^\\s*(\\d{1,2})\\s*[.)]?\\s*$");
    private static final Pattern WORD = Pattern.compile("[\\p{L}][\\p{L}'-]*");

    private static final Map<String, DayOfWeek> WEEKDAYS = Map.ofEntries(
            Map.entry("montag", DayOfWeek.MONDAY), Map.entry("dienstag", DayOfWeek.TUESDAY),
            Map.entry("mittwoch", DayOfWeek.WEDNESDAY), Map.entry("donnerstag", DayOfWeek.THURSDAY),
            Map.entry("freitag", DayOfWeek.FRIDAY), Map.entry("samstag", DayOfWeek.SATURDAY),
            Map.entry("sonntag", DayOfWeek.SUNDAY));

    /** Words that are never a name. */
    private static final Set<String> STOP_WORDS = Set.of(
            "hallo", "hi", "hey", "servus", "grüß", "gruss", "grüss", "gott", "guten", "tag", "abend", "moin", "salam",
            "ich", "bin", "mein", "name", "ist", "heiße", "heisse", "hier", "wäre", "waere", "geht", "gehts", "es",
            "termin", "termine", "um", "am", "an", "für", "fuer", "bei", "beim", "zum", "zur", "uhr", "h", "bitte",
            "danke", "frei", "noch", "möchte", "moechte", "will", "würde", "wuerde", "gerne", "gern", "einen", "ein",
            "eine", "der", "die", "das", "und", "oder", "mit", "von", "vom", "den", "dem", "nächste", "naechste",
            "nächsten", "naechsten", "woche", "haarschnitt", "haare", "haar", "bart", "schneiden", "fade", "frisur",
            "egal", "wer", "irgendwer", "jemand", "heute", "morgen", "übermorgen", "uebermorgen", "ok", "okay", "ja");

    private MessageParser() {
    }

    public static Parsed parse(String text, LocalDate today, Collection<String> barberNames, Collection<String> serviceNames) {
        String raw = text == null ? "" : text.trim();
        String lower = raw.toLowerCase(Locale.GERMAN);

        if (lower.matches("^(hilfe|help|\\?|info)[!.]*$")) return empty(Command.HELP);
        if (lower.matches("^(abbrechen|abbruch|stop|stopp|cancel|nein danke)[!.]*$")) return empty(Command.CANCEL);

        Matcher number = ONLY_NUMBER.matcher(lower);
        if (number.matches()) {
            return new Parsed(null, null, null, null, false, Integer.parseInt(number.group(1)), null, Command.NONE);
        }

        // a service name written out in full ("Bart kürzen & stylen"); the longest match wins
        String serviceName = serviceNames.stream()
                .filter(s -> lower.contains(s.toLowerCase(Locale.GERMAN)))
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
        String rest = serviceName == null ? lower : lower.replace(serviceName.toLowerCase(Locale.GERMAN), " ");

        // --- time -------------------------------------------------------------------------------
        LocalTime time = null;
        Matcher clock = CLOCK.matcher(rest);
        if (clock.find()) {
            time = time(clock.group(1), clock.group(2));
            rest = cut(rest, clock);
        } else {
            Matcher uhr = HOUR_UHR.matcher(rest);
            if (uhr.find()) {
                time = time(uhr.group(1), uhr.group(2) == null ? "00" : uhr.group(2));
                rest = cut(rest, uhr);
            }
        }

        // --- dates and dotted times like 11.30 -------------------------------------------------
        LocalDate date = null;
        List<int[]> ambiguous = new ArrayList<>(); // {a, b} that could be a date or a time
        Matcher dotted = DOTTED.matcher(rest);
        StringBuilder withoutDotted = new StringBuilder();
        while (dotted.find()) {
            int a = Integer.parseInt(dotted.group(1));
            int b = Integer.parseInt(dotted.group(2));
            boolean surelyDate = dotted.group(3) != null || dotted.group(4) != null; // "15.10." or "15.10.2026"
            boolean canBeDate = isDate(a, b);
            boolean canBeTime = !surelyDate && a <= 23 && b <= 59 && dotted.group(2).length() == 2;
            if (canBeDate && (surelyDate || !canBeTime)) {
                if (date == null) date = date(a, b, dotted.group(4), today);
            } else if (canBeTime && !canBeDate) {
                if (time == null) time = LocalTime.of(a, b);
            } else if (canBeDate) {
                ambiguous.add(new int[]{a, b});
            }
            dotted.appendReplacement(withoutDotted, " ");
        }
        dotted.appendTail(withoutDotted);
        rest = withoutDotted.toString();

        // "11.30 15.10": the first ambiguous value is the time, the second the date (people write time first)
        for (int[] v : ambiguous) {
            if (time == null && date != null) time = LocalTime.of(v[0], v[1]);
            else if (date == null && time != null) date = date(v[0], v[1], null, today);
            else if (time == null && ambiguous.size() > 1) time = LocalTime.of(v[0], v[1]);
            else if (date == null) date = date(v[0], v[1], null, today);
        }

        // --- relative days and weekdays ---------------------------------------------------------
        List<String> words = new ArrayList<>();
        Matcher w = WORD.matcher(rest);
        while (w.find()) words.add(w.group());

        if (date == null) {
            if (words.contains("übermorgen") || words.contains("uebermorgen")) date = today.plusDays(2);
            else if (words.contains("morgen") && !lower.contains("guten morgen")) date = today.plusDays(1);
            else if (words.contains("heute")) date = today;
            else {
                for (String word : words) {
                    DayOfWeek day = WEEKDAYS.get(word);
                    if (day != null) {
                        date = today.with(TemporalAdjusters.nextOrSame(day));
                        break;
                    }
                }
            }
        }

        // --- barber -----------------------------------------------------------------------------
        String barber = null;
        for (String b : barberNames) {
            if (words.contains(b.toLowerCase(Locale.GERMAN))) {
                barber = b;
                break;
            }
        }
        boolean any = barber == null && (words.contains("egal") || words.contains("irgendwer") || words.contains("jemand"));

        // --- name: the remaining "real" words, at most two ----------------------------------------
        Set<String> notNames = new HashSet<>(STOP_WORDS);
        notNames.addAll(WEEKDAYS.keySet());
        barberNames.forEach(b -> notNames.add(b.toLowerCase(Locale.GERMAN)));
        serviceNames.forEach(s -> Arrays.stream(s.toLowerCase(Locale.GERMAN).split("[^\\p{L}]+")).forEach(notNames::add));

        List<String> nameParts = new ArrayList<>();
        Matcher original = WORD.matcher(raw);
        while (original.find() && nameParts.size() < 2) {
            String token = original.group();
            if (token.length() >= 2 && !notNames.contains(token.toLowerCase(Locale.GERMAN))) {
                nameParts.add(capitalize(token));
            }
        }
        String name = nameParts.isEmpty() ? null : String.join(" ", nameParts);

        return new Parsed(name, date, time, barber, any, null, serviceName, Command.NONE);
    }

    private static final Pattern NUMBER_LIST =
            Pattern.compile("^\\s*\\d{1,2}(\\s*(,|\\+|&|und|u\\.|and|\\s)\\s*\\d{1,2})*\\s*\\.?\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * "1 und 3", "1, 3", "1+3" -> [1, 3]. Only messages that consist of nothing but small numbers count;
     * anything else (a date like "15.10", a time) returns an empty list.
     */
    public static List<Integer> numberList(String text) {
        if (text == null || text.contains(":") || text.matches(".*\\d\\.\\d.*") || !NUMBER_LIST.matcher(text).matches()) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        Matcher m = Pattern.compile("\\d{1,2}").matcher(text);
        while (m.find()) result.add(Integer.parseInt(m.group()));
        return result;
    }

    /** A message that carries no information (used after it was already handled otherwise). */
    public static Parsed nothing() {
        return empty(Command.NONE);
    }

    private static Parsed empty(Command c) {
        return new Parsed(null, null, null, null, false, null, null, c);
    }

    private static String cut(String s, Matcher m) {
        return s.substring(0, m.start()) + " " + s.substring(m.end());
    }

    private static LocalTime time(String h, String m) {
        int hour = Integer.parseInt(h);
        int minute = Integer.parseInt(m);
        return hour <= 23 && minute <= 59 ? LocalTime.of(hour, minute) : null;
    }

    private static boolean isDate(int day, int month) {
        return day >= 1 && day <= 31 && month >= 1 && month <= 12;
    }

    /** A date without year means the next such date: in December, "05.01" is January next year. */
    private static LocalDate date(int day, int month, String year, LocalDate today) {
        try {
            if (year != null) {
                int y = Integer.parseInt(year);
                return LocalDate.of(y < 100 ? 2000 + y : y, month, day);
            }
            LocalDate d = LocalDate.of(today.getYear(), month, day);
            return d.isBefore(today) ? d.plusYears(1) : d;
        } catch (DateTimeException e) {
            return null; // 31.02 and similar
        }
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase(Locale.GERMAN) + s.substring(1);
    }
}
