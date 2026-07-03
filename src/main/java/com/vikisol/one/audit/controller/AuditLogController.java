package com.vikisol.one.audit.controller;

import com.vikisol.one.audit.dto.AuditLogResponse;
import com.vikisol.one.audit.entity.AuditLog;
import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CEO','ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLog> result = auditService.getAll(pageable);
        Page<AuditLogResponse> mapped = result.map(a -> new AuditLogResponse(
                a.getId(), a.getAction(), a.getTarget(), a.getDetails(),
                a.getPerformedByName(), a.getPerformedByEmail(), a.getIpAddress(), a.getTimestamp()
        ));
        return ResponseEntity.ok(new ApiResponse<>(true, "Audit logs retrieved", new PagedResponse<>(
                mapped.getContent(), mapped.getNumber(), mapped.getSize(),
                mapped.getTotalElements(), mapped.getTotalPages(), mapped.isLast()
        )));
    }
}
