package com.issuemanagement.audit.service;

import com.issuemanagement.audit.dto.AuditLogResponse;
import com.issuemanagement.audit.entity.AuditLog;
import com.issuemanagement.audit.repository.AuditLogRepository;
import com.issuemanagement.common.dto.PageResponse;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.repository.UserRepository;
import com.issuemanagement.identity.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void logEvent(User actor, String action, String entityType, String entityId, String details) {
        AuditLog auditLog = AuditLog.builder()
                .actor(actor)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .build();
        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void logCurrentActorEvent(String action, String entityType, String entityId, String details) {
        User actor = null;
        try {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            if (currentUserId != null) {
                actor = userRepository.findById(currentUserId).orElse(null);
            }
        } catch (Exception ignored) {
        }
        logEvent(actor, action, entityType, entityId, details);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AuditLogResponse> getAuditLogs(String entityType, Pageable pageable) {
        Pageable sanitizedPageable = com.issuemanagement.common.util.PageableUtils.sanitize(pageable);
        Page<AuditLog> page = (entityType != null && !entityType.isBlank())
                ? auditLogRepository.findByEntityType(entityType, sanitizedPageable)
                : auditLogRepository.findAll(sanitizedPageable);
        return PageResponse.from(page.map(AuditLogResponse::fromEntity));
    }
}
