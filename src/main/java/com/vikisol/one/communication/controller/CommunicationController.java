package com.vikisol.one.communication.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.communication.entity.EmailLog;
import com.vikisol.one.communication.service.EmailLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/communication")
@RequiredArgsConstructor
public class CommunicationController {

    private final EmailLogService emailLogService;

    @GetMapping("/emails")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<?>> getEmails(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     @RequestParam(required = false) EmailLog.Category category,
                                                     @RequestParam(required = false) EmailLog.Status status,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
                                                     @RequestParam(required = false) String search) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Emails fetched",
                emailLogService.search(category, status, fromDate, toDate, search, PageRequest.of(page, size))));
    }
}
