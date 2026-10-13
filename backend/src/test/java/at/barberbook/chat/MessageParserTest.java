package at.barberbook.chat;

import at.barberbook.chat.MessageParser.Command;
import at.barberbook.chat.MessageParser.Parsed;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13); // a Tuesday
    private static final List<String> BARBERS = List.of("Reza", "Sami");
    private static final List<String> SERVICES = List.of("Schneiden & Föhnen", "Bart kürzen & stylen",
            "Haarschnitt & Bartschnitt", "Voll-Service", "Augenbrauen zupfen");

    private static Parsed parse(String text) {
        return MessageParser.parse(text, TODAY, BARBERS, SERVICES);
    }

    @Test
    void name_time_and_date() {
        Parsed p = parse("amir 11:30 15.10");
        assertThat(p.name()).isEqualTo("Amir");
        assertThat(p.time()).isEqualTo(LocalTime.of(11, 30));
        assertThat(p.date()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void dotted_time_and_date_in_any_order() {
        assertThat(parse("Amir 11.30 15.10").time()).isEqualTo(LocalTime.of(11, 30));
        Parsed p = parse("15.10. 11.30 Amir Siasi");
        assertThat(p.date()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(p.time()).isEqualTo(LocalTime.of(11, 30));
        assertThat(p.name()).isEqualTo("Amir Siasi");
    }

    @Test
    void relative_day_hour_with_uhr_and_barber() {
        Parsed p = parse("Hallo, ist morgen 17 Uhr bei Sami frei?");
        assertThat(p.date()).isEqualTo(TODAY.plusDays(1));
        assertThat(p.time()).isEqualTo(LocalTime.of(17, 0));
        assertThat(p.barberName()).isEqualTo("Sami");
        assertThat(p.name()).isNull();
    }

    @Test
    void weekday_and_full_service_name() {
        Parsed p = parse("Max Muster Donnerstag 9.30 Bart kürzen & stylen");
        assertThat(p.date()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(p.time()).isEqualTo(LocalTime.of(9, 30));
        assertThat(p.serviceName()).isEqualTo("Bart kürzen & stylen");
        assertThat(p.name()).isEqualTo("Max Muster");
    }

    @Test
    void any_barber_and_today() {
        Parsed p = parse("Hi ich bin Lukas, heute 18:15 egal wer");
        assertThat(p.name()).isEqualTo("Lukas");
        assertThat(p.date()).isEqualTo(TODAY);
        assertThat(p.anyBarber()).isTrue();
    }

    @Test
    void a_date_already_passed_this_year_means_next_year() {
        assertThat(parse("05.01 10:00 Leon").date()).isEqualTo(LocalDate.of(2027, 1, 5));
    }

    @Test
    void impossible_dates_are_ignored() {
        Parsed p = parse("Amir 31.02 10:00");
        assertThat(p.date()).isNull();
        assertThat(p.time()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void short_answers_and_commands() {
        assertThat(parse("2").choice()).isEqualTo(2);
        assertThat(parse("11:45").time()).isEqualTo(LocalTime.of(11, 45));
        assertThat(parse("hilfe").command()).isEqualTo(Command.HELP);
        assertThat(parse("Abbrechen").command()).isEqualTo(Command.CANCEL);
    }

    @Test
    void greeting_is_not_mistaken_for_tomorrow() {
        Parsed p = parse("Guten Morgen, übermorgen um 14 uhr");
        assertThat(p.date()).isEqualTo(TODAY.plusDays(2));
        assertThat(p.time()).isEqualTo(LocalTime.of(14, 0));
        assertThat(p.name()).isNull();
    }

    @Test
    void several_numbers_mean_several_services() {
        assertThat(MessageParser.numberList("1 und 3")).containsExactly(1, 3);
        assertThat(MessageParser.numberList("1, 4")).containsExactly(1, 4);
        assertThat(MessageParser.numberList("2+5")).containsExactly(2, 5);
        assertThat(MessageParser.numberList("2")).containsExactly(2);
        assertThat(MessageParser.numberList("15.10")).isEmpty();
        assertThat(MessageParser.numberList("11:30")).isEmpty();
        assertThat(MessageParser.numberList("Amir 1 und 3")).isEmpty();
    }
}
