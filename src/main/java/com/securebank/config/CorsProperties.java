package com.securebank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Browser origins allowed to call the API, e.g. the Vite dev server or the deployed frontend. */
@ConfigurationProperties(prefix = "banking.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
