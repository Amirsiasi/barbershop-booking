package at.barberbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan
public class BarberBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(BarberBookApplication.class, args);
    }

    /** Injected everywhere "now" is needed, so tests can freeze time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
