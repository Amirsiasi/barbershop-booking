package at.barberbook.booking;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNormalizationTest {

    @ParameterizedTest
    @CsvSource({
            "'0660 123 45 67',    +436601234567",
            "'+43 660 1234567',   +436601234567",
            "'0043/660-1234567',  +436601234567",
            "'0049 151 2345678',  +491512345678",
            "'660 1234567',       +436601234567"
    })
    void normalizes_to_international_format(String input, String expected) {
        assertThat(BookingService.normalizePhone(input)).isEqualTo(expected);
    }
}
