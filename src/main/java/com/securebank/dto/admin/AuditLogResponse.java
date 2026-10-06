package com.securebank.dto.admin;

import com.securebank.entity.AuditLog;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long customerId,
        String action,
        String resource,
        String resourceId,
        String status,
        String description,
        String ipAddress,
        String requestId,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getCustomerId(),
                log.getAction().name(),
                log.getResource(),
                log.getResourceId(),
                log.getStatus().name(),
                log.getDescription(),
                log.getIpAddress(),
                log.getRequestId(),
                log.getCreatedAt());
    }
}
