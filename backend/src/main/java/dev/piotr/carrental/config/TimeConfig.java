package dev.piotr.carrental.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Time infrastructure.
 *
 * The clock everything "is it in the past?" logic goes through (instantiated
 * instead of static so unit tests can freeze time), and the single booking
 * zone that turns the manager's wall-clock input into a persisted instant.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ZoneId bookingZone(@Value("${carrental.booking-zone}") String zone) {
        return ZoneId.of(zone);
    }
}
