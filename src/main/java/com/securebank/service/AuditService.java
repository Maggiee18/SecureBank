package com.securebank.service;

import com.securebank.dto.admin.AuditLogResponse;
import com.securebank.dto.common.PageResponse;
import com.securebank.entity.AuditAction;
import com.securebank.entity.AuditLog;
import com.securebank.entity.AuditStatus;
import com.securebank.repository.AuditLogRepository;
import com.securebank.web.RequestContext;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two ways to write an audit entry:
 * <ul>
 *   <li>{@link #recordSuccess} joins the caller's transaction, so a successful transfer and its
 *       audit row commit or roll back together.</li>
 *   <li>{@link #recordFailure} always uses a new transaction, so the record of a failed or
 *       rejected action survives even though the business transaction rolled back.</li>
 * </ul>
 * Request id and client IP come from the MDC populated by RequestIdFilter.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void recordSuccess(Long customerId, AuditAction action, String resource, String resourceId,
                              String description) {
        save(customerId, action, resource, resourceId, AuditStatus.SUCCESS, description);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long customerId, AuditAction action, String resource, String resourceId,
                              String description) {
        save(customerId, action, resource, resourceId, AuditStatus.FAILURE, description);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(Long customerId, Pageable pageable) {
        if (customerId == null) {
            return PageResponse.from(auditLogRepository.findAll(pageable).map(AuditLogResponse::from));
        }
        return PageResponse.from(auditLogRepository.findByCustomerId(customerId, pageable).map(AuditLogResponse::from));
    }

    private void save(Long customerId, AuditAction action, String resource, String resourceId, AuditStatus status,
                      String description) {
        auditLogRepository.save(new AuditLog(customerId, action, resource, resourceId, status, description,
                RequestContext.clientIp(), RequestContext.requestId()));
    }
}
