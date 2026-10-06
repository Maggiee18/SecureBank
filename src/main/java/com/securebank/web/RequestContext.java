package com.securebank.web;

import org.slf4j.MDC;

/**
 * Keys and accessors for per-request diagnostic context. Values live in the SLF4J MDC so
 * every log line written while handling a request carries them automatically.
 */
public final class RequestContext {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_ID = "requestId";
    public static final String CUSTOMER_ID = "customerId";
    public static final String CLIENT_IP = "clientIp";

    private RequestContext() {
    }

    public static String requestId() {
        return MDC.get(REQUEST_ID);
    }

    public static String clientIp() {
        return MDC.get(CLIENT_IP);
    }
}
