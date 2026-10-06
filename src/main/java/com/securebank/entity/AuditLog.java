package com.securebank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only security and business event. customerId is a plain column rather than a
 * foreign key so that failed logins for unknown emails can be recorded and audit history
 * is never affected by changes to customer rows.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    private static final int MAX_DESCRIPTION_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", updatable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40, updatable = false)
    private AuditAction action;

    @Column(name = "resource", nullable = false, length = 40, updatable = false)
    private String resource;

    @Column(name = "resource_id", length = 64, updatable = false)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, updatable = false)
    private AuditStatus status;

    @Column(name = "description", length = MAX_DESCRIPTION_LENGTH, updatable = false)
    private String description;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
        // required by JPA
    }

    public AuditLog(Long customerId, AuditAction action, String resource, String resourceId, AuditStatus status,
                    String description, String ipAddress, String requestId) {
        this.customerId = customerId;
        this.action = action;
        this.resource = resource;
        this.resourceId = resourceId;
        this.status = status;
        this.description = description != null && description.length() > MAX_DESCRIPTION_LENGTH
                ? description.substring(0, MAX_DESCRIPTION_LENGTH)
                : description;
        this.ipAddress = ipAddress;
        this.requestId = requestId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getResource() {
        return resource;
    }

    public String getResourceId() {
        return resourceId;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
