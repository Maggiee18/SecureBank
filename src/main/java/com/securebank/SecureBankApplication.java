package com.securebank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SecureBankApplication {

    public static void main(String[] args) {
        // The PostgreSQL driver sends the JVM time zone to the server. Legacy ids such as
        // "Asia/Calcutta" (the default on many Indian machines) are rejected by some PostgreSQL
        // builds, so the JVM runs in UTC. All timestamps are Instants, so nothing else changes.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(SecureBankApplication.class, args);
    }
}
