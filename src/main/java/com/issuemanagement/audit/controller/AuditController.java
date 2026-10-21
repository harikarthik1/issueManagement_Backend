package com.issuemanagement.audit.controller;

import com.issuemanagement.audit.dto.AuditLogResponse;
import com.issuemanagement.audit.service.AuditService;
import com.issuemanagement.common.dto.ApiResponse;
import com.issuemanagement.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Endpoints for viewing administrative audit logs")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Get system audit logs (Admin only)")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getAuditLogs(
            @RequestParam(required = false) String entityType,
            @org.springdoc.core.annotations.ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<AuditLogResponse> response = auditService.getAuditLogs(entityType, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
