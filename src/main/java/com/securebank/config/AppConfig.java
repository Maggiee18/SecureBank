package com.securebank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppConfig {

    /**
     * Injected instead of calling now() directly so time-dependent logic (login lockout,
     * reference dates) can be tested with a fixed clock.
     */
    @Bean
    public Clock clock(@Value("${banking.business-zone:Asia/Kolkata}") String businessZone) {
        return Clock.system(ZoneId.of(businessZone));
    }
}
